package ro.trenuri.app.data

import ro.trenuri.infofer.model.TrainItinerary

/** Test seam over InfoferClient.getTrain so the app layer needs no network in tests. */
fun interface TrainProvider {
    suspend fun getTrain(number: String, year: Int, month: Int, day: Int): TrainItinerary
}
