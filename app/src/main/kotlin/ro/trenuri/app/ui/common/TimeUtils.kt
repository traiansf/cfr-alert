package ro.trenuri.app.ui.common

import kotlin.math.max

/** Parse "H:MM" or "HH:MM" (infofer may omit leading zero) → total minutes since midnight. */
fun toMinutesOfDay(hhmm: String): Int {
    val colon = hhmm.indexOf(':')
    val h = hhmm.substring(0, colon).toInt()
    val m = hhmm.substring(colon + 1).toInt()
    return h * 60 + m
}

/**
 * Add [minutes] to [hhmm], wrapping at 24 h boundary.
 * Returns zero-padded "HH:MM".
 */
fun addMinutesWrap(hhmm: String, minutes: Int): String {
    val total = (toMinutesOfDay(hhmm) + minutes).mod(24 * 60)
    val h = total / 60
    val m = total % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/**
 * Returns true if the delay-adjusted event time (scheduled + max(delay,0)) is at or after
 * [nowMinutes].  Null or negative delay is treated as zero.
 *
 * NOTE: comparison is plain minutes-of-day; midnight-spanning journeys (e.g. scheduled at 23:50
 * with a run crossing midnight) are out of scope and not handled.
 */
fun isUpcoming(scheduledTime: String, delayMinutes: Int?, nowMinutes: Int): Boolean {
    val estimated = toMinutesOfDay(scheduledTime) + max(delayMinutes ?: 0, 0)
    return estimated >= nowMinutes
}

/**
 * Returns the estimated time string "HH:MM" when [delayMinutes] is positive (train is late),
 * otherwise null (on-time / no live data → show scheduled time only).
 */
fun estimatedTimeOrNull(scheduledTime: String, delayMinutes: Int?): String? =
    if (delayMinutes != null && delayMinutes > 0) addMinutesWrap(scheduledTime, delayMinutes)
    else null
