package com.xingyu.music.data;

import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.Song;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds a local-first long/medium/short/session taste profile from explicit and implicit signals. */
public final class TasteProfileEngine {
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private final PersonalizationStore store;

    public TasteProfileEngine(PersonalizationStore store) { this.store = store; }

    public Profile build(List<Song> favorites, List<ImportedPlaylist> playlists, List<Song> history) {
        PersonalizationStore.Snapshot snapshot = store.snapshot();
        long now = snapshot.now;
        Map<String, MutableArtist> artist = new LinkedHashMap<>();
        Map<String, Double> track = new HashMap<>();
        Set<String> recentTracks = new HashSet<>();
        Set<String> recentSkippedTracks = new HashSet<>();

        // Explicit library intent remains a strong bootstrap, so V86 is immediately useful before
        // the new behavior database has accumulated weeks of history.
        addLibraryArtists(artist, favorites, 8.0);
        if (playlists != null) for (ImportedPlaylist p : playlists) addLibraryArtists(artist, p == null ? null : p.songs, 3.2);
        if (history != null) {
            for (int i = 0; i < history.size(); i++) {
                Song s = history.get(i); if (s == null) continue;
                double w = 2.4 * Math.exp(-i / 12.0);
                addArtist(artist, s.artist, w, "recent-library");
                track.put(s.key(), track.getOrDefault(s.key(), 0d) + w);
                if (i < 20) recentTracks.add(s.key());
            }
        }

        // Long-term aggregate behavior keeps durable preferences beyond the rolling windows.
        for (PersonalizationStore.ArtistSignal s : snapshot.artists.values()) {
            double longScore = 0.38 * s.starts + 2.4 * s.completes + 3.2 * s.replays
                    + 6.2 * s.favoriteAdds - 6.8 * s.favoriteRemoves
                    + 3.2 * s.playlistAdds - 3.6 * s.playlistRemoves
                    - 2.5 * s.skips + Math.min(8d, s.listenedMs / 1_800_000d);
            double ageDays = s.lastEventAt <= 0 ? 365d : Math.max(0d, (now - s.lastEventAt) / (double) DAY);
            double freshness = 0.50 + 0.50 * Math.exp(-ageDays / 110d);
            addArtist(artist, s.artistName, longScore * freshness * 0.55d, "long");
        }

        // Bounded daily aggregation preserves explicit 7/30/180-day taste windows without keeping
        // hundreds of thousands of raw progress events forever.
        long today = Math.max(0L, now / DAY);
        for (PersonalizationStore.DailyArtistSignal s : snapshot.dailyArtists) {
            double ageDays = Math.max(0d, today - s.dayBucket);
            if (ageDays > 180d) continue;
            double base = 0.42 * s.starts + 3.0 * s.completes + 4.0 * s.replays
                    + 8.0 * s.favoriteAdds - 8.5 * s.favoriteRemoves
                    + 4.0 * s.playlistAdds - 4.4 * s.playlistRemoves
                    - 3.4 * s.skips + Math.min(3.0d, s.listenedMs / 1_800_000d);
            if (base == 0d) continue;
            // 180-day durable trend.
            addArtist(artist, s.artistName, base * 0.22d * Math.exp(-ageDays / 120d), "long-180");
            // 30-day medium trend.
            if (ageDays <= 30d) addArtist(artist, s.artistName, base * 0.48d * Math.exp(-ageDays / 18d), "medium-30");
            // 7-day short trend.
            if (ageDays <= 7d) addArtist(artist, s.artistName, base * 0.82d * Math.exp(-ageDays / 3.2d), "short");
        }
        for (PersonalizationStore.TrackSignal s : snapshot.tracks.values()) {
            double score = 0.45 * s.starts + 3.0 * s.completes + 4.0 * s.replays - 3.2 * s.skips
                    + 7.5 * s.favoriteAdds - 8.0 * s.favoriteRemoves
                    + 3.8 * s.playlistAdds - 4.0 * s.playlistRemoves
                    + Math.min(8d, s.listenedMs / 1_800_000d);
            track.put(s.trackKey, track.getOrDefault(s.trackKey, 0d) + score);
        }

        // Raw recent events are retained for track-level recency/skip and current-session reaction.
        // 7/30/180-day artist trends above come from daily aggregates, so high-frequency progress
        // events cannot evict long-horizon taste history.
        String newestSession = "";
        for (PersonalizationStore.RecentEvent e : snapshot.recentEvents) {
            String sid = safe(e.sessionId);
            if (!sid.isEmpty() && !"library".equals(sid)) { newestSession = sid; break; }
        }
        for (PersonalizationStore.RecentEvent e : snapshot.recentEvents) {
            long age = Math.max(0L, now - e.ts);
            double days = age / (double) DAY;
            double base = eventWeight(e);
            if (base == 0d) continue;
            double shortWeight = days <= 7d ? Math.exp(-days / 3.2d) : 0d;
            double mediumWeight = days <= 30d ? Math.exp(-days / 16d) : 0d;
            double sessionWeight = !newestSession.isEmpty() && newestSession.equals(safe(e.sessionId)) ? 1.0d : 0d;
            // Track score benefits from recent behavior; artist windows are already represented by
            // daily aggregation, except the live session which must react immediately.
            double combined = base * (0.35d * mediumWeight + 0.55d * shortWeight + 1.15d * sessionWeight);
            if (sessionWeight > 0d) addArtist(artist, e.artist, base * 1.15d, "session");
            track.put(e.trackKey, track.getOrDefault(e.trackKey, 0d) + combined);
            if (days <= 7d) recentTracks.add(e.trackKey);
            if (days <= 14d && PersonalizationStore.SKIP.equals(e.type)) recentSkippedTracks.add(e.trackKey);
        }

        List<ArtistAffinity> sortedArtists = new ArrayList<>();
        for (MutableArtist m : artist.values()) {
            if (m.name.isEmpty() || m.score <= -12d) continue;
            sortedArtists.add(new ArtistAffinity(m.name, m.score, m.longSignal, m.mediumSignal, m.shortSignal, m.sessionSignal));
        }
        sortedArtists.sort((a, b) -> Double.compare(b.score, a.score));
        if (sortedArtists.size() > 32) sortedArtists = new ArrayList<>(sortedArtists.subList(0, 32));

        return new Profile(sortedArtists, track, recentTracks, recentSkippedTracks, newestSession,
                snapshot.tracks.size(), snapshot.recentEvents.size());
    }

    private static double eventWeight(PersonalizationStore.RecentEvent e) {
        String type = e == null ? "" : e.type;
        if (PersonalizationStore.PLAY_START.equals(type)) return 0.35d;
        if (PersonalizationStore.COMPLETE.equals(type)) return 3.3d;
        if (PersonalizationStore.REPLAY.equals(type)) return 4.2d;
        if (PersonalizationStore.SKIP.equals(type)) return -4.0d;
        if (PersonalizationStore.FAVORITE_ADD.equals(type)) return 8.5d;
        if (PersonalizationStore.FAVORITE_REMOVE.equals(type)) return -9.0d;
        if (PersonalizationStore.PLAYLIST_ADD.equals(type)) return 4.4d;
        if (PersonalizationStore.PLAYLIST_REMOVE.equals(type)) return -4.8d;
        if (PersonalizationStore.PROGRESS.equals(type)) {
            // Natural listening time, not seek position, is the positive signal.
            return Math.min(0.8d, Math.max(0d, e.listenedDeltaMs) / 60_000d * 0.32d);
        }
        return 0d;
    }

    private static void addLibraryArtists(Map<String, MutableArtist> out, List<Song> songs, double weight) {
        if (songs == null) return;
        for (Song s : songs) {
            if (s == null) continue;
            addArtist(out, s.artist, weight, "library");
        }
    }

    private static void addArtist(Map<String, MutableArtist> out, String raw, double score, String window) {
        for (String name : PersonalizationStore.splitArtists(raw)) {
            String key = PersonalizationStore.normalizeArtist(name);
            if (key.isEmpty()) continue;
            MutableArtist m = out.get(key);
            if (m == null) { m = new MutableArtist(name); out.put(key, m); }
            m.score += score;
            if ("library".equals(window) || "long".equals(window) || "long-180".equals(window)) m.longSignal += score;
            if ("recent-library".equals(window) || "medium-30".equals(window)) m.mediumSignal += score;
            if ("short".equals(window)) m.shortSignal += score;
            if ("session".equals(window)) { m.sessionSignal += score; m.shortSignal += score; }
        }
    }

    public static final class Profile {
        public final List<ArtistAffinity> artists;
        public final Map<String, Double> trackScores;
        public final Set<String> recentTrackKeys;
        public final Set<String> recentSkippedTrackKeys;
        public final String sessionId;
        public final int learnedTracks;
        public final int recentEvents;

        Profile(List<ArtistAffinity> artists, Map<String, Double> trackScores,
                Set<String> recentTrackKeys, Set<String> recentSkippedTrackKeys,
                String sessionId, int learnedTracks, int recentEvents) {
            this.artists = artists; this.trackScores = trackScores; this.recentTrackKeys = recentTrackKeys;
            this.recentSkippedTrackKeys = recentSkippedTrackKeys; this.sessionId = sessionId;
            this.learnedTracks = learnedTracks; this.recentEvents = recentEvents;
        }

        public List<String> topArtistNames(int limit) {
            List<String> out = new ArrayList<>();
            for (ArtistAffinity a : artists) {
                if (a.score <= 0d) continue;
                out.add(a.name);
                if (out.size() >= Math.max(1, limit)) break;
            }
            return out;
        }

        public double artistScore(String rawArtist) {
            if (rawArtist == null) return 0d;
            double best = 0d;
            for (String name : PersonalizationStore.splitArtists(rawArtist)) {
                String key = PersonalizationStore.normalizeArtist(name);
                for (ArtistAffinity a : artists) {
                    if (PersonalizationStore.normalizeArtist(a.name).equals(key)) best = Math.max(best, a.score);
                }
            }
            return best;
        }

        public double trackScore(Song song) { return song == null ? 0d : trackScores.getOrDefault(song.key(), 0d); }
        public boolean wasRecentlySkipped(Song song) { return song != null && recentSkippedTrackKeys.contains(song.key()); }
    }

    public static final class ArtistAffinity {
        public final String name;
        public final double score;
        public final double longSignal;
        public final double mediumSignal;
        public final double shortSignal;
        public final double sessionSignal;
        ArtistAffinity(String name, double score, double longSignal, double mediumSignal, double shortSignal, double sessionSignal) {
            this.name = name; this.score = score; this.longSignal = longSignal; this.mediumSignal = mediumSignal;
            this.shortSignal = shortSignal; this.sessionSignal = sessionSignal;
        }
    }

    private static final class MutableArtist {
        final String name;
        double score, longSignal, mediumSignal, shortSignal, sessionSignal;
        MutableArtist(String name) { this.name = safe(name); }
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
