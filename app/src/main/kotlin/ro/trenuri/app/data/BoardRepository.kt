package ro.trenuri.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.StationBoard

sealed interface BoardResult {
    data class Success(val board: StationBoard) : BoardResult
    data object Empty : BoardResult
    data object NetworkError : BoardResult
    data object ParseError : BoardResult
}

class BoardRepository(
    private val provider: BoardProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun board(stationSlug: String, kind: BoardKind, date: AppDate): BoardResult =
        withContext(io) {
            try {
                val b = provider.board(stationSlug, kind, date.year, date.month, date.day)
                if (b.entries.isEmpty()) BoardResult.Empty else BoardResult.Success(b)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InfoferNetworkException) {
                BoardResult.NetworkError
            } catch (e: InfoferParseException) {
                BoardResult.ParseError
            } catch (e: Exception) {
                BoardResult.ParseError
            }
        }
}
