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
    private val date = AppDate(2026, 6, 29)
    private fun board(kind: BoardKind) = StationBoard(
        "Brasov", kind,
        listOf(BoardEntry("1733", TrainCategory.IR, "Bucuresti-Nord", "12:34", 5, "3"))
    )

    private fun vmWith(provider: BoardProvider) =
        BoardViewModel(BoardRepository(provider, UnconfinedTestDispatcher()), messages)

    @Test fun loadEmitsSuccess() = runTest {
        val vm = vmWith { _, kind, _, _, _ -> board(kind) }
        vm.load(brasov, date); advanceUntilIdle()
        assertTrue(vm.state.value is BoardUiState.Success)
    }

    @Test fun setKindRequeriesWithNewKind() = runTest {
        var lastKind: BoardKind? = null
        val vm = vmWith { _, kind, _, _, _ -> lastKind = kind; board(kind) }
        vm.load(brasov, date); advanceUntilIdle()
        vm.setKind(BoardKind.ARRIVALS); advanceUntilIdle()
        assertEquals(BoardKind.ARRIVALS, lastKind)
        assertEquals(BoardKind.ARRIVALS, vm.kind.value)
    }
}
