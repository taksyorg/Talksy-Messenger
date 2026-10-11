package com.talksy.messenger.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.R;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;

public class EmailActivity extends BaseActivity {

    private static final long RESEND_COOLDOWN_MS = 60_000;

    private EditText inpEmail;
    private TextView txtError;
    private Button btnContinue;
    private ApiClient apiClient;
    private android.os.CountDownTimer cooldownTimer;
    private String defaultBtnText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email);

        apiClient = new ApiClient(this);

        inpEmail = findViewById(R.id.inp_email);
        txtError = findViewById(R.id.txt_error);
        btnContinue = findViewById(R.id.btn_continue);
        defaultBtnText = btnContinue.getText().toString();

        btnContinue.setOnClickListener(v -> onContinue());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cooldownTimer != null) cooldownTimer.cancel();
    }

    private void onContinue() {
        String email = inpEmail.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(getString(R.string.auth_email_error));
            return;
        }

        if (!apiClient.isConfigured()) {
            showError(getString(R.string.server_not_configured_full));
            return;
        }

        txtError.setVisibility(View.GONE);
        btnContinue.setEnabled(false);
        btnContinue.setText(getString(R.string.sending_code));

        apiClient.requestEmailCode(email, (ok, data, error) -> {
            if (!ok) {
                btnContinue.setEnabled(true);
                btnContinue.setText(defaultBtnText);
                showError(error != null ? error : getString(R.string.code_send_failed));
                return;
            }

            Prefs prefs = new Prefs(this);
            prefs.put(Prefs.KEY_EMAIL, email);
            startCooldown();
            startActivity(new Intent(this, CodeActivity.class));
            AnimUtils.forward(this);
        });
    }

    private void startCooldown() {
        btnContinue.setEnabled(false);
        if (cooldownTimer != null) cooldownTimer.cancel();
        cooldownTimer = new android.os.CountDownTimer(RESEND_COOLDOWN_MS, 1000) {
            @Override public void onTick(long msLeft) {
                btnContinue.setText(getString(R.string.code_sent_wait, msLeft / 1000 + 1));
            }
            @Override public void onFinish() {
                btnContinue.setEnabled(true);
                btnContinue.setText(defaultBtnText);
            }
        }.start();
    }

    private void showError(String message) {
        txtError.setText(message);
        txtError.setVisibility(View.VISIBLE);
    }
}
