package com.xingyu.music.data;

import android.content.Context;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Canonical Track -> exact catalog variants -> Provider x Variant matrix -> Media3 candidate. */
public final class SourceCoordinator {
    public static final class Resolution {
        public final String url, quality, resolverLabel, providerId, routeKey;
        public final SourceVariant variant;
        public final boolean fromCache;
        public final long resolverLatencyMs;
        public final double matchConfidence;
        public final String providerType, backendGroup;
        final AudioCandidate candidate;
        final Song song;

        Resolution(AudioCandidate candidate, Song song, boolean fromCache) {
            this.candidate = candidate; this.song = song; this.url = candidate.url; this.quality = candidate.quality;
            this.variant = candidate.variant; this.fromCache = fromCache; this.resolverLabel = candidate.providerLabel;
            this.providerId = candidate.providerId; this.routeKey = candidate.routeKey; this.resolverLatencyMs = candidate.resolverLatencyMs;
            this.matchConfidence = candidate.matchConfidence; this.providerType = candidate.providerType; this.backendGroup = candidate.backendGroup;
        }
    }

    private final AudioProviderHub providerHub;
    private final PlaybackUrlStore cache;
    private final TrackVariantStore variantStore;
    private final TrackVariantEnricher enricher;
    private final ConcurrentHashMap<String, CompletableFuture<Resolution>> inFlight = new ConcurrentHashMap<>();

    public SourceCoordinator(Context context) {
        providerHub = new AudioProviderHub(context);
        cache = new PlaybackUrlStore(context);
        variantStore = new TrackVariantStore(context);
        enricher = new TrackVariantEnricher(variantStore);
    }

    public Resolution resolve(Song song, PlaybackTrace trace, Set<String> excludedRoutes) throws Exception {
        if (song == null) throw new Exception("歌曲为空");
        // V3 migration guard: old fallback variants created before the exact matrix are not
        // automatically trusted. Keep the anchor and revalidate every cross-catalog identity
        // from its own raw metadata before it may enter Provider × Variant routing.
        Song enriched = ExactTrackMatcher.sanitizeVariants(song);
        enriched = ExactTrackMatcher.sanitizeVariants(variantStore.enrich(enriched));
        // Never put the known-stable NetEase path behind extra catalog network calls.
        // On-demand enrichment is reserved for TX-anchored tracks; imported playlists are also
        // pre-enriched slowly in the background by MainActivity.
        if ("tx".equals(song.source) && variantStore.shouldEnrich(enriched)) enriched = enricher.enrichIfNeeded(enriched, trace);
        enriched = ExactTrackMatcher.sanitizeVariants(enriched);
        variantStore.remember(enriched);
        List<SourceVariant> variants = enriched.variants();
        if (variants.isEmpty()) throw new Exception("歌曲没有可用平台 ID");
        trace.add("identity", "Catalog variants=" + variantSummary(enriched) + " · anchor=" + Song.providerLabel(song.source));

        Set<String> excluded = excludedRoutes == null ? Collections.emptySet() : excludedRoutes;
        Set<String> crossQualitySkips = new HashSet<>(); // provider|platform, not whole provider
        Exception last = null;
        for (String quality : qualities(enriched)) {
            try {
                Resolution cached = cachedRoute(enriched, quality, excluded, trace);
                if (cached != null) return cached;
                return resolveFresh(enriched, quality, excluded, crossQualitySkips, trace);
            } catch (Exception e) {
                last = e;
                trace.add("quality-fail", "MATRIX/" + quality + " / " + safe(e));
                if (e instanceof ResolverFailure && !((ResolverFailure) e).lowerQualityMayHelp) {
                    trace.add("quality-stop", "MATRIX/" + quality + " · 所有已尝试失败均与码率无关");
                    break;
                }
            }
        }
        throw last == null ? new Exception("没有匹配到可播放线路") : last;
    }

    private Resolution cachedRoute(Song song, String quality, Set<String> excluded, PlaybackTrace trace) {
        String preferred = providerHub.preferredRoute(song, quality); // platform|provider
        List<SourceVariant> variants = new ArrayList<>(song.variants());
        if (!preferred.isEmpty()) {
            String platform = preferred.split("\\|", 2)[0];
            variants.sort((a,b) -> Boolean.compare(platform.equals(b.source), platform.equals(a.source)));
        }
        for (SourceVariant variant : variants) {
            if (!variant.supports(quality)) continue;
            PlaybackUrlStore.Entry stored = cache.get(variant.source, variant.sourceId, quality);
            if (stored == null) continue;
            String route = stored.providerId.isEmpty() ? "cache|" + variant.source + "|" + quality
                    : SourceHealthStore.routeKey(stored.providerId, variant.source, quality);
            if (excluded.contains(route)) { trace.add("cache-skip", "cached route already failed / " + route); continue; }
            if (!preferred.isEmpty() && !preferred.equals(variant.source + "|" + stored.providerId)) continue;
            trace.add("ready-cache", "hit " + Song.providerLabel(variant.source) + "/" + quality + " / "
                    + (stored.providerLabel.isEmpty() ? "历史 READY" : stored.providerLabel));
            AudioCandidate candidate = new AudioCandidate(stored.url, quality,
                    stored.providerId.isEmpty() ? "cache" : stored.providerId,
                    stored.providerLabel.isEmpty() ? "READY Cache" : stored.providerLabel,
                    "ready-cache", "ready-cache", route, variant, 1L, 1.0d, "PROGRESSIVE", "", 0L);
            return new Resolution(candidate, song, true);
        }
        // If preferred cache is missing/expired, allow any other proven READY cache.
        for (SourceVariant variant : variants) {
            if (!variant.supports(quality)) continue;
            PlaybackUrlStore.Entry stored = cache.get(variant.source, variant.sourceId, quality);
            if (stored == null) continue;
            String route = stored.providerId.isEmpty() ? "cache|" + variant.source + "|" + quality
                    : SourceHealthStore.routeKey(stored.providerId, variant.source, quality);
            if (excluded.contains(route)) continue;
            trace.add("ready-cache", "fallback hit " + Song.providerLabel(variant.source) + "/" + quality + " / " + stored.providerLabel);
            AudioCandidate candidate = new AudioCandidate(stored.url, quality,
                    stored.providerId.isEmpty() ? "cache" : stored.providerId,
                    stored.providerLabel.isEmpty() ? "READY Cache" : stored.providerLabel,
                    "ready-cache", "ready-cache", route, variant, 1L, 1.0d, "PROGRESSIVE", "", 0L);
            return new Resolution(candidate, song, true);
        }
        trace.add("ready-cache", "miss MATRIX/" + quality);
        return null;
    }

    private Resolution resolveFresh(Song song, String quality, Set<String> excluded,
                                    Set<String> crossQualitySkips, PlaybackTrace trace) throws Exception {
        String key = song.key() + ":matrix:" + quality + ":" + excluded.hashCode() + ":" + crossQualitySkips.hashCode();
        CompletableFuture<Resolution> mine = new CompletableFuture<>();
        CompletableFuture<Resolution> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            trace.add("dedupe", "join MATRIX/" + quality);
            try { return existing.get(55, TimeUnit.SECONDS); } catch (ExecutionException e) { throw unwrap(e); }
        }
        try {
            AudioCandidate candidate = providerHub.resolveMatrix(song, quality, excluded, crossQualitySkips, trace);
            Resolution resolved = new Resolution(candidate, song, false);
            trace.add("url", candidate.providerLabel + " × " + Song.providerLabel(candidate.variant.source) + " / AudioCandidate obtained / waiting Media3");
            mine.complete(resolved); return resolved;
        } catch (Exception e) { mine.completeExceptionally(e); throw e; }
        finally { inFlight.remove(key, mine); }
    }

    public void markPlaybackSuccess(Resolution r, long timeToReadyMs) {
        if (r == null || r.variant == null || r.song == null) return;
        providerHub.markPlaybackSuccess(r.candidate, r.song, r.resolverLatencyMs + Math.max(1L, timeToReadyMs));
        variantStore.remember(r.song);
        if (!r.fromCache) cache.put(r.variant.source, r.variant.sourceId, r.quality, r.url, r.providerId, r.resolverLabel);
    }
    public void markPlaybackFailure(Resolution r, String reason, long cooldownMs) { if (r != null && r.variant != null && r.song != null) providerHub.markPlaybackFailure(r.candidate, r.song, reason); }
    public void invalidate(Resolution r) { if (r != null && r.variant != null) cache.invalidate(r.variant.source, r.variant.sourceId, r.quality); }
    public void resetCommunityScripts() { providerHub.resetCommunityScripts(); }
    public void resetRouteLearning() { providerHub.resetRouteLearning(); }
    public String runtimeInfo() { return "Lunaxy Route Matrix 4.0 · Decayed Learning · Multi-Catalog TX/KW/KG/WY · LX Host Compat 2.0.0"; }
    public String sourceStatus() { return providerHub.statusText(); }
    public String compactSourceStatus() { return providerHub.compactStatus(); }
    public void destroy() { providerHub.destroy(); enricher.destroy(); }

    private static List<String> qualities(Song song) {
        boolean q320=false,q128=false; for (SourceVariant v : song.variants()) { q320 |= v.quality320; q128 |= v.quality128; }
        List<String> q = new ArrayList<>(); if (q320) q.add("320k"); if (q128 || !q320) q.add("128k"); return q;
    }
    private static String variantSummary(Song s) { StringBuilder b=new StringBuilder(); for (SourceVariant v:s.variants()) { if (b.length()>0)b.append(" · "); b.append(Song.providerLabel(v.source)).append('(').append(v.source).append(')'); } return b.toString(); }
    private static Exception unwrap(ExecutionException e) { Throwable c=e.getCause(); if(c instanceof ResolverFailure)return(ResolverFailure)c; if(c instanceof Exception)return(Exception)c; return new Exception(c==null||c.getMessage()==null?"source failed":c.getMessage()); }
    private static String safe(Exception e) { return e == null || e.getMessage() == null ? "unknown" : e.getMessage(); }
}
