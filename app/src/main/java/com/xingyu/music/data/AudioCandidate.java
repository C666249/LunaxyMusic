package com.xingyu.music.data;

import com.xingyu.music.model.SourceVariant;

/**
 * Sunflower playback-domain object.
 *
 * A resolver returning a URL is not playback success.  It is only one candidate
 * for the canonical track.  PlaybackService remains the final authority and
 * only Media3 STATE_READY can promote this candidate into the READY caches.
 */
public final class AudioCandidate {
    public final String url;
    public final String quality;
    public final String providerId;
    public final String providerLabel;
    public final String providerType;
    public final String backendGroup;
    public final String routeKey;
    public final SourceVariant variant;
    public final long resolverLatencyMs;
    public final double matchConfidence;
    public final String protocol;
    public final String codec;
    public final long expiresAtMs;

    public AudioCandidate(String url,
                          String quality,
                          String providerId,
                          String providerLabel,
                          String providerType,
                          String backendGroup,
                          String routeKey,
                          SourceVariant variant,
                          long resolverLatencyMs,
                          double matchConfidence,
                          String protocol,
                          String codec,
                          long expiresAtMs) {
        this.url = safe(url);
        this.quality = safe(quality);
        this.providerId = safe(providerId);
        this.providerLabel = safe(providerLabel);
        this.providerType = safe(providerType);
        this.backendGroup = safe(backendGroup);
        this.routeKey = safe(routeKey);
        this.variant = variant;
        this.resolverLatencyMs = Math.max(1L, resolverLatencyMs);
        this.matchConfidence = Math.max(0d, Math.min(1d, matchConfidence));
        this.protocol = safe(protocol);
        this.codec = safe(codec);
        this.expiresAtMs = Math.max(0L, expiresAtMs);
    }

    public boolean valid() { return url.startsWith("http://") || url.startsWith("https://"); }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
