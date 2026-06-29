package ro.trenuri.infofer.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

data class LatLon(val lat: Double, val lon: Double)

private fun Double.toRad() = this * PI / 180.0

fun haversineKm(a: LatLon, b: LatLon): Double {
    val r = 6371.0088
    val dLat = (b.lat - a.lat).toRad()
    val dLon = (b.lon - a.lon).toRad()
    val s = sin(dLat / 2) * sin(dLat / 2) +
        cos(a.lat.toRad()) * cos(b.lat.toRad()) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(s), sqrt(1 - s))
}
