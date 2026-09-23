package com.xingyu.music.data;

import android.content.Context;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lunaxy V48 route matrix.
 *
 * Catalog variant and audio provider are independent axes.  The scheduler ranks
 * Provider x Variant cells globally, so a broken TX route does not need to die
 * through every provider before an exact KW/KG variant can win.
 */
public final class AudioProviderHub {
    private static final long RATE_COOLDOWN = 20L * 60L * 1000L;
    private static final long BLOCK_COOLDOWN = 60L * 60L * 1000L;
    private static final long SOFT_NETWORK_COOLDOWN = 15L * 1000L;
    private static final long INIT_COOLDOWN = 60L * 1000L;
    private static final long SERVICE_COOLDOWN = 60L * 1000L;
    private static final long RECENT_READY_WINDOW = 10L * 60L * 1000L;
    private static final long MATRIX_BUDGET_MS = 20_000L;
    private static final int MATRIX_MAX_VISITED_CELLS = 18;
    private static final int MATRIX_MAX_RESOLVE_ATTEMPTS = 14;
    private static final String[] MATRIX_PLATFORMS = {"tx", "kw", "kg", "wy"};

    private final SourceHealthStore health;
    private final TrackRouteStore trackRoutes;
    private final List<AudioProvider> providers = new ArrayList<>();
    private final ConcurrentHashMap<String, Long> recentReady = new ConcurrentHashMap<>();

    public AudioProviderHub(Context context) {
        Context app = context.getApplicationContext();
        health = new SourceHealthStore(app);
        trackRoutes = new TrackRouteStore(app);
        add(app, new ResolverProvider("qdy", "全豆要 QDY", jsdelivr("qdy"), "", 108, true));
        add(app, new ResolverProvider("huibq", "Huibq", "", "source/huibq-latest.js", 102, false));
        add(app, new ResolverProvider("juhe", "Juhe 聚合", jsdelivr("juhe"), "", 116, true));
        add(app, new ResolverProvider("lx", "LX Source", jsdelivr("lx"), "", 112, true));
        add(app, new ResolverProvider("flower", "Flower 野花", jsdelivr("flower"), "", 106, false));
        add(app, new ResolverProvider("grass", "Grass", jsdelivr("grass"), "", 100, false));
        add(app, new ResolverProvider("sixyin", "SixYin 六音", jsdelivr("sixyin"), "", 96, false));
        add(app, new ResolverProvider("ikun", "ikun", jsdelivr("ikun"), "", 92, false));
    }

    public AudioCandidate resolveMatrix(Song song,
                                        String quality,
                                        Set<String> excludedRoutes,
                                        Set<String> qualityIndependentProviderPlatforms,
                                        PlaybackTrace trace) throws Exception {
        if (song == null) throw new ResolverFailure(ResolverFailure.Kind.UNKNOWN, "Canonical Track 为空", false);
        List<SourceVariant> variants = song.variants();
        if (variants.isEmpty()) throw new ResolverFailure(ResolverFailure.Kind.TRACK_UNAVAILABLE, "歌曲没有可用平台 ID", false);
        Set<String> excluded = excludedRoutes == null ? Collections.emptySet() : excludedRoutes;
        Set<String> crossQualitySkips = qualityIndependentProviderPlatforms == null
                ? ConcurrentHashMap.newKeySet() : qualityIndependentProviderPlatforms;
        List<RouteCell> cells = rankedCells(song, quality);
        ResolverFailure last = null;
        boolean lowerQualityMayHelp = false;
        int attempted = 0, visited = 0;
        long matrixStartedAt = System.currentTimeMillis();
        Set<String> deadBackends = new HashSet<>();

        trace.add("matrix", "quality=" + quality + " · variants=" + variantLabels(song) + " · cells=" + cells.size());
        for (RouteCell cell : cells) {
            if (visited++ >= MATRIX_MAX_VISITED_CELLS || attempted >= MATRIX_MAX_RESOLVE_ATTEMPTS
                    || System.currentTimeMillis() - matrixStartedAt >= MATRIX_BUDGET_MS) {
                trace.add("matrix-budget", "停止继续轰炸 · visited=" + visited + " · attempted=" + attempted
                        + " · elapsed=" + (System.currentTimeMillis() - matrixStartedAt) + "ms");
                break;
            }
            AudioProvider provider = cell.provider;
            SourceVariant variant = cell.variant;
            String pp = provider.id() + "|" + variant.source;
            String route = SourceHealthStore.routeKey(provider.id(), variant.source, quality);

            if (deadBackends.contains(provider.backendGroup())) {
                trace.add("matrix-skip", provider.label() + " · backend " + provider.backendGroup() + " 本轮已熔断");
                continue;
            }
            if (crossQualitySkips.contains(pp)) {
                trace.add("matrix-skip", provider.label() + " × " + Song.providerLabel(variant.source) + " · 上一档已证明与码率无关");
                continue;
            }
            if (excluded.contains(route)) {
                trace.add("route-skip", provider.label() + " × " + Song.providerLabel(variant.source) + " / " + quality + " · 本轮已失败");
                continue;
            }
            if (!variant.supports(quality)) {
                if (!"128k".equalsIgnoreCase(quality)) lowerQualityMayHelp = true;
                continue;
            }
            if (health.cooling(provider.id(), variant.source, quality)) {
                long seconds = Math.max(1L, health.cooldownRemaining(provider.id(), variant.source, quality) / 1000L);
                trace.add("matrix-skip", provider.label() + " × " + Song.providerLabel(variant.source) + " · 冷却 " + seconds + "s");
                continue;
            }

            int tries = provider.aggregate() ? 2 : 1;
            for (int tryIndex = 0; tryIndex < tries; tryIndex++) {
                long start = System.currentTimeMillis();
                try {
                    if (!provider.supportsPlatform(variant)) {
                        trace.add("matrix-skip", provider.label() + " × " + Song.providerLabel(variant.source) + " · 平台不支持");
                        crossQualitySkips.add(pp);
                        break;
                    }
                    if (!provider.supports(variant, quality)) {
                        trace.add("matrix-skip", provider.label() + " × " + Song.providerLabel(variant.source) + " · 不支持 " + quality);
                        if (!"128k".equalsIgnoreCase(quality)) lowerQualityMayHelp = true;
                        break;
                    }
                    attempted++;
                    trace.add("route", provider.label() + " × " + Song.providerLabel(variant.source) + " → " + quality
                            + " · score=" + cell.score + (tryIndex > 0 ? " · Runtime重建" : ""));
                    AudioCandidate candidate = provider.resolve(variant, song, quality, trace);
                    if (candidate == null || !candidate.valid()) throw new ResolverFailure(ResolverFailure.Kind.INVALID_CANDIDATE, provider.label() + " 返回无效音频候选", false);
                    if (candidate.matchConfidence < 0.98d) throw new ResolverFailure(ResolverFailure.Kind.INVALID_CANDIDATE, "Exact Track Gate 拒绝低置信候选", false);

                    if (provider.aggregate()) {
                        AudioUrlProbe.Result probe = AudioUrlProbe.check(candidate.url);
                        if (probe.checked && !probe.playable) {
                            ResolverFailure failure = ResolverFailure.http(probe.statusCode, "HTTP " + probe.statusCode + " · " + probe.reason);
                            if ((probe.statusCode >= 500 && probe.statusCode <= 599) || probe.statusCode == 401 || probe.statusCode == 403)
                                failure = new ResolverFailure(ResolverFailure.Kind.TRACK_UNAVAILABLE, failure.getMessage(), false);
                            trace.add("candidate-reject", provider.label() + " × " + Song.providerLabel(variant.source) + " / " + failure.getMessage());
                            health.routeFailure(provider.id(), variant.source, quality, failure.getMessage(), 0L);
                            if (probe.statusCode == 429) applyFailureScope(provider, variant.source, quality, failure);
                            if (!failure.lowerQualityMayHelp) crossQualitySkips.add(pp);
                            last = failure;
                            if (tryIndex == 0 && (probe.statusCode == 404 || probe.statusCode == 410)) { provider.recycle(); continue; }
                            break;
                        }
                        if (probe.checked) trace.add("candidate-probe", provider.label() + " × " + Song.providerLabel(variant.source) + " / HTTP " + probe.statusCode + " · 通过");
                    }

                    long latency = Math.max(1L, System.currentTimeMillis() - start);
                    health.resolverCandidate(provider.id(), variant.source, quality, latency);
                    trace.add("audio-candidate", provider.label() + " × " + Song.providerLabel(variant.source)
                            + " / confidence " + Math.round(candidate.matchConfidence * 100d) + "% / "
                            + candidate.protocol + " / " + latency + "ms · 等待 Media3 READY");
                    return candidate;
                } catch (Exception e) {
                    ResolverFailure failure = e instanceof ResolverFailure ? (ResolverFailure) e : ResolverFailure.classify(safe(e));
                    last = failure; lowerQualityMayHelp |= failure.lowerQualityMayHelp;
                    String friendly = friendlyReason(failure.getMessage());
                    if ("huibq".equals(provider.id())) {
                        int http = ResolverHttpDiagnostics.huibqHttpCodeSince(start);
                        if (http >= 500 && http <= 599) {
                            failure = ResolverFailure.http(http, "HTTP " + http + " · Huibq 服务暂不可用，短休后再探");
                            last = failure; friendly = failure.getMessage();
                        }
                    }
                    health.routeFailure(provider.id(), variant.source, quality, friendly, 0L);
                    applyFailureScope(provider, variant.source, quality, failure);
                    if (failure.kind == ResolverFailure.Kind.PROVIDER_INIT_FAILED
                            || failure.kind == ResolverFailure.Kind.PROVIDER_UNAVAILABLE
                            || failure.kind == ResolverFailure.Kind.RATE_LIMITED) deadBackends.add(provider.backendGroup());
                    if (!failure.lowerQualityMayHelp) crossQualitySkips.add(pp);
                    trace.add("resolver-fail", provider.label() + " × " + Song.providerLabel(variant.source) + " / " + friendly);
                    break;
                }
            }
        }

        String message = attempted == 0 ? "当前矩阵没有 Provider × Catalog 声明支持 " + quality : "当前矩阵暂时没有可靠音频候选";
        throw new ResolverFailure(last == null ? ResolverFailure.Kind.TRACK_UNAVAILABLE : last.kind, message, lowerQualityMayHelp);
    }

    public void markPlaybackSuccess(AudioCandidate candidate, Song song, long totalLatencyMs) {
        if (candidate == null || candidate.variant == null || song == null || candidate.providerId.isEmpty() || "cache".equals(candidate.providerId)) return;
        health.playbackSuccess(candidate.providerId, candidate.variant.source, candidate.quality, Math.max(1L, totalLatencyMs));
        trackRoutes.markReady(song.key(), candidate.variant.source, candidate.quality, candidate.providerId, candidate.providerLabel);
        recentReady.put(candidate.providerId + "|" + candidate.variant.source, System.currentTimeMillis());
    }

    public void markPlaybackFailure(AudioCandidate candidate, Song song, String reason) {
        if (candidate == null || candidate.variant == null || song == null || candidate.providerId.isEmpty() || "cache".equals(candidate.providerId)) return;
        String lower = reason == null ? "" : reason.toLowerCase(java.util.Locale.ROOT);
        health.routeFailure(candidate.providerId, candidate.variant.source, candidate.quality, reason, 0L);
        trackRoutes.markFreshFailure(song.key(), candidate.variant.source, candidate.quality, candidate.providerId);
        if (lower.contains("429") || lower.contains("too many") || lower.contains("请求过于频繁") || lower.contains("rate"))
            health.globalFailure(candidate.providerId, reason, RATE_COOLDOWN);
        else if (lower.contains("block ip") || lower.contains("ip 被限制") || lower.contains("当前网络被该音源限制"))
            health.globalFailure(candidate.providerId, reason, BLOCK_COOLDOWN);
    }

    public String preferredRoute(Song song, String quality) { return song == null ? "" : trackRoutes.preferredRoute(song.key(), quality); }

    public void resetCommunityScripts() {
        for (AudioProvider p : providers) try { p.resetPinnedSource(); } catch (Exception ignored) { }
        recentReady.clear();
    }

    /** V66 soft reset: neutralize route learning without touching READY URL cache or safety cooldowns. */
    public void resetRouteLearning() {
        health.resetLearning();
        trackRoutes.resetLearning();
        recentReady.clear();
    }

    public String statusText() {
        StringBuilder b = new StringBuilder();
        b.append("Lunaxy Route Matrix V4\n")
                .append("横轴 Catalog：QQ(tx) / 酷我(kw) / 酷狗(kg) / 网易(wy)；纵轴 Provider。成功只有 Exact Variant + Media3 READY 才加分，真实失败只做近期降权。\n")
                .append("学习对象是‘歌曲 × Catalog × Provider × 音质’；一个平台失败不会污染同 Provider 的其他平台，旧证据会自动衰减。\n\n");
        for (AudioProvider p : providers) {
            int ok = health.totalOk(p.id()), fail = health.totalFail(p.id());
            b.append(ok > 0 ? "● " : "○ ").append(p.label()).append(" · ").append(p.type()).append(" · backend=").append(p.backendGroup()).append('\n');
            for (String platform : MATRIX_PLATFORMS) {
                int po = health.platformOk(p.id(), platform), pf = health.platformFail(p.id(), platform);
                b.append("   ").append(String.format(java.util.Locale.ROOT, "%-5s", Song.providerLabel(platform) + "(" + platform + ")"))
                        .append(" ").append(po > 0 ? "READY " + po : "未验证");
                if (pf > 0) b.append(" · fail ").append(pf);
                float learn = health.platformLearningScore(p.id(), platform);
                if (Math.abs(learn) >= 0.05f) b.append(" · 近期 ").append(learn > 0 ? "+" : "").append(String.format(java.util.Locale.ROOT, "%.1f", learn));
                b.append('\n');
            }
            String caps = p.capabilityHint(); if (caps != null && !caps.isEmpty()) b.append("   声明：").append(caps).append('\n');
            String last = health.lastReason(p.id()); if (last != null && !last.isEmpty()) b.append("   最近：").append(last).append('\n');
            if (ok == 0 && fail == 0) b.append("   尚无实播样本\n");
        }
        b.append("\nLearning V4：路线偏好按近期真实结果学习，约 ").append(SourceHealthStore.learningHalfLifeDays())
                .append(" 天衰减一半，长期没有新证据会逐步回到中性；历史 READY/fail 总数仅作诊断，不再永久支配排序。\n")
                .append("READY URL 仍是临时快速路径；失效后播放会重新求解 Matrix。TrackVariantStore 身份知识不参与遗忘。");
        return b.toString();
    }

    public String compactStatus() {
        int healthyPlatforms = 0, readyRoutes = 0;
        for (String platform : MATRIX_PLATFORMS) {
            int sum = 0; for (AudioProvider p : providers) sum += health.platformOk(p.id(), platform);
            if (sum > 0) healthyPlatforms++; readyRoutes += sum;
        }
        if (readyRoutes == 0) return "矩阵 V4 · 4 个平台等待首次 READY";
        return "矩阵 V4 · " + healthyPlatforms + "/4 平台有 READY · " + readyRoutes + " 次实播";
    }

    public void destroy() { for (AudioProvider p : providers) try { p.destroy(); } catch (Exception ignored) { } providers.clear(); recentReady.clear(); }

    private List<RouteCell> rankedCells(Song song, String quality) {
        long now = System.currentTimeMillis();
        List<RouteCell> out = new ArrayList<>();
        for (SourceVariant variant : song.variants()) {
            if (variant == null || variant.source.isEmpty() || variant.sourceId.isEmpty()) continue;
            for (AudioProvider provider : providers) {
                int score = provider.basePriority(variant.source);
                // V66 Learning V4: exact-track affinity is still useful, but it decays instead of
                // acting like a 45-day permanent lock. Provider health uses recent decayed evidence
                // rather than lifetime READY/fail totals.
                score += trackRoutes.globalAffinityBonus(song.key(), quality, variant.source, provider.id());
                score += trackRoutes.platformAffinityBonus(song.key(), variant.source, quality, provider.id());
                Long ready = recentReady.get(provider.id() + "|" + variant.source);
                if (ready != null && now - ready >= 0L && now - ready <= RECENT_READY_WINDOW) score += 3_000;
                score += Math.round(health.learningScore(provider.id(), variant.source, quality) * 900f);
                long latency = health.latency(provider.id(), variant.source, quality); if (latency > 0) score -= (int) Math.min(700, latency / 20L);
                out.add(new RouteCell(provider, variant, score));
            }
        }
        out.sort(Comparator.comparingInt((RouteCell c) -> c.score).reversed());
        return out;
    }

    private void applyFailureScope(AudioProvider provider, String platform, String quality, ResolverFailure failure) {
        if (failure == null) return;
        String reason = friendlyReason(failure.getMessage());
        switch (failure.kind) {
            case RATE_LIMITED: health.globalFailure(provider.id(), reason, RATE_COOLDOWN); break;
            case PROVIDER_INIT_FAILED: health.globalFailure(provider.id(), reason, INIT_COOLDOWN); break;
            case PROVIDER_UNAVAILABLE: health.globalFailure(provider.id(), reason, SERVICE_COOLDOWN); break;
            case AUTH_OR_FORBIDDEN: health.platformFailure(provider.id(), platform, reason, 5L * 60L * 1000L); break;
            case PLATFORM_UNSUPPORTED: health.platformFailure(provider.id(), platform, reason, 10L * 60L * 1000L); break;
            case NETWORK: health.routeCooldown(provider.id(), platform, quality, reason, SOFT_NETWORK_COOLDOWN); break;
            default: break;
        }
    }

    private void add(Context app, ResolverProvider spec) { providers.add(new LxAudioProvider(app, spec)); }
    private static String jsdelivr(String folder) { return "https://fastly.jsdelivr.net/gh/pdone/lx-music-source@main/" + folder + "/latest.js"; }
    private static String variantLabels(Song song) { StringBuilder b = new StringBuilder(); for (SourceVariant v : song.variants()) { if (b.length() > 0) b.append('/'); b.append(v.source); } return b.toString(); }
    private static String friendlyReason(String m) {
        String s = m == null ? "" : m.trim(); String first = s.replace('\r','\n').split("\n",2)[0].trim(); String lower = first.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("block ip") || lower.contains("ip 被限制")) return "当前网络被该 Provider 限制，已进入保护冷却";
        if (lower.contains("too many") || first.contains("请求过于频繁") || lower.contains("429")) return "请求频率受限，已进入保护冷却";
        if (first.contains("初始化")) { String compact = "Provider 初始化失败" + suffix(first); return compact.length() > 110 ? compact.substring(0,110) + "…" : compact; }
        if (first.length() > 130) return first.substring(0,130) + "…"; return first.isEmpty() ? "暂时不可用" : first;
    }
    private static String suffix(String s) { int i=s.indexOf('：'); return i>=0 && i+1<s.length() ? "："+s.substring(i+1) : ""; }
    private static String safe(Exception e) { return e == null || e.getMessage() == null ? "unknown" : e.getMessage(); }
    private static final class RouteCell { final AudioProvider provider; final SourceVariant variant; final int score; RouteCell(AudioProvider p, SourceVariant v, int s) { provider=p; variant=v; score=s; } }
}
