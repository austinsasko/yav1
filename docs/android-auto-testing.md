# Android Auto testing guide

YaV1 now ships a native Android Auto car app (`com.franckyl.yav1.car`, branch
`android-auto`): a NavigationTemplate whose background surface draws the live
radar display (priority alert band / GHz frequency / direction arrow / 0-8
signal bars, bogey count, secondary alerts) with a single Mute action, plus
heads-up alert cards over Maps/Waze via `CarAppExtender` notifications.

This document lists what was already verified on the emulator and the exact
steps for the two test passes that need real hardware: **DHU (Desktop Head
Unit)** with a real phone, and a **real-car BTLE smoke test**.

---

## 1. What is already verified (emulator `yav1_test`, API 33 arm64)

Verified on 2026-07-14 with demo mode (no V1 hardware, no Bluetooth):

| Check | Result |
|---|---|
| `JAVA_HOME=<jdk17> ./gradlew :YaV1:assembleDebug` clean after every phase | pass |
| APK installs and runs; demo mode shows the full alert list on the phone UI (Ka/K/X, frequencies, signal dots, arrows) — no regression from the car-app changes | pass |
| `V1AlertRepository` delivers the initial snapshot and all updates **on the main thread** (logcat tag `V1CarRepo`) | pass |
| Connection states: `DISCONNECTED -> CONNECTED_IDLE -> ALERTING` on demo start, live alert/bogey updates (~1-2 Hz), back to `DISCONNECTED` within ~4s of demo end | pass |
| User mute toggles through the shared service path (`mMuteByUser`), reflected immediately in the phone sound icon | pass |
| `YaV1CarAppService` present in the merged manifest and registered in the package manager with action `androidx.car.app.CarAppService` + category `androidx.car.app.category.NAVIGATION` | pass |
| `V1CarNotifier` initializes cleanly; `CarConnection` reports type 0 (not projected) on the emulator; zero crashes in the crash buffer | pass |

Not coverable on the emulator: everything that needs an AA host (car screen
rendering, ActionStrip, HUN cards), the V1 soft-mute **ack** (`Snapshot.muted`
comes from replayed demo packets, so it never flips in demo), Bluetooth/BTLE,
and real audio routing.

To watch the repository yourself:

```
adb logcat -s V1CarRepo:D V1CarScreen:D V1CarRender:D V1CarNotify:D
```

---

## 2. DHU testing (needs a real phone; V1 hardware NOT needed)

### One-time setup

1. Install DHU: `~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "extras;google;auto"`
   -> binary at `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`.
2. On the phone, enable AA developer mode: Settings -> Google -> Android Auto
   (or the Android Auto app) -> tap **Version** ~10 times -> accept.
3. In Android Auto **Developer settings**: enable **Unknown sources**
   (required or the sideloaded, self-signed YaV1 will not appear on the car
   launcher).
4. Install the debug APK on the phone:
   `adb install -r YaV1/build/outputs/apk/debug/YaV1-debug.apk`

### Each session

1. Phone connected over USB with adb debugging on.
2. In Android Auto developer settings (three-dot menu): **Start head unit server**.
3. On the desktop: `adb forward tcp:5277 tcp:5277`, then run
   `~/Library/Android/sdk/extras/google/auto/desktop-head-unit`.
4. On the phone: open YaV1 -> options menu -> **Demo** to generate alerts.
5. On the DHU launcher: open **YaV1**.

### What to verify (P2/P3 acceptance)

- [ ] YaV1 appears in the DHU launcher (nav-apps group).
- [ ] With demo running, the car surface shows the priority alert big (band,
      frequency, arrow, signal bars), bogey count, and up to 3 secondary
      alerts; updates live.
- [ ] Idle state ("V1 connected - No alerts") when demo is between scenarios;
      disconnected state ("Open YaV1 on your phone to connect") within ~5s of
      quitting/ending demo on the phone.
- [ ] Mute action in the ActionStrip toggles (icon + label flip immediately);
      with a real V1, `Snapshot.muted` flips when the V1 acks soft mute
      (watch `V1CarRepo` logs); the phone sound icon mirrors it.
- [ ] Phone UI stays fully usable while the car screen is up.
- [ ] No crash when the DHU connects mid-alert, or when you kill the DHU
      mid-alert (disconnect mid-draw).
- [ ] Foreground Google Maps/Waze on the DHU and start navigation, then let a
      new demo alert fire: a heads-up card (band + arrow + frequency +
      strength) pops over the nav app, guidance keeps running unmuted, alert
      audio plays (ducks media). Tapping the card foregrounds YaV1's car
      screen. Gated by "Android Auto alert cards" in Sound settings
      (`aa_hun_alerts`, default on).
- [ ] Screenshots for the record: DHU alerting / idle / disconnected.

DHU quirks: if the launcher does not show YaV1, force-stop Android Auto on
the phone and restart the head unit server (the app list is cached), and
double-check "Unknown sources".

---

## 3. Real-car BTLE smoke test (needs V1 + car with Android Auto)

1. Phone paired to the car (USB or wireless AA), V1connection LE dongle in
   the V1.
2. Open YaV1 on the phone, **Scan for BTLE devices**, connect to the V1
   (this is the supported v1 flow: phone connects, car displays).
3. Drive past a known radar source (or use a leaky door opener for K band).
4. Verify on the head unit: live alert rendering; mute from the car ActionStrip
   actually soft-mutes the V1 (audio stops, `muted` flag flips); alerts and
   audio still reach you while Maps/Waze is foregrounded (HUN card + tones,
   guidance not interrupted); YaV1 never steals the screen on its own.
5. Verify the alert audio: tones play through the car speakers and duck car
   media (the SoundPool now uses navigation-guidance audio attributes with
   transient-may-duck focus). Also re-test phone-only alerting (no AA) since
   the audio attributes changed: tones, ducking of phone media, "no sound
   when music active" pref.
6. Disconnect the phone from the car mid-alert: no crash, phone alerting
   continues.

---

## 4. Known limitations / notes

- **v1 scope is "phone connects, car displays."** If the V1 was never
  connected from the phone, the car screen shows an instruction, not a
  connect button. Auto-connect-from-car is a possible fast-follow.
- YaV1 **never** claims navigation focus (no `navigationStarted()`, no
  turn-by-turn, no cluster) - Maps/Waze keep guidance and their nav card even
  with YaV1 foregrounded on the head unit.
- `Snapshot.muted` is the V1's soft-mute **ack** and only flips with real
  hardware; the ActionStrip mute icon reflects the user's intent immediately
  (`mMuteByUser`), matching the phone toggle semantics.
- Demo mode cannot ack mute, report a V1 version, or exercise BTLE.
- Keep minification **off** (reflection-based ESP callbacks).
- The `yav1_test` emulator (google_apis) has no Android Auto app and no
  Bluetooth; DHU work must run against a real phone.
