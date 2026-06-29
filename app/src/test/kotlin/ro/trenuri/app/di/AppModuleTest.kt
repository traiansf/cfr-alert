package ro.trenuri.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.test.verify.verify
import ro.trenuri.app.ui.common.Today
import ro.trenuri.infofer.net.InfoferSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class AppModuleTest {
    @Test
    fun appModule_dependency_graph_is_valid() {
        // Framework/factory-internal types not managed by Koin:
        // - InfoferSession: constructed inside defaultInfoferClient(), never injected by Koin.
        // - CoroutineDispatcher: TrainRepository's `io` param has a default of Dispatchers.IO;
        //   Koin's verify() doesn't understand Kotlin default parameter values.
        // - Context: consumed by history store bindings via androidContext(); provided by
        //   KoinAndroidContext at runtime (Application), listed here for static verify only.
        appModule.verify(extraTypes = listOf(
            InfoferSession::class,      // factory-internal; created by defaultInfoferClient()
            CoroutineDispatcher::class, // default param in TrainRepository; not Koin-injected
            Context::class,             // Android application context; supplied at runtime
        ))
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun namedTodayBinding_resolvesAndReturnsAppDate() {
        val koin = startKoin { modules(appModule) }.koin
        val today = koin.get<Today>(named("today"))
        assertNotNull(today(), "named(\"today\") provider must return a non-null AppDate")
    }
}
