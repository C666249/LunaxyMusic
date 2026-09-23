package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Restrained one-shot route flow for the playback matrix.
 * It is intentionally idle most of the time; a short pulse only appears when a route changes.
 */
public final class SourceFlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String[] labels = {"搜索", "匹配", "选源", "播放"};
    private float progress = -1f;
    private int accent = Ui.CYAN;
    private String routeLabel = "当前线路";
    private ValueAnimator animator;

    public SourceFlowView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setAccent(int color) { accent = color; invalidate(); }
    public void setRouteLabel(String value) {
        routeLabel = value == null || value.trim().isEmpty() ? "当前线路" : value.trim();
        invalidate();
    }

    public void pulse() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SpringMotion.isReducedMotion() ? 120L : 980L);
        animator.setInterpolator(new DecelerateInterpolator(1.25f));
        animator.addUpdateListener(a -> { progress = (Float) a.getAnimatedValue(); invalidate(); });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                progress = -1f; invalidate();
            }
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        animator = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float left = dp(18), right = w - dp(18), lineY = dp(27);
        float step = (right - left) / 3f;

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(34, 255, 255, 255));
        c.drawLine(left, lineY, right, lineY, paint);

        float pulseAlpha = progress < 0f ? 0f : (progress < .78f ? 1f : Math.max(0f, 1f - (progress - .78f) / .22f));
        if (progress >= 0f) {
            float x = left + (right - left) * Math.min(1f, progress * 1.05f);
            float trail = dp(36);
            paint.setStrokeWidth(dp(2.2f));
            paint.setColor(withAlpha(accent, Math.round(155f * pulseAlpha)));
            c.drawLine(Math.max(left, x - trail), lineY, x, lineY, paint);
            paint.setColor(withAlpha(accent, Math.round(70f * pulseAlpha)));
            c.drawCircle(x, lineY, dp(5.5f), paint);
        }

        int reached = progress < 0f ? 3 : Math.max(0, Math.min(3, (int)Math.floor(progress * 4f)));
        for (int i = 0; i < 4; i++) {
            float x = left + step * i;
            boolean hot = progress >= 0f && i <= reached;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(hot ? withAlpha(accent, 210) : Color.argb(54, 255, 255, 255));
            c.drawCircle(x, lineY, hot ? dp(4.2f) : dp(3.2f), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(9.2f));
            paint.setColor(hot ? Color.argb(220, 245, 248, 255) : Color.argb(120, 220, 224, 235));
            c.drawText(labels[i], x, lineY + dp(20), paint);
        }

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(dp(9f));
        paint.setColor(Color.argb(124, 210, 216, 228));
        c.drawText(routeLabel, right, dp(11), paint);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
