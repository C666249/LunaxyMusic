package com.xingyu.music.download;

import android.content.Context;
import android.content.SharedPreferences;

import com.xingyu.music.model.Song;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persisted, playback-independent download plans for Lunaxy.
 *
 * V3 state model: song progress belongs to a concrete plan instead of being globally keyed only by
 * Song.key(). This prevents an old failed/cancelled task from poisoning a later clean retry of the
 * same playlist while still sharing the only state that SHOULD be global: a verified local file in
 * OfflineStore.
 */
public final class DownloadPlanStore {
    private static final String PREFS = "lunaxy_download_plans_v2"; // plan files/metadata stay compatible
    private static final String STATE_PREFS = "lunaxy_download_plan_state_v2";
    private static final String LEGACY_STATE_PREFS = "lunaxy_download_state_v1";
    private static final String INDEX = "index";
    private static final String GLOBAL_PAUSED = "global_paused";
    private static final String CURRENT_PLAN = "current_plan";
    private static final String CURRENT_SONG = "current_song";
    private static final String CURRENT_STATE = "current_state";
    private static final String CURRENT_PROGRESS = "current_progress";
    private static final String MIGRATED_V2 = "plan_state_v2_migrated";
    private static final int MAX_AUTO_RETRY_FAILURES = 2;

    public static final String CAT_DONE = "done";
    public static final String CAT_ACTIVE = "active";
    public static final String CAT_WAITING = "waiting";
    public static final String CAT_FAILED = "failed";
    public static final String CAT_PENDING = "pending";
    public static final String CAT_CANCELLED = "cancelled";

    public static final class Plan {
        public final String id;
        public final String name;
        public final long createdAt;
        public final int total;
        public final int cursor;
        public final boolean paused;
        public final boolean cancelled;
        public final boolean exhausted;
        Plan(String id, String name, long createdAt, int total, int cursor, boolean paused, boolean cancelled, boolean exhausted) {
            this.id = id; this.name = name; this.createdAt = createdAt; this.total = total; this.cursor = cursor;
            this.paused = paused; this.cancelled = cancelled; this.exhausted = exhausted;
        }
    }

    public static final class Claim {
        public final Plan plan;
        public final int index;
        public final Song song;
        Claim(Plan plan, int index, Song song) { this.plan = plan; this.index = index; this.song = song; }
    }

    public static final class Summary {
        public final String id, name, status, currentSong, currentState;
        public final long createdAt;
        public final int total, downloaded, alreadyOffline, waiting, failed, pending, active, cursor, currentProgress;
        public final boolean paused, cancelled, exhausted;
        Summary(String id, String name, String status, String currentSong, String currentState, long createdAt,
                int total, int downloaded, int alreadyOffline, int waiting, int failed, int pending, int active,
                int cursor, int currentProgress, boolean paused, boolean cancelled, boolean exhausted) {
            this.id=id;this.name=name;this.status=status;this.currentSong=currentSong;this.currentState=currentState;this.createdAt=createdAt;
            this.total=total;this.downloaded=downloaded;this.alreadyOffline=alreadyOffline;this.waiting=waiting;this.failed=failed;
            this.pending=pending;this.active=active;this.cursor=cursor;this.currentProgress=currentProgress;
            this.paused=paused;this.cancelled=cancelled;this.exhausted=exhausted;
        }
        public int offlineTotal() { return Math.min(total, downloaded + alreadyOffline); }
        public int percent() { return total <= 0 ? 0 : Math.max(0, Math.min(100, Math.round(offlineTotal() * 100f / total))); }
        public boolean historyOnly() { return cancelled || (exhausted && active == 0 && pending == 0 && waiting == 0 && failed == 0); }
    }

    public static final class Item {
        public final int index;
        public final Song song;
        public final String state;
        public final String category;
        public final int progress;
        public final boolean offline;
        public final boolean preexisting;
        public final boolean current;
        Item(int index, Song song, String state, String category, int progress,
             boolean offline, boolean preexisting, boolean current) {
            this.index=index;this.song=song;this.state=state;this.category=category;this.progress=progress;
            this.offline=offline;this.preexisting=preexisting;this.current=current;
        }
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final SharedPreferences states;
    private final SharedPreferences legacyStates;
    private final OfflineStore offline;
    private final File dir;

    public DownloadPlanStore(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        states = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        legacyStates = app.getSharedPreferences(LEGACY_STATE_PREFS, Context.MODE_PRIVATE);
        offline = new OfflineStore(app);
        dir = new File(app.getFilesDir(), "download_plans_v2");
        if (!dir.exists()) dir.mkdirs();
        migrateLegacyStatesOnce();
    }

    /**
     * One-time V61/V62 cleanup. Legacy song-global FAILED/CANCELLED markers are deliberately not
     * trusted by the new scheduler. Existing cancelled plans remain historical; existing finished
     * plans with unresolved songs become explicit "待重试" history. An in-flight non-cancelled plan
     * is rewound only to its first unresolved local file so it can finish cleanly after app update.
     */
    private synchronized void migrateLegacyStatesOnce() {
        if (prefs.getBoolean(MIGRATED_V2, false)) return;
        SharedPreferences.Editor stateEdit = states.edit();
        SharedPreferences.Editor planEdit = prefs.edit();
        for (Plan p : plansRaw()) {
            List<Song> songs = readSongsRaw(p.id);
            int firstUnresolved = -1;
            for (int i=0;i<songs.size();i++) {
                Song s=songs.get(i); if (offline.contains(s)) continue;
                if (firstUnresolved < 0) firstUnresolved = i;
                String clean;
                if (p.cancelled) clean = "已取消";
                else if (p.exhausted || i < p.cursor) clean = "待重试";
                else clean = "等待下载";
                stateEdit.putString(stateKey(p.id,s), clean);
            }
            if (!p.cancelled && !p.exhausted && firstUnresolved >= 0 && firstUnresolved < p.cursor) {
                planEdit.putInt("cursor."+p.id, firstUnresolved);
            }
        }
        stateEdit.apply();
        // Old V61 global failure/cancel state must never control V63+ execution again.
        legacyStates.edit().clear().apply();
        planEdit.remove(CURRENT_PLAN).remove(CURRENT_SONG).remove(CURRENT_STATE).remove(CURRENT_PROGRESS)
                .putBoolean(MIGRATED_V2,true).apply();
    }

    public synchronized String create(String name, List<Song> input) throws Exception {
        LinkedHashMap<String,Song> unique = new LinkedHashMap<>();
        if (input != null) for (Song s : input) if (s != null) unique.putIfAbsent(s.key(), s);
        if (unique.isEmpty()) return "";
        String id = Long.toHexString(System.currentTimeMillis()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        JSONArray songs = new JSONArray();
        JSONArray preexisting = new JSONArray();
        for (Song s : unique.values()) {
            songs.put(s.toJson());
            if (offline.contains(s)) preexisting.put(hash(s.key()));
        }
        JSONObject root = new JSONObject();
        root.put("id", id); root.put("name", cleanName(name)); root.put("createdAt", System.currentTimeMillis());
        root.put("songs", songs); root.put("preexisting", preexisting);
        writeAtomic(file(id), root.toString());

        JSONArray index = index(); index.put(id);
        long now = root.optLong("createdAt", System.currentTimeMillis());
        prefs.edit().putString(INDEX, index.toString())
                .putString("name."+id, cleanName(name)).putLong("created."+id, now).putInt("total."+id, songs.length())
                .putInt("cursor."+id, 0).putBoolean("paused."+id, false).putBoolean("cancelled."+id, false)
                .putBoolean("exhausted."+id, false).apply();
        SharedPreferences.Editor editor = states.edit();
        for (Song s : unique.values()) if (!offline.contains(s)) editor.putString(stateKey(id,s), "等待下载");
        editor.apply();
        return id;
    }

    public synchronized List<Plan> plans() {
        ArrayList<Plan> out = plansRaw();
        out.sort(Comparator.comparingLong((Plan p)->p.createdAt));
        return out;
    }

    private ArrayList<Plan> plansRaw() {
        ArrayList<Plan> out = new ArrayList<>();
        JSONArray a = index();
        for (int i=0;i<a.length();i++) {
            String id=a.optString(i,""); if(id.isEmpty()||!file(id).isFile()) continue;
            out.add(plan(id));
        }
        return out;
    }

    public synchronized Plan plan(String id) {
        String key=id==null?"":id;
        return new Plan(key, prefs.getString("name."+key,"下载任务"), prefs.getLong("created."+key,0L),
                prefs.getInt("total."+key,0), prefs.getInt("cursor."+key,0), prefs.getBoolean("paused."+key,false),
                prefs.getBoolean("cancelled."+key,false), prefs.getBoolean("exhausted."+key,false));
    }

    public synchronized Claim nextClaim() {
        if (globalPaused()) return null;
        for (Plan original : plans()) {
            if (original.paused || original.cancelled || original.exhausted) continue;
            List<Song> songs = readSongs(original.id);
            int cursor = Math.max(0, original.cursor);
            while (true) {
                int scanStart=cursor;
                while (cursor < songs.size()) {
                    Song song=songs.get(cursor);
                    if (offline.contains(song)) { cursor++; continue; }
                    String st=songState(original.id,song);
                    if (st.startsWith("已取消") || st.startsWith("失败")) { cursor++; continue; }
                    if (isRetryState(st) && retryCount(original.id,song) >= MAX_AUTO_RETRY_FAILURES) {
                        setSongState(original.id,song,"失败 · 自动重试次数已用完"); cursor++; continue;
                    }
                    if(cursor!=scanStart)setCursor(original.id,cursor);
                    return new Claim(plan(original.id), cursor, song);
                }
                if(cursor!=scanStart)setCursor(original.id,cursor);
                int retry=firstAutoRetryIndex(original.id);
                if (retry >= 0) {
                    prefs.edit().putInt("cursor."+original.id,retry).putBoolean("exhausted."+original.id,false).apply();
                    cursor=retry;
                    continue;
                }
                markExhausted(original.id);
                break;
            }
        }
        return null;
    }

    public synchronized void finishClaim(Claim claim) {
        if (claim == null) return;
        int current = prefs.getInt("cursor."+claim.plan.id,0);
        if (current <= claim.index) prefs.edit().putInt("cursor."+claim.plan.id, claim.index + 1).apply();
    }

    public synchronized void markExhausted(String id) { prefs.edit().putBoolean("exhausted."+id,true).apply(); }
    public synchronized void pausePlan(String id) { prefs.edit().putBoolean("paused."+id,true).apply(); }
    public synchronized void resumePlan(String id) {
        Plan p=plan(id); SharedPreferences.Editor e=prefs.edit().putBoolean("paused."+id,false).putBoolean("cancelled."+id,false);
        if (p.exhausted) {
            int first=firstUnresolvedIndex(id, true);
            if (first >= 0) e.putInt("cursor."+id,first).putBoolean("exhausted."+id,false);
        }
        e.apply();
    }
    public synchronized void cancelPlan(String id) {
        prefs.edit().putBoolean("cancelled."+id,true).putBoolean("paused."+id,false).apply();
        SharedPreferences.Editor e=states.edit();
        for(Song s:readSongs(id)) if(!offline.contains(s)) e.putString(stateKey(id,s),"已取消");
        e.apply();
    }
    public synchronized void cancelSongAcrossActivePlans(String songKey) {
        if(songKey==null||songKey.isEmpty())return;
        SharedPreferences.Editor e=states.edit();
        for(Plan p:plans()){
            if(p.cancelled)continue;
            for(Song s:readSongs(p.id)) if(songKey.equals(s.key())&&!offline.contains(s)) e.putString(stateKey(p.id,s),"已取消");
        }
        e.apply();
    }

    public synchronized boolean isPlanCancelled(String id) { return prefs.getBoolean("cancelled."+id,false); }
    public synchronized boolean isPlanPaused(String id) { return prefs.getBoolean("paused."+id,false); }
    public boolean globalPaused() { return prefs.getBoolean(GLOBAL_PAUSED,false); }
    public void pauseAll() { prefs.edit().putBoolean(GLOBAL_PAUSED,true).apply(); }
    public void resumeAll() { prefs.edit().putBoolean(GLOBAL_PAUSED,false).apply(); }

    public synchronized int retryFailed(String id) {
        List<Song> songs=readSongs(id); int first=-1,count=0;
        SharedPreferences.Editor e=states.edit();
        for(int i=0;i<songs.size();i++){
            Song s=songs.get(i); if(offline.contains(s)) continue;
            String st=songState(id,s);
            if(st.startsWith("失败")||st.startsWith("待重试")||st.startsWith("等待可用线路")){
                e.putString(stateKey(id,s),"等待下载").remove(retryKey(id,s)).remove(attemptKey(id,s)); if(first<0)first=i; count++;
            }
        }
        e.apply();
        if(first>=0) prefs.edit().putInt("cursor."+id,first).putBoolean("exhausted."+id,false)
                .putBoolean("cancelled."+id,false).putBoolean("paused."+id,false).apply();
        return count;
    }

    public synchronized boolean deletePlanNow(String id) {
        if(id==null||id.isEmpty())return false;
        JSONArray old=index(),next=new JSONArray();
        for(int i=0;i<old.length();i++){String x=old.optString(i,"");if(!id.equals(x)&&!x.isEmpty())next.put(x);}
        SharedPreferences.Editor pe=prefs.edit().putString(INDEX,next.toString())
                .remove("name."+id).remove("created."+id).remove("total."+id).remove("cursor."+id)
                .remove("paused."+id).remove("cancelled."+id).remove("exhausted."+id);
        if(id.equals(prefs.getString(CURRENT_PLAN,""))) pe.remove(CURRENT_PLAN).remove(CURRENT_SONG).remove(CURRENT_STATE).remove(CURRENT_PROGRESS);
        pe.apply();
        clearPlanStates(id);
        File f=file(id); return !f.exists()||f.delete();
    }

    public synchronized int clearHistory() {
        ArrayList<String> ids=new ArrayList<>();
        for(Summary s:summaries()) if(s.cancelled || (s.exhausted && s.active==0)) ids.add(s.id);
        int n=0;for(String id:ids)if(deletePlanNow(id))n++;return n;
    }

    public synchronized int markRetryableFailure(String planId,Song song,String state){
        if(planId==null||song==null)return 0;int n=retryCount(planId,song)+1;
        states.edit().putInt(retryKey(planId,song),n).putString(stateKey(planId,song),safe(state)).apply();return n;
    }
    public synchronized int retryCount(String planId,Song song){return states.getInt(retryKey(planId,song),0);}
    public synchronized void clearRetryCount(String planId,Song song){if(planId!=null&&song!=null)states.edit().remove(retryKey(planId,song)).apply();}

    /** V65: routes tried by this concrete plan-song across its two automatic discovery rounds. */
    public synchronized Set<String> attemptedRoutes(String planId,Song song){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(planId==null||song==null)return out;
        try{JSONArray a=new JSONArray(states.getString(attemptKey(planId,song),"[]"));for(int i=0;i<a.length();i++){String x=a.optString(i,"");if(!x.isEmpty())out.add(x);}}catch(Exception ignored){}
        return out;
    }
    public synchronized void recordAttemptedRoute(String planId,Song song,String routeKey){
        if(planId==null||song==null||routeKey==null||routeKey.isEmpty())return;
        Set<String> set=attemptedRoutes(planId,song);if(!set.add(routeKey))return;JSONArray a=new JSONArray();for(String x:set)a.put(x);
        states.edit().putString(attemptKey(planId,song),a.toString()).apply();
    }

    /** V65: only the exact stale READY URL is ignored; a freshly learned playback URL is eligible again. */
    public synchronized Set<String> staleReadyUrls(String planId,Song song){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(planId==null||song==null)return out;
        try{JSONArray a=new JSONArray(states.getString(staleUrlKey(planId,song),"[]"));for(int i=0;i<a.length();i++){String x=a.optString(i,"");if(!x.isEmpty())out.add(x);}}catch(Exception ignored){}
        return out;
    }
    public synchronized void recordStaleReadyUrl(String planId,Song song,String urlFingerprint){
        if(planId==null||song==null||urlFingerprint==null||urlFingerprint.isEmpty())return;
        Set<String> set=staleReadyUrls(planId,song);if(!set.add(urlFingerprint))return;JSONArray a=new JSONArray();for(String x:set)a.put(x);
        states.edit().putString(staleUrlKey(planId,song),a.toString()).apply();
    }

    public synchronized void setCurrent(String planId, Song song, String state, int progress) {
        prefs.edit().putString(CURRENT_PLAN, safe(planId)).putString(CURRENT_SONG, song == null ? "" : song.toJson().toString())
                .putString(CURRENT_STATE, safe(state)).putInt(CURRENT_PROGRESS, Math.max(0,Math.min(100,progress))).apply();
    }
    public synchronized void updateCurrentState(String state, int progress) {
        prefs.edit().putString(CURRENT_STATE,safe(state)).putInt(CURRENT_PROGRESS,Math.max(0,Math.min(100,progress))).apply();
    }
    public synchronized void clearCurrent() { prefs.edit().remove(CURRENT_PLAN).remove(CURRENT_SONG).remove(CURRENT_STATE).remove(CURRENT_PROGRESS).apply(); }

    public synchronized String songState(String planId,Song song){
        if(planId==null||song==null)return "";
        return states.getString(stateKey(planId,song),"");
    }
    public synchronized void setSongState(String planId,Song song,String state){
        if(planId==null||planId.isEmpty()||song==null)return;
        states.edit().putString(stateKey(planId,song),safe(state)).apply();
    }
    public synchronized void clearSongState(String planId,Song song){
        if(planId==null||song==null)return;states.edit().remove(stateKey(planId,song)).remove(retryKey(planId,song)).remove(attemptKey(planId,song)).remove(staleUrlKey(planId,song)).apply();
    }

    /** Best-effort UI state for a song across live plans; cancelled history is intentionally ignored. */
    public synchronized String activeStateForSong(Song song){
        if(song==null||offline.contains(song))return "";
        List<Plan> ps=plans();
        for(int i=ps.size()-1;i>=0;i--){
            Plan p=ps.get(i);if(p.cancelled)continue;
            if(!containsSong(p.id,song.key()))continue;
            String st=songState(p.id,song);
            if(!st.isEmpty()&&!st.startsWith("失败")&&!st.startsWith("已取消")&&!st.startsWith("待重试"))return st;
        }
        return "";
    }

    public synchronized List<Summary> summaries() {
        ArrayList<Summary> out = new ArrayList<>();
        String currentPlanId=prefs.getString(CURRENT_PLAN,"");
        Song current=null; try { String raw=prefs.getString(CURRENT_SONG,""); if(raw!=null&&!raw.isEmpty()) current=Song.fromJson(new JSONObject(raw)); } catch(Exception ignored) { }
        String currentState=prefs.getString(CURRENT_STATE,""); int currentProgress=prefs.getInt(CURRENT_PROGRESS,0);
        boolean allPaused=globalPaused();
        for(Plan p:plans()){
            List<Song> songs=readSongs(p.id); Set<String> preexisting=preexisting(p.id);
            int downloaded=0,already=0,waiting=0,failed=0,pending=0,active=0;
            for(Song s:songs){
                String h=hash(s.key());
                if(offline.contains(s)){ if(preexisting.contains(h))already++; else downloaded++; continue; }
                String state=songState(p.id,s);
                if(state.startsWith("等待可用线路")||state.startsWith("待重试"))waiting++;
                else if(state.startsWith("失败"))failed++;
                else if(state.startsWith("下载")||state.startsWith("解析")||state.startsWith("主动")||state.startsWith("排队")||state.startsWith("等待播放")||state.startsWith("下载调度")||state.startsWith("已暂停")||state.startsWith("准备下载"))active++;
                else if(state.startsWith("已取消")) { /* plan history; do not mislabel as pending */ }
                else pending++;
            }
            boolean isCurrent=p.id.equals(currentPlanId);
            boolean paused=allPaused||p.paused;
            String status;
            if(p.cancelled)status="已取消";
            else if(paused)status="已暂停";
            else if(isCurrent)status="正在推进";
            else if(p.exhausted&&waiting>0)status="等待重试";
            else if(p.exhausted&&failed>0)status="本轮完成 · 有失败";
            else if(p.exhausted)status="已完成";
            else status="等待下载";
            out.add(new Summary(p.id,p.name,status,isCurrent&&current!=null?current.title+" · "+current.artist:"",
                    isCurrent?currentState:"",p.createdAt,songs.size(),downloaded,already,waiting,failed,pending,active,p.cursor,
                    isCurrent?currentProgress:0,paused,p.cancelled,p.exhausted));
        }
        out.sort(Comparator.comparingLong((Summary s)->s.createdAt).reversed());
        return out;
    }

    public synchronized List<Item> details(String id) {
        ArrayList<Item> out=new ArrayList<>();
        List<Song> songs=readSongs(id); Set<String> preexisting=preexisting(id);
        String currentPlanId=prefs.getString(CURRENT_PLAN,"");
        Song current=null;try{String raw=prefs.getString(CURRENT_SONG,"");if(raw!=null&&!raw.isEmpty())current=Song.fromJson(new JSONObject(raw));}catch(Exception ignored){}
        String currentState=prefs.getString(CURRENT_STATE,"");int currentProgress=prefs.getInt(CURRENT_PROGRESS,0);
        for(int i=0;i<songs.size();i++){
            Song s=songs.get(i);String h=hash(s.key());boolean isOffline=offline.contains(s);boolean pre=preexisting.contains(h);
            boolean cur=id.equals(currentPlanId)&&current!=null&&current.key().equals(s.key());
            String st;String cat;int progress=0;
            if(isOffline){st=pre?"已存在":"已下载";cat=CAT_DONE;progress=100;}
            else if(cur){st=currentState.isEmpty()?"处理中":currentState;cat=CAT_ACTIVE;progress=currentProgress;}
            else{
                st=songState(id,s);if(st.isEmpty())st="等待下载";progress=extractPercent(st);
                if(st.startsWith("失败"))cat=CAT_FAILED;
                else if(st.startsWith("等待可用线路")||st.startsWith("待重试")||st.startsWith("已暂停"))cat=CAT_WAITING;
                else if(st.startsWith("下载")||st.startsWith("解析")||st.startsWith("主动")||st.startsWith("排队")||st.startsWith("等待播放")||st.startsWith("下载调度")||st.startsWith("准备下载"))cat=CAT_ACTIVE;
                else if(st.startsWith("已取消"))cat=CAT_CANCELLED;
                else cat=CAT_PENDING;
            }
            out.add(new Item(i,s,st,cat,progress,isOffline,pre,cur));
        }
        return out;
    }

    public synchronized List<Song> readSongs(String id) { return readSongsRaw(id); }
    private ArrayList<Song> readSongsRaw(String id) {
        ArrayList<Song> out=new ArrayList<>();
        try{
            String raw=read(file(id)); if(raw.isEmpty())return out;
            JSONArray a=new JSONObject(raw).optJSONArray("songs"); if(a==null)return out;
            for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(Song.fromJson(o));}
        }catch(Exception ignored){}
        return out;
    }

    private synchronized Set<String> preexisting(String id){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        try{JSONArray a=new JSONObject(read(file(id))).optJSONArray("preexisting");if(a!=null)for(int i=0;i<a.length();i++){String s=a.optString(i,"");if(!s.isEmpty())out.add(s);}}catch(Exception ignored){}
        return out;
    }

    private int firstAutoRetryIndex(String id){
        List<Song> songs=readSongs(id);
        for(int i=0;i<songs.size();i++){Song s=songs.get(i);if(offline.contains(s))continue;String st=songState(id,s);if(isRetryState(st)&&retryCount(id,s)<MAX_AUTO_RETRY_FAILURES)return i;}
        return -1;
    }
    private static boolean isRetryState(String st){return st!=null&&(st.startsWith("待重试")||st.startsWith("等待可用线路"));}
    private void setCursor(String id,int cursor){prefs.edit().putInt("cursor."+id,Math.max(0,cursor)).apply();}

    private int firstUnresolvedIndex(String id,boolean includeFailed){
        List<Song> songs=readSongs(id);
        for(int i=0;i<songs.size();i++){
            Song s=songs.get(i);if(offline.contains(s))continue;
            String st=songState(id,s);
            if(!includeFailed&&st.startsWith("失败"))continue;
            return i;
        }
        return -1;
    }
    private boolean containsSong(String id,String key){for(Song s:readSongs(id))if(s.key().equals(key))return true;return false;}
    private void clearPlanStates(String id){
        String prefix="p."+id+".s.",retryPrefix="r."+id+".s.",attemptPrefix="a."+id+".s.",stalePrefix="u."+id+".s.";SharedPreferences.Editor e=states.edit();
        for(String key:states.getAll().keySet())if(key.startsWith(prefix)||key.startsWith(retryPrefix)||key.startsWith(attemptPrefix)||key.startsWith(stalePrefix))e.remove(key);e.apply();
    }
    private static String stateKey(String planId,Song song){return "p."+safe(planId)+".s."+hash(song==null?"":song.key());}
    private static String retryKey(String planId,Song song){return "r."+safe(planId)+".s."+hash(song==null?"":song.key());}
    private static String attemptKey(String planId,Song song){return "a."+safe(planId)+".s."+hash(song==null?"":song.key());}
    private static String staleUrlKey(String planId,Song song){return "u."+safe(planId)+".s."+hash(song==null?"":song.key());}
    private JSONArray index(){try{return new JSONArray(prefs.getString(INDEX,"[]"));}catch(Exception e){return new JSONArray();}}
    private File file(String id){return new File(dir,"plan-"+id.replaceAll("[^a-zA-Z0-9._-]","")+".json");}
    private static void writeAtomic(File file,String value)throws Exception{File tmp=new File(file.getParentFile(),file.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(value.getBytes(StandardCharsets.UTF_8));out.flush();}if(file.exists())file.delete();if(!tmp.renameTo(file))throw new Exception("无法保存下载计划");}
    private static String read(File file)throws Exception{if(file==null||!file.isFile())return "";try(FileInputStream in=new FileInputStream(file)){byte[]buf=new byte[(int)Math.min(file.length(),8L*1024L*1024L)];int off=0,n;while(off<buf.length&&(n=in.read(buf,off,buf.length-off))>0)off+=n;return new String(buf,0,off,StandardCharsets.UTF_8);}}
    private static String cleanName(String s){String v=s==null?"":s.trim();return v.isEmpty()?"下载任务":v;}
    private static String safe(String s){return s==null?"":s;}
    private static int extractPercent(String state){try{int i=state.indexOf('·'),j=state.indexOf('%');if(i>=0&&j>i)return Integer.parseInt(state.substring(i+1,j).trim());}catch(Exception ignored){}return 0;}
    public static String hash(String v){try{byte[]d=MessageDigest.getInstance("SHA-256").digest((v==null?"":v).getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return b.substring(0,24);}catch(Exception e){return Integer.toHexString((v==null?"":v).hashCode());}}
}
