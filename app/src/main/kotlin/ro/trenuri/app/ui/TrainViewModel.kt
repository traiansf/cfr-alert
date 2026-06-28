package ro.trenuri.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.app.data.TrainResult

class TrainViewModel(
    private val repository: TrainRepository,
    private val today: () -> Triple<Int, Int, Int>,
    private val messages: ErrorMessages,
) : ViewModel() {

    private val _state = MutableStateFlow<TrainUiState>(TrainUiState.Idle)
    val state: StateFlow<TrainUiState> = _state.asStateFlow()

    fun search(number: String) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        val (y, m, d) = today()
        _state.value = TrainUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.load(trimmed, y, m, d)) {
                is TrainResult.Success -> TrainUiState.Success(result.itinerary)
                TrainResult.NotFound -> TrainUiState.Empty
                TrainResult.NetworkError -> TrainUiState.Error(messages.network)
                TrainResult.ParseError -> TrainUiState.Error(messages.parse)
            }
        }
    }
}
