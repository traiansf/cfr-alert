package ro.trenuri.app.ui.itinerary

import ro.trenuri.infofer.model.ItineraryOption

sealed interface ItineraryUiState {
    data object Idle : ItineraryUiState
    data object Loading : ItineraryUiState
    data class Success(val options: List<ItineraryOption>) : ItineraryUiState
    data object Empty : ItineraryUiState
    data class Error(val message: String) : ItineraryUiState
}
