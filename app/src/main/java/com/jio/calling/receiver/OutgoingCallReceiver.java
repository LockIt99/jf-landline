package com.jio.calling.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jio.calling.service.JioSipService;
import com.jio.calling.util.AppPreferences;

/**
 * Intercepts numbers dialed from the phone's stock native dialer.
 * Aborts cellular call and redirects through JioFiber landline if enabled.
 * Toggled via AppPreferences.
 */
public class OutgoingCallReceiver extends BroadcastReceiver {
    private static final String TAG = "OutgoingCallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
            return;
        }

        // Check if interception is toggled on in settings
        if (!AppPreferences.isCallInterceptionEnabled(context)) {
            Log.i(TAG, "Call interception is disabled in settings. Permitting normal cellular call.");
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

        Log.i(TAG, "Intercepted outgoing call from stock dialer to: " + dialedNumber);

        // Abort the stock cellular call
        setResultData(null);

        // Send command to JioSipService to initiate the call via Jio landline
        Intent serviceIntent = new Intent(context, JioSipService.class);
        serviceIntent.setAction(JioSipService.ACTION_MAKE_CALL);
        serviceIntent.putExtra(JioSipService.EXTRA_PHONE_NUMBER, dialedNumber);
        context.startService(serviceIntent);
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
