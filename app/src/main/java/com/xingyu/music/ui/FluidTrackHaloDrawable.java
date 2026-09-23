package com.xingyu.music.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/**
 * Album-reactive current-track halo, rendering V92.4 song-owned visual state.
 *
 * The row stays theme-native: dark in Deep/OLED and paper-light in Moonlight. A restrained,
 * multi-tone edge and a soft internal tint move,
 * using the artwork accent as the anchor color. Animation is intentionally slow and pauses in
 * place when playback pauses. A rebound row renders the same palette transition and phase as the
 * previous renderer; detaching or recycling a view never changes the playback clock.
 */
public final class FluidTrackHaloDrawable extends Drawable implements Runnable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix gradientMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final RectF innerRect = new RectF();
    private final float density;

    private SweepGradient sweep;
    private LinearGradient softFill;
    private PlaybackHighlightState state = new PlaybackHighlightState("", Color.rgb(123, 211, 255), SystemClock.uptimeMillis());
    private int builtAccent;
    private boolean builtLight;
    private float builtCx = Float.NaN;
    private float builtCy = Float.NaN;
    private float phase;
    private boolean active;
    private boolean rendering = true;
    private int externalAlpha = 255;

    public FluidTrackHaloDrawable(float density) {
        this.density = Math.max(.75f, density);
        fill.setStyle(Paint.Style.FILL);
        outer.setStyle(Paint.Style.STROKE);
        outer.setStrokeCap(Paint.Cap.ROUND);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeCap(Paint.Cap.ROUND);
        inner.setStyle(Paint.Style.STROKE);
        phase = state.phase(SystemClock.uptimeMillis());
    }

    public void bindState(PlaybackHighlightState value) {
        if (value == null) return;
        if (state != value) { state = value; sweep = null; softFill = null; }
        rendering = true;
        invalidateSelf();
        ensureScheduled();
    }

    /** Detaching a renderer must never pause the song-owned clock. */
    public void stopRendering() {
        rendering = false;
        unscheduleSelf(this);
    }

    public void refreshState() {
        if (!rendering) return;
        invalidateSelf();
        ensureScheduled();
    }

    public void setAccent(int color) {
        state.setAccent(color, SystemClock.uptimeMillis());
        refreshState();
    }

    public void setActive(boolean value) {
        state.setPlaying(value, SystemClock.uptimeMillis());
        refreshState();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        sweep = null;
        softFill = null;
    }

    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible && rendering) ensureScheduled();
        else if (!visible) unscheduleSelf(this);
        return changed;
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        active = state.isPlaying();
        phase = SpringMotion.isReducedMotion() ? 0.12f : state.phase(now);

        float inset = 1.20f * density;
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        float radius = 15f * density;
        int accent = state.accent(now);
        ensureShaders(rect.centerX(), rect.centerY(), rect, accent);

        fill.setShader(softFill);
        fill.setAlpha(scaleAlpha(255));
        canvas.drawRoundRect(rect, radius, radius, fill);
        fill.setShader(null);

        gradientMatrix.reset();
        gradientMatrix.setRotate(phase * 360f, rect.centerX(), rect.centerY());
        sweep.setLocalMatrix(gradientMatrix);

        // A wide low-opacity outer stroke gives depth without becoming an RGB/neon border.
        outer.setShader(sweep);
        outer.setStrokeWidth(3.65f * density);
        outer.setAlpha(scaleAlpha(active ? 48 : 34));
        canvas.drawRoundRect(rect, radius, radius, outer);
        outer.setShader(null);

        edge.setShader(sweep);
        edge.setStrokeWidth(1.48f * density);
        edge.setAlpha(scaleAlpha(active ? 205 : 148));
        canvas.drawRoundRect(rect, radius, radius, edge);
        edge.setShader(null);

        inner.setStrokeWidth(.55f * density);
        inner.setColor(Ui.isLightAppearance()
                ? withAlpha(accent, scaleAlpha(active ? 38 : 26))
                : Color.argb(scaleAlpha(active ? 48 : 30), 255, 255, 255));
        innerRect.set(rect);
        innerRect.inset(1.7f * density, 1.7f * density);
        float innerRadius = Math.max(0f, radius - 1.7f * density);
        canvas.drawRoundRect(innerRect, innerRadius, innerRadius, inner);
    }

    private void ensureShaders(float cx, float cy, RectF bounds, int accent) {
        boolean lightAppearance = Ui.isLightAppearance();
        if (sweep != null && softFill != null && builtAccent == accent && builtLight == lightAppearance
                && builtCx == cx && builtCy == cy) return;
        int light = mix(accent, Color.WHITE, lightAppearance ? .18f : .34f);
        int warm = analogous(accent, -24f, .96f, 1.03f);
        int cool = analogous(accent, 22f, .92f, .98f);
        int deep = mix(accent, Color.BLACK, lightAppearance ? .18f : .43f);
        // Keep the halo album-led: secondary tones are close neighbours of the cover hue instead
        // of fixed cyan/purple brand colours. The result follows each cover without turning into RGB.
        sweep = new SweepGradient(cx, cy,
                new int[]{light, accent, warm, deep, cool, accent, light},
                new float[]{0f, .15f, .32f, .51f, .69f, .86f, 1f});
        if (lightAppearance) {
            // V92.9.2 Moonlight: preserve the colourful edge but keep the search/current-track
            // surface paper-white. The previous dark fill was the large grey bar seen in QA.
            int lightFill = Ui.SURFACE;
            int accentWash = mix(lightFill, accent, .070f);
            int warmWash = mix(lightFill, warm, .045f);
            softFill = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    new int[]{withAlpha(lightFill, 252), withAlpha(accentWash, 250), withAlpha(warmWash, 251),
                            withAlpha(accentWash, 249), withAlpha(lightFill, 252)},
                    new float[]{0f, .28f, .50f, .72f, 1f}, Shader.TileMode.CLAMP);
        } else {
            softFill = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                    new int[]{Color.argb(176, 9, 9, 15), withAlpha(deep, 54), withAlpha(warm, 22),
                            withAlpha(accent, 34), Color.argb(182, 8, 8, 14)},
                    new float[]{0f, .30f, .50f, .73f, 1f}, Shader.TileMode.CLAMP);
        }
        builtAccent = accent;
        builtLight = lightAppearance;
        builtCx = cx;
        builtCy = cy;
    }

    private void ensureScheduled() {
        unscheduleSelf(this);
        long now = SystemClock.uptimeMillis();
        boolean needsAccentSettle = state.isTransitioning(now);
        boolean needsAmbientMotion = state.isPlaying() && !SpringMotion.isReducedMotion();
        if (rendering && isVisible() && (needsAmbientMotion || needsAccentSettle))
            scheduleSelf(this, now + (SpringMotion.isReducedMotion() ? 80L : 32L));
    }

    @Override public void run() {
        if (!rendering || !isVisible()) return;
        invalidateSelf();
        ensureScheduled();
    }

    @Override public void setAlpha(int alpha) {
        externalAlpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter colorFilter) {
        edge.setColorFilter(colorFilter);
        outer.setColorFilter(colorFilter);
        fill.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private int scaleAlpha(int alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * (externalAlpha / 255f))));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float smoothstep(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }


    private static int analogous(int color, float hueShift, float satScale, float valueScale) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = Math.max(.18f, Math.min(.88f, hsv[1] * satScale));
        hsv[2] = Math.max(.42f, Math.min(1f, hsv[2] * valueScale));
        return Color.HSVToColor(hsv);
    }

    private static int mix(int a, int b, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
