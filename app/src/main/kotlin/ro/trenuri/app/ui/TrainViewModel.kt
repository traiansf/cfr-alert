package ro.trenuri.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.app.data.TrainResult
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.Today

class TrainViewModel(
    private val repository: TrainRepository,
    private val today: Today,
    private val messages: ErrorMessages,
) : ViewModel() {

    private val _state = MutableStateFlow<TrainUiState>(TrainUiState.Idle)
    val state: StateFlow<TrainUiState> = _state.asStateFlow()

    private val _loadedNumber = MutableStateFlow("")
    val loadedNumber: StateFlow<String> = _loadedNumber.asStateFlow()

    private var searchJob: Job? = null

    fun search(number: String) = load(number, today())

    fun load(number: String, date: AppDate) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        _loadedNumber.value = trimmed
        searchJob?.cancel()
        _state.value = TrainUiState.Loading
        searchJob = viewModelScope.launch {
            _state.value = when (val result = repository.load(trimmed, date.year, date.month, date.day)) {
                is TrainResult.Success -> TrainUiState.Success(result.itinerary)
                TrainResult.NotFound -> TrainUiState.Empty
                TrainResult.NetworkError -> TrainUiState.Error(messages.network)
                TrainResult.ParseError -> TrainUiState.Error(messages.parse)
            }
        }
    }
}
