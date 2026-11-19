package com.jio.calling.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.CallLog;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.jio.calling.R;
import com.jio.calling.sip.JioCallListener;
import com.jio.calling.sip.JioSipManager;
import com.jio.calling.util.JioCallLogHelper;

/**
 * Incoming call screen.
 * Displays on top of the Android lockscreen, rings, and provides Accept and Decline actions.
 */
public class IncomingCallActivity extends Activity implements JioCallListener {
    private static final String TAG = "IncomingCallActivity";

    private TextView tvCallerNumber;
    private TextView tvCallerName;
    private Button btnAnswer;
    private Button btnDecline;

    private Ringtone ringtone;
    private Vibrator vibrator;
    private JioSipManager sipManager;
    private String remoteNumber;
    private String remoteName;
    private long callReceiveTime = 0;
    private boolean isHandled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        callReceiveTime = System.currentTimeMillis();

        // Turn screen on and display over keyguard/lockscreen on KitKat
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_incoming_call);

        tvCallerNumber = findViewById(R.id.tv_caller_number);
        tvCallerName = findViewById(R.id.tv_caller_name);
        btnAnswer = findViewById(R.id.btn_answer);
        btnDecline = findViewById(R.id.btn_decline);

        remoteNumber = JioCallLogHelper.normalizeNumber(getIntent().getStringExtra("REMOTE_NUMBER"));
        remoteName = getIntent().getStringExtra("REMOTE_NAME");

        tvCallerNumber.setText(remoteNumber != null && !remoteNumber.isEmpty() ? remoteNumber : "Incoming Call");
        tvCallerName.setText(remoteName != null ? remoteName : "Jio Landline");

        sipManager = JioSipManager.getInstance(this);
        sipManager.addCallListener(this);

        startRinging();

        btnAnswer.setOnClickListener(v -> {
            if (isHandled) return;
            isHandled = true;
            btnAnswer.setEnabled(false);
            btnDecline.setEnabled(false);

            stopRinging();
            try {
                sipManager.answerCall();
            } catch (Exception e) {
                Log.e(TAG, "Error answering call: " + e.getMessage(), e);
            }

            Intent inCall = new Intent(this, InCallActivity.class);
            inCall.putExtra(InCallActivity.EXTRA_NUMBER, remoteNumber);
            inCall.putExtra(InCallActivity.EXTRA_IS_INCOMING, true);
            startActivity(inCall);
            finish();
        });

        btnDecline.setOnClickListener(v -> {
            if (isHandled) return;
            isHandled = true;
            btnAnswer.setEnabled(false);
            btnDecline.setEnabled(false);

            stopRinging();
            // Log declined call as MISSED_TYPE in native CallLog
            JioCallLogHelper.logCall(this, remoteNumber != null ? remoteNumber : "Unknown",
                    CallLog.Calls.MISSED_TYPE, callReceiveTime, 0);

            try {
                sipManager.hangupCall();
            } catch (Exception e) {
                Log.e(TAG, "Error hanging up call: " + e.getMessage(), e);
            }
            finish();
        });
    }

    private void startRinging() {
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start ringtone: " + t.getMessage());
        }

        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pattern = {0, 1000, 1000};
                vibrator.vibrate(pattern, 0);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start vibrator: " + t.getMessage());
        }
    }

    private void stopRinging() {
        try {
            if (ringtone != null) {
                if (ringtone.isPlaying()) {
                    ringtone.stop();
                }
                ringtone = null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error stopping ringtone: " + t.getMessage());
        }

        try {
            if (vibrator != null) {
                vibrator.cancel();
                vibrator = null;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error stopping vibrator: " + t.getMessage());
        }
    }

    @Override
    public void onCallTerminated(String reason) {
        runOnUiThread(() -> {
            if (!isHandled) {
                isHandled = true;
                // Caller terminated call before user answered -> log MISSED_TYPE
                JioCallLogHelper.logCall(this, remoteNumber != null ? remoteNumber : "Unknown",
                        CallLog.Calls.MISSED_TYPE, callReceiveTime, 0);
            }
            stopRinging();
            finish();
        });
    }

    @Override public void onCallIncoming(String num, String name) {}
    @Override public void onCallConnecting() {}
    @Override public void onCallRinging() {}
    @Override public void onCallEstablished() {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRinging();
        if (sipManager != null) {
            sipManager.removeCallListener(this);
        }
    }
}

