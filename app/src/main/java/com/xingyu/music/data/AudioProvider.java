package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

/**
 * Replaceable audio capability.  Catalog identity and lyrics are intentionally
 * outside this interface.
 */
public interface AudioProvider {
    String id();
    String label();
    String type();
    /** Logical infrastructure group for diagnostics/backoff de-duplication. */
    default String backendGroup() { return id(); }
    int basePriority(String platform);
    boolean aggregate();

    /** Whether this provider advertises the catalog platform at all. */
    default boolean supportsPlatform(SourceVariant variant) throws Exception { return variant != null; }

    /** Whether this exact platform/quality route is advertised. */
    boolean supports(SourceVariant variant, String quality) throws Exception;

    /** Cached/non-blocking capability text for diagnostics; must never trigger network by itself. */
    default String capabilityHint() { return ""; }

    AudioCandidate resolve(SourceVariant variant,
                           Song song,
                           String quality,
                           PlaybackTrace trace) throws Exception;

    /** Clear any pinned remote source snapshot; bundled-only providers may no-op. */
    void resetPinnedSource();

    void recycle();
    void destroy();
}
