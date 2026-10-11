package com.talksy.messenger.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.net.ApiClient;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

public final class EconomyUi {
    private EconomyUi() {}

    public static String symbol = "";
    public static String code = "FTR";
    public static String name = "Feathers";
    public static boolean devMode = false;
    public static boolean paymentsEnabled = false;
    public static int[] topupPackages = new int[0];
    public static int maxGiftQty = 20;

    public interface Done { void run(); }

    public static void loadConfig(ApiClient api, Done done) {
        api.economyGet("/economy/config", (ok, json, err) -> {
            if (ok && json != null) {
                JSONObject c = json.optJSONObject("currency");
                if (c != null) {
                    code = c.optString("code", code);
                    name = c.optString("name", name);
                }
                devMode = json.optBoolean("dev_mode");
                paymentsEnabled = json.optBoolean("payments_enabled");
                maxGiftQty = json.optInt("max_gift_quantity", 20);
                org.json.JSONArray pk = json.optJSONArray("topup_packages");
                topupPackages = new int[pk == null ? 0 : pk.length()];
                for (int i = 0; i < topupPackages.length; i++) topupPackages[i] = pk.optInt(i);
            }
            if (done != null) done.run();
        });
    }

    public static String amount(long v) {
        String s = String.format(java.util.Locale.US, "%,d", Math.abs(v)).replace(',', '\u202F');
        return (v < 0 ? "−" : "") + s + " " + code;
    }

    public static String signed(long v) {
        return (v > 0 ? "+" : "") + amount(v);
    }

    public static String newKey() { return UUID.randomUUID().toString().replace("-", ""); }

    public static String date(long seconds) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(seconds * 1000L));
    }

    public static String date(double seconds) { return date((long) seconds); }

    public static int rarityColor(String r) {
        if (r == null) return Color.parseColor("#9CA3AF");
        switch (r) {
            case "rare": return Color.parseColor("#3B82F6");
            case "epic": return Color.parseColor("#A855F7");
            case "legendary": return Color.parseColor("#F59E0B");
            case "mythic": return Color.parseColor("#EF4444");
            default: return Color.parseColor("#9CA3AF");
        }
    }

    public static String rarityName(String r) {
        if (r == null) return "Common";
        return r.substring(0, 1).toUpperCase() + r.substring(1);
    }

    public static String txTitle(String type) {
        switch (type) {
            case "GIFT_SENT": return "Подарок отправлен";
            case "GIFT_RECEIVED": return "Подарок получен";
            case "GIFT_SOLD": return "Подарок продан";
            case "GIFT_PURCHASED": return "Подарок куплен";
            case "PURCHASE_TKN": return "Пополнение";
            case "DONATION": return "Поддержка автора";
            case "DONATION_RECEIVED": return "Донат получен";
            case "PAID_REACTION": return "Платная реакция";
            case "PREMIUM_PURCHASE": return "Talksy Premium";
            case "THEME_PURCHASE": return "Тема";
            case "STORE_PURCHASE": return "Покупка в магазине";
            case "TRANSFER_SENT": return "Перевод отправлен";
            case "TRANSFER_RECEIVED": return "Перевод получен";
            case "NUMBER_PURCHASE": return "Покупка номера";
            case "NUMBER_SOLD": return "Номер продан";
            case "REFUND": return "Возврат";
            case "ADMIN_ADJUSTMENT": return "Корректировка";
            default: return type;
        }
    }

    public static String txIcon(String type) {
        switch (type) {
            case "GIFT_SENT": case "GIFT_RECEIVED": case "GIFT_SOLD": case "GIFT_PURCHASED": return "gift";
            case "PREMIUM_PURCHASE": return "premium";
            case "THEME_PURCHASE": return "themes";
            case "STORE_PURCHASE": case "NUMBER_PURCHASE": case "NUMBER_SOLD": return "store";
            case "DONATION": case "DONATION_RECEIVED": return "channel";
            case "PAID_REACTION": return "reaction";
            default: return "tsc";
        }
    }

    private static final java.util.Map<String, String> GIFT_URLS = new java.util.HashMap<>();
    private static boolean catalogRequested = false;

    public static int resId(Context c, String name) {
        return c.getResources().getIdentifier(name, "drawable", c.getPackageName());
    }

    public static CharSequence price(Context c, long v, int sizeDp) {
        int id = resId(c, "eco_currency");
        if (id == 0) return amount(v);
        String num = String.format(java.util.Locale.US, "%,d", Math.abs(v)).replace(',', '\u202F');
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder((v < 0 ? "−" : "") + num + " ");
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(c, id);
        if (d != null) {
            int px = dp(c, sizeDp);
            d.setBounds(0, 0, px, px);
            int start = sb.length();
            sb.append("\u00A0");
            sb.setSpan(new android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BOTTOM), start, start + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    public static CharSequence price(Context c, long v) { return price(c, v, 16); }

    public static CharSequence signedPrice(Context c, long v) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(v > 0 ? "+" : "");
        sb.append(price(c, v));
        return sb;
    }

    public static void giftImage(Context c, android.widget.ImageView iv, ApiClient api, String giftId, String imageUrl) {
        iv.setTag(giftId);
        int id = resId(c, "eco_gift_" + giftId);
        if (id != 0) { iv.clearColorFilter(); iv.setImageResource(id); return; }
        String url = imageUrl != null && !imageUrl.isEmpty() ? imageUrl : GIFT_URLS.get(giftId);
        com.talksy.messenger.data.IconManager.set(iv, "gift");
        if (url != null && !url.isEmpty()) {
            com.talksy.messenger.net.AvatarLoader.loadRaw(api.getBaseUrl() + url, 300, bmp -> {
                if (giftId.equals(iv.getTag())) { iv.clearColorFilter(); iv.setImageBitmap(bmp); }
            });
        } else if (!catalogRequested && api != null) {
            catalogRequested = true;
            api.economyGet("/economy/gifts", (ok, json, err) -> {
                if (!ok || json == null) { catalogRequested = false; return; }
                org.json.JSONArray a = json.optJSONArray("gifts");
                for (int i = 0; a != null && i < a.length(); i++) {
                    JSONObject g = a.optJSONObject(i);
                    GIFT_URLS.put(g.optString("id"), g.optString("image_url"));
                }
            });
        }
    }

    public static void storeImage(Context c, android.widget.ImageView iv, String itemId, String category) {
        int id = resId(c, "eco_store_" + itemId);
        if (id != 0) { iv.clearColorFilter(); iv.setImageResource(id); return; }
        String icon;
        switch (category == null ? "" : category) {
            case "themes": icon = "themes"; break;
            case "profile": icon = "contact_card"; break;
            case "reactions": icon = "reaction"; break;
            case "stickers": icon = "sticker"; break;
            default: icon = "verified";
        }
        com.talksy.messenger.data.IconManager.set(iv, icon);
    }

    public static android.widget.ImageView imageBox(Context c, int sizeDp) {
        android.widget.ImageView iv = new android.widget.ImageView(c);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        return iv;
    }

    public static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int res) { return ContextCompat.getColor(c, res); }

    public static TextView text(Context c, CharSequence t, int sp, int colorRes, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color(c, colorRes));
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    public static GradientDrawable round(Context c, int fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setBackground(round(c, color(c, R.color.bg2), 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        l.setLayoutParams(lp);
        return l;
    }

    public static Button button(Context c, CharSequence t, boolean primary) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : color(c, R.color.accent));
        b.setBackground(round(c, primary ? color(c, R.color.accent) : color(c, R.color.bg3), 12));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        return b;
    }

    public static com.google.android.material.bottomsheet.BottomSheetDialog sheet(android.app.Activity a, View content) {
        final com.google.android.material.bottomsheet.BottomSheetDialog d = new com.google.android.material.bottomsheet.BottomSheetDialog(a);
        final android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(content);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(a, R.color.bg2));
        float r = dp(a, 24);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sv.setBackground(g);
        d.setContentView(sv);
        d.setOnShowListener(x -> {
            View p = (View) sv.getParent();
            p.setBackgroundColor(0);
            com.google.android.material.bottomsheet.BottomSheetBehavior.from(p)
                    .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        });
        d.show();
        return d;
    }

    public static LinearLayout table(Context c) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(round(c, color(c, R.color.bg3), 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 14);
        t.setLayoutParams(lp);
        return t;
    }

    public static void tableRow(Context c, LinearLayout table, String key, CharSequence value) {
        if (table.getChildCount() > 0) {
            View div = new View(c);
            div.setBackgroundColor(color(c, R.color.bg4));
            table.addView(div, new LinearLayout.LayoutParams(-1, 1));
        }
        LinearLayout r = row(c);
        r.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        TextView k = text(c, key, 13, R.color.text2, false);
        k.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 96), -2));
        r.addView(k);
        r.addView(text(c, value, 14, R.color.text1, false));
        table.addView(r);
    }

    public static TextView pricePill(Context c, long v) {
        TextView t = text(c, price(c, v, 13), 12, R.color.text1, true);
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3));
        t.setBackground(round(c, color(c, R.color.bg4), 12));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static TextView chip(Context c, String label, boolean on) {
        TextView t = text(c, label, 13, on ? R.color.text1 : R.color.text2, true);
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        t.setBackground(round(c, on ? color(c, R.color.bg4) : 0, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(c, 6), 0);
        t.setLayoutParams(lp);
        return t;
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg != null ? msg : c.getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static View space(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, hDp)));
        return v;
    }
}
