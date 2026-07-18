# Test plan — component + integration suites (5 core features)

Scope of the `qa-component-integration` branch: JVM-only component and
integration tests for the five core data/logic features, layered on top of
the existing unit tests (which are left untouched). Everything runs with
`./gradlew :YaV1:testDebugUnitTest` — no emulator, no network — and is
picked up automatically by the existing CI workflow (`.github/workflows/ci.yml`).

Conventions:

- **Component tests** live next to the class under test (same package as the
  existing unit tests) and are named `*EdgeTest` / `*BoundaryTest` /
  `*BehaviorTest` / `*BackoffTest`. They cover error paths, boundary
  conditions and malformed input the existing `*Test` classes do not.
- **Integration tests** live in `com.glasslsoftware.yav1.integration` and are
  named `*IntegrationTest`. They wire several real classes together
  (fixtures, disk round trips, fake transports/gateways for the hardware/
  network seams that already exist in the production code).

## 1. PSL speed-limit-aware muting (`psl/`)

| Test class | Kind | What it covers |
|---|---|---|
| `psl/SpeedLimitCacheEdgeTest` | component | Malformed persisted geometry (truncated points, non-numeric coordinates, non-array `g`) keeps the limit but drops the geometry; tiles without a limit field are skipped; `fromJson` never overwrites live tiles; `Entry` copies geometry defensively; `hasRoadGeometry` rejects degenerate shapes; tile keys widen near the poles; save/load fail soft on uncreatable paths, directories and `null`. |
| `psl/PslMuteDeciderBoundaryTest` | component | Exact hysteresis-band edges (speed == threshold + 2 never releases; dipping to the exact band top resets the exit timer); unknown→known limit transitions in both speed regimes; zero speed mutes; negative offsets shrink the threshold; `isMuted()` is side-effect free. |
| `psl/OverpassSpeedLimitProviderEdgeTest` | component | `parseMaxspeed` corners (decimal mph, shouting units, composite values with an unusable first part, scientific notation); partially malformed Overpass elements (broken geometry point, non-object element, missing tags) are dropped without killing the batch; 90°-boundary of the direction-aware limit; `cachedRoadMatches` bearing (30°) and distance (25 m) thresholds and geometry-less entries; zero-length segments; 503/504 back off while 500/404 do not; deterministic way selection on ties. |
| `integration/PslMutingFlowIntegrationTest` | integration | Live-recorded I-45 Overpass JSON → `parseWays` → `selectWay`/`effectiveLimitKph` → `SpeedLimitCache` (with road geometry) → `cachedRoadMatches` → `PslMuteDecider`: mute below limit+offset, hysteresis release when speeding, immediate re-mute; cache **disk round trip** (save → reload → same decision); crossing-road fail-safe (mismatch ⇒ unknown ⇒ configured behavior); the US-290 reversible-lane fixture driving different limits (and mute outcomes) per direction of travel; expired tiles no longer feed the decider. |

Not covered (needs a seam / instrumentation): `PslMute.shouldMute()` static
glue (loads `YaV1` whose class init constructs ESP-library objects) and the
network/executor paths of `OverpassSpeedLimitProvider` (`httpPost`, fetch
scheduling, rate limiting beyond `noteHttpStatus`).

## 2. POI / camera alerts (`poi/`)

| Test class | Kind | What it covers |
|---|---|---|
| `poi/PoiCsvParserEdgeTest` | component | Header-only files; headers without coordinate columns fall back to positional; conflicting lon/lat order votes do not flip the file; delimiter precedence (most frequent wins, names keep foreign delimiters); inclusive coordinate bounds (±90/±180) vs. just-out-of-range; non-integer speed columns; trailing empty fields; CRLF endings; lone-`;` lines are data, not comments. |
| `poi/PoiGridIndexEdgeTest` | component | Polar latitude guard; zero radius; duplicate points; points exactly on a cell boundary. |
| `poi/PoiAlertEngineEdgeTest` | component | Null index; `reset()` re-arms an approach; no CLOSE escalation while stopped (and escalation resumes when moving); a passed POI never escalates from behind; the ±cone boundary is inclusive; shrinking the radius mid-drive. |
| `poi/PoiStoreEdgeTest` | component | Corrupt store JSON files are skipped while valid ones load; null import streams produce a readable error; `putGenerated` preserves the user's disabled flag across regeneration; `remove()` also deletes the loose CSV so it cannot re-import; display names with path components are flattened; empty display names get a default. |
| `integration/PoiAlertPipelineIntegrationTest` | integration | CSV text → `PoiStore` import (on disk) → `enabledPois` → `PoiGridIndex` → `PoiAlertEngine` (simulated GPS approach) → `PoiPhrases` announcement text for both stages, metric and imperial; store reload from disk feeds the identical pipeline (user-provided name wins in the phrase); disabled files silence the pipeline; live NYC Overpass fixture → `OverpassCameraSource.parse` → `PoiOnlineCache` merge/dedupe → `PoiStore.putGenerated` → index → engine → a spoken "License plate reader…" alert. |

Not covered (needs a seam / instrumentation): `Announcer` (TTS/Toast/
ToneGenerator), `PoiAlertManager`, `PoiOnlineManager` scheduling and
`PoiFilesPreference` (Android UI / service glue).

## 3. ADS-B aircraft awareness (`aircraft/`)

| Test class | Kind | What it covers |
|---|---|---|
| `aircraft/AdsbParserEdgeTest` | component | `lastPosition` fallback (complete and incomplete); non-"ground" altitude strings; case-insensitive "ground"; string-typed `gs`/`track` become unknown; non-object array entries are skipped; missing ident fields default to empty and `flight` is trimmed. |
| `aircraft/AdsbAggregatorBackoffTest` | component | Exponential backoff doubling per failure with the 15-minute cap (probed at exact boundaries); zero transport calls while every feed is backed off; an empty source list never polls and never throws. |
| `aircraft/AircraftTrackerBoundaryTest` | component | Inclusive altitude/speed gate edges (5500 ft, 30/150 kt); zero/unknown altitude and grounded aircraft excluded; NaN tracks never accumulate loiter; the exact 270° turn threshold (and 267° staying quiet); watchlist beats heuristic; never-assessed aircraft never alert; prune keeps fresh aircraft; hex keys are case-insensitive incl. shared cooldown. |
| `aircraft/EnforcementWatchlistEdgeTest` | component | Hex format rejection (short/long/non-hex/empty); whitespace tolerance on load and match; duplicate registrations (last wins for reg lookup, both by hex); hex match beats registration match; all-empty identifiers never match; `load()` returns the number added. |
| `integration/AdsbEnforcementFlowIntegrationTest` | integration | Fixture JSON → `AdsbAggregator.poll` (stub transport) → `EnforcementWatchlist.match` → `AircraftTracker.assess` → cooldown-gated alerts: the airborne FHP Cessna is flagged exactly once (agency + confidence verified), the grounded watchlist helicopter stays silent through the whole chain; a circling low-slow Cessna crosses the loiter threshold across five polls and alerts with cooldown; the live-recorded Atlanta feed (36 aircraft) raises no alerts on a single poll and ages out of the tracker. |

Not covered (needs a seam / instrumentation): `AdsbClient` real HTTP paths
beyond the existing `AdsbClientTest`, `AircraftMonitor` (service loop,
Handler, preferences UI), `AircraftStatusPreference`.

## 4. Geo profile switching (`geo/`)

| Test class | Kind | What it covers |
|---|---|---|
| `geo/GeoProfileEngineEdgeTest` | component | Exact debounce boundary (elapsed == window pushes); pending rules die when leaving the state before firing (and do not resurrect on reconnect); null rule maps; `reset()` re-arms the current state; `getCurrentState` across transitions incl. null; deferral reasons are logged once per cause, not per fix. |
| `geo/StateResolverEdgeTest` | component | Dataset without `states` throws `IOException`; null/empty state lists; `nameOf` falls back to the code; null (no-state) resolutions are cached with the same hysteresis; first matching state wins on overlap; `GeoState` null/degenerate geometry hardening (missing bbox, wrong-length bbox, missing rings, <3-point rings); multi-ring (island) states. |
| `integration/GeoProfileSwitchingIntegrationTest` | integration | Real shipped `us_states.json` + rules parsed from the persisted `GeoRuleStore` JSON + `GeoProfileEngine` with a fake V1 gateway: crossing the Ohio River switches profiles with the right announcements; short hops neither re-resolve (cache) nor re-push (engine); an on-screen alert defers the push until quiet; open water resolves to no state and pushes nothing; a state without a rule stays silent. |

Not covered (needs a seam / instrumentation): `GeoProfileManager` (service
wiring, SharedPreferences, event bus), `GeoAnnouncer` (TTS), `GeoProfileActivity`.

## 5. BSM filtering + lockout learning (`YaV1BsmFilter`, `lockout/`)

| Test class | Kind | What it covers |
|---|---|---|
| `YaV1BsmFilterEdgeTest` | component | `init()` actually reads the `bsm_filter` preference (via a plain `SharedPreferences` fake — it is an interface); missing preference defaults to off; the ramp threshold is inclusive at `sRampLeds`; the extended hold ends exactly at `sRampHoldMs`. |
| `lockout/LockoutBehaviorTest` | component | Front/rear signal encoding helpers and `reverseParamSignal` round trip; `getMaxSignal`; `forceLockout`/`forceWhite` flag transitions (mutually exclusive, seen=20, missed cleared); `resetFlag` clears transient bits and keeps sticky ones; white-listed lockouts are never auto-removed by default but honor a configured limit; `checkForUpdate` short-circuits on a pending UPDATE without counting a miss; seen lockouts are not counted missing; area enter/leave bookkeeping (`resetOnEnterCurrentArea` busy/non-busy, `setResetOnOut`/`resetOnOut`). |
| `integration/BsmLockoutLearningIntegrationTest` | integration | The K-band alert lifecycle across both systems with real collaborators: a rapid-ramp BSM false is held its entire life and never voiced; a steady persistent source outlives the hold and enters lockout learning; a door opener locks after five visit-separated commutes while its oscillator drifts (frequency window tracks the drift, same-pass packets never double-count); a removed source auto-unlocks after `mMaxUnseen` consecutive misses; manual lockouts survive to their higher limit; age-based expiry is verified as an independent unlock path. |

Not covered (needs a seam / instrumentation): `LockoutArea.searchNoDirection`
and `LockoutData`/`LockoutDb` — `LockoutArea` extends `android.util.SparseArray`
(stubbed to return-default no-ops in JVM unit tests, which would make any
assertion vacuous) and depends on `YaV1` app statics; `LockoutData`/`LockoutDb`
need SQLite. `YaV1PersistentAlert` construction dereferences
`YaV1.sContext` via `YaV1CurrentPosition`'s static initializer. These would
need Robolectric or a small refactoring seam — deliberately not done on this
tests-only branch. `LockoutOverride`/`LockoutDialog`/`LockoutActivity` are UI.

## Fixtures used

All fixtures were already in the repo (recorded live 2026-07-14, see their
headers): `psl/overpass_i45_live.json`, `psl/overpass_us290_directional.json`,
`aircraft/adsb_point_sample.json`, `aircraft/adsbfi_atl_live.json`,
`poi/overpass_cameras_nyc.json`, plus the shipped assets
`geo/us_states.json` and `aircraft/enforcement_hex.csv`. No new fixtures were
required.
