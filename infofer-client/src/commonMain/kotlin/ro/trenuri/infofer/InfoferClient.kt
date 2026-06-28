package ro.trenuri.infofer

import ro.trenuri.infofer.model.*
import ro.trenuri.infofer.net.InfoferSession
import ro.trenuri.infofer.net.extractTokens
import ro.trenuri.infofer.parse.*
import ro.trenuri.infofer.util.formatInfoferDate

class InfoferClient(private val session: InfoferSession) {

    suspend fun getTrain(trainNumber: String, year: Int, month: Int, day: Int): TrainItinerary {
        val page = session.getPage("/ro-RO/Tren/$trainNumber")
        val tokens = extractTokens(page)
        val html = session.postResult(
            "/ro-RO/Trains/TrainsResult",
            mapOf(
                "Date" to formatInfoferDate(year, month, day),
                "TrainRunningNumber" to trainNumber,
                "SelectedBranchCode" to "",
            ),
            tokens,
        )
        return TrainResultParser.parse(html, trainNumber)
    }

    suspend fun searchItineraries(
        fromSlug: String, toSlug: String, year: Int, month: Int, day: Int,
    ): List<ItineraryOption> {
        val page = session.getPage("/ro-RO/Rute-trenuri/$fromSlug/$toSlug")
        val tokens = extractTokens(page)
        val html = session.postResult(
            "/ro-RO/Itineraries/GetItineraries",
            mapOf(
                "DepartureStationName" to fromSlug,
                "ArrivalStationName" to toSlug,
                "DepartureDate" to formatInfoferDate(year, month, day),
                "ConnectionsTypeId" to "1",
                "OrderingTypeId" to "0",
                "TimeSelectionId" to "0",
                "MinutesInDay" to "0",
            ),
            tokens,
        )
        return ItinerariesParser.parse(html)
    }

    suspend fun getStationBoard(
        stationName: String, kind: BoardKind, year: Int, month: Int, day: Int,
    ): StationBoard {
        val page = session.getPage("/ro-RO/Statie/$stationName")
        val tokens = extractTokens(page)
        val html = session.postResult(
            "/ro-RO/Stations/StationsResult",
            mapOf("Date" to formatInfoferDate(year, month, day), "StationName" to stationName),
            tokens,
        )
        return StationBoardParser.parse(html, stationName, kind)
    }

    suspend fun findNearestStations(latitude: Double, longitude: Double): List<Station> {
        val html = session.getPage(
            "/api/ro-RO/Stations/GetNearestStationsName?latitude=$latitude&longitude=$longitude",
        )
        return NearestStationsParser.parse(html)
    }
}
