package ro.trenuri.app.data

import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.StationBoard

class InfoferBoardProvider(private val client: InfoferClient) : BoardProvider {
    override suspend fun board(
        stationSlug: String,
        kind: BoardKind,
        year: Int,
        month: Int,
        day: Int,
    ): StationBoard = client.getStationBoard(stationSlug, kind, year, month, day)
}
