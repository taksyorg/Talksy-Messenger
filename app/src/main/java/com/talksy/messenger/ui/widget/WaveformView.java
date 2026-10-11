package com.talksy.messenger.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;

public class WaveformView extends View {
    public interface OnSeek { void onSeek(float fraction); }

    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG), idle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private byte[] bars = new byte[0];
    private float progress;
    private OnSeek seek;
    private final float barW, gap;

    public WaveformView(Context c) { this(c, null); }
    public WaveformView(Context c, AttributeSet a) {
        super(c, a);
        float d = c.getResources().getDisplayMetrics().density;
        barW = 3 * d; gap = 2 * d;
        played.setStyle(Paint.Style.FILL); idle.setStyle(Paint.Style.FILL);
    }

    public void setColors(int playedColor, int idleColor) { played.setColor(playedColor); idle.setColor(idleColor); invalidate(); }
    public void setOnSeek(OnSeek s) { seek = s; }

    public void setWaveform(String b64, String seedKey) {
        byte[] data = null;
        if (b64 != null && !b64.isEmpty()) { try { data = Base64.decode(b64, Base64.NO_WRAP); } catch (Exception ignored) {} }
        if (data == null || data.length == 0) {
            data = new byte[40]; int h = seedKey == null ? 7 : seedKey.hashCode();
            for (int i = 0; i < data.length; i++) { h = h * 1103515245 + 12345; data[i] = (byte) (60 + ((h >>> 16) & 0x7F)); }
        }
        bars = data; invalidate();
    }

    public void setProgress(float p) { progress = Math.max(0, Math.min(1, p)); invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        int ww = MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED ? (int) (160 * getResources().getDisplayMetrics().density) : MeasureSpec.getSize(w);
        setMeasuredDimension(ww, (int) (28 * getResources().getDisplayMetrics().density));
    }

    @Override protected void onDraw(Canvas cv) {
        if (bars.length == 0) return;
        int n = Math.max(1, (int) ((getWidth() + gap) / (barW + gap)));
        float h = getHeight();
        for (int i = 0; i < n; i++) {
            int idx = Math.min(bars.length - 1, i * bars.length / n);
            float v = (bars[idx] & 0xFF) / 255f;
            float bh = Math.max(barW, v * h);
            float x = i * (barW + gap);
            r.set(x, (h - bh) / 2f, x + barW, (h + bh) / 2f);
            cv.drawRoundRect(r, barW / 2, barW / 2, (i + 0.5f) / n <= progress ? played : idle);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (seek == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: getParent().requestDisallowInterceptTouchEvent(true); return true;
            case MotionEvent.ACTION_MOVE: case MotionEvent.ACTION_UP:
                seek.onSeek(Math.max(0, Math.min(1, e.getX() / Math.max(1, getWidth()))));
                if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return true;
            default: return false;
        }
    }

    @Override public boolean performClick() { return super.performClick(); }
}
