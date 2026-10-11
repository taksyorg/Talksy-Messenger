package com.talksy.messenger.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.Prefs;
import com.talksy.messenger.data.TrafficStats;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.media.VoicePlayer;
import com.talksy.messenger.net.MediaCache;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

public class RoundVideoView extends FrameLayout {
    private static WeakReference<RoundVideoView> current = new WeakReference<>(null);
    public static void stopCurrent() { RoundVideoView v = current.get(); if (v != null) v.stopPlayback(); }

    private final ImageView thumb, glyph;
    private final TextureView tex;
    private final TextView chip;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaAttachment media;
    private String baseUrl, key;
    private File file;
    private MediaPlayer mp;
    private boolean prepared, wantPlay, downloading;
    private float progress;
    private int vw, vh;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (mp == null || !prepared) return;
            try {
                int pos = mp.getCurrentPosition(), dur = Math.max(1, mp.getDuration());
                progress = pos / (float) dur; chip.setText(fmt(dur - pos)); invalidate();
            } catch (Exception ignored) {}
            main.postDelayed(this, 50);
        }
    };

    public RoundVideoView(Context c) { this(c, null); }

    public RoundVideoView(Context c, AttributeSet a) {
        super(c, a);
        float d = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable(); bg.setShape(GradientDrawable.OVAL); bg.setColor(0xFF1B1B26);
        setBackground(bg);

        thumb = new ImageView(c); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(thumb, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tex = new TextureView(c); tex.setVisibility(INVISIBLE);
        addView(tex, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        glyph = new ImageView(c); glyph.setScaleType(ImageView.ScaleType.CENTER);
        GradientDrawable gb = new GradientDrawable(); gb.setShape(GradientDrawable.OVAL); gb.setColor(0x66000000);
        glyph.setBackground(gb);
        LayoutParams gp = new LayoutParams((int) (56 * d), (int) (56 * d)); gp.gravity = Gravity.CENTER;
        addView(glyph, gp);
        chip = new TextView(c); chip.setTextColor(Color.WHITE); chip.setTextSize(11);
        GradientDrawable cb = new GradientDrawable(); cb.setCornerRadius(40 * d); cb.setColor(0x66000000); chip.setBackground(cb);
        chip.setPadding((int) (8 * d), (int) (2 * d), (int) (8 * d), (int) (2 * d));
        LayoutParams cp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.START | Gravity.BOTTOM; cp.setMargins((int) (14 * d), 0, 0, (int) (14 * d));
        addView(chip, cp);

        ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(3 * d); ring.setColor(Color.WHITE); ring.setStrokeCap(Paint.Cap.ROUND);
        ringBg.setStyle(Paint.Style.STROKE); ringBg.setStrokeWidth(3 * d); ringBg.setColor(0x33FFFFFF);

        setOnClickListener(v -> onTap());
        tex.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { if (wantPlay && file != null) startPlayer(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { applyCrop(); }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
        });
    }

    private static String fmt(long ms) { long s = Math.max(0, ms / 1000); return String.format(Locale.US, "%d:%02d", s / 60, s % 60); }

    public void bind(String key, MediaAttachment media, String baseUrl) {
        if (this.key != null && !this.key.equals(key)) stopPlayback();
        this.key = key; this.media = media; this.baseUrl = baseUrl; this.file = null; this.progress = 0; this.downloading = false;
        thumb.setImageBitmap(null); tex.setVisibility(INVISIBLE);
        chip.setText(fmt(media.durationMs));
        if (media.url == null && media.localPath != null) { file = new File(media.localPath); onFileReady(); }
        else {
            file = MediaCache.peek(getContext(), TrafficStats.CAT_VIDEO, baseUrl + media.url);
            if (file != null) onFileReady();
            else if (autoDownload()) download(false);
            else glyph("file_download");
        }
        invalidate();
    }

    private boolean autoDownload() {
        String m = new Prefs(getContext()).get(Prefs.KEY_AUTO_DOWNLOAD, "wifi");
        return "always".equals(m) || ("wifi".equals(m) && !TrafficStats.isMetered(getContext()));
    }

    private void glyph(String icon) {
        glyph.setVisibility(VISIBLE);
        IconManager.set(glyph, icon, Color.WHITE);
    }

    private void download(boolean thenPlay) {
        if (downloading) return;
        downloading = true; glyph("loading");
        final String k = key;
        MediaCache.get(getContext(), TrafficStats.CAT_VIDEO, baseUrl + media.url, (f, err) -> {
            downloading = false;
            if (!k.equals(key)) return;
            if (f == null) { glyph("file_error"); return; }
            file = f; onFileReady();
            if (thenPlay) startPlayback();
        });
    }

    private void onFileReady() {
        glyph("play_tri");
        final String k = key; final File f = file;
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                r.setDataSource(f.getAbsolutePath());
                bmp = r.getFrameAtTime(0);
                r.release();
            } catch (Exception ignored) {}
            final Bitmap b = bmp;
            main.post(() -> { if (k.equals(key) && b != null) thumb.setImageBitmap(b); });
        }).start();
    }

    private void onTap() {
        if (media == null) return;
        if (file == null) { download(true); return; }
        if (mp != null && prepared) {
            if (mp.isPlaying()) { mp.pause(); glyph("play_tri"); main.removeCallbacks(tick); }
            else { mp.start(); glyph.setVisibility(GONE); main.post(tick); }
            return;
        }
        startPlayback();
    }

    private void startPlayback() {
        if (file == null) return;
        VoicePlayer.get().stop();
        RoundVideoView other = current.get();
        if (other != null && other != this) other.stopPlayback();
        current = new WeakReference<>(this);
        wantPlay = true; glyph.setVisibility(GONE);
        tex.setVisibility(VISIBLE);
        if (tex.isAvailable()) startPlayer();
    }

    private void startPlayer() {
        releasePlayer();
        try {
            mp = new MediaPlayer();
            mp.setDataSource(file.getAbsolutePath());
            mp.setSurface(new Surface(tex.getSurfaceTexture()));
            mp.setOnVideoSizeChangedListener((p, w, h) -> { vw = w; vh = h; applyCrop(); });
            mp.setOnPreparedListener(p -> { prepared = true; p.start(); main.post(tick); });
            mp.setOnCompletionListener(p -> stopPlayback());
            mp.setOnErrorListener((p, w, e) -> { stopPlayback(); return true; });
            mp.prepareAsync();
        } catch (Exception e) { stopPlayback(); }
    }

    private void applyCrop() {
        if (vw <= 0 || vh <= 0 || tex.getWidth() == 0) return;
        float sx = 1f, sy = 1f;
        float va = vw / (float) vh;
        if (va > 1f) sx = va; else sy = 1f / va;
        Matrix m = new Matrix();
        m.setScale(sx, sy, tex.getWidth() / 2f, tex.getHeight() / 2f);
        tex.setTransform(m);
    }

    public void stopPlayback() {
        wantPlay = false;
        releasePlayer();
        main.removeCallbacks(tick);
        progress = 0; tex.setVisibility(INVISIBLE);
        if (media != null) { chip.setText(fmt(media.durationMs)); if (file != null) glyph("play_tri"); }
        invalidate();
    }

    private void releasePlayer() {
        try { if (mp != null) mp.release(); } catch (Exception ignored) {}
        mp = null; prepared = false;
    }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); wantPlay = false; releasePlayer(); main.removeCallbacks(tick); }

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        float pad = ring.getStrokeWidth() / 2f + 2;
        arc.set(pad, pad, getWidth() - pad, getHeight() - pad);
        if (progress > 0f) { c.drawArc(arc, 0, 360, false, ringBg); c.drawArc(arc, -90, 360f * progress, false, ring); }
    }
}
