package com.bodzey.proaudioplayer.ui.player

import com.bodzey.proaudioplayer.core.api.ApiCapabilities
import com.bodzey.proaudioplayer.core.api.AudioLevelState
import com.bodzey.proaudioplayer.core.api.MpdState
import com.bodzey.proaudioplayer.core.api.PlayerControls
import com.bodzey.proaudioplayer.core.api.PlayerState
import com.bodzey.proaudioplayer.core.api.PlayerStatus
import com.bodzey.proaudioplayer.core.api.PriorityState
import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import com.bodzey.proaudioplayer.core.session.PlayerSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiProjectionTest {
    @Test
    fun timelineOnlyHeartbeatDoesNotInvalidateWholePlayerScreen() {
        val first = connected(player(positionSeconds = 10.0, progressPercent = 5))
        val heartbeat = connected(player(positionSeconds = 11.0, progressPercent = 6))
        assertTrue(samePlayerUiState(first, heartbeat))
    }

    @Test
    fun metadataAndArtworkChangesInvalidatePlayerScreen() {
        val first = connected(player())
        val next = connected(player(title = "Next track", artUrl = "https://i.scdn.co/image/next"))
        assertFalse(samePlayerUiState(first, next))
    }

    private fun connected(player: PlayerState): PlayerSessionState.Connected =
        PlayerSessionState.Connected(
            deviceId = DeviceId.parse("test-player"),
            displayName = "Test Player",
            endpoint = DeviceEndpoint(host = "192.168.1.10", port = 8080),
            capabilities = ApiCapabilities(1, "sse", setOf("status", "meters")),
            status = PlayerStatus(
                name = "ProAudio Player",
                audioTopologyRevision = 1,
                master = AudioLevelState(80.0, false, -5.0),
                music = AudioLevelState(80.0, false),
                priority = PriorityState(
                    mode = "",
                    active = false,
                    blocking = false,
                    duckOnlyDuringAnnouncement = false,
                    minuteSilenceActive = false,
                    matchedUids = emptyList(),
                    lastSuccessAt = null,
                    lastChangeAt = null,
                    lastError = null,
                ),
                mpd = MpdState(false, null),
                player = player,
            ),
        )

    private fun player(
        title: String = "Track",
        artUrl: String? = "https://i.scdn.co/image/current",
        positionSeconds: Double? = 10.0,
        progressPercent: Int = 5,
    ): PlayerState =
        PlayerState(
            source = "Spotify Connect",
            backend = "spotify-mpris",
            state = "playing",
            title = title,
            artist = "Artist",
            album = "Album",
            positionSeconds = positionSeconds,
            durationSeconds = 200.0,
            progressPercent = progressPercent,
            controls = PlayerControls(false, true, true, true, true),
            artUrl = artUrl,
        )
}
