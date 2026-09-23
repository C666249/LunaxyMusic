package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * Lightweight HSV color picker used by the lyric-gradient customizer.
 * The top area edits saturation/value; the bottom strip edits hue.
 * No dependency is required and no text/HEX input is needed.
 */
public final class ColorPickerView extends View {
    public interface Listener { void onColorChanged(int color); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF svRect = new RectF();
    private final RectF hueRect = new RectF();
    private final float[] hsv = new float[]{205f, .55f, .95f};
    private Listener listener;
    private int activeZone;

    public ColorPickerView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(Ui.dp(context, 2));
        markerPaint.setColor(Color.WHITE);
        setColor(Color.rgb(105, 171, 255));
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
        if (listener != null) listener.onColorChanged(getColor());
    }

    public int getColor() { return Color.HSVToColor(hsv); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        int pad = Ui.dp(getContext(), 2);
        int hueH = Ui.dp(getContext(), 28);
        int gap = Ui.dp(getContext(), 14);
        svRect.set(pad, pad, Math.max(pad + 1, w - pad), Math.max(pad + 1, h - hueH - gap - pad));
        hueRect.set(pad, svRect.bottom + gap, Math.max(pad + 1, w - pad), Math.max(svRect.bottom + gap + 1, h - pad));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Ui.dp(getContext(), 14);

        int hueColor = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
        paint.setShader(new LinearGradient(svRect.left, 0f, svRect.right, 0f,
                Color.WHITE, hueColor, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(svRect, radius, radius, paint);
        paint.setShader(new LinearGradient(0f, svRect.top, 0f, svRect.bottom,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(svRect, radius, radius, paint);
        paint.setShader(null);

        int[] hueColors = new int[]{
                Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                Color.BLUE, Color.MAGENTA, Color.RED
        };
        paint.setShader(new LinearGradient(hueRect.left, 0f, hueRect.right, 0f,
                hueColors, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(hueRect, hueRect.height() / 2f, hueRect.height() / 2f, paint);
        paint.setShader(null);

        float sx = svRect.left + hsv[1] * svRect.width();
        float sy = svRect.top + (1f - hsv[2]) * svRect.height();
        drawMarker(canvas, sx, sy, Ui.dp(getContext(), 8));

        float hx = hueRect.left + (hsv[0] / 360f) * hueRect.width();
        drawMarker(canvas, hx, hueRect.centerY(), Ui.dp(getContext(), 7));
    }

    private void drawMarker(Canvas canvas, float x, float y, float radius) {
        markerPaint.setColor(Color.argb(120, 0, 0, 0));
        markerPaint.setStrokeWidth(Ui.dp(getContext(), 4));
        canvas.drawCircle(x, y, radius + Ui.dp(getContext(), 1), markerPaint);
        markerPaint.setColor(Color.WHITE);
        markerPaint.setStrokeWidth(Ui.dp(getContext(), 2));
        canvas.drawCircle(x, y, radius, markerPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (hueRect.contains(x, y)) activeZone = 2;
                else if (svRect.contains(x, y)) activeZone = 1;
                else return false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                updateFromTouch(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeZone == 0) return false;
                updateFromTouch(x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeZone != 0) {
                    updateFromTouch(x, y);
                    activeZone = 0;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    performClick();
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void updateFromTouch(float x, float y) {
        if (activeZone == 2) {
            float p = clamp01((x - hueRect.left) / Math.max(1f, hueRect.width()));
            hsv[0] = p * 360f;
        } else if (activeZone == 1) {
            hsv[1] = clamp01((x - svRect.left) / Math.max(1f, svRect.width()));
            hsv[2] = 1f - clamp01((y - svRect.top) / Math.max(1f, svRect.height()));
        }
        invalidate();
        if (listener != null) listener.onColorChanged(getColor());
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
