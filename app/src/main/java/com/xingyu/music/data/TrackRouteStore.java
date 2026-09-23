package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Deterministic READY affinity for the Provider x Catalog route matrix.
 * Only Media3 READY writes affinity. Fresh failures erase stale affinities fast.
 */
public final class TrackRouteStore {
    private static final long MAX_AGE = 45L * 24L * 60L * 60L * 1000L;
    private static final long AFFINITY_HALF_LIFE = 7L * 24L * 60L * 60L * 1000L;
    private final SharedPreferences prefs;

    public TrackRouteStore(Context context) {
        // Keep the same preference file so V2 per-platform READY history remains useful.
        prefs = context.getSharedPreferences("lunaxy_track_routes_v1", Context.MODE_PRIVATE);
    }

    /** V2-compatible provider preference inside one platform. */
    public String preferred(String canonicalKey, String platform, String quality) {
        JSONObject o = read(key(canonicalKey, platform, quality));
        return valid(o) ? o.optString("providerId", "") : "";
    }

    /** V3 global route affinity: platform + provider for this exact canonical track/quality. */
    public String preferredRoute(String canonicalKey, String quality) {
        JSONObject o = read(globalKey(canonicalKey, quality));
        if (!valid(o)) return "";
        String p = o.optString("platform", ""), provider = o.optString("providerId", "");
        return p.isEmpty() || provider.isEmpty() ? "" : p + "|" + provider;
    }

    public void markReady(String canonicalKey, String platform, String quality,
                          String providerId, String providerLabel) {
        if (providerId == null || providerId.isEmpty()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("providerId", providerId); o.put("providerLabel", providerLabel == null ? "" : providerLabel);
            o.put("platform", platform == null ? "" : platform); o.put("readyAt", System.currentTimeMillis()); o.put("freshFail", 0);
            prefs.edit()
                    .putString(key(canonicalKey, platform, quality), o.toString())
                    .putString(globalKey(canonicalKey, quality), o.toString())
                    .apply();
        } catch (Exception ignored) { }
    }


    /**
     * V66 route affinity bonus. A freshly READY exact-track route still gets a strong head start,
     * but the preference fades continuously instead of staying a 45-day near-permanent lock.
     */
    public int globalAffinityBonus(String canonicalKey, String quality, String platform, String providerId) {
        JSONObject o = read(globalKey(canonicalKey, quality));
        return affinityBonus(o, platform, providerId, 18_000);
    }

    public int platformAffinityBonus(String canonicalKey, String platform, String quality, String providerId) {
        JSONObject o = read(key(canonicalKey, platform, quality));
        return affinityBonus(o, platform, providerId, 9_000);
    }

    public void resetLearning() { prefs.edit().clear().apply(); }

    private static int affinityBonus(JSONObject o, String platform, String providerId, int maxBonus) {
        if (!valid(o)) return 0;
        if (!safe(providerId).equals(o.optString("providerId", ""))) return 0;
        String storedPlatform = o.optString("platform", "");
        if (!storedPlatform.isEmpty() && !safe(platform).equals(storedPlatform)) return 0;
        long age = Math.max(0L, System.currentTimeMillis() - o.optLong("readyAt", 0L));
        double factor = Math.pow(0.5d, age / (double)AFFINITY_HALF_LIFE);
        if (o.optInt("freshFail", 0) > 0) factor *= 0.30d;
        return Math.max(0, (int)Math.round(maxBonus * factor));
    }

    public void markFreshFailure(String canonicalKey, String platform, String quality, String providerId) {
        markFailureAt(key(canonicalKey, platform, quality), providerId, platform);
        markFailureAt(globalKey(canonicalKey, quality), providerId, platform);
    }

    private void markFailureAt(String k, String providerId, String platform) {
        JSONObject o = read(k);
        if (o == null) return;
        if (!safe(providerId).equals(o.optString("providerId", ""))) return;
        String storedPlatform = o.optString("platform", "");
        if (!storedPlatform.isEmpty() && !safe(platform).equals(storedPlatform)) return;
        int fail = o.optInt("freshFail", 0) + 1;
        if (fail >= 2) prefs.edit().remove(k).apply();
        else {
            try { o.put("freshFail", fail); prefs.edit().putString(k, o.toString()).apply(); }
            catch (Exception ignored) { prefs.edit().remove(k).apply(); }
        }
    }

    private JSONObject read(String key) {
        String raw = prefs.getString(key, "");
        if (raw == null || raw.isEmpty()) return null;
        try { return new JSONObject(raw); } catch (Exception ignored) { return null; }
    }

    private static boolean valid(JSONObject o) {
        if (o == null) return false;
        long at = o.optLong("readyAt", 0L);
        return at > 0L && System.currentTimeMillis() - at <= MAX_AGE && o.optInt("freshFail", 0) < 2;
    }

    private static String key(String canonicalKey, String platform, String quality) {
        return "r." + sha256(canonicalKey) + "." + safe(platform) + "." + safe(quality);
    }
    private static String globalKey(String canonicalKey, String quality) {
        return "g." + sha256(canonicalKey) + "." + safe(quality);
    }
    private static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff)); return b.toString();
        } catch (Exception e) { return Integer.toHexString((value == null ? "" : value).hashCode()); }
    }
    private static String safe(String s) { return s == null ? "" : s; }
}
