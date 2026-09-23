package com.xingyu.music.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
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
    // V92.9 Visual System 2.0: neutral tokens are runtime palette values. Brand accents stay stable.
    public static int BG = Color.BLACK;
    public static int SURFACE = Color.rgb(7, 7, 10);
    public static int SURFACE_2 = Color.rgb(12, 12, 17);
    public static int SURFACE_3 = Color.rgb(18, 18, 24);
    public static int TEXT = Color.rgb(248, 248, 250);
    public static int TEXT_2 = Color.rgb(205, 205, 214);
    public static int DIM = Color.rgb(145, 145, 158);
    private static boolean LIGHT_APPEARANCE;
    private static boolean REDUCED_TRANSPARENCY;

    // Brand accents have separate optical values for dark and light canvases.  Reusing the very
    // pale dark-mode accents on white made Moonlight look washed out and reduced selected-state
    // contrast; the hue identity stays the same while light mode uses deeper chroma/value.
    private static final int DARK_PURPLE = Color.rgb(183, 165, 255);
    private static final int DARK_PINK = Color.rgb(255, 144, 195);
    private static final int DARK_CYAN = Color.rgb(123, 211, 255);
    private static final int DARK_GREEN = Color.rgb(135, 224, 175);
    private static final int DARK_GOLD = Color.rgb(244, 204, 126);
    private static final int DARK_ORANGE = Color.rgb(244, 155, 112);
    private static final int DARK_BLUE = Color.rgb(132, 169, 255);
    private static final int DARK_RED = Color.rgb(255, 121, 133);

    // Moonlight accents are intentionally crisp rather than greyed-out.  They sit on almost-white
    // surfaces, so each hue is slightly darker/more chromatic than the dark-mode equivalent while
    // still avoiding neon saturation.
    private static final int LIGHT_PURPLE = Color.rgb(104, 82, 218);
    private static final int LIGHT_PINK = Color.rgb(196, 70, 132);
    private static final int LIGHT_CYAN = Color.rgb(24, 137, 177);
    private static final int LIGHT_GREEN = Color.rgb(34, 145, 101);
    private static final int LIGHT_GOLD = Color.rgb(178, 112, 28);
    private static final int LIGHT_ORANGE = Color.rgb(198, 91, 45);
    private static final int LIGHT_BLUE = Color.rgb(57, 96, 203);
    private static final int LIGHT_RED = Color.rgb(198, 66, 82);

    public static int PURPLE = DARK_PURPLE;
    public static int PINK = DARK_PINK;
    public static int CYAN = DARK_CYAN;
    public static int GREEN = DARK_GREEN;
    public static int GOLD = DARK_GOLD;
    public static int ORANGE = DARK_ORANGE;
    public static int BLUE = DARK_BLUE;
    public static int RED = DARK_RED;

    // V16 synced-lyric default: cool, bright, but not neon.
    public static final int LYRIC_ICE_START = Color.rgb(201, 242, 255);
    public static final int LYRIC_ICE_END = Color.rgb(105, 171, 255);

    private Ui() { }

    /** Applies the app-level palette without coupling playback/business state to presentation. */
    public static void applyAppearance(int mode, boolean reduceTransparency) {
        REDUCED_TRANSPARENCY = reduceTransparency;
        LIGHT_APPEARANCE = mode == AppearanceSystem.MODE_MOONLIGHT;
        if (mode == AppearanceSystem.MODE_MOONLIGHT) {
            // Moonlight is a purpose-built light canvas, not an inverted Deep palette: neutral
            // paper, crisp white functional planes and darker brand accents for real contrast.
            BG = Color.rgb(248, 249, 253);
            SURFACE = Color.rgb(255, 255, 255);
            SURFACE_2 = Color.rgb(252, 253, 255);
            SURFACE_3 = Color.rgb(236, 240, 248);
            TEXT = Color.rgb(20, 22, 30);
            TEXT_2 = Color.rgb(66, 73, 87);
            DIM = Color.rgb(108, 118, 136);
            PURPLE = LIGHT_PURPLE;
            PINK = LIGHT_PINK;
            CYAN = LIGHT_CYAN;
            GREEN = LIGHT_GREEN;
            GOLD = LIGHT_GOLD;
            ORANGE = LIGHT_ORANGE;
            BLUE = LIGHT_BLUE;
            RED = LIGHT_RED;
        } else {
            PURPLE = DARK_PURPLE;
            PINK = DARK_PINK;
            CYAN = DARK_CYAN;
            GREEN = DARK_GREEN;
            GOLD = DARK_GOLD;
            ORANGE = DARK_ORANGE;
            BLUE = DARK_BLUE;
            RED = DARK_RED;
            if (mode == AppearanceSystem.MODE_OLED) {
                BG = Color.BLACK;
                SURFACE = Color.rgb(2, 2, 3);
                SURFACE_2 = Color.rgb(7, 7, 10);
                SURFACE_3 = Color.rgb(13, 13, 18);
                TEXT = Color.rgb(250, 250, 252);
                TEXT_2 = Color.rgb(211, 211, 220);
                DIM = Color.rgb(142, 143, 157);
            } else {
                BG = Color.rgb(3, 4, 8);
                SURFACE = Color.rgb(8, 9, 14);
                SURFACE_2 = Color.rgb(13, 14, 21);
                SURFACE_3 = Color.rgb(20, 21, 30);
                TEXT = Color.rgb(248, 248, 250);
                TEXT_2 = Color.rgb(205, 207, 218);
                DIM = Color.rgb(145, 147, 164);
            }
        }
    }

    public static boolean isLightAppearance() { return LIGHT_APPEARANCE; }
    public static boolean isReducedTransparency() { return REDUCED_TRANSPARENCY; }

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

    /** Theme-aware translucent surface retained for compatibility with older screens. */
    public static GradientDrawable glass(int alpha, float radius, int strokeAlpha, Context c) {
        int a = REDUCED_TRANSPARENCY ? 255 : Math.max(LIGHT_APPEARANCE ? 242 : 0, alpha);
        int base = SURFACE;
        int border = LIGHT_APPEARANCE ? Color.argb(Math.max(16, Math.min(28, strokeAlpha)), 38, 44, 56)
                : Color.argb(strokeAlpha, 255, 255, 255);
        return stroke(Color.argb(a, Color.red(base), Color.green(base), Color.blue(base)), radius, border, c);
    }

    /** Stable content plane: deliberately more opaque than controls/navigation. */
    public static GradientDrawable contentSurface(float radius, Context c) {
        int a = REDUCED_TRANSPARENCY ? 255 : (LIGHT_APPEARANCE ? 255 : 244);
        int border = LIGHT_APPEARANCE ? Color.argb(18, 38, 44, 56) : Color.argb(18, 255, 255, 255);
        return stroke(Color.argb(a, Color.red(SURFACE_2), Color.green(SURFACE_2), Color.blue(SURFACE_2)), radius, border, c);
    }

    /** Floating functional layer: navigation, mini player and compact contextual controls. */
    public static GradientDrawable functionalGlass(float radius, Context c) {
        int a = REDUCED_TRANSPARENCY ? 255 : (LIGHT_APPEARANCE ? 248 : 224);
        int border = LIGHT_APPEARANCE ? Color.argb(24, 38, 44, 56) : Color.argb(44, 230, 232, 246);
        return stroke(Color.argb(a, Color.red(SURFACE), Color.green(SURFACE), Color.blue(SURFACE)), radius, border, c);
    }

    /** Temporary interaction layer: sheets/popovers get the strongest separation. */
    public static GradientDrawable transientGlass(float radius, Context c) {
        int a = REDUCED_TRANSPARENCY ? 255 : (LIGHT_APPEARANCE ? 253 : 247);
        int border = LIGHT_APPEARANCE ? Color.argb(26, 38, 44, 56) : Color.argb(42, 255, 255, 255);
        return stroke(Color.argb(a, Color.red(SURFACE), Color.green(SURFACE), Color.blue(SURFACE)), radius, border, c);
    }

    /** Tinted surface: accent is present only as a trace, never a full neon card. */
    public static GradientDrawable tintedGlass(int accent, float radius, Context c) {
        int base = SURFACE_2;
        float tintAmount = LIGHT_APPEARANCE ? .085f : .075f;
        int r = mixChannel(Color.red(base), Color.red(accent), tintAmount);
        int g = mixChannel(Color.green(base), Color.green(accent), tintAmount);
        int b = mixChannel(Color.blue(base), Color.blue(accent), tintAmount);
        int fillAlpha = REDUCED_TRANSPARENCY ? 255 : (LIGHT_APPEARANCE ? 252 : 236);
        int borderAlpha = LIGHT_APPEARANCE ? 30 : 34;
        return stroke(Color.argb(fillAlpha, r, g, b), radius,
                Color.argb(borderAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)), c);
    }

    /** Flat premium primary action. Deliberately avoids the V15 purple-cyan AI gradient. */
    public static GradientDrawable primaryFill(int accent, float radius, Context c) {
        int fill = mix(accent, Color.WHITE, LIGHT_APPEARANCE ? .24f : .42f);
        GradientDrawable d = round(fill, radius, c);
        int stroke = LIGHT_APPEARANCE ? withAlpha(mix(accent, TEXT, .22f), 48) : withAlpha(Color.WHITE, 42);
        d.setStroke(dp(c, 1), stroke);
        return d;
    }

    /** Compact split/segmented action container used for paired contextual controls. */
    public static GradientDrawable segmentedSurface(float radius, Context c) {
        int fill = LIGHT_APPEARANCE ? Color.rgb(255, 255, 255) : SURFACE_2;
        int alpha = REDUCED_TRANSPARENCY ? 255 : (LIGHT_APPEARANCE ? 252 : 232);
        int border = LIGHT_APPEARANCE ? Color.argb(24, 64, 72, 92) : Color.argb(34, 235, 238, 248);
        return stroke(Color.argb(alpha, Color.red(fill), Color.green(fill), Color.blue(fill)), radius, border, c);
    }

    /**
     * Player utility controls get an accent-tinted plane instead of a generic grey circle.  On
     * Moonlight the tint is deliberately visible; in dark modes it remains a quiet trace.
     */
    public static GradientDrawable playerControlSurface(int accent, float radius, Context c) {
        int base = LIGHT_APPEARANCE ? Color.WHITE : SURFACE_2;
        float amount = LIGHT_APPEARANCE ? .105f : .060f;
        int fill = mix(base, accent, amount);
        int fillAlpha = REDUCED_TRANSPARENCY ? 255 : (LIGHT_APPEARANCE ? 252 : 222);
        int borderAlpha = LIGHT_APPEARANCE ? 42 : 30;
        return stroke(Color.argb(fillAlpha, Color.red(fill), Color.green(fill), Color.blue(fill)), radius,
                Color.argb(borderAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)), c);
    }

    /** Optical icon colour paired with playerControlSurface; avoids washed-out grey on Moonlight. */
    public static int playerControlIconColor(int accent) {
        return LIGHT_APPEARANCE ? mix(accent, TEXT, .10f) : mix(accent, Color.WHITE, .10f);
    }

    /**
     * V92.9.6 player glyph treatment: the icon is the resting control; material appears only
     * under touch as a bounded accent ripple. This removes the wall of translucent circles in
     * Moonlight while preserving a 42–50dp hit target and immediate physical acknowledgement.
     */
    public static Drawable playerIconRipple(int accent, float radius, Context c) {
        GradientDrawable content = round(Color.TRANSPARENT, radius, c);
        GradientDrawable mask = round(Color.WHITE, radius, c);
        int rippleColor = LIGHT_APPEARANCE
                ? withAlpha(mix(accent, TEXT, .08f), 54)
                : withAlpha(mix(accent, Color.WHITE, .12f), 66);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
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
