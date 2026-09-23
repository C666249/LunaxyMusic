package com.xingyu.music.data;

import com.xingyu.music.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Low-concurrency exact identity discovery for imported/library tracks. */
public final class TrackVariantEnricher {
    private final KuwoMusicApi kuwo = new KuwoMusicApi();
    private final KugouMusicApi kugou = new KugouMusicApi();
    private final NeteaseApi netease = new NeteaseApi();
    private final TrackVariantStore store;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public TrackVariantEnricher(TrackVariantStore store) { this.store = store; }

    public Song enrichIfNeeded(Song song, PlaybackTrace trace) {
        if (song == null) return null;
        Song current = store.enrich(song);
        if (!store.shouldEnrich(current)) return current;
        store.markEnrichAttempt(current);
        String query = query(current);
        List<Callable<List<Song>>> jobs = new ArrayList<>();
        if (current.variant("kw") == null) jobs.add(() -> kuwo.search(query, 12));
        if (current.variant("kg") == null) jobs.add(() -> kugou.search(query, 12));
        // For QQ anchors, exact NetEase identity is useful because QDY/wy is the most proven
        // route on the user's V47/V2 tests. ExactTrackMatcher still rejects covers/Live/remixes.
        if ("tx".equals(current.source) && current.variant("wy") == null) jobs.add(() -> netease.search(query, 12));
        if (jobs.isEmpty()) return current;

        try {
            List<Future<List<Song>>> futures = pool.invokeAll(jobs, 7, TimeUnit.SECONDS);
            for (Future<List<Song>> f : futures) {
                if (f == null || f.isCancelled()) continue;
                try { current = mergeBestExact(current, f.get()); } catch (Exception ignored) { }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        store.remember(current);
        if (trace != null) trace.add("identity", variantSummary(current));
        return current;
    }

    private static Song mergeBestExact(Song anchor, List<Song> candidates) {
        Song best = null; double bestScore = 0d;
        if (candidates != null) for (Song s : candidates) {
            double c = ExactTrackMatcher.confidence(anchor, s);
            if (c >= 0.98d && c > bestScore) { bestScore = c; best = s; }
        }
        return best == null ? anchor : anchor.withVariant(best);
    }

    private static String query(Song s) {
        String artist = s == null ? "" : s.artist;
        if (artist.contains(" / ")) artist = artist.substring(0, artist.indexOf(" / "));
        return (s == null ? "" : s.title) + (artist.trim().isEmpty() ? "" : " " + artist.trim());
    }

    private static String variantSummary(Song s) {
        StringBuilder b = new StringBuilder("exact variants ");
        if (s != null) for (com.xingyu.music.model.SourceVariant v : s.variants()) { if (b.charAt(b.length()-1) != ' ') b.append(','); b.append(v.source); }
        return b.toString();
    }

    public void destroy() { pool.shutdownNow(); }
}
