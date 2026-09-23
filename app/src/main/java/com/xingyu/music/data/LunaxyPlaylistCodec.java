package com.xingyu.music.data;

import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Portable, audio-free Lunaxy playlist bundle. No provider URL/cache/health state is exported. */
public final class LunaxyPlaylistCodec {
    public static final String EXTENSION = ".lunaxyplaylist";
    private static final int VERSION = 1;

    private LunaxyPlaylistCodec() {}

    public static String encode(ImportedPlaylist playlist) throws Exception {
        if (playlist == null) throw new IllegalArgumentException("playlist == null");
        JSONObject root = new JSONObject();
        root.put("format", "lunaxy-playlist");
        root.put("version", VERSION);
        root.put("name", playlist.name);
        root.put("source", playlist.source);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray songs = new JSONArray();
        if (playlist.songs != null) for (Song song : playlist.songs) if (song != null) {
            List<SourceVariant> portable = new ArrayList<>();
            for (SourceVariant v : song.variants()) if (v != null && !"local".equals(v.source)) portable.add(v);
            Song clean = new Song(song.title, song.artist, song.album, song.coverUrl, song.durationMs, portable);
            songs.put(clean.toJson());
        }
        root.put("songs", songs);
        return root.toString();
    }

    public static ImportedPlaylist decode(String raw) throws Exception {
        JSONObject root = new JSONObject(raw == null ? "" : raw);
        if (!"lunaxy-playlist".equals(root.optString("format", ""))) throw new IllegalArgumentException("不是 Lunaxy 歌单文件");
        int version = root.optInt("version", 0);
        if (version <= 0 || version > VERSION) throw new IllegalArgumentException("歌单文件版本暂不支持");
        String name = root.optString("name", "Lunaxy 歌单").trim();
        if (name.isEmpty()) name = "Lunaxy 歌单";
        JSONArray arr = root.optJSONArray("songs");
        List<Song> songs = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i); if (item != null) songs.add(Song.fromJson(item));
        }
        // Imported transfer bundles become normal locally-managed playlists. Provider identities inside Song stay intact.
        return new ImportedPlaylist("local:transfer:" + System.currentTimeMillis(), name, "local", songs);
    }

    public static String safeFileName(String name) {
        String v = name == null ? "Lunaxy 歌单" : name.trim();
        if (v.isEmpty()) v = "Lunaxy 歌单";
        v = v.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        if (v.length() > 72) v = v.substring(0, 72);
        return v + EXTENSION;
    }
}
