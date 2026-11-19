package com.jio.calling.provisioning;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Handles device identity generation.
 * Generates the virtual MAC address and instance UUID using the exact
 * hashing algorithm used by PJSIP and Jio router.
 */
public final class JioDeviceIdentity {

    private JioDeviceIdentity() {}

    /**
     * Calculates the PJSIP 32-bit string hash:
     * hash = (hash * 33) + byte
     */
    public static int computeDeviceHash(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return 0;
        }
        int hash = 0;
        byte[] bytes = hostname.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash = (hash * 33) + (b & 0xFF);
        }
        return hash;
    }

    /**
     * Formats the virtual MAC address known by the Jio router:
     * Two zero bytes followed by the 4-byte hash in little-endian order.
     * Example: 00:00:a1:b2:c3:d4
     */
    public static String computeDeviceMac(String hostname) {
        int hash = computeDeviceHash(hostname);
        int b0 = hash & 0xFF;
        int b1 = (hash >> 8) & 0xFF;
        int b2 = (hash >> 16) & 0xFF;
        int b3 = (hash >> 24) & 0xFF;

        return String.format(Locale.US, "00:00:%02x:%02x:%02x:%02x", b0, b1, b2, b3);
    }

    /**
     * Formats the +sip.instance UUID matching the virtual MAC:
     * Format: 00000000-0000-1000-8000-0000AABBCCDD
     * where AABBCCDD is the uppercase hex representation of the little-endian hash bytes.
     */
    public static String computeInstanceUuid(String hostname) {
        int hash = computeDeviceHash(hostname);
        int b0 = hash & 0xFF;
        int b1 = (hash >> 8) & 0xFF;
        int b2 = (hash >> 16) & 0xFF;
        int b3 = (hash >> 24) & 0xFF;

        return String.format(Locale.US, "00000000-0000-1000-8000-0000%02X%02X%02X%02X", b0, b1, b2, b3);
    }
}

