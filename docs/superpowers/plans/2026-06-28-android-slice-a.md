# Android Slice A — Skeleton + Train Detail — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Android `:app` module consuming the `infofer-client` KMP library natively, delivering one flow: enter a train number → see its itinerary with the live delay.

**Architecture:** Add an `androidTarget()` to the existing `infofer-client` KMP module (OkHttp engine in `androidMain`). A new `:app` Android module (Jetpack Compose + Koin + MVVM) depends on it: `TrainDetailScreen` → `TrainViewModel` (`StateFlow<TrainUiState>`) → `TrainRepository` → `InfoferClient`. No app code touches Ktor or HTML.

**Tech Stack:** Kotlin 2.2.21, Kotlin Multiplatform, Android Gradle Plugin 8.7.3, Jetpack Compose (BOM), Koin 4.0, Ktor (OkHttp engine on Android / CIO on JVM), kotlinx-coroutines, JUnit4 + kotlinx-coroutines-test.

## Global Constraints

- `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`. (Installed SDK: android-35, build-tools 35.0.0.)
- The CLAUDE.md boundary is absolute: **no `:app` code imports Ktor, Ksoup, or references infofer HTML/endpoints.** All network goes through `InfoferClient`'s typed methods.
- The delay distinction is correctness-critical: `Delay? == null` means **no live data** and must NEVER be rendered as "on time". `Delay(0, …)` is on-time; `Delay(>0, …)` is delayed.
- KMP `commonMain`/`jvmMain` of `infofer-client` and its existing 27 JVM tests must remain unchanged and green.
- No emulator/instrumented tests (container has no `/dev/kvm`). Unit tests run on the JVM via `testDebugUnitTest`; device behaviour is verified manually.
- Build/test via the repo wrapper: `./gradlew` (Gradle 8.13).
- UI chrome strings are Romanian; Romanian data terms are shown as-is.
- Commit after each task.

---

### Task 1: Make `infofer-client` build for Android

Add the Android target to the shared client and an OkHttp-based factory, without touching common/JVM code. Deliverable: the client compiles for Android **and** the existing JVM tests still pass.

**Files:**
- Modify: `settings.gradle.kts` (add `google()` repo to both repository blocks)
- Modify: `gradle/libs.versions.toml` (add `agp` version, `android-library` plugin, `ktor-client-okhttp` library)
- Modify: `build.gradle.kts` (register `android-library` plugin `apply false`)
- Modify: `infofer-client/build.gradle.kts` (apply android library plugin, add `androidTarget()`, `android {}` block, `androidMain` deps)
- Create: `infofer-client/src/androidMain/kotlin/ro/trenuri/infofer/InfoferClientFactory.kt`

**Interfaces:**
- Consumes: existing `InfoferClient(InfoferSession(HttpClient))` from `commonMain`.
- Produces: `ro.trenuri.infofer.defaultInfoferClient(): InfoferClient` in `androidMain` (same signature as the `jvmMain` one), so `:app` can call it on Android.

- [ ] **Step 1: Add `google()` to settings repositories**

Edit `settings.gradle.kts` so both blocks include Google's Maven (required for AGP + AndroidX):

```kotlin
rootProject.name = "trenuri"

pluginManagement {
    repositories { google(); gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

include(":infofer-client")
```

- [ ] **Step 2: Add versions/plugin/library to the catalog**

Edit `gradle/libs.versions.toml`. Under `[versions]` add:

```toml
agp = "8.7.3"
```

Under `[libraries]` add:

```toml
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
```

Under `[plugins]` add:

```toml
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 3: Register the android-library plugin at the root**

Edit `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
}
```

- [ ] **Step 4: Add the Android target to the client module**

Replace `infofer-client/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    jvm {
        // JVM is the target we build & test in this environment.
    }
    androidTarget {
        // Native target consumed by the Android app.
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ksoup)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}

android {
    namespace = "ro.trenuri.infofer"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
}
```

- [ ] **Step 5: Create the Android factory**

Create `infofer-client/src/androidMain/kotlin/ro/trenuri/infofer/InfoferClientFactory.kt`:

```kotlin
package ro.trenuri.infofer

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.HttpCookies
import ro.trenuri.infofer.net.InfoferSession

fun defaultInfoferClient(): InfoferClient {
    val http = HttpClient(OkHttp) { install(HttpCookies) }
    return InfoferClient(InfoferSession(http))
}
```

- [ ] **Step 6: Verify the Android target compiles**

Run: `./gradlew :infofer-client:compileReleaseKotlinAndroid`
Expected: BUILD SUCCESSFUL (downloads AGP/AndroidX on first run).

- [ ] **Step 7: Verify existing JVM tests still pass**

Run: `./gradlew :infofer-client:jvmTest`
Expected: BUILD SUCCESSFUL, 27 tests, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml infofer-client/build.gradle.kts infofer-client/src/androidMain
git commit -m "feat(infofer-client): add Android target with OkHttp factory"
```

---

### Task 2: Scaffold the `:app` Android application module

Create a minimal, buildable Android app that starts Koin and shows an empty Compose screen. Deliverable: `:app:assembleDebug` produces an APK.

**Files:**
- Modify: `settings.gradle.kts` (`include(":app")`)
- Modify: `gradle/libs.versions.toml` (Compose BOM, Koin, activity/lifecycle, core-ktx, coroutines-android, plugins)
- Modify: `build.gradle.kts` (register `android-application`, `kotlin-android`, `kotlin-compose` plugins `apply false`)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/ro/trenuri/app/TrenuriApp.kt` (Application + Koin start)
- Create: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt` (Koin module — `defaultInfoferClient`)
- Create: `app/src/main/kotlin/ro/trenuri/app/MainActivity.kt` (placeholder Compose content)

**Interfaces:**
- Consumes: `ro.trenuri.infofer.defaultInfoferClient()` (Task 1).
- Produces: a Koin `appModule` providing `single { defaultInfoferClient() }`; `TrenuriApp` starting Koin; an `:app` module other tasks add to. Later tasks extend `appModule` with the repository and viewmodel.

- [ ] **Step 1: Register `:app` in settings**

Edit `settings.gradle.kts`, last line:

```kotlin
include(":infofer-client")
include(":app")
```

- [ ] **Step 2: Add app dependencies to the catalog**

Edit `gradle/libs.versions.toml`. Under `[versions]` add:

```toml
composeBom = "2024.12.01"
koin = "4.0.0"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
coreKtx = "1.13.1"
```

Under `[libraries]` add:

```toml
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-androidx-compose = { module = "io.insert-koin:koin-androidx-compose", version.ref = "koin" }
junit = { module = "junit:junit", version = "4.13.2" }
```

Under `[plugins]` add:

```toml
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 3: Register the new plugins at the root**

Edit `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 4: Create the app module Gradle build**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ro.trenuri.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ro.trenuri.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
}

dependencies {
    implementation(project(":infofer-client"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 5: Create the manifest**

Create `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".TrenuriApp"
        android:label="Trenuri"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 6: Create the Koin app module**

Create `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt`:

```kotlin
package ro.trenuri.app.di

import org.koin.dsl.module
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.defaultInfoferClient

val appModule = module {
    single<InfoferClient> { defaultInfoferClient() }
}
```

- [ ] **Step 7: Create the Application class that starts Koin**

Create `app/src/main/kotlin/ro/trenuri/app/TrenuriApp.kt`:

```kotlin
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
```

- [ ] **Step 8: Create a placeholder MainActivity**

Create `app/src/main/kotlin/ro/trenuri/app/MainActivity.kt`:

```kotlin
package ro.trenuri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Trenuri")
                }
            }
        }
    }
}
```

- [ ] **Step 9: Verify the app builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; an APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app
git commit -m "feat(app): scaffold Android app module with Koin + Compose"
```

---

### Task 3: `TrainRepository` — fetch + map outcomes

Wrap the client behind a testable seam and map success/empty/exceptions to a result type, off the main thread. Pure JVM unit tests, no network.

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/data/TrainProvider.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/TrainResult.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/TrainRepository.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/InfoferTrainProvider.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt` (provide provider + repository)
- Test: `app/src/test/kotlin/ro/trenuri/app/data/TrainRepositoryTest.kt`

**Interfaces:**
- Consumes: `InfoferClient.getTrain(trainNumber: String, year: Int, month: Int, day: Int): TrainItinerary`; exceptions `InfoferNetworkException`, `InfoferParseException` from `ro.trenuri.infofer`.
- Produces:
  - `fun interface TrainProvider { suspend fun getTrain(number: String, year: Int, month: Int, day: Int): TrainItinerary }`
  - `sealed interface TrainResult { data class Success(val itinerary: TrainItinerary); object NotFound; object NetworkError; object ParseError }`
  - `class TrainRepository(provider: TrainProvider, io: CoroutineDispatcher = Dispatchers.IO)` with `suspend fun load(number: String, year: Int, month: Int, day: Int): TrainResult`.
  - `class InfoferTrainProvider(client: InfoferClient) : TrainProvider`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/ro/trenuri/app/data/TrainRepositoryTest.kt`:

```kotlin
package ro.trenuri.app.data

import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.TrainCategory
import ro.trenuri.infofer.model.TrainItinerary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun itinerary(branches: List<ro.trenuri.infofer.model.TrainBranch>) =
    TrainItinerary(trainNumber = "5568", category = TrainCategory.R, branches = branches)

class TrainRepositoryTest {

    @Test
    fun returns_success_when_provider_returns_branches() = runTest {
        val branch = ro.trenuri.infofer.model.TrainBranch("A", "B", delay = null, stops = emptyList())
        val repo = TrainRepository({ _, _, _, _ -> itinerary(listOf(branch)) })
        val result = repo.load("5568", 2026, 6, 28)
        assertTrue(result is TrainResult.Success)
        assertEquals("5568", (result as TrainResult.Success).itinerary.trainNumber)
    }

    @Test
    fun returns_not_found_when_no_branches() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> itinerary(emptyList()) })
        assertEquals(TrainResult.NotFound, repo.load("0000", 2026, 6, 28))
    }

    @Test
    fun returns_network_error_on_network_exception() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> throw InfoferNetworkException("boom") })
        assertEquals(TrainResult.NetworkError, repo.load("5568", 2026, 6, 28))
    }

    @Test
    fun returns_parse_error_on_parse_exception() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> throw InfoferParseException("bad html") })
        assertEquals(TrainResult.ParseError, repo.load("5568", 2026, 6, 28))
    }
}
```

> The exception constructors are `InfoferNetworkException(message)` and `InfoferParseException(message)` (verified in `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/InfoferException.kt`).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.data.TrainRepositoryTest"`
Expected: FAIL — `TrainProvider` / `TrainResult` / `TrainRepository` unresolved.

- [ ] **Step 3: Create `TrainProvider`**

Create `app/src/main/kotlin/ro/trenuri/app/data/TrainProvider.kt`:

```kotlin
package ro.trenuri.app.data

import ro.trenuri.infofer.model.TrainItinerary

/** Test seam over InfoferClient.getTrain so the app layer needs no network in tests. */
fun interface TrainProvider {
    suspend fun getTrain(number: String, year: Int, month: Int, day: Int): TrainItinerary
}
```

- [ ] **Step 4: Create `TrainResult`**

Create `app/src/main/kotlin/ro/trenuri/app/data/TrainResult.kt`:

```kotlin
package ro.trenuri.app.data

import ro.trenuri.infofer.model.TrainItinerary

sealed interface TrainResult {
    data class Success(val itinerary: TrainItinerary) : TrainResult
    data object NotFound : TrainResult
    data object NetworkError : TrainResult
    data object ParseError : TrainResult
}
```

- [ ] **Step 5: Create `TrainRepository`**

Create `app/src/main/kotlin/ro/trenuri/app/data/TrainRepository.kt`:

```kotlin
package ro.trenuri.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException

class TrainRepository(
    private val provider: TrainProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(number: String, year: Int, month: Int, day: Int): TrainResult =
        withContext(io) {
            try {
                val itinerary = provider.getTrain(number, year, month, day)
                if (itinerary.branches.isEmpty()) TrainResult.NotFound
                else TrainResult.Success(itinerary)
            } catch (e: InfoferNetworkException) {
                TrainResult.NetworkError
            } catch (e: InfoferParseException) {
                TrainResult.ParseError
            }
        }
}
```

> `CancellationException` is a subclass of neither caught type, so it propagates — in-flight loads cancel cleanly with the ViewModel scope.

- [ ] **Step 6: Create the real provider**

Create `app/src/main/kotlin/ro/trenuri/app/data/InfoferTrainProvider.kt`:

```kotlin
package ro.trenuri.app.data

import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.model.TrainItinerary

class InfoferTrainProvider(private val client: InfoferClient) : TrainProvider {
    override suspend fun getTrain(number: String, year: Int, month: Int, day: Int): TrainItinerary =
        client.getTrain(number, year, month, day)
}
```

- [ ] **Step 7: Wire provider + repository into Koin**

Replace `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt`:

```kotlin
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
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.data.TrainRepositoryTest"`
Expected: PASS (4 tests).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/data app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt app/src/test/kotlin/ro/trenuri/app/data
git commit -m "feat(app): TrainRepository mapping client outcomes to TrainResult"
```

---

### Task 4: `DelayBanner` — the pure delay-state mapper

Extract the `null` / on-time / delayed decision into a pure, headlessly-tested function so the headline correctness is verified without Compose.

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/DelayBanner.kt`
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/DelayBannerTest.kt`

**Interfaces:**
- Consumes: `ro.trenuri.infofer.model.Delay(minutes: Int, reportedAt: String?)`.
- Produces:
  - `sealed interface DelayBanner { object NoLiveData; object OnTime; data class Delayed(val minutes: Int, val reportedAt: String?) }`
  - `fun delayBannerOf(delay: Delay?): DelayBanner`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/ro/trenuri/app/ui/DelayBannerTest.kt`:

```kotlin
package ro.trenuri.app.ui

import ro.trenuri.infofer.model.Delay
import kotlin.test.Test
import kotlin.test.assertEquals

class DelayBannerTest {

    @Test
    fun null_delay_is_no_live_data_not_on_time() {
        assertEquals(DelayBanner.NoLiveData, delayBannerOf(null))
    }

    @Test
    fun zero_minutes_is_on_time() {
        assertEquals(DelayBanner.OnTime, delayBannerOf(Delay(0, "18:46")))
    }

    @Test
    fun positive_minutes_is_delayed_with_details() {
        assertEquals(DelayBanner.Delayed(7, "18:46"), delayBannerOf(Delay(7, "18:46")))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.DelayBannerTest"`
Expected: FAIL — `DelayBanner` / `delayBannerOf` unresolved.

- [ ] **Step 3: Implement the mapper**

Create `app/src/main/kotlin/ro/trenuri/app/ui/DelayBanner.kt`:

```kotlin
package ro.trenuri.app.ui

import ro.trenuri.infofer.model.Delay

sealed interface DelayBanner {
    /** No live data — train not currently running. NEVER shown as on-time. */
    data object NoLiveData : DelayBanner
    data object OnTime : DelayBanner
    data class Delayed(val minutes: Int, val reportedAt: String?) : DelayBanner
}

fun delayBannerOf(delay: Delay?): DelayBanner = when {
    delay == null -> DelayBanner.NoLiveData
    delay.minutes <= 0 -> DelayBanner.OnTime
    else -> DelayBanner.Delayed(delay.minutes, delay.reportedAt)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.DelayBannerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/ui/DelayBanner.kt app/src/test/kotlin/ro/trenuri/app/ui/DelayBannerTest.kt
git commit -m "feat(app): pure DelayBanner mapper (null vs on-time vs delayed)"
```

---

### Task 5: `TrainViewModel` — UI state machine

Drive `StateFlow<TrainUiState>` from the repository, off the main thread, with state transitions tested headlessly.

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/TrainUiState.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/TrainViewModel.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt` (provide the viewmodel)
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/TrainViewModelTest.kt`

**Interfaces:**
- Consumes: `TrainRepository.load(number, year, month, day): TrainResult`; `TrainResult` variants.
- Produces:
  - `sealed interface TrainUiState { object Idle; object Loading; data class Success(val itinerary: TrainItinerary); object Empty; data class Error(val message: String) }`
  - `class TrainViewModel(repository: TrainRepository, today: () -> Triple<Int,Int,Int>, messages: ErrorMessages)` exposing `val state: StateFlow<TrainUiState>` and `fun search(number: String)`.
  - `interface ErrorMessages { val network: String; val parse: String }` (so messages are injected, not hard-coded in the VM — keeps the VM string-free and testable).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/ro/trenuri/app/ui/TrainViewModelTest.kt`:

```kotlin
package ro.trenuri.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.app.data.TrainResult
import ro.trenuri.infofer.model.TrainBranch
import ro.trenuri.infofer.model.TrainCategory
import ro.trenuri.infofer.model.TrainItinerary
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val testMessages = object : ErrorMessages {
    override val network = "net"
    override val parse = "parse"
}
private val fixedClock = { Triple(2026, 6, 28) }

private fun repoReturning(result: TrainResult) =
    TrainRepository({ _, _, _, _ ->
        when (result) {
            is TrainResult.Success -> result.itinerary
            TrainResult.NotFound -> TrainItinerary("0", TrainCategory.R, emptyList())
            else -> throw IllegalStateException("unused")
        }
    }, Dispatchers.Unconfined)

class TrainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun blank_input_is_ignored() = runTest {
        val vm = TrainViewModel(repoReturning(TrainResult.NotFound), fixedClock, testMessages)
        vm.search("   ")
        advanceUntilIdle()
        assertEquals(TrainUiState.Idle, vm.state.value)
    }

    @Test
    fun success_sets_success_state() = runTest {
        val branch = TrainBranch("A", "B", delay = null, stops = emptyList())
        val itinerary = TrainItinerary("5568", TrainCategory.R, listOf(branch))
        val vm = TrainViewModel(repoReturning(TrainResult.Success(itinerary)), fixedClock, testMessages)
        vm.search("5568")
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state is TrainUiState.Success)
        assertEquals("5568", (state as TrainUiState.Success).itinerary.trainNumber)
    }

    @Test
    fun not_found_sets_empty_state() = runTest {
        val vm = TrainViewModel(repoReturning(TrainResult.NotFound), fixedClock, testMessages)
        vm.search("0000")
        advanceUntilIdle()
        assertEquals(TrainUiState.Empty, vm.state.value)
    }

    @Test
    fun network_error_sets_error_state_with_injected_message() = runTest {
        val repo = TrainRepository({ _, _, _, _ -> throw ro.trenuri.infofer.InfoferNetworkException("x") }, Dispatchers.Unconfined)
        val vm = TrainViewModel(repo, fixedClock, testMessages)
        vm.search("5568")
        advanceUntilIdle()
        assertEquals(TrainUiState.Error("net"), vm.state.value)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.TrainViewModelTest"`
Expected: FAIL — `TrainUiState` / `TrainViewModel` / `ErrorMessages` unresolved.

- [ ] **Step 3: Create the UI state + error messages types**

Create `app/src/main/kotlin/ro/trenuri/app/ui/TrainUiState.kt`:

```kotlin
package ro.trenuri.app.ui

import ro.trenuri.infofer.model.TrainItinerary

sealed interface TrainUiState {
    data object Idle : TrainUiState
    data object Loading : TrainUiState
    data class Success(val itinerary: TrainItinerary) : TrainUiState
    data object Empty : TrainUiState
    data class Error(val message: String) : TrainUiState
}

/** Injected user-facing error strings, so the ViewModel holds no hard-coded copy. */
interface ErrorMessages {
    val network: String
    val parse: String
}
```

- [ ] **Step 4: Create the ViewModel**

Create `app/src/main/kotlin/ro/trenuri/app/ui/TrainViewModel.kt`:

```kotlin
package ro.trenuri.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.trenuri.app.data.TrainRepository
import ro.trenuri.app.data.TrainResult

class TrainViewModel(
    private val repository: TrainRepository,
    private val today: () -> Triple<Int, Int, Int>,
    private val messages: ErrorMessages,
) : ViewModel() {

    private val _state = MutableStateFlow<TrainUiState>(TrainUiState.Idle)
    val state: StateFlow<TrainUiState> = _state.asStateFlow()

    fun search(number: String) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        val (y, m, d) = today()
        _state.value = TrainUiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.load(trimmed, y, m, d)) {
                is TrainResult.Success -> TrainUiState.Success(result.itinerary)
                TrainResult.NotFound -> TrainUiState.Empty
                TrainResult.NetworkError -> TrainUiState.Error(messages.network)
                TrainResult.ParseError -> TrainUiState.Error(messages.parse)
            }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.TrainViewModelTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Wire the ViewModel into Koin**

Edit `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt` — add imports and a `viewModel` definition. The full file becomes:

```kotlin
package ro.trenuri.app.di

import org.koin.androidx.viewmodel.dsl.viewModel
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

val appModule = module {
    single<InfoferClient> { defaultInfoferClient() }
    single<TrainProvider> { InfoferTrainProvider(get()) }
    single { TrainRepository(get()) }
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
```

> `kotlinx-datetime` is already a transitive dependency via `:infofer-client`; if the import does not resolve in `:app`, add `implementation(libs.kotlinx.datetime)` to `app/build.gradle.kts` dependencies.

- [ ] **Step 7: Verify the module still compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/ui/TrainUiState.kt app/src/main/kotlin/ro/trenuri/app/ui/TrainViewModel.kt app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt app/src/test/kotlin/ro/trenuri/app/ui/TrainViewModelTest.kt
git commit -m "feat(app): TrainViewModel state machine with injected error copy"
```

---

### Task 6: `TrainDetailScreen` Compose UI + wire MainActivity

Render the states and the delay banner, and host the screen. No headless UI test (no emulator); verified by build + manual device check.

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/TrainDetailScreen.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/MainActivity.kt`

**Interfaces:**
- Consumes: `TrainViewModel.state: StateFlow<TrainUiState>`, `TrainViewModel.search(String)`, `delayBannerOf(Delay?)`, `DelayBanner` variants; models `TrainItinerary`, `TrainBranch`, `TrainStop`.
- Produces: `@Composable fun TrainDetailScreen(viewModel: TrainViewModel)`; `MainActivity` hosting it via Koin.

- [ ] **Step 1: Create the screen**

Create `app/src/main/kotlin/ro/trenuri/app/ui/TrainDetailScreen.kt`:

```kotlin
package ro.trenuri.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ro.trenuri.infofer.model.TrainBranch
import ro.trenuri.infofer.model.TrainItinerary
import ro.trenuri.infofer.model.TrainStop

private val Green = Color(0xFF1B5E20)
private val Red = Color(0xFFB71C1C)

@Composable
fun TrainDetailScreen(viewModel: TrainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Număr tren") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.search(number) }) { Text("Caută") }
        }

        when (val s = state) {
            TrainUiState.Idle -> Text("Introdu un număr de tren.")
            TrainUiState.Loading -> CircularProgressIndicator()
            TrainUiState.Empty -> Text("Trenul nu a fost găsit sau nu are date.")
            is TrainUiState.Error -> Text(s.message, color = Red)
            is TrainUiState.Success -> TrainItineraryView(s.itinerary)
        }
    }
}

@Composable
private fun TrainItineraryView(itinerary: TrainItinerary) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "${itinerary.category} ${itinerary.trainNumber}",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        itinerary.branches.forEach { branch ->
            item { BranchHeader(branch) }
            items(branch.stops) { stop -> StopRow(stop) }
        }
    }
}

@Composable
private fun BranchHeader(branch: TrainBranch) {
    Column {
        Text("${branch.from} – ${branch.to}", fontWeight = FontWeight.SemiBold)
        when (val banner = delayBannerOf(branch.delay)) {
            DelayBanner.NoLiveData -> Text("Fără date live")
            DelayBanner.OnTime -> Text("La timp", color = Green)
            is DelayBanner.Delayed -> {
                val reported = banner.reportedAt?.let { " · raportat la $it" } ?: ""
                Text("${banner.minutes} min întârziere$reported", color = Red)
            }
        }
    }
}

@Composable
private fun StopRow(stop: TrainStop) {
    val time = stop.arrival ?: stop.departure ?: ""
    val track = stop.track?.let { "  linia $it" } ?: ""
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(time)
        Text(stop.station.name + track, modifier = Modifier.weight(1f))
    }
}
```

> `TrainStop(station: Station, km: Int?, track: String?, arrival: String?, departure: String?, status: StopStatus)` and `Station(name, slug)` — field names verified against `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/model/Models.kt`.

- [ ] **Step 2: Host the screen in MainActivity**

Replace `app/src/main/kotlin/ro/trenuri/app/MainActivity.kt`:

```kotlin
package ro.trenuri.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import org.koin.androidx.compose.koinViewModel
import ro.trenuri.app.ui.TrainDetailScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    TrainDetailScreen(viewModel = koinViewModel())
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Run the full app unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — `TrainRepositoryTest` (4) + `DelayBannerTest` (3) + `TrainViewModelTest` (4) all pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/ui/TrainDetailScreen.kt app/src/main/kotlin/ro/trenuri/app/MainActivity.kt
git commit -m "feat(app): TrainDetailScreen rendering states + delay banner"
```

- [ ] **Step 6: Manual acceptance on a physical phone** (cannot run in-container)

1. Transfer `app/build/outputs/apk/debug/app-debug.apk` to an Android phone (or `adb install` from a machine with the phone attached).
2. Open the app, enter a **currently-running** train number, tap "Caută".
3. Confirm: stops render; a delayed train shows red "N min întârziere · raportat la HH:MM"; an on-time running train shows green "La timp".
4. Enter a non-running train → confirm "Fără date live" (NOT "La timp").
5. Enter a nonsense number → "Trenul nu a fost găsit sau nu are date."
6. Enable airplane mode, search → "Verifică conexiunea la internet."

---

## Final verification (whole slice)

- [ ] `./gradlew :infofer-client:jvmTest` — existing 27 client tests green.
- [ ] `./gradlew :app:testDebugUnitTest` — 11 app unit tests green.
- [ ] `./gradlew :app:assembleDebug` — APK built.
- [ ] Manual device acceptance (Task 6, Step 6) performed and noted.

## Notes on versions

If first-run dependency resolution fails on a pinned version (AGP 8.7.3, Compose BOM 2024.12.01, Koin 4.0.0, lifecycle 2.8.7, activity-compose 1.9.3, core-ktx 1.13.1), bump to the nearest version that resolves against `google()` + `mavenCentral()` and keep the rest of the code unchanged — these are infrastructure pins, not behavioural choices. Record any change in the commit message.
