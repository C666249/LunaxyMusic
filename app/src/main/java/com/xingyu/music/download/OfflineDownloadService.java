package com.xingyu.music.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import com.xingyu.music.data.PlaybackNetworkGate;
import com.xingyu.music.data.PlaybackTrace;
import com.xingyu.music.model.Song;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lunaxy Download Queue 2.0 — V66 self-healing matrix scheduler.
 *
 * V66 keeps the proven single-worker isolation while broadening bounded route discovery:
 *  - exactly one download at a time; TX media GET remains disabled;
 *  - playback resolving / Media3 BUFFERING preempts download reads immediately;
 *  - steady online playback may coexist with ONE full-speed transfer; resolving/BUFFERING preempts it;
 *  - cache-only READY reuse is still the fast path; a sandboxed cold resolver is only the fallback;
 *  - cold discovery owns separate provider runtimes/cooldowns and never writes playback
 *    Health/Route/URL/READY state.
 *
 * Large playlists remain persisted DownloadPlans, so 1000+ songs never become a giant hot queue.
 */
public final class OfflineDownloadService extends Service {
    public static final String ACTION_CHANGED = "com.xingyu.music.DOWNLOAD_CHANGED";
    private static final String ACTION_WAKE = "com.xingyu.music.DOWNLOAD_WAKE";
    private static final String ACTION_CANCEL_SONG = "com.xingyu.music.DOWNLOAD_CANCEL_SONG";
    private static final String ACTION_PAUSE_ALL = "com.xingyu.music.DOWNLOAD_PAUSE_ALL";
    private static final String ACTION_RESUME_ALL = "com.xingyu.music.DOWNLOAD_RESUME_ALL";
    private static final String ACTION_PAUSE_PLAN = "com.xingyu.music.DOWNLOAD_PAUSE_PLAN";
    private static final String ACTION_RESUME_PLAN = "com.xingyu.music.DOWNLOAD_RESUME_PLAN";
    private static final String ACTION_CANCEL_PLAN = "com.xingyu.music.DOWNLOAD_CANCEL_PLAN";
    private static final String ACTION_DELETE_PLAN = "com.xingyu.music.DOWNLOAD_DELETE_PLAN";
    private static final String ACTION_RETRY_FAILED = "com.xingyu.music.DOWNLOAD_RETRY_FAILED";
    private static final String EXTRA_KEY = "song_key";
    private static final String EXTRA_PLAN = "plan_id";
    private static final String CHANNEL = "lunaxy_offline_downloads";
    private static final int NOTIFICATION_ID = 4207;
    private static final long MAX_TRACK_BYTES = 256L * 1024L * 1024L;
    private static final long ONLINE_DISCOVERY_GRACE_MS = 3_000L;
    private static final long PLAYBACK_RECOVERY_HOLD_MS = 5_000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean loopRunning;
    private volatile String currentKey = "";
    private volatile String currentPlanId = "";
    private volatile long lastBroadcastMs;
    private volatile String pendingDeletePlanId = "";
    private DownloadResolver resolver;
    private DownloadDiscoveryResolver discovery;
    private OfflineStore offline;
    private DownloadPlanStore plans;
    private long steadyOnlineSinceMs;
    private long playbackRecoveryUntilMs;

    private static final class PlanPaused extends Exception { PlanPaused(){super("已暂停");} }
    private static final class PlanCancelled extends Exception { PlanCancelled(){super("已取消");} }
    private static final class StaleMediaUrl extends Exception {
        final int code;
        StaleMediaUrl(int code){super("下载 HTTP "+code);this.code=code;}
    }

    @Override public void onCreate() {
        super.onCreate();
        resolver = new DownloadResolver(this);
        discovery = new DownloadDiscoveryResolver(this);
        offline = new OfflineStore(this);
        plans = new DownloadPlanStore(this);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        startForeground(NOTIFICATION_ID,notification("等待下载任务",0,true));
        if(intent!=null){
            String action=intent.getAction();
            if(ACTION_CANCEL_SONG.equals(action)){
                String key=intent.getStringExtra(EXTRA_KEY); if(key!=null&&!key.isEmpty()) cancelSongKey(key);
            } else if(ACTION_PAUSE_ALL.equals(action)) {
                plans.pauseAll(); broadcast(true);
            } else if(ACTION_RESUME_ALL.equals(action)) {
                plans.resumeAll(); broadcast(true);
            } else if(ACTION_PAUSE_PLAN.equals(action)) {
                String id=intent.getStringExtra(EXTRA_PLAN); if(id!=null&&!id.isEmpty()) plans.pausePlan(id); broadcast(true);
            } else if(ACTION_RESUME_PLAN.equals(action)) {
                String id=intent.getStringExtra(EXTRA_PLAN); if(id!=null&&!id.isEmpty()) plans.resumePlan(id); broadcast(true);
            } else if(ACTION_CANCEL_PLAN.equals(action)) {
                String id=intent.getStringExtra(EXTRA_PLAN); if(id!=null&&!id.isEmpty()) plans.cancelPlan(id); broadcast(true);
            } else if(ACTION_DELETE_PLAN.equals(action)) {
                String id=intent.getStringExtra(EXTRA_PLAN);
                if(id!=null&&!id.isEmpty()) {
                    plans.cancelPlan(id);
                    if(id.equals(currentPlanId)) pendingDeletePlanId=id; else plans.deletePlanNow(id);
                    broadcast(true);
                }
            } else if(ACTION_RETRY_FAILED.equals(action)) {
                String id=intent.getStringExtra(EXTRA_PLAN);
                if(id!=null&&!id.isEmpty()) { plans.retryFailed(id); broadcast(true); }
            }
        }
        ensureLoop();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent){return null;}

    public static void enqueue(Context context,Song song){
        if(context==null||song==null)return;
        ArrayList<Song> one=new ArrayList<>();one.add(song);
        enqueueAll(context,"单曲下载 · "+song.title,one);
    }

    /** Creates the persisted plan off the main thread, then wakes the one-at-a-time downloader. */
    public static void enqueueAll(Context context, List<Song> songs){ enqueueAll(context,"批量下载",songs); }
    public static void enqueueAll(Context context,String name,List<Song> songs){
        if(context==null||songs==null||songs.isEmpty())return;
        Context app=context.getApplicationContext();
        ArrayList<Song> copy=new ArrayList<>(); for(Song s:songs)if(s!=null)copy.add(s);
        if(copy.isEmpty())return;
        new Thread(()->{
            try{
                DownloadPlanStore store=new DownloadPlanStore(app);
                String id=store.create(name,copy);
                app.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(app.getPackageName()));
                if(!id.isEmpty()) start(app,new Intent(app,OfflineDownloadService.class).setAction(ACTION_WAKE));
            }catch(Exception ignored){ app.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(app.getPackageName())); }
        },"Lunaxy-DownloadPlan").start();
    }

    public static void cancel(Context context,Song song){
        if(context==null||song==null)return;
        start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_CANCEL_SONG).putExtra(EXTRA_KEY,song.key()));
    }
    public static void pauseAll(Context context){if(context==null)return;new DownloadPlanStore(context).pauseAll();start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_PAUSE_ALL));}
    public static void resumeAll(Context context){if(context==null)return;new DownloadPlanStore(context).resumeAll();start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_RESUME_ALL));}
    public static void pausePlan(Context context,String id){if(context==null||id==null)return;new DownloadPlanStore(context).pausePlan(id);start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_PAUSE_PLAN).putExtra(EXTRA_PLAN,id));}
    public static void resumePlan(Context context,String id){if(context==null||id==null)return;new DownloadPlanStore(context).resumePlan(id);start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_RESUME_PLAN).putExtra(EXTRA_PLAN,id));}
    public static void cancelPlan(Context context,String id){if(context==null||id==null)return;new DownloadPlanStore(context).cancelPlan(id);start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_CANCEL_PLAN).putExtra(EXTRA_PLAN,id));}
    public static void deletePlan(Context context,String id){if(context==null||id==null)return;start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_DELETE_PLAN).putExtra(EXTRA_PLAN,id));}
    public static void retryFailed(Context context,String id){if(context==null||id==null)return;DownloadPlanStore store=new DownloadPlanStore(context);int n=store.retryFailed(id);if(n>0)start(context,new Intent(context,OfflineDownloadService.class).setAction(ACTION_WAKE));context.getApplicationContext().sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));}
    public static int clearHistory(Context context){if(context==null)return 0;int n=new DownloadPlanStore(context).clearHistory();context.getApplicationContext().sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));return n;}
    public static List<DownloadPlanStore.Summary> planSummaries(Context context){return context==null?new ArrayList<>():new DownloadPlanStore(context).summaries();}
    public static List<DownloadPlanStore.Item> planDetails(Context context,String id){return context==null?new ArrayList<>():new DownloadPlanStore(context).details(id);}
    public static boolean downloadsPaused(Context context){return context!=null&&new DownloadPlanStore(context).globalPaused();}

    public static String state(Context context,Song song){if(context==null||song==null)return "";return new DownloadPlanStore(context).activeStateForSong(song);}
    public static boolean active(Context context,Song song){String s=state(context,song);return s.startsWith("排队")||s.startsWith("解析")||s.startsWith("主动")||s.startsWith("下载")||s.startsWith("下载调度")||s.startsWith("等待播放")||s.startsWith("已暂停")||s.startsWith("等待下载")||s.startsWith("准备下载");}

    private static void start(Context context,Intent intent){
        Context app=context.getApplicationContext(); intent.setClass(app,OfflineDownloadService.class);
        if(Build.VERSION.SDK_INT>=26)app.startForegroundService(intent);else app.startService(intent);
    }

    private void cancelSongKey(String key){
        plans.cancelSongAcrossActivePlans(key);
        broadcast(true);
    }

    private void ensureLoop(){if(loopRunning)return;loopRunning=true;worker.submit(this::runLoop);}

    private void runLoop(){
        try{
            while(true){
                if(plans.globalPaused())break;
                DownloadPlanStore.Claim claim=plans.nextClaim();
                if(claim==null)break;
                currentPlanId=claim.plan.id; currentKey=claim.song.key();
                String existing=plans.songState(claim.plan.id,claim.song);
                if(existing.startsWith("已取消")){ plans.finishClaim(claim); continue; }
                if(offline.contains(claim.song)){ clearState(claim); plans.finishClaim(claim); broadcast(false); continue; }
                plans.setCurrent(currentPlanId,claim.song,"准备下载",0);
                updateNotification(claim.plan.name+" · "+(claim.index+1)+" / "+claim.plan.total,0,true);
                boolean waitRoute=false;
                try{
                    downloadOne(claim);
                    plans.finishClaim(claim);
                }catch(PlanPaused paused){
                    cleanupPart(claim.song);
                    setState(claim,"已暂停");
                    if (plans.globalPaused()) break;
                    continue;
                }catch(PlanCancelled cancelled){
                    cleanupPart(claim.song);
                    setState(claim,"已取消");
                    if(!plans.isPlanCancelled(claim.plan.id)) plans.finishClaim(claim);
                    if(plans.isPlanCancelled(claim.plan.id)) continue;
                }catch(Exception e){
                    cleanupPart(claim.song);
                    if(isWaitingRoute(e)){
                        waitRoute=true; plans.markRetryableFailure(claim.plan.id,claim.song,"等待可用线路"); setState(claim,"等待可用线路");
                    }else if(isTransientDownloadFailure(e)){
                        plans.markRetryableFailure(claim.plan.id,claim.song,"待重试 · "+shortReason(e)); setState(claim,"待重试 · "+shortReason(e));
                    }else if(!plans.songState(claim.plan.id,claim.song).startsWith("已取消")){
                        setState(claim,"失败 · "+shortReason(e));
                    }
                    plans.finishClaim(claim);
                }finally{
                    String finishedPlan=currentPlanId;
                    plans.clearCurrent(); currentKey=""; currentPlanId="";
                    if(!pendingDeletePlanId.isEmpty()&&pendingDeletePlanId.equals(finishedPlan)){plans.deletePlanNow(pendingDeletePlanId);pendingDeletePlanId="";}
                    broadcast(true);
                }
                try{Thread.sleep(waitRoute?420L:1_500L);}catch(InterruptedException ie){Thread.currentThread().interrupt();break;}
            }
        }finally{
            currentKey="";currentPlanId="";plans.clearCurrent();loopRunning=false;
            broadcast(true);
            updateNotification(plans.globalPaused()?"下载任务已暂停":"下载任务暂时空闲",0,false);
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }

    private void downloadOne(DownloadPlanStore.Claim claim)throws Exception{
        Song song=claim.song;
        checkPlanControl(claim);
        setState(claim,"解析下载线路");plans.updateCurrentState("解析下载线路",0);updateNotification(song.title+" · 解析下载线路",0,true);
        PlaybackTrace trace=new PlaybackTrace();trace.reset(song.title,song.artist);

        Set<String> staleReadyUrls=plans.staleReadyUrls(claim.plan.id,song);
        Set<String> attemptedRoutes=plans.attemptedRoutes(claim.plan.id,song);
        int autoRound=Math.max(1,Math.min(2,plans.retryCount(claim.plan.id,song)+1));
        DownloadResolver.Route route=null;
        boolean routeFromReadyCache=false;
        DownloadDiscoveryResolver.Session session=null;

        try{
            // Fast path remains zero-resolver traffic, and V66 still never treats an old READY URL as
            // permanent truth. Exact URLs already proven stale inside THIS plan-song are skipped.
            route=resolver.resolve(song,trace,staleReadyUrls);
            routeFromReadyCache=true;
        }catch(Exception cachedMiss){
            trace.add("download-cache-miss",shortReason(cachedMiss));
        }

        while(true){
            checkPlanControl(claim);
            if(route==null){
                if(session==null){
                    awaitDiscoveryWindow(claim);
                    String st="主动寻找下载线路 · 第"+autoRound+"轮";setState(claim,st);plans.updateCurrentState(st,0);
                    updateNotification(song.title+" · "+st,0,true);
                    session=discovery.openSession(song,trace,attemptedRoutes,
                            key->plans.recordAttemptedRoute(claim.plan.id,song,key),
                            new DownloadDiscoveryResolver.AttemptGate(){
                                @Override public void awaitWindow() throws Exception { awaitPlaybackDiscoveryWindow(claim); }
                                @Override public boolean onlineSteady() {
                                    return PlaybackNetworkGate.isBusy(OfflineDownloadService.this)
                                            && isSteadyOnline(PlaybackNetworkGate.reason(OfflineDownloadService.this));
                                }
                            });
                }
                route=session.nextRoute();
                routeFromReadyCache=false;
            }

            checkPlanControl(claim);
            awaitTransferWindow(claim,"下载前让路");
            try{
                transferRoute(claim,route);
                return;
            }catch(StaleMediaUrl stale){
                cleanupPart(song);
                boolean staleWasReadyCache=routeFromReadyCache;
                if(routeFromReadyCache){
                    String fp=DownloadResolver.urlFingerprint(route.url);
                    plans.recordStaleReadyUrl(claim.plan.id,song,fp);staleReadyUrls.add(fp);
                    trace.add("download-cache-stale","READY URL HTTP "+stale.code+" · download-only stale");
                    String st="旧 READY "+stale.code+" · 主动换线路";setState(claim,st);plans.updateCurrentState(st,0);
                }else{
                    trace.add("download-route-stale",route.providerLabel+" · HTTP "+stale.code);
                    String st="线路 "+stale.code+" · 继续换路";setState(claim,st);plans.updateCurrentState(st,0);
                }
                // Do not invalidate PlaybackUrlStore / SourceHealth / TrackRouteStore here. The
                // download plane simply continues its bounded matrix session with a different cell.
                route=null;routeFromReadyCache=false;
                // A resolved URL that is rejected by the media host still consumes real upstream
                // traffic. Pace discovered-route fallbacks so the wider time-bounded matrix cannot become a
                // burst; the old READY fast-path gets only a tiny handoff delay.
                try{Thread.sleep(staleWasReadyCache?120L:800L);}catch(InterruptedException ie){Thread.currentThread().interrupt();throw ie;}
            }
        }
    }

    private void transferRoute(DownloadPlanStore.Claim claim,DownloadResolver.Route route)throws Exception{
        Song song=claim.song;
        File root=getExternalFilesDir(Environment.DIRECTORY_MUSIC);if(root==null)throw new Exception("系统未提供 App 离线存储目录");
        File dir=new File(root,"offline");if(!dir.exists()&&!dir.mkdirs())throw new Exception("无法创建离线目录");
        File part=new File(dir,DownloadPlanStore.hash(song.key())+".part");if(part.exists())part.delete();
        HttpURLConnection c=(HttpURLConnection)new URL(route.url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(6500);c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","Mozilla/5.0 LunaxyMusic/Offline");c.setRequestProperty("Accept","audio/*,*/*;q=0.8");
        int code=c.getResponseCode();
        if(code==401||code==403||code==404||code==410){c.disconnect();throw new StaleMediaUrl(code);}
        if(code<200||code>=300){c.disconnect();throw new Exception("下载 HTTP "+code);}
        String contentType=cleanMime(c.getContentType());if(!contentType.startsWith("audio/")&&!"application/octet-stream".equals(contentType)){c.disconnect();throw new Exception("下载响应不是音频："+contentType);}
        long total=c.getContentLengthLong(),written=0L,lastUi=0L;if(total>MAX_TRACK_BYTES){c.disconnect();throw new Exception("离线文件异常过大，已保护性停止下载");}
        try(BufferedInputStream in=new BufferedInputStream(c.getInputStream(),64*1024);BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(part),64*1024)){
            byte[] buf=new byte[64*1024];int n;
            while(true){
                checkPlanControl(claim);
                // V66 Playback-First adaptive transfer: stable playback gets full-speed coexistence.
                // Only resolving/BUFFERING/recovery windows pause reads; there is no fixed 160KB/s cap.
                awaitTransferWindow(claim,"播放优先，下载暂停");
                n=in.read(buf);if(n<0)break;if(n==0)continue;
                out.write(buf,0,n);written+=n;if(written>MAX_TRACK_BYTES)throw new Exception("离线文件超过 256 MB，已保护性停止下载");
                long now=System.currentTimeMillis();if(now-lastUi>650L){int p=percent(written,total);String st=total>0?"下载中 · "+p+"%":"下载中 · "+formatBytes(written);setState(claim,st);plans.updateCurrentState(st,p);updateNotification(song.title+" · "+(total>0?p+"%":formatBytes(written)),p,total>0);lastUi=now;}
            }out.flush();
        }finally{c.disconnect();}
        if(written<4096L)throw new Exception("下载文件异常过小");
        String ext=extension(contentType,route.url);File finalFile=new File(dir,DownloadPlanStore.hash(song.key())+ext);if(finalFile.exists())finalFile.delete();if(!part.renameTo(finalFile))throw new Exception("保存离线文件失败");
        try{verifyDuration(song,finalFile);}catch(Exception e){finalFile.delete();throw e;}
        offline.put(song,finalFile,contentType,route.quality,route.variant.source,route.providerId,route.providerLabel);resolver.markSuccess(route);clearState(claim);broadcast(true);
    }

    private void checkPlanControl(DownloadPlanStore.Claim claim)throws Exception{
        if(plans.globalPaused()||plans.isPlanPaused(claim.plan.id))throw new PlanPaused();
        if(plans.isPlanCancelled(claim.plan.id)||plans.songState(claim.plan.id,claim.song).startsWith("已取消"))throw new PlanCancelled();
    }
    /**
     * Cold resolver admission. Playback resolution / BUFFERING has absolute priority. During
     * steady online playback we wait for a grace window, then allow the bounded V66 matrix to proceed.
     * This is the key difference from the old all-or-nothing gate: playback stays P0, while a
     * carefully rate-limited background plane can still make progress.
     */
    private void awaitDiscoveryWindow(DownloadPlanStore.Claim claim)throws Exception{
        awaitPlaybackDiscoveryWindow(claim);
        boolean online=PlaybackNetworkGate.isBusy(this)&&isSteadyOnline(PlaybackNetworkGate.reason(this));
        while(true){
            checkPlanControl(claim); long wait=discovery.reserveDiscoverySlot(online); if(wait<=0L)break;
            String st="下载调度 · 等待安全窗口";setState(claim,st);plans.updateCurrentState(st,0);
            Thread.sleep(Math.min(600L,wait));
        }
    }

    /** V66: called before every real resolver request; playback wait time does not consume matrix budget. */
    private void awaitPlaybackDiscoveryWindow(DownloadPlanStore.Claim claim)throws Exception{
        while(true){
            checkPlanControl(claim);
            boolean busy=PlaybackNetworkGate.isBusy(this);
            String reason=PlaybackNetworkGate.reason(this);
            long now=System.currentTimeMillis();
            if(!busy){ steadyOnlineSinceMs=0L; return; }
            if(isSteadyOnline(reason)){
                if(steadyOnlineSinceMs<=0L)steadyOnlineSinceMs=now;
                if(now>=playbackRecoveryUntilMs&&now-steadyOnlineSinceMs>=ONLINE_DISCOVERY_GRACE_MS)return;
            }else{
                steadyOnlineSinceMs=0L; playbackRecoveryUntilMs=now+PLAYBACK_RECOVERY_HOLD_MS;
            }
            String st="等待播放 · "+(reason.isEmpty()?"播放链忙":reason);setState(claim,st);plans.updateCurrentState(st,0);
            updateNotification(claim.song.title+" · 播放优先",0,true);Thread.sleep(300L);
        }
    }

    /**
     * Returns true when transfer is allowed concurrently with stable online playback. Critical
     * playback phases (resolving/BUFFERING) stop download reads immediately and receive a recovery
     * hold. V66 keeps the fixed bandwidth cap removed once playback is stable again.
     */
    private boolean awaitTransferWindow(DownloadPlanStore.Claim claim,String label)throws Exception{
        while(true){
            checkPlanControl(claim);
            boolean busy=PlaybackNetworkGate.isBusy(this);String reason=PlaybackNetworkGate.reason(this);long now=System.currentTimeMillis();
            if(!busy){steadyOnlineSinceMs=0L;return false;}
            if(isSteadyOnline(reason)){
                if(steadyOnlineSinceMs<=0L)steadyOnlineSinceMs=now;
                if(now>=playbackRecoveryUntilMs)return true;
                // A prior BUFFERING/resolve event owns a short recovery hold. Do NOT extend the hold
                // merely because playback has become stable again.
            }else{
                steadyOnlineSinceMs=0L;playbackRecoveryUntilMs=now+PLAYBACK_RECOVERY_HOLD_MS;
            }
            String st="等待播放 · "+(reason.isEmpty()?"播放链忙":reason);setState(claim,st);plans.updateCurrentState(st,0);
            updateNotification(claim.song.title+" · "+label,0,true);Thread.sleep(300L);
        }
    }
    private static boolean isSteadyOnline(String reason){return reason!=null&&reason.contains("在线播放中")&&!reason.contains("BUFFERING")&&!reason.contains("解析");}
    private static boolean isWaitingRoute(Exception e){String s=e==null||e.getMessage()==null?"":e.getMessage();return s.contains("READY")||s.contains("可复用")||s.contains("TX")||s.contains("Exact Catalog")||s.contains("线路")||s.contains("下载候选")||s.contains("主动下载")||s.contains("非 TX")||s.contains("身份");}
    private static boolean isTransientDownloadFailure(Exception e){
        String s=e==null||e.getMessage()==null?"":e.getMessage().toLowerCase(java.util.Locale.ROOT);
        return s.contains("http 429")||s.contains("http 500")||s.contains("http 502")||s.contains("http 503")||s.contains("http 504")
                ||s.contains("timeout")||s.contains("timed out")||s.contains("reset")||s.contains("network")||s.contains("connection");
    }

    private static void verifyDuration(Song song,File file)throws Exception{MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(file.getAbsolutePath());String d=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(d==null||d.trim().isEmpty())throw new Exception("离线校验失败：无法读取音频时长");long actual=Long.parseLong(d);if(actual<=0)throw new Exception("离线校验失败：无效音频时长");if(song!=null&&song.durationMs>0L){long tol=Math.max(7000L,Math.min(12000L,Math.round(song.durationMs*.03d)));if(Math.abs(actual-song.durationMs)>tol)throw new Exception("离线校验失败：音频时长与目标歌曲不一致");}}finally{try{r.release();}catch(Exception ignored){}}}
    private void cleanupPart(Song song){try{File root=getExternalFilesDir(Environment.DIRECTORY_MUSIC);if(root==null)return;File p=new File(new File(root,"offline"),DownloadPlanStore.hash(song.key())+".part");if(p.exists())p.delete();}catch(Exception ignored){}}

    private void setState(DownloadPlanStore.Claim claim,String state){
        if(claim==null||claim.song==null)return;plans.setSongState(claim.plan.id,claim.song,state);
        if(claim.song.key().equals(currentKey))plans.updateCurrentState(state,extractPercent(state));broadcast(false);
    }
    private void clearState(DownloadPlanStore.Claim claim){if(claim!=null&&claim.song!=null)plans.clearSongState(claim.plan.id,claim.song);}
    private void broadcast(boolean force){long now=System.currentTimeMillis();if(!force&&now-lastBroadcastMs<420L)return;lastBroadcastMs=now;sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName()));}

    private Notification notification(String text,int progress,boolean indeterminate){return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Lunaxy Music · 离线下载").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,Math.max(0,Math.min(100,progress)),indeterminate).build();}
    private void updateNotification(String text,int progress,boolean indeterminate){NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(n!=null)n.notify(NOTIFICATION_ID,notification(text,progress,indeterminate));}
    private void createChannel(){if(Build.VERSION.SDK_INT<26)return;NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(n!=null){NotificationChannel c=new NotificationChannel(CHANNEL,"离线下载",NotificationManager.IMPORTANCE_LOW);c.setSound(null,null);c.enableVibration(false);n.createNotificationChannel(c);}}

    @Override public void onTimeout(int startId,int fgsType){
        if(!currentKey.isEmpty()&&!currentPlanId.isEmpty()){for(Song s:plans.readSongs(currentPlanId))if(s.key().equals(currentKey)){plans.setSongState(currentPlanId,s,"已暂停 · 系统后台时限");break;}}
        plans.clearCurrent();broadcast(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy(){resolver.destroy();if(discovery!=null)discovery.destroy();worker.shutdownNow();super.onDestroy();}

    private static int extractPercent(String state){try{int i=state.indexOf('·'),j=state.indexOf('%');if(i>=0&&j>i)return Integer.parseInt(state.substring(i+1,j).trim());}catch(Exception ignored){}return 0;}
    private static int percent(long n,long total){return total<=0?0:(int)Math.max(0,Math.min(100,n*100L/total));}
    private static String cleanMime(String s){if(s==null)return "application/octet-stream";int i=s.indexOf(';');return (i>=0?s.substring(0,i):s).trim();}
    private static String extension(String mime,String url){String m=mime==null?"":mime.toLowerCase(java.util.Locale.ROOT),u=url==null?"":url.toLowerCase(java.util.Locale.ROOT);if(m.contains("flac")||u.contains(".flac"))return ".flac";if(m.contains("mp4")||m.contains("m4a")||u.contains(".m4a"))return ".m4a";if(m.contains("ogg")||u.contains(".ogg"))return ".ogg";if(m.contains("wav")||u.contains(".wav"))return ".wav";return ".mp3";}
    private static String shortReason(Exception e){String s=e==null||e.getMessage()==null?"unknown":e.getMessage().replace('\n',' ').trim();return s.length()>100?s.substring(0,100)+"…":s;}
    private static String formatBytes(long b){if(b>=1024L*1024L)return String.format(java.util.Locale.ROOT,"%.1f MB",b/(1024d*1024d));if(b>=1024L)return String.format(java.util.Locale.ROOT,"%.0f KB",b/1024d);return b+" B";}
}
