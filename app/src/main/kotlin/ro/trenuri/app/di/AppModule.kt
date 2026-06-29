package ro.trenuri.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ro.trenuri.app.data.BoardProvider
import ro.trenuri.app.data.BoardRepository
import ro.trenuri.app.data.InfoferBoardProvider
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
import ro.trenuri.app.ui.board.BoardViewModel
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.Today
import ro.trenuri.app.ui.history.PrefsQueryHistoryStore
import ro.trenuri.app.ui.history.QueryHistoryStore
import ro.trenuri.app.ui.history.RouteQuery
import ro.trenuri.app.ui.history.StationQuery
import ro.trenuri.app.ui.history.TrainQuery
import ro.trenuri.app.ui.history.deserializeRouteQuery
import ro.trenuri.app.ui.history.deserializeStationQuery
import ro.trenuri.app.ui.history.deserializeTrainQuery
import ro.trenuri.app.ui.history.serializeRouteQuery
import ro.trenuri.app.ui.history.serializeStationQuery
import ro.trenuri.app.ui.history.serializeTrainQuery
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
    single<BoardProvider> { InfoferBoardProvider(get()) }
    single<CoroutineDispatcher>(ioDispatcherQualifier) { Dispatchers.IO }
    single { TrainRepository(get(), get(ioDispatcherQualifier)) }
    single { StationRepository(get(), get(ioDispatcherQualifier)) }
    single { ItineraryRepository(get(), get(ioDispatcherQualifier)) }
    single { BoardRepository(get(), get(ioDispatcherQualifier)) }
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
    viewModel { ItineraryViewModel(get(), get(), today = get(named("today"))) }
    viewModel { BoardViewModel(repository = get(), messages = get(), today = get(named("today"))) }

    // Per-tab query history — persisted via SharedPreferences
    single<QueryHistoryStore<TrainQuery>>(named("history_tren")) {
        val prefs = androidContext().getSharedPreferences("query_history", Context.MODE_PRIVATE)
        PrefsQueryHistoryStore(
            prefs = prefs,
            key = "tren",
            cap = 10,
            serialize = ::serializeTrainQuery,
            deserialize = ::deserializeTrainQuery,
        )
    }
    single<QueryHistoryStore<RouteQuery>>(named("history_rute")) {
        val prefs = androidContext().getSharedPreferences("query_history", Context.MODE_PRIVATE)
        PrefsQueryHistoryStore(
            prefs = prefs,
            key = "rute",
            cap = 10,
            serialize = ::serializeRouteQuery,
            deserialize = ::deserializeRouteQuery,
        )
    }
    single<QueryHistoryStore<StationQuery>>(named("history_statie")) {
        val prefs = androidContext().getSharedPreferences("query_history", Context.MODE_PRIVATE)
        PrefsQueryHistoryStore(
            prefs = prefs,
            key = "statie",
            cap = 10,
            serialize = ::serializeStationQuery,
            deserialize = ::deserializeStationQuery,
        )
    }
}
