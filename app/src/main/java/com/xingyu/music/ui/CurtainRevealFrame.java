package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Structure-preserving placeholder -> content handoff.
 *
 * V92.9.11 treats the handoff as a literal curtain instead of a fast clip/cross-fade: the resolved
 * object owns the left side, the skeleton keeps owning the unrevealed right side, and a soft light
 * seam travels between them.  Geometry never changes, so scrolling can continue while rows resolve.
 */
public final class CurtainRevealFrame extends FrameLayout {
    private View placeholder;
    private View content;
    private ValueAnimator animator;
    private float progress;
    private boolean curtainActive;
    private final Paint seamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CurtainRevealFrame(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
        setWillNotDraw(false);
    }

    public void setPlaceholder(View view) {
        if (placeholder != null && placeholder.getParent() == this) removeView(placeholder);
        placeholder = view;
        if (view != null) addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void reveal(View view) {
        reveal(view, 0L);
    }

    public void reveal(View view, long startDelayMs) {
        if (view == null) return;
        if (animator != null) animator.cancel();
        if (content != null && content.getParent() == this) removeView(content);
        content = view;
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        content.setAlpha(1f);
        content.setTranslationX(0f);
        content.setTranslationY(0f);

        if (SpringMotion.isReducedMotion()) {
            progress = 1f;
            content.setClipBounds(null);
            finishReveal();
            return;
        }

        progress = 0f;
        curtainActive = true;
        post(() -> {
            if (content != view || view.getParent() != this) return;
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            view.setClipBounds(new Rect(0, 0, 1, height));
            view.setAlpha(.58f);
            view.setTranslationX(-dp(7));
            view.setTranslationY(dp(9));
            if (placeholder != null) {
                placeholder.setAlpha(1f);
                placeholder.setTranslationX(0f);
                placeholder.setClipBounds(new Rect(0, 0, width, height));
            }
            animator = ValueAnimator.ofFloat(progress, 1f);
            animator.setStartDelay(Math.max(0L, startDelayMs));
            animator.setDuration(470L);
            animator.setInterpolator(input -> {
                float t = Math.max(0f, Math.min(1f, input));
                // Fast enough to acknowledge the row, slow through the middle so the travelling
                // curtain edge is readable instead of looking like a delayed instant replacement.
                return t * t * (3f - 2f * t);
            });
            animator.addUpdateListener(a -> {
                progress = (Float) a.getAnimatedValue();
                int w = Math.max(1, getWidth());
                int h = Math.max(1, getHeight());
                int feather = dp(18);
                int edge = Math.max(1, Math.min(w, Math.round(w * progress)));
                int contentRight = Math.min(w, edge + feather / 2);
                int placeholderLeft = Math.max(0, edge - feather / 2);
                view.setClipBounds(new Rect(0, 0, contentRight, h));
                view.setAlpha(.58f + .42f * progress);
                view.setTranslationX(-dp(7) * (1f - progress));
                view.setTranslationY(dp(9) * (1f - progress));
                if (placeholder != null) {
                    placeholder.setClipBounds(new Rect(placeholderLeft, 0, w, h));
                    placeholder.setAlpha(1f - .16f * progress);
                    placeholder.setTranslationX(dp(4) * progress);
                }
                invalidate();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (!cancelled && content == view) finishReveal();
                }
            });
            animator.start();
        });
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!curtainActive || progress <= 0f || progress >= 1f || getWidth() <= 0) return;
        float x = getWidth() * progress;
        float spread = dp(20);
        int center = AppearanceSystem.isLight() ? Color.argb(78, 255, 255, 255) : Color.argb(82, 172, 211, 255);
        int transparent = Color.argb(0, 255, 255, 255);
        seamPaint.setShader(new LinearGradient(x - spread, 0f, x + spread, 0f,
                new int[]{transparent, center, transparent}, new float[]{0f, .54f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(x - spread, 0f, x + spread, getHeight(), seamPaint);
        seamPaint.setShader(null);
    }

    public void showImmediately(View view) {
        if (view == null) return;
        if (animator != null) animator.cancel();
        animator = null;
        curtainActive = false;
        progress = 1f;
        if (content != null && content != view && content.getParent() == this) removeView(content);
        content = view;
        if (view.getParent() != this) addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.setClipBounds(null);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        if (placeholder != null) {
            placeholder.setClipBounds(null);
            placeholder.setAlpha(1f);
            placeholder.setTranslationX(0f);
            if (placeholder.getParent() == this) removeView(placeholder);
        }
        placeholder = null;
        invalidate();
    }

    public void cancelReveal() {
        if (animator != null) animator.cancel();
        animator = null;
        curtainActive = false;
        invalidate();
    }

    private void finishReveal() {
        progress = 1f;
        curtainActive = false;
        if (content != null) {
            content.setClipBounds(null);
            content.setAlpha(1f);
            content.setTranslationX(0f);
            content.setTranslationY(0f);
        }
        if (placeholder != null) {
            placeholder.setClipBounds(null);
            placeholder.setAlpha(1f);
            placeholder.setTranslationX(0f);
            if (placeholder.getParent() == this) removeView(placeholder);
        }
        placeholder = null;
        animator = null;
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        cancelReveal();
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
