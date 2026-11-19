package com.jio.calling.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Information returned by http://<router>:8080/request_account.
 */
public class RouterInfo implements Serializable {
    private String host;
    private String msisdn;
    private String imsi;
    private String mode;
    private String serialNumber;
    private String macAddress;
    private String extnMacAddress;
    private boolean voiceMailSupport;
    private boolean centralizedCallWaiting;
    private boolean centralizedCallBlocking;

    public RouterInfo() {}

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public String getExtnMacAddress() { return extnMacAddress; }
    public void setExtnMacAddress(String extnMacAddress) { this.extnMacAddress = extnMacAddress; }

    public boolean isVoiceMailSupport() { return voiceMailSupport; }
    public void setVoiceMailSupport(boolean voiceMailSupport) { this.voiceMailSupport = voiceMailSupport; }

    public boolean isCentralizedCallWaiting() { return centralizedCallWaiting; }
    public void setCentralizedCallWaiting(boolean centralizedCallWaiting) { this.centralizedCallWaiting = centralizedCallWaiting; }

    public boolean isCentralizedCallBlocking() { return centralizedCallBlocking; }
    public void setCentralizedCallBlocking(boolean centralizedCallBlocking) { this.centralizedCallBlocking = centralizedCallBlocking; }

    public boolean hasVoiceLine() {
        return msisdn != null && !msisdn.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "RouterInfo{" +
                "host='" + host + '\'' +
                ", msisdn='" + msisdn + '\'' +
                ", mode='" + mode + '\'' +
                ", serialNumber='" + serialNumber + '\'' +
                '}';
    }
}

