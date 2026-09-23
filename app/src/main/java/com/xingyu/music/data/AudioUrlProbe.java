package com.xingyu.music.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Lightweight validation for aggregate-source audio candidates. */
public final class AudioUrlProbe {
    public static final class Result {
        public final boolean checked;
        public final boolean playable;
        public final int statusCode;
        public final String finalUrl;
        public final String contentType;
        public final String reason;

        Result(boolean checked, boolean playable, int statusCode, String finalUrl, String contentType, String reason) {
            this.checked = checked;
            this.playable = playable;
            this.statusCode = statusCode;
            this.finalUrl = finalUrl == null ? "" : finalUrl;
            this.contentType = contentType == null ? "" : contentType;
            this.reason = reason == null ? "" : reason;
        }
    }

    private AudioUrlProbe() { }

    public static Result check(String url) {
        if (url == null || !url.startsWith("http")) return new Result(true, false, -1, "", "", "URL 无效");
        try {
            Map<String,String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
            headers.put("Accept", "audio/*,*/*;q=0.8");
            headers.put("Connection", "close");
            Http.Response r = Http.getNoBody(url, headers);
            String type = header(r.headers, "Content-Type");
            boolean okCode = (r.code >= 200 && r.code < 300) || r.code == 416;
            // 416 can mean the server understood Range but the object is tiny/edge-cached;
            // let Media3 make the final decision in that rare case.
            if (okCode) return new Result(true, true, r.code, r.finalUrl, type, "");
            if (r.code == 401 || r.code == 403 || r.code == 404 || r.code == 410) {
                return new Result(true, false, r.code, r.finalUrl, type, "HTTP " + r.code);
            }
            // Network oddities / 5xx are not authoritative enough to discard a candidate;
            // Media3 remains the final judge.
            return new Result(false, true, r.code, r.finalUrl, type, "probe inconclusive");
        } catch (Exception e) {
            return new Result(false, true, -1, url, "", e.getMessage() == null ? "probe error" : e.getMessage());
        }
    }

    private static String header(Map<String,String> headers, String key) {
        if (headers == null) return "";
        for (Map.Entry<String,String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(key.toLowerCase(Locale.ROOT))) {
                return e.getValue() == null ? "" : e.getValue();
            }
        }
        return "";
    }
}
