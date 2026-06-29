package ro.trenuri.app.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardTimeTest {

    // ── toMinutesOfDay ───────────────────────────────────────────────────────

    @Test fun toMinutesOfDay_normalHour() =
        assertEquals(12 * 60 + 34, toMinutesOfDay("12:34"))

    @Test fun toMinutesOfDay_singleDigitHour() =
        assertEquals(0 * 60 + 13, toMinutesOfDay("0:13"))

    @Test fun toMinutesOfDay_midnight() =
        assertEquals(0, toMinutesOfDay("00:00"))

    @Test fun toMinutesOfDay_endOfDay() =
        assertEquals(23 * 60 + 59, toMinutesOfDay("23:59"))

    // ── addMinutesWrap ───────────────────────────────────────────────────────

    @Test fun addMinutesWrap_simple() =
        assertEquals("12:50", addMinutesWrap("12:34", 16))

    @Test fun addMinutesWrap_wrapsAtMidnight() =
        assertEquals("00:10", addMinutesWrap("23:50", 20))

    @Test fun addMinutesWrap_singleDigitHour() =
        assertEquals("01:03", addMinutesWrap("0:13", 50))

    @Test fun addMinutesWrap_zeroDelay() =
        assertEquals("08:05", addMinutesWrap("08:05", 0))

    @Test fun addMinutesWrap_exactMidnight() =
        assertEquals("00:00", addMinutesWrap("23:59", 1))

    @Test fun addMinutesWrap_zeroPadsHour() =
        assertEquals("03:00", addMinutesWrap("02:45", 15))

    // ── isUpcoming ───────────────────────────────────────────────────────────

    @Test fun isUpcoming_futureNoDelay() =
        assertTrue(isUpcoming("12:50", null, 12 * 60 + 49))

    @Test fun isUpcoming_exactNowNoDelay() =
        assertTrue(isUpcoming("12:50", null, 12 * 60 + 50))

    @Test fun isUpcoming_pastNoDelay() =
        assertFalse(isUpcoming("12:50", null, 12 * 60 + 51))

    @Test fun isUpcoming_delayedPushesIntoPast_false() =
        // scheduled 12:50, +5 min delay → estimated 12:55; now is 12:56 → not upcoming
        assertFalse(isUpcoming("12:50", 5, 12 * 60 + 56))

    @Test fun isUpcoming_delayedStillUpcoming() =
        // scheduled 12:50, +10 min → estimated 13:00; now is 12:59 → upcoming
        assertTrue(isUpcoming("12:50", 10, 12 * 60 + 59))

    @Test fun isUpcoming_negativeDelayTreatedAsZero() =
        // negative delay (early) should not subtract from scheduled time
        assertTrue(isUpcoming("12:50", -5, 12 * 60 + 50))

    @Test fun isUpcoming_zeroDelayBoundary() =
        assertTrue(isUpcoming("12:50", 0, 12 * 60 + 50))

    // ── estimatedTimeOrNull ───────────────────────────────────────────────────

    @Test fun estimatedTimeOrNull_positiveDelay() =
        assertEquals("13:35", estimatedTimeOrNull("12:50", 45))

    @Test fun estimatedTimeOrNull_zeroDelay() =
        assertNull(estimatedTimeOrNull("12:50", 0))

    @Test fun estimatedTimeOrNull_negativeDelay() =
        assertNull(estimatedTimeOrNull("12:50", -3))

    @Test fun estimatedTimeOrNull_nullDelay() =
        assertNull(estimatedTimeOrNull("12:50", null))

    @Test fun estimatedTimeOrNull_wrapsAtMidnight() =
        assertEquals("00:10", estimatedTimeOrNull("23:50", 20))
}
