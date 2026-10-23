# Better Android Speaker Enhancer

## This app is a fork of AllEQ
This app will be th only one that lets you set an EQ for each volume step, so each of them will sound the best it can. Quality of each volume step over quantity of volume steps.

If you configure it well, you'll be able to have nice and bassy sound on your phone at low volumes (also quieter than the minimum volume if you want), and super loud sound at high volumes for outside use.

A rootless Android equalizer for Android 10 and newer. 

## What it does

- Adjusts ten EQ bands: 31 Hz through 16 kHz, mapped to the bands exposed by the device's Android Equalizer.
- Provides independent left/right channel gain controls (0-200%), including up to 6 dB of boost per channel.
- Keeps separate settings for the active audio output, with Bluetooth settings routed by device name and address.
- Offers three editable preset slots per output; select a slot to switch and changes save automatically.
- Lets you hold the A/B control to temporarily bypass processing.
- Shows the active audio output and can copy a diagnostics report.
- Runs its audio processor as a foreground media-playback service.

## Requirements

| Requirement | Details |
| --- | --- |
| Android | Android 10 (API 29) or newer |
| Root | Not required |
| Shizuku | Optional; needed only for Shizuku-backed actions such as Background Protection |
| Notification permission | Recommended on Android versions that request it, so the foreground-service notification is visible |

[Shizuku](https://shizuku.rikka.app) support is optional and is used for privileged actions such as applying Background Protection.
Audio-effect availability and behaviour depend on the device, Android build, and audio session. Android documents global-mix session 0 as deprecated, so system-wide coverage cannot be guaranteed on every device. The channel-gain stage uses Android `DynamicsProcessing` input gain only; its EQ, compressor, and limiter stages are disabled. Test changes at a safe volume.

The audio path uses Android's [`Equalizer`](https://developer.android.com/reference/android/media/audiofx/Equalizer) and [`DynamicsProcessing`](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing) APIs.

## Setup

1. Install and open Better Android Speaker Enhancer.
2. Turn on the main power control, adjust the bands, and use the A/B control to compare.
3. Optionally install and start [Shizuku](https://shizuku.rikka.app), grant its permission in Better Android Speaker Enhancer, then apply Background Protection from Settings if your phone aggressively stops background apps.

## Privacy and permissions

Better Android Speaker Enhancer does not declare the `INTERNET` permission. It requests audio-settings, foreground-service, notification, and battery-optimization permissions as needed for its local audio processing and service operation. Shizuku access is optional and is used for Background Protection.

## License and attribution

This project is a fork of [omixin/allEQ](https://github.com/omixin/allEQ). It is distributed under the [GNU GPL v3.0](LICENSE).
