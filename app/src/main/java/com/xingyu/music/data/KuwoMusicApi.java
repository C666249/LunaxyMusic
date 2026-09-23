package com.xingyu.music.data;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Search-only Kuwo catalog adapter, modelled after LX Music's current kw search shape. */
public final class KuwoMusicApi {
    private static final Pattern MINFO = Pattern.compile("level:(\\w+),bitrate:(\\d+),format:(\\w+),size:([\\w.]+)");
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
        String url = "http://search.kuwo.cn/r.s?client=kt&all="
                + URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                + "&pn=" + (p - 1) + "&rn=" + n
                + "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1"
                + "&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1";
        Http.Response r = Http.get(url, headers());
        if (r.code < 200 || r.code >= 300) throw new Exception("酷我 HTTP " + r.code);
        JSONObject root = new JSONObject(r.body);
        JSONArray list = root.optJSONArray("abslist");
        List<Song> songs = parse(list);
        int total = parseInt(root.opt("TOTAL"), songs.size());
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
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String rid = firstNonEmpty(o.optString("MUSICRID", ""), o.optString("musicrid", ""));
            if (rid.startsWith("MUSIC_")) rid = rid.substring(6);
            if (rid.isEmpty()) continue;
            String title = decode(firstNonEmpty(o.optString("SONGNAME", ""), o.optString("NAME", ""), "未知歌曲"));
            String artist = decode(firstNonEmpty(o.optString("ARTIST", ""), "未知歌手")).replace("&", " / ");
            String album = decode(o.optString("ALBUM", ""));
            long duration = parseLong(o.opt("DURATION"), 0L) * 1000L;
            String minfo = o.optString("N_MINFO", "");
            boolean q128 = false, q320 = false;
            JSONArray types = new JSONArray();
            JSONObject typeMap = new JSONObject();
            if (!minfo.isEmpty()) {
                for (String part : minfo.split(";")) {
                    Matcher m = MINFO.matcher(part);
                    if (!m.find()) continue;
                    String bitrate = m.group(2);
                    String type = "";
                    if ("128".equals(bitrate)) { type = "128k"; q128 = true; }
                    else if ("320".equals(bitrate)) { type = "320k"; q128 = true; q320 = true; }
                    else if ("2000".equals(bitrate)) { type = "flac"; q128 = true; q320 = true; }
                    else if ("4000".equals(bitrate)) { type = "flac24bit"; q128 = true; q320 = true; }
                    if (!type.isEmpty()) {
                        JSONObject t = new JSONObject();
                        try { t.put("type", type); t.put("size", m.group(4)); types.put(t); JSONObject mm = new JSONObject(); mm.put("size", m.group(4)); typeMap.put(type, mm); }
                        catch (Exception ignored) { }
                    }
                }
            }
            if (minfo.isEmpty()) { q128 = true; q320 = true; }

            JSONObject lx = new JSONObject();
            try {
                lx.put("name", title); lx.put("singer", artist); lx.put("source", "kw");
                lx.put("songmid", rid); lx.put("albumId", decode(o.optString("ALBUMID", "")));
                lx.put("albumName", album); lx.put("interval", formatDuration(duration));
                lx.put("types", types); lx.put("_types", typeMap); lx.put("typeUrl", new JSONObject());
            } catch (Exception ignored) { }
            List<SourceVariant> variants = new ArrayList<>();
            variants.add(new SourceVariant("kw", rid, q128, q320, lx));
            out.add(new Song(title, artist, album, "", duration, variants));
        }
        return out;
    }

    private static Map<String,String> headers() {
        Map<String,String> h = new LinkedHashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36");
        h.put("Referer", "http://www.kuwo.cn/");
        h.put("Accept", "application/json,text/plain,*/*");
        return h;
    }
    private static String decode(String s) { return s == null ? "" : s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim(); }
    private static String formatDuration(long ms) { long sec = Math.max(0, ms / 1000L); return String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60L, sec % 60L); }
    private static int parseInt(Object o, int fallback) { try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return fallback; } }
    private static long parseLong(Object o, long fallback) { try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return fallback; } }
    private static String firstNonEmpty(String... a) { for (String s : a) if (s != null && !s.trim().isEmpty()) return s.trim(); return ""; }
}
