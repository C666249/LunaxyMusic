package com.xingyu.music.ui;

public final class Player926Policy {
    private Player926Policy() { }
    public static float singleAlpha(float depth) { return Math.max(0f, 1f-Math.abs(depth)); }
    public static float friction(float speedDp) {
        float t=Math.max(0f, Math.min(1f, (Math.abs(speedDp)-1200f)/3800f));
        return 1f-.55f*t;
    }
    public static float edgeStep(float penetration, long heldMs) {
        float p=Math.max(0f,Math.min(1f,penetration));
        float ramp=Math.max(0f,Math.min(1f,heldMs/1400f));
        return 4f+32f*p*p*(.35f+.65f*ramp);
    }
}
