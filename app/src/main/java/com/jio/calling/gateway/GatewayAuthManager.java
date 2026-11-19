package com.jio.calling.gateway;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages pairing PIN generation and persistent auth tokens for Companion apps.
 */
public class GatewayAuthManager {
    private static final String TAG = "GatewayAuthManager";
    private static final String PREFS_NAME = "jio_gateway_auth";
    private static final String KEY_PIN = "gateway_pin";
    private static final String KEY_TOKENS = "authorized_tokens";

    private static GatewayAuthManager instance;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    private GatewayAuthManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensurePinExists();
    }

    public static synchronized GatewayAuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new GatewayAuthManager(context);
        }
        return instance;
    }

    private void ensurePinExists() {
        if (!prefs.contains(KEY_PIN)) {
            regeneratePin();
        }
    }

    /**
     * Gets the current 4-digit pairing PIN.
     */
    public synchronized String getPin() {
        String pin = prefs.getString(KEY_PIN, null);
        if (pin == null || pin.isEmpty()) {
            pin = regeneratePin();
        }
        return pin;
    }

    /**
     * Generates and stores a new 4-digit numeric PIN.
     */
    public synchronized String regeneratePin() {
        int pinNum = 1000 + random.nextInt(9000); // 1000 to 9999
        String newPin = String.valueOf(pinNum);
        prefs.edit().putString(KEY_PIN, newPin).apply();
        Log.i(TAG, "Generated new Gateway pairing PIN: " + newPin);
        return newPin;
    }

    /**
     * Validates a candidate PIN against the stored PIN.
     */
    public synchronized boolean validatePin(String candidatePin) {
        if (candidatePin == null) return false;
        String currentPin = getPin();
        return currentPin.trim().equals(candidatePin.trim());
    }

    /**
     * Validates candidate PIN and if correct, issues a persistent auth token for the companion client.
     */
    public synchronized String pairClient(String candidatePin, String clientName) {
        if (!validatePin(candidatePin)) {
            Log.w(TAG, "Pairing rejected: invalid PIN attempted by " + clientName);
            return null;
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        Set<String> tokens = new HashSet<>(prefs.getStringSet(KEY_TOKENS, new HashSet<>()));
        tokens.add(token);
        prefs.edit().putStringSet(KEY_TOKENS, tokens).apply();

        Log.i(TAG, "Client paired successfully: " + clientName + ", total paired tokens: " + tokens.size());
        return token;
    }

    /**
     * Validates an authorization token sent in HTTP requests.
     */
    public synchronized boolean validateToken(String token) {
        if (token == null || token.isEmpty()) return false;
        Set<String> tokens = prefs.getStringSet(KEY_TOKENS, null);
        return tokens != null && tokens.contains(token.trim());
    }

    /**
     * Returns the count of active paired tokens.
     */
    public synchronized int getPairedCount() {
        Set<String> tokens = prefs.getStringSet(KEY_TOKENS, null);
        return tokens != null ? tokens.size() : 0;
    }

    /**
     * Revokes all paired companion device tokens.
     */
    public synchronized void revokeAllTokens() {
        prefs.edit().remove(KEY_TOKENS).apply();
        Log.i(TAG, "Revoked all companion tokens");
    }
}
