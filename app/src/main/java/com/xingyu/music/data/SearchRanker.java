package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * N-catalog canonical search ranking.
 *
 * TX/KW/KG/WY are catalog identities only. Exact recordings are merged into
 * one UI row; different versions are intentionally kept separate.
 */
public final class SearchRanker {
    private SearchRanker() { }

    private static final String[] BAD_VERSION_MARKERS = {
            "live", "现场", "現場", "翻唱", "cover", "remix", "dj", "伴奏", "纯音乐", "純音樂",
            "instrumental", "karaoke", "demo", "sped up", "spedup", "slowed", "女声版", "女聲版",
            "男声版", "男聲版", "抖音版", "片段", "铃声", "鈴聲", "加速版", "慢速版",
            "说唱版", "說唱版", "完整版伴奏", "钢琴版", "鋼琴版"
    };
    private static final String[] SUSPICIOUS_ALBUM_MARKERS = {
            "翻唱", "cover", "抖音", "热歌", "熱歌", "网络", "網絡", "合集", "精选", "精選", "dj", "伴奏", "铃声", "鈴聲"
    };
    private static final Map<String, String[]> AMBIGUITY_HINTS = new HashMap<>();
    static {
        AMBIGUITY_HINTS.put(normalize("晴天"), new String[]{"周杰伦", "周杰倫"});
        AMBIGUITY_HINTS.put(normalize("不能说的秘密"), new String[]{"周杰伦", "周杰倫"});
        AMBIGUITY_HINTS.put(normalize("一样的月光"), new String[]{"徐佳莹", "徐佳瑩"});
    }

    private static final class Candidate {
        Song song;
        final Map<String,Integer> ranks = new LinkedHashMap<>();
        final Set<String> sources = new LinkedHashSet<>();
        int insertion;
        int score;
        int bestRank() { int best = 9999; for (int r : ranks.values()) best = Math.min(best, r); return best; }
    }

    /** Backward-compatible two-catalog entry point. */
    public static List<Song> mergeAndRank(String query, List<Song> wy, List<Song> tx, int limit) {
        LinkedHashMap<String,List<Song>> all = new LinkedHashMap<>();
        all.put("wy", wy == null ? new ArrayList<>() : wy);
        all.put("tx", tx == null ? new ArrayList<>() : tx);
        return mergeAndRank(query, all, limit);
    }

    public static List<Song> mergeAndRank(String query, Map<String,List<Song>> catalogs, int limit) {
        List<Candidate> canonical = new ArrayList<>();
        int insertion = 0;
        if (catalogs != null) {
            for (Map.Entry<String,List<Song>> entry : catalogs.entrySet()) {
                String source = entry.getKey() == null ? "" : entry.getKey();
                List<Song> songs = entry.getValue();
                if (songs == null) continue;
                for (int i = 0; i < songs.size(); i++) {
                    Song s = songs.get(i);
                    if (s == null || s.variants().isEmpty()) continue;
                    Candidate c = findExact(canonical, s);
                    if (c == null) {
                        c = new Candidate(); c.song = s; c.insertion = insertion++; canonical.add(c);
                    } else {
                        Song merged = c.song.withVariant(s);
                        if ((merged.coverUrl.isEmpty() && !s.coverUrl.isEmpty()) || (merged.album.isEmpty() && !s.album.isEmpty())) merged = s.withVariant(merged);
                        c.song = merged;
                    }
                    c.sources.add(source);
                    c.ranks.put(source, Math.min(c.ranks.getOrDefault(source, 9999), i));
                }
            }
        }

        String normalizedQuery = normalize(query);
        for (Candidate c : canonical) c.score = score(normalizedQuery, query, c);
        canonical.sort((a,b) -> {
            int x = Integer.compare(b.score, a.score); if (x != 0) return x;
            x = Integer.compare(a.bestRank(), b.bestRank()); if (x != 0) return x;
            return Integer.compare(a.insertion, b.insertion);
        });
        List<Song> out = new ArrayList<>();
        int max = Math.max(1, limit);
        for (Candidate c : canonical) { out.add(c.song); if (out.size() >= max) break; }
        return out;
    }

    private static Candidate findExact(List<Candidate> candidates, Song s) {
        for (Candidate c : candidates) if (ExactTrackMatcher.confidence(c.song, s) >= 0.98d) return c;
        return null;
    }

    private static int score(String normalizedQuery, String originalQuery, Candidate c) {
        Song s = c.song;
        String rawTitle = normalize(s.title);
        String artist = normalize(s.artist);
        int score = 0;
        String queryTitle = normalizedQuery;
        String queryArtist = "";
        String[] tokens = originalQuery == null ? new String[0] : originalQuery.trim().split("\\s+");
        if (tokens.length >= 2) {
            queryArtist = normalize(tokens[tokens.length - 1]);
            StringBuilder titlePart = new StringBuilder(); for (int i=0;i<tokens.length-1;i++) titlePart.append(tokens[i]);
            String possible = normalize(titlePart.toString()); if (!possible.isEmpty()) queryTitle = possible;
        }
        if (!queryTitle.isEmpty()) {
            if (rawTitle.equals(queryTitle)) score += 7000;
            else if (rawTitle.startsWith(queryTitle)) score += 4300;
            else if (rawTitle.contains(queryTitle)) score += 2800;
        }
        if (!queryArtist.isEmpty()) {
            if (artist.equals(queryArtist)) score += 5200;
            else if (artist.contains(queryArtist)) score += 3600;
            else score -= 1800;
        }
        score += Math.max(0, 2100 - c.bestRank() * 80);
        // V3: the same title + exact artist + exact duration independently appearing in several
        // catalogs is strong evidence for the canonical commercial recording. The first source
        // gets no bonus; corroborating catalogs do. ExactTrackMatcher already prevents different
        // artists/Live/cover versions from pooling this score.
        score += Math.max(0, Math.min(4, c.sources.size()) - 1) * 800;
        if (!s.album.isEmpty()) score += 330;
        if (s.durationMs >= 90_000L && s.durationMs <= 8 * 60_000L) score += 180;
        if (s.durationMs > 0 && s.durationMs < 55_000L) score -= 900;
        score += recordingQualityBias(s);
        String[] preferred = AMBIGUITY_HINTS.get(queryTitle);
        if (preferred != null) for (String a : preferred) if (artist.contains(normalize(a))) { score += 5400; break; }
        return score;
    }

    public static int recordingQualityBias(Song s) {
        if (s == null) return 0;
        String rawTitle = normalize(s.title), artist = normalize(s.artist), album = normalize(s.album);
        String all = rawTitle + "|" + artist + "|" + album;
        int score = 0;
        for (String marker : BAD_VERSION_MARKERS) if (all.contains(normalize(marker))) score -= 2300;
        for (String marker : SUSPICIOUS_ALBUM_MARKERS) if (album.contains(normalize(marker))) score -= 700;
        if (artist.contains(normalize("未知歌手")) || artist.contains(normalize("群星"))) score -= 800;
        boolean q320 = false, q128 = false;
        for (SourceVariant v : s.variants()) { if (v == null) continue; q320 |= v.quality320; q128 |= v.quality128; }
        if (q320) score += 360; else if (q128) score += 90;
        String[] canonicalArtists = AMBIGUITY_HINTS.get(normalize(s.title));
        if (canonicalArtists != null) for (String a : canonicalArtists) if (artist.contains(normalize(a))) { score += 2700; break; }
        return score;
    }

    private static String normalize(String value) {
        String v = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        v = Normalizer.normalize(v, Normalizer.Form.NFKC).replace("·", "").replace("•", "");
        return v.replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }
}
