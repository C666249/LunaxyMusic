package com.xingyu.music.voice;

/** Immutable, Android-free command emitted by the Lunaxy Voice parser. */
public final class VoiceCommand {
    public enum Type {
        NONE,
        NEXT,
        PREVIOUS,
        PAUSE,
        RESUME,
        PLAY_PLAYLIST,
        SEARCH,
        SEARCH_ARTIST,
        PLAY_ARTIST,
        PLAY_QUERY
    }

    public final Type type;
    public final String query;
    public final int count;
    public final String raw;

    public VoiceCommand(Type type, String query, int count, String raw) {
        this.type = type == null ? Type.NONE : type;
        this.query = query == null ? "" : query.trim();
        this.count = Math.max(1, count);
        this.raw = raw == null ? "" : raw.trim();
    }

    public static VoiceCommand none(String raw) {
        return new VoiceCommand(Type.NONE, "", 1, raw);
    }
}
