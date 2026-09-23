package com.xingyu.music.download;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Download-only route history. Never mutates playback route/health state. */
public final class DownloadRouteStore {
    private static final long MAX_AGE = 30L * 24L * 60L * 60L * 1000L;
    private static final long HALF_LIFE = 7L * 24L * 60L * 60L * 1000L;
    private final SharedPreferences prefs;
    public DownloadRouteStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("lunaxy_download_routes_v1", Context.MODE_PRIVATE);
    }

    public String preferred(String songKey, String quality) {
        try {
            String raw = prefs.getString(key(songKey, quality), "");
            if (raw == null || raw.isEmpty()) return "";
            JSONObject o = new JSONObject(raw);
            if (System.currentTimeMillis() - o.optLong("at", 0L) > MAX_AGE) return "";
            String platform = o.optString("platform", ""), provider = o.optString("provider", "");
            return platform.isEmpty() || provider.isEmpty() ? "" : platform + "|" + provider;
        } catch (Exception ignored) { return ""; }
    }


    /** Download-only affinity also fades so yesterday's successful route is a hint, not a lock. */
    public int affinityBonus(String songKey, String quality, String platform, String provider) {
        try {
            String raw = prefs.getString(key(songKey, quality), "");
            if (raw == null || raw.isEmpty()) return 0;
            JSONObject o = new JSONObject(raw);
            long at = o.optLong("at", 0L);
            long age = Math.max(0L, System.currentTimeMillis() - at);
            if (at <= 0L || age > MAX_AGE) return 0;
            if (!safe(platform).equals(o.optString("platform", "")) || !safe(provider).equals(o.optString("provider", ""))) return 0;
            double factor = Math.pow(0.5d, age / (double)HALF_LIFE);
            return Math.max(0, (int)Math.round(12_000d * factor));
        } catch (Exception ignored) { return 0; }
    }

    public void resetLearning() { prefs.edit().clear().apply(); }

    public void markSuccess(String songKey, String quality, String platform, String provider) {
        try {
            JSONObject o = new JSONObject();
            o.put("platform", platform == null ? "" : platform);
            o.put("provider", provider == null ? "" : provider);
            o.put("at", System.currentTimeMillis());
            prefs.edit().putString(key(songKey, quality), o.toString()).apply();
        } catch (Exception ignored) { }
    }

    public void markFailure(String songKey, String quality, String platform, String provider, String reason) {
        String k = "f." + key(songKey, quality) + "." + safe(platform) + "." + safe(provider);
        prefs.edit().putString(k, (reason == null ? "" : reason) + "\n" + System.currentTimeMillis()).apply();
    }

    private static String key(String songKey, String quality) { return sha256(songKey) + "." + safe(quality); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff)); return b.toString();
        } catch (Exception e) { return Integer.toHexString((value == null ? "" : value).hashCode()); }
    }
}
