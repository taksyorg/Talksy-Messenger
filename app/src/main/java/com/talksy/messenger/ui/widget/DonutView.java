package com.talksy.messenger.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.talksy.messenger.R;

public class DonutView extends View {
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG), big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float[] values = new float[0];
    private int[] colors = new int[0];
    private String bigText = "", smallText = "";
    private final float stroke;

    public DonutView(Context c) { this(c, null); }
    public DonutView(Context c, AttributeSet a) {
        super(c, a);
        float d = c.getResources().getDisplayMetrics().density;
        stroke = 26 * d;
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(stroke);
        big.setTextAlign(Paint.Align.CENTER); big.setTextSize(30 * d); big.setFakeBoldText(true);
        small.setTextAlign(Paint.Align.CENTER); small.setTextSize(14 * d);
        big.setColor(ContextCompat.getColor(c, R.color.text1)); small.setColor(ContextCompat.getColor(c, R.color.text2));
    }

    public void set(float[] values, int[] colors, String bigText, String smallText) {
        this.values = values; this.colors = colors; this.bigText = bigText; this.smallText = smallText; invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), (int) (240 * getResources().getDisplayMetrics().density));
        setMeasuredDimension(MeasureSpec.getSize(w), size);
    }

    @Override protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = size / 2f - stroke / 2f - 4;
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        float total = 0; for (float v : values) total += v;
        if (total <= 0) {
            arc.setColor(ContextCompat.getColor(getContext(), R.color.bg3)); c.drawArc(r, 0, 360, false, arc);
        } else {
            float start = -90, gap = values.length > 1 ? 2.5f : 0;
            for (int i = 0; i < values.length; i++) {
                float sweep = values[i] / total * 360f;
                if (sweep <= 0) continue;
                arc.setColor(colors[i % colors.length]);
                c.drawArc(r, start + gap / 2, Math.max(0.5f, sweep - gap), false, arc);
                start += sweep;
            }
        }
        c.drawText(bigText, cx, cy + big.getTextSize() * 0.15f, big);
        c.drawText(smallText, cx, cy + big.getTextSize() * 0.15f + small.getTextSize() * 1.4f, small);
    }
}
