package com.xingyu.music.ui;

/** Shared distance scale for preview, release and user preference. */
public final class VinylGesturePolicy {
    private VinylGesturePolicy() { }
    public static float slotDistance(float width, float sensitivity) {
        return Math.max(1f, width * .36f / Math.max(.5f, Math.min(1.5f, sensitivity)));
    }
    public static boolean shouldCommit(float dx, float dy, float vx, float width, float density, float sensitivity) {
        float distance = slotDistance(width, sensitivity);
        return Math.abs(dx) > Math.abs(dy) * 1.28f
                && (Math.abs(dx) >= distance * .72f
                || (Math.abs(dx) >= distance * .5f && Math.abs(vx) >= 1000f * density && dx * vx > 0f));
    }
    public static float velocitySlots(float vx, float width, float sensitivity) {
        return Math.max(-.25f, Math.min(.25f, vx * .045f / slotDistance(width, sensitivity)));
    }
}
