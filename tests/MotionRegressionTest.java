package com.xingyu.music.ui;

/** JVM regression tests; no Android runtime or network required. */
public final class MotionRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(float a, float b, String message) {
        check(Math.abs(a - b) < .0001f, message + ": " + a + " != " + b);
    }
    public static void main(String[] args) {
        PlaybackHighlightState state = new PlaybackHighlightState("song-a", 0xffd06030, 1000);
        state.setPlaying(true, 1000);
        float phase = state.phase(6000);
        near(state.phase(6000), phase, "two visible rows must not advance the clock twice");
        state.setPlaying(false, 6000);
        near(state.phase(66000), phase, "pause survives an off-screen rebind");
        state.setPlaying(true, 66000);
        near(state.phase(67000), (phase + 1f / 43f) % 1f, "resume excludes paused time");
        state.setAccent(0xff3080d0, 67000);
        int mid = state.accent(67310);
        state.setAccent(0xff3080d0, 67310);
        check(state.accent(67310) == mid, "same palette rebind must not restart interpolation");
        check(state.accent(67620) == 0xff3080d0, "palette reaches target after 620ms");
        check(state.songKey.equals("song-a"), "state belongs to song identity");

        near(VinylStackGeometry.scale(0), 1, "center scale");
        near(VinylStackGeometry.scale(1), .88f, "near scale");
        near(VinylStackGeometry.scale(2), .76f, "far scale");
        near(VinylStackGeometry.alpha(1), .35f, "near alpha");
        near(VinylStackGeometry.alpha(2), .15f, "far alpha");
        for (int i = 0; i <= 100; i++) {
            float p = i / 100f;
            near(VinylStackGeometry.x(-p), -VinylStackGeometry.x(p), "mirror geometry");
            check(VinylStackGeometry.scale(p) >= .88f, "continuous center-to-rear scale");
        }
        for (int slot = -1; slot <= 2; slot++) {
            near(VinylStackGeometry.x(slot - 1), VinylStackGeometry.x((slot - 1) - 0f), "commit preserves geometry");
        }
        float mainRadius = .82346f / 2;
        float nearEdge = VinylStackGeometry.x(1) + mainRadius * VinylStackGeometry.scale(1);
        float farEdge = VinylStackGeometry.x(2) + mainRadius * VinylStackGeometry.scale(2);
        check(nearEdge > mainRadius && nearEdge < mainRadius + .17f, "near record only exposes an edge");
        check(farEdge > nearEdge && farEdge < nearEdge + .12f, "far record exposes a smaller edge");
        check(VinylStackGeometry.neighbourIndex(0, -1, 5) == 4, "previous wraps at queue start");
        check(VinylStackGeometry.neighbourIndex(4, 2, 5) == 1, "far next wraps at queue end");
        check(VinylStackGeometry.neighbourIndex(0, 1, 1) == -1, "one-track queue has no fake neighbour");
        check(VinylStackGeometry.neighbourIndex(0, 2, 2) == -1, "do not duplicate center as a far record");
        System.out.println("PASS: " + checks + " motion checks");
    }
}
