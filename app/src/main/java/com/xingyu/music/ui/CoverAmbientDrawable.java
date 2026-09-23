package com.xingyu.music.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A restrained album-colour halo for recent-cover tiles. No blur pass or animation. */
public final class CoverAmbientDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF inner = new RectF();
    private final float density;
    private int accent = Ui.CYAN;
    private int alpha = 255;
    private LinearGradient gradient;
    private int builtAccent;
    private int builtW = -1;
    private int builtH = -1;

    public CoverAmbientDrawable(float density) {
        this.density = Math.max(.75f, density);
        fill.setStyle(Paint.Style.FILL);
        outer.setStyle(Paint.Style.STROKE);
        outer.setStrokeCap(Paint.Cap.ROUND);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setAccent(int color) {
        if (accent == color) return;
        accent = soften(color);
        gradient = null;
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        gradient = null;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float radius = 17f * density;
        rect.set(b.left + .7f * density, b.top + .7f * density, b.right - .7f * density, b.bottom - .7f * density);
        inner.set(rect);
        inner.inset(2.15f * density, 2.15f * density);
        ensureGradient(b.width(), b.height());

        fill.setShader(gradient);
        fill.setAlpha(scale(255));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        outer.setStrokeWidth(4.4f * density);
        outer.setColor(withAlpha(accent, scale(26)));
        canvas.drawRoundRect(rect, radius, radius, outer);

        edge.setStrokeWidth(.85f * density);
        edge.setColor(withAlpha(mix(accent, Color.WHITE, .42f), scale(112)));
        canvas.drawRoundRect(inner, Math.max(0f, radius - 2.15f * density), Math.max(0f, radius - 2.15f * density), edge);
    }

    private void ensureGradient(int w, int h) {
        if (gradient != null && builtAccent == accent && builtW == w && builtH == h) return;
        int deep = mix(accent, Color.BLACK, .70f);
        gradient = new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{withAlpha(accent, 52), withAlpha(deep, 24), Color.argb(4, 255, 255, 255), withAlpha(accent, 38)},
                new float[]{0f, .32f, .67f, 1f}, Shader.TileMode.CLAMP);
        builtAccent = accent;
        builtW = w;
        builtH = h;
    }

    @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter colorFilter) { fill.setColorFilter(colorFilter); outer.setColorFilter(colorFilter); edge.setColorFilter(colorFilter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private int scale(int value) { return Math.max(0, Math.min(255, Math.round(value * alpha / 255f))); }
    private static int withAlpha(int color, int a) { return Color.argb(Math.max(0, Math.min(255, a)), Color.red(color), Color.green(color), Color.blue(color)); }
    private static int mix(int a, int b, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
    private static int soften(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(.72f, hsv[1] * .82f);
        hsv[2] = Math.max(.48f, Math.min(.94f, hsv[2]));
        return Color.HSVToColor(hsv);
    }
}
