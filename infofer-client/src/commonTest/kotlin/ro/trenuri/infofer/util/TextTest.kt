package ro.trenuri.infofer.util

import ro.trenuri.infofer.model.TrainCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test fun folds_romanian_diacritics() {
        assertEquals("Bucuresti Nord", foldDiacritics("Bucureşti Nord"))
        assertEquals("Brasov", foldDiacritics("Brașov"))
        assertEquals("Targu Mures", foldDiacritics("Târgu Mureș"))
    }

    @Test fun slugifies_station_names() {
        assertEquals("Bucuresti-Nord", stationSlug("Bucureşti Nord"))
        assertEquals("Aeroport-Henri-Coanda", stationSlug("Aeroport Henri Coandă"))
    }

    @Test fun formats_date_with_zero_time() {
        assertEquals("26.06.2026 0:00:00", formatInfoferDate(2026, 6, 26))
        assertEquals("01.12.2026 0:00:00", formatInfoferDate(2026, 12, 1))
    }

    @Test fun parses_categories() {
        assertEquals(TrainCategory.IR, parseCategory("IR"))
        assertEquals(TrainCategory.RE, parseCategory("R-E"))
        assertEquals(TrainCategory.IR, parseCategory("span-train-category-ir"))
        assertEquals(TrainCategory.OTHER, parseCategory("ZZ"))
    }
}
