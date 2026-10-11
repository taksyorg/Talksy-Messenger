package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.Accounts;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.net.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GiftFlow {
    private GiftFlow() {}

    static final Map<Long, JSONObject> CACHE = new HashMap<>();

    public static void openShop(Activity a, long toUserId, String toName) {
        Intent i = new Intent(a, GiftShopActivity.class);
        i.putExtra(GiftShopActivity.EXTRA_USER_ID, toUserId);
        i.putExtra(GiftShopActivity.EXTRA_USER_NAME, toName);
        a.startActivity(i);
        AnimUtils.forward(a);
    }

    public static void attachSection(Activity a, ApiClient api, LinearLayout holder, long userId, boolean mine) {
        holder.removeAllViews();
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_panel);
        int pad = EconomyUi.dp(a, 16);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout head = EconomyUi.row(a);
        ImageView ic = EconomyUi.imageBox(a, 29);
        IconManager.set(ic, "gift");
        head.addView(ic);
        TextView title = EconomyUi.text(a, "Подарки", 15, R.color.text1, true);
        title.setPadding(EconomyUi.dp(a, 10), 0, 0, 0);
        head.addView(title);
        card.addView(head);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(a);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = EconomyUi.row(a);
        chipsScroll.addView(chips);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = EconomyUi.dp(a, 10);
        card.addView(chipsScroll, clp);

        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = EconomyUi.dp(a, 10);
        card.addView(grid, glp);

        TextView note = EconomyUi.text(a, "Пока нет подарков", 13, R.color.text2, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, EconomyUi.dp(a, 14), 0, EconomyUi.dp(a, 4));
        card.addView(note);
        holder.addView(card);

        api.economyGet("/economy/users/" + userId + "/gifts", (ok, json, err) -> {
            JSONArray arr = ok ? json.optJSONArray("gifts") : null;
            if (arr == null || arr.length() == 0) { if (!ok) note.setText(err != null ? err : "Ошибка"); chipsScroll.setVisibility(View.GONE); return; }
            final List<JSONObject> items = new ArrayList<>();
            final List<String> names = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                items.add(it);
                String n = it.optJSONObject("gift").optString("name");
                if (!names.contains(n)) names.add(n);
            }
            note.setText(mine ? "Нажмите на подарок, чтобы продать его или изменить настройки показа." : "");
            note.setVisibility(mine ? View.VISIBLE : View.GONE);
            final String[] filter = {null};
            final Runnable[] redraw = new Runnable[1];
            redraw[0] = () -> {
                chips.removeAllViews();
                chips.addView(chipView(a, "Все подарки", filter[0] == null, () -> { filter[0] = null; redraw[0].run(); }));
                if (names.size() > 1)
                    for (String n : names) chips.addView(chipView(a, n, n.equals(filter[0]), () -> { filter[0] = n; redraw[0].run(); }));
                chipsScroll.setVisibility(names.size() > 1 ? View.VISIBLE : View.GONE);
                drawGrid(a, api, grid, items, filter[0], mine, () -> attachSection(a, api, holder, userId, mine));
            };
            redraw[0].run();
        });
    }

    private static TextView chipView(Activity a, String label, boolean on, Runnable click) {
        TextView t = EconomyUi.chip(a, label, on);
        t.setOnClickListener(v -> click.run());
        return t;
    }

    private static void drawGrid(Activity a, ApiClient api, LinearLayout grid, List<JSONObject> items, String filter, boolean mine, Runnable onChanged) {
        grid.removeAllViews();
        List<JSONObject> shown = new ArrayList<>();
        for (JSONObject it : items)
            if (filter == null || filter.equals(it.optJSONObject("gift").optString("name"))) shown.add(it);
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(-1, EconomyUi.dp(a, 112)));
            }
            JSONObject it = shown.get(i);
            JSONObject g = it.optJSONObject("gift");
            FrameLayout tile = new FrameLayout(a);
            tile.setBackground(EconomyUi.round(a, EconomyUi.color(a, R.color.bg3), 14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(EconomyUi.dp(a, 3), EconomyUi.dp(a, 3), EconomyUi.dp(a, 3), EconomyUi.dp(a, 3));
            tile.setLayoutParams(lp);
            ImageView img = EconomyUi.imageBox(a, 72);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(EconomyUi.dp(a, 72), EconomyUi.dp(a, 72), Gravity.CENTER);
            img.setLayoutParams(ilp);
            EconomyUi.giftImage(a, img, api, g.optString("id"), g.optString("image_url"));
            tile.addView(img);
            if (it.optBoolean("hidden")) {
                img.setAlpha(0.45f);
                ImageView eye = EconomyUi.imageBox(a, 28);
                IconManager.set(eye, "hide");
                tile.addView(eye, new FrameLayout.LayoutParams(EconomyUi.dp(a, 28), EconomyUi.dp(a, 28), Gravity.CENTER));
            }
            tile.setOnClickListener(v -> showItemSheet(a, api, it, mine, onChanged));
            AnimUtils.pressScale(tile);
            row.addView(tile);
        }
        if (row != null) {
            for (int k = shown.size() % 3; k != 0 && k < 3; k++) {
                View pad = new View(a);
                row.addView(pad, new LinearLayout.LayoutParams(0, -1, 1f));
            }
        }
    }

    public static void showItemSheet(Activity a, ApiClient api, JSONObject it, boolean mine, Runnable onChanged) {
        JSONObject g = it.optJSONObject("gift");
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = EconomyUi.dp(a, 20);
        box.setPadding(p, EconomyUi.dp(a, 24), p, p);

        ImageView img = EconomyUi.imageBox(a, 140);
        img.setLayoutParams(new LinearLayout.LayoutParams(EconomyUi.dp(a, 140), EconomyUi.dp(a, 140)));
        EconomyUi.giftImage(a, img, api, g.optString("id"), g.optString("image_url"));
        box.addView(img);

        TextView title = EconomyUi.text(a, mine ? "Подарок Вам" : g.optString("name"), 20, R.color.text1, true);
        title.setPadding(0, EconomyUi.dp(a, 8), 0, EconomyUi.dp(a, 4));
        box.addView(title);
        TextView sub = EconomyUi.text(a, mine ? "Вы можете скрыть этот подарок из профиля."
                : EconomyUi.rarityName(g.optString("rarity")) + "  ·  #" + it.optInt("serial"), 13, R.color.text2, false);
        sub.setGravity(Gravity.CENTER);
        if (!mine) sub.setTextColor(EconomyUi.rarityColor(g.optString("rarity")));
        box.addView(sub);

        LinearLayout table = EconomyUi.table(a);
        String from = it.optString("from_name");
        LinearLayout fromRow = EconomyUi.row(a);
        fromRow.addView(EconomyUi.text(a, from.isEmpty() ? "—" : from, 14, R.color.text1, true));
        if (mine && it.optLong("from_user_id") > 0) {
            TextView chip = EconomyUi.text(a, "отправить подарок", 11, R.color.accent, true);
            chip.setPadding(EconomyUi.dp(a, 8), EconomyUi.dp(a, 2), EconomyUi.dp(a, 8), EconomyUi.dp(a, 2));
            chip.setBackground(EconomyUi.round(a, EconomyUi.color(a, R.color.bg4), 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(EconomyUi.dp(a, 8));
            fromRow.addView(chip, lp);
            chip.setOnClickListener(v -> openShop(a, it.optLong("from_user_id"), from));
        }
        EconomyUi.tableRow(a, table, "От", "");
        ((LinearLayout) table.getChildAt(table.getChildCount() - 1)).removeViewAt(1);
        ((LinearLayout) table.getChildAt(table.getChildCount() - 1)).addView(fromRow);
        EconomyUi.tableRow(a, table, "Дата", EconomyUi.date(it.optDouble("received_at")));
        EconomyUi.tableRow(a, table, "Стоимость", EconomyUi.price(a, it.optLong("paid_price")));
        EconomyUi.tableRow(a, table, "Редкость", EconomyUi.rarityName(g.optString("rarity")));
        if (!it.optString("message").isEmpty()) EconomyUi.tableRow(a, table, "Сообщение", it.optString("message"));
        if (mine && it.optBoolean("can_sell")) EconomyUi.tableRow(a, table, "Выкуп", EconomyUi.price(a, it.optLong("sell_price")));
        box.addView(table);

        final com.google.android.material.bottomsheet.BottomSheetDialog[] dlg = new com.google.android.material.bottomsheet.BottomSheetDialog[1];

        if (mine) {
            final boolean[] hidden = {it.optBoolean("hidden")};
            TextView foot = new TextView(a);
            foot.setTextSize(12);
            foot.setGravity(Gravity.CENTER);
            foot.setPadding(0, EconomyUi.dp(a, 12), 0, EconomyUi.dp(a, 4));
            Runnable setFoot = () -> {
                foot.setTextColor(EconomyUi.color(a, R.color.text3));
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(
                        hidden[0] ? "Подарок скрыт из Вашего профиля. " : "Подарок виден в Вашем профиле. ");
                int st = sb.length();
                sb.append(hidden[0] ? "Показать" : "Скрыть");
                sb.setSpan(new android.text.style.ForegroundColorSpan(EconomyUi.color(a, R.color.accent)), st, sb.length(),
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                foot.setText(sb);
            };
            setFoot.run();
            foot.setOnClickListener(v -> {
                JSONObject body = new JSONObject();
                try { body.put("hidden", !hidden[0]); } catch (Exception ignored) {}
                api.economyPost("/economy/gift-items/" + it.optInt("item_id") + "/hide", body, (ok, json, err) -> {
                    if (!ok) { EconomyUi.toast(a, err); return; }
                    hidden[0] = json.optBoolean("hidden");
                    try { it.put("hidden", hidden[0]); } catch (Exception ignored) {}
                    setFoot.run();
                    EconomyUi.toast(a, hidden[0] ? "Подарок скрыт из Вашего профиля" : "Подарок виден в Вашем профиле");
                    if (onChanged != null) onChanged.run();
                });
            });
            box.addView(foot);
        }

        if (mine && it.optBoolean("can_sell")) {
            Button sell = EconomyUi.button(a, android.text.TextUtils.concat("Продать за ", EconomyUi.price(a, it.optLong("sell_price"))), false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(a, 48));
            lp.topMargin = EconomyUi.dp(a, 10);
            sell.setOnClickListener(v -> confirmSell(a, api, it, () -> { dlg[0].dismiss(); if (onChanged != null) onChanged.run(); }));
            box.addView(sell, lp);
        }
        Button ok = EconomyUi.button(a, "OK", true);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(a, 48));
        olp.topMargin = EconomyUi.dp(a, 10);
        ok.setOnClickListener(v -> dlg[0].dismiss());
        box.addView(ok, olp);

        dlg[0] = EconomyUi.sheet(a, box);
    }

    private static void confirmSell(Activity a, ApiClient api, JSONObject it, Runnable onDone) {
        final String key = EconomyUi.newKey();
        final boolean[] busy = {false};
        new TDialog.Builder(a).setTitle("Продать подарок?")
                .setMessage("Вы получите " + EconomyUi.amount(it.optLong("sell_price")) + ". Talksy выкупит подарок, отменить нельзя.")
                .setPositiveButton("Продать", (d, w) -> {
                    if (busy[0]) return;
                    busy[0] = true;
                    JSONObject body = new JSONObject();
                    try { body.put("idempotency_key", key); } catch (Exception ignored) {}
                    api.economyPost("/economy/gift-items/" + it.optInt("item_id") + "/sell", body, (ok, json, err) -> {
                        busy[0] = false;
                        if (ok) { EconomyUi.toast(a, "+" + EconomyUi.amount(json.optLong("received"))); if (onDone != null) onDone.run(); }
                        else EconomyUi.toast(a, err);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    public static void fetchItem(ApiClient api, long itemId, ApiClient.JsonCallback cb) {
        JSONObject c = CACHE.get(itemId);
        if (c != null) { cb.onResult(true, c, null); return; }
        api.economyGet("/economy/gift-items/" + itemId, (ok, json, err) -> {
            if (ok && json != null && json.optJSONObject("item") != null) {
                JSONObject item = json.optJSONObject("item");
                CACHE.put(itemId, item);
                cb.onResult(true, item, null);
            } else cb.onResult(false, null, err);
        });
    }

    public static void openFromChat(Activity a, ApiClient api, long itemId) {
        CACHE.remove(itemId);
        fetchItem(api, itemId, (ok, item, err) -> {
            if (!ok) { EconomyUi.toast(a, err); return; }
            boolean mine = item.optLong("owner_id") == Accounts.currentUserId(a);
            showItemSheet(a, api, item, mine, null);
        });
    }

    public static View floatingGiftButton(Activity a, long userId, String name) {
        TextView t = new TextView(a);
        t.setText(IconManager.prefix(a, "gift", "Отправить подарок " + (name == null ? "" : name), Color.WHITE, 18));
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(EconomyUi.dp(a, 22), EconomyUi.dp(a, 13), EconomyUi.dp(a, 22), EconomyUi.dp(a, 13));
        t.setBackground(EconomyUi.round(a, EconomyUi.color(a, R.color.accent), 26));
        t.setElevation(EconomyUi.dp(a, 6));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = EconomyUi.dp(a, 20);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> openShop(a, userId, name));
        AnimUtils.pressScale(t);
        return t;
    }
}
