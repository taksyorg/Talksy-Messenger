package com.talksy.messenger.ui;

import com.talksy.messenger.data.IconManager;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.AppLock;

public class LockActivity extends BaseActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_UNLOCK = "unlock";
    public static final String MODE_SETUP = "setup";
    public static final String MODE_VERIFY = "verify";

    public static Intent intent(Context c, String mode) {
        return new Intent(c, LockActivity.class).putExtra(EXTRA_MODE, mode);
    }

    private String mode;
    private final StringBuilder entered = new StringBuilder();
    private String firstEntry = null;
    private TextView title, subtitle;
    private TextView[] dots;
    private LinearLayout dotsRow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable countdown;

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_UNLOCK;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        root.setPadding((int) dp(24), (int) dp(72), (int) dp(24), (int) dp(24));

        TextView logo = new TextView(this);
        IconManager.set(logo, "secure_chat", 56);
        root.addView(logo);

        title = new TextView(this);
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text1));
        title.setPadding(0, (int) dp(12), 0, (int) dp(4));
        root.addView(title);

        subtitle = new TextView(this);
        subtitle.setTextSize(13);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text2));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMinHeight((int) dp(36));
        root.addView(subtitle);

        dotsRow = new LinearLayout(this);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, (int) dp(16), 0, (int) dp(24));
        dots = new TextView[AppLock.PIN_LENGTH];
        for (int i = 0; i < dots.length; i++) {
            TextView d = new TextView(this);
            d.setTextSize(22);
            d.setPadding((int) dp(8), 0, (int) dp(8), 0);
            dots[i] = d;
            dotsRow.addView(d);
        }
        root.addView(dotsRow);

        root.addView(buildKeypad(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!MODE_UNLOCK.equals(mode)) {
            TextView cancel = new TextView(this);
            cancel.setText(android.R.string.cancel);
            cancel.setTextColor(ContextCompat.getColor(this, R.color.accent));
            cancel.setTextSize(15);
            cancel.setPadding((int) dp(16), (int) dp(16), (int) dp(16), (int) dp(8));
            cancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
            root.addView(cancel);
        }
        setContentView(root);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (MODE_UNLOCK.equals(mode)) moveTaskToBack(true);
                else { setResult(RESULT_CANCELED); finish(); }
            }
        });

        showPrompt();
        renderDots();
        if (MODE_UNLOCK.equals(mode) && AppLock.biometricEnabled(this) && biometricAvailable()) {
            handler.postDelayed(this::showBiometric, 250);
        }
        startCountdownIfNeeded();
    }

    private void showPrompt() {
        if (MODE_SETUP.equals(mode)) {
            title.setText(firstEntry == null ? R.string.lock_create_pin : R.string.lock_repeat_pin);
            subtitle.setText(R.string.lock_setup_hint);
        } else {
            title.setText(R.string.lock_enter_pin);
            subtitle.setText("");
        }
    }

    private View buildKeypad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"bio", "0", "del"}};
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (String k : r) row.addView(key(k));
            pad.addView(row);
        }
        return pad;
    }

    private View key(String k) {
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(ContextCompat.getColor(this, R.color.text1));
        t.setTextSize(26);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) dp(76), (int) dp(76));
        lp.setMargins((int) dp(8), (int) dp(6), (int) dp(8), (int) dp(6));
        t.setLayoutParams(lp);
        if ("bio".equals(k)) {
            boolean show = MODE_UNLOCK.equals(mode) && AppLock.biometricEnabled(this) && biometricAvailable();
            t.setText("");
            if (show) { IconManager.set(t, "security_check", 30); t.setOnClickListener(v -> showBiometric()); }
            return t;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(ContextCompat.getColor(this, R.color.bg2));
        t.setBackground(bg);
        if ("del".equals(k)) {
            IconManager.set(t, "backspace", 26);
            t.setOnClickListener(v -> { if (entered.length() > 0) { entered.deleteCharAt(entered.length() - 1); renderDots(); } });
        } else {
            t.setText(k);
            t.setOnClickListener(v -> onDigit(k));
        }
        AnimUtils.pressScale(t);
        return t;
    }

    private void onDigit(String d) {
        if (AppLock.secondsUntilRetry(this) > 0 && !MODE_SETUP.equals(mode)) return;
        if (entered.length() >= AppLock.PIN_LENGTH) return;
        entered.append(d);
        renderDots();
        if (entered.length() == AppLock.PIN_LENGTH) handler.postDelayed(this::submit, 120);
    }

    private void renderDots() {
        int accent = ContextCompat.getColor(this, R.color.accent);
        int muted = ContextCompat.getColor(this, R.color.text3);
        for (int i = 0; i < dots.length; i++) {
            boolean filled = i < entered.length();
            dots[i].setText(filled ? "●" : "○");
            dots[i].setTextColor(filled ? accent : muted);
        }
    }

    private void submit() {
        final String pin = entered.toString();
        entered.setLength(0);
        renderDots();

        if (MODE_SETUP.equals(mode)) {
            if (firstEntry == null) {
                firstEntry = pin;
                showPrompt();
            } else if (firstEntry.equals(pin)) {
                AppLock.setPin(this, pin);
                setResult(RESULT_OK);
                finish();
            } else {
                firstEntry = null;
                showPrompt();
                fail(getString(R.string.lock_mismatch));
            }
            return;
        }

        if (AppLock.verify(this, pin)) {
            setResult(RESULT_OK);
            finish();
        } else {
            long wait = AppLock.secondsUntilRetry(this);
            if (wait > 0) startCountdownIfNeeded();
            else fail(getString(R.string.lock_wrong_pin));
        }
    }

    private void fail(String message) {
        subtitle.setText(message);
        subtitle.setTextColor(0xFFE5484D);
        dotsRow.animate().translationX(dp(14)).setDuration(60).withEndAction(() ->
                dotsRow.animate().translationX(-dp(14)).setDuration(90).withEndAction(() ->
                        dotsRow.animate().translationX(0).setDuration(60).start()).start()).start();
    }

    private void startCountdownIfNeeded() {
        if (MODE_SETUP.equals(mode)) return;
        if (countdown != null) handler.removeCallbacks(countdown);
        countdown = new Runnable() {
            @Override public void run() {
                long left = AppLock.secondsUntilRetry(LockActivity.this);
                if (left > 0) {
                    subtitle.setTextColor(0xFFE5484D);
                    subtitle.setText(getString(R.string.lock_too_many, left));
                    handler.postDelayed(this, 1000);
                } else if (subtitle.getCurrentTextColor() == 0xFFE5484D) {
                    subtitle.setText("");
                    subtitle.setTextColor(ContextCompat.getColor(LockActivity.this, R.color.text2));
                }
            }
        };
        handler.post(countdown);
    }

    private boolean biometricAvailable() {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometric() {
        if (isFinishing()) return;
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        AppLock.markUnlocked();
                        setResult(RESULT_OK);
                        finish();
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_biometric_title))
                .setNegativeButtonText(getString(R.string.lock_use_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();
        prompt.authenticate(info);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
