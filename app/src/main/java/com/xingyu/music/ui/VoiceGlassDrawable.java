package com.xingyu.music.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Lunaxy Voice transient material.
 *
 * Voice is an interaction layer rather than content, so it keeps a stronger separation than a
 * normal card. The material follows the global appearance tokens and becomes essentially opaque
 * when Reduced Transparency is enabled; Moonlight gets a paper-white lens instead of the old dark
 * panel pasted on top of a light canvas.
 */
public final class VoiceGlassDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float phase;
    private float compactProgress;

    public VoiceGlassDrawable(float density) {
        this.density = Math.max(.75f, density);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.10f * this.density);
    }

    public void setPhase(float phase) { this.phase = phase; invalidateSelf(); }
    public void setCompactProgress(float value) { compactProgress = Math.max(0f, Math.min(1f, value)); invalidateSelf(); }
    public float getCompactProgress() { return compactProgress; }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        RectF r = new RectF(b.left, b.top, b.right, b.bottom);
        float radius = (27f + compactProgress * 6f) * density;
        boolean light = AppearanceSystem.isLight();
        boolean opaque = AppearanceSystem.reduceTransparency();

        int start;
        int middle;
        int end;
        if (light) {
            int paper = Color.rgb(255, 255, 255);
            int cool = Ui.mix(Color.rgb(250, 252, 255), Ui.CYAN, .055f);
            int violet = Ui.mix(Color.rgb(251, 250, 255), Ui.PURPLE, .045f);
            int a = opaque ? 255 : 251;
            start = Color.argb(a, Color.red(paper), Color.green(paper), Color.blue(paper));
            middle = Color.argb(a, Color.red(cool), Color.green(cool), Color.blue(cool));
            end = Color.argb(a, Color.red(violet), Color.green(violet), Color.blue(violet));
        } else {
            int a = opaque ? 255 : 238;
            int base = AppearanceSystem.effectiveMode() == AppearanceSystem.MODE_OLED
                    ? Color.rgb(3, 4, 7) : Color.rgb(13, 15, 26);
            int cool = Ui.mix(base, Ui.CYAN, .055f);
            int violet = Ui.mix(base, Ui.PURPLE, .075f);
            start = Color.argb(a, Color.red(base), Color.green(base), Color.blue(base));
            middle = Color.argb(a, Color.red(cool), Color.green(cool), Color.blue(cool));
            end = Color.argb(a, Color.red(violet), Color.green(violet), Color.blue(violet));
        }
        fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{start, middle, end}, new float[]{0f, .52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(r, radius, radius, fill);
        fill.setShader(null);

        int rim = light ? Color.argb(42, 58, 76, 103) : Color.argb(76, 222, 238, 255);
        stroke.setColor(rim);
        canvas.drawRoundRect(new RectF(r.left + density, r.top + density,
                r.right - density, r.bottom - density), radius, radius, stroke);

        // Localized chroma only: no full-panel rainbow glow and no glass-on-glass effect.
        stroke.setColor(light
                ? Color.argb(52, Color.red(Ui.CYAN), Color.green(Ui.CYAN), Color.blue(Ui.CYAN))
                : Color.argb(50, 112, 224, 255));
        RectF chroma = new RectF(r.left + 2.6f * density, r.top + 2.0f * density,
                r.right - 1.4f * density, r.bottom - 2.2f * density);
        canvas.drawArc(chroma, 182f + phase * 10f, 88f, false, stroke);

        stroke.setColor(light ? Color.argb(168, 255, 255, 255) : Color.argb(112, 255, 255, 255));
        RectF shine = new RectF(r.left + 6f * density, r.top + 4f * density,
                r.right - 6f * density, r.bottom - 6f * density);
        canvas.drawArc(shine, 205f, 74f, false, stroke);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { fill.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
