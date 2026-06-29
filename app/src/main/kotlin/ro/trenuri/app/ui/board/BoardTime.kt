package ro.trenuri.app.ui.board

// Thin wrappers — implementations live in ui.common.TimeUtils (shared with Rute tab).
import ro.trenuri.app.ui.common.addMinutesWrap as commonAddMinutesWrap
import ro.trenuri.app.ui.common.estimatedTimeOrNull as commonEstimatedTimeOrNull
import ro.trenuri.app.ui.common.isUpcoming as commonIsUpcoming
import ro.trenuri.app.ui.common.toMinutesOfDay as commonToMinutesOfDay

fun toMinutesOfDay(hhmm: String): Int = commonToMinutesOfDay(hhmm)
fun addMinutesWrap(hhmm: String, minutes: Int): String = commonAddMinutesWrap(hhmm, minutes)
fun isUpcoming(scheduledTime: String, delayMinutes: Int?, nowMinutes: Int): Boolean =
    commonIsUpcoming(scheduledTime, delayMinutes, nowMinutes)
fun estimatedTimeOrNull(scheduledTime: String, delayMinutes: Int?): String? =
    commonEstimatedTimeOrNull(scheduledTime, delayMinutes)
