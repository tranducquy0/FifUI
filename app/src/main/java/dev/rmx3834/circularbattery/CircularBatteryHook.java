package dev.rmx3834.circularbattery;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CircularBatteryHook implements IXposedHookLoadPackage {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String BATTERY_VIEW = "com.android.systemui.battery.BatteryMeterView";
    private static volatile Bundle settings;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> batteryView = XposedHelpers.findClass(BATTERY_VIEW, lpparam.classLoader);
            XposedHelpers.findAndHookConstructor(batteryView, android.content.Context.class,
                    android.util.AttributeSet.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            installDrawable(param.thisObject);
                        }
                    });
            XposedHelpers.findAndHookMethod(batteryView, "onBatteryLevelChanged", int.class,
                    boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            syncDrawable(param.thisObject);
                        }
                    });
            XposedHelpers.findAndHookMethod(batteryView, "onBatteryUnknownStateChanged", boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            installDrawable(param.thisObject);
                        }
                    });
            XposedHelpers.findAndHookMethod(batteryView, "updateColors", int.class, int.class,
                    int.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            syncDrawable(param.thisObject);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to hook BatteryMeterView: " + t);
        }

        hookClock(lpparam);
    }

    private static void hookClock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clockClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.policy.Clock", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(clockClass, "updateClock", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    applyClockSettings(param.thisObject);
                }
            });
            XposedHelpers.findAndHookMethod(clockClass, "updateClockVisibility", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (isStatusBarClock(param.thisObject)
                            && !getBooleanSetting((View) param.thisObject, ModuleSettings.CLOCK_VISIBLE, true)) {
                        ((View) param.thisObject).setVisibility(View.GONE);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(clockClass, "updateShowSeconds", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (isStatusBarClock(param.thisObject)) setSecondsPreference(param.thisObject);
                }
            });
            XposedHelpers.findAndHookMethod(clockClass, "onTuningChanged", String.class, String.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (isStatusBarClock(param.thisObject) && "clock_seconds".equals(param.args[0])) {
                                setSecondsPreference(param.thisObject);
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(clockClass, "onDarkChanged", java.util.ArrayList.class,
                    float.class, int.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (isStatusBarClock(param.thisObject)) applyClockColor((TextView) param.thisObject);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to hook status-bar Clock: " + t);
        }
    }

    private static void setSecondsPreference(Object clock) {
        View view = (View) clock;
        loadSettings(view);
        if (!settings.containsKey(ModuleSettings.CLOCK_SECONDS)) return;
        try {
            XposedHelpers.setBooleanField(clock, "mShowSeconds",
                    getBooleanSetting(view, ModuleSettings.CLOCK_SECONDS, false));
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to apply clock seconds: " + t);
        }
    }

    private static void applyClockSettings(Object object) {
        if (!isStatusBarClock(object)) return;
        TextView clock = (TextView) object;
        loadSettings(clock);
        try {
            int size = getIntSetting(clock, ModuleSettings.CLOCK_SIZE, 14);
            clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
            int weight = getIntSetting(clock, ModuleSettings.CLOCK_WEIGHT, 400);
            String family = weight >= 600 ? "sans-serif" : weight >= 500 ? "sans-serif-medium" : "sans-serif";
            clock.setTypeface(Typeface.create(family, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
            applyClockColor(clock);

            String format = settings.getString(ModuleSettings.CLOCK_FORMAT, "").trim();
            boolean use24Hour = getBooleanSetting(clock, ModuleSettings.CLOCK_24_HOUR, false);
            if (!format.isEmpty() || use24Hour) {
                if (format.isEmpty()) {
                    format = "HH:mm" + (getBooleanSetting(clock, ModuleSettings.CLOCK_SECONDS, false) ? ":ss" : "");
                }
                try {
                    clock.setText(new SimpleDateFormat(format, Locale.getDefault()).format(new Date()));
                } catch (IllegalArgumentException ignored) {
                    // Invalid patterns leave the SystemUI-generated text in place.
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to apply clock settings: " + t);
        }
    }

    private static void applyClockColor(TextView clock) {
        if (getBooleanSetting(clock, ModuleSettings.CLOCK_CUSTOM_COLOR, false)) {
            clock.setTextColor(getIntSetting(clock, ModuleSettings.CLOCK_COLOR, Color.WHITE));
        }
    }

    private static boolean getBooleanSetting(View view, String key, boolean defaultValue) {
        loadSettings(view);
        return settings.getBoolean(key, defaultValue);
    }

    private static int getIntSetting(View view, String key, int defaultValue) {
        loadSettings(view);
        return settings.getInt(key, defaultValue);
    }

    private static void loadSettings(View view) {
        if (settings != null) return;
        synchronized (CircularBatteryHook.class) {
            if (settings != null) return;
            try {
                Bundle loaded = view.getContext().getContentResolver().call(
                        Uri.parse("content://" + ModuleSettings.AUTHORITY), "read", null, null);
                if (loaded == null) throw new IllegalStateException("settings provider returned no data");
                settings = loaded;
            } catch (Throwable t) {
                XposedBridge.log("CircularBattery: failed to read settings: " + t);
                settings = Bundle.EMPTY;
            }
        }
    }

    private static boolean isStatusBarClock(Object object) {
        if (!(object instanceof View)) return false;
        android.view.ViewParent parent = ((View) object).getParent();
        while (parent != null) {
            if ("com.android.systemui.statusbar.phone.PhoneStatusBarView".equals(
                    parent.getClass().getName())) return true;
            parent = parent instanceof View ? ((View) parent).getParent() : null;
        }
        return false;
    }

    private static void installDrawable(Object view) {
        try {
            loadSettings((View) view);
            if (!getBooleanSetting((View) view, ModuleSettings.BATTERY_ENABLED, true)) {
                return;
            }
            ImageView icon = (ImageView) getField(view, "mBatteryIconView");
            if (!(icon.getDrawable() instanceof CircularBatteryDrawable)) {
                icon.setImageDrawable(new CircularBatteryDrawable());
            }
            syncDrawable(view);
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to install drawable: " + t);
        }
    }

    private static void syncDrawable(Object view) {
        try {
            loadSettings((View) view);
            ImageView icon = (ImageView) getField(view, "mBatteryIconView");
            Drawable drawable = icon.getDrawable();
            if (!(drawable instanceof CircularBatteryDrawable)) {
                return;
            }
            CircularBatteryDrawable circular = (CircularBatteryDrawable) drawable;
            circular.setBatteryLevel(getIntField(view, "mLevel"));
            circular.setCharging(getBooleanField(view, "mPluggedIn"));
            circular.setPowerSave(getBooleanField(view, "mPowerSaveEnabled"));
            int color = getIntField(view, "mTextColor");
            circular.setColor(color == 0 ? Color.WHITE : color);
            circular.setThickness(getIntSetting((View) view, ModuleSettings.BATTERY_THICKNESS, 15));
            circular.setShowCharging(getBooleanSetting((View) view, ModuleSettings.BATTERY_CHARGING, true));
            circular.setShowPowerSave(getBooleanSetting((View) view, ModuleSettings.BATTERY_POWER_SAVE, true));
            circular.invalidateSelf();
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to sync drawable: " + t);
        }
    }

    private static Object getField(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static int getIntField(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }

    private static boolean getBooleanField(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(object);
    }
}
