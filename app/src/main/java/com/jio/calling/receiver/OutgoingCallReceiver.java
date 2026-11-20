package com.jio.calling.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.jio.calling.service.JioSipService;
import com.jio.calling.util.AppPreferences;

/**
 * Intercepts numbers dialed from the phone's stock native dialer.
 * Aborts cellular call and redirects through JioFiber landline when enabled.
 * - On Android < 6.0: Enabled by default (Telecom Calling Accounts not available).
 * - On Android >= 6.0: Disabled by default (Native Telecom Calling Accounts used instead).
 */
public class OutgoingCallReceiver extends BroadcastReceiver {
    private static final String TAG = "OutgoingCallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
            return;
        }

        // Check if outgoing interception is enabled
        if (!AppPreferences.isCallInterceptionEnabled(context)) {
            Log.d(TAG, "Outgoing call interception is disabled. Skipping.");
            return;
        }

        String dialedNumber = getResultData();
        if (dialedNumber == null || dialedNumber.isEmpty()) {
            dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
        }

        if (dialedNumber == null || dialedNumber.trim().isEmpty()) {
            return;
        }

        // Safety: Do NOT intercept emergency numbers (100, 101, 102, 108, 112, 911)
        if (AppPreferences.isEmergencyExclusionEnabled(context) && isEmergencyNumber(dialedNumber)) {
            Log.i(TAG, "Emergency number detected (" + dialedNumber + "). Permitting normal cellular call.");
            return;
        }

        // On Android 6.0+, if this call is already routed through Jio's ConnectionService, do not intercept
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PhoneAccountHandle handle = intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
            if (handle != null && handle.getComponentName() != null &&
                    handle.getComponentName().getClassName().contains("JioConnectionService")) {
                Log.i(TAG, "Call is bound to JioConnectionService PhoneAccount; permitting Telecom pass-through.");
                return;
            }
        }

        Log.i(TAG, "Intercepting outgoing call from stock dialer to: " + dialedNumber + " -> Routing via Jio Landline");

        // Abort the stock cellular call
        setResultData(null);

        // Send command to JioSipService to initiate the call via Jio landline
        Intent serviceIntent = new Intent(context, JioSipService.class);
        serviceIntent.setAction(JioSipService.ACTION_MAKE_CALL);
        serviceIntent.putExtra(JioSipService.EXTRA_PHONE_NUMBER, dialedNumber);
        ContextCompat.startForegroundService(context, serviceIntent);
    }

    /**
     * Checks if dialed number is an Indian or universal emergency number.
     */
    public static boolean isEmergencyNumber(String number) {
        if (number == null) return false;
        String clean = number.replaceAll("[^0-9]", "");
        return clean.equals("100") || clean.equals("101") || clean.equals("102") ||
               clean.equals("108") || clean.equals("112") || clean.equals("911");
    }
}
