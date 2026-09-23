package com.xingyu.music.download;

import android.content.Context;

import com.xingyu.music.data.ExactTrackMatcher;
import com.xingyu.music.data.PlaybackTrace;
import com.xingyu.music.data.PlaybackUrlStore;
import com.xingyu.music.data.TrackRouteStore;
import com.xingyu.music.data.TrackVariantStore;
import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Collections;

/**
 * Hard isolation resolver for downloads.
 * CACHE-ONLY by design: it never constructs an LX runtime, never invokes any Provider musicUrl(),
 * never writes playback Health/Route/URL state and never invalidates a playback cache entry.
 * The V3 matrix still helps because any exact KW/KG/WY/MG route that reached Media3 READY may
 * contribute a cached URL; download simply reuses the already-proven route.
 * V4 deliberately refuses TX-only download routes: old V34 device evidence showed that a second
 * QQ/TX media GET could disturb the upstream even when playback state itself was isolated.
 */
public final class DownloadResolver {
    public static final class Route {
        public final Song song; public final SourceVariant variant; public final String quality;
        public final String url; public final String providerId; public final String providerLabel; public final String protocol;
        Route(Song song, SourceVariant variant, String quality, String url, String providerId, String providerLabel) {
            this.song=song;this.variant=variant;this.quality=quality;this.url=url;this.providerId=providerId;this.providerLabel=providerLabel;this.protocol="PROGRESSIVE";
        }
    }

    private final TrackVariantStore variants;
    private final TrackRouteStore playbackRoutes;
    private final PlaybackUrlStore playbackUrls;
    private final DownloadRouteStore downloadRoutes;

    public DownloadResolver(Context context) {
        Context app=context.getApplicationContext();variants=new TrackVariantStore(app);playbackRoutes=new TrackRouteStore(app);
        playbackUrls=new PlaybackUrlStore(app);downloadRoutes=new DownloadRouteStore(app);
    }

    public Route resolve(Song input,PlaybackTrace trace)throws Exception{ return resolve(input,trace,Collections.emptySet()); }

    /** V65: exact stale READY URLs are ignored without mutating PlaybackUrlStore itself. */
    public Route resolve(Song input,PlaybackTrace trace,Set<String> staleUrlFingerprints)throws Exception{
        Song song=ExactTrackMatcher.sanitizeVariants(variants.enrich(ExactTrackMatcher.sanitizeVariants(input)));
        if(song.variants().isEmpty())throw new Exception("歌曲没有可用的 Exact Catalog 身份");
        for(String quality:new String[]{"320k","128k"}){
            Route r=cached(song,quality,trace,staleUrlFingerprints);if(r!=null)return r;
        }
        boolean hasTxCache = hasCachedTx(song);
        if (hasTxCache) throw new Exception("仅检测到 QQ/TX READY 缓存 · 为保护在线播放链，V4 暂不直接下载 TX；请先让矩阵从酷我/酷狗/网易成功播放一次");
        throw new Exception("还没有可复用的非 TX Media3 READY 下载线路 · 请先成功播放一次再下载");
    }

    private Route cached(Song song,String quality,PlaybackTrace trace,Set<String> staleUrlFingerprints){
        String downloadPreferred=downloadRoutes.preferred(song.key(),quality);
        String playbackPreferred=playbackRoutes.preferredRoute(song.key(),quality);
        List<SourceVariant> ordered=new ArrayList<>(song.variants());
        ordered.sort((a,b)->Integer.compare(downloadPlatformRank(a.source),downloadPlatformRank(b.source)));
        if(!downloadPreferred.isEmpty()){String p=downloadPreferred.split("\\|",2)[0];ordered.sort((a,b)->Boolean.compare(p.equals(b.source),p.equals(a.source)));}
        else if(!playbackPreferred.isEmpty()){String p=playbackPreferred.split("\\|",2)[0];if(!"tx".equals(p))ordered.sort((a,b)->Boolean.compare(p.equals(b.source),p.equals(a.source)));}
        for(SourceVariant v:ordered){
            if ("tx".equals(v.source)) continue; // V4 hard safety rail: never issue a second TX media GET.
            if(!v.supports(quality))continue;PlaybackUrlStore.Entry e=playbackUrls.get(v.source,v.sourceId,quality);if(e==null)continue;
            if(staleUrlFingerprints!=null&&staleUrlFingerprints.contains(urlFingerprint(e.url)))continue;
            String route=v.source+"|"+e.providerId;
            if(!downloadPreferred.isEmpty()&&!downloadPreferred.equals(route))continue;
            trace.add("download-route","CACHE-ONLY · "+e.providerLabel+" × "+Song.providerLabel(v.source)+"/"+quality);
            return new Route(song,v,quality,e.url,e.providerId,e.providerLabel);
        }
        // Preferred cache may be expired; any other exact Media3 READY URL is still eligible.
        for(SourceVariant v:ordered){
            if ("tx".equals(v.source)) continue;
            if(!v.supports(quality))continue;PlaybackUrlStore.Entry e=playbackUrls.get(v.source,v.sourceId,quality);if(e==null)continue;
            if(staleUrlFingerprints!=null&&staleUrlFingerprints.contains(urlFingerprint(e.url)))continue;
            trace.add("download-route","CACHE-ONLY fallback · "+e.providerLabel+" × "+Song.providerLabel(v.source)+"/"+quality);
            return new Route(song,v,quality,e.url,e.providerId,e.providerLabel);
        }
        return null;
    }

    private boolean hasCachedTx(Song song) {
        for (SourceVariant v : song.variants()) {
            if (!"tx".equals(v.source)) continue;
            for (String q : new String[]{"320k", "128k"}) if (v.supports(q) && playbackUrls.get(v.source, v.sourceId, q) != null) return true;
        }
        return false;
    }

    private static int downloadPlatformRank(String source) {
        String s = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if ("kw".equals(s)) return 0;
        if ("kg".equals(s)) return 1;
        if ("wy".equals(s)) return 2;
        if ("mg".equals(s)) return 3;
        if ("tx".equals(s)) return 99;
        return 20;
    }

    public static String urlFingerprint(String url){return DownloadPlanStore.hash("ready-url:"+(url==null?"":url));}

    public void markSuccess(Route r){if(r!=null)downloadRoutes.markSuccess(r.song.key(),r.quality,r.variant.source,r.providerId);}
    public void destroy(){ }
}
