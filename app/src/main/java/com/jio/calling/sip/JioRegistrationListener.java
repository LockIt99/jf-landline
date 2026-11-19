package com.jio.calling.sip;

/**
 * SIP registration state events.
 */
public interface JioRegistrationListener {
    void onRegistrationProgress();
    void onRegistered();
    void onRegistrationFailed(int statusCode, String reason);
    void onUnregistered();
}

