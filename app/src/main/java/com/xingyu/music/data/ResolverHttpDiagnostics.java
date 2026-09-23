package com.xingyu.music.data;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Observation-only HTTP trace for the bundled Huibq endpoint.
 *
 * V39 safety rules:
 * - never performs/retries a request;
 * - never mutates routing, source health, cache, or response data;
 * - inspects only a small fixed prefix of an already-completed response body;
 * - keeps a small fixed in-memory ring so diagnostics cannot grow without bound.
 */
public final class ResolverHttpDiagnostics {
    private static final int MAX_LINES = 8;
    private static final int MAX_BODY_PREFIX_CHARS = 384;
    private static final int MAX_LINE_CHARS = 360;
    private static final List<String> lines = new ArrayList<>();
    private static final String HUIBQ_HOST = "lxmusicapi.onrender.com";
    private static volatile int lastHuibqHttpCode = 0;
    private static volatile long lastHuibqHttpAt = 0L;
    private static final Pattern CODE = Pattern.compile("[\\\"']?code[\\\"']?\\s*:\\s*([\\\"']?[^,}\\s\\\"']+[\\\"']?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MSG = Pattern.compile("[\\\"']?msg[\\\"']?\\s*:\\s*[\\\"']([^\\\"']{0,120})[\\\"']", Pattern.CASE_INSENSITIVE);

    private ResolverHttpDiagnostics() { }

    public static void observe(String url, Http.Response response) {
        try {
            if (response == null || !isHuibq(url)) return;
            lastHuibqHttpCode = response.code;
            lastHuibqHttpAt = System.currentTimeMillis();
            String contentType = header(response.headers, "Content-Type");
            String contentLength = header(response.headers, "Content-Length");
            String raw = response.body == null ? "" : response.body;
            int bodyChars = raw.length(); // O(1), does not scan/copy the full body.
            String prefix = raw.substring(0, Math.min(bodyChars, MAX_BODY_PREFIX_CHARS));

            StringBuilder detail = new StringBuilder(256);
            detail.append("Huibq HTTP ").append(response.code);
            if (!contentType.isEmpty()) detail.append(" · type=").append(shorten(contentType, 52));
            if (!contentLength.isEmpty()) detail.append(" · len=").append(shorten(contentLength, 20));
            detail.append(" · chars=").append(bodyChars);
            detail.append(" · ").append(summarizePrefix(prefix, bodyChars));
            add(shorten(detail.toString(), MAX_LINE_CHARS));
        } catch (Exception ignored) {
            // Diagnostics must be impossible to turn into a playback failure.
        }
    }


    /** Returns the already-observed Huibq HTTP status; never performs network I/O. */
    public static int recentHuibqHttpCode(long maxAgeMs) {
        long age = System.currentTimeMillis() - lastHuibqHttpAt;
        return age >= 0 && age <= Math.max(0L, maxAgeMs) ? lastHuibqHttpCode : 0;
    }

    /** Returns a Huibq status only when that observation happened after this resolver attempt began. */
    public static int huibqHttpCodeSince(long sinceMs) {
        return lastHuibqHttpAt >= sinceMs ? lastHuibqHttpCode : 0;
    }

    public static synchronized String text() {
        if (lines.isEmpty()) return "";
        StringBuilder b = new StringBuilder(3200);
        b.append("--- Huibq HTTP 纯观察（固定长度，不参与选路/学习） ---");
        for (String line : lines) b.append('\n').append(line);
        return b.toString();
    }

    private static synchronized void add(String detail) {
        if (lines.size() >= MAX_LINES) lines.remove(0);
        lines.add(time() + "  " + detail);
    }

    private static boolean isHuibq(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = new URI(url).getHost();
            return host != null && HUIBQ_HOST.equalsIgnoreCase(host);
        } catch (Exception ignored) {
            return url.contains(HUIBQ_HOST);
        }
    }

    private static String header(Map<String,String> headers, String name) {
        if (headers == null || headers.isEmpty()) return "";
        for (Map.Entry<String,String> e : headers.entrySet()) {
            if (e.getKey() != null && name.equalsIgnoreCase(e.getKey())) return safe(e.getValue());
        }
        return "";
    }

    /** Only the bounded prefix reaches regex/whitespace processing. */
    private static String summarizePrefix(String prefix, int fullChars) {
        String t = safe(prefix).trim();
        if (fullChars == 0 || t.isEmpty()) return "body=empty";
        String oneLine = t.replaceAll("\\s+", " ");
        boolean jsonLike = oneLine.startsWith("{") || oneLine.startsWith("[");
        String suffix = fullChars > prefix.length() ? "…[truncated]" : "";
        if (jsonLike) {
            String code = match(CODE, oneLine);
            String msg = match(MSG, oneLine);
            StringBuilder b = new StringBuilder("body=json");
            if (!code.isEmpty()) b.append(" code=").append(shorten(stripQuotes(code), 24));
            if (!msg.isEmpty()) b.append(" msg=").append(shorten(msg, 72));
            if (code.isEmpty() && msg.isEmpty()) {
                b.append(" snippet=\"").append(shorten(redactUrl(oneLine), 96)).append("\"");
            }
            b.append(suffix);
            return b.toString();
        }
        return "body=text \"" + shorten(redactUrl(oneLine), 104) + suffix + "\"";
    }

    private static String match(Pattern p, String s) {
        Matcher m = p.matcher(s == null ? "" : s);
        return m.find() ? safe(m.group(1)) : "";
    }

    private static String stripQuotes(String s) {
        String v = safe(s).trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static String redactUrl(String s) {
        // Operates only on the <=384-char prefix; never scans the full response body.
        return safe(s).replaceAll("https?://[^\\s\\\"']+", "<url-redacted>");
    }

    private static String shorten(String s, int max) {
        String v = safe(s);
        if (v.length() <= max) return v;
        return v.substring(0, Math.max(1, max)) + "…";
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String time() { return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date()); }
}
