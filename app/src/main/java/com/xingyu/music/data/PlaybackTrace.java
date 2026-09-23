package com.xingyu.music.data;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Small in-memory diagnostic trace for real playback failures. */
public final class PlaybackTrace {
    private final List<String> lines = new ArrayList<>();
    private static final int MAX = 36;

    public synchronized void reset(String title, String artist) {
        lines.clear();
        add("track", title + " / " + artist);
    }

    public synchronized void add(String stage, String detail) {
        if (lines.size() >= MAX) lines.remove(0);
        lines.add(time() + "  " + stage + "  " + (detail == null ? "" : detail));
    }

    public synchronized String text() {
        StringBuilder b = new StringBuilder();
        for (String line : lines) {
            if (b.length() > 0) b.append('\n');
            b.append(line);
        }
        return b.toString();
    }

    private static String time() { return new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date()); }
}
