package ro.trenuri.app.data

import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.model.ItineraryOption

class InfoferItineraryProvider(private val client: InfoferClient) : ItineraryProvider {
    override suspend fun search(fromSlug: String, toSlug: String, year: Int, month: Int, day: Int): List<ItineraryOption> =
        client.searchItineraries(fromSlug, toSlug, year, month, day)
}
