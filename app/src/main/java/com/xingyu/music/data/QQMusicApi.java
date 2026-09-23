package com.xingyu.music.data;

import com.xingyu.music.model.ImportedPlaylist;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ Music metadata adapter.
 *
 * The primary search request intentionally follows LX Music Mobile's current
 * signed QQ Music mobile request. QQ began returning code=2001/login-required
 * after repeated unsigned requests in 2026, so the old unsigned desktop API
 * is only a fallback now.
 */
public final class QQMusicApi {
    private volatile int lastSearchTotal;
    private static final String WEB_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36";
    private static final String APP_UA = "QQMusic 14090508(android 12)";
    private static final int[] PART_1 = {23, 14, 6, 36, 16, 40, 7, 19};
    private static final int[] PART_2 = {16, 1, 32, 12, 19, 27, 8, 5};
    private static final int[] SCRAMBLE = {89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179};

    public int lastSearchTotal() { return lastSearchTotal; }

    public List<Song> search(String keyword, int limit) throws Exception {
        lastSearchTotal = 0;
        if (keyword == null || keyword.trim().isEmpty()) return new ArrayList<>();
        String q = keyword.trim();
        List<String> errors = new ArrayList<>();

        try {
            List<Song> result = searchSignedMobile(q, limit);
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            errors.add("signed=" + message(e));
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("搜索已取消");
        try {
            List<Song> result = searchUnsignedDesktop(q, limit);
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            errors.add("desktop=" + message(e));
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("搜索已取消");
        try {
            List<Song> result = searchLegacy(q, limit);
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            errors.add("legacy=" + message(e));
        }
        if (!errors.isEmpty()) throw new Exception(join(errors));
        return new ArrayList<>();
    }

    private List<Song> searchSignedMobile(String keyword, int limit) throws Exception {
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
        param.put("page_num", 1);
        param.put("num_per_page", Math.max(1, Math.min(limit, 50)));
        param.put("highlight", 0);
        param.put("nqc_flag", 0);
        param.put("multi_zhida", 0);
        param.put("cat", 2);
        param.put("grp", 1);
        param.put("sin", 0);
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
        headers.put("User-Agent", APP_UA);
        headers.put("Accept", "application/json,text/plain,*/*");
        Http.Response response = Http.postJson("https://u.y.qq.com/cgi-bin/musics.fcg?sign=" + sign, headers, raw);
        if (response.code < 200 || response.code >= 300) throw new Exception("HTTP " + response.code);
        JSONObject root = parseJsonObject(response.body);
        if (root.optInt("code", -1) != 0) throw new Exception("root code=" + root.optInt("code", -1));
        JSONObject reqBody = root.optJSONObject("req");
        if (reqBody == null) throw new Exception("缺少 req");
        int reqCode = reqBody.optInt("code", -1);
        if (reqCode != 0) throw new Exception(reqCode == 2001 ? "QQ 风控要求登录" : "req code=" + reqCode);
        JSONObject data = reqBody.optJSONObject("data");
        JSONObject body = data == null ? null : data.optJSONObject("body");
        JSONArray items = body == null ? null : body.optJSONArray("item_song");
        JSONObject meta = body == null ? null : body.optJSONObject("meta");
        lastSearchTotal = firstPositive(
                meta == null ? 0 : meta.optInt("sum", 0),
                meta == null ? 0 : meta.optInt("total", 0),
                body == null ? 0 : body.optInt("sum", 0),
                body == null ? 0 : body.optInt("total", 0),
                data == null ? 0 : data.optInt("sum", 0),
                items == null ? 0 : items.length());
        return parseMobileList(items);
    }

    private List<Song> searchUnsignedDesktop(String keyword, int limit) throws Exception {
        JSONObject param = new JSONObject();
        param.put("num_per_page", Math.max(1, Math.min(limit, 50)));
        param.put("page_num", 1);
        param.put("query", keyword);
        param.put("search_type", 0);
        param.put("searchid", String.valueOf(new java.util.Random().nextLong() & Long.MAX_VALUE));

        JSONObject req = new JSONObject();
        req.put("method", "DoSearchForQQMusicDesktop");
        req.put("module", "music.search.SearchCgiService");
        req.put("param", param);
        JSONObject comm = new JSONObject();
        comm.put("ct", 24);
        comm.put("cv", 0);
        JSONObject payload = new JSONObject();
        payload.put("comm", comm);
        payload.put("music.search.SearchCgiService", req);

        Http.Response response = Http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", headers(), payload.toString());
        if (response.code < 200 || response.code >= 300) throw new Exception("HTTP " + response.code);
        JSONObject root = parseJsonObject(response.body);
        JSONObject block = root.optJSONObject("music.search.SearchCgiService");
        if (block != null && block.optInt("code", 0) == 2001) throw new Exception("QQ 风控要求登录");
        JSONObject data = block == null ? null : block.optJSONObject("data");
        JSONObject body = data == null ? null : data.optJSONObject("body");
        JSONObject song = body == null ? null : body.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        lastSearchTotal = firstPositive(
                song == null ? 0 : song.optInt("totalnum", 0),
                song == null ? 0 : song.optInt("total", 0),
                list == null ? 0 : list.length());
        return parseModernList(list);
    }

    private List<Song> searchLegacy(String keyword, int limit) throws Exception {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=" + Math.max(1, Math.min(limit, 50))
                + "&w=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name())
                + "&format=json&cr=1&g_tk=5381";
        Http.Response response = Http.get(url, headers());
        if (response.code < 200 || response.code >= 300) throw new Exception("HTTP " + response.code);
        JSONObject root = parseJsonObject(response.body);
        JSONObject data = root.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        lastSearchTotal = firstPositive(
                song == null ? 0 : song.optInt("totalnum", 0),
                song == null ? 0 : song.optInt("total", 0),
                list == null ? 0 : list.length());
        List<Song> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            Song s = parseLegacySong(list.optJSONObject(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    public ImportedPlaylist playlistFromInput(String input) throws Exception {
        String id = extractPlaylistId(input);
        if (id == null) throw new Exception("没有识别到 QQ 音乐歌单 ID");
        Exception oldError = null;
        try {
            return playlistLegacy(id);
        } catch (Exception e) {
            oldError = e;
        }
        try {
            return playlistModern(id);
        } catch (Exception e) {
            throw new Exception("QQ 歌单读取失败：legacy=" + message(oldError) + "; modern=" + message(e));
        }
    }

    private ImportedPlaylist playlistLegacy(String id) throws Exception {
        String url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg"
                + "?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid="
                + URLEncoder.encode(id, StandardCharsets.UTF_8.name())
                + "&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0";
        Http.Response response = Http.get(url, headers());
        if (response.code < 200 || response.code >= 300) throw new Exception("HTTP " + response.code);
        JSONObject root = parseJsonObject(response.body);
        if (root.optInt("code", -1) != 0 || root.optInt("subcode", -1) != 0) throw new Exception("code=" + root.optInt("code", -1));
        JSONArray cd = root.optJSONArray("cdlist");
        JSONObject p = cd == null ? null : cd.optJSONObject(0);
        if (p == null) throw new Exception("缺少歌单数据");
        JSONArray list = p.optJSONArray("songlist");
        List<Song> songs = new ArrayList<>();
        if (list != null) for (int i = 0; i < list.length(); i++) {
            Song s = parseLegacySong(list.optJSONObject(i));
            if (s != null) songs.add(s);
        }
        return new ImportedPlaylist("tx:" + id, p.optString("dissname", "QQ 音乐歌单"), "tx", songs);
    }

    private ImportedPlaylist playlistModern(String id) throws Exception {
        JSONObject comm = new JSONObject();
        comm.put("cv", 4747474); comm.put("ct", 24); comm.put("format", "json");
        comm.put("inCharset", "utf-8"); comm.put("outCharset", "utf-8");
        comm.put("platform", "yqq.json"); comm.put("needNewCode", 1); comm.put("uin", 0);
        JSONObject param = new JSONObject();
        param.put("disstid", Long.parseLong(id)); param.put("userinfo", 1); param.put("tag", 1);
        param.put("orderlist", 1); param.put("song_begin", 0); param.put("song_num", 100000);
        param.put("onlysonglist", 0); param.put("enc_host_uin", "");
        JSONObject req1 = new JSONObject();
        req1.put("module", "music.srfDissInfo.aiDissInfo");
        req1.put("method", "uniform_get_Dissinfo"); req1.put("param", param);
        JSONObject payload = new JSONObject(); payload.put("comm", comm); payload.put("req_1", req1);
        Map<String,String> h = headers();
        h.put("Referer", "https://y.qq.com/n/yqq/playsquare/" + id + ".html");
        Http.Response r = Http.postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", h, payload.toString());
        if (r.code < 200 || r.code >= 300) throw new Exception("HTTP " + r.code);
        JSONObject root = parseJsonObject(r.body);
        JSONObject block = root.optJSONObject("req_1");
        if (root.optInt("code", -1) != 0 || block == null || block.optInt("code", -1) != 0) throw new Exception("接口返回失败");
        JSONObject data = block.optJSONObject("data");
        JSONObject dir = data == null ? null : data.optJSONObject("dirinfo");
        JSONArray list = data == null ? null : data.optJSONArray("songlist");
        List<Song> songs = parseModernList(list);
        return new ImportedPlaylist("tx:" + id, dir == null ? "QQ 音乐歌单" : dir.optString("title", "QQ 音乐歌单"), "tx", songs);
    }

    private List<Song> parseMobileList(JSONArray list) {
        return parseModernList(list);
    }

    private List<Song> parseModernList(JSONArray list) {
        List<Song> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String mid = firstNonEmpty(o.optString("mid", ""), o.optString("songmid", ""));
            if (mid.isEmpty()) continue;
            String title = firstNonEmpty(o.optString("title", ""), o.optString("songname", ""), o.optString("name", ""), "未知歌曲");
            String artist = singerNames(o.optJSONArray("singer"));
            JSONObject album = o.optJSONObject("album");
            String albumName = album == null ? o.optString("albumname", "") : album.optString("name", "");
            String albumMid = album == null ? o.optString("albummid", "") : album.optString("mid", "");
            String cover;
            if (!albumMid.isEmpty()) cover = "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + albumMid + ".jpg";
            else {
                JSONArray singers = o.optJSONArray("singer");
                JSONObject first = singers == null ? null : singers.optJSONObject(0);
                String singerMid = first == null ? "" : first.optString("mid", "");
                cover = singerMid.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T001R500x500M000" + singerMid + ".jpg";
            }
            long duration = o.optLong("interval", 0L) * 1000L;
            JSONObject file = o.optJSONObject("file");
            boolean knownQuality = file != null;
            boolean q128 = !knownQuality || file.optLong("size_128mp3", 0L) > 0L || file.optLong("size_96aac", 0L) > 0L;
            boolean q320 = !knownQuality || file.optLong("size_320mp3", 0L) > 0L || file.optLong("size_flac", 0L) > 0L;
            out.add(providerSong(o, mid, title, artist, albumName, cover, duration, q128, q320));
        }
        return out;
    }

    private Song parseLegacySong(JSONObject o) {
        if (o == null) return null;
        String mid = firstNonEmpty(o.optString("songmid", ""), o.optString("mid", ""));
        if (mid.isEmpty()) return null;
        String artist = singerNames(o.optJSONArray("singer"));
        JSONObject album = o.optJSONObject("album");
        String albumMid = firstNonEmpty(o.optString("albummid", ""), album == null ? "" : album.optString("mid", ""));
        String cover = albumMid.isEmpty() ? "" : "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + albumMid + ".jpg";
        String title = firstNonEmpty(o.optString("songname", ""), o.optString("title", ""), o.optString("name", ""), "未知歌曲");
        String albumName = firstNonEmpty(o.optString("albumname", ""), album == null ? "" : album.optString("name", ""));
        JSONObject file = o.optJSONObject("file");
        boolean knownQuality = file != null;
        boolean q128 = !knownQuality || file.optLong("size_128mp3", 0L) > 0L || file.optLong("size_96aac", 0L) > 0L;
        boolean q320 = !knownQuality || file.optLong("size_320mp3", 0L) > 0L || file.optLong("size_flac", 0L) > 0L;
        return providerSong(o, mid, title, artist, albumName, cover, o.optLong("interval", 0L) * 1000L, q128, q320);
    }

    private static Song providerSong(JSONObject raw, String mid, String title, String artist,
                                     String albumName, String cover, long duration,
                                     boolean q128, boolean q320) {
        List<SourceVariant> variants = new ArrayList<>();
        variants.add(new SourceVariant("tx", mid, q128, q320, raw));
        return new Song(title, artist, albumName, cover, duration, variants);
    }

    private String extractPlaylistId(String input) throws Exception {
        String text = input == null ? "" : input.trim();
        if (text.matches("\\d{5,}")) return text;
        Matcher m = Pattern.compile("(?:id|disstid)=([0-9]+)").matcher(text);
        if (m.find()) return m.group(1);
        m = Pattern.compile("/playlist/([0-9]+)").matcher(text);
        if (m.find()) return m.group(1);
        if (text.startsWith("http")) {
            Http.Response r = Http.get(text, headers());
            String u = r.finalUrl;
            m = Pattern.compile("(?:id|disstid)=([0-9]+)").matcher(u);
            if (m.find()) return m.group(1);
            m = Pattern.compile("/playlist/([0-9]+)").matcher(u);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", WEB_UA);
        h.put("Referer", "https://y.qq.com/");
        h.put("Origin", "https://y.qq.com");
        h.put("Accept", "application/json,text/plain,*/*");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        return h;
    }

    private static String zzcSign(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hashBuilder = new StringBuilder(40);
        for (byte b : digest) hashBuilder.append(String.format("%02x", b & 0xff));
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
        return ("zzc" + p1 + b64 + p2).toLowerCase(java.util.Locale.ROOT);
    }

    private static JSONObject parseJsonObject(String raw) throws Exception {
        String t = raw == null ? "" : raw.trim();
        if (t.startsWith("\uFEFF")) t = t.substring(1).trim();
        if (t.startsWith("{")) return new JSONObject(t);
        // Only unwrap a real JSONP wrapper at the start of the response.
        // Song titles such as "Value (Live)" therefore remain untouched.
        Matcher m = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$.]*\\s*\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL).matcher(t);
        if (m.matches()) {
            String inner = m.group(1).trim();
            if (inner.startsWith("{")) return new JSONObject(inner);
        }
        int firstBrace = t.indexOf('{');
        int lastBrace = t.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) return new JSONObject(t.substring(firstBrace, lastBrace + 1));
        throw new Exception("返回不是 JSON" + (t.isEmpty() ? "" : "：" + abbreviate(t)));
    }

    private static String singerNames(JSONArray singers) {
        StringBuilder ar = new StringBuilder();
        if (singers != null) for (int i = 0; i < singers.length(); i++) {
            JSONObject s = singers.optJSONObject(i);
            if (s == null) continue;
            String n = s.optString("name", "").trim();
            if (!n.isEmpty()) {
                if (ar.length() > 0) ar.append(" / ");
                ar.append(n);
            }
        }
        return ar.length() == 0 ? "未知歌手" : ar.toString();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static int firstPositive(int... values) {
        if (values != null) for (int v : values) if (v > 0) return v;
        return 0;
    }

    private static String message(Exception e) { return e == null || e.getMessage() == null ? "unknown" : e.getMessage(); }
    private static String abbreviate(String s) { return s.length() <= 100 ? s : s.substring(0, 100) + "…"; }
    private static String join(List<String> a) { StringBuilder b = new StringBuilder(); for (String s : a) { if (b.length() > 0) b.append("; "); b.append(s); } return b.toString(); }
}
