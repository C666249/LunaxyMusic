package com.xingyu.music.data;

import com.xingyu.music.model.LyricLine;
import com.xingyu.music.model.LyricWord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricParser {
    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern YRC_LINE_MS = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_LINE_CLOCK = Pattern.compile("^\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?,(\\d+)](.*)$");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),(?:-?\\d+)\\)");

    private LyricParser() {}

    public static List<LyricLine> parse(String raw) {
        List<LyricLine> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.split("\\r?\\n")) {
            Matcher m = TAG.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find()) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long frac = fractionMs(m.group(3));
                times.add((min * 60 + sec) * 1000 + frac);
                end = m.end();
            }
            String text = end < line.length() ? line.substring(end).trim() : "";
            if (!text.isEmpty()) for (Long t : times) out.add(new LyricLine(t, text));
        }
        out.sort(Comparator.comparingLong(a -> a.timeMs));
        return withDerivedEnds(out);
    }

    /** Prefer NetEase YRC word timing when available, otherwise keep normal LRC. */
    public static List<LyricLine> parseEnhanced(String lrc, String yrc) {
        List<LyricLine> karaoke = parseYrc(yrc);
        if (!karaoke.isEmpty()) return withDerivedEnds(karaoke);
        return parse(lrc);
    }

    public static List<LyricLine> parseYrc(String raw) {
        List<LyricLine> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String source : raw.split("\\r?\\n")) {
            String line = source == null ? "" : source.trim();
            if (line.isEmpty() || line.startsWith("{")) continue; // metadata rows
            long lineStart;
            long lineDuration;
            String body;
            Matcher ms = YRC_LINE_MS.matcher(line);
            Matcher clock = YRC_LINE_CLOCK.matcher(line);
            if (ms.matches()) {
                lineStart = parseLong(ms.group(1));
                lineDuration = parseLong(ms.group(2));
                body = ms.group(3);
            } else if (clock.matches()) {
                lineStart = (parseLong(clock.group(1)) * 60L + parseLong(clock.group(2))) * 1000L
                        + fractionMs(clock.group(3));
                lineDuration = parseLong(clock.group(4));
                body = clock.group(5);
            } else continue;

            List<LyricWord> words = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            Matcher wm = YRC_WORD.matcher(body);
            while (wm.find()) {
                long rawStart = parseLong(wm.group(1));
                long duration = Math.max(1L, parseLong(wm.group(2)));
                int textStart = wm.end();
                int next = body.indexOf('(', textStart);
                String word = (next < 0 ? body.substring(textStart) : body.substring(textStart, next));
                if (word.isEmpty()) continue;
                // Current NCM YRC normally uses absolute millisecond starts. Some
                // compatible exporters use line-relative offsets, so accept both.
                long absoluteStart = rawStart < lineStart ? lineStart + rawStart : rawStart;
                words.add(new LyricWord(absoluteStart, duration, word));
                text.append(word);
            }
            String clean = text.toString().trim();
            if (clean.isEmpty()) continue;
            long endMs = lineDuration > 0 ? lineStart + lineDuration : 0L;
            out.add(new LyricLine(lineStart, endMs, clean, words));
        }
        out.sort(Comparator.comparingLong(a -> a.timeMs));
        return out;
    }

    public static int activeIndex(List<LyricLine> lines, long pos) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= pos) idx = i;
            else break;
        }
        return idx;
    }

    /**
     * Character-level karaoke progress. With YRC this follows word/syllable timestamps.
     * Plain LRC falls back to a conservative interpolation until the next line.
     */
    public static float characterProgress(LyricLine line, long positionMs, long fallbackEndMs) {
        if (line == null || line.text.isEmpty()) return 0f;
        if (positionMs <= line.timeMs) return 0f;
        if (line.hasWordTiming()) {
            int doneChars = 0;
            for (LyricWord word : line.words) {
                int chars = Math.max(1, word.text.length());
                if (positionMs >= word.endMs()) {
                    doneChars += chars;
                    continue;
                }
                if (positionMs <= word.startMs) break;
                float within = Math.min(1f, Math.max(0f,
                        (float) (positionMs - word.startMs) / Math.max(1L, word.durationMs)));
                return clamp((doneChars + chars * within) / Math.max(1f, line.text.length()));
            }
            return clamp(doneChars / Math.max(1f, line.text.length()));
        }
        long end = line.endMs > line.timeMs ? line.endMs : fallbackEndMs;
        if (end <= line.timeMs) end = line.timeMs + 3600L;
        return clamp((float) (positionMs - line.timeMs) / Math.max(1L, end - line.timeMs));
    }

    private static List<LyricLine> withDerivedEnds(List<LyricLine> input) {
        List<LyricLine> out = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            LyricLine line = input.get(i);
            long next = i + 1 < input.size() ? input.get(i + 1).timeMs : line.timeMs + 4200L;
            long end = line.endMs > line.timeMs ? line.endMs : next;
            out.add(new LyricLine(line.timeMs, end, line.text, line.words));
        }
        return out;
    }

    private static long fractionMs(String f) {
        if (f == null || f.isEmpty()) return 0L;
        if (f.length() == 1) return parseLong(f) * 100L;
        if (f.length() == 2) return parseLong(f) * 10L;
        return parseLong(f.substring(0, Math.min(3, f.length())));
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value == null ? "0" : value); }
        catch (Exception ignored) { return 0L; }
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
