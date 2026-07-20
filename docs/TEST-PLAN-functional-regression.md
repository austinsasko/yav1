# Functional & Regression Test Plan

JVM-only suites (no emulator) covering the core user-facing flows end to end
plus pins for every bug fixed on `master`. All tests live in the YaV1 module
and run with the standard unit-test task:

```
./gradlew :YaV1:testDebugUnitTest
```

Packages:

- `com.glasslsoftware.yav1.functional` - end-to-end flows on real shipped
  data (demo recordings, live-recorded API fixtures, shipped assets)
- `com.glasslsoftware.yav1.regression` - one class per fixed bug, each test
  commented with the commit that fixed it

They complement (and do not overlap file-wise with) the existing per-class
unit tests under `com.glasslsoftware.yav1.*` and the ESPLibrary suites under
`com.valentine.esp.*`.

Suite helpers (all in `functional`): `RepoFile` (locates shipped assets from
the test working directory), `DemoReplay` (replays a demo `.dat` recording
through the real decode/demo/alert-assembly pipeline), `WorkingSparseArray`
(a functioning `android.util.SparseArray` injected into `GetAlertData`,
whose mockable-jar SparseArray is a returnDefaultValues no-op on the JVM)
and `TestSeams` (reflection access to package-private test seams, since this
suite deliberately lives outside the app packages).

## Functional suite: flow -> test class

| # | Flow | Test class | Data driven through |
|---|------|-----------|---------------------|
| 1 | ESP packet -> alert pipeline: framing/checksum decode, demo routing, alert-table assembly, band/direction/signal classification, priority alert, bogey counts, bogey-counter 7-segment letters | `functional.EspAlertPipelineFunctionalTest` | every frame of the shipped Gen1 recording `assets/demo/demo.dat` through `ESPPacket.makeFromBuffer` -> `DemoData.handleDemoPacket` -> `GetAlertData.getAlertDataCallback` -> `YaV1Alert` (helper: `functional.DemoReplay`) |
| 2 | Quiet-ride decision chain: PSL mute (speed vs limit + offset, hysteresis, unit conversion, unknown-limit policy), K-band BSM filter hold/release, lockout/whitelist silencing, TTS announce gate and exact phrases | `functional.QuietRideDecisionChainFunctionalTest` | scenario walks over `PslMuteDecider` + `YaV1BsmFilter` + `YaV1Alert` property masks + `YaV1Tts`; the announce gate mirrors `YaV1AlertProcessor.newProcess` |
| 3 | Gen2 device handling: demo-mode Gen2 recognition, junk/BSM aux bit into `PROP_JUNK`, BSM filter honoring the junk flag, sweep degradation (no custom-sweep dialogue on Gen2), Gen2 volume packet, version boundaries, no state leak across connections | `functional.Gen2DeviceHandlingFunctionalTest` | full replays of `assets/demo/demo_gen2.dat` vs `assets/demo/demo.dat` |
| 4 | POI + aircraft alerting: camera CSV -> grid index -> approach/close/quiet state machine -> spoken phrase; ADS-B payload -> parser -> shipped FAA watchlist match -> behavior assessment -> alert throttling / loiter heuristic | `functional.PoiAircraftAlertingFunctionalTest` | in-test camera CSV via `PoiCsvParser`; recorded payload `src/test/resources/aircraft/adsb_point_sample.json`; shipped `assets/aircraft/enforcement_hex.csv` |
| 5 | Geo profile switching: simulated drive Cincinnati OH -> Covington KY on the shipped state polygons, profile push + TTS announcement, debounce, alert-quiet / disconnect / demo guards, manual override, resolver caching | `functional.GeoProfileSwitchingFunctionalTest` | `assets/geo/us_states.json` through `StateResolver` -> `GeoProfileEngine` with a recording gateway |

## Regression suite: bug -> pin

Each test class names the fixing commit in its javadoc; every test protects
one observable symptom of the original bug.

| Bug (as shipped) | Fixed in | Test class |
|------------------|----------|-----------|
| `SpeedLimitCache`'s anonymous LinkedHashMap shadowed `Map.Entry` with the cache's own `Entry` in `removeEldestEntry`, so the override never engaged and the "LRU" grew without bound | `ee29637` (PR #2, "Fix review regressions in radar feature suite") | `regression.SpeedLimitCacheLruRegressionTest` |
| BLE GATT status 133 (also with a bogus `STATE_CONNECTED`) left `connect()` blocked and the reader spinning; late callbacks after close resurrected dead links | `ee29637` (PR #2, BLE hardening) + `811d840` (PR #7, "Fix Android permission and BLE reliability issues") | `regression.BleGattStatus133RegressionTest` |
| Android 12 ignored the fine-location runtime request because coarse was not requested (or declared) together with it | `811d840` (PR #7) | `regression.Android12LocationPermissionRegressionTest` |
| Overpass query filtered on `["maxspeed"]` only, so ways tagged exclusively `maxspeed:forward/backward` (US 290 Express Lane) were never fetched and the wrong road's limit was used | `6a0a856` (PR #5, "PSL: fetch and honor directional maxspeed tags") | `regression.OverpassDirectionalMaxspeedRegressionTest` |
| Both Overpass clients (PSL + POI online cameras) kept retrying through live HTTP 429/503/504 answers instead of backing off | `6a0a856` (PR #5, "PSL/POI: corridor seeding, prefetch-ahead, 429 backoff") | `regression.OverpassHttp429BackoffRegressionTest` |
| Sweep queries/pushes were sent to V1 Gen2 units that never answer them (hanging state machines); Gen2 flag could leak across reconnects; no Gen2 demo data | `ee29637` (PR #2, "P0: Gen2 demo simulation, sweep degradation") | `regression.Gen2SweepDegradationRegressionTest` |

## Known JVM-testability limits (no app-code changes allowed)

- **`YaV1AlertProcessor.processAlert`** (persistence/lockout/mute orchestration)
  reads the live app object graph (`YaV1.mV1Client`, `YaV1.sAutoLockout`,
  `YaV1CurrentPosition`, sound manager statics). The functional suite covers
  the deciders it delegates to and mirrors its announce gate; running the
  processor itself needs a seam or Robolectric.
- **`ValentineClient`** cannot be constructed without an Android `Context`
  (SharedPreferences + receiver registration in `init`), so its Gen2
  early-return methods (`setCustomSweeps`, `setSweepsToDefault`) are pinned
  via their shared decision input (`V1VersionSettingLookup.isGen2`) and the
  shipped demo data instead of direct calls.
- **`YaV1Activity.requestNeededPermissions`** is Activity code
  (`checkSelfPermission`/`requestPermissions`); the regression pin covers the
  manifest declarations and the grouped-request source contract.
- **BLE `SecurityException` injection** (permission revoked mid-write) needs
  a mockable `BluetoothGatt`; the shared fail-closed contract (IOException,
  never a crash/hang) is pinned instead.
- **`PoiOnlineManager`'s** 429 handling sits inside its private HTTP
  transport (real `HttpURLConnection`); its backoff contract is pinned at the
  constant level, behavior at the `OverpassSpeedLimitProvider` seam.

## CI

`.github/workflows/ci.yml` already runs `:YaV1:testDebugUnitTest` (and the
ESPLibrary suite) on every PR/push, so both new suites are picked up with no
pipeline changes.
