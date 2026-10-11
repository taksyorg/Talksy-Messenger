package com.talksy.messenger.ui;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.AnimUtils;
import com.talksy.messenger.data.IconManager;

import org.json.JSONArray;
import org.json.JSONObject;

public class PremiumActivity extends EconomyScreen {
    private JSONArray plans = new JSONArray();
    private int selected = -1;
    private boolean active;
    private TextView buy;
    private LinearLayout planBox;

    @Override protected String screenTitle() { return ""; }

    @Override
    protected void onBuilt() {
        bar.setVisibility(View.GONE);
        ImageView back = new ImageView(this);
        IconManager.set(back, "close");
        back.setPadding(EconomyUi.dp(this, 12), EconomyUi.dp(this, 12), EconomyUi.dp(this, 12), EconomyUi.dp(this, 12));
        back.setOnClickListener(v -> { finish(); AnimUtils.back(this); });
        root.addView(back, 0, new LinearLayout.LayoutParams(EconomyUi.dp(this, 48), EconomyUi.dp(this, 48)));

        buy = new TextView(this);
        buy.setGravity(Gravity.CENTER);
        buy.setTextColor(Color.WHITE);
        buy.setTextSize(15);
        buy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF6366F1, 0xFFA855F7});
        g.setCornerRadius(EconomyUi.dp(this, 26));
        buy.setBackground(g);
        buy.setOnClickListener(v -> confirm());
        AnimUtils.pressScale(buy);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 52));
        blp.setMargins(EconomyUi.dp(this, 14), EconomyUi.dp(this, 8), EconomyUi.dp(this, 14), EconomyUi.dp(this, 14));
        root.addView(buy, blp);

        EconomyUi.loadConfig(api, this::load);
    }

    private void load() {
        api.economyGet("/economy/premium", (ok, p, err) -> {
            content.removeAllViews();
            if (!ok) { content.addView(EconomyUi.text(this, err != null ? err : "Ошибка", 14, R.color.err, false)); return; }
            active = p.optBoolean("active");
            plans = p.optJSONArray("plans") == null ? new JSONArray() : p.optJSONArray("plans");
            if (selected < 0) selected = Math.min(plans.length() - 1, 3);

            ImageView crown = EconomyUi.imageBox(this, 120);
            crown.setLayoutParams(new LinearLayout.LayoutParams(EconomyUi.dp(this, 120), EconomyUi.dp(this, 120)));
            int own = EconomyUi.resId(this, "eco_premium");
            if (own != 0) { crown.clearColorFilter(); crown.setImageResource(own); } else IconManager.set(crown, "premium", 0xFFA855F7);
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.VERTICAL);
            top.setGravity(Gravity.CENTER_HORIZONTAL);
            top.addView(crown);
            TextView t = EconomyUi.text(this, "Talksy Premium", 24, R.color.text1, true);
            t.setPadding(0, EconomyUi.dp(this, 10), 0, EconomyUi.dp(this, 6));
            top.addView(t);
            TextView d = EconomyUi.text(this, active
                    ? "Ваш Premium активен до " + EconomyUi.date(p.optDouble("until"))
                    : "Больше свободы и дополнительные функции с подпиской Talksy Premium.", 14, R.color.text2, false);
            d.setGravity(Gravity.CENTER);
            d.setPadding(EconomyUi.dp(this, 30), 0, EconomyUi.dp(this, 30), EconomyUi.dp(this, 16));
            top.addView(d);
            content.addView(top);

            planBox = EconomyUi.card(this);
            planBox.setPadding(0, EconomyUi.dp(this, 6), 0, EconomyUi.dp(this, 6));
            content.addView(planBox);
            drawPlans();

            LinearLayout feats = EconomyUi.card(this);
            feats.setPadding(0, EconomyUi.dp(this, 4), 0, EconomyUi.dp(this, 4));
            JSONArray fs = p.optJSONArray("features");
            for (int i = 0; fs != null && i < fs.length(); i++) feats.addView(featureRow(fs.optJSONObject(i)));
            content.addView(feats);

            TextView foot = EconomyUi.text(this, "Личные сообщения, группы, звонки и файлы остаются бесплатными.", 12, R.color.text3, false);
            foot.setGravity(Gravity.CENTER);
            foot.setPadding(EconomyUi.dp(this, 24), EconomyUi.dp(this, 8), EconomyUi.dp(this, 24), 0);
            content.addView(foot);
            updateButton();
        });
    }

    private void drawPlans() {
        planBox.removeAllViews();
        for (int i = 0; i < plans.length(); i++) {
            final int idx = i;
            JSONObject pl = plans.optJSONObject(i);
            LinearLayout row = EconomyUi.row(this);
            row.setPadding(EconomyUi.dp(this, 14), EconomyUi.dp(this, 12), EconomyUi.dp(this, 14), EconomyUi.dp(this, 12));

            View radio = new View(this);
            boolean on = i == selected;
            GradientDrawable rd = new GradientDrawable();
            rd.setShape(GradientDrawable.OVAL);
            if (on) rd.setColor(0xFF6366F1); else { rd.setColor(0); rd.setStroke(EconomyUi.dp(this, 2), EconomyUi.color(this, R.color.text3)); }
            radio.setBackground(rd);
            row.addView(radio, new LinearLayout.LayoutParams(EconomyUi.dp(this, 22), EconomyUi.dp(this, 22)));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(EconomyUi.dp(this, 14), 0, 0, 0);
            LinearLayout line1 = EconomyUi.row(this);
            line1.addView(EconomyUi.text(this, pl.optString("name"), 15, R.color.text1, true));
            int disc = pl.optInt("discount_percent");
            if (disc > 0) {
                TextView badge = EconomyUi.text(this, "−" + disc + "%", 11, R.color.text1, true);
                badge.setTextColor(Color.WHITE);
                badge.setPadding(EconomyUi.dp(this, 6), EconomyUi.dp(this, 1), EconomyUi.dp(this, 6), EconomyUi.dp(this, 1));
                badge.setBackground(EconomyUi.round(this, 0xFF6366F1, 8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMarginStart(EconomyUi.dp(this, 8));
                line1.addView(badge, lp);
            }
            mid.addView(line1);
            if (disc > 0) {
                TextView old = EconomyUi.text(this, EconomyUi.amount(pl.optLong("old_price")) + " за период", 12, R.color.text3, false);
                old.setPaintFlags(old.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                mid.addView(old);
            }
            row.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.addView(EconomyUi.text(this, android.text.TextUtils.concat(EconomyUi.price(this, pl.optLong("per_month"), 13), " в месяц"), 13, R.color.text1, false));
            if (pl.optInt("months") > 1)
                right.addView(EconomyUi.text(this, android.text.TextUtils.concat(EconomyUi.price(this, pl.optLong("price"), 12), " за период"), 11, R.color.text2, false));
            row.addView(right);

            row.setOnClickListener(v -> { selected = idx; drawPlans(); updateButton(); });
            planBox.addView(row);
        }
    }

    private void updateButton() {
        JSONObject pl = selected >= 0 && selected < plans.length() ? plans.optJSONObject(selected) : null;
        if (pl == null) { buy.setText(""); return; }
        buy.setText(android.text.TextUtils.concat(active ? "Продлить за " : "Подключить за ", EconomyUi.price(this, pl.optLong("price"), 16)));
    }

    private View featureRow(JSONObject f) {
        LinearLayout r = EconomyUi.row(this);
        r.setPadding(EconomyUi.dp(this, 14), EconomyUi.dp(this, 12), EconomyUi.dp(this, 14), EconomyUi.dp(this, 12));
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(EconomyUi.round(this, featureColor(f.optString("id")), 10));
        ImageView ic = new ImageView(this);
        IconManager.set(ic, featureIcon(f.optString("id")), Color.WHITE);
        ic.setPadding(EconomyUi.dp(this, 7), EconomyUi.dp(this, 7), EconomyUi.dp(this, 7), EconomyUi.dp(this, 7));
        badge.addView(ic, new FrameLayout.LayoutParams(-1, -1));
        r.addView(badge, new LinearLayout.LayoutParams(EconomyUi.dp(this, 36), EconomyUi.dp(this, 36)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(EconomyUi.dp(this, 14), 0, EconomyUi.dp(this, 8), 0);
        t.addView(EconomyUi.text(this, f.optString("title"), 15, R.color.text1, true));
        t.addView(EconomyUi.text(this, f.optString("description"), 12, R.color.text2, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView chev = new ImageView(this);
        IconManager.set(chev, "forward", EconomyUi.color(this, R.color.text3));
        r.addView(chev, new LinearLayout.LayoutParams(EconomyUi.dp(this, 18), EconomyUi.dp(this, 18)));
        r.setOnClickListener(v -> showFeature(f));
        return r;
    }

    private void showFeature(JSONObject f) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = EconomyUi.dp(this, 22);
        box.setPadding(p, EconomyUi.dp(this, 28), p, p);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(EconomyUi.round(this, featureColor(f.optString("id")), 22));
        ImageView ic = new ImageView(this);
        IconManager.set(ic, featureIcon(f.optString("id")), Color.WHITE);
        ic.setPadding(EconomyUi.dp(this, 18), EconomyUi.dp(this, 18), EconomyUi.dp(this, 18), EconomyUi.dp(this, 18));
        badge.addView(ic, new FrameLayout.LayoutParams(-1, -1));
        box.addView(badge, new LinearLayout.LayoutParams(EconomyUi.dp(this, 84), EconomyUi.dp(this, 84)));
        TextView title = EconomyUi.text(this, f.optString("title"), 20, R.color.text1, true);
        title.setPadding(0, EconomyUi.dp(this, 16), 0, EconomyUi.dp(this, 6));
        box.addView(title);
        TextView d = EconomyUi.text(this, f.optString("description"), 14, R.color.text2, false);
        d.setGravity(Gravity.CENTER);
        box.addView(d);
        final com.google.android.material.bottomsheet.BottomSheetDialog[] dlg = new com.google.android.material.bottomsheet.BottomSheetDialog[1];
        TextView go = new TextView(this);
        go.setGravity(Gravity.CENTER);
        go.setTextColor(Color.WHITE);
        go.setTextSize(15);
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        go.setText(buy.getText());
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF6366F1, 0xFFA855F7});
        g.setCornerRadius(EconomyUi.dp(this, 26));
        go.setBackground(g);
        go.setOnClickListener(v -> { dlg[0].dismiss(); confirm(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, EconomyUi.dp(this, 52));
        lp.topMargin = EconomyUi.dp(this, 22);
        box.addView(go, lp);
        dlg[0] = EconomyUi.sheet(this, box);
    }

    private static String featureIcon(String id) {
        switch (id) {
            case "scheduled": return "timer";
            case "disappearing": return "history";
            case "badge": return "premium_star";
            case "themes": return "themes";
            case "gifts": return "gift";
            case "reactions": return "reaction";
            default: return "premium";
        }
    }

    private static int featureColor(String id) {
        switch (id) {
            case "scheduled": return 0xFF3B82F6;
            case "disappearing": return 0xFFEF4444;
            case "badge": return 0xFFA855F7;
            case "themes": return 0xFF22C55E;
            case "gifts": return 0xFFF59E0B;
            case "reactions": return 0xFFEC4899;
            default: return 0xFF6366F1;
        }
    }

    private void confirm() {
        JSONObject plan = selected >= 0 && selected < plans.length() ? plans.optJSONObject(selected) : null;
        if (plan == null) return;
        final String key = EconomyUi.newKey();
        final boolean[] busy = {false};
        new TDialog.Builder(this).setTitle("Talksy Premium · " + plan.optString("name"))
                .setMessage("Вы потратите " + EconomyUi.amount(plan.optLong("price")))
                .setPositiveButton("Подтвердить", (d, w) -> {
                    if (busy[0]) return;
                    busy[0] = true;
                    JSONObject b = new JSONObject();
                    try { b.put("plan_id", plan.optString("id")); b.put("idempotency_key", key); } catch (Exception ignored) {}
                    api.economyPost("/economy/premium/buy", b, (ok, json, err) -> {
                        busy[0] = false;
                        if (ok) { EconomyUi.toast(this, "Premium активен"); load(); }
                        else if (!offerPay(err, "premium", plan.optString("id"), 0, 0, this::confirm)) EconomyUi.toast(this, err);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
