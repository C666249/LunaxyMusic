package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Health/circuit-breaker telemetry and decayed route learning shared by Lunaxy providers.
 *
 * READY/FAIL/latency are diagnostics; AudioProviderHub owns current routing.
 * A transient track failure must not create a permanent global preference.
 * The legacy rank() helper remains only so the unused V47 MultiSourceManager can
 * compile as a rollback reference.
 */
public final class SourceHealthStore {
    // V66 Learning V4: route preference is recent evidence, not lifetime baggage.
    // A 14-day half-life lets old successes/failures fade gradually back toward neutral while
    // lifetime READY/FAIL counters remain available for diagnostics.
    private static final long LEARNING_HALF_LIFE_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final float LEARNING_MAX = 8.0f;
    private static final float LEARNING_SUCCESS_STEP = 1.20f;
    private static final float LEARNING_FAILURE_STEP = 0.85f;
    private final SharedPreferences prefs;

    public SourceHealthStore(Context c) {
        prefs = c.getSharedPreferences("source_health_v8", Context.MODE_PRIVATE);
    }

    public static String routeKey(String providerId, String platform, String quality) {
        return safe(providerId) + "|" + safe(platform) + "|" + safe(quality);
    }

    private static String platformKey(String providerId, String platform) {
        return safe(providerId) + "|" + safe(platform) + "|*";
    }

    private static String globalKey(String providerId) { return safe(providerId) + "|*|*"; }

    public void resolverCandidate(String providerId, String platform, String quality, long latencyMs) {
        String k = routeKey(providerId, platform, quality);
        long old = prefs.getLong(k + ".lat", latencyMs);
        long ema = old <= 0 ? latencyMs : (old * 3 + latencyMs) / 4;
        prefs.edit().putLong(k + ".lat", Math.max(1, ema))
                .putLong(k + ".lastCandidate", System.currentTimeMillis()).apply();
    }

    public void playbackSuccess(String providerId, String platform, String quality, long latencyMs) {
        String k = routeKey(providerId, platform, quality);
        String pk = platformKey(providerId, platform);
        String gk = globalKey(providerId);
        int ok = Math.min(500, prefs.getInt(k + ".ok", 0) + 1);
        int fail = Math.max(0, prefs.getInt(k + ".fail", 0) - 1);
        long old = prefs.getLong(k + ".lat", latencyMs);
        long ema = old <= 0 ? latencyMs : (old * 3 + Math.max(1, latencyMs)) / 4;
        long now = System.currentTimeMillis();
        prefs.edit().putInt(k + ".ok", ok).putInt(k + ".fail", fail)
                .putLong(k + ".lat", ema).putLong(k + ".lastOk", now)
                .remove(k + ".cooldown").remove(k + ".reason")
                .remove(pk + ".cooldown").remove(pk + ".reason")
                .remove(gk + ".cooldown").remove(gk + ".reason")
                .putString(providerId + ".lastRoute", platform + "/" + quality)
                .putLong(providerId + ".lastOk", now).apply();
        bumpLearning(k, LEARNING_SUCCESS_STEP, now);
    }

    /** Historical/statistical failure. cooldownMs is only a short route breaker, never a ranking penalty. */
    public void routeFailure(String providerId, String platform, String quality, String reason, long cooldownMs) {
        String k = routeKey(providerId, platform, quality);
        int fail = Math.min(500, prefs.getInt(k + ".fail", 0) + 1);
        long until = cooldownMs > 0 ? System.currentTimeMillis() + cooldownMs : 0L;
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = prefs.edit().putInt(k + ".fail", fail)
                .putString(k + ".reason", reason == null ? "" : reason)
                .putLong(k + ".lastFail", now)
                .putString(providerId + ".lastReason", reason == null ? "" : reason)
                .putLong(providerId + ".lastFail", now);
        if (cooldownMs > 0) e.putLong(k + ".cooldown", until);
        e.apply();
        bumpLearning(k, -LEARNING_FAILURE_STEP, now);
    }

    /** Route-only breaker without adding another historical failure count. */
    public void routeCooldown(String providerId, String platform, String quality, String reason, long cooldownMs) {
        if (cooldownMs <= 0) return;
        String k = routeKey(providerId, platform, quality);
        prefs.edit().putString(k + ".reason", reason == null ? "" : reason)
                .putLong(k + ".cooldown", System.currentTimeMillis() + cooldownMs)
                .putString(providerId + ".lastReason", reason == null ? "" : reason)
                .putLong(providerId + ".lastFail", System.currentTimeMillis()).apply();
    }

    /** Platform-only breaker: e.g. QDY's QQ route can rest while QDY Netease remains available. */
    public void platformFailure(String providerId, String platform, String reason, long cooldownMs) {
        if (cooldownMs <= 0) return;
        String k = platformKey(providerId, platform);
        prefs.edit().putString(k + ".reason", reason == null ? "" : reason)
                .putLong(k + ".cooldown", System.currentTimeMillis() + cooldownMs)
                .putString(providerId + ".lastReason", reason == null ? "" : reason)
                .putLong(providerId + ".lastFail", System.currentTimeMillis()).apply();
    }

    /** Provider-wide breaker: reserved for real service outages, rate limits and IP blocks. */
    public void globalFailure(String providerId, String reason, long cooldownMs) {
        if (cooldownMs <= 0) return;
        String k = globalKey(providerId);
        prefs.edit().putString(k + ".reason", reason == null ? "" : reason)
                .putLong(k + ".cooldown", System.currentTimeMillis() + cooldownMs)
                .putString(providerId + ".lastReason", reason == null ? "" : reason)
                .putLong(providerId + ".lastFail", System.currentTimeMillis()).apply();
    }

    public boolean cooling(String providerId, String platform, String quality) {
        long now = System.currentTimeMillis();
        return prefs.getLong(globalKey(providerId) + ".cooldown", 0L) > now
                || prefs.getLong(platformKey(providerId, platform) + ".cooldown", 0L) > now
                || prefs.getLong(routeKey(providerId, platform, quality) + ".cooldown", 0L) > now;
    }

    public long cooldownRemaining(String providerId, String platform, String quality) {
        long now = System.currentTimeMillis();
        long a = prefs.getLong(globalKey(providerId) + ".cooldown", 0L);
        long b = prefs.getLong(platformKey(providerId, platform) + ".cooldown", 0L);
        long c = prefs.getLong(routeKey(providerId, platform, quality) + ".cooldown", 0L);
        return Math.max(0, Math.max(a, Math.max(b, c)) - now);
    }

    public int ok(String providerId, String platform, String quality) {
        return prefs.getInt(routeKey(providerId, platform, quality) + ".ok", 0);
    }

    public int fail(String providerId, String platform, String quality) {
        return prefs.getInt(routeKey(providerId, platform, quality) + ".fail", 0);
    }

    public long latency(String providerId, String platform, String quality) {
        return prefs.getLong(routeKey(providerId, platform, quality) + ".lat", 0L);
    }

    public String reason(String providerId, String platform, String quality) {
        String route = prefs.getString(routeKey(providerId, platform, quality) + ".reason", "");
        if (route != null && !route.isEmpty()) return route;
        String platformReason = prefs.getString(platformKey(providerId, platform) + ".reason", "");
        if (platformReason != null && !platformReason.isEmpty()) return platformReason;
        return prefs.getString(globalKey(providerId) + ".reason", "");
    }

    public String lastRoute(String providerId) { return prefs.getString(providerId + ".lastRoute", ""); }
    public String lastReason(String providerId) { return compactReason(prefs.getString(providerId + ".lastReason", "")); }

    // V67 observation-only dashboard getters. These methods never mutate learning/cooldown state.
    public long lastOkAt(String providerId, String platform, String quality) {
        return prefs.getLong(routeKey(providerId, platform, quality) + ".lastOk", 0L);
    }

    public long lastFailAt(String providerId, String platform, String quality) {
        return prefs.getLong(routeKey(providerId, platform, quality) + ".lastFail", 0L);
    }

    public float peekLearningScore(String providerId, String platform, String quality) {
        return previewDecayedLearning(routeKey(providerId, platform, quality), System.currentTimeMillis());
    }

    public float peekPlatformLearningScore(String providerId, String platform) {
        float a = peekLearningScore(providerId, platform, "320k");
        float b = peekLearningScore(providerId, platform, "128k");
        if (Math.abs(a) < 0.001f) return b;
        if (Math.abs(b) < 0.001f) return a;
        return (a + b) * 0.5f;
    }

    private float previewDecayedLearning(String routeKey, long now) {
        long at = prefs.getLong(routeKey + ".learnAt", 0L);
        float value;
        if (at <= 0L) {
            int ok = prefs.getInt(routeKey + ".ok", 0);
            int fail = prefs.getInt(routeKey + ".fail", 0);
            if (ok == 0 && fail == 0) return 0f;
            double seed = Math.log1p(ok) * 0.55d - Math.log1p(fail) * 0.45d;
            value = (float)Math.max(-1.5d, Math.min(2.0d, seed));
        } else {
            value = prefs.getFloat(routeKey + ".learnScore", 0f);
        }
        long age = at <= 0L ? 0L : Math.max(0L, now - at);
        if (age > 0L && Math.abs(value) >= 0.001f) {
            double factor = Math.pow(0.5d, age / (double) LEARNING_HALF_LIFE_MS);
            value = (float)(value * factor);
        }
        return Math.abs(value) < 0.015f ? 0f : value;
    }

    public int platformOk(String providerId, String platform) {
        int total = 0;
        for (String q : new String[]{"320k","128k"}) total += ok(providerId, platform, q);
        return total;
    }

    public int platformFail(String providerId, String platform) {
        int total = 0;
        for (String q : new String[]{"320k","128k"}) total += fail(providerId, platform, q);
        return total;
    }

    public int totalOk(String providerId) {
        int total = 0;
        for (String p : new String[]{"wy","tx","kw","kg","mg"})
            for (String q : new String[]{"320k","128k"}) total += ok(providerId, p, q);
        return total;
    }

    public int totalFail(String providerId) {
        int total = 0;
        for (String p : new String[]{"wy","tx","kw","kg","mg"})
            for (String q : new String[]{"320k","128k"}) total += fail(providerId, p, q);
        return total;
    }


    /**
     * Recent route confidence used by the V66 matrix. It continuously decays toward zero, so an
     * upstream that was excellent or broken weeks ago eventually becomes neutral unless new real
     * evidence arrives. Old V65 lifetime counters are imported only as a very small capped seed.
     */
    public float learningScore(String providerId, String platform, String quality) {
        return decayedLearning(routeKey(providerId, platform, quality), System.currentTimeMillis(), true);
    }

    public float platformLearningScore(String providerId, String platform) {
        float a = learningScore(providerId, platform, "320k");
        float b = learningScore(providerId, platform, "128k");
        if (Math.abs(a) < 0.001f) return b;
        if (Math.abs(b) < 0.001f) return a;
        return (a + b) * 0.5f;
    }

    /**
     * Soft reset: remove learned bias/statistics but keep active circuit-breaker cooldowns and
     * their reasons. Playlist/library/variant/READY URL data live elsewhere and are untouched.
     */
    public void resetLearning() {
        SharedPreferences.Editor e = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.endsWith(".ok") || key.endsWith(".fail") || key.endsWith(".lat")
                    || key.endsWith(".lastCandidate") || key.endsWith(".lastOk") || key.endsWith(".lastFail")
                    || key.endsWith(".learnScore") || key.endsWith(".learnAt") || key.endsWith(".lastRoute")
                    || key.endsWith(".lastReason")) {
                e.remove(key);
            }
        }
        e.apply();
    }

    public static int learningHalfLifeDays() { return 14; }

    private void bumpLearning(String routeKey, float delta, long now) {
        float value = decayedLearning(routeKey, now, true);
        value = Math.max(-LEARNING_MAX, Math.min(LEARNING_MAX, value + delta));
        prefs.edit().putFloat(routeKey + ".learnScore", value).putLong(routeKey + ".learnAt", now).apply();
    }

    private float decayedLearning(String routeKey, long now, boolean seedLegacy) {
        long at = prefs.getLong(routeKey + ".learnAt", 0L);
        float value;
        if (at <= 0L) {
            if (!seedLegacy) return 0f;
            int ok = prefs.getInt(routeKey + ".ok", 0);
            int fail = prefs.getInt(routeKey + ".fail", 0);
            if (ok == 0 && fail == 0) return 0f;
            // Legacy history gets only a small logarithmic seed so V66 does not inherit giant
            // READY/fail counters as permanent routing truth. Fresh evidence quickly dominates it.
            double seed = Math.log1p(ok) * 0.55d - Math.log1p(fail) * 0.45d;
            value = (float)Math.max(-1.5d, Math.min(2.0d, seed));
            prefs.edit().putFloat(routeKey + ".learnScore", value).putLong(routeKey + ".learnAt", now).apply();
            return value;
        }
        value = prefs.getFloat(routeKey + ".learnScore", 0f);
        long age = Math.max(0L, now - at);
        if (age <= 0L || Math.abs(value) < 0.001f) return value;
        double factor = Math.pow(0.5d, age / (double) LEARNING_HALF_LIFE_MS);
        float decayed = (float)(value * factor);
        return Math.abs(decayed) < 0.015f ? 0f : decayed;
    }

    /** Legacy V47 ordering helper; Sunflower AudioProviderHub does not call this. */
    public List<ResolverProvider> rank(List<ResolverProvider> providers, String platform, String quality) {
        List<ResolverProvider> list = new ArrayList<>(providers);
        list.sort(Comparator.comparingInt(p -> fixedOrder(p.id, platform)));
        return list;
    }

    private static int fixedOrder(String providerId, String platform) {
        if ("tx".equals(platform)) {
            if ("huibq".equals(providerId)) return 0;
            if ("qdy".equals(providerId)) return 1;
        }
        if ("wy".equals(platform)) {
            if ("qdy".equals(providerId)) return 0;
            if ("huibq".equals(providerId)) return 1;
        }
        if ("qdy".equals(providerId)) return 0;
        if ("huibq".equals(providerId)) return 1;
        return 100;
    }

    private static String compactReason(String s) {
        if (s == null) return "";
        String first = s.replace('\r', '\n').split("\n", 2)[0].trim();
        return first.length() > 120 ? first.substring(0, 120) + "…" : first;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
