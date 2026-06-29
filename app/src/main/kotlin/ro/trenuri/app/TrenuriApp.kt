package ro.trenuri.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ro.trenuri.app.di.appModule

class TrenuriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TrenuriApp)
            modules(appModule)
        }
    }
}
