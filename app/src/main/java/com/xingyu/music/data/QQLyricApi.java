package com.xingyu.music.data;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lyric-only QQ Music adapter.
 *
 * IMPORTANT: this class is deliberately separate from QQMusicApi and every audio
 * resolver. A lyric failure must never alter playback routing, provider health,
 * cached playback URLs, or Media3 state.
 */
public final class QQLyricApi {
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/125 Safari/537.36";

    public String lyric(String songMid) throws Exception {
        String mid = songMid == null ? "" : songMid.trim();
        if (mid.isEmpty()) throw new Exception("QQ songmid 为空");

        String endpoint = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                + "?songmid=" + URLEncoder.encode(mid, StandardCharsets.UTF_8.name())
                + "&format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0"
                + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0";
        Http.Response r = Http.get(endpoint, headers());
        if (r.code < 200 || r.code >= 300) throw new Exception("QQ 歌词 HTTP " + r.code);
        JSONObject root = parseObject(r.body);
        int code = root.optInt("code", 0);
        if (code != 0) throw new Exception("QQ 歌词 code=" + code);
        String raw = root.optString("lyric", "");
        String lyric = decodeMaybeBase64(raw);
        if (lyric.trim().isEmpty()) throw new Exception("QQ 暂无歌词");
        return lyric;
    }

    private static Map<String,String> headers() {
        Map<String,String> h = new LinkedHashMap<>();
        h.put("User-Agent", UA);
        h.put("Referer", "https://y.qq.com/");
        h.put("Accept", "application/json,text/plain,*/*");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7");
        return h;
    }

    private static JSONObject parseObject(String raw) throws Exception {
        String t = raw == null ? "" : raw.trim();
        if (t.startsWith("\uFEFF")) t = t.substring(1).trim();
        if (t.startsWith("{")) return new JSONObject(t);
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) return new JSONObject(t.substring(first, last + 1));
        throw new Exception("QQ 歌词响应不是 JSON");
    }

    private static String decodeMaybeBase64(String raw) {
        if (raw == null) return "";
        String text = raw.trim();
        if (text.isEmpty()) return "";
        // nobase64=1 normally returns LRC directly. Keep a conservative fallback for
        // deployments that ignore the flag and still return base64.
        if (text.indexOf('[') >= 0 || text.contains("\\n") || text.contains("\n")) return text;
        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            String candidate = new String(decoded, StandardCharsets.UTF_8);
            if (candidate.indexOf('[') >= 0) return candidate;
        } catch (Exception ignored) { }
        return text;
    }
}
