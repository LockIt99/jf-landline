package com.jio.calling.gateway;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.jio.calling.service.JioSipService;
import com.jio.calling.sip.JioSipManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight, zero-dependency HTTP server running on port 8088.
 * Provides REST endpoints for remote companion apps to query status,
 * pair with a PIN, and trigger calls through the Jio landline line.
 */
public class GatewayServer {
    private static final String TAG = "GatewayServer";
    public static final int GATEWAY_PORT = 8088;

    private final Context context;
    private final JioSipManager sipManager;
    private final GatewayAuthManager authManager;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean isRunning = false;
    private PowerManager.WakeLock wakeLock;

    public GatewayServer(Context context) {
        this.context = context.getApplicationContext();
        this.sipManager = JioSipManager.getInstance(this.context);
        this.authManager = GatewayAuthManager.getInstance(this.context);
        initWakeLock();
    }

    @SuppressWarnings("deprecation")
    private void initWakeLock() {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                int flags = PowerManager.PARTIAL_WAKE_LOCK;
                try {
                    flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
                } catch (Exception ignored) {}
                wakeLock = pm.newWakeLock(flags, "jio:gateway_call_trigger");
                wakeLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to create wake lock: " + e.getMessage());
        }
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        threadPool = Executors.newCachedThreadPool();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(GATEWAY_PORT);
                Log.i(TAG, "GatewayServer listening on port " + GATEWAY_PORT);

                while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setSoTimeout(10000); // 10s socket timeout
                        if (threadPool != null && !threadPool.isShutdown()) {
                            threadPool.execute(() -> handleClient(clientSocket));
                        }
                    } catch (Exception e) {
                        if (isRunning) {
                            Log.w(TAG, "Socket accept error: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (isRunning) {
                    Log.e(TAG, "GatewayServer failed to start on port " + GATEWAY_PORT + ": " + e.getMessage(), e);
                }
            }
        }, "GatewayServer-Listener").start();
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception ignored) {}

        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
        Log.i(TAG, "GatewayServer stopped");
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendResponse(out, 400, "Bad Request", "{\"error\":\"Malformed HTTP request\"}");
                return;
            }

            String method = parts[0].toUpperCase();
            String path = parts[1];
            if (path.contains("?")) {
                path = path.substring(0, path.indexOf("?"));
            }

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            int contentLength = 0;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int colonIndex = headerLine.indexOf(':');
                if (colonIndex > 0) {
                    String key = headerLine.substring(0, colonIndex).trim().toLowerCase();
                    String value = headerLine.substring(colonIndex + 1).trim();
                    headers.put(key, value);
                    if ("content-length".equals(key)) {
                        try {
                            contentLength = Integer.parseInt(value);
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Read request body if present
            String body = "";
            if (contentLength > 0 && contentLength < 65536) {
                char[] charBuf = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = reader.read(charBuf, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                body = new String(charBuf, 0, totalRead);
            }

            // Handle CORS preflight
            if ("OPTIONS".equals(method)) {
                sendCorsOk(out);
                return;
            }

            // Route request
            routeRequest(method, path, headers, body, out);

        } catch (Exception e) {
            Log.w(TAG, "Error handling client request: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void routeRequest(String method, String path, Map<String, String> headers, String body, OutputStream out) throws Exception {
        if ("GET".equals(method) && "/api/status".equals(path)) {
            handleGetStatus(out);
        } else if ("POST".equals(method) && "/api/pair".equals(path)) {
            handlePostPair(body, out);
        } else if ("POST".equals(method) && "/api/call".equals(path)) {
            handlePostCall(headers, body, out);
        } else if ("POST".equals(method) && "/api/hangup".equals(path)) {
            handlePostHangup(headers, out);
        } else {
            sendResponse(out, 404, "Not Found", "{\"error\":\"Endpoint not found\"}");
        }
    }

    private void handleGetStatus(OutputStream out) throws Exception {
        JSONObject json = new JSONObject();
        boolean isRegistered = sipManager.isRegistered();
        boolean isCallActive = sipManager.isCallActive();
        String msisdn = (sipManager.getCurrentAccount() != null) ? sipManager.getCurrentAccount().getMsisdn() : "";
        String activeCallNumber = sipManager.getActiveCallNumber();

        json.put("status", isCallActive ? "IN_CALL" : "IDLE");
        json.put("registered", isRegistered);
        json.put("msisdn", msisdn != null ? msisdn : "");
        json.put("activeCallNumber", (isCallActive && activeCallNumber != null) ? activeCallNumber : JSONObject.NULL);
        json.put("batteryLevel", getBatteryLevel());
        json.put("isCharging", isBatteryCharging());
        json.put("wifiSsid", getWifiSsid());
        json.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
        json.put("port", GATEWAY_PORT);
        json.put("pairedDevices", authManager.getPairedCount());
        json.put("timestamp", System.currentTimeMillis());

        sendResponse(out, 200, "OK", json.toString());
    }

    private void handlePostPair(String body, OutputStream out) throws Exception {
        if (body == null || body.trim().isEmpty()) {
            sendResponse(out, 400, "Bad Request", "{\"error\":\"Missing request body\"}");
            return;
        }

        JSONObject req = new JSONObject(body);
        String pin = req.optString("pin", "").trim();
        String clientName = req.optString("clientName", "Companion Device").trim();

        if (pin.isEmpty()) {
            sendResponse(out, 400, "Bad Request", "{\"error\":\"PIN is required\"}");
            return;
        }

        String token = authManager.pairClient(pin, clientName);
        if (token == null) {
            sendResponse(out, 401, "Unauthorized", "{\"error\":\"Invalid PIN entered\"}");
            return;
        }

        JSONObject resp = new JSONObject();
        resp.put("status", "PAIRED");
        resp.put("token", token);
        resp.put("msisdn", (sipManager.getCurrentAccount() != null) ? sipManager.getCurrentAccount().getMsisdn() : "");
        resp.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);

        sendResponse(out, 200, "OK", resp.toString());
    }

    private void handlePostCall(Map<String, String> headers, String body, OutputStream out) throws Exception {
        if (!isAuthorized(headers)) {
            sendResponse(out, 401, "Unauthorized", "{\"error\":\"Invalid or missing Authorization Bearer token\"}");
            return;
        }

        if (body == null || body.trim().isEmpty()) {
            sendResponse(out, 400, "Bad Request", "{\"error\":\"Missing request body\"}");
            return;
        }

        JSONObject req = new JSONObject(body);
        String number = req.optString("number", "").trim();
        if (number.isEmpty()) {
            sendResponse(out, 400, "Bad Request", "{\"error\":\"Target phone number is required\"}");
            return;
        }

        if (!sipManager.isRegistered()) {
            sendResponse(out, 503, "Service Unavailable", "{\"error\":\"Jio SIP line is offline or unprovisioned\"}");
            return;
        }

        Log.i(TAG, "Remotely triggering outbound call to: " + number);

        // Wake screen and CPU temporarily
        acquireWakeLock(5000);

        // Dispatch call action to JioSipService
        Intent callIntent = new Intent(context, JioSipService.class);
        callIntent.setAction(JioSipService.ACTION_MAKE_CALL);
        callIntent.putExtra(JioSipService.EXTRA_PHONE_NUMBER, number);
        context.startService(callIntent);

        JSONObject resp = new JSONObject();
        resp.put("status", "DIALING");
        resp.put("number", number);
        resp.put("message", "Call initiated on Device A");

        sendResponse(out, 200, "OK", resp.toString());
    }

    private void handlePostHangup(Map<String, String> headers, OutputStream out) throws Exception {
        if (!isAuthorized(headers)) {
            sendResponse(out, 401, "Unauthorized", "{\"error\":\"Invalid or missing Authorization Bearer token\"}");
            return;
        }

        Log.i(TAG, "Remotely triggering call hangup");
        sipManager.hangupCall();

        JSONObject resp = new JSONObject();
        resp.put("status", "DISCONNECTED");

        sendResponse(out, 200, "OK", resp.toString());
    }

    private boolean isAuthorized(Map<String, String> headers) {
        String auth = headers.get("authorization");
        if (auth == null || !auth.toLowerCase().startsWith("bearer ")) {
            return false;
        }
        String token = auth.substring(7).trim();
        return authManager.validateToken(token);
    }

    private void acquireWakeLock(long timeoutMs) {
        try {
            if (wakeLock != null) {
                wakeLock.acquire(timeoutMs);
                Log.i(TAG, "Screen and CPU woken up for remote call trigger");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire wake lock: " + e.getMessage());
        }
    }

    private int getBatteryLevel() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent bStatus = context.registerReceiver(null, ifilter);
            if (bStatus != null) {
                int level = bStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private boolean isBatteryCharging() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent bStatus = context.registerReceiver(null, ifilter);
            if (bStatus != null) {
                int status = bStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String getWifiSsid() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getSSID() != null) {
                    return info.getSSID().replace("\"", "");
                }
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private void sendCorsOk(OutputStream out) throws Exception {
        String response = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Authorization, Content-Type\r\n" +
                "Connection: close\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void sendResponse(OutputStream out, int statusCode, String statusText, String jsonBody) throws Exception {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Authorization, Content-Type\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }
}
