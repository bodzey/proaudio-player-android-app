package com.bodzey.proaudioplayer.ui.mixer

import com.bodzey.proaudioplayer.core.api.AudioLevelState
import com.bodzey.proaudioplayer.core.api.MixerState
import com.bodzey.proaudioplayer.core.api.MixerTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class MixerMutationMergeTest {
    @Test
    fun responseDoesNotOverwriteProtectedConcurrentChannel() {
        val current = mixer(
            musicDb = -8.0,
            alertDb = -20.0,
        )
        val response = mixer(
            musicDb = -6.0,
            alertDb = -30.0,
        )

        val merged = mergeMixerMutationResult(
            current = current,
            updated = response,
            target = MixerTarget.Music,
            protectedTargets = setOf(MixerTarget.Alert),
        )

        assertEquals(-6.0, merged.music.db ?: Double.NaN, 0.0)
        assertEquals(-20.0, merged.alert.db ?: Double.NaN, 0.0)
    }

    @Test
    fun responseRefreshesUnprotectedChannels() {
        val current = mixer(
            musicDb = -8.0,
            alertDb = -20.0,
        )
        val response = mixer(
            musicDb = -6.0,
            alertDb = -18.0,
        )

        val merged = mergeMixerMutationResult(
            current = current,
            updated = response,
            target = MixerTarget.Music,
            protectedTargets = emptySet(),
        )

        assertEquals(response, merged)
    }

    private fun mixer(
        musicDb: Double,
        alertDb: Double,
    ): MixerState =
        MixerState(
            music = AudioLevelState(
                volumePercent = 50.0,
                muted = false,
                db = musicDb,
            ),
            alert = AudioLevelState(
                volumePercent = 50.0,
                muted = false,
                db = alertDb,
            ),
            master = AudioLevelState(
                volumePercent = 50.0,
                muted = false,
                db = -12.0,
            ),
        )
}
