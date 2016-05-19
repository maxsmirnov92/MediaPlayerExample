package ru.maxsmr.commonutils.android.hardware;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Locale;


public class DeviceUtils {

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public static HashMap<String, UsbDevice> getDevicesList(@NonNull Context ctx) {
        UsbManager usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        return usbManager.getDeviceList();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public static UsbAccessory[] getAccessoryList(@NonNull Context ctx) {
        UsbManager usbManager = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
        return usbManager.getAccessoryList();
    }

    public static boolean isWakeLockHeld(@Nullable PowerManager.WakeLock wakeLock) {
        return wakeLock != null && wakeLock.isHeld();
    }

    public static boolean releaseWakeLock(@Nullable PowerManager.WakeLock wakeLock) {
        if (isWakeLockHeld(wakeLock)) {
            wakeLock.release();
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    public static PowerManager.WakeLock wakeScreen(@NonNull Context ctx, @Nullable PowerManager.WakeLock wakeLock, @NonNull String name) {
        releaseWakeLock(wakeLock);
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, name);
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        return wakeLock;
    }

    public static void showKeyguard(@NonNull Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD); // doesn't require permission
    }

    /**
     * dismiss keyguard flag not working in all cases
     */
    public static void dismissKeyguard(@NonNull Activity activity) {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD); // doesn't require permission
        // KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        // final KeyguardManager.KeyguardLock kl = km.newKeyguardLock("MyKeyguardLock");
        // kl.disableKeyguard();
    }

    public static int getScreenOffTimeout(@NonNull Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    public static void setScreenOffTimeout(@NonNull ScreenOffTimeout timeout, @NonNull Context ctx) {
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, timeout.value);
    }

    public static boolean checkPermission(Context ctx, String permission) {
        return (ctx.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
    }

    public static LanguageCode getCurrentLanguageCode(@NonNull Context ctx) {
        final Locale current = ctx.getResources().getConfiguration().locale;

        if (current.getLanguage().equalsIgnoreCase(LanguageCode.RU.getCode())) {
            return LanguageCode.RU;
        } else if (current.getLanguage().equalsIgnoreCase(LanguageCode.EN.getCode())) {
            return LanguageCode.EN;
        } else {
            return LanguageCode.OTHER;
        }
    }

    public enum ScreenOffTimeout {
        _0(15000), _1(30000), _2(60000), _3(120000), _4(600000), _5(1800000), DEFAULT(-1);

        public final int value;

        ScreenOffTimeout(int value) {
            this.value = value;
        }

        @NonNull
        public static ScreenOffTimeout fromValue(int value) {
            for (ScreenOffTimeout e : ScreenOffTimeout.values()) {
                if (e.value == value) {
                    return e;
                }
            }
            throw new IllegalArgumentException("no enum " + ScreenOffTimeout.class + " for value " + value);
        }

        @NonNull
        public static ScreenOffTimeout fromValueNoThrow(int value) {
            try {
                return fromValue(value);
            } catch (IllegalArgumentException e) {
                return DEFAULT;
            }
        }
    }

    public enum LanguageCode {

        EN("EN"), RU("RU"), OTHER("OTHER");

        private final String code;

        public String getCode() {
            return code;
        }

        LanguageCode(String code) {
            this.code = code;
        }

        public LanguageCode fromValueNoThrow(String value) {
            for (LanguageCode e : LanguageCode.values()) {
                if (e.getCode().equalsIgnoreCase(value))
                    return e;
            }
            return null;
        }

        public LanguageCode fromValue(String value) {
            LanguageCode code = fromValueNoThrow(value);
            if (code == null) {
                throw new IllegalArgumentException("Incorrect value " + value + " for enum type " + LanguageCode.class.getName());
            }
            return code;
        }
    }
}
