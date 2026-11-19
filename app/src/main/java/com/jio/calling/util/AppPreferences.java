package com.jio.calling.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Manages user configurations and toggles for Jio Landline:
 * - Stock dialer call interception
 * - Android Telecom calling account registration
 * - In-call UI mode (System dialer vs App overlay)
 * - Emergency number safety bypass
 */
public class AppPreferences {
    private static final String PREF_NAME = "jio_calling_config";

    private static final String KEY_INTERCEPT_CALLS = "intercept_outgoing_calls";
    private static final String KEY_TELECOM_ENABLED = "telecom_account_enabled";
    private static final String KEY_USE_TELECOM_UI = "use_telecom_system_ui";
    private static final String KEY_EXCLUDE_EMERGENCY = "exclude_emergency_numbers";

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Whether outgoing calls dialed from the stock phone dialer should be intercepted.
     * Default: true
     */
    public static boolean isCallInterceptionEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_INTERCEPT_CALLS, true);
    }

    public static void setCallInterceptionEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_INTERCEPT_CALLS, enabled).apply();
    }

    /**
     * Whether to register a PhoneAccount with Android's TelecomManager (Android 6.0+).
     * Default: true on API 23+, false on older versions.
     */
    public static boolean isTelecomEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        return getPrefs(context).getBoolean(KEY_TELECOM_ENABLED, true);
    }

    public static void setTelecomEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_TELECOM_ENABLED, enabled).apply();
    }

    /**
     * Whether to route incoming calls to Android Telecom system in-call UI instead of app's custom UI.
     * Default: false (custom high-contrast UI)
     */
    public static boolean isUseTelecomUiEnabled(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        return getPrefs(context).getBoolean(KEY_USE_TELECOM_UI, false);
    }

    public static void setUseTelecomUiEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_USE_TELECOM_UI, enabled).apply();
    }

    /**
     * Whether emergency numbers (100, 101, 102, 108, 112, 911) should strictly bypass interception.
     * Default: true
     */
    public static boolean isEmergencyExclusionEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_EXCLUDE_EMERGENCY, true);
    }

    public static void setEmergencyExclusionEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_EXCLUDE_EMERGENCY, enabled).apply();
    }
}
