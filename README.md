# Circular Battery Xposed

LSPosed/Xposed module for the RMX3834 Android 15 SystemUI build, with a small
RealmeUI-inspired settings app.

It hooks `com.android.systemui.battery.BatteryMeterView` and replaces the
status-bar battery drawable with a configurable circular level indicator. The
settings app also customizes the status-bar clock's visibility, seconds,
24-hour format, text size, weight, color, and Java time pattern.

## Build

```sh
gradle --no-daemon :app:assembleDebug
```

Install the APK, enable it for `SystemUI` in LSPosed, then restart SystemUI
after changing settings.

## Known limitations

- This is tied to the inspected RMX3834 Android 15 SystemUI class and fields.
- This is tied to the inspected RMX3834 Android 15 SystemUI classes and fields.
- Clock positioning is unchanged; moving it can collide with notification icons
  in this status-bar layout.
