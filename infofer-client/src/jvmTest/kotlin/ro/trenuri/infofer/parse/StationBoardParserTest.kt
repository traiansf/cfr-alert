package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import ro.trenuri.infofer.model.BoardKind
import kotlin.test.Test
import kotlin.test.assertTrue

class StationBoardParserTest {
    @Test fun parses_departures_rows() {
        val board = StationBoardParser.parse(
            Fixtures.load("station-board-brasov.html"), "Brașov", BoardKind.DEPARTURES,
        )
        assertTrue(board.entries.size >= 5, "expected several departures, got ${board.entries.size}")
        assertTrue(board.entries.all { it.trainNumber.isNotBlank() })
        assertTrue(board.entries.all { it.scheduledTime.matches(Regex("\\d{1,2}:\\d{2}")) })
    }

    @Test fun distinguishes_on_time_from_no_live_data() {
        // "la timp" badge → delayMinutes == 0 (on time, live), NOT null (no live data).
        // The UI shows a green "(la timp)" only for 0; null renders nothing.
        val board = StationBoardParser.parse(
            Fixtures.load("station-board-brasov.html"), "Brașov", BoardKind.DEPARTURES,
        )
        assertTrue(
            board.entries.any { it.delayMinutes == 0 },
            "expected at least one on-time (la timp -> 0) entry",
        )
        assertTrue(
            board.entries.any { (it.delayMinutes ?: 0) > 0 },
            "expected at least one delayed entry",
        )
    }
}
