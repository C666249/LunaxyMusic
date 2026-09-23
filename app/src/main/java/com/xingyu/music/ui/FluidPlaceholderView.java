package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Structure-matched skeleton. Shimmer is intentionally subtle and bounded to the placeholder. */
public final class FluidPlaceholderView extends View {
    public static final int SONG_ROW = 1;
    public static final int CARD = 2;
    public static final int TITLE = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    private int accent;
    private ValueAnimator shimmer;
    private float phase = -.45f;
    private long shimmerStartDelayMs;

    public FluidPlaceholderView(Context context, int kind, int accent) {
        super(context);
        this.kind = kind;
        this.accent = accent;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setShimmerStartDelay(long delayMs) {
        shimmerStartDelayMs = Math.max(0L, delayMs);
        if (isAttachedToWindow() && !SpringMotion.isReducedMotion()) startShimmer();
    }

    /** Retint an in-flight skeleton without restarting its shimmer phase. */
    public void setAccent(int color) {
        if (accent == color) return;
        accent = color;
        invalidate();
    }

    public boolean isSongRow() { return kind == SONG_ROW; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!SpringMotion.isReducedMotion()) startShimmer();
    }

    @Override protected void onDetachedFromWindow() {
        stopShimmer();
        super.onDetachedFromWindow();
    }

    private void startShimmer() {
        stopShimmer();
        shimmer = ValueAnimator.ofFloat(-.45f, 1.45f);
        shimmer.setDuration(1450L);
        shimmer.setStartDelay(shimmerStartDelayMs);
        shimmer.setRepeatCount(ValueAnimator.INFINITE);
        shimmer.setInterpolator(new LinearInterpolator());
        shimmer.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidate(); });
        shimmer.start();
    }

    private void stopShimmer() {
        if (shimmer != null) shimmer.cancel();
        shimmer = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int base = AppearanceSystem.isLight() ? Color.rgb(228, 231, 238) : Color.rgb(33, 36, 45);
        int accentSoft = Color.rgb(Color.red(accent), Color.green(accent), Color.blue(accent));
        float band = Math.max(dp(70), w * .22f);
        float center = phase * w;
        int lowA = AppearanceSystem.isLight() ? 150 : 112;
        int hiA = AppearanceSystem.isLight() ? 205 : 158;
        paint.setShader(new LinearGradient(center - band, 0, center + band, 0,
                new int[]{withAlpha(base, lowA), withAlpha(accentSoft, hiA), withAlpha(base, lowA)},
                new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP));

        if (kind == SONG_ROW) drawSongRow(canvas, w, h);
        else if (kind == TITLE) drawTitle(canvas, w, h);
        else drawCard(canvas, w, h);
        paint.setShader(null);
    }

    private void drawSongRow(Canvas c, int w, int h) {
        float y = (h - dp(56)) * .5f;
        round(c, dp(9), y, dp(65), y + dp(56), dp(12));
        float left = dp(78);
        round(c, left, y + dp(8), Math.min(w - dp(118), left + w * .43f), y + dp(20), dp(6));
        round(c, left, y + dp(34), Math.min(w - dp(158), left + w * .30f), y + dp(44), dp(5));
        round(c, w - dp(104), y + dp(16), w - dp(82), y + dp(38), dp(11));
        round(c, w - dp(66), y + dp(16), w - dp(44), y + dp(38), dp(11));
        round(c, w - dp(28), y + dp(16), w - dp(10), y + dp(38), dp(9));
    }

    private void drawCard(Canvas c, int w, int h) {
        round(c, dp(4), dp(4), w - dp(4), h - dp(4), dp(22));
        round(c, dp(18), dp(18), Math.min(w - dp(18), dp(84)), Math.min(h - dp(18), dp(84)), dp(17));
        round(c, dp(18), Math.max(dp(96), h - dp(54)), Math.min(w - dp(32), dp(160)), Math.max(dp(108), h - dp(38)), dp(7));
        round(c, dp(18), Math.max(dp(119), h - dp(30)), Math.min(w - dp(70), dp(118)), Math.max(dp(129), h - dp(18)), dp(5));
    }

    private void drawTitle(Canvas c, int w, int h) {
        round(c, dp(2), dp(6), Math.min(w - dp(12), w * .52f), Math.max(dp(18), h * .58f), dp(8));
        round(c, dp(2), Math.max(dp(28), h * .72f), Math.min(w - dp(58), w * .36f), h - dp(4), dp(5));
    }

    private void round(Canvas c, float l, float t, float r, float b, float radius) {
        if (r <= l || b <= t) return;
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
