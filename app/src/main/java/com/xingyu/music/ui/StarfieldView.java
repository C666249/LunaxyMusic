package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure-black 3D starfield used by Xingyu Music.
 *
 * Design goals:
 *  - true black background, no grey/aurora wash;
 *  - bright white points with real perspective depth;
 *  - stars travel slowly from far to near, giving a gentle "moving forward"
 *    feeling instead of a noisy particle effect;
 *  - rotation sensor parallax so the star volume subtly reacts when the phone
 *    is tilted. The UI itself stays stable; only the space behind it moves.
 *
 * No sensor permission is required for the rotation-vector sensor.
 */
public final class StarfieldView extends View implements SensorEventListener {
    private static final class Star {
        float x, y, z;
        float size;
        float twinkle;
        float phase;
        float speed;
        boolean coolWhite;
    }

    private static final int STAR_COUNT = 430;
    private static final float NEAR_Z = 0.18f;
    private static final float FAR_Z = 1.18f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Star> stars = new ArrayList<>(STAR_COUNT);
    private final Random random = new Random(9281);
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final float[] rotation = new float[9];
    private final float[] orientation = new float[3];

    private boolean running;
    private boolean sensorRegistered;
    private long lastFrameMs;
    private float parallaxX;
    private float parallaxY;
    private float targetParallaxX;
    private float targetParallaxY;
    private boolean hasBaseline;
    private float basePitch;
    private float baseRoll;
    private int accent = Color.rgb(183, 165, 255);
    private AudioLevelProvider audioLevels;
    private float visualEnergy;
    private float visualBeat;

    public StarfieldView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        for (int i = 0; i < STAR_COUNT; i++) stars.add(newStar(true));

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (sensor == null && sensorManager != null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        rotationSensor = sensor;
    }

    /** V15: album-derived tint for a minority of stars while the background remains true black. */
    public void setAccentColor(int color) {
        accent = color;
        invalidate();
    }

    public void setAudioLevelProvider(AudioLevelProvider provider) {
        audioLevels = provider;
        if (running) postInvalidateOnAnimation();
    }

    private Star newStar(boolean randomDepth) {
        Star s = new Star();
        resetStar(s, randomDepth ? NEAR_Z + random.nextFloat() * (FAR_Z - NEAR_Z) : FAR_Z);
        return s;
    }

    private void resetStar(Star s, float z) {
        // A slightly wider camera volume prevents visible "spawning" near edges.
        s.x = (random.nextFloat() * 2f - 1f) * 1.34f;
        s.y = (random.nextFloat() * 2f - 1f) * 2.12f;
        // Keep a small dead zone around the exact vanishing point so stars fan out naturally.
        if (Math.abs(s.x) < .055f) s.x += s.x < 0 ? -.08f : .08f;
        if (Math.abs(s.y) < .055f) s.y += s.y < 0 ? -.08f : .08f;
        s.z = z;
        s.size = .34f + random.nextFloat() * .82f;
        s.twinkle = .38f + random.nextFloat() * .92f;
        s.phase = random.nextFloat() * 6.283185f;
        s.speed = .78f + random.nextFloat() * .48f;
        s.coolWhite = random.nextFloat() < .18f;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startMotion();
    }

    @Override protected void onDetachedFromWindow() {
        stopMotion();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (!isAttachedToWindow()) return;
        if (visibility == VISIBLE) startMotion();
        else stopMotion();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView != this || !isAttachedToWindow()) return;
        if (visibility == VISIBLE) startMotion();
        else stopMotion();
    }

    private void startMotion() {
        if (!running) {
            running = true;
            hasBaseline = false;
            lastFrameMs = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
        if (!sensorRegistered && sensorManager != null && rotationSensor != null) {
            sensorRegistered = sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void stopMotion() {
        running = false;
        if (sensorRegistered && sensorManager != null) sensorManager.unregisterListener(this);
        sensorRegistered = false;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // True black, deliberately no grey gradient or colored aurora.
        canvas.drawColor(Color.BLACK);

        long now = SystemClock.uptimeMillis();
        float dt = Math.min(.05f, Math.max(.001f, (now - lastFrameMs) / 1000f));
        lastFrameMs = now;

        float targetEnergy = 0f, targetBeat = 0f;
        if (audioLevels != null) {
            try {
                targetEnergy = clamp(audioLevels.energy(), 0f, 1f);
                targetBeat = clamp(audioLevels.beat(), 0f, 1f);
            } catch (Exception ignored) { }
        }
        visualEnergy += (targetEnergy - visualEnergy) * (1f - (float) Math.exp(-5.2f * dt));
        visualBeat += (targetBeat - visualBeat) * (1f - (float) Math.exp(-(targetBeat > visualBeat ? 15f : 7f) * dt));

        // Smooth sensor movement so handheld jitter looks like camera parallax, not shaking.
        float smoothing = 1f - (float) Math.pow(.0008f, dt);
        parallaxX += (targetParallaxX - parallaxX) * smoothing;
        parallaxY += (targetParallaxY - parallaxY) * smoothing;

        float density = getResources().getDisplayMetrics().density;
        float focal = Math.min(w, h) * .54f;
        float time = now / 1000f;
        float driftX = (float) Math.sin(time * .055f) * w * .0035f;
        float driftY = (float) Math.cos(time * .043f) * h * .0022f;
        float centerX = w * .5f + driftX;
        float centerY = h * .47f + driftY;

        // Slow forward camera speed. Close stars appear faster naturally due to perspective.
        final float forwardSpeed = .052f * (1f + visualEnergy * .10f + visualBeat * .07f);

        for (Star s : stars) {
            if (running) s.z -= forwardSpeed * s.speed * dt;
            if (s.z <= NEAR_Z) resetStar(s, FAR_Z);

            float invZ = 1f / s.z;
            float depth = clamp((FAR_Z - s.z) / (FAR_Z - NEAR_Z), 0f, 1f);
            // Real depth parallax: distant particles barely move, near stars
            // travel much farther when the phone is tilted.
            float px = parallaxX * w * (.018f + depth * .158f);
            float py = parallaxY * h * (.012f + depth * .115f);
            float sx = centerX + s.x * focal * invZ + px;
            float sy = centerY + s.y * focal * invZ + py;

            // When a star leaves the camera frustum, recycle it into deep space.
            if (sx < -80f || sx > w + 80f || sy < -80f || sy > h + 80f) {
                resetStar(s, FAR_Z * (.92f + random.nextFloat() * .08f));
                continue;
            }

            float twinkle = .82f + .18f * (float) Math.sin(time * s.twinkle + s.phase);
            int alpha = (int) ((82f + 196f * depth) * twinkle);
            alpha = Math.max(58, Math.min(255, alpha));

            // Distant points stay tiny; foreground points become crisp and luminous.
            float radius = (s.size * (.50f + depth * 1.85f)) * density * (1f + visualEnergy * .08f + visualBeat * .15f);
            int baseR = s.coolWhite ? 224 : 250;
            int baseG = s.coolWhite ? 236 : 250;
            int baseB = 255;
            // Keep most points neutral; nearer points borrow more of the album palette on beats.
            float tint = clamp((s.coolWhite ? .07f : .16f) + depth * .10f + visualEnergy * .10f + visualBeat * .15f, .04f, .48f);
            int r = mix(baseR, Color.red(accent), tint);
            int g = mix(baseG, Color.green(accent), tint);
            int b = mix(baseB, Color.blue(accent), tint);

            if (depth > .69f) {
                float glowRadius = radius * (2.8f + depth * 2.2f);
                paint.setColor(Color.argb((int) (alpha * .13f), r, g, b));
                canvas.drawCircle(sx, sy, glowRadius, paint);
            }

            paint.setColor(Color.argb(alpha, r, g, b));
            canvas.drawCircle(sx, sy, Math.max(.45f * density, radius), paint);

            // Very few close stars receive a small lens-flare cross, like the approved mockup.
            if (depth > .91f && s.size > .98f) {
                paint.setStrokeWidth(Math.max(.55f * density, radius * .25f));
                paint.setColor(Color.argb((int) (alpha * .48f), r, g, b));
                float arm = radius * 4.1f;
                canvas.drawLine(sx - arm, sy, sx + arm, sy, paint);
                canvas.drawLine(sx, sy - arm, sx, sy + arm, paint);
            }
        }

        if (running) postInvalidateOnAnimation();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length < 3) return;
        try {
            SensorManager.getRotationMatrixFromVector(rotation, event.values);
            SensorManager.getOrientation(rotation, orientation);
            float pitch = orientation[1];
            float roll = orientation[2];
            if (!hasBaseline) {
                hasBaseline = true;
                basePitch = pitch;
                baseRoll = roll;
                return;
            }
            float dRoll = wrapAngle(roll - baseRoll);
            float dPitch = wrapAngle(pitch - basePitch);
            targetParallaxX = clamp(dRoll / .13f, -1.7f, 1.7f);
            targetParallaxY = clamp(-dPitch / .12f, -1.6f, 1.6f);
        } catch (Exception ignored) { }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private static float wrapAngle(float v) {
        while (v > Math.PI) v -= (float) (Math.PI * 2);
        while (v < -Math.PI) v += (float) (Math.PI * 2);
        return v;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int mix(int a, int b, float t) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
    }
}
