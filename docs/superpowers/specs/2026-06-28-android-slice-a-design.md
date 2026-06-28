# Android Slice A — App skeleton + Train Detail (design)

> Date: 2026-06-28. Status: approved for planning.
> First of three Android sub-projects. Slice B = remaining informational
> screens; Slice C = the dynamic delay-aware alarm engine (the headline).
> Each sub-project gets its own spec → plan → build cycle.

## Goal

Stand up the Android app as a real, installable module that consumes the shared
`infofer-client` KMP library **natively**, and deliver one end-to-end
informational flow: **look up a train number → see its itinerary with the live
delay**.

The point of Slice A is to **de-risk the platform integration** — prove the
Kotlin Multiplatform client (Ktor + Ksoup) actually runs on a real Android
device and returns live data — before building the rest of the app on top of it.

## Non-goals (explicitly out of Slice A)

- Other informational screens (itinerary search, station board, nearest
  stations) — that is Slice B.
- The alarm engine, WorkManager, AlarmManager, notifications — that is Slice C.
- Date selection (a date picker); Slice A always queries **today**.
- Navigation library, multi-screen back stack — one feature, no nav graph yet.
- Full i18n. UI chrome is Romanian to match the data; a real localization pass
  is deferred per `CLAUDE.md`.
- Emulator / instrumented tests — the dev container has no `/dev/kvm`, so the
  on-device acceptance check is done manually on a physical phone.

## Architecture decisions

- **UI:** Jetpack Compose.
- **Pattern:** MVVM — a `ViewModel` exposing a single `StateFlow<TrainUiState>`,
  a thin `Repository` wrapping `InfoferClient`.
- **DI:** Koin (lightweight, no annotation processing, KMP-friendly).
- **SDK levels:** `minSdk 26`, `compileSdk 35`, `targetSdk 35` (installed SDK is
  android-35 / build-tools 35.0.0). `minSdk 26` is chosen now because Slice C
  will need notification channels and modern `AlarmManager` APIs.
- **The CLAUDE.md boundary holds:** no `:app` code touches Ktor, HTML, or
  infofer endpoints. Everything goes through `InfoferClient`'s typed methods.

### Making the client consumable on Android

`infofer-client` currently declares only a `jvm()` target. Slice A adds an
**`androidTarget()`** to the KMP module (the proper KMP path; chosen over having
`:app` depend on the `jvm()` artifact, which is unsupported and fragile):

- `androidMain` gets an **OkHttp** Ktor engine (robust standard on Android),
  with `HttpCookies` installed — mirroring the existing `jvmMain`
  `defaultInfoferClient()` factory that uses CIO. CIO stays on JVM; OkHttp on
  Android.
- The Android library namespace/manifest is added to the client module's Gradle
  config. Existing JVM tests and `commonMain` code are unchanged.

## Module structure

```
:infofer-client            (existing KMP lib)
   ├─ commonMain           unchanged — parsers, models, InfoferClient
   ├─ jvmMain              unchanged — CIO factory
   └─ androidMain   (NEW)  OkHttp factory: defaultInfoferClient()
:app                       (NEW Android application module)
   ├─ Compose UI           TrainDetailScreen (single feature)
   ├─ TrainViewModel       StateFlow<TrainUiState>
   ├─ TrainRepository      wraps InfoferClient
   └─ Koin module          InfoferClient → Repository → ViewModel
```

`settings.gradle.kts` gains `include(":app")`.

## Data flow

```
TrainDetailScreen ──onSearch(number)──▶ TrainViewModel
                                          │ repository.getTrain(number, today)
                                          ▼
                                       TrainRepository ──▶ InfoferClient.getTrain(
                                          number, year, month, day)   [Dispatchers.IO]
                                          ▼
                          TrainUiState: Idle | Loading | Success | Empty | Error
```

`today` is derived on-device (local date) and split into year/month/day for the
client's `getTrain(trainNumber, year, month, day)` signature.

## Screen & rendering

Single screen, two logical regions (input always visible; result region swaps on
state):

- **Input:** a `TextField` for the train number (e.g. `5568`) + a "Caută" button.
  Empty/blank input is a no-op.
- **Result region** by state:
  - `Idle` — hint text.
  - `Loading` — progress indicator.
  - `Empty` — friendly "train not found / no data" message.
  - `Error(message)` — mapped human message (see Error mapping).
  - `Success(TrainItinerary)`:
    - **Header:** train number + `category` badge.
    - **Live-delay banner** — the headline. Three *distinct* states derived from
      `TrainBranch.delay: Delay?`:
      - `null` → "Fără date live" (train not currently running). **Must not** be
        rendered as on-time. This preserves the data-layer correctness fix where
        absent live data is `null`, not `Delay(0)`.
      - `minutes == 0` → "La timp" (green).
      - `minutes > 0` → "{minutes} min întârziere · raportat la {reportedAt}"
        (red).
    - **Stop list:** for each `TrainStop` — station name, arrival/departure
      (`HH:MM`), `linia {track}` when present, `km {km}` when present, and
      per-stop `status` colouring (`ON_TIME` green / `DELAYED` red / `UNKNOWN`
      neutral).
    - **Multi-branch trains:** one section per `TrainBranch`, each with its own
      delay banner and stop list (a train can split).

UI chrome strings are Romanian; Romanian data terms (`întârziere`, `sosire`,
`plecare`) are shown as-is.

## Error handling & threading

- `InfoferNetworkException` → "Verifică conexiunea la internet."
- `InfoferParseException` → "Nu am putut citi răspunsul de la infofer." (and the
  exception is logged for diagnosis — a parse failure means selectors drifted).
- Empty/unrecognized train (no branches) → `Empty` state, not an error.
- `getTrain` executes off the main thread (`Dispatchers.IO`).
- Coroutine `CancellationException` is allowed to propagate (the ViewModel scope
  cancels in-flight requests on clear / re-search); it is never swallowed by the
  error mapping.

## Testing

**Headless, in-container (CI-style — must pass before the on-device check):**

- `TrainViewModel` unit tests with a **fake `TrainRepository`** (no network),
  covering every UI state transition: Idle → Loading → Success / Empty / Error,
  and specifically the three delay renderings — `null` (no live data) vs
  `minutes == 0` (on time) vs `minutes > 0` (delayed). The `null`-vs-on-time
  distinction is asserted explicitly.
- `TrainRepository` test mapping `InfoferClient` outcomes / exceptions to the
  repository result type (network vs parse vs empty).
- Run via Gradle unit tests (`:app:testDebugUnitTest`, `:infofer-client` tests
  unchanged). No device, no emulator.

**On a physical phone (manual acceptance — the container cannot do this):**

1. Build the debug APK (`:app:assembleDebug`).
2. Install on a real Android phone.
3. Look up a **currently-running** train and confirm the live delay banner
   renders correctly (delayed / on-time), and a non-running train shows "Fără
   date live".

No instrumented or emulator tests in Slice A (no `/dev/kvm` in the container).

## Acceptance criteria

- `:infofer-client` builds for both `jvm` and `android` targets; existing JVM
  tests stay green.
- `:app` builds a debug APK headlessly in the container.
- `TrainViewModel` / `TrainRepository` unit tests pass headlessly, including the
  delay `null` vs on-time vs delayed distinction.
- Manually on a phone: entering a running train number shows its stops and the
  correct live delay; a non-running train shows "Fără date live"; a bad number
  shows the empty state; airplane mode shows the connection error.

## Follow-ups deferred to later slices

- Date picker (query dates other than today) — Slice B or a small follow-up.
- Train lookup by search/typeahead instead of raw number entry — Slice B.
- Real localization pass — later.
- Everything alarm-related — Slice C.
