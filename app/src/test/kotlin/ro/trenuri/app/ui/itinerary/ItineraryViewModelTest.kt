package ro.trenuri.app.ui.itinerary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import ro.trenuri.app.data.*
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.model.*
import kotlin.test.*

class ItineraryViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val messages = object : ErrorMessages { override val network = "net"; override val parse = "parse" }
    private val bv = Station("Brașov","Brasov"); private val bn = Station("București Nord","Bucuresti-Nord")
    private val date = AppDate(2026,6,29)
    private val opt = ItineraryOption("08:00","10:45",165,0,
        listOf(ItineraryLeg("1733", TrainCategory.IR,"Bucuresti-Nord","08:00","Brasov","10:45")))

    private fun vmWith(provider: ItineraryProvider) =
        ItineraryViewModel(ItineraryRepository(provider, UnconfinedTestDispatcher()), messages)

    @Test fun successEmitsOptions() = runTest {
        val vm = vmWith { _,_,_,_,_ -> listOf(opt) }
        vm.search(bn, bv, date); advanceUntilIdle()
        assertEquals(ItineraryUiState.Success(listOf(opt)), vm.state.value)
    }
    @Test fun emptyListEmitsEmpty() = runTest {
        val vm = vmWith { _,_,_,_,_ -> emptyList() }
        vm.search(bn, bv, date); advanceUntilIdle()
        assertEquals(ItineraryUiState.Empty, vm.state.value)
    }
    @Test fun networkErrorMapsToMessage() = runTest {
        val vm = vmWith { _,_,_,_,_ -> throw InfoferNetworkException("x") }
        vm.search(bn, bv, date); advanceUntilIdle()
        assertEquals(ItineraryUiState.Error("net"), vm.state.value)
    }
}
