package com.jio.calling.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.jio.calling.R;
import com.jio.calling.gateway.GatewayAuthManager;
import com.jio.calling.model.JioAccount;
import com.jio.calling.provisioning.JioAccountStorage;
import com.jio.calling.service.JioSipService;
import com.jio.calling.sip.JioRegistrationListener;
import com.jio.calling.sip.JioSipManager;
import com.jio.calling.telecom.JioTelecomManager;
import com.jio.calling.util.AppPreferences;

/**
 * Main dashboard & configuration hub for Jio Landline.
 * Provides controls for:
 * - Real-time SIP registration & line status
 * - Stock dialer call interception toggle
 * - Android Telecom Calling Accounts integration & configuration
 * - Remote companion gateway pairing PIN & status
 * - Device and network diagnostics
 */
public class MainActivity extends AppCompatActivity implements JioRegistrationListener {
    private static final String TAG = "MainActivity";

    // Header Status Pill
    private LinearLayout llStatusPill;
    private ImageView ivStatusDot;
    private TextView tvStatusPill;

    // Line Status
    private TextView tvPhoneNumber;
    private Button btnRefresh;
    private TextView tvRouterHost;
    private TextView tvSipPort;
    private TextView tvRealmInfo;
    private TextView tvWifiSsid;
    private TextView tvStatusDesc;

    // Call Interception
    private SwitchCompat switchIntercept;
    private TextView tvInterceptStatus;

    // Android Telecom
    private LinearLayout llTelecomControls;
    private LinearLayout llTelecomKitkatInfo;
    private SwitchCompat switchTelecom;
    private SwitchCompat switchTelecomUi;
    private Button btnOpenTelecomSettings;
    private TextView tvTelecomStatus;

    // Companion Gateway
    private TextView tvGatewayInfo;
    private TextView tvGatewayPin;
    private Button btnNewPin;
    private Button btnCopyPin;

    // Diagnostics
    private TextView tvDiagIp;
    private TextView tvDiagCodec;
    private TextView tvDiagMac;
    private TextView tvDiagEngine;
    private TextView tvDiagAndroid;
    private TextView tvDiagKeepalive;

    private JioSipManager sipManager;
    private GatewayAuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Turn screen on and display smoothly on dedicated landline phone
        getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        initViews();
        setupManagers();
        setupListeners();

        // Start background 24/7 service
        Intent serviceIntent = new Intent(this, JioSipService.class);
        serviceIntent.setAction(JioSipService.ACTION_START);
        startService(serviceIntent);

        updateAccountDisplay();
    }

    private void initViews() {
        llStatusPill = findViewById(R.id.ll_status_pill);
        ivStatusDot = findViewById(R.id.iv_status_dot);
        tvStatusPill = findViewById(R.id.tv_status_pill);

        tvPhoneNumber = findViewById(R.id.tv_phone_number);
        btnRefresh = findViewById(R.id.btn_refresh);
        tvRouterHost = findViewById(R.id.tv_router_host);
        tvSipPort = findViewById(R.id.tv_sip_port);
        tvRealmInfo = findViewById(R.id.tv_realm_info);
        tvWifiSsid = findViewById(R.id.tv_wifi_ssid);
        tvStatusDesc = findViewById(R.id.tv_status_desc);

        switchIntercept = findViewById(R.id.switch_intercept);
        tvInterceptStatus = findViewById(R.id.tv_intercept_status);

        llTelecomControls = findViewById(R.id.ll_telecom_controls);
        llTelecomKitkatInfo = findViewById(R.id.ll_telecom_kitkat_info);
        switchTelecom = findViewById(R.id.switch_telecom);
        switchTelecomUi = findViewById(R.id.switch_telecom_ui);
        btnOpenTelecomSettings = findViewById(R.id.btn_open_telecom_settings);
        tvTelecomStatus = findViewById(R.id.tv_telecom_status);

        tvGatewayInfo = findViewById(R.id.tv_gateway_info);
        tvGatewayPin = findViewById(R.id.tv_gateway_pin);
        btnNewPin = findViewById(R.id.btn_new_pin);
        btnCopyPin = findViewById(R.id.btn_copy_pin);

        tvDiagIp = findViewById(R.id.tv_diag_ip);
        tvDiagCodec = findViewById(R.id.tv_diag_codec);
        tvDiagMac = findViewById(R.id.tv_diag_mac);
        tvDiagEngine = findViewById(R.id.tv_diag_engine);
        tvDiagAndroid = findViewById(R.id.tv_diag_android);
        tvDiagKeepalive = findViewById(R.id.tv_diag_keepalive);
    }

    private void setupManagers() {
        sipManager = JioSipManager.getInstance(this);
        sipManager.addRegistrationListener(this);
        authManager = GatewayAuthManager.getInstance(this);
    }

    private void setupListeners() {
        // --- 1. Stock Dialer Interception Switch ---
        boolean isInterceptEnabled = AppPreferences.isCallInterceptionEnabled(this);
        switchIntercept.setChecked(isInterceptEnabled);
        updateInterceptStatusText(isInterceptEnabled);

        switchIntercept.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPreferences.setCallInterceptionEnabled(this, isChecked);
            updateInterceptStatusText(isChecked);
            String msg = isChecked
                    ? "Stock dialer call interception ENABLED"
                    : "Call interception DISABLED (Normal cellular calls active)";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        // --- 2. Android Telecom Integration ---
        if (JioTelecomManager.isSupported()) {
            llTelecomControls.setVisibility(View.VISIBLE);
            llTelecomKitkatInfo.setVisibility(View.GONE);

            boolean isTelecomEnabled = AppPreferences.isTelecomEnabled(this);
            switchTelecom.setChecked(isTelecomEnabled);

            boolean isTelecomUi = AppPreferences.isUseTelecomUiEnabled(this);
            switchTelecomUi.setChecked(isTelecomUi);
            switchTelecomUi.setEnabled(isTelecomEnabled);

            switchTelecom.setOnCheckedChangeListener((buttonView, isChecked) -> {
                AppPreferences.setTelecomEnabled(this, isChecked);
                switchTelecomUi.setEnabled(isChecked);
                if (isChecked) {
                    JioTelecomManager.registerPhoneAccount(this);
                    Toast.makeText(this, "Registered with Android Telecom", Toast.LENGTH_SHORT).show();
                } else {
                    JioTelecomManager.unregisterPhoneAccount(this);
                    Toast.makeText(this, "Unregistered from Android Telecom", Toast.LENGTH_SHORT).show();
                }
                updateTelecomStatusText();
            });

            switchTelecomUi.setOnCheckedChangeListener((buttonView, isChecked) -> {
                AppPreferences.setUseTelecomUiEnabled(this, isChecked);
                String msg = isChecked
                        ? "Using Android System In-Call UI"
                        : "Using App Custom Call Screen";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            });

            btnOpenTelecomSettings.setOnClickListener(v -> {
                boolean opened = JioTelecomManager.openCallingAccountsSettings(this);
                if (!opened) {
                    Toast.makeText(this, "Could not open Calling Accounts settings directly", Toast.LENGTH_SHORT).show();
                }
            });

            updateTelecomStatusText();
        } else {
            // KitKat / Pre-API 23: Hide modern switches and show informative compatibility card
            llTelecomControls.setVisibility(View.GONE);
            llTelecomKitkatInfo.setVisibility(View.VISIBLE);
        }

        // --- 3. Refresh Credentials / Router Sync ---
        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(this, "Re-discovering router & credentials...", Toast.LENGTH_SHORT).show();
            JioAccountStorage.clear(this);
            Intent refreshIntent = new Intent(this, JioSipService.class);
            refreshIntent.setAction(JioSipService.ACTION_START);
            startService(refreshIntent);
        });

        // --- 4. Companion PIN Management ---
        btnNewPin.setOnClickListener(v -> {
            String newPin = authManager.regeneratePin();
            tvGatewayPin.setText("PIN: " + newPin);
            Toast.makeText(this, "New PIN: " + newPin, Toast.LENGTH_SHORT).show();
        });

        btnCopyPin.setOnClickListener(v -> {
            String pin = authManager.getPin();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip = ClipData.newPlainText("Companion PIN", pin);
                cm.setPrimaryClip(clip);
                Toast.makeText(this, "PIN copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateInterceptStatusText(boolean enabled) {
        if (enabled) {
            tvInterceptStatus.setText("Active: Numbers dialed in phone app will route through Jio Landline");
            tvInterceptStatus.setTextColor(getResources().getColor(R.color.call_green));
        } else {
            tvInterceptStatus.setText("Disabled: Outgoing calls will proceed normally through cellular SIM");
            tvInterceptStatus.setTextColor(getResources().getColor(R.color.status_amber));
        }
    }

    private void updateTelecomStatusText() {
        if (!JioTelecomManager.isSupported()) return;

        boolean isRegistered = JioTelecomManager.isPhoneAccountRegistered(this);
        boolean isEnabled = JioTelecomManager.isPhoneAccountEnabled(this);

        if (isEnabled) {
            tvTelecomStatus.setText("Status: Calling Account Active in Android Settings");
            tvTelecomStatus.setTextColor(getResources().getColor(R.color.call_green));
        } else if (isRegistered) {
            tvTelecomStatus.setText("Status: Registered. (Tap button to enable in Settings)");
            tvTelecomStatus.setTextColor(getResources().getColor(R.color.status_amber));
        } else {
            tvTelecomStatus.setText("Status: Not Registered with TelecomManager");
            tvTelecomStatus.setTextColor(getResources().getColor(R.color.text_secondary));
        }
    }

    private void updateAccountDisplay() {
        JioAccount account = JioAccountStorage.load(this);
        boolean isReg = sipManager != null && sipManager.isRegistered();

        if (account != null && account.isValid()) {
            String num = account.getMsisdn();
            if (num != null && num.length() == 10) {
                // Format: 033-31628760 or similar
                tvPhoneNumber.setText(num.substring(0, 3) + "-" + num.substring(3));
            } else {
                tvPhoneNumber.setText(num != null ? num : "Active Line");
            }
            tvRouterHost.setText("Router: " + account.getProxyHost());
            tvRealmInfo.setText("Realm: " + (account.getRealm() != null ? account.getRealm() : "ko.wln.ims.jio.com"));
        } else {
            tvPhoneNumber.setText("Searching Router...");
            tvRouterHost.setText("Router: Detecting...");
            tvRealmInfo.setText("Realm: Jio IMS");
        }

        // Live Registration status
        if (isReg) {
            llStatusPill.setBackgroundResource(R.drawable.bg_status_pill_green);
            ivStatusDot.setImageResource(R.drawable.ic_dot_green);
            tvStatusPill.setText("ONLINE");
            tvStatusPill.setTextColor(getResources().getColor(R.color.call_green));
            tvStatusDesc.setText("Status: Registered (Ready for Calls)");
            tvStatusDesc.setTextColor(getResources().getColor(R.color.call_green));
        } else {
            llStatusPill.setBackgroundResource(R.drawable.bg_status_pill_amber);
            ivStatusDot.setImageResource(R.drawable.ic_dot_amber);
            tvStatusPill.setText("CONNECTING");
            tvStatusPill.setTextColor(getResources().getColor(R.color.status_amber));
            tvStatusDesc.setText("Status: Connecting to router...");
            tvStatusDesc.setTextColor(getResources().getColor(R.color.status_amber));
        }

        // Companion Gateway display
        if (authManager != null) {
            String ip = (sipManager != null) ? sipManager.getLocalWifiIpAddress() : "192.168.29.X";
            tvGatewayInfo.setText("Companion Gateway: " + ip + ":8088");
            tvGatewayPin.setText("PIN: " + authManager.getPin());
            tvDiagIp.setText("Local IP: " + ip);
        }

        // Diagnostics
        if (account != null && account.getVirtualMac() != null && !account.getVirtualMac().isEmpty()) {
            tvDiagMac.setText("MAC: " + account.getVirtualMac().toUpperCase());
        }
        tvDiagAndroid.setText("OS: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        if (JioTelecomManager.isSupported()) {
            updateTelecomStatusText();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountDisplay();
    }

    // --- JioRegistrationListener ---
    @Override
    public void onRegistrationProgress() {
        runOnUiThread(() -> {
            llStatusPill.setBackgroundResource(R.drawable.bg_status_pill_amber);
            ivStatusDot.setImageResource(R.drawable.ic_dot_amber);
            tvStatusPill.setText("CONNECTING");
            tvStatusPill.setTextColor(getResources().getColor(R.color.status_amber));
            tvStatusDesc.setText("Status: Connecting to Jio router...");
            tvStatusDesc.setTextColor(getResources().getColor(R.color.status_amber));
        });
    }

    @Override
    public void onRegistered() {
        runOnUiThread(() -> {
            updateAccountDisplay();
        });
    }

    @Override
    public void onRegistrationFailed(int statusCode, String reason) {
        runOnUiThread(() -> {
            llStatusPill.setBackgroundResource(R.drawable.bg_status_pill_red);
            ivStatusDot.setImageResource(R.drawable.ic_dot_red);
            tvStatusPill.setText("OFFLINE");
            tvStatusPill.setTextColor(getResources().getColor(R.color.call_red));
            tvStatusDesc.setText("Status: Registration Failed (" + statusCode + ")");
            tvStatusDesc.setTextColor(getResources().getColor(R.color.call_red));
        });
    }

    @Override
    public void onUnregistered() {
        runOnUiThread(() -> {
            llStatusPill.setBackgroundResource(R.drawable.bg_status_pill_red);
            ivStatusDot.setImageResource(R.drawable.ic_dot_red);
            tvStatusPill.setText("DISCONNECTED");
            tvStatusPill.setTextColor(getResources().getColor(R.color.call_red));
            tvStatusDesc.setText("Status: Disconnected");
            tvStatusDesc.setTextColor(getResources().getColor(R.color.call_red));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sipManager != null) {
            sipManager.removeRegistrationListener(this);
        }
    }
}
