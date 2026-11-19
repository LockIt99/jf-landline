package com.jio.calling.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jio.calling.service.JioSipService;
import com.jio.calling.sip.JioSipManager;

/**
 * Intercepts numbers dialed from the phone's stock native dialer in Android KitKat.
 * Aborts the cellular phone call and redirects the call through the JioFiber landline.
 */
public class OutgoingCallReceiver extends BroadcastReceiver {
    private static final String TAG = "OutgoingCallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
            return;
        }

        String dialedNumber = getResultData();
        if (dialedNumber == null || dialedNumber.isEmpty()) {
            dialedNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
        }

        if (dialedNumber == null || dialedNumber.trim().isEmpty()) {
            return;
        }

        Log.i(TAG, "Intercepted outgoing call from stock dialer to: " + dialedNumber);

        // Abort the stock cellular call
        setResultData(null);

        // Send command to JioSipService to initiate the call
        Intent serviceIntent = new Intent(context, JioSipService.class);
        serviceIntent.setAction(JioSipService.ACTION_MAKE_CALL);
        serviceIntent.putExtra(JioSipService.EXTRA_PHONE_NUMBER, dialedNumber);
        context.startService(serviceIntent);
    }
}

