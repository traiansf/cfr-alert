package ro.trenuri.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.InfoferTrainNotFoundException

class TrainRepository(
    private val provider: TrainProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(number: String, year: Int, month: Int, day: Int): TrainResult =
        withContext(io) {
            try {
                val itinerary = provider.getTrain(number, year, month, day)
                if (itinerary.branches.isEmpty()) TrainResult.NotFound
                else TrainResult.Success(itinerary)
            } catch (e: InfoferTrainNotFoundException) {
                TrainResult.NotFound
            } catch (e: InfoferNetworkException) {
                TrainResult.NetworkError
            } catch (e: InfoferParseException) {
                TrainResult.ParseError
            }
        }
}
