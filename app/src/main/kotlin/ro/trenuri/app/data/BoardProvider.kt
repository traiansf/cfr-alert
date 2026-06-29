package ro.trenuri.app.data

import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.StationBoard

fun interface BoardProvider {
    suspend fun board(stationSlug: String, kind: BoardKind, year: Int, month: Int, day: Int): StationBoard
}
