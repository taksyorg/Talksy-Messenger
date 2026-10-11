package com.talksy.messenger.ui;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GiftShopActivity extends EconomyScreen {
    public static final String EXTRA_USER_ID = "to_user_id";
    public static final String EXTRA_USER_NAME = "to_user_name";

    private long toId;
    private String toName = "";
    private TextView balanceView;
    private boolean onlyLimited = false;
    private JSONArray gifts = new JSONArray();
    private LinearLayout premiumBox, gridBox;
    private TextView tabAll, tabLimited;

    @Override protected String screenTitle() {
        String n = getIntent().getStringExtra(EXTRA_USER_NAME);
        return "Подарок для " + (n == null ? "" : n);
    }

    @Override
    protected void onBuilt() {
        toId = getIntent().getLongExtra(EXTRA_USER_ID, -1);
        toName = getIntent().getStringExtra(EXTRA_USER_NAME) == null ? "" : getIntent().getStringExtra(EXTRA_USER_NAME);

        balanceView = EconomyUi.text(this, "", 14, R.color.text1, true);
        balanceView.setPadding(0, 0, EconomyUi.dp(this, 16), 0);
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(balanceView);

        premiumBox = new LinearLayout(this);
        premiumBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(premiumBox);

        TextView h = EconomyUi.text(this, "Отправить подарок", 18, R.color.text1, true);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, EconomyUi.dp(this, 22), 0, EconomyUi.dp(this, 4));
        content.addView(h);
        TextView sub = EconomyUi.text(this, "Дарите " + toName + " подарки, которые можно хранить в профиле или продать Talksy.", 13, R.color.text2, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(EconomyUi.dp(this, 28), 0, EconomyUi.dp(this, 28), EconomyUi.dp(this, 10));
        content.addView(sub);

        LinearLayout tabs = EconomyUi.row(this);
        tabs.setGravity(Gravity.CENTER);
        tabAll = EconomyUi.chip(this, "Все", true);
        tabLimited = EconomyUi.chip(this, "Коллекционные", false);
        tabAll.setOnClickListener(v -> { onlyLimited = false; drawTabs(); drawGrid(); });
        tabLimited.setOnClickListener(v -> { onlyLimited = true; drawTabs(); drawGrid(); });
        tabs.addView(tabAll);
        tabs.addView(tabLimited);
        content.addView(tabs);

        gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setPadding(EconomyUi.dp(this, 10), EconomyUi.dp(this, 10), EconomyUi.dp(this, 10), 0);
        content.addView(gridBox);

        EconomyUi.loadConfig(api, this::load);
    }

    private void drawTabs() {
        tabAll.setBackground(EconomyUi.round(this, onlyLimited ? 0 : EconomyUi.color(this, R.color.bg4), 16));
        tabAll.setTextColor(EconomyUi.color(this, onlyLimited ? R.color.text2 : R.color.text1));
        tabLimited.setBackground(EconomyUi.round(this, onlyLimited ? EconomyUi.color(this, R.color.bg4) : 0, 16));
        tabLimited.setTextColor(EconomyUi.color(this, onlyLimited ? R.color.text1 : R.color.text2));
    }

    private void load() {
        api.economyGet("/economy/wallet", (ok, w, err) -> {
            if (ok) balanceView.setText(android.text.TextUtils.concat("Баланс  ", EconomyUi.price(this, w.optLong("balance"), 14)));
        });
        api.economyGet("/economy/premium", (ok, p, err) -> { if (ok) drawPremium(p); });
        api.economyGet("/economy/gifts", (ok, json, err) -> {
            if (!ok) { gridBox.removeAllViews(); gridBox.addView(EconomyUi.text(this, err != null ? err : "Ошибка", 14, R.color.err, false)); return; }
            gifts = json.optJSONArray("gifts") == null ? new JSONArray() : json.optJSONArray("gifts");
            drawGrid();
        });
    }

    private void drawPremium(JSONObject p) {
        premiumBox.removeAllViews();
        TextView t = EconomyUi.text(this, "Подарить Premium", 18, R.color.text1, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, EconomyUi.dp(this, 14), 0, EconomyUi.dp(this, 4));
        premiumBox.addView(t);
        TextView d = EconomyUi.text(this, "Подарите " + toName + " доступ к эксклюзивным функциям Talksy. Подробнее о Premium", 13, R.color.text2, false);
        d.setGravity(Gravity.CENTER);
        d.setPadding(EconomyUi.dp(this, 28), 0, EconomyUi.dp(this, 28), EconomyUi.dp(this, 12));
        d.setOnClickListener(v -> { startActivity(new Intent(this, PremiumActivity.class)); AnimUtils.forward(this); });
        premiumBox.addView(d);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(EconomyUi.dp(this, 10), 0, EconomyUi.dp(this, 10), 0);
        JSONArray plans = p.optJSONArray("plans");
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; plans != null && i < plans.length(); i++)
            if (plans.optJSONObject(i).optInt("months") > 1) list.add(plans.optJSONObject(i));
        for (JSONObject pl : list) row.addView(planTile(pl));
        premiumBox.addView(row);
    }

    private View planTile(JSONObject pl) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(EconomyUi.round(this, EconomyUi.color(this, R.color.bg3), 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, EconomyUi.dp(this, 150), 1f);
        lp.setMargins(EconomyUi.dp(this, 4), 0, EconomyUi.dp(this, 4), 0);
        tile.setLayoutParams(lp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(EconomyUi.dp(this, 6), EconomyUi.dp(this, 14), EconomyUi.dp(this, 6), EconomyUi.dp(this, 10));
        ImageView ic = EconomyUi.imageBox(this, 52);
        int own = EconomyUi.resId(this, "eco_premium");
        if (own != 0) ic.setImageResource(own); else IconManager.set(ic, "premium");
        col.addView(ic);
        TextView name = EconomyUi.text(this, pl.optInt("months") + " мес.", 14, R.color.text1, true);
        name.setPadding(0, EconomyUi.dp(this, 6), 0, 0);
        col.addView(name);
        col.addView(EconomyUi.text(this, "Premium", 11, R.color.text2, false));
        TextView price = EconomyUi.pricePill(this, pl.optLong("price"));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = EconomyUi.dp(this, 8);
        col.addView(price, plp);
        tile.addView(col, new FrameLayout.LayoutParams(-1, -1));
        int disc = pl.optInt("discount_percent");
        if (disc > 0) {
            TextView badge = EconomyUi.text(this, "−" + disc + "%", 10, R.color.text1, true);
            badge.setTextColor(android.graphics.Color.WHITE);
            badge.setPadding(EconomyUi.dp(this, 6), EconomyUi.dp(this, 2), EconomyUi.dp(this, 6), EconomyUi.dp(this, 2));
            badge.setBackground(EconomyUi.round(this, 0xFF8B5CF6, 8));
            tile.addView(badge, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        }
        tile.setOnClickListener(v -> confirmPremium(pl));
        AnimUtils.pressScale(tile);
        return tile;
    }

    private void confirmPremium(JSONObject pl) {
        LinearLayout box = sheetBox();
        ImageView ic = EconomyUi.imageBox(this, 110);
        ic.setLayoutParams(new LinearLayout.LayoutParams(EconomyUi.dp(this, 110), EconomyUi.dp(this, 110)));
        int own = EconomyUi.resId(this, "eco_premium");
        if (own != 0) ic.setImageResource(own); else IconManager.set(ic, "premium");
        box.addView(ic);
        box.addView(titleView("Talksy Premium для " + toName));
        box.addView(subView(pl.optInt("months") + " мес. · откроются все функции Premium"));
        final com.google.android.material.bottomsheet.BottomSheetDialog[] dlg = new com.google.android.material.bottomsheet.BottomSheetDialog[1];
        final String key = EconomyUi.newKey();
        final boolean[] busy = {false};
        Button go = EconomyUi.button(this, android.text.TextUtils.concat("Подарить за ", EconomyUi.price(this, pl.optLong("price"))), true);
        go.setOnClickListener(v -> {
            if (busy[0]) return;
            busy[0] = true;
            JSONObject b = new JSONObject();
            try { b.put("plan_id", pl.optString("id")); b.put("to_user_id", toId); b.put("idempotency_key", key); } catch (Exception ignored) {}
            api.economyPost("/economy/premium/gift", b, (ok, json, err) -> {
                busy[0] = false;
                if (!ok) { EconomyUi.toast(this, err); return; }
                dlg[0].dismiss();
                done("Premium подарен");
            });
        });
        box.addView(go, btnLp());
        dlg[0] = EconomyUi.sheet(this, box);
    }

    private void drawGrid() {
        gridBox.removeAllViews();
        List<JSONObject> shown = new ArrayList<>();
        for (int i = 0; i < gifts.length(); i++) {
            JSONObject g = gifts.optJSONObject(i);
            if (onlyLimited && g.isNull("supply_limit")) continue;
            shown.add(g);
        }
        if (shown.isEmpty()) {
            TextView e = EconomyUi.text(this, "Здесь пока пусто", 13, R.color.text3, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, EconomyUi.dp(this, 24), 0, 0);
            gridBox.addView(e);
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                gridBox.addView(row, new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 128)));
            }
            row.addView(giftTile(shown.get(i)));
        }
        for (int k = shown.size() % 3; k != 0 && k < 3; k++)
            row.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f));
    }

    private View giftTile(JSONObject g) {
        boolean out = g.optBoolean("sold_out"), locked = g.optBoolean("locked");
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(EconomyUi.round(this, EconomyUi.color(this, R.color.bg3), 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(EconomyUi.dp(this, 4), EconomyUi.dp(this, 4), EconomyUi.dp(this, 4), EconomyUi.dp(this, 4));
        tile.setLayoutParams(lp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, EconomyUi.dp(this, 12), 0, EconomyUi.dp(this, 8));
        ImageView img = EconomyUi.imageBox(this, 64);
        img.setLayoutParams(new LinearLayout.LayoutParams(EconomyUi.dp(this, 64), EconomyUi.dp(this, 64)));
        EconomyUi.giftImage(this, img, api, g.optString("id"), g.optString("image_url"));
        col.addView(img);
        TextView price = out ? EconomyUi.text(this, "Распродано", 11, R.color.text2, true)
                : EconomyUi.pricePill(this, g.optLong("price"));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = EconomyUi.dp(this, 8);
        col.addView(price, plp);
        tile.addView(col, new FrameLayout.LayoutParams(-1, -1));
        if (!g.isNull("supply_limit")) {
            TextView rib = EconomyUi.text(this, "ЛИМИТ", 9, R.color.text1, true);
            rib.setTextColor(android.graphics.Color.WHITE);
            rib.setPadding(EconomyUi.dp(this, 6), EconomyUi.dp(this, 2), EconomyUi.dp(this, 6), EconomyUi.dp(this, 2));
            rib.setBackground(EconomyUi.round(this, 0xFF22C55E, 6));
            tile.addView(rib, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        }
        if (out || locked) col.setAlpha(0.45f);
        tile.setOnClickListener(v -> {
            if (out) { EconomyUi.toast(this, "Тираж закончился"); return; }
            if (locked) { PremiumGate.show(this, "Этот подарок"); return; }
            confirmGift(g);
        });
        AnimUtils.pressScale(tile);
        return tile;
    }

    private void confirmGift(JSONObject g) {
        final int[] qty = {1};
        LinearLayout box = sheetBox();
        ImageView img = EconomyUi.imageBox(this, 130);
        img.setLayoutParams(new LinearLayout.LayoutParams(EconomyUi.dp(this, 130), EconomyUi.dp(this, 130)));
        EconomyUi.giftImage(this, img, api, g.optString("id"), g.optString("image_url"));
        box.addView(img);
        box.addView(titleView(g.optString("name")));
        TextView rar = subView(EconomyUi.rarityName(g.optString("rarity")) + (g.optString("description").isEmpty() ? "" : "  ·  " + g.optString("description")));
        rar.setTextColor(EconomyUi.rarityColor(g.optString("rarity")));
        box.addView(rar);
        if (!g.isNull("supply_limit"))
            box.addView(subView("Выпущено " + g.optInt("minted") + " из " + g.optInt("supply_limit")));

        LinearLayout q = EconomyUi.row(this);
        q.setGravity(Gravity.CENTER);
        Button minus = EconomyUi.button(this, "−", false), plus = EconomyUi.button(this, "+", false);
        TextView qv = EconomyUi.text(this, "1", 18, R.color.text1, true);
        qv.setPadding(EconomyUi.dp(this, 20), 0, EconomyUi.dp(this, 20), 0);
        q.addView(minus, new LinearLayout.LayoutParams(EconomyUi.dp(this, 44), EconomyUi.dp(this, 44)));
        q.addView(qv);
        q.addView(plus, new LinearLayout.LayoutParams(EconomyUi.dp(this, 44), EconomyUi.dp(this, 44)));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-2, -2);
        qlp.topMargin = EconomyUi.dp(this, 12);
        box.addView(q, qlp);

        EditText msg = new EditText(this);
        msg.setHint("Добавить сообщение");
        msg.setTextSize(14);
        msg.setMaxLines(2);
        msg.setTextColor(EconomyUi.color(this, R.color.text1));
        msg.setHintTextColor(EconomyUi.color(this, R.color.text3));
        msg.setBackground(EconomyUi.round(this, EconomyUi.color(this, R.color.bg3), 12));
        msg.setPadding(EconomyUi.dp(this, 14), EconomyUi.dp(this, 10), EconomyUi.dp(this, 14), EconomyUi.dp(this, 10));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = EconomyUi.dp(this, 12);
        box.addView(msg, mlp);

        final com.google.android.material.bottomsheet.BottomSheetDialog[] dlg = new com.google.android.material.bottomsheet.BottomSheetDialog[1];
        final String key = EconomyUi.newKey();
        final boolean[] busy = {false};
        Button go = EconomyUi.button(this, "", true);
        Runnable refresh = () -> {
            qv.setText(String.valueOf(qty[0]));
            go.setText(android.text.TextUtils.concat("Отправить за ", EconomyUi.price(this, g.optLong("price") * qty[0])));
        };
        refresh.run();
        minus.setOnClickListener(v -> { if (qty[0] > 1) { qty[0]--; refresh.run(); } });
        plus.setOnClickListener(v -> { if (qty[0] < EconomyUi.maxGiftQty) { qty[0]++; refresh.run(); } });
        go.setOnClickListener(v -> {
            if (busy[0]) return;
            busy[0] = true;
            JSONObject b = new JSONObject();
            try {
                b.put("gift_id", g.optString("id"));
                b.put("to_user_id", toId);
                b.put("quantity", qty[0]);
                b.put("message", msg.getText().toString().trim());
                b.put("idempotency_key", key);
            } catch (Exception ignored) {}
            api.economyPost("/economy/gifts/send", b, (ok, json, err) -> {
                busy[0] = false;
                if (!ok) {
                    if (!offerPay(err, "gift", g.optString("id"), toId, qty[0], () -> go.performClick())) EconomyUi.toast(this, err);
                    return;
                }
                dlg[0].dismiss();
                done("Подарок отправлен");
            });
        });
        box.addView(go, btnLp());
        dlg[0] = EconomyUi.sheet(this, box);
    }

    private void done(String text) {
        EconomyUi.toast(this, text);
        setResult(RESULT_OK);
        finish();
        AnimUtils.back(this);
    }

    private LinearLayout sheetBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = EconomyUi.dp(this, 20);
        box.setPadding(p, EconomyUi.dp(this, 24), p, p);
        return box;
    }

    private TextView titleView(String t) {
        TextView v = EconomyUi.text(this, t, 20, R.color.text1, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, EconomyUi.dp(this, 8), 0, EconomyUi.dp(this, 4));
        return v;
    }

    private TextView subView(String t) {
        TextView v = EconomyUi.text(this, t, 13, R.color.text2, false);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 50));
        lp.topMargin = EconomyUi.dp(this, 16);
        return lp;
    }
}
