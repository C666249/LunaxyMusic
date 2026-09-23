package com.xingyu.music.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LyricLine {
    public final long timeMs;
    public final long endMs;
    public final String text;
    public final List<LyricWord> words;

    public LyricLine(long timeMs, String text) {
        this(timeMs, 0L, text, Collections.emptyList());
    }

    public LyricLine(long timeMs, long endMs, String text, List<LyricWord> words) {
        this.timeMs = Math.max(0L, timeMs);
        this.endMs = Math.max(this.timeMs, endMs);
        this.text = text == null ? "" : text;
        this.words = words == null ? Collections.emptyList() :
                Collections.unmodifiableList(new ArrayList<>(words));
    }

    public boolean hasWordTiming() { return !words.isEmpty(); }
}
