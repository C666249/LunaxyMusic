package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

/**
 * Xingyu Music album visual.
 *
 * V92.2 also serves as the landing target for shared-artwork transitions. Its shaders/matrices are
 * retained rather than allocated every frame, and the record can be briefly frozen/reset while a
 * Hero proxy lands so the final cross-fade has no orientation jump or GC hitch.
 */
public final class VinylRecordView extends View {
    private static final int WAVE_COUNT = 5;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix gradientMatrix = new Matrix();
    private final Matrix coverMatrix = new Matrix();
    private Bitmap cover;
    private BitmapShader coverShader;
    private SweepGradient waveSweep;
    private float waveSweepCx = Float.NaN;
    private float waveSweepCy = Float.NaN;
    private int waveSweepAccent;
    private boolean spinning;
    private boolean heroTransitionHold;
    private float angle;
    private float angularVelocity;
    private float wavePhase;
    private long lastFrame;
    private int accent = Color.rgb(183, 165, 255);
    private AudioLevelProvider audioLevels;
    private float visualEnergy;
    private float visualBass;
    private float visualTreble;
    private float visualBeat;

    public VinylRecordView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setCoverBitmap(Bitmap bitmap) {
        cover = bitmap;
        coverShader = bitmap == null || bitmap.isRecycled()
                ? null : new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        invalidate();
    }

    public void setAccentColor(int color) {
        accent = color;
        waveSweep = null;
        invalidate();
    }

    public void setAudioLevelProvider(AudioLevelProvider provider) {
        audioLevels = provider;
        if (spinning && !SpringMotion.isReducedMotion()) postInvalidateOnAnimation();
    }

    public void setSpinning(boolean value) {
        if (spinning == value) return;
        spinning = value;
        lastFrame = SystemClock.uptimeMillis();
        if (!SpringMotion.isReducedMotion()) postInvalidateOnAnimation();
        else invalidate();
    }

    /** Align the large record with the card artwork while the shared Hero overlay is landing. */
    public void beginHeroTransition() {
        heroTransitionHold = true;
        angle = 0f;
        angularVelocity = 0f;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Resume normal inertial playback motion after the Hero proxy has been cross-faded away. */
    public void endHeroTransition() {
        heroTransitionHold = false;
        lastFrame = SystemClock.uptimeMillis();
        if (!SpringMotion.isReducedMotion()) postInvalidateOnAnimation();
        else invalidate();
    }

    /** Actual circular album-art diameter inside this view (outer space is reserved for waves). */
    public float artworkDiameterPx() {
        return Math.min(getWidth(), getHeight()) * .82346f;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        waveSweep = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (lastFrame == 0L) lastFrame = now;
        float dt = Math.min(.08f, Math.max(.001f, (now - lastFrame) / 1000f));
        lastFrame = now;

        if (!heroTransitionHold) {
            sampleAudio(dt);
            float targetVelocity = spinning && !SpringMotion.isReducedMotion()
                    ? (4.5f + visualEnergy * .42f + visualBeat * .22f) : 0f;
            angularVelocity = approach(angularVelocity, targetVelocity, dt, spinning ? 1.35f : .72f);
            if (Math.abs(angularVelocity) < .012f && !spinning) angularVelocity = 0f;
            angle = (angle + dt * angularVelocity) % 360f;
            float motionRatio = Math.min(1f, Math.abs(angularVelocity) / 4.5f);
            if (motionRatio > .002f) {
                wavePhase = (wavePhase + dt * (.10f + .10f * motionRatio + visualEnergy * .05f
                        + visualBass * .05f + visualBeat * .09f)) % 1f;
            }
        }

        float cx = w * .5f, cy = h * .5f;
        float r = Math.min(w, h) * .418f;
        float artR = r * .985f;
        float density = getResources().getDisplayMetrics().density;

        drawAlbumWaves(canvas, cx, cy, r, density);

        float pulse = heroTransitionHold ? 1f : 1f + visualBass * .006f + visualBeat * .012f;
        canvas.save();
        canvas.scale(pulse, pulse, cx, cy);
        canvas.rotate(angle, cx, cy);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setColor(Color.rgb(7, 7, 10));
        canvas.drawCircle(cx, cy, r, paint);

        if (cover != null && !cover.isRecycled() && coverShader != null) {
            float scale = Math.max(artR * 2f / cover.getWidth(), artR * 2f / cover.getHeight());
            float dw = cover.getWidth() * scale, dh = cover.getHeight() * scale;
            coverMatrix.reset();
            coverMatrix.setScale(scale, scale);
            coverMatrix.postTranslate(cx - dw / 2f, cy - dh / 2f);
            coverShader.setLocalMatrix(coverMatrix);
            paint.setShader(coverShader);
            paint.setAlpha(spinning ? 244 : 255);
            canvas.drawCircle(cx, cy, artR, paint);
            paint.setAlpha(255);
            paint.setShader(null);
        } else {
            paint.setColor(Color.rgb(24, 24, 30));
            canvas.drawCircle(cx, cy, artR, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(.85f * density, r * .0048f));
        paint.setColor(Color.argb((int) (88 + visualEnergy * 28 + visualBeat * 38),
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawCircle(cx, cy, artR, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();

        if (!SpringMotion.isReducedMotion() && !heroTransitionHold
                && (spinning || Math.abs(angularVelocity) > .01f || visualEnergy > .008f
                || visualBeat > .008f || visualBass > .008f)) {
            postInvalidateOnAnimation();
        }
    }

    private void drawAlbumWaves(Canvas canvas, float cx, float cy, float r, float density) {
        if (waveSweep == null || waveSweepAccent != accent || waveSweepCx != cx || waveSweepCy != cy) {
            int light = mixColor(accent, Color.WHITE, .28f);
            int mid = mixColor(accent, Color.WHITE, .07f);
            int deep = mixColor(accent, Color.BLACK, .22f);
            waveSweep = new SweepGradient(cx, cy,
                    new int[]{light, mid, accent, deep, accent, light},
                    new float[]{0f, .17f, .38f, .58f, .78f, 1f});
            waveSweepAccent = accent;
            waveSweepCx = cx;
            waveSweepCy = cy;
        }
        gradientMatrix.reset();
        gradientMatrix.setRotate(wavePhase * 120f + visualTreble * 10f, cx, cy);
        waveSweep.setLocalMatrix(gradientMatrix);
        wavePaint.setShader(waveSweep);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        int haloAlpha = (int) (12 + visualEnergy * 18 + visualBass * 10 + visualBeat * 16);
        paint.setColor(Color.argb(Math.min(54, haloAlpha),
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawCircle(cx, cy, r * (1.018f + visualBass * .010f + visualBeat * .018f), paint);

        for (int i = 0; i < WAVE_COUNT; i++) {
            float phase = (wavePhase + i / (float) WAVE_COUNT) % 1f;
            float life = 1f - phase;
            float musicalLift = visualEnergy * .012f + visualBass * .018f + visualBeat * (.015f + life * .025f);
            float radius = r * (1.022f + phase * .115f + musicalLift);

            float basePresence = spinning ? .31f : .075f;
            float strength = basePresence + visualEnergy * .34f + visualBass * .28f + visualBeat * (.46f * life);
            strength *= (.30f + .70f * life);
            int alpha = Math.max(5, Math.min(126, Math.round(12f + strength * 88f)));

            float coreWidth = density * (.65f + life * .55f + visualBass * 1.10f + visualBeat * (1.15f + life * .95f));
            coreWidth = Math.min(4.6f * density, coreWidth);

            wavePaint.setAlpha(Math.max(3, Math.round(alpha * .18f)));
            wavePaint.setStrokeWidth(coreWidth * (2.8f + visualEnergy * .45f));
            canvas.drawCircle(cx, cy, radius, wavePaint);

            wavePaint.setAlpha(alpha);
            wavePaint.setStrokeWidth(coreWidth);
            canvas.drawCircle(cx, cy, radius, wavePaint);
        }
        wavePaint.setAlpha(255);
        wavePaint.setShader(null);
    }

    private void sampleAudio(float dt) {
        float targetEnergy = 0f, targetBass = 0f, targetTreble = 0f, targetBeat = 0f;
        if (spinning && audioLevels != null) {
            try {
                targetEnergy = clamp(audioLevels.energy(), 0f, 1f);
                targetBass = clamp(audioLevels.bass(), 0f, 1f);
                targetTreble = clamp(audioLevels.treble(), 0f, 1f);
                targetBeat = clamp(audioLevels.beat(), 0f, 1f);
            } catch (Exception ignored) { }
        }
        visualEnergy = approach(visualEnergy, targetEnergy, dt, targetEnergy > visualEnergy ? 11f : 4.4f);
        visualBass = approach(visualBass, targetBass, dt, targetBass > visualBass ? 10.5f : 4.0f);
        visualTreble = approach(visualTreble, targetTreble, dt, targetTreble > visualTreble ? 12f : 4.8f);
        visualBeat = approach(visualBeat, targetBeat, dt, targetBeat > visualBeat ? 18f : 7.5f);
    }

    private static float approach(float old, float target, float dt, float speed) {
        float f = 1f - (float) Math.exp(-speed * dt);
        return old + (target - old) * f;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int mixColor(int a, int b, float t) {
        float f = clamp(t, 0f, 1f);
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
