package com.xingyu.music.download;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.Song;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Side-car offline library. It never rewrites LibraryStore playlists/favorites. */
public final class OfflineStore {
    public static final String ORIGIN_DOWNLOAD = "download";
    public static final String ORIGIN_LOCAL = "local";
    public static final class Record {
        public final Song song;
        public final String path;
        public final String mime;
        public final String quality;
        public final String platform;
        public final String providerId;
        public final String providerLabel;
        public final long bytes;
        public final long downloadedAt;
        public final String origin;
        Record(Song song, String path, String mime, String quality, String platform,
               String providerId, String providerLabel, long bytes, long downloadedAt, String origin) {
            this.song = song; this.path = path == null ? "" : path; this.mime = mime == null ? "" : mime;
            this.quality = quality == null ? "" : quality; this.platform = platform == null ? "" : platform;
            this.providerId = providerId == null ? "" : providerId; this.providerLabel = providerLabel == null ? "" : providerLabel;
            this.bytes = Math.max(0L, bytes); this.downloadedAt = downloadedAt;
            this.origin = ORIGIN_LOCAL.equals(origin) ? ORIGIN_LOCAL : ORIGIN_DOWNLOAD;
        }
        public File file() { return new File(path); }
        public boolean exists() { return !path.isEmpty() && file().isFile() && file().length() > 0L; }
    }

    private static final String PREFIX = "r.";
    private final SharedPreferences prefs;
    public OfflineStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("lunaxy_offline_store_v1", Context.MODE_PRIVATE);
    }

    public Record get(Song song) { return song == null ? null : getBySongKey(song.key()); }
    public Record getBySongKey(String songKey) {
        try {
            String raw = prefs.getString(PREFIX + sha256(songKey), "");
            if (raw == null || raw.isEmpty()) return null;
            Record r = fromJson(new JSONObject(raw));
            if (r == null || !r.exists()) { if (r != null) prefs.edit().remove(PREFIX + sha256(songKey)).apply(); return null; }
            return r;
        } catch (Exception ignored) { return null; }
    }

    public boolean contains(Song song) { Record r = get(song); return r != null && r.exists(); }

    public void put(Song song, File file, String mime, String quality, String platform,
                    String providerId, String providerLabel) {
        if (song == null || file == null || !file.isFile()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("song", song.toJson()); o.put("path", file.getAbsolutePath()); o.put("mime", safe(mime));
            o.put("quality", safe(quality)); o.put("platform", safe(platform)); o.put("providerId", safe(providerId));
            o.put("providerLabel", safe(providerLabel)); o.put("bytes", file.length()); o.put("downloadedAt", System.currentTimeMillis());
            o.put("origin", ORIGIN_DOWNLOAD);
            prefs.edit().putString(PREFIX + sha256(song.key()), o.toString()).apply();
        } catch (Exception ignored) { }
    }


    /** Register a user-approved local audio copy inside Lunaxy private storage. The original device file is never deleted. */
    public void putLocalCopy(Song song, File file, String mime) {
        if (song == null || file == null || !file.isFile()) return;
        try {
            JSONObject o = new JSONObject();
            o.put("song", song.toJson()); o.put("path", file.getAbsolutePath()); o.put("mime", safe(mime));
            o.put("quality", "本地"); o.put("platform", "local"); o.put("providerId", "local-import");
            o.put("providerLabel", "本地导入"); o.put("bytes", file.length()); o.put("downloadedAt", System.currentTimeMillis());
            o.put("origin", ORIGIN_LOCAL);
            prefs.edit().putString(PREFIX + sha256(song.key()), o.toString()).apply();
        } catch (Exception ignored) { }
    }

    public List<Record> allByOrigin(String origin) {
        if (origin == null || origin.trim().isEmpty() || "all".equals(origin)) return all();
        List<Record> out = new ArrayList<>();
        for (Record r : all()) if (r != null && origin.equals(r.origin)) out.add(r);
        return out;
    }

    public void remove(Song song) { if (song != null) removeBySongKey(song.key(), true); }
    public void removeBySongKey(String songKey, boolean deleteFile) {
        Record r = getBySongKey(songKey);
        if (deleteFile && r != null) try { r.file().delete(); } catch (Exception ignored) { }
        prefs.edit().remove(PREFIX + sha256(songKey)).apply();
    }

    public List<Record> all() {
        List<Record> out = new ArrayList<>();
        for (Map.Entry<String,?> e : prefs.getAll().entrySet()) {
            if (!e.getKey().startsWith(PREFIX) || !(e.getValue() instanceof String)) continue;
            try { Record r = fromJson(new JSONObject((String)e.getValue())); if (r != null && r.exists()) out.add(r); }
            catch (Exception ignored) { }
        }
        out.sort(Comparator.comparingLong((Record r) -> r.downloadedAt).reversed());
        return out;
    }

    public long totalBytes() { long n=0L; for (Record r : all()) n += r.file().length(); return n; }

    private static Record fromJson(JSONObject o) {
        if (o == null) return null;
        Song s = Song.fromJson(o.optJSONObject("song"));
        return new Record(s, o.optString("path", ""), o.optString("mime", ""), o.optString("quality", ""),
                o.optString("platform", ""), o.optString("providerId", ""), o.optString("providerLabel", ""),
                o.optLong("bytes", 0L), o.optLong("downloadedAt", 0L),
                o.optString("origin", ORIGIN_DOWNLOAD));
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String sha256(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(); for (byte x : d) b.append(String.format(java.util.Locale.ROOT, "%02x", x & 0xff)); return b.toString();
        } catch (Exception e) { return Integer.toHexString((value == null ? "" : value).hashCode()); }
    }
}
