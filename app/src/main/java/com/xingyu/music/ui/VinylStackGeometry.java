package com.xingyu.music.ui;

/** Continuous depth field; offsets are relative queue slots, never page bounds. */
public final class VinylStackGeometry {
    private VinylStackGeometry() { }
    public static float x(float offset) {
        float d = Math.abs(offset);
        float x = d <= 1f ? .19f * d : d <= 2f ? .19f + .14f * (d - 1f)
                : .33f + .10f * Math.min(1f, d - 2f);
        return Math.copySign(x, offset);
    }
    public static float scale(float offset) { return 1f - .12f * Math.min(3f, Math.abs(offset)); }
    public static float alpha(float offset) {
        float d = Math.abs(offset);
        return d <= 1f ? 1f - .65f * d : d <= 2f ? .35f - .20f * (d - 1f)
                : Math.max(0f, .15f * (3f - d));
    }
    public static int neighbourIndex(int current, int offset, int count) {
        if (count <= 1 || current < 0 || current >= count || Math.abs(offset) >= count) return -1;
        return Math.floorMod(current + offset, count);
    }
}
