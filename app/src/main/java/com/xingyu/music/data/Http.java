package com.xingyu.music.data;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Http {
    private Http() {}

    public static final class Response {
        public final int code;
        public final String body;
        public final String finalUrl;
        public final Map<String,String> headers;
        public final byte[] bodyBytes;
        Response(int code, String body, String finalUrl) { this(code, body, finalUrl, new LinkedHashMap<>(), body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8)); }
        Response(int code, String body, String finalUrl, Map<String,String> headers) { this(code, body, finalUrl, headers, body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8)); }
        Response(int code, String body, String finalUrl, Map<String,String> headers, byte[] bodyBytes) {
            this.code = code; this.body = body; this.finalUrl = finalUrl;
            this.headers = headers == null ? new LinkedHashMap<>() : headers;
            this.bodyBytes = bodyBytes == null ? new byte[0] : bodyBytes;
        }
    }

    public static Response get(String url, Map<String, String> headers) throws Exception {
        return request("GET", url, headers, null, true, 7000, 10000);
    }

    public static Response get(String url, Map<String, String> headers, int connectMs, int readMs) throws Exception {
        return request("GET", url, headers, null, true, connectMs, readMs);
    }

    public static Response getNoBody(String url, Map<String, String> headers) throws Exception {
        HttpURLConnection c = open("GET", url, headers, true, 7000, 10000);
        c.setRequestProperty("Range", "bytes=0-1");
        int code = c.getResponseCode();
        String finalUrl = c.getURL().toString();
        try { InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream(); if (in != null) in.close(); } catch (Exception ignored) {}
        Map<String,String> responseHeaders = flattenHeaders(c);
        c.disconnect();
        return new Response(code, "", finalUrl, responseHeaders);
    }


    public static Response postJson(String url, Map<String, String> headers, String json) throws Exception {
        Map<String,String> h = new LinkedHashMap<>();
        if (headers != null) h.putAll(headers);
        h.put("Content-Type", "application/json; charset=UTF-8");
        byte[] body = (json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8);
        return request("POST", url, h, body, true, 7000, 10000);
    }

    public static Response postRaw(String url, Map<String, String> headers, String text, int timeoutMs) throws Exception {
        byte[] body = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        return request("POST", url, headers, body, true, Math.min(Math.max(3000, timeoutMs), 18000), Math.max(5000, timeoutMs));
    }

    public static Response postForm(String url, Map<String, String> headers, Map<String, String> form) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        Map<String,String> h = new LinkedHashMap<>();
        if (headers != null) h.putAll(headers);
        h.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        return request("POST", url, h, sb.toString().getBytes(StandardCharsets.UTF_8), true, 7000, 10000);
    }

    public static Response postMultipart(String url, Map<String, String> headers, Map<String, String> fields, int timeoutMs) throws Exception {
        String boundary = "----XingyuLX" + Long.toHexString(System.nanoTime());
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        if (fields != null) {
            for (Map.Entry<String,String> e : fields.entrySet()) {
                bytes.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                bytes.write(("Content-Disposition: form-data; name=\"" + e.getKey().replace("\"", "") + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                bytes.write((e.getValue() == null ? "" : e.getValue()).getBytes(StandardCharsets.UTF_8));
                bytes.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
        }
        bytes.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        Map<String,String> h = new LinkedHashMap<>();
        if (headers != null) h.putAll(headers);
        h.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        return request("POST", url, h, bytes.toByteArray(), true, Math.min(Math.max(3000, timeoutMs), 18000), Math.max(5000, timeoutMs));
    }

    private static Response request(String method, String url, Map<String,String> headers, byte[] body, boolean redirects, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = open(method, url, headers, redirects, connectMs, readMs);
        if (body != null) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
        }
        int code = c.getResponseCode();
        String finalUrl = c.getURL().toString();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        byte[] raw = readBytes(in);
        String text = new String(raw, StandardCharsets.UTF_8);
        Map<String,String> responseHeaders = flattenHeaders(c);
        c.disconnect();
        Response response = new Response(code, text, finalUrl, responseHeaders, raw);
        // V38 observation-only hook: summarizes an already-completed Huibq response.
        // It does not perform/retry requests and cannot mutate the response or route health.
        ResolverHttpDiagnostics.observe(url, response);
        return response;
    }

    private static HttpURLConnection open(String method, String url, Map<String,String> headers, boolean redirects, int connectMs, int readMs) throws Exception {
        return configure((HttpURLConnection) new URL(url).openConnection(), method, headers, redirects, connectMs, readMs);
    }

    private static HttpURLConnection configure(HttpURLConnection c, String method, Map<String,String> headers, boolean redirects, int connectMs, int readMs) throws Exception {
        c.setRequestMethod(method);
        c.setConnectTimeout(Math.max(3000, connectMs));
        c.setReadTimeout(Math.max(5000, readMs));
        c.setInstanceFollowRedirects(redirects);
        c.setUseCaches(false);
        boolean hasEncoding = false;
        if (headers != null) for (Map.Entry<String,String> e : headers.entrySet()) {
            c.setRequestProperty(e.getKey(), e.getValue());
            if ("Accept-Encoding".equalsIgnoreCase(e.getKey())) hasEncoding = true;
        }
        if (!hasEncoding) c.setRequestProperty("Accept-Encoding", "identity");
        return c;
    }

    private static Map<String,String> flattenHeaders(HttpURLConnection c) {
        Map<String,String> out = new LinkedHashMap<>();
        try {
            Map<String, java.util.List<String>> all = c.getHeaderFields();
            if (all != null) for (Map.Entry<String, java.util.List<String>> e : all.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
                out.put(e.getKey(), String.join(", ", e.getValue()));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static byte[] readBytes(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = input.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
