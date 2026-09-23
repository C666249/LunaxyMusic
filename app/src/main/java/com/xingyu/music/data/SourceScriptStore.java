package com.xingyu.music.data;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V14 stable source store.
 *
 * Important behaviour change:
 * - bundled Huibq is always used as-is; it is never silently replaced by a remote latest.js.
 * - non-bundled primary sources (QDY) are downloaded only when no pinned copy exists.
 *   The first valid copy is then pinned on disk and reused indefinitely.
 * - if an older V8/V10 cache exists, it is migrated first so upgrades preserve the exact
 *   script snapshot that may already have proven itself on the device.
 *
 * This makes one APK reproducible across launches instead of changing its resolver code
 * every 24 hours.
 */
public final class SourceScriptStore {
    private final Context context;
    private final File stableDir;
    private final File legacyDir;

    public SourceScriptStore(Context context) {
        this.context = context.getApplicationContext();
        stableDir = new File(this.context.getFilesDir(), "lx-source-stable-v14");
        if (!stableDir.exists()) stableDir.mkdirs();
        legacyDir = new File(this.context.getFilesDir(), "lx-source-cache-v8");
    }

    public String load(ResolverProvider provider) throws Exception {
        // Huibq is deliberately frozen to the bundled source snapshot.
        if (!provider.bundledAsset.isEmpty() && "huibq".equals(provider.id)) {
            String bundled = readAsset(provider.bundledAsset);
            if (!looksLikeScript(bundled)) throw new Exception(provider.label + " 内置音源脚本无效");
            return bundled;
        }

        File pinned = new File(stableDir, provider.id + ".js");
        String pinnedText = pinned.exists() ? readFile(pinned) : "";
        if (looksLikeScript(pinnedText)) return pinnedText;

        // Preserve a known-good script from V8/V10/V11 if the user upgraded without uninstalling.
        File legacy = new File(legacyDir, provider.id + ".js");
        if (legacy.exists()) {
            String legacyText = readFile(legacy);
            if (looksLikeScript(legacyText)) {
                writeFile(pinned, legacyText);
                return legacyText;
            }
        }

        // Fresh install: fetch once, pin once. No 24h auto-refresh afterwards.
        if (!provider.scriptUrl.isEmpty()) {
            Map<String,String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", "SunflowerMusic/2.0 Android");
            headers.put("Accept", "text/plain, application/javascript, */*");
            for (String candidate : candidates(provider.scriptUrl)) {
                try {
                    Http.Response r = Http.get(candidate, headers, 6500, 9000);
                    if (r.code >= 200 && r.code < 300 && looksLikeScript(r.body)) {
                        writeFile(pinned, r.body);
                        return r.body;
                    }
                } catch (Exception ignored) { }
            }
        }

        if (!provider.bundledAsset.isEmpty()) {
            String bundled = readAsset(provider.bundledAsset);
            if (looksLikeScript(bundled)) return bundled;
        }
        throw new Exception(provider.label + " 音源脚本暂时无法加载");
    }

    /** Manual maintenance hook for a future explicit source update action. */
    public void clearPinned(String providerId) {
        if (providerId == null || providerId.isEmpty()) return;
        try { new File(stableDir, providerId + ".js").delete(); }
        catch (Exception ignored) { }
    }

    private static java.util.List<String> candidates(String url) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        out.add(url);
        if (url.contains("fastly.jsdelivr.net/gh/")) out.add(url.replace("fastly.jsdelivr.net/gh/", "cdn.jsdelivr.net/gh/"));
        String marker = "jsdelivr.net/gh/";
        int i = url.indexOf(marker);
        if (i >= 0) {
            String tail = url.substring(i + marker.length());
            String[] parts = tail.split("/", 3);
            if (parts.length == 3 && parts[1].contains("@")) {
                int at = parts[1].lastIndexOf('@');
                String repo = parts[1].substring(0, at);
                String ref = parts[1].substring(at + 1);
                out.add("https://raw.githubusercontent.com/" + parts[0] + "/" + repo + "/" + ref + "/" + parts[2]);
            }
        }
        return out;
    }

    private static boolean looksLikeScript(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.length() > 80 && (t.contains("globalThis.lx") || t.contains("EVENT_NAMES") || t.contains("lx"));
    }

    private String readAsset(String name) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(name), StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) b.append(line).append('\n');
            return b.toString();
        }
    }

    private static String readFile(File f) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) b.append(line).append('\n');
            return b.toString();
        }
    }

    private static void writeFile(File f, String s) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
