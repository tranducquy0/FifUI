package dev.rmx3834.circularbattery;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CircularBatteryHook implements IXposedHookLoadPackage {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String BATTERY_VIEW = "com.android.systemui.battery.BatteryMeterView";
    private static final String BATTERY_CONTROLLER_CALLBACK =
            "com.android.systemui.battery.BatteryMeterViewController$3";
    private static final String CLOCK = "com.android.systemui.statusbar.policy.Clock";
    private static volatile Bundle settings;
    private static volatile boolean settingsReadFailureLogged;
    private static volatile boolean batterySyncLogged;
    private static volatile int lastAppliedClockColor = Integer.MIN_VALUE;
    private static final Set<Class<?>> HOOKED_CLASSES = Collections.newSetFromMap(new WeakHashMap<Class<?>, Boolean>());
    private static final Set<View> SETTINGS_VIEWS = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static ContentResolverSettingsObserver settingsObserver;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }

        hookDeferredClassLookup();
        hookClassIfAvailable(BATTERY_VIEW, lpparam.classLoader);
        hookClassIfAvailable(BATTERY_CONTROLLER_CALLBACK, lpparam.classLoader);
        hookClassIfAvailable(CLOCK, lpparam.classLoader);
    }

    private static void hookDeferredClassLookup() {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onSystemUiClassLoaded((String) param.args[0], param.getResult());
                        }
                    });
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class,
                    boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    onSystemUiClassLoaded((String) param.args[0], param.getResult());
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to watch SystemUI class loading: " + t);
        }
    }

    private static void onSystemUiClassLoaded(String name, Object result) {
        if (!(result instanceof Class<?>) || !(BATTERY_VIEW.equals(name)
                || BATTERY_CONTROLLER_CALLBACK.equals(name) || CLOCK.equals(name))) return;
        Class<?> loadedClass = (Class<?>) result;
        if (BATTERY_VIEW.equals(name)) hookBatteryClass(loadedClass);
        else if (BATTERY_CONTROLLER_CALLBACK.equals(name)) hookBatteryPowerSaveClass(loadedClass);
        else hookClockClass(loadedClass);
    }

    private static void hookClassIfAvailable(String name, ClassLoader classLoader) {
        try {
            Class<?> loadedClass = XposedHelpers.findClassIfExists(name, classLoader);
            if (loadedClass != null) {
                if (BATTERY_VIEW.equals(name)) hookBatteryClass(loadedClass);
                else if (BATTERY_CONTROLLER_CALLBACK.equals(name)) hookBatteryPowerSaveClass(loadedClass);
                else hookClockClass(loadedClass);
            }
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: initial lookup failed for " + name + ": " + t);
        }
    }

    private static void hookBatteryPowerSaveClass(Class<?> callbackClass) {
        synchronized (HOOKED_CLASSES) {
            if (HOOKED_CLASSES.contains(callbackClass)) return;
            try {
                XposedHelpers.findAndHookMethod(callbackClass, "onPowerSaveChanged", boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    Object controller = getField(param.thisObject, "this$0");
                                    syncDrawable(getField(controller, "mView"));
                                } catch (Throwable t) {
                                    XposedBridge.log("CircularBattery: failed to sync after power saver change: " + t);
                                }
                            }
                        });
                HOOKED_CLASSES.add(callbackClass);
                XposedBridge.log("CircularBattery: hooked battery power saver callback");
            } catch (Throwable t) {
                XposedBridge.log("CircularBattery: failed to hook battery power saver callback: " + t);
            }
        }
    }

    private static void hookBatteryClass(Class<?> batteryView) {
        synchronized (HOOKED_CLASSES) {
            if (HOOKED_CLASSES.contains(batteryView)) return;
            try {
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
                XposedHelpers.findAndHookMethod(batteryView, "updatePercentText", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        syncDrawable(param.thisObject);
                    }
                });
                HOOKED_CLASSES.add(batteryView);
                XposedBridge.log("CircularBattery: hooked " + BATTERY_VIEW);
            } catch (Throwable t) {
                XposedBridge.log("CircularBattery: failed to hook BatteryMeterView: " + t);
            }
        }
    }

    private static void hookClockClass(Class<?> clockClass) {
        synchronized (HOOKED_CLASSES) {
            if (HOOKED_CLASSES.contains(clockClass)) return;
            int successfulHooks = 0;
            try {
                XposedHelpers.findAndHookMethod(clockClass, "updateClock", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        applyClockSettings(param.thisObject);
                    }
                });
                successfulHooks++;
                XposedHelpers.findAndHookMethod(clockClass, "updateClockVisibility", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (isStatusBarClock(param.thisObject)
                                && !getBooleanSetting((View) param.thisObject, ModuleSettings.CLOCK_VISIBLE, true)) {
                            ((View) param.thisObject).setVisibility(View.GONE);
                        }
                    }
                });
                successfulHooks++;
                XposedHelpers.findAndHookMethod(clockClass, "updateShowSeconds", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        setSecondsPreference(param.thisObject);
                    }
                });
                successfulHooks++;
                XposedHelpers.findAndHookMethod(clockClass, "onTuningChanged", String.class, String.class,
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if ("clock_seconds".equals(param.args[0])) setSecondsPreference(param.thisObject);
                            }
                        });
                successfulHooks++;
                XposedHelpers.findAndHookMethod(clockClass, "onDarkChanged", java.util.ArrayList.class,
                        float.class, int.class, new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if (isStatusBarClock(param.thisObject)) applyClockColor((TextView) param.thisObject);
                            }
                        });
                successfulHooks++;
                HOOKED_CLASSES.add(clockClass);
                XposedBridge.log("CircularBattery: hooked " + CLOCK);
            } catch (Throwable t) {
                if (successfulHooks == 0) HOOKED_CLASSES.remove(clockClass);
                XposedBridge.log("CircularBattery: failed to hook status-bar Clock after "
                        + successfulHooks + " methods: " + t);
            }
        }
    }

    private static void setSecondsPreference(Object clock) {
        View view = (View) clock;
        loadSettings(view);
        Bundle currentSettings = settings;
        if (currentSettings == null || !currentSettings.containsKey(ModuleSettings.CLOCK_SECONDS)) return;
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

            Bundle currentSettings = settings;
            String format = currentSettings == null ? ""
                    : currentSettings.getString(ModuleSettings.CLOCK_FORMAT, "").trim();
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
            int color = getIntSetting(clock, ModuleSettings.CLOCK_COLOR, Color.WHITE);
            clock.setTextColor(color);
            if (lastAppliedClockColor != color) {
                XposedBridge.log("CircularBattery: applied fixed clock color #"
                        + String.format(Locale.ROOT, "%08X", color));
                lastAppliedClockColor = color;
            }
        }
    }

    private static boolean getBooleanSetting(View view, String key, boolean defaultValue) {
        loadSettings(view);
        Bundle currentSettings = settings;
        return currentSettings == null ? defaultValue : currentSettings.getBoolean(key, defaultValue);
    }

    private static int getIntSetting(View view, String key, int defaultValue) {
        loadSettings(view);
        Bundle currentSettings = settings;
        return currentSettings == null ? defaultValue : currentSettings.getInt(key, defaultValue);
    }

    private static void loadSettings(View view) {
        registerSettingsObserver(view);
        if (settings != null) return;
        synchronized (CircularBatteryHook.class) {
            if (settings != null) return;
            try {
                Bundle loaded = view.getContext().getContentResolver().call(
                        Uri.parse("content://" + ModuleSettings.AUTHORITY), "read", null, null);
                if (loaded == null) throw new IllegalStateException("settings provider returned no data");
                settings = loaded;
                settingsReadFailureLogged = false;
                XposedBridge.log("CircularBattery: settings loaded; batteryEnabled="
                        + loaded.getBoolean(ModuleSettings.BATTERY_ENABLED, true)
                        + ", batteryCustomColor="
                        + loaded.getBoolean(ModuleSettings.BATTERY_CUSTOM_COLOR, false)
                        + ", hasBatteryColor=" + loaded.containsKey(ModuleSettings.BATTERY_COLOR)
                        + ", hasLowBatteryColor=" + loaded.containsKey(ModuleSettings.BATTERY_COLOR_LOW)
                        + ", customClockColor="
                        + loaded.getBoolean(ModuleSettings.CLOCK_CUSTOM_COLOR, false)
                        + ", hasClockColor=" + loaded.containsKey(ModuleSettings.CLOCK_COLOR)
                        + ", batteryThickness=" + loaded.getInt(ModuleSettings.BATTERY_THICKNESS, 15));
            } catch (Throwable t) {
                if (!settingsReadFailureLogged) {
                    XposedBridge.log("CircularBattery: failed to read settings; will retry: " + t);
                    settingsReadFailureLogged = true;
                }
            }
        }
    }

    private static void registerSettingsObserver(View view) {
        synchronized (CircularBatteryHook.class) {
            SETTINGS_VIEWS.add(view);
            if (settingsObserver != null) return;
            settingsObserver = new ContentResolverSettingsObserver();
            view.getContext().getContentResolver().registerContentObserver(
                    Uri.parse("content://" + ModuleSettings.AUTHORITY), false, settingsObserver);
        }
    }

    private static final class ContentResolverSettingsObserver extends ContentObserver {
        ContentResolverSettingsObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            settings = null;
            synchronized (CircularBatteryHook.class) {
                for (View view : SETTINGS_VIEWS) {
                    if (view == null) continue;
                    loadSettings(view);
                    if (view.getClass().getName().equals(BATTERY_VIEW)) {
                        syncDrawable(view);
                    } else if (view instanceof TextView && CLOCK.equals(view.getClass().getName())) {
                        Object clock = view;
                        applyClockSettings(clock);
                        setSecondsPreference(clock);
                        if (!getBooleanSetting(view, ModuleSettings.CLOCK_VISIBLE, true)) {
                            view.setVisibility(View.GONE);
                        } else {
                            XposedHelpers.callMethod(clock, "updateClockVisibility");
                        }
                    }
                }
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
            int color = getBatteryColorForCurrentState(view);
            circular.setColor(color);
            Object percentView = getField(view, "mBatteryPercentView");
            if (percentView instanceof TextView) {
                ((TextView) percentView).setTextColor(color);
            }
            circular.setThickness(getIntSetting((View) view, ModuleSettings.BATTERY_THICKNESS, 15));
            circular.setShowCharging(getBooleanSetting((View) view, ModuleSettings.BATTERY_CHARGING, true));
            circular.setShowPowerSave(getBooleanSetting((View) view, ModuleSettings.BATTERY_POWER_SAVE, true));
            circular.invalidateSelf();
            if (!batterySyncLogged && settings != null) {
                XposedBridge.log("CircularBattery: battery drawable synced; customColor="
                        + getBooleanSetting((View) view, ModuleSettings.BATTERY_CUSTOM_COLOR, false)
                        + ", level=" + getIntField(view, "mLevel")
                        + ", charging=" + getBooleanField(view, "mPluggedIn")
                        + ", powerSave=" + getBooleanField(view, "mPowerSaveEnabled")
                        + ", color=#" + String.format(Locale.ROOT, "%08X", color)
                        + ", percentLabel=" + (percentView instanceof TextView));
                batterySyncLogged = true;
            }
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to sync drawable: " + t);
        }
    }

    private static int getBatteryColorForCurrentState(Object view) {
        View batteryView = (View) view;
        if (!getBooleanSetting(batteryView, ModuleSettings.BATTERY_CUSTOM_COLOR, false)) {
            return getBatteryStateColor(view);
        }

        String stateColorKey = null;
        try {
            Object chargingResult = XposedHelpers.callMethod(view, "isCharging");
            boolean charging = chargingResult instanceof Boolean && (Boolean) chargingResult;
            boolean powerSave = getBooleanField(view, "mPowerSaveEnabled");
            int level = getIntField(view, "mLevel");
            if (charging) stateColorKey = ModuleSettings.BATTERY_COLOR_CHARGING;
            else if (powerSave) stateColorKey = ModuleSettings.BATTERY_COLOR_POWER_SAVE;
            else if (level <= 20) stateColorKey = ModuleSettings.BATTERY_COLOR_LOW;
            else stateColorKey = ModuleSettings.BATTERY_COLOR_NORMAL;
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to determine battery color state: " + t);
        }

        Bundle currentSettings = settings;
        if (currentSettings != null && stateColorKey != null && currentSettings.containsKey(stateColorKey)) {
            return currentSettings.getInt(stateColorKey);
        }
        if (currentSettings != null && currentSettings.containsKey(ModuleSettings.BATTERY_COLOR)) {
            return currentSettings.getInt(ModuleSettings.BATTERY_COLOR);
        }
        return getBatteryStateColor(view);
    }

    /** Reads ThemedBatteryDrawable.levelColor, which SystemUI calculates from battery state. */
    private static int getBatteryStateColor(Object view) {
        try {
            Object accessorizedDrawable = getField(view, "mDrawable");
            Object themedDrawable = XposedHelpers.callMethod(accessorizedDrawable, "getDrawable");
            int stateColor = getIntField(themedDrawable, "levelColor");
            if (Color.alpha(stateColor) != 0) return stateColor;
        } catch (Throwable t) {
            XposedBridge.log("CircularBattery: failed to read SystemUI battery state color: " + t);
        }
        try {
            int textColor = getIntField(view, "mTextColor");
            return textColor == 0 ? Color.WHITE : textColor;
        } catch (Throwable ignored) {
            return Color.WHITE;
        }
    }

    private static Object getField(Object object, String name) throws ReflectiveOperationException {
        Class<?> currentClass = object.getClass();
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
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
