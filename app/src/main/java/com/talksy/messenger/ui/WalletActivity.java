package com.talksy.messenger.ui;

import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;

import org.json.JSONArray;
import org.json.JSONObject;

public class WalletActivity extends EconomyScreen {

    @Override protected String screenTitle() { return "Wallet"; }

    @Override protected void onBuilt() { }

    @Override
    protected void onResume() {
        super.onResume();
        EconomyUi.loadConfig(api, this::load);
    }

    private void load() {
        api.economyGet("/economy/wallet", (ok, w, err) -> {
            if (!ok) { content.removeAllViews(); content.addView(EconomyUi.text(this, err != null ? err : "Ошибка", 14, R.color.err, false)); return; }
            api.economyGet("/economy/transactions", (ok2, t, err2) -> render(w, ok2 ? t : null));
        });
    }

    private void render(JSONObject w, JSONObject tx) {
        content.removeAllViews();

        LinearLayout bal = EconomyUi.card(this);
        bal.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView big = EconomyUi.text(this, EconomyUi.price(this, w.optLong("balance"), 30), 34, R.color.text1, true);
        big.setGravity(Gravity.CENTER);
        bal.addView(big);
        bal.addView(EconomyUi.text(this, EconomyUi.name, 13, R.color.text2, false));
        Button top = EconomyUi.button(this, "Пополнить", true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 46));
        lp.topMargin = EconomyUi.dp(this, 14);
        top.setLayoutParams(lp);
        top.setOnClickListener(v -> showTopup());
        bal.addView(top);
        content.addView(bal);

        content.addView(navRow("store", "Магазин", "Темы, оформления, реакции, стикеры", StoreActivity.class));
        boolean prem = w.optJSONObject("premium") != null && w.optJSONObject("premium").optBoolean("active");
        content.addView(navRow("premium", "Talksy Premium", prem ? "Активен" : "Откройте дополнительные возможности", PremiumActivity.class));

        JSONObject cr = w.optJSONObject("creator");
        if (cr != null && cr.optLong("total") > 0) {
            LinearLayout c = EconomyUi.card(this);
            c.addView(EconomyUi.text(this, "Доход автора", 15, R.color.text1, true));
            c.addView(EconomyUi.text(this, android.text.TextUtils.concat("Доступно: ", EconomyUi.price(this, cr.optLong("available"))), 14, R.color.text1, false));
            c.addView(EconomyUi.text(this, android.text.TextUtils.concat("В ожидании: ", EconomyUi.price(this, cr.optLong("pending"))), 14, R.color.text2, false));
            c.addView(EconomyUi.text(this, android.text.TextUtils.concat("Всего: ", EconomyUi.price(this, cr.optLong("total"))), 14, R.color.text2, false));
            content.addView(c);
        }

        TextView h = EconomyUi.text(this, "Последние операции", 14, R.color.text2, true);
        h.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 14), 0, EconomyUi.dp(this, 4));
        content.addView(h);
        JSONArray arr = tx == null ? null : tx.optJSONArray("transactions");
        if (arr == null || arr.length() == 0) {
            TextView e = EconomyUi.text(this, "Операций пока нет", 13, R.color.text3, false);
            e.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 8), 0, 0);
            content.addView(e);
            return;
        }
        LinearLayout list = EconomyUi.card(this);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject t = arr.optJSONObject(i);
            LinearLayout r = EconomyUi.row(this);
            r.setPadding(0, EconomyUi.dp(this, 8), 0, EconomyUi.dp(this, 8));
            android.widget.ImageView ic = EconomyUi.imageBox(this, 34);
            com.talksy.messenger.data.IconManager.set(ic, EconomyUi.txIcon(t.optString("type")));
            r.addView(ic);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setPadding(EconomyUi.dp(this, 8), 0, 0, 0);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            left.addView(EconomyUi.text(this, EconomyUi.txTitle(t.optString("type")), 14, R.color.text1, false));
            left.addView(EconomyUi.text(this, EconomyUi.date(t.optDouble("time")), 11, R.color.text3, false));
            r.addView(left);
            long amt = t.optLong("amount");
            TextView a = EconomyUi.text(this, amt == 0 ? "" : EconomyUi.signedPrice(this, amt), 14, R.color.text1, true);
            a.setTextColor(amt > 0 ? EconomyUi.color(this, R.color.ok) : EconomyUi.color(this, R.color.text1));
            r.addView(a);
            list.addView(r);
        }
        content.addView(list);
    }

    private View navRow(String icon, String title, String sub, Class<?> target) {
        LinearLayout c = EconomyUi.card(this);
        c.setClickable(true);
        LinearLayout r = EconomyUi.row(this);
        android.widget.ImageView ic = EconomyUi.imageBox(this, 36);
        com.talksy.messenger.data.IconManager.set(ic, icon);
        r.addView(ic);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(EconomyUi.dp(this, 10), 0, 0, 0);
        col.addView(EconomyUi.text(this, title, 15, R.color.text1, true));
        col.addView(EconomyUi.text(this, sub, 12, R.color.text2, false));
        r.addView(col);
        c.addView(r);
        c.setOnClickListener(v -> { startActivity(new Intent(this, target)); AnimUtils.forward(this); });
        AnimUtils.pressScale(c);
        return c;
    }

    private void showTopup() {
        if (!EconomyUi.devMode || EconomyUi.paymentsEnabled) {
            startActivity(new Intent(this, PaymentsActivity.class));
            AnimUtils.forward(this);
            return;
        }
        if (!EconomyUi.devMode || EconomyUi.topupPackages.length == 0) {
            new TDialog.Builder(this).setTitle("Пополнение")
                    .setMessage("Пополнение скоро будет доступно.")
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        CharSequence[] items = new CharSequence[EconomyUi.topupPackages.length];
        for (int i = 0; i < items.length; i++) items[i] = EconomyUi.amount(EconomyUi.topupPackages[i]);
        new TDialog.Builder(this).setTitle("Тестовое пополнение (dev)")
                .setItems(items, (d, which) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("amount", EconomyUi.topupPackages[which]); b.put("idempotency_key", EconomyUi.newKey()); } catch (Exception ignored) {}
                    api.economyPost("/economy/dev/topup", b, (ok, json, err) -> {
                        if (ok) load(); else EconomyUi.toast(this, err);
                    });
                }).show();
    }
}
