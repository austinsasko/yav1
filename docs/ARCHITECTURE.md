# Architecture

## Modules

```
settings.gradle → :YaV1Lib :ESPLibrary :AmbilWarna :aFileDialog :YaV1
```

| Module | Role |
|---|---|
| **YaV1** | The application: UI, services, preferences, and all feature packages. |
| **ESPLibrary** | The Valentine ESP protocol stack — packets, transports, threading. |
| **YaV1Lib** | Shared alert model (`YaV1Alert`, `YaV1AlertList`) used by app and ESP layers. |
| **AmbilWarna** | Color-picker widget (used in preferences). |
| **aFileDialog** | File chooser (used for CSV import / backup-restore). |

## The ESP protocol layer (`ESPLibrary`)

The Valentine One speaks the **ESP** (Enhanced Serial Protocol) over a serial link. The
library wraps that so the app deals in typed packets, not bytes.

- `ValentineClient` — the public API the app uses (connect, register for alert/display
  data, push settings, mute).
- `ValentineESP` — transport orchestration; owns the reader/writer/processing threads and
  selects the transport (`CONNECTION_SPP` vs `CONNECTION_LE`).
- `packets/ESPPacket` — parse/serialize. Classic V1connection wraps ESP frames in **PACK
  framing** (`0x7F` delimiters, length, wrapper checksum, `0x7D` escaping).
- `bluetooth/V1connectionLE` — the **BTLE bridge**. The V1connection LE dongle and V1 Gen2
  carry *bare* ESP frames (`0xAA … 0xAB`, no PACK wrapper) over GATT notifications. This
  class re-frames LE traffic into PACK framing in both directions so the existing parser and
  reader/writer threads work unchanged. Service/characteristic UUIDs match Valentine's
  official ESP library.
- `threads/DataReaderThread`, `DataWriterThread` — move bytes between the transport streams
  and the packet queue.

> **Reflection warning:** callbacks are registered by method name
> (`registerForPacket(id, obj, "methodName")`). Do not enable R8/minification and be careful
> renaming callback methods.

## Transports

| Transport | Device | How |
|---|---|---|
| Classic Bluetooth (SPP/RFCOMM) | V1connection (Gen1) | `BluetoothSocket`, PACK-framed ESP |
| Bluetooth LE (GATT) | V1connection LE dongle, V1 Gen2 | `V1connectionLE` bridge, bare ESP re-framed to PACK |

Device type (Gen1 vs Gen2) is recognized from the ESP version/identifier data; Gen1-only
features (e.g. custom sweeps) degrade gracefully when a Gen2 is connected.

## App feature packages (`com.glasslsoftware.yav1`)

| Package | Feature | Key data source |
|---|---|---|
| `psl` | Speed-limit-aware muting | OpenStreetMap via Overpass (`maxspeed`), tile-cached |
| `poi` | Camera / ALPR / red-light alerts + CSV import | OSM via Overpass (`highway=speed_camera`, `surveillance:type=ALPR`, enforcement relations); CSV files |
| `aircraft` | ADS-B enforcement awareness | adsb.lol / airplanes.live / adsb.fi, aggregated by ICAO hex |
| `geo` | Location-based V1 profile switching | Bundled offline US-state polygons |
| `lockout` | GPS lockouts + learning | On-device store |

### Shared patterns
- **Position** flows over an [Otto](https://square.github.io/otto/) event bus
  (`YaV1.getEventBus()`, `GpsEvent`) from `YaV1GpsService`; data-service managers subscribe
  rather than touching the GPS service directly.
- **Tile caching / rate limiting** for the network data services (`psl`, `poi`) share a
  pattern: a geographic grid keys an on-device LRU/JSON cache, requests are throttled and
  fetched off the main thread, and everything fails soft to "unknown" on error/timeout.
- **Foreground services** — `YaV1AlertService` (type `connectedDevice`) runs the detector
  link; `YaV1GpsService` (type `location`) runs positioning. Both post to a notification
  channel required since Android 8.

## Data flow (one alert)

```
V1 hardware
   │  ESP bytes (SPP socket, or GATT notification → V1connectionLE re-frames to PACK)
   ▼
DataReaderThread → ESPPacket.makeFromBuffer → PacketQueue
   ▼
ValentineESP processing thread → registered callbacks in ValentineClient
   ▼
YaV1AlertService (alert processing, muting decisions, lockouts, TTS)
   ▼
Otto bus → UI (alert board, V1 replica, overlay) + data services (POI/aircraft proximity)
```
