package com.jio.calling.telecom;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import com.jio.calling.sip.JioSipManager;

/**
 * Android Telecom ConnectionService for Android 6.0+ (API 23+).
 * Binds with Android OS Telecom system to route outgoing/incoming VoIP calls through Jio Landline.
 */
@TargetApi(Build.VERSION_CODES.M)
public class JioConnectionService extends ConnectionService {
    private static final String TAG = "JioConnectionService";

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.i(TAG, "onCreateOutgoingConnection requested: " + (request != null ? request.getAddress() : "null"));

        String number = "";
        if (request != null && request.getAddress() != null) {
            number = request.getAddress().getSchemeSpecificPart();
        }

        JioConnection connection = new JioConnection(this, number, false);
        connection.setInitializing();
        connection.setDialing();

        // Initiate SIP call via JioSipManager
        JioSipManager sipManager = JioSipManager.getInstance(this);
        sipManager.makeCall(number);

        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.i(TAG, "onCreateIncomingConnection requested");

        String number = "Unknown";
        String name = "Jio Landline";

        if (request != null && request.getExtras() != null) {
            Bundle extras = request.getExtras();
            Uri address = extras.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
            if (address != null) {
                number = address.getSchemeSpecificPart();
            }
            if (extras.containsKey("REMOTE_NAME")) {
                name = extras.getString("REMOTE_NAME");
            }
        }

        JioConnection connection = new JioConnection(this, number, true);
        connection.setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED);
        connection.setAddress(Uri.fromParts("tel", number, null), TelecomManager.PRESENTATION_ALLOWED);
        connection.setRinging();

        return connection;
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.w(TAG, "onCreateIncomingConnectionFailed");
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.w(TAG, "onCreateOutgoingConnectionFailed");
    }
}
