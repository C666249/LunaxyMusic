package com.xingyu.music.voice;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class VoiceAssistantContract {
    public static final String PREFS = "lunaxy_voice_v1";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_CUSTOM_WAKE = "custom_wake";
    public static final String KEY_OVERLAY = "background_overlay";
    public static final String KEY_RECOGNITION_MODE = "recognition_mode";
    public static final String KEY_OVERLAY_COMPACT = "overlay_compact";
    public static final String KEY_OVERLAY_SNOOZE_UNTIL = "overlay_snooze_until";

    public static final String MODE_AUTO = "auto";
    public static final String MODE_LOCAL = "local";
    public static final String MODE_SYSTEM = "system";

    public static final String ACTION_START = "com.xingyu.music.voice.START";
    public static final String ACTION_STOP = "com.xingyu.music.voice.STOP";
    public static final String ACTION_TEST_LISTEN = "com.xingyu.music.voice.TEST_LISTEN";
    public static final String ACTION_RELOAD_ENGINE = "com.xingyu.music.voice.RELOAD_ENGINE";
    public static final String ACTION_STATE = "com.xingyu.music.voice.STATE";

    public static final String EXTRA_PHASE = "phase";
    public static final String EXTRA_TRANSCRIPT = "transcript";
    public static final String EXTRA_DETAIL = "detail";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_OPEN_SEARCH = "open_search";
    public static final String EXTRA_OPEN_VOICE_SETTINGS = "open_voice_settings";

    public static final String PHASE_OFF = "off";
    public static final String PHASE_ARMED = "armed";
    public static final String PHASE_WAKE = "wake";
    public static final String PHASE_LISTENING = "listening";
    public static final String PHASE_PROCESSING = "processing";
    public static final String PHASE_RESULT = "result";
    public static final String PHASE_ERROR = "error";

    private VoiceAssistantContract() { }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static boolean backgroundOverlayEnabled(Context context) {
        return prefs(context).getBoolean(KEY_OVERLAY, false);
    }


    public static boolean overlayCompact(Context context) {
        return prefs(context).getBoolean(KEY_OVERLAY_COMPACT, false);
    }

    public static void setOverlayCompact(Context context, boolean compact) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_COMPACT, compact).apply();
    }

    public static boolean overlaySnoozed(Context context) {
        return System.currentTimeMillis() < prefs(context).getLong(KEY_OVERLAY_SNOOZE_UNTIL, 0L);
    }

    public static void snoozeOverlay(Context context, long durationMs) {
        long safe = Math.max(0L, durationMs);
        prefs(context).edit().putLong(KEY_OVERLAY_SNOOZE_UNTIL, System.currentTimeMillis() + safe).apply();
    }

    public static void clearOverlaySnooze(Context context) {
        prefs(context).edit().remove(KEY_OVERLAY_SNOOZE_UNTIL).apply();
    }

    public static String customWake(Context context) {
        return prefs(context).getString(KEY_CUSTOM_WAKE, "");
    }

    public static String recognitionMode(Context context) {
        String mode = prefs(context).getString(KEY_RECOGNITION_MODE, MODE_AUTO);
        if (MODE_LOCAL.equals(mode) || MODE_SYSTEM.equals(mode)) return mode;
        return MODE_AUTO;
    }

    public static String recognitionModeLabel(Context context) {
        String mode = recognitionMode(context);
        if (MODE_LOCAL.equals(mode)) return "仅本地";
        if (MODE_SYSTEM.equals(mode)) return "系统识别";
        return "本地优先";
    }

    public static boolean isAutoMode(Context context) {
        return MODE_AUTO.equals(recognitionMode(context));
    }

    public static boolean isLocalOnlyMode(Context context) {
        return MODE_LOCAL.equals(recognitionMode(context));
    }

    public static boolean isSystemOnlyMode(Context context) {
        return MODE_SYSTEM.equals(recognitionMode(context));
    }

    public static String wakeLabel(Context context) {
        String custom = customWake(context).trim();
        return custom.isEmpty() ? "Hey Lunaxy / 露娜希" : custom + " / Hey Lunaxy";
    }

    public static String[] wakeAliases(Context context) {
        List<String> aliases = new ArrayList<>();
        String custom = customWake(context).trim();
        if (!custom.isEmpty()) aliases.add(custom);
        aliases.add("hey lunaxy");
        aliases.add("hi lunaxy");
        aliases.add("你好 lunaxy");
        aliases.add("lunaxy");
        aliases.add("露娜希");
        aliases.add("露娜西");
        aliases.add("卢娜希");
        aliases.add("鲁娜希");
        return aliases.toArray(new String[0]);
    }

    public static Intent serviceIntent(Context context, String action) {
        return new Intent(context, VoiceAssistantService.class).setAction(action);
    }
}
