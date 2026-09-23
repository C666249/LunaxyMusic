package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.Song;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Side-car identity cache. LibraryStore remains byte-compatible with V47.
 * Cross-catalog IDs are overlaid at playback/search time and can be discarded
 * without risking favorites, playlists or history.
 */
public final class TrackVariantStore {
    private static final long MAX_AGE = 120L * 24L * 60L * 60L * 1000L;
    private static final long ENRICH_RETRY = 8L * 60L * 60L * 1000L;
    private final SharedPreferences prefs;

    public TrackVariantStore(Context context) {
        prefs = context.getSharedPreferences("lunaxy_track_variants_v1", Context.MODE_PRIVATE);
    }

    public synchronized Song enrich(Song song) {
        if (song == null) return null;
        String raw = prefs.getString(key(song), "");
        if (raw == null || raw.isEmpty()) return song;
        try {
            JSONObject wrapper = new JSONObject(raw);
            long at = wrapper.optLong("at", 0L);
            if (at <= 0L || System.currentTimeMillis() - at > MAX_AGE) return song;
            Song saved = Song.fromJson(wrapper.optJSONObject("song"));
            if (ExactTrackMatcher.confidence(song, saved) < 0.98d) return song;
            return song.withVariant(saved);
        } catch (Exception ignored) { return song; }
    }

    public synchronized void remember(Song song) {
        if (song == null || song.variants().isEmpty()) return;
        Song merged = enrich(song);
        if (merged == null) merged = song;
        try {
            JSONObject wrapper = new JSONObject(); wrapper.put("at", System.currentTimeMillis()); wrapper.put("song", merged.toJson());
            prefs.edit().putString(key(song), wrapper.toString()).apply();
        } catch (Exception ignored) { }
    }

    public boolean shouldEnrich(Song song) {
        if (song == null) return false;
        Song enriched = enrich(song);
        boolean lacksKw = enriched.variant("kw") == null;
        boolean lacksKg = enriched.variant("kg") == null;
        boolean lacksWyForTx = "tx".equals(enriched.source) && enriched.variant("wy") == null;
        if (!lacksKw && !lacksKg && !lacksWyForTx) return false;
        long last = prefs.getLong(key(song) + ".attempt", 0L);
        return last <= 0L || System.currentTimeMillis() - last >= ENRICH_RETRY;
    }

    public void markEnrichAttempt(Song song) {
        if (song != null) prefs.edit().putLong(key(song) + ".attempt", System.currentTimeMillis()).apply();
    }

    private static String key(Song song) { return "v." + sha256(ExactTrackMatcher.identityKey(song)); }
    private static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff)); return b.toString();
        } catch (Exception e) { return Integer.toHexString((value == null ? "" : value).hashCode()); }
    }
}
