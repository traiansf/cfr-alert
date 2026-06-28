package ro.trenuri.app.di

import org.koin.dsl.module
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.defaultInfoferClient

val appModule = module {
    single<InfoferClient> { defaultInfoferClient() }
}
