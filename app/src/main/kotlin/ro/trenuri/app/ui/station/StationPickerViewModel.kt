package ro.trenuri.app.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ro.trenuri.app.data.NearestResult
import ro.trenuri.app.data.StationRepository
import ro.trenuri.infofer.data.LatLon
import ro.trenuri.infofer.model.Station

sealed interface NearbyUiState {
    data object Idle : NearbyUiState
    data object Loading : NearbyUiState
    data class Ready(val stations: List<Station>) : NearbyUiState
    data class Error(val message: String) : NearbyUiState
}

class StationPickerViewModel(private val repository: StationRepository) : ViewModel() {
    private val _suggestions = MutableStateFlow<List<Station>>(emptyList())
    val suggestions: StateFlow<List<Station>> = _suggestions.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyUiState>(NearbyUiState.Idle)
    val nearby: StateFlow<NearbyUiState> = _nearby.asStateFlow()

    private var location: LatLon? = null
    private var lastQuery: String = ""

    /** Record the device location so future suggestions are distance-ordered; re-rank the current query. */
    fun setLocation(lat: Double, lon: Double) {
        location = LatLon(lat, lon)
        if (lastQuery.isNotBlank()) onQueryChange(lastQuery)
    }

    fun onQueryChange(query: String) {
        lastQuery = query
        _suggestions.value = if (query.isBlank()) emptyList() else repository.find(query, location)
    }

    fun loadNearby(lat: Double, lon: Double) {
        setLocation(lat, lon)
        _nearby.value = NearbyUiState.Loading
        viewModelScope.launch {
            _nearby.value = when (val r = repository.nearest(lat, lon)) {
                is NearestResult.Ok -> NearbyUiState.Ready(r.stations)
                NearestResult.NetworkError -> NearbyUiState.Error("Verifică conexiunea la internet.")
                NearestResult.ParseError -> NearbyUiState.Error("Nu am putut citi răspunsul de la infofer.")
            }
        }
    }
}
