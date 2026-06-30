package ro.trenuri.infofer

import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.model.BoardKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveSmokeTest {
    private val enabled = System.getenv("INFOFER_LIVE") == "1"

    // Dates are offset from today so the live smoke tests never go stale.
    private val tomorrow: LocalDate = LocalDate.now().plusDays(1)

    @Test fun nearest_stations_live() = runTest {
        if (!enabled) return@runTest
        val client = defaultInfoferClient()
        val stations = client.findNearestStations(44.4268, 26.1025)
        assertTrue(stations.isNotEmpty())
    }

    @Test fun itineraries_live() = runTest {
        if (!enabled) return@runTest
        val client = defaultInfoferClient()
        val itineraries = client.searchItineraries(
            "Bucuresti-Nord", "Brasov", tomorrow.year, tomorrow.monthValue, tomorrow.dayOfMonth,
        )
        assertTrue(itineraries.isNotEmpty())
    }

    @Test fun future_station_board_live() = runTest {
        if (!enabled) return@runTest
        val client = defaultInfoferClient()
        // A future date — the case that previously returned a JS redirect stub.
        val board = client.getStationBoard(
            "Brasov", BoardKind.DEPARTURES, tomorrow.year, tomorrow.monthValue, tomorrow.dayOfMonth,
        )
        assertTrue(board.entries.isNotEmpty(), "expected a future-date board, got no entries")
    }
}
