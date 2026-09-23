package com.xingyu.music.ui;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared in-app/system-overlay presentation for Lunaxy Voice. */
public final class VoiceAssistantPanel extends FrameLayout {
    private final VoiceGlassDrawable glass;
    private final LinearLayout row;
    private final VoiceOrbView orb;
    private final TextView compactLabel;
    private final LinearLayout copy;
    private final TextView title;
    private final TextView transcript;
    private final TextView detail;
    private final TextView action;
    private final LinearLayout controls;
    private final FrameLayout close;
    private final IconView closeIcon;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final int touchSlop;

    private Runnable closeAction;
    private Runnable primaryAction;
    private Runnable collapseAction;
    private Runnable expandAction;
    private Runnable openAppAction;
    private Runnable snoozeAction;
    private String lastPhase = "armed";
    private boolean compact;
    private boolean dragging;
    private boolean thresholdHaptic;
    private float downX;
    private float downY;
    private VelocityTracker velocityTracker;
    private ValueAnimator shapeAnimator;
    private long gestureGuideUntil;

    private final Runnable longPressGuide = () -> {
        if (dragging) return;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (compact && expandAction != null) expandAction.run();
        main.postDelayed(this::showGestureGuide, compact ? 170L : 0L);
    };

    public VoiceAssistantPanel(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        glass = new VoiceGlassDrawable(density);
        setBackground(glass);
        setElevation(Ui.dp(context, 16));
        setPadding(Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10));
        setClipToPadding(false);
        setClipChildren(false);
        setClickable(true);
        setFocusable(true);
        setMinimumHeight(Ui.dp(context, 124));
        setContentDescription("Lunaxy Voice 语音面板");

        row = Ui.row(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, Ui.frame(-1, Ui.dp(context, 104), Gravity.CENTER_VERTICAL));

        orb = new VoiceOrbView(context);
        row.addView(orb, Ui.lp(Ui.dp(context, 68), Ui.dp(context, 68)));

        compactLabel = Ui.text(context, "聆听", 11.4f, Ui.CYAN, true);
        compactLabel.setGravity(Gravity.CENTER_VERTICAL);
        compactLabel.setSingleLine(true);
        compactLabel.setVisibility(GONE);
        row.addView(compactLabel, Ui.lp(Ui.dp(context, 78), Ui.dp(context, 46)));

        copy = Ui.column(context);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(Ui.dp(context, 11), 0, Ui.dp(context, 7), 0);
        title = Ui.text(context, "Lunaxy Voice", 12.7f, Ui.TEXT_2, true);
        transcript = Ui.text(context, "正在聆听…", 16.2f, Ui.TEXT, true);
        transcript.setSingleLine(true);
        transcript.setEllipsize(TextUtils.TruncateAt.END);
        detail = Ui.text(context, "说出你的音乐指令", 10.5f, Ui.DIM, false);
        detail.setMaxLines(2);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(title, Ui.lp(-1, Ui.dp(context, 21)));
        copy.addView(transcript, Ui.lp(-1, Ui.dp(context, 29)));
        copy.addView(detail, Ui.lp(-1, Ui.dp(context, 32)));
        row.addView(copy, new LinearLayout.LayoutParams(0, Ui.dp(context, 86), 1f));

        controls = Ui.column(context);
        controls.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);

        close = new FrameLayout(context);
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription("收起为顶部胶囊");
        closeIcon = new IconView(context, IconView.Type.CLOSE, Ui.TEXT_2);
        closeIcon.setStrokeDp(1.62f);
        close.addView(closeIcon, Ui.frame(Ui.dp(context, 19), Ui.dp(context, 19), Gravity.CENTER));
        close.setBackground(Ui.playerControlSurface(Ui.PURPLE, 24, context));
        Ui.applyRipple(close, Color.TRANSPARENT);
        close.setOnClickListener(v -> {
            if (collapseAction != null) collapseAction.run();
            else if (closeAction != null) closeAction.run();
        });
        controls.addView(close, Ui.lp(Ui.dp(context, 48), Ui.dp(context, 48)));

        action = Ui.text(context, "", 10.4f, Ui.playerControlIconColor(Ui.CYAN), true);
        action.setGravity(Gravity.CENTER);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setBackground(Ui.playerControlSurface(Ui.CYAN, 16, context));
        action.setVisibility(GONE);
        action.setClickable(false);
        action.setFocusable(true);
        action.setMinHeight(Ui.dp(context, 48));
        Ui.applyRipple(action, Color.TRANSPARENT);
        action.setOnClickListener(v -> { if (primaryAction != null) primaryAction.run(); });
        LinearLayout.LayoutParams actionLp = Ui.lp(Ui.dp(context, 86), Ui.dp(context, 48));
        actionLp.topMargin = Ui.dp(context, 3);
        controls.addView(action, actionLp);
        row.addView(controls, Ui.lp(Ui.dp(context, 92), Ui.dp(context, 104)));

        refreshAppearance();
    }

    public void setCloseAction(Runnable action) { closeAction = action; }
    public void setCollapseAction(Runnable action) { collapseAction = action; }
    public void setExpandAction(Runnable action) { expandAction = action; }
    public void setOpenAppAction(Runnable action) { openAppAction = action; }
    public void setSnoozeAction(Runnable action) { snoozeAction = action; }
    public boolean isCompact() { return compact; }

    public void setPrimaryAction(String label, Runnable runnable) {
        primaryAction = runnable;
        boolean visible = label != null && !label.trim().isEmpty() && runnable != null;
        action.setText(visible ? label.trim() : "");
        action.setVisibility(visible && !compact ? VISIBLE : GONE);
        action.setClickable(visible && !compact);
        if (visible) {
            int accent = "error".equals(lastPhase) ? Ui.RED : Ui.CYAN;
            action.setTextColor(Ui.playerControlIconColor(accent));
            action.setBackground(Ui.playerControlSurface(accent, 16, getContext()));
        }
    }

    public void refreshAppearance() {
        title.setTextColor(Ui.TEXT_2);
        transcript.setTextColor(Ui.TEXT);
        detail.setTextColor(Ui.DIM);
        compactLabel.setTextColor(accentForPhase(lastPhase));
        closeIcon.setIconColor(Ui.playerControlIconColor(Ui.PURPLE));
        close.setBackground(Ui.playerControlSurface(Ui.PURPLE, 24, getContext()));
        glass.invalidateSelf();
        invalidate();
    }

    public void render(String phase, String heard, String message, float audioLevel) {
        refreshAppearance();
        String p = phase == null ? "armed" : phase;
        boolean phaseChanged = !p.equals(lastPhase);
        lastPhase = p;
        orb.setVoiceState(p);
        orb.setAudioLevel(audioLevel);
        String heading;
        if ("wake".equals(p)) heading = "我在";
        else if ("listening".equals(p)) heading = "正在聆听…";
        else if ("processing".equals(p)) heading = "正在理解…";
        else if ("result".equals(p)) heading = "完成";
        else if ("error".equals(p)) heading = "需要处理";
        else heading = "Lunaxy Voice";
        title.setText("Lunaxy Voice · " + phaseLabel(p));
        title.setTextColor(accentForPhase(p));
        compactLabel.setText(phaseLabel(p));
        compactLabel.setTextColor(accentForPhase(p));
        transcript.setText(heard == null || heard.trim().isEmpty() ? heading : heard.trim());
        String resolved = message == null || message.trim().isEmpty() ? defaultDetail(p) : message.trim();
        if (SystemClock.uptimeMillis() < gestureGuideUntil) resolved = gestureGuideText();
        detail.setText(resolved);
        if (phaseChanged && !compact && !SpringMotion.isReducedMotion()) {
            // Phase changes are one continuous object changing state: a tiny vertical settle gives
            // acknowledgement without cross-fading two copies of the banner text.
            copy.animate().cancel();
            copy.setTranslationY(Ui.dp(getContext(), 3f));
            copy.setAlpha(.90f);
            copy.animate().translationY(0f).alpha(1f).setDuration(220L).setInterpolator(SpringMotion.LAND).start();
        }
        if (action.getVisibility() == VISIBLE || primaryAction != null) setPrimaryAction(action.getText().toString(), primaryAction);
    }

    public void applyCompactImmediate() {
        compact = true;
        main.removeCallbacks(longPressGuide);
        if (shapeAnimator != null) shapeAnimator.cancel();
        animate().cancel(); copy.animate().cancel(); controls.animate().cancel(); orb.animate().cancel();
        setPivotX(0f); setPivotY(0f);
        setScaleX(1f); setScaleY(1f); setTranslationX(0f); setTranslationY(0f); setRotation(0f); setAlpha(1f);
        setPadding(Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8), Ui.dp(getContext(), 8));
        setMinimumHeight(Ui.dp(getContext(), 62));
        FrameLayout.LayoutParams rp = (FrameLayout.LayoutParams) row.getLayoutParams();
        rp.height = Ui.dp(getContext(), 46); row.setLayoutParams(rp);
        LinearLayout.LayoutParams op = (LinearLayout.LayoutParams) orb.getLayoutParams();
        op.width = Ui.dp(getContext(), 46); op.height = Ui.dp(getContext(), 46); orb.setLayoutParams(op);
        compactLabel.setVisibility(VISIBLE); compactLabel.setAlpha(1f);
        copy.setVisibility(GONE); copy.setAlpha(0f);
        controls.setVisibility(GONE); controls.setAlpha(0f);
        glass.setCompactProgress(1f);
        setContentDescription("Lunaxy Voice 顶部胶囊，点击展开");
        requestLayout();
    }

    public void applyExpandedImmediate() {
        compact = false;
        main.removeCallbacks(longPressGuide);
        if (shapeAnimator != null) shapeAnimator.cancel();
        animate().cancel(); copy.animate().cancel(); controls.animate().cancel(); orb.animate().cancel();
        setPivotX(0f); setPivotY(0f);
        setScaleX(1f); setScaleY(1f); setTranslationX(0f); setTranslationY(0f); setRotation(0f); setAlpha(1f);
        setPadding(Ui.dp(getContext(), 14), Ui.dp(getContext(), 10), Ui.dp(getContext(), 10), Ui.dp(getContext(), 10));
        setMinimumHeight(Ui.dp(getContext(), 124));
        FrameLayout.LayoutParams rp = (FrameLayout.LayoutParams) row.getLayoutParams();
        rp.height = Ui.dp(getContext(), 104); row.setLayoutParams(rp);
        LinearLayout.LayoutParams op = (LinearLayout.LayoutParams) orb.getLayoutParams();
        op.width = Ui.dp(getContext(), 68); op.height = Ui.dp(getContext(), 68); orb.setLayoutParams(op);
        compactLabel.setVisibility(GONE);
        copy.setVisibility(VISIBLE); copy.setAlpha(1f); copy.setTranslationX(0f);
        controls.setVisibility(VISIBLE); controls.setAlpha(1f); controls.setTranslationX(0f);
        action.setVisibility(primaryAction != null ? VISIBLE : GONE);
        action.setClickable(primaryAction != null);
        glass.setCompactProgress(0f);
        setContentDescription("Lunaxy Voice 语音面板");
        requestLayout();
    }

    public void prepareExpandedFromCompact() {
        compact = false;
        setAlpha(1f);
        setPadding(Ui.dp(getContext(), 14), Ui.dp(getContext(), 10), Ui.dp(getContext(), 10), Ui.dp(getContext(), 10));
        setMinimumHeight(Ui.dp(getContext(), 124));
        FrameLayout.LayoutParams rp = (FrameLayout.LayoutParams) row.getLayoutParams();
        rp.height = Ui.dp(getContext(), 104); row.setLayoutParams(rp);
        LinearLayout.LayoutParams op = (LinearLayout.LayoutParams) orb.getLayoutParams();
        op.width = Ui.dp(getContext(), 68); op.height = Ui.dp(getContext(), 68); orb.setLayoutParams(op);
        compactLabel.setVisibility(GONE);
        copy.setVisibility(VISIBLE); copy.setAlpha(0f); copy.setTranslationX(-Ui.dp(getContext(), 10));
        controls.setVisibility(VISIBLE); controls.setAlpha(0f); controls.setTranslationX(-Ui.dp(getContext(), 8));
        action.setVisibility(primaryAction != null ? VISIBLE : GONE);
        action.setClickable(primaryAction != null);
        requestLayout();
        // The WindowManager has already been expanded before this is called. Use the current
        // laid-out bounds synchronously so the inverse compact→expanded morph begins from the
        // exact compact presentation state; do not post a second mutation into the animation.
        float targetWidth = Math.max(Ui.dp(getContext(), 280f),
                getResources().getDisplayMetrics().widthPixels * .92f);
        float targetHeight = Ui.dp(getContext(), 124f);
        float sx = Math.max(.28f, Math.min(.72f, Ui.dp(getContext(), 156f) / targetWidth));
        float sy = Math.max(.42f, Math.min(.86f, Ui.dp(getContext(), 62f) / targetHeight));
        setPivotX(0f); setPivotY(0f);
        setScaleX(sx); setScaleY(sy); setTranslationY(-Ui.dp(getContext(), 42)); setTranslationX(0f);
        glass.setCompactProgress(1f);
        setContentDescription("Lunaxy Voice 语音面板");
    }

    public void animateToExpanded() {
        if (SpringMotion.isReducedMotion()) {
            setScaleX(1f); setScaleY(1f); setTranslationX(0f); setTranslationY(0f); glass.setCompactProgress(0f);
            copy.setAlpha(1f); copy.setTranslationX(0f); controls.setAlpha(1f); controls.setTranslationX(0f);
            return;
        }
        animate().cancel();
        animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).rotation(0f).alpha(1f)
                .setDuration(520L).setInterpolator(SpringMotion.LAND).start();
        copy.animate().alpha(1f).translationX(0f).setStartDelay(115L).setDuration(330L).setInterpolator(SpringMotion.SOFT).start();
        controls.animate().alpha(1f).translationX(0f).setStartDelay(155L).setDuration(320L).setInterpolator(SpringMotion.SOFT).start();
        animateCompactProgress(0f, 520L);
    }

    public void animateToCompact(Runnable onEnd) {
        compact = true;
        main.removeCallbacks(longPressGuide);
        if (SpringMotion.isReducedMotion()) {
            if (onEnd != null) onEnd.run();
            return;
        }
        float sx = Math.max(.28f, Math.min(.72f, Ui.dp(getContext(), 156f) / Math.max(1f, getWidth())));
        float sy = Math.max(.42f, Math.min(.86f, Ui.dp(getContext(), 62f) / Math.max(1f, getHeight())));
        copy.animate().cancel(); controls.animate().cancel(); animate().cancel();
        copy.animate().alpha(0f).translationX(-Ui.dp(getContext(), 9)).setDuration(140L).setInterpolator(SpringMotion.SNAPPY).start();
        controls.animate().alpha(0f).translationX(-Ui.dp(getContext(), 7)).setDuration(125L).setInterpolator(SpringMotion.SNAPPY).start();
        animateCompactProgress(1f, 460L);
        setPivotX(0f); setPivotY(0f);
        animate().translationX(0f).translationY(-Ui.dp(getContext(), 42)).scaleX(sx).scaleY(sy).rotation(0f).alpha(.985f)
                .setDuration(460L).setInterpolator(SpringMotion.LAND)
                .withEndAction(() -> { if (onEnd != null) onEnd.run(); }).start();
    }

    private void animateCompactProgress(float target, long duration) {
        if (shapeAnimator != null) shapeAnimator.cancel();
        final float start = glass.getCompactProgress();
        if (SpringMotion.isReducedMotion() || Math.abs(start - target) < .01f) {
            glass.setCompactProgress(target);
            return;
        }
        shapeAnimator = ValueAnimator.ofFloat(start, target);
        shapeAnimator.setDuration(Math.max(100L, Math.round(duration * Math.abs(target - start))));
        shapeAnimator.setInterpolator(SpringMotion.TAB_PAGE);
        shapeAnimator.addUpdateListener(a -> glass.setCompactProgress((Float) a.getAnimatedValue()));
        shapeAnimator.start();
    }

    private void showGestureGuide() {
        gestureGuideUntil = SystemClock.uptimeMillis() + 3400L;
        if (!compact) detail.setText(gestureGuideText());
        else compactLabel.setText("手势");
        animate().cancel();
        if (!SpringMotion.isReducedMotion()) {
            setScaleX(Math.min(1f, getScaleX() * .985f));
            setScaleY(Math.min(1f, getScaleY() * .985f));
            animate().scaleX(1f).scaleY(1f).setDuration(220L).setInterpolator(SpringMotion.PRESS).start();
        }
    }

    private String gestureGuideText() { return "← 稍后 10 分钟 · ↓ 收起 · → 打开 Lunaxy · 长按再看手势"; }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            beginGesture(event);
            return compact;
        }
        if (velocityTracker != null) velocityTracker.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE && !dragging) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (Math.hypot(dx, dy) > touchSlop) {
                dragging = true;
                main.removeCallbacks(longPressGuide);
                return true;
            }
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && !dragging) {
            // Expanded child buttons keep their own click stream, but the parent still receives
            // the terminal intercept event. Always cancel the delayed gesture guide so a normal
            // tap never turns into a surprise long-press explanation afterward.
            main.removeCallbacks(longPressGuide);
            recycleVelocityTracker();
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            beginGesture(event);
            return true;
        }
        if (velocityTracker != null) velocityTracker.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                dragging = true;
                main.removeCallbacks(longPressGuide);
            }
            if (dragging) {
                float horizontal = dx * .88f;
                float downward = Math.max(0f, dy) * .82f;
                setTranslationX(horizontal);
                setTranslationY(downward);
                float p = Math.min(1f, Math.max(Math.abs(dx) / Math.max(1f, getWidth()), downward / Math.max(1f, getHeight())));
                setScaleX(1f - p * .018f); setScaleY(1f - p * .018f);
                if (!compact && downward > 0f) {
                    // Direct manipulation: glass corners/material continuously approach capsule
                    // geometry under the finger; release then settles from this exact state.
                    if (shapeAnimator != null) shapeAnimator.cancel();
                    glass.setCompactProgress(Math.min(.72f, downward / Math.max(1f, getHeight()) * .92f));
                }
                setRotation(Math.max(-1.7f, Math.min(1.7f, dx / Math.max(1f, getWidth()) * 3f)));
                if (!thresholdHaptic && (Math.abs(dx) > getWidth() * .20f || downward > getHeight() * .18f)) {
                    thresholdHaptic = true;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            main.removeCallbacks(longPressGuide);
            float dx = event.getRawX() - downX;
            float dy = event.getRawY() - downY;
            float vx = 0f, vy = 0f;
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000);
                vx = velocityTracker.getXVelocity(); vy = velocityTracker.getYVelocity();
                velocityTracker.recycle(); velocityTracker = null;
            }
            boolean wasDragging = dragging;
            dragging = false;
            if (action == MotionEvent.ACTION_CANCEL) { settleGesture(); return true; }
            float fling = Ui.dp(getContext(), 760);
            boolean horizontal = Math.abs(dx) > Math.max(Ui.dp(getContext(), 44), getWidth() * .20f) || Math.abs(vx) > fling;
            boolean down = dy > Math.max(Ui.dp(getContext(), 34), getHeight() * .18f) || vy > Ui.dp(getContext(), 820);
            if (wasDragging && horizontal && Math.abs(dx) >= Math.abs(dy) * .72f) {
                if (dx < 0f || vx < -fling) flingAway(false, snoozeAction);
                else flingAway(true, openAppAction);
            } else if (!compact && wasDragging && down) {
                if (collapseAction != null) collapseAction.run(); else settleGesture();
            } else if (compact && !wasDragging) {
                if (expandAction != null) expandAction.run();
            } else settleGesture();
            return true;
        }
        return true;
    }

    private void beginGesture(MotionEvent event) {
        downX = event.getRawX(); downY = event.getRawY(); dragging = false; thresholdHaptic = false;
        if (velocityTracker != null) velocityTracker.recycle();
        velocityTracker = VelocityTracker.obtain(); velocityTracker.addMovement(event);
        main.removeCallbacks(longPressGuide); main.postDelayed(longPressGuide, 560L);
        animate().cancel();
    }

    private void recycleVelocityTracker() {
        if (velocityTracker == null) return;
        velocityTracker.recycle();
        velocityTracker = null;
    }

    private void settleGesture() {
        animate().cancel();
        animateCompactProgress(compact ? 1f : 0f, 300L);
        animate().translationX(0f).translationY(0f).rotation(0f).scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(SpringMotion.isReducedMotion() ? 90L : 300L).setInterpolator(SpringMotion.LAND).start();
    }

    private void flingAway(boolean right, Runnable action) {
        animate().cancel();
        float target = (right ? 1f : -1f) * Math.max(getWidth() * 1.12f, Ui.dp(getContext(), 230));
        animate().translationX(target).translationY(0f).rotation(right ? 1.6f : -1.6f).alpha(.12f).scaleX(.982f).scaleY(.982f)
                .setDuration(SpringMotion.isReducedMotion() ? 90L : 230L).setInterpolator(SpringMotion.SNAPPY)
                .withEndAction(() -> { if (action != null) action.run(); }).start();
    }

    private int accentForPhase(String phase) {
        if ("result".equals(phase)) return Ui.GREEN;
        if ("error".equals(phase)) return Ui.RED;
        if ("listening".equals(phase) || "wake".equals(phase)) return Ui.CYAN;
        if ("processing".equals(phase)) return Ui.PURPLE;
        return Ui.TEXT_2;
    }

    private String phaseLabel(String phase) {
        if ("wake".equals(phase)) return "已唤醒";
        if ("listening".equals(phase)) return "聆听";
        if ("processing".equals(phase)) return "处理中";
        if ("result".equals(phase)) return "完成";
        if ("error".equals(phase)) return "重试";
        return "待命";
    }

    private String defaultDetail(String phase) {
        if ("listening".equals(phase) || "wake".equals(phase)) return "下一首、播放歌单、搜索歌手…";
        if ("processing".equals(phase)) return "正在把语音变成音乐动作";
        if ("error".equals(phase)) return "可以重试，或打开语音设置切换识别方式";
        return "";
    }
}
