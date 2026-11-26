package com.jio.calling.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.jio.calling.service.JioSipService;

/**
 * Starts the JioSipService when the device powers on.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i(TAG, "Device boot completed. Auto-starting JioSipService...");
            Intent serviceIntent = new Intent(context, JioSipService.class);
            serviceIntent.setAction(JioSipService.ACTION_START);
            ContextCompat.startForegroundService(context, serviceIntent);
        }
    }
}

