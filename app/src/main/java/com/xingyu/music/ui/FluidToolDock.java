package com.xingyu.music.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact progressive-disclosure action dock used by Lunaxy's Home surface.
 *
 * One stable 48dp hit target grows into secondary actions from the same physical source. The visible
 * icon surface is smaller (40dp), so Home stays quiet without sacrificing Android touch ergonomics.
 * Motion is interruptible: a new tap cancels the previous settle and resumes from current progress.
 */
public final class FluidToolDock extends FrameLayout {
    public enum Direction { LEFT, DOWN }

    private static final int HIT_DP = 48;
    private static final int VISUAL_DP = 40;
    private static final int GAP_DP = 4;

    private final Direction direction;
    private final int itemPx;
    private final int gapPx;
    private final FrameLayout hub;
    private final List<FrameLayout> actions = new ArrayList<>();
    private float progress;
    private ValueAnimator animator;
    private boolean expanded;

    public FluidToolDock(Context context, Direction direction, IconView.Type hubType, int hubTint,
                         String collapsedDescription) {
        super(context);
        this.direction = direction;
        itemPx = Ui.dp(context, HIT_DP);
        gapPx = Ui.dp(context, GAP_DP);
        setClipChildren(false);
        setClipToPadding(false);

        hub = makeHost(hubType, hubTint, collapsedDescription, true);
        hub.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            setExpanded(!expanded, true);
        });
        addView(hub, new FrameLayout.LayoutParams(itemPx, itemPx, hubGravity()));
        setPresentation(0f);
    }

    private FrameLayout makeHost(IconView.Type type, int tint, String description, boolean glass) {
        FrameLayout host = new FrameLayout(getContext());
        host.setClickable(true);
        host.setFocusable(true);
        host.setContentDescription(description);
        Ui.applyRipple(host, Color.TRANSPARENT);
        FrameLayout visual = Ui.iconButton(getContext(), type, VISUAL_DP, tint, Color.TRANSPARENT);
        visual.setClickable(false);
        visual.setFocusable(false);
        visual.setBackground(glass ? Ui.transientGlass(15, getContext()) : Ui.contentSurface(15, getContext()));
        visual.setElevation(Ui.dp(getContext(), glass ? 2 : 1));
        host.addView(visual, Ui.frame(Ui.dp(getContext(), VISUAL_DP), Ui.dp(getContext(), VISUAL_DP), Gravity.CENTER));
        return host;
    }

    public FrameLayout addAction(IconView.Type type, int tint, String description, Runnable action) {
        FrameLayout child = makeHost(type, tint, description, false);
        child.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (action != null) action.run();
            setExpanded(false, true);
        });
        actions.add(child);
        addView(child, Math.max(0, getChildCount() - 1), actionLayoutParams());
        setPresentation(progress);
        return child;
    }

    public FrameLayout hubView() { return hub; }
    public boolean isExpanded() { return expanded; }
    public void collapse(boolean animate) { setExpanded(false, animate); }

    public void setExpanded(boolean expand, boolean animate) {
        expanded = expand;
        float target = expand ? 1f : 0f;
        if (animator != null) animator.cancel();
        if (!animate || SpringMotion.isReducedMotion()) {
            setPresentation(target);
            return;
        }
        final float start = progress;
        animator = ValueAnimator.ofFloat(start, target);
        // Progressive disclosure is intentionally slower than press acknowledgement, so users can
        // track the stable hub growing into its children and the exact inverse on collapse.
        animator.setDuration(Math.max(170L, Math.round(500L * Math.abs(target - start))));
        // Keep the master clock linear. Each geometric property derives its own easing from this
        // one reversible progress value; stacking TAB_PAGE on top of child easing made the first
        // 100ms race ahead and then visually stall near the end.
        animator.setInterpolator(input -> input);
        animator.addUpdateListener(a -> setPresentation((Float) a.getAnimatedValue()));
        animator.start();
    }

    private void setPresentation(float value) {
        progress = clamp(value);
        float geometry = smoothstep(progress);
        int count = actions.size();
        int expandedW = direction == Direction.LEFT ? itemPx + count * (itemPx + gapPx) : itemPx;
        int expandedH = direction == Direction.DOWN ? itemPx + count * (itemPx + gapPx) : itemPx;
        int width = Math.round(itemPx + (expandedW - itemPx) * geometry);
        int height = Math.round(itemPx + (expandedH - itemPx) * geometry);
        setLayoutParamsSized(width, height);

        hub.setRotation(direction == Direction.LEFT ? -10f * geometry : 10f * geometry);
        hub.setScaleX(1f - .018f * geometry);
        hub.setScaleY(1f - .018f * geometry);

        for (int i = 0; i < count; i++) {
            FrameLayout child = actions.get(i);
            // V92.9.6: secondary actions no longer appear as one simultaneous burst. A single
            // expansion progress owns the whole dock, but each child receives a distinct time
            // window. Collapse naturally plays the exact inverse order because progress reverses.
            // V92.9.7: keep the one-by-one reading order, but remove the dead interval between
            // children.  The next action joins while the previous one is still settling, which
            // reads as one cascading object instead of two queued animations.  One shared progress
            // still owns the geometry, so reversal is the exact inverse and never waits in a queue.
            // A 70-ish ms lead is enough to establish order without creating a perceptible pause.
            // Both children therefore spend most of the trip moving at the same time, like links in
            // one extending mechanism rather than two independent buttons waiting in a queue.
            float window = .78f;
            float first = .02f;
            float stagger = .13f;
            float start = first + stagger * i;
            float local = easeOutCubic(clamp((progress - start) / window));
            child.setVisibility(local <= .003f ? INVISIBLE : VISIBLE);
            child.setAlpha(smoothstep(clamp(local * 1.35f)));
            float scale = .94f + .06f * local;
            child.setScaleX(scale);
            child.setScaleY(scale);
            if (direction == Direction.LEFT) {
                float target = -(i + 1) * (itemPx + gapPx);
                child.setTranslationX(target * local);
                child.setTranslationY(0f);
            } else {
                float target = (i + 1) * (itemPx + gapPx);
                child.setTranslationX(0f);
                child.setTranslationY(target * local);
            }
        }
        requestLayout();
        invalidate();
    }

    private int hubGravity() {
        return direction == Direction.LEFT ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    }

    private FrameLayout.LayoutParams actionLayoutParams() {
        return new FrameLayout.LayoutParams(itemPx, itemPx, hubGravity());
    }

    private void setLayoutParamsSized(int width, int height) {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) lp = new android.view.ViewGroup.LayoutParams(width, height);
        if (lp.width == width && lp.height == height) return;
        lp.width = width;
        lp.height = height;
        setLayoutParams(lp);
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float smoothstep(float v) {
        float t = clamp(v);
        return t * t * (3f - 2f * t);
    }

    private static float easeOutCubic(float v) {
        float t = 1f - clamp(v);
        return 1f - t * t * t;
    }
}

