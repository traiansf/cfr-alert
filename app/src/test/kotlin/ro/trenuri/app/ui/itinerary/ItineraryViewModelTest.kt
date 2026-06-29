package ro.trenuri.app.ui.itinerary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import ro.trenuri.app.data.*
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.model.*
import kotlin.test.*

class ItineraryViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val messages = object : ErrorMessages { override val network = "net"; override val parse = "parse" }
    private val bv = Station("Brașov", "Brasov")
    private val bn = Station("București Nord", "Bucuresti-Nord")
    private val today = AppDate(2026, 6, 29)
    private val tomorrow = today.nextDay()
    private val notToday = AppDate(2026, 6, 28)

    private fun makeOption(dep: String = "08:00", arr: String = "10:45", delayMin: Int? = null): ItineraryOption {
        val leg = ItineraryLeg(
            "1733", TrainCategory.IR, "Bucuresti-Nord", dep, "Brasov", arr,
            delay = delayMin?.let { ro.trenuri.infofer.model.Delay(it, null) },
        )
        return ItineraryOption(dep, arr, 165, 0, listOf(leg))
    }

    private val opt = makeOption("08:00", "10:45")
    private val tomorrowOpt = makeOption("01:00", "03:30")

    private fun vmWith(
        todayProvider: () -> AppDate = { today },
        nowProvider: () -> Int = { 0 }, // midnight → everything upcoming
        provider: ItineraryProvider,
    ) = ItineraryViewModel(
        ItineraryRepository(provider, UnconfinedTestDispatcher()),
        messages,
        today = todayProvider,
        now = nowProvider,
    )

    // ── existing behaviour (adapted for new Success shape) ────────────────────

    @Test fun successEmitsOptions() = runTest {
        val vm = vmWith { _, _, _, _, _ -> listOf(opt) }
        vm.search(bn, bv, today); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(listOf(opt), s.sections.single().options)
    }

    @Test fun emptyListEmitsEmpty() = runTest {
        val vm = vmWith { _, _, _, _, _ -> emptyList() }
        vm.search(bn, bv, today); advanceUntilIdle()
        assertEquals(ItineraryUiState.Empty, vm.state.value)
    }

    @Test fun networkErrorMapsToMessage() = runTest {
        val vm = vmWith { _, _, _, _, _ -> throw InfoferNetworkException("x") }
        vm.search(bn, bv, today); advanceUntilIdle()
        assertEquals(ItineraryUiState.Error("net"), vm.state.value)
    }

    // ── today filter: upcoming only ───────────────────────────────────────────

    @Test fun todayFilter_keepsUpcomingOptions() = runTest {
        // now = 10:00 (600 min). Option at 08:00 → past → filtered out.
        // Option at 12:00 → future → kept.
        val vm = vmWith(
            provider = { _, _, _, _, _ -> listOf(makeOption("08:00"), makeOption("12:00")) },
            todayProvider = { today },
            nowProvider = { 600 }, // 10:00
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(1, s.sections.first().options.size)
        assertEquals("12:00", s.sections.first().options.single().departureTime)
    }

    @Test fun todayFilter_allPastTriggersAutoLoadMore() = runTest {
        // now = 23:00 → both options are past → empty today + auto-load tomorrow
        val vm = vmWith(
            provider = { _, _, y, m, d ->
                val date = AppDate(y, m, d)
                when (date) {
                    today -> listOf(makeOption("08:00"), makeOption("12:00"))
                    else  -> listOf(tomorrowOpt)
                }
            },
            todayProvider = { today },
            nowProvider = { 23 * 60 },
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(2, s.sections.size)
        assertEquals(today, s.sections[0].date)
        assertTrue(s.sections[0].options.isEmpty())
        assertEquals(tomorrow, s.sections[1].date)
        assertEquals(1, s.sections[1].options.size)
    }

    @Test fun todayFilter_noFilterOnOtherDate() = runTest {
        // date != today → all options returned regardless of time
        val vm = vmWith(
            provider = { _, _, _, _, _ -> listOf(makeOption("08:00"), makeOption("12:00")) },
            todayProvider = { today },
            nowProvider = { 23 * 60 }, // would filter everything if applied
        )
        vm.search(bn, bv, notToday); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(2, s.sections.first().options.size)
    }

    @Test fun todayFilter_delayedOptionPushedIntoPast_filtered() = runTest {
        // Option departs 12:00, leg has +10 min delay → estimated 12:10; now = 12:11 → filtered.
        val vm = vmWith(
            provider = { _, _, y, m, d ->
                val date = AppDate(y, m, d)
                if (date == today) listOf(makeOption("12:00", delayMin = 10)) else listOf(tomorrowOpt)
            },
            todayProvider = { today },
            nowProvider = { 12 * 60 + 11 },
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertTrue(s.sections.isNotEmpty())
        assertTrue(s.sections.first().options.isEmpty())
    }

    @Test fun todayFilter_delayedOptionStillUpcoming_kept() = runTest {
        // Option departs 12:00, +10 min → estimated 12:10; now = 12:09 → still upcoming.
        val vm = vmWith(
            provider = { _, _, _, _, _ -> listOf(makeOption("12:00", delayMin = 10)) },
            todayProvider = { today },
            nowProvider = { 12 * 60 + 9 },
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(1, s.sections.first().options.size)
    }

    // ── multi-day sections ────────────────────────────────────────────────────

    @Test fun loadMore_appendsNextDaySection() = runTest {
        val vm = vmWith(
            nowProvider = { 0 }, // midnight → all upcoming, no auto-loadMore
            provider = { _, _, y, m, d ->
                val date = AppDate(y, m, d)
                when (date) {
                    today    -> listOf(opt)
                    tomorrow -> listOf(tomorrowOpt)
                    else     -> throw IllegalArgumentException("Unexpected date: $date")
                }
            },
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        val s0 = vm.state.value as ItineraryUiState.Success
        assertEquals(1, s0.sections.size)
        assertEquals(today, s0.sections.single().date)

        vm.loadMore(); advanceUntilIdle()
        val s1 = vm.state.value as ItineraryUiState.Success
        assertEquals(2, s1.sections.size)
        assertEquals(today, s1.sections[0].date)
        assertEquals(tomorrow, s1.sections[1].date)
        assertEquals(tomorrowOpt, s1.sections[1].options.single())
    }

    @Test fun search_resetsToSingleSection() = runTest {
        val vm = vmWith(
            nowProvider = { 0 },
            provider = { _, _, y, m, d ->
                val date = AppDate(y, m, d)
                when (date) {
                    today    -> listOf(opt)
                    tomorrow -> listOf(tomorrowOpt)
                    else     -> listOf(opt)
                }
            },
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        val afterLoad = vm.state.value as ItineraryUiState.Success
        assertEquals(2, afterLoad.sections.size)

        // Re-searching resets sections
        vm.search(bn, bv, today); advanceUntilIdle()
        val afterSearch = vm.state.value as ItineraryUiState.Success
        assertEquals(1, afterSearch.sections.size, "re-search must reset to a single section")
        assertEquals(today, afterSearch.sections.single().date)
    }

    @Test fun futureDay_notFiltered() = runTest {
        // Loading a non-today date: all options kept (no upcoming filter applied)
        val vm = vmWith(
            provider = { _, _, _, _, _ -> listOf(makeOption("08:00"), makeOption("12:00")) },
            todayProvider = { today },
            nowProvider = { 23 * 60 }, // would filter everything if applied to today
        )
        vm.search(bn, bv, notToday); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(2, s.sections.size)
        assertEquals(2, s.sections[0].options.size) // initial date: 2 options, no filter
        assertEquals(2, s.sections[1].options.size) // loaded more: 2 options, no filter
    }

    @Test fun loadMore_onError_keepsExistingSections() = runTest {
        val vm = vmWith(
            nowProvider = { 0 },
            provider = { _, _, y, m, d ->
                val date = AppDate(y, m, d)
                if (date == today) listOf(opt)
                else throw InfoferNetworkException("fail")
            },
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        val before = vm.state.value as ItineraryUiState.Success
        assertEquals(1, before.sections.size)

        vm.loadMore(); advanceUntilIdle()
        val after = vm.state.value as ItineraryUiState.Success
        assertEquals(1, after.sections.size, "error must not blow away existing sections")
        assertFalse(after.canLoadMore, "canLoadMore must be false after error")
    }

    // ── crash regression: empty departureTime must not throw ─────────────────

    @Test fun todayFilter_emptyDepartureTime_keptNoThrow() = runTest {
        // Reproduces the Predeal→Brașov crash: infofer returns an option whose
        // time span can't be parsed → departureTime = "". Before the fix,
        // isUpcoming("", ...) threw StringIndexOutOfBoundsException inside the
        // coroutine. After the fix it returns true (keep) and no exception is thrown.
        val emptyTimeOpt = makeOption(dep = "", arr = "10:45")
        val vm = vmWith(
            provider = { _, _, _, _, _ -> listOf(emptyTimeOpt, makeOption("12:00")) },
            todayProvider = { today },
            nowProvider = { 600 }, // 10:00 — would filter past options normally
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        // Both options kept: the empty-time one is never filtered (isUpcoming = true for it),
        // the 12:00 one is genuinely upcoming at 10:00.
        assertEquals(2, s.sections.first().options.size)
    }

    @Test fun loadMore_guardedByLoadingMore() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val vm = ItineraryViewModel(
            ItineraryRepository(
                { _, _, y, m, d ->
                    val date = AppDate(y, m, d)
                    when (date) {
                        today -> listOf(opt)
                        else  -> listOf(tomorrowOpt)
                    }
                },
                ioDispatcher,
            ),
            messages,
            today = { today },
            now = { 0 },
        )
        vm.search(bn, bv, today); advanceUntilIdle()

        vm.loadMore() // launches coroutine; suspends at withContext(ioDispatcher)
        // At this point loadingMore = true; second call must be a no-op
        vm.loadMore() // guard: loadingMore=true → returns early
        advanceUntilIdle()

        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(2, s.sections.size) // only one tomorrow section appended
    }

    @Test fun loadMore_emptydayAppendsEmptySection() = runTest {
        val vm = vmWith(
            nowProvider = { 0 },
            provider = { _, _, y, m, d ->
                val date = AppDate(y, m, d)
                when (date) {
                    today -> listOf(opt)
                    else  -> emptyList() // next day has no itineraries
                }
            },
        )
        vm.search(bn, bv, today); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        val s = vm.state.value as ItineraryUiState.Success
        assertEquals(2, s.sections.size)
        assertTrue(s.sections[1].options.isEmpty())
        assertTrue(s.canLoadMore)
    }
}
