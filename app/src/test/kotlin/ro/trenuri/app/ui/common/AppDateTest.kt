package ro.trenuri.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class AppDateTest {
    @Test fun formatsAsDayMonthYearZeroPadded() {
        assertEquals("05.06.2026", AppDate(2026, 6, 5).format())
        assertEquals("29.12.2026", AppDate(2026, 12, 29).format())
    }
}
