package ro.trenuri.infofer.parse

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.*
import ro.trenuri.infofer.util.parseCategory

object ItinerariesParser {
    private val TIME_RE = Regex("""\d{1,2}:\d{2}""")
    private val DELAY_MIN_RE = Regex("""\+(\d+)\s*min""")

    fun parse(html: String): List<ItineraryOption> {
        val doc = Ksoup.parse(html)
        // Each itinerary option card is a <li id="li-itinerary-N"> element.
        // The brief hypothesised div.div-itineraries-station-to-station as the wrapper,
        // but the fixture shows that class appears only 3 times (it's the "from → to" heading).
        // The real per-option container is li[id^=li-itinerary-] — 45 items matching 45 operator logos.
        val optionEls = doc.select("li[id^=li-itinerary-]")
        if (optionEls.isEmpty()) throw InfoferParseException("no itinerary options found")
        return optionEls.mapNotNull { runCatching { parseOption(it) }.getOrNull() }
            .filter { it.legs.isNotEmpty() }
            .ifEmpty { throw InfoferParseException("found ${optionEls.size} option containers but none yielded parseable legs") }
    }

    private fun parseOption(el: Element): ItineraryOption {
        // Departure and arrival times are shown in span.text-1-4rem in the outer summary:
        //   [0] = departure time (e.g. "4:41")
        //   [1] = arrival time (mobile, d-sm-none)
        //   [2] = arrival time (desktop, d-none d-sm-block) — same value
        // Taking first = departure, last = arrival covers both single- and multi-leg cases.
        // NOTE: the extra `color-gray` class only marks already-departed trains; upcoming
        // trains use the bare `text-1-4rem` class, so the selector must NOT require color-gray
        // (otherwise upcoming itineraries — exactly the ones a "today" filter shows — lose their
        // header times). The leg/detail section uses text-1-1rem, so this stays scoped to the 3
        // summary spans.
        val timeSpans = el.select("span.text-1-4rem")
        val departureTime = timeSpans.firstOrNull()?.text()?.trim().orEmpty()
        val arrivalTime = timeSpans.lastOrNull()?.text()?.trim().orEmpty()

        // Extract option-level live delay from the <!--Real time part--> section.
        // span.color-darkgreen → on-time; span.color-firebrick → delayed (+N min).
        // Fail gracefully: null if neither span is found.
        val optionDelay = parseOptionDelay(el)

        // Legs come from the expandable detail section (hidden in page but fully present in DOM).
        val detailEl = el.selectFirst("div.div-itineraries-row-details")
        val legs = if (detailEl != null) parseLegs(detailEl, optionDelay) else emptyList()
        val changes = (legs.size - 1).coerceAtLeast(0)

        return ItineraryOption(
            departureTime = departureTime,
            arrivalTime = arrivalTime,
            durationMinutes = null,
            changes = changes,
            legs = legs,
        )
    }

    /**
     * Extracts the live delay for this itinerary option.
     * - div.text-0-8rem span.color-darkgreen → on-time → Delay(0, null)
     * - div.text-0-8rem span.color-firebrick → delayed → parse "+N min" → Delay(N, null)
     *   If firebrick is present but "+N min" cannot be parsed, return null (no live data).
     * - neither found → null (no live data; fail gracefully)
     */
    private fun parseOptionDelay(el: Element): Delay? {
        if (el.selectFirst("div.text-0-8rem span.color-darkgreen") != null) {
            return Delay(minutes = 0, reportedAt = null)
        }
        val firebrick = el.selectFirst("div.text-0-8rem span.color-firebrick")
        if (firebrick != null) {
            val text = firebrick.text()
            val minutes = DELAY_MIN_RE.find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: return null  // firebrick found but no parseable minutes → fail gracefully
            return Delay(minutes = minutes, reportedAt = null)
        }
        return null
    }

    /**
     * The detail section contains a flat list of li.list-group-item-itinerary-part items:
     *   li[0]         = departure station (no category span)
     *   li[1]         = first train details (has category span, departure+arrival times)
     *   li[2]         = connection/arrival station (no category span)
     *   li[3]         = second train details (if connection route)
     *   …
     *   li[last]      = final arrival station
     *
     * For a direct train: 3 items (station, train, station).
     * For N trains: 2*N+1 items alternating station / train / station.
     *
     * [optionDelay] is the option-level live delay and is propagated to every leg.
     */
    private fun parseLegs(detailEl: Element, optionDelay: Delay? = null): List<ItineraryLeg> {
        val allLis = detailEl.select("li.list-group-item.list-group-item-itinerary-part")
        if (allLis.isEmpty()) return emptyList()

        // Split into train-detail lis (have a category badge) and station lis (don't).
        val trainLis = allLis.filter { it.selectFirst("[class*=span-train-category-]") != null }
        val stationLis = allLis.filter { it.selectFirst("[class*=span-train-category-]") == null }

        if (trainLis.isEmpty()) return emptyList()

        return trainLis.mapIndexed { i, trainLi ->
            val badge = trainLi.selectFirst("[class*=span-train-category-]")
            val category = badge?.className()?.let { parseCategory(it) } ?: TrainCategory.OTHER
            val trainNumber = trainLi.selectFirst("a[href*=/ro-RO/Tren/]")?.text()?.trim().orEmpty()

            // Two div.div-itineraries-departure-arrival per train li:
            //   first  = "Ple DD mon. HH:MM" (departure)
            //   second = "Sos DD mon. HH:MM" (arrival)
            val depArrDivs = trainLi.select("div.div-itineraries-departure-arrival")
            val depTime = depArrDivs.firstOrNull()?.text()?.let { extractTime(it) }.orEmpty()
            val arrTime = depArrDivs.lastOrNull()?.text()?.let { extractTime(it) }.orEmpty()

            // Station names come from the surrounding station lis.
            // Station li text is directly in div[class~=col-9] (not nested deeper).
            val depStation = stationLis.getOrNull(i)
                ?.selectFirst("div.col-9")?.text()?.trim().orEmpty()
            val arrStation = stationLis.getOrNull(i + 1)
                ?.selectFirst("div.col-9")?.text()?.trim().orEmpty()

            ItineraryLeg(
                trainNumber = trainNumber,
                category = category,
                departureStation = depStation,
                departureTime = depTime,
                arrivalStation = arrStation,
                arrivalTime = arrTime,
                delay = optionDelay,
            )
        }
    }

    private fun extractTime(text: String): String = TIME_RE.find(text)?.value.orEmpty()
}
