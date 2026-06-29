package ro.trenuri.app.data

import ro.trenuri.infofer.data.LatLon
import ro.trenuri.infofer.model.Station

interface StationProvider {
    fun find(query: String, near: LatLon?): List<Station>
    suspend fun nearest(lat: Double, lon: Double): List<Station>
}
