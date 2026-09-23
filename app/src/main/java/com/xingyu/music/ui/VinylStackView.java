package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import java.util.Random;

/** Five retained records in one depth field. Committing rotates roles, not artwork layers. */
public final class VinylStackView extends FrameLayout {
    private final VinylRecordView[] records = new VinylRecordView[5];
    private final Bitmap[] covers = new Bitmap[5];
    private final boolean[] available = new boolean[5];
    private final int[] accents = new int[5];
    private final ParticleLayer particles;
    private final ParticleLayer foreground;
    private final float density;
    private AudioLevelProvider audioLevels;
    private boolean spinning;
    private float swipeProgress;
    private int swipeDirection;
    private ValueAnimator settleAnimator;
    private float sidesReveal = 1f;
    // V92.8.3: while the shared Hero proxy is travelling, the real center record must not
    // become visible through a geometry/layout refresh. This lock is visual-only.
    private boolean centerRevealSuppressed;
    private float expandedReveal;
    private boolean expanded;
    private ValueAnimator expandAnimator;
    public boolean isExpanded() { return expanded; }
    public void resetPresentation() {
        if (expandAnimator != null) expandAnimator.cancel();
        expanded=false; expandedReveal=0f; applyGeometry();
    }
    public void setExpanded(boolean value) {
        expanded=value;
        if(expandAnimator!=null) expandAnimator.cancel();
        expandAnimator=ValueAnimator.ofFloat(expandedReveal,value?1f:0f);
        expandAnimator.setDuration(240);
        expandAnimator.addUpdateListener(a->{ expandedReveal=(float)a.getAnimatedValue(); applyGeometry(); });
        expandAnimator.start();
    }
    // V92.5: 1 = crystal clear neighbours, 0 = full depth blur (Android 12+).
    private float neighbourClarity = 1f;
    private int accent = Color.rgb(183, 165, 255);

    public VinylStackView(Context context) {
        super(context);
        density = Math.max(.75f, getResources().getDisplayMetrics().density);
        setClipChildren(false);
        setClipToPadding(false);
        for (int i = 0; i < records.length; i++) {
            records[i] = new VinylRecordView(context);
            accents[i] = accent;
            addView(records[i], new LayoutParams(-1, -1));
        }
        available[2] = true;
        particles = new ParticleLayer(context);
        foreground = new ParticleLayer(context);
        addView(particles, new LayoutParams(-1, -1));
        addView(foreground, new LayoutParams(-1, -1));
        particles.setTranslationZ(2.4f * density);
        foreground.setTranslationZ(5f * density);
        foreground.setAlpha(.30f);
    }

    public VinylRecordView currentRecord() { return records[2]; }
    public void setAudioLevelProvider(AudioLevelProvider provider) {
        audioLevels = provider;
        updateRecordMotion();
    }
    private void updateRecordMotion() {
        for (int i = 0; i < 5; i++) {
            records[i].setAudioLevelProvider(i == 2 ? audioLevels : null);
            records[i].setSpinning(i == 2 && spinning);
        }
    }
    public void setSpinning(boolean value) {
        spinning = value;
        updateRecordMotion();
        particles.setActive(value);
        foreground.setActive(value);
    }
    public void setAccentColor(int color) {
        accent = color;
        accents[2] = color;
        records[2].setAccentColor(color);
        particles.setAccent(color);
        foreground.setAccent(color);
    }
    public void setCurrentCover(Bitmap bitmap) {
        covers[2] = bitmap;
        records[2].setCoverBitmap(bitmap);
    }
    public void setNeighbour(int offset, boolean exists, Bitmap bitmap, int color) {
        int i = offset + 2;
        if (i < 0 || i > 4 || i == 2) return;
        available[i] = exists;
        covers[i] = bitmap;
        accents[i] = color;
        records[i].setCoverBitmap(bitmap);
        records[i].setAccentColor(color);
        applyGeometry();
    }
    public void setSidesReveal(float progress) {
        sidesReveal = clamp(progress);
        applyGeometry();
    }
    public void setCenterRevealSuppressed(boolean suppressed) {
        if (centerRevealSuppressed == suppressed) return;
        centerRevealSuppressed = suppressed;
        if (!suppressed) records[2].setAlpha(1f);
        applyGeometry();
    }
    public void setNeighbourClarity(float clarity) {
        neighbourClarity = clamp(clarity);
        applyGeometry();
    }
    public void setSwipeProgress(float dxPx, float fraction) {
        cancelSettle();
        swipeProgress = clamp(fraction);
        swipeDirection = dxPx < 0 ? 1 : dxPx > 0 ? -1 : 0;
        applyGeometry();
    }
    /** Local signed slot position after MainActivity has rotated the preview window. */
    public void setSwipeSlotPosition(float signedSlots) {
        cancelSettle();
        float signed = Math.max(-1f, Math.min(1f, signedSlots));
        swipeDirection = signed > 0f ? 1 : signed < 0f ? -1 : 0;
        swipeProgress = Math.abs(signed);
        applyGeometry();
    }
    public float swipeProgress() { return swipeProgress; }
    public void settleBack() { settleBack(null); }
    public void settleBack(Runnable landed) {
        animateProgress(0f, 240L, () -> {
            swipeDirection = 0; applyGeometry();
            if (landed != null) landed.run();
        });
    }
    public void settleToNeighbour(int direction, float velocity, Runnable landed) {
        swipeDirection = direction >= 0 ? 1 : -1;
        float speed = Math.min(3f, Math.abs(velocity) / Math.max(1f, getWidth()));
        long duration = Math.round(280f * (1f - swipeProgress) / (1f + speed * .25f));
        animateProgress(1f, Math.max(120L, duration), landed);
    }

    /**
     * Rotate the retained five-record window without changing playback.  Used while one finger
     * browses through multiple queue slots; the record already under the finger becomes center.
     */
    public void shiftPreviewCenter(int direction) {
        cancelSettle();
        int step = direction >= 0 ? 1 : -1;
        VinylRecordView spare = records[step > 0 ? 0 : 4];
        if (step > 0) {
            for (int i = 0; i < 4; i++) {
                records[i] = records[i + 1]; covers[i] = covers[i + 1];
                accents[i] = accents[i + 1]; available[i] = available[i + 1];
            }
        } else {
            for (int i = 4; i > 0; i--) {
                records[i] = records[i - 1]; covers[i] = covers[i - 1];
                accents[i] = accents[i - 1]; available[i] = available[i - 1];
            }
        }
        int edge = step > 0 ? 4 : 0;
        records[edge] = spare; covers[edge] = null; available[edge] = false;
        spare.setCoverBitmap(null);
        available[2] = true;
        accent = accents[2];
        particles.setAccent(accent); foreground.setAccent(accent);
        swipeProgress = 0f; swipeDirection = 0;
        updateRecordMotion();
        applyGeometry();
    }

    /** Incoming view is already centered; keep that same view, bitmap and rotation alive. */
    public void adoptCommittedCenter(int direction, Bitmap bitmap, int newAccent, Runnable finished) {
        cancelSettle();
        int step = direction >= 0 ? 1 : -1;
        VinylRecordView spare = records[step > 0 ? 0 : 4];
        if (step > 0) {
            for (int i = 0; i < 4; i++) {
                records[i] = records[i + 1]; covers[i] = covers[i + 1];
                accents[i] = accents[i + 1]; available[i] = available[i + 1];
            }
        } else {
            for (int i = 4; i > 0; i--) {
                records[i] = records[i - 1]; covers[i] = covers[i - 1];
                accents[i] = accents[i - 1]; available[i] = available[i - 1];
            }
        }
        int edge = step > 0 ? 4 : 0;
        records[edge] = spare; covers[edge] = null; available[edge] = false;
        spare.setCoverBitmap(null);
        available[2] = true;
        if (bitmap != null) setCurrentCover(bitmap);
        setAccentColor(newAccent);
        swipeProgress = 0f; swipeDirection = 0;
        updateRecordMotion();
        applyGeometry();
        if (finished != null) finished.run();
    }

    private void cancelSettle() {
        ValueAnimator old = settleAnimator;
        settleAnimator = null;
        if (old != null) { old.removeAllListeners(); old.cancel(); }
    }
    public void cancelAnimations() {
        if (expandAnimator != null) expandAnimator.cancel();
        cancelSettle();
        for (VinylRecordView record : records) record.animate().cancel();
        swipeProgress = 0f; swipeDirection = 0;
        applyGeometry();
    }
    private void animateProgress(float to, long duration, Runnable end) {
        cancelSettle();
        ValueAnimator animator = ValueAnimator.ofFloat(swipeProgress, to);
        settleAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(SpringMotion.SOFT);
        animator.addUpdateListener(v -> {
            swipeProgress = clamp((Float) v.getAnimatedValue());
            applyGeometry();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (settleAnimator != animation) return;
                settleAnimator = null;
                if (end != null) end.run();
            }
        });
        animator.start();
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyGeometry();
    }
    @Override protected void onDetachedFromWindow() {
        cancelAnimations();
        setSpinning(false);
        super.onDetachedFromWindow();
    }
    private void applyGeometry() {
        if (particles == null) return;
        float progress = swipeDirection * swipeProgress;
        for (int i = 0; i < 5; i++) {
            if (i == 2 && centerRevealSuppressed) {
                records[i].setAlpha(0f);
                continue;
            }
            // Direct Mini -> Full drag owns the center transform until its reveal has completed.
            if (i == 2 && sidesReveal < .999f && swipeProgress == 0f) continue;
            float offset = i - 2 - progress;
            float depth = Math.abs(offset);
            View record = records[i];
            float singleX=Math.max(-1.5f,Math.min(1.5f,offset))*.72f;
            record.setTranslationX(getWidth() * (singleX*(1f-expandedReveal)+VinylStackGeometry.x(offset)*expandedReveal));
            record.setTranslationY(Math.min(3f, depth) * 3f * density);
            float scale = VinylStackGeometry.scale(offset);
            record.setScaleX(scale); record.setScaleY(scale);
            float opacity=Player926Policy.singleAlpha(offset)*(1f-expandedReveal)+VinylStackGeometry.alpha(offset)*expandedReveal;
            record.setAlpha(available[i] ? opacity * (i == 2 ? 1f : sidesReveal) : 0f);
            record.setTranslationZ((4f - depth) * density);
            if (Build.VERSION.SDK_INT >= 31) Api31Blur.apply(record, depth, density, neighbourClarity);
        }
        particles.setSwipeIntensity(swipeProgress);
        foreground.setSwipeIntensity(swipeProgress);
        particles.setTranslationX(-progress * getWidth() * .015f);
        foreground.setTranslationX(progress * getWidth() * .02f);
    }
    @android.annotation.TargetApi(31)
    private static final class Api31Blur {
        private static final java.util.WeakHashMap<View, Integer> LEVELS = new java.util.WeakHashMap<>();
        private static android.graphics.RenderEffect[] effects;
        private static float builtDensity;
        static void apply(View view, float depth, float density, float clarity) {
            float blurAmount = 1f - Math.max(0f, Math.min(1f, clarity));
            int level = Math.min(24, Math.round(Math.max(0f, depth) * 12f * blurAmount));
            if (effects == null || Math.abs(builtDensity - density) > .01f) {
                effects = new android.graphics.RenderEffect[25];
                for (int i = 1; i < effects.length; i++) {
                    float r = i * .22f * density;
                    effects[i] = android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL);
                }
                builtDensity = density;
                LEVELS.clear();
            }
            Integer old = LEVELS.get(view);
            if (old != null && old == level) return;
            view.setRenderEffect(effects[level]);
            LEVELS.put(view, level);
        }
    }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private final class ParticleLayer extends View {
        private static final int COUNT = 13;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] px = new float[COUNT];
        private final float[] py = new float[COUNT];
        private final float[] speed = new float[COUNT];
        private final float[] size = new float[COUNT];
        private final float[] depth = new float[COUNT];
        private long lastFrame;
        private boolean active;
        private float intensity;
        private int particleAccent = accent;

        ParticleLayer(Context context) {
            super(context);
            setClickable(false);
            setFocusable(false);
            Random r = new Random(923L);
            for (int i = 0; i < COUNT; i++) {
                px[i] = r.nextFloat();
                py[i] = .18f + r.nextFloat() * .64f;
                speed[i] = .012f + r.nextFloat() * .022f;
                size[i] = .45f + r.nextFloat() * .85f;
                depth[i] = .35f + r.nextFloat() * .65f;
            }
        }

        void setActive(boolean value) { active = value; if (value) postInvalidateOnAnimation(); }
        void setSwipeIntensity(float value) { intensity = clamp(value); invalidate(); }
        void setAccent(int color) { particleAccent = color; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long now = SystemClock.uptimeMillis();
            if (lastFrame == 0L) lastFrame = now;
            float dt = Math.min(.08f, Math.max(.001f, (now - lastFrame) / 1000f));
            lastFrame = now;
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            for (int i = 0; i < COUNT; i++) {
                if (active || intensity > .02f) {
                    px[i] += speed[i] * dt * (1f + intensity * 1.8f);
                    if (px[i] > 1.08f) px[i] = -.08f;
                }
                float x = px[i] * w;
                float y = py[i] * h + (float)Math.sin((px[i] + i * .17f) * Math.PI * 2.0) * density * 4f * depth[i];
                int alpha = Math.round((24f + 46f * intensity) * depth[i]);
                paint.setColor(Color.argb(Math.min(92, alpha), Color.red(particleAccent), Color.green(particleAccent), Color.blue(particleAccent)));
                canvas.drawCircle(x, y, size[i] * density, paint);
            }
            if (active || intensity > .02f) postInvalidateOnAnimation();
        }
    }
}
