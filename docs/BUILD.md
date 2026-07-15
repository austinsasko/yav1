# Building & running YaV1

## Toolchain

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.10.2 (via the committed wrapper) |
| JDK | 17 |
| compileSdk / targetSdk | 35 |
| minSdk | 21 |
| Build-tools | 35.0.0 |

The Gradle wrapper is committed, so you do not need Gradle installed — use `./gradlew`.

## One-time setup

1. Install a **JDK 17** (Temurin, Zulu, or the JetBrains runtime all work).
2. Install the **Android SDK** (Android Studio, or the command-line tools). You need
   `platforms;android-35`, `build-tools;35.0.0`, and `platform-tools`.
3. Point Gradle at the SDK. Either set `ANDROID_HOME` / `ANDROID_SDK_ROOT`, or create a
   `local.properties` file in the repo root:

   ```properties
   sdk.dir=/Users/you/Library/Android/sdk
   ```

   `local.properties` is machine-specific and intentionally **not** committed.

## Build

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"   # macOS; use your JDK 17 path elsewhere
./gradlew :YaV1:assembleDebug
```

The APK lands at `YaV1/build/outputs/apk/debug/YaV1-debug.apk`.

## Test

```bash
# both modules that carry unit tests
./gradlew :ESPLibrary:testDebugUnitTest :YaV1:testDebugUnitTest
```

> **Note:** ESPLibrary keeps its tests in a non-standard `ESPLibrary/tests/` source set
> (wired up in `ESPLibrary/build.gradle`). Running the whole thing with the module targets
> above picks up both suites.

## Run on an emulator

```bash
# create an AVD once (Android 15, arm64 for Apple silicon; x86_64 on Intel/Linux)
sdkmanager "system-images;android-35;google_apis;arm64-v8a"
avdmanager create avd -n yav1 -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_5

# boot it, install, launch
emulator -avd yav1 -no-snapshot &
adb install -r YaV1/build/outputs/apk/debug/YaV1-debug.apk
adb shell am start -n com.glasslsoftware.yav1/.YaV1Activity
```

### Trying it without hardware

Open the overflow menu → **Demo** and pick a recorded scenario (including `demo_gen2` for a
Gen2 session). Demo mode replays ESP data through the full parsing/alert pipeline.

To exercise the location-driven features on an emulator, enable them in **Settings**, turn
on `use_gps`, and feed movement:

```bash
adb emu geo fix <lon> <lat> 0 10 <speed_knots>
```

Speed-limit, camera, and aircraft services read the emulator's real network connection.

## Gotchas

- **Never turn on R8 / minification.** ESP packet callbacks are registered via string
  reflection and are silently stripped by the shrinker.
- `android.nonFinalResIds=false` is set in `gradle.properties` because some legacy code
  `switch`es over resource IDs — leave it.
- The old `com.google.android.maps` (Maps v1) library is declared `required="false"` so the
  app still installs on devices without it; the app uses the Maps SDK (Play services) v2.
