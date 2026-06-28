# infofer-client — tracked follow-ups

These were surfaced during the subagent-driven build + final whole-branch review
(2026-06-26) and consciously deferred. None are P0/P1; the data layer ships with
27 passing tests against real fixtures. Address before the relevant consumer
relies on the affected method.

## Before relying on `searchItineraries` (itinerary search)

- **Capture a multi-leg / connection fixture and test the change path.** The only
  itinerary fixture (`itineraries-bucuresti-brasov.html`) is 45 *direct* options,
  so `ItinerariesParser`'s multi-leg leg-extraction and `changes > 0` logic are
  unverified. Capture a route requiring a change (via Playwright, as the originals
  were), save as a fixture, and add tests.
- **Stop silently dropping un-parseable option cards.** `ItinerariesParser.parse`
  only throws when *every* card fails; if some multi-leg cards fail while direct
  ones succeed, connection routes vanish with no error (partial-data-as-complete).
  Make partial failure detectable (fail loudly past a threshold, or count/log
  drops) once the connection fixture exists.

## Station board

- Add an **ARRIVALS** test (the board fixture has 102 arrival rows; only
  departures are tested) and **assert `delayMinutes`/`track`** values.
- `StationBoardParser` delay regex is **direction-unaware** — an early train
  ("N min mai devreme/în avans") would be mislabeled as a positive delay. The
  train parser guards on `întârziere`; make the board parser consistent.

## Facade / requests

- `InfoferClient.getStationBoard` GET URL omits `?Date=` (token-only GET; likely
  harmless — verify with the live smoke).
- `searchItineraries` omits the documented optional POST fields
  (`BetweenTrainsMinimumMinutes`, `Is*ServiceRequired`, …); server defaults
  assumed — verify against live before relying on it.
- Add MockEngine facade tests for `getStationBoard` and `findNearestStations`
  (cheap; currently only `getTrain`/`searchItineraries` are covered at the facade).

## Robustness / polish

- **Distinguish anti-abuse/error pages from parse failures.** A ReCaptcha wall or
  rejected-token POST returns HTML without the expected containers → generic
  `InfoferParseException`. The alarm engine's retry/backoff can't tell "throttled"
  from "selectors broke." Detect the ReCaptcha/abuse response distinctly.
- `parseCategory` (`Text.kt`) uses `substringAfterLast` over the whole class
  attribute; if infofer adds trailing classes to a category badge it silently maps
  to `OTHER`. Extract the `span-train-category-*` token via regex instead. Add a
  category assertion to a parser test so a regression is visible.
- Set a real contact URL in the `User-Agent` (currently a placeholder).
- Decide where the **rate-limit / cache** "polite client" mandate is enforced —
  the data layer currently leaves it entirely to callers.
- Nits: `TrainResultParser` dash-split fallback can mis-split hyphenated station
  names when the en-dash separator is absent; `NearestStationsParser` `.text().trim()`
  is redundant; add a comment on `parseCategory`'s `substringAfterLast` fallback.

## Resolved in this build (for reference)

- Delay now returns `null` for trains with no live data (was `Delay(0)` = falsely
  on-time) — headline-correctness fix for the alarm engine.
- Live smoke now exercises the two-step token/cookie flow via `searchItineraries`
  (was only the token-less nearest-stations GET).
- Fail-loudly throws added for empty train branches and empty itinerary results.
- Coroutine `CancellationException` re-thrown in the session layer.
