package com.jio.calling.provisioning;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.jio.calling.model.RouterInfo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers the JioFiber router on the local network and checks its VoIP line status.
 */
public class JioRouterDiscovery {
    private static final String TAG = "JioRouterDiscovery";

    public static final String DEFAULT_ROUTER_HOST = "192.168.29.1";
    public static final String[] FALLBACK_IPS = {"192.168.29.1", "jiofiber.local.html", "192.168.31.1", "10.0.2.2"};
    public static final int ACCOUNT_PORT = 8080;
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int READ_TIMEOUT_MS = 5000;

    /**
     * Resolves a list of candidate router IPs/hostnames based on current Wi-Fi configuration.
     */
    public static List<String> getCandidateHosts(Context context) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(DEFAULT_ROUTER_HOST);

        // Retrieve Wi-Fi default gateway
        if (context != null) {
            try {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
                    if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                        int g = dhcpInfo.gateway;
                        String gatewayIp = String.format("%d.%d.%d.%d",
                                (g & 0xFF), (g >> 8 & 0xFF), (g >> 16 & 0xFF), (g >> 24 & 0xFF));
                        candidates.add(gatewayIp);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get default Wi-Fi gateway: " + e.getMessage());
            }
        }

        // Add standard fallback IPs
        for (String ip : FALLBACK_IPS) {
            candidates.add(ip);
        }

        return new ArrayList<>(candidates);
    }

    /**
     * Probes candidate hosts until a responsive Jio router with an active voice line is found.
     * Note: Must be called from a background thread.
     */
    public static RouterInfo discover(Context context) {
        List<String> candidates = getCandidateHosts(context);
        for (String host : candidates) {
            Log.d(TAG, "Probing candidate: " + host);
            RouterInfo info = probeHost(host);
            if (info != null && info.hasVoiceLine()) {
                Log.i(TAG, "Found active Jio router at " + host + " (MSISDN: " + info.getMsisdn() + ")");
                return info;
            }
        }
        return null;
    }

    /**
     * Queries http://<host>:8080/request_account.
     */
    public static RouterInfo probeHost(String host) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + host + ":" + ACCOUNT_PORT + "/request_account");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                return parseRouterAccount(host, sb.toString());
            }
        } catch (Exception e) {
            Log.d(TAG, "Host " + host + " not reachable: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    /**
     * Parses the JSON output of request_account.
     */
    public static RouterInfo parseRouterAccount(String host, String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            RouterInfo info = new RouterInfo();
            info.setHost(host);
            info.setMsisdn(json.optString("msisdn", ""));
            info.setImsi(json.optString("imsi", ""));
            info.setMode(json.optString("mode", ""));
            info.setSerialNumber(json.optString("SerialNumber", ""));
            info.setMacAddress(json.optString("mac_address", ""));
            info.setExtnMacAddress(json.optString("extn_mac_address", ""));

            info.setVoiceMailSupport("true".equalsIgnoreCase(json.optString("VoiceMailSupport")));
            info.setCentralizedCallWaiting("true".equalsIgnoreCase(json.optString("CentralizedCallWaiting")));
            info.setCentralizedCallBlocking("true".equalsIgnoreCase(json.optString("CentralizedCallBlocking")));

            return info;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing request_account JSON: " + e.getMessage());
            return null;
        }
    }
}

