package ro.trenuri.app.ui

import ro.trenuri.infofer.model.Delay
import kotlin.test.Test
import kotlin.test.assertEquals

class DelayBannerTest {

    @Test
    fun null_delay_is_no_live_data_not_on_time() {
        assertEquals(DelayBanner.NoLiveData, delayBannerOf(null))
    }

    @Test
    fun zero_minutes_is_on_time() {
        assertEquals(DelayBanner.OnTime, delayBannerOf(Delay(0, "18:46")))
    }

    @Test
    fun positive_minutes_is_delayed_with_details() {
        assertEquals(DelayBanner.Delayed(7, "18:46"), delayBannerOf(Delay(7, "18:46")))
    }
}
