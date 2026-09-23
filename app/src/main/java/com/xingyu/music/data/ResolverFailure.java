package com.xingyu.music.data;

import java.util.Locale;

/**
 * Typed resolver failure used by Sunflower V2 to decide scope and whether
 * dropping quality can possibly help.  It deliberately separates a route
 * failure from provider/platform outages so one bad QQ candidate cannot poison
 * a provider's working Netease route.
 */
public final class ResolverFailure extends Exception {
    public enum Kind {
        QUALITY_UNSUPPORTED,
        PLATFORM_UNSUPPORTED,
        AUTH_OR_FORBIDDEN,
        RATE_LIMITED,
        PROVIDER_UNAVAILABLE,
        PROVIDER_INIT_FAILED,
        TRACK_UNAVAILABLE,
        NETWORK,
        INVALID_CANDIDATE,
        UNKNOWN
    }

    public final Kind kind;
    public final boolean lowerQualityMayHelp;

    public ResolverFailure(Kind kind, String message, boolean lowerQualityMayHelp) {
        super(message == null || message.trim().isEmpty() ? "暂时不可用" : message.trim());
        this.kind = kind == null ? Kind.UNKNOWN : kind;
        this.lowerQualityMayHelp = lowerQualityMayHelp;
    }

    public static ResolverFailure classify(String raw) {
        String text = raw == null ? "" : raw.trim();
        String s = text.toLowerCase(Locale.ROOT);
        if (s.startsWith("unsupported ") || s.contains("quality unsupported") || s.contains("音质不支持"))
            return new ResolverFailure(Kind.QUALITY_UNSUPPORTED, text, true);
        if (s.contains("source not match") || s.contains("platform unsupported") || s.contains("不支持 tx") || s.contains("不支持 wy"))
            return new ResolverFailure(Kind.PLATFORM_UNSUPPORTED, text, false);
        if (s.contains("http 401") || s.contains("http 403") || s.contains(" 401") || s.contains(" 403") || s.contains("forbidden") || s.contains("unauthorized"))
            return new ResolverFailure(Kind.AUTH_OR_FORBIDDEN, text, false);
        if (s.contains("429") || s.contains("too many") || s.contains("rate limit") || s.contains("请求过于频繁") || s.contains("频繁"))
            return new ResolverFailure(Kind.RATE_LIMITED, text, false);
        if (s.contains("初始化") || s.contains("init failed") || s.contains("加载音源信息失败") || s.contains("脚本错误"))
            return new ResolverFailure(Kind.PROVIDER_INIT_FAILED, text, false);
        if (s.contains("http 503") || s.contains("http 502") || s.contains("http 504") || s.contains("service unavailable"))
            return new ResolverFailure(Kind.PROVIDER_UNAVAILABLE, text, false);
        if (s.contains("404") || s.contains("410") || s.contains("get music url failed") || s.contains("not found") || s.contains("无可用播放") || s.contains("解析失败"))
            return new ResolverFailure(Kind.TRACK_UNAVAILABLE, text, false);
        if (s.contains("timeout") || s.contains("timed out") || s.contains("unknownhost") || s.contains("unable to resolve host") || s.contains("network"))
            return new ResolverFailure(Kind.NETWORK, text, false);
        if (s.contains("无效音频") || s.contains("invalid") || s.contains("candidate"))
            return new ResolverFailure(Kind.INVALID_CANDIDATE, text, false);
        return new ResolverFailure(Kind.UNKNOWN, text, false);
    }

    public static ResolverFailure http(int statusCode, String message) {
        String text = (message == null || message.isEmpty()) ? "HTTP " + statusCode : message;
        if (statusCode == 401 || statusCode == 403)
            return new ResolverFailure(Kind.AUTH_OR_FORBIDDEN, text, false);
        if (statusCode == 429)
            return new ResolverFailure(Kind.RATE_LIMITED, text, false);
        if (statusCode == 404 || statusCode == 410)
            return new ResolverFailure(Kind.TRACK_UNAVAILABLE, text, false);
        if (statusCode >= 500)
            return new ResolverFailure(Kind.PROVIDER_UNAVAILABLE, text, false);
        return new ResolverFailure(Kind.INVALID_CANDIDATE, text, false);
    }
}
