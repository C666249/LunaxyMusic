package com.xingyu.music.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers a proven QQ songmid -> NetEase song id lyric-only mapping. */
public final class LyricMatchStore {
    private final SharedPreferences prefs;

    public LyricMatchStore(Context context) {
        prefs = context.getSharedPreferences("xingyu_lyric_match_v20", Context.MODE_PRIVATE);
    }

    public String getNeteaseId(String qqMid) {
        if (qqMid == null || qqMid.trim().isEmpty()) return "";
        return prefs.getString("tx:" + qqMid.trim(), "");
    }

    public void putNeteaseId(String qqMid, String wyId) {
        if (qqMid == null || wyId == null || qqMid.trim().isEmpty() || wyId.trim().isEmpty()) return;
        prefs.edit().putString("tx:" + qqMid.trim(), wyId.trim()).apply();
    }
}
