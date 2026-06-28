package ro.trenuri.app.di

import org.koin.dsl.module
import ro.trenuri.app.data.InfoferTrainProvider
import ro.trenuri.app.data.TrainProvider
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.defaultInfoferClient

val appModule = module {
    single<InfoferClient> { defaultInfoferClient() }
    single<TrainProvider> { InfoferTrainProvider(get()) }
    single { TrainRepository(get()) }
}
