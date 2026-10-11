package com.talksy.messenger.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;
import com.talksy.messenger.data.IconManager;
import com.talksy.messenger.data.model.MediaAttachment;
import com.talksy.messenger.media.VoicePlayer;

import java.util.Locale;

public class VoiceBubbleView extends LinearLayout {
    private final ImageView btn;
    private final WaveformView wave;
    private final TextView dur, speed;
    private String key;
    private MediaAttachment media;
    private String baseUrl;
    private long totalMs;
    private int fg, dim;

    public VoiceBubbleView(Context c) { this(c, null); }

    public VoiceBubbleView(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        float d = c.getResources().getDisplayMetrics().density;
        setPadding((int) (10 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d));

        btn = new ImageView(c);
        btn.setScaleType(ImageView.ScaleType.CENTER);
        addView(btn, new LayoutParams((int) (42 * d), (int) (42 * d)));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        LayoutParams cp = new LayoutParams((int) (170 * d), LayoutParams.WRAP_CONTENT);
        cp.setMarginStart((int) (10 * d));
        addView(col, cp);

        wave = new WaveformView(c);
        col.addView(wave, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        dur = new TextView(c); dur.setTextSize(11);
        row.addView(dur, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        speed = new TextView(c); speed.setTextSize(11); speed.setVisibility(GONE);
        speed.setPadding((int) (6 * d), (int) (1 * d), (int) (6 * d), (int) (1 * d));
        row.addView(speed);

        btn.setOnClickListener(v -> {
            if (media == null) return;
            VoicePlayer.get().toggle(getContext(), key, media, baseUrl, this);
        });
        speed.setOnClickListener(v -> { float s = VoicePlayer.get().cycleSpeed(); showSpeed(s); });
        wave.setOnSeek(f -> VoicePlayer.get().seekTo(key, f));
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    public void bind(String key, MediaAttachment media, String baseUrl, boolean outgoing) {
        this.key = key; this.media = media; this.baseUrl = baseUrl; this.totalMs = media.durationMs;
        Context c = getContext();
        fg = outgoing ? Color.WHITE : ContextCompat.getColor(c, R.color.accent);
        dim = outgoing ? 0x80FFFFFF : ContextCompat.getColor(c, R.color.text3);
        wave.setColors(fg, dim);
        wave.setWaveform(media.waveform, key);
        dur.setTextColor(outgoing ? 0xCCFFFFFF : ContextCompat.getColor(c, R.color.text2));
        speed.setTextColor(fg);
        GradientDrawable chip = new GradientDrawable(); chip.setCornerRadius(40);
        chip.setColor(outgoing ? 0x33FFFFFF : 0x1F6366F1); speed.setBackground(chip);
        VoicePlayer vp = VoicePlayer.get();
        vp.attach(key, this);
        boolean playing = vp.isPlaying(key), loading = vp.isLoading(key);
        renderState(playing, loading);
        wave.setProgress(playing || key.equals(vp.currentKey()) ? progressOf(vp.position(key), totalMs) : 0);
        dur.setText(fmt(key.equals(vp.currentKey()) && vp.position(key) > 0 ? vp.position(key) : totalMs));
        showSpeed(vp.speed());
        speed.setVisibility(key.equals(vp.currentKey()) ? VISIBLE : GONE);
    }

    private float progressOf(int pos, long total) { return total <= 0 ? 0 : Math.min(1f, pos / (float) total); }

    private void showSpeed(float s) { speed.setText(s == 1f ? "1×" : s == 1.5f ? "1.5×" : "2×"); }

    private void renderState(boolean playing, boolean loading) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(fg == Color.WHITE ? 0x33FFFFFF : 0x226366F1);
        btn.setBackground(circle);
        IconManager.set(btn, loading ? "loading" : playing ? "pause" : "play_tri", fg);
        btn.setPadding(0, 0, 0, 0);
    }

    public void onPlayback(String forKey, boolean playing, int posMs, int durMs) {
        if (key == null || !key.equals(forKey)) return;
        long total = durMs > 0 ? durMs : totalMs;
        boolean active = playing || posMs > 0 || key.equals(VoicePlayer.get().currentKey());
        renderState(playing, VoicePlayer.get().isLoading(key));
        wave.setProgress(playing || posMs > 0 ? progressOf(posMs, total) : 0);
        dur.setText(fmt(playing || posMs > 0 ? posMs : total));
        speed.setVisibility(active ? VISIBLE : GONE);
    }
}
