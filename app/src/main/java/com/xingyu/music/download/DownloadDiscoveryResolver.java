package com.xingyu.music.download;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import com.xingyu.music.data.AudioCandidate;
import com.xingyu.music.data.AudioProvider;
import com.xingyu.music.data.ExactTrackMatcher;
import com.xingyu.music.data.KugouMusicApi;
import com.xingyu.music.data.KuwoMusicApi;
import com.xingyu.music.data.LxAudioProvider;
import com.xingyu.music.data.NeteaseApi;
import com.xingyu.music.data.PlaybackTrace;
import com.xingyu.music.data.ResolverFailure;
import com.xingyu.music.data.ResolverProvider;
import com.xingyu.music.data.SourceHealthStore;
import com.xingyu.music.data.TrackRouteStore;
import com.xingyu.music.data.TrackVariantStore;
import com.xingyu.music.model.Song;
import com.xingyu.music.model.SourceVariant;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lunaxy V66 cold-download discovery plane — Playback Matrix Full-Lite.
 *
 * This is intentionally NOT SourceCoordinator and NOT AudioProviderHub. It owns its own provider
 * runtimes/cooldowns and never WRITES PlaybackUrlStore / SourceHealthStore / TrackRouteStore or
 * marks Media3 READY. V66 may READ the playback brain's decayed route intelligence so download and
 * playback share navigation knowledge without letting background downloads teach/pollute playback.
 *
 * Safety rails:
 *  - TX is excluded from cold downloads (the V34/V60 protection stays intact).
 *  - only one caller exists (OfflineDownloadService's single worker);
 *  - small per-song provider budget; independent cooldown on 429/5xx/init failures;
 *  - metadata enrichment is exact-gated and stays in-memory for this download attempt.
 */
public final class DownloadDiscoveryResolver {
    private static final String PREFS = "lunaxy_download_discovery_v1";
    private static final long RATE_COOLDOWN = 30L * 60L * 1000L;
    private static final long SERVICE_COOLDOWN = 90L * 1000L;
    private static final long INIT_COOLDOWN = 3L * 60L * 1000L;
    private static final long SOFT_COOLDOWN = 20L * 1000L;
    private static final int MAX_PROVIDER_ATTEMPTS_PER_ROUND = 14;
    private static final long DISCOVERY_ROUND_BUDGET_MS = 20_000L;

    private final Context app;
    private final Context providerSandbox;
    private final SharedPreferences prefs;
    private final TrackVariantStore storedVariants;
    private final DownloadRouteStore downloadRoutes;
    private final SourceHealthStore playbackHealth;
    private final TrackRouteStore playbackRoutes;
    private final NeteaseApi netease = new NeteaseApi();
    private final KuwoMusicApi kuwo = new KuwoMusicApi();
    private final KugouMusicApi kugou = new KugouMusicApi();
    private final List<ResolverProvider> providerSpecs = new ArrayList<>();
    private final Map<String,AudioProvider> providers = new LinkedHashMap<>();

    public DownloadDiscoveryResolver(Context context) {
        app = context.getApplicationContext();
        providerSandbox = new DownloadSandboxContext(app);
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        storedVariants = new TrackVariantStore(app);
        downloadRoutes = new DownloadRouteStore(app);
        playbackHealth = new SourceHealthStore(app);
        playbackRoutes = new TrackRouteStore(app);
        // Specs are cheap metadata only. Runtime/WebView creation is LAZY and happens only after
        // OfflineDownloadService grants a cold-discovery slot. This prevents merely opening the
        // download service from warming several resolver runtimes next to active playback.
        addSpec(new ResolverProvider("qdy", "全豆要 QDY", jsdelivr("qdy"), "", 108, true));
        addSpec(new ResolverProvider("juhe", "Juhe 聚合", jsdelivr("juhe"), "", 116, true));
        addSpec(new ResolverProvider("lx", "LX Source", jsdelivr("lx"), "", 112, true));
        addSpec(new ResolverProvider("flower", "Flower 野花", jsdelivr("flower"), "", 106, false));
        addSpec(new ResolverProvider("grass", "Grass", jsdelivr("grass"), "", 100, false));
        addSpec(new ResolverProvider("sixyin", "SixYin 六音", jsdelivr("sixyin"), "", 96, false));
        addSpec(new ResolverProvider("ikun", "ikun", jsdelivr("ikun"), "", 92, false));
        // Huibq remains available only while playback is idle; TX is always excluded.
        addSpec(new ResolverProvider("huibq", "Huibq", "", "source/huibq-latest.js", 88, false));
    }

    public interface AttemptListener { void onAttempt(String routeKey); }
    /** Playback owns this gate. Waiting here never consumes the per-song discovery time budget. */
    public interface AttemptGate {
        void awaitWindow() throws Exception;
        boolean onlineSteady();
    }

    /**
     * V66 Download Matrix Full-Lite. A plan-song gets up to fourteen REAL resolver calls and about
     * twenty seconds of active discovery in one automatic round. Playback wait time is excluded
     * from that budget. The second automatic round receives the persisted attempted-route set, so
     * it explores new cells instead of repeating the first round.
     */
    public final class Session {
        private final Song song;
        private final List<SourceVariant> variants;
        private final PlaybackTrace trace;
        private final Set<String> attempted;
        private final AttemptListener listener;
        private final AttemptGate gate;
        private long activeBudgetLeftMs = DISCOVERY_ROUND_BUDGET_MS;
        private int attemptsThisRound;
        private Exception lastFailure;

        private Session(Song song,List<SourceVariant> variants,PlaybackTrace trace,Set<String> attempted,
                        AttemptListener listener,AttemptGate gate){
            this.song=song;this.variants=variants;this.trace=trace;this.attempted=attempted;
            this.listener=listener;this.gate=gate;
        }

        public int attemptsThisRound(){return attemptsThisRound;}
        public int maxAttempts(){return MAX_PROVIDER_ATTEMPTS_PER_ROUND;}

        public DownloadResolver.Route nextRoute() throws Exception {
            if(attemptsThisRound>=MAX_PROVIDER_ATTEMPTS_PER_ROUND||activeBudgetLeftMs<=0L)throw budgetError();
            for(String quality:new String[]{"320k","128k"}){
                boolean onlineSteady=gate!=null&&gate.onlineSteady();
                List<Cell> cells=rankedCells(song,variants,quality,onlineSteady);
                for(Cell cell:cells){
                    if(attemptsThisRound>=MAX_PROVIDER_ATTEMPTS_PER_ROUND||activeBudgetLeftMs<=0L)throw budgetError();
                    if(!cell.variant.supports(quality)||cooling(cell.spec.id))continue;
                    if(gate!=null)gate.awaitWindow();
                    onlineSteady=gate!=null&&gate.onlineSteady();
                    if(onlineSteady&&"huibq".equals(cell.spec.id))continue;
                    String routeKey=routeKey(cell.spec,cell.variant,quality);
                    if(attempted!=null&&attempted.contains(routeKey))continue;
                    AudioProvider provider=provider(cell.spec);
                    if(!provider.supportsPlatform(cell.variant)||!provider.supports(cell.variant,quality))continue;
                    if(attempted!=null)attempted.add(routeKey);
                    if(listener!=null)listener.onAttempt(routeKey);
                    attemptsThisRound++;
                    long started=System.currentTimeMillis();
                    try{
                        trace.add("download-discovery",provider.label()+" × "+Song.providerLabel(cell.variant.source)+" / "+quality+
                                " · "+attemptsThisRound+"/"+MAX_PROVIDER_ATTEMPTS_PER_ROUND+" · 剩余"+Math.max(0L,activeBudgetLeftMs/1000L)+"s");
                        AudioCandidate candidate=provider.resolve(cell.variant,song,quality,trace);
                        if(candidate==null||!candidate.valid()||candidate.matchConfidence<.98d)
                            throw new Exception("下载候选未通过 Exact Gate");
                        activeBudgetLeftMs-=Math.max(1L,System.currentTimeMillis()-started);
                        markProviderSuccess(provider.id());
                        return new DownloadResolver.Route(song,cell.variant,candidate.quality,candidate.url,candidate.providerId,candidate.providerLabel);
                    }catch(Exception e){
                        activeBudgetLeftMs-=Math.max(1L,System.currentTimeMillis()-started);
                        lastFailure=e;markProviderFailure(provider.id(),e);
                        trace.add("download-discovery-fail",provider.label()+" · "+safe(e));
                        if(attemptsThisRound>=MAX_PROVIDER_ATTEMPTS_PER_ROUND||activeBudgetLeftMs<=0L)throw budgetError();
                        long pace=onlineSteady?1_400L:850L;
                        long sleep=Math.min(pace,Math.max(0L,activeBudgetLeftMs));
                        if(sleep>0L){
                            try{Thread.sleep(sleep);}catch(InterruptedException ie){Thread.currentThread().interrupt();throw ie;}
                            activeBudgetLeftMs-=sleep;
                        }
                    }
                }
            }
            if(lastFailure!=null)throw new Exception("主动下载本轮没有新的可靠线路 · "+safe(lastFailure));
            throw new Exception("主动下载本轮没有新的可靠线路");
        }

        private Exception budgetError(){
            String suffix=lastFailure==null?"":" · 最近："+safe(lastFailure);
            return new Exception("主动下载本轮预算已用完 · 最多14路/20秒"+suffix);
        }
    }

    public Session openSession(Song input,PlaybackTrace trace,Set<String> attempted,AttemptListener listener,AttemptGate gate)throws Exception{
        Song song=ExactTrackMatcher.sanitizeVariants(storedVariants.enrich(ExactTrackMatcher.sanitizeVariants(input)));
        song=discoverNonTxIdentity(song,trace);
        List<SourceVariant> variants=nonTxVariants(song);
        if(variants.isEmpty())throw new Exception("未找到可用于安全下载的非 TX Exact 身份");
        return new Session(song,variants,trace,attempted,listener,gate);
    }

    private static String routeKey(ResolverProvider spec,SourceVariant variant,String quality){
        return (spec==null?"":spec.id)+"|"+(variant==null?"":variant.source)+"|"+(variant==null?"":variant.sourceId)+"|"+(quality==null?"":quality);
    }

    /** Global pacing persisted across service restarts. */
    public long reserveDiscoverySlot(boolean whileOnlinePlayback) {
        synchronized (DownloadDiscoveryResolver.class) {
            long now = System.currentTimeMillis();
            long next = prefs.getLong("global.next", 0L);
            if (next > now) return next - now;
            // Single-threaded + per-song 20s budget + provider cooldowns are the primary safety
            // rails now. Stable playback no longer needs a 12s artificial discovery gap.
            long interval = whileOnlinePlayback ? 2_500L : 1_250L;
            prefs.edit().putLong("global.next", now + interval).apply();
            return 0L;
        }
    }

    private Song discoverNonTxIdentity(Song base, PlaybackTrace trace) {
        Song current = base;
        if (hasNonTx(current)) return current;
        String query = query(current);
        // One exact metadata search at a time.  Stop as soon as a safe identity exists.
        current = mergeExact(current, safeSearchNetease(query));
        if (!hasNonTx(current)) current = mergeExact(current, safeSearchKuwo(query));
        if (!hasNonTx(current)) current = mergeExact(current, safeSearchKugou(query));
        if (trace != null) trace.add("download-identity", variantSummary(current));
        return ExactTrackMatcher.sanitizeVariants(current);
    }

    private List<Song> safeSearchNetease(String q) { try { return netease.search(q, 10); } catch (Exception ignored) { return new ArrayList<>(); } }
    private List<Song> safeSearchKuwo(String q) { try { return kuwo.search(q, 10); } catch (Exception ignored) { return new ArrayList<>(); } }
    private List<Song> safeSearchKugou(String q) { try { return kugou.search(q, 10); } catch (Exception ignored) { return new ArrayList<>(); } }

    private static Song mergeExact(Song anchor, List<Song> candidates) {
        Song best = null; double score = 0d;
        if (candidates != null) for (Song s : candidates) {
            double c = ExactTrackMatcher.confidence(anchor, s);
            if (c >= .98d && c > score) { best = s; score = c; }
        }
        return best == null ? anchor : anchor.withVariant(best);
    }

    private List<Cell> rankedCells(Song song, List<SourceVariant> variants, String quality, boolean whileOnlinePlayback) {
        ArrayList<Cell> out = new ArrayList<>();
        for (SourceVariant v : variants) for (ResolverProvider spec : providerSpecs) {
            if (whileOnlinePlayback && "huibq".equals(spec.id)) continue;
            int score = spec.basePriority + platformScore(v.source);
            // V66 shares NAVIGATION knowledge with playback, read-only. A route that recently
            // proved READY gets tried early; old successes/failures fade toward neutral. Download
            // still owns its own provider runtimes/cooldowns and never writes playback learning.
            score += downloadRoutes.affinityBonus(song.key(), quality, v.source, spec.id);
            score += playbackRoutes.globalAffinityBonus(song.key(), quality, v.source, spec.id);
            score += playbackRoutes.platformAffinityBonus(song.key(), v.source, quality, spec.id);
            score += Math.round(playbackHealth.learningScore(spec.id, v.source, quality) * 650f);
            out.add(new Cell(spec, v, score));
        }
        out.sort(Comparator.comparingInt((Cell c) -> c.score).reversed());
        return out;
    }

    private boolean cooling(String provider) { return prefs.getLong("cool." + provider, 0L) > System.currentTimeMillis(); }
    private void markProviderSuccess(String provider) { prefs.edit().remove("cool." + provider).remove("reason." + provider).apply(); }
    private void markProviderFailure(String provider, Exception error) {
        ResolverFailure f = error instanceof ResolverFailure ? (ResolverFailure) error : ResolverFailure.classify(safe(error));
        long cooldown;
        switch (f.kind) {
            case RATE_LIMITED: cooldown = RATE_COOLDOWN; break;
            case PROVIDER_UNAVAILABLE: cooldown = SERVICE_COOLDOWN; break;
            case PROVIDER_INIT_FAILED: cooldown = INIT_COOLDOWN; break;
            case NETWORK: cooldown = SOFT_COOLDOWN; break;
            default: cooldown = 0L; break;
        }
        SharedPreferences.Editor e = prefs.edit().putString("reason." + provider, safe(error));
        if (cooldown > 0L) e.putLong("cool." + provider, System.currentTimeMillis() + cooldown);
        e.apply();
    }

    private static List<SourceVariant> nonTxVariants(Song s) {
        ArrayList<SourceVariant> out = new ArrayList<>();
        if (s != null) for (SourceVariant v : s.variants()) if (v != null && !"tx".equals(v.source)) out.add(v);
        out.sort(Comparator.comparingInt(v -> platformRank(v.source)));
        return out;
    }
    private static boolean hasNonTx(Song s) { return !nonTxVariants(s).isEmpty(); }
    private static int platformRank(String s) { if ("wy".equals(s)) return 0; if ("kw".equals(s)) return 1; if ("kg".equals(s)) return 2; if ("mg".equals(s)) return 3; return 9; }
    private static int platformScore(String s) { if ("wy".equals(s)) return 500; if ("kw".equals(s)) return 420; if ("kg".equals(s)) return 360; if ("mg".equals(s)) return 250; return 0; }
    private static String query(Song s) { String a = s == null ? "" : s.artist; if (a.contains(" / ")) a = a.substring(0, a.indexOf(" / ")); return (s == null ? "" : s.title) + (a.trim().isEmpty() ? "" : " " + a.trim()); }
    private static String variantSummary(Song s) { StringBuilder b = new StringBuilder("download variants "); if (s != null) for (SourceVariant v : s.variants()) { if (b.charAt(b.length()-1) != ' ') b.append(','); b.append(v.source); } return b.toString(); }
    private static String safe(Exception e) { String s = e == null || e.getMessage() == null ? "unknown" : e.getMessage().replace('\n',' ').trim(); return s.length() > 120 ? s.substring(0,120) + "…" : s; }
    private void addSpec(ResolverProvider spec) { providerSpecs.add(spec); }
    private synchronized AudioProvider provider(ResolverProvider spec) {
        AudioProvider existing = providers.get(spec.id);
        if (existing != null) return existing;
        AudioProvider created = new LxAudioProvider(providerSandbox, spec);
        providers.put(spec.id, created);
        return created;
    }
    private static String jsdelivr(String folder) { return "https://fastly.jsdelivr.net/gh/pdone/lx-music-source@main/" + folder + "/latest.js"; }

    public void destroy() { for (AudioProvider p : providers.values()) try { p.destroy(); } catch (Exception ignored) { } providers.clear(); }

    /** Gives download resolver runtimes their own pinned-script filesystem and preferences. */
    private static final class DownloadSandboxContext extends ContextWrapper {
        private final File files;
        DownloadSandboxContext(Context base) {
            super(base);
            files = new File(base.getFilesDir(), "download_resolver_sandbox_v1");
            if (!files.exists()) files.mkdirs();
        }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { return files; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences("download-sandbox." + name, mode);
        }
    }

    private static final class Cell {
        final ResolverProvider spec; final SourceVariant variant; final int score;
        Cell(ResolverProvider spec, SourceVariant v, int score) { this.spec = spec; variant = v; this.score = score; }
    }
}
