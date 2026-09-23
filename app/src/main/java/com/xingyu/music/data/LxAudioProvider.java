package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

/** Trusted adapter that hosts one LX custom-source script inside the restricted runtime. */
public final class LxAudioProvider implements AudioProvider {
    private final android.content.Context context;
    private final ResolverProvider spec;
    private volatile LxSourceRuntime runtime;

    public LxAudioProvider(android.content.Context context, ResolverProvider spec) {
        this.context = context.getApplicationContext();
        this.spec = spec;
        // Warm only the top remote candidates. This downloads source scripts in the
        // background, never music URLs, so the first QQ fallback does not wait for
        // several serial script fetches.
        if ("qdy".equals(spec.id) || "juhe".equals(spec.id) || "lx".equals(spec.id)) runtime();
    }

    @Override public String id() { return spec.id; }
    @Override public String label() { return spec.label; }
    @Override public String type() { return "lx-script"; }
    @Override public String backendGroup() { return "lx:" + spec.id; }
    @Override public boolean aggregate() { return spec.aggregateFallback; }

    @Override public int basePriority(String platform) {
        int base = spec.basePriority;
        // Tiny platform hints only shape the very first attempt.  Once a track has
        // a Media3 READY affinity, TrackRouteStore outranks these defaults.
        if ("wy".equals(platform) && "qdy".equals(spec.id)) return base + 22;
        if ("tx".equals(platform)) {
            if ("qdy".equals(spec.id)) return base - 16;   // QQ is the route QDY currently struggles with most.
            if ("huibq".equals(spec.id)) return base - 2; // keep legacy route, but no longer make it the gatekeeper.
        }
        return base;
    }

    @Override public boolean supportsPlatform(SourceVariant variant) throws Exception {
        if (variant == null) return false;
        return runtime().supportsPlatform(variant.source);
    }

    @Override public boolean supports(SourceVariant variant, String quality) throws Exception {
        if (variant == null) return false;
        return runtime().supportsRoute(variant.source, quality);
    }

    @Override public String capabilityHint() {
        LxSourceRuntime current = runtime;
        return current == null ? "脚本尚未加载" : current.capabilitySummary();
    }

    @Override public AudioCandidate resolve(SourceVariant variant, Song song, String quality,
                                            PlaybackTrace trace) throws Exception {
        long start = System.currentTimeMillis();
        LxSourceRuntime.RuntimeResult result = runtime().musicUrl(variant, song, quality);
        long latency = Math.max(1L, System.currentTimeMillis() - start);
        return new AudioCandidate(
                result.url,
                result.quality,
                spec.id,
                spec.label,
                type(),
                backendGroup(),
                SourceHealthStore.routeKey(spec.id, variant.source, result.quality),
                variant,
                latency,
                1.0d, // platform-native ID -> direct candidate for this canonical track
                protocol(result.url),
                "",
                0L);
    }

    @Override public synchronized void resetPinnedSource() {
        if (!"huibq".equals(spec.id)) {
            new SourceScriptStore(context).clearPinned(spec.id);
            recycle();
        }
    }

    @Override public synchronized void recycle() {
        LxSourceRuntime old = runtime;
        runtime = null;
        if (old != null) old.destroy();
    }

    @Override public synchronized void destroy() { recycle(); }

    private synchronized LxSourceRuntime runtime() {
        if (runtime == null) runtime = new LxSourceRuntime(context, spec);
        return runtime;
    }

    private static String protocol(String url) {
        String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains(".m3u8") || lower.contains("m3u8?")) return "HLS";
        if (lower.contains(".mpd") || lower.contains("mpd?")) return "DASH";
        return "PROGRESSIVE";
    }
}
