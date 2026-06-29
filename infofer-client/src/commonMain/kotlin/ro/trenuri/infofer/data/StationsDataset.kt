package ro.trenuri.infofer.data

import ro.trenuri.infofer.model.Station

object StationsDataset {
    /** Diacritic-insensitive folding: reuse the slug rule, lowercased, hyphens->spaces. */
    private fun fold(s: String): String =
        canonicalStationSlug(s).replace('-', ' ').lowercase()

    fun matchIn(stations: List<Station>, query: String, near: LatLon?, limit: Int): List<Station> {
        val q = fold(query).trim()
        if (q.isEmpty()) return emptyList()
        val prefix = ArrayList<Station>()
        val substring = ArrayList<Station>()
        for (st in stations) {
            val name = fold(st.name)
            when {
                name.startsWith(q) -> prefix.add(st)
                name.contains(q) -> substring.add(st)
            }
        }
        val matches = prefix + substring
        val ordered = if (near == null) matches else orderByDistance(matches, near)
        return ordered.take(limit)
    }

    /** Located matches sorted by ascending distance; coordless matches keep their (name) order at the end. */
    private fun orderByDistance(matches: List<Station>, near: LatLon): List<Station> {
        val located = matches.filter { it.lat != null && it.lon != null }
            .sortedBy { haversineKm(near, LatLon(it.lat!!, it.lon!!)) }
        val coordless = matches.filter { it.lat == null || it.lon == null }
        return located + coordless
    }

    fun find(query: String, near: LatLon? = null, limit: Int = 20): List<Station> =
        matchIn(ALL_STATIONS, query, near, limit)
}
