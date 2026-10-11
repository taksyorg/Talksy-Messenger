package com.talksy.messenger.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.Locale;

public final class MapKey {
    private MapKey() {}

    public static String get(Context c) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(c.getPackageName(), PackageManager.GET_META_DATA);
            String k = ai.metaData == null ? null : ai.metaData.getString("com.google.android.geo.API_KEY");
            return k == null ? "" : k.trim();
        } catch (Exception e) { return ""; }
    }

    public static boolean has(Context c) { String k = get(c); return !k.isEmpty() && !k.startsWith("YOUR_"); }

    public static String staticMapUrl(Context c, double lat, double lon, int wDp, int hDp) {
        if (!has(c)) return "";
        return String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%1$.6f,%2$.6f&zoom=15&size=%3$dx%4$d&scale=2"
                + "&maptype=roadmap&markers=color:red%%7C%1$.6f,%2$.6f&key=%5$s", lat, lon, wDp, hDp, get(c));
    }
}
