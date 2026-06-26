# infofer-client

Kotlin Multiplatform client for the [Infofer](https://mersultrenurilor.infofer.ro) rail-information website. Provides parsed, typed data for the four main queries the Romanian National Railways (CFR) site exposes.

## Purpose

This module wraps the Infofer site's HTML/JSON endpoints and returns structured Kotlin data-class objects suitable for use by the alarm engine (and any other consumer). All parsing, data modelling, and business logic live in **`commonMain`** so the client can run on JVM, Android, iOS, and JS without changes.

See [`docs/infofer-api.md`](../docs/infofer-api.md) for the full endpoint contract, request/response shapes, and the two-step token/cookie POST flow that all mutating calls require.

## Public API

### `InfoferClient`

`InfoferClient(session: InfoferSession)` is the main facade. All methods are `suspend` functions.

```kotlin
// Full train itinerary with per-stop delay data
suspend fun getTrain(trainNumber: String, year: Int, month: Int, day: Int): TrainItinerary

// Route search: returns ordered list of travel options (direct and with changes)
suspend fun searchItineraries(
    fromSlug: String, toSlug: String, year: Int, month: Int, day: Int,
): List<ItineraryOption>

// Live departure or arrival board for a station
suspend fun getStationBoard(
    stationName: String, kind: BoardKind, year: Int, month: Int, day: Int,
): StationBoard

// Nearest stations to a GPS coordinate (JSON endpoint)
suspend fun findNearestStations(latitude: Double, longitude: Double): List<Station>
```

Station names passed to `getTrain`, `searchItineraries`, and `getStationBoard` must be the URL-slug form used by Infofer (e.g., `"Bucuresti-Nord"` not `"București Nord"`). Use `Station.slug` from `findNearestStations` results, or apply the `stationSlug()` helper from `ro.trenuri.infofer.util`.

### `defaultInfoferClient()` (JVM / Android)

```kotlin
// jvmMain — ro.trenuri.infofer
fun defaultInfoferClient(): InfoferClient
```

Constructs a ready-to-use `InfoferClient` backed by a Ktor `CIO` engine with cookie support. This is the normal entry point for JVM-based callers. Android, iOS, and JS targets will each provide their own equivalent factory using their platform's Ktor engine (see below).

### Error handling

All methods throw `InfoferParseException` (subclass of `InfoferException`) if the site returns unexpected HTML. Network errors surface as `InfoferNetworkException`.

## Data models (`commonMain`)

```
Station(name, slug)
TrainItinerary(trainNumber, category, branches: List<TrainBranch>)
  TrainBranch(from, to, delay: Delay?, stops: List<TrainStop>)
    Delay(minutes, reportedAt)
    TrainStop(station, km, track, arrival, departure, status: StopStatus)
ItineraryOption(departureTime, arrivalTime, durationMinutes, changes, legs: List<ItineraryLeg>)
  ItineraryLeg(trainNumber, category, departureStation, departureTime, arrivalStation, arrivalTime)
StationBoard(station, kind: BoardKind, entries: List<BoardEntry>)
  BoardEntry(trainNumber, category, counterpartStation, scheduledTime, delayMinutes, track)
```

`TrainCategory` — `R, RE, RA, IR, IRN, IC, ICN, RR, IRA, RRF, OTHER`  
`BoardKind` — `DEPARTURES, ARRIVALS`  
`StopStatus` — `ON_TIME, DELAYED, UNKNOWN`

## Architecture: KMP + Ktor

```
commonMain
 ├─ InfoferClient.kt          (facade — four suspend methods)
 ├─ InfoferException.kt       (InfoferException, InfoferParseException, InfoferNetworkException)
 ├─ model/Models.kt           (all data classes and enums)
 ├─ net/InfoferSession.kt     (Ktor HTTP layer: getPage, postResult)
 ├─ net/PageTokens.kt         (extracts __RequestVerificationToken + ConfirmationKey)
 ├─ parse/TrainResultParser.kt
 ├─ parse/ItinerariesParser.kt
 ├─ parse/StationBoardParser.kt
 └─ parse/NearestStationsParser.kt

jvmMain
 └─ InfoferClientFactory.kt   (defaultInfoferClient() using Ktor CIO engine)
```

**Multi-target plan:** when Android, iOS, or JS clients are added, each target supplies its own Ktor engine factory (e.g., `OkHttp` for Android, `Darwin` for iOS, `Js` for browser/Node). All parsing and session logic stay in `commonMain` unchanged.

## Testing

Tests run against **static HTML fixtures** stored in `src/commonTest/resources/fixtures/`. The four fixtures — `train-result-5568.html`, `itineraries-bucuresti-brasov.html`, `station-board-brasov.html`, `nearest-stations-bucuresti.html` — are captures of real Infofer responses and are treated as **immutable ground truth**. Do not update them unless you are re-snapshotting from the live site in a deliberate fixture-refresh exercise.

Each parser has a corresponding test that asserts structural invariants (non-empty result lists, non-blank times, correct entry counts). `TrainResultParserTest` additionally asserts the exact delay value (`2 min`) and exact departure time (`9:21`) that are the headline-feature guarantees of the alarm engine.

### Running tests

```bash
./gradlew :infofer-client:jvmTest
```

All 25 tests run offline.

### Live smoke test (opt-in)

`LiveSmokeTest` hits the real Infofer endpoint and is **skipped by default**. To run it manually:

```bash
INFOFER_LIVE=1 ./gradlew :infofer-client:jvmTest --tests "*LiveSmokeTest"
```

Do not enable this in CI — the test calls the live site once and therefore must respect the polite-client User-Agent rules enforced by `InfoferSession`.

## Reference

- Endpoint contract: [`docs/infofer-api.md`](../docs/infofer-api.md)
