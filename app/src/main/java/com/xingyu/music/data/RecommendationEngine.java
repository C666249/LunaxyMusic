package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lunaxy Personalization Engine V2.2 (V88).
 *
 * Recommendation metadata is strictly separated from playback routing. No method in this class
 * resolves, stores or reuses an audio URL. ListenBrainz/MusicBrainz only broaden candidate metadata;
 * the existing SourceCoordinator/PlaybackService still resolves a fresh playable route on tap.
 */
public final class RecommendationEngine {
    private static final String ENGINE_VERSION = "v88-personalization-2.2";
    private static final long ARTIST_SEARCH_TTL = 18L * 60L * 60L * 1000L;
    private static final long ARTIST_SEARCH_STALE_TTL = 7L * 24L * 60L * 60L * 1000L;
    private static final int ARTIST_CACHE_MAX = 48;
    private static final ExecutorService DISCOVERY_POOL = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "Lunaxy-RecoDiscovery");
        thread.setDaemon(true);
        return thread;
    });
    private final NeteaseApi netease;
    private final QQMusicApi qq;
    private final SharedPreferences prefs;
    private final PersonalizationStore behaviorStore;
    private final TasteProfileEngine taste;
    private final ListenBrainzDiscoveryClient listenBrainz;
    private final Object artistCacheLock = new Object();

    public RecommendationEngine(Context context, NeteaseApi netease, QQMusicApi qq) {
        this(context, netease, qq, new PersonalizationStore(context));
    }

    public RecommendationEngine(Context context, NeteaseApi netease, QQMusicApi qq, PersonalizationStore behaviorStore) {
        this.netease = netease;
        this.qq = qq;
        this.behaviorStore = behaviorStore;
        this.taste = new TasteProfileEngine(behaviorStore);
        this.listenBrainz = new ListenBrainzDiscoveryClient(behaviorStore);
        this.prefs = context.getSharedPreferences("xingyu_recommend_v2", Context.MODE_PRIVATE);
    }

    /** V88 fast-path: read the last materialized recommendation immediately without network. */
    public List<Song> cachedDaily(int limit) {
        return trim(decode(prefs.getString("daily_songs", "[]")), Math.max(1, Math.min(limit, 30)));
    }

    public List<Song> cachedPrivate(int limit) {
        return trim(decode(prefs.getString("private_songs", "[]")), Math.max(1, Math.min(limit, 40)));
    }

    public boolean hasFreshDailySnapshot() {
        return ENGINE_VERSION.equals(prefs.getString("daily_engine", ""))
                && dayKey().equals(prefs.getString("daily_day", ""))
                && !decode(prefs.getString("daily_songs", "[]")).isEmpty();
    }

    /**
     * V88 first-frame fallback. It only touches local behavior/library data and never calls
     * MusicBrainz, ListenBrainz, NetEase or QQ. The richer remote list can be generated later.
     */
    public List<Song> dailyLocalPreview(List<Song> favorites, List<ImportedPlaylist> playlists,
                                        List<Song> history, int limit) {
        int max = Math.max(1, Math.min(limit, 30));
        behaviorStore.awaitPendingWrites(150L);
        TasteProfileEngine.Profile profile = taste.build(favorites, playlists, history);
        LinkedHashMap<String,Candidate> map = new LinkedHashMap<>();
        addLocalCandidates(map, favorites, "rediscovery", 7.6d, profile);
        if (playlists != null) for (ImportedPlaylist p : playlists)
            addLocalCandidates(map, p == null ? null : p.songs, "rediscovery", 4.5d, profile);
        addLocalCandidates(map, history, "local", 3.0d, profile);
        return rankAndDiversify(map, profile, knownKeysWithBehavior(favorites, playlists, history),
                true, max, dayKey().hashCode() ^ 0x1734L).songs;
    }

    public List<Song> privateLocalPreview(List<Song> favorites, List<ImportedPlaylist> playlists,
                                          List<Song> history, int limit) {
        int max = Math.max(1, Math.min(limit, 40));
        behaviorStore.awaitPendingWrites(150L);
        TasteProfileEngine.Profile profile = taste.build(favorites, playlists, history);
        return privateRadioLocalOnly(profile, favorites, playlists, history, max, System.nanoTime()).songs;
    }

    /** Daily: discovery-heavy and stable for the day. When enough candidates exist, >=55% are unheard. */
    public List<Song> daily(List<Song> favorites, List<ImportedPlaylist> playlists,
                            List<Song> history, int limit) {
        int max = Math.max(1, Math.min(limit, 30));
        String day = dayKey();
        if (ENGINE_VERSION.equals(prefs.getString("daily_engine", ""))
                && day.equals(prefs.getString("daily_day", ""))) {
            List<Song> cached = decode(prefs.getString("daily_songs", "[]"));
            if (!cached.isEmpty()) return trim(cached, max);
        }

        behaviorStore.awaitPendingWrites(900L);
        TasteProfileEngine.Profile profile = taste.build(favorites, playlists, history);
        List<String> seeds = profile.topArtistNames(5);
        if (seeds.isEmpty()) seeds = legacyTopArtists(favorites, playlists, history, 4);
        if (seeds.isEmpty()) return privateRadioLocalOnly(profile, favorites, playlists, history, max, day.hashCode()).songs;

        Set<String> known = knownKeysWithBehavior(favorites, playlists, history);
        LinkedHashMap<String,Candidate> candidates = new LinkedHashMap<>();

        // Familiar expansion intentionally searches beyond the local library. A familiar artist can
        // therefore contribute recordings the user has never heard in Lunaxy.
        List<String> familiarSeeds = new ArrayList<>(seeds.subList(0, Math.min(4, seeds.size())));
        Map<String,List<Song>> familiarResults = searchArtistsParallel(familiarSeeds, 20);
        for (int i = 0; i < familiarSeeds.size(); i++) {
            String artist = familiarSeeds.get(i);
            double seedStrength = 10.0d - i * 1.2d;
            for (Song s : familiarResults.getOrDefault(artist, new ArrayList<>())) {
                if (s == null || !artistMatches(s.artist, artist)) continue;
                addCandidate(candidates, s, "familiar", seedStrength + scaledArtistScore(profile, s.artist));
            }
        }

        // Global collaborative discovery. Related artists are mapped back to Lunaxy's normal song
        // metadata; ListenBrainz is never involved in actual playback URL resolution.
        List<ListenBrainzDiscoveryClient.RelatedArtist> related = listenBrainz.relatedArtists(
                seeds.subList(0, Math.min(3, seeds.size())), ListenBrainzDiscoveryClient.Mode.MEDIUM, 12);
        List<String> relatedNames = new ArrayList<>();
        for (int i = 0; i < Math.min(7, related.size()); i++) relatedNames.add(related.get(i).name);
        Map<String,List<Song>> relatedResults = searchArtistsParallel(relatedNames, 14);
        for (int i = 0; i < relatedNames.size(); i++) {
            ListenBrainzDiscoveryClient.RelatedArtist r = related.get(i);
            double discoveryStrength = 8.2d + Math.min(3.4d, r.score * 0.42d) - i * 0.20d;
            for (Song s : relatedResults.getOrDefault(r.name, new ArrayList<>())) {
                if (s == null || !artistMatches(s.artist, r.name)) continue;
                addCandidate(candidates, s, "listenbrainz", discoveryStrength + scaledArtistScore(profile, s.artist) * 0.20d);
            }
        }

        addLocalCandidates(candidates, favorites, "rediscovery", 5.5d, profile);
        if (playlists != null) for (ImportedPlaylist p : playlists)
            addLocalCandidates(candidates, p == null ? null : p.songs, "rediscovery", 3.3d, profile);

        long stableSeed = ((long) day.hashCode() << 32) ^ seeds.toString().hashCode();
        RankedResult ranked = rankAndDiversify(candidates, profile, known, true, max, stableSeed);
        List<Song> out = new ArrayList<>(ranked.songs);
        LinkedHashMap<String,RecommendationReason> finalReasons = new LinkedHashMap<>(ranked.reasons);
        if (out.size() < Math.min(10, max)) {
            RankedResult fallback = privateRadioLocalOnly(profile, favorites, playlists, history, max, stableSeed ^ 0x5DEECE66DL);
            for (Song s : fallback.songs) {
                if (!containsKey(out, s.key())) {
                    out.add(s);
                    RecommendationReason reason = fallback.reasons.get(s.key());
                    if (reason != null) finalReasons.put(s.key(), reason);
                }
                if (out.size() >= max) break;
            }
        }
        out = trim(out, max);
        if (!out.isEmpty()) {
            prefs.edit().putString("daily_engine", ENGINE_VERSION).putString("daily_day", day)
                    .putString("daily_songs", encode(out)).putString("reason_daily", encodeReasons(finalReasons)).apply();
        }
        return out;
    }

    /** Private Radio: current-session heavy; when candidate depth allows, >=25% are unheard tracks. */
    public List<Song> privateRadio(List<Song> favorites, List<ImportedPlaylist> playlists,
                                   List<Song> history, int limit) {
        int max = Math.max(1, Math.min(limit, 40));
        behaviorStore.awaitPendingWrites(900L);
        TasteProfileEngine.Profile profile = taste.build(favorites, playlists, history);
        List<String> seeds = profile.topArtistNames(4);
        if (seeds.isEmpty()) seeds = legacyTopArtists(favorites, playlists, history, 3);
        LinkedHashMap<String,Candidate> candidates = new LinkedHashMap<>();

        addLocalCandidates(candidates, favorites, "local", 8.8d, profile);
        if (playlists != null) for (ImportedPlaylist p : playlists)
            addLocalCandidates(candidates, p == null ? null : p.songs, "local", 4.2d, profile);
        addLocalCandidates(candidates, history, "local", 3.0d, profile);

        List<String> privateSeeds = new ArrayList<>(seeds.subList(0, Math.min(3, seeds.size())));
        Map<String,List<Song>> privateFamiliar = searchArtistsParallel(privateSeeds, 14);
        for (int i = 0; i < privateSeeds.size(); i++) {
            String artist = privateSeeds.get(i);
            for (Song s : privateFamiliar.getOrDefault(artist, new ArrayList<>())) {
                if (s != null && artistMatches(s.artist, artist))
                    addCandidate(candidates, s, "familiar", 7.3d - i * 0.7d + scaledArtistScore(profile, s.artist));
            }
        }

        List<ListenBrainzDiscoveryClient.RelatedArtist> related = listenBrainz.relatedArtists(
                seeds.subList(0, Math.min(2, seeds.size())), ListenBrainzDiscoveryClient.Mode.EASY, 8);
        List<String> privateRelatedNames = new ArrayList<>();
        for (int i = 0; i < Math.min(5, related.size()); i++) privateRelatedNames.add(related.get(i).name);
        Map<String,List<Song>> privateRelated = searchArtistsParallel(privateRelatedNames, 12);
        for (int i = 0; i < privateRelatedNames.size(); i++) {
            ListenBrainzDiscoveryClient.RelatedArtist r = related.get(i);
            for (Song s : privateRelated.getOrDefault(r.name, new ArrayList<>())) {
                if (s != null && artistMatches(s.artist, r.name))
                    addCandidate(candidates, s, "listenbrainz", 5.9d + Math.min(2.6d, r.score * 0.35d) - i * 0.2d);
            }
        }

        Set<String> known = knownKeysWithBehavior(favorites, playlists, history);
        RankedResult ranked = rankAndDiversify(candidates, profile, known, false, max, System.nanoTime());
        prefs.edit().putString("reason_private", encodeReasons(ranked.reasons)).putString("private_songs", encode(ranked.songs))
                .putLong("private_generated_at", System.currentTimeMillis()).apply();
        return ranked.songs;
    }

    public List<Song> weatherRadio(List<Song> favorites, List<ImportedPlaylist> playlists,
                                   List<Song> history, WeatherMoodProvider.WeatherSnapshot weather,
                                   int limit) {
        int max = Math.max(1, Math.min(limit, 30));
        if (weather == null) return privateRadio(favorites, playlists, history, max);
        String cacheKey = ENGINE_VERSION + "|" + dayKey() + "|" + weather.cacheKey();
        if (cacheKey.equals(prefs.getString("weather_key", ""))) {
            List<Song> cached = decode(prefs.getString("weather_songs", "[]"));
            if (!cached.isEmpty()) return trim(cached, max);
        }

        behaviorStore.awaitPendingWrites(900L);
        TasteProfileEngine.Profile profile = taste.build(favorites, playlists, history);
        List<String> seeds = profile.topArtistNames(4);
        if (seeds.isEmpty()) seeds = legacyTopArtists(favorites, playlists, history, 3);
        LinkedHashMap<String,Candidate> map = new LinkedHashMap<>();
        String moodWord = weather.discoveryWord();
        for (int i = 0; i < Math.min(3, seeds.size()); i++) {
            String artist = seeds.get(i);
            String query = (artist + " " + moodWord).trim();
            for (Song s : search(query, 16)) {
                if (s == null || !artistMatches(s.artist, artist)) continue;
                addCandidate(map, s, "weather", 7.2d - i * 0.6d + scaledArtistScore(profile, s.artist));
            }
        }
        addLocalCandidates(map, favorites, "weather-local", 5.2d, profile);
        addLocalCandidates(map, history, "weather-local", 3.0d, profile);

        RankedResult ranked = rankAndDiversify(map, profile, knownKeysWithBehavior(null, null, history), false, max, cacheKey.hashCode());
        List<Song> out = ranked.songs;
        if (!out.isEmpty()) prefs.edit().putString("weather_key", cacheKey).putString("weather_songs", encode(out))
                .putString("reason_weather", encodeReasons(ranked.reasons)).apply();
        return out;
    }

    public boolean hasTaste(List<Song> favorites, List<ImportedPlaylist> playlists, List<Song> history) {
        if (favorites != null && !favorites.isEmpty()) return true;
        if (history != null && !history.isEmpty()) return true;
        if (playlists != null) for (ImportedPlaylist p : playlists) if (p != null && !p.songs.isEmpty()) return true;
        try { return !taste.build(favorites, playlists, history).artists.isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    /** Read-only snapshot for the front-end Personalization Matrix. No network calls are made here. */
    public PersonalizationInsights insights(List<Song> favorites, List<ImportedPlaylist> playlists, List<Song> history) {
        behaviorStore.awaitPendingWrites(700L);
        TasteProfileEngine.Profile profile = taste.build(favorites, playlists, history);
        PersonalizationStore.Snapshot raw = behaviorStore.snapshot();
        ArrayList<ArtistInsight> artists = new ArrayList<>();
        double totalMax = maxPositive(profile.artists, 0), longMax = maxPositive(profile.artists, 1);
        double mediumMax = maxPositive(profile.artists, 2), shortMax = maxPositive(profile.artists, 3), sessionMax = maxPositive(profile.artists, 4);
        for (int i = 0; i < Math.min(10, profile.artists.size()); i++) {
            TasteProfileEngine.ArtistAffinity a = profile.artists.get(i);
            artists.add(new ArtistInsight(a.name, signedIndex(a.score, totalMax), signedIndex(a.longSignal, longMax),
                    signedIndex(a.mediumSignal, mediumMax), signedIndex(a.shortSignal, shortMax), signedIndex(a.sessionSignal, sessionMax)));
        }
        int completes = 0, skips = 0, replays = 0, favoritesAdded = 0;
        for (PersonalizationStore.RecentEvent e : raw.recentEvents) {
            if (profile.sessionId == null || profile.sessionId.isEmpty() || !profile.sessionId.equals(e.sessionId)) continue;
            if (PersonalizationStore.COMPLETE.equals(e.type)) completes++;
            else if (PersonalizationStore.SKIP.equals(e.type)) skips++;
            else if (PersonalizationStore.REPLAY.equals(e.type)) replays++;
            else if (PersonalizationStore.FAVORITE_ADD.equals(e.type)) favoritesAdded++;
        }
        int maturity = Math.max(0, Math.min(100, (int) Math.round(profile.learnedTracks * 1.15d + Math.min(60, profile.recentEvents) * 0.45d)));
        return new PersonalizationInsights(artists, profile.learnedTracks, profile.recentEvents, profile.sessionId,
                maturity, completes, skips, replays, favoritesAdded,
                readSummary("daily", decode(prefs.getString("daily_songs", "[]")), favorites, playlists, history),
                readSummary("private", decode(prefs.getString("private_songs", "[]")), favorites, playlists, history));
    }

    public RecommendationReason reasonFor(String kind, Song song) {
        if (song == null) return new RecommendationReason("", "根据你的口味推荐", false, 0d);
        Map<String,RecommendationReason> map = decodeReasons(prefs.getString("reason_" + safeKind(kind), "{}"));
        RecommendationReason r = map.get(song.key());
        return r == null ? new RecommendationReason("", "根据你的口味推荐", false, 0d) : r;
    }

    public RecommendationSummary summaryFor(String kind, List<Song> songs, List<Song> favorites,
                                              List<ImportedPlaylist> playlists, List<Song> history) {
        return readSummary(kind, songs, favorites, playlists, history);
    }

    public String diagnostics() {
        try {
            JSONObject o = behaviorStore.diagnostics();
            o.put("engine", ENGINE_VERSION);
            return o.toString();
        } catch (Exception e) { return "{\"engine\":\"" + ENGINE_VERSION + "\"}"; }
    }

    private RecommendationSummary readSummary(String kind, List<Song> songs, List<Song> favorites,
                                                List<ImportedPlaylist> playlists, List<Song> history) {
        Map<String,RecommendationReason> reasons = decodeReasons(prefs.getString("reason_" + safeKind(kind), "{}"));
        Set<String> known = null;
        int total = 0, unheard = 0, lb = 0, familiar = 0, rediscovery = 0, local = 0;
        List<Song> list = songs == null ? new ArrayList<>() : songs;
        for (Song song : list) {
            if (song == null) continue;
            total++;
            RecommendationReason r = reasons.get(song.key());
            // Materialized recommendation lists persist the exact unheard decision made during ranking, so
            // front-end summaries are cheap and never hit SQLite on the main thread. Only legacy or
            // incomplete reason maps fall back to the long-lived heard-track ledger.
            boolean isUnheard;
            if (r != null) isUnheard = r.unheard;
            else {
                if (known == null) known = knownKeysWithBehavior(favorites, playlists, history);
                isUnheard = !known.contains(song.key());
            }
            if (isUnheard) unheard++;
            String origin = r == null ? "" : r.origin;
            if ("listenbrainz".equals(origin)) lb++;
            else if ("familiar".equals(origin)) familiar++;
            else if ("rediscovery".equals(origin)) rediscovery++;
            else if (origin.startsWith("local") || origin.endsWith("local")) local++;
        }
        return new RecommendationSummary(total, unheard, lb, familiar, rediscovery, local);
    }

    private RankedResult rankAndDiversify(Map<String,Candidate> source, TasteProfileEngine.Profile profile,
                                          Set<String> known, boolean dailyMode, int max, long seed) {
        List<Candidate> all = new ArrayList<>(source.values());
        Random random = new Random(seed);
        for (Candidate c : all) {
            c.unheard = known == null || !known.contains(c.song.key());
            c.score += profile.trackScore(c.song) * (dailyMode ? 0.12d : 0.25d);
            c.score += scaledArtistScore(profile, c.song.artist) * (dailyMode ? 0.35d : 0.60d);
            if (profile.wasRecentlySkipped(c.song)) c.score -= 12d;
            if (profile.recentTrackKeys.contains(c.song.key())) c.score -= dailyMode ? 8.0d : 3.8d;
            if (!c.unheard) c.score += dailyMode ? -3.0d : 0.8d;
            if ("listenbrainz".equals(c.origin)) c.score += dailyMode ? 2.2d : 0.8d;
            if ("rediscovery".equals(c.origin) && dailyMode) c.score -= 0.8d;
            c.score += random.nextDouble() * (dailyMode ? 0.9d : 1.8d);
        }
        all.sort((a,b) -> Double.compare(b.score, a.score));

        ArrayList<Candidate> selected = new ArrayList<>();
        Map<String,Integer> artistCount = new HashMap<>();
        int unseenTarget = Math.min(max, Math.max(dailyMode ? 4 : 3,
                (int) Math.ceil(max * (dailyMode ? 0.55d : 0.25d))));

        // Pass 1: reserve a meaningful part of the list for genuinely unheard recordings. If the
        // catalog has fewer than the target, pass 2 simply fills from the strongest known tracks.
        for (Candidate c : all) {
            if (!c.unheard || containsCandidate(selected, c.song.key())) continue;
            String artistKey = primaryArtistKey(c.song.artist);
            int count = artistCount.getOrDefault(artistKey, 0);
            if (count >= (dailyMode ? 2 : 3)) continue;
            selected.add(c); artistCount.put(artistKey, count + 1);
            if (selected.size() >= unseenTarget) break;
        }

        for (Candidate c : all) {
            if (containsCandidate(selected, c.song.key())) continue;
            String artistKey = primaryArtistKey(c.song.artist);
            int count = artistCount.getOrDefault(artistKey, 0);
            if (count >= (dailyMode ? 3 : 4)) continue;
            selected.add(c); artistCount.put(artistKey, count + 1);
            if (selected.size() >= max) break;
        }

        ArrayList<Song> songs = new ArrayList<>();
        LinkedHashMap<String,RecommendationReason> reasons = new LinkedHashMap<>();
        for (Candidate c : selected) {
            songs.add(c.song);
            reasons.put(c.song.key(), reasonForCandidate(c, profile));
            if (songs.size() >= max) break;
        }
        return new RankedResult(songs, reasons);
    }

    private RecommendationReason reasonForCandidate(Candidate c, TasteProfileEngine.Profile profile) {
        String why;
        if ("listenbrainz".equals(c.origin)) why = c.unheard ? "相似听众发现 · 你还没听过" : "相似听众也常听";
        else if ("familiar".equals(c.origin)) why = c.unheard ? "喜欢歌手的新发现 · 你还没听过" : "熟悉歌手延伸";
        else if ("rediscovery".equals(c.origin)) why = "旧收藏 / 歌单重新发现";
        else if ("weather".equals(c.origin)) why = "天气与当前口味匹配";
        else if (c.origin != null && c.origin.contains("local")) why = "当前口味锚点";
        else why = "根据你的口味推荐";
        double artist = profile.artistScore(c.song.artist);
        for (TasteProfileEngine.ArtistAffinity a : profile.artists) {
            if (!artistMatches(c.song.artist, a.name)) continue;
            if (a.sessionSignal > 1.2d) why = "本次会话偏好 · " + why;
            else if (a.shortSignal > 2.0d && !why.startsWith("最近")) why = "最近兴趣 · " + why;
            break;
        }
        return new RecommendationReason(c.origin, why, c.unheard, c.score + Math.min(4d, Math.log1p(Math.max(0d, artist))));
    }

    private RankedResult privateRadioLocalOnly(TasteProfileEngine.Profile profile,
                                               List<Song> favorites, List<ImportedPlaylist> playlists,
                                               List<Song> history, int max, long seed) {
        LinkedHashMap<String,Candidate> map = new LinkedHashMap<>();
        addLocalCandidates(map, favorites, "local", 8.5d, profile);
        if (playlists != null) for (ImportedPlaylist p : playlists)
            addLocalCandidates(map, p == null ? null : p.songs, "local", 4.0d, profile);
        addLocalCandidates(map, history, "local", 2.6d, profile);
        return rankAndDiversify(map, profile, knownKeysWithBehavior(favorites, playlists, history), false, max, seed);
    }

    private void addLocalCandidates(Map<String,Candidate> out, List<Song> songs, String origin,
                                    double base, TasteProfileEngine.Profile profile) {
        if (songs == null) return;
        for (int i = 0; i < songs.size(); i++) {
            Song s = songs.get(i); if (s == null) continue;
            double positional = Math.max(0d, 1.6d - i * 0.035d);
            addCandidate(out, s, origin, base + positional + profile.trackScore(s) * 0.18d);
        }
    }

    private List<Song> searchArtist(String artist, int limit) {
        String normalized = normalize(artist);
        if (normalized.isEmpty()) return new ArrayList<>();
        String cacheId = Integer.toHexString(normalized.hashCode());
        String qKey = "artist_q_" + cacheId, tKey = "artist_t_" + cacheId, sKey = "artist_s_" + cacheId;
        long age = System.currentTimeMillis() - prefs.getLong(tKey, 0L);
        String storedQuery = prefs.getString(qKey, "");
        List<Song> cached = storedQuery.equals(normalized) ? decode(prefs.getString(sKey, "[]")) : new ArrayList<>();
        if (!cached.isEmpty() && age >= 0L && age <= ARTIST_SEARCH_TTL) return trim(filterArtist(cached, artist), limit);

        List<Song> network = search(artist, limit);
        List<Song> filtered = filterArtist(network, artist);
        if (!filtered.isEmpty()) {
            rememberArtistCache(cacheId, normalized, encode(trim(filtered, Math.max(limit, 20))));
            return trim(filtered, limit);
        }
        // Stale-while-error: a transient catalog failure must not erase useful discovery metadata.
        if (!cached.isEmpty() && age >= 0L && age <= ARTIST_SEARCH_STALE_TTL) return trim(filterArtist(cached, artist), limit);
        return filtered;
    }

    private void rememberArtistCache(String cacheId, String normalizedQuery, String encodedSongs) {
        synchronized (artistCacheLock) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            String raw = prefs.getString("artist_cache_index", "");
            if (raw != null && !raw.trim().isEmpty()) {
                for (String id : raw.split(",")) if (id != null && !id.trim().isEmpty()) ids.add(id.trim());
            }
            ids.remove(cacheId);
            ids.add(cacheId);
            SharedPreferences.Editor edit = prefs.edit();
            while (ids.size() > ARTIST_CACHE_MAX) {
                String oldest = ids.iterator().next();
                ids.remove(oldest);
                edit.remove("artist_q_" + oldest).remove("artist_t_" + oldest).remove("artist_s_" + oldest);
            }
            edit.putString("artist_q_" + cacheId, normalizedQuery)
                    .putLong("artist_t_" + cacheId, System.currentTimeMillis())
                    .putString("artist_s_" + cacheId, encodedSongs)
                    .putString("artist_cache_index", String.join(",", ids)).apply();
        }
    }

    private static List<Song> filterArtist(List<Song> source, String artist) {
        List<Song> filtered = new ArrayList<>();
        if (source == null) return filtered;
        for (Song s : source) if (s != null && artistMatches(s.artist, artist)) filtered.add(s);
        return filtered;
    }

    /** Parallelize independent artist catalog expansion with a small fixed pool. */
    private Map<String,List<Song>> searchArtistsParallel(List<String> artists, int limit) {
        LinkedHashMap<String,List<Song>> out = new LinkedHashMap<>();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (artists != null) for (String artist : artists) if (artist != null && !artist.trim().isEmpty()) unique.add(artist.trim());
        List<String> ordered = new ArrayList<>(unique);
        List<Future<ArtistSearchBatch>> jobs = new ArrayList<>();
        for (String artist : ordered) jobs.add(DISCOVERY_POOL.submit(() -> new ArtistSearchBatch(artist, searchArtist(artist, limit))));
        for (Future<ArtistSearchBatch> job : jobs) {
            try {
                ArtistSearchBatch batch = job.get(6500L, TimeUnit.MILLISECONDS);
                out.put(batch.artist, batch.songs);
            } catch (TimeoutException e) { job.cancel(true); }
            catch (Exception e) { job.cancel(true); }
        }
        for (String artist : ordered) if (!out.containsKey(artist)) out.put(artist, new ArrayList<>());
        return out;
    }

    private List<Song> search(String query, int limit) {
        List<Song> wy = new ArrayList<>(), tx = new ArrayList<>();
        try { wy = netease.search(query, limit); } catch (Exception ignored) { }
        if (Thread.currentThread().isInterrupted()) return new ArrayList<>();
        try { tx = qq.search(query, limit); } catch (Exception ignored) { }
        return SearchRanker.mergeAndRank(query, wy, tx, Math.max(limit, 18));
    }

    private static void addCandidate(Map<String,Candidate> out, Song song, String origin, double score) {
        if (song == null) return;
        Candidate old = out.get(song.key());
        if (old == null) out.put(song.key(), new Candidate(song, origin, score));
        else {
            old.score = Math.max(old.score, score) + 0.35d;
            if ("listenbrainz".equals(origin)) old.origin = origin;
        }
    }

    private Set<String> knownKeysWithBehavior(List<Song> favorites, List<ImportedPlaylist> playlists, List<Song> history) {
        Set<String> out = knownKeys(favorites, playlists, history);
        // Discovery must not call an old track "new" merely because it fell outside the
        // 1200-row analytic snapshot. track_stats is the long-lived heard-track ledger.
        try { out.addAll(behaviorStore.knownTrackKeys()); } catch (Exception ignored) { }
        return out;
    }

    private static double scaledArtistScore(TasteProfileEngine.Profile profile, String artist) {
        double raw = Math.max(-20d, Math.min(80d, profile.artistScore(artist)));
        return raw >= 0 ? Math.log1p(raw) : -Math.log1p(-raw);
    }

    private static Set<String> knownKeys(List<Song> favorites, List<ImportedPlaylist> playlists, List<Song> history) {
        Set<String> out = new HashSet<>();
        addKeys(out, favorites); addKeys(out, history);
        if (playlists != null) for (ImportedPlaylist p : playlists) addKeys(out, p == null ? null : p.songs);
        return out;
    }

    private static List<String> legacyTopArtists(List<Song> favorites, List<ImportedPlaylist> playlists,
                                                  List<Song> history, int limit) {
        Map<String,Double> score = new LinkedHashMap<>();
        addArtistScore(score, favorites, 7d);
        if (playlists != null) for (ImportedPlaylist p : playlists) addArtistScore(score, p == null ? null : p.songs, 3d);
        addArtistScore(score, history, 1d);
        List<Map.Entry<String,Double>> entries = new ArrayList<>(score.entrySet());
        entries.sort((a,b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> out = new ArrayList<>();
        for (Map.Entry<String,Double> e : entries) {
            if (e.getKey().trim().isEmpty()) continue;
            out.add(e.getKey()); if (out.size() >= limit) break;
        }
        return out;
    }

    private static void addArtistScore(Map<String,Double> score, List<Song> songs, double weight) {
        if (songs == null) return;
        for (Song s : songs) if (s != null) for (String name : PersonalizationStore.splitArtists(s.artist))
            score.put(name, score.getOrDefault(name, 0d) + weight);
    }

    private static boolean artistMatches(String songArtist, String seed) {
        String a = normalize(songArtist), b = normalize(seed);
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.contains(b) || b.contains(a);
    }

    private static String primaryArtistKey(String raw) {
        List<String> names = PersonalizationStore.splitArtists(raw);
        return names.isEmpty() ? normalize(raw) : PersonalizationStore.normalizeArtist(names.get(0));
    }

    private static String normalize(String value) {
        return (value == null ? "" : value.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()·]+", "");
    }

    private static void addKeys(Set<String> out, List<Song> songs) {
        if (songs == null) return;
        for (Song s : songs) if (s != null) out.add(s.key());
    }

    private static boolean containsKey(List<Song> list, String key) {
        if (list == null) return false;
        for (Song s : list) if (s != null && s.key().equals(key)) return true;
        return false;
    }

    private static boolean containsCandidate(List<Candidate> list, String key) {
        for (Candidate c : list) if (c != null && c.song != null && c.song.key().equals(key)) return true;
        return false;
    }

    private static String dayKey() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String safeKind(String kind) {
        if ("private".equals(kind) || "weather".equals(kind) || "daily".equals(kind)) return kind;
        return "daily";
    }

    private static String encode(List<Song> songs) {
        JSONArray a = new JSONArray();
        if (songs != null) for (Song s : songs) if (s != null) a.put(s.toJson());
        return a.toString();
    }

    private static List<Song> decode(String raw) {
        List<Song> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o != null) out.add(Song.fromJson(o));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static String encodeReasons(Map<String,RecommendationReason> reasons) {
        JSONObject root = new JSONObject();
        try {
            if (reasons != null) for (Map.Entry<String,RecommendationReason> e : reasons.entrySet()) {
                RecommendationReason r = e.getValue();
                JSONObject o = new JSONObject();
                o.put("origin", r.origin); o.put("why", r.why); o.put("unheard", r.unheard); o.put("score", r.score);
                root.put(e.getKey(), o);
            }
        } catch (Exception ignored) { }
        return root.toString();
    }

    private static Map<String,RecommendationReason> decodeReasons(String raw) {
        LinkedHashMap<String,RecommendationReason> out = new LinkedHashMap<>();
        try {
            JSONObject root = new JSONObject(raw == null ? "{}" : raw);
            java.util.Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String key = it.next(); JSONObject o = root.optJSONObject(key); if (o == null) continue;
                out.put(key, new RecommendationReason(o.optString("origin", ""), o.optString("why", "根据你的口味推荐"),
                        o.optBoolean("unheard", false), o.optDouble("score", 0d)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static List<Song> trim(List<Song> source, int max) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        return source.size() <= max ? new ArrayList<>(source) : new ArrayList<>(source.subList(0, max));
    }

    private static double maxPositive(List<TasteProfileEngine.ArtistAffinity> list, int metric) {
        double max = 0.001d;
        for (TasteProfileEngine.ArtistAffinity a : list) {
            double v = metric == 0 ? a.score : metric == 1 ? a.longSignal : metric == 2 ? a.mediumSignal : metric == 3 ? a.shortSignal : a.sessionSignal;
            max = Math.max(max, Math.abs(v));
        }
        return max;
    }

    private static int signedIndex(double value, double maxAbs) {
        return (int) Math.round(Math.max(-100d, Math.min(100d, value / Math.max(.001d, maxAbs) * 100d)));
    }

    private static final class ArtistSearchBatch {
        final String artist;
        final List<Song> songs;
        ArtistSearchBatch(String artist, List<Song> songs) {
            this.artist = artist == null ? "" : artist;
            this.songs = songs == null ? new ArrayList<>() : songs;
        }
    }

    private static final class Candidate {
        final Song song;
        String origin;
        double score;
        boolean unheard;
        Candidate(Song song, String origin, double score) { this.song = song; this.origin = origin; this.score = score; }
    }

    private static final class RankedResult {
        final List<Song> songs;
        final Map<String,RecommendationReason> reasons;
        RankedResult(List<Song> songs, Map<String,RecommendationReason> reasons) { this.songs = songs; this.reasons = reasons; }
    }

    public static final class RecommendationReason {
        public final String origin;
        public final String why;
        public final boolean unheard;
        public final double score;
        RecommendationReason(String origin, String why, boolean unheard, double score) {
            this.origin = origin == null ? "" : origin; this.why = why == null ? "" : why; this.unheard = unheard; this.score = score;
        }
    }

    public static final class RecommendationSummary {
        public final int total, unheard, listenBrainz, familiar, rediscovery, local;
        RecommendationSummary(int total, int unheard, int listenBrainz, int familiar, int rediscovery, int local) {
            this.total = total; this.unheard = unheard; this.listenBrainz = listenBrainz; this.familiar = familiar;
            this.rediscovery = rediscovery; this.local = local;
        }
        public int unheardPercent() { return total <= 0 ? 0 : (int) Math.round(unheard * 100d / total); }
    }

    public static final class ArtistInsight {
        public final String name;
        public final int overall, long180, medium30, short7, session;
        ArtistInsight(String name, int overall, int long180, int medium30, int short7, int session) {
            this.name = name; this.overall = overall; this.long180 = long180; this.medium30 = medium30; this.short7 = short7; this.session = session;
        }
    }

    public static final class PersonalizationInsights {
        public final List<ArtistInsight> artists;
        public final int learnedTracks, recentEvents, maturity, completes, skips, replays, favoritesAdded;
        public final String sessionId;
        public final RecommendationSummary daily, privateRadio;
        PersonalizationInsights(List<ArtistInsight> artists, int learnedTracks, int recentEvents, String sessionId,
                                int maturity, int completes, int skips, int replays, int favoritesAdded,
                                RecommendationSummary daily, RecommendationSummary privateRadio) {
            this.artists = artists; this.learnedTracks = learnedTracks; this.recentEvents = recentEvents; this.sessionId = sessionId;
            this.maturity = maturity; this.completes = completes; this.skips = skips; this.replays = replays; this.favoritesAdded = favoritesAdded;
            this.daily = daily; this.privateRadio = privateRadio;
        }
    }
}
