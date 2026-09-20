package com.bodzey.proaudioplayer.core.device

import com.bodzey.proaudioplayer.core.model.DeviceEndpoint
import com.bodzey.proaudioplayer.core.model.DeviceId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListEntryMergeTest {

    private val id = DeviceId.parse(
        "019c2c87-e95f-7b31-8bab-33e45ca6c2af",
    )
    private val otherId = DeviceId.parse(
        "019c2c87-e95f-7b31-8bab-33e45ca6c2b0",
    )

    @Test
    fun liveDeviceOverridesPersistedCopy() {
        val live = available(
            id = id,
            name = "Live Name",
            host = "192.168.88.10",
            seen = "2026-09-20T12:00:00Z",
        )
        val known = known(
            id = id,
            name = "Old Name",
            host = "192.168.88.9",
            seen = "2026-09-19T12:00:00Z",
        )

        val entry = mergeDeviceListEntries(
            live = listOf(live),
            known = listOf(known),
        ).single()

        assertTrue(entry.online)
        assertEquals("Live Name", entry.displayName)
        assertEquals(
            setOf(DeviceEndpoint("192.168.88.10", 8080)),
            entry.endpoints,
        )
    }

    @Test
    fun persistedOnlyDeviceRemainsOffline() {
        val entry = mergeDeviceListEntries(
            live = emptyList(),
            known = listOf(
                known(
                    id = id,
                    name = "Rack",
                    host = "192.168.88.20",
                    seen = "2026-09-19T12:00:00Z",
                ),
            ),
        ).single()

        assertFalse(entry.online)
        assertEquals("Rack", entry.displayName)
    }

    @Test
    fun onlineDevicesSortBeforeOfflineHistory() {
        val entries = mergeDeviceListEntries(
            live = listOf(
                available(
                    id = otherId,
                    name = "Studio",
                    host = "192.168.88.30",
                    seen = "2026-09-20T12:00:00Z",
                ),
            ),
            known = listOf(
                known(
                    id = id,
                    name = "Rack",
                    host = "192.168.88.20",
                    seen = "2026-09-19T12:00:00Z",
                ),
            ),
        )

        assertTrue(entries[0].online)
        assertFalse(entries[1].online)
    }

    private fun available(
        id: DeviceId,
        name: String,
        host: String,
        seen: String,
    ) = AvailableDevice(
        id = id,
        displayName = name,
        apiMajorVersion = 1,
        endpoints = setOf(DeviceEndpoint(host, 8080)),
        lastSeen = Instant.parse(seen),
        presenceCount = 1,
    )

    private fun known(
        id: DeviceId,
        name: String,
        host: String,
        seen: String,
    ) = KnownDevice(
        id = id,
        displayName = name,
        apiMajorVersion = 1,
        endpoints = setOf(DeviceEndpoint(host, 8080)),
        lastSeen = Instant.parse(seen),
    )
}
