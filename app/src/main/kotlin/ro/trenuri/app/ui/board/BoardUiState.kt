package ro.trenuri.app.ui.board

import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.BoardEntry

data class BoardDay(val date: AppDate, val entries: List<BoardEntry>)

sealed interface BoardUiState {
    data object Idle : BoardUiState
    data object Loading : BoardUiState
    data class Success(
        val sections: List<BoardDay>,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = true,
    ) : BoardUiState
    data object Empty : BoardUiState
    data class Error(val message: String) : BoardUiState
}
