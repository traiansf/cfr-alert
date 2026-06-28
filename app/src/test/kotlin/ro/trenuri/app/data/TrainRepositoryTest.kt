package ro.trenuri.app.data

import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.TrainCategory
import ro.trenuri.infofer.model.TrainItinerary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun itinerary(branches: List<ro.trenuri.infofer.model.TrainBranch>) =
    TrainItinerary(trainNumber = "5568", category = TrainCategory.R, branches = branches)

class TrainRepositoryTest {

    @Test
    fun returns_success_when_provider_returns_branches() = runTest {
        val branch = ro.trenuri.infofer.model.TrainBranch("A", "B", delay = null, stops = emptyList())
        val repo = TrainRepository({ _, _, _, _ -> itinerary(listOf(branch)) })
        val result = repo.load("5568", 2026, 6, 28)
        assertTrue(result is TrainResult.Success)
        assertEquals("5568", (result as TrainResult.Success).itinerary.trainNumber)
    }

    @Test
    fun returns_not_found_when_no_branches() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> itinerary(emptyList()) })
        assertEquals(TrainResult.NotFound, repo.load("0000", 2026, 6, 28))
    }

    @Test
    fun returns_network_error_on_network_exception() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> throw InfoferNetworkException("boom") })
        assertEquals(TrainResult.NetworkError, repo.load("5568", 2026, 6, 28))
    }

    @Test
    fun returns_parse_error_on_parse_exception() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> throw InfoferParseException("bad html") })
        assertEquals(TrainResult.ParseError, repo.load("5568", 2026, 6, 28))
    }
}
