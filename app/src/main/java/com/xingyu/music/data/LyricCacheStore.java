package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Permanent local cache for static line-synced lyrics.
 *
 * V85 keeps V20 data fully readable, but no longer relies on Song.key() alone. Provider-native
 * catalog IDs are the strongest cache identity and a slightly tolerant metadata key bridges the
 * same recording when the preferred catalog changes. No playback URL is ever stored here.
 */
public final class LyricCacheStore {
    private static final String PREFS = "xingyu_lyric_cache_v20";
    private final SharedPreferences prefs;

    public LyricCacheStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String get(Song song) {
        if (song == null) return "";

        for (String key : v85Keys(song)) {
            String value = prefs.getString(key, "");
            if (value != null && !value.trim().isEmpty()) return value;
        }

        // Backward compatibility: V20-V84 wrote exactly one Song.key()-based entry.
        String legacy = prefs.getString(legacyKey(song), "");
        if (legacy != null && !legacy.trim().isEmpty()) {
            put(song, legacy); // opportunistic migration; old data remains untouched.
            return legacy;
        }
        return "";
    }

    public void put(Song song, String lrc) {
        if (song == null || lrc == null || lrc.trim().isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : v85Keys(song)) editor.putString(key, lrc);
        editor.apply();
    }

    private static List<String> v85Keys(Song song) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (SourceVariant variant : song.variants()) {
            if (variant == null || variant.source == null || variant.sourceId == null) continue;
            String source = variant.source.trim();
            String id = variant.sourceId.trim();
            if (!source.isEmpty() && !id.isEmpty()) out.add("v85:id:" + source + ":" + id);
        }

        // Catalogs can disagree by a few seconds about duration. A 6-second bucket is tolerant
        // enough to bridge those metadata differences without using transient network URLs.
        long durationBucket = song.durationMs <= 0L ? 0L : Math.round(song.durationMs / 6000.0d);
        out.add("v85:meta:" + normalize(song.title) + "|" + normalize(song.artist) + "|" + durationBucket);
        return new ArrayList<>(out);
    }

    private static String legacyKey(Song song) { return "song:" + song.key(); }

    private static String normalize(String value) {
        return (value == null ? "" : value.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }
}
