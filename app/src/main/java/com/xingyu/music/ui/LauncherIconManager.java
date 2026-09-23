package com.xingyu.music.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Runtime launcher-icon switcher using two manifest activity aliases. */
public final class LauncherIconManager {
    public static final String STYLE_STARDUST = "stardust";
    public static final String STYLE_XM = "xm";
    private static final String PREF = "xingyu_ui_settings";
    private static final String KEY = "launcher_icon";

    private LauncherIconManager() {}

    public static String current(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, STYLE_STARDUST);
    }

    public static void apply(Context context, String style) {
        String chosen = STYLE_XM.equals(style) ? STYLE_XM : STYLE_STARDUST;
        PackageManager pm = context.getPackageManager();
        ComponentName star = new ComponentName(context, context.getPackageName() + ".LauncherStarNote");
        ComponentName xm = new ComponentName(context, context.getPackageName() + ".LauncherXM");
        ComponentName enable = STYLE_XM.equals(chosen) ? xm : star;
        ComponentName disable = STYLE_XM.equals(chosen) ? star : xm;
        pm.setComponentEnabledSetting(enable, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(disable, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, chosen).apply();
    }
}
