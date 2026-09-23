package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * V87 Search Engine V2 display cache.
 *
 * Stores only canonical song metadata/variants and provider result counts. It never stores audio
 * URLs, resolver tokens, playback health or Media3 state. Entries are small, bounded and used as a
 * stale-while-revalidate first paint while fresh catalog searches continue in the background.
 */
public final class SearchCacheStore {
    private static final String PREFS = "lunaxy_search_cache_v2";
    private static final String INDEX = "index";
    private static final int MAX_ENTRIES = 32;
    private static final int MAX_SONGS = 36;
    private static final long MAX_AGE_MS = 24L * 60L * 60L * 1000L;

    private final SharedPreferences prefs;

    public SearchCacheStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Entry get(String query) {
        String normalized = normalizeQuery(query);
        if (normalized.isEmpty()) return null;
        String id = id(normalized);
        String raw = prefs.getString("e_" + id, "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(raw);
            if (!normalized.equals(o.optString("q", ""))) return null;
            long savedAt = o.optLong("ts", 0L);
            long age = Math.max(0L, System.currentTimeMillis() - savedAt);
            if (savedAt <= 0L || age > MAX_AGE_MS) return null;
            List<Song> songs = new ArrayList<>();
            JSONArray a = o.optJSONArray("songs");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject song = a.optJSONObject(i);
                if (song != null) songs.add(Song.fromJson(song));
            }
            if (songs.isEmpty()) return null;
            LinkedHashMap<String,Integer> totals = new LinkedHashMap<>();
            JSONObject t = o.optJSONObject("totals");
            for (String source : new String[]{"wy","tx","kw","kg"}) totals.put(source, t == null ? 0 : Math.max(0, t.optInt(source, 0)));
            touch(id);
            return new Entry(query == null ? "" : query.trim(), savedAt, songs, totals);
        } catch (Exception ignored) {
            prefs.edit().remove("e_" + id).apply();
            return null;
        }
    }

    public void put(String query, List<Song> songs, Map<String,Integer> totals) {
        String normalized = normalizeQuery(query);
        if (normalized.isEmpty() || songs == null || songs.isEmpty()) return;
        String id = id(normalized);
        try {
            JSONObject o = new JSONObject();
            o.put("q", normalized);
            o.put("display", query == null ? "" : query.trim());
            o.put("ts", System.currentTimeMillis());
            JSONArray a = new JSONArray();
            int count = 0;
            for (Song song : songs) {
                if (song == null) continue;
                a.put(song.toJson());
                if (++count >= MAX_SONGS) break;
            }
            o.put("songs", a);
            JSONObject t = new JSONObject();
            for (String source : new String[]{"wy","tx","kw","kg"}) t.put(source, totals == null ? 0 : Math.max(0, totals.getOrDefault(source, 0)));
            o.put("totals", t);
            SharedPreferences.Editor e = prefs.edit().putString("e_" + id, o.toString());
            List<String> index = readIndex();
            index.remove(id); index.add(0, id);
            while (index.size() > MAX_ENTRIES) {
                String removed = index.remove(index.size() - 1);
                e.remove("e_" + removed);
            }
            e.putString(INDEX, new JSONArray(index).toString()).apply();
        } catch (Exception ignored) { }
    }

    public void clear() { prefs.edit().clear().apply(); }

    private void touch(String id) {
        List<String> index = readIndex();
        if (!index.remove(id)) return;
        index.add(0, id);
        prefs.edit().putString(INDEX, new JSONArray(index).toString()).apply();
    }

    private List<String> readIndex() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(INDEX, "[]"));
            for (int i = 0; i < a.length(); i++) {
                String v = a.optString(i, "");
                if (!v.isEmpty() && !out.contains(v)) out.add(v);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static String normalizeQuery(String q) {
        return (q == null ? "" : q.trim().toLowerCase(Locale.ROOT)).replaceAll("\\s+", " ");
    }

    private static String id(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 10 && i < b.length; i++) s.append(String.format(Locale.US, "%02x", b[i] & 0xff));
            return s.toString();
        } catch (Exception ignored) { return Integer.toHexString(text.hashCode()); }
    }

    public static final class Entry {
        public final String query;
        public final long savedAt;
        public final List<Song> songs;
        public final Map<String,Integer> totals;
        Entry(String query, long savedAt, List<Song> songs, Map<String,Integer> totals) {
            this.query = query; this.savedAt = savedAt; this.songs = songs; this.totals = totals;
        }
    }
}
