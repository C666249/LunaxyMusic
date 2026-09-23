package com.xingyu.music.voice;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic Chinese-first music command parser. No network or Android dependency. */
public final class VoiceCommandParser {
    private static final Pattern NEXT = Pattern.compile("(?:下|往后|后面)([一二两三四五六七八九十0-9]*)(?:首|个)");
    private static final Pattern PREVIOUS = Pattern.compile("(?:上|往前|前面)([一二两三四五六七八九十0-9]*)(?:首|个)");
    private static final Pattern PLAYLIST = Pattern.compile("^(?:请)?(?:播放|放|打开)(?:一下|点)?(?:我的)?(.+?)(?:歌单|播放列表)$");
    private static final Pattern PLAYLIST_PREFIX = Pattern.compile("^(?:请)?(?:播放|放|打开)(?:一下|点)?(?:我的)?(?:歌单|播放列表)(.+)$");
    private static final Pattern SEARCH_ARTIST = Pattern.compile("^(?:请)?(?:搜索|搜|查)(?:一下)?(?:歌手|艺人)(.+)$");
    private static final Pattern SEARCH = Pattern.compile("^(?:请)?(?:搜索|搜|查)(?:一下)?(?:歌曲|歌名|音乐)?(.+)$");
    private static final Pattern PLAY_ARTIST = Pattern.compile("^(?:请)?(?:播放|放|来|听)(?:一下|点)?(.+?)(?:的歌|的歌曲|的音乐)$");
    private static final Pattern PLAY_QUERY = Pattern.compile("^(?:请)?(?:播放|放|听)(?:一下|点)?(.+)$");

    private VoiceCommandParser() { }

    public static VoiceCommand parse(String raw) {
        String original = raw == null ? "" : raw.trim();
        String text = cleanup(original);
        if (text.isEmpty()) return VoiceCommand.none(original);

        if (containsAny(text, "暂停", "暂停播放", "停一下", "先停", "先暂停", "停止播放"))
            return new VoiceCommand(VoiceCommand.Type.PAUSE, "", 1, original);
        if (containsAny(text, "继续", "继续播放", "恢复播放", "开始播放", "接着播", "接着播放"))
            return new VoiceCommand(VoiceCommand.Type.RESUME, "", 1, original);
        if (containsAny(text, "下一首", "下一曲", "切下一首"))
            return new VoiceCommand(VoiceCommand.Type.NEXT, "", 1, original);
        if (containsAny(text, "上一首", "上一曲", "前一首", "切上一首"))
            return new VoiceCommand(VoiceCommand.Type.PREVIOUS, "", 1, original);

        Matcher next = NEXT.matcher(text);
        if (next.find() && (text.contains("下") || text.contains("往后") || text.contains("后面")))
            return new VoiceCommand(VoiceCommand.Type.NEXT, "", parseCount(next.group(1)), original);
        Matcher previous = PREVIOUS.matcher(text);
        if (previous.find() && (text.contains("上") || text.contains("往前") || text.contains("前面")))
            return new VoiceCommand(VoiceCommand.Type.PREVIOUS, "", parseCount(previous.group(1)), original);

        if (containsAny(text, "播放收藏", "播放我的收藏", "播放我收藏的歌", "放我的收藏", "放我收藏的歌", "播放喜欢的歌", "播放我喜欢的歌"))
            return new VoiceCommand(VoiceCommand.Type.PLAY_PLAYLIST, "收藏", 1, original);
        if (containsAny(text, "播放最近播放", "播放最近听过", "放最近听的", "播放最近的歌"))
            return new VoiceCommand(VoiceCommand.Type.PLAY_PLAYLIST, "最近播放", 1, original);

        Matcher playlist = PLAYLIST.matcher(text);
        if (playlist.matches()) return new VoiceCommand(VoiceCommand.Type.PLAY_PLAYLIST, trimQuery(playlist.group(1)), 1, original);
        Matcher playlistPrefix = PLAYLIST_PREFIX.matcher(text);
        if (playlistPrefix.matches()) return new VoiceCommand(VoiceCommand.Type.PLAY_PLAYLIST, trimQuery(playlistPrefix.group(1)), 1, original);

        Matcher searchArtist = SEARCH_ARTIST.matcher(text);
        if (searchArtist.matches()) return new VoiceCommand(VoiceCommand.Type.SEARCH_ARTIST, trimQuery(searchArtist.group(1)), 1, original);

        Matcher search = SEARCH.matcher(text);
        if (search.matches()) return new VoiceCommand(VoiceCommand.Type.SEARCH, trimQuery(search.group(1)), 1, original);

        Matcher artist = PLAY_ARTIST.matcher(text);
        if (artist.matches()) return new VoiceCommand(VoiceCommand.Type.PLAY_ARTIST, trimQuery(artist.group(1)), 1, original);

        Matcher play = PLAY_QUERY.matcher(text);
        if (play.matches()) {
            String query = trimQuery(play.group(1));
            if (query.isEmpty() || "音乐".equals(query) || "歌曲".equals(query))
                return new VoiceCommand(VoiceCommand.Type.RESUME, "", 1, original);
            return new VoiceCommand(VoiceCommand.Type.PLAY_QUERY, query, 1, original);
        }

        return VoiceCommand.none(original);
    }

    /** Remove a detected wake phrase and return the command tail, if any. */
    public static String stripWakePhrase(String text, String... wakeAliases) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return "";

        // ASR often emits English wake words with punctuation inserted between the two words,
        // e.g. "Hey，Lunaxy 下一首". Handle that shape before the generic normalized lookup.
        String englishStripped = value.replaceFirst("(?i)^.*?(?:hey|hi)[\\s，,。.!！?？]*lunaxy[\\s，,。.!！?？:：-]*", "").trim();
        if (!englishStripped.equals(value)) return englishStripped;

        String normalized = normalize(value);
        if (wakeAliases != null) {
            for (String alias : wakeAliases) {
                String a = normalize(alias);
                if (a.isEmpty()) continue;
                int at = normalized.indexOf(a);
                if (at >= 0) {
                    // Chinese/custom aliases normally survive ASR as a contiguous source substring.
                    // If exact mapping fails, returning an empty tail is safer than accidentally executing
                    // the wake phrase itself as a song query; the assistant will simply wait for the command.
                    String rawAlias = alias == null ? "" : alias.trim();
                    int rawAt = value.toLowerCase(Locale.ROOT).indexOf(rawAlias.toLowerCase(Locale.ROOT));
                    if (rawAt >= 0) return value.substring(Math.min(value.length(), rawAt + rawAlias.length())).trim();
                    return "";
                }
            }
        }
        return value;
    }

    public static boolean containsWakePhrase(String text, String... wakeAliases) {
        String normalized = normalize(text);
        if (normalized.isEmpty() || wakeAliases == null) return false;
        for (String alias : wakeAliases) {
            String a = normalize(alias);
            if (!a.isEmpty() && normalized.contains(a)) return true;
        }
        return false;
    }

    public static String normalize(String text) {
        return (text == null ? "" : text.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }

    private static String cleanup(String text) {
        return (text == null ? "" : text.trim())
                .replace('，', ' ').replace('。', ' ').replace('！', ' ').replace('？', ' ')
                .replaceAll("\\s+", "")
                .replace("lunaxy", "Lunaxy");
    }

    private static String trimQuery(String value) {
        String q = value == null ? "" : value.trim();
        q = q.replaceAll("^[的地得]+", "").replaceAll("(?:吧|呀|啊|呢|一下)$", "").trim();
        return q;
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.equals(value) || text.contains(value)) return true;
        return false;
    }

    private static int parseCount(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 1;
        String s = raw.trim();
        try { return clamp(Integer.parseInt(s)); } catch (Exception ignored) { }
        if ("十".equals(s)) return 10;
        int total = 0;
        if (s.contains("十")) {
            String[] parts = s.split("十", -1);
            int tens = parts[0].isEmpty() ? 1 : chineseDigit(parts[0]);
            int ones = parts.length > 1 && !parts[1].isEmpty() ? chineseDigit(parts[1]) : 0;
            total = tens * 10 + ones;
        } else total = chineseDigit(s);
        return clamp(total <= 0 ? 1 : total);
    }

    private static int chineseDigit(String s) {
        if (s == null || s.isEmpty()) return 0;
        switch (s.charAt(0)) {
            case '一': return 1;
            case '二': case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return 0;
        }
    }

    private static int clamp(int value) { return Math.max(1, Math.min(10, value)); }
}
