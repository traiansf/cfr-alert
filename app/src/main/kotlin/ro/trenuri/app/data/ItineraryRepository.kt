package ro.trenuri.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.ItineraryOption

sealed interface ItineraryResult {
    data class Success(val options: List<ItineraryOption>) : ItineraryResult
    data object Empty : ItineraryResult
    data object NetworkError : ItineraryResult
    data object ParseError : ItineraryResult
}

class ItineraryRepository(
    private val provider: ItineraryProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(fromSlug: String, toSlug: String, date: AppDate): ItineraryResult = withContext(io) {
        try {
            val options = provider.search(fromSlug, toSlug, date.year, date.month, date.day)
            if (options.isEmpty()) ItineraryResult.Empty else ItineraryResult.Success(options)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InfoferNetworkException) {
            ItineraryResult.NetworkError
        } catch (e: InfoferParseException) {
            ItineraryResult.ParseError
        } catch (e: Exception) {
            ItineraryResult.ParseError
        }
    }
}
