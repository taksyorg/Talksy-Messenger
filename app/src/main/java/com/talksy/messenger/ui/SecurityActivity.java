package com.talksy.messenger.ui;

import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

import org.json.JSONArray;
import org.json.JSONObject;

public class SecurityActivity extends EconomyScreen {
    private JSONObject sec;

    @Override protected String screenTitle() { return "Безопасность аккаунта"; }
    @Override protected void onBuilt() { }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        api.economyGet("/me/security", (ok, s, err) -> {
            if (!ok) { content.removeAllViews(); content.addView(EconomyUi.text(this, err != null ? err : "Ошибка загрузки", 14, R.color.err, false)); return; }
            sec = s;
            api.economyGet("/me/login-history", (ok2, h, err2) -> render(ok2 ? h : null));
        });
    }

    private LinearLayout actionRow(String title, String sub, View.OnClickListener click) {
        LinearLayout c = EconomyUi.card(this);
        c.setClickable(true);
        c.addView(EconomyUi.text(this, title, 15, R.color.text1, true));
        if (sub != null && !sub.isEmpty()) c.addView(EconomyUi.text(this, sub, 12, R.color.text2, false));
        c.setOnClickListener(click);
        AnimUtils.pressScale(c);
        return c;
    }

    private void render(JSONObject history) {
        content.removeAllViews();
        boolean anon = sec.optBoolean("is_anon");
        boolean hasPw = sec.optBoolean("has_password");
        boolean hasEmail = sec.optBoolean("has_email");
        String username = sec.isNull("username") ? "" : sec.optString("username", "");

        LinearLayout info = EconomyUi.card(this);
        info.addView(EconomyUi.text(this, anon ? "Анонимный аккаунт" : "Аккаунт", 12, R.color.text2, false));
        info.addView(EconomyUi.text(this, sec.optString("phone", ""), 17, R.color.text1, true));
        info.addView(EconomyUi.text(this, hasEmail ? sec.optString("email") : "Почта не привязана", 12, R.color.text3, false));
        content.addView(info);

        content.addView(actionRow("Имя пользователя", username.isEmpty() ? "Не задано" : username,
                v -> UsernameDialog.show(this, api, username, this::load)));

        if (hasPw) content.addView(actionRow("Изменить пароль", "Остальные сеансы будут завершены", v -> changePassword()));

        if (hasEmail) {
            if (hasPw) content.addView(actionRow("Удалить почту", sec.optString("email"), v -> removeEmail()));
        } else {
            content.addView(actionRow("Добавить почту", "Необязательно. Позволит восстановить вход.", v -> addEmail()));
        }

        if (hasPw) {
            LinearLayout c = EconomyUi.card(this);
            LinearLayout r = EconomyUi.row(this);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            left.addView(EconomyUi.text(this, "Дополнительная защита", 15, R.color.text1, true));
            left.addView(EconomyUi.text(this, "PIN из 6 цифр при каждом входе по номеру", 12, R.color.text2, false));
            r.addView(left);
            Switch sw = new Switch(this);
            sw.setChecked(sec.optBoolean("extra_protection"));
            sw.setOnClickListener(v -> toggleProtection(sw.isChecked(), sw));
            r.addView(sw);
            c.addView(r);
            content.addView(c);
        }

        content.addView(actionRow("Активные сеансы", "Устройства, на которых выполнен вход", v -> {
            startActivity(new Intent(this, DevicesActivity.class)); AnimUtils.forward(this);
        }));

        TextView h = EconomyUi.text(this, "История входов", 14, R.color.text2, true);
        h.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 14), 0, EconomyUi.dp(this, 4));
        content.addView(h);
        JSONArray arr = history == null ? null : history.optJSONArray("history");
        if (arr == null || arr.length() == 0) {
            TextView e = EconomyUi.text(this, "Записей пока нет", 13, R.color.text3, false);
            e.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 4), 0, 0);
            content.addView(e);
            return;
        }
        LinearLayout list = EconomyUi.card(this);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            boolean ok = x.optBoolean("success");
            LinearLayout r = EconomyUi.row(this);
            r.setPadding(0, EconomyUi.dp(this, 8), 0, EconomyUi.dp(this, 8));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            String device = x.optString("device");
            left.addView(EconomyUi.text(this, device.isEmpty() ? "Неизвестное устройство" : device, 14, R.color.text1, false));
            String place = x.optString("city") + (x.optString("country").isEmpty() ? "" : (x.optString("city").isEmpty() ? "" : ", ") + x.optString("country"));
            String line = EconomyUi.date(x.optDouble("time")) + (place.isEmpty() ? "" : " • " + place)
                    + (x.optString("ip").isEmpty() ? "" : " • " + x.optString("ip"));
            left.addView(EconomyUi.text(this, line, 11, R.color.text3, false));
            r.addView(left);
            TextView st = EconomyUi.text(this, ok ? ("number".equals(x.optString("kind")) ? "номер" : "e-mail") : "неудача", 12,
                    ok ? R.color.text2 : R.color.err, true);
            st.setGravity(Gravity.END);
            r.addView(st);
            list.addView(r);
        }
        content.addView(list);
    }

    private void changePassword() {
        EditText old = NumbersUi.field(this, "Текущий пароль", NumbersUi.PASSWORD);
        EditText n1 = NumbersUi.field(this, "Новый пароль (от 8 символов)", NumbersUi.PASSWORD);
        EditText n2 = NumbersUi.field(this, "Повторите новый пароль", NumbersUi.PASSWORD);
        EditText pin = NumbersUi.field(this, "PIN (если включена доп. защита)", NumbersUi.PIN);
        LinearLayout form = NumbersUi.form(this, "", old, n1, n2);
        if (sec.optBoolean("extra_protection")) form.addView(pin);
        new TDialog.Builder(this).setTitle("Изменить пароль").setView(form)
                .setPositiveButton("Сохранить", (d, w) -> {
                    if (!n1.getText().toString().equals(n2.getText().toString())) { EconomyUi.toast(this, "Пароли не совпадают"); return; }
                    JSONObject b = new JSONObject();
                    try {
                        b.put("old_password", old.getText().toString());
                        b.put("new_password", n1.getText().toString());
                        b.put("pin", pin.getText().toString());
                    } catch (Exception ignored) {}
                    api.economyPost("/me/password", b, (ok, json, err) -> {
                        EconomyUi.toast(this, ok ? "Пароль изменён" : (err != null ? err : "Ошибка"));
                        if (ok) load();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void addEmail() {
        EditText email = NumbersUi.field(this, "E-mail", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        new TDialog.Builder(this).setTitle("Добавить почту").setView(NumbersUi.form(this, "Мы отправим код подтверждения.", email))
                .setPositiveButton("Отправить код", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("email", email.getText().toString().trim()); } catch (Exception ignored) {}
                    api.economyPost("/me/email/request", b, (ok, json, err) -> {
                        if (ok) confirmEmail(); else EconomyUi.toast(this, err != null ? err : "Ошибка");
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirmEmail() {
        EditText code = NumbersUi.field(this, "Код из письма", InputType.TYPE_CLASS_NUMBER);
        new TDialog.Builder(this).setTitle("Подтверждение").setView(NumbersUi.form(this, "Введите 6-значный код.", code))
                .setPositiveButton("Подтвердить", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("code", code.getText().toString().trim()); } catch (Exception ignored) {}
                    api.economyPost("/me/email/confirm", b, (ok, json, err) -> {
                        EconomyUi.toast(this, ok ? "Почта привязана" : (err != null ? err : "Ошибка"));
                        if (ok) load();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void removeEmail() {
        EditText pw = NumbersUi.field(this, "Пароль", NumbersUi.PASSWORD);
        EditText pin = NumbersUi.field(this, "PIN", NumbersUi.PIN);
        LinearLayout form = NumbersUi.form(this, "Для удаления почты нужен пароль.", pw);
        if (sec.optBoolean("extra_protection")) form.addView(pin);
        new TDialog.Builder(this).setTitle("Удалить почту").setView(form)
                .setPositiveButton("Удалить", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("password", pw.getText().toString()); b.put("pin", pin.getText().toString()); } catch (Exception ignored) {}
                    api.economyPost("/me/email/remove", b, (ok, json, err) -> {
                        EconomyUi.toast(this, ok ? "Почта удалена" : (err != null ? err : "Ошибка"));
                        if (ok) load();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void toggleProtection(boolean enable, Switch sw) {
        EditText pw = NumbersUi.field(this, "Пароль", NumbersUi.PASSWORD);
        EditText pin = NumbersUi.field(this, enable ? "Новый PIN (6 цифр)" : "Текущий PIN", NumbersUi.PIN);
        new TDialog.Builder(this).setTitle(enable ? "Включить дополнительную защиту" : "Выключить дополнительную защиту")
                .setView(NumbersUi.form(this, enable ? "Без этого PIN войти по номеру будет нельзя, даже зная пароль." : "", pw, pin))
                .setCancelable(false)
                .setPositiveButton(enable ? "Включить" : "Выключить", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try {
                        b.put("enable", enable);
                        b.put("password", pw.getText().toString());
                        if (enable) b.put("new_pin", pin.getText().toString()); else b.put("pin", pin.getText().toString());
                    } catch (Exception ignored) {}
                    api.economyPost("/me/protection", b, (ok, json, err) -> {
                        if (!ok) { EconomyUi.toast(this, err != null ? err : "Ошибка"); sw.setChecked(!enable); }
                        else load();
                    });
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> sw.setChecked(!enable)).show();
    }
}
