package com.talksy.messenger.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

public final class TrafficStats {
    private TrafficStats() {}

    public static final String NET_WIFI = "wifi", NET_MOBILE = "mobile", NET_ROAMING = "roaming";
    public static final String[] NETS = {NET_WIFI, NET_MOBILE, NET_ROAMING};

    public static final String CAT_VIDEO = "video", CAT_FILES = "files", CAT_PHOTO = "photo", CAT_MESSAGES = "messages",
            CAT_VOICE = "voice", CAT_STICKERS = "stickers", CAT_OTHER = "other";

    public static final String[] CATS = {CAT_VIDEO, CAT_FILES, CAT_PHOTO, CAT_MESSAGES, CAT_VOICE, CAT_STICKERS, CAT_OTHER};

    private static final String FILE = "traffic_stats";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Long> PENDING = new HashMap<>();
    private static boolean flushScheduled;
    private static Context app;

    public static void init(Context ctx) { if (app == null) app = ctx.getApplicationContext(); }

    public static String currentNet(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c == null) return NET_MOBILE;
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return NET_WIFI;
            if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) return NET_ROAMING;
            return NET_MOBILE;
        } catch (Exception e) { return NET_MOBILE; }
    }

    public static boolean isMetered(Context ctx) { return !NET_WIFI.equals(currentNet(ctx)); }

    public static void add(Context ctx, String category, long sent, long received) {
        if (sent <= 0 && received <= 0) return;
        init(ctx);
        String net = currentNet(app);
        synchronized (PENDING) {
            if (sent > 0) PENDING.merge(net + ":" + category + ":tx", sent, Long::sum);
            if (received > 0) PENDING.merge(net + ":" + category + ":rx", received, Long::sum);
            if (!flushScheduled) { flushScheduled = true; MAIN.postDelayed(TrafficStats::flush, 5000); }
        }
    }

    public static void flush() {
        if (app == null) return;
        Map<String, Long> copy;
        synchronized (PENDING) { copy = new HashMap<>(PENDING); PENDING.clear(); flushScheduled = false; }
        if (copy.isEmpty()) return;
        SharedPreferences sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = sp.edit();
        for (Map.Entry<String, Long> en : copy.entrySet()) e.putLong(en.getKey(), sp.getLong(en.getKey(), 0) + en.getValue());
        if (!sp.contains("since")) e.putLong("since", System.currentTimeMillis());
        e.apply();
    }

    public static long get(Context ctx, String net, String category, boolean sent) {
        flush();
        SharedPreferences sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        long sum = 0;
        for (String n : NETS) {
            if (net != null && !net.equals(n)) continue;
            sum += sp.getLong(n + ":" + category + (sent ? ":tx" : ":rx"), 0);
        }
        return sum;
    }

    public static long total(Context ctx, String net, boolean sent) {
        long s = 0;
        for (String c : CATS) s += get(ctx, net, c, sent);
        return s;
    }

    public static long since(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        long v = sp.getLong("since", 0);
        if (v == 0) { v = System.currentTimeMillis(); sp.edit().putLong("since", v).apply(); }
        return v;
    }

    public static void reset(Context ctx) {
        synchronized (PENDING) { PENDING.clear(); }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().putLong("since", System.currentTimeMillis()).apply();
    }

    public static String categoryOfMedia(String mediaType) {
        if (mediaType == null) return CAT_FILES;
        switch (mediaType) {
            case "image": return CAT_PHOTO;
            case "video": case "round": return CAT_VIDEO;
            case "voice": case "audio": return CAT_VOICE;
            case "sticker_image": case "sticker_video": return CAT_STICKERS;
            default: return CAT_FILES;
        }
    }
}
