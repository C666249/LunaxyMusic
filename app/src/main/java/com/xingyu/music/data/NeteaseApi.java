package com.xingyu.music.data;

import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadata adapter for NetEase Cloud Music. */
public final class NeteaseApi {
    private volatile int lastSearchTotal;
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36";

    public int lastSearchTotal() { return lastSearchTotal; }

    public List<Song> search(String keyword, int limit) throws Exception {
        lastSearchTotal = 0;
        if (keyword == null || keyword.trim().isEmpty()) return new ArrayList<>();
        String q = keyword.trim();
        List<String> errors = new ArrayList<>();

        // Current LX Music Mobile route (2026): eapi /api/search/song/list/page.
        for (int i = 0; i < 1; i++) {
            try {
                List<Song> result = searchEapi(q, limit);
                if (!result.isEmpty()) return result;
            } catch (Exception e) { errors.add("eapi=" + message(e)); }
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("搜索已取消");
        try {
            List<Song> result = searchWeapi(q, limit);
            if (!result.isEmpty()) return result;
        } catch (Exception e) { errors.add("weapi=" + message(e)); }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("搜索已取消");
        try {
            List<Song> result = searchLegacy(q, limit);
            if (!result.isEmpty()) return result;
        } catch (Exception e) { errors.add("api=" + message(e)); }
        if (!errors.isEmpty()) throw new Exception(join(errors));
        return new ArrayList<>();
    }

    private List<Song> searchEapi(String keyword, int limit) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("keyword", keyword);
        payload.put("needCorrect", "1");
        payload.put("channel", "typing");
        payload.put("offset", 0);
        payload.put("scene", "normal");
        payload.put("total", true);
        payload.put("limit", Math.max(1, Math.min(limit, 50)));
        String route = "/api/search/song/list/page";
        Map<String,String> form = new LinkedHashMap<>();
        form.put("params", NeteaseCrypto.eapi(route, payload.toString()));
        Map<String,String> h = headers();
        h.put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/60 Safari/537.36");
        Http.Response r;
        try {
            r = Http.postForm("https://interface.music.163.com/eapi/batch", h, form);
            if (r.code < 200 || r.code >= 300) throw new Exception("HTTPS " + r.code);
        } catch (Exception first) {
            // LX Music Mobile currently calls this endpoint over HTTP. Some networks
            // accept HTTPS while others only behave correctly on the original route.
            r = Http.postForm("http://interface.music.163.com/eapi/batch", h, form);
            if (r.code < 200 || r.code >= 300) throw new Exception("HTTP " + r.code);
        }
        JSONObject root = new JSONObject(r.body);
        if (root.optInt("code", -1) != 200) throw new Exception("code=" + root.optInt("code", -1));
        JSONObject data = root.optJSONObject("data");
        JSONArray resources = data == null ? null : data.optJSONArray("resources");
        List<Song> out = new ArrayList<>();
        if (resources == null) return out;
        lastSearchTotal = firstPositive(
                data == null ? 0 : data.optInt("totalCount", 0),
                data == null ? 0 : data.optInt("total", 0),
                resources.length());
        for (int i = 0; i < resources.length(); i++) {
            JSONObject item = resources.optJSONObject(i);
            JSONObject base = item == null ? null : item.optJSONObject("baseInfo");
            JSONObject simple = base == null ? null : base.optJSONObject("simpleSongData");
            Song song = parseSong(simple);
            if (song != null) out.add(song);
        }
        return out;
    }

    private List<Song> searchWeapi(String keyword, int limit) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("s", keyword);
        payload.put("type", "1");
        payload.put("limit", String.valueOf(Math.max(1, Math.min(limit, 50))));
        payload.put("offset", "0");
        payload.put("total", "true");
        payload.put("csrf_token", "");
        JSONObject root = new JSONObject(weapiPost("/weapi/cloudsearch/get/web", payload.toString()));
        JSONObject result = root.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        lastSearchTotal = firstPositive(result == null ? 0 : result.optInt("songCount", 0), songs == null ? 0 : songs.length());
        return parseSongs(songs);
    }

    private List<Song> searchLegacy(String keyword, int limit) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("s", keyword);
        form.put("type", "1");
        form.put("offset", "0");
        form.put("total", "true");
        form.put("limit", String.valueOf(Math.max(1, Math.min(limit, 50))));
        Http.Response r = Http.postForm("https://music.163.com/api/search/get/", headers(), form);
        if (r.code < 200 || r.code >= 300) throw new Exception("HTTP " + r.code);
        JSONObject root = new JSONObject(r.body);
        JSONObject result = root.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        lastSearchTotal = firstPositive(result == null ? 0 : result.optInt("songCount", 0), songs == null ? 0 : songs.length());
        return parseSongs(songs);
    }

    public ImportedPlaylist playlist(String id) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("id", id);
        payload.put("n", "100000");
        payload.put("s", "8");
        payload.put("csrf_token", "");
        JSONObject root = new JSONObject(weapiPost("/weapi/v6/playlist/detail", payload.toString()));
        JSONObject p = root.optJSONObject("playlist");
        if (p == null) throw new Exception("网易云歌单读取失败");
        List<Song> direct = parseSongs(p.optJSONArray("tracks"));
        JSONArray ids = p.optJSONArray("trackIds");
        if (ids != null && ids.length() > direct.size()) {
            List<String> allIds = new ArrayList<>();
            for (int i = 0; i < ids.length(); i++) {
                JSONObject o = ids.optJSONObject(i);
                if (o != null && o.optLong("id") > 0) allIds.add(String.valueOf(o.optLong("id")));
            }
            List<Song> details = songDetails(allIds);
            if (!details.isEmpty()) direct = details;
        }
        return new ImportedPlaylist("wy:" + id, p.optString("name", "网易云歌单"), "wy", direct);
    }

    public static final class LyricData {
        public final String lrc;
        public final String yrc;
        public LyricData(String lrc, String yrc) {
            this.lrc = lrc == null ? "" : lrc;
            this.yrc = yrc == null ? "" : yrc;
        }
        public boolean hasWordTiming() { return !yrc.trim().isEmpty(); }
    }

    /**
     * V18 lyric-only metadata path. It is completely separate from audio URL routing.
     * Prefer the mobile lyric/v1 EAPI because it can include YRC word timestamps, then
     * fall back to the long-proven public LRC endpoint when the mobile endpoint rejects.
     */
    public LyricData lyricData(String id) throws Exception {
        Exception firstError = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("id", id);
            payload.put("cp", false);
            payload.put("tv", 0);
            payload.put("lv", 0);
            payload.put("rv", 0);
            payload.put("kv", 0);
            payload.put("yv", 0);
            payload.put("ytv", 0);
            payload.put("yrv", 0);
            String route = "/api/song/lyric/v1";
            Map<String,String> form = new LinkedHashMap<>();
            form.put("params", NeteaseCrypto.eapi(route, payload.toString()));
            Map<String,String> h = headers();
            h.put("User-Agent", "NeteaseMusic/9.3.60 (Linux; Android 14; zh_CN)");
            h.put("Cookie", "os=android; appver=9.3.60; channel=netease;");
            Http.Response r = Http.postForm("https://interface.music.163.com/eapi/song/lyric/v1", h, form);
            if (r.code >= 200 && r.code < 300) {
                JSONObject root = new JSONObject(r.body);
                String lrc = lyricField(root, "lrc");
                String yrc = lyricField(root, "yrc");
                if (!lrc.isEmpty() || !yrc.isEmpty()) return new LyricData(lrc, yrc);
            }
        } catch (Exception e) { firstError = e; }

        String url = "https://music.163.com/api/song/lyric?os=pc&id="
                + URLEncoder.encode(id, StandardCharsets.UTF_8.name()) + "&lv=-1&kv=-1&tv=-1";
        Http.Response r = Http.get(url, headers());
        if (r.code < 200 || r.code >= 300) {
            if (firstError != null) throw firstError;
            return new LyricData("", "");
        }
        JSONObject root = new JSONObject(r.body);
        return new LyricData(lyricField(root, "lrc"), lyricField(root, "yrc"));
    }

    public String lyric(String id) throws Exception { return lyricData(id).lrc; }

    private static String lyricField(JSONObject root, String key) {
        JSONObject block = root == null ? null : root.optJSONObject(key);
        return block == null ? "" : block.optString("lyric", "");
    }

    private List<Song> songDetails(List<String> ids) throws Exception {
        List<Song> out = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += 200) {
            int end = Math.min(start + 200, ids.size());
            JSONArray idArray = new JSONArray();
            JSONArray c = new JSONArray();
            for (int i = start; i < end; i++) {
                idArray.put(ids.get(i));
                JSONObject item = new JSONObject();
                item.put("id", ids.get(i));
                c.put(item);
            }
            JSONObject payload = new JSONObject();
            payload.put("ids", idArray);
            payload.put("c", c.toString());
            payload.put("csrf_token", "");
            JSONObject root = new JSONObject(weapiPost("/weapi/v3/song/detail", payload.toString()));
            out.addAll(parseSongs(root.optJSONArray("songs")));
        }
        return out;
    }

    private String weapiPost(String path, String payload) throws Exception {
        String[] enc = NeteaseCrypto.weapi(payload);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("params", enc[0]);
        form.put("encSecKey", enc[1]);
        Http.Response r = Http.postForm("https://music.163.com" + path, headers(), form);
        if (r.code < 200 || r.code >= 300) throw new Exception("网易云 HTTP " + r.code);
        return r.body;
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", UA);
        h.put("Referer", "https://music.163.com/");
        h.put("Origin", "https://music.163.com");
        h.put("Cookie", "os=pc; appver=2.10.13; channel=netease;");
        h.put("Accept", "application/json,text/plain,*/*");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        return h;
    }

    private List<Song> parseSongs(JSONArray a) {
        List<Song> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            Song s = parseSong(a.optJSONObject(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    private Song parseSong(JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id");
        if (id <= 0) return null;
        JSONArray artists = o.optJSONArray("ar");
        if (artists == null) artists = o.optJSONArray("artists");
        StringBuilder ar = new StringBuilder();
        if (artists != null) for (int j = 0; j < artists.length(); j++) {
            JSONObject ao = artists.optJSONObject(j);
            if (ao == null) continue;
            String n = ao.optString("name", "").trim();
            if (!n.isEmpty()) {
                if (ar.length() > 0) ar.append(" / ");
                ar.append(n);
            }
        }
        JSONObject album = o.optJSONObject("al");
        if (album == null) album = o.optJSONObject("album");
        String cover = album == null ? "" : album.optString("picUrl", "");
        long duration = o.has("dt") ? o.optLong("dt") : o.optLong("duration");
        boolean q128 = qualityPresent(o, "l") || qualityPresent(o, "m") || qualityPresent(o, "h") || qualityPresent(o, "sq") || qualityPresent(o, "hr");
        boolean q320 = qualityPresent(o, "h") || qualityPresent(o, "sq") || qualityPresent(o, "hr");
        // Some search endpoints omit quality objects. Unknown means "do not over-filter".
        if (!o.has("l") && !o.has("m") && !o.has("h") && !o.has("sq") && !o.has("hr")) { q128 = true; q320 = true; }
        List<SourceVariant> variants = new ArrayList<>();
        variants.add(new SourceVariant("wy", String.valueOf(id), q128, q320, o));
        return new Song(o.optString("name", "未知歌曲"),
                ar.length() == 0 ? "未知歌手" : ar.toString(),
                album == null ? "" : album.optString("name", ""), cover, duration, variants);
    }

    private static boolean qualityPresent(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return false;
        JSONObject q = o.optJSONObject(key);
        if (q == null) return true;
        return q.optLong("size", q.optLong("br", 1L)) > 0L;
    }

    private static int firstPositive(int... values) {
        if (values != null) for (int v : values) if (v > 0) return v;
        return 0;
    }

    private static String message(Exception e) { return e == null || e.getMessage() == null ? "unknown" : e.getMessage(); }
    private static String join(List<String> a) { StringBuilder b = new StringBuilder(); for (String s : a) { if (b.length() > 0) b.append("; "); b.append(s); } return b.toString(); }
}
