package ro.trenuri.app.data

import ro.trenuri.infofer.model.TrainItinerary

sealed interface TrainResult {
    data class Success(val itinerary: TrainItinerary) : TrainResult
    data object NotFound : TrainResult
    data object NetworkError : TrainResult
    data object ParseError : TrainResult
}
