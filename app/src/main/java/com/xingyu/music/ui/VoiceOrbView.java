package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Lightweight liquid voice orb; animation is visibility-bound and respects Reduced Motion. */
public final class VoiceOrbView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase;
    private float level;
    private float targetLevel;
    private String state = "armed";
    private ValueAnimator animator;

    public VoiceOrbView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setVoiceState(String state) {
        this.state = state == null ? "armed" : state;
        invalidate();
    }

    public void setAudioLevel(float value) {
        targetLevel = Math.max(0f, Math.min(1f, value));
        if (SpringMotion.isReducedMotion()) {
            level = targetLevel;
            invalidate();
        }
    }

    public void start() {
        if (!isShown()) return;
        if (SpringMotion.isReducedMotion() || !ValueAnimator.areAnimatorsEnabled()) {
            phase = .14f;
            level = targetLevel;
            invalidate();
            return;
        }
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2200L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (Float) a.getAnimatedValue();
            level += (targetLevel - level) * .18f;
            targetLevel *= .96f;
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) animator.cancel();
        animator = null;
    }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); stop(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (isShown()) start(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (!isAttachedToWindow()) return;
        if (isShown()) start(); else stop();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (!isAttachedToWindow()) return;
        if (visibility == VISIBLE && isShown()) start(); else stop();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float cx = w * .5f, cy = h * .5f;
        float size = Math.min(w, h);
        float breathing = SpringMotion.isReducedMotion() ? 0f : (float) Math.sin(phase * Math.PI * 2.0) * .030f;
        float reactive = SpringMotion.isReducedMotion() ? level * .045f : level * .105f;
        float r = size * (.305f + breathing + reactive);
        int accent = accentForState();
        boolean light = AppearanceSystem.isLight();

        paint.setStyle(Paint.Style.FILL);
        int haloAlpha = light ? 58 : 102;
        paint.setShader(new RadialGradient(cx, cy, r * 1.55f,
                new int[]{Color.argb(haloAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        light ? Color.argb(14, 78, 132, 184) : Color.argb(18, 90, 120, 255), Color.TRANSPARENT},
                new float[]{0f, .58f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 1.55f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * .017f);
        paint.setColor(Color.argb(light ? 68 : 88, Color.red(Ui.CYAN), Color.green(Ui.CYAN), Color.blue(Ui.CYAN)));
        canvas.drawCircle(cx - size * .010f, cy, r * 1.02f, paint);
        paint.setColor(Color.argb(light ? 54 : 72, Color.red(Ui.PINK), Color.green(Ui.PINK), Color.blue(Ui.PINK)));
        canvas.drawCircle(cx + size * .010f, cy, r * 1.02f, paint);

        paint.setStyle(Paint.Style.FILL);
        int edge = light ? Ui.mix(Color.WHITE, accent, .20f) : Color.rgb(11, 14, 28);
        paint.setShader(new RadialGradient(cx - r * .28f, cy - r * .34f, r * 1.25f,
                new int[]{light ? Color.rgb(255, 255, 255) : Color.rgb(248, 252, 255),
                        Color.argb(light ? 138 : 112, Color.red(accent), Color.green(accent), Color.blue(accent)), edge},
                new float[]{0f, .38f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(size * .025f);
        paint.setColor(Color.argb(light ? 206 : 178, 255, 255, 255));
        float sweep = 68f + level * 34f;
        canvas.drawArc(cx - r * .78f, cy - r * .78f, cx + r * .78f, cy + r * .78f,
                212f + (SpringMotion.isReducedMotion() ? 0f : phase * 16f), sweep, false, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(light ? Ui.withAlpha(Ui.mix(accent, Color.WHITE, .42f), 190) : Color.argb(150, 255, 255, 255));
        float core = r * (.12f + level * .12f);
        canvas.drawCircle(cx, cy, core, paint);
    }

    private int accentForState() {
        if ("processing".equals(state)) return Ui.PURPLE;
        if ("result".equals(state)) return Ui.GREEN;
        if ("error".equals(state)) return Ui.RED;
        if ("listening".equals(state) || "wake".equals(state)) return Ui.CYAN;
        return Ui.PURPLE;
    }
}
