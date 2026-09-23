package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Lightweight adaptive search-source scheduler. Search only; never affects playback routing. */
public final class SearchPerformanceStore {
    private static final String PREFS = "lunaxy_search_perf_v2";
    private final SharedPreferences prefs;

    public SearchPerformanceStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void record(String source, long latencyMs, boolean success, int count) {
        if (!valid(source)) return;
        String p = source + "_";
        int ok = prefs.getInt(p + "ok", 0);
        int fail = prefs.getInt(p + "fail", 0);
        float old = prefs.getFloat(p + "ema", defaultLatency(source));
        float sample = Math.max(40f, Math.min(8000f, latencyMs));
        float ema = old <= 0f ? sample : old * .72f + sample * .28f;
        SharedPreferences.Editor e = prefs.edit().putFloat(p + "ema", ema)
                .putLong(p + "last", latencyMs).putInt(p + "count", Math.max(0, count));
        if (success) e.putInt(p + "ok", Math.min(5000, ok + 1)); else e.putInt(p + "fail", Math.min(5000, fail + 1));
        if (ok + fail > 4500) {
            e.putInt(p + "ok", Math.max(1, ok / 2)); e.putInt(p + "fail", fail / 2);
        }
        e.apply();
    }

    public List<String> orderedSources() {
        ArrayList<String> out = new ArrayList<>();
        Collections.addAll(out, "wy", "tx", "kg", "kw");
        out.sort(Comparator.comparingDouble(this::schedulerScore));
        return out;
    }

    public Stats stats(String source) {
        String p = source + "_";
        int ok = prefs.getInt(p + "ok", 0), fail = prefs.getInt(p + "fail", 0);
        float ema = prefs.getFloat(p + "ema", defaultLatency(source));
        long last = prefs.getLong(p + "last", 0L);
        int count = prefs.getInt(p + "count", 0);
        double success = ok + fail <= 0 ? 1d : ok / (double) (ok + fail);
        return new Stats(source, Math.round(ema), last, success, count, ok + fail);
    }

    private double schedulerScore(String source) {
        Stats s = stats(source);
        double failurePenalty = (1d - s.successRate) * 1600d;
        double coldPenalty = s.samples <= 0 ? 80d : 0d;
        return s.emaLatencyMs + failurePenalty + coldPenalty;
    }

    private static boolean valid(String source) {
        return "wy".equals(source) || "tx".equals(source) || "kw".equals(source) || "kg".equals(source);
    }

    private static float defaultLatency(String source) {
        if ("wy".equals(source)) return 360f;
        if ("tx".equals(source)) return 430f;
        if ("kg".equals(source)) return 620f;
        return 720f;
    }

    public static final class Stats {
        public final String source;
        public final long emaLatencyMs;
        public final long lastLatencyMs;
        public final double successRate;
        public final int lastCount;
        public final int samples;
        Stats(String source, long emaLatencyMs, long lastLatencyMs, double successRate, int lastCount, int samples) {
            this.source = source; this.emaLatencyMs = emaLatencyMs; this.lastLatencyMs = lastLatencyMs;
            this.successRate = successRate; this.lastCount = lastCount; this.samples = samples;
        }

        public String compact() {
            return String.format(Locale.US, "%dms · %.0f%%", emaLatencyMs, successRate * 100d);
        }
    }
}
