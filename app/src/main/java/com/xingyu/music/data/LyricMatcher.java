package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Conservative cross-provider matcher used only to borrow NetEase lyrics for QQ tracks. */
public final class LyricMatcher {
    private LyricMatcher() { }

    public static SourceVariant bestNeteaseVariant(Song target, List<Song> candidates) {
        if (target == null || candidates == null) return null;
        String targetTitle = normalize(stripVersion(target.title));
        String targetArtist = normalizeArtist(target.artist);
        if (targetTitle.isEmpty() || targetArtist.isEmpty()) return null;

        Song best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Song candidate : candidates) {
            if (candidate == null || candidate.variant("wy") == null) continue;
            String title = normalize(stripVersion(candidate.title));
            String artist = normalizeArtist(candidate.artist);
            if (!targetTitle.equals(title)) continue; // title must be exact after version suffix cleanup
            if (!artistOverlap(targetArtist, artist)) continue; // artist must agree too

            int score = 100;
            long a = target.durationMs, b = candidate.durationMs;
            if (a > 0 && b > 0) {
                long diff = Math.abs(a - b);
                if (diff <= 1800L) score += 40;
                else if (diff <= 3800L) score += 22;
                else if (diff <= 6500L) score += 5;
                else continue; // wrong edit/live version risk is too high
            }
            if (!target.album.isEmpty() && !candidate.album.isEmpty()
                    && normalize(target.album).equals(normalize(candidate.album))) score += 12;
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        return best == null ? null : best.variant("wy");
    }

    private static boolean artistOverlap(String a, String b) {
        if (a.equals(b) || a.contains(b) || b.contains(a)) return true;
        for (String x : a.split("[/、&,，]+")) {
            if (x.isEmpty()) continue;
            for (String y : b.split("[/、&,，]+")) {
                if (!y.isEmpty() && (x.equals(y) || x.contains(y) || y.contains(x))) return true;
            }
        }
        return false;
    }

    private static String normalizeArtist(String s) {
        return normalize(s).replace("feat", "").replace("ft", "");
    }

    private static String stripVersion(String s) {
        String t = s == null ? "" : s.trim();
        return t.replaceAll("(?i)\\s*[（(\\[]\\s*(live|现场|現場|翻唱|cover|remix|dj|伴奏|instrumental|demo|acoustic)[^）)\\]]*[）)\\]]\\s*$", "")
                .replaceAll("(?i)\\s*[-–—]\\s*(live|现场|現場|翻唱|cover|remix|dj|伴奏|instrumental|demo|acoustic).*$", "")
                .trim();
    }

    private static String normalize(String s) {
        String v = s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
        v = Normalizer.normalize(v, Normalizer.Form.NFKC).replace("·", "").replace("•", "");
        return v.replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }
}
