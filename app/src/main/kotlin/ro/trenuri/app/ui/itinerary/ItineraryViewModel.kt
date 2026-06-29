package ro.trenuri.app.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.trenuri.app.data.ItineraryRepository
import ro.trenuri.app.data.ItineraryResult
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.Today
import ro.trenuri.app.ui.common.isUpcoming
import ro.trenuri.infofer.model.Station

/** Maximum number of day-sections to load (day 0 + up to MAX_SECTIONS-1 more). */
private const val MAX_SECTIONS = 14

class ItineraryViewModel(
    private val repository: ItineraryRepository,
    private val messages: ErrorMessages,
    private val today: Today,
    /** Returns minutes elapsed since midnight in local time. Injected for testability. */
    private val now: () -> Int = {
        val cal = java.util.Calendar.getInstance()
        cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    },
) : ViewModel() {
    private val _state = MutableStateFlow<ItineraryUiState>(ItineraryUiState.Idle)
    val state: StateFlow<ItineraryUiState> = _state.asStateFlow()

    private val _loadedFrom = MutableStateFlow<Station?>(null)
    val loadedFrom: StateFlow<Station?> = _loadedFrom.asStateFlow()

    private val _loadedTo = MutableStateFlow<Station?>(null)
    val loadedTo: StateFlow<Station?> = _loadedTo.asStateFlow()

    private var lastFrom: Station? = null
    private var lastTo: Station? = null
    private var job: Job? = null

    /** Accumulated day-sections in chronological order. */
    private val sections = mutableListOf<ItineraryDay>()

    fun search(from: Station, to: Station, date: AppDate) {
        if (from.slug.isBlank() || to.slug.isBlank()) return
        lastFrom = from
        lastTo = to
        _loadedFrom.value = from
        _loadedTo.value = to
        sections.clear()
        job?.cancel()
        _state.value = ItineraryUiState.Loading
        job = viewModelScope.launch {
            when (val r = repository.search(from.slug, to.slug, date)) {
                is ItineraryResult.Success -> {
                    val isToday = date == today()
                    val options = if (isToday) {
                        val nowMin = now()
                        r.options.filter { option ->
                            isUpcoming(option.departureTime, option.legs.firstOrNull()?.delay?.minutes, nowMin)
                        }
                    } else {
                        r.options
                    }
                    sections.add(ItineraryDay(date, options))
                    _state.value = ItineraryUiState.Success(
                        sections = sections.toList(),
                        loadingMore = false,
                        canLoadMore = sections.size < MAX_SECTIONS,
                    )
                    // Auto-load next day if today's filter produced no upcoming options
                    // (raw result was non-empty but all departures have passed)
                    if (isToday && options.isEmpty()) {
                        loadMore()
                    }
                }
                ItineraryResult.Empty -> _state.value = ItineraryUiState.Empty
                ItineraryResult.NetworkError -> _state.value = ItineraryUiState.Error(messages.network)
                ItineraryResult.ParseError -> _state.value = ItineraryUiState.Error(messages.parse)
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? ItineraryUiState.Success ?: return
        if (current.loadingMore || !current.canLoadMore) return
        val from = lastFrom ?: return
        val to = lastTo ?: return
        val nextDate = sections.lastOrNull()?.date?.nextDay() ?: return

        _state.value = current.copy(loadingMore = true)
        job = viewModelScope.launch {
            when (val r = repository.search(from.slug, to.slug, nextDate)) {
                is ItineraryResult.Success -> {
                    sections.add(ItineraryDay(nextDate, r.options))
                    _state.value = ItineraryUiState.Success(
                        sections = sections.toList(),
                        loadingMore = false,
                        canLoadMore = sections.size < MAX_SECTIONS,
                    )
                }
                ItineraryResult.Empty -> {
                    // No trains this day: still add an empty section so the separator appears
                    sections.add(ItineraryDay(nextDate, emptyList()))
                    _state.value = ItineraryUiState.Success(
                        sections = sections.toList(),
                        loadingMore = false,
                        canLoadMore = sections.size < MAX_SECTIONS,
                    )
                }
                ItineraryResult.NetworkError,
                ItineraryResult.ParseError -> {
                    // Stop loading more on error; keep existing sections visible
                    _state.value = current.copy(loadingMore = false, canLoadMore = false)
                }
            }
        }
    }
}
