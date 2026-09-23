package com.xingyu.music.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical track model.
 *
 * The UI shows one song, while the track may carry multiple provider-native
 * identities (tx / wy / kw / kg / mg). This mirrors LX's important separation
 * between "which song is this?" and "which provider should resolve its URL?".
 *
 * Legacy public fields are intentionally retained so the existing V5 UI and
 * local library format continue to work.
 */
public final class Song {
    public final String source;
    public final String sourceId;
    public final String fallbackSource;
    public final String fallbackSourceId;
    public final String title;
    public final String artist;
    public final String album;
    public final String coverUrl;
    public final long durationMs;
    private final List<SourceVariant> variants;

    public Song(String source, String sourceId, String title, String artist,
                String album, String coverUrl, long durationMs) {
        this(source, sourceId, title, artist, album, coverUrl, durationMs, true, true);
    }

    public Song(String source, String sourceId, String title, String artist,
                String album, String coverUrl, long durationMs,
                boolean quality128, boolean quality320) {
        this(title, artist, album, coverUrl, durationMs,
                Collections.singletonList(new SourceVariant(safe(source, "wy"), safe(sourceId, ""), quality128, quality320)));
    }

    /** Backward-compatible V5 constructor. */
    public Song(String source, String sourceId, String fallbackSource, String fallbackSourceId,
                String title, String artist, String album, String coverUrl, long durationMs) {
        List<SourceVariant> vs = new ArrayList<>();
        vs.add(new SourceVariant(safe(source, "wy"), safe(sourceId, ""), true, true));
        if (fallbackSource != null && !fallbackSource.trim().isEmpty()
                && fallbackSourceId != null && !fallbackSourceId.trim().isEmpty()) {
            vs.add(new SourceVariant(fallbackSource.trim(), fallbackSourceId.trim(), true, true));
        }
        this.title = safe(title, "未知歌曲");
        this.artist = safe(artist, "未知歌手");
        this.album = album == null ? "" : album.trim();
        this.coverUrl = normalizeHttps(coverUrl == null ? "" : coverUrl.trim());
        this.durationMs = Math.max(durationMs, 0L);
        this.variants = dedupe(vs);
        SourceVariant p = this.variants.isEmpty() ? new SourceVariant("wy", "", true, true) : this.variants.get(0);
        SourceVariant f = this.variants.size() > 1 ? this.variants.get(1) : null;
        this.source = p.source;
        this.sourceId = p.sourceId;
        this.fallbackSource = f == null ? "" : f.source;
        this.fallbackSourceId = f == null ? "" : f.sourceId;
    }

    public Song(String title, String artist, String album, String coverUrl,
                long durationMs, List<SourceVariant> variants) {
        this.title = safe(title, "未知歌曲");
        this.artist = safe(artist, "未知歌手");
        this.album = album == null ? "" : album.trim();
        this.coverUrl = normalizeHttps(coverUrl == null ? "" : coverUrl.trim());
        this.durationMs = Math.max(durationMs, 0L);
        this.variants = dedupe(variants);
        SourceVariant p = this.variants.isEmpty() ? new SourceVariant("wy", "", true, true) : this.variants.get(0);
        SourceVariant f = this.variants.size() > 1 ? this.variants.get(1) : null;
        this.source = p.source;
        this.sourceId = p.sourceId;
        this.fallbackSource = f == null ? "" : f.source;
        this.fallbackSourceId = f == null ? "" : f.sourceId;
    }

    public String key() {
        // A canonical identity remains stable even if preferred provider changes.
        return normalizeIdentity(title) + "|" + normalizeIdentity(artist) + "|" + Math.round(durationMs / 2000.0);
    }

    public List<SourceVariant> variants() { return new ArrayList<>(variants); }
    public SourceVariant variant(String sourceName) {
        if (sourceName == null) return null;
        for (SourceVariant v : variants) if (sourceName.equals(v.source)) return v;
        return null;
    }
    public boolean hasFallback() { return variants.size() > 1; }

    public Song withFallback(Song alt) { return withVariant(alt); }

    public Song withVariant(Song alt) {
        if (alt == null) return this;
        List<SourceVariant> merged = new ArrayList<>(variants);
        merged.addAll(alt.variants);
        return new Song(title, artist,
                album.isEmpty() ? alt.album : album,
                coverUrl.isEmpty() ? alt.coverUrl : coverUrl,
                durationMs > 0 ? durationMs : alt.durationMs,
                merged);
    }

    /** Put a provider first without destroying the other provider identities. */
    public Song preferSource(String wanted) {
        if (wanted == null || wanted.isEmpty() || variants.size() < 2) return this;
        List<SourceVariant> reordered = new ArrayList<>();
        for (SourceVariant v : variants) if (wanted.equals(v.source)) reordered.add(v);
        for (SourceVariant v : variants) if (!wanted.equals(v.source)) reordered.add(v);
        return new Song(title, artist, album, coverUrl, durationMs, reordered);
    }

    public String sourceLabel() {
        if (variants.isEmpty()) return providerLabel(source);
        StringBuilder b = new StringBuilder();
        for (SourceVariant v : variants) {
            String label = providerLabel(v.source);
            if (b.indexOf(label) >= 0) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(label);
            if (b.length() > 16) break;
        }
        return b.toString();
    }

    public static String providerLabel(String s) {
        if ("tx".equals(s)) return "QQ";
        if ("kw".equals(s)) return "酷我";
        if ("kg".equals(s)) return "酷狗";
        if ("mg".equals(s)) return "咪咕";
        if ("wy".equals(s)) return "网易";
        if ("ytm".equals(s)) return "YouTube Music";
        if ("local".equals(s)) return "本地";
        return s == null || s.trim().isEmpty() ? "未知来源" : s.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public JSONObject toJson() {
        JSONObject out = new JSONObject();
        try {
            out.put("source", source);
            out.put("sourceId", sourceId);
            out.put("fallbackSource", fallbackSource);
            out.put("fallbackSourceId", fallbackSourceId);
            out.put("title", title);
            out.put("artist", artist);
            out.put("album", album);
            out.put("coverUrl", coverUrl);
            out.put("durationMs", durationMs);
            JSONArray a = new JSONArray();
            for (SourceVariant v : variants) a.put(v.toJson());
            out.put("variants", a);
        } catch (JSONException ignored) { }
        return out;
    }

    public static Song fromJson(JSONObject o) {
        if (o == null) return new Song("wy", "", "未知歌曲", "未知歌手", "", "", 0L);
        List<SourceVariant> vs = new ArrayList<>();
        JSONArray a = o.optJSONArray("variants");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                SourceVariant v = SourceVariant.fromJson(a.optJSONObject(i));
                if (!v.source.isEmpty() && !v.sourceId.isEmpty()) vs.add(v);
            }
        }
        if (vs.isEmpty()) {
            String source = o.optString("source", "wy");
            String sourceId = o.optString("sourceId", "");
            vs.add(new SourceVariant(source, sourceId, true, true));
            String fs = o.optString("fallbackSource", "");
            String fi = o.optString("fallbackSourceId", "");
            if (!fs.isEmpty() && !fi.isEmpty()) vs.add(new SourceVariant(fs, fi, true, true));
        }
        return new Song(
                o.optString("title", "未知歌曲"),
                o.optString("artist", "未知歌手"),
                o.optString("album", ""),
                o.optString("coverUrl", ""),
                o.optLong("durationMs", 0L),
                vs);
    }

    private static List<SourceVariant> dedupe(List<SourceVariant> input) {
        Map<String, SourceVariant> map = new LinkedHashMap<>();
        if (input != null) {
            for (SourceVariant v : input) {
                if (v == null || v.source.isEmpty() || v.sourceId.isEmpty()) continue;
                SourceVariant old = map.get(v.key());
                if (old == null) map.put(v.key(), v);
                else map.put(v.key(), old.merge(v));
            }
        }
        return new ArrayList<>(map.values());
    }

    private static String safe(String v, String fallback) {
        return v == null || v.trim().isEmpty() ? fallback : v.trim();
    }

    private static String normalizeHttps(String s) {
        return s.startsWith("http://") ? "https://" + s.substring(7) : s;
    }

    private static String normalizeIdentity(String s) {
        return (s == null ? "" : s.toLowerCase(java.util.Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }
}
