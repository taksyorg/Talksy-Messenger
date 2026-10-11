package com.talksy.messenger.ui;

import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;

import org.json.JSONArray;
import org.json.JSONObject;

public class StoreActivity extends EconomyScreen {
    private static final String[][] CATS = {
            {"themes", "Themes"}, {"profile", "Profile"}, {"reactions", "Reactions"}, {"stickers", "Stickers"}};
    private String category = "themes";
    private LinearLayout list;

    @Override protected String screenTitle() { return "Магазин"; }

    @Override
    protected void onBuilt() {
        EconomyUi.loadConfig(api, null);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = EconomyUi.row(this);
        chips.setPadding(EconomyUi.dp(this, 10), EconomyUi.dp(this, 6), EconomyUi.dp(this, 10), EconomyUi.dp(this, 6));
        for (String[] c : CATS) {
            Button b = EconomyUi.button(this, c[1], c[0].equals(category));
            b.setOnClickListener(v -> { category = c[0]; rebuildChips(chips); load(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, EconomyUi.dp(this, 38));
            lp.setMargins(EconomyUi.dp(this, 4), 0, EconomyUi.dp(this, 4), 0);
            b.setLayoutParams(lp);
            chips.addView(b);
        }
        hs.addView(chips);
        content.addView(hs);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        load();
    }

    private void rebuildChips(LinearLayout chips) {
        for (int i = 0; i < chips.getChildCount(); i++) {
            Button b = (Button) chips.getChildAt(i);
            boolean on = CATS[i][0].equals(category);
            b.setTextColor(on ? android.graphics.Color.WHITE : EconomyUi.color(this, R.color.accent));
            b.setBackground(EconomyUi.round(this, on ? EconomyUi.color(this, R.color.accent) : EconomyUi.color(this, R.color.bg3), 12));
        }
    }

    private void load() {
        api.economyGet("/economy/store?category=" + category, (ok, json, err) -> {
            list.removeAllViews();
            if (!ok) { list.addView(EconomyUi.text(this, err != null ? err : "Ошибка", 14, R.color.err, false)); return; }
            JSONArray arr = json.optJSONArray("items");
            if (arr == null || arr.length() == 0) {
                TextView e = EconomyUi.text(this, "Скоро здесь появятся предметы", 13, R.color.text3, false);
                e.setPadding(EconomyUi.dp(this, 20), EconomyUi.dp(this, 20), 0, 0);
                list.addView(e);
                return;
            }
            for (int i = 0; i < arr.length(); i++) list.addView(itemView(arr.optJSONObject(i)));
        });
    }

    private View itemView(JSONObject it) {
        LinearLayout c = EconomyUi.card(this);
        LinearLayout r = EconomyUi.row(this);
        android.widget.ImageView em = EconomyUi.imageBox(this, 48);
        EconomyUi.storeImage(this, em, it.optString("id"), it.optString("category"));
        r.addView(em);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(EconomyUi.dp(this, 12), 0, 0, 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        mid.addView(EconomyUi.text(this, it.optString("name"), 15, R.color.text1, true));
        String sub = it.optString("description");
        if (!it.isNull("duration_days")) sub += "  ·  " + it.optInt("duration_days") + " дн.";
        mid.addView(EconomyUi.text(this, sub, 12, R.color.text2, false));
        r.addView(mid);

        boolean owned = it.optBoolean("owned"), locked = it.optBoolean("locked");
        CharSequence label;
        if (owned) label = "Есть";
        else if (locked) label = "Premium";
        else if (it.optBoolean("free")) label = "Бесплатно";
        else label = EconomyUi.price(this, it.optLong("price"));
        Button b = EconomyUi.button(this, label, !owned && !locked);
        b.setEnabled(!owned && !locked);
        b.setOnClickListener(v -> confirmBuy(it));
        r.addView(b, new LinearLayout.LayoutParams(-2, EconomyUi.dp(this, 40)));
        c.addView(r);
        return c;
    }

    private void confirmBuy(JSONObject it) {
        long price = it.optLong("price");
        final String key = EconomyUi.newKey();
        final boolean[] busy = {false};
        new TDialog.Builder(this).setTitle(it.optString("name"))
                .setMessage(price > 0 ? "Вы потратите " + EconomyUi.amount(price) : "Получить бесплатно?")
                .setPositiveButton(price > 0 ? "Купить" : "Получить", (d, w) -> {
                    if (busy[0]) return;
                    busy[0] = true;
                    JSONObject b = new JSONObject();
                    try { b.put("item_id", it.optString("id")); b.put("idempotency_key", key); } catch (Exception ignored) {}
                    api.economyPost("/economy/store/buy", b, (ok, json, err) -> {
                        busy[0] = false;
                        if (ok) { EconomyUi.toast(this, "Готово"); load(); } else EconomyUi.toast(this, err);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
