package ro.trenuri.app.ui

import ro.trenuri.infofer.model.TrainItinerary

sealed interface TrainUiState {
    data object Idle : TrainUiState
    data object Loading : TrainUiState
    data class Success(val itinerary: TrainItinerary) : TrainUiState
    data object Empty : TrainUiState
    data class Error(val message: String) : TrainUiState
}

/** Injected user-facing error strings, so the ViewModel holds no hard-coded copy. */
interface ErrorMessages {
    val network: String
    val parse: String
}
