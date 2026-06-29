package ro.trenuri.app.ui.board

import ro.trenuri.infofer.model.StationBoard

sealed interface BoardUiState {
    data object Idle : BoardUiState
    data object Loading : BoardUiState
    data class Success(val board: StationBoard) : BoardUiState
    data object Empty : BoardUiState
    data class Error(val message: String) : BoardUiState
}
