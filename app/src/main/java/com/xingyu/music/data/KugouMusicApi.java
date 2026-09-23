package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Search-only Kugou catalog adapter, using the same public search shape LX consumes. */
public final class KugouMusicApi {
    private volatile int lastSearchTotal;
    public int lastSearchTotal() { return lastSearchTotal; }

    public List<Song> search(String keyword, int limit) throws Exception {
        Page p = searchPage(keyword, 1, limit);
        lastSearchTotal = p.total;
        return p.songs;
    }

    public Page searchPage(String keyword, int page, int limit) throws Exception {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) return new Page(new ArrayList<>(), 0);
        int p = Math.max(1, page);
        int n = Math.max(1, Math.min(limit, 50));
        String url = "https://songsearch.kugou.com/song_search_v2?keyword="
                + URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                + "&page=" + p + "&pagesize=" + n
                + "&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1";
        Http.Response r = Http.get(url, headers());
        if (r.code < 200 || r.code >= 300) throw new Exception("酷狗 HTTP " + r.code);
        JSONObject root = new JSONObject(r.body);
        if (root.optInt("error_code", -1) != 0) throw new Exception("酷狗 code=" + root.optInt("error_code", -1));
        JSONObject data = root.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("lists");
        List<Song> songs = parse(list);
        int total = data == null ? songs.size() : Math.max(songs.size(), data.optInt("total", songs.size()));
        lastSearchTotal = total;
        return new Page(songs, total);
    }

    public static final class Page {
        public final List<Song> songs; public final int total;
        Page(List<Song> songs, int total) { this.songs = songs; this.total = Math.max(0, total); }
    }

    private List<Song> parse(JSONArray list) {
        List<Song> out = new ArrayList<>();
        if (list == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            add(item, out, seen);
            JSONArray group = item == null ? null : item.optJSONArray("Grp");
            if (group != null) for (int j = 0; j < group.length(); j++) add(group.optJSONObject(j), out, seen);
        }
        return out;
    }

    private void add(JSONObject o, List<Song> out, Set<String> seen) {
        if (o == null) return;
        String audioId = String.valueOf(o.optLong("Audioid", o.optLong("AudioID", 0L)));
        String fileHash = firstNonEmpty(o.optString("FileHash", ""), o.optString("Hash", ""));
        if (("0".equals(audioId) || audioId.isEmpty()) && fileHash.isEmpty()) return;
        String unique = audioId + "|" + fileHash;
        if (!seen.add(unique)) return;
        String title = decode(firstNonEmpty(o.optString("SongName", ""), o.optString("SongNameOri", ""), "未知歌曲"));
        String artist = parseSingers(o.optJSONArray("Singers"), o.optString("SingerName", ""));
        String album = decode(o.optString("AlbumName", ""));
        long duration = o.optLong("Duration", 0L) * 1000L;
        boolean q128 = o.optLong("FileSize", 0L) > 0L || !fileHash.isEmpty();
        boolean q320 = o.optLong("HQFileSize", 0L) > 0L || o.optLong("SQFileSize", 0L) > 0L || o.optLong("ResFileSize", 0L) > 0L;
        if (!q128 && !q320) { q128 = true; q320 = true; }

        JSONArray types = new JSONArray();
        JSONObject typeMap = new JSONObject();
        addType(types, typeMap, "128k", o.optLong("FileSize", 0L), fileHash);
        addType(types, typeMap, "320k", o.optLong("HQFileSize", 0L), o.optString("HQFileHash", ""));
        addType(types, typeMap, "flac", o.optLong("SQFileSize", 0L), o.optString("SQFileHash", ""));
        addType(types, typeMap, "flac24bit", o.optLong("ResFileSize", 0L), o.optString("ResFileHash", ""));

        JSONObject lx = new JSONObject();
        try {
            lx.put("name", title); lx.put("singer", artist); lx.put("source", "kg");
            lx.put("songmid", audioId); lx.put("songId", audioId); lx.put("audioId", audioId);
            lx.put("albumName", album); lx.put("albumId", o.optLong("AlbumID", 0L));
            lx.put("interval", formatDuration(duration)); lx.put("_interval", Math.max(0L, duration / 1000L));
            lx.put("hash", fileHash); lx.put("types", types); lx.put("_types", typeMap); lx.put("typeUrl", new JSONObject());
        } catch (Exception ignored) { }
        List<SourceVariant> variants = new ArrayList<>();
        variants.add(new SourceVariant("kg", audioId, q128, q320, lx));
        out.add(new Song(title, artist, album, "", duration, variants));
    }

    private static void addType(JSONArray types, JSONObject map, String type, long size, String hash) {
        if (size <= 0 && (hash == null || hash.isEmpty())) return;
        try {
            JSONObject t = new JSONObject(); t.put("type", type); t.put("size", String.valueOf(size)); if (hash != null && !hash.isEmpty()) t.put("hash", hash); types.put(t);
            JSONObject m = new JSONObject(); m.put("size", String.valueOf(size)); if (hash != null && !hash.isEmpty()) m.put("hash", hash); map.put(type, m);
        } catch (Exception ignored) { }
    }

    private static String parseSingers(JSONArray singers, String fallback) {
        StringBuilder b = new StringBuilder();
        if (singers != null) for (int i = 0; i < singers.length(); i++) {
            JSONObject s = singers.optJSONObject(i); if (s == null) continue;
            String name = decode(firstNonEmpty(s.optString("name", ""), s.optString("Name", "")));
            if (name.isEmpty()) continue; if (b.length() > 0) b.append(" / "); b.append(name);
        }
        if (b.length() == 0) return decode(fallback).replace("、", " / ");
        return b.toString();
    }
    private static Map<String,String> headers() { Map<String,String> h = new LinkedHashMap<>(); h.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36"); h.put("Referer", "https://www.kugou.com/"); h.put("Accept", "application/json,text/plain,*/*"); return h; }
    private static String decode(String s) { return s == null ? "" : s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim(); }
    private static String formatDuration(long ms) { long sec = Math.max(0, ms / 1000L); return String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60L, sec % 60L); }
    private static String firstNonEmpty(String... a) { for (String s : a) if (s != null && !s.trim().isEmpty()) return s.trim(); return ""; }
}
