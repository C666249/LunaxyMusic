package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * V92.8 Mini -> Full player surface morph.
 *
 * Draws one continuous rounded surface that expands from the mini-player bounds to the full app
 * viewport.  The view lives below the real full-player overlay, so it can provide spatial
 * continuity without scaling/squashing the player's actual controls or changing hit geometry.
 */
public final class PlayerSurfaceMorphView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF start = new RectF();
    private final RectF end = new RectF();
    private final RectF current = new RectF();
    private float progress;
    private float startRadiusPx;
    private int accent = Ui.PURPLE;

    public PlayerSurfaceMorphView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        startRadiusPx = Ui.dp(context, 20);
        fill.setStyle(Paint.Style.FILL);
        tint.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, Ui.dp(context, 1)));
    }

    public void setStartRect(float left, float top, float right, float bottom) {
        start.set(left, top, right, bottom);
        invalidate();
    }

    public void setEndRect(float left, float top, float right, float bottom) {
        end.set(left, top, right, bottom);
        invalidate();
    }

    public void setStartRadiusDp(float dp) {
        startRadiusPx = Ui.dp(getContext(), dp);
        invalidate();
    }

    public void setAccentColor(int color) {
        accent = color;
        invalidate();
    }

    public void setProgress(float value) {
        progress = clamp(value);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (start.isEmpty() || end.isEmpty()) return;

        float p = progress;
        current.set(
                lerp(start.left, end.left, p),
                lerp(start.top, end.top, p),
                lerp(start.right, end.right, p),
                lerp(start.bottom, end.bottom, p)
        );
        float radius = startRadiusPx * (1f - p);

        // Keep p=0 visually identical to the existing mini player; the new surface becomes opaque
        // over the first few frames, then owns the background as it unfolds to full screen.
        float opacity = smoothstep(clamp(p / .12f));
        fill.setColor(Color.argb(Math.round(255f * opacity), 0, 0, 0));
        canvas.drawRoundRect(current, radius, radius, fill);

        // A very restrained cover-tinted wash prevents the expanding surface from reading as a
        // plain black rectangle, while the real star field takes over during the second half.
        int tintAlpha = Math.round(24f * opacity * (1f - .45f * p));
        tint.setColor(Color.argb(tintAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawRoundRect(current, radius, radius, tint);

        // Preserve the mini glass edge at lift-off and dissolve it before the surface reaches full.
        int edgeAlpha = Math.round(58f * opacity * (1f - smoothstep(clamp(p / .62f))));
        if (edgeAlpha > 0) {
            edge.setColor(Color.argb(edgeAlpha, 255, 255, 255));
            float inset = edge.getStrokeWidth() * .5f;
            RectF stroke = new RectF(current);
            stroke.inset(inset, inset);
            canvas.drawRoundRect(stroke, Math.max(0f, radius - inset), Math.max(0f, radius - inset), edge);
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float smoothstep(float v) { return v * v * (3f - 2f * v); }
}
