package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.Delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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

    @Test fun summary_times_parsed_when_not_color_gray() {
        // Upcoming itineraries render the summary departure/arrival times as
        // `text-1-4rem` WITHOUT `color-gray` (color-gray marks already-departed trains).
        // The header must still show times for these — selector must not require color-gray.
        val html = """
            <ul>
              <li id="li-itinerary-1">
                <span class="text-1-4rem ">17:30</span>
                <span class="text-1-4rem ">20:17</span>
                <span class="text-1-4rem ">20:17</span>
                <div class="div-itineraries-row-details">
                  <ul>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <div class="col-9">București Nord</div>
                    </li>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <span class="span-train-category-IR"></span>
                      <a href="/ro-RO/Tren/16595">16595</a>
                      <div class="div-itineraries-departure-arrival">Ple 17:30</div>
                      <div class="div-itineraries-departure-arrival">Sos 20:17</div>
                    </li>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <div class="col-9">Brașov</div>
                    </li>
                  </ul>
                </div>
              </li>
            </ul>
        """.trimIndent()
        val parsed = ItinerariesParser.parse(html)
        assertEquals("17:30", parsed[0].departureTime, "departure time of an upcoming (non-gray) option")
        assertEquals("20:17", parsed[0].arrivalTime, "arrival time of an upcoming (non-gray) option")
    }

    @Test fun throws_when_containers_present_but_no_legs() {
        // One option container matching li[id^=li-itinerary-] but with no parseable legs (no detail section).
        val html = """<ul><li id="li-itinerary-1"></li></ul>"""
        assertFailsWith<InfoferParseException> {
            ItinerariesParser.parse(html)
        }
    }

    // ---- TDD: per-option live delay (fixture-derived ground truth) ----

    @Test fun option0_leg_is_on_time() {
        // Fixture option 0: color-darkgreen "la timp" → Delay(minutes=0, reportedAt=null)
        val leg = options[0].legs[0]
        assertEquals(Delay(minutes = 0, reportedAt = null), leg.delay,
            "option 0 should be on-time (Delay(0, null))")
    }

    @Test fun option1_leg_has_6_min_delay() {
        // Fixture option 1: color-firebrick "+6 min întârziere" → Delay(minutes=6, reportedAt=null)
        val leg = options[1].legs[0]
        assertEquals(Delay(minutes = 6, reportedAt = null), leg.delay,
            "option 1 should have 6 min delay")
    }

    @Test fun option3_leg_has_3_min_delay() {
        // Fixture option 3: color-firebrick "+3 min întârziere" → Delay(minutes=3, reportedAt=null)
        val leg = options[3].legs[0]
        assertEquals(Delay(minutes = 3, reportedAt = null), leg.delay,
            "option 3 should have 3 min delay")
    }

    @Test fun firebrick_with_unparseable_minutes_yields_null_delay() {
        // Fix 1: color-firebrick present but no "+N min" text → must return null, NOT Delay(0,...).
        // If it returned Delay(0,...), delayBannerOf would map it to OnTime — a delayed train shown as "la timp".
        val html = """
            <ul>
              <li id="li-itinerary-1">
                <span class="text-1-4rem color-gray">08:00</span>
                <span class="text-1-4rem color-gray">10:00</span>
                <div class="text-0-8rem">
                  <span class="color-firebrick">întârziere necunoscută</span>
                </div>
                <div class="div-itineraries-row-details">
                  <ul>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <div class="col-9">București Nord</div>
                    </li>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <span class="span-train-category-IR"></span>
                      <a href="/ro-RO/Tren/9999">9999</a>
                      <div class="div-itineraries-departure-arrival">Ple 08:00</div>
                      <div class="div-itineraries-departure-arrival">Sos 10:00</div>
                    </li>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <div class="col-9">Brașov</div>
                    </li>
                  </ul>
                </div>
              </li>
            </ul>
        """.trimIndent()
        val parsed = ItinerariesParser.parse(html)
        assertNull(parsed[0].legs[0].delay,
            "firebrick with no parseable '+N min' should yield null delay, not Delay(0,...)")
    }

    @Test fun missing_realtime_span_yields_null_delay() {
        // A valid option with legs but no real-time span → leg.delay == null
        val html = """
            <ul>
              <li id="li-itinerary-1">
                <span class="text-1-4rem color-gray">10:00</span>
                <span class="text-1-4rem color-gray">12:00</span>
                <div class="div-itineraries-row-details">
                  <ul>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <div class="col-9">București Nord</div>
                    </li>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <span class="span-train-category-IR"></span>
                      <a href="/ro-RO/Tren/1234">1234</a>
                      <div class="div-itineraries-departure-arrival">Ple 10:00</div>
                      <div class="div-itineraries-departure-arrival">Sos 12:00</div>
                    </li>
                    <li class="list-group-item list-group-item-itinerary-part">
                      <div class="col-9">Brașov</div>
                    </li>
                  </ul>
                </div>
              </li>
            </ul>
        """.trimIndent()
        val parsed = ItinerariesParser.parse(html)
        assertNull(parsed[0].legs[0].delay,
            "leg.delay should be null when no real-time span is present")
    }
}
