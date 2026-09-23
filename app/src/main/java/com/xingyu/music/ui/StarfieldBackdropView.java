package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Very light ambient colour layer for the now-playing background.
 *
 * It is intentionally separate from both the exact V49 2D starfield and the V55 3D renderer:
 * switching 2D/3D therefore never changes how the ambient backdrop is configured.  Pure-black
 * mode draws nothing at all.  Coloured modes use two broad radial gradients whose edges fade to
 * full transparency, so the page gains atmosphere without becoming a coloured wallpaper.
 */
public final class StarfieldBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private Shader primaryShader;
    private Shader secondaryShader;
    private int color = Color.rgb(110, 160, 255);
    private float strength;

    public StarfieldBackdropView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        rebuildShaders();
    }

    public void setAmbientColor(int color) {
        if (this.color == color) return;
        this.color = color;
        rebuildShaders();
        invalidate();
    }

    public void setStrength(float strength) {
        this.strength = clamp(strength, 0f, 1f);
        invalidate();
    }

    public float strength() { return strength; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (strength <= .002f || getWidth() <= 0 || getHeight() <= 0) return;

        final float w = getWidth();
        final float h = getHeight();
        final float base = Math.max(w, h);

        if (primaryShader != null) {
            float radius = base * .72f;
            matrix.reset();
            matrix.setScale(radius, radius);
            matrix.postTranslate(w * .50f, h * .36f);
            primaryShader.setLocalMatrix(matrix);
            paint.setShader(primaryShader);
            paint.setAlpha(Math.round(255f * strength));
            canvas.drawCircle(w * .50f, h * .36f, radius, paint);
        }

        if (secondaryShader != null) {
            float radius = base * .58f;
            matrix.reset();
            matrix.setScale(radius, radius);
            matrix.postTranslate(w * .20f, h * .78f);
            secondaryShader.setLocalMatrix(matrix);
            paint.setShader(secondaryShader);
            paint.setAlpha(Math.round(255f * strength * .58f));
            canvas.drawCircle(w * .20f, h * .78f, radius, paint);
        }

        paint.setShader(null);
        paint.setAlpha(255);
    }

    private void rebuildShaders() {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        int hot = Color.argb(48, r, g, b);
        int mid = Color.argb(21, r, g, b);
        int haze = Color.argb(7, r, g, b);
        int zero = Color.argb(0, r, g, b);
        primaryShader = new RadialGradient(0f, 0f, 1f,
                new int[]{hot, mid, haze, zero},
                new float[]{0f, .28f, .63f, 1f}, Shader.TileMode.CLAMP);

        int r2 = mix(r, 122, .26f);
        int g2 = mix(g, 178, .26f);
        int b2 = mix(b, 255, .26f);
        secondaryShader = new RadialGradient(0f, 0f, 1f,
                new int[]{Color.argb(30, r2, g2, b2), Color.argb(10, r2, g2, b2), zero},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
    }

    private static int mix(int a, int b, float t) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
