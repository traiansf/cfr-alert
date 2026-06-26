package ro.trenuri.infofer

import kotlin.test.Test
import kotlin.test.assertTrue

class FixturesTest {
    @Test fun loads_train_fixture() {
        val html = Fixtures.load("train-result-5568.html")
        assertTrue(html.contains("Parcurs tren"), "fixture should contain itinerary markup")
    }
}
