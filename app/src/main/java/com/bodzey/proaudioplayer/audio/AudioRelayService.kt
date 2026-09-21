package com.bodzey.proaudioplayer.audio

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import com.bodzey.proaudioplayer.MainActivity
import com.bodzey.proaudioplayer.R
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.data.api.apiUrl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AudioRelayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private var relayJob: Job? = null
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null

    @Volatile
    private var activeCall: Call? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRelay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action != ACTION_START || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat(buildNotification())
        runCatching {
            startRelay(intent)
        }.onFailure {
            _error.value = getString(R.string.audio_relay_failed)
            cleanupRelay(cancelJob = true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startRelay(intent: Intent) {
        if (!running.compareAndSet(false, true)) {
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = intent.getParcelableExtraCompat(EXTRA_RESULT_DATA)
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, 0)
        val transport = intent.getStringExtra(EXTRA_TRANSPORT)
            ?.let { runCatching { DeviceEndpoint.Transport.valueOf(it) }.getOrNull() }
            ?: DeviceEndpoint.Transport.HTTP

        if (
            resultCode != Activity.RESULT_OK ||
            resultData == null ||
            host.isBlank() ||
            port !in 1..65535
        ) {
            cleanupRelay(cancelJob = false)
            stopSelf()
            return
        }

        val endpoint = DeviceEndpoint(host = host, port = port, transport = transport)
        _error.value = null
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("MediaProjection is unavailable")
        projection = mediaProjection

        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRelay()
                    stopSelf()
                }
            },
            Handler(Looper.getMainLooper()),
        )

        val captureConfig = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission is required")
        }

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBufferBytes <= 0) {
            cleanupRelay(cancelJob = false)
            stopSelf()
            return
        }

        val audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(max(minBufferBytes, CHUNK_BYTES * 4))
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            cleanupRelay(cancelJob = false)
            stopSelf()
            return
        }

        recorder = audioRecord
        audioRecord.startRecording()

        relayJob = serviceScope.launch {
            var failure: String? = null
            try {
                stream(endpoint, audioRecord)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failure = getString(R.string.audio_relay_failed)
            } finally {
                cleanupRelay(cancelJob = false)
                failure?.let { message -> _error.value = message }
                stopSelf()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun stream(
        endpoint: DeviceEndpoint,
        audioRecord: AudioRecord,
    ) {
        val sessionId = startSession(endpoint)
        _active.value = true
        try {
            val samples = FloatArray(CHUNK_SAMPLES)
            val payload = ByteArray(CHUNK_BYTES)
            val byteBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

            while (running.get()) {
                val read = audioRecord.read(
                    samples,
                    0,
                    samples.size,
                    AudioRecord.READ_BLOCKING,
                )
                if (read <= 0) {
                    if (running.get()) {
                        throw IllegalStateException("AudioPlaybackCapture read failed: $read")
                    }
                    break
                }

                val sampleCount = read - (read % CHANNELS)
                if (sampleCount == 0) {
                    continue
                }

                byteBuffer.clear()
                repeat(sampleCount) { index ->
                    byteBuffer.putFloat(samples[index])
                }
                sendFrame(
                    endpoint = endpoint,
                    sessionId = sessionId,
                    bytes = payload,
                    byteCount = sampleCount * Float.SIZE_BYTES,
                )
            }
        } finally {
            stopSession(endpoint, sessionId)
        }
    }

    private fun startSession(endpoint: DeviceEndpoint): Long {
        val request = Request.Builder()
            .url(endpoint.apiUrl("/api/v1/audio/network/start"))
            .post(EMPTY_BODY)
            .build()

        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Player rejected network audio start: HTTP ${response.code}",
                )
            }
            return JSONObject(response.body.string()).getLong("session_id")
        }
    }

    private fun sendFrame(
        endpoint: DeviceEndpoint,
        sessionId: Long,
        bytes: ByteArray,
        byteCount: Int,
    ) {
        val request = Request.Builder()
            .url(endpoint.apiUrl("/api/v1/audio/network/frame"))
            .header("X-ProAudio-Session", sessionId.toString())
            .post(bytes.toRequestBody(PCM_MEDIA_TYPE, 0, byteCount))
            .build()

        execute(request).use { response ->
            if (response.code != 204) {
                throw IllegalStateException(
                    "Player rejected network audio frame: HTTP ${response.code}",
                )
            }
        }
    }

    private fun stopSession(
        endpoint: DeviceEndpoint,
        sessionId: Long,
    ) {
        val request = Request.Builder()
            .url(endpoint.apiUrl("/api/v1/audio/network/stop"))
            .header("X-ProAudio-Session", sessionId.toString())
            .post(EMPTY_BODY)
            .build()

        runCatching {
            execute(request).close()
        }
    }

    private fun execute(request: Request): okhttp3.Response {
        val call = client.newCall(request)
        activeCall = call
        return try {
            call.execute()
        } finally {
            if (activeCall === call) {
                activeCall = null
            }
        }
    }

    private fun stopRelay() {
        _error.value = null
        cleanupRelay(cancelJob = true)
    }

    private fun cleanupRelay(cancelJob: Boolean) {
        running.set(false)
        activeCall?.cancel()
        activeCall = null

        recorder?.let { audioRecord ->
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
        recorder = null

        val activeProjection = projection
        projection = null
        if (activeProjection != null) {
            runCatching { activeProjection.stop() }
        }

        val job = relayJob
        relayJob = null
        if (cancelJob) {
            job?.cancel()
        }

        _active.value = false
    }

    override fun onDestroy() {
        cleanupRelay(cancelJob = true)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AudioRelayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val activityIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.audio_relay_notification_text))
            .setContentIntent(activityIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.audio_relay_stop),
                    stopPendingIntent,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.audio_relay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Intent::class.java)
        } else {
            getParcelableExtra(name)
        }

    companion object {
        private const val ACTION_START = "com.bodzey.proaudioplayer.audio.START"
        private const val ACTION_STOP = "com.bodzey.proaudioplayer.audio.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_TRANSPORT = "transport"

        private const val CHANNEL_ID = "audio_relay"
        private const val NOTIFICATION_ID = 1042

        private const val SAMPLE_RATE = 48_000
        private const val CHANNELS = 2
        private const val CHUNK_MILLIS = 100
        private const val FRAMES_PER_CHUNK = SAMPLE_RATE * CHUNK_MILLIS / 1000
        private const val CHUNK_SAMPLES = FRAMES_PER_CHUNK * CHANNELS
        private const val CHUNK_BYTES = CHUNK_SAMPLES * Float.SIZE_BYTES

        private val PCM_MEDIA_TYPE = "application/x-proaudio-pcm".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        private val _active = MutableStateFlow(false)
        val active: StateFlow<Boolean> = _active

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        fun start(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            endpoint: DeviceEndpoint,
        ) {
            require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Audio playback capture requires Android 10 or newer"
            }
            val intent = Intent(context, AudioRelayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_HOST, endpoint.host)
                putExtra(EXTRA_PORT, endpoint.port)
                putExtra(EXTRA_TRANSPORT, endpoint.transport.name)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AudioRelayService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
