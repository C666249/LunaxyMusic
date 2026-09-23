package com.xingyu.music.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * Lightweight recommendation-card material with five selectable visual styles.
 *
 * Style 0 intentionally mirrors the V92.8.9 card. The four optional styles stay GPU-cheap:
 * shader/stroke/path drawing only, no RenderEffect/blur bitmap pass and no WebView layer.
 */
public final class RecommendationGlassDrawable extends Drawable implements Runnable {
    public static final int STYLE_ORIGINAL = 0;
    public static final int STYLE_AURORA = 1;
    public static final int STYLE_PRISM = 2;
    public static final int STYLE_MOON_FROST = 3;
    public static final int STYLE_DEEP_CRYSTAL = 4;
    public static final int STYLE_COUNT = 5;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF innerRect = new RectF();
    private final Matrix matrix = new Matrix();
    private final Path clipPath = new Path();
    private final float density;
    private final boolean animated;

    private int style;
    private int accent;
    private int externalAlpha = 255;
    private int shaderStyle = -1;
    private int shaderAccent;
    private int shaderW = -1;
    private int shaderH = -1;
    private LinearGradient baseGradient;
    private LinearGradient movingBand;
    private SweepGradient prismSweep;
    private RadialGradient frostBloom;

    public RecommendationGlassDrawable(float density, int style, int accent, boolean animated) {
        this.density = Math.max(.75f, density);
        this.style = clampStyle(style);
        this.accent = accent;
        this.animated = animated;
        fill.setStyle(Paint.Style.FILL);
        glow.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeCap(Paint.Cap.ROUND);
        edge.setStrokeJoin(Paint.Join.ROUND);
        hairline.setStyle(Paint.Style.STROKE);
        hairline.setStrokeCap(Paint.Cap.ROUND);
        detail.setStyle(Paint.Style.STROKE);
        detail.setStrokeCap(Paint.Cap.ROUND);
        detail.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setStyle(int value) {
        int next = clampStyle(value);
        if (next == style) return;
        style = next;
        invalidateShaders();
        invalidateSelf();
    }

    public void setAccent(int color) {
        if (color == accent) return;
        accent = color;
        invalidateShaders();
        invalidateSelf();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        invalidateShaders();
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float inset = 1.0f * density;
        float radius = 20f * density;
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        innerRect.set(rect);
        innerRect.inset(1.25f * density, 1.25f * density);
        if (style != STYLE_ORIGINAL) ensureShaders(b.width(), b.height());

        switch (style) {
            case STYLE_AURORA:
                drawAurora(canvas, radius);
                break;
            case STYLE_PRISM:
                drawPrism(canvas, radius);
                break;
            case STYLE_MOON_FROST:
                drawMoonFrost(canvas, radius);
                break;
            case STYLE_DEEP_CRYSTAL:
                drawDeepCrystal(canvas, radius);
                break;
            case STYLE_ORIGINAL:
            default:
                drawOriginal(canvas, radius);
                break;
        }

        if (animated && isAnimatedStyle(style) && isVisible()) {
            unscheduleSelf(this);
            scheduleSelf(this, SystemClock.uptimeMillis() + 84L); // ~12 fps, intentionally calm.
        }
    }

    private void drawOriginal(Canvas canvas, float radius) {
        fill.setShader(null);
        fill.setColor(Color.argb(scaleAlpha(164), 11, 11, 16));
        canvas.drawRoundRect(rect, radius, radius, fill);
        hairline.setShader(null);
        hairline.setStrokeWidth(1f * density);
        hairline.setColor(Color.argb(scaleAlpha(22), 255, 255, 255));
        canvas.drawRoundRect(rect, radius, radius, hairline);
    }

    private void drawAurora(Canvas canvas, float radius) {
        fill.setShader(baseGradient);
        fill.setAlpha(scaleAlpha(255));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        float phase = phase(12800L);
        matrix.reset();
        matrix.setTranslate((phase * 2f - 1f) * rect.width() * .62f, 0f);
        movingBand.setLocalMatrix(matrix);
        glow.setShader(movingBand);
        glow.setAlpha(scaleAlpha(185));
        canvas.drawRoundRect(rect, radius, radius, glow);
        glow.setShader(null);

        int light = mix(accent, Color.WHITE, .42f);
        edge.setShader(null);
        edge.setStrokeWidth(2.7f * density);
        edge.setColor(withAlpha(light, scaleAlpha(28)));
        canvas.drawRoundRect(rect, radius, radius, edge);
        hairline.setStrokeWidth(.85f * density);
        hairline.setColor(withAlpha(light, scaleAlpha(112)));
        canvas.drawRoundRect(innerRect, Math.max(0f, radius - 1.25f * density), Math.max(0f, radius - 1.25f * density), hairline);
    }

    private void drawPrism(Canvas canvas, float radius) {
        fill.setShader(baseGradient);
        fill.setAlpha(scaleAlpha(255));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        float phase = phase(11600L);
        matrix.reset();
        matrix.setRotate(phase * 360f, rect.centerX(), rect.centerY());
        prismSweep.setLocalMatrix(matrix);
        edge.setShader(prismSweep);
        edge.setStrokeWidth(3.25f * density);
        edge.setAlpha(scaleAlpha(45));
        canvas.drawRoundRect(rect, radius, radius, edge);
        edge.setStrokeWidth(1.05f * density);
        edge.setAlpha(scaleAlpha(190));
        canvas.drawRoundRect(innerRect, Math.max(0f, radius - 1.25f * density), Math.max(0f, radius - 1.25f * density), edge);
        edge.setShader(null);

        hairline.setStrokeWidth(.45f * density);
        hairline.setColor(Color.argb(scaleAlpha(58), 255, 255, 255));
        canvas.drawRoundRect(innerRect, Math.max(0f, radius - 1.25f * density), Math.max(0f, radius - 1.25f * density), hairline);
    }

    private void drawMoonFrost(Canvas canvas, float radius) {
        fill.setShader(baseGradient);
        fill.setAlpha(scaleAlpha(255));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        glow.setShader(frostBloom);
        glow.setAlpha(scaleAlpha(200));
        canvas.drawRoundRect(rect, radius, radius, glow);
        glow.setShader(null);

        // Deterministic micro-grain. It looks like misted glass without raster blur/noise textures.
        int seed = accent ^ 0x51a93d;
        detail.setStyle(Paint.Style.FILL);
        detail.setColor(Color.argb(scaleAlpha(18), 255, 255, 255));
        float w = Math.max(1f, rect.width()), h = Math.max(1f, rect.height());
        for (int i = 0; i < 15; i++) {
            seed = seed * 1103515245 + 12345;
            float x = rect.left + (((seed >>> 8) & 1023) / 1023f) * w;
            seed = seed * 1103515245 + 12345;
            float y = rect.top + (((seed >>> 9) & 1023) / 1023f) * h;
            canvas.drawCircle(x, y, (.32f + (i % 3) * .14f) * density, detail);
        }
        detail.setStyle(Paint.Style.STROKE);

        int frost = mix(accent, Color.WHITE, .72f);
        edge.setStrokeWidth(2.8f * density);
        edge.setColor(withAlpha(frost, scaleAlpha(34)));
        canvas.drawRoundRect(rect, radius, radius, edge);
        hairline.setStrokeWidth(.85f * density);
        hairline.setColor(Color.argb(scaleAlpha(130), 238, 244, 255));
        canvas.drawRoundRect(innerRect, Math.max(0f, radius - 1.25f * density), Math.max(0f, radius - 1.25f * density), hairline);
    }

    private void drawDeepCrystal(Canvas canvas, float radius) {
        fill.setShader(baseGradient);
        fill.setAlpha(scaleAlpha(255));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clipPath);
        float w = rect.width(), h = rect.height();
        detail.setStyle(Paint.Style.STROKE);
        detail.setStrokeWidth(.72f * density);
        detail.setColor(withAlpha(mix(accent, Color.WHITE, .50f), scaleAlpha(34)));
        Path facets = new Path();
        facets.moveTo(rect.left + w * .05f, rect.top + h * .28f);
        facets.lineTo(rect.left + w * .33f, rect.top + h * .08f);
        facets.lineTo(rect.left + w * .58f, rect.top + h * .43f);
        facets.lineTo(rect.right - w * .06f, rect.top + h * .17f);
        facets.moveTo(rect.left + w * .18f, rect.bottom - h * .06f);
        facets.lineTo(rect.left + w * .58f, rect.top + h * .43f);
        facets.lineTo(rect.right - w * .12f, rect.bottom - h * .08f);
        facets.moveTo(rect.left + w * .33f, rect.top + h * .08f);
        facets.lineTo(rect.left + w * .18f, rect.bottom - h * .06f);
        canvas.drawPath(facets, detail);

        float phase = phase(14200L);
        matrix.reset();
        matrix.setTranslate((phase * 2f - 1f) * w * .75f, 0f);
        movingBand.setLocalMatrix(matrix);
        glow.setShader(movingBand);
        glow.setAlpha(scaleAlpha(132));
        canvas.drawRect(rect, glow);
        glow.setShader(null);
        canvas.restoreToCount(save);

        edge.setStrokeWidth(2.75f * density);
        edge.setColor(withAlpha(mix(accent, Color.WHITE, .45f), scaleAlpha(32)));
        canvas.drawRoundRect(rect, radius, radius, edge);
        hairline.setStrokeWidth(.82f * density);
        hairline.setColor(withAlpha(mix(accent, Color.WHITE, .62f), scaleAlpha(126)));
        canvas.drawRoundRect(innerRect, Math.max(0f, radius - 1.25f * density), Math.max(0f, radius - 1.25f * density), hairline);
    }

    private void ensureShaders(int width, int height) {
        if (shaderStyle == style && shaderAccent == accent && shaderW == width && shaderH == height
                && baseGradient != null && movingBand != null && prismSweep != null && frostBloom != null) return;
        float l = rect.left, t = rect.top, r = rect.right, b = rect.bottom;
        int deep = mix(accent, Color.BLACK, .78f);
        int deeper = mix(accent, Color.BLACK, .90f);
        int soft = mix(accent, Color.WHITE, .18f);
        int cool = analogous(accent, 24f, .82f, .98f);
        int warm = analogous(accent, -28f, .80f, 1.00f);

        if (style == STYLE_MOON_FROST) {
            baseGradient = new LinearGradient(l, t, r, b,
                    new int[]{Color.argb(188, 15, 17, 25), Color.argb(178, 31, 34, 45), withAlpha(deep, 122)},
                    new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
        } else if (style == STYLE_DEEP_CRYSTAL) {
            baseGradient = new LinearGradient(l, t, r, b,
                    new int[]{Color.argb(205, 7, 9, 16), withAlpha(deeper, 182), Color.argb(212, 6, 8, 14)},
                    new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
        } else {
            baseGradient = new LinearGradient(l, t, r, b,
                    new int[]{Color.argb(194, 8, 10, 17), withAlpha(deep, 120), Color.argb(196, 9, 10, 17)},
                    new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
        }

        movingBand = new LinearGradient(l - rect.width() * .55f, t, r + rect.width() * .55f, b,
                new int[]{Color.TRANSPARENT, withAlpha(cool, 16), withAlpha(soft, 52), withAlpha(warm, 22), Color.TRANSPARENT},
                new float[]{0f, .30f, .48f, .66f, 1f}, Shader.TileMode.CLAMP);
        prismSweep = new SweepGradient(rect.centerX(), rect.centerY(),
                new int[]{soft, cool, Color.rgb(214, 177, 255), warm, Color.rgb(255, 218, 148), soft},
                new float[]{0f, .18f, .38f, .61f, .81f, 1f});
        frostBloom = new RadialGradient(l + rect.width() * .24f, t + rect.height() * .05f,
                Math.max(rect.width(), rect.height()) * .92f,
                new int[]{Color.argb(46, 250, 252, 255), withAlpha(soft, 22), Color.TRANSPARENT},
                new float[]{0f, .46f, 1f}, Shader.TileMode.CLAMP);
        shaderStyle = style;
        shaderAccent = accent;
        shaderW = width;
        shaderH = height;
    }

    private void invalidateShaders() {
        shaderStyle = -1;
        baseGradient = null;
        movingBand = null;
        prismSweep = null;
        frostBloom = null;
    }

    @Override public void run() {
        if (!animated || !isVisible() || !isAnimatedStyle(style)) return;
        invalidateSelf();
    }

    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) unscheduleSelf(this);
        else if (animated && isAnimatedStyle(style)) invalidateSelf();
        return changed;
    }

    @Override public void setAlpha(int alpha) {
        externalAlpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        glow.setColorFilter(colorFilter);
        edge.setColorFilter(colorFilter);
        hairline.setColorFilter(colorFilter);
        detail.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private int scaleAlpha(int alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * (externalAlpha / 255f))));
    }

    private static int clampStyle(int style) {
        return Math.max(STYLE_ORIGINAL, Math.min(STYLE_DEEP_CRYSTAL, style));
    }

    private static boolean isAnimatedStyle(int style) {
        return style == STYLE_AURORA || style == STYLE_PRISM || style == STYLE_DEEP_CRYSTAL;
    }

    private static float phase(long periodMs) {
        return (SystemClock.uptimeMillis() % Math.max(1000L, periodMs)) / (float) Math.max(1000L, periodMs);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int mix(int a, int b, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    private static int analogous(int color, float hueShift, float satScale, float valueScale) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = Math.max(.12f, Math.min(.92f, hsv[1] * satScale));
        hsv[2] = Math.max(.35f, Math.min(1f, hsv[2] * valueScale));
        return Color.HSVToColor(hsv);
    }
}
