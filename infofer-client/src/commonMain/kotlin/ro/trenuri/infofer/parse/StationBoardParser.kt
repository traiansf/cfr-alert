package ro.trenuri.infofer.parse

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.*
import ro.trenuri.infofer.util.parseCategory

/**
 * Parses a station departures/arrivals board from the infofer HTML fragment
 * (the fragment returned for `/ro-RO/Stations/{station}` pages).
 *
 * Selector rationale (verified against station-board-brasov.html):
 *
 * The page has two distinct sections in order:
 *   1. Departures — `<ul>` containing `<li id="li-train-departures-{n}">` items
 *   2. Arrivals   — `<ul>` containing `<li id="li-train-arrivals-{n}">` items
 *
 * We scope every row selection to `li[id^=li-train-departures-]` or
 * `li[id^=li-train-arrivals-]` respectively, ensuring the two tabs never mix.
 *
 * Within each row:
 *  - Scheduled time: first `div.line-height-1-25` → second child div (no class) holds the time.
 *  - Counterpart station: `a[href*=/ro-RO/Statie/]` text.
 *  - Train number: `a[href*=/ro-RO/Tren/]` href, last path segment before `?`.
 *  - Category: `[class*=span-train-category-]` className.
 *  - Real-time badge: `div.badge.div-stations-train-real-time-badge`
 *      - Delay: `+N min` pattern → N; null when "la timp" or badge absent.
 *      - Track: `div.d-inline-block.ml-3` text with "linia {n}" → extracted track number/name.
 */
object StationBoardParser {
    private val DELAY_RE = Regex("""(\d+)\s*min""")
    private val LINIA_RE = Regex("""linia\s+(\S+)""")
    private val TIME_RE = Regex("""\d{1,2}:\d{2}""")

    fun parse(html: String, station: String, kind: BoardKind): StationBoard {
        val doc = Ksoup.parse(html)
        val idPrefix = if (kind == BoardKind.DEPARTURES) "li-train-departures-" else "li-train-arrivals-"
        val rows = doc.select("li[id^=$idPrefix]")
        if (rows.isEmpty()) throw InfoferParseException("no ${kind.name.lowercase()} rows found for station '$station'")

        val entries = rows.map { parseRow(it) }
        return StationBoard(station = station, kind = kind, entries = entries)
    }

    private fun parseRow(row: Element): BoardEntry {
        // Scheduled time: first line-height-1-25 div, second child div contains the time text.
        // Structure: <div class="line-height-1-25"><div class="text-0-7rem">Pleacă la</div><div>0:13</div></div>
        val timeDiv = row.selectFirst("div.line-height-1-25")
        val scheduledTime = timeDiv
            ?.children()
            ?.firstOrNull { !it.hasClass("text-0-7rem") }
            ?.text()
            ?.trim()
            ?.let { TIME_RE.find(it)?.value }
            ?: throw InfoferParseException("no scheduled time in row: ${row.id()}")

        // Counterpart station (destination for departures, origin for arrivals)
        val counterpartStation = row.selectFirst("a[href*=/ro-RO/Statie/]")
            ?.text()?.trim()
            ?: throw InfoferParseException("no counterpart station in row: ${row.id()}")

        // Train number from the href
        val trainLink = row.selectFirst("a[href*=/ro-RO/Tren/]")
            ?: throw InfoferParseException("no train link in row: ${row.id()}")
        val trainNumber = trainLink.attr("href")
            .substringAfter("/ro-RO/Tren/")
            .substringBefore("?")
            .substringBefore("/")
            .trim()

        // Category badge
        val category = row.selectFirst("[class*=span-train-category-]")
            ?.className()
            ?.let { parseCategory(it) }
            ?: TrainCategory.OTHER

        // Real-time badge (may be absent for trains without live data)
        val badge = row.selectFirst("div.badge.div-stations-train-real-time-badge")
        val badgeText = badge?.text()?.trim()

        val delayMinutes: Int? = when {
            badgeText == null -> null
            badgeText.contains("la timp", ignoreCase = true) -> null
            else -> DELAY_RE.find(badgeText)?.groupValues?.get(1)?.toIntOrNull()
        }

        val track: String? = badge
            ?.selectFirst("div.d-inline-block.ml-3")
            ?.text()?.trim()
            ?.let { LINIA_RE.find(it)?.groupValues?.get(1) }

        return BoardEntry(
            trainNumber = trainNumber,
            category = category,
            counterpartStation = counterpartStation,
            scheduledTime = scheduledTime,
            delayMinutes = delayMinutes,
            track = track,
        )
    }
}
