package com.xingyu.music.voice;

import com.xingyu.music.data.KugouMusicApi;
import com.xingyu.music.data.KuwoMusicApi;
import com.xingyu.music.data.NeteaseApi;
import com.xingyu.music.data.QQMusicApi;
import com.xingyu.music.model.Song;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Small isolated catalog search used only by voice auto-play commands. */
public final class VoiceMusicSearch {
    private final NeteaseApi netease = new NeteaseApi();
    private final QQMusicApi qq = new QQMusicApi();
    private final KuwoMusicApi kuwo = new KuwoMusicApi();
    private final KugouMusicApi kugou = new KugouMusicApi();
    private final ExecutorService catalogs = Executors.newFixedThreadPool(4);

    public List<Song> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>();
        int each = Math.max(8, Math.min(20, limit));
        List<Callable<List<Song>>> jobs = new ArrayList<>();
        jobs.add(() -> safe(() -> netease.search(q, each)));
        jobs.add(() -> safe(() -> qq.search(q, each)));
        jobs.add(() -> safe(() -> kuwo.search(q, each)));
        jobs.add(() -> safe(() -> kugou.search(q, each)));
        LinkedHashMap<String, Song> merged = new LinkedHashMap<>();
        try {
            List<Future<List<Song>>> futures = catalogs.invokeAll(jobs, 6500L, TimeUnit.MILLISECONDS);
            for (Future<List<Song>> future : futures) {
                if (future == null || future.isCancelled()) continue;
                List<Song> songs;
                try { songs = future.get(); } catch (Exception ignored) { continue; }
                for (Song song : songs) {
                    if (song == null) continue;
                    Song old = merged.get(song.key());
                    merged.put(song.key(), old == null ? song : old.withVariant(song));
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        ArrayList<Song> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> Integer.compare(score(b, q), score(a, q)));
        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }

    public List<Song> searchArtist(String artist, int limit) {
        String q = artist == null ? "" : artist.trim();
        ArrayList<Song> out = new ArrayList<>();
        String n = norm(q);
        for (Song song : search(q, Math.max(limit * 2, 24))) {
            if (norm(song.artist).contains(n)) out.add(song);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public Song bestSong(String query) {
        List<Song> songs = search(query, 12);
        return songs.isEmpty() ? null : songs.get(0);
    }

    public void destroy() { catalogs.shutdownNow(); }

    private int score(Song song, String query) {
        String q = norm(query), title = norm(song.title), artist = norm(song.artist);
        int score = 0;
        if (title.equals(q)) score += 100;
        else if (title.startsWith(q)) score += 70;
        else if (title.contains(q)) score += 45;
        if (artist.equals(q)) score += 75;
        else if (artist.startsWith(q)) score += 45;
        else if (artist.contains(q)) score += 28;
        if (song.durationMs > 45_000L) score += 4;
        return score;
    }

    private static String norm(String value) {
        return (value == null ? "" : value.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }

    private interface SearchCall { List<Song> run() throws Exception; }
    private static List<Song> safe(SearchCall call) {
        try { List<Song> out = call.run(); return out == null ? new ArrayList<>() : out; }
        catch (Exception ignored) { return new ArrayList<>(); }
    }
}
