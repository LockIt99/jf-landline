package com.jio.calling.provisioning;

import android.os.Build;
import android.util.Log;

import com.jio.calling.model.JioAccount;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Handles communication with the router's credentials service on HTTPS port 8443.
 */
public class JioProvisioningClient {
    private static final String TAG = "JioProvisioningClient";
    public static final int PROVISIONING_PORT = 8443;
    public static final int TIMEOUT_MS = 10000;

    /**
     * Session object holding the OTP state when using Permanent Wi-Fi mode.
     */
    public static class OtpSession {
        public final String host;
        public final String hostname;
        public final String cookie;
        public final String sentToNumber;

        public OtpSession(String host, String hostname, String cookie, String sentToNumber) {
            this.host = host;
            this.hostname = hostname;
            this.cookie = cookie;
            this.sentToNumber = sentToNumber;
        }
    }

    public enum Mode {
        TEMPORARY_ETH, // nwk_intf=eth: Zero-OTP bypass (Instant credentials)
        PERMANENT_WIFI // nwk_intf=wifi: Triggers SMS OTP verification
    }

    /**
     * Fetches SIP credentials using the zero-OTP Ethernet bypass.
     * Note: Must be called from a background thread.
     */
    public static JioAccount acquireCredentialsZeroOtp(String host) throws Exception {
        return acquireCredentialsZeroOtp(host, null);
    }

    public static JioAccount acquireCredentialsZeroOtp(String host, String preferredMac) throws Exception {
        String hostname = Build.MODEL != null && !Build.MODEL.isEmpty() ? Build.MODEL : "Android";
        return acquireCredentials(host, hostname, preferredMac, Mode.TEMPORARY_ETH);
    }

    /**
     * Fetches credentials from the router.
     */
    public static JioAccount acquireCredentials(String host, String hostname, Mode mode) throws Exception {
        return acquireCredentials(host, hostname, null, mode);
    }

    public static JioAccount acquireCredentials(String host, String hostname, String preferredMac, Mode mode) throws Exception {
        String targetMac = (preferredMac != null && !preferredMac.trim().isEmpty())
                ? preferredMac.trim().toLowerCase(java.util.Locale.US)
                : JioDeviceIdentity.computeDeviceMac(hostname);
        String defaultInstanceUuid = JioDeviceIdentity.computeInstanceUuid(hostname);

        String query = buildProvisioningQuery(hostname, targetMac, mode, false);
        URL url = new URL("https://" + host + ":" + PROVISIONING_PORT + "/?" + query);

        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        configureSsl(conn);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        if (code != HttpsURLConnection.HTTP_OK) {
            throw new Exception("Router provisioning failed with HTTP status: " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder xmlBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            xmlBuilder.append(line).append("\n");
        }
        reader.close();

        String xml = xmlBuilder.toString();
        JioAccount account = JioProvisioningParser.parse(xml, host);
        if (account != null) {
            account.setVirtualMac(targetMac);
            if (account.getInstanceUuid() == null || account.getInstanceUuid().trim().isEmpty()) {
                account.setInstanceUuid(defaultInstanceUuid);
            }
        }
        return account;
    }

    /**
     * Starts the Permanent Wi-Fi registration flow, which sends an SMS OTP.
     */
    public static OtpSession startRegistrationOtp(String host, String hostname) throws Exception {
        String virtualMac = JioDeviceIdentity.computeDeviceMac(hostname);
        String query = buildProvisioningQuery(hostname, virtualMac, Mode.PERMANENT_WIFI, true);
        URL url = new URL("https://" + host + ":" + PROVISIONING_PORT + "/?" + query);

        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        configureSsl(conn);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        if (code != HttpsURLConnection.HTTP_OK) {
            throw new Exception("Registration request failed with HTTP status: " + code);
        }

        String cookie = conn.getHeaderField("Set-Cookie");
        String sentTo = conn.getHeaderField("x-amn");

        return new OtpSession(host, hostname, cookie, sentTo);
    }

    /**
     * Submits the received OTP to verify the device and obtain credentials.
     */
    public static JioAccount submitOtp(OtpSession session, String otp) throws Exception {
        URL url = new URL("https://" + session.host + ":" + PROVISIONING_PORT + "/?OTP=" + otp.trim());
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        configureSsl(conn);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("GET");

        if (session.cookie != null && !session.cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", session.cookie);
        }

        int code = conn.getResponseCode();
        if (code != HttpsURLConnection.HTTP_OK) {
            throw new Exception("OTP verification failed with HTTP status: " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder xmlBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            xmlBuilder.append(line).append("\n");
        }
        reader.close();

        String virtualMac = JioDeviceIdentity.computeDeviceMac(session.hostname);
        String instanceUuid = JioDeviceIdentity.computeInstanceUuid(session.hostname);

        JioAccount account = JioProvisioningParser.parse(xmlBuilder.toString(), session.host);
        if (account != null) {
            account.setVirtualMac(virtualMac);
            account.setInstanceUuid(instanceUuid);
        }
        return account;
    }

    /**
     * Builds the exact URL query expected by the Jio Juice Server.
     */
    private static String buildProvisioningQuery(String hostname, String virtualMac, Mode mode, boolean register) throws UnsupportedEncodingException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("terminal_sw_version", "RCSAndrd");
        params.put("terminal_vendor", hostname);
        params.put("terminal_model", hostname);
        params.put("SMS_port", "0");
        params.put("act_type", "volatile");
        params.put("IMSI", "");
        params.put("msisdn", "");
        params.put("IMEI", "");
        params.put("vers", "0");
        params.put("token", "");
        params.put("rcs_state", "0");
        params.put("rcs_version", "5.1B");
        params.put("rcs_profile", "joyn_blackbird");
        params.put("client_vendor", "JUIC");
        params.put("default_sms_app", "2");
        params.put("default_vvm_app", "0");
        params.put("device_type", "vvm");
        params.put("client_version", "JSEAndrd-1.0");
        params.put("mac_address", virtualMac);
        params.put("alias", hostname);
        params.put("nwk_intf", mode == Mode.TEMPORARY_ETH ? "eth" : "wifi");

        if (register) {
            params.put("op_type", "add");
        }

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) {
                query.append("&");
            }
            query.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                 .append("=")
                 .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return query.toString();
    }

    /**
     * Configures HttpsURLConnection to accept the router's local self-signed certificate.
     */
    private static void configureSsl(HttpsURLConnection conn) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            conn.setSSLSocketFactory(new TLSSocketFactory(sc.getSocketFactory()));
            conn.setHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to configure trust-all SSL: " + e.getMessage());
        }
    }
}

