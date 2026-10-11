package com.talksy.messenger.data;

import com.talksy.messenger.R;

import java.io.File;

public class ChatWallpaper {

    public static final String DEFAULT = "default";
    public static final String CUSTOM = "custom";
    public static final String CUSTOM_FILE_NAME = "chat_wallpaper.jpg";

    public static File customFile(android.content.Context ctx) {
        return new File(ctx.getFilesDir(), CUSTOM_FILE_NAME);
    }

    public static int drawableFor(String presetId) {
        if (presetId == null) return 0;
        switch (presetId) {
            case "indigo": return R.drawable.wallpaper_indigo;
            case "sunset": return R.drawable.wallpaper_sunset;
            case "ocean": return R.drawable.wallpaper_ocean;
            case "forest": return R.drawable.wallpaper_forest;
            case "rose": return R.drawable.wallpaper_rose;
            case "midnight": return R.drawable.wallpaper_midnight;
            default: return 0;
        }
    }

    public static String labelFor(android.content.Context ctx, String presetId) {
        if (presetId == null || DEFAULT.equals(presetId)) return ctx.getString(R.string.wallpaper_default);
        if (CUSTOM.equals(presetId)) return ctx.getString(R.string.wallpaper_custom);
        switch (presetId) {
            case "indigo": return ctx.getString(R.string.wallpaper_indigo);
            case "sunset": return ctx.getString(R.string.wallpaper_sunset);
            case "ocean": return ctx.getString(R.string.wallpaper_ocean);
            case "forest": return ctx.getString(R.string.wallpaper_forest);
            case "rose": return ctx.getString(R.string.wallpaper_rose);
            case "midnight": return ctx.getString(R.string.wallpaper_midnight);
            default: return ctx.getString(R.string.wallpaper_default);
        }
    }

    public static void apply(android.view.View chatRoot, String presetId) {
        android.content.Context ctx = chatRoot.getContext();
        if (CUSTOM.equals(presetId)) {
            File f = customFile(ctx);
            if (f.exists()) {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bmp != null) {
                    chatRoot.setBackground(new android.graphics.drawable.BitmapDrawable(ctx.getResources(), bmp));
                    return;
                }
            }
        }
        int res = drawableFor(presetId);
        if (res != 0) {
            chatRoot.setBackgroundResource(res);
        } else {
            chatRoot.setBackgroundResource(R.color.bg);
        }
    }
}
