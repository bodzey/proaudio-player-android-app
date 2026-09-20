package com.bodzey.proaudioplayer.ui.meter

import android.os.SystemClock
import com.bodzey.proaudioplayer.core.api.MeterFrame
import com.bodzey.proaudioplayer.core.api.StereoMeterLevel
import com.bodzey.proaudioplayer.core.api.StereoMeterValues
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

internal enum class MeterBus {
    Master,
    Music,
    Alert,
}

internal class MeterRenderBuffer(
    private val nowNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
    private val staleAfterNanos: Long = STALE_AFTER_NANOS,
) {
    @Volatile
    private var frame: MeterFrame? = null

    @Volatile
    private var writtenAtNanos: Long = Long.MIN_VALUE

    fun write(value: MeterFrame) {
        writtenAtNanos = nowNanos()
        frame = value
    }

    fun reset() {
        frame = null
        writtenAtNanos = Long.MIN_VALUE
    }

    fun read(bus: MeterBus): StereoMeterLevel {
        val current = frame ?: return SILENT_LEVEL
        val age = nowNanos() - writtenAtNanos
        if (age < 0L || age > staleAfterNanos) {
            return SILENT_LEVEL
        }

        return when (bus) {
            MeterBus.Master -> current.master
            MeterBus.Music -> current.music
            MeterBus.Alert -> current.alert
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
        level: StereoMeterLevel,
        frameTimeNanos: Long,
    ) {
        if (frameTimeNanos <= 0L) return

        val dtSeconds = if (lastFrameNanos == 0L) {
            1.0 / 60.0
        } else {
            ((frameTimeNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0)
                .coerceAtMost(0.1)
        }
        lastFrameNanos = frameTimeNanos

        val targetPeakLeft = if (level.available) clampDb(level.peakDb.left) else METER_MIN_DB
        val targetPeakRight = if (level.available) clampDb(level.peakDb.right) else METER_MIN_DB
        val targetRms = if (level.available) {
            stereoRmsDb(level.rmsDb.left, level.rmsDb.right)
        } else {
            METER_MIN_DB
        }

        displayedRmsDb = smoothDb(
            current = displayedRmsDb,
            target = targetRms,
            dtSeconds = dtSeconds,
            attackSeconds = RMS_ATTACK_SECONDS,
            releaseSeconds = RMS_RELEASE_SECONDS,
        )

        val targetPeaks = doubleArrayOf(targetPeakLeft, targetPeakRight)
        val clips = booleanArrayOf(level.clipLeft, level.clipRight)

        for (channel in 0..1) {
            displayedPeakDb[channel] = smoothPeak(
                current = displayedPeakDb[channel],
                target = targetPeaks[channel],
                dtSeconds = dtSeconds,
            )

            if (clips[channel]) {
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
internal const val METER_SEGMENTS = 96

private const val RMS_ATTACK_SECONDS = 0.055
private const val RMS_RELEASE_SECONDS = 0.34
private const val PEAK_RELEASE_SECONDS = 0.11
private const val PEAK_HOLD_NANOS = 850_000_000L
private const val PEAK_DECAY_DB_PER_SECOND = 28.0
private const val CLIP_HOLD_NANOS = 1_800_000_000L

private val SILENT_VALUES = StereoMeterValues(
    left = METER_MIN_DB,
    right = METER_MIN_DB,
)

private val SILENT_LEVEL = StereoMeterLevel(
    peakDb = SILENT_VALUES,
    rmsDb = SILENT_VALUES,
    clipLeft = false,
    clipRight = false,
    available = false,
)

private fun clampDb(value: Double): Double =
    value.coerceIn(METER_MIN_DB, METER_MAX_DB)

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
