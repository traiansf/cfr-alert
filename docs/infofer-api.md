# infofer "API" — Reverse-Engineered Reference

> The infofer site (`https://mersultrenurilor.infofer.ro`) has **no official
> API**. This documents the site's own internal endpoints, captured from live
> network traffic on 2026-06-26. Everything here is **unofficial and may change
> without notice** — treat it as the contract our `infofer-client` isolates.

Base URL: `https://mersultrenurilor.infofer.ro`

## Big picture

Each feature is a **two-step flow**, not a single GET:

1. **GET the feature page** (e.g. `/ro-RO/Tren/5568`). This returns the HTML
   shell and, crucially, sets an **antiforgery cookie** and embeds two tokens in
   the page:
   - `__RequestVerificationToken` (hidden form field)
   - `ConfirmationKey` (hidden form field)
2. **POST to the feature's result endpoint** with those tokens + the cookie. The
   response is an **HTML fragment** containing the actual data (schedules,
   delays, boards). This is what we parse.

> ⚠️ Earlier project notes assumed results were inline in a single GET. They are
> **not** — results come from the POST endpoints below. The only true JSON
> endpoint is nearest-stations, and even it returns HTML buttons.

### Shared POST fields (anti-abuse)

Every result POST includes these. They must be harvested from the GET page in
step 1; a POST without a valid token/cookie pair is rejected.

| Field | Value |
| --- | --- |
| `__RequestVerificationToken` | from hidden field on the GET page |
| `ConfirmationKey` | from hidden field on the GET page |
| `ReCaptcha` | empty under normal use |
| `IsReCaptchaFailed` | `False` |
| `IsSearchWanted` | `False` |

If we hammer the endpoints, infofer can start requiring `ReCaptcha`
(`IsReCaptchaFailed` flips). **This is the hard ceiling on polling frequency** —
be a polite client (cache, rate-limit, sane User-Agent).

Dates are formatted `DD.MM.YYYY 0:00:00` (e.g. `26.06.2026 0:00:00`).

## Station name forms

- **Canonical slug** (ASCII, hyphenated, no diacritics): `Bucuresti-Nord`,
  `Brasov`. Used in route URLs and accepted by the POST bodies. **Prefer this.**
- **Display name with diacritics**: `Bucureşti Nord`. The free-form search form
  requires correct diacritics or it finds nothing. We resolve user input to the
  canonical slug rather than depend on diacritics.

## Endpoints

### 1. Train detail + live delay

- **Page (GET):** `/ro-RO/Tren/{trainNumber}` — e.g. `/ro-RO/Tren/5568`. Also the
  landing/search form is at `/ro-RO/Trains`, whose "Trenuri astăzi" list links to
  `/ro-RO/Tren/{n}` for trains running today.
- **Data (POST):** `/ro-RO/Trains/TrainsResult`
  - Body: `Date`, `TrainRunningNumber`, `SelectedBranchCode` (empty) + shared
    fields.
- **Response structure (HTML fragment):**
  - Branches (a train may split): `div#div-stations-branch-{id}`, each headed by
    `<h4>Parcurs tren {from}–{to}</h4>`.
  - **Live delay summary** (per branch):
    ```html
    <p class="text-1-1rem">
      <i class="fas fa-stopwatch"></i>
      <span class="color-firebrick">2 min întârziere </span>
      la sosirea în Suceava Nord
      <span class="color-firebrick">(Raportat la 18:46).</span>
    </p>
    ```
    `color-firebrick` = delayed; "Raportat la HH:MM" = when the delay was last
    reported. On-time trains show no delay span / "la timp".
  - **Per-stop rows:** `ul.list-group > li.list-group-item`, each with:
    - Station link: `<a href="/ro-RO/Statie/{name}?Date=…">{Station}</a>`
    - Distance: `km {n}`
    - Track/line: `linia {x}`
    - Time: `div.text-1-3rem` (arrival in left column, departure in right column;
      origin has only departure, terminus only arrival)
    - Per-stop status: `div.text-0-8rem` with `color-darkgreen` ("la timp") or
      `color-firebrick` (delay).
- **Live data caveat:** real-time delay/position is populated **only for trains
  currently running** ("din circulație"). For other dates/trains you get the
  static schedule with no live status.
- Fixture: `infofer-client/src/commonTest/resources/fixtures/train-result-5568.html`

### 2. Itinerary search (routes between two stations)

- **Page (GET):** `/ro-RO/Itineraries` (form) or the canonical
  `/ro-RO/Rute-trenuri/{from-slug}/{to-slug}` — e.g.
  `/ro-RO/Rute-trenuri/Bucuresti-Nord/Brasov`.
- **Data (POST):** `/ro-RO/Itineraries/GetItineraries`
  - Body: `DepartureStationName`, `ArrivalStationName` (slugs), `DepartureDate`,
    `ConnectionsTypeId` (1 = direct + changes), `OrderingTypeId` (0),
    `TimeSelectionId` (0), `MinutesInDay` (0), `BetweenTrainsMinimumMinutes`,
    `Is{Bikes,OnlineBuying,BarRestaurant,SleeperCouchette}ServiceRequired`
    (`False`), `Departure/ArrivalTrainRunningNumber`, `ChangeStationName` +
    shared fields.
- **Response structure (HTML fragment, large):** one block per itinerary option
  (~45 for a busy pair):
  - Train category badge: `span.span-train-category-{ir|ic|r|rr|ira|…}`
  - Operator logo: `img.img-train-operator`
  - Stations: `div.div-itinerary-station`, departure/arrival times in
    `div.div-itineraries-departure-arrival`
  - Direct vs. with-change indicated by "direct" / "schimbare" text.
- Fixture: `infofer-client/src/commonTest/resources/fixtures/itineraries-bucuresti-brasov.html`

### 3. Station board (departures / arrivals)

- **Page (GET):** `/ro-RO/Statie/{stationName}?Date=DD.MM.YYYY`
- **Data (POST):** `/ro-RO/Stations/StationsResult`
  - Body: `Date`, `StationName` + shared fields.
- **Response structure (HTML fragment):**
  - Two tabs: **Plecări** (departures) / **Sosiri** (arrivals).
  - Rows: `div.div-departures-arrivails-details` (sic — site's spelling).
  - Real-time per row: `div.div-itinerary-station-with-real-time`,
    `div.div-stations-train-real-time-badge`,
    `…-badge-next`, `…-badge-next-in`.
  - Train category badges and links to `/ro-RO/Tren/{n}`.
- Fixture: `infofer-client/src/commonTest/resources/fixtures/station-board-brasov.html`

### 4. Nearest stations (only JSON-ish endpoint)

- **GET:** `/api/ro-RO/Stations/GetNearestStationsName?latitude={lat}&longitude={lon}`
- Returns an **HTML fragment** (not JSON): `button` list items with
  `<span class="font-weight-bold">{Station}</span>` and a distance string
  ("La 3,1 km depărtare"). No token required.
- Fixture: `infofer-client/src/commonTest/resources/fixtures/nearest-stations-bucuresti.html`

## Implications for `infofer-client`

1. A request needs a **session**: GET the page → extract `__RequestVerificationToken`
   + `ConfirmationKey` + keep the cookie → POST the result endpoint. Tokens are
   short-lived; fetch fresh per logical operation (and cache results, not tokens).
2. All parsing is **HTML scraping** → pin selectors loosely, fail loudly on parse
   mismatch (never return silently-wrong data), and keep fixtures current.
3. **ReCaptcha is the abuse ceiling** — the alarm engine's ~15-min polling is well
   within polite limits; never poll aggressively.
4. Resolve user-entered station text to the **canonical slug** before requests.
