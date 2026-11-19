package com.jio.calling.provisioning;

import android.content.Context;
import android.content.SharedPreferences;

import com.jio.calling.model.JioAccount;

/**
 * Persists and retrieves the active JioAccount from Android SharedPreferences.
 */
public class JioAccountStorage {
    private static final String PREF_NAME = "jio_sip_prefs";
    private static final String KEY_MSISDN = "msisdn";
    private static final String KEY_IMSI = "imsi";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_REALM = "realm";
    private static final String KEY_HOME_DOMAIN = "home_domain";
    private static final String KEY_PROXY_HOST = "proxy_host";
    private static final String KEY_PROXY_PORT = "proxy_port";
    private static final String KEY_VIRTUAL_MAC = "virtual_mac";
    private static final String KEY_INSTANCE_UUID = "instance_uuid";
    private static final String KEY_EXPIRES_AT = "expires_at";

    public static void save(Context context, JioAccount account) {
        if (context == null || account == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_MSISDN, account.getMsisdn())
            .putString(KEY_IMSI, account.getImsi())
            .putString(KEY_USERNAME, account.getUsername())
            .putString(KEY_PASSWORD, account.getPassword())
            .putString(KEY_REALM, account.getRealm())
            .putString(KEY_HOME_DOMAIN, account.getHomeDomain())
            .putString(KEY_PROXY_HOST, account.getProxyHost())
            .putInt(KEY_PROXY_PORT, account.getProxyPort())
            .putString(KEY_VIRTUAL_MAC, account.getVirtualMac())
            .putString(KEY_INSTANCE_UUID, account.getInstanceUuid())
            .putLong(KEY_EXPIRES_AT, account.getExpiresAtTimestamp())
            .apply();
    }

    public static JioAccount load(Context context) {
        if (context == null) return null;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_USERNAME) || !prefs.contains(KEY_PASSWORD)) {
            return null;
        }

        JioAccount account = new JioAccount();
        account.setMsisdn(prefs.getString(KEY_MSISDN, ""));
        account.setImsi(prefs.getString(KEY_IMSI, ""));
        account.setUsername(prefs.getString(KEY_USERNAME, ""));
        account.setPassword(prefs.getString(KEY_PASSWORD, ""));
        account.setRealm(prefs.getString(KEY_REALM, ""));
        account.setHomeDomain(prefs.getString(KEY_HOME_DOMAIN, ""));
        account.setProxyHost(prefs.getString(KEY_PROXY_HOST, ""));
        account.setProxyPort(prefs.getInt(KEY_PROXY_PORT, 5068));
        account.setVirtualMac(prefs.getString(KEY_VIRTUAL_MAC, ""));
        account.setInstanceUuid(prefs.getString(KEY_INSTANCE_UUID, ""));
        account.setExpiresAtTimestamp(prefs.getLong(KEY_EXPIRES_AT, 0));

        return account.isValid() ? account : null;
    }

    public static void clear(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}

