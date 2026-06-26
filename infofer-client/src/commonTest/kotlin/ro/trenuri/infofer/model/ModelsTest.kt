package ro.trenuri.infofer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsTest {
    @Test
    fun delay_zero_means_on_time() {
        assertTrue(Delay(0, null).minutes == 0)
    }

    @Test
    fun train_itinerary_holds_branches() {
        val it = TrainItinerary(
            trainNumber = "5568",
            category = TrainCategory.R,
            branches = listOf(TrainBranch("Botoșani", "Suceava Nord", Delay(2, "18:46"), emptyList())),
        )
        assertEquals(1, it.branches.size)
        assertEquals(2, it.branches.first().delay?.minutes)
    }
}
