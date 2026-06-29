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
import ro.trenuri.app.ui.common.Today
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.Station

class BoardViewModel(
    private val repository: BoardRepository,
    private val messages: ErrorMessages,
    private val today: Today,
    /** Returns minutes elapsed since midnight in local time. Injected for testability. */
    private val now: () -> Int = {
        val cal = java.util.Calendar.getInstance()
        cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    },
) : ViewModel() {

    private val _state = MutableStateFlow<BoardUiState>(BoardUiState.Idle)
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    private val _kind = MutableStateFlow(BoardKind.DEPARTURES)
    val kind: StateFlow<BoardKind> = _kind.asStateFlow()

    private val _loadedStation = MutableStateFlow<Station?>(null)
    val loadedStation: StateFlow<Station?> = _loadedStation.asStateFlow()

    private var lastStation: Station? = null
    private var lastDate: AppDate? = null
    private var job: Job? = null

    fun load(station: Station, date: AppDate) {
        if (station.slug.isBlank()) return
        lastStation = station
        lastDate = date
        _loadedStation.value = station
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
                is BoardResult.Success -> {
                    val entries = if (date == today()) {
                        val nowMin = now()
                        r.board.entries.filter { entry ->
                            isUpcoming(entry.scheduledTime, entry.delayMinutes, nowMin)
                        }
                    } else {
                        r.board.entries
                    }
                    if (entries.isEmpty()) BoardUiState.Empty
                    else BoardUiState.Success(r.board.copy(entries = entries))
                }
                BoardResult.Empty -> BoardUiState.Empty
                BoardResult.NetworkError -> BoardUiState.Error(messages.network)
                BoardResult.ParseError -> BoardUiState.Error(messages.parse)
            }
        }
    }
}
