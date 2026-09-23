package com.xingyu.music.ui;

public final class VinylGesturePolicyTest {
    public static void main(String[] args) {
        check(VinylGesturePolicy.slotDistance(400, 1) == 144f, "default needs 144dp per song on 400dp screen");
        check(VinylGesturePolicy.slotDistance(400, .5f) > VinylGesturePolicy.slotDistance(400, 1.5f), "lower sensitivity needs longer drag");
        check(!VinylGesturePolicy.shouldCommit(25, 0, 3000, 400, 1, 1), "short fast flick cannot skip");
        check(!VinylGesturePolicy.shouldCommit(75, 0, 0, 400, 1, 1), "small slow drag rolls back");
        check(VinylGesturePolicy.shouldCommit(110, 0, 0, 400, 1, 1), "deliberate slow drag commits");
        check(!VinylGesturePolicy.shouldCommit(110, 180, 0, 400, 1, 1), "vertical drag does not commit");
        check(VinylGesturePolicy.velocitySlots(100000, 400, 1) <= .25f, "fling projection capped");
        System.out.println("7 gesture checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
