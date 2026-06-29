package ro.trenuri.infofer.data

import ro.trenuri.infofer.model.Station
import kotlin.test.*

class StationsDatasetTest {
    private val sample = listOf(
        Station("Brașov", "Brasov"),
        Station("București Nord", "Bucuresti-Nord"),
        Station("Buzău", "Buzau"),
        Station("Cluj-Napoca", "Cluj-Napoca"),
    )

    @Test fun prefixMatchesRankFirstAndAreDiacriticInsensitive() {
        val r = StationsDataset.matchIn(sample, "bra", null, 10)
        assertEquals("Brasov", r.first().slug)
    }

    @Test fun substringMatchesAreFound() {
        val r = StationsDataset.matchIn(sample, "napoca", null, 10).map { it.slug }
        assertTrue("Cluj-Napoca" in r)
    }

    @Test fun diacriticTypedQueryStillMatches() {
        val r = StationsDataset.matchIn(sample, "buză", null, 10).map { it.slug }
        assertTrue("Buzau" in r)
    }

    @Test fun blankQueryReturnsEmpty() {
        assertTrue(StationsDataset.matchIn(sample, "  ", null, 10).isEmpty())
    }

    @Test fun limitIsRespected() {
        assertEquals(1, StationsDataset.matchIn(sample, "bu", null, 1).size)
    }

    @Test fun nearOrdersMatchesByDistanceAndSinksCoordlessLast() {
        val geo = listOf(
            Station("Gară Aproape", "Gara-Aproape", lat = 44.50, lon = 26.10),
            Station("Gară Departe", "Gara-Departe", lat = 47.15, lon = 27.60),
            Station("Gară FărăCoord", "Gara-FaraCoord"), // no coords
        )
        val near = LatLon(44.43, 26.10) // closest to "Aproape"
        val r = StationsDataset.matchIn(geo, "gara", near, 10).map { it.slug }
        assertEquals(listOf("Gara-Aproape", "Gara-Departe", "Gara-FaraCoord"), r)
    }

    @Test fun realDatasetContainsKnownStations() {
        val slugs = StationsDataset.find("brasov").map { it.slug }
        assertTrue("Brasov" in slugs)
    }
}
