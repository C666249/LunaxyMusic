package com.xingyu.music.data;

import android.os.SystemClock;

import com.xingyu.music.model.PlaybackSnapshot;
import com.xingyu.music.model.Song;

import java.util.UUID;

/**
 * Observes UI playback snapshots and converts them into recommendation-only behavior signals.
 * It never controls playback and all database writes are asynchronous.
 */
public final class PlaybackBehaviorTracker {
    private static final long PROGRESS_FLUSH_MS = 30_000L;
    private final PersonalizationStore store;
    private final String sessionId = Long.toHexString(System.currentTimeMillis()) + "-" + UUID.randomUUID().toString().substring(0, 8);

    private Song song;
    private long maxPositionMs;
    private long lastPositionMs;
    private long pendingListenedMs;
    private long totalListenedMs;
    private long lastFlushElapsed;
    private boolean completed;
    private boolean manualSkip;
    private long manualSkipMarkedAt;
    private boolean hadError;

    public PlaybackBehaviorTracker(PersonalizationStore store) { this.store = store; }

    public void markManualSkip() { manualSkip = true; manualSkipMarkedAt = SystemClock.elapsedRealtime(); }

    public void onSnapshot(PlaybackSnapshot snapshot) {
        if (snapshot == null || snapshot.song == null) {
            finishCurrent(false);
            return;
        }
        Song incoming = snapshot.song;
        if (song == null || !song.key().equals(incoming.key())) {
            finishCurrent(true);
            song = incoming;
            maxPositionMs = Math.max(0L, snapshot.positionMs);
            lastPositionMs = Math.max(0L, snapshot.positionMs);
            pendingListenedMs = 0L;
            totalListenedMs = 0L;
            lastFlushElapsed = SystemClock.elapsedRealtime();
            completed = false;
            hadError = snapshot.error != null;
            store.recordAsync(song, PersonalizationStore.PLAY_START, snapshot.positionMs, effectiveDuration(snapshot), ratio(snapshot), sessionId, 0L);
            manualSkip = false;
            manualSkipMarkedAt = 0L;
            return;
        }

        hadError |= snapshot.error != null;
        long pos = Math.max(0L, snapshot.positionMs);
        long duration = effectiveDuration(snapshot);
        if (snapshot.playing) {
            long delta = pos - lastPositionMs;
            if (delta > 0L && delta <= 30_000L) { pendingListenedMs += delta; totalListenedMs += delta; }
        }
        if (lastPositionMs > 45_000L && pos < 4_000L && duration > 0L && lastPositionMs >= duration * 0.72d) {
            store.recordAsync(song, PersonalizationStore.REPLAY, pos, duration, ratio(pos, duration), sessionId, 0L);
            completed = false;
            maxPositionMs = pos;
        } else {
            maxPositionMs = Math.max(maxPositionMs, pos);
        }
        lastPositionMs = pos;
        long now = SystemClock.elapsedRealtime();
        if (manualSkip && manualSkipMarkedAt > 0L && now - manualSkipMarkedAt > 5_000L) {
            manualSkip = false;
            manualSkipMarkedAt = 0L;
        }

        double r = ratio(maxPositionMs, duration);
        if (!completed && duration >= 30_000L && r >= 0.88d && qualifiesAsListen(duration)) {
            completed = true;
            store.recordAsync(song, PersonalizationStore.COMPLETE, maxPositionMs, duration, r, sessionId, 0L);
        }
        if (pendingListenedMs >= PROGRESS_FLUSH_MS || now - lastFlushElapsed >= 30_000L) flushProgress(duration);
    }

    public void finish(boolean classifySkip) { finishCurrent(classifySkip); }

    private void finishCurrent(boolean classifySkip) {
        if (song == null) { manualSkip = false; return; }
        long duration = Math.max(song.durationMs, 0L);
        flushProgress(duration);
        double r = ratio(maxPositionMs, duration);
        if (!completed && duration >= 30_000L && r >= 0.88d && qualifiesAsListen(duration)) {
            completed = true;
            store.recordAsync(song, PersonalizationStore.COMPLETE, maxPositionMs, duration, r, sessionId, 0L);
        } else if (classifySkip && !hadError && (manualSkip || (maxPositionMs >= 3_000L && r < 0.25d))) {
            store.recordAsync(song, PersonalizationStore.SKIP, maxPositionMs, duration, r, sessionId, 0L);
        }
        song = null;
        maxPositionMs = 0L;
        lastPositionMs = 0L;
        pendingListenedMs = 0L;
        totalListenedMs = 0L;
        completed = false;
        hadError = false;
        manualSkip = false;
        manualSkipMarkedAt = 0L;
    }

    private boolean qualifiesAsListen(long duration) {
        if (duration <= 0L) return totalListenedMs >= 60_000L;
        long threshold = Math.min(240_000L, Math.max(30_000L, duration / 2L));
        return totalListenedMs >= threshold;
    }

    private void flushProgress(long duration) {
        if (song == null || pendingListenedMs <= 0L) return;
        long delta = pendingListenedMs;
        pendingListenedMs = 0L;
        lastFlushElapsed = SystemClock.elapsedRealtime();
        store.recordAsync(song, PersonalizationStore.PROGRESS, maxPositionMs, duration,
                ratio(maxPositionMs, duration), sessionId, delta);
    }

    private long effectiveDuration(PlaybackSnapshot s) {
        return Math.max(s == null ? 0L : s.durationMs, s == null || s.song == null ? 0L : s.song.durationMs);
    }

    private static double ratio(PlaybackSnapshot s) {
        return s == null ? 0d : ratio(s.positionMs, Math.max(s.durationMs, s.song == null ? 0L : s.song.durationMs));
    }
    private static double ratio(long pos, long duration) {
        if (duration <= 0L) return 0d;
        return Math.max(0d, Math.min(1.2d, (double) Math.max(0L, pos) / duration));
    }
}
