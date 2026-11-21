package com.jio.calling.model;

import java.io.Serializable;

/**
 * Represents a provisioned JioFiber SIP Account.
 */
public class JioAccount implements Serializable {
    private String msisdn;              // Fixed voice number (e.g. 022XXXXXXXX)
    private String imsi;
    private String username;            // e.g. 91XXXXXXXXXX@wb.wln.ims.jio.com
    private String password;            // Digest authentication password
    private String realm;               // wb.wln.ims.jio.com
    private String homeDomain;          // wb.wln.ims.jio.com
    private String proxyHost;           // Router IP or jiofiber.local.html
    private int proxyPort = 5068;       // Default Jio SIP TLS port
    private String virtualMac;          // 00:00:xx:xx:xx:xx
    private String instanceUuid;        // 00000000-0000-1000-8000-XXXXXXXXXXXX
    private long expiresAtTimestamp;    // Unix timestamp when credentials expire

    public JioAccount() {}

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRealm() { return realm; }
    public void setRealm(String realm) { this.realm = realm; }

    public String getHomeDomain() { return homeDomain; }
    public void setHomeDomain(String homeDomain) { this.homeDomain = homeDomain; }

    public String getProxyHost() {
        if (proxyHost == null || proxyHost.isEmpty() || "jiofiber.local.html".equalsIgnoreCase(proxyHost)) {
            return "192.168.29.1";
        }
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }

    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }

    public String getVirtualMac() { return virtualMac; }
    public void setVirtualMac(String virtualMac) { this.virtualMac = virtualMac; }

    public String getInstanceUuid() { return instanceUuid; }
    public void setInstanceUuid(String instanceUuid) { this.instanceUuid = instanceUuid; }

    public long getExpiresAtTimestamp() { return expiresAtTimestamp; }
    public void setExpiresAtTimestamp(long expiresAtTimestamp) { this.expiresAtTimestamp = expiresAtTimestamp; }

    public String getRegistrarHost() {
        return getProxyHost();
    }

    public String getProxyUri() {
        return "sip:" + getProxyHost() + ":" + proxyPort + ";transport=tls";
    }

    public String getRegistrarUri() {
        String host = homeDomain != null && !homeDomain.isEmpty() ? homeDomain : realm;
        return "sip:" + host + ";transport=tls";
    }

    public String getIdUri() {
        String user = username != null ? username : "";
        if (!user.startsWith("+") && !user.isEmpty()) {
            user = "+" + user;
        }
        return "sip:" + user;
    }

    public String getAuthUser() {
        if (username == null) return "";
        return username.startsWith("+") ? username.substring(1) : username;
    }

    public boolean isValid() {
        return username != null && !username.isEmpty()
                && password != null && !password.isEmpty()
                && proxyHost != null && !proxyHost.isEmpty();
    }

    @Override
    public String toString() {
        return "JioAccount{" +
                "msisdn='" + msisdn + '\'' +
                ", username='" + username + '\'' +
                ", realm='" + realm + '\'' +
                ", proxyHost='" + proxyHost + '\'' +
                ", proxyPort=" + proxyPort +
                ", virtualMac='" + virtualMac + '\'' +
                '}';
    }
}

