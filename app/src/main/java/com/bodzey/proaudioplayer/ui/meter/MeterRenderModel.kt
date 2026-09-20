package com.bodzey.proaudioplayer.ui.meter

import android.os.SystemClock
import com.bodzey.proaudioplayer.core.api.MeterFrame
import com.bodzey.proaudioplayer.core.api.StereoMeterLevel
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal enum class MeterStreamStatus {
    Inactive,
    Connecting,
    Active,
    Failed,
}

class MeterRenderSource internal constructor(
    internal val status: StateFlow<MeterStreamStatus>,
    internal val buffer: MeterRenderBuffer,
    internal val renderPulse: SharedFlow<Unit>,
)

internal class MeterRenderBuffer(
    private val nowNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val staleAfterNanos: Long = STALE_AFTER_NANOS,
) {
    @Volatile
    private var frame: MeterFrame? = null

    @Volatile
    private var writtenAtNanos: Long = Long.MIN_VALUE

    private var visuallyActive = false

    fun write(value: MeterFrame): Boolean {
        writtenAtNanos = nowNanos()
        frame = value

        val nextActive = value.hasVisualActivity()
        val shouldWakeRenderer = nextActive || nextActive != visuallyActive
        visuallyActive = nextActive
        return shouldWakeRenderer
    }

    fun reset() {
        frame = null
        writtenAtNanos = Long.MIN_VALUE
        visuallyActive = false
    }

    fun readFrame(): MeterFrame? {
        val current = frame ?: return null
        val age = nowNanos() - writtenAtNanos
        return current.takeIf {
            age >= 0L && age <= staleAfterNanos
        }
    }

    private companion object {
        const val STALE_AFTER_NANOS = 500_000_000L
    }
}

internal class MeterDynamics {
    var displayedRmsDb: Double = METER_MIN_DB
        private set

    val displayedPeakDb = doubleArrayOf(METER_MIN_DB, METER_MIN_DB)
    val heldPeakDb = doubleArrayOf(METER_MIN_DB, METER_MIN_DB)

    private val heldUntilNanos = longArrayOf(0L, 0L)
    private val clipUntilNanos = longArrayOf(0L, 0L)
    private var lastFrameNanos = 0L

    fun update(
        level: StereoMeterLevel?,
        frameTimeNanos: Long,
    ) {
        if (frameTimeNanos <= 0L) return

        val dtSeconds = if (lastFrameNanos == 0L) {
            1.0 / TARGET_RENDER_FPS
        } else {
            ((frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0)
                .coerceAtMost(0.1)
        }
        lastFrameNanos = frameTimeNanos

        val currentLevel = level?.takeIf { it.available }
        val targetPeakLeft =
            currentLevel?.peakDb?.left?.let(::clampDb) ?: METER_MIN_DB
        val targetPeakRight =
            currentLevel?.peakDb?.right?.let(::clampDb) ?: METER_MIN_DB
        val targetRms = currentLevel?.let {
            stereoRmsDb(it.rmsDb.left, it.rmsDb.right)
        } ?: METER_MIN_DB

        displayedRmsDb = smoothDb(
            current = displayedRmsDb,
            target = targetRms,
            dtSeconds = dtSeconds,
            attackSeconds = RMS_ATTACK_SECONDS,
            releaseSeconds = RMS_RELEASE_SECONDS,
        )

        for (channel in 0..1) {
            val targetPeak = if (channel == 0) targetPeakLeft else targetPeakRight
            val clipped = if (channel == 0) {
                currentLevel?.clipLeft == true
            } else {
                currentLevel?.clipRight == true
            }

            displayedPeakDb[channel] = smoothPeak(
                current = displayedPeakDb[channel],
                target = targetPeak,
                dtSeconds = dtSeconds,
            )

            if (clipped) {
                clipUntilNanos[channel] = frameTimeNanos + CLIP_HOLD_NANOS
            }

            if (displayedPeakDb[channel] >= heldPeakDb[channel]) {
                heldPeakDb[channel] = displayedPeakDb[channel]
                heldUntilNanos[channel] = frameTimeNanos + PEAK_HOLD_NANOS
            } else if (frameTimeNanos > heldUntilNanos[channel]) {
                heldPeakDb[channel] = max(
                    displayedPeakDb[channel],
                    heldPeakDb[channel] - PEAK_DECAY_DB_PER_SECOND * dtSeconds,
                )
            }
        }
    }

    fun visiblePeakDb(channel: Int): Double =
        max(displayedPeakDb[channel], heldPeakDb[channel])

    fun clipVisible(
        channel: Int,
        frameTimeNanos: Long,
    ): Boolean = frameTimeNanos < clipUntilNanos[channel]
}

internal const val METER_MIN_DB = -60.0
internal const val METER_MAX_DB = 0.0
internal const val TARGET_RENDER_FPS = 60.0

private const val RMS_ATTACK_SECONDS = 0.055
private const val RMS_RELEASE_SECONDS = 0.34
private const val PEAK_RELEASE_SECONDS = 0.11
private const val PEAK_HOLD_NANOS = 850_000_000L
private const val PEAK_DECAY_DB_PER_SECOND = 28.0
private const val CLIP_HOLD_NANOS = 1_800_000_000L
private const val VISUAL_ACTIVITY_THRESHOLD_DB = METER_MIN_DB + 0.5

private fun MeterFrame.hasVisualActivity(): Boolean =
    master.hasVisualActivity() ||
        music.hasVisualActivity() ||
        alert.hasVisualActivity()

private fun StereoMeterLevel.hasVisualActivity(): Boolean {
    if (!available) return false
    return clipLeft ||
        clipRight ||
        peakDb.left > VISUAL_ACTIVITY_THRESHOLD_DB ||
        peakDb.right > VISUAL_ACTIVITY_THRESHOLD_DB
}

private fun clampDb(value: Double): Double {
    if (!value.isFinite()) return METER_MIN_DB
    return value.coerceIn(METER_MIN_DB, METER_MAX_DB)
}

private fun smoothDb(
    current: Double,
    target: Double,
    dtSeconds: Double,
    attackSeconds: Double,
    releaseSeconds: Double,
): Double {
    val timeConstant = if (target > current) attackSeconds else releaseSeconds
    val alpha = 1.0 - exp(-dtSeconds / timeConstant.coerceAtLeast(0.001))
    return clampDb(current + (target - current) * alpha)
}

private fun smoothPeak(
    current: Double,
    target: Double,
    dtSeconds: Double,
): Double {
    if (target >= current) return target
    return smoothDb(
        current = current,
        target = target,
        dtSeconds = dtSeconds,
        attackSeconds = PEAK_RELEASE_SECONDS,
        releaseSeconds = PEAK_RELEASE_SECONDS,
    )
}

private fun dbToAmplitude(db: Double): Double {
    if (db <= METER_MIN_DB) return 0.0
    return 10.0.pow(clampDb(db) / 20.0)
}

private fun amplitudeToDb(amplitude: Double): Double {
    if (!amplitude.isFinite() || amplitude <= 0.001) return METER_MIN_DB
    return clampDb(20.0 * log10(amplitude))
}

private fun stereoRmsDb(
    leftDb: Double,
    rightDb: Double,
): Double {
    val left = dbToAmplitude(leftDb)
    val right = dbToAmplitude(rightDb)
    return amplitudeToDb(sqrt((left * left + right * right) / 2.0))
}
