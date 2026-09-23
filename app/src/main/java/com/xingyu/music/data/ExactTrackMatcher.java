package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative recording identity gate for cross-catalog variants.
 *
 * A route may come from another catalog, but it is admitted only when title,
 * artist and duration describe the same recording.  Obvious Live/Cover/Remix
 * version markers are treated as identity, not decoration.
 */
public final class ExactTrackMatcher {
    private ExactTrackMatcher() { }

    private static final String[] VERSION_MARKERS = {
            "live", "现场", "現場", "翻唱", "cover", "remix", "dj", "伴奏", "纯音乐", "純音樂",
            "instrumental", "karaoke", "demo", "acoustic", "sped up", "spedup", "slowed",
            "女声版", "女聲版", "男声版", "男聲版", "抖音版", "片段", "铃声", "鈴聲", "加速版",
            "慢速版", "说唱版", "說唱版", "钢琴版", "鋼琴版", "remastered", "重制", "重製"
    };

    public static double confidence(Song anchor, Song candidate) {
        if (anchor == null || candidate == null) return 0d;
        String at = normalizeTitle(anchor.title);
        String bt = normalizeTitle(candidate.title);
        if (at.isEmpty() || !at.equals(bt)) return 0d;
        if (!versionSignature(anchor.title).equals(versionSignature(candidate.title))) return 0d;

        Set<String> aa = artistTokens(anchor.artist);
        Set<String> ba = artistTokens(candidate.artist);
        if (aa.isEmpty() || ba.isEmpty()) return 0d;
        if (!aa.equals(ba)) return 0d;

        long ad = anchor.durationMs;
        long bd = candidate.durationMs;
        if (ad > 0 && bd > 0) {
            long diff = Math.abs(ad - bd);
            if (diff <= 4_000L) return albumCompatible(anchor, candidate) ? 1.0d : 0.995d;
            if (diff <= 8_000L) return albumCompatible(anchor, candidate) ? 0.995d : 0.99d;
            if (diff <= 12_000L) return albumCompatible(anchor, candidate) ? 0.985d : 0.98d;
            return 0d;
        }

        // Cross-catalog identity without duration is too weak for auto playback.
        if (albumCompatible(anchor, candidate) && !safe(anchor.album).isEmpty() && !safe(candidate.album).isEmpty()) return 0.97d;
        return 0.90d;
    }

    public static boolean isExact(Song anchor, Song candidate) { return confidence(anchor, candidate) >= 0.98d; }

    /** Stable overlay key. Version signature is intentionally included. */
    public static String identityKey(Song s) {
        if (s == null) return "";
        return normalizeTitle(s.title) + "|" + String.join("/", artistTokens(s.artist)) + "|" + versionSignature(s.title);
    }

    public static boolean hasAlternateVersionMarker(String title) { return !versionSignature(title).isEmpty(); }

    /**
     * Validate one provider-native identity against the canonical anchor using the
     * variant's own raw catalog metadata. This is also a migration guard for old
     * V47/V1/V2 songs whose fallback variants may have been merged under looser rules.
     */
    public static double variantConfidence(Song anchor, SourceVariant variant) {
        if (anchor == null || variant == null) return 0d;
        if (variant.source.equals(anchor.source) && variant.sourceId.equals(anchor.sourceId)) return 1d;
        String raw = variant.rawMusicInfoJson();
        if (raw == null || raw.trim().isEmpty()) return 0d;
        try {
            JSONObject o = new JSONObject(raw);
            String title = firstNonEmpty(o.optString("name", ""), o.optString("title", ""),
                    o.optString("songname", ""), o.optString("SongName", ""));
            String artist = rawArtist(o);
            if (title.isEmpty() || artist.isEmpty()) return 0d;
            String album = rawAlbum(o);
            long duration = rawDurationMs(o);
            Song candidate = new Song(title, artist, album, "", duration,
                    java.util.Collections.singletonList(variant));
            return confidence(anchor, candidate);
        } catch (Exception ignored) { return 0d; }
    }

    /** Keep the anchor identity and only cross-catalog variants that pass the exact gate. */
    public static Song sanitizeVariants(Song song) {
        if (song == null) return null;
        List<SourceVariant> trusted = new ArrayList<>();
        for (SourceVariant v : song.variants()) {
            if (v == null) continue;
            if ((v.source.equals(song.source) && v.sourceId.equals(song.sourceId)) || variantConfidence(song, v) >= 0.98d)
                trusted.add(v);
        }
        return new Song(song.title, song.artist, song.album, song.coverUrl, song.durationMs, trusted);
    }

    private static long rawDurationMs(JSONObject o) {
        long v = 0L;
        if (o.has("durationMs")) v = o.optLong("durationMs", 0L);
        if (v <= 0 && o.has("dt")) v = o.optLong("dt", 0L);
        if (v <= 0 && o.has("duration")) v = o.optLong("duration", 0L);
        if (v <= 0 && o.has("DURATION")) v = o.optLong("DURATION", 0L);
        if (v <= 0 && o.has("Duration")) v = o.optLong("Duration", 0L);
        if (v <= 0 && o.has("_interval")) v = o.optLong("_interval", 0L);
        if (v <= 0 && o.has("interval")) {
            Object rawInterval = o.opt("interval");
            if (rawInterval instanceof Number) v = ((Number) rawInterval).longValue();
            else { try { v = Long.parseLong(String.valueOf(rawInterval)); } catch (Exception ignored) { } }
        }
        if (v > 0 && v < 20_000L) v *= 1000L;
        if (v <= 0) {
            String interval = firstNonEmpty(o.optString("interval", ""), o.optString("time", ""));
            String[] parts = interval.split(":");
            try {
                if (parts.length == 2) v = (Long.parseLong(parts[0]) * 60L + Long.parseLong(parts[1])) * 1000L;
            } catch (Exception ignored) { }
        }
        return Math.max(0L, v);
    }

    private static String rawArtist(JSONObject o) {
        if (o == null) return "";
        String direct = firstNonEmpty(o.optString("artist", ""), o.optString("SingerName", ""), o.optString("ARTIST", ""));
        Object singerValue = o.opt("singer");
        if (singerValue instanceof org.json.JSONArray) {
            String joined = joinArtistArray((org.json.JSONArray) singerValue);
            if (!joined.isEmpty()) return joined;
        } else if (singerValue != null) {
            String s = String.valueOf(singerValue).trim();
            if (!s.isEmpty() && !s.startsWith("[")) return s;
        }
        org.json.JSONArray ar = o.optJSONArray("ar");
        if (ar == null) ar = o.optJSONArray("artists");
        String joined = joinArtistArray(ar);
        return !joined.isEmpty() ? joined : direct;
    }

    private static String joinArtistArray(org.json.JSONArray a) {
        if (a == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length(); i++) {
            JSONObject item = a.optJSONObject(i);
            String name = item == null ? "" : firstNonEmpty(item.optString("name", ""), item.optString("Name", ""));
            if (name.isEmpty()) continue;
            if (b.length() > 0) b.append(" / ");
            b.append(name);
        }
        return b.toString();
    }

    private static String rawAlbum(JSONObject o) {
        if (o == null) return "";
        String direct = firstNonEmpty(o.optString("albumName", ""), o.optString("ALBUM", ""));
        Object album = o.opt("album");
        if (album instanceof JSONObject) {
            String name = ((JSONObject) album).optString("name", "");
            if (!name.trim().isEmpty()) return name.trim();
        }
        JSONObject al = o.optJSONObject("al");
        if (al != null && !al.optString("name", "").trim().isEmpty()) return al.optString("name", "").trim();
        return direct;
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private static boolean albumCompatible(Song a, Song b) {
        String x = normalize(a == null ? "" : a.album);
        String y = normalize(b == null ? "" : b.album);
        if (x.isEmpty() || y.isEmpty()) return true;
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    private static Set<String> artistTokens(String value) {
        String raw = safe(value).replace('、', '/').replace('&', '/').replace('，', '/').replace(',', '/');
        List<String> parts = new ArrayList<>(Arrays.asList(raw.split("[/;|]+")));
        Set<String> out = new java.util.TreeSet<>();
        for (String p : parts) {
            String n = normalize(p);
            if (!n.isEmpty() && !"未知歌手".equals(n) && !"群星".equals(n)) out.add(n);
        }
        return out;
    }

    private static String normalizeTitle(String value) {
        return normalize(safe(value));
    }

    private static String versionSignature(String title) {
        String n = normalize(safe(title));
        List<String> hits = new ArrayList<>();
        for (String marker : VERSION_MARKERS) {
            String m = normalize(marker);
            if (!m.isEmpty() && n.contains(m)) hits.add(m);
        }
        java.util.Collections.sort(hits);
        return String.join("+", hits);
    }

    private static String normalize(String value) {
        String v = safe(value).toLowerCase(Locale.ROOT);
        v = Normalizer.normalize(v, Normalizer.Form.NFKC).replace("·", "").replace("•", "");
        return v.replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
