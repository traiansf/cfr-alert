package ro.trenuri.app.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.trenuri.app.data.BoardRepository
import ro.trenuri.app.data.BoardResult
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.Station

class BoardViewModel(
    private val repository: BoardRepository,
    private val messages: ErrorMessages,
) : ViewModel() {

    private val _state = MutableStateFlow<BoardUiState>(BoardUiState.Idle)
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    private val _kind = MutableStateFlow(BoardKind.DEPARTURES)
    val kind: StateFlow<BoardKind> = _kind.asStateFlow()

    private var lastStation: Station? = null
    private var lastDate: AppDate? = null
    private var job: Job? = null

    fun load(station: Station, date: AppDate) {
        if (station.slug.isBlank()) return
        lastStation = station
        lastDate = date
        run()
    }

    fun setKind(kind: BoardKind) {
        if (kind == _kind.value) return
        _kind.value = kind
        if (lastStation != null) run()
    }

    private fun run() {
        val station = lastStation ?: return
        val date = lastDate ?: return
        job?.cancel()
        _state.value = BoardUiState.Loading
        job = viewModelScope.launch {
            _state.value = when (val r = repository.board(station.slug, _kind.value, date)) {
                is BoardResult.Success -> BoardUiState.Success(r.board)
                BoardResult.Empty -> BoardUiState.Empty
                BoardResult.NetworkError -> BoardUiState.Error(messages.network)
                BoardResult.ParseError -> BoardUiState.Error(messages.parse)
            }
        }
    }
}
