package ro.trenuri.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.app.data.TrainResult
import ro.trenuri.infofer.model.TrainBranch
import ro.trenuri.infofer.model.TrainCategory
import ro.trenuri.infofer.model.TrainItinerary
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val testMessages = object : ErrorMessages {
    override val network = "net"
    override val parse = "parse"
}
private val fixedClock = { Triple(2026, 6, 28) }

private fun repoReturning(result: TrainResult) =
    TrainRepository({ _, _, _, _ ->
        when (result) {
            is TrainResult.Success -> result.itinerary
            TrainResult.NotFound -> TrainItinerary("0", TrainCategory.R, emptyList())
            else -> throw IllegalStateException("unused")
        }
    }, Dispatchers.Unconfined)

class TrainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun blank_input_is_ignored() = runTest {
        val vm = TrainViewModel(repoReturning(TrainResult.NotFound), fixedClock, testMessages)
        vm.search("   ")
        advanceUntilIdle()
        assertEquals(TrainUiState.Idle, vm.state.value)
    }

    @Test
    fun success_sets_success_state() = runTest {
        val branch = TrainBranch("A", "B", delay = null, stops = emptyList())
        val itinerary = TrainItinerary("5568", TrainCategory.R, listOf(branch))
        val vm = TrainViewModel(repoReturning(TrainResult.Success(itinerary)), fixedClock, testMessages)
        vm.search("5568")
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is TrainUiState.Success)
        assertEquals("5568", (state as TrainUiState.Success).itinerary.trainNumber)
    }

    @Test
    fun not_found_sets_empty_state() = runTest {
        val vm = TrainViewModel(repoReturning(TrainResult.NotFound), fixedClock, testMessages)
        vm.search("0000")
        advanceUntilIdle()
        assertEquals(TrainUiState.Empty, vm.state.value)
    }

    @Test
    fun network_error_sets_error_state_with_injected_message() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> throw ro.trenuri.infofer.InfoferNetworkException("x") }, Dispatchers.Unconfined)
        val vm = TrainViewModel(repo, fixedClock, testMessages)
        vm.search("5568")
        advanceUntilIdle()
        assertEquals(TrainUiState.Error("net"), vm.state.value)
    }

    @Test
    fun second_search_cancels_first_so_only_second_train_is_loaded() = runTest {
        val loaded = mutableListOf<String>()
        val branch = ro.trenuri.infofer.model.TrainBranch("X", "Y", delay = null, stops = emptyList())
        val repo = TrainRepository(
            { number, _, _, _ ->
                loaded.add(number)
                TrainItinerary(number, ro.trenuri.infofer.model.TrainCategory.R, listOf(branch))
            },
            Dispatchers.Unconfined,
        )
        val vm = TrainViewModel(repo, fixedClock, testMessages)
        vm.search("A")
        vm.search("B")
        advanceUntilIdle()
        assertEquals(listOf("B"), loaded)
    }
}
