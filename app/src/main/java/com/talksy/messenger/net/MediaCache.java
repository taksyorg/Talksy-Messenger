package com.talksy.messenger.net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.TrafficStats;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaCache {
    private MediaCache() {}

    public interface Callback { void onDone(File file, String error); }

    public static final String[] CATEGORIES = {
            TrafficStats.CAT_VIDEO, TrafficStats.CAT_FILES, TrafficStats.CAT_PHOTO, TrafficStats.CAT_VOICE, TrafficStats.CAT_STICKERS};

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final ConcurrentHashMap<String, List<Callback>> INFLIGHT = new ConcurrentHashMap<>();

    public static File root(Context ctx) {
        File r = new File(ctx.getFilesDir(), "media_cache");
        if (!r.exists()) r.mkdirs();
        return r;
    }

    public static File dir(Context ctx, String category) {
        File d = new File(root(ctx), category);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String sha1(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }

    private static String ext(String url) {
        int q = url.indexOf('?'); String u = q >= 0 ? url.substring(0, q) : url;
        int dot = u.lastIndexOf('.'), slash = u.lastIndexOf('/');
        return dot > slash && u.length() - dot <= 6 ? u.substring(dot) : "";
    }

    public static File fileFor(Context ctx, String category, String fullUrl) {
        return new File(dir(ctx, category), sha1(fullUrl) + ext(fullUrl));
    }

    public static File peek(Context ctx, String category, String fullUrl) {
        File f = fileFor(ctx, category, fullUrl);
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f; }
        return null;
    }

    public static void get(Context ctx, String category, String fullUrl, Callback cb) {
        final Context app = ctx.getApplicationContext();
        File cached = peek(app, category, fullUrl);
        if (cached != null) { cb.onDone(cached, null); return; }
        final String key = category + "|" + fullUrl;
        List<Callback> waiters = new ArrayList<>();
        List<Callback> existing = INFLIGHT.putIfAbsent(key, waiters);
        if (existing != null) { synchronized (existing) { existing.add(cb); } return; }
        waiters.add(cb);
        POOL.execute(() -> {
            String[] errOut = new String[1];
            File res = download(app, category, fullUrl, errOut);
            final String e2 = errOut[0];
            List<Callback> list = INFLIGHT.remove(key);
            if (list != null) {
                List<Callback> copy; synchronized (list) { copy = new ArrayList<>(list); }
                MAIN.post(() -> { for (Callback c : copy) c.onDone(res, e2); });
            }
        });
    }

    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private static File download(Context app, String category, String fullUrl, String[] errOut) {
        Object lock = LOCKS.computeIfAbsent(category + "|" + fullUrl, k -> new Object());
        synchronized (lock) {
            File cached = peek(app, category, fullUrl);
            if (cached != null) return cached;
            File out = null;
            File target = fileFor(app, category, fullUrl);
            File tmp = new File(target.getPath() + ".part");
            HttpURLConnection conn = null;
            long got = 0;
            try {
                URL url = new URL(fullUrl);
                Proxy proxy = new ProxyManager(app).getJavaProxyOrNull();
                conn = (HttpURLConnection) (proxy != null ? url.openConnection(proxy) : url.openConnection());
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                if (conn.getResponseCode() / 100 != 2) throw new java.io.IOException("HTTP " + conn.getResponseCode());
                try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[32 * 1024]; int n;
                    while ((n = in.read(buf)) != -1) { fos.write(buf, 0, n); got += n; }
                }
                if (tmp.length() == 0) throw new java.io.IOException("empty");
                if (!tmp.renameTo(target)) throw new java.io.IOException("rename");
                out = target;
            } catch (Exception e) {
                if (errOut != null) errOut[0] = String.valueOf(e.getMessage());
                tmp.delete();
            } finally {
                if (conn != null) conn.disconnect();
                TrafficStats.add(app, category, 0, got);
            }
            LOCKS.remove(category + "|" + fullUrl);
            return out;
        }
    }

    private static Context appCtx;
    public static void init(Context ctx) { if (appCtx == null) appCtx = ctx.getApplicationContext(); }
    private static Context ctx() { return appCtx != null ? appCtx : com.talksy.messenger.TalksyApp.appContext(); }

    public interface PathCallback { void onPath(String localPathOrNull); }

    private static String guessCategory(String url) {
        String u = url == null ? "" : url.toLowerCase();
        int q = u.indexOf('?'); if (q >= 0) u = u.substring(0, q);
        if (u.endsWith(".mp4") || u.endsWith(".mov") || u.endsWith(".webm") || u.endsWith(".mkv") || u.endsWith(".3gp") || u.endsWith(".m4v")) return TrafficStats.CAT_VIDEO;
        if (u.endsWith(".m4a") || u.endsWith(".mp3") || u.endsWith(".ogg") || u.endsWith(".aac") || u.endsWith(".wav") || u.endsWith(".opus")) return TrafficStats.CAT_VOICE;
        return TrafficStats.CAT_PHOTO;
    }

    public static File cachedFile(String url) {
        Context c = ctx();
        if (c == null || url == null || url.isEmpty()) return null;
        for (String cat : CATEGORIES) { File f = peek(c, cat, url); if (f != null) return f; }
        return null;
    }

    public static File getOrDownload(String url) {
        Context c = ctx();
        if (c == null || url == null || url.isEmpty()) return null;
        File f = cachedFile(url);
        return f != null ? f : download(c, guessCategory(url), url, null);
    }

    public static void withLocalPath(String url, PathCallback cb) { withLocalPath(url, guessCategory(url), cb); }

    public static void withLocalPath(String url, String category, PathCallback cb) {
        Context c = ctx();
        if (c == null || url == null || url.isEmpty()) { cb.onPath(null); return; }
        get(c, category, url, (file, err) -> cb.onPath(file == null ? null : file.getAbsolutePath()));
    }

    public static void prefetch(String url) { prefetch(url, guessCategory(url)); }

    public static void prefetch(String url, String category) {
        Context c = ctx();
        if (c == null || url == null || url.isEmpty() || peek(c, category, url) != null) return;
        get(c, category, url, (file, err) -> { });
    }

    public static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0; File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) s += size(k);
        return s;
    }

    public static long categorySize(Context ctx, String category) { return size(new File(root(ctx), category)); }

    public static long otherSize(Context ctx) {
        return size(ctx.getCacheDir()) + size(ctx.getExternalCacheDir());
    }

    public static void clearCategory(Context ctx, String category) { delete(new File(root(ctx), category)); }

    public static void clearAll(Context ctx) {
        delete(root(ctx));
        deleteContents(ctx.getCacheDir());
        deleteContents(ctx.getExternalCacheDir());
    }

    private static void deleteContents(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] kids = dir.listFiles();
        if (kids != null) for (File k : kids) delete(k);
    }

    private static void delete(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] kids = f.listFiles(); if (kids != null) for (File k : kids) delete(k); }
        f.delete();
    }

    public static void trim(Context ctx) {
        Prefs prefs = new Prefs(ctx);
        long ttlDays = prefs.getLong("cache_ttl_days", 30);
        long maxMb = prefs.getLong("cache_max_mb", 5 * 1024);
        List<File> all = new ArrayList<>();
        collect(root(ctx), all);
        long now = System.currentTimeMillis();
        if (ttlDays > 0) {
            for (File f : new ArrayList<>(all)) {
                if (now - f.lastModified() > ttlDays * 86_400_000L) { f.delete(); all.remove(f); }
            }
        }
        if (maxMb > 0) {
            long limit = maxMb * 1024 * 1024, total = 0;
            for (File f : all) total += f.length();
            if (total > limit) {
                Collections.sort(all, Comparator.comparingLong(File::lastModified));
                for (File f : all) {
                    if (total <= limit) break;
                    total -= f.length(); f.delete();
                }
            }
        }
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) { if (k.isDirectory()) collect(k, out); else out.add(k); }
    }

    public static void trimAsync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        POOL.execute(() -> { try { trim(app); } catch (Exception ignored) {} });
    }

    public static List<String> allCategories() { return new ArrayList<>(Arrays.asList(CATEGORIES)); }
}
