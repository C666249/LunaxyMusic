package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.xingyu.music.model.LyricLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local-only vertical lyric selector used by the ringtone lab.
 *
 * The lyric list scrolls exactly like a normal lyric viewport. Two horizontal boundaries are
 * anchored to lyric rows; long-press either label and drag vertically to move START/END. No
 * network/provider/playback routing code is referenced here.
 */
public final class RingtoneLyricSelectorView extends FrameLayout {
    public interface Listener { void onSelectionChanged(long startMs, long endMs); }

    private static final long DEFAULT_MIN_CLIP_MS = 5_000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LyricGestureScrollView scroll;
    private final LinearLayout rows;
    private final List<LyricLine> lines = new ArrayList<>();
    private final List<TextView> rowViews = new ArrayList<>();
    private final Boundary startBoundary;
    private final Boundary endBoundary;
    private final long totalMs;
    private final long minClipMs;
    private int startIndex;
    private int endIndex;
    private Listener listener;

    public RingtoneLyricSelectorView(Context context, List<LyricLine> source, long totalMs,
                                     long initialStartMs, long initialEndMs) {
        super(context);
        this.totalMs = Math.max(1_000L, totalMs);
        this.minClipMs = Math.min(DEFAULT_MIN_CLIP_MS, this.totalMs);
        setClipChildren(false);
        setClipToPadding(false);
        setBackground(Ui.glass(105, 18, 22, context));

        buildLines(source);
        startIndex = indexForStart(initialStartMs);
        endIndex = indexForEnd(initialEndMs);
        normalizeSelection(false);

        scroll = new LyricGestureScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        rows = Ui.column(context);
        rows.setPadding(Ui.dp(context, 12), Ui.dp(context, 34), Ui.dp(context, 12), Ui.dp(context, 34));
        scroll.addView(rows, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        buildRows();
        startBoundary = new Boundary(true, Ui.CYAN);
        endBoundary = new Boundary(false, Ui.GREEN);
        addView(startBoundary.line, lineParams());
        addView(endBoundary.line, lineParams());
        addView(startBoundary.label, handleParams());
        addView(endBoundary.label, handleParams());

        scroll.setOnScrollChangeListener((v, sx, sy, oldSx, oldSy) -> updateBoundaries());
        post(() -> {
            updateRows();
            centerSelection();
            updateBoundaries();
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        dispatchSelection();
    }

    public long startMs() { return lineStart(startIndex); }
    public long endMs() { return lineEnd(endIndex); }
    public boolean hasRealLyrics() {
        for (LyricLine line : lines) if (line != null && line.text != null && !line.text.startsWith("♪")) return true;
        return false;
    }

    public void setSelection(long startMs, long endMs) {
        startIndex = indexForStart(startMs);
        endIndex = indexForEnd(endMs <= 0L ? totalMs : endMs);
        normalizeSelection(true);
        post(() -> { centerSelection(); updateBoundaries(); });
    }

    private void buildLines(List<LyricLine> source) {
        if (source != null) {
            for (LyricLine l : source) {
                if (l == null || l.text == null || l.text.trim().isEmpty()) continue;
                if (l.timeMs > totalMs + 1_000L) continue;
                lines.add(l);
            }
        }
        if (!lines.isEmpty()) {
            if (lines.get(0).timeMs > 2_000L) lines.add(0, new LyricLine(0L, lines.get(0).timeMs, "♪ 前奏", java.util.Collections.emptyList()));
            LyricLine last = lines.get(lines.size() - 1);
            if (totalMs - last.timeMs > 4_000L) lines.add(new LyricLine(Math.max(last.timeMs + 1L, totalMs - 1_000L), totalMs, "♪ 结尾", java.util.Collections.emptyList()));
            return;
        }
        // Offline-safe fallback: keep the editor useful without fetching lyrics from the network.
        long step = totalMs <= 120_000L ? 10_000L : 15_000L;
        for (long t = 0L; t < totalMs; t += step) {
            lines.add(new LyricLine(t, Math.min(totalMs, t + step), "♪ 时间点 " + clock(t), java.util.Collections.emptyList()));
        }
        if (lines.isEmpty()) lines.add(new LyricLine(0L, totalMs, "♪ 时间点 00:00", java.util.Collections.emptyList()));
    }

    private void buildRows() {
        rows.removeAllViews(); rowViews.clear();
        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            TextView row = Ui.text(getContext(), clock(line.timeMs) + "   " + line.text, 13.2f, Ui.TEXT_2, false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 8), Ui.dp(getContext(), 86), Ui.dp(getContext(), 8));
            row.setMinHeight(Ui.dp(getContext(), 48));
            row.setLineSpacing(0f, 1.12f);
            final int index = i;
            row.setOnClickListener(v -> {
                long t = lineStart(index);
                long ds = Math.abs(t - startMs());
                long de = Math.abs(t - endMs());
                if (ds <= de) startIndex = index; else endIndex = index;
                normalizeSelection(true);
            });
            rows.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            rowViews.add(row);
        }
    }

    private void updateRows() {
        long start = startMs(), end = endMs();
        for (int i = 0; i < rowViews.size(); i++) {
            TextView row = rowViews.get(i); LyricLine line = lines.get(i);
            boolean selected = line.timeMs < end && lineEnd(i) > start;
            row.setTextColor(selected ? Ui.TEXT : Ui.TEXT_2);
            row.setAlpha(selected ? 1f : .64f);
            if (selected) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.argb(12, 170, 225, 255));
                bg.setCornerRadius(Ui.dp(getContext(), 10));
                row.setBackground(bg);
            } else row.setBackground(null);
        }
    }

    private void normalizeSelection(boolean notify) {
        startIndex = Math.max(0, Math.min(startIndex, lines.size() - 1));
        endIndex = Math.max(0, Math.min(endIndex, lines.size() - 1));
        if (lineEnd(endIndex) < lineStart(startIndex) + minClipMs) {
            int candidate = endIndex;
            while (candidate < lines.size() - 1 && lineEnd(candidate) < lineStart(startIndex) + minClipMs) candidate++;
            if (lineEnd(candidate) >= lineStart(startIndex) + minClipMs) endIndex = candidate;
            else {
                candidate = startIndex;
                while (candidate > 0 && lineEnd(endIndex) < lineStart(candidate) + minClipMs) candidate--;
                startIndex = candidate;
            }
        }
        updateRows(); updateBoundaries(); if (notify) dispatchSelection();
    }

    private int indexForStart(long ms) {
        int best = 0; long delta = Long.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            long d = Math.abs(lineStart(i) - Math.max(0L, ms));
            if (d < delta) { delta = d; best = i; }
        }
        return best;
    }

    private int indexForEnd(long ms) {
        int best = lines.size() - 1; long delta = Long.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            long d = Math.abs(lineEnd(i) - Math.max(0L, ms));
            if (d < delta) { delta = d; best = i; }
        }
        return best;
    }

    private long lineStart(int index) { return Math.max(0L, Math.min(totalMs, lines.get(index).timeMs)); }
    private long lineEnd(int index) {
        LyricLine l = lines.get(index);
        long end = l.endMs > l.timeMs ? l.endMs : (index + 1 < lines.size() ? lines.get(index + 1).timeMs : totalMs);
        return Math.max(lineStart(index), Math.min(totalMs, end));
    }

    private void centerSelection() {
        if (rowViews.isEmpty() || getHeight() <= 0) return;
        int first = Math.min(startIndex, endIndex), last = Math.max(startIndex, endIndex);
        int center = (rowViews.get(first).getTop() + rowViews.get(last).getBottom()) / 2;
        scroll.scrollTo(0, Math.max(0, center - Math.max(1, getHeight()) / 2));
    }

    private void updateBoundaries() {
        if (rowViews.isEmpty() || startBoundary == null || endBoundary == null || getHeight() <= 0) return;
        if (!startBoundary.dragging) placeBoundary(startBoundary, startIndex, true);
        if (!endBoundary.dragging) placeBoundary(endBoundary, endIndex, false);
    }

    private void placeBoundary(Boundary boundary, int index, boolean start) {
        TextView row = rowViews.get(Math.max(0, Math.min(index, rowViews.size() - 1)));
        float contentY = start ? row.getTop() : row.getBottom();
        float y = contentY - scroll.getScrollY();
        float minY = Ui.dp(getContext(), 10), maxY = Math.max(minY, getHeight() - Ui.dp(getContext(), 10));
        float drawY = Math.max(minY, Math.min(maxY, y));
        setBoundaryVisual(boundary, drawY, false);
        String arrow = y < minY ? "↑ " : (y > maxY ? "↓ " : "");
        boundary.label.setText(arrow + (start ? "开始 " + clock(startMs()) : "结束 " + clock(endMs())));
    }

    private void setBoundaryVisual(Boundary boundary, float lineY, boolean animate) {
        float labelY = lineY - Ui.dp(getContext(), 15);
        boundary.line.animate().cancel();
        boundary.label.animate().cancel();
        if (animate) {
            boundary.line.animate().translationY(lineY).setDuration(SpringMotion.isReducedMotion() ? 70L : 135L).start();
            boundary.label.animate().translationY(labelY).setDuration(SpringMotion.isReducedMotion() ? 70L : 135L).start();
        } else {
            boundary.line.setTranslationY(lineY);
            boundary.label.setTranslationY(labelY);
        }
    }

    private LayoutParams lineParams() {
        LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(getContext(), 1)));
        p.gravity = Gravity.TOP; p.leftMargin = Ui.dp(getContext(), 8); p.rightMargin = Ui.dp(getContext(), 8); return p;
    }

    private LayoutParams handleParams() {
        LayoutParams p = new LayoutParams(Ui.dp(getContext(), 104), Ui.dp(getContext(), 30));
        p.gravity = Gravity.TOP | Gravity.END; p.rightMargin = Ui.dp(getContext(), 6); return p;
    }

    private void dispatchSelection() { if (listener != null) listener.onSelectionChanged(startMs(), endMs()); }

    private int nearestBoundaryIndexForContentY(float contentY, boolean start) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < rowViews.size(); i++) {
            TextView row = rowViews.get(i);
            // The nearest anchor changes exactly when the finger crosses the midpoint between
            // two adjacent anchors. That gives a continuous drag with a natural half-way snap.
            float anchor = start ? row.getTop() : row.getBottom();
            float d = Math.abs(anchor - contentY);
            if (d < bestDistance) { bestDistance = d; best = i; }
        }
        return best;
    }

    private void dragBoundary(Boundary boundary, float rawY) {
        int[] loc = new int[2]; getLocationOnScreen(loc);
        float y = rawY - loc[1];
        int edge = Ui.dp(getContext(), 48);
        if (y < edge) scroll.scrollBy(0, -Ui.dp(getContext(), 13));
        else if (y > getHeight() - edge) scroll.scrollBy(0, Ui.dp(getContext(), 13));

        float minY = Ui.dp(getContext(), 10);
        float maxY = Math.max(minY, getHeight() - Ui.dp(getContext(), 10));
        float visualY = Math.max(minY, Math.min(maxY, y));
        setBoundaryVisual(boundary, visualY, false);

        float contentY = scroll.getScrollY() + visualY;
        int candidate = nearestBoundaryIndexForContentY(contentY, boundary.start);
        int before = boundary.start ? startIndex : endIndex;
        if (boundary.start) {
            while (candidate > 0 && lineStart(candidate) > endMs() - minClipMs) candidate--;
            if (lineStart(candidate) <= endMs() - minClipMs) startIndex = candidate;
        } else {
            while (candidate < lines.size() - 1 && lineEnd(candidate) < startMs() + minClipMs) candidate++;
            if (lineEnd(candidate) >= startMs() + minClipMs) endIndex = candidate;
        }
        int after = boundary.start ? startIndex : endIndex;
        boundary.label.setText((boundary.start ? "开始 " + clock(startMs()) : "结束 " + clock(endMs())));
        if (after != before) {
            updateRows();
            // Keep the opposite boundary tied to its exact lyric anchor while this one follows
            // the finger continuously.
            if (boundary.start && !endBoundary.dragging) placeBoundary(endBoundary, endIndex, false);
            if (!boundary.start && !startBoundary.dragging) placeBoundary(startBoundary, startIndex, true);
            dispatchSelection();
        }
    }

    private void finishBoundaryDrag(Boundary boundary) {
        boundary.dragging = false;
        updateRows();
        TextView row = rowViews.get(Math.max(0, Math.min(boundary.start ? startIndex : endIndex, rowViews.size() - 1)));
        float contentY = boundary.start ? row.getTop() : row.getBottom();
        float y = contentY - scroll.getScrollY();
        float minY = Ui.dp(getContext(), 10), maxY = Math.max(minY, getHeight() - Ui.dp(getContext(), 10));
        float drawY = Math.max(minY, Math.min(maxY, y));
        setBoundaryVisual(boundary, drawY, true);
        String arrow = y < minY ? "↑ " : (y > maxY ? "↓ " : "");
        boundary.label.setText(arrow + (boundary.start ? "开始 " + clock(startMs()) : "结束 " + clock(endMs())));
        dispatchSelection();
    }

    private final class Boundary {
        final View line;
        final TextView label;
        final boolean start;
        boolean dragging;
        Boundary(boolean start, int color) {
            this.start = start;
            line = new View(getContext()); line.setBackgroundColor(color); line.setAlpha(.88f);
            label = Ui.text(getContext(), start ? "开始" : "结束", 10.6f, Color.BLACK, true);
            label.setGravity(Gravity.CENTER);
            label.setBackground(Ui.round(color, 12, getContext()));
            label.setElevation(Ui.dp(getContext(), 3));
            label.setOnTouchListener(new LongPressDragListener(start));
        }
    }

    private final class LongPressDragListener implements View.OnTouchListener {
        private final boolean start;
        private final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private float downX, downY;
        private boolean dragging;
        private final Runnable activate;
        LongPressDragListener(boolean start) {
            this.start = start;
            this.activate = () -> {
                dragging = true;
                Boundary boundary = this.start ? startBoundary : endBoundary;
                boundary.dragging = true;
                boundary.line.animate().cancel();
                boundary.label.animate().cancel();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            };
        }
        @Override public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY(); dragging = false;
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    main.postDelayed(activate, 260L); return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && Math.hypot(event.getRawX() - downX, event.getRawY() - downY) > slop * 1.5f) main.removeCallbacks(activate);
                    if (dragging) dragBoundary(start ? startBoundary : endBoundary, event.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    main.removeCallbacks(activate);
                    if (dragging) finishBoundaryDrag(start ? startBoundary : endBoundary);
                    dragging = false;
                    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default: return true;
            }
        }
    }

    private static String clock(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", total / 60L, total % 60L);
    }
}
