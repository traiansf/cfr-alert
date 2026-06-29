package ro.trenuri.app.ui.history

import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.Station

/** ASCII Unit Separator — cannot appear in station names, slugs, or train numbers. */
private const val FS = ""

// ---------------------------------------------------------------------------
// Per-tab query types
// ---------------------------------------------------------------------------

data class TrainQuery(val number: String)

data class RouteQuery(val from: Station, val to: Station)

data class StationQuery(val station: Station, val kind: BoardKind)

// ---------------------------------------------------------------------------
// Serializers / deserializers (functions, not methods, so they can be used as
// method references in Koin bindings: ::serializeTrainQuery etc.)
// ---------------------------------------------------------------------------

fun serializeTrainQuery(q: TrainQuery): String = q.number

fun deserializeTrainQuery(s: String): TrainQuery? =
    if (s.isNotBlank()) TrainQuery(s) else null

fun serializeRouteQuery(q: RouteQuery): String =
    "${q.from.name}$FS${q.from.slug}$FS${q.to.name}$FS${q.to.slug}"

fun deserializeRouteQuery(s: String): RouteQuery? {
    val parts = s.split(FS)
    if (parts.size != 4) return null
    return RouteQuery(
        from = Station(name = parts[0], slug = parts[1]),
        to = Station(name = parts[2], slug = parts[3]),
    )
}

fun serializeStationQuery(q: StationQuery): String =
    "${q.station.name}$FS${q.station.slug}$FS${q.kind.name}"

fun deserializeStationQuery(s: String): StationQuery? {
    val parts = s.split(FS)
    if (parts.size != 3) return null
    val kind = runCatching { BoardKind.valueOf(parts[2]) }.getOrNull() ?: return null
    return StationQuery(
        station = Station(name = parts[0], slug = parts[1]),
        kind = kind,
    )
}
