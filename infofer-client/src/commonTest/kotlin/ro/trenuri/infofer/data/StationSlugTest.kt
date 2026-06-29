package ro.trenuri.infofer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class StationSlugTest {
    @Test fun mapsRomanianDiacriticsBothUnicodeForms() {
        // cedilla form ş/ţ and comma-below form ș/ț both transliterate
        assertEquals("Bucuresti-Nord", canonicalStationSlug("Bucureşti Nord"))
        assertEquals("Bucuresti-Nord", canonicalStationSlug("București Nord"))
        assertEquals("Brasov", canonicalStationSlug("Braşov"))
        assertEquals("Ramificatia-Floreni", canonicalStationSlug("Ramificaţia Floreni"))
        assertEquals("Vatra-Dornei-Bai", canonicalStationSlug("Vatra Dornei Băi"))
    }

    @Test fun collapsesSeparatorsAndTrimsHyphens() {
        assertEquals("Foo-Bar", canonicalStationSlug("  Foo   Bar  "))
        assertEquals("A-B", canonicalStationSlug("A / B"))
        assertEquals("A-B", canonicalStationSlug("A.B"))
    }

    @Test fun normalizeStripsStationTypeSuffixes() {
        assertEquals("Vatra Dornei Băi", normalizeStationName("Vatra Dornei Băi hc."))
        assertEquals("Roşu", normalizeStationName("Roşu Hm."))
        assertEquals("Dorna Candrenilor", normalizeStationName("Dorna Candrenilor h."))
        assertEquals("Brașov", normalizeStationName("Brașov"))
    }
}
