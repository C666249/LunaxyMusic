package com.xingyu.music.ui;

import android.content.Context;
import android.animation.ValueAnimator;
import android.os.Build;
import android.graphics.RenderEffect;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/**
 * One synced lyric row. The active row is painted twice: a subdued base pass and
 * a clipped gradient pass whose reveal position follows karaoke timing. That gives
 * true word/character-progress rendering while still supporting wrapped lines.
 */
public final class LyricLineView extends TextView {
    private boolean active;
    private boolean seekSelected;
    private int gradientStart = Ui.LYRIC_ICE_START;
    private int gradientEnd = Ui.LYRIC_ICE_END;
    private float karaokeProgress;
    private int focusDistance = 3;
    private ValueAnimator focusAnimator;
    private float fontScale=1f;
    public void setFontScale(float value) { fontScale=Math.max(.8f,Math.min(1.6f,value)); refreshState(false); }

    public LyricLineView(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        setIncludeFontPadding(false);
        setLineSpacing(0f, 1.12f);
        setTextSize(14f);
        setTextColor(Ui.DIM);
        setTypeface(Typeface.DEFAULT);
        setAlpha(.78f);
    }

    public void setGradientColors(int start, int end) {
        gradientStart = start;
        gradientEnd = end;
        invalidate();
    }

    public void setActiveLine(boolean value) {
        if (active == value) return;
        active = value;
        if (!active) karaokeProgress = 0f;
        refreshState(true);
    }

    /** Distance from the live line. Farther rows are gently de-emphasised instead of all sharing one alpha. */
    public void setFocusDistance(int distance) {
        int next = Math.max(0, Math.min(4, distance));
        if (focusDistance == next) return;
        focusDistance = next;
        if (!active && !seekSelected) refreshState(true);
    }

    public void setKaraokeProgress(float value) {
        float next = Math.max(0f, Math.min(1f, value));
        if (Math.abs(next - karaokeProgress) < .002f) return;
        karaokeProgress = next;
        if (active) invalidate();
    }

    public void setSeekSelected(boolean value) {
        if (seekSelected == value) return;
        seekSelected = value;
        refreshState(true);
    }

    private void refreshState(boolean animate) {
        float targetSize = (active ? 16.4f : (seekSelected ? 15.1f : 14f))*fontScale;
        float targetAlpha;
        if (active) targetAlpha = 1f;
        else if (seekSelected) targetAlpha = .98f;
        else targetAlpha = Math.max(.40f, .73f - Math.max(0, focusDistance - 1) * .095f);
        float targetScale = active ? 1.035f : (seekSelected ? 1.012f : 1f - Math.min(3, focusDistance) * .004f);
        float targetLetter = active ? .012f : (seekSelected ? .006f : .002f);

        setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        // The not-yet-sung portion stays visible but quieter. The gradient overlay
        // progressively replaces it from left to right / line to line.
        setTextColor(active ? Color.rgb(150, 157, 170) : (seekSelected ? Ui.TEXT : Ui.DIM));
        if (seekSelected) {
            GradientDrawable focus = new GradientDrawable();
            focus.setColor(Color.argb(16, 220, 229, 246));
            focus.setCornerRadius(Ui.dp(getContext(), 10));
            setBackground(focus);
        } else {
            setBackground(null);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            float density = getResources().getDisplayMetrics().density;
            float blur = (!active && !seekSelected && focusDistance >= 2) ? (.22f + .14f * (focusDistance - 2)) * density : 0f;
            setRenderEffect(blur > .01f ? RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP) : null);
        }
        if (active) setShadowLayer(Ui.dp(getContext(), .7f), 0f, 0f, Color.argb(70, 170, 220, 255));
        else getPaint().clearShadowLayer();

        if (!animate) {
            setTextSize(targetSize); setAlpha(targetAlpha); setScaleX(targetScale); setScaleY(targetScale);
            if (Build.VERSION.SDK_INT >= 21) setLetterSpacing(targetLetter);
        } else {
            animate().cancel();
            animate().alpha(targetAlpha).scaleX(targetScale).scaleY(targetScale)
                    .setDuration(SpringMotion.isReducedMotion() ? SpringMotion.selectionDuration() : (active ? 250L : 210L))
                    .setInterpolator(active ? SpringMotion.SOFT : SpringMotion.SNAPPY).start();
            if (focusAnimator != null) focusAnimator.cancel();
            final float startSize = getTextSize() / getResources().getDisplayMetrics().scaledDensity;
            final float startLetter = Build.VERSION.SDK_INT >= 21 ? getLetterSpacing() : 0f;
            focusAnimator = ValueAnimator.ofFloat(0f, 1f);
            focusAnimator.setDuration(SpringMotion.isReducedMotion() ? SpringMotion.selectionDuration() : (active ? 250L : 210L));
            focusAnimator.setInterpolator(active ? SpringMotion.SOFT : SpringMotion.SNAPPY);
            focusAnimator.addUpdateListener(a -> {
                float t = (Float) a.getAnimatedValue();
                setTextSize(startSize + (targetSize - startSize) * t);
                if (Build.VERSION.SDK_INT >= 21) setLetterSpacing(startLetter + (targetLetter - startLetter) * t);
            });
            focusAnimator.start();
        }
        getPaint().setShader(null);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!active || getWidth() <= 0 || getLayout() == null) {
            getPaint().setShader(null);
            super.onDraw(canvas);
            return;
        }

        // Base (future) lyric.
        getPaint().setShader(null);
        super.onDraw(canvas);
        if (karaokeProgress <= .001f) return;

        Layout layout = getLayout();
        CharSequence value = getText();
        int length = value == null ? 0 : value.length();
        if (length <= 0) return;

        // V20+ uses sentence-level highlighting (the active line is 100% highlighted).
        // Handle that common path directly instead of routing it through a selection
        // clip.  This guarantees that the style chooser paints EVERY glyph with the
        // chosen gradient, rather than leaving most of the sample looking gray/white.
        if (karaokeProgress >= .999f) {
            applyTextGradient(layout);
            super.onDraw(canvas);
            getPaint().setShader(null);
            return;
        }

        // Keep partial reveal support for compatibility with any future karaoke mode.
        float charPos = Math.min(length, karaokeProgress * length);
        int whole = Math.min(length, (int) Math.floor(charPos));
        float partial = charPos - whole;
        Path reveal = new Path();
        if (whole > 0) layout.getSelectionPath(0, whole, reveal);

        if (whole < length && partial > .001f) {
            int line = layout.getLineForOffset(whole);
            float x1 = layout.getPrimaryHorizontal(whole);
            float x2 = layout.getPrimaryHorizontal(Math.min(length, whole + 1));
            float left = Math.min(x1, x2);
            float right = Math.max(x1, x2);
            if (right - left < 1f) right = left + Math.max(1f, getTextSize() * .45f);
            reveal.addRect(left, layout.getLineTop(line), left + (right - left) * partial,
                    layout.getLineBottom(line), Path.Direction.CW);
        }

        int xOffset = getCompoundPaddingLeft();
        int yOffset = getExtendedPaddingTop();
        reveal.offset(xOffset, yOffset);

        int save = canvas.save();
        canvas.clipPath(reveal);
        applyTextGradient(layout);
        super.onDraw(canvas);
        getPaint().setShader(null);
        canvas.restoreToCount(save);
    }

    /** Paint the gradient across the actual text span, not across the empty row width. */
    private void applyTextGradient(Layout layout) {
        float textLeft = Float.MAX_VALUE;
        float textRight = -Float.MAX_VALUE;
        for (int line = 0; line < layout.getLineCount(); line++) {
            float a = layout.getLineLeft(line);
            float b = layout.getLineRight(line);
            textLeft = Math.min(textLeft, Math.min(a, b));
            textRight = Math.max(textRight, Math.max(a, b));
        }
        if (textLeft == Float.MAX_VALUE || textRight <= textLeft + 1f) {
            textLeft = 0f;
            textRight = Math.max(1f, layout.getWidth());
        }
        getPaint().setShader(new LinearGradient(
                textLeft, 0f, textRight, 0f,
                gradientStart, gradientEnd, Shader.TileMode.CLAMP));
    }
}
