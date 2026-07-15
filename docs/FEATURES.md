# Features reference

Every feature added during the 2026 modernization, its preference keys, its data source, and
its limitations. All of these default **off**. Preference keys are the SharedPreferences
keys, useful for debugging via `adb shell run-as com.franckyl.yav1`.

---

## Voice announcements (TTS)
Speaks each new, non-muted alert as band + direction + frequency ("Ka front, 35.5").

- **Where:** Settings → Sound → *Voice & filters*
- **Keys:** `tts_alert`
- **Notes:** uses the device TTS engine and locale; respects the app mute state.

## K-band BSM filter
Heuristic suppression of blind-spot-monitor falses (short-lived K-band signals with rapid
strength change and no persistence).

- **Where:** Settings → Sound → *Voice & filters*
- **Keys:** `bsm_filter`
- **Limitation:** heuristic; unit-tested but not tuned against a large real-world BSM corpus.

## Speed-limit-aware muting (PSL)
Mutes alerts while at or below the posted limit (plus an offset), the "Silent Ride" idea.

- **Where:** Settings → *Speed-limit muting*
- **Keys:** `psl_enable`, `psl_offset` (mph/kph-aware), `psl_unknown` (behavior when the
  limit is unknown). Requires `use_gps` on. Debug: `psl_debug_stub`, `psl_debug_stub_kph`.
- **Data:** OpenStreetMap `maxspeed` (incl. `maxspeed:forward/backward`) via the Overpass
  API, fetched on a background thread, tile-cached on device with corridor seeding and
  look-ahead prefetch; backs off on HTTP 429/5xx.
- **Limitation:** OSM speed-limit coverage varies; unknown tiles fall back to `psl_unknown`.
  Way selection uses GPS bearing.

## Camera / POI & ALPR alerts
Alerts on approach to speed cameras, red-light enforcement, and ALPR/Flock readers.

- **Where:** Settings → *Camera / POI alerts*
- **Keys:** `poi_enable`, `poi_radius`, `poi_alert_style`, `poi_files` (manual imports),
  `poi_online_enable`, `poi_online_cams`, `poi_online_alpr`.
- **Data (online, no import):** OpenStreetMap via Overpass — `highway=speed_camera`,
  `type=enforcement` relations (speed / red-light), `surveillance:type=ALPR` (the DeFlock
  tagging). Auto-fetched around your location, tile-cached (offline-first, 7-day TTL).
- **Data (manual):** CSV import via the file chooser — `lat,lon[,type][,speed][,name]`,
  auto-detecting delimiter, header, and IGO/SCDB lon-first ordering. Multiple files, each
  toggleable/removable.
- **Alerting:** grid-indexed proximity within `poi_radius`, roughly ahead (bearing cone),
  escalates at half distance, one alert per approach.

## Aircraft-enforcement awareness (ADS-B)
Warns about aircraft that may be doing aerial speed enforcement.

- **Where:** Settings → *Aircraft awareness*
- **Keys:** `adsb_enable`, `adsb_radius_nm`, `adsb_heuristic` (loiter detection),
  `adsb_alert_style`, `adsb_status` (last-seen list). Debug: `adsb_debug_fixture`.
- **Data:** multi-source ADS-B — adsb.lol, airplanes.live, adsb.fi — polled round-robin,
  merged by ICAO hex, with a per-aircraft source count as a confidence signal and per-feed
  backoff. Matched against a bundled 330-aircraft watchlist derived from the FAA Releasable
  Aircraft Registry (state patrol / DPS / sheriff fixed-wing aviation), user-extendable via
  `enforcement_user.csv` in app storage.
- **Loiter heuristic:** airborne, low altitude, 30–150 kt, high heading variance → "possible
  aerial enforcement" (clearly labeled a guess; rotorcraft downgraded to "possible").
- **Limitation:** free community feeds, no SLA; name-based watchlist misses lease-trust
  registrations; false-positives on traffic-watch / training / survey flights.

## Location-based V1 profile switching
Automatically pushes a named V1 settings profile when you cross into a configured US state.

- **Where:** Settings → *Location profiles*
- **Keys:** `geo_auto_profile`, `geo_tts` (announce switches), `geo_configure` (opens the
  state→profile mapping screen). Mappings persist as JSON.
- **Data:** bundled offline US-state polygons (Census-derived, simplified); no network.
- **Safety:** never pushes when disconnected, in demo mode, or mid-alert; 60s debounce; a
  manual profile push suspends auto-switching until the next state change.
- **Limitation:** border accuracy ≈ 2 km (polygon simplification + hysteresis); requires a
  connected V1 to actually push.

## V1 Gen2 support
Recognizes and parses the V1 Gen2 over its built-in Bluetooth LE.

- **Data:** Gen2 device identifiers and ESP payloads (per Valentine's ESP spec). Custom
  sweeps (Gen1-only) degrade gracefully on Gen2.
- **Try it:** overflow menu → **Demo** → `demo_gen2` replays a Gen2 session with no hardware.
- **Limitation:** validated via demo simulation and unit tests only — not yet confirmed
  against a physical Gen2.
