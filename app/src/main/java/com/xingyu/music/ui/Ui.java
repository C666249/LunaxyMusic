package com.xingyu.music.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Shared visual tokens for the native UI. Playback code must not depend on this class. */
public final class Ui {
    public static final int BG = Color.BLACK;
    public static final int SURFACE = Color.rgb(7, 7, 10);
    public static final int SURFACE_2 = Color.rgb(12, 12, 17);
    public static final int SURFACE_3 = Color.rgb(18, 18, 24);
    public static final int TEXT = Color.rgb(248, 248, 250);
    public static final int TEXT_2 = Color.rgb(205, 205, 214);
    public static final int DIM = Color.rgb(145, 145, 158);
    public static final int PURPLE = Color.rgb(183, 165, 255);
    public static final int PINK = Color.rgb(255, 144, 195);
    public static final int CYAN = Color.rgb(123, 211, 255);
    public static final int GREEN = Color.rgb(135, 224, 175);
    public static final int GOLD = Color.rgb(244, 204, 126);
    public static final int ORANGE = Color.rgb(244, 155, 112);
    public static final int BLUE = Color.rgb(132, 169, 255);
    public static final int RED = Color.rgb(255, 121, 133);

    // V16 synced-lyric default: cool, bright, but not neon.
    public static final int LYRIC_ICE_START = Color.rgb(201, 242, 255);
    public static final int LYRIC_ICE_END = Color.rgb(105, 171, 255);

    private Ui() { }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextColor(color);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false);
        v.setFontFeatureSettings("kern");
        v.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return v;
    }

    public static GradientDrawable round(int color, float radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    public static GradientDrawable stroke(int color, float radius, int strokeColor, Context c) {
        GradientDrawable d = round(color, radius, c);
        d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    /** Calm translucent surface used for most cards/sheets in V16. */
    public static GradientDrawable glass(int alpha, float radius, int strokeAlpha, Context c) {
        return stroke(Color.argb(alpha, 11, 11, 16), radius,
                Color.argb(strokeAlpha, 255, 255, 255), c);
    }

    /** Tinted surface: accent is present only as a trace, never a full neon card. */
    public static GradientDrawable tintedGlass(int accent, float radius, Context c) {
        int r = mixChannel(11, Color.red(accent), .07f);
        int g = mixChannel(11, Color.green(accent), .07f);
        int b = mixChannel(16, Color.blue(accent), .07f);
        return stroke(Color.argb(232, r, g, b), radius,
                Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent)), c);
    }

    /** Flat premium primary action. Deliberately avoids the V15 purple-cyan AI gradient. */
    public static GradientDrawable primaryFill(int accent, float radius, Context c) {
        int fill = mix(accent, Color.WHITE, .42f);
        GradientDrawable d = round(fill, radius, c);
        d.setStroke(dp(c, 1), withAlpha(Color.WHITE, 42));
        return d;
    }

    public static GradientDrawable gradient(int[] colors, float radius, Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    /** Legacy helper kept for compatibility; new V16 screens prefer flat primaryFill/glass. */
    public static GradientDrawable premiumGradient(int first, int second, float radius, Context c) {
        int a = mix(first, Color.WHITE, .22f);
        int b = mix(second, first, .62f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        d.setCornerRadius(dp(c, radius));
        return d;
    }

    public static int mix(int a, int b, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(a) * (1f - t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1f - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1f - t) + Color.blue(b) * t));
    }

    private static int mixChannel(int channel, int otherChannel, float amount) {
        return Math.round(channel * (1f - amount) + otherChannel * amount);
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.gravity = gravity;
        return p;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static void margins(ViewGroup.MarginLayoutParams p, int l, int t, int r, int b, Context c) {
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
    }

    public static TextView pill(Context c, String label, int tint) {
        TextView v = text(c, label, 10.2f, mix(tint, Color.WHITE, .10f), true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3));
        v.setBackground(stroke(
                Color.argb(12, Color.red(tint), Color.green(tint), Color.blue(tint)),
                12,
                Color.argb(42, Color.red(tint), Color.green(tint), Color.blue(tint)), c));
        return v;
    }

    public static FrameLayout iconButton(Context c, IconView.Type icon, int sizeDp, int iconColor, int bgColor) {
        FrameLayout b = new FrameLayout(c);
        b.setClickable(true);
        b.setFocusable(true);
        if (Color.alpha(bgColor) == 0) {
            b.setBackgroundColor(Color.TRANSPARENT);
        } else {
            b.setBackground(stroke(bgColor, sizeDp / 2f, Color.argb(24, 255, 255, 255), c));
        }
        IconView iv = new IconView(c, icon, iconColor);
        iv.setStrokeDp(sizeDp >= 54 ? 1.72f : 1.62f);
        int inner = Math.max(17, (int) (sizeDp * .46f));
        b.addView(iv, frame(dp(c, inner), dp(c, inner), Gravity.CENTER));
        applyRipple(b, Color.argb(48, 255, 255, 255));
        return b;
    }

    /**
     * Lunaxy press physics.  Large surfaces compress less than compact icon buttons, so the whole
     * app shares one tactile language without every control looking like it is "popping".
     */
    public static void applyRipple(View view, int ignoredRippleColor) {
        if (view == null) return;
        view.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float min = Math.min(v.getWidth(), v.getHeight());
                    float density = Math.max(.1f, v.getResources().getDisplayMetrics().density);
                    float dp = min <= 0f ? 48f : min / density;
                    float scale = dp <= 48f ? .958f : (dp <= 84f ? .968f : .978f);
                    SpringMotion.pressDown(v, scale);
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    SpringMotion.pressUp(v);
                    break;
            }
            return false;
        });
    }

    public static String time(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
