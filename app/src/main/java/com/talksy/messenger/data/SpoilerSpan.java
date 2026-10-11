package com.talksy.messenger.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

public class SpoilerSpan extends ReplacementSpan {

    public boolean revealed = false;
    private static final float PAD = 10f;

    private CharSequence inner(CharSequence text, int start, int end) {
        int s = Math.min(start + 2, end);
        int e = Math.max(s, end - 2);
        return text.subSequence(s, e);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        CharSequence in = inner(text, start, end);
        float w = paint.measureText(in, 0, in.length());
        if (fm != null) {
            Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
            fm.ascent = pfm.ascent; fm.descent = pfm.descent; fm.top = pfm.top; fm.bottom = pfm.bottom;
        }
        return (int) Math.ceil(w + PAD * 2);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        CharSequence in = inner(text, start, end);
        if (revealed) {
            canvas.drawText(in, 0, in.length(), x + PAD, y, paint);
            return;
        }
        float w = paint.measureText(in, 0, in.length());
        RectF rect = new RectF(x, top + 2, x + w + PAD * 2, bottom - 2);
        Paint bg = new Paint(paint);
        bg.setColor(0xFF6B7280);
        bg.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(rect, 10f, 10f, bg);
    }
}
