package com.talksy.messenger.auth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.ChatsActivity;

public class CodeActivity extends BaseActivity {

    private static final long RESEND_COOLDOWN_MS = 60_000;

    private EditText inpCode;
    private TextView txtError;
    private Button btnVerify;
    private TextView btnResend;
    private Prefs prefs;
    private ApiClient apiClient;
    private String email;
    private android.os.CountDownTimer cooldownTimer;
    private String defaultResendText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code);

        prefs = new Prefs(this);
        apiClient = new ApiClient(this);
        email = prefs.get(Prefs.KEY_EMAIL, "");

        ((TextView) findViewById(R.id.txt_sub1)).setText(getString(R.string.auth_code_sub_phone, email));
        findViewById(R.id.txt_sub2).setVisibility(View.GONE);
        findViewById(R.id.txt_dev_hint).setVisibility(View.GONE);

        inpCode = findViewById(R.id.inp_code);
        txtError = findViewById(R.id.txt_error);
        btnVerify = findViewById(R.id.btn_verify);
        btnResend = findViewById(R.id.btn_resend);
        defaultResendText = btnResend.getText().toString();

        btnVerify.setOnClickListener(v -> onVerify());
        btnResend.setOnClickListener(v -> onResend());

        startCooldown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cooldownTimer != null) cooldownTimer.cancel();
    }

    private void startCooldown() {
        btnResend.setEnabled(false);
        if (cooldownTimer != null) cooldownTimer.cancel();
        cooldownTimer = new android.os.CountDownTimer(RESEND_COOLDOWN_MS, 1000) {
            @Override public void onTick(long msLeft) {
                btnResend.setText(getString(R.string.code_sent_wait, msLeft / 1000 + 1));
            }
            @Override public void onFinish() {
                btnResend.setEnabled(true);
                btnResend.setText(defaultResendText);
            }
        }.start();
    }

    private void onResend() {
        btnResend.setEnabled(false);
        apiClient.requestEmailCode(email, (ok, data, error) -> {
            if (ok) {
                txtError.setVisibility(View.GONE);
                startCooldown();
            } else {
                btnResend.setEnabled(true);
                showError(error != null ? error : getString(R.string.code_resend_failed));
            }
        });
    }

    private void onVerify() {
        String entered = inpCode.getText().toString().trim();
        if (entered.isEmpty()) {
            showError(getString(R.string.auth_code_error));
            return;
        }

        btnVerify.setEnabled(false);
        btnVerify.setText(getString(R.string.checking));

        String localPhone = prefs.get(Prefs.KEY_PHONE, "");
        String deviceLabel = Build.MANUFACTURER + " " + Build.MODEL;

        apiClient.verifyEmailCode(email, entered, localPhone, deviceLabel, (ok, result, error) -> {
            btnVerify.setEnabled(true);
            btnVerify.setText(getString(R.string.auth_code_verify));

            if (!ok || result == null) {
                showError(error != null ? error : getString(R.string.auth_code_error));
                return;
            }

            txtError.setVisibility(View.GONE);
            prefs.putBool(Prefs.KEY_LOGGED_IN, true);

            apiClient.economyGet("/me/phone-status", (okP, st, errP) -> {
                boolean needPhone = okP && st != null && st.optBoolean("required") && !st.optBoolean("verified");
                Intent i = needPhone ? new Intent(this, PhoneVerifyActivity.class).putExtra(PhoneVerifyActivity.EXTRA_NEW, result.isNew)
                        : result.isNew ? new Intent(this, CreateProfileActivity.class)
                        : new Intent(this, ChatsActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                AnimUtils.forward(this);
                finish();
            });
        });
    }

    private void showError(String message) {
        txtError.setText(message);
        txtError.setVisibility(View.VISIBLE);
    }
}
