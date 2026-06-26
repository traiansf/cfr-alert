package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.StopStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrainResultParserTest {
    private val itinerary = TrainResultParser.parse(Fixtures.load("train-result-5568.html"), "5568")

    @Test fun extracts_train_number() {
        assertEquals("5568", itinerary.trainNumber)
    }

    @Test fun has_at_least_one_branch_with_stops() {
        assertTrue(itinerary.branches.isNotEmpty())
        assertTrue(itinerary.branches.first().stops.size >= 2)
    }

    @Test fun first_stop_is_origin_with_departure_only() {
        val first = itinerary.branches.first().stops.first()
        assertEquals("Botoșani", first.station.name)
        assertEquals("9:21", first.departure)
        assertEquals(null, first.arrival)
    }

    @Test fun parses_reported_delay_of_2_minutes_at_suceava() {
        // Fixture captured live with "2 min întârziere ... (Raportat la 18:46)"
        val delay = itinerary.branches.first().delay
        assertEquals(2, delay?.minutes)
        assertEquals("18:46", delay?.reportedAt)
    }

    @Test fun on_time_stops_have_on_time_status() {
        val origin = itinerary.branches.first().stops.first()
        assertEquals(StopStatus.ON_TIME, origin.status)
    }

    @Test fun throws_when_branch_has_no_stops() {
        val html = """
            <html><body>
              <div id="div-stations-branch-1">
                <h4>Parcurs tren A–B</h4>
                <ul class="list-group"></ul>
              </div>
            </body></html>
        """.trimIndent()
        assertFailsWith<InfoferParseException> {
            TrainResultParser.parse(html, "1")
        }
    }
}
