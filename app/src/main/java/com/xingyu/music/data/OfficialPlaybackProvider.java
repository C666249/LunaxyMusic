package com.xingyu.music.data;

import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

/**
 * Reserved seam for first-party authorized playback SDKs (for example an
 * officially provisioned QQ/QPlay integration).  V2 intentionally ships no
 * credentials, private endpoints, or entitlement bypass.  An implementation
 * can be added later without changing SourceCoordinator/Media3.
 */
public interface OfficialPlaybackProvider extends AudioProvider {
    boolean isConfigured();
    boolean hasPlaybackEntitlement(SourceVariant variant, Song song) throws Exception;
}
