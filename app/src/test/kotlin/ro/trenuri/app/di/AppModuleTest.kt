package ro.trenuri.app.di

import kotlinx.coroutines.CoroutineDispatcher
import org.koin.test.verify.verify
import ro.trenuri.infofer.net.InfoferSession
import kotlin.test.Test

class AppModuleTest {
    @Test
    fun appModule_dependency_graph_is_valid() {
        // Both are framework/factory-internal types, not Koin-managed:
        // - InfoferSession: constructed inside defaultInfoferClient(), never injected by Koin.
        // - CoroutineDispatcher: TrainRepository's `io` param has a default of Dispatchers.IO;
        //   Koin's verify() doesn't understand Kotlin default parameter values.
        appModule.verify(extraTypes = listOf(
            InfoferSession::class,      // factory-internal; created by defaultInfoferClient()
            CoroutineDispatcher::class, // default param in TrainRepository; not Koin-injected
            Function0::class,           // `today` lambda in viewModel block; not Koin-injected
        ))
    }
}
