package ro.trenuri.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.data.LatLon
import ro.trenuri.infofer.model.Station

sealed interface NearestResult {
    data class Ok(val stations: List<Station>) : NearestResult
    data object NetworkError : NearestResult
    data object ParseError : NearestResult
}

class StationRepository(
    private val provider: StationProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun find(query: String, near: LatLon? = null): List<Station> =
        provider.find(query, near)

    suspend fun nearest(lat: Double, lon: Double): NearestResult = withContext(io) {
        try {
            NearestResult.Ok(provider.nearest(lat, lon))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InfoferNetworkException) {
            NearestResult.NetworkError
        } catch (e: InfoferParseException) {
            NearestResult.ParseError
        } catch (e: Exception) {
            NearestResult.ParseError
        }
    }
}
