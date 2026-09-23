package com.xingyu.music.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Optional anonymous ListenBrainz discovery bridge.
 *
 * Flow: artist name -> cached MusicBrainz artist MBID -> ListenBrainz LB Radio related artists.
 * No Lunaxy listening history is uploaded and no ListenBrainz user token is required. Failures are
 * soft: callers simply continue with local/provider discovery.
 */
public final class ListenBrainzDiscoveryClient {
    public enum Mode { EASY, MEDIUM, HARD }

    private static final String LB_ROOT = "https://api.listenbrainz.org";
    private static final String MB_ROOT = "https://musicbrainz.org/ws/2/artist/";
    private static final long MBID_TTL = 120L * 24L * 60L * 60L * 1000L;
    private static final long RADIO_TTL = 7L * 24L * 60L * 60L * 1000L;
    private static final Object MB_RATE_LOCK = new Object();
    private static long nextMusicBrainzAt;

    private final PersonalizationStore store;
    private volatile long backoffUntil;

    public ListenBrainzDiscoveryClient(PersonalizationStore store) { this.store = store; }

    public List<RelatedArtist> relatedArtists(List<String> seeds, Mode mode, int limit) {
        if (seeds == null || seeds.isEmpty() || System.currentTimeMillis() < backoffUntil) return new ArrayList<>();
        int max = Math.max(1, Math.min(limit, 18));
        LinkedHashMap<String, RelatedArtist> merged = new LinkedHashMap<>();
        List<String> usableSeeds = new ArrayList<>();
        for (String seed : seeds) {
            if (seed == null || seed.trim().isEmpty()) continue;
            usableSeeds.add(seed.trim());
            if (usableSeeds.size() >= 3) break;
        }

        // V88: cached relations are the fast path. They are enough to keep repeated opens fully
        // local and also avoid paying MusicBrainz's rate limit on every recommendation refresh.
        for (String seed : usableSeeds) {
            try {
                String mbid = store.cachedArtistMbid(seed, MBID_TTL);
                if (mbid.isEmpty()) continue;
                String raw = store.cachedExternalJson(radioCacheKey(mbid, mode), RADIO_TTL);
                if (!raw.isEmpty()) mergeRelated(merged, decodeRelated(raw, seed), seed);
            } catch (Exception ignored) { }
        }
        if (merged.size() >= max) return top(merged, max);

        // At most one uncached/stale seed is allowed to hit MusicBrainz/ListenBrainz per refresh.
        // More seeds will become warm over future sessions, while the current screen never waits.
        int networkSeeds = 0;
        for (String seed : usableSeeds) {
            if (networkSeeds >= 1) break;
            try {
                String mbid = store.cachedArtistMbid(seed, MBID_TTL);
                String raw = mbid.isEmpty() ? "" : store.cachedExternalJson(radioCacheKey(mbid, mode), RADIO_TTL);
                if (!raw.isEmpty()) {
                    mergeRelated(merged, decodeRelated(raw, seed), seed);
                    continue;
                }
                // From here on this seed may perform network I/O, so consume the one-seed budget
                // before MusicBrainz resolution rather than after it.
                networkSeeds++;
                if (mbid.isEmpty()) mbid = resolveArtistMbid(seed);
                if (mbid.isEmpty()) continue;
                mergeRelated(merged, loadLbRadio(seed, mbid, mode), seed);
            } catch (Exception ignored) { }
        }
        return top(merged, max);
    }

    private static String radioCacheKey(String mbid, Mode mode) {
        String modeName = mode == null ? "medium" : mode.name().toLowerCase(Locale.ROOT);
        return "lb-radio|" + mbid + "|" + modeName;
    }

    private static void mergeRelated(Map<String,RelatedArtist> merged, List<RelatedArtist> source, String seed) {
        if (source == null) return;
        for (RelatedArtist r : source) {
            if (r == null || r.name.isEmpty()) continue;
            if (PersonalizationStore.normalizeArtist(r.name).equals(PersonalizationStore.normalizeArtist(seed))) continue;
            String key = PersonalizationStore.normalizeArtist(r.name);
            RelatedArtist old = merged.get(key);
            if (old == null || r.score > old.score) merged.put(key, r);
        }
    }

    private static List<RelatedArtist> top(Map<String,RelatedArtist> merged, int max) {
        List<RelatedArtist> out = new ArrayList<>(merged.values());
        out.sort((a, b) -> Double.compare(b.score, a.score));
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }

    private String resolveArtistMbid(String artist) throws Exception {
        String cached = store.cachedArtistMbid(artist, MBID_TTL);
        if (!cached.isEmpty()) return cached;

        throttleMusicBrainz();
        String query = "artist:\"" + artist.replace("\"", "") + "\"";
        String url = MB_ROOT + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name()) + "&fmt=json&limit=5";
        Map<String,String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "LunaxyMusic/88.0 (Android personalization client)");
        headers.put("Accept", "application/json");
        Http.Response response = Http.get(url, headers, 7000, 10000);
        if (response.code < 200 || response.code >= 300) return "";
        JSONArray artists = new JSONObject(response.body).optJSONArray("artists");
        if (artists == null) return "";
        String wanted = PersonalizationStore.normalizeArtist(artist);
        String bestId = ""; int bestScore = -1;
        for (int i = 0; i < artists.length(); i++) {
            JSONObject a = artists.optJSONObject(i); if (a == null) continue;
            String name = a.optString("name", ""); String id = a.optString("id", ""); int score = a.optInt("score", 0);
            String normalized = PersonalizationStore.normalizeArtist(name);
            int effective = score + (normalized.equals(wanted) ? 25 : 0);
            if (!id.isEmpty() && effective > bestScore) { bestScore = effective; bestId = id; }
        }
        if (!bestId.isEmpty() && bestScore >= 82) store.cacheArtistMbid(artist, bestId, Math.min(100, bestScore));
        return bestScore >= 82 ? bestId : "";
    }

    private List<RelatedArtist> loadLbRadio(String seedName, String mbid, Mode mode) throws Exception {
        String modeName = mode == null ? "medium" : mode.name().toLowerCase(Locale.ROOT);
        String cacheKey = radioCacheKey(mbid, mode);
        String cached = store.cachedExternalJson(cacheKey, RADIO_TTL);
        if (!cached.isEmpty()) return decodeRelated(cached, seedName);

        String url = LB_ROOT + "/1/lb-radio/artist/" + URLEncoder.encode(mbid, StandardCharsets.UTF_8.name())
                + "?mode=" + modeName
                + "&max_similar_artists=8&max_recordings_per_artist=2&pop_begin=18&pop_end=100";
        Map<String,String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "LunaxyMusic/88.0");
        headers.put("Accept", "application/json");
        Http.Response response = Http.get(url, headers, 7000, 11000);
        if (response.code == 429) {
            backoffUntil = System.currentTimeMillis() + rateResetMs(response, 15L * 60L * 1000L);
            return new ArrayList<>();
        }
        if (response.code < 200 || response.code >= 300) {
            if (response.code >= 500) backoffUntil = System.currentTimeMillis() + 10L * 60L * 1000L;
            return new ArrayList<>();
        }
        store.cacheExternalJson(cacheKey, response.body);
        return decodeRelated(response.body, seedName);
    }

    private static List<RelatedArtist> decodeRelated(String raw, String seedName) {
        List<RelatedArtist> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(raw == null ? "{}" : raw);
            java.util.Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String bucket = keys.next();
                JSONArray recordings = root.optJSONArray(bucket);
                if (recordings == null) continue;
                for (int i = 0; i < recordings.length(); i++) {
                    JSONObject item = recordings.optJSONObject(i); if (item == null) continue;
                    String name = item.optString("similar_artist_name", "").trim();
                    String artistMbid = item.optString("similar_artist_mbid", "").trim();
                    long listens = Math.max(0L, item.optLong("total_listen_count", 0L));
                    if (name.isEmpty()) continue;
                    double score = Math.log10(10d + listens);
                    if (!PersonalizationStore.normalizeArtist(name).equals(PersonalizationStore.normalizeArtist(seedName))) score += 0.6d;
                    out.add(new RelatedArtist(name, artistMbid, score, listens, seedName));
                }
            }
        } catch (Exception ignored) { }
        LinkedHashMap<String,RelatedArtist> dedupe = new LinkedHashMap<>();
        for (RelatedArtist r : out) {
            String key = PersonalizationStore.normalizeArtist(r.name);
            RelatedArtist old = dedupe.get(key);
            if (old == null || r.score > old.score) dedupe.put(key, r);
        }
        List<RelatedArtist> result = new ArrayList<>(dedupe.values());
        result.sort((a,b) -> Double.compare(b.score, a.score));
        return result;
    }

    private static void throttleMusicBrainz() {
        synchronized (MB_RATE_LOCK) {
            long now = System.currentTimeMillis();
            long wait = nextMusicBrainzAt - now;
            if (wait > 0L) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            nextMusicBrainzAt = System.currentTimeMillis() + 1100L;
        }
    }

    private static long rateResetMs(Http.Response response, long fallback) {
        if (response == null || response.headers == null) return fallback;
        for (Map.Entry<String,String> e : response.headers.entrySet()) {
            if (!"X-RateLimit-Reset-In".equalsIgnoreCase(e.getKey())) continue;
            try { return Math.max(30_000L, Long.parseLong(e.getValue().trim()) * 1000L); }
            catch (Exception ignored) { return fallback; }
        }
        return fallback;
    }

    public static final class RelatedArtist {
        public final String name;
        public final String mbid;
        public final double score;
        public final long totalListenCount;
        public final String seedArtist;
        RelatedArtist(String name, String mbid, double score, long totalListenCount, String seedArtist) {
            this.name = name == null ? "" : name.trim(); this.mbid = mbid == null ? "" : mbid.trim();
            this.score = score; this.totalListenCount = totalListenCount; this.seedArtist = seedArtist == null ? "" : seedArtist.trim();
        }
    }
}
