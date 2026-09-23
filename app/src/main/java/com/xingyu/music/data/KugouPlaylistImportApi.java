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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Metadata-only Kugou playlist importer. It never participates in search ranking or playback routing. */
public final class KugouPlaylistImportApi {
    private static final Pattern SPECIAL_ID = Pattern.compile("(?:special/single/|specialid=|special_id=)(\\d{3,})", Pattern.CASE_INSENSITIVE);
    // collection_<type>_<uid>_<listId>_<...>: capture listId, never uid.
    private static final Pattern COLLECTION_ID = Pattern.compile("collection_\\d+_\\d+_(\\d{1,})_(?:\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GCID = Pattern.compile("(?:/songlist/)?gcid_([a-z0-9]+)|[?&]src_cid=([a-z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_SPECIAL_ID = Pattern.compile("(?:specialid|specialId|special_id|list_id)[^0-9]{0,16}(\\d{1,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern KUGOU_URL = Pattern.compile("https?://(?:[a-z0-9-]+\\.)*kugou\\.com/[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final String SIGN_KEY = "OIlwieks28dk2k092lksi2UIkp";
    private static final String MID = "239526275778893399526700786998289824956";

    public ImportedPlaylist playlistFromInput(String input) throws Exception {
        String text = input == null ? "" : input.trim();
        String specialId = playlistId(text);
        String gcid = gcid(text);
        String discoveredName = "";
        // Kugou's share sheet usually copies prose + a t1/t2/t3.kugou.com short URL instead of a
        // bare URL. V65 only followed redirects when the WHOLE input started with http, so normal
        // share text could not be resolved and users sometimes tried an ambiguous numeric ID which
        // then belonged to another platform. Extract only Kugou's own URL here; QQ/WY/KW importers
        // and their dispatch paths are untouched.
        String shareUrl = firstKugouUrl(text);
        if (specialId.isEmpty() && !shareUrl.isEmpty()) {
            Http.Response page = Http.get(shareUrl, pageHeaders());
            specialId = playlistId(page.finalUrl);
            if (specialId.isEmpty()) specialId = playlistId(page.body);
            if (specialId.isEmpty()) {
                Matcher matcher = HTML_SPECIAL_ID.matcher(page.body == null ? "" : page.body);
                if (matcher.find()) specialId = matcher.group(1);
            }
            discoveredName = htmlTitle(page.body);
        }
        if (specialId.isEmpty()) {
            if (!gcid.isEmpty()) throw new Exception("已识别酷狗 GCID 歌单，但官方页面暂未给出可读取的集合 ID");
            throw new Exception("无法识别酷狗歌单链接，请重新复制完整分享链接");
        }

        ArrayList<Song> songs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int page = 1;
        int total = Integer.MAX_VALUE;
        String serverName = "";
        while (songs.size() < total && page <= 40) {
            LinkedHashMap<String,String> params = new LinkedHashMap<>();
            params.put("specialid", specialId);
            params.put("need_sort", "1");
            params.put("module", "CloudMusic");
            params.put("clientver", "11239");
            params.put("pagesize", "300");
            params.put("specalidpgc", specialId);
            params.put("userid", "0");
            params.put("page", String.valueOf(page));
            params.put("type", "0");
            params.put("area_code", "1");
            params.put("appid", "1005");
            String url = "http://gatewayretry.kugou.com/v2/get_other_list_file?" + encodeQuery(params)
                    + "&signature=" + signature(params);
            Http.Response response = Http.get(url, playlistHeaders());
            if (response.code < 200 || response.code >= 300) throw new Exception("酷狗歌单 HTTP " + response.code);
            JSONObject root = new JSONObject(response.body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) break;
            if (serverName.isEmpty()) serverName = firstNonEmpty(data.optString("specialname", ""), data.optString("name", ""), data.optString("title", ""));
            JSONArray info = data.optJSONArray("info");
            if (info == null || info.length() == 0) break;
            total = Math.max(info.length(), data.optInt("count", songs.size() + info.length()));
            for (int i = 0; i < info.length(); i++) {
                Song song = parseSong(info.optJSONObject(i));
                if (song != null && seen.add(song.sourceId + "|" + song.key())) songs.add(song);
            }
            if (info.length() < 300) break;
            page++;
        }
        if (songs.isEmpty()) throw new Exception("酷狗歌单没有返回可导入歌曲");
        return new ImportedPlaylist("kg:" + specialId, firstNonEmpty(serverName, discoveredName, "酷狗歌单"), "kg", songs);
    }

    private Song parseSong(JSONObject o) {
        if (o == null) return null;
        String fileHash = firstNonEmpty(o.optString("hash", ""), o.optString("Hash", ""), o.optString("FileHash", ""));
        String audioId = firstNonEmpty(o.optString("audio_id", ""), o.optString("audioid", ""), o.optString("Audioid", ""), o.optString("album_audio_id", ""));
        if (audioId.isEmpty() || "0".equals(audioId)) audioId = fileHash;
        if (audioId.isEmpty()) return null;
        String combined = decode(firstNonEmpty(o.optString("name", ""), o.optString("filename", ""), o.optString("songname", "")));
        String title = firstNonEmpty(o.optString("songname", ""), o.optString("SongName", ""));
        String artist = firstNonEmpty(o.optString("singername", ""), o.optString("SingerName", ""), o.optString("author_name", ""));
        if ((title.isEmpty() || artist.isEmpty()) && combined.contains(" - ")) {
            String[] pair = combined.split(" - ", 2);
            if (artist.isEmpty()) artist = pair[0].trim();
            if (title.isEmpty() && pair.length > 1) title = pair[1].trim();
        }
        if (title.isEmpty()) title = combined.isEmpty() ? "未知歌曲" : combined;
        if (artist.isEmpty()) artist = "未知歌手";
        String album = decode(firstNonEmpty(o.optString("album_name", ""), o.optString("albumname", ""), o.optString("AlbumName", "")));
        String cover = firstNonEmpty(o.optString("cover", ""), o.optString("cover_url", ""));
        if (cover.contains("/{size}")) cover = cover.replace("/{size}", "");
        long rawDuration = firstLong(o, "duration", "timelen", "Duration");
        long duration = rawDuration > 100000L ? rawDuration : rawDuration * 1000L;

        JSONArray types = new JSONArray();
        JSONObject typeMap = new JSONObject();
        addType(types, typeMap, "128k", o.optLong("FileSize", o.optLong("filesize", 0L)), fileHash);
        addType(types, typeMap, "320k", o.optLong("HQFileSize", o.optLong("hqfilesize", 0L)), firstNonEmpty(o.optString("HQFileHash", ""), o.optString("hqhash", "")));
        addType(types, typeMap, "flac", o.optLong("SQFileSize", o.optLong("sqfilesize", 0L)), firstNonEmpty(o.optString("SQFileHash", ""), o.optString("sqhash", "")));
        JSONObject lx = new JSONObject();
        try {
            lx.put("name", decode(title)); lx.put("singer", decode(artist).replace("、", " / ")); lx.put("source", "kg");
            lx.put("songmid", audioId); lx.put("songId", audioId); lx.put("audioId", audioId);
            lx.put("albumName", album); lx.put("interval", formatDuration(duration)); lx.put("_interval", Math.max(0L, duration / 1000L));
            lx.put("hash", fileHash); lx.put("types", types); lx.put("_types", typeMap); lx.put("typeUrl", new JSONObject());
        } catch (Exception ignored) { }
        List<SourceVariant> variants = new ArrayList<>();
        variants.add(new SourceVariant("kg", audioId, true, true, lx));
        return new Song(decode(title), decode(artist).replace("、", " / "), album, cover, duration, variants);
    }

    private static void addType(JSONArray types, JSONObject map, String type, long size, String hash) {
        if (size <= 0 && (hash == null || hash.isEmpty())) return;
        try {
            JSONObject t = new JSONObject(); t.put("type", type); t.put("size", String.valueOf(size)); if (hash != null && !hash.isEmpty()) t.put("hash", hash); types.put(t);
            JSONObject m = new JSONObject(); m.put("size", String.valueOf(size)); if (hash != null && !hash.isEmpty()) m.put("hash", hash); map.put(type, m);
        } catch (Exception ignored) { }
    }

    private static String firstKugouUrl(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher m = KUGOU_URL.matcher(text);
        if (!m.find()) return text.startsWith("http") && text.toLowerCase(java.util.Locale.ROOT).contains("kugou.com") ? text : "";
        String url = m.group();
        while (!url.isEmpty() && ",.;!?)）】》〉，。；！".indexOf(url.charAt(url.length()-1)) >= 0) url = url.substring(0, url.length()-1);
        return url;
    }


    private static String gcid(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher m = GCID.matcher(text);
        if (!m.find()) return "";
        String a = m.group(1), b = m.group(2);
        return a != null && !a.isEmpty() ? a : (b == null ? "" : b);
    }

    private static String playlistId(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.matches("\\d{3,}")) return text;
        Matcher m = SPECIAL_ID.matcher(text);
        if (m.find()) return m.group(1);
        Matcher collection = COLLECTION_ID.matcher(text);
        return collection.find() ? collection.group(1) : "";
    }
    private static String encodeQuery(Map<String,String> params) throws Exception {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String,String> entry : params.entrySet()) {
            if (b.length() > 0) b.append('&');
            b.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name())).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        return b.toString();
    }
    private static String signature(Map<String,String> params) {
        try {
            ArrayList<String> keys = new ArrayList<>(params.keySet()); Collections.sort(keys);
            StringBuilder raw = new StringBuilder(SIGN_KEY);
            for (String key : keys) raw.append(key).append('=').append(params.get(key));
            raw.append(SIGN_KEY);
            byte[] digest = MessageDigest.getInstance("MD5").digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); for (byte value : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return out.toString();
        } catch (Exception e) { return ""; }
    }
    private static Map<String,String> playlistHeaders() {
        Map<String,String> h = new LinkedHashMap<>();
        h.put("User-Agent", "Android9-AndroidPhone-11239-18-0-playlist-wifi"); h.put("Host", "gatewayretry.kugou.com");
        h.put("x-router", "pubsongscdn.kugou.com"); h.put("mid", MID); h.put("dfid", "-");
        h.put("clienttime", String.valueOf(System.currentTimeMillis() / 1000L)); h.put("Accept", "application/json,text/plain,*/*");
        return h;
    }
    private static Map<String,String> pageHeaders() { Map<String,String> h = new LinkedHashMap<>(); h.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36"); h.put("Referer", "https://www.kugou.com/"); h.put("Accept", "application/json,text/plain,*/*"); return h; }
    private static String htmlTitle(String html) { if (html == null) return ""; Matcher m = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html); if (!m.find()) return ""; return decode(m.group(1).replaceAll("<[^>]+>", "").replace("- 酷狗音乐", "").trim()); }
    private static long firstLong(JSONObject o, String... keys) { for (String key : keys) { Object value=o.opt(key); if(value==null)continue; try { long n=Long.parseLong(String.valueOf(value)); if(n>0L)return n; } catch(Exception ignored){} } return 0L; }
    private static String decode(String s) { return s == null ? "" : s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim(); }
    private static String formatDuration(long ms) { long sec = Math.max(0, ms / 1000L); return String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60L, sec % 60L); }
    private static String firstNonEmpty(String... values) { for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim(); return ""; }
}
