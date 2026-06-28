# CLAUDE.md — Trenuri (Romanian Rail Information App)

Guidance for AI agents working in this repository. Read this before making
changes. See `README.md` for the full product/architecture overview.

## What this project is

A free, **informational** app for Romanian rail (inspired by CFR Călători /
Mersul Trenurilor) covering search, station boards, train details with live
delays, and **dynamic delay-aware alarms**. **No purchasing, accounts, or
payments** — do not add them.

## Architecture at a glance

Three clients sharing one data layer; **no backend of our own**.

- **`infofer-client`** — shared **Kotlin Multiplatform** data layer (Ktor +
  Ksoup). The single source of truth for talking to infofer; reused natively by
  Android/iOS and via Kotlin/JS by the web app.
- **Android app (Kotlin)** — phase 1: informational screens + the on-device
  alarm engine.
- **iOS app** — phase 2. **Web app** — phase 3 (informational only).

Build order: **shared client → Android → iOS → web.** Until told otherwise,
work targets the shared client and Android.

## The one rule that matters most

**Apps NEVER touch raw infofer HTML or endpoints directly.** All scraping,
parsing, request shaping, and the fragility that comes with unofficial endpoints
live **only** inside `infofer-client`, behind typed methods:

- `searchItineraries(...)`
- `getTrain(trainNumber, date)` — returns itinerary **and live delay**
- `getStationBoard(station, ...)`
- `findStations(query)` / nearest-station lookup

If a feature needs new data, extend the client's interface — do not scrape from
an app. This keeps a site redesign a one-place fix.

## Data sources (no official API exists)

1. **Official static timetable** — data.gov.ro XML (OGL-ROU-1.0, openly
   licensed). Schedules only, no live data. Used for station lists/coords and an
   offline schedule base.
2. **infofer website endpoints** — unofficial, reverse-engineered, return HTML.
   **Full contract: [`docs/infofer-api.md`](docs/infofer-api.md) — read it before
   touching the client.** Each feature is a **two-step flow**: GET the feature
   page to harvest `__RequestVerificationToken` + `ConfirmationKey` + the
   antiforgery cookie, then POST to the result endpoint:
   - Itineraries: GET `/ro-RO/Rute-trenuri/{from}/{to}` → POST `/ro-RO/Itineraries/GetItineraries`
   - Train + **live delay** (`întârziere`): GET `/ro-RO/Tren/{n}` → POST `/ro-RO/Trains/TrainsResult`
   - Station board: GET `/ro-RO/Statie/{name}` → POST `/ro-RO/Stations/StationsResult`
   - Nearest stations (no token): GET `/api/ro-RO/Stations/GetNearestStationsName`

   Dates use `DD.MM.YYYY 0:00:00`. The POST endpoints **require** the token +
   cookie. Live delay data exists **only for trains currently running**. A
   `ReCaptcha` field is enforced under heavy traffic — keep polling polite.

Reference parsers (don't depend on them; use for guidance):
`FlashWebIT/cfr-iris-scraper` (delays), `vasile/data.gov.ro-gtfs-exporter`
(static XML→GTFS).

## Hard constraints — keep these true

- **No backend.** Clients call infofer directly; alarms run on-device.
- **On-device background polling is throttled** (~15 min; opportunistic on iOS).
  ~15 min is acceptable — station-to-station times are usually longer. Don't
  design anything that assumes sub-15-min or guaranteed background ticks.
- **Alarms vs. notifications:** notification = tray + normal sound (broadly
  available); alarm = wakes through silent/DND (Android reliable; iOS needs
  Critical Alerts entitlement; **impossible in a browser**). Don't promise alarm
  behavior where the platform can't deliver it.
- **Be a polite client:** cache aggressively, rate-limit, sane User-Agent. No
  high-volume scraping.

## The dynamic alarm engine (Android, phase 1)

- A `WorkManager` periodic worker (~15 min) re-polls each active alarm via
  `infofer-client.getTrain(...)`.
- It recomputes the delay-adjusted arrival/alarm time and reschedules
  `AlarmManager` / the notification.
- Treat as a self-contained, independently testable unit. Cover the
  delay-recompute and reschedule logic with unit tests (no live network in
  tests — mock the client).

## Conventions

- Keep components small and single-purpose with clear interfaces (see
  `infofer-client` boundary above). When a file grows large, it's probably doing
  too much — split it.
- `infofer-client` is **Kotlin Multiplatform** (Ktor for HTTP, Ksoup for HTML
  parsing). It lives in `commonMain` with no app/UI imports; platform specifics
  (HTTP engine, etc.) go in the relevant source set. Android/iOS consume it
  natively; the web app consumes the Kotlin/JS output.
- Parsing code: pin selectors loosely, fail gracefully, and surface parse
  failures (don't return silently-wrong data).
- Romanian station/train terms appear in data (`întârziere` = delay, `sosire` =
  arrival, `plecare` = departure). Keep them accurate; user-facing language is a
  later decision.

## Testing expectations

- Unit-test the data-layer parsers against **saved HTML fixtures** (capture real
  responses; never hit the live site in CI).
- **Captured fixtures are immutable ground truth.** They must reflect exactly what
  infofer returns. If a test disagrees with a fixture, the test (or the parser) is
  wrong — fix those, NEVER edit the fixture to make a test pass. Re-capture a
  fixture only when intentionally refreshing it against the live site.
- Unit-test the alarm recompute/reschedule logic with mocked client data.
- For behavior changes, add tests for the changed behavior before claiming done.

## Working process

The global agent-assisted development guidelines in `~/.claude/CLAUDE.md` apply:
keep PRs narrowly scoped, separate implementation from review, verify with
evidence before claiming completion. Use the Superpowers skills (TDD,
systematic-debugging, verification-before-completion) as appropriate.

## Out of scope (do not add)

- Ticket purchasing, payments, seat reservation, user accounts.
- A server/backend or any cloud component.
- Scraping beyond what the informational features need.
