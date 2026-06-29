package ro.trenuri.app.ui.board

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import ro.trenuri.app.data.*
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.*
import kotlin.test.*

class BoardViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val messages = object : ErrorMessages { override val network = "net"; override val parse = "parse" }
    private val brasov = Station("Brașov", "Brasov")
    private val today = AppDate(2026, 6, 29)
    private val notToday = AppDate(2026, 6, 28)
    private val tomorrow = today.nextDay() // 2026-06-30

    // ── board factories ───────────────────────────────────────────────────────

    /** A board with two entries: one at 08:00 (on-time) and one at 12:00 +10 min late. */
    private fun boardWithMixedTimes(kind: BoardKind) = StationBoard(
        "Brasov", kind,
        listOf(
            BoardEntry("1001", TrainCategory.IR, "Bucuresti-Nord", "08:00", null, "1"),
            BoardEntry("1733", TrainCategory.IR, "Bucuresti-Nord", "12:00", 10,   "3"),
        )
    )

    // ── original helpers ──────────────────────────────────────────────────────

    private fun board(kind: BoardKind) = StationBoard(
        "Brasov", kind,
        listOf(BoardEntry("1733", TrainCategory.IR, "Bucuresti-Nord", "12:34", 5, "3"))
    )

    private fun tomorrowBoard(kind: BoardKind) = StationBoard(
        "Brasov", kind,
        listOf(BoardEntry("2001", TrainCategory.IR, "Cluj-Napoca", "01:00", null, "1"))
    )

    private fun vmWith(
        todayProvider: () -> AppDate = { today },
        nowProvider: () -> Int = { 0 }, // midnight → everything upcoming
        provider: BoardProvider,
    ) = BoardViewModel(
        BoardRepository(provider, UnconfinedTestDispatcher()),
        messages,
        today = todayProvider,
        now = nowProvider,
    )

    // ── existing behaviour (adapted for new Success shape) ────────────────────

    @Test fun loadEmitsSuccess() = runTest {
        val vm = vmWith { _, kind, _, _, _ -> board(kind) }
        vm.load(brasov, today); advanceUntilIdle()
        assertTrue(vm.state.value is BoardUiState.Success)
    }

    @Test fun setKindRequeriesWithNewKind() = runTest {
        var lastKind: BoardKind? = null
        val vm = vmWith { _, kind, _, _, _ -> lastKind = kind; board(kind) }
        vm.load(brasov, today); advanceUntilIdle()
        vm.setKind(BoardKind.ARRIVALS); advanceUntilIdle()
        assertEquals(BoardKind.ARRIVALS, lastKind)
        assertEquals(BoardKind.ARRIVALS, vm.kind.value)
    }

    // ── today filter: upcoming only ───────────────────────────────────────────

    @Test fun todayFilter_keepsUpcomingRows() = runTest {
        // now = 10:00 (600 min). Row at 08:00 (no delay) → 480 min → past → filtered out.
        // Row at 12:00 +10 → 730 min → future → kept.
        val vm = vmWith(
            provider = { _, kind, _, _, _ -> boardWithMixedTimes(kind) },
            todayProvider = { today },
            nowProvider = { 600 }, // 10:00
        )
        vm.load(brasov, today); advanceUntilIdle()
        val s = vm.state.value as BoardUiState.Success
        assertEquals(1, s.sections.first().entries.size)
        assertEquals("1733", s.sections.first().entries.single().trainNumber)
    }

    @Test fun todayFilter_allPastTriggersAutoLoadMore() = runTest {
        // now = 23:00 → both rows (08:00, 12:10 estimated) are past.
        // Today's section is empty → auto-loadMore fires → tomorrow section is added.
        val vm = vmWith(
            provider = { _, kind, y, m, d ->
                val date = AppDate(y, m, d)
                when (date) {
                    today -> boardWithMixedTimes(kind)
                    else  -> tomorrowBoard(kind)
                }
            },
            todayProvider = { today },
            nowProvider = { 23 * 60 },
        )
        vm.load(brasov, today); advanceUntilIdle()
        val s = vm.state.value as BoardUiState.Success
        // today section present (empty) + tomorrow section auto-loaded
        assertEquals(2, s.sections.size)
        assertEquals(today, s.sections[0].date)
        assertTrue(s.sections[0].entries.isEmpty())
        assertEquals(tomorrow, s.sections[1].date)
        assertEquals(1, s.sections[1].entries.size)
    }

    @Test fun todayFilter_noFilterOnOtherDate() = runTest {
        // date != today → all rows returned regardless of time
        val vm = vmWith(
            provider = { _, kind, _, _, _ -> boardWithMixedTimes(kind) },
            todayProvider = { today },
            nowProvider = { 23 * 60 }, // would filter everything if applied
        )
        vm.load(brasov, notToday); advanceUntilIdle()
        val s = vm.state.value as BoardUiState.Success
        assertEquals(2, s.sections.first().entries.size)
    }

    @Test fun todayFilter_delayedRowPushedIntoPast_filtered() = runTest {
        // A train scheduled at 12:00 with +10 min delay → estimated 12:10.
        // now = 12:11 → should be filtered out.
        val board = StationBoard(
            "Brasov", BoardKind.DEPARTURES,
            listOf(BoardEntry("9999", TrainCategory.IR, "X", "12:00", 10, null))
        )
        val vm = vmWith(
            provider = { _, kind, y, m, d ->
                val date = AppDate(y, m, d)
                if (date == today) board else tomorrowBoard(kind)
            },
            todayProvider = { today },
            nowProvider = { 12 * 60 + 11 },
        )
        vm.load(brasov, today); advanceUntilIdle()
        // today filtered to 0 → auto-loadMore → tomorrow appended
        val s = vm.state.value as BoardUiState.Success
        assertTrue(s.sections.isNotEmpty())
        assertTrue(s.sections.first().entries.isEmpty())
    }

    @Test fun todayFilter_delayedRowStillUpcoming_kept() = runTest {
        // Train at 12:00 +10 min → estimated 12:10; now = 12:09 → still upcoming.
        val board = StationBoard(
            "Brasov", BoardKind.DEPARTURES,
            listOf(BoardEntry("9999", TrainCategory.IR, "X", "12:00", 10, null))
        )
        val vm = vmWith(
            provider = { _, kind, _, _, _ -> board },
            todayProvider = { today },
            nowProvider = { 12 * 60 + 9 },
        )
        vm.load(brasov, today); advanceUntilIdle()
        val s = vm.state.value as BoardUiState.Success
        assertEquals(1, s.sections.first().entries.size)
    }

    // ── multi-day sections ────────────────────────────────────────────────────

    @Test fun loadMore_appendsNextDaySection() = runTest {
        var callCount = 0
        val vm = vmWith(
            nowProvider = { 0 }, // midnight → all upcoming, no auto-loadMore
            provider = { _, kind, y, m, d ->
                callCount++
                val date = AppDate(y, m, d)
                when (date) {
                    today    -> board(kind)
                    tomorrow -> tomorrowBoard(kind)
                    else     -> throw IllegalArgumentException("Unexpected date: $date")
                }
            },
        )
        vm.load(brasov, today); advanceUntilIdle()
        val s0 = vm.state.value as BoardUiState.Success
        assertEquals(1, s0.sections.size)
        assertEquals(today, s0.sections.single().date)

        vm.loadMore(); advanceUntilIdle()
        val s1 = vm.state.value as BoardUiState.Success
        assertEquals(2, s1.sections.size)
        assertEquals(today, s1.sections[0].date)
        assertEquals(tomorrow, s1.sections[1].date)
        assertEquals("2001", s1.sections[1].entries.single().trainNumber)
    }

    @Test fun setKind_resetsToSingleSection() = runTest {
        val vm = vmWith(
            nowProvider = { 0 },
            provider = { _, kind, y, m, d ->
                val date = AppDate(y, m, d)
                when (date) {
                    today    -> board(kind)
                    tomorrow -> tomorrowBoard(kind)
                    else     -> board(kind)
                }
            },
        )
        vm.load(brasov, today); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        val afterLoad = vm.state.value as BoardUiState.Success
        assertEquals(2, afterLoad.sections.size)

        vm.setKind(BoardKind.ARRIVALS); advanceUntilIdle()
        val afterKind = vm.state.value as BoardUiState.Success
        assertEquals(1, afterKind.sections.size, "setKind must reset to a single section")
        assertEquals(today, afterKind.sections.single().date)
    }

    @Test fun futureDay_notFiltered() = runTest {
        // Loading a non-today date: all entries kept (no upcoming filter)
        val vm = vmWith(
            provider = { _, kind, _, _, _ -> boardWithMixedTimes(kind) },
            todayProvider = { today },
            nowProvider = { 23 * 60 }, // would filter everything if applied
        )
        vm.load(brasov, notToday); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        val s = vm.state.value as BoardUiState.Success
        // Both sections (notToday and notToday.nextDay) should have all entries unfiltered
        assertEquals(2, s.sections.size)
        assertEquals(2, s.sections[0].entries.size) // initial date: 2 entries, no filter
        assertEquals(2, s.sections[1].entries.size) // loaded more: 2 entries, no filter
    }

    @Test fun loadMore_onError_keepsExistingSections() = runTest {
        var callCount = 0
        val vm = vmWith(
            nowProvider = { 0 },
            provider = { _, kind, y, m, d ->
                callCount++
                val date = AppDate(y, m, d)
                if (date == today) board(kind)
                else throw ro.trenuri.infofer.InfoferNetworkException("fail", null)
            },
        )
        vm.load(brasov, today); advanceUntilIdle()
        val before = vm.state.value as BoardUiState.Success
        assertEquals(1, before.sections.size)

        vm.loadMore(); advanceUntilIdle()
        val after = vm.state.value as BoardUiState.Success
        assertEquals(1, after.sections.size, "error must not blow away existing sections")
        assertFalse(after.canLoadMore, "canLoadMore must be false after error")
    }

    @Test fun loadMore_guardedByLoadingMore() = runTest {
        // Use StandardTestDispatcher for io so that withContext(io) actually suspends.
        // This lets us observe the loadingMore=true guard before the first loadMore finishes.
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val vm = BoardViewModel(
            BoardRepository(
                { _, kind, y, m, d ->
                    val date = AppDate(y, m, d)
                    when (date) {
                        today -> board(kind)
                        else  -> tomorrowBoard(kind)
                    }
                },
                ioDispatcher,
            ),
            messages,
            today = { today },
            now = { 0 },
        )
        vm.load(brasov, today); advanceUntilIdle()

        vm.loadMore() // launches coroutine; suspends at withContext(ioDispatcher)
        // At this point loadingMore = true; second call must be a no-op
        vm.loadMore() // guard: loadingMore=true → returns early
        advanceUntilIdle()

        val s = vm.state.value as BoardUiState.Success
        assertEquals(2, s.sections.size) // only one tomorrow section appended
    }
}
