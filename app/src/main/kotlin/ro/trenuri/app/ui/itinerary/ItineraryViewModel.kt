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
import ro.trenuri.infofer.model.ItineraryOption
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

    private val _filterMode = MutableStateFlow(ItineraryFilterMode.DEPARTURE)
    val filterMode: StateFlow<ItineraryFilterMode> = _filterMode.asStateFlow()

    private val _loadedFrom = MutableStateFlow<Station?>(null)
    val loadedFrom: StateFlow<Station?> = _loadedFrom.asStateFlow()

    private val _loadedTo = MutableStateFlow<Station?>(null)
    val loadedTo: StateFlow<Station?> = _loadedTo.asStateFlow()

    private var lastFrom: Station? = null
    private var lastTo: Station? = null
    private var job: Job? = null

    /** Accumulated day-sections in chronological order. */
    private val sections = mutableListOf<ItineraryDay>()

    /** Raw (unfiltered) options for the day-0 (search) date. */
    private var rawTodayOptions: List<ItineraryOption> = emptyList()

    /** The date that was passed to [search]; used to decide whether today-filtering applies. */
    private var day0Date: AppDate? = null

    /**
     * Apply the current [filterMode] to [options] when [day0Date] equals today.
     * For non-today dates (or when [day0Date] is unset) returns [options] unchanged.
     *
     * DEPARTURE mode: hide options whose delay-adjusted DEPARTURE has passed.
     * ARRIVAL mode: hide options that have already ARRIVED (i.e. delay-adjusted arrival passed).
     */
    private fun filterToday(options: List<ItineraryOption>): List<ItineraryOption> {
        if (day0Date != today()) return options
        val nowMin = now()
        return when (_filterMode.value) {
            ItineraryFilterMode.DEPARTURE -> options.filter { option ->
                isUpcoming(option.departureTime, option.legs.firstOrNull()?.delay?.minutes, nowMin)
            }
            ItineraryFilterMode.ARRIVAL -> options.filter { option ->
                isUpcoming(option.arrivalTime, option.legs.lastOrNull()?.delay?.minutes, nowMin)
            }
        }
    }

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
                    day0Date = date
                    rawTodayOptions = r.options
                    val options = filterToday(r.options)
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

    /**
     * Switch between DEPARTURE and ARRIVAL filter modes.
     *
     * If the loaded day-0 is today, recomputes the day-0 section in place from
     * [rawTodayOptions] using the new mode and re-emits [ItineraryUiState.Success].
     * All other day-sections are left unchanged. No network call is made.
     */
    fun setFilterMode(mode: ItineraryFilterMode) {
        if (_filterMode.value == mode) return
        _filterMode.value = mode
        // Only recompute if we have a live today section loaded
        if (day0Date != today()) return
        val current = _state.value as? ItineraryUiState.Success ?: return
        val filteredDay0 = filterToday(rawTodayOptions)
        val updatedSections = current.sections.toMutableList()
        if (updatedSections.isNotEmpty()) {
            updatedSections[0] = ItineraryDay(day0Date!!, filteredDay0)
        }
        _state.value = current.copy(sections = updatedSections.toList())
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
