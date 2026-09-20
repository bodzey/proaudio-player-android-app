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
        val frame = meterFrame(
            music = meterLevel(
                peakLeft = -6.0,
                peakRight = -7.0,
                rmsLeft = -18.0,
                rmsRight = -19.0,
            ),
        )

        buffer.write(frame)

        assertSame(frame, buffer.readFrame())

        now = 1_501L

        assertEquals(null, buffer.readFrame())
    }

    @Test
    fun bufferWakesRendererForSignalAndTransitionBackToSilence() {
        val buffer = MeterRenderBuffer(
            nowNanos = { 10L },
        )

        assertFalse(buffer.write(meterFrame()))
        assertTrue(
            buffer.write(
                meterFrame(
                    music = meterLevel(
                        peakLeft = -12.0,
                        peakRight = -14.0,
                        rmsLeft = -22.0,
                        rmsRight = -24.0,
                    ),
                ),
            ),
        )
        assertTrue(buffer.write(meterFrame()))
        assertFalse(buffer.write(meterFrame()))
    }

    @Test
    fun bufferResetImmediatelyClearsLatestFrame() {
        val buffer = MeterRenderBuffer(
            nowNanos = { 10L },
        )
        buffer.write(
            meterFrame(
                master = meterLevel(
                    peakLeft = -3.0,
                    peakRight = -4.0,
                    rmsLeft = -12.0,
                    rmsRight = -13.0,
                ),
            ),
        )

        buffer.reset()

        assertEquals(null, buffer.readFrame())
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

    @Test
    fun unavailableSourceReleasesTowardSilence() {
        val dynamics = MeterDynamics()
        dynamics.update(
            level = meterLevel(
                peakLeft = -6.0,
                peakRight = -8.0,
                rmsLeft = -12.0,
                rmsRight = -14.0,
            ),
            frameTimeNanos = 1_000_000_000L,
        )
        val beforeRelease = dynamics.displayedRmsDb

        dynamics.update(
            level = null,
            frameTimeNanos = 1_100_000_000L,
        )

        assertTrue(dynamics.displayedRmsDb < beforeRelease)
    }

    private fun meterFrame(
        master: StereoMeterLevel = silentLevel(),
        music: StereoMeterLevel = silentLevel(),
        alert: StereoMeterLevel = silentLevel(),
    ): MeterFrame =
        MeterFrame(
            sequence = 1L,
            sampleRate = 48_000,
            intervalMillis = 20L,
            master = master,
            music = music,
            alert = alert,
        )

    private fun silentLevel(): StereoMeterLevel =
        meterLevel(
            peakLeft = METER_MIN_DB,
            peakRight = METER_MIN_DB,
            rmsLeft = METER_MIN_DB,
            rmsRight = METER_MIN_DB,
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
