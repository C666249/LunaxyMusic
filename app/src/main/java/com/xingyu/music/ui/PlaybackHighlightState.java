package com.xingyu.music.ui;

/** Song-owned visual clock. UI-thread only; independent of Drawable/View attachment. */
public final class PlaybackHighlightState {
    public final String songKey;
    private boolean playing;
    private long phaseAt;
    private float savedPhase;
    private int accentFrom;
    private int accentTo;
    private long accentAt;

    public PlaybackHighlightState(String songKey, int accent, long now) {
        this.songKey = songKey;
        accentFrom = accentTo = accent;
        phaseAt = accentAt = now;
    }

    public void setPlaying(boolean value, long now) {
        if (playing == value) return;
        savedPhase = phase(now);
        phaseAt = now;
        playing = value;
    }

    public boolean isPlaying() { return playing; }

    public float phase(long now) {
        return (savedPhase + (playing ? Math.max(0L, now - phaseAt) / 43000f : 0f)) % 1f;
    }

    public void setAccent(int color, long now) {
        if (color == accentTo) return; // rebinding must not restart the transition
        accentFrom = accent(now);
        accentTo = color;
        accentAt = now;
    }

    public boolean isTransitioning(long now) {
        return accentFrom != accentTo && now - accentAt < 620L;
    }

    public int accent(long now) {
        float t = Math.max(0f, Math.min(1f, (now - accentAt) / 620f));
        t = t * t * (3f - 2f * t);
        int color = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (accentFrom >>> shift) & 255;
            int b = (accentTo >>> shift) & 255;
            color |= Math.round(a + (b - a) * t) << shift;
        }
        return color;
    }
}
