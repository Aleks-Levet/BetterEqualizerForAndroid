# BetterEQ

A rootless Android equalizer for Android 10 and newer. [Shizuku](https://shizuku.rikka.app) support is optional and is used for privileged actions such as applying Background Protection.

## What it does

- Adjusts ten EQ bands: 31 Hz through 16 kHz.
- Provides Bass Boost, a pre-amp control, and independent left/right channel levels.
- Includes the built-in Flat, Bass Punch, Vocal Clarity, Club, Rock, Cinematic, and Acoustic presets.
- Keeps separate settings for the active output type, such as speaker, wired headset, Bluetooth, or USB audio.
- Offers three quick-preset slots for each output type.
- Lets you hold the A/B control to temporarily bypass processing.
- Shows the active audio output and can copy a diagnostics report.
- Runs its audio processor as a foreground media-playback service.

The app does not include Smart Dynamic Bass or volume-adaptive bass processing.

## Requirements

| Requirement | Details |
| --- | --- |
| Android | Android 10 (API 29) or newer |
| Root | Not required |
| Shizuku | Optional; needed only for Shizuku-backed actions such as Background Protection |
| Notification permission | Recommended on Android versions that request it, so the foreground-service notification is visible |

Audio-effect availability and behaviour ultimately depend on the device, Android build, and audio session. Test changes at a safe volume.

## Setup

1. Install and open BetterEQ.
2. Turn on the main power control, choose a preset or adjust the bands, and use the A/B control to compare.
3. Optionally install and start [Shizuku](https://shizuku.rikka.app), grant its permission in BetterEQ, then apply Background Protection from Settings if your phone aggressively stops background apps.

## Building from source

Requirements:

- JDK 17
- Android SDK Platform 35

```bash
git clone <repository-url>
cd BetterEqualizerForAndroid
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To build a release APK, provide `keystore.properties` with the signing values expected by `app/build.gradle.kts`, then run:

```bash
./gradlew assembleRelease
```

## Privacy and permissions

BetterEQ does not declare the `INTERNET` permission. It requests audio-settings, foreground-service, notification, and battery-optimization permissions as needed for its local audio processing and service operation. Shizuku access is optional and is used for Background Protection.

## License and attribution

This project is a fork of [omixin/allEQ](https://github.com/omixin/allEQ). It is distributed under the [GNU GPL v3.0](LICENSE).
