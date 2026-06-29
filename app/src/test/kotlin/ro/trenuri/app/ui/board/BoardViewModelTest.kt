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

    // ── existing behaviour ────────────────────────────────────────────────────

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
        assertEquals(1, s.board.entries.size)
        assertEquals("1733", s.board.entries.single().trainNumber)
    }

    @Test fun todayFilter_allPastBecomesEmpty() = runTest {
        // now = 23:00 → both rows (08:00, 12:10 estimated) are past.
        val vm = vmWith(
            provider = { _, kind, _, _, _ -> boardWithMixedTimes(kind) },
            todayProvider = { today },
            nowProvider = { 23 * 60 },
        )
        vm.load(brasov, today); advanceUntilIdle()
        assertEquals(BoardUiState.Empty, vm.state.value)
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
        assertEquals(2, s.board.entries.size)
    }

    @Test fun todayFilter_delayedRowPushedIntoPast_filtered() = runTest {
        // A train scheduled at 12:00 with +10 min delay → estimated 12:10.
        // now = 12:11 → should be filtered out.
        val board = StationBoard(
            "Brasov", BoardKind.DEPARTURES,
            listOf(BoardEntry("9999", TrainCategory.IR, "X", "12:00", 10, null))
        )
        val vm = vmWith(
            provider = { _, kind, _, _, _ -> board },
            todayProvider = { today },
            nowProvider = { 12 * 60 + 11 },
        )
        vm.load(brasov, today); advanceUntilIdle()
        assertEquals(BoardUiState.Empty, vm.state.value)
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
        assertEquals(1, s.board.entries.size)
    }
}
