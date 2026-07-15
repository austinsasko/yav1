# YaV1

[![Android CI](https://github.com/austinsasko/yav1/actions/workflows/ci.yml/badge.svg)](https://github.com/austinsasko/yav1/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

**YaV1 is an open-source Android companion app for the Valentine One radar detector.**
It shows the detector's alerts on your phone in real time, filters out false alarms
with GPS lockouts and heuristics, and adds data-driven awareness — speed cameras,
ALPR/Flock readers, and aerial speed enforcement — without any account, subscription,
or cloud service. Every feature runs on the open web or on your device.

> **Status:** actively modernized (2026). All features below build and pass a 279-test
> unit suite and have been validated on an Android 15 emulator. Features that talk to a
> real Valentine One over Bluetooth (Gen2 recognition, the LE transport) are validated in
> simulation; they have **not yet been confirmed against real hardware**. See
> [Status & caveats](#status--caveats).

---

## Features

### Detection & display
- **Every Valentine One, one app** — V1 **Gen1** over classic Bluetooth (SPP) *and* the
  V1connection LE dongle (BTLE), plus **V1 Gen2** over its built-in Bluetooth LE.
- Live alert board and a faithful V1-display replica (bogey counter, band indicators,
  directional arrows), background overlay window, dark mode, and a demo mode that replays
  recorded ESP data (including a Gen2 scenario) with no hardware attached.
- **Voice announcements** (TTS) — band, direction, and frequency spoken as alerts arrive.

### Muting & lockouts
- **Auto GPS lockouts** with learning: frequency-drift tolerance, multi-visit confirmation
  before a signal is locked, and automatic un-lock when a locked signal stops appearing.
  Manual lockout and whitelist supported.
- **Speed-based muting** (Savvy emulation) and **speed-limit-aware muting** — mutes at or
  below the posted limit using OpenStreetMap/Overpass speed-limit data, cached on-device.
- **K-band BSM filter** — heuristic suppression of blind-spot-monitor falses (opt-in).

### Data services (no file wrangling required)
- **Camera & ALPR alerts** — automatically fetches speed cameras, red-light enforcement,
  and ALPR/Flock readers from OpenStreetMap around you as you drive, cached offline-first.
  Manual CSV import (IGO/SCDB-tolerant) is retained as a power-user option.
- **Aircraft-enforcement awareness** — multi-source ADS-B (adsb.lol, airplanes.live,
  adsb.fi) with a 330-aircraft FAA-registry-derived enforcement watchlist plus a loitering
  heuristic; the watchlist is transparent and user-extendable.

### Automation
- **Location-based V1 profiles** — automatically pushes a V1 settings profile per US state
  (offline state resolution), e.g. enable X-band when you enter one state, disable K in
  another.
- Custom sweeps (Gen1) and named V1 settings profiles; alert logging with map review.

All new features default **off** and fail soft when the network or a data source is
unavailable.

---

## Requirements

| | |
|---|---|
| Android | 5.0+ (minSdk 21), targets Android 15 (targetSdk 35) |
| Hardware | Valentine One (Gen1 or Gen2) + a V1connection / V1connection LE, or Gen2's built-in BLE |
| Build JDK | 17 |

## Quick start

```bash
# build a debug APK
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"   # or your JDK 17
./gradlew :YaV1:assembleDebug
# → YaV1/build/outputs/apk/debug/YaV1-debug.apk

# run the unit test suite
./gradlew :ESPLibrary:testDebugUnitTest :YaV1:testDebugUnitTest
```

Full build, emulator, and run instructions: **[docs/BUILD.md](docs/BUILD.md)**.

## Documentation

- **[docs/BUILD.md](docs/BUILD.md)** — building, the SDK/toolchain, running on an emulator.
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — modules, the ESP protocol layer, how the transports and data services fit together.
- **[docs/FEATURES.md](docs/FEATURES.md)** — every feature, its preference keys, its data sources, and its limitations.
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — how to build, test, and submit changes.

## Status & caveats

- **Validated in emulator + unit tests, not on real hardware.** Gen2 recognition and the
  BLE transport are exercised through demo simulation and unit tests; the speed-limit,
  camera, and aircraft data paths are validated against the live services from an emulator.
  None have been road-tested with an actual Valentine One yet.
- Data quality depends on open sources: OpenStreetMap speed-limit/camera coverage varies by
  area, and the aircraft watchlist matches on FAA registrant names (aircraft under leasing
  trusts stay invisible). Treat aircraft "possible enforcement" heuristics as hints.
- **Do not enable code shrinking (R8/minification).** ESP callbacks are registered by
  reflection and break when shrunk.

## Project heritage

YaV1 started as **Franckyl06**'s app, released under the GPL. This repository is the
modern continuation of that work: recently modernized by **Austin Sasko / Glass L
Software** (2026) — rebuilt for Android 15 and the AndroidX toolchain, given Bluetooth LE
and V1 Gen2 support, Android Auto integration, and extended with the data services above.
It is not affiliated with or endorsed by Valentine Research.

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
