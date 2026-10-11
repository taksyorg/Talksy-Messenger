package com.talksy.messenger.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AvatarLoader {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    public interface Target {
        void onLoaded(Bitmap circularBitmap);
    }

    public static void load(String fullUrl, Target target) {
        if (fullUrl == null || fullUrl.isEmpty()) return;

        Bitmap cached = CACHE.get(fullUrl);
        if (cached != null) {
            target.onLoaded(cached);
            return;
        }

        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                try {
                    java.io.File f = MediaCache.getOrDownload(fullUrl);
                    if (f == null) return null;
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                    return bmp == null ? null : toRoundedSquare(bmp);
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bmp) {
                if (bmp != null) {
                    CACHE.put(fullUrl, bmp);
                    target.onLoaded(bmp);
                }
            }
        }.execute();
    }

    public static void loadRaw(String fullUrl, int maxDim, Target target) {
        loadRaw(fullUrl, maxDim, com.talksy.messenger.data.TrafficStats.CAT_PHOTO, target);
    }

    public static void loadRaw(String fullUrl, int maxDim, String category, Target target) {
        if (fullUrl == null || fullUrl.isEmpty()) return;
        String cacheKey = "raw:" + maxDim + ":" + fullUrl;

        Bitmap cached = CACHE.get(cacheKey);
        if (cached != null) {
            target.onLoaded(cached);
            return;
        }
        android.content.Context ctx = com.talksy.messenger.TalksyApp.appContext();
        if (ctx == null) return;
        MediaCache.get(ctx, category, fullUrl, (file, error) -> {
            if (file == null) return;
            new AsyncTask<Void, Void, Bitmap>() {
                @Override protected Bitmap doInBackground(Void... v) {
                    try {
                        BitmapFactory.Options b = new BitmapFactory.Options();
                        b.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(file.getPath(), b);
                        int sample = 1;
                        while (b.outWidth / sample > maxDim * 2 || b.outHeight / sample > maxDim * 2) sample *= 2;
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inSampleSize = sample;
                        Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), o);
                        return bmp == null ? null : scaleDown(bmp, maxDim);
                    } catch (Exception e) { return null; }
                }
                @Override protected void onPostExecute(Bitmap bmp) {
                    if (bmp != null) { CACHE.put(cacheKey, bmp); target.onLoaded(bmp); }
                }
            }.execute();
        });
    }

    private static Bitmap scaleDown(Bitmap bitmap, int maxDim) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w <= maxDim && h <= maxDim) return bitmap;
        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        return Bitmap.createScaledBitmap(bitmap, Math.round(w * scale), Math.round(h * scale), true);
    }

    public static Bitmap toRoundedSquare(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.RectF rect = new android.graphics.RectF(0, 0, size, size);
        float radius = size * 0.28f;

        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(0xFF424242);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        int x = (bitmap.getWidth() - size) / 2;
        int y = (bitmap.getHeight() - size) / 2;
        canvas.drawBitmap(bitmap, new Rect(x, y, x + size, y + size), new Rect(0, 0, size, size), paint);
        return output;
    }
}
