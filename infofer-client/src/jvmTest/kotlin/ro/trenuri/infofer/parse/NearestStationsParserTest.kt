package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NearestStationsParserTest {
    private val stations = NearestStationsParser.parse(Fixtures.load("nearest-stations-bucuresti.html"))

    @Test fun finds_nearest_stations() {
        assertTrue(stations.isNotEmpty())
        assertEquals("Bucureşti Nord", stations.first().name)
        assertEquals("Bucuresti-Nord", stations.first().slug)
    }
}
