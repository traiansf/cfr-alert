package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import ro.trenuri.infofer.InfoferParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ItinerariesParserTest {
    private val options = ItinerariesParser.parse(Fixtures.load("itineraries-bucuresti-brasov.html"))

    @Test fun finds_multiple_options() {
        assertTrue(options.size >= 10, "expected many itineraries, got ${options.size}")
    }

    @Test fun every_option_has_times_and_at_least_one_leg() {
        assertTrue(options.all { it.departureTime.isNotBlank() && it.arrivalTime.isNotBlank() })
        assertTrue(options.all { it.legs.isNotEmpty() })
    }

    @Test fun direct_options_have_zero_changes() {
        assertTrue(options.any { it.changes == 0 })
    }

    @Test fun throws_when_containers_present_but_no_legs() {
        // One option container matching li[id^=li-itinerary-] but with no parseable legs (no detail section).
        val html = """<ul><li id="li-itinerary-1"></li></ul>"""
        assertFailsWith<InfoferParseException> {
            ItinerariesParser.parse(html)
        }
    }
}
