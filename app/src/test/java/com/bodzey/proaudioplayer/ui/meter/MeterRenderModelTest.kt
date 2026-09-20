package com.bodzey.proaudioplayer.ui.meter

import com.bodzey.proaudioplayer.core.api.MeterFrame
import com.bodzey.proaudioplayer.core.api.StereoMeterLevel
import com.bodzey.proaudioplayer.core.api.StereoMeterValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterRenderModelTest {

    @Test
    fun bufferReturnsLatestFrameUntilItBecomesStale() {
        var now = 1_000L
        val buffer = MeterRenderBuffer(
            nowNanos = { now },
            staleAfterNanos = 500L,
        )
        val frame = meterFrame()

        buffer.write(frame)

        assertSame(frame.music, buffer.read(MeterBus.Music))

        now = 1_501L

        assertFalse(buffer.read(MeterBus.Music).available)
    }

    @Test
    fun bufferResetImmediatelyMakesMetersUnavailable() {
        val buffer = MeterRenderBuffer(
            nowNanos = { 10L },
        )
        buffer.write(meterFrame())

        buffer.reset()

        assertFalse(buffer.read(MeterBus.Master).available)
        assertFalse(buffer.read(MeterBus.Alert).available)
    }

    @Test
    fun dynamicsUsesFastPeakAttackAndSmoothedRmsAttack() {
        val dynamics = MeterDynamics()
        val level = meterLevel(
            peakLeft = -6.0,
            peakRight = -8.0,
            rmsLeft = -12.0,
            rmsRight = -14.0,
        )

        dynamics.update(
            level = level,
            frameTimeNanos = 1_000_000_000L,
        )

        assertEquals(-6.0, dynamics.displayedPeakDb[0], 0.0001)
        assertEquals(-8.0, dynamics.displayedPeakDb[1], 0.0001)
        assertTrue(dynamics.displayedRmsDb > METER_MIN_DB)
        assertTrue(dynamics.displayedRmsDb < -12.0)
    }

    @Test
    fun clipIndicatorIsHeldAfterSourceClipClears() {
        val dynamics = MeterDynamics()
        val clipped = meterLevel(
            peakLeft = 0.0,
            peakRight = -8.0,
            rmsLeft = -10.0,
            rmsRight = -12.0,
            clipLeft = true,
        )

        dynamics.update(
            level = clipped,
            frameTimeNanos = 1_000_000_000L,
        )

        assertTrue(dynamics.clipVisible(0, 2_000_000_000L))
        assertFalse(dynamics.clipVisible(0, 2_900_000_000L))
    }

    private fun meterFrame(): MeterFrame =
        MeterFrame(
            sequence = 1L,
            sampleRate = 48_000,
            intervalMillis = 20L,
            master = meterLevel(-3.0, -4.0, -12.0, -13.0),
            music = meterLevel(-6.0, -7.0, -18.0, -19.0),
            alert = meterLevel(-30.0, -31.0, -36.0, -37.0),
        )

    private fun meterLevel(
        peakLeft: Double,
        peakRight: Double,
        rmsLeft: Double,
        rmsRight: Double,
        clipLeft: Boolean = false,
        clipRight: Boolean = false,
    ): StereoMeterLevel =
        StereoMeterLevel(
            peakDb = StereoMeterValues(
                left = peakLeft,
                right = peakRight,
            ),
            rmsDb = StereoMeterValues(
                left = rmsLeft,
                right = rmsRight,
            ),
            clipLeft = clipLeft,
            clipRight = clipRight,
            available = true,
        )
}
