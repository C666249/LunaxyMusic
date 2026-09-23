package com.xingyu.music.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.xingyu.music.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * V86 local personalization datastore.
 *
 * Deliberately stores behavior/metadata only. Audio URLs, route health, resolver state and
 * PlaybackUrlStore contents never enter this database. The recommendation plane can therefore
 * evolve without changing the proven playback plane.
 */
public final class PersonalizationStore {
    public static final String PLAY_START = "play_start";
    public static final String PROGRESS = "progress";
    public static final String COMPLETE = "complete";
    public static final String SKIP = "skip";
    public static final String REPLAY = "replay";
    public static final String FAVORITE_ADD = "favorite_add";
    public static final String FAVORITE_REMOVE = "favorite_remove";
    public static final String PLAYLIST_ADD = "playlist_add";
    public static final String PLAYLIST_REMOVE = "playlist_remove";

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private final Db db;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lunaxy-PersonalizationWriter");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger writeCount = new AtomicInteger();

    public PersonalizationStore(Context context) {
        db = new Db(context.getApplicationContext());
    }

    public void recordAsync(Song song, String type, long positionMs, long durationMs,
                            double ratio, String sessionId, long listenedDeltaMs) {
        if (song == null || type == null || type.trim().isEmpty()) return;
        writer.execute(() -> {
            try { recordNow(song, type, positionMs, durationMs, ratio, sessionId, listenedDeltaMs); }
            catch (Exception ignored) { }
        });
    }

    public void recordLibraryEvent(Song song, String type) {
        recordAsync(song, type, 0L, song == null ? 0L : song.durationMs, 0d, "library", 0L);
    }

    /**
     * Short ordering barrier used by recommendation generation on its IO thread. It makes skips,
     * completions and explicit library actions from the current session visible before ranking,
     * without turning normal playback callbacks into synchronous database writes.
     */
    public void awaitPendingWrites(long timeoutMs) {
        try {
            Future<?> barrier = writer.submit(() -> { });
            barrier.get(Math.max(50L, Math.min(1500L, timeoutMs)), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) { }
    }

    private void recordNow(Song song, String type, long positionMs, long durationMs,
                           double ratio, String sessionId, long listenedDeltaMs) {
        SQLiteDatabase sql = db.getWritableDatabase();
        long now = System.currentTimeMillis();
        sql.beginTransaction();
        try {
            ContentValues event = new ContentValues();
            event.put("event_type", type);
            event.put("track_key", song.key());
            event.put("title", song.title);
            event.put("artist", song.artist);
            event.put("album", song.album);
            event.put("duration_ms", Math.max(durationMs, song.durationMs));
            event.put("position_ms", Math.max(0L, positionMs));
            event.put("ratio", clamp(ratio, 0d, 1.2d));
            event.put("session_id", safe(sessionId));
            event.put("listened_delta_ms", Math.max(0L, listenedDeltaMs));
            event.put("ts", now);
            sql.insert("interaction_event", null, event);

            ensureTrack(sql, song);
            updateTrack(sql, song, type, listenedDeltaMs, now);
            for (String artist : splitArtists(song.artist)) {
                ensureArtist(sql, artist);
                updateArtist(sql, artist, type, listenedDeltaMs, now);
                updateDailyArtist(sql, artist, type, listenedDeltaMs, now);
            }
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }

        int count = writeCount.incrementAndGet();
        if (count % 64 == 0) pruneEvents(sql);
    }

    public Snapshot snapshot() {
        SQLiteDatabase sql = db.getReadableDatabase();
        long now = System.currentTimeMillis();
        Map<String, TrackSignal> tracks = new LinkedHashMap<>();
        try (Cursor c = sql.rawQuery("SELECT track_key,title,artist,album,duration_ms,starts,completes,skips,replays,favorite_adds,favorite_removes,playlist_adds,playlist_removes,listened_ms,last_played_at,last_event_at FROM track_stats ORDER BY last_event_at DESC LIMIT 1200", null)) {
            while (c.moveToNext()) {
                TrackSignal s = new TrackSignal();
                s.trackKey = c.getString(0); s.title = c.getString(1); s.artist = c.getString(2); s.album = c.getString(3);
                s.durationMs = c.getLong(4); s.starts = c.getInt(5); s.completes = c.getInt(6); s.skips = c.getInt(7);
                s.replays = c.getInt(8); s.favoriteAdds = c.getInt(9); s.favoriteRemoves = c.getInt(10);
                s.playlistAdds = c.getInt(11); s.playlistRemoves = c.getInt(12); s.listenedMs = c.getLong(13);
                s.lastPlayedAt = c.getLong(14); s.lastEventAt = c.getLong(15);
                tracks.put(s.trackKey, s);
            }
        }

        Map<String, ArtistSignal> artists = new LinkedHashMap<>();
        try (Cursor c = sql.rawQuery("SELECT artist_key,artist_name,starts,completes,skips,replays,favorite_adds,favorite_removes,playlist_adds,playlist_removes,listened_ms,last_played_at,last_event_at FROM artist_stats ORDER BY last_event_at DESC LIMIT 400", null)) {
            while (c.moveToNext()) {
                ArtistSignal s = new ArtistSignal();
                s.artistKey = c.getString(0); s.artistName = c.getString(1); s.starts = c.getInt(2); s.completes = c.getInt(3);
                s.skips = c.getInt(4); s.replays = c.getInt(5); s.favoriteAdds = c.getInt(6); s.favoriteRemoves = c.getInt(7);
                s.playlistAdds = c.getInt(8); s.playlistRemoves = c.getInt(9); s.listenedMs = c.getLong(10);
                s.lastPlayedAt = c.getLong(11); s.lastEventAt = c.getLong(12);
                artists.put(s.artistKey, s);
            }
        }

        List<DailyArtistSignal> dailyArtists = new ArrayList<>();
        long minDay = dayBucket(now - 180L * DAY);
        try (Cursor c = sql.rawQuery("SELECT day_bucket,artist_key,artist_name,starts,completes,skips,replays,favorite_adds,favorite_removes,playlist_adds,playlist_removes,listened_ms FROM artist_daily_stats WHERE day_bucket>=? ORDER BY day_bucket DESC LIMIT 3600", new String[]{String.valueOf(minDay)})) {
            while (c.moveToNext()) {
                DailyArtistSignal s = new DailyArtistSignal();
                s.dayBucket = c.getLong(0); s.artistKey = c.getString(1); s.artistName = c.getString(2);
                s.starts = c.getInt(3); s.completes = c.getInt(4); s.skips = c.getInt(5); s.replays = c.getInt(6);
                s.favoriteAdds = c.getInt(7); s.favoriteRemoves = c.getInt(8); s.playlistAdds = c.getInt(9); s.playlistRemoves = c.getInt(10);
                s.listenedMs = c.getLong(11);
                dailyArtists.add(s);
            }
        }

        List<RecentEvent> recent = new ArrayList<>();
        long since = now - 30L * DAY;
        try (Cursor c = sql.rawQuery("SELECT event_type,track_key,title,artist,ratio,session_id,listened_delta_ms,ts FROM interaction_event WHERE ts>=? ORDER BY ts DESC LIMIT 1600", new String[]{String.valueOf(since)})) {
            while (c.moveToNext()) {
                RecentEvent e = new RecentEvent();
                e.type = c.getString(0); e.trackKey = c.getString(1); e.title = c.getString(2); e.artist = c.getString(3);
                e.ratio = c.getDouble(4); e.sessionId = c.getString(5); e.listenedDeltaMs = c.getLong(6); e.ts = c.getLong(7);
                recent.add(e);
            }
        }
        return new Snapshot(now, tracks, artists, dailyArtists, recent);
    }

    /**
     * Returns every track identity that Lunaxy has ever persisted in the long-lived track aggregate.
     * Unlike snapshot(), this is intentionally not capped at the most recent 1200 tracks because
     * recommendation discovery uses it to avoid presenting an old heard recording as "never heard".
     * The table stores metadata keys only, not audio URLs.
     */
    public List<String> knownTrackKeys() {
        ArrayList<String> out = new ArrayList<>();
        SQLiteDatabase sql = db.getReadableDatabase();
        try (Cursor c = sql.rawQuery("SELECT track_key FROM track_stats", null)) {
            while (c.moveToNext()) {
                String key = safe(c.getString(0));
                if (!key.isEmpty()) out.add(key);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public String cachedArtistMbid(String artistName, long maxAgeMs) {
        String key = normalizeArtist(artistName);
        if (key.isEmpty()) return "";
        SQLiteDatabase sql = db.getReadableDatabase();
        try (Cursor c = sql.rawQuery("SELECT mbid,updated_at FROM external_artist_map WHERE artist_key=?", new String[]{key})) {
            if (!c.moveToFirst()) return "";
            String mbid = safe(c.getString(0)); long updatedAt = c.getLong(1);
            return !mbid.isEmpty() && System.currentTimeMillis() - updatedAt <= maxAgeMs ? mbid : "";
        }
    }

    public void cacheArtistMbid(String artistName, String mbid, int score) {
        String key = normalizeArtist(artistName);
        if (key.isEmpty() || mbid == null || mbid.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("artist_key", key); v.put("artist_name", safe(artistName)); v.put("mbid", mbid.trim());
        v.put("match_score", Math.max(0, Math.min(100, score))); v.put("updated_at", System.currentTimeMillis());
        db.getWritableDatabase().insertWithOnConflict("external_artist_map", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String cachedExternalJson(String cacheKey, long maxAgeMs) {
        if (cacheKey == null || cacheKey.trim().isEmpty()) return "";
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT payload,updated_at FROM external_cache WHERE cache_key=?", new String[]{cacheKey})) {
            if (!c.moveToFirst()) return "";
            String payload = safe(c.getString(0)); long updatedAt = c.getLong(1);
            return System.currentTimeMillis() - updatedAt <= maxAgeMs ? payload : "";
        }
    }

    public void cacheExternalJson(String cacheKey, String payload) {
        if (cacheKey == null || cacheKey.trim().isEmpty() || payload == null || payload.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("cache_key", cacheKey); v.put("payload", payload); v.put("updated_at", System.currentTimeMillis());
        db.getWritableDatabase().insertWithOnConflict("external_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public JSONObject diagnostics() {
        JSONObject o = new JSONObject();
        try {
            SQLiteDatabase sql = db.getReadableDatabase();
            o.put("events", scalar(sql, "SELECT COUNT(*) FROM interaction_event"));
            o.put("tracks", scalar(sql, "SELECT COUNT(*) FROM track_stats"));
            o.put("artists", scalar(sql, "SELECT COUNT(*) FROM artist_stats"));
            o.put("artistDailyRows", scalar(sql, "SELECT COUNT(*) FROM artist_daily_stats"));
            o.put("externalArtistMappings", scalar(sql, "SELECT COUNT(*) FROM external_artist_map"));
            o.put("externalCaches", scalar(sql, "SELECT COUNT(*) FROM external_cache"));
        } catch (Exception ignored) { }
        return o;
    }

    private static long scalar(SQLiteDatabase sql, String query) {
        try (Cursor c = sql.rawQuery(query, null)) { return c.moveToFirst() ? c.getLong(0) : 0L; }
    }

    private static void ensureTrack(SQLiteDatabase sql, Song song) {
        ContentValues v = new ContentValues();
        v.put("track_key", song.key()); v.put("title", song.title); v.put("artist", song.artist); v.put("album", song.album);
        v.put("duration_ms", song.durationMs); v.put("last_event_at", System.currentTimeMillis());
        sql.insertWithOnConflict("track_stats", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void ensureArtist(SQLiteDatabase sql, String artist) {
        ContentValues v = new ContentValues();
        v.put("artist_key", normalizeArtist(artist)); v.put("artist_name", artist); v.put("last_event_at", System.currentTimeMillis());
        sql.insertWithOnConflict("artist_stats", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void updateTrack(SQLiteDatabase sql, Song song, String type, long listenedDeltaMs, long now) {
        ContentValues v = new ContentValues();
        v.put("title", song.title); v.put("artist", song.artist); v.put("album", song.album); v.put("duration_ms", song.durationMs); v.put("last_event_at", now);
        if (PLAY_START.equals(type)) { v.put("starts", value(sql, "track_stats", "starts", "track_key", song.key()) + 1); v.put("last_played_at", now); }
        else if (COMPLETE.equals(type)) v.put("completes", value(sql, "track_stats", "completes", "track_key", song.key()) + 1);
        else if (SKIP.equals(type)) v.put("skips", value(sql, "track_stats", "skips", "track_key", song.key()) + 1);
        else if (REPLAY.equals(type)) v.put("replays", value(sql, "track_stats", "replays", "track_key", song.key()) + 1);
        else if (FAVORITE_ADD.equals(type)) v.put("favorite_adds", value(sql, "track_stats", "favorite_adds", "track_key", song.key()) + 1);
        else if (FAVORITE_REMOVE.equals(type)) v.put("favorite_removes", value(sql, "track_stats", "favorite_removes", "track_key", song.key()) + 1);
        else if (PLAYLIST_ADD.equals(type)) v.put("playlist_adds", value(sql, "track_stats", "playlist_adds", "track_key", song.key()) + 1);
        else if (PLAYLIST_REMOVE.equals(type)) v.put("playlist_removes", value(sql, "track_stats", "playlist_removes", "track_key", song.key()) + 1);
        if (listenedDeltaMs > 0) v.put("listened_ms", valueLong(sql, "track_stats", "listened_ms", "track_key", song.key()) + listenedDeltaMs);
        sql.update("track_stats", v, "track_key=?", new String[]{song.key()});
    }

    private static void updateArtist(SQLiteDatabase sql, String artist, String type, long listenedDeltaMs, long now) {
        String key = normalizeArtist(artist);
        if (key.isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("artist_name", artist); v.put("last_event_at", now);
        if (PLAY_START.equals(type)) { v.put("starts", value(sql, "artist_stats", "starts", "artist_key", key) + 1); v.put("last_played_at", now); }
        else if (COMPLETE.equals(type)) v.put("completes", value(sql, "artist_stats", "completes", "artist_key", key) + 1);
        else if (SKIP.equals(type)) v.put("skips", value(sql, "artist_stats", "skips", "artist_key", key) + 1);
        else if (REPLAY.equals(type)) v.put("replays", value(sql, "artist_stats", "replays", "artist_key", key) + 1);
        else if (FAVORITE_ADD.equals(type)) v.put("favorite_adds", value(sql, "artist_stats", "favorite_adds", "artist_key", key) + 1);
        else if (FAVORITE_REMOVE.equals(type)) v.put("favorite_removes", value(sql, "artist_stats", "favorite_removes", "artist_key", key) + 1);
        else if (PLAYLIST_ADD.equals(type)) v.put("playlist_adds", value(sql, "artist_stats", "playlist_adds", "artist_key", key) + 1);
        else if (PLAYLIST_REMOVE.equals(type)) v.put("playlist_removes", value(sql, "artist_stats", "playlist_removes", "artist_key", key) + 1);
        if (listenedDeltaMs > 0) v.put("listened_ms", valueLong(sql, "artist_stats", "listened_ms", "artist_key", key) + listenedDeltaMs);
        sql.update("artist_stats", v, "artist_key=?", new String[]{key});
    }

    private static void updateDailyArtist(SQLiteDatabase sql, String artist, String type, long listenedDeltaMs, long now) {
        String key = normalizeArtist(artist);
        if (key.isEmpty()) return;
        long day = dayBucket(now);
        ContentValues seed = new ContentValues();
        seed.put("day_bucket", day); seed.put("artist_key", key); seed.put("artist_name", artist);
        sql.insertWithOnConflict("artist_daily_stats", null, seed, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues v = new ContentValues();
        v.put("artist_name", artist);
        String where = "day_bucket=? AND artist_key=?";
        String[] args = new String[]{String.valueOf(day), key};
        if (PLAY_START.equals(type)) v.put("starts", dailyValue(sql, "starts", day, key) + 1);
        else if (COMPLETE.equals(type)) v.put("completes", dailyValue(sql, "completes", day, key) + 1);
        else if (SKIP.equals(type)) v.put("skips", dailyValue(sql, "skips", day, key) + 1);
        else if (REPLAY.equals(type)) v.put("replays", dailyValue(sql, "replays", day, key) + 1);
        else if (FAVORITE_ADD.equals(type)) v.put("favorite_adds", dailyValue(sql, "favorite_adds", day, key) + 1);
        else if (FAVORITE_REMOVE.equals(type)) v.put("favorite_removes", dailyValue(sql, "favorite_removes", day, key) + 1);
        else if (PLAYLIST_ADD.equals(type)) v.put("playlist_adds", dailyValue(sql, "playlist_adds", day, key) + 1);
        else if (PLAYLIST_REMOVE.equals(type)) v.put("playlist_removes", dailyValue(sql, "playlist_removes", day, key) + 1);
        if (listenedDeltaMs > 0L) v.put("listened_ms", dailyValueLong(sql, "listened_ms", day, key) + listenedDeltaMs);
        sql.update("artist_daily_stats", v, where, args);
    }

    private static int dailyValue(SQLiteDatabase sql, String column, long day, String key) {
        try (Cursor c = sql.query("artist_daily_stats", new String[]{column}, "day_bucket=? AND artist_key=?",
                new String[]{String.valueOf(day), key}, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static long dailyValueLong(SQLiteDatabase sql, String column, long day, String key) {
        try (Cursor c = sql.query("artist_daily_stats", new String[]{column}, "day_bucket=? AND artist_key=?",
                new String[]{String.valueOf(day), key}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    private static long dayBucket(long timestampMs) { return Math.max(0L, timestampMs / DAY); }

    private static int value(SQLiteDatabase sql, String table, String column, String keyCol, String key) {
        try (Cursor c = sql.query(table, new String[]{column}, keyCol + "=?", new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private static long valueLong(SQLiteDatabase sql, String table, String column, String keyCol, String key) {
        try (Cursor c = sql.query(table, new String[]{column}, keyCol + "=?", new String[]{key}, null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    private static void pruneEvents(SQLiteDatabase sql) {
        try {
            sql.execSQL("DELETE FROM interaction_event WHERE id NOT IN (SELECT id FROM interaction_event ORDER BY id DESC LIMIT 12000)");
            long stale = System.currentTimeMillis() - 120L * DAY;
            sql.delete("external_cache", "updated_at<?", new String[]{String.valueOf(stale)});
            long oldDaily = dayBucket(System.currentTimeMillis() - 400L * DAY);
            sql.delete("artist_daily_stats", "day_bucket<?", new String[]{String.valueOf(oldDaily)});
        } catch (Exception ignored) { }
    }

    public static List<String> splitArtists(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String[] parts = raw.split("[/、&,，·]+");
        for (String part : parts) {
            String clean = part == null ? "" : part.trim();
            if (clean.isEmpty() || clean.length() > 48) continue;
            boolean seen = false;
            for (String old : out) if (normalizeArtist(old).equals(normalizeArtist(clean))) { seen = true; break; }
            if (!seen) out.add(clean);
        }
        if (out.isEmpty() && !raw.trim().isEmpty()) out.add(raw.trim());
        return out;
    }

    public static String normalizeArtist(String value) {
        return (value == null ? "" : value.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()·]+", "");
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    public static final class Snapshot {
        public final long now;
        public final Map<String, TrackSignal> tracks;
        public final Map<String, ArtistSignal> artists;
        public final List<DailyArtistSignal> dailyArtists;
        public final List<RecentEvent> recentEvents;
        Snapshot(long now, Map<String, TrackSignal> tracks, Map<String, ArtistSignal> artists,
                 List<DailyArtistSignal> dailyArtists, List<RecentEvent> recentEvents) {
            this.now = now; this.tracks = tracks; this.artists = artists; this.dailyArtists = dailyArtists; this.recentEvents = recentEvents;
        }
    }

    public static final class TrackSignal {
        public String trackKey, title, artist, album;
        public long durationMs, listenedMs, lastPlayedAt, lastEventAt;
        public int starts, completes, skips, replays, favoriteAdds, favoriteRemoves, playlistAdds, playlistRemoves;
    }

    public static final class ArtistSignal {
        public String artistKey, artistName;
        public long listenedMs, lastPlayedAt, lastEventAt;
        public int starts, completes, skips, replays, favoriteAdds, favoriteRemoves, playlistAdds, playlistRemoves;
    }

    public static final class DailyArtistSignal {
        public long dayBucket, listenedMs;
        public String artistKey, artistName;
        public int starts, completes, skips, replays, favoriteAdds, favoriteRemoves, playlistAdds, playlistRemoves;
    }

    public static final class RecentEvent {
        public String type, trackKey, title, artist, sessionId;
        public double ratio;
        public long listenedDeltaMs, ts;
    }

    private static final class Db extends SQLiteOpenHelper {
        Db(Context context) {
            super(context, "xingyu_personalization_v2.db", null, 2);
            try { setWriteAheadLoggingEnabled(true); } catch (Exception ignored) { }
        }
        @Override public void onConfigure(SQLiteDatabase db) {
            super.onConfigure(db);
            db.setForeignKeyConstraintsEnabled(false);
        }
        @Override public void onCreate(SQLiteDatabase sql) {
            sql.execSQL("CREATE TABLE interaction_event (id INTEGER PRIMARY KEY AUTOINCREMENT,event_type TEXT NOT NULL,track_key TEXT NOT NULL,title TEXT,artist TEXT,album TEXT,duration_ms INTEGER DEFAULT 0,position_ms INTEGER DEFAULT 0,ratio REAL DEFAULT 0,session_id TEXT,listened_delta_ms INTEGER DEFAULT 0,ts INTEGER NOT NULL)");
            sql.execSQL("CREATE INDEX idx_event_ts ON interaction_event(ts DESC)");
            sql.execSQL("CREATE INDEX idx_event_track ON interaction_event(track_key,ts DESC)");
            sql.execSQL("CREATE TABLE track_stats (track_key TEXT PRIMARY KEY,title TEXT,artist TEXT,album TEXT,duration_ms INTEGER DEFAULT 0,starts INTEGER DEFAULT 0,completes INTEGER DEFAULT 0,skips INTEGER DEFAULT 0,replays INTEGER DEFAULT 0,favorite_adds INTEGER DEFAULT 0,favorite_removes INTEGER DEFAULT 0,playlist_adds INTEGER DEFAULT 0,playlist_removes INTEGER DEFAULT 0,listened_ms INTEGER DEFAULT 0,last_played_at INTEGER DEFAULT 0,last_event_at INTEGER DEFAULT 0)");
            sql.execSQL("CREATE TABLE artist_stats (artist_key TEXT PRIMARY KEY,artist_name TEXT,starts INTEGER DEFAULT 0,completes INTEGER DEFAULT 0,skips INTEGER DEFAULT 0,replays INTEGER DEFAULT 0,favorite_adds INTEGER DEFAULT 0,favorite_removes INTEGER DEFAULT 0,playlist_adds INTEGER DEFAULT 0,playlist_removes INTEGER DEFAULT 0,listened_ms INTEGER DEFAULT 0,last_played_at INTEGER DEFAULT 0,last_event_at INTEGER DEFAULT 0)");
            sql.execSQL("CREATE TABLE artist_daily_stats (day_bucket INTEGER NOT NULL,artist_key TEXT NOT NULL,artist_name TEXT,starts INTEGER DEFAULT 0,completes INTEGER DEFAULT 0,skips INTEGER DEFAULT 0,replays INTEGER DEFAULT 0,favorite_adds INTEGER DEFAULT 0,favorite_removes INTEGER DEFAULT 0,playlist_adds INTEGER DEFAULT 0,playlist_removes INTEGER DEFAULT 0,listened_ms INTEGER DEFAULT 0,PRIMARY KEY(day_bucket,artist_key))");
            sql.execSQL("CREATE INDEX idx_artist_daily_day ON artist_daily_stats(day_bucket DESC)");
            sql.execSQL("CREATE TABLE external_artist_map (artist_key TEXT PRIMARY KEY,artist_name TEXT,mbid TEXT,match_score INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0)");
            sql.execSQL("CREATE TABLE external_cache (cache_key TEXT PRIMARY KEY,payload TEXT,updated_at INTEGER DEFAULT 0)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("CREATE TABLE IF NOT EXISTS artist_daily_stats (day_bucket INTEGER NOT NULL,artist_key TEXT NOT NULL,artist_name TEXT,starts INTEGER DEFAULT 0,completes INTEGER DEFAULT 0,skips INTEGER DEFAULT 0,replays INTEGER DEFAULT 0,favorite_adds INTEGER DEFAULT 0,favorite_removes INTEGER DEFAULT 0,playlist_adds INTEGER DEFAULT 0,playlist_removes INTEGER DEFAULT 0,listened_ms INTEGER DEFAULT 0,PRIMARY KEY(day_bucket,artist_key))");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_artist_daily_day ON artist_daily_stats(day_bucket DESC)");
            }
        }
    }
}
