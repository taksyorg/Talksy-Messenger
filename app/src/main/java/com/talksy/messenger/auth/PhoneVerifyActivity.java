package com.talksy.messenger.auth;

import android.content.Intent;
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
import com.talksy.messenger.net.DeviceId;
import com.talksy.messenger.ui.ChatsActivity;
import com.talksy.messenger.ui.EconomyUi;
import com.talksy.messenger.ui.NumbersUi;

import org.json.JSONObject;

public class PhoneVerifyActivity extends BaseActivity {
    public static final String EXTRA_NEW = "is_new";
    private ApiClient api;
    private EditText inpPhone, inpCode;
    private Button btnSend, btnCheck;
    private TextView txtMsg;
    private boolean codeSent = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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

        panel.addView(EconomyUi.text(this, "Подтвердите номер телефона", 19, R.color.text1, true));
        TextView sub = EconomyUi.text(this, "Мы отправим SMS с кодом. Это нужно, чтобы убедиться, что номер принадлежит вам, "
                + "и защитить людей от повторных аккаунтов заблокированных пользователей. Один номер — один аккаунт.", 13, R.color.text2, false);
        sub.setPadding(0, EconomyUi.dp(this, 4), 0, EconomyUi.dp(this, 10));
        panel.addView(sub);

        inpPhone = NumbersUi.field(this, "+49 151 2345678", InputType.TYPE_CLASS_PHONE);
        String saved = new Prefs(this).get(Prefs.KEY_PHONE, "");
        if (saved != null && !saved.isEmpty()) inpPhone.setText(saved.startsWith("+") ? saved : "+" + saved);
        panel.addView(inpPhone);

        btnSend = EconomyUi.button(this, "Отправить код", true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 48));
        lp.topMargin = EconomyUi.dp(this, 12);
        btnSend.setLayoutParams(lp);
        btnSend.setOnClickListener(v -> sendCode());
        panel.addView(btnSend);

        inpCode = NumbersUi.field(this, "Код из SMS", InputType.TYPE_CLASS_NUMBER);
        inpCode.setVisibility(android.view.View.GONE);
        panel.addView(inpCode);

        btnCheck = EconomyUi.button(this, "Подтвердить", true);
        btnCheck.setLayoutParams(new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 48)));
        ((LinearLayout.LayoutParams) btnCheck.getLayoutParams()).topMargin = EconomyUi.dp(this, 12);
        btnCheck.setVisibility(android.view.View.GONE);
        btnCheck.setOnClickListener(v -> check());
        panel.addView(btnCheck);

        txtMsg = EconomyUi.text(this, "", 12, R.color.err, false);
        txtMsg.setGravity(Gravity.CENTER);
        txtMsg.setPadding(0, EconomyUi.dp(this, 10), 0, 0);
        panel.addView(txtMsg);
        setContentView(sv);
    }

    private void sendCode() {
        String phone = inpPhone.getText().toString().trim();
        if (phone.isEmpty()) { msg("Введите номер телефона", true); return; }
        btnSend.setEnabled(false);
        JSONObject body = new JSONObject();
        try { body.put("phone", phone); body.put("device_id", DeviceId.get(this)); } catch (Exception ignored) {}
        api.economyPost("/auth/phone/send", body, (ok, json, err) -> {
            btnSend.setEnabled(true);
            if (!ok) { msg(err != null ? err : "Не удалось отправить SMS", true); return; }
            codeSent = true;
            btnSend.setText("Отправить код ещё раз");
            inpCode.setVisibility(android.view.View.VISIBLE);
            btnCheck.setVisibility(android.view.View.VISIBLE);
            inpCode.requestFocus();
            msg(json != null && json.optBoolean("sandbox") ? "Тестовый режим: код 000000" : "Код отправлен", false);
        });
    }

    private void check() {
        String phone = inpPhone.getText().toString().trim();
        String code = inpCode.getText().toString().trim();
        if (!codeSent || code.isEmpty()) { msg("Введите код из SMS", true); return; }
        btnCheck.setEnabled(false);
        JSONObject body = new JSONObject();
        try { body.put("phone", phone); body.put("code", code); } catch (Exception ignored) {}
        api.economyPost("/auth/phone/check", body, (ok, json, err) -> {
            btnCheck.setEnabled(true);
            if (!ok) { msg(err != null ? err : "Неверный код", true); return; }
            Intent i = getIntent().getBooleanExtra(EXTRA_NEW, false)
                    ? new Intent(this, CreateProfileActivity.class) : new Intent(this, ChatsActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            AnimUtils.forward(this);
            finish();
        });
    }

    private void msg(String m, boolean error) {
        txtMsg.setText(m);
        txtMsg.setTextColor(EconomyUi.color(this, error ? R.color.err : R.color.ok));
    }

    @Override public void onBackPressed() {   }
}
