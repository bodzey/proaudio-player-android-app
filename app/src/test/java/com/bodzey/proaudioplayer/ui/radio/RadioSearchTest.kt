package com.bodzey.proaudioplayer.ui.radio

import com.bodzey.proaudioplayer.core.api.RadioStation
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RadioSearchTest {
    private val stations = listOf(
        station("1", "Київ Радіо", listOf("rock", "ukrainian")),
        station("2", "Місто FM", listOf("jazz")),
        station("3", "HIT FM", listOf("pop")),
    )

    @Test
    fun emptySearchKeepsTheOriginalCatalogOrder() {
        assertSame(stations, filterRadioStations(stations, "  \n "))
    }

    @Test
    fun searchMatchesWordsAcrossNameAndGenre() {
        assertEquals(listOf(stations[0]), filterRadioStations(stations, "  київ ROCK  "))
        assertEquals(listOf(stations[1]), filterRadioStations(stations, "міст"))
        assertEquals(emptyList<RadioStation>(), filterRadioStations(stations, "радіо jazz"))
    }

    @Test
    fun stationNamesMatchIndependentlyOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf(stations[2]), filterRadioStations(stations, "hit"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun station(id: String, name: String, tags: List<String>) = RadioStation(
        id = id,
        name = name,
        url = "https://example.org/$id.mp3",
        homepage = null,
        favicon = null,
        tags = tags,
        codec = "MP3",
        bitrate = 128,
        votes = 0,
    )
}
