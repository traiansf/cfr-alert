package ro.trenuri.app.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.trenuri.app.data.ItineraryRepository
import ro.trenuri.app.data.ItineraryResult
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.Station

class ItineraryViewModel(
    private val repository: ItineraryRepository,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _state = MutableStateFlow<ItineraryUiState>(ItineraryUiState.Idle)
    val state: StateFlow<ItineraryUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun search(from: Station, to: Station, date: AppDate) {
        if (from.slug.isBlank() || to.slug.isBlank()) return
        job?.cancel()
        _state.value = ItineraryUiState.Loading
        job = viewModelScope.launch {
            _state.value = when (val r = repository.search(from.slug, to.slug, date)) {
                is ItineraryResult.Success -> ItineraryUiState.Success(r.options)
                ItineraryResult.Empty -> ItineraryUiState.Empty
                ItineraryResult.NetworkError -> ItineraryUiState.Error(messages.network)
                ItineraryResult.ParseError -> ItineraryUiState.Error(messages.parse)
            }
        }
    }
}
