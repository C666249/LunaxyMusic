package com.xingyu.music.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Provider-specific identity for one canonical track.
 *
 * V8 keeps the provider's original metadata object instead of reconstructing a
 * tiny object from title/id. LX custom-source scripts are allowed to read any
 * field from info.musicInfo, and several real-world sources do exactly that.
 */
public final class SourceVariant {
    public final String source;
    public final String sourceId;
    public final boolean quality128;
    public final boolean quality320;
    private final String rawMusicInfoJson;

    public SourceVariant(String source, String sourceId, boolean quality128, boolean quality320) {
        this(source, sourceId, quality128, quality320, "");
    }

    public SourceVariant(String source, String sourceId, boolean quality128, boolean quality320, JSONObject rawMusicInfo) {
        this(source, sourceId, quality128, quality320,
                rawMusicInfo == null ? "" : rawMusicInfo.toString());
    }

    public SourceVariant(String source, String sourceId, boolean quality128, boolean quality320, String rawMusicInfoJson) {
        this.source = source == null ? "" : source.trim();
        this.sourceId = sourceId == null ? "" : sourceId.trim();
        this.quality128 = quality128;
        this.quality320 = quality320;
        this.rawMusicInfoJson = rawMusicInfoJson == null ? "" : rawMusicInfoJson.trim();
    }

    public String key() { return source + ":" + sourceId; }

    public boolean supports(String quality) {
        if ("320k".equals(quality)) return quality320;
        return quality128;
    }

    public String rawMusicInfoJson() { return rawMusicInfoJson; }

    /**
     * Returns the platform-native object, enriched only with missing aliases.
     * Existing provider fields always win over our compatibility aliases.
     */
    public JSONObject musicInfo(Song song) {
        JSONObject out;
        try { out = rawMusicInfoJson.isEmpty() ? new JSONObject() : new JSONObject(rawMusicInfoJson); }
        catch (Exception ignored) { out = new JSONObject(); }

        putIfMissing(out, "id", sourceId);
        putIfMissing(out, "songmid", sourceId);
        putIfMissing(out, "songId", sourceId);
        putIfMissing(out, "mid", sourceId);
        if ("kg".equals(source)) putIfMissing(out, "hash", sourceId);
        if ("mg".equals(source)) putIfMissing(out, "copyrightId", sourceId);

        if (song != null) {
            putIfMissing(out, "name", song.title);
            putIfMissing(out, "title", song.title);
            putIfMissing(out, "songname", song.title);
            putIfMissing(out, "artist", song.artist);
            putIfMissing(out, "singer", song.artist);
            putIfMissing(out, "albumName", song.album);
            if (!out.has("album")) {
                try { out.put("album", song.album); } catch (JSONException ignored) { }
            }
            if (!out.has("interval") && song.durationMs > 0) {
                long total = Math.max(0, song.durationMs / 1000L);
                try { out.put("interval", String.format(java.util.Locale.ROOT, "%02d:%02d", total / 60L, total % 60L)); }
                catch (JSONException ignored) { }
            }
            if (!out.has("duration") && song.durationMs > 0) {
                try { out.put("duration", song.durationMs); } catch (JSONException ignored) { }
            }
            if (!out.has("durationMs") && song.durationMs > 0) {
                try { out.put("durationMs", song.durationMs); } catch (JSONException ignored) { }
            }
        }
        return out;
    }

    public SourceVariant merge(SourceVariant other) {
        if (other == null || !key().equals(other.key())) return this;
        String raw = rawMusicInfoJson.length() >= other.rawMusicInfoJson.length()
                ? rawMusicInfoJson : other.rawMusicInfoJson;
        return new SourceVariant(source, sourceId,
                quality128 || other.quality128,
                quality320 || other.quality320,
                raw);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("source", source);
            o.put("sourceId", sourceId);
            o.put("quality128", quality128);
            o.put("quality320", quality320);
            if (!rawMusicInfoJson.isEmpty()) o.put("rawMusicInfo", rawMusicInfoJson);
        } catch (JSONException ignored) { }
        return o;
    }

    public static SourceVariant fromJson(JSONObject o) {
        if (o == null) return new SourceVariant("", "", true, true);
        return new SourceVariant(
                o.optString("source", ""),
                o.optString("sourceId", ""),
                o.optBoolean("quality128", true),
                o.optBoolean("quality320", true),
                o.optString("rawMusicInfo", ""));
    }

    private static void putIfMissing(JSONObject o, String key, String value) {
        if (o == null || key == null || value == null || value.isEmpty() || o.has(key)) return;
        try { o.put(key, value); } catch (JSONException ignored) { }
    }
}
