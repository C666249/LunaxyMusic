package com.xingyu.music.ui;

import android.animation.TimeInterpolator;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Build;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.WeakHashMap;

/**
 * Lunaxy Motion System 1.0.
 *
 * Lightweight spring-like motion primitives built only on platform ViewPropertyAnimator so
 * drag/reorder polish does not add another runtime dependency or touch playback threads.
 */
public final class SpringMotion {
    private static final WeakHashMap<View, Float> SHIFT_TARGETS = new WeakHashMap<>();

    private SpringMotion() {}

    public static final TimeInterpolator SNAPPY = new DampedSpringInterpolator(10.6, 7.2);
    public static final TimeInterpolator SOFT = new DampedSpringInterpolator(9.6, 6.4);
    public static final TimeInterpolator LAND = new DampedSpringInterpolator(11.8, 7.4);
    /** Press-release spring: fast response, no visible rubber-band wobble. */
    public static final TimeInterpolator PRESS = new DampedSpringInterpolator(13.4, 7.0);
    /** Paging spring: slightly softer so a finger-driven page can settle naturally. */
    public static final TimeInterpolator PAGE = new DampedSpringInterpolator(10.4, 6.6);
    /** Mini-player to full-player surface expansion: monotonic, cinematic, and deliberately non-bouncy. */
    public static final TimeInterpolator PLAYER_OPEN = new PathInterpolator(.18f, .78f, .18f, 1f);
    /** Drag-neighbour follow curve: critically damped feel with no overshoot while the finger is moving. */
    public static final TimeInterpolator FOLLOW = input -> {
        float t = Math.max(0f, Math.min(1f, input));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    };

    public static void lift(View view) {
        if (view == null) return;
        view.animate().cancel();
        if (Build.VERSION.SDK_INT >= 21) view.setTranslationZ(dp(view, 12));
        view.animate().scaleX(1.035f).scaleY(1.035f).alpha(.965f)
                .setDuration(150L).setInterpolator(SNAPPY).start();
    }

    public static void ghostSource(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.animate().scaleX(.985f).scaleY(.985f).alpha(.14f)
                .setDuration(115L).setInterpolator(SNAPPY).start();
    }

    public static void shiftY(View view, float translationY) {
        shiftYElastic(view, translationY, 1f);
    }

    /**
     * Reorder neighbour motion.  The row travels the requested slot distance, but compresses by
     * only a few tenths of a percent while moving.  This reads as a soft connected spring without
     * changing hit-test geometry or creating the V91 feedback loop.
     */
    public static void shiftYElastic(View view, float translationY, float intensity) {
        if (view == null) return;
        Float previous = SHIFT_TARGETS.get(view);
        if (previous != null && Math.abs(previous - translationY) < .5f) return;
        SHIFT_TARGETS.put(view, translationY);
        float f = Math.max(0f, Math.min(1f, intensity));
        float scale = Math.abs(translationY) < .5f ? 1f : (1f - .0045f * f);
        view.animate().cancel();
        long waveDelay = Math.round((1f - f) * 14f);
        view.animate().translationY(translationY).scaleX(scale).scaleY(scale)
                .setStartDelay(waveDelay).setDuration(215L).setInterpolator(SOFT).start();
    }

    /**
     * Continuous drag displacement. Unlike shiftYElastic(), this never overshoots: the target can
     * change every MOVE frame and the row eases toward it, which makes neighbouring cards look as
     * if they are being gradually squeezed aside instead of opening a full slot instantly.
     */
    public static void shiftYFollow(View view, float translationY, float intensity) {
        if (view == null) return;
        Float previous = SHIFT_TARGETS.get(view);
        if (previous != null && Math.abs(previous - translationY) < .75f) return;
        SHIFT_TARGETS.put(view, translationY);
        float f = Math.max(0f, Math.min(1f, intensity));
        float scale = Math.abs(translationY) < .5f ? 1f : (1f - .0026f * f);
        // Reorder targets are now frame-coalesced by the controller, so a slightly longer critically
        // damped follow reads as cards being pushed aside rather than snapping open.
        view.animate().translationY(translationY).scaleX(scale).scaleY(scale)
                .setStartDelay(0L).setDuration(168L).setInterpolator(FOLLOW).start();
    }

    public static void pressDown(View view, float scale) {
        if (view == null) return;
        float target = Math.max(.94f, Math.min(.985f, scale));
        view.animate().cancel();
        view.animate().scaleX(target).scaleY(target).alpha(.90f)
                .setDuration(72L).setInterpolator(SNAPPY).start();
    }

    public static void pressUp(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(185L).setInterpolator(PRESS).start();
    }


    /** Immediate visual reset used underneath the floating drag proxy during an atomic drop. */
    public static void resetNow(View view) {
        if (view == null) return;
        SHIFT_TARGETS.remove(view);
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
        if (Build.VERSION.SDK_INT >= 21) view.setTranslationZ(0f);
    }

    public static void restore(View view) {
        if (view == null) return;
        SHIFT_TARGETS.remove(view);
        view.animate().cancel();
        view.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(205L).setInterpolator(LAND)
                .withEndAction(() -> {
                    if (Build.VERSION.SDK_INT >= 21) view.setTranslationZ(0f);
                }).start();
    }

    public static void land(View view, int direction) {
        if (view == null) return;
        SHIFT_TARGETS.remove(view);
        view.animate().cancel();
        view.setAlpha(.70f);
        view.setScaleX(.982f);
        view.setScaleY(.982f);
        view.setTranslationY(dp(view, 12) * (direction >= 0 ? 1f : -1f));
        if (Build.VERSION.SDK_INT >= 21) view.setTranslationZ(dp(view, 5));
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(235L).setInterpolator(LAND)
                .withEndAction(() -> {
                    if (Build.VERSION.SDK_INT >= 21) view.setTranslationZ(0f);
                }).start();
    }

    public static final class LiftedDragShadowBuilder extends View.DragShadowBuilder {
        private final View source;
        private final float scale;

        public LiftedDragShadowBuilder(View source) { this(source, 1.045f); }

        public LiftedDragShadowBuilder(View source, float scale) {
            super(source);
            this.source = source;
            this.scale = Math.max(1f, scale);
        }

        @Override public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            if (source == null) { super.onProvideShadowMetrics(shadowSize, shadowTouchPoint); return; }
            int w = Math.max(1, Math.round(source.getWidth() * scale));
            int h = Math.max(1, Math.round(source.getHeight() * scale));
            shadowSize.set(w, h);
            shadowTouchPoint.set(w / 2, h / 2);
        }

        @Override public void onDrawShadow(Canvas canvas) {
            if (source == null) { super.onDrawShadow(canvas); return; }
            canvas.save();
            float dx = (canvas.getWidth() - source.getWidth() * scale) / 2f;
            float dy = (canvas.getHeight() - source.getHeight() * scale) / 2f;
            canvas.translate(dx, dy);
            canvas.scale(scale, scale);
            source.draw(canvas);
            canvas.restore();
        }
    }

    private static int dp(View view, int dp) {
        return Math.round(dp * view.getResources().getDisplayMetrics().density);
    }

    /** Normalized under-damped oscillator: tiny overshoot, fast settle, no rubber-band wobble. */
    private static final class DampedSpringInterpolator implements TimeInterpolator {
        private final double damping;
        private final double frequency;
        private final double end;

        DampedSpringInterpolator(double damping, double frequency) {
            this.damping = damping;
            this.frequency = frequency;
            this.end = sample(1d);
        }

        private double sample(double t) {
            return 1d - Math.exp(-damping * t) * Math.cos(frequency * t);
        }

        @Override public float getInterpolation(float input) {
            double safeEnd = Math.abs(end) < 1e-6 ? 1d : end;
            return (float) (sample(Math.max(0d, Math.min(1d, input))) / safeEnd);
        }
    }
}
