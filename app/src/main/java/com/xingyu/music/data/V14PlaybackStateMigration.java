package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * One-time V14 cleanup.
 * Keeps genuine Media3 READY history but removes stale cooldown gates left by
 * V10-V13 so the new stable two-source router starts from a neutral protection state.
 */
public final class V14PlaybackStateMigration {
    private static final String MIGRATION_PREF = "xingyu_migrations";
    private static final String DONE = "v14_stable_source_engine";

    private V14PlaybackStateMigration() { }

    public static void runOnce(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences marker = app.getSharedPreferences(MIGRATION_PREF, Context.MODE_PRIVATE);
        if (marker.getBoolean(DONE, false)) return;

        SharedPreferences health = app.getSharedPreferences("source_health_v8", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = health.edit();
        for (Map.Entry<String, ?> entry : health.getAll().entrySet()) {
            String key = entry.getKey();
            if (key != null && key.endsWith(".cooldown")) edit.remove(key);
        }
        edit.remove("cooldown_schema");
        edit.apply();
        marker.edit().putBoolean(DONE, true).apply();
    }
}
