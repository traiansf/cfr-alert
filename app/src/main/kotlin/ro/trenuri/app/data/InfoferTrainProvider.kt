package ro.trenuri.app.data

import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.model.TrainItinerary

class InfoferTrainProvider(private val client: InfoferClient) : TrainProvider {
    override suspend fun getTrain(number: String, year: Int, month: Int, day: Int): TrainItinerary =
        client.getTrain(number, year, month, day)
}
