# Trenuri — Romanian Rail Information App

> Working title. A free, informational app for Romanian rail travel — inspired by
> the **CFR Călători** / **Mersul Trenurilor** apps, but covering only the
> **information** features (no ticket purchasing).

Search trains between stations, browse a station's departures and arrivals, look up
a specific train's full itinerary, and — the headline feature — set **dynamic,
delay-aware alarms** that wake you when your train is actually about to arrive,
adjusting automatically as delays change.

## Status

Early stage. This repository currently contains the project's design documentation
(`README.md`, `CLAUDE.md`). No application code yet.

## Features

Informational only — there is intentionally **no purchasing or account/payment**
functionality.

- **Itinerary search** — find trains between two stations, with direct routes and
  connections.
- **Station board** — departures and arrivals for a given station.
- **Train details** — full stop-by-stop itinerary for a specific train number,
  including **live delays** (`întârziere`).
- **Dynamic delay-aware alarms & notifications** (mobile) — set an alarm for a
  train arriving at a station; the app re-polls in the background and adjusts the
  alarm time as the delay changes.

### Alarms vs. notifications

- **Notification** — appears in the tray with normal sound. Available on all
  platforms that support background delivery.
- **Alarm** — wakes you even on silent / Do-Not-Disturb, like an alarm clock.
  Reliable on Android; on iOS this requires Apple's *Critical Alerts* entitlement;
  **not possible in a web browser**.

## Architecture

Three pieces that share only a common data layer. **There is no backend of our
own** — clients talk to infofer directly, and the delay-aware alarms run as an
on-device background service.

```
                 ┌──────────────────────────────────┐
                 │  infofer-client (Kotlin Multiplat.)│
                 │  typed data layer; isolates ALL    │
                 │  HTML scraping/parsing (Ktor/Ksoup)│
                 └─────────────────┬─────────────────┘
                                   │ searchItineraries / getTrain /
              native ┌─────────────┼─────────────┐ native / Kotlin-JS
                     │             │             │
            ┌────────▼──────┐ ┌────▼─────────┐ ┌─▼──────────────┐
            │  Android app  │ │   iOS app    │ │    Web app     │
            │  (Kotlin)     │ │  (Swift,     │ │  (framework    │
            │  info+ALARMS  │ │   phase 2)   │ │   TBD, phase 3)│
            └───────────────┘ └──────────────┘ │  info only     │
                                               └────────────────┘
```

1. **`infofer-client` — shared data layer (Kotlin Multiplatform).** A thin
   library that wraps the infofer endpoints and the static timetable data,
   exposing clean typed methods (`searchItineraries`, `getTrain`,
   `getStationBoard`, `findStations`). Written **once in Kotlin** (HTTP via
   **Ktor**, HTML parsing via **Ksoup**) and reused **natively by Android and
   iOS** and via **Kotlin/JS** by the web app. **All fragility from
   scraping/parsing HTML is isolated here** behind a stable interface, so no app
   ever touches raw HTML.

2. **Android app (Kotlin) — phase 1.** The informational screens plus the
   **dynamic alarm engine**: a `WorkManager` periodic worker (~15 min) re-polls
   active alarms through the data layer, recomputes delay-adjusted arrival/alarm
   times, and reschedules `AlarmManager` / notifications accordingly.

3. **iOS app — phase 2.** Same informational features and (best-effort) alarms,
   within iOS background-execution limits.

4. **Web app — phase 3.** Informational only (framework TBD). Browsers have no
   reliable background execution, so no alarms.

### Roadmap / phasing

1. **Phase 1** — shared `infofer-client` + Android app (informational + alarms).
2. **Phase 2** — iOS app.
3. **Phase 3** — web app.

## Data sources

There is **no official JSON or real-time API** for Romanian rail. Data comes from
two places:

### 1. Official static timetable (sanctioned)

- **data.gov.ro — Informatică Feroviară SA**:
  <https://data.gov.ro/organization/sc-informatica-feroviara-sa>
- Per-operator timetable **XML** under the **OGL-ROU-1.0** open license (reuse,
  incl. commercial, permitted). **Schedules only — no live delays.**
- Used for station lists, station coordinates, and an offline/cached schedule base.
  The `vasile/data.gov.ro-gtfs-exporter` project converts this XML to GTFS.

### 2. infofer website endpoints (unofficial, reverse-engineered)

Each feature is a **two-step flow**: GET the feature page (which sets an
antiforgery cookie and embeds a `__RequestVerificationToken` + `ConfirmationKey`),
then **POST** to a result endpoint that returns an HTML fragment we parse. Live
delays come from the train-result POST and exist **only for trains currently
running**. Full contract: **[`docs/infofer-api.md`](docs/infofer-api.md)**.

| Purpose | Page (GET) | Data (POST) |
| --- | --- | --- |
| Itinerary search | `/ro-RO/Rute-trenuri/{from}/{to}` | `/ro-RO/Itineraries/GetItineraries` |
| Train itinerary + **live delay** | `/ro-RO/Tren/{number}` | `/ro-RO/Trains/TrainsResult` |
| Station departures/arrivals board | `/ro-RO/Statie/{name}` | `/ro-RO/Stations/StationsResult` |
| Nearest stations (no token needed) | — | `GET /api/ro-RO/Stations/GetNearestStationsName` |

Base URL: `https://mersultrenurilor.infofer.ro`

Notes: the POST endpoints **require** the token + cookie harvested from the GET
page. A `ReCaptcha` field is present and empty under normal use but can be enforced
under heavy traffic — the practical ceiling on polling frequency.

## Legal & etiquette

- The static timetable XML is openly licensed (OGL-ROU-1.0).
- The website endpoints are **unofficial** and have **no published API terms**.
  Treat them as a courtesy: **cache aggressively, rate-limit requests, send a sane
  User-Agent**, and avoid high-volume scraping. This is a personal/informational
  tool, not a high-traffic public service.
- A site redesign can break the HTML parsers at any time — see the data-layer
  isolation above and add monitoring.

## Reference projects

- `FlashWebIT/cfr-iris-scraper` — parsing logic for live delays (dated but useful).
- `vasile/data.gov.ro-gtfs-exporter` — static XML → GTFS + station GPS.
- `flashwebit.github.io/ROTI` — catalogue of Romanian transit data resources.

## License

TBD.
