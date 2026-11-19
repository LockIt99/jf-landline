package com.jio.calling.sip;

/**
 * Call state callback events.
 */
public interface JioCallListener {
    void onCallIncoming(String remoteNumber, String remoteDisplayName);
    void onCallConnecting();
    void onCallRinging();
    void onCallEstablished();
    void onCallTerminated(String reason);
}

