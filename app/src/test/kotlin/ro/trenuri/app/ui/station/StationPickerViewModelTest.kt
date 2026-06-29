package ro.trenuri.app.ui.station

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import ro.trenuri.app.data.*
import ro.trenuri.infofer.model.Station
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StationPickerViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val all = listOf(Station("Brașov","Brasov"), Station("Buzău","Buzau"))
    private var lastNear: ro.trenuri.infofer.data.LatLon? = null
    private val provider = object : StationProvider {
        override fun find(query: String, near: ro.trenuri.infofer.data.LatLon?): List<Station> {
            lastNear = near
            return all.filter { it.slug.lowercase().contains(query.lowercase()) }
        }
        override suspend fun nearest(lat: Double, lon: Double) = all
    }
    private val repo = StationRepository(provider, testDispatcher)

    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun queryChangeUpdatesSuggestions() {
        val vm = StationPickerViewModel(repo)
        vm.onQueryChange("bra")
        assertEquals(listOf("Brasov"), vm.suggestions.value.map { it.slug })
    }

    @Test fun setLocationIsPassedToFind() {
        val vm = StationPickerViewModel(repo)
        vm.setLocation(44.43, 26.10)
        vm.onQueryChange("bra")
        assertEquals(ro.trenuri.infofer.data.LatLon(44.43, 26.10), lastNear)
    }

    @Test fun blankQueryClearsSuggestions() {
        val vm = StationPickerViewModel(repo)
        vm.onQueryChange("bra"); vm.onQueryChange("")
        assertTrue(vm.suggestions.value.isEmpty())
    }

    @Test fun loadNearbyReturnsReady() = runTest {
        val vm = StationPickerViewModel(repo)
        vm.loadNearby(45.0, 25.0)
        advanceUntilIdle()
        assertEquals(NearbyUiState.Ready(all), vm.nearby.value)
    }

    @Test fun clearNearbyResetsToIdle() = runTest {
        val vm = StationPickerViewModel(repo)
        vm.loadNearby(45.0, 25.0)
        advanceUntilIdle()
        assertEquals(NearbyUiState.Ready(all), vm.nearby.value)
        vm.clearNearby()
        assertEquals(NearbyUiState.Idle, vm.nearby.value)
    }
}
