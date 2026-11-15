# Circular Battery Xposed

Minimal LSPosed/Xposed module for the RMX3834 Android 15 SystemUI build.

It hooks `com.android.systemui.battery.BatteryMeterView` and replaces the
status-bar battery drawable with a circular level indicator. Charging is shown
with a center dot; power saver is shown with a center line.

## Build

```sh
./gradlew :app:assembleDebug
```

Install the APK, enable it for `SystemUI` in LSPosed, then restart SystemUI.

## Known limitations

- This is tied to the inspected RMX3834 Android 15 SystemUI class and fields.
- The unknown-battery drawable is currently replaced on the next state sync.
- The first version does not expose user settings or icon style choices.
