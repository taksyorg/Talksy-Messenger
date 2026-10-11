package com.talksy.messenger.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Drafts {
    private Drafts() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("talksy_drafts", Context.MODE_PRIVATE);
    }

    public static String get(Context c, String key) { return sp(c).getString(key, ""); }

    public static void set(Context c, String key, String text) {
        if (text == null || text.trim().isEmpty()) sp(c).edit().remove(key).apply();
        else sp(c).edit().putString(key, text).apply();
    }
}
