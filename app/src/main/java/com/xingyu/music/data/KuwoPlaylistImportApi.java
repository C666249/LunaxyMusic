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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Metadata-only Kuwo playlist importer. It never participates in search ranking or playback routing. */
public final class KuwoPlaylistImportApi {
    private static final Pattern PLAYLIST_ID = Pattern.compile("(?:playlist_detail/|playlist/|pid=)(\\d{3,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_PLAYLIST_ID = Pattern.compile("(?:playlistId|playlist_id|pid)[\\\"'\\s:=]+(\\d{3,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern KUWO_URL = Pattern.compile("https?://(?:[a-z0-9-]+\\.)*kuwo\\.cn/[^\\s]+", Pattern.CASE_INSENSITIVE);

    public ImportedPlaylist playlistFromInput(String input) throws Exception {
        String text = input == null ? "" : input.trim();
        String id = playlistId(text);
        String shareUrl = firstKuwoUrl(text);
        if (id.isEmpty() && !shareUrl.isEmpty()) {
            Http.Response page = Http.get(shareUrl, pageHeaders());
            id = playlistId(page.finalUrl);
            if (id.isEmpty()) {
                Matcher matcher = HTML_PLAYLIST_ID.matcher(page.body == null ? "" : page.body);
                if (matcher.find()) id = matcher.group(1);
            }
        }
        if (id.isEmpty()) throw new Exception("无法识别酷我歌单链接，请重新复制完整分享链接");
        ArrayList<Song> songs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String name = "酷我歌单";
        int page = 1;
        int total = Integer.MAX_VALUE;
        while (songs.size() < total && page <= 60) {
            String url = "https://m.kuwo.cn/newh5app/wapi/api/www/playlist/playListInfo?pid="
                    + URLEncoder.encode(id, StandardCharsets.UTF_8.name()) + "&pn=" + page + "&rn=100";
            Http.Response r = Http.get(url, headers(id));
            if (r.code < 200 || r.code >= 300) throw new Exception("酷我歌单 HTTP " + r.code);
            JSONObject root = new JSONObject(r.body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) break;
            if (page == 1) name = firstNonEmpty(data.optString("name", ""), data.optString("playlistName", ""), name);
            JSONArray list = data.optJSONArray("musicList");
            if (list == null || list.length() == 0) break;
            total = Math.max(list.length(), parseInt(data.opt("total"), songs.size() + list.length()));
            for (int i = 0; i < list.length(); i++) {
                Song song = parseSong(list.optJSONObject(i));
                if (song != null && seen.add(song.sourceId + "|" + song.key())) songs.add(song);
            }
            if (list.length() < 100) break;
            page++;
        }
        if (songs.isEmpty()) throw new Exception("酷我歌单没有返回可导入歌曲");
        return new ImportedPlaylist("kw:" + id, name, "kw", songs);
    }

    private Song parseSong(JSONObject o) {
        if (o == null) return null;
        String rid = firstNonEmpty(o.optString("musicrid", ""), o.optString("MUSICRID", ""), o.optString("rid", ""), o.optString("id", ""));
        if (rid.startsWith("MUSIC_")) rid = rid.substring(6);
        if (rid.isEmpty()) return null;
        String title = decode(firstNonEmpty(o.optString("name", ""), o.optString("songName", ""), o.optString("SONGNAME", ""), "未知歌曲"));
        String artist = decode(firstNonEmpty(o.optString("artist", ""), o.optString("ARTIST", ""), "未知歌手")).replace("&", " / ");
        String album = decode(firstNonEmpty(o.optString("album", ""), o.optString("albumName", ""), o.optString("ALBUM", "")));
        long durationRaw = parseLong(firstNonEmpty(o.optString("duration", ""), o.optString("DURATION", "0")), 0L);
        long duration = durationRaw > 100000L ? durationRaw : durationRaw * 1000L;
        String cover = firstNonEmpty(o.optString("pic", ""), o.optString("albumpic", ""), o.optString("hts_MVPIC", ""));
        JSONObject lx = new JSONObject();
        try {
            lx.put("name", title); lx.put("singer", artist); lx.put("source", "kw"); lx.put("songmid", rid);
            lx.put("albumName", album); lx.put("interval", formatDuration(duration)); lx.put("typeUrl", new JSONObject());
        } catch (Exception ignored) { }
        List<SourceVariant> variants = new ArrayList<>();
        variants.add(new SourceVariant("kw", rid, true, true, lx));
        return new Song(title, artist, album, cover, duration, variants);
    }


    private static String firstKuwoUrl(String raw) {
        String text = raw == null ? "" : raw.trim();
        Matcher m = KUWO_URL.matcher(text);
        if (!m.find()) return text.startsWith("http") && text.toLowerCase(java.util.Locale.ROOT).contains("kuwo.cn") ? text : "";
        String url = m.group();
        while (!url.isEmpty() && ",.;!?)）】》〉，。；！".indexOf(url.charAt(url.length()-1)) >= 0) url = url.substring(0, url.length()-1);
        return url;
    }

    private static String playlistId(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.matches("\\d{3,}")) return text;
        Matcher m = PLAYLIST_ID.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static Map<String,String> pageHeaders() {
        Map<String,String> h = new LinkedHashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36");
        h.put("Accept", "text/html,application/xhtml+xml,application/json,text/plain,*/*");
        return h;
    }
    private static Map<String,String> headers(String id) {
        Map<String,String> h = new LinkedHashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1");
        h.put("Referer", "https://m.kuwo.cn/newh5app/playlist_detail/" + id);
        h.put("Accept", "application/json,text/plain,*/*");
        return h;
    }
    private static String decode(String s) { return s == null ? "" : s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim(); }
    private static String formatDuration(long ms) { long sec = Math.max(0, ms / 1000L); return String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60L, sec % 60L); }
    private static int parseInt(Object o, int fallback) { try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return fallback; } }
    private static long parseLong(Object o, long fallback) { try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return fallback; } }
    private static String firstNonEmpty(String... values) { for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim(); return ""; }
}
