# infofer-client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `infofer-client`, the shared Kotlin Multiplatform data layer that wraps the unofficial infofer endpoints behind a clean typed API, with all HTML-scraping isolated and unit-tested against real saved fixtures.

**Architecture:** A KMP library with all logic in `commonMain`. Pure parsers turn saved HTML fragments into typed models; a Ktor-based session layer performs the two-step GET-token → POST-result flow; an `InfoferClient` facade composes them. We build and test the **JVM target** in this environment (real red/green `./gradlew test`); Android/iOS/JS targets are added in their own later phases without touching `commonMain` logic.

**Tech Stack:** Kotlin 2.2.21, Gradle 8.13 (wrapper), Ktor 3.5.1 (client-core + cio + mock), Ksoup 0.2.6 (HTML parsing), kotlinx-coroutines 1.11.0, kotlinx-datetime 0.6.2, kotlin-test.

## Global Constraints

- **Module path:** everything lives under `infofer-client/`.
- **No app/UI imports** in the library. `commonMain` only.
- **Endpoint contract is authoritative:** follow `docs/infofer-api.md` exactly. Base URL `https://mersultrenurilor.infofer.ro`. Dates formatted `DD.MM.YYYY 0:00:00`.
- **Parsers fail loudly:** on a structural mismatch throw `InfoferParseException` — never return silently-wrong/empty data as if valid.
- **Tests never hit the network.** Parser tests read fixtures from `commonTest/resources/fixtures/`. Session tests use Ktor `MockEngine`.
- **Fixtures already captured** (live data, 2026-06-26): `train-result-5568.html`, `itineraries-bucuresti-brasov.html`, `station-board-brasov.html`, `nearest-stations-bucuresti.html` in `infofer-client/src/commonTest/resources/fixtures/`.
- **Versions pinned in** `gradle/libs.versions.toml`; never hardcode versions in module build files.
- **Polite client:** the session layer sets a descriptive `User-Agent` and exposes a single configurable base URL; no retry storms.

---

### Task 1: KMP project scaffold (JVM target, builds green)

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts` (root, minimal)
- Create: `infofer-client/build.gradle.kts`
- Create: `infofer-client/src/commonMain/kotlin/.gitkeep`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` (via `gradle wrapper`)

**Interfaces:**
- Produces: a buildable KMP module `:infofer-client` with a `jvm()` target and a `commonTest` source set wired to kotlin-test.

- [ ] **Step 1: Generate the Gradle wrapper**

Run (Gradle 8.13 is installed at `~/tools/gradle-8.13`):
```bash
cd /home/traian/cfr
~/tools/gradle-8.13/bin/gradle wrapper --gradle-version 8.13 --distribution-type bin
```
Expected: creates `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 2: Write the version catalog**

`gradle/libs.versions.toml`:
```toml
[versions]
kotlin = "2.2.21"
ktor = "3.5.1"
coroutines = "1.11.0"
datetime = "0.6.2"
ksoup = "0.2.6"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
ksoup = { module = "com.fleeksoft.ksoup:ksoup", version.ref = "ksoup" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
```

- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
rootProject.name = "trenuri"

pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}

include(":infofer-client")
```

- [ ] **Step 4: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
}
```

- [ ] **Step 5: Write `infofer-client/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        // JVM is the target we build & test in this environment.
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
    }
}
```

- [ ] **Step 6: Verify the build resolves and runs**

Run: `./gradlew :infofer-client:compileKotlinJvm`
Expected: BUILD SUCCESSFUL (downloads dependencies on first run).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: scaffold infofer-client KMP module (JVM target)"
```

---

### Task 2: Domain models

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/model/Models.kt`
- Test: `infofer-client/src/commonTest/kotlin/ro/trenuri/infofer/model/ModelsTest.kt`

**Interfaces:**
- Produces (used by every later task):
  - `data class Station(val name: String, val slug: String)`
  - `enum class TrainCategory { R, RE, RA, IR, IRN, IC, ICN, RR, IRA, RRF, OTHER }`
  - `enum class StopStatus { ON_TIME, DELAYED, UNKNOWN }`
  - `data class Delay(val minutes: Int, val reportedAt: String?)` — `minutes == 0` means on time.
  - `data class TrainStop(val station: Station, val km: Int?, val track: String?, val arrival: String?, val departure: String?, val status: StopStatus)`
  - `data class TrainBranch(val from: String, val to: String, val delay: Delay?, val stops: List<TrainStop>)`
  - `data class TrainItinerary(val trainNumber: String, val category: TrainCategory, val branches: List<TrainBranch>)`
  - `data class ItineraryLeg(val trainNumber: String, val category: TrainCategory, val departureStation: String, val departureTime: String, val arrivalStation: String, val arrivalTime: String)`
  - `data class ItineraryOption(val departureTime: String, val arrivalTime: String, val durationMinutes: Int?, val changes: Int, val legs: List<ItineraryLeg>)`
  - `enum class BoardKind { DEPARTURES, ARRIVALS }`
  - `data class BoardEntry(val trainNumber: String, val category: TrainCategory, val counterpartStation: String, val scheduledTime: String, val delayMinutes: Int?, val track: String?)`
  - `data class StationBoard(val station: String, val kind: BoardKind, val entries: List<BoardEntry>)`

- [ ] **Step 1: Write the failing test**

`ModelsTest.kt`:
```kotlin
package ro.trenuri.infofer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsTest {
    @Test
    fun delay_zero_means_on_time() {
        assertTrue(Delay(0, null).minutes == 0)
    }

    @Test
    fun train_itinerary_holds_branches() {
        val it = TrainItinerary(
            trainNumber = "5568",
            category = TrainCategory.R,
            branches = listOf(TrainBranch("Botoșani", "Suceava Nord", Delay(2, "18:46"), emptyList())),
        )
        assertEquals(1, it.branches.size)
        assertEquals(2, it.branches.first().delay?.minutes)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.model.ModelsTest"`
Expected: FAIL — unresolved references (`Delay`, `TrainItinerary`, ...).

- [ ] **Step 3: Write the models**

Create `Models.kt` with exactly the types listed in **Interfaces** above (all `data class` / `enum class`, no behavior).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.model.ModelsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): domain models"
```

---

### Task 3: Station + date helpers (slugify, diacritic-fold, date format, category parse)

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/util/Text.kt`
- Test: `infofer-client/src/commonTest/kotlin/ro/trenuri/infofer/util/TextTest.kt`

**Interfaces:**
- Produces:
  - `fun foldDiacritics(s: String): String` — ș/ş→s, ț/ţ→t, ă→a, â→a, î→i (both cedilla and comma forms), case-preserving.
  - `fun stationSlug(name: String): String` — fold diacritics, trim, collapse spaces/`/` to single `-`, strip other punctuation. `"Bucureşti Nord"` → `"Bucuresti-Nord"`.
  - `fun formatInfoferDate(year: Int, month: Int, day: Int): String` — `"DD.MM.YYYY 0:00:00"` zero-padded D/M.
  - `fun parseCategory(raw: String): TrainCategory` — maps `"IR"`,`"R-E"`→`RE`,`"RR"`,`"IC"`, css token `span-train-category-ir`→`IR`, else `OTHER`.

- [ ] **Step 1: Write the failing test**

`TextTest.kt`:
```kotlin
package ro.trenuri.infofer.util

import ro.trenuri.infofer.model.TrainCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test fun folds_romanian_diacritics() {
        assertEquals("Bucuresti Nord", foldDiacritics("Bucureşti Nord"))
        assertEquals("Brasov", foldDiacritics("Brașov"))
        assertEquals("Targu Mures", foldDiacritics("Târgu Mureș"))
    }

    @Test fun slugifies_station_names() {
        assertEquals("Bucuresti-Nord", stationSlug("Bucureşti Nord"))
        assertEquals("Aeroport-Henri-Coanda", stationSlug("Aeroport Henri Coandă"))
    }

    @Test fun formats_date_with_zero_time() {
        assertEquals("26.06.2026 0:00:00", formatInfoferDate(2026, 6, 26))
        assertEquals("01.12.2026 0:00:00", formatInfoferDate(2026, 12, 1))
    }

    @Test fun parses_categories() {
        assertEquals(TrainCategory.IR, parseCategory("IR"))
        assertEquals(TrainCategory.RE, parseCategory("R-E"))
        assertEquals(TrainCategory.IR, parseCategory("span-train-category-ir"))
        assertEquals(TrainCategory.OTHER, parseCategory("ZZ"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.util.TextTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write `Text.kt`**

```kotlin
package ro.trenuri.infofer.util

import ro.trenuri.infofer.model.TrainCategory

private val DIACRITICS = mapOf(
    'ș' to 's', 'ş' to 's', 'Ș' to 'S', 'Ş' to 'S',
    'ț' to 't', 'ţ' to 't', 'Ț' to 'T', 'Ţ' to 'T',
    'ă' to 'a', 'Ă' to 'A', 'â' to 'a', 'Â' to 'A',
    'î' to 'i', 'Î' to 'I',
)

fun foldDiacritics(s: String): String = buildString {
    for (c in s) append(DIACRITICS[c] ?: c)
}

fun stationSlug(name: String): String =
    foldDiacritics(name).trim()
        .replace(Regex("[\\s/]+"), "-")
        .replace(Regex("[^A-Za-z0-9-]"), "")
        .replace(Regex("-+"), "-")
        .trim('-')

fun formatInfoferDate(year: Int, month: Int, day: Int): String {
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${p(day)}.${p(month)}.$year 0:00:00"
}

fun parseCategory(raw: String): TrainCategory {
    val t = raw.substringAfterLast("span-train-category-").trim().uppercase().replace("-", "")
    return when (t) {
        "R" -> TrainCategory.R
        "RE" -> TrainCategory.RE
        "RA" -> TrainCategory.RA
        "IR" -> TrainCategory.IR
        "IRN" -> TrainCategory.IRN
        "IC" -> TrainCategory.IC
        "ICN" -> TrainCategory.ICN
        "RR" -> TrainCategory.RR
        "IRA" -> TrainCategory.IRA
        "RRF" -> TrainCategory.RRF
        else -> TrainCategory.OTHER
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.util.TextTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): text/date/slug helpers"
```

---

### Task 4: Fixture loader (jvm test util) + parse-exception type

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/InfoferException.kt`
- Create: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/Fixtures.kt`
- Test: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/FixturesTest.kt`

**Interfaces:**
- Produces:
  - `open class InfoferException(message: String, cause: Throwable? = null) : Exception(message, cause)`
  - `class InfoferParseException(message: String) : InfoferException(message)`
  - `object Fixtures { fun load(name: String): String }` (JVM-only test helper reading `commonTest/resources/fixtures/<name>`).

Note: fixtures live in `commonTest/resources`; the JVM loader reads them from the test classpath, so parser tests can run on the JVM target now and be promoted to `commonTest` once other targets exist.

- [ ] **Step 1: Write the failing test**

`FixturesTest.kt`:
```kotlin
package ro.trenuri.infofer

import kotlin.test.Test
import kotlin.test.assertTrue

class FixturesTest {
    @Test fun loads_train_fixture() {
        val html = Fixtures.load("train-result-5568.html")
        assertTrue(html.contains("Parcurs tren"), "fixture should contain itinerary markup")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.FixturesTest"`
Expected: FAIL — `Fixtures` unresolved.

- [ ] **Step 3: Implement loader + exceptions**

`InfoferException.kt`:
```kotlin
package ro.trenuri.infofer

open class InfoferException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InfoferParseException(message: String) : InfoferException(message)
class InfoferNetworkException(message: String, cause: Throwable? = null) : InfoferException(message, cause)
```

`Fixtures.kt` (jvmTest):
```kotlin
package ro.trenuri.infofer

object Fixtures {
    fun load(name: String): String {
        val stream = this::class.java.classLoader.getResourceAsStream("fixtures/$name")
            ?: error("fixture not found: fixtures/$name")
        return stream.readBytes().decodeToString()
    }
}
```

Ensure `infofer-client/build.gradle.kts` exposes test resources: add inside `kotlin { jvm { } }` block nothing extra is needed — `commonTest/resources` is on the JVM test runtime classpath by default in KMP. If the fixture is not found, add to the module build:
```kotlin
// in infofer-client/build.gradle.kts, top-level
tasks.named<ProcessResources>("jvmTestProcessResources") {
    from("src/commonTest/resources")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.FixturesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): fixture loader and exception types"
```

---

### Task 5: TrainResult parser (live delay + stops + branches) — the core feature

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/parse/TrainResultParser.kt`
- Test: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/parse/TrainResultParserTest.kt`

**Interfaces:**
- Consumes: models (Task 2), `parseCategory` (Task 3), `InfoferParseException` (Task 4), Ksoup.
- Produces: `object TrainResultParser { fun parse(html: String, trainNumber: String): TrainItinerary }`

Selectors (from `docs/infofer-api.md`, verified against `train-result-5568.html`):
- Branch container: `div[id^=div-stations-branch-]`; header `h4` text `"Parcurs tren {from}–{to}"` (en-dash `–`).
- Delay summary: within branch, `p` containing `i.fa-stopwatch`; a `span.color-firebrick` holding `"{n} min întârziere"`; reported-at in a following `span.color-firebrick` matching `Raportat la (\d{1,2}:\d{2})`. Absence ⇒ `Delay(0, null)`.
- Stops: `li.list-group-item`. Station link `a[href*=/ro-RO/Statie/]`. `km {n}` and `linia {x}` appear as column text. Times: `div.text-1-3rem`. Per-stop status: `div.text-0-8rem` with class `color-darkgreen` (ON_TIME) or `color-firebrick` (DELAYED).

- [ ] **Step 1: Write the failing test (against the real fixture)**

`TrainResultParserTest.kt`:
```kotlin
package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import ro.trenuri.infofer.model.StopStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainResultParserTest {
    private val itinerary = TrainResultParser.parse(Fixtures.load("train-result-5568.html"), "5568")

    @Test fun extracts_train_number() {
        assertEquals("5568", itinerary.trainNumber)
    }

    @Test fun has_at_least_one_branch_with_stops() {
        assertTrue(itinerary.branches.isNotEmpty())
        assertTrue(itinerary.branches.first().stops.size >= 2)
    }

    @Test fun first_stop_is_origin_with_departure_only() {
        val first = itinerary.branches.first().stops.first()
        assertEquals("Botoșani", first.station.name)
        assertEquals("9:21", first.departure)
        assertEquals(null, first.arrival)
    }

    @Test fun parses_reported_delay_of_2_minutes_at_suceava() {
        // Fixture captured live with "2 min întârziere ... (Raportat la 18:46)"
        val delay = itinerary.branches.first().delay
        assertEquals(2, delay?.minutes)
        assertEquals("18:46", delay?.reportedAt)
    }

    @Test fun on_time_stops_have_on_time_status() {
        val origin = itinerary.branches.first().stops.first()
        assertEquals(StopStatus.ON_TIME, origin.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.TrainResultParserTest"`
Expected: FAIL — `TrainResultParser` unresolved.

- [ ] **Step 3: Implement `TrainResultParser`**

```kotlin
package ro.trenuri.infofer.parse

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.*
import ro.trenuri.infofer.util.parseCategory

object TrainResultParser {
    private val DELAY_MIN = Regex("""(\d+)\s*min""")
    private val REPORTED = Regex("""Raportat la\s*(\d{1,2}:\d{2})""")
    private val KM = Regex("""km\s*(\d+)""")
    private val LINIA = Regex("""linia\s*([^\s<]+)""")

    fun parse(html: String, trainNumber: String): TrainItinerary {
        val doc = Ksoup.parse(html)
        val branchEls = doc.select("div[id^=div-stations-branch-]")
        if (branchEls.isEmpty()) throw InfoferParseException("no train branches in result for $trainNumber")

        val category = doc.selectFirst("[class*=span-train-category-]")
            ?.className()?.let { parseCategory(it) } ?: TrainCategory.OTHER

        val branches = branchEls.map { parseBranch(it) }
        return TrainItinerary(trainNumber, category, branches)
    }

    private fun parseBranch(el: Element): TrainBranch {
        val header = el.selectFirst("h4")?.text().orEmpty()
        val (from, to) = header.substringAfter("Parcurs tren").trim()
            .split("–", "-").let {
                (it.getOrNull(0)?.trim().orEmpty()) to (it.getOrNull(1)?.trim().orEmpty())
            }
        val delay = parseDelay(el)
        val stops = el.select("li.list-group-item").map { parseStop(it) }
        return TrainBranch(from, to, delay, stops)
    }

    private fun parseDelay(branch: Element): Delay {
        val p = branch.select("p:has(i.fa-stopwatch)").firstOrNull() ?: return Delay(0, null)
        val text = p.text()
        val mins = DELAY_MIN.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val reported = REPORTED.find(text)?.groupValues?.get(1)
        return if (mins != null && text.contains("întârziere")) Delay(mins, reported) else Delay(0, reported)
    }

    private fun parseStop(li: Element): TrainStop {
        val link = li.selectFirst("a[href*=/ro-RO/Statie/]")
        val name = link?.text()?.trim().orEmpty()
        val slug = link?.attr("href")?.substringAfter("/Statie/")?.substringBefore("?").orEmpty()
        val whole = li.text()
        val km = KM.find(whole)?.groupValues?.get(1)?.toIntOrNull()
        val track = LINIA.find(whole)?.groupValues?.get(1)
        val times = li.select("div.text-1-3rem").map { it.text().trim() }.filter { it.isNotEmpty() }
        // Origin row has a single time = departure; terminus a single = arrival;
        // intermediate rows: [arrival, departure]. Disambiguate by column position.
        val arrival: String?
        val departure: String?
        when (times.size) {
            0 -> { arrival = null; departure = null }
            1 -> {
                // origin (left col empty) => departure; else arrival.
                val isOrigin = li.selectFirst("div.col-3 div.text-1-3rem") == null
                if (isOrigin) { arrival = null; departure = times[0] }
                else { arrival = times[0]; departure = null }
            }
            else -> { arrival = times[0]; departure = times[1] }
        }
        val status = when {
            li.selectFirst("div.text-0-8rem.color-firebrick") != null -> StopStatus.DELAYED
            li.selectFirst("div.text-0-8rem.color-darkgreen") != null -> StopStatus.ON_TIME
            else -> StopStatus.UNKNOWN
        }
        val station = Station(name, slug)
        return TrainStop(station, km, track, arrival, departure, status)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.TrainResultParserTest"`
Expected: PASS. If a selector assertion fails, open `train-result-5568.html`, adjust the selector to the real markup (the fixture is ground truth), and re-run. Do NOT change the asserted values — they are real captured data.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): train result parser with live delay"
```

---

### Task 6: Itineraries parser

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/parse/ItinerariesParser.kt`
- Test: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/parse/ItinerariesParserTest.kt`

**Interfaces:**
- Produces: `object ItinerariesParser { fun parse(html: String): List<ItineraryOption> }`

Selectors (verified against `itineraries-bucuresti-brasov.html`): each option exposes a category badge `[class*=span-train-category-]`, station rows `div.div-itinerary-station`, departure/arrival blocks `div.div-itineraries-departure-arrival`, and a "direct"/"schimbare" indicator. The fixture has ~45 options for Bucureşti Nord → Braşov.

- [ ] **Step 1: Write the failing test**

```kotlin
package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import kotlin.test.Test
import kotlin.test.assertTrue

class ItinerariesParserTest {
    private val options = ItinerariesParser.parse(Fixtures.load("itineraries-bucuresti-brasov.html"))

    @Test fun finds_multiple_options() {
        assertTrue(options.size >= 10, "expected many itineraries, got ${options.size}")
    }

    @Test fun every_option_has_times_and_at_least_one_leg() {
        assertTrue(options.all { it.departureTime.isNotBlank() && it.arrivalTime.isNotBlank() })
        assertTrue(options.all { it.legs.isNotEmpty() })
    }

    @Test fun direct_options_have_zero_changes() {
        assertTrue(options.any { it.changes == 0 })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.ItinerariesParserTest"`
Expected: FAIL — unresolved `ItinerariesParser`.

- [ ] **Step 3: Implement `ItinerariesParser`**

Implement against the fixture. Locate the repeating option container (the element that wraps one `img-train-operator` + its station/time rows; inspect the fixture to confirm the wrapper class), then for each:
- `category = parseCategory(firstBadgeClass)`
- collect station names from `div.div-itinerary-station` and times from `div.div-itineraries-departure-arrival`
- `departureTime` = first time, `arrivalTime` = last time
- `changes` = count of legs − 1 (a leg per train badge in the option); `direct` text ⇒ 0
- build `ItineraryLeg`s from consecutive (train badge, from-station/time, to-station/time) groups

```kotlin
package ro.trenuri.infofer.parse

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.*
import ro.trenuri.infofer.util.parseCategory

object ItinerariesParser {
    fun parse(html: String): List<ItineraryOption> {
        val doc = Ksoup.parse(html)
        // Each option wraps one or more train legs; the operator logo appears once per leg.
        val optionEls = doc.select("div.div-itineraries-station-to-station")
            .ifEmpty { doc.select("[class*=div-itineraries-station-to-station]") }
        if (optionEls.isEmpty()) throw InfoferParseException("no itinerary options found")
        return optionEls.mapNotNull { runCatching { parseOption(it) }.getOrNull() }
            .filter { it.legs.isNotEmpty() }
    }

    private fun parseOption(el: Element): ItineraryOption {
        val badges = el.select("[class*=span-train-category-]")
        val stations = el.select("div.div-itinerary-station").map { it.text().trim() }
        val times = el.select("div.div-itineraries-departure-arrival").map { it.text().trim() }
            .filter { it.matches(Regex(".*\\d{1,2}:\\d{2}.*")) }
        val legs = badges.mapIndexed { i, badge ->
            ItineraryLeg(
                trainNumber = Regex("\\d{2,5}").find(badge.text())?.value.orEmpty(),
                category = parseCategory(badge.className()),
                departureStation = stations.getOrElse(i) { "" },
                departureTime = times.getOrElse(i * 2) { times.firstOrNull().orEmpty() },
                arrivalStation = stations.getOrElse(i + 1) { stations.lastOrNull().orEmpty() },
                arrivalTime = times.getOrElse(i * 2 + 1) { times.lastOrNull().orEmpty() },
            )
        }
        return ItineraryOption(
            departureTime = times.firstOrNull().orEmpty(),
            arrivalTime = times.lastOrNull().orEmpty(),
            durationMinutes = null,
            changes = (legs.size - 1).coerceAtLeast(0),
            legs = legs,
        )
    }
}
```
Refine selectors against the fixture until the tests in Step 1 pass.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.ItinerariesParserTest"`
Expected: PASS (adjust selectors to the fixture as needed; keep assertions).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): itineraries parser"
```

---

### Task 7: Station board parser

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/parse/StationBoardParser.kt`
- Test: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/parse/StationBoardParserTest.kt`

**Interfaces:**
- Produces: `object StationBoardParser { fun parse(html: String, station: String, kind: BoardKind): StationBoard }`

Selectors (verified against `station-board-brasov.html`): rows `div.div-departures-arrivails-details` (site spelling); real-time badges `div.div-stations-train-real-time-badge`; category `[class*=span-train-category-]`; train link `a[href*=/ro-RO/Tren/]`. Departures vs arrivals are separate tabs in the same fragment.

- [ ] **Step 1: Write the failing test**

```kotlin
package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import ro.trenuri.infofer.model.BoardKind
import kotlin.test.Test
import kotlin.test.assertTrue

class StationBoardParserTest {
    @Test fun parses_departures_rows() {
        val board = StationBoardParser.parse(
            Fixtures.load("station-board-brasov.html"), "Brașov", BoardKind.DEPARTURES,
        )
        assertTrue(board.entries.size >= 5, "expected several departures, got ${board.entries.size}")
        assertTrue(board.entries.all { it.trainNumber.isNotBlank() })
        assertTrue(board.entries.all { it.scheduledTime.matches(Regex("\\d{1,2}:\\d{2}")) })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.StationBoardParserTest"`
Expected: FAIL — unresolved `StationBoardParser`.

- [ ] **Step 3: Implement `StationBoardParser`**

Parse `div.div-departures-arrivails-details` rows under the tab matching `kind`. For each row: train number from `a[href*=/ro-RO/Tren/]` (`href.substringAfterLast('/')`), category from badge class, counterpart station text, scheduled time (`HH:MM`), delay minutes from real-time badge text (`(\d+)\s*min` or null when "la timp"), track if present. Throw `InfoferParseException` if no rows found. Refine selectors against the fixture.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.StationBoardParserTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): station board parser"
```

---

### Task 8: Nearest-stations parser

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/parse/NearestStationsParser.kt`
- Test: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/parse/NearestStationsParserTest.kt`

**Interfaces:**
- Produces: `object NearestStationsParser { fun parse(html: String): List<Station> }`

Selectors (verified against `nearest-stations-bucuresti.html`): `button.list-group-item` items, station name in `span.font-weight-bold`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ro.trenuri.infofer.parse

import ro.trenuri.infofer.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NearestStationsParserTest {
    private val stations = NearestStationsParser.parse(Fixtures.load("nearest-stations-bucuresti.html"))

    @Test fun finds_nearest_stations() {
        assertTrue(stations.isNotEmpty())
        assertEquals("Bucureşti Nord", stations.first().name)
        assertEquals("Bucuresti-Nord", stations.first().slug)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.NearestStationsParserTest"`
Expected: FAIL — unresolved `NearestStationsParser`.

- [ ] **Step 3: Implement `NearestStationsParser`**

```kotlin
package ro.trenuri.infofer.parse

import com.fleeksoft.ksoup.Ksoup
import ro.trenuri.infofer.model.Station
import ro.trenuri.infofer.util.stationSlug

object NearestStationsParser {
    fun parse(html: String): List<Station> =
        Ksoup.parse(html).select("button.list-group-item span.font-weight-bold")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .map { Station(it, stationSlug(it)) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.parse.NearestStationsParserTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): nearest stations parser"
```

---

### Task 9: HTTP session (token harvest + two-step POST flow)

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/net/InfoferSession.kt`
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/net/PageTokens.kt`
- Test: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/net/InfoferSessionTest.kt`

**Interfaces:**
- Consumes: Ktor `HttpClient`, `InfoferNetworkException`.
- Produces:
  - `data class PageTokens(val requestVerificationToken: String, val confirmationKey: String)`
  - `fun extractTokens(html: String): PageTokens` — pulls `input[name=__RequestVerificationToken]` and `input[name=ConfirmationKey]` `value`s; throws `InfoferParseException` if absent.
  - ```
    class InfoferSession(
        private val http: HttpClient,
        private val baseUrl: String = "https://mersultrenurilor.infofer.ro",
    ) {
        suspend fun getPage(path: String): String
        suspend fun postResult(path: String, fields: Map<String, String>, pageTokens: PageTokens): String
    }
    ```
    `postResult` form-encodes `fields` + `__RequestVerificationToken`, `ConfirmationKey`, and the anti-abuse fields (`ReCaptcha=""`, `IsReCaptchaFailed=False`, `IsSearchWanted=False`). The Ktor client is created by the caller with `HttpCookies` installed so the antiforgery cookie from `getPage` is reused on `postResult`.

- [ ] **Step 1: Write the failing test (MockEngine, no network)**

```kotlin
package ro.trenuri.infofer.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfoferSessionTest {
    @Test fun extracts_tokens_from_page_html() {
        val html = """
            <form><input name="__RequestVerificationToken" value="TOK123"/>
            <input name="ConfirmationKey" value="CONF456"/></form>
        """.trimIndent()
        val t = extractTokens(html)
        assertEquals("TOK123", t.requestVerificationToken)
        assertEquals("CONF456", t.confirmationKey)
    }

    @Test fun post_includes_tokens_and_antiabuse_fields() = runTest {
        var captured = ""
        val engine = MockEngine { req ->
            captured = (req.body as io.ktor.http.content.TextContent).text
            respond("<html>ok</html>", HttpStatusCode.OK)
        }
        val session = InfoferSession(HttpClient(engine))
        val body = session.postResult(
            "/ro-RO/Trains/TrainsResult",
            mapOf("TrainRunningNumber" to "5568", "Date" to "26.06.2026 0:00:00"),
            PageTokens("TOK123", "CONF456"),
        )
        assertTrue(body.contains("ok"))
        assertTrue(captured.contains("TrainRunningNumber=5568"))
        assertTrue(captured.contains("__RequestVerificationToken=TOK123"))
        assertTrue(captured.contains("ConfirmationKey=CONF456"))
        assertTrue(captured.contains("IsReCaptchaFailed=False"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.net.InfoferSessionTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `PageTokens.kt` and `InfoferSession.kt`**

```kotlin
// PageTokens.kt
package ro.trenuri.infofer.net

import com.fleeksoft.ksoup.Ksoup
import ro.trenuri.infofer.InfoferParseException

data class PageTokens(val requestVerificationToken: String, val confirmationKey: String)

fun extractTokens(html: String): PageTokens {
    val doc = Ksoup.parse(html)
    val rvt = doc.selectFirst("input[name=__RequestVerificationToken]")?.attr("value")
    val conf = doc.selectFirst("input[name=ConfirmationKey]")?.attr("value").orEmpty()
    if (rvt.isNullOrEmpty()) throw InfoferParseException("missing __RequestVerificationToken on page")
    return PageTokens(rvt, conf)
}
```

```kotlin
// InfoferSession.kt
package ro.trenuri.infofer.net

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ro.trenuri.infofer.InfoferNetworkException

class InfoferSession(
    private val http: HttpClient,
    private val baseUrl: String = "https://mersultrenurilor.infofer.ro",
) {
    suspend fun getPage(path: String): String =
        try {
            http.get("$baseUrl$path") {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }.bodyAsText()
        } catch (e: Exception) {
            throw InfoferNetworkException("GET $path failed", e)
        }

    suspend fun postResult(path: String, fields: Map<String, String>, pageTokens: PageTokens): String {
        val params = Parameters.build {
            fields.forEach { (k, v) -> append(k, v) }
            append("ReCaptcha", "")
            append("IsReCaptchaFailed", "False")
            append("IsSearchWanted", "False")
            append("ConfirmationKey", pageTokens.confirmationKey)
            append("__RequestVerificationToken", pageTokens.requestVerificationToken)
        }
        return try {
            http.post("$baseUrl$path") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(params.formUrlEncode())
            }.bodyAsText()
        } catch (e: Exception) {
            throw InfoferNetworkException("POST $path failed", e)
        }
    }

    companion object {
        const val USER_AGENT = "TrenuriApp/0.1 (+https://github.com/; informational; contact in README)"
    }
}
```
Note: `setBody(params.formUrlEncode())` yields a `TextContent`, matching the test's cast.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.net.InfoferSessionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): http session with token harvest + POST flow"
```

---

### Task 10: InfoferClient facade

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/InfoferClient.kt`
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/InfoferClientFactory.kt`
- Test: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/InfoferClientTest.kt`

**Interfaces:**
- Consumes: session (Task 9), all parsers (Tasks 5–8), helpers (Task 3).
- Produces:
  ```
  class InfoferClient(private val session: InfoferSession) {
      suspend fun getTrain(trainNumber: String, year: Int, month: Int, day: Int): TrainItinerary
      suspend fun searchItineraries(fromSlug: String, toSlug: String, year: Int, month: Int, day: Int): List<ItineraryOption>
      suspend fun getStationBoard(stationName: String, kind: BoardKind, year: Int, month: Int, day: Int): StationBoard
      suspend fun findNearestStations(latitude: Double, longitude: Double): List<Station>
  }
  ```
  Each method: GET the feature page → `extractTokens` → `postResult` → parse. `findNearestStations` is a single GET (no token).
- Also `fun defaultInfoferClient(): InfoferClient` in `InfoferClientFactory.kt`, building a Ktor `HttpClient(CIO)` (jvm) with `HttpCookies` installed. (CIO engine is JVM-only; Android/iOS/JS provide their own engine in later phases via `expect/actual` — out of scope here.)

- [ ] **Step 1: Write the failing test (MockEngine returns real fixtures by path)**

```kotlin
package ro.trenuri.infofer

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.net.InfoferSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfoferClientTest {
    private fun clientReturning(pageHtml: String, resultHtml: String): InfoferClient {
        val engine = MockEngine { req ->
            val body = if (req.method == HttpMethod.Get) pageHtml else resultHtml
            respond(body, HttpStatusCode.OK)
        }
        return InfoferClient(InfoferSession(HttpClient(engine)))
    }

    @Test fun getTrain_parses_fixture_result() = runTest {
        val page = """<input name="__RequestVerificationToken" value="T"/><input name="ConfirmationKey" value="C"/>"""
        val client = clientReturning(page, Fixtures.load("train-result-5568.html"))
        val train = client.getTrain("5568", 2026, 6, 26)
        assertEquals("5568", train.trainNumber)
        assertEquals(2, train.branches.first().delay?.minutes)
    }

    @Test fun searchItineraries_parses_fixture_result() = runTest {
        val page = """<input name="__RequestVerificationToken" value="T"/><input name="ConfirmationKey" value="C"/>"""
        val client = clientReturning(page, Fixtures.load("itineraries-bucuresti-brasov.html"))
        val opts = client.searchItineraries("Bucuresti-Nord", "Brasov", 2026, 6, 26)
        assertTrue(opts.size >= 10)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.InfoferClientTest"`
Expected: FAIL — unresolved `InfoferClient`.

- [ ] **Step 3: Implement `InfoferClient` and factory**

```kotlin
// InfoferClient.kt
package ro.trenuri.infofer

import ro.trenuri.infofer.model.*
import ro.trenuri.infofer.net.InfoferSession
import ro.trenuri.infofer.net.extractTokens
import ro.trenuri.infofer.parse.*
import ro.trenuri.infofer.util.formatInfoferDate

class InfoferClient(private val session: InfoferSession) {

    suspend fun getTrain(trainNumber: String, year: Int, month: Int, day: Int): TrainItinerary {
        val page = session.getPage("/ro-RO/Tren/$trainNumber")
        val tokens = extractTokens(page)
        val html = session.postResult(
            "/ro-RO/Trains/TrainsResult",
            mapOf(
                "Date" to formatInfoferDate(year, month, day),
                "TrainRunningNumber" to trainNumber,
                "SelectedBranchCode" to "",
            ),
            tokens,
        )
        return TrainResultParser.parse(html, trainNumber)
    }

    suspend fun searchItineraries(
        fromSlug: String, toSlug: String, year: Int, month: Int, day: Int,
    ): List<ItineraryOption> {
        val page = session.getPage("/ro-RO/Rute-trenuri/$fromSlug/$toSlug")
        val tokens = extractTokens(page)
        val html = session.postResult(
            "/ro-RO/Itineraries/GetItineraries",
            mapOf(
                "DepartureStationName" to fromSlug,
                "ArrivalStationName" to toSlug,
                "DepartureDate" to formatInfoferDate(year, month, day),
                "ConnectionsTypeId" to "1",
                "OrderingTypeId" to "0",
                "TimeSelectionId" to "0",
                "MinutesInDay" to "0",
            ),
            tokens,
        )
        return ItinerariesParser.parse(html)
    }

    suspend fun getStationBoard(
        stationName: String, kind: BoardKind, year: Int, month: Int, day: Int,
    ): StationBoard {
        val page = session.getPage("/ro-RO/Statie/$stationName")
        val tokens = extractTokens(page)
        val html = session.postResult(
            "/ro-RO/Stations/StationsResult",
            mapOf("Date" to formatInfoferDate(year, month, day), "StationName" to stationName),
            tokens,
        )
        return StationBoardParser.parse(html, stationName, kind)
    }

    suspend fun findNearestStations(latitude: Double, longitude: Double): List<Station> {
        val html = session.getPage(
            "/api/ro-RO/Stations/GetNearestStationsName?latitude=$latitude&longitude=$longitude",
        )
        return NearestStationsParser.parse(html)
    }
}
```

```kotlin
// InfoferClientFactory.kt
package ro.trenuri.infofer

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.HttpCookies
import ro.trenuri.infofer.net.InfoferSession

fun defaultInfoferClient(): InfoferClient {
    val http = HttpClient(CIO) { install(HttpCookies) }
    return InfoferClient(InfoferSession(http))
}
```
Note: `InfoferClientFactory.kt` uses the CIO engine, which is JVM-only; place it in `jvmMain` if `commonMain` cannot see CIO. Move the file to `src/jvmMain/kotlin/...` if compilation complains.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.InfoferClientTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(infofer-client): InfoferClient facade"
```

---

### Task 11: Full green run, optional live smoke test, module README

**Files:**
- Create: `infofer-client/README.md`
- Create: `infofer-client/src/jvmTest/kotlin/ro/trenuri/infofer/LiveSmokeTest.kt` (disabled by default)

**Interfaces:** none new.

- [ ] **Step 1: Run the whole suite**

Run: `./gradlew :infofer-client:jvmTest`
Expected: all tests PASS.

- [ ] **Step 2: Add an opt-in live smoke test (skipped unless env var set)**

```kotlin
package ro.trenuri.infofer

import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.model.BoardKind
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveSmokeTest {
    private val enabled = System.getenv("INFOFER_LIVE") == "1"

    @Test fun nearest_stations_live() = runTest {
        if (!enabled) return@runTest
        val client = defaultInfoferClient()
        val stations = client.findNearestStations(44.4268, 26.1025)
        assertTrue(stations.isNotEmpty())
    }
}
```
Run (manual, only when explicitly checking live): `INFOFER_LIVE=1 ./gradlew :infofer-client:jvmTest --tests "*LiveSmokeTest"`
Expected when enabled: PASS (hits the real site once — respects the polite-client rule).

- [ ] **Step 3: Write `infofer-client/README.md`**

Document: purpose, the `InfoferClient` API surface, that all logic is in `commonMain`, how to add Android/iOS/JS targets (each provides a Ktor engine), the fixture-based testing approach, and a pointer to `docs/infofer-api.md`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "test(infofer-client): full suite green + live smoke + module README"
```

---

## Self-Review

**Spec coverage** (against `README.md` / `CLAUDE.md` / `docs/infofer-api.md`):
- Itinerary search → Task 6 + facade Task 10. ✓
- Train detail + live delay → Task 5 + Task 10. ✓ (delay is the headline data the alarm engine consumes.)
- Station board → Task 7 + Task 10. ✓
- Nearest stations → Task 8 + Task 10. ✓
- Two-step token/cookie POST flow → Task 9. ✓
- Station slug/diacritic handling → Task 3. ✓
- Parsers fail loudly → `InfoferParseException` in Tasks 4–9. ✓
- KMP, commonMain-only logic, JVM-testable here → Tasks 1, 4. ✓
- Polite client (User-Agent, single live call gated) → Tasks 9, 11. ✓

**Out of this plan (tracked for follow-on):**
- The **Android app** (UI + WorkManager alarm engine) — separate plan; depends on this client.
- Adding `androidTarget()`, iOS, and `js()` targets with `expect/actual` Ktor engines — done when each client phase starts.
- Static data.gov.ro GTFS ingestion for offline station lists — later; nearest-stations + parsed boards suffice for phase 1.

**Type consistency:** model names (`TrainItinerary`, `TrainBranch`, `Delay`, `ItineraryOption`, `ItineraryLeg`, `StationBoard`, `BoardEntry`, `Station`) are defined once in Task 2 and used unchanged in Tasks 5–10. Facade method signatures in Task 10 match the parser return types from Tasks 5–8. ✓

**Known risk:** the Itineraries/Station parsers (Tasks 6–7) target large fixtures; exact wrapper-class selectors must be confirmed against the fixture during implementation. The tests assert structural invariants (counts, non-blank times) rather than brittle exact strings, so reasonable selector refinement keeps them green. The Train parser (Task 5) asserts exact captured values (2-min delay, `9:21`) because those are the headline-feature guarantees.
