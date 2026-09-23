package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search-only QQ Music lyric index adapter.
 *
 * This class is deliberately isolated from QQMusicApi and every playback resolver. Search type 7
 * is used only to discover candidate recordings from lyric text. The normal Canonical Track / Audio
 * Provider Hub still decides how a selected song is played.
 */
public final class QQMusicLyricSearchApi {
    private static final String WEB_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36";
    private static final String APP_UA = "QQMusic 14090508(android 12)";
    private static final int[] PART_1 = {23, 14, 6, 36, 16, 40, 7, 19};
    private static final int[] PART_2 = {16, 1, 32, 12, 19, 27, 8, 5};
    private static final int[] SCRAMBLE = {89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179};
    private volatile int lastTotal;

    public static final class Hit {
        public final Song song;
        public final String snippet;
        public Hit(Song song, String snippet) {
            this.song = song;
            this.snippet = snippet == null ? "" : snippet.trim();
        }
    }

    public int lastTotal() { return Math.max(0, lastTotal); }

    public List<Hit> search(String keyword, int limit) throws Exception {
        lastTotal = 0;
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) return new ArrayList<>();
        Exception first = null;
        try {
            List<Hit> hits = searchSignedMobile(q, limit);
            if (!hits.isEmpty()) return hits;
        } catch (Exception e) { first = e; }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("歌词搜索已取消");
        try {
            List<Hit> hits = searchUnsignedDesktop(q, limit);
            if (!hits.isEmpty()) return hits;
        } catch (Exception second) {
            if (first == null) first = second;
        }
        if (first != null) throw first;
        return new ArrayList<>();
    }

    private List<Hit> searchSignedMobile(String keyword, int limit) throws Exception {
        JSONObject comm = mobileComm();
        JSONObject param = searchParam(keyword, limit);
        JSONObject req = new JSONObject();
        req.put("module", "music.search.SearchCgiService");
        req.put("method", "DoSearchForQQMusicMobile");
        req.put("param", param);
        JSONObject payload = new JSONObject();
        payload.put("comm", comm);
        payload.put("req", req);
        String raw = payload.toString();
        Map<String,String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", APP_UA);
        headers.put("Accept", "application/json,text/plain,*/*");
        Http.Response response = Http.postJson("https://u.y.qq.com/cgi-bin/musics.fcg?sign=" + zzcSign(raw), headers, raw);
        if (response.code < 200 || response.code >= 300) throw new Exception("QQ歌词索引 HTTP " + response.code);
        JSONObject root = parseJsonObject(response.body);
        if (root.optInt("code", -1) != 0) throw new Exception("QQ歌词索引 root=" + root.optInt("code", -1));
        JSONObject block = root.optJSONObject("req");
        if (block == null || block.optInt("code", -1) != 0) {
            int code = block == null ? -1 : block.optInt("code", -1);
            throw new Exception(code == 2001 ? "QQ歌词索引要求登录" : "QQ歌词索引 code=" + code);
        }
        return parseSearchBlock(block.optJSONObject("data"), keyword);
    }

    private List<Hit> searchUnsignedDesktop(String keyword, int limit) throws Exception {
        JSONObject req = new JSONObject();
        req.put("method", "DoSearchForQQMusicDesktop");
        req.put("module", "music.search.SearchCgiService");
        req.put("param", searchParam(keyword, limit));
        JSONObject comm = new JSONObject();
        comm.put("ct", 24);
        comm.put("cv", 0);
        JSONObject payload = new JSONObject();
        payload.put("comm", comm);
        payload.put("music.search.SearchCgiService", req);
        Http.Response response = Http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", webHeaders(), payload.toString());
        if (response.code < 200 || response.code >= 300) throw new Exception("QQ歌词索引 HTTP " + response.code);
        JSONObject root = parseJsonObject(response.body);
        JSONObject block = root.optJSONObject("music.search.SearchCgiService");
        if (block == null) throw new Exception("QQ歌词索引缺少搜索结果");
        int code = block.optInt("code", -1);
        if (code != 0) throw new Exception(code == 2001 ? "QQ歌词索引要求登录" : "QQ歌词索引 code=" + code);
        return parseSearchBlock(block.optJSONObject("data"), keyword);
    }

    private JSONObject searchParam(String keyword, int limit) throws Exception {
        JSONObject param = new JSONObject();
        param.put("search_type", 7); // QQ Music lyric full-text index
        param.put("searchid", String.valueOf(new java.util.Random().nextLong() & Long.MAX_VALUE));
        param.put("query", keyword);
        param.put("page_num", 1);
        param.put("num_per_page", Math.max(1, Math.min(limit, 30)));
        param.put("highlight", 0);
        return param;
    }

    private JSONObject mobileComm() throws Exception {
        JSONObject comm = new JSONObject();
        comm.put("ct", "11"); comm.put("cv", "14090508"); comm.put("v", "14090508");
        comm.put("tmeAppID", "qqmusic"); comm.put("phonetype", "EBG-AN10");
        comm.put("deviceScore", "553.47"); comm.put("devicelevel", "50"); comm.put("newdevicelevel", "20");
        comm.put("rom", "HuaWei/EMOTION/EmotionUI_14.2.0"); comm.put("os_ver", "12");
        String[] zeroKeys = {"OpenUDID", "OpenUDID2", "QIMEI36", "udid", "chid", "aid", "oaid", "taid", "tid", "wid", "uid", "sid"};
        for (String key : zeroKeys) comm.put(key, "0");
        comm.put("modeSwitch", "6"); comm.put("teenMode", "0"); comm.put("ui_mode", "2"); comm.put("nettype", "1020"); comm.put("v4ip", "");
        return comm;
    }

    /** Tolerates both current mobile item_song results and older desktop lyric/song list envelopes. */
    private List<Hit> parseSearchBlock(JSONObject data, String keyword) {
        ArrayList<Hit> out = new ArrayList<>();
        if (data == null) return out;
        JSONObject body = data.optJSONObject("body");
        if (body == null) body = data;
        ArrayList<JSONArray> arrays = new ArrayList<>();
        addArray(arrays, body.optJSONArray("item_song"));
        addArray(arrays, body.optJSONArray("item_lyric"));
        addArray(arrays, body.optJSONArray("list"));
        addNestedList(arrays, body.optJSONObject("lyric"));
        addNestedList(arrays, body.optJSONObject("song"));
        addNestedList(arrays, data.optJSONObject("lyric"));
        addNestedList(arrays, data.optJSONObject("song"));

        int total = 0;
        for (JSONArray array : arrays) total = Math.max(total, array == null ? 0 : array.length());
        total = firstPositive(
                body.optInt("sum", 0), body.optInt("total", 0), data.optInt("sum", 0), data.optInt("total", 0), total);
        lastTotal = total;

        LinkedHashMap<String,Hit> dedup = new LinkedHashMap<>();
        for (JSONArray array : arrays) {
            if (array == null) continue;
            for (int i = 0; i < array.length(); i++) {
                JSONObject raw = array.optJSONObject(i);
                if (raw == null) continue;
                JSONObject songObject = unwrapSong(raw);
                Song song = parseSong(songObject == null ? raw : songObject);
                if (song == null) continue;
                String snippet = findSnippet(raw, keyword);
                if (snippet.isEmpty() && songObject != raw) snippet = findSnippet(songObject, keyword);
                dedup.putIfAbsent(song.key(), new Hit(song, snippet));
            }
        }
        out.addAll(dedup.values());
        return out;
    }

    private static void addArray(List<JSONArray> arrays, JSONArray value) { if (value != null) arrays.add(value); }
    private static void addNestedList(List<JSONArray> arrays, JSONObject block) {
        if (block == null) return;
        addArray(arrays, block.optJSONArray("list"));
        addArray(arrays, block.optJSONArray("item_song"));
    }

    private JSONObject unwrapSong(JSONObject raw) {
        if (raw == null) return null;
        String[] keys = {"song", "music", "track", "songInfo", "song_info"};
        for (String key : keys) {
            JSONObject nested = raw.optJSONObject(key);
            if (nested != null) return nested;
        }
        return raw;
    }

    private Song parseSong(JSONObject o) {
        if (o == null) return null;
        String mid = firstNonEmpty(o.optString("mid", ""), o.optString("songmid", ""));
        if (mid.isEmpty()) return null;
        String title = firstNonEmpty(o.optString("title", ""), o.optString("songname", ""), o.optString("name", ""), "未知歌曲");
        String artist = singerNames(o.optJSONArray("singer"));
        if ("未知歌手".equals(artist)) artist = firstNonEmpty(o.optString("singername", ""), o.optString("artist", ""), artist);
        JSONObject album = o.optJSONObject("album");
        String albumName = firstNonEmpty(o.optString("albumname", ""), album == null ? "" : album.optString("name", ""));
        String albumMid = firstNonEmpty(o.optString("albummid", ""), album == null ? "" : album.optString("mid", ""));
        String cover = albumMid.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + albumMid + ".jpg";
        long duration = o.optLong("interval", 0L) * 1000L;
        if (duration <= 0L) duration = o.optLong("duration", 0L);
        if (duration > 0L && duration < 2000L) duration *= 1000L;
        List<SourceVariant> variants = new ArrayList<>();
        variants.add(new SourceVariant("tx", mid, true, true, o));
        return new Song(title, artist, albumName, cover, duration, variants);
    }

    private String findSnippet(JSONObject item, String keyword) {
        if (item == null) return "";
        String[] keys = {"content", "content_hilight", "desc", "desc_hilight", "lyric", "lyrics", "lyricText", "matchLyric", "matchLyrics", "text"};
        for (String key : keys) {
            String text = flattenText(item.opt(key));
            if (!text.isEmpty()) {
                String snippet = aroundKeyword(text, keyword);
                if (!snippet.isEmpty()) return snippet;
            }
        }
        return "";
    }

    private String flattenText(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String) return cleanText((String) value);
        if (value instanceof JSONArray) {
            StringBuilder b = new StringBuilder();
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < Math.min(8, array.length()); i++) {
                String text = flattenText(array.opt(i));
                if (!text.isEmpty()) { if (b.length() > 0) b.append(' '); b.append(text); }
            }
            return b.toString();
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String[] keys = {"text", "content", "lyric", "lyrics", "value", "name"};
            StringBuilder b = new StringBuilder();
            for (String key : keys) {
                String text = flattenText(object.opt(key));
                if (!text.isEmpty()) { if (b.length() > 0) b.append(' '); b.append(text); }
            }
            return b.toString();
        }
        return "";
    }

    private String aroundKeyword(String raw, String keyword) {
        String text = cleanText(raw);
        if (text.isEmpty()) return "";
        String needle = normalize(keyword);
        if (needle.isEmpty()) return text.length() <= 70 ? text : text.substring(0, 70) + "…";
        int[] mapped = normalizedIndexMap(text);
        String normalized = normalize(text);
        int at = normalized.indexOf(needle);
        if (at < 0) return text.length() <= 70 ? text : text.substring(0, 70) + "…";
        int startNormalized = Math.max(0, at - 10);
        int endNormalized = Math.min(normalized.length(), at + needle.length() + 18);
        int start = mapped.length == 0 ? 0 : mapped[Math.min(startNormalized, mapped.length - 1)];
        int end = mapped.length == 0 ? text.length() : Math.min(text.length(), mapped[Math.min(endNormalized - 1, mapped.length - 1)] + 1);
        return (start > 0 ? "…" : "") + text.substring(start, end).trim() + (end < text.length() ? "…" : "");
    }

    private int[] normalizedIndexMap(String text) {
        ArrayList<Integer> indexes = new ArrayList<>();
        if (text != null) for (int i = 0; i < text.length(); i++) {
            String n = normalize(String.valueOf(text.charAt(i)));
            for (int j = 0; j < n.length(); j++) indexes.add(i);
        }
        int[] out = new int[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) out[i] = indexes.get(i);
        return out;
    }

    private String cleanText(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replaceAll("\\[[0-9:.]+\\]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String normalize(String raw) {
        return (raw == null ? "" : raw.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }

    private Map<String,String> webHeaders() {
        Map<String,String> h = new LinkedHashMap<>();
        h.put("User-Agent", WEB_UA); h.put("Referer", "https://y.qq.com/"); h.put("Origin", "https://y.qq.com");
        h.put("Accept", "application/json,text/plain,*/*"); h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        return h;
    }

    private static String zzcSign(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hashBuilder = new StringBuilder(40);
        for (byte b : digest) hashBuilder.append(String.format("%02x", b & 0xff));
        String hash = hashBuilder.toString();
        StringBuilder p1 = new StringBuilder(); StringBuilder p2 = new StringBuilder();
        for (int idx : PART_1) if (idx >= 0 && idx < hash.length()) p1.append(hash.charAt(idx));
        for (int idx : PART_2) if (idx >= 0 && idx < hash.length()) p2.append(hash.charAt(idx));
        byte[] scrambled = new byte[SCRAMBLE.length];
        for (int i = 0; i < SCRAMBLE.length; i++) scrambled[i] = (byte) (SCRAMBLE[i] ^ Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16));
        String b64 = Base64.getEncoder().encodeToString(scrambled).replace("/", "").replace("\\", "").replace("+", "").replace("=", "");
        return ("zzc" + p1 + b64 + p2).toLowerCase(Locale.ROOT);
    }

    private static JSONObject parseJsonObject(String raw) throws Exception {
        String t = raw == null ? "" : raw.trim();
        if (t.startsWith("\uFEFF")) t = t.substring(1).trim();
        if (t.startsWith("{")) return new JSONObject(t);
        int first = t.indexOf('{'); int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) return new JSONObject(t.substring(first, last + 1));
        throw new Exception("QQ歌词索引响应不是 JSON");
    }

    private static String singerNames(JSONArray singers) {
        StringBuilder b = new StringBuilder();
        if (singers != null) for (int i = 0; i < singers.length(); i++) {
            JSONObject s = singers.optJSONObject(i); if (s == null) continue;
            String name = s.optString("name", "").trim(); if (name.isEmpty()) continue;
            if (b.length() > 0) b.append(" / "); b.append(name);
        }
        return b.length() == 0 ? "未知歌手" : b.toString();
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static int firstPositive(int... values) {
        if (values != null) for (int value : values) if (value > 0) return value;
        return 0;
    }
}
