package com.talksy.messenger.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;

public final class TToast {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int TAG_KEY = R.id.txt_avatar;

    private final Context ctx;
    private final CharSequence text;
    private final int duration;

    private TToast(Context ctx, CharSequence text, int duration) {
        this.ctx = ctx;
        this.text = text;
        this.duration = duration;
    }

    public static TToast makeText(Context ctx, CharSequence text, int duration) {
        return new TToast(ctx, text, duration);
    }

    public static TToast makeText(Context ctx, int resId, int duration) {
        return new TToast(ctx, ctx.getString(resId), duration);
    }

    private static Activity findActivity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    public void show() {
        final Activity a = findActivity(ctx);
        if (a == null || a.isFinishing() || a.isDestroyed()) {
            Toast.makeText(ctx.getApplicationContext(), text, duration).show();
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(this::show);
            return;
        }
        final ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) {
            Toast.makeText(ctx.getApplicationContext(), text, duration).show();
            return;
        }
        final float dp = a.getResources().getDisplayMetrics().density;

        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View v = content.getChildAt(i);
            if (v.getTag(TAG_KEY) != null) content.removeViewAt(i);
        }

        final TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(4);
        tv.setPadding((int) (18 * dp), (int) (12 * dp), (int) (18 * dp), (int) (12 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ContextCompat.getColor(a, R.color.bg3) & 0x00FFFFFF | 0xEB000000);
        bg.setColor(0xEB23232B);
        bg.setCornerRadius(22 * dp);
        tv.setBackground(bg);
        tv.setElevation(8 * dp);
        tv.setTag(TAG_KEY, Boolean.TRUE);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = (int) (96 * dp);
        lp.leftMargin = (int) (24 * dp);
        lp.rightMargin = (int) (24 * dp);
        content.addView(tv, lp);

        tv.setAlpha(0f);
        tv.setTranslationY(24 * dp);
        tv.animate().alpha(1f).translationY(0).setDuration(260)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();

        long visibleMs = duration == Toast.LENGTH_LONG ? 3500 : 2000;
        MAIN.postDelayed(() -> {
            if (tv.getParent() == null) return;
            tv.animate().alpha(0f).translationY(12 * dp).setDuration(220)
                    .withEndAction(() -> {
                        ViewGroup p = (ViewGroup) tv.getParent();
                        if (p != null) p.removeView(tv);
                    }).start();
        }, visibleMs);
    }
}
