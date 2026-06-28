package ro.trenuri.app.ui

import ro.trenuri.infofer.model.Delay

sealed interface DelayBanner {
    /** No live data — train not currently running. NEVER shown as on-time. */
    data object NoLiveData : DelayBanner
    data object OnTime : DelayBanner
    data class Delayed(val minutes: Int, val reportedAt: String?) : DelayBanner
}

fun delayBannerOf(delay: Delay?): DelayBanner = when {
    delay == null -> DelayBanner.NoLiveData
    delay.minutes <= 0 -> DelayBanner.OnTime
    else -> DelayBanner.Delayed(delay.minutes, delay.reportedAt)
}
