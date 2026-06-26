package ro.trenuri.infofer.parse

import com.fleeksoft.ksoup.Ksoup
import ro.trenuri.infofer.model.Station
import ro.trenuri.infofer.util.stationSlug

object NearestStationsParser {
    fun parse(html: String): List<Station> =
        Ksoup.parse(html).select("button.list-group-item span.font-weight-bold")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .map { Station(it, stationSlug(it)) }
}
