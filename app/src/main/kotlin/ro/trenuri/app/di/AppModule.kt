package ro.trenuri.app.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ro.trenuri.app.data.InfoferItineraryProvider
import ro.trenuri.app.data.InfoferStationProvider
import ro.trenuri.app.data.InfoferTrainProvider
import ro.trenuri.app.data.ItineraryProvider
import ro.trenuri.app.data.ItineraryRepository
import ro.trenuri.app.data.StationProvider
import ro.trenuri.app.data.StationRepository
import ro.trenuri.app.data.TrainProvider
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.TrainViewModel
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.Today
import ro.trenuri.app.ui.itinerary.ItineraryViewModel
import ro.trenuri.app.ui.station.StationPickerViewModel
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.defaultInfoferClient
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

val ioDispatcherQualifier = named("io")

val appModule = module {
    single<InfoferClient> { defaultInfoferClient() }
    single<TrainProvider> { InfoferTrainProvider(get()) }
    single<StationProvider> { InfoferStationProvider(get()) }
    single<ItineraryProvider> { InfoferItineraryProvider(get()) }
    single<CoroutineDispatcher>(ioDispatcherQualifier) { Dispatchers.IO }
    single { TrainRepository(get(), get(ioDispatcherQualifier)) }
    single { StationRepository(get(), get(ioDispatcherQualifier)) }
    single { ItineraryRepository(get(), get(ioDispatcherQualifier)) }
    single<ErrorMessages> {
        object : ErrorMessages {
            override val network = "Verifică conexiunea la internet."
            override val parse = "Nu am putut citi răspunsul de la infofer."
        }
    }
    single<Today>(named("today")) {
        {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            AppDate(now.year, now.monthNumber, now.dayOfMonth)
        }
    }
    viewModel {
        TrainViewModel(
            repository = get(),
            today = get(named("today")),
            messages = get(),
        )
    }
    viewModel { StationPickerViewModel(get()) }
    viewModel { ItineraryViewModel(get(), get()) }
}
