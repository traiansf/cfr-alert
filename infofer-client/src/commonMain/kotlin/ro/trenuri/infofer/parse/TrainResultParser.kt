package ro.trenuri.infofer.parse

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.*
import ro.trenuri.infofer.util.parseCategory

object TrainResultParser {
    private val DELAY_MIN = Regex("""(\d+)\s*min""")
    private val REPORTED = Regex("""Raportat la\s*(\d{1,2}:\d{2})""")
    private val KM = Regex("""km\s*(\d+)""")
    private val LINIA = Regex("""linia\s*([^\s]+)""")

    fun parse(html: String, trainNumber: String): TrainItinerary {
        val doc = Ksoup.parse(html)
        val branchEls = doc.select("div[id^=div-stations-branch-]")
        if (branchEls.isEmpty()) throw InfoferParseException("no train branches in result for $trainNumber")

        val category = doc.selectFirst("[class*=span-train-category-]")
            ?.className()?.let { parseCategory(it) } ?: TrainCategory.OTHER

        val branches = branchEls.map { parseBranch(it) }
        return TrainItinerary(trainNumber, category, branches)
    }

    private fun parseBranch(el: Element): TrainBranch {
        val header = el.selectFirst("h4")?.text().orEmpty()
        val headerBody = header.substringAfter("Parcurs tren").trim()
        // Split on en-dash (–) or plain hyphen as fallback
        val dashIdx = headerBody.indexOf('–').takeIf { it >= 0 }
            ?: headerBody.indexOf('-').takeIf { it >= 0 }
        val from = if (dashIdx != null) headerBody.substring(0, dashIdx).trim() else headerBody
        val to = if (dashIdx != null) headerBody.substring(dashIdx + 1).trim() else ""
        val delay = parseDelay(el)
        val stops = el.select("li.list-group-item").map { parseStop(it) }
        if (stops.isEmpty()) throw InfoferParseException("branch '$from–$to' has no stops")
        return TrainBranch(from, to, delay, stops)
    }

    private fun parseDelay(branch: Element): Delay {
        // Find the paragraph that contains the stopwatch icon
        val p = branch.select("p").firstOrNull { it.selectFirst("i.fa-stopwatch") != null }
            ?: return Delay(0, null)
        val text = p.text()
        val mins = DELAY_MIN.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val reported = REPORTED.find(text)?.groupValues?.get(1)
        return if (mins != null && text.contains("întârziere")) Delay(mins, reported) else Delay(0, reported)
    }

    private fun parseStop(li: Element): TrainStop {
        val link = li.selectFirst("a[href*=/ro-RO/Statie/]")
        val name = link?.text()?.trim().orEmpty()
        val slug = link?.attr("href")
            ?.substringAfter("/Statie/")?.substringBefore("?").orEmpty()
        val whole = li.text()
        val km = KM.find(whole)?.groupValues?.get(1)?.toIntOrNull()
        val track = LINIA.find(whole)?.groupValues?.get(1)

        // Left column (arrival): div.text-1-3rem without text-right class
        // Right column (departure): div.text-1-3rem with text-right class
        val arrival = li.select("div.text-1-3rem")
            .firstOrNull { !it.hasClass("text-right") }
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val departure = li.selectFirst("div.text-1-3rem.text-right")
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }

        val status = when {
            li.selectFirst("div.text-0-8rem.color-firebrick") != null -> StopStatus.DELAYED
            li.selectFirst("div.text-0-8rem.color-darkgreen") != null -> StopStatus.ON_TIME
            else -> StopStatus.UNKNOWN
        }
        return TrainStop(Station(name, slug), km, track, arrival, departure, status)
    }
}
