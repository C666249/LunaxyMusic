package com.xingyu.music.model;

/** One karaoke-timed word / syllable inside a lyric line. */
public final class LyricWord {
    public final long startMs;
    public final long durationMs;
    public final String text;

    public LyricWord(long startMs, long durationMs, String text) {
        this.startMs = Math.max(0L, startMs);
        this.durationMs = Math.max(1L, durationMs);
        this.text = text == null ? "" : text;
    }

    public long endMs() { return startMs + durationMs; }
}
