# FifUI

RealmeUI-inspired controls for the RMX3834 status bar. A small settings app,
plus an LSPosed module doing the actual SystemUI hooks.

## Features

- Replace the status-bar battery icon with a circular level ring.
- Tune ring thickness and charging / power-saver indicators.
- Customize the status-bar clock: visibility, seconds, 24-hour time, text size,
  weight, fixed color, and a custom Java `SimpleDateFormat` pattern.
- Choose clock colors from presets or enter `#RRGGBB` / `#AARRGGBB`.

## Install and enable

1. Install `app-debug.apk` and enable **FifUI** in LSPosed.
2. Scope the module to **SystemUI** (`com.android.systemui`).
3. Open FifUI and adjust settings. Restart SystemUI for changes to apply.

If SystemUI gets unstable, disable FifUI in LSPosed and restart SystemUI.

## Build

Requires Java 17, Gradle 8.11.1, and Android SDK 35.

```sh
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- Hooks target the inspected RMX3834 Android 15 SystemUI classes and fields; other builds may differ.
- Clock position is unchanged because this layout places it alongside notification icons.
- Time patterns use Java `SimpleDateFormat` syntax, e.g. `h:mm a` or `HH:mm:ss`.
