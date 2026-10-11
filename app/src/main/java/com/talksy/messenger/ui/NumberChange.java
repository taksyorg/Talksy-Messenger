package com.talksy.messenger.ui;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;

import com.talksy.messenger.net.ApiClient;

import org.json.JSONObject;

public final class NumberChange {
    private NumberChange() {}

    public static void showMenu(Activity a, ApiClient api, String current, Runnable onChanged) {
        CharSequence[] items = {"Скопировать номер", "Изменить номер"};
        new TDialog.Builder(a).setTitle(current == null || current.isEmpty() ? "Номер" : current).setItems(items, (d, which) -> {
            if (which == 0) NumbersUi.copy(a, "number", current == null ? "" : current);
            else askNew(a, api, onChanged);
        }).show();
    }

    private static void askNew(Activity a, ApiClient api, Runnable onChanged) {
        EditText number = NumbersUi.field(a, "+888 0000 0000", InputType.TYPE_CLASS_PHONE);
        EditText pw = NumbersUi.field(a, "Пароль номера", NumbersUi.PASSWORD);
        EditText pin = NumbersUi.field(a, "PIN (если включена доп. защита)", NumbersUi.PIN);
        android.widget.LinearLayout form = NumbersUi.form(a, "Введите номер +888/+999 и его пароль. Номер сменится, "
                + "прежний номер будет стёрт, чаты и группы сохранятся.", number, pw, pin);
        new TDialog.Builder(a).setTitle("Изменить номер").setView(form)
                .setPositiveButton("Изменить", (d, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("number", number.getText().toString().trim()); b.put("password", pw.getText().toString()); b.put("pin", pin.getText().toString().trim()); }
                    catch (Exception ignored) {}
                    api.economyPost("/me/number/change", b, (ok, json, err) -> {
                        if (!ok) { EconomyUi.toast(a, err != null ? err : "Не удалось изменить номер"); return; }
                        EconomyUi.toast(a, "Номер изменён");
                        if (onChanged != null) onChanged.run();
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
