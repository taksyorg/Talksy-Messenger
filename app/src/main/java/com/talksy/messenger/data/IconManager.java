package com.talksy.messenger.data;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public final class IconManager {
    private IconManager() {}

    public static final int DEFAULT_DP = 24;

    public static int tint(Context c) {
        boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        return night ? 0xFFFFFFFF : 0xFF000000;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static Drawable raw(Context c, String name) {
        int id = c.getResources().getIdentifier("ic_tk_" + name, "drawable", c.getPackageName());
        if (id == 0) return null;
        Drawable d = ContextCompat.getDrawable(c, id);
        return d == null ? null : d.mutate();
    }

    public static Drawable get(Context c, String name, int sizeDp, int color) {
        Drawable d = raw(c, name);
        if (d == null) d = new ColorDrawable(0);
        else d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        int px = dp(c, sizeDp);
        d.setBounds(0, 0, px, px);
        return d;
    }

    public static Drawable get(Context c, String name, int sizeDp) { return get(c, name, sizeDp, tint(c)); }
    public static Drawable get(Context c, String name) { return get(c, name, DEFAULT_DP, tint(c)); }

    public static void set(ImageView iv, String name) { set(iv, name, tint(iv.getContext())); }

    public static void set(ImageView iv, String name, int color) {
        iv.setImageDrawable(get(iv.getContext(), name, DEFAULT_DP, color));
        iv.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    public static void set(TextView tv, String name, int sizeDp) { set(tv, name, sizeDp, tint(tv.getContext())); }

    public static void set(TextView tv, String name, int sizeDp, int color) {

        tv.setCompoundDrawablesRelative(null, null, null, null);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setText(inline(tv.getContext(), name, sizeDp, color));
    }

    public static void clear(TextView tv) {
        tv.setCompoundDrawablesRelative(null, null, null, null);
    }

    public static CharSequence inline(Context c, String name, int sizeDp, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder("\u00A0");
        sb.setSpan(new CenteredImageSpan(get(c, name, sizeDp, color)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    public static SpannableStringBuilder prefix(Context c, String name, CharSequence text, int color, int sizeDp) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(inline(c, name, sizeDp, color)).append(' ').append(text == null ? "" : text);
        return sb;
    }
    public static SpannableStringBuilder prefix(Context c, String name, CharSequence text, int color) {
        return prefix(c, name, text, color, 16);
    }
    public static SpannableStringBuilder prefix(Context c, String name, CharSequence text) {
        return prefix(c, name, text, tint(c), 16);
    }

    public static SpannableStringBuilder suffix(Context c, String name, CharSequence text, int color, int sizeDp) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text == null ? "" : text);
        sb.append(' ').append(inline(c, name, sizeDp, color));
        return sb;
    }
    public static SpannableStringBuilder suffix(Context c, String name, CharSequence text) {
        return suffix(c, name, text, tint(c), 14);
    }

    private static final class CenteredImageSpan extends ImageSpan {
        CenteredImageSpan(Drawable d) { super(d); }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            Drawable d = getDrawable();
            int w = d.getBounds().width();
            if (fm != null) {
                Paint.FontMetricsInt pf = paint.getFontMetricsInt();
                int h = d.getBounds().height();
                int center = (pf.ascent + pf.descent) / 2;
                fm.ascent = Math.min(pf.ascent, center - h / 2);
                fm.top = fm.ascent;
                fm.descent = Math.max(pf.descent, center + h / 2);
                fm.bottom = fm.descent;
            }
            return w;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint paint) {
            Drawable d = getDrawable();
            Paint.FontMetricsInt pf = paint.getFontMetricsInt();
            int center = y + (pf.ascent + pf.descent) / 2;
            canvas.save();
            canvas.translate(x, center - d.getBounds().height() / 2f);
            d.draw(canvas);
            canvas.restore();
        }
    }
}
