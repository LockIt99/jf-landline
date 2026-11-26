package com.jio.calling.telecom;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import com.jio.calling.model.JioAccount;
import com.jio.calling.provisioning.JioAccountStorage;
import com.jio.calling.util.AppPreferences;

/**
 * Helper coordinating Android TelecomManager and Calling Accounts for Android 6.0+ (API 23+).
 * Safely guards all Telecom class references to ensure zero VerifyError or runtime crashes on KitKat (API 19).
 */
public class JioTelecomManager {
    private static final String TAG = "JioTelecomManager";

    /**
     * Checks if this Android device supports Telecom Calling Accounts (requires API 23+).
     */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * Registers the Jio Landline PhoneAccount with Android Telecom if supported and enabled.
     */
    public static boolean registerPhoneAccount(Context context) {
        if (!isSupported()) {
            Log.d(TAG, "Telecom Calling Accounts not supported on API < 23");
            return false;
        }
        return Api23Impl.registerPhoneAccount(context);
    }

    /**
     * Unregisters the Jio Landline PhoneAccount from Android Telecom.
     */
    public static void unregisterPhoneAccount(Context context) {
        if (!isSupported()) return;
        Api23Impl.unregisterPhoneAccount(context);
    }

    /**
     * Checks if the Jio Landline PhoneAccount is currently registered and enabled in Android Settings.
     */
    public static boolean isPhoneAccountEnabled(Context context) {
        if (!isSupported()) return false;
        return Api23Impl.isPhoneAccountEnabled(context);
    }

    /**
     * Checks if the PhoneAccount is registered with TelecomManager (regardless of whether user toggled it in Settings).
     */
    public static boolean isPhoneAccountRegistered(Context context) {
        if (!isSupported()) return false;
        return Api23Impl.isPhoneAccountRegistered(context);
    }

    /**
     * Launches the system Android Calling Accounts settings screen.
     * Tries standard AOSP first, falling back to Realme-specific hidden activity if needed.
     */
    public static boolean openCallingAccountsSettings(Context context) {
        if (!isSupported()) return false;
        return Api23Impl.openCallingAccountsSettings(context);
    }

    /**
     * Explicitly launches standard AOSP Telecom calling accounts screen.
     */
    public static boolean openStandardCallingAccountsSettings(Context context) {
        if (!isSupported()) return false;
        return Api23Impl.openStandardCallingAccountsSettings(context);
    }

    /**
     * Explicitly launches Realme/ColorOS hidden calling accounts toggle screen.
     */
    public static boolean openRealmeCallingAccountsSettings(Context context) {
        if (!isSupported()) return false;
        return Api23Impl.openRealmeCallingAccountsSettings(context);
    }

    /**
     * Returns the PhoneAccountHandle for the Jio Landline VoIP account.
     */
    public static PhoneAccountHandle getPhoneAccountHandle(Context context) {
        if (!isSupported()) return null;
        return Api23Impl.getHandle(context);
    }

    /**
     * Notifies Android Telecom of an incoming call, launching the system incoming call UI.
     */
    public static boolean addNewIncomingCall(Context context, String remoteNumber, String remoteName) {
        if (!isSupported()) return false;
        return Api23Impl.addNewIncomingCall(context, remoteNumber, remoteName);
    }

    /**
     * Places an outgoing call through Android Telecom using the registered Jio PhoneAccount.
     */
    public static boolean placeCall(Context context, String number) {
        if (!isSupported()) return false;
        return Api23Impl.placeCall(context, number);
    }

    /**
     * Isolated inner class holding all direct references to android.telecom.* and android.graphics.drawable.Icon.
     * Dalvik / ART will ONLY load this class on API 23+, completely preventing any class resolution crash on KitKat.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private static class Api23Impl {
        private static final String ACCOUNT_ID = "jio_landline_voip";

        static PhoneAccountHandle getHandle(Context context) {
            ComponentName componentName = new ComponentName(context, JioConnectionService.class);
            return new PhoneAccountHandle(componentName, ACCOUNT_ID);
        }

        static boolean registerPhoneAccount(Context context) {
            try {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm == null) return false;

                PhoneAccountHandle handle = getHandle(context);
                JioAccount account = JioAccountStorage.load(context);
                String number = (account != null && account.getMsisdn() != null && !account.getMsisdn().isEmpty())
                        ? account.getMsisdn()
                        : "Jio Landline";

                PhoneAccount.Builder builder = PhoneAccount.builder(handle, "Jio Landline (" + number + ")")
                        .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                        .setAddress(Uri.fromParts("tel", number, null))
                        .setSubscriptionAddress(Uri.fromParts("tel", number, null))
                        .setShortDescription("JioFiber VoIP Landline")
                        .addSupportedUriScheme("tel")
                        .addSupportedUriScheme("sip");

                try {
                    builder.setIcon(Icon.createWithResource(context, android.R.drawable.sym_action_call));
                } catch (Throwable ignored) {}

                tm.registerPhoneAccount(builder.build());
                Log.i(TAG, "Successfully registered PhoneAccount with TelecomManager: " + number);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to register PhoneAccount: " + e.getMessage(), e);
                return false;
            }
        }

        static void unregisterPhoneAccount(Context context) {
            try {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) {
                    tm.unregisterPhoneAccount(getHandle(context));
                    Log.i(TAG, "Unregistered PhoneAccount from TelecomManager");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to unregister PhoneAccount: " + e.getMessage());
            }
        }

        static boolean isPhoneAccountRegistered(Context context) {
            try {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) {
                    PhoneAccount account = tm.getPhoneAccount(getHandle(context));
                    return account != null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error checking PhoneAccount registration: " + e.getMessage());
            }
            return false;
        }

        static boolean isPhoneAccountEnabled(Context context) {
            try {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) {
                    PhoneAccount account = tm.getPhoneAccount(getHandle(context));
                    return account != null && account.isEnabled();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error checking PhoneAccount enabled state: " + e.getMessage());
            }
            return false;
        }

        static boolean openStandardCallingAccountsSettings(Context context) {
            try {
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.i(TAG, "Launched standard ACTION_CHANGE_PHONE_ACCOUNTS");
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Failed standard ACTION_CHANGE_PHONE_ACCOUNTS: " + e.getMessage());
                return false;
            }
        }

        static boolean openRealmeCallingAccountsSettings(Context context) {
            try {
                Intent intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                intent.setComponent(new ComponentName("com.android.server.telecom",
                        "com.android.server.telecom.settings.EnableAccountPreferenceActivity"));
                context.startActivity(intent);
                Log.i(TAG, "Launched Realme EnableAccountPreferenceActivity");
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Failed Realme EnableAccountPreferenceActivity: " + e.getMessage());
                return false;
            }
        }

        static boolean openCallingAccountsSettings(Context context) {
            // 1. Try standard AOSP first
            boolean opened = openStandardCallingAccountsSettings(context);
            if (!opened) {
                // 2. If standard fails or is missing, fall back to Realme/ColorOS hidden activity
                opened = openRealmeCallingAccountsSettings(context);
            }
            if (!opened) {
                try {
                    // Fallback to stock phone call settings
                    Intent fallback = new Intent(Intent.ACTION_MAIN);
                    fallback.setClassName("com.android.phone", "com.android.phone.CallFeaturesSetting");
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(fallback);
                    return true;
                } catch (Exception ignored) {}
            }
            return opened;
        }

        static boolean addNewIncomingCall(Context context, String remoteNumber, String remoteName) {
            try {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm == null) return false;

                PhoneAccountHandle handle = getHandle(context);
                Bundle extras = new Bundle();
                Uri callerUri = Uri.fromParts("tel", remoteNumber != null ? remoteNumber : "", null);
                extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, callerUri);
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
                if (remoteName != null) {
                    extras.putString("REMOTE_NAME", remoteName);
                }

                tm.addNewIncomingCall(handle, extras);
                Log.i(TAG, "Successfully injected incoming call into TelecomManager: " + remoteNumber);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to inject incoming call into TelecomManager: " + e.getMessage(), e);
                return false;
            }
        }

        static boolean placeCall(Context context, String number) {
            try {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm == null) return false;

                PhoneAccountHandle handle = getHandle(context);
                Bundle extras = new Bundle();
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
                Uri uri = Uri.fromParts("tel", number != null ? number : "", null);
                tm.placeCall(uri, extras);
                Log.i(TAG, "Successfully placed call via TelecomManager to: " + number);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to placeCall via TelecomManager: " + e.getMessage(), e);
                return false;
            }
        }
    }
}
