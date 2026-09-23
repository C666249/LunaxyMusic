package com.xingyu.music.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * Lunaxy Visual System 2.0 appearance state.
 *
 * UI-only preferences live beside the existing xingyu_ui_settings file and never enter playback,
 * library, download, or recommendation state.  The selected mode and accessibility fallbacks are
 * resolved before views are built so all new surfaces use one coherent palette/material policy.
 */
public final class AppearanceSystem {
    public static final String PREFS = "xingyu_ui_settings";
    public static final String KEY_MODE = "appearance_mode_v2";
    public static final String KEY_REDUCE_MOTION = "appearance_reduce_motion_v1";
    public static final String KEY_REDUCE_TRANSPARENCY = "appearance_reduce_transparency_v1";

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_DEEP = 1;
    public static final int MODE_OLED = 2;
    public static final int MODE_MOONLIGHT = 3;

    private static int selectedMode = MODE_DEEP;
    private static int effectiveMode = MODE_DEEP;
    private static boolean reduceMotion;
    private static boolean reduceTransparency;

    private AppearanceSystem() { }

    public static void load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        selectedMode = clampMode(p.getInt(KEY_MODE, MODE_DEEP));
        reduceMotion = p.getBoolean(KEY_REDUCE_MOTION, false);
        reduceTransparency = p.getBoolean(KEY_REDUCE_TRANSPARENCY, false);
        resolve(context);
        applyResolved();
    }

    public static void setMode(Context context, int mode) {
        selectedMode = clampMode(mode);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MODE, selectedMode).apply();
        resolve(context);
        applyResolved();
    }

    public static void setReduceMotion(Context context, boolean value) {
        reduceMotion = value;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REDUCE_MOTION, value).apply();
        SpringMotion.setReducedMotion(value);
    }

    public static void setReduceTransparency(Context context, boolean value) {
        reduceTransparency = value;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REDUCE_TRANSPARENCY, value).apply();
        Ui.applyAppearance(effectiveMode, reduceTransparency);
    }

    public static int selectedMode() { return selectedMode; }
    public static int effectiveMode() { return effectiveMode; }
    public static boolean reduceMotion() { return reduceMotion; }
    public static boolean reduceTransparency() { return reduceTransparency; }
    public static boolean isLight() { return effectiveMode == MODE_MOONLIGHT; }

    public static String modeName(int mode) {
        switch (clampMode(mode)) {
            case MODE_DEEP: return "深空";
            case MODE_OLED: return "OLED";
            case MODE_MOONLIGHT: return "月白";
            default: return "跟随系统";
        }
    }

    private static void resolve(Context context) {
        if (selectedMode != MODE_SYSTEM) {
            effectiveMode = selectedMode;
            return;
        }
        int mask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        effectiveMode = mask == Configuration.UI_MODE_NIGHT_NO ? MODE_MOONLIGHT : MODE_DEEP;
    }

    private static void applyResolved() {
        Ui.applyAppearance(effectiveMode, reduceTransparency);
        SpringMotion.setReducedMotion(reduceMotion);
    }

    private static int clampMode(int value) {
        return value < MODE_SYSTEM || value > MODE_MOONLIGHT ? MODE_SYSTEM : value;
    }
}
