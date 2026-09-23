package com.xingyu.music.data;

/** A trusted LX custom-source script provider. */
public final class ResolverProvider {
    public final String id;
    public final String label;
    public final String scriptUrl;
    public final String bundledAsset;
    public final int basePriority;
    public final boolean aggregateFallback;

    public ResolverProvider(String id, String label, String scriptUrl, String bundledAsset,
                            int basePriority, boolean aggregateFallback) {
        this.id = id;
        this.label = label;
        this.scriptUrl = scriptUrl == null ? "" : scriptUrl;
        this.bundledAsset = bundledAsset == null ? "" : bundledAsset;
        this.basePriority = basePriority;
        this.aggregateFallback = aggregateFallback;
    }
}
