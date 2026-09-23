package com.xingyu.music.data;

import android.content.Context;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Legacy V47 fixed Huibq/QDY manager retained only as a rollback reference.
 * Sunflower playback uses AudioProviderHub via SourceCoordinator.
 *
 * V14 stable primary-source router.
 *
 * The old builds intentionally tried many community resolvers and learned over time.
 * That explains why a fresh install often started badly and later "became good":
 * repeated failures pushed dead routes down while Media3 READY successes promoted
 * Huibq/QDY. V14 no longer requires that training phase.
 *
 * Production path is deliberately small:
 *   tx (QQ): Huibq first, QDY fallback
 *   wy (Netease): QDY first, Huibq fallback
 * Other historical providers are not probed during normal playback.
 */
@Deprecated
public final class MultiSourceManager {
    public static final class Result {
        public final String url, quality, providerId, providerLabel, routeKey;
        public final long resolverLatencyMs;
        Result(String url, String quality, ResolverProvider p, String platform, long latency) {
            this.url = url;
            this.quality = quality;
            this.providerId = p.id;
            this.providerLabel = p.label;
            this.routeKey = SourceHealthStore.routeKey(p.id, platform, quality);
            this.resolverLatencyMs = latency;
        }
    }

    private static final long RATE_COOLDOWN = 20L * 60L * 1000L;
    private static final long BLOCK_COOLDOWN = 60L * 60L * 1000L;
    private static final long SOFT_NETWORK_COOLDOWN = 15L * 1000L;
    private static final long INIT_COOLDOWN = 60L * 1000L;
    // V41 short circuit breakers. They do not affect long-term provider order.
    private static final long HUIBQ_SERVICE_COOLDOWN = 60L * 1000L;
    private static final long QDY_QQ_ACCESS_COOLDOWN = 30L * 1000L;
    private static final long QDY_QQ_SERVER_COOLDOWN = 30L * 1000L;

    private final android.content.Context context;
    private final SourceHealthStore health;
    private final Map<String, LxSourceRuntime> runtimes = new LinkedHashMap<>();
    private final List<ResolverProvider> providers = new ArrayList<>();

    public MultiSourceManager(Context context) {
        Context app = context.getApplicationContext();
        this.context = app;
        health = new SourceHealthStore(app);

        // Only the two routes that the user's real device repeatedly proved with Media3 READY.
        providers.add(new ResolverProvider(
                "huibq", "Huibq",
                "", "source/huibq-latest.js",
                100, false));
        providers.add(new ResolverProvider(
                "qdy", "全豆要聚合",
                "https://fastly.jsdelivr.net/gh/pdone/lx-music-source@main/qdy/latest.js", "",
                96, true));

        // Huibq is bundled and cheap to initialize. QDY is lazy so startup never triggers a remote script fetch.
        runtime(providers.get(0));
    }

    public Result resolve(SourceVariant variant, Song song, String quality,
                          Set<String> excludedRoutes, PlaybackTrace trace) throws Exception {
        if (variant == null) throw new Exception("歌曲平台信息为空");
        Set<String> excluded = excludedRoutes == null ? Collections.emptySet() : excludedRoutes;
        List<ResolverProvider> ranked = health.rank(providers, variant.source, quality);
        Exception last = null;

        for (ResolverProvider provider : ranked) {
            String route = SourceHealthStore.routeKey(provider.id, variant.source, quality);
            if (excluded.contains(route)) {
                trace.add("route-skip", provider.label + " / " + variant.source + "/" + quality + " · 本轮已失败");
                continue;
            }
            if (health.cooling(provider.id, variant.source, quality)) {
                long seconds = Math.max(1L, health.cooldownRemaining(provider.id, variant.source, quality) / 1000L);
                trace.add("resolver-skip", provider.label + " 保护冷却中 · " + variant.source + "/" + quality + " · " + seconds + "s");
                continue;
            }

            int tries = "qdy".equals(provider.id) ? 2 : 1;
            for (int tryIndex = 0; tryIndex < tries; tryIndex++) {
                long start = System.currentTimeMillis();
                try {
                    LxSourceRuntime rt = runtime(provider);
                    if (!rt.supportsRoute(variant.source, quality)) {
                        trace.add("resolver-skip", provider.label + " 不支持 " + variant.source + "/" + quality);
                        break;
                    }

                    trace.add("resolver", provider.label + " → " + variant.source + "/" + quality
                            + (tryIndex > 0 ? " · 清缓存重试" : ""));
                    LxSourceRuntime.RuntimeResult r = rt.musicUrl(variant, song, quality);
                    long latency = Math.max(1, System.currentTimeMillis() - start);

                    // QDY is itself an aggregator. It can return a syntactically valid URL whose
                    // internal endpoint is already 404. Probe only QDY candidates; Huibq stays one-request-only.
                    if ("qdy".equals(provider.id)) {
                        AudioUrlProbe.Result probe = AudioUrlProbe.check(r.url);
                        if (probe.checked && !probe.playable) {
                            trace.add("candidate-reject", provider.label + " / " + probe.reason
                                    + " · 音频 URL 在进入播放器前已失效");
                            trace.add("failure-class", candidateFailureClass(provider.id, variant.source, probe.statusCode));
                            String candidateReason = "候选音频地址失效：" + probe.reason;
                            health.routeFailure(provider.id, variant.source, quality, candidateReason, 0L);
                            // QDY's QQ candidate may be broadly forbidden even while its Netease route is healthy.
                            // Rest only qdy|tx, never QDY Netease. 404/410 stays song-local.
                            if ("tx".equals(variant.source) && (probe.statusCode == 401 || probe.statusCode == 403 || probe.statusCode == 409)) {
                                health.platformFailure(provider.id, variant.source,
                                        "QQ 候选地址 HTTP " + probe.statusCode + "，短暂暂停 QDY-QQ 备用",
                                        QDY_QQ_ACCESS_COOLDOWN);
                            } else if ("tx".equals(variant.source) && probe.statusCode >= 500) {
                                health.platformFailure(provider.id, variant.source,
                                        "QQ 候选地址 HTTP " + probe.statusCode + "，短暂暂停 QDY-QQ 备用",
                                        QDY_QQ_SERVER_COOLDOWN);
                            }
                            last = new Exception(provider.label + " 返回失效音频地址：" + probe.reason);
                            if (tryIndex == 0 && (probe.statusCode == 404 || probe.statusCode == 410)) {
                                recycleRuntime(provider.id);
                                continue;
                            }
                            break;
                        }
                        if (probe.checked) trace.add("candidate-probe", provider.label + " / HTTP " + probe.statusCode + " · 通过");
                    }

                    health.resolverCandidate(provider.id, variant.source, quality, latency);
                    trace.add("resolver-url", provider.label + " / " + latency + "ms · 待播放器验证");
                    return new Result(r.url, r.quality, provider, variant.source, latency);
                } catch (Exception e) {
                    last = e;
                    String raw = safe(e);
                    if (raw.startsWith("UNSUPPORTED ")) {
                        trace.add("resolver-skip", provider.label + " 不支持 " + raw.substring("UNSUPPORTED ".length()));
                        break;
                    }
                    String friendly = friendlyReason(raw);
                    long cooldown = classifyCooldown(raw);
                    boolean global = isGlobalFailure(raw);

                    // Huibq's script collapses non-JSON responses into "unknow error". V39 diagnostics
                    // already observed the completed HTTP response, so V40 can recognize a real 5xx
                    // without issuing any extra request or changing the resolver payload.
                    if ("huibq".equals(provider.id)) {
                        int http = ResolverHttpDiagnostics.huibqHttpCodeSince(start);
                        if (http >= 500 && http <= 599) {
                            friendly = "HTTP " + http + " · Huibq 服务暂不可用，60 秒后再探";
                            cooldown = HUIBQ_SERVICE_COOLDOWN;
                            global = true;
                            trace.add("failure-class", "SOURCE_UNAVAILABLE / Huibq HTTP " + http);
                        }
                    }

                    // Failure counters remain visible statistics, but no longer participate in ranking.
                    health.routeFailure(provider.id, variant.source, quality, friendly, 0L);
                    if (global && cooldown > 0) health.globalFailure(provider.id, friendly, cooldown);
                    else if (cooldown > 0) health.routeCooldown(provider.id, variant.source, quality, friendly, cooldown);
                    trace.add("resolver-fail", provider.label + " / " + friendly);
                    break;
                }
            }
        }
        throw last == null ? new Exception("当前两个主音源都不可用") : new Exception(friendlyReason(safe(last)));
    }

    public void markPlaybackSuccess(String providerId, String platform, String quality, long latencyMs) {
        if (providerId == null || providerId.isEmpty() || "cache".equals(providerId)) return;
        health.playbackSuccess(providerId, platform, quality, Math.max(1, latencyMs));
    }

    public void markPlaybackFailure(String providerId, String platform, String quality, String reason, long cooldownMs) {
        if (providerId == null || providerId.isEmpty() || "cache".equals(providerId)) return;
        String lower = reason == null ? "" : reason.toLowerCase(java.util.Locale.ROOT);

        // Media CDN 401/403/404/410/5xx describes this candidate URL, not the whole resolver.
        // Excluded-routes already prevents looping during the current tap, so do not poison
        // the next song. Only explicit rate/IP errors are allowed to cross-song cool down.
        long effective = 0L;
        if (lower.contains("429") || lower.contains("too many") || lower.contains("请求过于频繁") || lower.contains("rate")) {
            effective = RATE_COOLDOWN;
        } else if (lower.contains("block ip") || lower.contains("ip 被限制") || lower.contains("当前网络被该音源限制")) {
            effective = BLOCK_COOLDOWN;
        }
        // Record the failure as statistics only; fixed routing never learns a permanent demotion.
        health.routeFailure(providerId, platform, quality, reason, 0L);
        if (effective > 0) {
            health.globalFailure(providerId, reason, effective);
            return;
        }

        // Keep QDY's Netease route untouched when only its QQ candidate path is forbidden/unhealthy.
        if ("qdy".equals(providerId) && "tx".equals(platform)) {
            if (lower.contains("http 401") || lower.contains("http 403") || lower.contains("http 409")) {
                health.platformFailure(providerId, platform, reason, QDY_QQ_ACCESS_COOLDOWN);
            } else if (containsHttp5xx(lower) || lower.contains("timeout") || lower.contains("timed out")) {
                health.platformFailure(providerId, platform, reason, QDY_QQ_SERVER_COOLDOWN);
            }
        }
    }

    private synchronized LxSourceRuntime runtime(ResolverProvider p) {
        LxSourceRuntime r = runtimes.get(p.id);
        if (r == null) {
            r = new LxSourceRuntime(context, p);
            runtimes.put(p.id, r);
        }
        return r;
    }

    private synchronized void recycleRuntime(String providerId) {
        LxSourceRuntime old = runtimes.remove(providerId);
        if (old != null) old.destroy();
    }

    public String statusText() {
        StringBuilder b = new StringBuilder();
        b.append("固定主路由：QQ 永远优先 Huibq，QDY 仅作备用；网易云永远优先全豆要，Huibq 仅作备用。\n")
                .append("历史 READY / 失败次数只做统计，不再改变长期顺序；临时 503 / 403 / 429 只触发短期保护。\n\n");
        for (ResolverProvider p : providers) {
            int ok = health.totalOk(p.id);
            int fail = health.totalFail(p.id);
            String route = health.lastRoute(p.id);
            b.append(ok > 0 ? "● " : "○ ").append(p.label);
            if (ok > 0) {
                b.append(" · 实播成功 ").append(ok).append(" 次");
                if (!route.isEmpty()) b.append(" · ").append(routeLabel(route));
            } else b.append(" · 等待第一次 READY");
            if (fail > 0) b.append(" · 失败 ").append(fail);
            String reason = health.lastReason(p.id);
            if (!reason.isEmpty() && ok == 0) b.append("\n   ").append(reason);
            b.append('\n');
        }
        b.append("\n只有 Media3 READY 才计成功并缓存 URL。Huibq HTTP 5xx 会短休 60 秒后自动再探；明确 block ip / 429 仍使用长保护冷却。\n")
                .append("QDY 的 QQ 候选若 401/403/409，仅短休 30 秒且只影响 QDY-QQ 备用；404/410 仍只影响当前候选，绝不牵连 QDY-网易。\n")
                .append("QDY 脚本首次获取后固定保存；Huibq 永远使用 APK 内置快照。");
        return b.toString();
    }

    public String compactStatus() {
        int hOk = health.totalOk("huibq");
        int qOk = health.totalOk("qdy");
        if (hOk == 0 && qOk == 0) return "固定主路由 · QQ→Huibq / 网易→全豆要";
        return "固定主路由 · Huibq " + hOk + " READY / 全豆要 " + qOk + " READY";
    }

    public void destroy() {
        synchronized (this) {
            for (LxSourceRuntime r : runtimes.values()) r.destroy();
            runtimes.clear();
        }
    }

    private static String routeLabel(String route) {
        if (route == null) return "";
        String[] p = route.split("/", 2);
        if (p.length != 2) return route;
        return Song.providerLabel(p[0]) + "/" + p[1];
    }

    private static boolean isGlobalFailure(String m) {
        String s = m == null ? "" : m.toLowerCase(java.util.Locale.ROOT);
        return s.contains("block ip") || s.contains("too many") || s.contains("频繁")
                || s.contains("429") || s.contains("rate");
    }

    private static long classifyCooldown(String m) {
        String s = m == null ? "" : m.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("block ip") || s.contains("ip 被限制")) return BLOCK_COOLDOWN;
        if (s.contains("too many") || s.contains("频繁") || s.contains("429") || s.contains("rate")) return RATE_COOLDOWN;
        if (s.contains("初始化") || s.contains("脚本")) return INIT_COOLDOWN;
        if (s.contains("unable to resolve host") || s.contains("unknownhost") || s.contains("timeout") || s.contains("timed out")) return SOFT_NETWORK_COOLDOWN;
        // Normal single-song resolver errors are not evidence that every future song is bad.
        return 0L;
    }

    private static boolean containsHttp5xx(String lower) {
        if (lower == null) return false;
        return lower.contains("http 500") || lower.contains("http 501") || lower.contains("http 502")
                || lower.contains("http 503") || lower.contains("http 504") || lower.contains("http 505")
                || lower.contains("http 506") || lower.contains("http 507") || lower.contains("http 508")
                || lower.contains("http 509") || lower.contains("http 510") || lower.contains("http 511");
    }

    private static String candidateFailureClass(String providerId, String platform, int statusCode) {
        if (statusCode == 401 || statusCode == 403 || statusCode == 404 || statusCode == 409 || statusCode == 410) {
            return "CANDIDATE_INVALID / " + providerId + "/" + platform + " HTTP " + statusCode;
        }
        if (statusCode >= 500 && statusCode <= 599) {
            return "SOURCE_UNAVAILABLE / " + providerId + "/" + platform + " HTTP " + statusCode;
        }
        if (statusCode > 0) return "CANDIDATE_REJECT / " + providerId + "/" + platform + " HTTP " + statusCode;
        return "CANDIDATE_REJECT / " + providerId + "/" + platform;
    }

    private static String friendlyReason(String m) {
        String s = m == null ? "" : m;
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("block ip") || lower.contains("ip 被限制")) return "当前网络被该音源限制，已进入保护冷却";
        if (lower.contains("too many") || s.contains("请求过于频繁") || lower.contains("429")) return "请求频率受限，已进入保护冷却";
        if (s.contains("初始化")) return "音源初始化失败" + suffix(s);
        if (s.length() > 120) return s.substring(0, 120) + "…";
        return s.isEmpty() ? "暂时不可用" : s;
    }

    private static String suffix(String s) {
        int i = s.indexOf('：');
        if (i >= 0 && i + 1 < s.length()) return "：" + s.substring(i + 1);
        return "";
    }

    private static String safe(Exception e) {
        return e == null || e.getMessage() == null ? "unknown" : e.getMessage();
    }
}
