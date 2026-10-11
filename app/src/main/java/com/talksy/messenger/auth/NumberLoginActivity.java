package com.talksy.messenger.auth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.talksy.messenger.BaseActivity;
import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.ChatsActivity;
import com.talksy.messenger.ui.EconomyUi;
import com.talksy.messenger.ui.NumbersUi;

public class NumberLoginActivity extends BaseActivity {
    private ApiClient api;
    private EditText inpNumber, inpPassword, inpPin;
    private TextView txtError;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(EconomyUi.color(this, R.color.bg));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        int pad = EconomyUi.dp(this, 28);
        col.setPadding(pad, pad, pad, pad);
        sv.addView(col);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.bg_panel);
        panel.setPadding(EconomyUi.dp(this, 24), EconomyUi.dp(this, 24), EconomyUi.dp(this, 24), EconomyUi.dp(this, 24));
        col.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        panel.addView(EconomyUi.text(this, "Анонимный номер", 19, R.color.text1, true));
        TextView sub = EconomyUi.text(this, "Введите номер +888 или +999 и пароль, выданный при покупке. Почта не нужна.", 13, R.color.text2, false);
        sub.setPadding(0, EconomyUi.dp(this, 4), 0, EconomyUi.dp(this, 10));
        panel.addView(sub);

        inpNumber = NumbersUi.field(this, "+888 0000 0000", InputType.TYPE_CLASS_PHONE);
        inpPassword = NumbersUi.field(this, "Пароль", NumbersUi.PASSWORD);
        inpPin = NumbersUi.field(this, "PIN дополнительной защиты", NumbersUi.PIN);
        inpPin.setVisibility(android.view.View.GONE);
        panel.addView(inpNumber);
        panel.addView(inpPassword);
        panel.addView(inpPin);

        txtError = EconomyUi.text(this, "", 12, R.color.err, false);
        txtError.setGravity(Gravity.CENTER);
        txtError.setVisibility(android.view.View.GONE);
        txtError.setPadding(0, EconomyUi.dp(this, 8), 0, 0);
        panel.addView(txtError);

        btn = EconomyUi.button(this, "Войти", true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 48));
        lp.topMargin = EconomyUi.dp(this, 14);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> submit());
        panel.addView(btn);

        TextView back = EconomyUi.text(this, "Войти по e-mail", 13, R.color.accent, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, EconomyUi.dp(this, 16), 0, 0);
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        panel.addView(back);

        setContentView(sv);
    }

    private void submit() {
        String number = inpNumber.getText().toString().trim();
        String pw = inpPassword.getText().toString();
        if (number.isEmpty() || pw.isEmpty()) { showError("Введите номер и пароль"); return; }
        btn.setEnabled(false);
        btn.setText(getString(R.string.checking));
        api.loginWithNumber(number, pw, inpPin.getText().toString().trim(), Build.MANUFACTURER + " " + Build.MODEL, (ok, json, err) -> {
            btn.setEnabled(true);
            btn.setText("Войти");
            if (ok) {
                new Prefs(this).putBool(Prefs.KEY_LOGGED_IN, true);
                Intent i = new Intent(this, ChatsActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                AnimUtils.forward(this);
                finish();
                return;
            }
            if (json != null && json.optBoolean("need_pin")) {
                inpPin.setVisibility(android.view.View.VISIBLE);
                inpPin.requestFocus();
                showError("Введите PIN дополнительной защиты");
                return;
            }
            showError(err != null ? err : "Не удалось войти");
        });
    }

    private void showError(String m) {
        txtError.setText(m);
        txtError.setVisibility(android.view.View.VISIBLE);
    }
}
