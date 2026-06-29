package ro.trenuri.app.data

import ro.trenuri.infofer.model.ItineraryOption

fun interface ItineraryProvider {
    suspend fun search(fromSlug: String, toSlug: String, year: Int, month: Int, day: Int): List<ItineraryOption>
}
