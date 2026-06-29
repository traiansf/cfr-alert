package ro.trenuri.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class AppDateTest {
    @Test fun formatsAsDayMonthYearZeroPadded() {
        assertEquals("05.06.2026", AppDate(2026, 6, 5).format())
        assertEquals("29.12.2026", AppDate(2026, 12, 29).format())
    }

    // ── nextDay ───────────────────────────────────────────────────────────────

    @Test fun nextDay_normalDay() {
        assertEquals(AppDate(2026, 6, 30), AppDate(2026, 6, 29).nextDay())
    }

    @Test fun nextDay_endOfMonth() {
        assertEquals(AppDate(2026, 7, 1), AppDate(2026, 6, 30).nextDay())
    }

    @Test fun nextDay_endOfYear() {
        assertEquals(AppDate(2027, 1, 1), AppDate(2026, 12, 31).nextDay())
    }

    @Test fun nextDay_leapYear_feb28_advances_to_feb29() {
        // 2024 is a leap year
        assertEquals(AppDate(2024, 2, 29), AppDate(2024, 2, 28).nextDay())
    }

    @Test fun nextDay_nonLeapYear_feb28_advances_to_mar01() {
        // 2026 is NOT a leap year
        assertEquals(AppDate(2026, 3, 1), AppDate(2026, 2, 28).nextDay())
    }
}
