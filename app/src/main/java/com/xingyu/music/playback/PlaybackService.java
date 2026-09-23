package com.xingyu.music.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.net.Uri;
import android.provider.Settings;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

import com.xingyu.music.MainActivity;
import com.xingyu.music.R;
import com.xingyu.music.data.PlaybackTrace;
import com.xingyu.music.data.PlaybackNetworkGate;
import com.xingyu.music.data.PlaybackSessionStore;
import com.xingyu.music.data.SourceCoordinator;
import com.xingyu.music.desktop.DesktopLyricService;
import com.xingyu.music.download.OfflineStore;
import com.xingyu.music.download.DownloadRouteStore;
import com.xingyu.music.model.PlaybackSnapshot;
import com.xingyu.music.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sunflower queue-aware Media3 playback service, preserving the proven Lunaxy V47 player state machine.
 *
 * Resolver URLs are only candidates: duplicate resolves are coalesced, stale READY-cache URLs refresh
 * once, large duration mismatches are rejected, and only Media3 STATE_READY is allowed to promote a
 * provider/candidate into the persistent success path.
 */
@UnstableApi
public final class PlaybackService extends Service {
    public static final String ACTION_TOGGLE = "com.xingyu.music.TOGGLE";
    public static final String ACTION_NEXT = "com.xingyu.music.NEXT";
    public static final String ACTION_PREV = "com.xingyu.music.PREV";
    public static final String ACTION_STOP = "com.xingyu.music.STOP";
    public static final String ACTION_REFRESH_SURFACE = "com.xingyu.music.REFRESH_MEDIA_SURFACE";
    public static final String ACTION_SLEEP_TIMER = "com.xingyu.music.SLEEP_TIMER";
    private static final String CUSTOM_DESKTOP_LYRIC = "lunaxy.desktop.lyric";
    private static final String CUSTOM_DESKTOP_LYRIC_CLOSE = "lunaxy.desktop.lyric.close";
    private static final int NOTIF_ID = 1007;
    private static final String CHANNEL = "xingyu_playback";
    private static final String SLEEP_TIMER_PREFS = "lunaxy_sleep_timer_v1";
    private static final String SLEEP_TIMER_DEADLINE = "deadline_at";
    private static final long SESSION_CHECKPOINT_INTERVAL_MS = 5_000L;

    public static final int MODE_SEQUENTIAL = 0;
    public static final int MODE_SHUFFLE = 1;
    public static final int MODE_REPEAT_ONE = 2;

    public interface Listener { void onPlaybackChanged(PlaybackSnapshot snapshot); }
    public final class LocalBinder extends Binder { public PlaybackService getService(){ return PlaybackService.this; } }

    private final IBinder binder = new LocalBinder();
    private final List<Song> queue = new ArrayList<>();
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final PlaybackTrace trace = new PlaybackTrace();
    private final BeatAudioProcessor beatAudioProcessor = new BeatAudioProcessor();

    private Listener listener;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private SourceCoordinator sourceCoordinator;
    private OfflineStore offlineStore;
    private PlaybackSessionStore sessionStore;
    private int index = -1;
    private String lastError;
    private boolean preparing;
    private final Set<String> failedRoutes = new LinkedHashSet<>();
    private boolean cacheRefreshUsed;
    private boolean routeProven;
    private long candidateStartedAt;
    private SourceCoordinator.Resolution currentResolution;
    private boolean currentOffline;
    private boolean offlineBypassForCurrent;
    private Bitmap currentArtwork;
    private long requestGeneration;
    private android.os.PowerManager.WakeLock transitionWakeLock;
    private boolean playbackRequested;
    private Runnable failureSkip;
    private long failureSkipGeneration=-1;
    private int consecutiveFailures;
    private boolean serviceStarted, destroyed;

    private void holdTransitionWakeLock() {
        if(transitionWakeLock==null) {
            android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
            transitionWakeLock=power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"Lunaxy:track-transition");
            transitionWakeLock.setReferenceCounted(false);
        }
        transitionWakeLock.acquire(60000L);
    }
    private void releaseTransitionWakeLock() {
        if(transitionWakeLock!=null && transitionWakeLock.isHeld()) transitionWakeLock.release();
    }
    private void cancelFailureSkip() {
        if(failureSkip!=null) main.removeCallbacks(failureSkip);
        failureSkip=null; failureSkipGeneration=-1;
    }
    private void scheduleFailureSkip() {
        if(!playbackRequested || preparing || lastError==null || lastError.trim().isEmpty() || queue.size()<2) return;
        if(failureSkipGeneration==requestGeneration) return;
        cancelFailureSkip();
        final long generation=requestGeneration;
        failureSkipGeneration=generation;
        failureSkip=()->{
            failureSkip=null;
            if(generation!=requestGeneration || !playbackRequested || preparing || lastError==null) return;
            if(++consecutiveFailures>=queue.size()) { playbackRequested=false; releaseTransitionWakeLock(); return; }
            trace.add("auto-skip","service-owned failed track recovery");
            nextInternal(false);
        };
        holdTransitionWakeLock();
        main.postDelayed(failureSkip,3000);
    }
    private int playMode = MODE_SEQUENTIAL;
    private long lastDownloadGateHeartbeatAt;
    private final Random shuffleRandom = new Random();
    private long sleepTimerDeadlineAt;
    private String restoredSongKey = "";
    private long restoredPositionMs;
    private long restoredDurationMs;
    private long lastSessionCheckpointAt;

    private final Runnable sleepTimerRunnable = new Runnable() {
        @Override public void run() {
            long remaining = sleepTimerDeadlineAt - System.currentTimeMillis();
            if (sleepTimerDeadlineAt <= 0L) return;
            if (remaining > 750L) {
                main.postDelayed(this, remaining);
                return;
            }
            clearSleepTimerState();
            pause();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (player != null && player.isPlaying() && !currentOffline && now - lastDownloadGateHeartbeatAt > 10_000L) {
                PlaybackNetworkGate.heartbeat(PlaybackService.this, "在线播放中");
                lastDownloadGateHeartbeatAt = now;
            }
            maybeCheckpointSession();
            notifyState(); main.postDelayed(this, 500);
        }
    };

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) pause();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        sourceCoordinator = new SourceCoordinator(this);
        offlineStore = new OfflineStore(this);
        sessionStore = new PlaybackSessionStore(this);
        restorePlaybackSession();
        playMode = getSharedPreferences("xingyu_playback_state", MODE_PRIVATE)
                .getInt("play_mode", MODE_SEQUENTIAL);
        if (playMode < MODE_SEQUENTIAL || playMode > MODE_REPEAT_ONE) playMode = MODE_SEQUENTIAL;

        androidx.media3.common.AudioAttributes attrs = new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        // V15 visualizer taps Media3's already-decoded PCM with a pass-through
        // AudioProcessor. It does not use the microphone and does not alter audio.
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this) {
            @Override
            protected AudioSink buildAudioSink(Context context,
                                               boolean enableFloatOutput,
                                               boolean enableAudioOutputPlaybackParams) {
                return new DefaultAudioSink.Builder(context)
                        .setAudioProcessors(new AudioProcessor[]{beatAudioProcessor})
                        // Keep PCM in a processor-friendly representation so the
                        // visualizer receives the same stream Media3 is rendering.
                        .setEnableFloatOutput(false)
                        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                        .build();
            }
        };
        player = new ExoPlayer.Builder(this, renderersFactory).build();
        player.setWakeMode(C.WAKE_MODE_LOCAL);
        player.setAudioAttributes(attrs, true);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) {
                preparing = playbackState == Player.STATE_BUFFERING;
                if (playbackState == Player.STATE_BUFFERING) PlaybackNetworkGate.heartbeat(PlaybackService.this, "Media3 BUFFERING");
                else if (playbackState == Player.STATE_READY) {
                    if (player != null && player.getPlayWhenReady() && !currentOffline) PlaybackNetworkGate.setBusy(PlaybackService.this, true, "在线播放中");
                    else PlaybackNetworkGate.setBusy(PlaybackService.this, false, "");
                } else if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) PlaybackNetworkGate.setBusy(PlaybackService.this, false, "");
                if (playbackState == Player.STATE_READY) {
                    consecutiveFailures=0;
                    cancelFailureSkip();
                    releaseTransitionWakeLock();
                    lastError = null;
                    if (currentOffline) trace.add("playback-ok", "OFFLINE · app-private file · Media3 READY");
                    if (!routeProven && currentResolution != null) {
                        Song target = currentSong();
                        long actualDuration = player == null ? C.TIME_UNSET : player.getDuration();
                        if (!durationMatches(target, actualDuration)) {
                            long expected = target == null ? 0L : target.durationMs;
                            trace.add("candidate-mismatch", currentResolution.resolverLabel
                                    + " / expected=" + expected + "ms / actual=" + actualDuration
                                    + "ms · READY 但不是可靠的目标录音");
                            if (player != null) player.pause();
                            handlePlayerFailure(new IllegalStateException(
                                    "候选音频时长与目标歌曲不匹配：expected=" + expected + "ms actual=" + actualDuration + "ms"));
                            return;
                        }
                        long readyMs = Math.max(1, System.currentTimeMillis() - candidateStartedAt);
                        sourceCoordinator.markPlaybackSuccess(currentResolution, readyMs);
                        trace.add("playback-ok", currentResolution.resolverLabel + " / "
                                + currentResolution.providerType + " / "
                                + Song.providerLabel(currentResolution.variant.source) + "/" + currentResolution.quality
                                + " / backend=" + currentResolution.backendGroup
                                + " / confidence " + Math.round(currentResolution.matchConfidence * 100d) + "%"
                                + " / Media3 READY / " + readyMs + "ms");
                        routeProven = true;
                    }
                    completeRestoredPositionIfReady();
                    persistSessionState(false);
                    trace.add("media3", "READY");
                    if (player.getPlayWhenReady()) updateNotification(true);
                } else if (playbackState == Player.STATE_ENDED) {
                    trace.add("media3", "ENDED");
                    if(playbackRequested) nextInternal(true);
                }
                updateMediaSession();
                notifyState();
            }

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying && !currentOffline) PlaybackNetworkGate.setBusy(PlaybackService.this, true, "在线播放中");
                else if (!preparing) PlaybackNetworkGate.setBusy(PlaybackService.this, false, "");
                updateMediaSession();
                updateNotification(isPlaying);
                notifyState();
            }

            @Override public void onPlayerError(PlaybackException error) {
                trace.add("media3-error", error.getErrorCodeName() + " / " + safeMessage(error));
                handlePlayerFailure(error);
            }
        });

        mediaSession = new MediaSession(this, "LunaxyMusicSessionV49");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onSeekTo(long pos) { seekTo(pos); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onCustomAction(String action, Bundle extras) {
                if (CUSTOM_DESKTOP_LYRIC.equals(action) && Settings.canDrawOverlays(PlaybackService.this)) {
                    DesktopLyricService.toggleFromSystemSurface(PlaybackService.this);
                    main.postDelayed(PlaybackService.this::refreshSystemSurface, 180L);
                } else if (CUSTOM_DESKTOP_LYRIC_CLOSE.equals(action)) {
                    DesktopLyricService.stop(PlaybackService.this);
                    main.postDelayed(PlaybackService.this::refreshSystemSurface, 180L);
                }
            }
        });
        // Keep the framework MediaSession explicit for older/vendor SystemUI implementations.
        // These flags/activity/audio attributes only describe the media surface; ExoPlayer routing
        // and the proven READY/fallback state machine remain unchanged.
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        PendingIntent sessionActivity = PendingIntent.getActivity(this, 10,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mediaSession.setSessionActivity(sessionActivity);
        mediaSession.setPlaybackToLocal(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mediaSession.setActive(true);

        IntentFilter noisy = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(noisyReceiver, noisy, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisyReceiver, noisy);
        restoreSleepTimer();
        main.post(ticker);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        serviceStarted=true;
        String action = intent == null ? null : intent.getAction();
        if (action == null) ensureForeground();
        else if (ACTION_TOGGLE.equals(action)) toggle();
        else if (ACTION_NEXT.equals(action)) next();
        else if (ACTION_PREV.equals(action)) previous();
        else if (ACTION_STOP.equals(action)) stopPlayback();
        else if (ACTION_REFRESH_SURFACE.equals(action)) updateNotification(player != null && player.isPlaying());
        else if (ACTION_SLEEP_TIMER.equals(action)) handleSleepTimerAlarm();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    public void setListener(Listener l) { listener = l; notifyState(); }
    public void clearListener(Listener l) { if (listener == l) listener = null; }

    public void playQueue(List<Song> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) return;
        queue.clear();
        queue.addAll(songs);
        index = Math.max(0, Math.min(startIndex, queue.size() - 1));
        resetAttempts();
        currentArtwork = null;
        persistSessionState(false);
        ensureForeground();
        resolveCurrent();
    }


    public List<Song> queueSnapshot() { return new ArrayList<>(queue); }
    public int currentQueueIndex() { return index; }
    public int getPlayMode() { return playMode; }

    public int cyclePlayMode() {
        playMode = (playMode + 1) % 3;
        getSharedPreferences("xingyu_playback_state", MODE_PRIVATE).edit().putInt("play_mode", playMode).apply();
        notifyState();
        return playMode;
    }

    public void setPlayMode(int mode) {
        if (mode < MODE_SEQUENTIAL || mode > MODE_REPEAT_ONE) mode = MODE_SEQUENTIAL;
        playMode = mode;
        getSharedPreferences("xingyu_playback_state", MODE_PRIVATE).edit().putInt("play_mode", playMode).apply();
        notifyState();
    }

    /** V70+ wall-clock sleep timer. It only pauses playback and preserves queue/current position. */
    public void setSleepTimerMinutes(int minutes) {
        setSleepTimerDurationMs(minutes <= 0 ? 0L : Math.max(1, minutes) * 60_000L);
    }

    /** V72 exact-duration entry point used by the hour/minute/second sleep-timer UI. */
    public void setSleepTimerDurationMs(long durationMs) {
        if (durationMs <= 0L) {
            cancelSleepTimer();
            return;
        }
        long now = System.currentTimeMillis();
        long safeDuration = Math.max(1_000L, durationMs);
        sleepTimerDeadlineAt = safeDuration > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + safeDuration;
        getSharedPreferences(SLEEP_TIMER_PREFS, MODE_PRIVATE).edit()
                .putLong(SLEEP_TIMER_DEADLINE, sleepTimerDeadlineAt).apply();
        scheduleSleepTimer();
    }

    public void cancelSleepTimer() {
        clearSleepTimerState();
    }

    public long sleepTimerRemainingMs() {
        if (sleepTimerDeadlineAt <= 0L) return 0L;
        return Math.max(0L, sleepTimerDeadlineAt - System.currentTimeMillis());
    }

    public boolean sleepTimerActive() { return sleepTimerRemainingMs() > 0L; }

    private void restoreSleepTimer() {
        long saved = getSharedPreferences(SLEEP_TIMER_PREFS, MODE_PRIVATE)
                .getLong(SLEEP_TIMER_DEADLINE, 0L);
        if (saved <= System.currentTimeMillis()) {
            clearSleepTimerState();
            return;
        }
        sleepTimerDeadlineAt = saved;
        scheduleSleepTimer();
    }

    private void scheduleSleepTimer() {
        main.removeCallbacks(sleepTimerRunnable);
        long remaining = sleepTimerRemainingMs();
        if (remaining <= 0L) {
            clearSleepTimerState();
            return;
        }
        // Main-thread callback is the zero-overhead fast path while the media process is awake.
        main.postDelayed(sleepTimerRunnable, remaining);

        // Audio playback can let the CPU sleep on some devices. Keep an AlarmManager wake-up as
        // a screen-off/vendor-safe backup. Exact alarms are used only when Android already grants
        // that capability; otherwise setAndAllowWhileIdle is sufficient for a sleep timer.
        try {
            android.app.AlarmManager alarm = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm != null) {
                PendingIntent pi = servicePi(ACTION_SLEEP_TIMER, 70);
                if (android.os.Build.VERSION.SDK_INT >= 31 && alarm.canScheduleExactAlarms())
                    alarm.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, sleepTimerDeadlineAt, pi);
                else if (android.os.Build.VERSION.SDK_INT >= 23)
                    alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, sleepTimerDeadlineAt, pi);
                else
                    alarm.set(android.app.AlarmManager.RTC_WAKEUP, sleepTimerDeadlineAt, pi);
            }
        } catch (Exception ignored) { }
    }

    private void handleSleepTimerAlarm() {
        long remaining = sleepTimerRemainingMs();
        if (remaining > 750L) {
            scheduleSleepTimer();
            return;
        }
        if (sleepTimerDeadlineAt <= 0L) return;
        clearSleepTimerState();
        pause();
    }

    private void clearSleepTimerState() {
        main.removeCallbacks(sleepTimerRunnable);
        try {
            android.app.AlarmManager alarm = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm != null) alarm.cancel(servicePi(ACTION_SLEEP_TIMER, 70));
        } catch (Exception ignored) { }
        sleepTimerDeadlineAt = 0L;
        getSharedPreferences(SLEEP_TIMER_PREFS, MODE_PRIVATE).edit()
                .remove(SLEEP_TIMER_DEADLINE).apply();
    }

    /**
     * Play a picked song without replacing the user's existing queue.
     * The selected track is inserted immediately after the current item and
     * becomes current, so the rest of the queue continues afterwards.
     */
    public void insertAndPlay(Song song) {
        if (song == null) return;
        if (queue.isEmpty()) {
            List<Song> one = new ArrayList<>(); one.add(song); playQueue(one, 0); return;
        }
        int existing = findQueueSong(song);
        if (existing == index) { resume(); return; }
        if (existing >= 0) {
            queue.remove(existing);
            if (existing < index) index--;
        }
        int at = Math.min(queue.size(), Math.max(0, index + 1));
        queue.add(at, song);
        index = at;
        resetAttempts();
        currentArtwork = null;
        persistSessionState(false);
        ensureForeground();
        resolveCurrent();
    }

    public void removeAt(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= queue.size()) return;
        boolean removingCurrent = targetIndex == index;
        queue.remove(targetIndex);
        if (queue.isEmpty()) { stopPlayback(true); return; }
        if (targetIndex < index) index--;
        if (removingCurrent) {
            index = Math.min(targetIndex, queue.size() - 1);
            resetAttempts();
            currentArtwork = null;
            persistSessionState(false);
            resolveCurrent();
        } else {
            persistSessionState(false);
            notifyState();
        }
    }

    public void moveQueueItem(int from, int to) {
        if (from < 0 || from >= queue.size() || to < 0 || to >= queue.size() || from == to) return;
        Song current = currentSong();
        Song moved = queue.remove(from);
        queue.add(to, moved);
        if (current != null) {
            int newIndex = queue.indexOf(current);
            if (newIndex >= 0) index = newIndex;
        }
        persistSessionState(false);
        notifyState();
    }

    public void playAt(int targetIndex) {
        if (queue.isEmpty() || targetIndex < 0 || targetIndex >= queue.size()) return;
        if (targetIndex == index) { resume(); return; }
        index = targetIndex;
        resetAttempts();
        currentArtwork = null;
        persistSessionState(false);
        resolveCurrent();
    }

    public void enqueueNext(Song song) {
        if (song == null) return;
        if (queue.isEmpty()) {
            List<Song> one = new ArrayList<>(); one.add(song); playQueue(one, 0); return;
        }
        int existing = findQueueSong(song);
        if (existing == index) return;
        if (existing >= 0) { queue.remove(existing); if (existing < index) index--; }
        int at = Math.min(queue.size(), Math.max(0, index + 1));
        queue.add(at, song);
        persistSessionState(false);
        notifyState();
    }

    public void enqueueEnd(Song song) {
        if (song == null) return;
        if (queue.isEmpty()) {
            List<Song> one = new ArrayList<>(); one.add(song); playQueue(one, 0); return;
        }
        int existing = findQueueSong(song);
        if (existing == index) return;
        if (existing >= 0) { queue.remove(existing); if (existing < index) index--; }
        queue.add(song);
        persistSessionState(false);
        notifyState();
    }

    private int findQueueSong(Song song) {
        if (song == null) return -1;
        for (int i = 0; i < queue.size(); i++) if (queue.get(i).key().equals(song.key())) return i;
        return -1;
    }

    public void toggle() {
        if (player == null) return;
        if (player.isPlaying()) pause(); else resume();
    }

    public void pause() {
        playbackRequested=false; cancelFailureSkip(); releaseTransitionWakeLock();
        if (player != null) player.pause();
        updateMediaSession();
        updateNotification(false);
        persistSessionState(false);
        notifyState();
    }

    public void resume() {
        playbackRequested=true; consecutiveFailures=0;
        try {
            if (player != null && currentSong() != null) {
                ensureForeground();
                if(lastError!=null) { resetAttempts(); resolveCurrent(); }
                else if (player.getPlaybackState() == Player.STATE_IDLE && currentResolution == null) resolveCurrent();
                else player.play();
            }
        } catch (Exception e) { lastError = safeMessage(e); }
        updateMediaSession();
        notifyState();
    }

    public void next() { nextInternal(false); }

    /**
     * Gesture-specific previous-track action. Unlike the transport PREV button,
     * swiping right always changes track instead of restarting the current song
     * after five seconds of playback.
     */
    public void skipPreviousTrack() {
        if (queue.isEmpty()) return;
        if (playMode == MODE_SHUFFLE && queue.size() > 1) index = randomOtherIndex(index);
        else index = (index - 1 + queue.size()) % queue.size();
        resetAttempts();
        currentArtwork = null;
        persistSessionState(false);
        resolveCurrent();
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (snapshot().positionMs > 5000L) {
            seekTo(0L);
            return;
        }
        if (playMode == MODE_SHUFFLE && queue.size() > 1) index = randomOtherIndex(index);
        else index = (index - 1 + queue.size()) % queue.size();
        resetAttempts();
        currentArtwork = null;
        persistSessionState(false);
        resolveCurrent();
    }

    public void seekTo(long ms) {
        long safe = Math.max(0L, ms);
        Song current = currentSong();
        try {
            if (current != null && isRestoredPositionPendingFor(current)
                    && (player == null || player.getCurrentMediaItem() == null)) {
                restoredPositionMs = clampToKnownDuration(safe);
            } else if (player != null) {
                player.seekTo(safe);
            }
        } catch (Exception ignored) { }
        persistSessionState(false);
        notifyState();
    }

    private void nextInternal(boolean fromCompletion) {
        if (queue.isEmpty()) return;
        if (fromCompletion && playMode == MODE_REPEAT_ONE) {
            if (player != null) { player.seekTo(0); player.play(); }
            persistSessionState(false);
            return;
        }
        if (fromCompletion && playMode == MODE_SEQUENTIAL && index >= queue.size() - 1) {
            // V18: sequential means ordered continuous playback. After the final item,
            // continue from the first item while preserving the exact same resolve path.
            index = 0;
            resetAttempts();
            currentArtwork = null;
            persistSessionState(false);
            resolveCurrent();
            return;
        }
        if (queue.size() == 1) {
            if (player != null) { player.seekTo(0); player.play(); }
            persistSessionState(false);
            return;
        }
        if (playMode == MODE_SHUFFLE) index = randomOtherIndex(index);
        else index = (index + 1) % queue.size();
        resetAttempts();
        currentArtwork = null;
        persistSessionState(false);
        resolveCurrent();
    }

    private int randomOtherIndex(int current) {
        if (queue.size() <= 1) return Math.max(0, current);
        int next = current;
        for (int guard = 0; guard < 8 && next == current; guard++) next = shuffleRandom.nextInt(queue.size());
        if (next == current) next = (current + 1) % queue.size();
        return next;
    }

    private void resetAttempts() {
        playbackRequested=true; cancelFailureSkip();
        requestGeneration++;
        clearRestoredPosition();
        preparing = false;
        failedRoutes.clear();
        cacheRefreshUsed = false;
        routeProven = false;
        candidateStartedAt = 0L;
        currentResolution = null;
        currentOffline = false;
        offlineBypassForCurrent = false;
        PlaybackNetworkGate.setBusy(this, false, "");
        lastError = null;
        Song s = currentSong();
        trace.reset(s == null ? "" : s.title, s == null ? "" : s.artist);
        trace.add("runtime", sourceCoordinator == null ? "starting" : sourceCoordinator.runtimeInfo());
        if (sourceCoordinator != null) trace.add("sources", sourceCoordinator.compactSourceStatus());
        beatAudioProcessor.resetVisuals();
        if (player != null) player.stop();
    }

    // Local visualizer values exposed to MainActivity. These are intentionally
    // normalized 0..1 envelopes and contain no audio samples.
    public float visualEnergy() { return beatAudioProcessor.energy(); }
    public float visualBass() { return beatAudioProcessor.bass(); }
    public float visualTreble() { return beatAudioProcessor.treble(); }
    public float visualBeat() { return beatAudioProcessor.beat(); }

    private void resolveCurrent() {
        if(destroyed || player==null || resolverExecutor.isShutdown()) return;
        Song song = currentSong();
        if (song == null || sourceCoordinator == null) return;
        if(playbackRequested) holdTransitionWakeLock();
        if (!offlineBypassForCurrent && offlineStore != null) {
            OfflineStore.Record local = offlineStore.get(song);
            if (local != null && local.exists()) {
                prepareOffline(local);
                return;
            }
        }
        currentOffline = false;
        PlaybackNetworkGate.setBusy(this, true, "解析在线播放线路");
        final long generation = ++requestGeneration;
        final Set<String> excluded = new LinkedHashSet<>(failedRoutes);
        preparing = true;
        lastError = null;
        trace.add("resolve", "route matrix / excluded=" + excluded.size());
        notifyState();

        resolverExecutor.submit(() -> {
            try {
                SourceCoordinator.Resolution r = sourceCoordinator.resolve(song, trace, excluded);
                main.post(() -> {
                    if (generation != requestGeneration || song != currentSong()) return;
                    prepareResolved(r);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (generation != requestGeneration || song != currentSong()) return;
                    preparing = false;
                    lastError = friendlyResolveError(e);
                    trace.add("failed", lastError);
                    updateNotification(false);
                    notifyState();
                });
            }
        });
    }

    private void prepareOffline(OfflineStore.Record local) {
        if (local == null || !local.exists()) return;
        currentResolution = null;
        currentOffline = true;
        routeProven = true;
        candidateStartedAt = System.currentTimeMillis();
        preparing = true;
        PlaybackNetworkGate.setBusy(this, true, "准备离线文件");
        trace.add("offline", "hit · " + local.quality + " · " + local.providerLabel + " × " + Song.providerLabel(local.platform));
        try {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(local.file())));
            applyRestoredPositionBeforePrepare();
            player.prepare();
            player.setPlayWhenReady(playbackRequested);
            updateMediaSession();
            updateNotification(false);
            notifyState();
        } catch (Exception e) {
            currentOffline = false;
            offlineBypassForCurrent = true;
            if (offlineStore != null && currentSong() != null) offlineStore.remove(currentSong());
            trace.add("offline-corrupt", safeMessage(e) + " · 删除离线副本并回退在线矩阵");
            resolveCurrent();
        }
    }

    private void prepareResolved(SourceCoordinator.Resolution resolution) {
        if (resolution == null || resolution.url == null || resolution.url.isEmpty()) {
            preparing = false;
            lastError = "播放地址为空";
            notifyState();
            return;
        }
        currentResolution = resolution;
        routeProven = false;
        candidateStartedAt = System.currentTimeMillis();
        trace.add("candidate", (resolution.fromCache ? "cache" : "fresh") + " / "
                + resolution.resolverLabel + " / " + resolution.providerType + " / "
                + Song.providerLabel(resolution.variant.source) + "/" + resolution.quality
                + " / backend=" + resolution.backendGroup
                + " / confidence=" + Math.round(resolution.matchConfidence * 100d) + "%"
                + " / route=" + resolution.routeKey);
        try {
            MediaItem item = MediaItem.fromUri(resolution.url);
            player.setMediaItem(item);
            applyRestoredPositionBeforePrepare();
            player.prepare();
            player.setPlayWhenReady(playbackRequested);
            preparing = true;
            updateMediaSession();
            updateNotification(false);
            notifyState();
        } catch (Exception e) {
            trace.add("prepare-error", safeMessage(e));
            handlePlayerFailure(e);
        }
    }

    private void handlePlayerFailure(Throwable error) {
        Song song = currentSong();
        if (song == null || sourceCoordinator == null) {
            preparing = false;
            lastError = "播放器失败：" + safeMessage(error);
            notifyState();
            return;
        }

        if (currentOffline) {
            String reason = safeMessage(error);
            trace.add("offline-fail", reason + " · 删除离线副本并回退在线矩阵");
            if (offlineStore != null) offlineStore.remove(song);
            currentOffline = false;
            offlineBypassForCurrent = true;
            currentResolution = null;
            if (player != null) player.stop();
            resolveCurrent();
            return;
        }

        HttpFailureDetail detail = inspectHttpFailure(error);
        String reason = detail.describe(error);
        if (detail.statusCode > 0) {
            trace.add("http", "HTTP " + detail.statusCode + (detail.host.isEmpty() ? "" : " / " + detail.host));
        }
        trace.add("media3-fail", reason);

        SourceCoordinator.Resolution failed = currentResolution;
        if (failed == null) {
            preparing = false;
            lastError = "音频链接无法播放：" + reason;
            trace.add("failed", lastError);
            updateNotification(false);
            notifyState();
            return;
        }

        sourceCoordinator.invalidate(failed);

        // A cache hit may simply have expired. Refresh it once without penalizing the route.
        if (failed.fromCache && !cacheRefreshUsed) {
            cacheRefreshUsed = true;
            trace.add("cache-stale", "cached URL rejected; refresh route once / " + failed.routeKey);
            currentResolution = null;
            resolveCurrent();
            return;
        }

        // Fresh URL was actually rejected by Media3: this exact route is bad for this attempt.
        long cooldown = cooldownForHttp(detail.statusCode);
        sourceCoordinator.markPlaybackFailure(failed, reason, cooldown);
        failedRoutes.add(failed.routeKey);
        trace.add("route-failed", failed.resolverLabel + " / "
                + Song.providerLabel(failed.variant.source) + "/" + failed.quality
                + " · 本轮不再重复");
        currentResolution = null;

        // Continue the matrix: same platform/quality other resolver -> lower quality -> other platform.
        if (failedRoutes.size() < 40) {
            resolveCurrent();
            return;
        }

        preparing = false;
        lastError = "音频链接无法播放：" + reason;
        trace.add("failed", lastError);
        updateNotification(false);
        notifyState();
    }


    /**
     * READY proves the URL is decodable, not that it is the requested recording.
     * Community resolvers sometimes return a short notice/preview or a different
     * version.  When both catalog and Media3 durations are known, reject a large
     * mismatch before it can poison READY cache / provider affinity.
     */
    private static boolean durationMatches(Song target, long actualMs) {
        if (target == null) return true;
        long expectedMs = target.durationMs;
        if (expectedMs < 60_000L || actualMs == C.TIME_UNSET || actualMs <= 0L) return true;
        long tolerance = Math.max(7_000L, Math.min(12_000L, Math.round(expectedMs * 0.03d)));
        return Math.abs(actualMs - expectedMs) <= tolerance;
    }

    private static long cooldownForHttp(int code) {
        if (code == 429) return 20L * 60L * 1000L;
        if (code == 401 || code == 403) return 5L * 60L * 1000L;
        if (code == 404 || code == 410) return 90L * 1000L;
        if (code >= 500) return 2L * 60L * 1000L;
        return 3L * 60L * 1000L;
    }

    private static HttpFailureDetail inspectHttpFailure(Throwable error) {
        Throwable t = error;
        for (int depth = 0; t != null && depth < 10; depth++, t = t.getCause()) {
            try {
                Class<?> c = t.getClass();
                if (c.getName().contains("InvalidResponseCodeException")) {
                    int code = ((Number) c.getField("responseCode").get(t)).intValue();
                    String host = "";
                    try {
                        Object dataSpec = c.getField("dataSpec").get(t);
                        Object uri = dataSpec.getClass().getField("uri").get(dataSpec);
                        if (uri != null) host = android.net.Uri.parse(String.valueOf(uri)).getHost();
                    } catch (Exception ignored) { }
                    return new HttpFailureDetail(code, host == null ? "" : host);
                }
            } catch (Exception ignored) { }
        }
        return new HttpFailureDetail(-1, "");
    }

    private static final class HttpFailureDetail {
        final int statusCode;
        final String host;
        HttpFailureDetail(int statusCode, String host) { this.statusCode = statusCode; this.host = host == null ? "" : host; }
        String describe(Throwable error) {
            String base = safeMessage(error);
            if (statusCode > 0) return "HTTP " + statusCode + (host.isEmpty() ? "" : " @ " + host) + " / " + base;
            return base;
        }
    }

    private void restorePlaybackSession() {
        if (sessionStore == null) return;
        PlaybackSessionStore.Session saved = sessionStore.load();
        if (saved == null || saved.queue.isEmpty()) return;
        queue.clear();
        queue.addAll(saved.queue);
        index = Math.max(0, Math.min(saved.index, queue.size() - 1));
        Song current = currentSong();
        restoredSongKey = current == null ? "" : current.key();
        restoredPositionMs = saved.positionMs;
        restoredDurationMs = Math.max(saved.durationMs, current == null ? 0L : current.durationMs);
        lastSessionCheckpointAt = android.os.SystemClock.elapsedRealtime();
    }

    private boolean isRestoredPositionPendingFor(Song song) {
        return song != null && !restoredSongKey.isEmpty() && restoredSongKey.equals(song.key());
    }

    private long clampToKnownDuration(long positionMs) {
        long safe = Math.max(0L, positionMs);
        long known = restoredDurationMs;
        Song current = currentSong();
        if (current != null) known = Math.max(known, current.durationMs);
        return known > 0L ? Math.min(safe, known) : safe;
    }

    private void clearRestoredPosition() {
        restoredSongKey = "";
        restoredPositionMs = 0L;
        restoredDurationMs = 0L;
    }

    private void applyRestoredPositionBeforePrepare() {
        Song current = currentSong();
        if (player == null || !isRestoredPositionPendingFor(current)) return;
        try { player.seekTo(clampToKnownDuration(restoredPositionMs)); } catch (Exception ignored) { }
    }

    private void completeRestoredPositionIfReady() {
        Song current = currentSong();
        if (!isRestoredPositionPendingFor(current)) return;
        clearRestoredPosition();
    }

    private void maybeCheckpointSession() {
        if (player == null || !player.isPlaying() || queue.isEmpty()) return;
        long elapsed = android.os.SystemClock.elapsedRealtime();
        if (elapsed - lastSessionCheckpointAt < SESSION_CHECKPOINT_INTERVAL_MS) return;
        lastSessionCheckpointAt = elapsed;
        persistSessionState(false);
    }

    private void persistSessionState(boolean synchronous) {
        if (sessionStore == null || queue.isEmpty() || index < 0 || index >= queue.size()) return;
        PlaybackSnapshot snap = snapshot();
        sessionStore.save(queue, index, snap.positionMs, snap.durationMs, synchronous);
        lastSessionCheckpointAt = android.os.SystemClock.elapsedRealtime();
    }

    public void resetCommunityProviderScripts() {
        if (sourceCoordinator != null) sourceCoordinator.resetCommunityScripts();
    }

    /**
     * V66 soft source relearn. Preserve READY URL cache, Exact identities, library/offline data and
     * active provider cooldowns; only route preference/history is neutralized.
     */
    public void resetRouteLearning() {
        if (sourceCoordinator != null) sourceCoordinator.resetRouteLearning();
        try { new DownloadRouteStore(this).resetLearning(); } catch (Exception ignored) { }
    }

    public String sourceStatus() { return sourceCoordinator == null ? "音源管理器正在启动" : sourceCoordinator.sourceStatus(); }

    public String compactSourceStatus() { return sourceCoordinator == null ? "智能音源 · 启动中" : sourceCoordinator.compactSourceStatus(); }

    public PlaybackSnapshot snapshot() {
        long pos = 0;
        long dur = currentSong() == null ? 0 : currentSong().durationMs;
        boolean playing = false;
        try {
            if (player != null) {
                pos = Math.max(0, player.getCurrentPosition());
                long d = player.getDuration();
                if (d != C.TIME_UNSET && d > 0) dur = d;
                playing = player.isPlaying();
            }
        } catch (Exception ignored) { }
        Song current = currentSong();
        if (current != null && isRestoredPositionPendingFor(current)) {
            pos = Math.max(pos, restoredPositionMs);
            dur = Math.max(dur, restoredDurationMs);
        }
        return new PlaybackSnapshot(current, playing, pos, dur, index, queue.size(), lastError);
    }

    public Song currentSong() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    public boolean isPreparing() { return preparing; }
    public boolean isUsingFallback() {
        return currentResolution != null && currentSong() != null
                && !currentResolution.variant.source.equals(currentSong().source);
    }
    public String diagnostics() { return trace.text(); }

    public void setArtwork(Bitmap bitmap) {
        currentArtwork = bitmap;
        updateMediaSession();
        updateNotification(player != null && player.isPlaying());
    }

    /** UI-only refresh hook for system media surfaces (for example desktop-lyric action state). */
    public void refreshSystemSurface() {
        updateMediaSession();
        updateNotification(player != null && player.isPlaying());
    }

    private void notifyState() {
        if(lastError!=null && !preparing) {
            if(failureSkip==null) releaseTransitionWakeLock();
            scheduleFailureSkip();
        }
        Listener l = listener;
        if (l != null) l.onPlaybackChanged(snapshot());
    }

    private void updateMediaSession() {
        if (mediaSession == null) return;
        Song s = currentSong();
        PlaybackSnapshot snap = snapshot();
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP;
        int state = preparing ? PlaybackState.STATE_BUFFERING
                : (snap.playing ? PlaybackState.STATE_PLAYING : (s == null ? PlaybackState.STATE_NONE : PlaybackState.STATE_PAUSED));
        PlaybackState.Builder playbackStateBuilder = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, snap.positionMs, snap.playing ? 1f : 0f);
        if (Settings.canDrawOverlays(this)) {
            boolean lyricEnabled = DesktopLyricService.enabled(this);
            boolean lyricLocked = lyricEnabled && DesktopLyricService.locked(this);
            playbackStateBuilder.addCustomAction(new PlaybackState.CustomAction.Builder(
                    CUSTOM_DESKTOP_LYRIC,
                    lyricEnabled ? (lyricLocked ? "已锁定歌词" : "未锁定歌词") : "桌面歌词",
                    lyricEnabled ? (lyricLocked ? R.drawable.ic_lyric_lock_system : R.drawable.ic_lyric_unlock_system) : R.drawable.ic_lyric_toggle_system).build());
            if (lyricEnabled) playbackStateBuilder.addCustomAction(new PlaybackState.CustomAction.Builder(
                    CUSTOM_DESKTOP_LYRIC_CLOSE, "关闭歌词", R.drawable.ic_lyric_close_system).build());
        }
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        if (s != null) {
            MediaMetadata.Builder mb = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, s.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, s.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, s.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, snap.durationMs);
            if (currentArtwork != null) mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArtwork);
            mediaSession.setMetadata(mb.build());
        }
    }

    private void ensureForeground() {
        if(destroyed) return;
        if(!serviceStarted) {
            serviceStarted=true;
            Intent start=new Intent(this,PlaybackService.class).setAction(ACTION_REFRESH_SURFACE);
            if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(start); else startService(start);
        }
        startForeground(NOTIF_ID, buildNotification(false));
    }

    private void updateNotification(boolean playing) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, buildNotification(playing));
        } catch (Exception ignored) { }
    }

    private Notification buildNotification(boolean playing) {
        Song s = currentSong();
        String title = s == null ? "Lunaxy Music" : s.title;
        String sub = s == null ? "准备播放" : s.artist;
        PendingIntent content = PendingIntent.getActivity(this, 11, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent prev = servicePi(ACTION_PREV, 12);
        PendingIntent toggle = servicePi(ACTION_TOGGLE, 13);
        PendingIntent next = servicePi(ACTION_NEXT, 14);
        PendingIntent stop = servicePi(ACTION_STOP, 15);
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean lyricEnabled = overlayGranted && DesktopLyricService.enabled(this);
        boolean lyricLocked = lyricEnabled && DesktopLyricService.locked(this);
        PendingIntent lyricState = PendingIntent.getService(this, 16,
                new Intent(this, DesktopLyricService.class).setAction(lyricEnabled
                        ? DesktopLyricService.ACTION_TOGGLE_LOCK : DesktopLyricService.ACTION_ENABLE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent lyricClose = PendingIntent.getService(this, 17,
                new Intent(this, DesktopLyricService.class).setAction(DesktopLyricService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(sub)
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT);
        if (currentArtwork != null) b.setLargeIcon(currentArtwork);
        b.addAction(new Notification.Action.Builder(R.drawable.ic_media_prev, "上一首", prev).build());
        b.addAction(new Notification.Action.Builder(playing ? R.drawable.ic_media_pause : R.drawable.ic_media_play, playing ? "暂停" : "播放", toggle).build());
        b.addAction(new Notification.Action.Builder(R.drawable.ic_media_next, "下一首", next).build());
        if (overlayGranted) {
            b.addAction(new Notification.Action.Builder(lyricEnabled
                            ? (lyricLocked ? R.drawable.ic_lyric_lock_system : R.drawable.ic_lyric_unlock_system)
                            : R.drawable.ic_lyric_toggle_system,
                    lyricEnabled ? (lyricLocked ? "已锁定歌词" : "未锁定歌词") : "桌面歌词", lyricState).build());
            if (lyricEnabled) b.addAction(new Notification.Action.Builder(R.drawable.ic_lyric_close_system, "关闭歌词", lyricClose).build());
        }
        b.setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession == null ? null : mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        b.setDeleteIntent(stop);
        return b.build();
    }

    private PendingIntent servicePi(String action, int code) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
        c.setSound(null, null);
        c.enableVibration(false);
        c.setShowBadge(false);
        c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(c);
    }

    public void stopPlayback() {
        stopPlayback(false);
    }

    private void stopPlayback(boolean clearSavedSession) {
        playbackRequested=false; cancelFailureSkip(); releaseTransitionWakeLock();
        cancelSleepTimer();
        if (clearSavedSession) {
            if (sessionStore != null) sessionStore.clear(true);
        } else {
            // Stopping/removing the notification ends playback but keeps the last listening context.
            persistSessionState(true);
        }
        requestGeneration++;
        clearRestoredPosition();
        if (player != null) player.stop();
        queue.clear();
        index = -1;
        preparing = false;
        lastError = null;
        currentArtwork = null;
        currentResolution = null;
        currentOffline = false;
        offlineBypassForCurrent = false;
        PlaybackNetworkGate.setBusy(this, false, "");
        if (mediaSession != null) mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_STOPPED, 0, 0).build());
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        notifyState();
    }

    private static String friendlyResolveError(Exception e) {
        String m = safeMessage(e);
        if (m.contains("IP 被限制") || m.contains("block ip")) return "当前线路被限制，已自动切换其它健康音源";
        if (m.contains("请求过于频繁") || m.contains("too many") || m.contains("429")) return "当前线路触发限流，已进入保护冷却";
        return m;
    }

    private static String safeMessage(Throwable e) {
        return e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()
                ? (e == null ? "unknown" : e.getClass().getSimpleName()) : e.getMessage();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        persistSessionState(true);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        destroyed=true; listener=null;
        cancelFailureSkip(); releaseTransitionWakeLock();
        persistSessionState(true);
        PlaybackNetworkGate.setBusy(this, false, "");
        requestGeneration++;
        main.removeCallbacks(ticker);
        main.removeCallbacks(sleepTimerRunnable);
        resolverExecutor.shutdownNow();
        try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) { }
        if (sourceCoordinator != null) sourceCoordinator.destroy();
        if (player != null) { player.release(); player = null; }
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
        super.onDestroy();
    }
}
