package com.talksy.messenger.data;

import android.graphics.Bitmap;

public class BlurUtils {
    private BlurUtils() {}

    public static Bitmap blur(Bitmap src) {
        if (src == null) return null;
        int w = Math.max(1, src.getWidth() / 28), h = Math.max(1, src.getHeight() / 28);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        return Bitmap.createScaledBitmap(small, src.getWidth(), src.getHeight(), true);
    }
}
