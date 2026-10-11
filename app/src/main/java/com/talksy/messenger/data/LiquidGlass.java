package com.talksy.messenger.data;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;

public class LiquidGlass {

    private LiquidGlass() {}

    public static boolean isEnabled(Context ctx) {
        return new Prefs(ctx).getBool(Prefs.KEY_LIQUID_GLASS_ENABLED, true);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        new Prefs(ctx).putBool(Prefs.KEY_LIQUID_GLASS_ENABLED, enabled);
    }

    private static float dp(Context ctx, float v) {
        return v * ctx.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable buildSurface(Context ctx, Integer tintColor, float cornerDp, boolean dark) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(ctx, cornerDp));

        int base = dark ? Color.argb(175, 38, 38, 52) : Color.argb(200, 248, 249, 255);
        if (tintColor != null) {
            int r = Color.red(tintColor), g = Color.green(tintColor), b = Color.blue(tintColor);
            base = Color.argb(225, r, g, b);
        }
        int highlight = tintColor != null ? Color.argb(70, 255, 255, 255)
                : (dark ? Color.argb(45, 255, 255, 255) : Color.argb(230, 255, 255, 255));

        gd.setColors(new int[]{highlight, base});
        gd.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        gd.setStroke((int) dp(ctx, 1f), dark ? Color.argb(40, 255, 255, 255) : Color.argb(90, 255, 255, 255));
        return gd;
    }

    public static void applyPanel(View v, float cornerDp) {
        if (v == null || !isEnabled(v.getContext())) return;
        boolean dark = isNightMode(v.getContext());
        v.setBackground(buildSurface(v.getContext(), null, cornerDp, dark));
        v.setElevation(dp(v.getContext(), 3f));
    }

    public static void applyBubble(View v, Integer tintColor, boolean outgoing) {
        if (v == null || !isEnabled(v.getContext())) return;
        boolean dark = isNightMode(v.getContext());
        v.setBackground(buildSurface(v.getContext(), outgoing ? tintColor : null, 20f, dark));
    }

    public static void applyInputCapsule(View v) {
        if (v == null || !isEnabled(v.getContext())) return;
        boolean dark = isNightMode(v.getContext());
        v.setBackground(buildSurface(v.getContext(), null, 26f, dark));
        v.setElevation(dp(v.getContext(), 4f));
    }

    private static boolean isNightMode(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static void blur(View v, float radiusDp) {
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                float r = dp(v.getContext(), radiusDp);
                v.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {

            }
        }
    }

    public static void clearBlur(View v) {
        if (v != null && Build.VERSION.SDK_INT >= 31) {
            try { v.setRenderEffect(null); } catch (Throwable ignored) {}
        }
    }
}
