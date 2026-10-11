package com.talksy.messenger.adapters;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.BlurUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VideoBubble {
    private VideoBubble() {}

    private static final LruCache<String, Bitmap> THUMBS = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final ConcurrentHashMap<String, Long> DURATIONS = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void reset(View frameImage) {
        hide(frameImage, R.id.txt_video_badge);
        hide(frameImage, R.id.img_video_play);
        hide(frameImage, R.id.txt_video_time);
        ImageView img = frameImage.findViewById(R.id.img_media);
        if (img != null) {
            img.setMinimumHeight(0);
            img.setTag(null);
        }
    }

    private static void hide(View root, int id) {
        View v = root.findViewById(id);
        if (v != null) v.setVisibility(View.GONE);
    }

    public static void bind(View frameImage, ImageView img, String fullUrl, boolean hidden) {
        final float dp = img.getResources().getDisplayMetrics().density;
        final TextView badge = frameImage.findViewById(R.id.txt_video_badge);
        final View play = frameImage.findViewById(R.id.img_video_play);

        img.setMinimumHeight((int) (160 * dp));
        img.setImageBitmap(null);
        img.setTag(fullUrl);
        if (badge != null) badge.setVisibility(View.GONE);
        if (play != null) play.setVisibility(hidden ? View.GONE : View.VISIBLE);

        Bitmap cached = THUMBS.get(fullUrl);
        if (cached != null) {
            Long d = DURATIONS.get(fullUrl);
            apply(img, badge, fullUrl, cached, d == null ? 0 : d, hidden);
            return;
        }

        POOL.execute(() -> {
            Bitmap bmp = null;
            long durationMs = 0;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                java.io.File local = com.talksy.messenger.net.MediaCache.getOrDownload(fullUrl);
                if (local != null) r.setDataSource(local.getAbsolutePath());
                else r.setDataSource(fullUrl, new HashMap<String, String>());
                String ds = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (ds != null) durationMs = Long.parseLong(ds);

                long timeUs = Math.min(1_000_000L, durationMs * 500L);
                Bitmap raw = r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (raw != null) {
                    int maxW = 720;
                    if (raw.getWidth() > maxW) {
                        int h = Math.round(raw.getHeight() * (maxW / (float) raw.getWidth()));
                        bmp = Bitmap.createScaledBitmap(raw, maxW, Math.max(1, h), true);
                        if (bmp != raw) raw.recycle();
                    } else {
                        bmp = raw;
                    }
                }
            } catch (Exception ignored) {

            } finally {
                try { r.release(); } catch (Exception ignored) {}
            }
            final Bitmap fBmp = bmp;
            final long fDur = durationMs;
            MAIN.post(() -> {
                if (fBmp != null) THUMBS.put(fullUrl, fBmp);
                if (fDur > 0) DURATIONS.put(fullUrl, fDur);

                if (fullUrl.equals(img.getTag())) apply(img, badge, fullUrl, fBmp, fDur, hidden);
            });
        });
    }

    private static void apply(ImageView img, TextView badge, String url, Bitmap bmp, long durationMs, boolean hidden) {
        if (bmp != null) {
            img.setMinimumHeight(0);
            img.setImageBitmap(hidden ? BlurUtils.blur(bmp) : bmp);
        }
        if (badge != null && durationMs > 0 && !hidden) {
            badge.setText(formatDuration(durationMs));
            badge.setVisibility(View.VISIBLE);
        }
    }

    public static void applyTime(View frameImage, TextView timeView, boolean overlay) {
        TextView t = frameImage.findViewById(R.id.txt_video_time);
        if (t == null) { timeView.setVisibility(View.VISIBLE); return; }
        if (overlay) {
            t.setText(timeView.getText());
            t.setVisibility(View.VISIBLE);
            timeView.setVisibility(View.GONE);
        } else {
            t.setVisibility(View.GONE);
            timeView.setVisibility(View.VISIBLE);
        }
    }

    public static String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                     : String.format(Locale.US, "%d:%02d", m, sec);
    }
}
