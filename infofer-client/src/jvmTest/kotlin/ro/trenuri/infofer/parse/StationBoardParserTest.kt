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
}
