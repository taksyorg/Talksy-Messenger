package com.talksy.messenger.stickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class SquareCropView extends View {

    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private float lastX, lastY;
    private boolean dragging;
    private float minScale = 1f;

    public SquareCropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                clampAndInvalidate();
                return true;
            }
        });
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int size = (w > 0 && h > 0) ? Math.min(w, h) : Math.max(w, h);
        setMeasuredDimension(size, size);
    }

    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        post(this::resetMatrix);
    }

    private void resetMatrix() {
        if (bitmap == null || getWidth() == 0) return;
        matrix.reset();
        float scale = Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        minScale = scale;
        float dx = (getWidth() - bitmap.getWidth() * scale) / 2f;
        float dy = (getHeight() - bitmap.getHeight() * scale) / 2f;
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX(); lastY = event.getY(); dragging = true; break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    matrix.postTranslate(event.getX() - lastX, event.getY() - lastY);
                    lastX = event.getX(); lastY = event.getY();
                    clampAndInvalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false; break;
        }
        return true;
    }

    private void clampAndInvalidate() {
        float[] values = new float[9];
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        if (scale < minScale) {
            float factor = minScale / scale;
            matrix.postScale(factor, factor, getWidth() / 2f, getHeight() / 2f);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(bitmap, matrix, paint);
        canvas.restore();
    }

    public Bitmap renderSquareBitmap(int outputSize) {
        Bitmap out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float scaleToOutput = (float) outputSize / getWidth();
        Matrix m = new Matrix(matrix);
        m.postScale(scaleToOutput, scaleToOutput);
        c.drawBitmap(bitmap, m, paint);
        return out;
    }
}
