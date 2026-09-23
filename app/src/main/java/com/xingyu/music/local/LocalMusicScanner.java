package com.xingyu.music.local;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * MediaStore-only local audio discovery.  It never imports or deletes anything by itself.
 * Classification is intentionally advisory: every result remains user-selectable in the review UI.
 */
public final class LocalMusicScanner {
    public static final class Item {
        public final Uri uri;
        public final String title;
        public final String artist;
        public final String album;
        public final String displayName;
        public final String folder;
        public final String mime;
        public final String artworkUri;
        public final long durationMs;
        public final long sizeBytes;
        public final boolean recommended;
        public final boolean recordingLike;
        public final boolean shortAudio;

        Item(Uri uri, String title, String artist, String album, String displayName, String folder,
             String mime, String artworkUri, long durationMs, long sizeBytes,
             boolean recommended, boolean recordingLike, boolean shortAudio) {
            this.uri = uri;
            this.title = clean(title, stripExtension(displayName, "本地音频"));
            this.artist = cleanArtist(artist);
            this.album = clean(album, "");
            this.displayName = clean(displayName, this.title);
            this.folder = clean(folder, "未知文件夹");
            this.mime = clean(mime, "audio/*");
            this.artworkUri = artworkUri == null ? "" : artworkUri;
            this.durationMs = Math.max(0L, durationMs);
            this.sizeBytes = Math.max(0L, sizeBytes);
            this.recommended = recommended;
            this.recordingLike = recordingLike;
            this.shortAudio = shortAudio;
        }

        public String stableKey() { return uri == null ? displayName : uri.toString(); }
        public String categoryLabel() {
            if (recordingLike) return "可能是录音";
            if (shortAudio) return "短音频";
            return recommended ? "推荐音乐" : "其它音频";
        }
    }

    private LocalMusicScanner() {}

    public static List<Item> scan(Context context) {
        if (context == null) return new ArrayList<>();
        ArrayList<Item> out = new ArrayList<>();
        ArrayList<String> cols = new ArrayList<>();
        Collections.addAll(cols,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.ALBUM_ID);
        if (Build.VERSION.SDK_INT >= 29) cols.add(MediaStore.Audio.Media.RELATIVE_PATH);
        else cols.add(MediaStore.Audio.Media.DATA);

        try (Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                cols.toArray(new String[0]), null, null,
                MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            if (c == null) return out;
            int idI = c.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleI = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int artistI = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int albumI = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int durI = c.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int sizeI = c.getColumnIndex(MediaStore.Audio.Media.SIZE);
            int nameI = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
            int mimeI = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
            int musicI = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC);
            int albumIdI = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int folderI = Build.VERSION.SDK_INT >= 29
                    ? c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    : c.getColumnIndex(MediaStore.Audio.Media.DATA);
            while (c.moveToNext()) {
                long id = getLong(c, idI);
                long duration = getLong(c, durI);
                long bytes = getLong(c, sizeI);
                if (id <= 0L || bytes <= 0L) continue;
                String title = getString(c, titleI);
                String artist = getString(c, artistI);
                String album = getString(c, albumI);
                String displayName = getString(c, nameI);
                String mime = getString(c, mimeI);
                String folder = getString(c, folderI);
                if (Build.VERSION.SDK_INT < 29 && folder != null) {
                    int slash = folder.lastIndexOf('/');
                    folder = slash > 0 ? folder.substring(0, slash) : folder;
                }
                boolean isMusic = musicI >= 0 && getLong(c, musicI) == 1L;
                String hay = (safe(folder) + " " + safe(displayName) + " " + safe(title)).toLowerCase(Locale.ROOT);
                boolean recording = containsAny(hay,
                        "record", "recording", "recorder", "voice", "录音", "语音", "micromsg", "wechat", "tencent/mobileqq", "qqfile_recv/audio");
                boolean shortAudio = duration > 0L && duration < 55_000L;
                boolean musicFolder = containsAny(hay, "/music", "music/", "/download", "download/", "歌曲", "音乐");
                boolean metadataMusic = !isUnknownArtist(artist) && duration >= 70_000L;
                boolean recommended = !recording && !shortAudio && (isMusic || musicFolder || metadataMusic);
                long albumId = getLong(c, albumIdI);
                String artwork = albumId > 0L ? "content://media/external/audio/albumart/" + albumId : "";
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                out.add(new Item(uri, title, artist, album, displayName, folder, mime, artwork,
                        duration, bytes, recommended, recording, shortAudio));
            }
        } catch (Exception ignored) { }

        out.sort(Comparator
                .comparing((Item x) -> !x.recommended)
                .thenComparing(x -> x.title.toLowerCase(Locale.ROOT))
                .thenComparing(x -> x.artist.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String n : needles) if (n != null && !n.isEmpty() && text.contains(n.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    private static boolean isUnknownArtist(String s) {
        String v = safe(s).trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() || "<unknown>".equals(v) || "unknown".equals(v) || "未知歌手".equals(v);
    }
    private static String cleanArtist(String s) { return isUnknownArtist(s) ? "未知歌手" : s.trim(); }
    private static String clean(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s.trim(); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String stripExtension(String s, String fallback) {
        String v = clean(s, fallback); int dot = v.lastIndexOf('.'); return dot > 0 ? v.substring(0, dot) : v;
    }
    private static long getLong(Cursor c, int i) { try { return i < 0 || c.isNull(i) ? 0L : c.getLong(i); } catch (Exception e) { return 0L; } }
    private static String getString(Cursor c, int i) { try { return i < 0 || c.isNull(i) ? "" : c.getString(i); } catch (Exception e) { return ""; } }
}
