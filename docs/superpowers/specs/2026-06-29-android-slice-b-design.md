# Android Slice B — Itinerary search, Station board, Nearest stations (design)

> Date: 2026-06-29. Status: approved for planning.
> Second of three Android sub-projects. Slice A delivered the app skeleton +
> train-detail flow (merged to `main`). Slice C is the dynamic delay-aware alarm
> engine (the headline). Each sub-project gets its own spec → plan → build cycle.

## Goal

Turn the single-screen Slice A app into a complete **informational** app: add the
remaining infofer features — **itinerary search**, **station board**, and
**nearest stations** — behind a bottom-navigation shell, with cross-tab linking
and per-tab state retention. Also: a shared station picker (typeahead +
GPS), shared date selection, the app rename, and the deferred Slice A
follow-ups.

The app should feel "done" as an info app after Slice B, so that Slice C can
focus purely on the alarm engine.

## Decisions (from brainstorming)

- **Scope:** all three remaining screens in this slice (not split into B1/B2).
- **Navigation:** Material3 bottom navigation bar, three tabs — **Tren** /
  **Rute** / **Stație**. Nearest-stations is a GPS button inside the station
  picker, not a standalone tab. Slice C will add an **Alarme** tab.
- **Station picking:** bundle the data.gov.ro static station dataset and expose a
  new typed client method `findStations(query)`; no app-side slugifying.
- **Date:** full date selection now (default today), shared across all tabs, so
  Slice C inherits date handling for alarms.
- **Rename:** launcher label → **"Alerte Sosiri Trenuri"** (label only;
  `applicationId` `ro.trenuri.app` and package names unchanged).
- **Cross-tab navigation:** follow a train number (from Rute/Stație → Tren) and a
  station name (from Tren → Stație), preserving each tab's memory until a
  navigation action overwrites it.
- **History (nice-to-have, P3):** per-tab recent-query history that stores only
  form inputs (not result data), to re-init the form for adjustment + re-run.

## Non-goals (explicitly out of Slice B)

- The alarm engine, WorkManager, AlarmManager, notifications — that is Slice C.
- Changing `applicationId` / package names (rename is label-only).
- Full Navigation-Compose route graph (a lightweight custom tab host is used —
  see Navigation).
- A bundled offline *schedule* base (only the *station list* is bundled now).
- Time-of-day filtering for itinerary search (`TimeSelectionId`/`MinutesInDay`
  stay `0`); date is selectable, intra-day time filtering is deferred.
- Full i18n. UI chrome stays Romanian to match the data, per `CLAUDE.md`.
- Emulator / instrumented tests — the dev container has no `/dev/kvm`; on-device
  acceptance is manual on a physical phone.

## Architecture

### The CLAUDE.md boundary holds

No `:app` code touches Ktor, HTML, infofer endpoints, or slug derivation. The
station-name → canonical-slug resolution becomes a **new typed client method**
on `InfoferClient`, alongside the existing four. Apps call typed methods only.

### Data layer — station dataset & `findStations`

The client gains the ability to resolve free-text station names to infofer's
canonical slugs (ASCII, de-diacriticized, hyphenated — e.g. `Bucuresti-Nord`),
which both itinerary search and the station board require.

- **Source:** the official **data.gov.ro** "Mers tren — SNTFC CFR Călători
  S.A." passenger timetable (OGL-ROU-1.0, openly licensed) — `CLAUDE.md` data
  source #1. Current edition 2025-2026 (11.9 MB XML), direct download:
  `https://data.gov.ro/dataset/c4f71dbb-de39-49b2-b697-5b60a5f299a2/resource/0f67143e-bb88-4a06-8e7a-b35b1eb91329/download/trenuri-2025-2026_sntfc.xml`
  (HEAD-checked reachable from the build environment, returns `text/xml`). The
  station list is the set of unique stations referenced across all train routes
  in this timetable.
- **One-time conversion script** (committed; run manually, NOT at app build or
  runtime): fetches the source, derives each station's canonical slug
  (de-diacritic + hyphenate), and emits a compact asset `stations.json`
  (`name`, `slug`, optional `county` for disambiguation).
- **Asset location:** `infofer-client` **commonMain resources**, so iOS/web
  reuse it later. Android consumes it via the existing KMP dependency.
- **New method:** `InfoferClient.findStations(query: String): List<Station>` —
  loads `stations.json` once (lazily, cached in memory) and filters
  diacritic-insensitively (prefix-first, then substring), returning
  `Station(name, slug)`. No per-query network; fully offline; sub-millisecond
  over ~1,000–1,500 stations.
- **Correctness gate (static, not runtime):** the conversion script validates a
  representative sample of derived slugs against infofer (and against the
  existing `itineraries-*`/`station-board-*` fixtures) so we never ship names
  that resolve to nothing. A station that still fails resolution at runtime
  surfaces a graceful "not found" — never silently-wrong data.
- **Environment dependency:** generating `stations.json` needs a one-time fetch
  of the data.gov.ro file (URL above; reachability confirmed). The committed
  `stations.json` is the source of truth thereafter; re-run the script only to
  refresh against a newer timetable edition.

> Captured HTML fixtures remain immutable ground truth (`CLAUDE.md`). The new
> `stations.json` is a *generated, committed* asset, regenerated only by
> re-running the conversion script against the live source — not hand-edited to
> make a test pass.

### Navigation — lightweight custom tab host

A single `MainActivity` hosts a `Scaffold` with a Material3 `NavigationBar`
(three destinations) and a body that renders the selected tab. A full
Navigation-Compose route graph is **not** used: programmatic "load a target into
a tab, then switch, while preserving the other tabs" fits a small custom host
better than route-arg plumbing, and the host's logic becomes a plain
unit-testable class.

- **Tab state retention:** each tab's ViewModel is **retained for the Activity
  lifetime** (Koin-provided; not recreated on tab switch). A tab's state lives in
  its `StateFlow` (form inputs + results), so switching tabs always restores
  exactly what that tab last had. Inactive tabs leave the composition but their
  ViewModel — and thus state — survives. Scroll position is preserved with
  `rememberSaveable`.
- **`TabNavigator` (testable):** owned by the host; holds `selectedTab` and a
  tab back-stack; exposes intents:
  - `openTrain(number, date)` → `TrainViewModel.load(number, date)`; select Tren.
  - `openStation(station, date)` → `BoardViewModel.load(station, date)`; select
    Stație.
- **Cross-tab links:** screens emit callbacks routed to the intents:
  - Itinerary legs + board rows → `onTrainClick(number)` ⇒ `openTrain`.
  - Train-detail stops → `onStationClick(station)` ⇒ `openStation`.
- **Overwrite semantics:** a *follow* action overwrites the target tab's state
  with the new target; a plain tab-bar switch never overwrites. (Tren shows 5568;
  tapping train 1733 on a board makes Tren show 1733; merely tapping the Tren tab
  keeps whatever was there.)
- **Date carried along:** following a train from a board/itinerary uses *that
  screen's* selected date; following a station from a train stop uses the train's
  date — so the destination context matches the source.
- **Back behavior:** the tab back-stack means system Back returns to the
  previously selected tab (follow a train from the board → Back returns to the
  board), instead of jumping straight to exit.

### `openStation` slug correctness (flagged)

`openStation` needs the stop's slug. Implementation verifies
`TrainResultParser` populates `TrainStop.station.slug`; if a stop carries only a
display name, `openStation` resolves it via `findStations(name)` before loading
the board — never guessing silently.

## Module / file structure

```
:infofer-client
   ├─ commonMain
   │    ├─ resources/stations.json            (NEW generated, committed asset)
   │    ├─ InfoferClient.findStations(query)   (NEW typed method)
   │    └─ parse/StationsDataset.kt            (NEW loader/index over the asset)
   └─ (conversion script committed under tools/ or scripts/)
:app
   ui/
     nav/        AppScaffold (NavigationBar + body), Destinations, TabNavigator
     station/    StationPickerField (typeahead + GPS), StationPickerViewModel
     itinerary/  ItinerarySearchScreen, ItineraryViewModel, ItineraryUiState
     board/      StationBoardScreen, BoardViewModel, BoardUiState (Plecări/Sosiri)
     train/      (existing TrainDetailScreen et al, moved under train/)
     common/     DatePickerField, shared Empty/Error/Loading renderers
     history/    QueryHistoryStore + per-tab history models (P3, nice-to-have)
   data/
     StationRepository    (findStations + findNearestStations)
     ItineraryRepository  (wraps searchItineraries)
     BoardRepository      (wraps getStationBoard)
     (existing TrainRepository unchanged)
```

## Screens & rendering

All screens reuse the Slice A state-machine shape: **Idle · Loading · Empty ·
Error · Success**, with a thin ViewModel exposing one `StateFlow<…UiState>` and a
Repository wrapping `InfoferClient` (off the main thread via an injected
dispatcher — see Follow-ups).

### Tren tab

Existing `TrainDetailScreen` and live-delay rendering, **extended** with the
shared `DatePickerField` (default today) so a train can be queried on a chosen
date. Stops expose `onStationClick(station)` for cross-tab navigation.

### Rute tab — itinerary search

- **Inputs:** two `StationPickerField`s (From / To with a swap button),
  `DatePickerField`, "Caută". Blank from/to is a no-op.
- **Success:** list of `ItineraryOption` cards — departure→arrival times,
  duration, `direct` / `N schimbări`, and each leg's train number + category
  badge. Each leg's train number is tappable (`onTrainClick`).
- **Empty:** no options found for the pair/date.

### Stație tab — station board

- **Inputs:** one `StationPickerField`, `DatePickerField`, a **Plecări / Sosiri**
  segmented toggle (maps to `BoardKind`). Re-queries on toggle change.
- **Success:** `BoardEntry` rows — scheduled time, train number + category,
  counterpart station (spre/dinspre), delay badge (reusing Slice A's delay
  colouring conventions), `linia {track}` when present. Train number tappable
  (`onTrainClick`).
- **Empty:** no departures/arrivals for the station/date.

### Shared components

- **`StationPickerField`:** a text field with a typeahead dropdown backed by
  `findStations(query)` (diacritic-insensitive), plus a **GPS button** that calls
  `findNearestStations(lat, lon)` and shows a short pick-list. Either path yields
  a `Station(name, slug)`; the screen holds the resolved slug. Typing a name not
  in the dataset is allowed but flagged as unresolved.
- **`DatePickerField`:** Material3 date-picker dialog; default today; feeds
  `year/month/day` to the client. Shared by all three tabs.

## History (nice-to-have, P3)

A per-tab recent-query store of **form inputs only** (never result data):

- **Tren:** recent train numbers.
- **Rute:** recent (from, to) station pairs.
- **Stație:** recent (station, kind).

Behaviour: selecting a history entry **fills the form inputs** and does **not**
auto-run, so the user can adjust (e.g. change the date) before querying.
Abstracted behind a `QueryHistoryStore` interface (capped to ~10 entries,
de-duplicated, most-recent-first) with a swappable persistence impl (Jetpack
DataStore Preferences or a small persisted JSON). Tested with a fake store. May
be deferred within the slice if time-constrained; the interface keeps it from
bloating the core.

## Folded-in Slice A follow-ups

- **DelayBanner tests:** negative-minutes and null-`reportedAt` cases (T4).
- **TrainViewModel `ParseError` test** (T5).
- **Material3 theme + `enableEdgeToEdge()`:** fix the API-35 status-bar overlap
  (the manifest currently uses `Theme.Material.Light.NoActionBar`; move to a
  Material3 theme and enable edge-to-edge). Verify on-device alongside the new
  scaffold.
- **Explicit `CoroutineDispatcher` Koin binding:** inject the IO dispatcher
  rather than referencing `Dispatchers.IO` directly, so repositories/ViewModels
  are testable with a test dispatcher.
- **Redundant `srcDirs`:** remove the unnecessary `sourceSets[...].java.srcDirs`
  entries in `app/build.gradle.kts`.

## Permissions

Nearest-stations requires `ACCESS_COARSE_LOCATION` (added to the manifest;
runtime-requested on first GPS-button use). If denied, show a brief rationale;
typeahead search still works without location.

## Error handling & threading

- Reuse Slice A's mapping: `InfoferNetworkException` → "Verifică conexiunea la
  internet."; `InfoferParseException` → "Nu am putut citi răspunsul de la
  infofer." (logged, since a parse failure means selectors drifted).
- Empty/unrecognized results → `Empty` state, not an error.
- Unresolved station (no slug) → friendly "station not found" prompt, not a crash.
- All network calls run off the main thread via the **injected** dispatcher.
- `CancellationException` propagates (stale searches cancel on re-query / tab
  overwrite); it is never swallowed by error mapping. Re-querying cancels the
  previous in-flight request per tab.

## Testing

**Headless, in-container (CI-style — must pass before on-device check):**

- `findStations` over a small fixture dataset: prefix vs substring matching,
  diacritic-insensitivity, slug correctness for known stations, empty query.
- `ItineraryViewModel` / `BoardViewModel` state-machine tests with fake
  repositories: Idle → Loading → Success / Empty / Error; board toggle re-query;
  stale-request cancellation.
- `ItineraryRepository` / `BoardRepository` mapping of client outcomes/exceptions
  (network vs parse vs empty), using the injected test dispatcher.
- `TabNavigator` tests: `openTrain`/`openStation` set the target ViewModel state
  and select the right tab; overwrite semantics (follow overwrites, tab-switch
  preserves); back-stack returns to the previous tab.
- `StationPickerViewModel`: typeahead query → results; GPS path yields slugs;
  unresolved-name handling.
- Folded follow-up tests: DelayBanner negative/null-`reportedAt`,
  TrainViewModel `ParseError`.
- `QueryHistoryStore` (if implemented): cap, de-dup, ordering; fill-not-run.

**On a physical phone (manual acceptance — container cannot do this):**

1. Build + install the debug APK; confirm launcher label reads "Alerte Sosiri
   Trenuri".
2. Rute: search a real pair (e.g. Bucuresti-Nord → Brasov) for today; tap a
   leg's train number → lands on Tren showing that train.
3. Stație: open a station board; toggle Plecări/Sosiri; tap a train → Tren;
   Back returns to the board with its state intact.
4. Tren: tap a stop's station → lands on Stație showing that station's board.
5. Station picker: typeahead resolves a diacritic name; GPS button lists nearby
   stations and fills the field.
6. Switch tabs via the bar and confirm each tab retains its last inputs/results.
7. Edge-to-edge: no status-bar overlap.

No instrumented/emulator tests in Slice B (no `/dev/kvm`).

## Acceptance criteria

- `:infofer-client` gains `findStations` + committed `stations.json`; existing
  JVM/Android targets and tests stay green; the boundary is intact (no app-side
  slug derivation).
- `:app` builds a debug APK headlessly; all new unit tests pass headlessly,
  including `TabNavigator` overwrite/preserve semantics and `findStations`
  matching.
- Bottom navigation with Tren / Rute / Stație; each tab retains state across
  switches; cross-tab follow (train number, station name) works with the
  specified overwrite + date-carry + back behaviour.
- Itinerary search, station board (with Plecări/Sosiri), shared date picker, and
  the typeahead+GPS station picker function on a real phone.
- Launcher label is "Alerte Sosiri Trenuri"; edge-to-edge has no overlap.
- All folded Slice A follow-ups are addressed.

## Follow-ups deferred to later slices

- Time-of-day filtering for itinerary search.
- Bundled offline schedule base (beyond the station list).
- Real localization pass.
- Everything alarm-related — Slice C (which adds the Alarme tab and reuses the
  date selection, station picker, and `getTrain` polling).
</content>
</invoke>
