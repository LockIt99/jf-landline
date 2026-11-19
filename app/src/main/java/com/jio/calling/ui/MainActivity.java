package com.jio.calling.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.jio.calling.R;
import com.jio.calling.gateway.GatewayAuthManager;
import com.jio.calling.model.JioAccount;
import com.jio.calling.provisioning.JioAccountStorage;
import com.jio.calling.service.JioSipService;
import com.jio.calling.sip.JioRegistrationListener;
import com.jio.calling.sip.JioSipManager;

/**
 * Main dashboard & manual dialer for JioFiber Landline phone.
 */
public class MainActivity extends Activity implements JioRegistrationListener {
    private TextView tvStatus;
    private TextView tvPhoneNumber;
    private TextView tvRouterHost;
    private TextView tvGatewayInfo;
    private TextView tvGatewayPin;
    private Button btnNewPin;
    private EditText etDialNumber;
    private ImageButton btnBackspace;
    private Button btnCall;
    private Button btnRefresh;

    private JioSipManager sipManager;
    private GatewayAuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvPhoneNumber = findViewById(R.id.tv_phone_number);
        tvRouterHost = findViewById(R.id.tv_router_host);
        tvGatewayInfo = findViewById(R.id.tv_gateway_info);
        tvGatewayPin = findViewById(R.id.tv_gateway_pin);
        btnNewPin = findViewById(R.id.btn_new_pin);
        etDialNumber = findViewById(R.id.et_dial_number);
        btnBackspace = findViewById(R.id.btn_backspace);
        btnCall = findViewById(R.id.btn_call);
        btnRefresh = findViewById(R.id.btn_refresh);

        sipManager = JioSipManager.getInstance(this);
        sipManager.addRegistrationListener(this);
        authManager = GatewayAuthManager.getInstance(this);

        btnNewPin.setOnClickListener(v -> {
            String newPin = authManager.regeneratePin();
            tvGatewayPin.setText("Companion PIN: " + newPin);
            Toast.makeText(this, "New Companion PIN: " + newPin, Toast.LENGTH_SHORT).show();
        });

        // Start background 24/7 service
        Intent serviceIntent = new Intent(this, JioSipService.class);
        serviceIntent.setAction(JioSipService.ACTION_START);
        startService(serviceIntent);

        setupDialpad();
        updateAccountDisplay();

        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(this, "Discovering router & refreshing credentials...", Toast.LENGTH_SHORT).show();
            JioAccountStorage.clear(this);
            Intent refreshIntent = new Intent(this, JioSipService.class);
            refreshIntent.setAction(JioSipService.ACTION_START);
            startService(refreshIntent);
        });
    }

    private void setupDialpad() {
        int[] buttonIds = {
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
            R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7,
            R.id.btn_8, R.id.btn_9, R.id.btn_star, R.id.btn_hash
        };

        View.OnClickListener digitClick = v -> {
            Button b = (Button) v;
            etDialNumber.append(b.getText().toString());
        };

        for (int id : buttonIds) {
            Button b = findViewById(id);
            if (b != null) {
                b.setOnClickListener(digitClick);
            }
        }

        btnBackspace.setOnClickListener(v -> {
            int length = etDialNumber.getText().length();
            if (length > 0) {
                etDialNumber.getText().delete(length - 1, length);
            }
        });

        btnBackspace.setOnLongClickListener(v -> {
            etDialNumber.setText("");
            return true;
        });

        btnCall.setOnClickListener(v -> {
            String number = etDialNumber.getText().toString().trim();
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a number to call", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent callIntent = new Intent(this, JioSipService.class);
            callIntent.setAction(JioSipService.ACTION_MAKE_CALL);
            callIntent.putExtra(JioSipService.EXTRA_PHONE_NUMBER, number);
            startService(callIntent);
        });
    }

    private void updateAccountDisplay() {
        JioAccount account = JioAccountStorage.load(this);
        if (account != null && account.isValid()) {
            tvPhoneNumber.setText(account.getMsisdn() != null ? account.getMsisdn() : "Active");
            tvRouterHost.setText("Router: " + account.getProxyHost());
            tvStatus.setText("Status: " + (sipManager.isRegistered() ? "Registered (Ready)" : "Connecting..."));
        } else {
            tvPhoneNumber.setText("Searching Router...");
            tvRouterHost.setText("Wi-Fi checking...");
            tvStatus.setText("Status: Not Provisioned");
        }

        if (authManager != null) {
            String ip = (sipManager != null) ? sipManager.getLocalWifiIpAddress() : "192.168.29.X";
            tvGatewayInfo.setText("Companion Gateway: " + ip + ":8088");
            tvGatewayPin.setText("Companion PIN: " + authManager.getPin());
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
        runOnUiThread(() -> tvStatus.setText("Status: Connecting to router..."));
    }

    @Override
    public void onRegistered() {
        runOnUiThread(() -> {
            tvStatus.setText("Status: Registered (Ready)");
            updateAccountDisplay();
        });
    }

    @Override
    public void onRegistrationFailed(int statusCode, String reason) {
        runOnUiThread(() -> tvStatus.setText("Status: Registration Failed (" + statusCode + ")"));
    }

    @Override
    public void onUnregistered() {
        runOnUiThread(() -> tvStatus.setText("Status: Disconnected"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sipManager != null) {
            sipManager.removeRegistrationListener(this);
        }
    }
}

