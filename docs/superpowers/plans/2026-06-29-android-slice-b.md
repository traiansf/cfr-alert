# Android Slice B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Grow the Slice-A app into a complete informational app — itinerary search, station board, and nearest-stations — behind a bottom-navigation shell with cross-tab linking, a bundled station dataset for name→slug resolution, shared date selection, the "Alerte Sosiri Trenuri" rename, and the deferred Slice-A follow-ups.

**Architecture:** New typed `InfoferClient.findStations(query)` backed by a generated, committed station dataset (derived once from the data.gov.ro timetable) keeps the CLAUDE.md boundary intact. The `:app` module adds three MVVM features (each: `…Provider` seam → `…Repository` → `…ViewModel` exposing one `StateFlow<…UiState>`) plus a lightweight custom tab host (`Scaffold` + Material3 `NavigationBar` + a testable `TabNavigator`) whose retained ViewModels give per-tab state that survives switches and is overwritten only by follow actions.

**Tech Stack:** Kotlin Multiplatform (Ktor + Ksoup) for `infofer-client`; Android + Jetpack Compose + Material3; Koin DI; kotlinx-coroutines; kotlinx-datetime; JUnit + kotlin-test + coroutines-test.

## Global Constraints

- `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`; `jvmToolchain(21)` (only JDK 21 on host). Verbatim from `app/build.gradle.kts`.
- DI is **Koin** (no annotation processing). UI is **Jetpack Compose + Material3**.
- **CLAUDE.md boundary:** no `:app` code touches Ktor, HTML, infofer endpoints, or slug derivation. Station name→slug resolution is a typed `infofer-client` method only.
- **No network in unit tests** — use the `…Provider` fun-interface seams with fakes. No emulator/instrumented tests (no `/dev/kvm`); Compose UI is verified on a physical phone.
- **Captured HTML fixtures are immutable** — never edit a fixture to pass a test. The generated `StationsData.kt` is a committed build artifact, regenerated only by re-running the generator against the live source.
- UI chrome strings are **Romanian**; Romanian data terms (`întârziere`, `sosire`, `plecare`, `Plecări`, `Sosiri`) shown as-is.
- Launcher label must read **"Alerte Sosiri Trenuri"**; `applicationId` stays `ro.trenuri.app`; package names unchanged.
- Each task ends green: run the stated tests/build before the commit step.

---

### Task 1: Slice A follow-ups — Koin dispatcher binding, DelayBanner & ParseError tests, srcDirs cleanup

**Files:**
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt`
- Modify: `app/build.gradle.kts:31-32` (remove redundant `srcDirs`)
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/DelayBannerTest.kt`
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/TrainViewModelTest.kt`

**Interfaces:**
- Consumes: existing `TrainRepository(provider, io)`, `delayBannerOf(Delay?)`, `TrainViewModel`, `ErrorMessages`.
- Produces: Koin now provides a named `CoroutineDispatcher` (the IO dispatcher) injected into `TrainRepository`. No new public types.

- [ ] **Step 1: Add the DelayBanner edge-case tests (failing only if behavior wrong)**

Append to `DelayBannerTest.kt`:

```kotlin
    @Test
    fun negativeMinutesIsTreatedAsOnTime() {
        // infofer never reports negative delay; guard documents the <= 0 branch.
        assertEquals(DelayBanner.OnTime, delayBannerOf(Delay(minutes = -3, reportedAt = "18:46")))
    }

    @Test
    fun delayedWithNullReportedAtIsPreserved() {
        assertEquals(
            DelayBanner.Delayed(minutes = 5, reportedAt = null),
            delayBannerOf(Delay(minutes = 5, reportedAt = null)),
        )
    }
```

- [ ] **Step 2: Add the TrainViewModel ParseError test**

Append a test to `TrainViewModelTest.kt` (reuse the file's existing fake-repository / dispatcher-rule setup; mirror the existing NetworkError test but return `TrainResult.ParseError` and assert the parse message):

```kotlin
    @Test
    fun parseErrorMapsToParseMessage() = runTest {
        val vm = TrainViewModel(
            repository = repositoryReturning(TrainResult.ParseError),
            today = { Triple(2026, 6, 29) },
            messages = testMessages, // network = "net", parse = "parse"
        )
        vm.search("5568")
        advanceUntilIdle()
        assertEquals(TrainUiState.Error("parse"), vm.state.value)
    }
```

> If `repositoryReturning(...)`/`testMessages` helpers don't already exist in the file, add a minimal fake `TrainRepository` subclass-free seam by constructing `TrainRepository(provider = { _,_,_,_ -> throw ... })` is not possible (provider returns itinerary). Instead build the VM with a fake repository via a local `object : TrainProvider` that the real `TrainRepository` wraps, returning a parse-triggering path — OR follow the existing test's established fake pattern. Match whatever pattern the existing Success/NetworkError tests use.

- [ ] **Step 3: Run the new tests to verify they pass against current behavior**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.DelayBannerTest" --tests "ro.trenuri.app.ui.TrainViewModelTest"`
Expected: PASS (these lock in current behavior; they fail only if the mapping regresses).

- [ ] **Step 4: Add the Koin IO-dispatcher binding and inject it**

In `AppModule.kt`, add an import and a qualified binding, then pass it to `TrainRepository`:

```kotlin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named

val ioDispatcherQualifier = named("io")

// inside module { ... }, before the TrainRepository line:
single<CoroutineDispatcher>(ioDispatcherQualifier) { Dispatchers.IO }
single { TrainRepository(get(), get(ioDispatcherQualifier)) }
```

- [ ] **Step 5: Remove the redundant srcDirs lines**

Delete these two lines from `app/build.gradle.kts`:

```kotlin
    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
```

(The Kotlin Android plugin already compiles `src/main/kotlin` / `src/test/kotlin`.)

- [ ] **Step 6: Verify the Koin graph still resolves and everything builds**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, including the existing `AppModuleTest` (Koin graph verification).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt app/build.gradle.kts app/src/test/kotlin/ro/trenuri/app/ui/DelayBannerTest.kt app/src/test/kotlin/ro/trenuri/app/ui/TrainViewModelTest.kt
git commit -m "chore(app): fold Slice A follow-ups (Koin IO dispatcher, DelayBanner/ParseError tests, srcDirs)"
```

---

### Task 2: Canonical station slug, name normalization, geo helpers, and `Station` coordinates (commonMain)

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationSlug.kt`
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/Geo.kt`
- Modify: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/model/Models.kt` (add optional coords to `Station`)
- Test: `infofer-client/src/commonTest/kotlin/ro/trenuri/infofer/data/StationSlugTest.kt`
- Test: `infofer-client/src/commonTest/kotlin/ro/trenuri/infofer/data/GeoTest.kt`

**Interfaces:**
- Produces:
  - `fun canonicalStationSlug(displayName: String): String` — transliterate Romanian diacritics + hyphenate, preserving case.
  - `fun normalizeStationName(rawName: String): String` — strip trailing station-type abbreviations (`Hm.`, `h.`, `hc.`, `hcv.` and capitalized variants) and collapse whitespace.
  - `data class LatLon(val lat: Double, val lon: Double)` and `fun haversineKm(a: LatLon, b: LatLon): Double`.
  - `Station` gains `val lat: Double? = null, val lon: Double? = null` (backward-compatible defaults; existing `Station(name, slug)` call sites unaffected).

- [ ] **Step 1: Write the failing tests**

```kotlin
package ro.trenuri.infofer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class StationSlugTest {
    @Test fun mapsRomanianDiacriticsBothUnicodeForms() {
        // cedilla form ş/ţ and comma-below form ș/ț both transliterate
        assertEquals("Bucuresti-Nord", canonicalStationSlug("Bucureşti Nord"))
        assertEquals("Bucuresti-Nord", canonicalStationSlug("București Nord"))
        assertEquals("Brasov", canonicalStationSlug("Braşov"))
        assertEquals("Ramificatia-Floreni", canonicalStationSlug("Ramificaţia Floreni"))
        assertEquals("Vatra-Dornei-Bai", canonicalStationSlug("Vatra Dornei Băi"))
    }

    @Test fun collapsesSeparatorsAndTrimsHyphens() {
        assertEquals("Foo-Bar", canonicalStationSlug("  Foo   Bar  "))
        assertEquals("A-B", canonicalStationSlug("A / B"))
        assertEquals("A-B", canonicalStationSlug("A.B"))
    }

    @Test fun normalizeStripsStationTypeSuffixes() {
        assertEquals("Vatra Dornei Băi", normalizeStationName("Vatra Dornei Băi hc."))
        assertEquals("Roşu", normalizeStationName("Roşu Hm."))
        assertEquals("Dorna Candrenilor", normalizeStationName("Dorna Candrenilor h."))
        assertEquals("Brașov", normalizeStationName("Brașov"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.data.StationSlugTest"`
Expected: FAIL (unresolved reference `canonicalStationSlug`).

- [ ] **Step 3: Implement**

```kotlin
package ro.trenuri.infofer.data

private val DIACRITICS = mapOf(
    'ă' to "a", 'Ă' to "A", 'â' to "a", 'Â' to "A", 'î' to "i", 'Î' to "I",
    'ș' to "s", 'Ș' to "S", 'ş' to "s", 'Ş' to "S",   // comma-below + cedilla
    'ț' to "t", 'Ț' to "T", 'ţ' to "t", 'Ţ' to "T",
)

private val SUFFIX = Regex("""\s+(?:[Hh][Mm]|[Hh][Cc][Vv]?|[Hh])\.\s*$""")

/** Strip trailing station-type abbreviations (Hm., h., hc., hcv.) and collapse whitespace. */
fun normalizeStationName(rawName: String): String =
    rawName.replace(SUFFIX, "").trim().replace(Regex("""\s+"""), " ")

/** Transliterate Romanian diacritics and hyphenate, preserving case. e.g. "Bucureşti Nord" -> "Bucuresti-Nord". */
fun canonicalStationSlug(displayName: String): String {
    val sb = StringBuilder()
    for (ch in displayName.trim()) {
        when {
            DIACRITICS.containsKey(ch) -> sb.append(DIACRITICS[ch])
            ch.isLetterOrDigit() && ch.code < 128 -> sb.append(ch)
            ch == ' ' || ch == '/' || ch == '.' || ch == '-' || ch == '_' -> sb.append('-')
            // drop any other punctuation / non-ASCII
        }
    }
    return sb.toString()
        .replace(Regex("-+"), "-")
        .trim('-')
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.data.StationSlugTest"`
Expected: PASS.

- [ ] **Step 5: Add optional coordinates to the `Station` model**

In `Models.kt` change the data class (defaults keep every existing call site valid):

```kotlin
data class Station(
    val name: String,
    val slug: String,
    val lat: Double? = null,
    val lon: Double? = null,
)
```

- [ ] **Step 6: Write the failing geo test**

```kotlin
package ro.trenuri.infofer.data

import kotlin.test.Test
import kotlin.test.assertTrue

class GeoTest {
    @Test fun haversineApproximatesKnownDistance() {
        // Bucharest (~44.44,26.10) to Brașov (~45.65,25.61) ≈ 137 km great-circle
        val d = haversineKm(LatLon(44.4396, 26.0963), LatLon(45.6536, 25.6112))
        assertTrue(d in 130.0..145.0, "expected ~137 km, got $d")
    }
}
```

- [ ] **Step 7: Implement `Geo.kt`**

```kotlin
package ro.trenuri.infofer.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

data class LatLon(val lat: Double, val lon: Double)

private fun Double.toRad() = this * PI / 180.0

fun haversineKm(a: LatLon, b: LatLon): Double {
    val r = 6371.0088
    val dLat = (b.lat - a.lat).toRad()
    val dLon = (b.lon - a.lon).toRad()
    val s = sin(dLat / 2) * sin(dLat / 2) +
        cos(a.lat.toRad()) * cos(b.lat.toRad()) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(s), sqrt(1 - s))
}
```

- [ ] **Step 8: Run all the new commonMain tests**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.data.*"`
Expected: PASS (slug + geo).

- [ ] **Step 9: Commit**

```bash
git add infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/ infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/model/Models.kt infofer-client/src/commonTest/kotlin/ro/trenuri/infofer/data/
git commit -m "feat(infofer-client): slug/normalize + geo (LatLon, haversine) + Station coords"
```

---

### Task 3: Station dataset generator + generated `StationsData.kt`

**Files:**
- Create: `tools/stations-gen/build.gradle.kts`
- Create: `tools/stations-gen/src/main/kotlin/StationsGen.kt`
- Modify: `settings.gradle.kts` (add `include(":tools:stations-gen")`)
- Create (generated, committed): `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationsData.kt`

**Interfaces:**
- Consumes: `canonicalStationSlug`, `normalizeStationName`, `Station` (with coords) — Task 2.
- Produces: `internal val ALL_STATIONS: List<Station>` in package `ro.trenuri.infofer.data`, each `Station(name, slug, lat, lon)` with coords where an OSM match was found (else `null`). Consumed by Task 4.

- [ ] **Step 1: Create the generator Gradle module**

`tools/stations-gen/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
    application
}
application { mainClass.set("StationsGenKt") }
dependencies { implementation(project(":infofer-client")) }
kotlin { jvmToolchain(21) }
```

Add to `settings.gradle.kts`: `include(":tools:stations-gen")`.

- [ ] **Step 2: Write the generator**

`tools/stations-gen/src/main/kotlin/StationsGen.kt` — (a) download the timetable XML, StAX-stream every `ElementTrasa`, collect unique stations from `DenStaOrigine`/`DenStaDestinatie`, normalize + slugify, dedup by slug; (b) fetch OSM Overpass railway stations/halts in Romania as CSV and index by slug; (c) attach coords by slug match; (d) sort and emit Kotlin:

```kotlin
import ro.trenuri.infofer.data.canonicalStationSlug
import ro.trenuri.infofer.data.normalizeStationName
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

private const val XML_URL =
    "https://data.gov.ro/dataset/c4f71dbb-de39-49b2-b697-5b60a5f299a2/resource/0f67143e-bb88-4a06-8e7a-b35b1eb91329/download/trenuri-2025-2026_sntfc.xml"
private const val OUT =
    "infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationsData.kt"
private const val OVERPASS = "https://overpass-api.de/api/interpreter"
private val OVERPASS_QUERY = """
    [out:csv(::lat,::lon,name;false)][timeout:180];
    area["ISO3166-1"="RO"][admin_level=2]->.ro;
    ( node["railway"="station"](area.ro); node["railway"="halt"](area.ro); );
    out;
""".trimIndent()

/** slug -> first OSM coordinate seen for that slug. */
private fun fetchOsmCoords(): Map<String, Pair<Double, Double>> {
    val client = HttpClient.newHttpClient()
    val req = HttpRequest.newBuilder(URI(OVERPASS))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("data=" + java.net.URLEncoder.encode(OVERPASS_QUERY, "UTF-8")))
        .build()
    val body = client.send(req, HttpResponse.BodyHandlers.ofString()).body()
    val out = LinkedHashMap<String, Pair<Double, Double>>()
    body.lineSequence().forEach { line ->
        val cols = line.split('\t')
        if (cols.size >= 3) {
            val lat = cols[0].toDoubleOrNull(); val lon = cols[1].toDoubleOrNull()
            val name = cols.drop(2).joinToString("\t")
            if (lat != null && lon != null && name.isNotBlank()) {
                val slug = canonicalStationSlug(normalizeStationName(name))
                if (slug.isNotEmpty()) out.putIfAbsent(slug, lat to lon)
            }
        }
    }
    return out
}

fun main() {
    // (a) timetable station names
    val xml = URI(XML_URL).toURL().openStream().buffered()
    val reader = XMLInputFactory.newInstance().createXMLStreamReader(xml)
    val bySlug = LinkedHashMap<String, String>() // slug -> display name
    fun add(raw: String?) {
        if (raw.isNullOrBlank()) return
        val name = normalizeStationName(raw)
        val slug = canonicalStationSlug(name)
        if (slug.isNotEmpty()) bySlug.putIfAbsent(slug, name)
    }
    while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "ElementTrasa") {
            add(reader.getAttributeValue(null, "DenStaOrigine"))
            add(reader.getAttributeValue(null, "DenStaDestinatie"))
        }
    }
    reader.close()

    // (b) + (c) OSM coords by slug
    val coords = fetchOsmCoords()

    // (d) emit
    val entries = bySlug.entries.sortedBy { it.value }
    val sb = StringBuilder()
    sb.appendLine("package ro.trenuri.infofer.data")
    sb.appendLine()
    sb.appendLine("import ro.trenuri.infofer.model.Station")
    sb.appendLine()
    sb.appendLine("// GENERATED by :tools:stations-gen. Source: data.gov.ro 2025-2026 timetable (names);")
    sb.appendLine("// OpenStreetMap via Overpass (coordinates, ODbL). Do not edit by hand.")
    sb.appendLine("internal val ALL_STATIONS: List<Station> = listOf(")
    var matched = 0
    for (e in entries) {
        val name = e.value.replace("\\", "\\\\").replace("\"", "\\\"")
        val c = coords[e.key]
        if (c != null) matched++
        val coordArgs = if (c != null) ", lat = ${c.first}, lon = ${c.second}" else ""
        sb.appendLine("    Station(name = \"$name\", slug = \"${e.key}\"$coordArgs),")
    }
    sb.appendLine(")")
    File(OUT).writeText(sb.toString())
    println("Wrote ${entries.size} stations to $OUT; OSM coords matched: $matched")
    listOf("Bucuresti-Nord", "Brasov").forEach { s ->
        println("contains $s = ${bySlug.containsKey(s)}; coord = ${coords[s]}")
    }
}
```

> Overpass is rate-limited and shared — run this generator sparingly (the output is committed, so it runs only when refreshing the dataset). If Overpass times out, retry once or use a mirror (`https://overpass.kumi.systems/api/interpreter`).

- [ ] **Step 3: Run the generator**

Run: `./gradlew :tools:stations-gen:run`
Expected: prints `Wrote <N> stations … OSM coords matched: <M>` (N ~1000–1500; M a large fraction of N — expect the majority matched, the rest `null` and degrading gracefully) and `contains Bucuresti-Nord = true; coord = (…, …)`, `contains Brasov = true; coord = (…, …)`. If a known slug is `false`, fix `normalizeStationName`/`canonicalStationSlug` and re-run — do **not** hand-edit the output. If coord coverage is surprisingly low, inspect a few unmatched names for a systematic OSM-vs-timetable naming difference (note it; perfect coverage is not required).

> Network note: downloads ~12 MB once from data.gov.ro (reachability confirmed) plus one Overpass query. If data.gov.ro is blocked, fetch the file manually and point `XML_URL` at a local `file:` path, then revert. If Overpass is blocked, coords come back empty and all stations get `null` coordinates — the build still works, distance-sort just stays inactive until regenerated.

- [ ] **Step 4: Validate a sample of derived slugs against infofer (manual, one-off)**

For ~5 stations spanning simple and suffixed names, confirm `https://mersultrenurilor.infofer.ro/ro-RO/Statie/<slug>` resolves (HTTP 200, not the not-found page). This is the spec's static correctness gate. Record any systematic mismatch as a normalization fix in Task 2 and regenerate. (Polite: a handful of GETs only.)

- [ ] **Step 5: Commit the generator + generated data**

```bash
git add tools/stations-gen settings.gradle.kts infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationsData.kt
git commit -m "feat(infofer-client): generate bundled station dataset (data.gov.ro names + OSM coords)"
```

---

### Task 3b: Full station coordinate coverage + drop operational sub-points

> Added mid-execution per user request ("there should be coordinates for all
> stations; if not from the station, then from the city/village"). Amends Task 3's
> generator and regenerates `StationsData.kt`. Full step-by-step in
> `.superpowers/sdd/task-3b-brief.md`.

**Files:**
- Modify: `tools/stations-gen/src/main/kotlin/StationsGen.kt`
- Regenerate (committed): `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationsData.kt`

**What changes:**
- **Drop operational sub-points** (Triaj / Post N / Ram. / Gr.X / Macazuri /
  Atelier / Bif. / Tj. / P.M.) — print the dropped list and verify no real
  station is caught.
- **Multi-tier coordinate resolution** (first hit wins): OSM railway nodes
  (widened to `railway=stop` and matched on `name`/`name:ro`/`official_name`/
  `alt_name`/`short_name`) → OSM `place` nodes (city/town/village/hamlet/suburb)
  → Nominatim (`"<name>, Romania"`, ≤1 req/s, UA) → curated overrides.
- Name-cleaning candidates for matching: strip parenthetical `(…)` codes and
  period-less `Hm`/`Hc`/`h` suffixes before slugging.
- Coverage guard: abort-without-write if OSM railway+place matches < 800
  (anti-regression); print a per-tier breakdown; list any still-null.

**Verify:** operational points gone (`grep -E "Triaj|Gr\.|Ram\.|Post "` → none);
Brasov/Bucuresti-Nord/Iasi present with coords; near-100% have `lat =`;
`:infofer-client:jvmTest` green.

**Commit:** `feat(stations-gen): full coordinate coverage (place-node + Nominatim fallback); drop operational sub-points`

---

### Task 4: `InfoferClient.findStations(query)`

**Files:**
- Create: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationsDataset.kt`
- Modify: `infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/InfoferClient.kt`
- Test: `infofer-client/src/commonTest/kotlin/ro/trenuri/infofer/data/StationsDatasetTest.kt`

**Interfaces:**
- Consumes: `ALL_STATIONS` (Task 3), `Station`, `LatLon`, `haversineKm` (Task 2).
- Produces:
  - `object StationsDataset { fun find(query: String, near: LatLon? = null, limit: Int = 20): List<Station>; fun matchIn(stations: List<Station>, query: String, near: LatLon?, limit: Int): List<Station> }`
  - `fun InfoferClient.findStations(query: String, near: LatLon? = null, limit: Int = 20): List<Station>` (synchronous, in-memory; no network/IO). When `near != null`, matches with coords are ordered by haversine distance ascending; coordless matches follow in name order.

- [ ] **Step 1: Write the failing tests** (against a small local list to keep them independent of the generated data size, plus one assertion on the real dataset)

```kotlin
package ro.trenuri.infofer.data

import ro.trenuri.infofer.model.Station
import kotlin.test.*

class StationsDatasetTest {
    private val sample = listOf(
        Station("Brașov", "Brasov"),
        Station("București Nord", "Bucuresti-Nord"),
        Station("Buzău", "Buzau"),
        Station("Cluj-Napoca", "Cluj-Napoca"),
    )

    @Test fun prefixMatchesRankFirstAndAreDiacriticInsensitive() {
        val r = StationsDataset.matchIn(sample, "bra", null, 10)
        assertEquals("Brasov", r.first().slug)
    }

    @Test fun substringMatchesAreFound() {
        val r = StationsDataset.matchIn(sample, "napoca", null, 10).map { it.slug }
        assertTrue("Cluj-Napoca" in r)
    }

    @Test fun diacriticTypedQueryStillMatches() {
        val r = StationsDataset.matchIn(sample, "buză", null, 10).map { it.slug }
        assertTrue("Buzau" in r)
    }

    @Test fun blankQueryReturnsEmpty() {
        assertTrue(StationsDataset.matchIn(sample, "  ", null, 10).isEmpty())
    }

    @Test fun limitIsRespected() {
        assertEquals(1, StationsDataset.matchIn(sample, "bu", null, 1).size)
    }

    @Test fun nearOrdersMatchesByDistanceAndSinksCoordlessLast() {
        val geo = listOf(
            Station("Gară Aproape", "Gara-Aproape", lat = 44.50, lon = 26.10),
            Station("Gară Departe", "Gara-Departe", lat = 47.15, lon = 27.60),
            Station("Gară FărăCoord", "Gara-FaraCoord"), // no coords
        )
        val near = LatLon(44.43, 26.10) // closest to "Aproape"
        val r = StationsDataset.matchIn(geo, "gara", near, 10).map { it.slug }
        assertEquals(listOf("Gara-Aproape", "Gara-Departe", "Gara-FaraCoord"), r)
    }

    @Test fun realDatasetContainsKnownStations() {
        val slugs = StationsDataset.find("brasov").map { it.slug }
        assertTrue("Brasov" in slugs)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.data.StationsDatasetTest"`
Expected: FAIL (unresolved `StationsDataset`).

- [ ] **Step 3: Implement the dataset index**

```kotlin
package ro.trenuri.infofer.data

import ro.trenuri.infofer.model.Station

object StationsDataset {
    /** Diacritic-insensitive folding: reuse the slug rule, lowercased, hyphens->spaces. */
    private fun fold(s: String): String =
        canonicalStationSlug(s).replace('-', ' ').lowercase()

    fun matchIn(stations: List<Station>, query: String, near: LatLon?, limit: Int): List<Station> {
        val q = fold(query).trim()
        if (q.isEmpty()) return emptyList()
        val prefix = ArrayList<Station>()
        val substring = ArrayList<Station>()
        for (st in stations) {
            val name = fold(st.name)
            when {
                name.startsWith(q) -> prefix.add(st)
                name.contains(q) -> substring.add(st)
            }
        }
        val matches = prefix + substring
        val ordered = if (near == null) matches else orderByDistance(matches, near)
        return ordered.take(limit)
    }

    /** Located matches sorted by ascending distance; coordless matches keep their (name) order at the end. */
    private fun orderByDistance(matches: List<Station>, near: LatLon): List<Station> {
        val located = matches.filter { it.lat != null && it.lon != null }
            .sortedBy { haversineKm(near, LatLon(it.lat!!, it.lon!!)) }
        val coordless = matches.filter { it.lat == null || it.lon == null }
        return located + coordless
    }

    fun find(query: String, near: LatLon? = null, limit: Int = 20): List<Station> =
        matchIn(ALL_STATIONS, query, near, limit)
}
```

Add to `InfoferClient.kt`:

```kotlin
import ro.trenuri.infofer.data.StationsDataset
import ro.trenuri.infofer.data.LatLon

// inside class InfoferClient:
/** Resolve free-text station input to canonical stations (offline, in-memory).
 *  When [near] is supplied, suggestions with coordinates are ordered by distance. */
fun findStations(query: String, near: LatLon? = null, limit: Int = 20): List<ro.trenuri.infofer.model.Station> =
    StationsDataset.find(query, near, limit)
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :infofer-client:jvmTest --tests "ro.trenuri.infofer.data.StationsDatasetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationsDataset.kt infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/InfoferClient.kt infofer-client/src/commonTest/kotlin/ro/trenuri/infofer/data/StationsDatasetTest.kt
git commit -m "feat(infofer-client): findStations(query) over bundled dataset"
```

---

### Task 5: App date type + shared `today` provider

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/common/AppDate.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/ui/TrainViewModel.kt`
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/common/AppDateTest.kt`
- Modify: `app/src/test/kotlin/ro/trenuri/app/ui/TrainViewModelTest.kt` (adapt `today` type)

**Interfaces:**
- Produces:
  - `data class AppDate(val year: Int, val month: Int, val day: Int)` with `fun format(): String` → `DD.MM.YYYY`.
  - Koin: `factory<AppDate>(named("today")) { … }` and a `Today = () -> AppDate` typealias provider.
  - `TrainViewModel.load(number: String, date: AppDate)` (public; called by `TabNavigator`), with `search(number)` delegating to it using the injected today.

- [ ] **Step 1: Write the failing test**

```kotlin
package ro.trenuri.app.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class AppDateTest {
    @Test fun formatsAsDayMonthYearZeroPadded() {
        assertEquals("05.06.2026", AppDate(2026, 6, 5).format())
        assertEquals("29.12.2026", AppDate(2026, 12, 29).format())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.common.AppDateTest"`
Expected: FAIL (unresolved `AppDate`).

- [ ] **Step 3: Implement `AppDate` and migrate `TrainViewModel`**

`AppDate.kt`:

```kotlin
package ro.trenuri.app.ui.common

data class AppDate(val year: Int, val month: Int, val day: Int) {
    fun format(): String {
        fun p(n: Int) = n.toString().padStart(2, '0')
        return "${p(day)}.${p(month)}.$year"
    }
}

typealias Today = () -> AppDate
```

In `TrainViewModel.kt`, change the constructor `today: () -> Triple<Int,Int,Int>` to `today: Today`, add `load`, and have `search` delegate:

```kotlin
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.app.ui.common.Today

class TrainViewModel(
    private val repository: TrainRepository,
    private val today: Today,
    private val messages: ErrorMessages,
) : ViewModel() {
    // ...state unchanged...

    fun search(number: String) = load(number, today())

    fun load(number: String, date: AppDate) {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        _state.value = TrainUiState.Loading
        searchJob = viewModelScope.launch {
            _state.value = when (val result = repository.load(trimmed, date.year, date.month, date.day)) {
                is TrainResult.Success -> TrainUiState.Success(result.itinerary)
                TrainResult.NotFound -> TrainUiState.Empty
                TrainResult.NetworkError -> TrainUiState.Error(messages.network)
                TrainResult.ParseError -> TrainUiState.Error(messages.parse)
            }
        }
    }
}
```

- [ ] **Step 4: Update `AppModule` and the existing TrainViewModel test for the new `today` type**

In `AppModule.kt` replace the `today = { Triple(...) }` lambda with one returning `AppDate(now.year, now.monthNumber, now.dayOfMonth)`. In `TrainViewModelTest.kt`, change every `today = { Triple(y,m,d) }` to `today = { AppDate(y,m,d) }`.

- [ ] **Step 5: Run all app tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (AppDate test + adapted TrainViewModel tests + Koin graph).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/ui/common/AppDate.kt app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt app/src/main/kotlin/ro/trenuri/app/ui/TrainViewModel.kt app/src/test/kotlin/ro/trenuri/app/ui/common/AppDateTest.kt app/src/test/kotlin/ro/trenuri/app/ui/TrainViewModelTest.kt
git commit -m "feat(app): AppDate + shared today provider; TrainViewModel.load(number,date)"
```

---

### Task 6: Station picker data layer (StationRepository + StationPickerViewModel)

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/data/StationProvider.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/InfoferStationProvider.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/StationRepository.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/station/StationPickerViewModel.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt`
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/station/StationPickerViewModelTest.kt`

**Interfaces:**
- Consumes: `InfoferClient.findStations`, `InfoferClient.findNearestStations`, `Station`, `LatLon` (`ro.trenuri.infofer.data.LatLon`), injected IO dispatcher.
- Produces:
  - `interface StationProvider { fun find(query: String, near: LatLon?): List<Station>; suspend fun nearest(lat: Double, lon: Double): List<Station> }`
  - `class StationRepository(provider, io) { fun find(query: String, near: LatLon?): List<Station>; suspend fun nearest(lat,lon): NearestResult }` where `sealed interface NearestResult { data class Ok(val stations: List<Station>); data object NetworkError; data object ParseError }`.
  - `class StationPickerViewModel(repository)` exposing `val suggestions: StateFlow<List<Station>>` updated by `fun onQueryChange(q: String)`, `fun setLocation(lat: Double, lon: Double)` (stores a `LatLon` used to distance-order future suggestions), and `val nearby: StateFlow<NearbyUiState>` updated by `fun loadNearby(lat,lon)`; `sealed interface NearbyUiState { Idle; Loading; data class Ready(List<Station>); data class Error(String) }`.

- [ ] **Step 1: Write the failing tests** (fake provider, no network)

```kotlin
package ro.trenuri.app.ui.station

import kotlinx.coroutines.test.*
import ro.trenuri.app.data.*
import ro.trenuri.infofer.model.Station
import kotlin.test.*

class StationPickerViewModelTest {
    private val all = listOf(Station("Brașov","Brasov"), Station("Buzău","Buzau"))
    private var lastNear: ro.trenuri.infofer.data.LatLon? = null
    private val provider = object : StationProvider {
        override fun find(query: String, near: ro.trenuri.infofer.data.LatLon?): List<Station> {
            lastNear = near
            return all.filter { it.slug.lowercase().contains(query.lowercase()) }
        }
        override suspend fun nearest(lat: Double, lon: Double) = all
    }
    private val repo = StationRepository(provider, UnconfinedTestDispatcher())

    @Test fun queryChangeUpdatesSuggestions() {
        val vm = StationPickerViewModel(repo)
        vm.onQueryChange("bra")
        assertEquals(listOf("Brasov"), vm.suggestions.value.map { it.slug })
    }

    @Test fun setLocationIsPassedToFind() {
        val vm = StationPickerViewModel(repo)
        vm.setLocation(44.43, 26.10)
        vm.onQueryChange("bra")
        assertEquals(ro.trenuri.infofer.data.LatLon(44.43, 26.10), lastNear)
    }

    @Test fun blankQueryClearsSuggestions() {
        val vm = StationPickerViewModel(repo)
        vm.onQueryChange("bra"); vm.onQueryChange("")
        assertTrue(vm.suggestions.value.isEmpty())
    }

    @Test fun loadNearbyReturnsReady() = runTest {
        val vm = StationPickerViewModel(repo)
        vm.loadNearby(45.0, 25.0)
        advanceUntilIdle()
        assertEquals(NearbyUiState.Ready(all), vm.nearby.value)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.station.StationPickerViewModelTest"`
Expected: FAIL (unresolved types).

- [ ] **Step 3: Implement provider, repository, ViewModel**

`StationProvider.kt`:

```kotlin
package ro.trenuri.app.data
import ro.trenuri.infofer.data.LatLon
import ro.trenuri.infofer.model.Station
interface StationProvider {
    fun find(query: String, near: LatLon?): List<Station>
    suspend fun nearest(lat: Double, lon: Double): List<Station>
}
```

`InfoferStationProvider.kt`:

```kotlin
package ro.trenuri.app.data
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.data.LatLon
import ro.trenuri.infofer.model.Station
class InfoferStationProvider(private val client: InfoferClient) : StationProvider {
    override fun find(query: String, near: LatLon?): List<Station> = client.findStations(query, near)
    override suspend fun nearest(lat: Double, lon: Double): List<Station> =
        client.findNearestStations(lat, lon)
}
```

`StationRepository.kt`:

```kotlin
package ro.trenuri.app.data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.Station

sealed interface NearestResult {
    data class Ok(val stations: List<Station>) : NearestResult
    data object NetworkError : NearestResult
    data object ParseError : NearestResult
}

class StationRepository(
    private val provider: StationProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun find(query: String, near: ro.trenuri.infofer.data.LatLon? = null): List<Station> =
        provider.find(query, near)

    suspend fun nearest(lat: Double, lon: Double): NearestResult = withContext(io) {
        try {
            NearestResult.Ok(provider.nearest(lat, lon))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InfoferNetworkException) {
            NearestResult.NetworkError
        } catch (e: InfoferParseException) {
            NearestResult.ParseError
        } catch (e: Exception) {
            NearestResult.ParseError
        }
    }
}
```

`StationPickerViewModel.kt`:

```kotlin
package ro.trenuri.app.ui.station
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ro.trenuri.app.data.NearestResult
import ro.trenuri.app.data.StationRepository
import ro.trenuri.infofer.model.Station

sealed interface NearbyUiState {
    data object Idle : NearbyUiState
    data object Loading : NearbyUiState
    data class Ready(val stations: List<Station>) : NearbyUiState
    data class Error(val message: String) : NearbyUiState
}

class StationPickerViewModel(private val repository: StationRepository) : ViewModel() {
    private val _suggestions = MutableStateFlow<List<Station>>(emptyList())
    val suggestions: StateFlow<List<Station>> = _suggestions.asStateFlow()
    private val _nearby = MutableStateFlow<NearbyUiState>(NearbyUiState.Idle)
    val nearby: StateFlow<NearbyUiState> = _nearby.asStateFlow()

    private var location: ro.trenuri.infofer.data.LatLon? = null
    private var lastQuery: String = ""

    /** Record the device location so future suggestions are distance-ordered; re-rank the current query. */
    fun setLocation(lat: Double, lon: Double) {
        location = ro.trenuri.infofer.data.LatLon(lat, lon)
        if (lastQuery.isNotBlank()) onQueryChange(lastQuery)
    }

    fun onQueryChange(query: String) {
        lastQuery = query
        _suggestions.value = if (query.isBlank()) emptyList() else repository.find(query, location)
    }

    fun loadNearby(lat: Double, lon: Double) {
        setLocation(lat, lon)
        _nearby.value = NearbyUiState.Loading
        viewModelScope.launch {
            _nearby.value = when (val r = repository.nearest(lat, lon)) {
                is NearestResult.Ok -> NearbyUiState.Ready(r.stations)
                NearestResult.NetworkError -> NearbyUiState.Error("Verifică conexiunea la internet.")
                NearestResult.ParseError -> NearbyUiState.Error("Nu am putut citi răspunsul de la infofer.")
            }
        }
    }
}
```

Wire in `AppModule.kt`:

```kotlin
single<StationProvider> { InfoferStationProvider(get()) }
single { StationRepository(get(), get(ioDispatcherQualifier)) }
viewModel { StationPickerViewModel(get()) }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.station.StationPickerViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/data/StationProvider.kt app/src/main/kotlin/ro/trenuri/app/data/InfoferStationProvider.kt app/src/main/kotlin/ro/trenuri/app/data/StationRepository.kt app/src/main/kotlin/ro/trenuri/app/ui/station/StationPickerViewModel.kt app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt app/src/test/kotlin/ro/trenuri/app/ui/station/StationPickerViewModelTest.kt
git commit -m "feat(app): station picker data layer (find + nearest) and ViewModel"
```

---

### Task 7: Itinerary search data layer + ViewModel

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/data/ItineraryProvider.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/InfoferItineraryProvider.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/ItineraryRepository.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/itinerary/ItineraryUiState.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/itinerary/ItineraryViewModel.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt`
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/itinerary/ItineraryViewModelTest.kt`

**Interfaces:**
- Consumes: `InfoferClient.searchItineraries`, `ItineraryOption`, `Station`, `AppDate`, injected IO dispatcher, `ErrorMessages`.
- Produces:
  - `fun interface ItineraryProvider { suspend fun search(fromSlug,toSlug,year,month,day): List<ItineraryOption> }`
  - `class ItineraryRepository(provider, io) { suspend fun search(fromSlug,toSlug,date: AppDate): ItineraryResult }`, `sealed interface ItineraryResult { data class Success(List<ItineraryOption>); data object Empty; data object NetworkError; data object ParseError }`
  - `class ItineraryViewModel(repository, messages)` exposing `val state: StateFlow<ItineraryUiState>` and `fun search(from: Station, to: Station, date: AppDate)`; `sealed interface ItineraryUiState { Idle; Loading; data class Success(List<ItineraryOption>); Empty; data class Error(String) }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package ro.trenuri.app.ui.itinerary

import kotlinx.coroutines.test.*
import ro.trenuri.app.data.*
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.model.*
import kotlin.test.*

class ItineraryViewModelTest {
    private val messages = object : ErrorMessages { override val network = "net"; override val parse = "parse" }
    private val bv = Station("Brașov","Brasov"); private val bn = Station("București Nord","Bucuresti-Nord")
    private val date = AppDate(2026,6,29)
    private val opt = ItineraryOption("08:00","10:45",165,0,
        listOf(ItineraryLeg("1733", TrainCategory.IR,"Bucuresti-Nord","08:00","Brasov","10:45")))

    private fun vmWith(provider: ItineraryProvider) =
        ItineraryViewModel(ItineraryRepository(provider, UnconfinedTestDispatcher()), messages)

    @Test fun successEmitsOptions() = runTest {
        val vm = vmWith { _,_,_,_,_ -> listOf(opt) }
        vm.search(bn, bv, date); advanceUntilIdle()
        assertEquals(ItineraryUiState.Success(listOf(opt)), vm.state.value)
    }
    @Test fun emptyListEmitsEmpty() = runTest {
        val vm = vmWith { _,_,_,_,_ -> emptyList() }
        vm.search(bn, bv, date); advanceUntilIdle()
        assertEquals(ItineraryUiState.Empty, vm.state.value)
    }
    @Test fun networkErrorMapsToMessage() = runTest {
        val vm = vmWith { _,_,_,_,_ -> throw InfoferNetworkException("x") }
        vm.search(bn, bv, date); advanceUntilIdle()
        assertEquals(ItineraryUiState.Error("net"), vm.state.value)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.itinerary.ItineraryViewModelTest"`
Expected: FAIL (unresolved types).

- [ ] **Step 3: Implement**

`ItineraryProvider.kt`:

```kotlin
package ro.trenuri.app.data
import ro.trenuri.infofer.model.ItineraryOption
fun interface ItineraryProvider {
    suspend fun search(fromSlug: String, toSlug: String, year: Int, month: Int, day: Int): List<ItineraryOption>
}
```

`InfoferItineraryProvider.kt`:

```kotlin
package ro.trenuri.app.data
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.model.ItineraryOption
class InfoferItineraryProvider(private val client: InfoferClient) : ItineraryProvider {
    override suspend fun search(fromSlug: String, toSlug: String, year: Int, month: Int, day: Int): List<ItineraryOption> =
        client.searchItineraries(fromSlug, toSlug, year, month, day)
}
```

`ItineraryRepository.kt`:

```kotlin
package ro.trenuri.app.data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.ItineraryOption

sealed interface ItineraryResult {
    data class Success(val options: List<ItineraryOption>) : ItineraryResult
    data object Empty : ItineraryResult
    data object NetworkError : ItineraryResult
    data object ParseError : ItineraryResult
}

class ItineraryRepository(
    private val provider: ItineraryProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(fromSlug: String, toSlug: String, date: AppDate): ItineraryResult = withContext(io) {
        try {
            val options = provider.search(fromSlug, toSlug, date.year, date.month, date.day)
            if (options.isEmpty()) ItineraryResult.Empty else ItineraryResult.Success(options)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InfoferNetworkException) {
            ItineraryResult.NetworkError
        } catch (e: InfoferParseException) {
            ItineraryResult.ParseError
        } catch (e: Exception) {
            ItineraryResult.ParseError
        }
    }
}
```

`ItineraryUiState.kt`:

```kotlin
package ro.trenuri.app.ui.itinerary
import ro.trenuri.infofer.model.ItineraryOption
sealed interface ItineraryUiState {
    data object Idle : ItineraryUiState
    data object Loading : ItineraryUiState
    data class Success(val options: List<ItineraryOption>) : ItineraryUiState
    data object Empty : ItineraryUiState
    data class Error(val message: String) : ItineraryUiState
}
```

`ItineraryViewModel.kt`:

```kotlin
package ro.trenuri.app.ui.itinerary
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ro.trenuri.app.data.ItineraryRepository
import ro.trenuri.app.data.ItineraryResult
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.Station

class ItineraryViewModel(
    private val repository: ItineraryRepository,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _state = MutableStateFlow<ItineraryUiState>(ItineraryUiState.Idle)
    val state: StateFlow<ItineraryUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun search(from: Station, to: Station, date: AppDate) {
        if (from.slug.isBlank() || to.slug.isBlank()) return
        job?.cancel()
        _state.value = ItineraryUiState.Loading
        job = viewModelScope.launch {
            _state.value = when (val r = repository.search(from.slug, to.slug, date)) {
                is ItineraryResult.Success -> ItineraryUiState.Success(r.options)
                ItineraryResult.Empty -> ItineraryUiState.Empty
                ItineraryResult.NetworkError -> ItineraryUiState.Error(messages.network)
                ItineraryResult.ParseError -> ItineraryUiState.Error(messages.parse)
            }
        }
    }
}
```

Wire in `AppModule.kt`:

```kotlin
single<ItineraryProvider> { InfoferItineraryProvider(get()) }
single { ItineraryRepository(get(), get(ioDispatcherQualifier)) }
viewModel { ItineraryViewModel(get(), get()) }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.itinerary.ItineraryViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/data/ItineraryProvider.kt app/src/main/kotlin/ro/trenuri/app/data/InfoferItineraryProvider.kt app/src/main/kotlin/ro/trenuri/app/data/ItineraryRepository.kt app/src/main/kotlin/ro/trenuri/app/ui/itinerary/ app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt app/src/test/kotlin/ro/trenuri/app/ui/itinerary/ItineraryViewModelTest.kt
git commit -m "feat(app): itinerary search data layer + ViewModel"
```

---

### Task 8: Station board data layer + ViewModel (with Plecări/Sosiri toggle)

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/data/BoardProvider.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/InfoferBoardProvider.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/data/BoardRepository.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/board/BoardUiState.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/board/BoardViewModel.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt`
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/board/BoardViewModelTest.kt`

**Interfaces:**
- Consumes: `InfoferClient.getStationBoard`, `StationBoard`, `BoardKind`, `BoardEntry`, `Station`, `AppDate`, injected IO dispatcher, `ErrorMessages`.
- Produces:
  - `fun interface BoardProvider { suspend fun board(stationSlug,kind: BoardKind,year,month,day): StationBoard }`
  - `class BoardRepository(provider, io) { suspend fun board(stationSlug,kind,date): BoardResult }`, `sealed interface BoardResult { data class Success(StationBoard); data object Empty; data object NetworkError; data object ParseError }`
  - `class BoardViewModel(repository, messages)` exposing `val state: StateFlow<BoardUiState>` and `val kind: StateFlow<BoardKind>`; `fun load(station: Station, date: AppDate)`, `fun setKind(kind: BoardKind)` (re-queries the last station/date). `sealed interface BoardUiState { Idle; Loading; data class Success(StationBoard); Empty; data class Error(String) }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package ro.trenuri.app.ui.board

import kotlinx.coroutines.test.*
import ro.trenuri.app.data.*
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.*
import kotlin.test.*

class BoardViewModelTest {
    private val messages = object : ErrorMessages { override val network = "net"; override val parse = "parse" }
    private val brasov = Station("Brașov","Brasov")
    private val date = AppDate(2026,6,29)
    private fun board(kind: BoardKind) = StationBoard("Brasov", kind,
        listOf(BoardEntry("1733", TrainCategory.IR, "Bucuresti-Nord", "12:34", 5, "3")))

    private fun vmWith(provider: BoardProvider) =
        BoardViewModel(BoardRepository(provider, UnconfinedTestDispatcher()), messages)

    @Test fun loadEmitsSuccess() = runTest {
        val vm = vmWith { _,kind,_,_,_ -> board(kind) }
        vm.load(brasov, date); advanceUntilIdle()
        assertTrue(vm.state.value is BoardUiState.Success)
    }
    @Test fun setKindRequeriesWithNewKind() = runTest {
        var lastKind: BoardKind? = null
        val vm = vmWith { _,kind,_,_,_ -> lastKind = kind; board(kind) }
        vm.load(brasov, date); advanceUntilIdle()
        vm.setKind(BoardKind.ARRIVALS); advanceUntilIdle()
        assertEquals(BoardKind.ARRIVALS, lastKind)
        assertEquals(BoardKind.ARRIVALS, vm.kind.value)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.board.BoardViewModelTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

`BoardProvider.kt`:

```kotlin
package ro.trenuri.app.data
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.StationBoard
fun interface BoardProvider {
    suspend fun board(stationSlug: String, kind: BoardKind, year: Int, month: Int, day: Int): StationBoard
}
```

`InfoferBoardProvider.kt`:

```kotlin
package ro.trenuri.app.data
import ro.trenuri.infofer.InfoferClient
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.StationBoard
class InfoferBoardProvider(private val client: InfoferClient) : BoardProvider {
    override suspend fun board(stationSlug: String, kind: BoardKind, year: Int, month: Int, day: Int): StationBoard =
        client.getStationBoard(stationSlug, kind, year, month, day)
}
```

`BoardRepository.kt`:

```kotlin
package ro.trenuri.app.data
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.InfoferNetworkException
import ro.trenuri.infofer.InfoferParseException
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.StationBoard

sealed interface BoardResult {
    data class Success(val board: StationBoard) : BoardResult
    data object Empty : BoardResult
    data object NetworkError : BoardResult
    data object ParseError : BoardResult
}

class BoardRepository(
    private val provider: BoardProvider,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun board(stationSlug: String, kind: BoardKind, date: AppDate): BoardResult = withContext(io) {
        try {
            val b = provider.board(stationSlug, kind, date.year, date.month, date.day)
            if (b.entries.isEmpty()) BoardResult.Empty else BoardResult.Success(b)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: InfoferNetworkException) {
            BoardResult.NetworkError
        } catch (e: InfoferParseException) {
            BoardResult.ParseError
        } catch (e: Exception) {
            BoardResult.ParseError
        }
    }
}
```

`BoardUiState.kt`:

```kotlin
package ro.trenuri.app.ui.board
import ro.trenuri.infofer.model.StationBoard
sealed interface BoardUiState {
    data object Idle : BoardUiState
    data object Loading : BoardUiState
    data class Success(val board: StationBoard) : BoardUiState
    data object Empty : BoardUiState
    data class Error(val message: String) : BoardUiState
}
```

`BoardViewModel.kt`:

```kotlin
package ro.trenuri.app.ui.board
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ro.trenuri.app.data.BoardRepository
import ro.trenuri.app.data.BoardResult
import ro.trenuri.app.ui.ErrorMessages
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.model.Station

class BoardViewModel(
    private val repository: BoardRepository,
    private val messages: ErrorMessages,
) : ViewModel() {
    private val _state = MutableStateFlow<BoardUiState>(BoardUiState.Idle)
    val state: StateFlow<BoardUiState> = _state.asStateFlow()
    private val _kind = MutableStateFlow(BoardKind.DEPARTURES)
    val kind: StateFlow<BoardKind> = _kind.asStateFlow()

    private var lastStation: Station? = null
    private var lastDate: AppDate? = null
    private var job: Job? = null

    fun load(station: Station, date: AppDate) {
        if (station.slug.isBlank()) return
        lastStation = station; lastDate = date
        run()
    }

    fun setKind(kind: BoardKind) {
        if (kind == _kind.value) return
        _kind.value = kind
        if (lastStation != null) run()
    }

    private fun run() {
        val station = lastStation ?: return
        val date = lastDate ?: return
        job?.cancel()
        _state.value = BoardUiState.Loading
        job = viewModelScope.launch {
            _state.value = when (val r = repository.board(station.slug, _kind.value, date)) {
                is BoardResult.Success -> BoardUiState.Success(r.board)
                BoardResult.Empty -> BoardUiState.Empty
                BoardResult.NetworkError -> BoardUiState.Error(messages.network)
                BoardResult.ParseError -> BoardUiState.Error(messages.parse)
            }
        }
    }
}
```

Wire in `AppModule.kt`:

```kotlin
single<BoardProvider> { InfoferBoardProvider(get()) }
single { BoardRepository(get(), get(ioDispatcherQualifier)) }
viewModel { BoardViewModel(get(), get()) }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.board.BoardViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/data/BoardProvider.kt app/src/main/kotlin/ro/trenuri/app/data/InfoferBoardProvider.kt app/src/main/kotlin/ro/trenuri/app/data/BoardRepository.kt app/src/main/kotlin/ro/trenuri/app/ui/board/ app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt app/src/test/kotlin/ro/trenuri/app/ui/board/BoardViewModelTest.kt
git commit -m "feat(app): station board data layer + ViewModel with Plecari/Sosiri toggle"
```

---

### Task 9: TabNavigator (cross-tab routing + state semantics)

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/nav/TabNavigator.kt`
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/nav/TabNavigatorTest.kt`

**Interfaces:**
- Consumes: `AppDate`, `Station`.
- Produces:
  - `enum class Tab { TREN, RUTE, STATIE }`
  - `class TabNavigator(initial: Tab = Tab.TREN, onOpenTrain: (String, AppDate) -> Unit, onOpenStation: (Station, AppDate) -> Unit)` with:
    - `val selectedTab: StateFlow<Tab>`
    - `fun select(tab: Tab)` — tab-bar switch; pushes current onto back-stack; never invokes callbacks (preserve semantics).
    - `fun openTrain(number: String, date: AppDate)` — invokes `onOpenTrain`, pushes current, selects `TREN` (overwrite semantics).
    - `fun openStation(station: Station, date: AppDate)` — invokes `onOpenStation`, pushes current, selects `STATIE`.
    - `fun back(): Boolean` — pop to previous tab; returns `false` when back-stack empty (caller lets the system handle Back).

- [ ] **Step 1: Write the failing tests**

```kotlin
package ro.trenuri.app.ui.nav

import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.Station
import kotlin.test.*

class TabNavigatorTest {
    private val date = AppDate(2026,6,29)
    private val brasov = Station("Brașov","Brasov")

    @Test fun openTrainSelectsTrenAndInvokesCallback() {
        var opened: Pair<String, AppDate>? = null
        val nav = TabNavigator(Tab.STATIE, onOpenTrain = { n,d -> opened = n to d }, onOpenStation = { _,_ -> })
        nav.openTrain("1733", date)
        assertEquals(Tab.TREN, nav.selectedTab.value)
        assertEquals("1733" to date, opened)
    }

    @Test fun openStationSelectsStatieAndInvokesCallback() {
        var opened: Station? = null
        val nav = TabNavigator(Tab.TREN, onOpenTrain = { _,_ -> }, onOpenStation = { s,_ -> opened = s })
        nav.openStation(brasov, date)
        assertEquals(Tab.STATIE, nav.selectedTab.value)
        assertEquals(brasov, opened)
    }

    @Test fun plainSelectDoesNotInvokeCallbacks() {
        var called = false
        val nav = TabNavigator(Tab.TREN, onOpenTrain = { _,_ -> called = true }, onOpenStation = { _,_ -> called = true })
        nav.select(Tab.RUTE)
        assertEquals(Tab.RUTE, nav.selectedTab.value)
        assertFalse(called)
    }

    @Test fun backReturnsToPreviousTabThenFalseWhenEmpty() {
        val nav = TabNavigator(Tab.TREN, onOpenTrain = { _,_ -> }, onOpenStation = { _,_ -> })
        nav.select(Tab.RUTE)        // back-stack: [TREN]
        nav.openTrain("1", date)     // back-stack: [TREN, RUTE], now TREN
        assertTrue(nav.back()); assertEquals(Tab.RUTE, nav.selectedTab.value)
        assertTrue(nav.back()); assertEquals(Tab.TREN, nav.selectedTab.value)
        assertFalse(nav.back())     // empty -> system handles
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.nav.TabNavigatorTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

```kotlin
package ro.trenuri.app.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ro.trenuri.app.ui.common.AppDate
import ro.trenuri.infofer.model.Station

enum class Tab { TREN, RUTE, STATIE }

class TabNavigator(
    initial: Tab = Tab.TREN,
    private val onOpenTrain: (String, AppDate) -> Unit,
    private val onOpenStation: (Station, AppDate) -> Unit,
) {
    private val _selectedTab = MutableStateFlow(initial)
    val selectedTab: StateFlow<Tab> = _selectedTab.asStateFlow()
    private val backStack = ArrayDeque<Tab>()

    private fun goto(tab: Tab) {
        if (tab != _selectedTab.value) {
            backStack.addLast(_selectedTab.value)
            _selectedTab.value = tab
        }
    }

    fun select(tab: Tab) = goto(tab)

    fun openTrain(number: String, date: AppDate) {
        onOpenTrain(number, date)
        goto(Tab.TREN)
    }

    fun openStation(station: Station, date: AppDate) {
        onOpenStation(station, date)
        goto(Tab.STATIE)
    }

    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        _selectedTab.value = prev
        return true
    }
}
```

> Note: `openTrain` toward the already-selected tab still fires the callback (overwrite) even though `goto` won't push/switch — that's correct: a follow always reloads the target.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.nav.TabNavigatorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/ui/nav/TabNavigator.kt app/src/test/kotlin/ro/trenuri/app/ui/nav/TabNavigatorTest.kt
git commit -m "feat(app): TabNavigator with overwrite/preserve/back-stack semantics"
```

---

### Task 10: Compose screens — shared widgets, three feature screens, move train screen

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/common/DatePickerField.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/common/ResultStates.kt` (shared Loading/Empty/Error renderers)
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/station/StationPickerField.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/itinerary/ItinerarySearchScreen.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/board/StationBoardScreen.kt`
- Move: `app/src/main/kotlin/ro/trenuri/app/ui/TrainDetailScreen.kt` → `app/.../ui/train/TrainDetailScreen.kt` (add `onStationClick: (Station) -> Unit` param and date picker)

**Interfaces:**
- Consumes: the ViewModels/UiStates from Tasks 5–8, `AppDate`, `Station`, `BoardKind`.
- Produces: stateless-ish composables driven by `collectAsStateWithLifecycle()`, each taking cross-tab callbacks:
  - `ItinerarySearchScreen(vm, picker, today, onTrainClick: (String) -> Unit)`
  - `StationBoardScreen(vm, picker, today, onTrainClick: (String) -> Unit)`
  - `TrainDetailScreen(vm, today, onStationClick: (Station) -> Unit)`
  - `StationPickerField(label, vm, onPicked: (Station) -> Unit, onRequestLocation: () -> Unit)`

> UI is verified on-device (no emulator). Tests in this task are limited to any pure mapping helpers; the composables themselves are exercised manually in Task 12.

- [ ] **Step 1: Implement `DatePickerField`**

Material3 date picker dialog returning `AppDate`. Default value passed in; on confirm calls `onDateChange(AppDate)`. Use `rememberDatePickerState`; convert epoch-millis (UTC) → `AppDate` via `kotlinx.datetime` (`Instant.fromEpochMilliseconds(...).toLocalDateTime(TimeZone.UTC)` then read year/monthNumber/dayOfMonth). Show the field as an `OutlinedTextField(readOnly=true, value=date.format())` with a trailing calendar icon that opens the dialog.

- [ ] **Step 2: Implement `ResultStates`** — small composables `LoadingState()`, `EmptyState(text)`, `ErrorState(text)` (a centered progress indicator / message) reused by all three screens.

- [ ] **Step 3: Implement `StationPickerField`**

`OutlinedTextField` bound to a local `query` state; on each change call `vm.onQueryChange(query)` and show `vm.suggestions` in a dropdown (`ExposedDropdownMenuBox` or a simple `DropdownMenu`); selecting an item calls `onPicked(station)` and sets the field text to `station.name`. A trailing location `IconButton` calls `onRequestLocation()`; render `vm.nearby` (Loading spinner / `Ready` list to pick from / `Error` text). Each `StationPickerField` instance gets its own `StationPickerViewModel` (`koinViewModel(key = label)`).

- [ ] **Step 4: Implement `ItinerarySearchScreen`**

Two `StationPickerField`s (From/To) + a swap `IconButton` (swaps the two held `Station?`s), a `DatePickerField`, and a "Caută" `Button` enabled only when both stations are picked; on click `vm.search(from, to, date)`. Render `vm.state`: Idle hint / Loading / Empty / Error / Success → `LazyColumn` of option cards. Each card shows `departureTime → arrivalTime`, duration (`"${m/60}h ${m%60}m"` when non-null), `if (changes == 0) "direct" else "$changes schimbări"`, and a row per leg with the category badge + a clickable train number (`Text(modifier = Modifier.clickable { onTrainClick(leg.trainNumber) })`).

- [ ] **Step 5: Implement `StationBoardScreen`**

One `StationPickerField` + `DatePickerField` + a `SingleChoiceSegmentedButtonRow` with **Plecări**/**Sosiri** mapped to `BoardKind` (calls `vm.setKind`). On station pick call `vm.load(station, date)`. Render `vm.state`: rows show `scheduledTime`, category badge, train number (clickable → `onTrainClick`), counterpart station (prefix `spre `/`dinspre ` by `vm.kind`), a delay badge reusing the Slice-A colour convention (`delayMinutes == null` neutral / `0` green / `>0` red), and `linia {track}` when present.

- [ ] **Step 6: Move and extend `TrainDetailScreen`**

Move the file to `ui/train/`, update its `package` to `ro.trenuri.app.ui.train`, add a `DatePickerField` above the input (default `today()`), pass the chosen date into `vm.load(number, date)`, and make each stop's station name clickable → `onStationClick(stop.station)`. Update the import in any references.

- [ ] **Step 7: Compile (headless) to catch type errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/ui/
git commit -m "feat(app): date picker, station picker, itinerary/board screens; move train screen"
```

---

### Task 11: Navigation shell — MainActivity rewrite, retained ViewModels, rename, theme, edge-to-edge

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/nav/AppScaffold.kt`
- Modify: `app/src/main/kotlin/ro/trenuri/app/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (label + Material3 theme)
- Create: `app/src/main/res/values/themes.xml` (Material3 theme)
- Modify: `app/src/main/kotlin/ro/trenuri/app/di/AppModule.kt` (provide `TabNavigator`-backing wiring if needed)

**Interfaces:**
- Consumes: `Tab`, `TabNavigator`, the three screens, the feature ViewModels (retained at Activity scope via `koinViewModel()` hoisted in the scaffold so they survive tab switches).
- Produces: `AppScaffold()` composable hosting the `NavigationBar` + body.

- [ ] **Step 1: Add a Material3 app theme and rename the label**

`res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.AlerteSosiriTrenuri" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

In `AndroidManifest.xml` set `android:label="Alerte Sosiri Trenuri"` and `android:theme="@style/Theme.AlerteSosiriTrenuri"`. (The Compose `MaterialTheme` supplies the real M3 styling; this base theme just removes the action bar and provides a stable name.)

- [ ] **Step 2: Implement `AppScaffold`**

Hoist the retained ViewModels once at the top (so they outlive tab switches), build the `TabNavigator` with callbacks wired to them, then render:

```kotlin
@Composable
fun AppScaffold() {
    val trainVm: TrainViewModel = koinViewModel()
    val itineraryVm: ItineraryViewModel = koinViewModel()
    val boardVm: BoardViewModel = koinViewModel()
    val today: Today = koinInject(qualifier = named("today")) // or inject a Today provider
    val nav = remember {
        TabNavigator(
            onOpenTrain = { number, date -> trainVm.load(number, date) },
            onOpenStation = { station, date -> boardVm.load(station, date) },
        )
    }
    val selected by nav.selectedTab.collectAsStateWithLifecycle()
    BackHandler(enabled = true) { if (!nav.back()) { /* allow default */ } }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected == Tab.TREN, { nav.select(Tab.TREN) },
                    icon = { Icon(Icons.Default.Train, null) }, label = { Text("Tren") })
                NavigationBarItem(selected == Tab.RUTE, { nav.select(Tab.RUTE) },
                    icon = { Icon(Icons.Default.SwapHoriz, null) }, label = { Text("Rute") })
                NavigationBarItem(selected == Tab.STATIE, { nav.select(Tab.STATIE) },
                    icon = { Icon(Icons.Default.Place, null) }, label = { Text("Stație") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (selected) {
                Tab.TREN -> TrainDetailScreen(trainVm, today, onStationClick = { nav.openStation(it, today()) })
                Tab.RUTE -> ItinerarySearchScreen(itineraryVm, today, onTrainClick = { nav.openTrain(it, today()) })
                Tab.STATIE -> StationBoardScreen(boardVm, today, onTrainClick = { nav.openTrain(it, today()) })
            }
        }
    }
}
```

> Cross-tab date: when following from a screen, pass *that screen's* selected date. Implement by having each screen expose its current `AppDate` (hoist the date state into `AppScaffold`, or read it back via the callback). Simplest: hoist the per-tab `AppDate` states into `AppScaffold` (three `remember { mutableStateOf(today()) }`), pass them down, and use the relevant one in the follow callbacks (e.g. `nav.openTrain(it, boardDate)` from the board). Adjust the callbacks accordingly.

- [ ] **Step 3: Rewrite `MainActivity` for edge-to-edge + Material3**

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppScaffold() } }
    }
}
```

Add `androidx.activity:activity-compose` `enableEdgeToEdge` import. Ensure screens consume the `Scaffold` inner padding (already via `Box(Modifier.padding(padding))`), so no status-bar overlap.

- [ ] **Step 4: Add icon/back dependencies if missing**

Ensure `libs.compose.material.icons.extended` (or core icons) is available for `Icons.Default.Train/SwapHoriz/Place`, and `androidx.activity.compose.BackHandler`. Add to `gradle/libs.versions.toml` + `app/build.gradle.kts` if not present.

- [ ] **Step 5: Verify Koin graph + build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: tests PASS (AppModuleTest still resolves the graph) and APK builds.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/ app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat(app): bottom-nav shell, retained VMs, rename to Alerte Sosiri Trenuri, edge-to-edge"
```

---

### Task 12: Location permission + GPS wiring for nearest stations

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (add `ACCESS_COARSE_LOCATION`)
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/station/LocationButton.kt` (permission + last-location helper)
- Modify: `StationPickerField` to call into it
- Modify: `app/build.gradle.kts` (add `play-services-location` OR use the platform `LocationManager` to avoid Play deps)

**Interfaces:**
- Produces: a composable `rememberLocationRequester(onLocation: (lat: Double, lon: Double) -> Unit, onDenied: () -> Unit)` that requests `ACCESS_COARSE_LOCATION` via `rememberLauncherForActivityResult` and reads last-known location via the platform `LocationManager` (no Google Play dependency — keeps the app dependency-light per project ethos).

- [ ] **Step 1: Add the permission** to the manifest:

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

- [ ] **Step 2: Implement the permission+location helper** using `ContextCompat.checkSelfPermission`, `ActivityResultContracts.RequestPermission`, and `LocationManager.getLastKnownLocation(NETWORK_PROVIDER ?: GPS_PROVIDER)`. On grant + location → `onLocation(lat, lon)`; on deny → `onDenied()` (the picker shows a brief rationale snackbar/text). If last-known is null, surface a "nu am putut obține locația" message.

- [ ] **Step 3: Wire into `StationPickerField`** — the location `IconButton` triggers the requester; the resulting `(lat,lon)` calls `vm.loadNearby(lat, lon)` (which also records the location); tapping a `nearby` result calls `onPicked(station)`. Additionally, in a `LaunchedEffect` when the field is first composed, **if `ACCESS_COARSE_LOCATION` is already granted**, read last-known location and call `vm.setLocation(lat, lon)` so typed suggestions are distance-ordered without requiring a GPS-button tap (never prompt for permission unprompted — only read if already granted).

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/
git commit -m "feat(app): location permission + GPS nearest-station picker"
```

---

### Task 13 (P3, nice-to-have): Per-tab query history

> Implement only if slice time allows; the interface keeps it isolated. Skip cleanly by not wiring it into screens.

**Files:**
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/history/QueryHistoryStore.kt`
- Create: `app/src/main/kotlin/ro/trenuri/app/ui/history/InMemoryQueryHistoryStore.kt` (and/or DataStore-backed)
- Test: `app/src/test/kotlin/ro/trenuri/app/ui/history/QueryHistoryStoreTest.kt`

**Interfaces:**
- Produces: `interface QueryHistoryStore<T> { fun recent(): List<T>; fun add(item: T) }` with cap=10, de-dup (most-recent-first). Per-tab item types: `TrainQuery(number)`, `RouteQuery(from: Station, to: Station)`, `StationQuery(station: Station, kind: BoardKind)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package ro.trenuri.app.ui.history
import kotlin.test.*
class QueryHistoryStoreTest {
    @Test fun capsAndDedupsMostRecentFirst() {
        val s = InMemoryQueryHistoryStore<String>(cap = 3)
        s.add("a"); s.add("b"); s.add("a"); s.add("c"); s.add("d")
        assertEquals(listOf("d","c","a"), s.recent()) // "b" evicted, "a" moved to front then capped
    }
}
```

- [ ] **Step 2: Run to verify failure** — `./gradlew :app:testDebugUnitTest --tests "ro.trenuri.app.ui.history.QueryHistoryStoreTest"` → FAIL.

- [ ] **Step 3: Implement** `QueryHistoryStore` + `InMemoryQueryHistoryStore` (a `LinkedHashSet`-style most-recent-first list with cap). De-dup by equality; re-adding moves to front.

- [ ] **Step 4: Run to verify pass.**

- [ ] **Step 5: Wire (optional)** selecting a history chip fills the form inputs only (does not auto-run): the screen sets its picker `Station?`/number/date state from the entry and waits for the user to press the action button.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/ro/trenuri/app/ui/history/ app/src/test/kotlin/ro/trenuri/app/ui/history/
git commit -m "feat(app): per-tab query history (inputs only, fill-not-run)"
```

---

### Task 14: Full verification + acceptance

**Files:** none (verification only) — plus a memory/README touch if warranted.

- [ ] **Step 1: Full headless suite**

Run: `./gradlew test :app:assembleDebug`
Expected: BUILD SUCCESSFUL; all `:infofer-client` and `:app` unit tests pass.

- [ ] **Step 2: Self-review the diff** against the spec acceptance criteria; confirm the CLAUDE.md boundary (grep `:app` for any `Ksoup`/`/ro-RO/`/`canonicalStationSlug` usage — there should be none in `:app`).

Run: `! grep -rn "ro-RO\|Ksoup\|canonicalStationSlug\|StationsDataset" app/src/main` → expect no matches.

- [ ] **Step 3: On-device acceptance (physical phone)** — run the manual checklist from the spec's Testing section (label, itinerary search + follow train, board toggle + follow train + Back preserves board, train stop → station board, typeahead + GPS, tab state retention across switches, edge-to-edge no overlap).

- [ ] **Step 4:** Once accepted, proceed to `finishing-a-development-branch` to merge `feat/android-slice-b`.

---

## Self-Review

**Spec coverage:**
- All three screens → Tasks 7 (itinerary), 8 (board), 10 (screens); train detail extended in Task 10. ✓
- Bottom navigation + retained VMs + cross-tab follow + overwrite/preserve/back/date-carry → Tasks 9 (TabNavigator) + 11 (scaffold). ✓
- Station dataset + `findStations` (in `infofer-client`) → Tasks 2, 3, 4. ✓
- Station picker (typeahead + GPS) → Tasks 6, 10, 12. ✓
- Distance-ordered suggestions (OSM coords) → `Station` coords + `LatLon`/haversine (Task 2), OSM Overpass match in generator (Task 3), distance-aware `findStations(query, near)` (Task 4), location threaded through picker (Tasks 6, 12). Graceful degrade when coords/location absent. ✓
- Date selection shared → Tasks 5, 10. ✓
- Rename (label only) + Material3 theme + edge-to-edge → Task 11. ✓
- Folded A follow-ups: dispatcher binding, DelayBanner/ParseError tests, srcDirs → Task 1; theme + edge-to-edge → Task 11. ✓
- History (P3) → Task 13. ✓
- Permissions (`ACCESS_COARSE_LOCATION`) → Task 12. ✓
- `openStation` slug correctness → handled: stops carry `Station(name, slug)`; if a stop's slug is blank, resolve via `findStations(name)` before `openStation` (verify in Task 10 Step 6 / Task 11).

**Placeholder scan:** No "TBD"/"implement later" in code steps; Task 13 is explicitly optional, not a placeholder. UI composables in Tasks 10–12 give concrete construction guidance with exact widgets and signatures (full pixel layout is verified on-device, per the no-emulator constraint).

**Type consistency:** `AppDate`, `Today`, `Tab`, `TabNavigator`, the `…Provider`/`…Repository`/`…Result`/`…UiState`/`…ViewModel` names and signatures are used identically across Tasks 5–11. `ioDispatcherQualifier` (Task 1) is reused by Tasks 6–8. `Station(name, slug)` model used consistently.

**Open verification carried into execution:** the derived-slug correctness gate (Task 3 Step 4) may surface normalization fixes that loop back to Task 2 — expected and bounded.
</content>
