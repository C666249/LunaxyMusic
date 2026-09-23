package com.xingyu.music.ui;

import android.animation.TimeInterpolator;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Build;
import android.view.Choreographer;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Lunaxy Motion System 1.0.
 *
 * Lightweight spring-like motion primitives built only on platform ViewPropertyAnimator so
 * drag/reorder polish does not add another runtime dependency or touch playback threads.
 */
public final class SpringMotion {
    private static final WeakHashMap<View, Float> SHIFT_TARGETS = new WeakHashMap<>();
    private static final WeakHashMap<View, TranslationSpring> X_SPRINGS = new WeakHashMap<>();
    private static boolean REDUCED_MOTION;

    private SpringMotion() {}

    // V92.9 semantic motion tokens. Durations remain implementation details of named behaviors.
    public static void setReducedMotion(boolean reduced) { REDUCED_MOTION = reduced; }
    public static boolean isReducedMotion() { return REDUCED_MOTION; }
    public static long pressDownDuration() { return REDUCED_MOTION ? 36L : 72L; }
    public static long pressUpDuration() { return REDUCED_MOTION ? 70L : 185L; }
    public static long selectionDuration() { return REDUCED_MOTION ? 90L : 210L; }
    /** Root-tab carrier: quick enough to feel attached to the finger, monotonic so it never overshoots its tab. */
    public static long tabSelectionDuration() { return REDUCED_MOTION ? 80L : 165L; }
    public static long fadeDuration() { return REDUCED_MOTION ? 80L : 145L; }
    public static long spatialDuration() { return REDUCED_MOTION ? 120L : 280L; }
    public static long pageDuration() { return REDUCED_MOTION ? 120L : 285L; }
    /**
     * Root-tab pages move exactly one neighboring viewport at a time.  When a new tap retargets a
     * transition already in flight the remaining travel can be shorter/longer than one viewport,
     * so duration scales with that distance instead of changing perceived velocity.
     */
    public static long tabPageDuration(float travelScreens) {
        if (REDUCED_MOTION) return 105L;
        float screens = Math.max(.45f, Math.min(1.30f, travelScreens));
        return Math.round(205f * screens);
    }
    public static long sheetDuration() { return REDUCED_MOTION ? 120L : 260L; }
    public static long themeRevealDuration() { return REDUCED_MOTION ? 100L : 430L; }

    public static final TimeInterpolator SNAPPY = new DampedSpringInterpolator(10.6, 7.2);
    public static final TimeInterpolator SOFT = new DampedSpringInterpolator(9.6, 6.4);
    public static final TimeInterpolator LAND = new DampedSpringInterpolator(11.8, 7.4);
    /** Press-release spring: fast response, no visible rubber-band wobble. */
    public static final TimeInterpolator PRESS = new DampedSpringInterpolator(13.4, 7.0);
    /** Paging spring: slightly softer so a finger-driven page can settle naturally. */
    public static final TimeInterpolator PAGE = new DampedSpringInterpolator(10.4, 6.6);
    /** Root-tab navigation is intentionally monotonic: spatial continuity without bounce/ghosting. */
    public static final TimeInterpolator TAB_PAGE = new PathInterpolator(.20f, .78f, .20f, 1f);
    /** Selection carrier follows the chosen tab with a shorter monotonic settle. */
    public static final TimeInterpolator TAB_SELECTION = new PathInterpolator(.16f, .84f, .22f, 1f);
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
                .setDuration(REDUCED_MOTION ? 70L : 150L).setInterpolator(SNAPPY).start();
    }

    public static void ghostSource(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.animate().scaleX(.985f).scaleY(.985f).alpha(.14f)
                .setDuration(REDUCED_MOTION ? 60L : 115L).setInterpolator(SNAPPY).start();
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
                .setStartDelay(REDUCED_MOTION ? 0L : waveDelay).setDuration(REDUCED_MOTION ? 90L : 215L).setInterpolator(SOFT).start();
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
                .setStartDelay(0L).setDuration(REDUCED_MOTION ? 80L : 168L).setInterpolator(FOLLOW).start();
    }

    public static void pressDown(View view, float scale) {
        if (view == null) return;
        float target = Math.max(.94f, Math.min(.985f, scale));
        view.animate().cancel();
        view.animate().scaleX(target).scaleY(target).alpha(.90f)
                .setDuration(pressDownDuration()).setInterpolator(SNAPPY).start();
    }

    public static void pressUp(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(pressUpDuration()).setInterpolator(PRESS).start();
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
                .setDuration(REDUCED_MOTION ? 95L : 205L).setInterpolator(LAND)
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
                .setDuration(REDUCED_MOTION ? 105L : 235L).setInterpolator(LAND)
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


    /**
     * Interruptible translation spring used by selection carriers. Unlike ViewPropertyAnimator, the
     * spring keeps its current velocity when the target changes, so a rapid tab retarget feels like
     * one physical object changing destination instead of a chain of independent tweens.
     */
    public static void springTranslationX(View view, float target) {
        if (view == null) return;
        if (REDUCED_MOTION || !view.isAttachedToWindow()) {
            TranslationSpring previous = X_SPRINGS.remove(view);
            if (previous != null) previous.cancelled = true;
            view.setTranslationX(target);
            return;
        }
        TranslationSpring spring = X_SPRINGS.get(view);
        if (spring == null) {
            spring = new TranslationSpring(view, target);
            X_SPRINGS.put(view, spring);
            Choreographer.getInstance().postFrameCallback(spring);
        } else {
            spring.target = target;
            spring.cancelled = false;
            if (!spring.scheduled) {
                spring.scheduled = true;
                Choreographer.getInstance().postFrameCallback(spring);
            }
        }
    }

    public static void cancelTranslationX(View view) {
        if (view == null) return;
        TranslationSpring spring = X_SPRINGS.remove(view);
        if (spring != null) spring.cancelled = true;
    }

    private static final class TranslationSpring implements Choreographer.FrameCallback {
        private final WeakReference<View> ref;
        private float target;
        private float velocity;
        private long lastFrameNanos;
        private boolean cancelled;
        private boolean scheduled = true;

        TranslationSpring(View view, float target) {
            this.ref = new WeakReference<>(view);
            this.target = target;
        }

        @Override public void doFrame(long frameTimeNanos) {
            scheduled = false;
            View view = ref.get();
            if (cancelled || view == null || !view.isAttachedToWindow()) return;
            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos - 16_000_000L;
            float dt = Math.max(.006f, Math.min(.032f, (frameTimeNanos - lastFrameNanos) / 1_000_000_000f));
            lastFrameNanos = frameTimeNanos;

            float x = view.getTranslationX();
            // Near-critical damping: no decorative bounce, but real carried velocity survives a
            // retarget. Constants are in seconds, so the feel is density-independent.
            final float omega = 14.2f;
            final float dampingRatio = .94f;
            float acceleration = -omega * omega * (x - target) - 2f * dampingRatio * omega * velocity;
            velocity += acceleration * dt;
            float maxVelocity = 5200f * Math.max(.75f, view.getResources().getDisplayMetrics().density);
            velocity = Math.max(-maxVelocity, Math.min(maxVelocity, velocity));
            x += velocity * dt;
            view.setTranslationX(x);

            float d = Math.max(.75f, view.getResources().getDisplayMetrics().density);
            if (Math.abs(x - target) <= .28f * d && Math.abs(velocity) <= 7f * d) {
                view.setTranslationX(target);
                velocity = 0f;
                X_SPRINGS.remove(view);
                return;
            }
            scheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
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
