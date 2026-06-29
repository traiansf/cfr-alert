package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrainNotFoundTest {
    @Test fun returns_true_for_not_found_fixture() {
        val html = Fixtures.load("train-page-notfound-9999999.html")
        assertTrue(isTrainNotFoundPage(html))
    }

    @Test fun returns_false_for_normal_result_fixture() {
        val html = Fixtures.load("train-result-5568.html")
        assertFalse(isTrainNotFoundPage(html))
    }
}
