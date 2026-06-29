package ro.trenuri.infofer.data

import kotlin.test.Test
import kotlin.test.assertTrue

class GeoTest {
    @Test fun haversineApproximatesKnownDistance() {
        // Bucharest (~44.44,26.10) to Brașov (~45.65,25.61) ≈ 137 km great-circle
        val d = haversineKm(LatLon(44.4396, 26.0963), LatLon(45.6536, 25.6112))
        assertTrue(d in 130.0..145.0, "expected ~137 km, got $d")
    }
}
