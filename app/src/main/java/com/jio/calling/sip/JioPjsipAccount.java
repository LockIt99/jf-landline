package com.jio.calling.sip;

import android.util.Log;

import org.pjsip.pjsua2.Account;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.OnRegStateParam;

/**
 * PJSIP Account implementation handling registration state updates and incoming SIP INVITEs.
 */
public class JioPjsipAccount extends Account {
    private static final String TAG = "JioPjsipAccount";
    private final JioSipManager sipManager;

    public JioPjsipAccount(JioSipManager manager) {
        super();
        this.sipManager = manager;
    }

    @Override
    public void onRegState(OnRegStateParam prm) {
        Log.i(TAG, "onRegState: code=" + prm.getCode() + ", reason=" + prm.getReason());
        sipManager.handleRegState(prm.getCode(), prm.getReason());
    }

    @Override
    public void onIncomingCall(OnIncomingCallParam prm) {
        Log.i(TAG, "onIncomingCall: callId=" + prm.getCallId());
        JioPjsipCall call = new JioPjsipCall(this, prm.getCallId(), sipManager);
        sipManager.handleIncomingCall(call);
    }
}

