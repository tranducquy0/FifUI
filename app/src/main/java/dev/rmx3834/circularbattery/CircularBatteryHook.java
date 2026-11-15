package dev.rmx3834.circularbattery;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CircularBatteryHook implements IXposedHookLoadPackage {
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String BATTERY_VIEW = "com.android.systemui.battery.BatteryMeterView";

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
    }

    private static void installDrawable(Object view) {
        try {
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
