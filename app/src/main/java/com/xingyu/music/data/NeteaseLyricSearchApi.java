package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search-only NetEase lyric matcher.
 *
 * This class is intentionally separate from NeteaseApi so adding lyric discovery cannot alter
 * the locked metadata/audio playback path. Normal title/artist search renders first; callers may
 * run this adapter independently and append its hits later.
 */
public final class NeteaseLyricSearchApi {
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36";
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
            return searchWeapi(q, limit);
        } catch (Exception e) {
            first = e;
        }
        try {
            return searchLegacy(q, limit);
        } catch (Exception second) {
            if (first != null) throw first;
            throw second;
        }
    }

    private List<Hit> searchWeapi(String keyword, int limit) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("s", keyword);
        payload.put("type", "1006"); // NetEase lyric search
        payload.put("limit", String.valueOf(Math.max(1, Math.min(limit, 30))));
        payload.put("offset", "0");
        payload.put("total", "true");
        payload.put("csrf_token", "");
        String[] enc = NeteaseCrypto.weapi(payload.toString());
        Map<String,String> form = new LinkedHashMap<>();
        form.put("params", enc[0]);
        form.put("encSecKey", enc[1]);
        Http.Response r = Http.postForm("https://music.163.com/weapi/cloudsearch/get/web", headers(), form);
        if (r.code < 200 || r.code >= 300) throw new Exception("歌词搜索 HTTP " + r.code);
        return parse(new JSONObject(r.body), keyword);
    }

    private List<Hit> searchLegacy(String keyword, int limit) throws Exception {
        Map<String,String> form = new LinkedHashMap<>();
        form.put("s", keyword);
        form.put("type", "1006");
        form.put("offset", "0");
        form.put("total", "true");
        form.put("limit", String.valueOf(Math.max(1, Math.min(limit, 30))));
        Http.Response r = Http.postForm("https://music.163.com/api/search/get/", headers(), form);
        if (r.code < 200 || r.code >= 300) throw new Exception("歌词搜索 HTTP " + r.code);
        return parse(new JSONObject(r.body), keyword);
    }

    private List<Hit> parse(JSONObject root, String keyword) {
        ArrayList<Hit> out = new ArrayList<>();
        JSONObject result = root == null ? null : root.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        lastTotal = Math.max(result == null ? 0 : result.optInt("songCount", 0), songs == null ? 0 : songs.length());
        if (songs == null) return out;
        for (int i = 0; i < songs.length(); i++) {
            JSONObject item = songs.optJSONObject(i);
            Song song = parseSong(item);
            if (song == null) continue;
            out.add(new Hit(song, findSnippet(item, keyword)));
        }
        return out;
    }

    private Song parseSong(JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id");
        if (id <= 0L) return null;
        JSONArray artists = o.optJSONArray("ar");
        if (artists == null) artists = o.optJSONArray("artists");
        StringBuilder artist = new StringBuilder();
        if (artists != null) {
            for (int i = 0; i < artists.length(); i++) {
                JSONObject a = artists.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "").trim();
                if (name.isEmpty()) continue;
                if (artist.length() > 0) artist.append(" / ");
                artist.append(name);
            }
        }
        JSONObject album = o.optJSONObject("al");
        if (album == null) album = o.optJSONObject("album");
        long duration = o.has("dt") ? o.optLong("dt") : o.optLong("duration");
        List<SourceVariant> variants = new ArrayList<>();
        // Search-only results sometimes omit quality blocks. Keep both qualities eligible and let
        // the already-locked playback verifier/resolver make the real decision later.
        variants.add(new SourceVariant("wy", String.valueOf(id), true, true, o));
        return new Song(
                o.optString("name", "未知歌曲"),
                artist.length() == 0 ? "未知歌手" : artist.toString(),
                album == null ? "" : album.optString("name", ""),
                album == null ? "" : album.optString("picUrl", ""),
                duration,
                variants);
    }

    private String findSnippet(JSONObject item, String keyword) {
        if (item == null) return "";
        String[] keys = {"lyrics", "lyric", "lyricText", "matchLyric", "matchLyrics", "content", "text"};
        for (String key : keys) {
            Object value = item.opt(key);
            String found = flattenText(value);
            if (!found.isEmpty()) {
                String snip = aroundKeyword(found, keyword);
                if (!snip.isEmpty()) return snip;
            }
        }
        return "";
    }

    private String flattenText(Object value) {
        if (value == null || value == JSONObject.NULL) return "";
        if (value instanceof String) return cleanText((String) value);
        if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < Math.min(6, a.length()); i++) {
                String x = flattenText(a.opt(i));
                if (!x.isEmpty()) { if (b.length() > 0) b.append(' '); b.append(x); }
            }
            return b.toString();
        }
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String[] keys = {"text", "content", "lyric", "lyrics", "value", "name"};
            StringBuilder b = new StringBuilder();
            for (String key : keys) {
                String x = flattenText(o.opt(key));
                if (!x.isEmpty()) { if (b.length() > 0) b.append(' '); b.append(x); }
            }
            return b.toString();
        }
        return "";
    }

    private String aroundKeyword(String text, String keyword) {
        String clean = cleanText(text);
        if (clean.isEmpty()) return "";
        String lower = clean.toLowerCase(Locale.ROOT);
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int at = needle.isEmpty() ? -1 : lower.indexOf(needle);
        if (at < 0) return clean.length() <= 48 ? clean : clean.substring(0, 48) + "…";
        int start = Math.max(0, at - 14);
        int end = Math.min(clean.length(), at + needle.length() + 22);
        String out = clean.substring(start, end).trim();
        return (start > 0 ? "…" : "") + out + (end < clean.length() ? "…" : "");
    }

    private String cleanText(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\[[0-9:.]+\\]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Map<String,String> headers() {
        Map<String,String> h = new LinkedHashMap<>();
        h.put("User-Agent", UA);
        h.put("Referer", "https://music.163.com/");
        h.put("Origin", "https://music.163.com");
        h.put("Cookie", "os=pc; appver=2.10.13; channel=netease;");
        h.put("Accept", "application/json,text/plain,*/*");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        return h;
    }
}
