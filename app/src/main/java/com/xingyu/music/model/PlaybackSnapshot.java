package com.xingyu.music.model;

public final class PlaybackSnapshot {
    public final Song song;
    public final boolean playing;
    public final long positionMs;
    public final long durationMs;
    public final int queueIndex;
    public final int queueSize;
    public final String error;

    public PlaybackSnapshot(Song song, boolean playing, long positionMs, long durationMs, int queueIndex, int queueSize, String error) {
        this.song = song;
        this.playing = playing;
        this.positionMs = Math.max(0L, positionMs);
        this.durationMs = Math.max(0L, durationMs);
        this.queueIndex = queueIndex;
        this.queueSize = queueSize;
        this.error = error;
    }
}
