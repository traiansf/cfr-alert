package ro.trenuri.app.ui.itinerary

import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.ItineraryOption

data class ItineraryDay(val date: AppDate, val options: List<ItineraryOption>)

sealed interface ItineraryUiState {
    data object Idle : ItineraryUiState
    data object Loading : ItineraryUiState
    data class Success(
        val sections: List<ItineraryDay>,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = true,
    ) : ItineraryUiState
    data object Empty : ItineraryUiState
    data class Error(val message: String) : ItineraryUiState
}
