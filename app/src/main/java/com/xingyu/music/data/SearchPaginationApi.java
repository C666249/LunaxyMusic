package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search-only pagination helper for V45.
 *
 * This class is deliberately separate from NeteaseApi/QQMusicApi so the locked playback +
 * scheduling objects (and the already-proven first-page metadata adapters) remain byte-identical.
 * It is invoked only by the dedicated search-results list when it approaches the bottom. It never resolves audio URLs, never touches
 * SourceHealthStore, and never reads/writes PlaybackUrlStore.
 */
public final class SearchPaginationApi {
    private final KuwoMusicApi kuwoApi = new KuwoMusicApi();
    private final KugouMusicApi kugouApi = new KugouMusicApi();
    private static final String WEB_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36";
    private static final String QQ_APP_UA = "QQMusic 14090508(android 12)";
    private static final int[] PART_1 = {23, 14, 6, 36, 16, 40, 7, 19};
    private static final int[] PART_2 = {16, 1, 32, 12, 19, 27, 8, 5};
    private static final int[] SCRAMBLE = {89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179};

    public static final class Page {
        public final List<Song> songs;
        public final int total;
        public final String error;

        public Page(List<Song> songs, int total, String error) {
            this.songs = songs == null ? new ArrayList<>() : songs;
            this.total = Math.max(0, total);
            this.error = error == null ? "" : error;
        }
    }

    public Page kuwo(String keyword, int page, int limit) {
        try { KuwoMusicApi.Page p = kuwoApi.searchPage(keyword, page, limit); return new Page(p.songs, p.total, ""); }
        catch (Exception e) { return new Page(new ArrayList<>(), 0, message(e)); }
    }

    public Page kugou(String keyword, int page, int limit) {
        try { KugouMusicApi.Page p = kugouApi.searchPage(keyword, page, limit); return new Page(p.songs, p.total, ""); }
        catch (Exception e) { return new Page(new ArrayList<>(), 0, message(e)); }
    }

    public Page netease(String keyword, int offset, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) return new Page(new ArrayList<>(), 0, "");
        int n = Math.max(1, Math.min(limit, 50));
        int start = Math.max(0, offset);
        String firstError = "";
        try {
            return neteaseEapi(q, start, n);
        } catch (Exception e) {
            firstError = message(e);
        }
        try {
            return neteaseLegacy(q, start, n);
        } catch (Exception e) {
            String second = message(e);
            return new Page(new ArrayList<>(), 0, firstError.isEmpty() ? second : firstError + "; fallback=" + second);
        }
    }

    private Page neteaseEapi(String keyword, int offset, int limit) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("keyword", keyword);
        payload.put("needCorrect", "1");
        payload.put("channel", "typing");
        payload.put("offset", offset);
        payload.put("scene", "normal");
        payload.put("total", true);
        payload.put("limit", limit);
        String route = "/api/search/song/list/page";
        Map<String, String> form = new LinkedHashMap<>();
        form.put("params", NeteaseCrypto.eapi(route, payload.toString()));
        Map<String, String> headers = neteaseHeaders();
        headers.put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/60 Safari/537.36");

        Http.Response r;
        try {
            r = Http.postForm("https://interface.music.163.com/eapi/batch", headers, form);
            if (r.code < 200 || r.code >= 300) throw new Exception("HTTPS " + r.code);
        } catch (Exception first) {
            r = Http.postForm("http://interface.music.163.com/eapi/batch", headers, form);
            if (r.code < 200 || r.code >= 300) throw new Exception("HTTP " + r.code);
        }

        JSONObject root = new JSONObject(r.body);
        if (root.optInt("code", -1) != 200) throw new Exception("code=" + root.optInt("code", -1));
        JSONObject data = root.optJSONObject("data");
        JSONArray resources = data == null ? null : data.optJSONArray("resources");
        int total = firstPositive(
                data == null ? 0 : data.optInt("totalCount", 0),
                data == null ? 0 : data.optInt("total", 0),
                resources == null ? 0 : offset + resources.length());
        return new Page(parseNeteaseResources(resources), total, "");
    }

    private Page neteaseLegacy(String keyword, int offset, int limit) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("s", keyword);
        form.put("type", "1");
        form.put("offset", String.valueOf(offset));
        form.put("total", "true");
        form.put("limit", String.valueOf(limit));
        Http.Response r = Http.postForm("https://music.163.com/api/search/get/", neteaseHeaders(), form);
        if (r.code < 200 || r.code >= 300) throw new Exception("HTTP " + r.code);
        JSONObject root = new JSONObject(r.body);
        JSONObject result = root.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        int total = firstPositive(
                result == null ? 0 : result.optInt("songCount", 0),
                songs == null ? 0 : offset + songs.length());
        return new Page(parseNeteaseSongs(songs), total, "");
    }

    public Page qq(String keyword, int page, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) return new Page(new ArrayList<>(), 0, "");
        int n = Math.max(1, Math.min(limit, 50));
        int p = Math.max(1, page);
        String firstError = "";
        try {
            return qqSignedMobile(q, p, n);
        } catch (Exception e) {
            firstError = message(e);
        }
        try {
            return qqLegacy(q, p, n);
        } catch (Exception e) {
            String second = message(e);
            return new Page(new ArrayList<>(), 0, firstError.isEmpty() ? second : firstError + "; fallback=" + second);
        }
    }

    private Page qqSignedMobile(String keyword, int page, int limit) throws Exception {
        JSONObject comm = new JSONObject();
        comm.put("ct", "11");
        comm.put("cv", "14090508");
        comm.put("v", "14090508");
        comm.put("tmeAppID", "qqmusic");
        comm.put("phonetype", "EBG-AN10");
        comm.put("deviceScore", "553.47");
        comm.put("devicelevel", "50");
        comm.put("newdevicelevel", "20");
        comm.put("rom", "HuaWei/EMOTION/EmotionUI_14.2.0");
        comm.put("os_ver", "12");
        String[] zeroKeys = {"OpenUDID", "OpenUDID2", "QIMEI36", "udid", "chid", "aid", "oaid", "taid", "tid", "wid", "uid", "sid"};
        for (String key : zeroKeys) comm.put(key, "0");
        comm.put("modeSwitch", "6");
        comm.put("teenMode", "0");
        comm.put("ui_mode", "2");
        comm.put("nettype", "1020");
        comm.put("v4ip", "");

        JSONObject param = new JSONObject();
        param.put("search_type", 0);
        param.put("searchid", String.valueOf(new java.util.Random().nextLong() & Long.MAX_VALUE));
        param.put("query", keyword);
        param.put("page_num", page);
        param.put("num_per_page", limit);
        param.put("highlight", 0);
        param.put("nqc_flag", 0);
        param.put("multi_zhida", 0);
        param.put("cat", 2);
        param.put("grp", 1);
        param.put("sin", Math.max(0, (page - 1) * limit));
        param.put("sem", 0);

        JSONObject req = new JSONObject();
        req.put("module", "music.search.SearchCgiService");
        req.put("method", "DoSearchForQQMusicMobile");
        req.put("param", param);

        JSONObject payload = new JSONObject();
        payload.put("comm", comm);
        payload.put("req", req);
        String raw = payload.toString();
        String sign = zzcSign(raw);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", QQ_APP_UA);
        headers.put("Accept", "application/json,text/plain,*/*");
        Http.Response response = Http.postJson("https://u.y.qq.com/cgi-bin/musics.fcg?sign=" + sign, headers, raw);
        if (response.code < 200 || response.code >= 300) throw new Exception("HTTP " + response.code);
        JSONObject root = new JSONObject(response.body);
        if (root.optInt("code", -1) != 0) throw new Exception("root code=" + root.optInt("code", -1));
        JSONObject reqBody = root.optJSONObject("req");
        if (reqBody == null) throw new Exception("缺少 req");
        int reqCode = reqBody.optInt("code", -1);
        if (reqCode != 0) throw new Exception(reqCode == 2001 ? "QQ 风控要求登录" : "req code=" + reqCode);
        JSONObject data = reqBody.optJSONObject("data");
        JSONObject body = data == null ? null : data.optJSONObject("body");
        JSONArray items = body == null ? null : body.optJSONArray("item_song");
        JSONObject meta = body == null ? null : body.optJSONObject("meta");
        int total = firstPositive(
                meta == null ? 0 : meta.optInt("sum", 0),
                meta == null ? 0 : meta.optInt("total", 0),
                body == null ? 0 : body.optInt("sum", 0),
                body == null ? 0 : body.optInt("total", 0),
                data == null ? 0 : data.optInt("sum", 0),
                items == null ? 0 : (page - 1) * limit + items.length());
        return new Page(parseQQMobile(items), total, "");
    }

    private Page qqLegacy(String keyword, int page, int limit) throws Exception {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=" + page + "&n=" + limit
                + "&w=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name())
                + "&format=json&cr=1&g_tk=5381";
        Http.Response r = Http.get(url, qqHeaders());
        if (r.code < 200 || r.code >= 300) throw new Exception("HTTP " + r.code);
        JSONObject root = new JSONObject(r.body);
        JSONObject data = root.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        int total = firstPositive(
                song == null ? 0 : song.optInt("totalnum", 0),
                song == null ? 0 : song.optInt("total", 0),
                list == null ? 0 : (page - 1) * limit + list.length());
        return new Page(parseQQSongs(list), total, "");
    }

    private List<Song> parseNeteaseResources(JSONArray resources) {
        List<Song> out = new ArrayList<>();
        if (resources == null) return out;
        for (int i = 0; i < resources.length(); i++) {
            JSONObject item = resources.optJSONObject(i);
            JSONObject base = item == null ? null : item.optJSONObject("baseInfo");
            JSONObject simple = base == null ? null : base.optJSONObject("simpleSongData");
            Song song = parseNeteaseSong(simple);
            if (song != null) out.add(song);
        }
        return out;
    }

    private List<Song> parseNeteaseSongs(JSONArray songs) {
        List<Song> out = new ArrayList<>();
        if (songs == null) return out;
        for (int i = 0; i < songs.length(); i++) {
            Song song = parseNeteaseSong(songs.optJSONObject(i));
            if (song != null) out.add(song);
        }
        return out;
    }

    private Song parseNeteaseSong(JSONObject o) {
        if (o == null) return null;
        long id = o.optLong("id");
        if (id <= 0) return null;
        JSONArray artists = o.optJSONArray("ar");
        if (artists == null) artists = o.optJSONArray("artists");
        StringBuilder ar = new StringBuilder();
        if (artists != null) {
            for (int j = 0; j < artists.length(); j++) {
                JSONObject ao = artists.optJSONObject(j);
                if (ao == null) continue;
                String name = ao.optString("name", "").trim();
                if (name.isEmpty()) continue;
                if (ar.length() > 0) ar.append(" / ");
                ar.append(name);
            }
        }
        JSONObject album = o.optJSONObject("al");
        if (album == null) album = o.optJSONObject("album");
        String cover = album == null ? "" : album.optString("picUrl", "");
        long duration = o.has("dt") ? o.optLong("dt") : o.optLong("duration");
        boolean q128 = qualityPresent(o, "l") || qualityPresent(o, "m") || qualityPresent(o, "h")
                || qualityPresent(o, "sq") || qualityPresent(o, "hr");
        boolean q320 = qualityPresent(o, "h") || qualityPresent(o, "sq") || qualityPresent(o, "hr");
        if (!o.has("l") && !o.has("m") && !o.has("h") && !o.has("sq") && !o.has("hr")) {
            q128 = true;
            q320 = true;
        }
        List<SourceVariant> variants = new ArrayList<>();
        variants.add(new SourceVariant("wy", String.valueOf(id), q128, q320, o));
        return new Song(o.optString("name", "未知歌曲"),
                ar.length() == 0 ? "未知歌手" : ar.toString(),
                album == null ? "" : album.optString("name", ""),
                cover, duration, variants);
    }

    private List<Song> parseQQMobile(JSONArray items) {
        List<Song> out = new ArrayList<>();
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String mid = firstNonEmpty(o.optString("mid", ""), o.optString("songmid", ""));
            if (mid.isEmpty()) continue;
            String title = firstNonEmpty(o.optString("title", ""), o.optString("name", ""), o.optString("songname", ""), "未知歌曲");
            String artist = singerNames(o.optJSONArray("singer"));
            JSONObject album = o.optJSONObject("album");
            String albumMid = album == null ? o.optString("albummid", "") : album.optString("mid", "");
            String cover;
            if (!albumMid.isEmpty()) cover = "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + albumMid + ".jpg";
            else {
                JSONArray singers = o.optJSONArray("singer");
                JSONObject first = singers == null ? null : singers.optJSONObject(0);
                String singerMid = first == null ? "" : first.optString("mid", "");
                cover = singerMid.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T001R500x500M000" + singerMid + ".jpg";
            }
            JSONObject file = o.optJSONObject("file");
            boolean knownQuality = file != null;
            boolean q128 = !knownQuality || file.optLong("size_128mp3", 0L) > 0L || file.optLong("size_96aac", 0L) > 0L;
            boolean q320 = !knownQuality || file.optLong("size_320mp3", 0L) > 0L || file.optLong("size_flac", 0L) > 0L;
            long interval = o.optLong("interval", 0L);
            List<SourceVariant> variants = new ArrayList<>();
            variants.add(new SourceVariant("tx", mid, q128, q320, o));
            out.add(new Song(title, artist, album == null ? "" : album.optString("name", ""), cover,
                    interval > 0 ? interval * 1000L : 0L, variants));
        }
        return out;
    }

    private List<Song> parseQQSongs(JSONArray list) {
        List<Song> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String mid = firstNonEmpty(o.optString("songmid", ""), o.optString("mid", ""));
            if (mid.isEmpty()) continue;
            String title = firstNonEmpty(o.optString("songname", ""), o.optString("title", ""), o.optString("name", ""), "未知歌曲");
            String artist = singerNames(o.optJSONArray("singer"));
            JSONObject album = o.optJSONObject("album");
            String albumMid = firstNonEmpty(o.optString("albummid", ""), album == null ? "" : album.optString("mid", ""));
            String albumName = firstNonEmpty(o.optString("albumname", ""), album == null ? "" : album.optString("name", ""));
            String cover = albumMid.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + albumMid + ".jpg";
            JSONObject file = o.optJSONObject("file");
            boolean knownQuality = file != null;
            boolean q128 = !knownQuality || file.optLong("size_128mp3", 0L) > 0L || file.optLong("size_96aac", 0L) > 0L;
            boolean q320 = !knownQuality || file.optLong("size_320mp3", 0L) > 0L || file.optLong("size_flac", 0L) > 0L;
            List<SourceVariant> variants = new ArrayList<>();
            variants.add(new SourceVariant("tx", mid, q128, q320, o));
            out.add(new Song(title, artist, albumName, cover, o.optLong("interval", 0L) * 1000L, variants));
        }
        return out;
    }

    private static String singerNames(JSONArray singers) {
        if (singers == null || singers.length() == 0) return "未知歌手";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < singers.length(); i++) {
            JSONObject s = singers.optJSONObject(i);
            if (s == null) continue;
            String n = s.optString("name", "").trim();
            if (n.isEmpty()) continue;
            if (b.length() > 0) b.append(" / ");
            b.append(n);
        }
        return b.length() == 0 ? "未知歌手" : b.toString();
    }

    private static boolean qualityPresent(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return false;
        JSONObject q = o.optJSONObject(key);
        if (q == null) return true;
        return q.optLong("size", q.optLong("br", 1L)) > 0L;
    }

    private static Map<String, String> neteaseHeaders() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", WEB_UA);
        h.put("Referer", "https://music.163.com/");
        h.put("Accept", "application/json,text/plain,*/*");
        return h;
    }

    private static Map<String, String> qqHeaders() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", WEB_UA);
        h.put("Referer", "https://y.qq.com/");
        h.put("Origin", "https://y.qq.com");
        h.put("Accept", "application/json,text/plain,*/*");
        return h;
    }

    private static String zzcSign(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hashBuilder = new StringBuilder(40);
        for (byte b : digest) hashBuilder.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        String hash = hashBuilder.toString();
        StringBuilder p1 = new StringBuilder();
        StringBuilder p2 = new StringBuilder();
        for (int idx : PART_1) if (idx >= 0 && idx < hash.length()) p1.append(hash.charAt(idx));
        for (int idx : PART_2) if (idx >= 0 && idx < hash.length()) p2.append(hash.charAt(idx));
        byte[] scrambled = new byte[SCRAMBLE.length];
        for (int i = 0; i < SCRAMBLE.length; i++) {
            int hv = Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16);
            scrambled[i] = (byte) (SCRAMBLE[i] ^ hv);
        }
        String b64 = Base64.getEncoder().encodeToString(scrambled)
                .replace("/", "").replace("\\", "").replace("+", "").replace("=", "");
        return ("zzc" + p1 + b64 + p2).toLowerCase(Locale.ROOT);
    }

    private static String firstNonEmpty(String... values) {
        if (values != null) for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return "";
    }

    private static int firstPositive(int... values) {
        if (values != null) for (int v : values) if (v > 0) return v;
        return 0;
    }

    private static String message(Exception e) {
        return e == null || e.getMessage() == null || e.getMessage().trim().isEmpty() ? "unknown" : e.getMessage().trim();
    }
}
