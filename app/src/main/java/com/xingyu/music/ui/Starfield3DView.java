package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Lunaxy V54 cinematic living 3D star-volume renderer for the now-playing page.
 *
 * V54 keeps the physical 360-degree quaternion camera and continuous motion from V53, but
 * restores the luminous depth and delicacy of the original V49 starfield with a physically-inspired
 * soft point-spread-function treatment: a pin-sharp core, smooth radial falloff and rare diffraction sparkle. Every star keeps intrinsic
 * world-space motion at all times.  Turning the phone only changes where the camera
 * looks; it can never pause, restart or "wait for" the particle simulation.
 *
 * The scene intentionally mixes several coherent motion personalities instead of a
 * single warp-speed effect: deep approach, curved drift, slow orbital arcs, breathing
 * pulses and rare comet-like foreground streaks.  Long, incommensurate scene waves
 * gently change which behaviour is dominant, so the sky feels alive and non-repeating
 * without becoming chaotic.
 *
 * Rendering remains a non-clickable hardware Canvas View.  Playback, lyrics, queue,
 * download, ringtone and all other app interaction layers stay completely independent.
 */
public final class Starfield3DView extends View implements SensorEventListener {
    private static final int LAYER_FAR = 0;
    private static final int LAYER_MID = 1;
    private static final int LAYER_NEAR = 2;

    private static final int MOTION_FLOW = 0;
    private static final int MOTION_CURVE = 1;
    private static final int MOTION_ORBIT = 2;
    private static final int MOTION_PULSE = 3;
    private static final int MOTION_COMET = 4;

    public static final int PROFILE_LIVING = 0;
    public static final int PROFILE_CLASSIC_FORWARD = 1;
    public static final int PROFILE_DEEP_SPACE = 2;

    // A complete 360-degree volume.  Only the current camera frustum is rendered.
    // V55 tuned population is kept as the 1.00x reference. V56 adds a reserve pool so
    // density can rise without reallocating particles while the player is on-screen.
    private static final int FAR_COUNT = 1700;
    private static final int MID_COUNT = 760;
    private static final int NEAR_COUNT = 220;
    private static final int STAR_COUNT = FAR_COUNT + MID_COUNT + NEAR_COUNT;
    private static final int EXTRA_FAR_COUNT = 595;
    private static final int EXTRA_MID_COUNT = 266;
    private static final int EXTRA_NEAR_COUNT = 77;
    private static final int STAR_POOL_COUNT = STAR_COUNT + EXTRA_FAR_COUNT + EXTRA_MID_COUNT + EXTRA_NEAR_COUNT;
    private static final float MAX_DENSITY_MULTIPLIER = 1.35f;
    // Travel profiles intentionally render a much calmer subset of the V56 pool.
    // V57 pushed nearly the whole recycled field into the camera frustum, which looked busy
    // and increased Canvas work. These multipliers keep Living mode unchanged while
    // Classic/Deep Space stay comfortably below half of the previous travel population.
    private static final float CLASSIC_TRAVEL_DENSITY_SCALE = .44f;
    private static final float DEEP_SPACE_DENSITY_SCALE = .38f;

    private static final float BASE_VERTICAL_FOV_DEG = 74f;
    private static final float NEAR_PLANE = .28f;
    private static final long UNIVERSE_SEED = 0x4C554E4158595632L; // "LUNAXYV2"

    // Foreground camera-volume stream.  It is deliberately independent from the 360
    // world stars and is replenished immediately in the current view while rotating.
    private static final int FLOW_COUNT = 260;
    private static final int EXTRA_FLOW_COUNT = 91;
    private static final int FLOW_POOL_COUNT = FLOW_COUNT + EXTRA_FLOW_COUNT;
    private static final float FLOW_NEAR_DEPTH = .68f;
    private static final float FLOW_FAR_DEPTH = 49f;
    private static final long FLOW_SEED = 0x4C554E4158594632L; // "LUNAXYF2"

    private static final class Star {
        float x, y, z;
        float distance;
        float farDistance;
        float nearDistance;
        float worldRadius;
        float brightness;
        float phase;
        float twinkleSpeed;
        float pulseSpeed;
        float lifeBreathCycles;
        float sizeBreathDepth;
        float lightBreathDepth;
        float fadeStart;
        float tintWeight;
        float radialSpeed;
        float angularSpeed;
        float wobbleSpeed;
        float axisX, axisY, axisZ;
        int layer;
        int motionType;
        boolean flare;
        boolean fadeBeforeRecycle;
        boolean baseDensityPool;
        float densityRank;
        boolean previousVisible;
        float previousX, previousY;
    }

    private static final class FlowStar {
        float x, y, z;
        float dirX, dirY, dirZ;
        float remainingDepth;
        float speed;
        float size;
        float brightness;
        float phase;
        float twinkleSpeed;
        float pulseSpeed;
        float lifeBreathCycles;
        float sizeBreathDepth;
        float lightBreathDepth;
        float fadeStart;
        float tintWeight;
        float curveStrength;
        float axisX, axisY, axisZ;
        int motionType;
        boolean coolWhite;
        boolean flare;
        boolean fadeBeforeRecycle;
        boolean baseDensityPool;
        float densityRank;
        boolean previousVisible;
        float previousX, previousY;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix glowMatrix = new Matrix();
    private final Shader[] glowShaders = new Shader[5];
    private final int[] glowPalette = new int[5];
    private final Star[] stars = new Star[STAR_POOL_COUNT];
    private final FlowStar[] flowStars = new FlowStar[FLOW_POOL_COUNT];
    private final Random random = new Random(UNIVERSE_SEED);
    private final Random flowRandom = new Random(FLOW_SEED);
    // Separate seeds make density selection deterministic without perturbing the V55 star layout.
    private final Random densityRandom = new Random(0x4C554E4158594433L);
    private final Random flowDensityRandom = new Random(0x4C554E4158594446L);
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final Sensor linearAccelerationSensor;

    // Android/SensorManager quaternion layout after getQuaternionFromVector: [w,x,y,z].
    private final float[] baselineSensorQ = new float[]{1f, 0f, 0f, 0f};
    private final float[] sensorQ = new float[]{1f, 0f, 0f, 0f};
    private final float[] targetCameraQ = new float[]{1f, 0f, 0f, 0f};
    private final float[] cameraQ = new float[]{1f, 0f, 0f, 0f};
    private final float[] nextCameraQ = new float[4];
    private final float[] lastSensorQ = new float[]{1f, 0f, 0f, 0f};
    private final float[] rotated = new float[3];
    private final float[] inverseCameraQ = new float[]{1f, 0f, 0f, 0f};
    private final float[] spawnWorld = new float[3];
    private final float[] spawnDirection = new float[3];
    private final float[] travelForward = new float[3];
    private final float[] travelRight = new float[3];
    private final float[] travelUp = new float[3];

    private boolean running;
    private boolean rotationRegistered;
    private boolean linearRegistered;
    private boolean hasBaseline;
    private boolean hasLastSensorQ;
    private long lastFrameMs;
    private long lastRotationSensorNs;

    private float angularSpeed;
    private float targetShiftX, targetShiftY, targetShiftZ;
    private float cameraShiftX, cameraShiftY, cameraShiftZ;
    private int accent = Color.rgb(183, 165, 255);
    private AudioLevelProvider audioLevels;
    private float visualEnergy;
    private float visualBeat;
    private float speedMultiplier = 1f;
    private float sensitivity = 1f;
    // V56 official visual baseline: slightly larger, brighter and denser than V55.
    // Combined, these restore the immediately-readable star presence of V49 while preserving
    // V55's finer PSF, breathing lifecycle and 360-degree motion architecture.
    private float starSizeMultiplier = 1.18f;
    private float starBrightnessMultiplier = 1.20f;
    private float starDensityMultiplier = 1.16f;
    private int motionProfile = PROFILE_LIVING;

    public Starfield3DView(Context context) {
        super(context);
        setBackgroundColor(Ui.BG);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        rebuildGlowPalette();
        createUniverse();
        createFlightField();

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor rotation = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rotation == null && sensorManager != null)
            rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        rotationSensor = rotation;
        linearAccelerationSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    public void setAccentColor(int color) {
        accent = color;
        rebuildGlowPalette();
        invalidate();
    }

    public void setAudioLevelProvider(AudioLevelProvider provider) {
        audioLevels = provider;
        if (running && !SpringMotion.isReducedMotion()) postInvalidateOnAnimation();
    }

    public boolean hasRotationTracking() {
        return rotationSensor != null;
    }

    /** Visual-only user controls. Motion defaults preserve V55; V56 raises visual presence separately. */
    public void setSpeedMultiplier(float value) {
        speedMultiplier = clamp(value, .45f, 1.90f);
    }

    public float speedMultiplier() { return speedMultiplier; }

    public void setSensitivity(float value) {
        sensitivity = clamp(value, .35f, 1.85f);
    }

    public float sensitivity() { return sensitivity; }

    public void setMotionProfile(int profile) {
        int next = profile == PROFILE_CLASSIC_FORWARD ? PROFILE_CLASSIC_FORWARD
                : profile == PROFILE_DEEP_SPACE ? PROFILE_DEEP_SPACE : PROFILE_LIVING;
        if (motionProfile == next) return;
        motionProfile = next;
        clearTrails();
        // Foreground particles are re-seeded only when the user explicitly changes the visual
        // personality. Living mode's deterministic V56 layout remains untouched during playback.
        resetFlightField(true);
    }

    public int motionProfile() { return motionProfile; }

    public void setStarSizeMultiplier(float value) {
        float next = clamp(value, .70f, 1.55f);
        if (Math.abs(next - starSizeMultiplier) < .0005f) return;
        starSizeMultiplier = next;
        invalidate();
    }

    public float starSizeMultiplier() { return starSizeMultiplier; }

    public void setStarBrightnessMultiplier(float value) {
        float next = clamp(value, .65f, 1.55f);
        if (Math.abs(next - starBrightnessMultiplier) < .0005f) return;
        starBrightnessMultiplier = next;
        invalidate();
    }

    public float starBrightnessMultiplier() { return starBrightnessMultiplier; }

    public void setStarDensityMultiplier(float value) {
        float next = clamp(value, .60f, MAX_DENSITY_MULTIPLIER);
        if (Math.abs(next - starDensityMultiplier) < .0005f) return;
        starDensityMultiplier = next;
        clearTrails();
        invalidate();
    }

    public float starDensityMultiplier() { return starDensityMultiplier; }

    private void createUniverse() {
        int index = 0;
        // Build the original V55 population first so its deterministic star layout remains intact
        // at 1.00x. Reserve particles are appended and only revealed above 1.00x density.
        for (int i = 0; i < FAR_COUNT; i++) stars[index++] = createStar(LAYER_FAR, true);
        for (int i = 0; i < MID_COUNT; i++) stars[index++] = createStar(LAYER_MID, true);
        for (int i = 0; i < NEAR_COUNT; i++) stars[index++] = createStar(LAYER_NEAR, true);
        for (int i = 0; i < EXTRA_FAR_COUNT; i++) stars[index++] = createStar(LAYER_FAR, false);
        for (int i = 0; i < EXTRA_MID_COUNT; i++) stars[index++] = createStar(LAYER_MID, false);
        for (int i = 0; i < EXTRA_NEAR_COUNT; i++) stars[index++] = createStar(LAYER_NEAR, false);
    }

    private Star createStar(int layer, boolean baseDensityPool) {
        Star s = new Star();
        s.baseDensityPool = baseDensityPool;
        s.densityRank = densityRandom.nextFloat();
        s.layer = layer;
        if (layer == LAYER_FAR) {
            s.farDistance = lerp(72f, 104f, random.nextFloat());
            s.nearDistance = lerp(3.0f, 4.6f, random.nextFloat());
            s.worldRadius = lerp(.095f, .225f, random.nextFloat());
            s.brightness = lerp(.44f, .84f, random.nextFloat());
            s.tintWeight = lerp(.04f, .13f, random.nextFloat());
            s.radialSpeed = lerp(1.35f, 2.35f, random.nextFloat());
            s.angularSpeed = lerp(.020f, .060f, random.nextFloat());
            s.flare = random.nextFloat() < .008f;
        } else if (layer == LAYER_MID) {
            s.farDistance = lerp(48f, 78f, random.nextFloat());
            s.nearDistance = lerp(1.9f, 3.1f, random.nextFloat());
            s.worldRadius = lerp(.055f, .145f, random.nextFloat());
            s.brightness = lerp(.56f, .98f, random.nextFloat());
            s.tintWeight = lerp(.08f, .24f, random.nextFloat());
            s.radialSpeed = lerp(1.55f, 2.85f, random.nextFloat());
            s.angularSpeed = lerp(.032f, .086f, random.nextFloat());
            s.flare = random.nextFloat() < .018f;
        } else {
            s.farDistance = lerp(30f, 54f, random.nextFloat());
            s.nearDistance = lerp(1.05f, 1.75f, random.nextFloat());
            s.worldRadius = lerp(.028f, .085f, random.nextFloat());
            s.brightness = lerp(.67f, 1.0f, random.nextFloat());
            s.tintWeight = lerp(.12f, .36f, random.nextFloat());
            s.radialSpeed = lerp(1.85f, 3.45f, random.nextFloat());
            s.angularSpeed = lerp(.045f, .120f, random.nextFloat());
            s.flare = random.nextFloat() < .048f;
        }
        s.phase = random.nextFloat() * (float) (Math.PI * 2.0);
        s.twinkleSpeed = lerp(.48f, 1.42f, random.nextFloat());
        s.pulseSpeed = lerp(.22f, .78f, random.nextFloat());
        s.wobbleSpeed = lerp(.18f, .72f, random.nextFloat());
        s.motionType = chooseMotionType(random.nextFloat());
        randomizeLightLife(s);
        randomUnitVector(random, spawnDirection);
        randomUnitVector(random, spawnWorld);
        s.axisX = spawnWorld[0];
        s.axisY = spawnWorld[1];
        s.axisZ = spawnWorld[2];
        float initialDepth = lerp(s.nearDistance + .4f, s.farDistance, random.nextFloat());
        placeStar(s, spawnDirection[0], spawnDirection[1], spawnDirection[2], initialDepth);
        return s;
    }

    private int chooseMotionType(float r) {
        if (r < .42f) return MOTION_FLOW;
        if (r < .66f) return MOTION_CURVE;
        if (r < .80f) return MOTION_ORBIT;
        if (r < .94f) return MOTION_PULSE;
        return MOTION_COMET;
    }

    private void recycleStar(Star s) {
        if (motionProfile == PROFILE_LIVING) {
            // Keep V56 Living mode's random-call order exactly intact.
            randomUnitVector(random, spawnDirection);
            randomUnitVector(random, spawnWorld);
            s.axisX = spawnWorld[0];
            s.axisY = spawnWorld[1];
            s.axisZ = spawnWorld[2];
            s.motionType = chooseMotionType(random.nextFloat());
            s.phase = random.nextFloat() * (float) (Math.PI * 2.0);
            s.flare = random.nextFloat() < (s.layer == LAYER_NEAR ? .05f : s.layer == LAYER_MID ? .02f : .009f);
            randomizeLightLife(s);
            placeStar(s, spawnDirection[0], spawnDirection[1], spawnDirection[2],
                    lerp(s.farDistance * .90f, s.farDistance, random.nextFloat()));
            s.previousVisible = false;
            return;
        }

        randomUnitVector(random, spawnWorld);
        s.axisX = spawnWorld[0];
        s.axisY = spawnWorld[1];
        s.axisZ = spawnWorld[2];
        s.motionType = chooseMotionType(random.nextFloat());
        s.phase = random.nextFloat() * (float) (Math.PI * 2.0);
        s.flare = random.nextFloat() < (s.layer == LAYER_NEAR ? .05f : s.layer == LAYER_MID ? .02f : .009f);
        randomizeLightLife(s);
        float depth = lerp(s.farDistance * .90f, s.farDistance, random.nextFloat());

        // Travel stars respawn in a deliberately wider camera-space volume than V57.
        // Only part of that volume intersects the phone viewport at once, giving the same
        // forward-travel illusion without turning the page into a wall of glowing points.
        float spreadX = motionProfile == PROFILE_DEEP_SPACE ? .80f : .70f;
        float spreadY = motionProfile == PROFILE_DEEP_SPACE ? 1.25f : 1.10f;
        float localX = (random.nextFloat() * 2f - 1f) * depth * spreadX;
        float localY = (random.nextFloat() * 2f - 1f) * depth * spreadY;
        if (Math.abs(localX) < depth * .022f) localX += localX < 0f ? -depth * .045f : depth * .045f;
        if (Math.abs(localY) < depth * .022f) localY += localY < 0f ? -depth * .045f : depth * .045f;
        cameraToWorld(localX, localY, -depth, spawnWorld);
        s.x = spawnWorld[0];
        s.y = spawnWorld[1];
        s.z = spawnWorld[2];
        s.distance = depth;
        s.previousVisible = false;
    }

    private void randomizeLightLife(Star s) {
        s.lifeBreathCycles = lerp(2.15f, 4.75f, random.nextFloat());
        s.sizeBreathDepth = lerp(.13f, .34f, random.nextFloat());
        s.lightBreathDepth = lerp(.17f, .42f, random.nextFloat());
        s.fadeBeforeRecycle = random.nextFloat() < .68f;
        s.fadeStart = lerp(.82f, .94f, random.nextFloat());
    }

    private void randomizeLightLife(FlowStar s) {
        s.lifeBreathCycles = lerp(2.35f, 5.10f, flowRandom.nextFloat());
        s.sizeBreathDepth = lerp(.16f, .38f, flowRandom.nextFloat());
        s.lightBreathDepth = lerp(.18f, .44f, flowRandom.nextFloat());
        s.fadeBeforeRecycle = flowRandom.nextFloat() < .60f;
        s.fadeStart = lerp(.84f, .955f, flowRandom.nextFloat());
    }

    private static void placeStar(Star s, float dx, float dy, float dz, float distance) {
        float n = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (n < 1.0e-6f) {
            dx = 0f; dy = 0f; dz = -1f; n = 1f;
        }
        float inv = 1f / n;
        dx *= inv; dy *= inv; dz *= inv;
        s.distance = distance;
        s.x = dx * distance;
        s.y = dy * distance;
        s.z = dz * distance;
    }

    private void createFlightField() {
        for (int i = 0; i < FLOW_POOL_COUNT; i++) {
            FlowStar s = new FlowStar();
            s.baseDensityPool = i < FLOW_COUNT;
            s.densityRank = flowDensityRandom.nextFloat();
            flowStars[i] = s;
            resetFlowStar(s, true);
        }
    }

    private void resetFlightField(boolean randomDepth) {
        for (FlowStar s : flowStars) if (s != null) resetFlowStar(s, randomDepth);
    }

    private void resetFlowStar(FlowStar s, boolean randomDepth) {
        float depth = randomDepth
                ? lerp(2.8f, FLOW_FAR_DEPTH, flowRandom.nextFloat())
                : lerp(FLOW_FAR_DEPTH * .86f, FLOW_FAR_DEPTH, flowRandom.nextFloat());

        float localX;
        float localY;
        if (motionProfile == PROFILE_DEEP_SPACE) {
            // A wide elliptical corridor with a quieter vanishing-point core. Most particles live
            // around the travel tunnel, while a smaller inner population keeps the center alive.
            float angle = flowRandom.nextFloat() * (float) (Math.PI * 2.0);
            float radiusSeed = (float) Math.sqrt(flowRandom.nextFloat());
            float band = flowRandom.nextFloat() < .78f
                    ? lerp(.34f, 1.0f, radiusSeed)
                    : lerp(.06f, .42f, radiusSeed);
            localX = (float) Math.cos(angle) * 10.2f * band;
            localY = (float) Math.sin(angle) * 16.4f * band;
        } else {
            localX = (flowRandom.nextFloat() * 2f - 1f) * 9.2f;
            localY = (flowRandom.nextFloat() * 2f - 1f) * 15.0f;
            if (Math.abs(localX) < .40f) localX += localX < 0f ? -.74f : .74f;
            if (Math.abs(localY) < .44f) localY += localY < 0f ? -.84f : .84f;
        }

        cameraToWorld(localX, localY, -depth, spawnWorld);
        cameraToWorld(0f, 0f, 1f, spawnDirection);
        s.x = spawnWorld[0];
        s.y = spawnWorld[1];
        s.z = spawnWorld[2];
        s.dirX = spawnDirection[0];
        s.dirY = spawnDirection[1];
        s.dirZ = spawnDirection[2];
        s.remainingDepth = depth;
        s.speed = motionProfile == PROFILE_DEEP_SPACE
                ? lerp(1.42f, 2.72f, flowRandom.nextFloat())
                : lerp(1.70f, 3.05f, flowRandom.nextFloat());
        s.size = lerp(.22f, .72f, flowRandom.nextFloat());
        s.brightness = motionProfile == PROFILE_DEEP_SPACE
                ? lerp(.72f, 1.0f, flowRandom.nextFloat())
                : lerp(.68f, 1.0f, flowRandom.nextFloat());
        s.phase = flowRandom.nextFloat() * (float) (Math.PI * 2.0);
        s.twinkleSpeed = lerp(.70f, 1.60f, flowRandom.nextFloat());
        s.pulseSpeed = lerp(.36f, 1.12f, flowRandom.nextFloat());
        s.tintWeight = lerp(.08f, .33f, flowRandom.nextFloat());
        s.coolWhite = flowRandom.nextFloat() < .20f;
        s.flare = flowRandom.nextFloat() < .055f;
        s.motionType = chooseMotionType(flowRandom.nextFloat());
        s.curveStrength = lerp(.012f, .052f, flowRandom.nextFloat());
        randomUnitVector(flowRandom, spawnWorld);
        s.axisX = spawnWorld[0];
        s.axisY = spawnWorld[1];
        s.axisZ = spawnWorld[2];
        randomizeLightLife(s);
        s.previousVisible = false;
    }

    /** Convert a camera-local vector into the persistent world coordinate system. */
    private void cameraToWorld(float x, float y, float z, float[] out) {
        inverseCameraQ[0] = cameraQ[0];
        inverseCameraQ[1] = -cameraQ[1];
        inverseCameraQ[2] = -cameraQ[2];
        inverseCameraQ[3] = -cameraQ[3];
        Starfield3DMath.rotateVector(inverseCameraQ, x, y, z, out);
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
        if (!isAttachedToWindow()) return;
        if (visibility == VISIBLE) startMotion();
        else stopMotion();
    }

    private void startMotion() {
        if (!running) {
            running = true;
            hasBaseline = false;
            hasLastSensorQ = false;
            lastRotationSensorNs = 0L;
            angularSpeed = 0f;
            targetShiftX = targetShiftY = targetShiftZ = 0f;
            cameraShiftX = cameraShiftY = cameraShiftZ = 0f;
            Starfield3DMath.identity(targetCameraQ);
            Starfield3DMath.identity(cameraQ);
            clearTrails();
            resetFlightField(true);
            lastFrameMs = SystemClock.uptimeMillis();
            if (!SpringMotion.isReducedMotion()) postInvalidateOnAnimation();
        }
        if (!rotationRegistered && sensorManager != null && rotationSensor != null) {
            rotationRegistered = sensorManager.registerListener(
                    this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        if (!linearRegistered && sensorManager != null && linearAccelerationSensor != null) {
            linearRegistered = sensorManager.registerListener(
                    this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void stopMotion() {
        running = false;
        if (sensorManager != null) {
            if (rotationRegistered) sensorManager.unregisterListener(this, rotationSensor);
            if (linearRegistered) sensorManager.unregisterListener(this, linearAccelerationSensor);
        }
        rotationRegistered = false;
        linearRegistered = false;
        hasBaseline = false;
        clearTrails();
    }

    private void clearTrails() {
        for (Star s : stars) if (s != null) s.previousVisible = false;
        for (FlowStar s : flowStars) if (s != null) s.previousVisible = false;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) return;
        canvas.drawColor(Ui.BG);

        long nowMs = SystemClock.uptimeMillis();
        float dt = Math.min(.045f, Math.max(.001f, (nowMs - lastFrameMs) / 1000f));
        lastFrameMs = nowMs;
        float time = nowMs / 1000f;

        updateAudio(dt);

        // Camera tracking is intentionally independent from the simulation.  Even while
        // targetCameraQ changes rapidly, every particle below keeps advancing every frame.
        float orientationBlend = 1f - (float) Math.exp(-11.5f * dt);
        Starfield3DMath.slerp(cameraQ, targetCameraQ, orientationBlend, nextCameraQ);
        Starfield3DMath.copy(nextCameraQ, cameraQ);

        float shiftBlend = 1f - (float) Math.exp(-7.0f * dt);
        cameraShiftX += (targetShiftX - cameraShiftX) * shiftBlend;
        cameraShiftY += (targetShiftY - cameraShiftY) * shiftBlend;
        cameraShiftZ += (targetShiftZ - cameraShiftZ) * shiftBlend;
        float shiftDecay = (float) Math.exp(-4.4f * dt);
        targetShiftX *= shiftDecay;
        targetShiftY *= shiftDecay;
        targetShiftZ *= shiftDecay;
        angularSpeed *= (float) Math.exp(-2.6f * dt);

        float density = getResources().getDisplayMetrics().density;
        float speedFov = Math.min(1.7f, angularSpeed * .52f);
        float fovRad = (float) Math.toRadians(BASE_VERTICAL_FOV_DEG + speedFov);
        float focal = (height * .5f) / (float) Math.tan(fovRad * .5f);
        float centerX = width * .5f;
        float centerY = height * .5f;

        float observerX = cameraShiftX + (float) Math.sin(time * .27f) * .018f;
        float observerY = cameraShiftY + (float) Math.cos(time * .23f) * .013f;
        float observerZ = cameraShiftZ + (float) Math.sin(time * .19f) * .009f;

        // Long incommensurate waves produce coherent scene chapters without a hard loop:
        // sometimes the field feels like deep forward travel, sometimes curved orbital
        // drift becomes more visible, then the whole scene gently breathes and blooms.
        float surgeWave = .5f + .5f * (float) Math.sin(time * .205f + .45f * (float) Math.sin(time * .071f));
        float swirlWave = .5f + .5f * (float) Math.sin(time * .137f + 1.13f);
        float bloomWave = .5f + .5f * (float) Math.sin(time * .097f + 2.20f);
        float surge = lerp(.86f, 1.34f, smoothstep(.05f, .95f, surgeWave));
        float swirl = smoothstep(.34f, .88f, swirlWave);
        float bloom = smoothstep(.18f, .92f, bloomWave);

        // Slowly wandering shared vortex axis.  It gives periods of readable coordinated
        // movement while each star still carries its own local axis and phase.
        float sceneAxisX = (float) Math.sin(time * .041f) * .68f + .22f;
        float sceneAxisY = (float) Math.cos(time * .033f + .7f) * .54f + .18f;
        float sceneAxisZ = (float) Math.sin(time * .027f + 1.9f) * .72f;
        float sceneAxisN = (float) Math.sqrt(sceneAxisX * sceneAxisX + sceneAxisY * sceneAxisY + sceneAxisZ * sceneAxisZ);
        sceneAxisX /= sceneAxisN;
        sceneAxisY /= sceneAxisN;
        sceneAxisZ /= sceneAxisN;

        // Camera-local travel basis. Classic Forward and Deep Space translate the star volume
        // through the camera instead of radially scaling every xyz coordinate together. That is
        // the key geometric difference that makes every visible point actually move on-screen.
        cameraToWorld(0f, 0f, 1f, travelForward);
        cameraToWorld(1f, 0f, 0f, travelRight);
        cameraToWorld(0f, 1f, 0f, travelUp);
        float tunnelSteerX = motionProfile == PROFILE_DEEP_SPACE
                ? (float) Math.sin(time * .103f) * .052f + (float) Math.sin(time * .037f + 1.21f) * .024f : 0f;
        float tunnelSteerY = motionProfile == PROFILE_DEEP_SPACE
                ? (float) Math.cos(time * .087f + .72f) * .043f + (float) Math.sin(time * .031f + 2.0f) * .018f : 0f;

        for (Star s : stars) {
            if (s == null) continue;
            if (!isDensityActive(s.baseDensityPool, s.densityRank)) {
                s.previousVisible = false;
                continue;
            }
            updateStarMotion(s, time, dt, surge, swirl, sceneAxisX, sceneAxisY, sceneAxisZ,
                    travelForward[0], travelForward[1], travelForward[2],
                    travelRight[0], travelRight[1], travelRight[2],
                    travelUp[0], travelUp[1], travelUp[2], tunnelSteerX, tunnelSteerY);
            drawWorldStar(canvas, s, width, height, density, focal, centerX, centerY,
                    observerX, observerY, observerZ, time, bloom);
        }

        drawFlightField(canvas, width, height, density, focal, centerX, centerY,
                observerX, observerY, observerZ, time, dt, surge, swirl, bloom,
                travelForward[0], travelForward[1], travelForward[2],
                travelRight[0], travelRight[1], travelRight[2],
                travelUp[0], travelUp[1], travelUp[2], tunnelSteerX, tunnelSteerY);

        // Never throttle because the phone is moving.  Particle time always advances while
        // the player is visible; sensors merely alter cameraQ on top of that simulation.
        if (running && !SpringMotion.isReducedMotion()) postInvalidateOnAnimation();
    }

    private void updateStarMotion(Star s, float time, float dt, float surge, float swirl,
                                  float sceneAxisX, float sceneAxisY, float sceneAxisZ,
                                  float forwardX, float forwardY, float forwardZ,
                                  float rightX, float rightY, float rightZ,
                                  float upX, float upY, float upZ,
                                  float tunnelSteerX, float tunnelSteerY) {
        if (!running) return;

        if (motionProfile == PROFILE_CLASSIC_FORWARD || motionProfile == PROFILE_DEEP_SPACE) {
            // V49's readable travel illusion comes from holding camera-local x/y while z advances.
            // V56's previous radial shrink changed x/y/z by the same proportion, so x/z and y/z
            // barely changed and many stars looked pinned. Translating the world along camera-forward
            // restores real perspective motion for every visible star while retaining 360° cameraQ.
            float localPulse = .94f + .10f * (float) Math.sin(time * s.wobbleSpeed + s.phase * 1.71f);
            float audioDrive = 1f + visualEnergy * .055f + visualBeat * .065f;
            float surgeT = clamp((surge - .86f) / .48f, 0f, 1f);
            float profileSurge = motionProfile == PROFILE_CLASSIC_FORWARD
                    ? lerp(.95f, 1.08f, surgeT) : lerp(.91f, 1.16f, surgeT);
            float profileSpeed = motionProfile == PROFILE_CLASSIC_FORWARD ? 1.12f : .96f;
            float advance = s.radialSpeed * profileSpeed * profileSurge * localPulse
                    * audioDrive * speedMultiplier * dt;
            float laneX = 0f;
            float laneY = 0f;
            if (motionProfile == PROFILE_DEEP_SPACE) {
                // Shared steering makes the viewer feel like the craft is following a very gentle
                // curved route through the starfield. Per-star micro drift avoids a rigid tube.
                laneX = tunnelSteerX + .010f * (float) Math.sin(time * .127f + s.phase);
                laneY = tunnelSteerY + .008f * (float) Math.cos(time * .113f + s.phase * 1.37f);
            }
            s.x += (forwardX + rightX * laneX + upX * laneY) * advance;
            s.y += (forwardY + rightY * laneX + upY * laneY) * advance;
            s.z += (forwardZ + rightZ * laneX + upZ * laneY) * advance;
            s.distance -= advance;
            if (s.distance <= s.nearDistance) recycleStar(s);
            return;
        }

        float ux = s.x / Math.max(.0001f, s.distance);
        float uy = s.y / Math.max(.0001f, s.distance);
        float uz = s.z / Math.max(.0001f, s.distance);

        float typeRadial;
        float typeAngular;
        switch (s.motionType) {
            case MOTION_CURVE:
                typeRadial = .92f;
                typeAngular = 1.28f;
                break;
            case MOTION_ORBIT:
                typeRadial = .62f;
                typeAngular = 1.92f;
                break;
            case MOTION_PULSE:
                typeRadial = .88f + .36f * (.5f + .5f * (float) Math.sin(time * s.pulseSpeed + s.phase));
                typeAngular = .96f;
                break;
            case MOTION_COMET:
                typeRadial = 1.48f;
                typeAngular = .52f;
                break;
            case MOTION_FLOW:
            default:
                typeRadial = 1.16f;
                typeAngular = .72f;
                break;
        }

        if (motionProfile == PROFILE_CLASSIC_FORWARD) {
            // V49-like personality inside the 3D camera: pure perspective approach, no orbit/curve.
            typeRadial = 1.16f;
            typeAngular = 0f;
        }

        // Every star always approaches.  Orbit/curve personalities only redistribute the
        // radial/tangential balance; none of them can become a static decorative point.
        float localPulse = .90f + .16f * (float) Math.sin(time * s.wobbleSpeed + s.phase * 1.71f);
        float audioDrive = 1f + visualEnergy * .08f + visualBeat * .08f;
        s.distance -= s.radialSpeed * typeRadial * surge * localPulse * audioDrive * speedMultiplier * dt;
        if (s.distance <= s.nearDistance) {
            recycleStar(s);
            return;
        }

        // Local curved motion around each star's private axis.
        float tx = s.axisY * uz - s.axisZ * uy;
        float ty = s.axisZ * ux - s.axisX * uz;
        float tz = s.axisX * uy - s.axisY * ux;

        // Shared scene vortex, blended in and out over tens of seconds.
        float gx = sceneAxisY * uz - sceneAxisZ * uy;
        float gy = sceneAxisZ * ux - sceneAxisX * uz;
        float gz = sceneAxisX * uy - sceneAxisY * ux;

        float localAngle = s.angularSpeed * typeAngular
                * (.42f + .58f * (.5f + .5f * (float) Math.sin(time * s.wobbleSpeed + s.phase))) * dt;
        float globalAngle = motionProfile == PROFILE_CLASSIC_FORWARD ? 0f : (.008f + .031f * swirl) * dt
                * (s.motionType == MOTION_ORBIT ? 1.45f : 1f);
        float micro = motionProfile == PROFILE_CLASSIC_FORWARD ? 0f
                : .0022f * (float) Math.sin(time * (s.wobbleSpeed * .73f + .11f) + s.phase * 2.13f) * dt;

        ux += tx * localAngle + gx * globalAngle + gy * micro;
        uy += ty * localAngle + gy * globalAngle - gx * micro;
        uz += tz * localAngle + gz * globalAngle + tx * micro;
        float n = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (n < 1.0e-6f) {
            recycleStar(s);
            return;
        }
        float inv = 1f / n;
        ux *= inv; uy *= inv; uz *= inv;
        s.x = ux * s.distance;
        s.y = uy * s.distance;
        s.z = uz * s.distance;
    }

    private void drawWorldStar(Canvas canvas, Star s, float width, float height, float density,
                               float focal, float centerX, float centerY,
                               float observerX, float observerY, float observerZ,
                               float time, float bloom) {
        Starfield3DMath.rotateVector(cameraQ, s.x, s.y, s.z, rotated);
        float x = rotated[0] - observerX;
        float y = rotated[1] - observerY;
        float z = rotated[2] - observerZ;
        if (z >= -NEAR_PLANE) {
            if (motionProfile == PROFILE_CLASSIC_FORWARD || motionProfile == PROFILE_DEEP_SPACE) recycleStar(s);
            else s.previousVisible = false;
            return;
        }

        float invDepth = 1f / -z;
        float sx = centerX + x * focal * invDepth;
        float sy = centerY - y * focal * invDepth;
        if (sx < -72f || sx > width + 72f || sy < -72f || sy > height + 72f) {
            // Do not immediately recycle an off-screen travel star back into the current viewport.
            // Let it finish its depth lifecycle naturally. This keeps the field spacious and avoids
            // V57's rapid refill loop that made almost every active particle visible at once.
            s.previousVisible = false;
            return;
        }

        float life = clamp((s.farDistance - s.distance)
                / Math.max(.001f, s.farDistance - s.nearDistance), 0f, 1f);
        // Refined stellar luminance: never hard-flash.  A star slowly breathes while its
        // approach to the camera supplies most of the perceived brightening.
        float twinkleWave = .5f + .5f * (float) Math.sin(time * s.twinkleSpeed + s.phase);
        float pulseWave = .5f + .5f * (float) Math.sin(time * s.pulseSpeed + s.phase * 1.31f);
        // Each particle goes through several smooth bright/dim and size cycles during one flight.
        // The cycles are tied mostly to lifetime progress, not frame time, so the star visibly
        // breathes a few times before fading/recycling instead of merely translating and vanishing.
        float lifePulse = stellarLifePulse(life, time, s.phase, s.lifeBreathCycles);
        float twinkle = lerp(.91f, 1.035f, twinkleWave);
        float breath = lerp(1f - s.sizeBreathDepth * .50f, 1f + s.sizeBreathDepth, lifePulse);
        breath *= lerp(.985f, 1.025f, pulseWave);
        if (s.motionType == MOTION_PULSE) breath *= lerp(.965f, 1.055f, pulseWave);
        // A travelling luminance wave gives the whole sky a slow common breath while each star
        // keeps its own local cycle.  Both are continuous sinusoids, never threshold flashes.
        float depthWave = .5f + .5f * (float) Math.sin(time * .37f - life * 5.6f + s.phase * .19f);
        float shimmer = lerp(.955f, 1.060f, depthWave);
        float localLight = lerp(1f - s.lightBreathDepth * .58f, 1f + s.lightBreathDepth * .24f, lifePulse);
        float radius = s.worldRadius * focal * invDepth;
        radius *= breath * (1f + life * .14f + visualEnergy * .022f + visualBeat * .038f);
        radius *= starSizeMultiplier;
        radius = clamp(radius, .17f * density * starSizeMultiplier,
                1.92f * density * starSizeMultiplier);

        float layerAlpha = s.layer == LAYER_FAR ? .82f : s.layer == LAYER_MID ? .94f : 1f;
        float approach = smoothstep(.02f, .98f, life);
        float lifeAlpha = stellarLifeEnvelope(life, s.fadeBeforeRecycle, s.fadeStart);
        int alpha = (int) ((60f + 195f * approach) * s.brightness * layerAlpha * twinkle * shimmer
                * localLight * lifeAlpha * starBrightnessMultiplier);
        alpha = Math.min(255, alpha);
        if (!s.fadeBeforeRecycle || life < s.fadeStart)
            alpha = Math.max(Math.round(30f * starBrightnessMultiplier), alpha);
        else alpha = Math.max(0, alpha);
        float appearanceAlpha = Ui.isLightAppearance() ? .16f : 1f;
        alpha = Math.max(Ui.isLightAppearance() ? 5 : 0, Math.round(alpha * appearanceAlpha));

        int neutralR = Ui.isLightAppearance() ? (s.layer == LAYER_FAR ? 70 : 44) : (s.layer == LAYER_FAR ? 226 : 248);
        int neutralG = Ui.isLightAppearance() ? (s.layer == LAYER_FAR ? 82 : 53) : (s.layer == LAYER_FAR ? 237 : 249);
        int neutralB = Ui.isLightAppearance() ? (s.layer == LAYER_FAR ? 112 : 78) : 255;
        float tint = clamp(s.tintWeight + life * .13f + bloom * .035f
                + visualEnergy * .08f + visualBeat * .12f, .03f, .52f);
        int r = mix(neutralR, Color.red(accent), tint);
        int g = mix(neutralG, Color.green(accent), tint);
        int b = mix(neutralB, Color.blue(accent), tint);

        // Keep trails hairline-thin.  Camera movement may contribute to displacement, but
        // only close / comet-like stars leave a trace so turning the phone never becomes a
        // screen full of streaks.
        if (s.previousVisible) {
            float dx = sx - s.previousX;
            float dy = sy - s.previousY;
            float displacement = (float) Math.sqrt(dx * dx + dy * dy);
            boolean deepTrace = motionProfile == PROFILE_DEEP_SPACE && s.layer == LAYER_NEAR && life > .72f;
            boolean livingTrace = motionProfile == PROFILE_LIVING
                    && (s.motionType == MOTION_COMET || (s.layer == LAYER_NEAR && life > .74f));
            boolean trace = deepTrace || livingTrace;
            if (!Ui.isLightAppearance() && trace && displacement > .60f && displacement < 29f * density) {
                float tail = deepTrace ? clamp(.07f + life * .10f, .07f, .17f)
                        : clamp(.07f + life * .12f, .07f, .19f);
                float trailAlpha = deepTrace ? .045f : (s.motionType == MOTION_COMET ? .13f : .065f);
                paint.setStrokeWidth(Math.max(.22f * density, radius * .18f));
                paint.setColor(Color.argb((int) (alpha * trailAlpha), r, g, b));
                canvas.drawLine(sx - dx * tail, sy - dy * tail, sx, sy, paint);
            }
        }

        drawRefinedStar(canvas, sx, sy, radius, alpha, r, g, b, life, time, s.phase,
                s.flare, s.motionType, false, bloom, density);

        s.previousX = sx;
        s.previousY = sy;
        s.previousVisible = true;
    }

    private void drawFlightField(Canvas canvas, float width, float height, float density,
                                 float focal, float centerX, float centerY,
                                 float observerX, float observerY, float observerZ,
                                 float time, float dt, float surge, float swirl, float bloom,
                                 float forwardX, float forwardY, float forwardZ,
                                 float rightX, float rightY, float rightZ,
                                 float upX, float upY, float upZ,
                                 float tunnelSteerX, float tunnelSteerY) {
        float audioDrive = 1f + visualEnergy * .11f + visualBeat * .09f;
        for (FlowStar s : flowStars) {
            if (s == null) continue;
            if (!isDensityActive(s.baseDensityPool, s.densityRank)) {
                s.previousVisible = false;
                continue;
            }
            if (running) {
                // Flow particles also never pause while the device rotates. Living mode retains
                // V56's individual Flow/Curve/Orbit/Pulse/Comet personalities byte-for-byte in its
                // branch; the two travel profiles use a coordinated camera-forward translation.
                if (motionProfile == PROFILE_CLASSIC_FORWARD || motionProfile == PROFILE_DEEP_SPACE) {
                    float surgeT = clamp((surge - .86f) / .48f, 0f, 1f);
                    float profileSurge = motionProfile == PROFILE_CLASSIC_FORWARD
                            ? lerp(.96f, 1.08f, surgeT) : lerp(.92f, 1.18f, surgeT);
                    float layerVariation = motionProfile == PROFILE_DEEP_SPACE
                            ? lerp(.88f, 1.12f, .5f + .5f * (float) Math.sin(s.phase * 1.73f)) : 1f;
                    float advance = s.speed * profileSurge * layerVariation * audioDrive * speedMultiplier * dt;
                    float laneX = motionProfile == PROFILE_DEEP_SPACE
                            ? tunnelSteerX + .014f * (float) Math.sin(time * .119f + s.phase) : 0f;
                    float laneY = motionProfile == PROFILE_DEEP_SPACE
                            ? tunnelSteerY + .011f * (float) Math.cos(time * .103f + s.phase * 1.41f) : 0f;
                    s.x += (forwardX + rightX * laneX + upX * laneY) * advance;
                    s.y += (forwardY + rightY * laneX + upY * laneY) * advance;
                    s.z += (forwardZ + rightZ * laneX + upZ * laneY) * advance;
                    s.remainingDepth -= advance;
                } else {
                    float typeSpeed = s.motionType == MOTION_ORBIT ? .76f
                            : s.motionType == MOTION_COMET ? 1.38f
                            : s.motionType == MOTION_PULSE
                            ? (.88f + .28f * (.5f + .5f * (float) Math.sin(time * s.pulseSpeed + s.phase)))
                            : 1f;
                    float advance = s.speed * typeSpeed * surge * audioDrive * speedMultiplier * dt;

                    if (s.motionType == MOTION_CURVE || s.motionType == MOTION_ORBIT || s.motionType == MOTION_PULSE) {
                        float cx = s.axisY * s.dirZ - s.axisZ * s.dirY;
                        float cy = s.axisZ * s.dirX - s.axisX * s.dirZ;
                        float cz = s.axisX * s.dirY - s.axisY * s.dirX;
                        float bend = s.curveStrength * (s.motionType == MOTION_ORBIT ? 1.65f : 1f)
                                * (.45f + .55f * swirl) * dt;
                        s.dirX += cx * bend;
                        s.dirY += cy * bend;
                        s.dirZ += cz * bend;
                        float n = (float) Math.sqrt(s.dirX * s.dirX + s.dirY * s.dirY + s.dirZ * s.dirZ);
                        if (n > 1.0e-6f) {
                            float inv = 1f / n;
                            s.dirX *= inv; s.dirY *= inv; s.dirZ *= inv;
                        }
                    }

                    s.x += s.dirX * advance;
                    s.y += s.dirY * advance;
                    s.z += s.dirZ * advance;
                    s.remainingDepth -= advance;
                }
            }
            if (s.remainingDepth <= FLOW_NEAR_DEPTH) {
                resetFlowStar(s, false);
                continue;
            }

            Starfield3DMath.rotateVector(cameraQ, s.x, s.y, s.z, rotated);
            float x = rotated[0] - observerX;
            float y = rotated[1] - observerY;
            float z = rotated[2] - observerZ;
            if (z >= -NEAR_PLANE) {
                // Replenish immediately in the current view instead of waiting 600-800 ms.
                // This is the V51 rotation-pause bug fix.
                resetFlowStar(s, false);
                continue;
            }

            float invDepth = 1f / -z;
            float sx = centerX + x * focal * invDepth;
            float sy = centerY - y * focal * invDepth;
            if (sx < -104f || sx > width + 104f || sy < -104f || sy > height + 104f) {
                resetFlowStar(s, false);
                continue;
            }

            float progress = clamp((FLOW_FAR_DEPTH - s.remainingDepth)
                    / (FLOW_FAR_DEPTH - FLOW_NEAR_DEPTH), 0f, 1f);
            float twinkleWave = .5f + .5f * (float) Math.sin(time * s.twinkleSpeed + s.phase);
            float pulseWave = .5f + .5f * (float) Math.sin(time * s.pulseSpeed + s.phase * 1.37f);
            float lifePulse = stellarLifePulse(progress, time, s.phase, s.lifeBreathCycles);
            float twinkle = lerp(.91f, 1.04f, twinkleWave);
            float pulse = lerp(1f - s.sizeBreathDepth * .52f, 1f + s.sizeBreathDepth, lifePulse);
            pulse *= lerp(.982f, 1.032f, pulseWave);
            if (s.motionType == MOTION_PULSE) pulse *= lerp(.958f, 1.06f, pulseWave);
            float depthWave = .5f + .5f * (float) Math.sin(time * .41f - progress * 5.1f + s.phase * .17f);
            float shimmer = lerp(.95f, 1.065f, depthWave);
            float localLight = lerp(1f - s.lightBreathDepth * .58f, 1f + s.lightBreathDepth * .25f, lifePulse);
            float radius = s.size * (.26f + progress * 1.55f) * density;
            radius *= pulse * (1f + visualEnergy * .027f + visualBeat * .047f);
            radius *= starSizeMultiplier;
            radius = clamp(radius, .20f * density * starSizeMultiplier,
                    2.12f * density * starSizeMultiplier);

            float approach = smoothstep(.02f, .99f, progress);
            float lifeAlpha = stellarLifeEnvelope(progress, s.fadeBeforeRecycle, s.fadeStart);
            int alpha = (int) ((64f + 191f * approach) * s.brightness * twinkle * shimmer
                    * localLight * lifeAlpha * starBrightnessMultiplier);
            alpha = Math.min(255, alpha);
            if (!s.fadeBeforeRecycle || progress < s.fadeStart)
                alpha = Math.max(Math.round(34f * starBrightnessMultiplier), alpha);
            else alpha = Math.max(0, alpha);
            float appearanceAlpha = Ui.isLightAppearance() ? .16f : 1f;
            alpha = Math.max(Ui.isLightAppearance() ? 5 : 0, Math.round(alpha * appearanceAlpha));
            int neutralR = Ui.isLightAppearance() ? (s.coolWhite ? 72 : 42) : (s.coolWhite ? 224 : 250);
            int neutralG = Ui.isLightAppearance() ? (s.coolWhite ? 84 : 52) : (s.coolWhite ? 236 : 250);
            int neutralB = Ui.isLightAppearance() ? (s.coolWhite ? 112 : 76) : 255;
            float tint = clamp(s.tintWeight + progress * .12f
                    + visualEnergy * .10f + visualBeat * .15f, .05f, .54f);
            int r = mix(neutralR, Color.red(accent), tint);
            int g = mix(neutralG, Color.green(accent), tint);
            int b = mix(neutralB, Color.blue(accent), tint);

            if (s.previousVisible) {
                float dx = sx - s.previousX;
                float dy = sy - s.previousY;
                float displacement = (float) Math.sqrt(dx * dx + dy * dy);
                boolean deepTrace = motionProfile == PROFILE_DEEP_SPACE && progress > .62f;
                boolean livingTrace = motionProfile == PROFILE_LIVING && (progress > .58f || s.motionType == MOTION_COMET);
                boolean trace = deepTrace || livingTrace;
                if (trace && displacement > .62f && displacement < 28f * density) {
                    float tail = deepTrace ? lerp(.08f, .20f, progress) : lerp(.08f, .22f, progress);
                    paint.setStrokeWidth(Math.max(.22f * density, radius * .17f));
                    float trailAlpha = deepTrace ? .052f : (s.motionType == MOTION_COMET ? .14f : .07f);
                    paint.setColor(Color.argb((int) (alpha * trailAlpha), r, g, b));
                    canvas.drawLine(sx - dx * tail, sy - dy * tail, sx, sy, paint);
                }
            }

            drawRefinedStar(canvas, sx, sy, radius, alpha, r, g, b, progress, time, s.phase,
                    s.flare, s.motionType, true, bloom, density);

            s.previousX = sx;
            s.previousY = sy;
            s.previousVisible = true;
        }
    }


    /**
     * V54 stellar point-spread-function treatment.
     *
     * The important difference from V52/V53 is that the visible glow is no longer a flat
     * translucent disc.  It is a reusable radial-gradient light profile: intensely bright
     * in the central few percent and then falling off cubically-like toward full transparency.
     * This mirrors the point-light idea used by refined WebGL particle shaders while keeping
     * Lunaxy's renderer native Canvas and compatible with older Android devices.
     */
    private void drawRefinedStar(Canvas canvas, float sx, float sy, float radius, int alpha,
                                 int r, int g, int b, float life, float time, float phase,
                                 boolean flare, int motionType, boolean foreground,
                                 float sceneBloom, float density) {
        float near = smoothstep(.10f, .985f, life);

        // The core remains tiny; the perceived size comes from a smooth PSF, not a bubble.
        float coreRadius = Math.max(.14f * density * starSizeMultiplier,
                radius * (foreground ? .58f : .54f));
        coreRadius = Math.min(coreRadius, (foreground ? 1.18f : 1.02f)
                * density * starSizeMultiplier);

        // A faint halo may extend several core radii, just like V49, but its alpha decays to
        // exactly zero at the edge.  Hence large near stars glow without exposing a circle.
        float glowBreath = .5f + .5f * (float) Math.sin(time * .29f + phase * .61f);
        float glowScale = foreground ? lerp(3.6f, 5.8f, near) : lerp(3.0f, 5.05f, near);
        glowScale *= lerp(.94f, 1.08f, glowBreath) * (1f + sceneBloom * .030f);
        float glowRadius = Math.max(.84f * density * starSizeMultiplier, coreRadius * glowScale);
        glowRadius = Math.min(glowRadius, (foreground ? 9.0f : 7.6f)
                * density * starSizeMultiplier);

        float tint = colorTintDistance(r, g, b);
        Shader shader = glowShaders[paletteIndex(tint)];
        if (shader != null) {
            glowMatrix.reset();
            glowMatrix.setScale(glowRadius, glowRadius);
            glowMatrix.postTranslate(sx, sy);
            shader.setLocalMatrix(glowMatrix);
            glowPaint.setShader(shader);
            // Far stars keep a detectable but restrained atmosphere; near stars bloom more.
            float haloStrength = (foreground ? .43f : .37f) + near * (foreground ? .29f : .23f);
            haloStrength *= lerp(.92f, 1.06f, glowBreath);
            glowPaint.setAlpha(clamp255((int) (alpha * haloStrength)));
            canvas.drawCircle(sx, sy, glowRadius, glowPaint);
            glowPaint.setShader(null);
            glowPaint.setAlpha(255);
        }

        // Coloured stellar body.  It is intentionally smaller than V53 and therefore reads
        // as a point of light even when flying close to the virtual camera.
        int bodyAlpha = clamp255((int) (alpha * lerp(.94f, 1f, near)));
        paint.setColor(Color.argb(bodyAlpha, r, g, b));
        canvas.drawCircle(sx, sy, coreRadius, paint);

        // Sub-pixel hot nucleus: this is what gives the point a crisp photographic texture.
        int nucleusTarget = Ui.isLightAppearance() ? 18 : 255;
        int hotR = mix(r, nucleusTarget, .78f);
        int hotG = mix(g, nucleusTarget, .78f);
        int hotB = mix(b, nucleusTarget, .78f);
        float nucleusRadius = Math.max(.11f * density * starSizeMultiplier, coreRadius * .26f);
        int hotAlpha = clamp255((int) (alpha * (.92f + near * .08f)));
        canvas.drawCircle(sx, sy, nucleusRadius, colorPaint(hotAlpha, hotR, hotG, hotB));

        // Rare diffraction spikes use two low-frequency envelopes.  Their rise/fall is
        // continuous over seconds, never threshold-flashed; most stars never show them.
        if (flare || motionType == MOTION_COMET) {
            float slow = .5f + .5f * (float) Math.sin(time * .31f + phase * 1.07f);
            float second = .5f + .5f * (float) Math.sin(time * .173f + phase * .43f + 1.2f);
            float sparkle = smoothstep(.46f, .94f, slow * .72f + second * .28f)
                    * smoothstep(.34f, .96f, life);
            if (sparkle > .004f) {
                float arm = coreRadius * (1.8f + 4.0f * sparkle);
                float fine = Math.max(.12f * density, coreRadius * .075f);
                paint.setStrokeWidth(fine);
                paint.setColor(Color.argb(clamp255((int) (alpha * .21f * sparkle)), hotR, hotG, hotB));
                canvas.drawLine(sx - arm, sy, sx + arm, sy, paint);
                canvas.drawLine(sx, sy - arm * .72f, sx, sy + arm * .72f, paint);

                // Only the rarest flare stars get a very faint diagonal pair.
                if (flare && sparkle > .58f) {
                    float diag = arm * .45f;
                    paint.setStrokeWidth(Math.max(.09f * density, fine * .68f));
                    paint.setColor(Color.argb(clamp255((int) (alpha * .065f * sparkle)), r, g, b));
                    canvas.drawLine(sx - diag, sy - diag, sx + diag, sy + diag, paint);
                    canvas.drawLine(sx - diag, sy + diag, sx + diag, sy - diag, paint);
                }
            }
        }
    }

    private boolean isDensityActive(boolean baseDensityPool, float rank) {
        float density = starDensityMultiplier;
        if (motionProfile == PROFILE_CLASSIC_FORWARD) density *= CLASSIC_TRAVEL_DENSITY_SCALE;
        else if (motionProfile == PROFILE_DEEP_SPACE) density *= DEEP_SPACE_DENSITY_SCALE;
        if (density < 1f) return baseDensityPool && rank <= Math.max(0f, density);
        if (baseDensityPool) return true;
        float extraFraction = (density - 1f) / Math.max(.001f, MAX_DENSITY_MULTIPLIER - 1f);
        return rank <= extraFraction;
    }

    /** Rebuild a tiny gradient palette only when the cover accent changes, never per frame. */
    private void rebuildGlowPalette() {
        final float[] tintStops = new float[]{.05f, .16f, .28f, .41f, .55f};
        for (int i = 0; i < glowShaders.length; i++) {
            int neutralR = Ui.isLightAppearance() ? (i == 0 ? 74 : 48) : (i == 0 ? 228 : 247);
            int neutralG = Ui.isLightAppearance() ? (i == 0 ? 86 : 58) : (i == 0 ? 238 : 248);
            int neutralB = Ui.isLightAppearance() ? (i == 0 ? 116 : 84) : 255;
            int rr = mix(neutralR, Color.red(accent), tintStops[i]);
            int gg = mix(neutralG, Color.green(accent), tintStops[i]);
            int bb = mix(neutralB, Color.blue(accent), tintStops[i]);
            glowPalette[i] = Color.rgb(rr, gg, bb);

            // This alpha profile approximates pow(1-r, 3): bright center, fast organic
            // falloff, then a long almost-invisible tail.  The transparent last stop is
            // what removes the visible "bubble boundary" of solid-circle bloom.
            int glowTarget = Ui.isLightAppearance() ? 18 : 255;
            int hot = Color.argb(255, mix(rr, glowTarget, .82f), mix(gg, glowTarget, .82f), mix(bb, glowTarget, .82f));
            int body = Color.argb(196, rr, gg, bb);
            int inner = Color.argb(92, rr, gg, bb);
            int soft = Color.argb(38, rr, gg, bb);
            int haze = Color.argb(10, rr, gg, bb);
            int zero = Color.argb(0, rr, gg, bb);
            glowShaders[i] = new RadialGradient(0f, 0f, 1f,
                    new int[]{hot, body, inner, soft, haze, zero},
                    new float[]{0f, .070f, .22f, .47f, .76f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    private int paletteIndex(float tint) {
        if (tint < .11f) return 0;
        if (tint < .22f) return 1;
        if (tint < .34f) return 2;
        if (tint < .48f) return 3;
        return 4;
    }

    /** Approximate how strongly this already-mixed body colour leans toward the cover accent. */
    private float colorTintDistance(int r, int g, int b) {
        int ar = Color.red(accent), ag = Color.green(accent), ab = Color.blue(accent);
        float accentDistance = Math.abs(r - ar) + Math.abs(g - ag) + Math.abs(b - ab);
        float whiteDistance = Math.abs(r - 244) + Math.abs(g - 248) + Math.abs(b - 255);
        float total = accentDistance + whiteDistance;
        return total < 1f ? .20f : clamp(whiteDistance / total, .04f, .60f);
    }

    /** Several gentle light/size breaths across one particle lifetime. */
    private static float stellarLifePulse(float life, float time, float phase, float cycles) {
        float a = life * cycles * (float) (Math.PI * 2.0) + phase + time * .085f;
        float primary = .5f + .5f * (float) Math.sin(a);
        float secondary = .5f + .5f * (float) Math.sin(a * .51f + phase * .73f + 1.17f);
        float mixed = primary * .76f + secondary * .24f;
        return smoothstep(.04f, .96f, mixed);
    }

    /**
     * Most stars fade out slowly near the end of their flight; a minority keep their light
     * and simply leave/recycle, which prevents the scene from looking mechanically uniform.
     */
    private static float stellarLifeEnvelope(float life, boolean fadeBeforeRecycle, float fadeStart) {
        float fadeIn = smoothstep(0f, .075f, life);
        if (!fadeBeforeRecycle) return fadeIn;
        float fadeOut = 1f - smoothstep(fadeStart, .995f, life);
        return clamp(fadeIn * fadeOut, 0f, 1f);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private Paint colorPaint(int alpha, int r, int g, int b) {
        paint.setColor(Color.argb(alpha, r, g, b));
        return paint;
    }

    private void updateAudio(float dt) {
        float targetEnergy = 0f, targetBeat = 0f;
        if (audioLevels != null) {
            try {
                targetEnergy = clamp(audioLevels.energy(), 0f, 1f);
                targetBeat = clamp(audioLevels.beat(), 0f, 1f);
            } catch (Exception ignored) { }
        }
        visualEnergy += (targetEnergy - visualEnergy) * (1f - (float) Math.exp(-5.0f * dt));
        visualBeat += (targetBeat - visualBeat)
                * (1f - (float) Math.exp(-(targetBeat > visualBeat ? 14f : 6.5f) * dt));
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null || event.values == null) return;
        if (event.sensor == rotationSensor) {
            updateRotation(event);
        } else if (event.sensor == linearAccelerationSensor && event.values.length >= 3) {
            targetShiftX = clamp(-event.values[0] * .030f * sensitivity, -.26f, .26f);
            targetShiftY = clamp(-event.values[1] * .026f * sensitivity, -.24f, .24f);
            targetShiftZ = clamp(event.values[2] * .018f * sensitivity, -.16f, .16f);
        }
    }

    private void updateRotation(SensorEvent event) {
        if (event.values.length < 3) return;
        try {
            SensorManager.getQuaternionFromVector(sensorQ, event.values);
            Starfield3DMath.normalize(sensorQ);
            if (!hasBaseline) {
                Starfield3DMath.copy(sensorQ, baselineSensorQ);
                Starfield3DMath.identity(targetCameraQ);
                Starfield3DMath.identity(cameraQ);
                Starfield3DMath.copy(sensorQ, lastSensorQ);
                hasBaseline = true;
                hasLastSensorQ = true;
                lastRotationSensorNs = event.timestamp;
                clearTrails();
                return;
            }

            Starfield3DMath.relativeCamera(baselineSensorQ, sensorQ, targetCameraQ);
            scaleQuaternionAngle(targetCameraQ, sensitivity);

            if (hasLastSensorQ && lastRotationSensorNs > 0L && event.timestamp > lastRotationSensorNs) {
                float seconds = (event.timestamp - lastRotationSensorNs) / 1_000_000_000f;
                if (seconds > .0005f && seconds < .25f) {
                    float instant = Starfield3DMath.angularDistance(lastSensorQ, sensorQ) / seconds;
                    angularSpeed += (clamp(instant, 0f, 7f) - angularSpeed) * .32f;
                }
            }
            Starfield3DMath.copy(sensorQ, lastSensorQ);
            hasLastSensorQ = true;
            lastRotationSensorNs = event.timestamp;
        } catch (Exception ignored) { }
    }

    private static void scaleQuaternionAngle(float[] q, float gain) {
        if (q == null || q.length < 4 || Math.abs(gain - 1f) < .001f) return;
        // Use the shortest equivalent quaternion before scaling its axis-angle magnitude.
        if (q[0] < 0f) { q[0] = -q[0]; q[1] = -q[1]; q[2] = -q[2]; q[3] = -q[3]; }
        float w = clamp(q[0], -1f, 1f);
        float half = (float) Math.acos(w);
        float sinHalf = (float) Math.sin(half);
        if (Math.abs(sinHalf) < 1.0e-6f) return;
        float ax = q[1] / sinHalf, ay = q[2] / sinHalf, az = q[3] / sinHalf;
        float scaledHalf = half * gain;
        float outSin = (float) Math.sin(scaledHalf);
        q[0] = (float) Math.cos(scaledHalf);
        q[1] = ax * outSin; q[2] = ay * outSin; q[3] = az * outSin;
        Starfield3DMath.normalize(q);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private static void randomUnitVector(Random r, float[] out3) {
        float u = r.nextFloat() * 2f - 1f;
        float theta = r.nextFloat() * (float) (Math.PI * 2.0);
        float ring = (float) Math.sqrt(Math.max(0f, 1f - u * u));
        out3[0] = ring * (float) Math.cos(theta);
        out3[1] = u;
        out3[2] = ring * (float) Math.sin(theta);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) return x >= edge1 ? 1f : 0f;
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int mix(int a, int b, float t) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * t)));
    }
}
