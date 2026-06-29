package ro.trenuri.app.ui.common

import kotlin.math.max

/**
 * Parse "H:MM" or "HH:MM" (infofer may omit leading zero) → total minutes since midnight.
 * Returns null for any input that doesn't contain a valid colon-separated H:MM pair
 * (empty string, missing colon, colon at position 0, non-numeric parts).
 */
fun toMinutesOfDay(hhmm: String): Int? {
    val colon = hhmm.indexOf(':')
    if (colon <= 0) return null
    val h = hhmm.substring(0, colon).toIntOrNull() ?: return null
    val m = hhmm.substring(colon + 1).toIntOrNull() ?: return null
    return h * 60 + m
}

/**
 * Add [minutes] to [hhmm], wrapping at 24 h boundary.
 * Returns zero-padded "HH:MM".
 * If [hhmm] cannot be parsed, returns [hhmm] unchanged (never throws).
 */
fun addMinutesWrap(hhmm: String, minutes: Int): String {
    val base = toMinutesOfDay(hhmm) ?: return hhmm
    val total = (base + minutes).mod(24 * 60)
    val h = total / 60
    val m = total % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}

/**
 * Returns true if the delay-adjusted event time (scheduled + max(delay,0)) is at or after
 * [nowMinutes].  Null or negative delay is treated as zero.
 *
 * If [scheduledTime] cannot be parsed, returns true (keep the entry — never filter-crash
 * on a bad time).
 *
 * NOTE: comparison is plain minutes-of-day; midnight-spanning journeys (e.g. scheduled at 23:50
 * with a run crossing midnight) are out of scope and not handled.
 */
fun isUpcoming(scheduledTime: String, delayMinutes: Int?, nowMinutes: Int): Boolean {
    val base = toMinutesOfDay(scheduledTime) ?: return true
    val estimated = base + max(delayMinutes ?: 0, 0)
    return estimated >= nowMinutes
}

/**
 * Returns the estimated time string "HH:MM" when [delayMinutes] is positive (train is late),
 * otherwise null (on-time / no live data → show scheduled time only).
 * Also returns null if [scheduledTime] cannot be parsed.
 */
fun estimatedTimeOrNull(scheduledTime: String, delayMinutes: Int?): String? {
    if (delayMinutes == null || delayMinutes <= 0) return null
    if (toMinutesOfDay(scheduledTime) == null) return null
    return addMinutesWrap(scheduledTime, delayMinutes)
}
