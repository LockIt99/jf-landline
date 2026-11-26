package com.jio.calling.telecom;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.jio.calling.receiver.OutgoingCallReceiver;
import com.jio.calling.util.AppPreferences;

/**
 * Modern stock dialer call redirection service for Android 10+ (API 29+).
 * When granted the ROLE_CALL_REDIRECTION role, Telecom routes outgoing call
 * placements through onPlaceCall. Calling redirectCall() delegates the call
 * to JioConnectionService, keeping the system dialer's native in-call UI active.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class JioCallRedirectionService extends CallRedirectionService {
    private static final String TAG = "JioCallRedirectService";

    @Override
    public void onPlaceCall(@NonNull Uri handle, @NonNull PhoneAccountHandle initialPhoneAccount, boolean allowInteractiveResponse) {
        // 1. Check if outgoing call interception is enabled in app preferences
        if (!AppPreferences.isCallInterceptionEnabled(this)) {
            Log.d(TAG, "Call interception disabled. Permitting unmodified cellular call.");
            placeCallUnmodified();
            return;
        }

        String schemeSpecificPart = handle.getSchemeSpecificPart();
        if (schemeSpecificPart == null || schemeSpecificPart.trim().isEmpty()) {
            placeCallUnmodified();
            return;
        }

        // Clean phone number (strip query parameters if any)
        String dialedNumber = schemeSpecificPart;
        int queryIndex = dialedNumber.indexOf('?');
        if (queryIndex != -1) {
            dialedNumber = dialedNumber.substring(0, queryIndex);
        }

        // 2. Safety: Do NOT intercept emergency numbers (100, 101, 102, 108, 112, 911)
        if (AppPreferences.isEmergencyExclusionEnabled(this) && OutgoingCallReceiver.isEmergencyNumber(dialedNumber)) {
            Log.i(TAG, "Emergency number detected (" + dialedNumber + "). Permitting normal cellular call.");
            placeCallUnmodified();
            return;
        }

        // 3. Avoid loop: If the call is already being placed through JioConnectionService
        if (initialPhoneAccount != null && initialPhoneAccount.getComponentName() != null &&
                initialPhoneAccount.getComponentName().getClassName().contains("JioConnectionService")) {
            Log.i(TAG, "Call is already bound to JioConnectionService PhoneAccount; permitting pass-through.");
            placeCallUnmodified();
            return;
        }

        // 4. Ensure Jio PhoneAccount is registered with Telecom
        if (!JioTelecomManager.isPhoneAccountRegistered(this)) {
            Log.i(TAG, "Jio PhoneAccount not registered, registering now...");
            JioTelecomManager.registerPhoneAccount(this);
        }

        PhoneAccountHandle jioPhoneAccount = JioTelecomManager.getPhoneAccountHandle(this);
        if (jioPhoneAccount == null) {
            Log.w(TAG, "Jio PhoneAccount handle is null. Permitting unmodified cellular call.");
            placeCallUnmodified();
            return;
        }

        Log.i(TAG, "Redirecting call from stock dialer to Jio Landline PhoneAccount: " + dialedNumber);

        // 5. Redirect the call to Jio Landline PhoneAccount.
        // This instructs Telecom to route the call via JioConnectionService while keeping
        // the system dialer's in-call UI (Google Phone / InCallUI / ODialer) active.
        redirectCall(handle, jioPhoneAccount, false);
    }
}
