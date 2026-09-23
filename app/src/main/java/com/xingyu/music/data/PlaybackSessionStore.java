package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Durable local playback-session memory.
 *
 * Stores only user playback state: queue order, current item and progress. It does not store
 * resolved audio URLs, provider health, transient errors or autoplay intent. A restored session
 * therefore comes back paused and reuses the normal resolver only when the user presses Play.
 */
public final class PlaybackSessionStore {
    private static final String PREFS = "lunaxy_playback_session_v1";
    private static final String KEY_SESSION = "session";
    private static final int SCHEMA = 1;

    private final SharedPreferences prefs;

    public PlaybackSessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Session {
        public final List<Song> queue;
        public final int index;
        public final String currentSongKey;
        public final long positionMs;
        public final long durationMs;
        public final long updatedAt;

        Session(List<Song> queue, int index, String currentSongKey,
                long positionMs, long durationMs, long updatedAt) {
            this.queue = queue;
            this.index = index;
            this.currentSongKey = currentSongKey == null ? "" : currentSongKey;
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            this.updatedAt = Math.max(0L, updatedAt);
        }
    }

    public Session load() {
        String raw = prefs.getString(KEY_SESSION, "");
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(raw);
            if (root.optInt("schema", 0) != SCHEMA) return null;
            JSONArray songsJson = root.optJSONArray("queue");
            if (songsJson == null || songsJson.length() == 0) return null;

            List<Song> queue = new ArrayList<>();
            for (int i = 0; i < songsJson.length(); i++) {
                JSONObject item = songsJson.optJSONObject(i);
                if (item != null) queue.add(Song.fromJson(item));
            }
            if (queue.isEmpty()) return null;

            String currentKey = root.optString("currentSongKey", "");
            int index = root.optInt("index", 0);
            if (index < 0 || index >= queue.size()
                    || (!currentKey.isEmpty() && !currentKey.equals(queue.get(index).key()))) {
                int matched = -1;
                if (!currentKey.isEmpty()) {
                    for (int i = 0; i < queue.size(); i++) {
                        if (currentKey.equals(queue.get(i).key())) {
                            matched = i;
                            break;
                        }
                    }
                }
                index = matched >= 0 ? matched : Math.max(0, Math.min(index, queue.size() - 1));
            }

            Song current = queue.get(index);
            long duration = Math.max(root.optLong("durationMs", 0L), current.durationMs);
            long position = Math.max(0L, root.optLong("positionMs", 0L));
            if (duration > 0L) position = Math.min(position, duration);

            return new Session(queue, index, current.key(), position, duration,
                    root.optLong("updatedAt", 0L));
        } catch (Exception ignored) {
            // A truncated/corrupt checkpoint must never block startup. Remove it and start clean.
            prefs.edit().remove(KEY_SESSION).apply();
            return null;
        }
    }

    public boolean save(List<Song> queue, int index, long positionMs, long durationMs,
                        boolean synchronous) {
        if (queue == null || queue.isEmpty() || index < 0 || index >= queue.size()) return false;
        try {
            JSONArray songsJson = new JSONArray();
            for (Song song : queue) {
                if (song == null) return false;
                songsJson.put(song.toJson());
            }
            if (songsJson.length() == 0) return false;

            Song current = queue.get(index);
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            root.put("queue", songsJson);
            root.put("index", index);
            root.put("currentSongKey", current == null ? "" : current.key());
            root.put("positionMs", Math.max(0L, positionMs));
            root.put("durationMs", Math.max(0L, durationMs));
            root.put("updatedAt", System.currentTimeMillis());

            SharedPreferences.Editor editor = prefs.edit().putString(KEY_SESSION, root.toString());
            if (synchronous) return editor.commit();
            editor.apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void clear(boolean synchronous) {
        SharedPreferences.Editor editor = prefs.edit().remove(KEY_SESSION);
        if (synchronous) editor.commit(); else editor.apply();
    }
}
