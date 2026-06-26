package ro.trenuri.infofer

import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.model.BoardKind
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveSmokeTest {
    private val enabled = System.getenv("INFOFER_LIVE") == "1"

    @Test fun nearest_stations_live() = runTest {
        if (!enabled) return@runTest
        val client = defaultInfoferClient()
        val stations = client.findNearestStations(44.4268, 26.1025)
        assertTrue(stations.isNotEmpty())
    }
}
