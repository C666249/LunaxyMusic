package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Persistent URL cache. V8 also remembers which resolver produced the URL. */
public final class PlaybackUrlStore {
    public static final class Entry {
        public final String url;
        public final long savedAt;
        public final String providerId;
        public final String providerLabel;
        Entry(String url, long savedAt, String providerId, String providerLabel) {
            this.url = url;
            this.savedAt = savedAt;
            this.providerId = providerId == null ? "" : providerId;
            this.providerLabel = providerLabel == null ? "" : providerLabel;
        }
    }

    private final SharedPreferences prefs;

    public PlaybackUrlStore(Context context) {
        prefs = context.getSharedPreferences("xingyu_playback_urls_v8", Context.MODE_PRIVATE);
    }

    public Entry get(String source, String id, String quality) {
        String raw = prefs.getString(key(source, id, quality), null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(raw);
            String url = o.optString("url", "");
            if (!url.startsWith("http")) return null;
            return new Entry(url, o.optLong("savedAt", 0L),
                    o.optString("providerId", ""), o.optString("providerLabel", ""));
        } catch (Exception e) { return null; }
    }

    public void put(String source, String id, String quality, String url,
                    String providerId, String providerLabel) {
        if (url == null || !url.startsWith("http")) return;
        try {
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("savedAt", System.currentTimeMillis());
            o.put("providerId", providerId == null ? "" : providerId);
            o.put("providerLabel", providerLabel == null ? "" : providerLabel);
            prefs.edit().putString(key(source, id, quality), o.toString()).apply();
        } catch (Exception ignored) { }
    }

    public void invalidate(String source, String id, String quality) {
        prefs.edit().remove(key(source, id, quality)).apply();
    }

    public void invalidateProvider(String source, String id) {
        prefs.edit().remove(key(source, id, "320k")).remove(key(source, id, "128k")).apply();
    }

    private static String key(String source, String id, String quality) {
        return source + ":" + id + ":" + quality;
    }
}
