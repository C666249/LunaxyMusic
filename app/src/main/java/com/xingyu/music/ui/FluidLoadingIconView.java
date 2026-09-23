package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Dependency-free 24-grid loading glyph. The twin arc language is compatible with Lunaxy's
 * Iconsax-inspired linear icons, but the drawing/animation is authored locally.
 */
public final class FluidLoadingIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private int color;
    private float rotation;
    private ValueAnimator animator;

    public FluidLoadingIconView(Context context, int color) {
        super(context);
        this.color = color;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setIconColor(int color) { this.color = color; invalidate(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    public void start() {
        stop();
        if (SpringMotion.isReducedMotion()) { rotation = 0f; invalidate(); return; }
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(1120L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> { rotation = (Float) a.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    public void stop() {
        if (animator != null) animator.cancel();
        animator = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight(), s = Math.min(w, h);
        float cx = w * .5f, cy = h * .5f;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(1.5f, s * .065f));
        RectF oval = new RectF(cx - s * .27f, cy - s * .27f, cx + s * .27f, cy + s * .27f);
        canvas.save();
        canvas.rotate(rotation, cx, cy);
        canvas.drawArc(oval, -42f, 132f, false, paint);
        canvas.drawArc(oval, 138f, 132f, false, paint);
        drawArrow(canvas, cx + s * .18f, cy - s * .205f, 1f, s);
        drawArrow(canvas, cx - s * .18f, cy + s * .205f, -1f, s);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, s * .035f, paint);
        canvas.restore();
    }

    private void drawArrow(Canvas c, float x, float y, float dir, float s) {
        arrow.reset();
        arrow.moveTo(x, y);
        arrow.lineTo(x - dir * s * .09f, y - s * .018f);
        arrow.moveTo(x, y);
        arrow.lineTo(x - dir * s * .026f, y + s * .085f);
        c.drawPath(arrow, paint);
    }
}
