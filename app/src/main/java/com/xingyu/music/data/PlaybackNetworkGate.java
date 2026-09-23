package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * One-way safety gate from playback -> background download.
 * Playback may pause download resolver/network I/O. Download code never writes playback health,
 * route affinity, READY cache or playback state.
 */
public final class PlaybackNetworkGate {
    private static final String PREFS = "lunaxy_playback_gate_v1";
    private static final String KEY_BUSY_UNTIL = "busy_until";
    private static final String KEY_REASON = "reason";
    private static volatile long processBusyUntil;
    private static volatile String processReason = "";

    private PlaybackNetworkGate() { }

    public static void setBusy(Context context, boolean busy, String reason) {
        long until = busy ? System.currentTimeMillis() + 15_000L : 0L;
        processBusyUntil = until;
        processReason = busy && reason != null ? reason : "";
        if (context != null) {
            SharedPreferences.Editor e = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            if (busy) e.putLong(KEY_BUSY_UNTIL, until).putString(KEY_REASON, processReason);
            else e.remove(KEY_BUSY_UNTIL).remove(KEY_REASON);
            e.apply();
        }
    }

    /** Refresh while Media3 remains BUFFERING so long buffers keep downloads yielded. */
    public static void heartbeat(Context context, String reason) { setBusy(context, true, reason); }

    public static boolean isBusy(Context context) {
        long now = System.currentTimeMillis();
        if (processBusyUntil > now) return true;
        if (context == null) return false;
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_BUSY_UNTIL, 0L) > now;
    }

    public static String reason(Context context) {
        if (processBusyUntil > System.currentTimeMillis()) return processReason;
        if (context == null) return "";
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_REASON, "");
    }
}
