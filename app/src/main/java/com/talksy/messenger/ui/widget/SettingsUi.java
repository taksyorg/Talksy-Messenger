package com.talksy.messenger.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.data.IconManager;

public final class SettingsUi {
    private SettingsUi() {}

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static final class Row {
        public LinearLayout view;
        public TextView title, sub, value;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.bg_panel);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 14);
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView header(Context c, CharSequence text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(c, R.color.accent));
        t.setTextSize(13);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 6), dp(c, 6), 0, dp(c, 8));
        t.setLayoutParams(lp);
        return t;
    }

    public static TextView note(Context c, CharSequence text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(c, R.color.text3));
        t.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(c, 8), 0, dp(c, 8), dp(c, 14));
        t.setLayoutParams(lp);
        return t;
    }

    public static void divider(LinearLayout card) {
        Context c = card.getContext();
        View v = new View(c);
        v.setBackgroundColor(ContextCompat.getColor(c, R.color.border1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        lp.setMarginStart(dp(c, 56));
        card.addView(v, lp);
    }

    public static Row row(Context c, String icon, CharSequence title, CharSequence subtitle, CharSequence value, View.OnClickListener click) {
        Row r = new Row();
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        l.setMinimumHeight(dp(c, 56));
        l.setBackgroundResource(R.drawable.ripple_item);
        if (icon != null) {
            ImageView iv = new ImageView(c);
            IconManager.set(iv, icon);
            l.addView(iv, new LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)));
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMarginStart(icon != null ? dp(c, 16) : 0);
        l.addView(col, cp);
        r.title = new TextView(c);
        r.title.setText(title); r.title.setTextColor(ContextCompat.getColor(c, R.color.text1)); r.title.setTextSize(15);
        col.addView(r.title);
        r.sub = new TextView(c);
        r.sub.setTextColor(ContextCompat.getColor(c, R.color.text3)); r.sub.setTextSize(12);
        if (subtitle == null || subtitle.length() == 0) r.sub.setVisibility(View.GONE); else r.sub.setText(subtitle);
        col.addView(r.sub);
        r.value = new TextView(c);
        r.value.setTextColor(ContextCompat.getColor(c, R.color.accent)); r.value.setTextSize(14);
        r.value.setGravity(Gravity.END);
        if (value != null) r.value.setText(value);
        l.addView(r.value);
        if (click != null) l.setOnClickListener(click);
        r.view = l;
        return r;
    }

    public static Row styledRow(Context c, String icon, CharSequence title, CharSequence subtitle, View trailing, View.OnClickListener click) {
        Row r = new Row();
        LinearLayout l = new LinearLayout(c, null, 0, R.style.SettingsRow);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        l.setBackgroundResource(R.drawable.ripple_item);
        l.setClickable(click != null);
        ImageView iv = new ImageView(c, null, 0, R.style.SettingsRowIcon);
        iv.setPadding(dp(c, 7), dp(c, 7), dp(c, 7), dp(c, 7));
        IconManager.set(iv, icon, ContextCompat.getColor(c, R.color.text1));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32));
        ip.setMarginEnd(dp(c, 12));
        l.addView(iv, ip);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        l.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.title = new TextView(c);
        r.title.setText(title); r.title.setTextColor(ContextCompat.getColor(c, R.color.text1)); r.title.setTextSize(14);
        col.addView(r.title);
        r.sub = new TextView(c);
        r.sub.setTextColor(ContextCompat.getColor(c, R.color.text2)); r.sub.setTextSize(11);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(c, 2);
        if (subtitle == null || subtitle.length() == 0) r.sub.setVisibility(View.GONE); else r.sub.setText(subtitle);
        col.addView(r.sub, sp);
        if (trailing == null) {
            TextView chev = new TextView(c);
            chev.setText("›"); chev.setTextColor(ContextCompat.getColor(c, R.color.text3)); chev.setTextSize(20);
            trailing = chev;
        }
        l.addView(trailing);
        if (click != null) l.setOnClickListener(click);
        r.view = l;
        return r;
    }

    public static View colorDot(Context c, int color) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL); g.setColor(color);
        v.setBackground(g);
        return v;
    }

    public static String bytes(long b) {
        if (b < 1024) return b + " Б";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f КБ", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f МБ", mb);
        return String.format(java.util.Locale.getDefault(), "%.2f ГБ", mb / 1024.0);
    }

    public static final int[] PALETTE = {0xFF3B82F6, 0xFF22C55E, 0xFF8B5CF6, 0xFFF97316, 0xFFEAB308, 0xFF06B6D4, 0xFFEC4899};
    public static int white() { return Color.WHITE; }
}
