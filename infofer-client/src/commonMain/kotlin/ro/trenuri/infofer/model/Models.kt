package ro.trenuri.infofer.model

data class Station(
    val name: String,
    val slug: String,
    val lat: Double? = null,
    val lon: Double? = null,
)

enum class TrainCategory { R, RE, RA, IR, IRN, IC, ICN, RR, IRA, RRF, OTHER }

enum class StopStatus { ON_TIME, DELAYED, UNKNOWN }

data class Delay(val minutes: Int, val reportedAt: String?)

data class TrainStop(
    val station: Station,
    val km: Int?,
    val track: String?,
    val arrival: String?,
    val departure: String?,
    val status: StopStatus
)

data class TrainBranch(
    val from: String,
    val to: String,
    val delay: Delay?,
    val stops: List<TrainStop>
)

data class TrainItinerary(
    val trainNumber: String,
    val category: TrainCategory,
    val branches: List<TrainBranch>
)

data class ItineraryLeg(
    val trainNumber: String,
    val category: TrainCategory,
    val departureStation: String,
    val departureTime: String,
    val arrivalStation: String,
    val arrivalTime: String
)

data class ItineraryOption(
    val departureTime: String,
    val arrivalTime: String,
    val durationMinutes: Int?,
    val changes: Int,
    val legs: List<ItineraryLeg>
)

enum class BoardKind { DEPARTURES, ARRIVALS }

data class BoardEntry(
    val trainNumber: String,
    val category: TrainCategory,
    val counterpartStation: String,
    val scheduledTime: String,
    val delayMinutes: Int?,
    val track: String?
)

data class StationBoard(
    val station: String,
    val kind: BoardKind,
    val entries: List<BoardEntry>
)
