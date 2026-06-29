package ro.trenuri.app.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ro.trenuri.app.data.InfoferTrainProvider
import ro.trenuri.app.data.TrainProvider
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.TrainViewModel
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.defaultInfoferClient
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

val ioDispatcherQualifier = named("io")

val appModule = module {
    single<InfoferClient> { defaultInfoferClient() }
    single<TrainProvider> { InfoferTrainProvider(get()) }
    single<CoroutineDispatcher>(ioDispatcherQualifier) { Dispatchers.IO }
    single { TrainRepository(get(), get(ioDispatcherQualifier)) }
    single<ErrorMessages> {
        object : ErrorMessages {
            override val network = "Verifică conexiunea la internet."
            override val parse = "Nu am putut citi răspunsul de la infofer."
        }
    }
    viewModel {
        TrainViewModel(
            repository = get(),
            today = {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                Triple(now.year, now.monthNumber, now.dayOfMonth)
            },
            messages = get(),
        )
    }
}
