package ro.trenuri.app.data

import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.data.LatLon
import ro.trenuri.infofer.model.Station

class InfoferStationProvider(private val client: InfoferClient) : StationProvider {
    override fun find(query: String, near: LatLon?): List<Station> = client.findStations(query, near)
    override suspend fun nearest(lat: Double, lon: Double): List<Station> =
        client.findNearestStations(lat, lon)
}
