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

/** Maximum number of day-sections to load (day 0 + up to MAX_SECTIONS-1 more). */
private const val MAX_SECTIONS = 14

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
    private var initialDate: AppDate? = null
    private var job: Job? = null

    /** Accumulated day-sections in chronological order. */
    private val sections = mutableListOf<BoardDay>()

    fun load(station: Station, date: AppDate) {
        if (station.slug.isBlank()) return
        lastStation = station
        initialDate = date
        _loadedStation.value = station
        sections.clear()
        runInitialLoad()
    }

    fun setKind(kind: BoardKind) {
        if (kind == _kind.value) return
        _kind.value = kind
        sections.clear()
        if (lastStation != null) runInitialLoad()
    }

    fun loadMore() {
        val current = _state.value as? BoardUiState.Success ?: return
        if (current.loadingMore || !current.canLoadMore) return
        val station = lastStation ?: return
        val nextDate = sections.lastOrNull()?.date?.nextDay() ?: return

        _state.value = current.copy(loadingMore = true)
        job = viewModelScope.launch {
            when (val r = repository.board(station.slug, _kind.value, nextDate)) {
                is BoardResult.Success -> {
                    sections.add(BoardDay(nextDate, r.board.entries))
                    _state.value = BoardUiState.Success(
                        sections = sections.toList(),
                        loadingMore = false,
                        canLoadMore = sections.size < MAX_SECTIONS,
                    )
                }
                BoardResult.Empty -> {
                    // Day has no trains: still add an empty section so the separator appears
                    sections.add(BoardDay(nextDate, emptyList()))
                    _state.value = BoardUiState.Success(
                        sections = sections.toList(),
                        loadingMore = false,
                        canLoadMore = sections.size < MAX_SECTIONS,
                    )
                }
                BoardResult.NetworkError,
                BoardResult.ParseError -> {
                    // Stop loading more on error; keep existing sections visible
                    _state.value = current.copy(loadingMore = false, canLoadMore = false)
                }
            }
        }
    }

    private fun runInitialLoad() {
        val station = lastStation ?: return
        val date = initialDate ?: return
        job?.cancel()
        _state.value = BoardUiState.Loading
        job = viewModelScope.launch {
            when (val r = repository.board(station.slug, _kind.value, date)) {
                is BoardResult.Success -> {
                    val isToday = date == today()
                    val entries = if (isToday) {
                        val nowMin = now()
                        r.board.entries.filter { entry ->
                            isUpcoming(entry.scheduledTime, entry.delayMinutes, nowMin)
                        }
                    } else {
                        r.board.entries
                    }
                    sections.add(BoardDay(date, entries))
                    _state.value = BoardUiState.Success(
                        sections = sections.toList(),
                        loadingMore = false,
                        canLoadMore = sections.size < MAX_SECTIONS,
                    )
                    // Auto-load next day if today's filter produced no upcoming entries
                    // (raw board was non-empty but all trains have passed)
                    if (isToday && entries.isEmpty()) {
                        loadMore()
                    }
                }
                BoardResult.Empty -> _state.value = BoardUiState.Empty
                BoardResult.NetworkError -> _state.value = BoardUiState.Error(messages.network)
                BoardResult.ParseError -> _state.value = BoardUiState.Error(messages.parse)
            }
        }
    }
}
