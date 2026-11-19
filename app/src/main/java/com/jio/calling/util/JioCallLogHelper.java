package com.jio.calling.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog;
import android.util.Log;

/**
 * Helper to synchronize Jio VoIP calls into Android's native system CallLog database.
 * Compatible with Android 4.4 KitKat (API 19).
 */
public class JioCallLogHelper {
    private static final String TAG = "JioCallLogHelper";

    /**
     * Normalizes a phone number for display and native CallLog storage.
     * Converts national trunk mobile numbers (e.g., 07044756295) into clean 10-digit format (7044756295),
     * while preserving international formats (+91...) and standard landlines.
     */
    public static String normalizeNumber(String number) {
        if (number == null) return "";
        String clean = number.trim();
        if (clean.isEmpty()) return "";

        // Strip SIP or TEL URI wrappers if present
        if (clean.startsWith("sip:") || clean.startsWith("tel:")) {
            int colon = clean.indexOf(':');
            int at = clean.indexOf('@');
            if (at > colon) {
                clean = clean.substring(colon + 1, at);
            } else {
                clean = clean.substring(colon + 1);
            }
        }

        // Clean punctuation like spaces, dashes, brackets
        String digits = clean.replaceAll("[^0-9+*#]", "");

        // 11-digit Indian mobile number with leading trunk 0 (e.g. 07044756295 -> 7044756295)
        if (digits.length() == 11 && digits.matches("^0[6-9][0-9]{9}$")) {
            return digits.substring(1);
        }

        return digits.isEmpty() ? clean : digits;
    }

    /**
     * Inserts a call entry into android.provider.CallLog.Calls asynchronously.
     *
     * @param context Application or activity context
     * @param number Dialed or received phone number
     * @param callType CallLog.Calls.OUTGOING_TYPE, INCOMING_TYPE, or MISSED_TYPE
     * @param startMillis Timestamp in epoch milliseconds when call began (or 0 for now)
     * @param durationSeconds Total call duration in seconds (0 for missed/unanswered)
     */
    public static void logCall(final Context context, final String number, final int callType, final long startMillis, final long durationSeconds) {
        if (context == null || number == null || number.trim().isEmpty()) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        final String cleanNumber = normalizeNumber(number);

        new Thread(() -> {
            try {
                ContentResolver resolver = appContext.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(CallLog.Calls.NUMBER, cleanNumber);
                values.put(CallLog.Calls.DATE, startMillis > 0 ? startMillis : System.currentTimeMillis());
                values.put(CallLog.Calls.DURATION, Math.max(0, durationSeconds));
                values.put(CallLog.Calls.TYPE, callType);
                values.put(CallLog.Calls.NEW, 1);

                resolver.insert(CallLog.Calls.CONTENT_URI, values);
                Log.i(TAG, "Synced call to native CallLog: num=" + cleanNumber + ", type=" + callType + ", dur=" + durationSeconds + "s");
            } catch (SecurityException se) {
                Log.e(TAG, "Permission denied writing to CallLog: " + se.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert into native CallLog: " + e.getMessage(), e);
            }
        }).start();
    }
}
