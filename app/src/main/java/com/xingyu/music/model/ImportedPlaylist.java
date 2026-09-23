package com.xingyu.music.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class ImportedPlaylist {
    public final String id;
    public final String name;
    public final String source;
    public final List<Song> songs;
    public final long importedAt;

    public ImportedPlaylist(String id, String name, String source, List<Song> songs) {
        this(id, name, source, songs, System.currentTimeMillis());
    }

    public ImportedPlaylist(String id, String name, String source, List<Song> songs, long importedAt) {
        this.id = id == null ? "" : id;
        this.name = name == null || name.trim().isEmpty() ? "导入歌单" : name.trim();
        this.source = source == null ? "wy" : source;
        this.songs = songs == null ? new ArrayList<>() : new ArrayList<>(songs);
        this.importedAt = importedAt;
    }

    public JSONObject toJson() {
        JSONArray arr = new JSONArray();
        for (Song s : songs) {
            if (s != null) arr.put(s.toJson());
        }
        JSONObject out = new JSONObject();
        try {
            out.put("id", id);
            out.put("name", name);
            out.put("source", source);
            out.put("importedAt", importedAt);
            out.put("songs", arr);
        } catch (JSONException impossible) {
            // Primitive/String/JSONArray values are valid JSON values.
        }
        return out;
    }

    public static ImportedPlaylist fromJson(JSONObject o) {
        if (o == null) return new ImportedPlaylist("", "导入歌单", "wy", new ArrayList<>(), 0L);
        List<Song> songs = new ArrayList<>();
        JSONArray a = o.optJSONArray("songs");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject item = a.optJSONObject(i);
                if (item != null) songs.add(Song.fromJson(item));
            }
        }
        return new ImportedPlaylist(
                o.optString("id", ""),
                o.optString("name", "导入歌单"),
                o.optString("source", "wy"),
                songs,
                o.optLong("importedAt", 0L));
    }
}
