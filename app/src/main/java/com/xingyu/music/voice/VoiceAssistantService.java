package com.xingyu.music.voice;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.WindowManager;

import com.xingyu.music.MainActivity;
import com.xingyu.music.R;
import com.xingyu.music.data.LibraryStore;
import com.xingyu.music.model.ImportedPlaylist;
import com.xingyu.music.model.Song;
import com.xingyu.music.playback.PlaybackService;
import com.xingyu.music.ui.AppearanceSystem;
import com.xingyu.music.ui.Ui;
import com.xingyu.music.ui.VoiceAssistantPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-enabled foreground microphone service for Lunaxy Voice.
 *
 * The service is deliberately started only from a visible Activity after RECORD_AUDIO is granted.
 * It prefers Android's on-device recognizer when available and otherwise falls back to the device
 * speech-recognition provider. This first-party-only design avoids shipping a large wake-word model
 * or requiring a third-party access key. The recognizer is restarted after normal speech timeouts.
 */
public final class VoiceAssistantService extends Service implements RecognitionListener {
    private static final String CHANNEL_ID = "lunaxy_voice";
    private static final int NOTIFICATION_ID = 4207;
    private static final long COMMAND_WINDOW_MS = 9_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final VoiceMusicSearch musicSearch = new VoiceMusicSearch();
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private boolean recognizerListening;
    private boolean recognizerReady;
    private boolean usingOnDeviceRecognizer;
    private boolean alternateRecognizerTried;
    private final List<ComponentName> systemRecognizerProviders = new ArrayList<>();
    private int systemRecognizerProviderIndex = -1;
    private ComponentName activeSystemRecognizerProvider;
    private int systemRecognizerProviderRotations;
    private boolean localModelDownloadRequested;
    private int consecutiveRecognizerFailures;
    private boolean commandMode;
    private boolean recognitionPaused;
    private boolean stopping;
    private long commandDeadline;
    private long lastRmsBroadcast;
    private long lastNotificationUpdate;
    private String lastNotificationSignature = "";
    private String phase = VoiceAssistantContract.PHASE_ARMED;
    private String transcript = "";
    private String detail = "";
    private String resultQuery = "";
    private boolean resultOpensSearch;

    private LibraryStore library;
    private PlaybackService playback;
    private boolean playbackBound;
    private Runnable pendingPlaybackAction;

    private WindowManager overlayManager;
    private VoiceAssistantPanel overlayPanel;
    private boolean overlayAttached;
    private WindowManager.LayoutParams overlayLayoutParams;

    private final ServiceConnection playbackConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            playback = ((PlaybackService.LocalBinder) service).getService();
            playbackBound = true;
            Runnable pending = pendingPlaybackAction;
            pendingPlaybackAction = null;
            if (pending != null) main.post(pending);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            playbackBound = false;
            playback = null;
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        // The service can outlive the Activity. Resolve the same Appearance/Reduced Motion tokens
        // before constructing a system overlay so Lunaxy Voice never falls back to a stale dark UI.
        AppearanceSystem.load(this);
        library = new LibraryStore(this);
        overlayManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        try { bindService(new Intent(this, PlaybackService.class), playbackConnection, BIND_AUTO_CREATE); }
        catch (Exception ignored) { }
        createRecognizer();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? VoiceAssistantContract.ACTION_START : intent.getAction();
        if (VoiceAssistantContract.ACTION_STOP.equals(action)) {
            VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_ENABLED, false).apply();
            stopVoiceService();
            return START_NOT_STICKY;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            publish(VoiceAssistantContract.PHASE_ERROR, "需要麦克风权限", "返回 Lunaxy 后重新开启语音唤醒", 0f, "", false);
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureForeground();
        VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_ENABLED, true).apply();
        if (VoiceAssistantContract.ACTION_RELOAD_ENGINE.equals(action)) {
            alternateRecognizerTried = false;
            localModelDownloadRequested = false;
            systemRecognizerProviderIndex = -1;
            activeSystemRecognizerProvider = null;
            systemRecognizerProviderRotations = 0;
            createRecognizer();
            recognitionPaused = false;
            commandMode = false;
            publish(VoiceAssistantContract.PHASE_ARMED, "",
                    "识别模式已切换 · " + VoiceAssistantContract.recognitionModeLabel(this), 0f, "", false);
            scheduleListen(220L);
            return START_NOT_STICKY;
        }
        if (VoiceAssistantContract.ACTION_TEST_LISTEN.equals(action)) {
            // An explicit user listen request should always surface feedback even if an earlier
            // background banner was snoozed/collapsed.
            VoiceAssistantContract.clearOverlaySnooze(this);
            VoiceAssistantContract.setOverlayCompact(this, false);
            beginCommandWindow("直接聆听");
        } else {
            recognitionPaused = false;
            commandMode = false;
            publish(VoiceAssistantContract.PHASE_ARMED, "", "后台唤醒已开启 · " + VoiceAssistantContract.wakeLabel(this), 0f, "", false);
            scheduleListen(180L);
        }
        return START_NOT_STICKY;
    }

    private void ensureForeground() {
        Notification notification = buildNotification("后台唤醒已开启 · " + VoiceAssistantContract.wakeLabel(this), null);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else startForeground(NOTIFICATION_ID, notification);
    }

    private void createRecognizer() {
        String mode = VoiceAssistantContract.recognitionMode(this);
        // Local-first remains the default.  The system path is now provider-aware: several ROMs
        // expose more than one RecognitionService and the default service can return NETWORK/SERVER
        // even while the device itself is online.  We keep the platform API but can rotate to an
        // installed alternate provider before telling the user that the network is down.
        if (VoiceAssistantContract.MODE_LOCAL.equals(mode)) createRecognizer(true);
        else if (VoiceAssistantContract.MODE_SYSTEM.equals(mode)) createRecognizer(false);
        else {
            boolean localFirst = canTryOnDeviceRecognizer();
            createRecognizer(localFirst);
            if (localFirst && recognizer == null) createRecognizer(false);
        }
    }

    private void refreshSystemRecognizerProviders() {
        systemRecognizerProviders.clear();
        try {
            String flat = Settings.Secure.getString(getContentResolver(), "voice_recognition_service");
            ComponentName preferred = flat == null ? null : ComponentName.unflattenFromString(flat);
            if (preferred != null) systemRecognizerProviders.add(preferred);
        } catch (Throwable ignored) { }
        try {
            Intent query = new Intent("android.speech.RecognitionService");
            List<ResolveInfo> resolved = getPackageManager().queryIntentServices(query, PackageManager.MATCH_ALL);
            if (resolved != null) {
                for (ResolveInfo info : resolved) {
                    if (info == null || info.serviceInfo == null) continue;
                    ComponentName candidate = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
                    if (!systemRecognizerProviders.contains(candidate)) systemRecognizerProviders.add(candidate);
                }
            }
        } catch (Throwable ignored) { }
    }

    private void createRecognizer(boolean preferOnDevice) {
        destroyRecognizer();
        usingOnDeviceRecognizer = false;
        if (!SpeechRecognizer.isRecognitionAvailable(this) && !preferOnDevice) return;
        if (preferOnDevice && Build.VERSION.SDK_INT >= 31) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                    usingOnDeviceRecognizer = true;
                }
            } catch (Throwable ignored) { recognizer = null; }
        }
        if (recognizer == null && !preferOnDevice) {
            try {
                // V92.9.5: let Android bind its configured/default RecognitionService first. Some
                // OEM services expose an explicit component but only work reliably through the
                // platform's default binding path. Explicit provider rotation is now a fallback
                // after a real NETWORK/SERVER/disconnect failure, not the first connection attempt.
                recognizer = activeSystemRecognizerProvider == null
                        ? SpeechRecognizer.createSpeechRecognizer(this)
                        : SpeechRecognizer.createSpeechRecognizer(this, activeSystemRecognizerProvider);
            } catch (Throwable ignored) { recognizer = null; }
        }
        if (recognizer != null) recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, usingOnDeviceRecognizer || VoiceAssistantContract.isLocalOnlyMode(this))
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1050L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
    }

    private boolean rotateSystemRecognizerProvider(String reason) {
        if (VoiceAssistantContract.isLocalOnlyMode(this)) return false;
        if (systemRecognizerProviders.isEmpty()) refreshSystemRecognizerProviders();
        if (systemRecognizerProviders.isEmpty() || systemRecognizerProviderRotations >= systemRecognizerProviders.size()) return false;
        int next = systemRecognizerProviderIndex < 0 ? 0 : (systemRecognizerProviderIndex + 1) % systemRecognizerProviders.size();
        ComponentName candidate = systemRecognizerProviders.get(next);
        if (candidate.equals(activeSystemRecognizerProvider) && systemRecognizerProviders.size() == 1) return false;
        systemRecognizerProviderIndex = next;
        activeSystemRecognizerProvider = candidate;
        systemRecognizerProviderRotations++;
        createRecognizer(false);
        if (recognizer == null) return false;
        if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                reason + " · 正在切换系统识别服务", .04f, "", false);
        scheduleListen(320L);
        return true;
    }

    private boolean hasUsableNetwork() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null || cm.getActiveNetwork() == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            // Do not require NET_CAPABILITY_VALIDATED here. Several OEM/China ROMs can reach the
            // speech provider while Android's validation probe is blocked, so VALIDATED caused a
            // false "offline" diagnosis even when ordinary app networking worked.
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable ignored) { return false; }
    }

    private String currentEngineLabel() {
        if (usingOnDeviceRecognizer) return "本地识别";
        return VoiceAssistantContract.isLocalOnlyMode(this) ? "本地识别不可用" : "系统识别";
    }

    private boolean automaticFallbackAllowed() {
        return VoiceAssistantContract.isAutoMode(this);
    }

    private void requestLocalModelDownload() {
        if (Build.VERSION.SDK_INT < 33 || recognizer == null || !usingOnDeviceRecognizer || recognizerIntent == null) return;
        if (localModelDownloadRequested) return;
        localModelDownloadRequested = true;
        try { recognizer.triggerModelDownload(recognizerIntent); }
        catch (Throwable ignored) { }
    }

    private void destroyRecognizer() {
        main.removeCallbacks(recognizerReadyWatchdog);
        recognizerListening = false;
        recognizerReady = false;
        if (recognizer == null) return;
        try { recognizer.setRecognitionListener(null); } catch (Exception ignored) { }
        try { recognizer.cancel(); } catch (Exception ignored) { }
        try { recognizer.destroy(); } catch (Exception ignored) { }
        recognizer = null;
    }

    private boolean canTryOnDeviceRecognizer() {
        if (Build.VERSION.SDK_INT < 31) return false;
        try { return SpeechRecognizer.isOnDeviceRecognitionAvailable(this); }
        catch (Throwable ignored) { return false; }
    }

    private void switchRecognizer(boolean useOnDevice, String reason) {
        alternateRecognizerTried = true;
        createRecognizer(useOnDevice);
        if (recognizer == null && useOnDevice && automaticFallbackAllowed()) createRecognizer(false);
        String engine = usingOnDeviceRecognizer ? "本地识别" : "系统识别";
        if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                reason + " · " + engine, .06f, "", false);
        scheduleListen(220L);
    }

    private void scheduleListen(long delay) {
        if (recognitionPaused) return;
        main.removeCallbacks(restartRecognition);
        main.postDelayed(restartRecognition, Math.max(80L, delay));
    }

    // Keep the watchdog declaration before restartRecognition. Java field initializers may not
    // use a later field via a simple-name forward reference; V92.8.12 therefore failed javac
    // at the removeCallbacks/postDelayed calls even though the runtime logic itself was valid.
    private final Runnable recognizerReadyWatchdog = () -> {
        if (stopping || recognitionPaused || !recognizerListening || recognizerReady) return;
        recognizerListening = false;
        if (automaticFallbackAllowed() && !alternateRecognizerTried && canTryOnDeviceRecognizer() && !usingOnDeviceRecognizer) {
            switchRecognizer(true, "系统识别没有响应");
            return;
        }
        if (automaticFallbackAllowed() && usingOnDeviceRecognizer) {
            switchRecognizer(false, "本地识别没有响应");
            return;
        }
        if (commandMode) publish(VoiceAssistantContract.PHASE_ERROR, "没有收到麦克风音频",
                "请检查系统“语音识别与合成”服务，或确认没有其他 App 正在占用麦克风", 0f, "", false);
        scheduleListen(1800L);
    };

    private final Runnable restartRecognition = () -> {
        if (stopping || recognitionPaused || !VoiceAssistantContract.enabled(this)) return;
        if (recognizer == null) {
            createRecognizer();
            if (recognizer == null) {
                String message = VoiceAssistantContract.isLocalOnlyMode(this)
                        ? "当前设备没有可用的本地语音识别器 · 可切换“本地优先”或“系统识别”"
                        : "可以安装/启用系统语音识别服务后重试";
                publish(VoiceAssistantContract.PHASE_ERROR, "设备没有可用的语音识别器", message, 0f, "", false);
                return;
            }
        }
        if (commandMode && SystemClock.uptimeMillis() > commandDeadline) {
            commandMode = false;
            publish(VoiceAssistantContract.PHASE_ARMED, "", "没有听到指令 · 继续等待唤醒词", 0f, "", false);
        }
        try {
            if (recognizerListening) recognizer.cancel();
            recognizerReady = false;
            recognizer.startListening(recognizerIntent);
            recognizerListening = true;
            main.removeCallbacks(recognizerReadyWatchdog);
            main.postDelayed(recognizerReadyWatchdog, usingOnDeviceRecognizer ? 3800L : 5200L);
            if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                    "正在连接麦克风…", .04f, "", false);
        } catch (Exception e) {
            recognizerListening = false;
            recognizerReady = false;
            if (commandMode) publish(VoiceAssistantContract.PHASE_ERROR, "语音引擎启动失败",
                    "正在重新连接系统语音服务", 0f, "", false);
            scheduleListen(1200L);
        }
    };

    private void beginCommandWindow(String source) {
        recognitionPaused = false;
        commandMode = true;
        alternateRecognizerTried = false;
        consecutiveRecognizerFailures = 0;
        commandDeadline = SystemClock.uptimeMillis() + COMMAND_WINDOW_MS;
        vibrateWake();
        publish(VoiceAssistantContract.PHASE_WAKE, source == null ? "我在" : source,
                "下一首、暂停、播放歌单、搜索歌手…", .18f, "", false);
        main.postDelayed(() -> {
            if (!commandMode || stopping) return;
            publish(VoiceAssistantContract.PHASE_LISTENING, "", "请说音乐指令", .1f, "", false);
        }, 260L);
        if (recognizerListening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            recognizerListening = false;
        }
        scheduleListen(360L);
    }

    private void wakeFromTranscript(String heard) {
        if (commandMode) return;
        commandMode = true;
        alternateRecognizerTried = false;
        consecutiveRecognizerFailures = 0;
        commandDeadline = SystemClock.uptimeMillis() + COMMAND_WINDOW_MS;
        vibrateWake();
        publish(VoiceAssistantContract.PHASE_WAKE, "我在", "说出你的音乐指令", .25f, "", false);
        String tail = VoiceCommandParser.stripWakePhrase(heard, VoiceAssistantContract.wakeAliases(this));
        if (!tail.isEmpty()) executeRecognizedCommand(tail);
    }

    private void executeRecognizedCommand(String heard) {
        if (recognitionPaused) return;
        String clean = VoiceCommandParser.stripWakePhrase(heard, VoiceAssistantContract.wakeAliases(this));
        if (clean.isEmpty()) {
            publish(VoiceAssistantContract.PHASE_LISTENING, "", "继续说，例如“下两首”", .16f, "", false);
            commandDeadline = SystemClock.uptimeMillis() + COMMAND_WINDOW_MS;
            scheduleListen(180L);
            return;
        }
        recognitionPaused = true;
        main.removeCallbacks(restartRecognition);
        if (recognizerListening && recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            recognizerListening = false;
        }
        VoiceCommand command = VoiceCommandParser.parse(clean);
        publish(VoiceAssistantContract.PHASE_PROCESSING, clean, "正在执行音乐指令", .12f, "", false);
        switch (command.type) {
            case NEXT:
                withPlayback(() -> skipRelative(command.count));
                finishResult(clean, command.count == 1 ? "已切到下一首" : "已向后跳过 " + command.count + " 首", "", false);
                break;
            case PREVIOUS:
                withPlayback(() -> skipRelative(-command.count));
                finishResult(clean, command.count == 1 ? "已切到上一首" : "已向前跳过 " + command.count + " 首", "", false);
                break;
            case PAUSE:
                withPlayback(() -> { if (playback != null) playback.pause(); });
                finishResult(clean, "已暂停", "", false);
                break;
            case RESUME:
                withPlayback(() -> { if (playback != null) playback.resume(); });
                finishResult(clean, "继续播放", "", false);
                break;
            case PLAY_PLAYLIST:
                playPlaylist(command.query, clean);
                break;
            case SEARCH:
            case SEARCH_ARTIST:
                if (command.query.isEmpty()) failCommand(clean, "没有听到要搜索的内容");
                else finishResult(clean, "搜索 “" + command.query + "”", command.query, true);
                break;
            case PLAY_ARTIST:
                playArtist(command.query, clean);
                break;
            case PLAY_QUERY:
                playQuery(command.query, clean);
                break;
            default:
                failCommand(clean, "可以说“下一首”“播放深夜歌单”“放周杰伦的歌”");
                break;
        }
    }

    private void skipRelative(int delta) {
        if (playback == null) return;
        List<Song> queue = playback.queueSnapshot();
        if (queue.isEmpty()) return;
        int size = queue.size();
        int current = Math.max(0, playback.currentQueueIndex());
        int target = ((current + delta) % size + size) % size;
        playback.playAt(target);
    }

    private void playPlaylist(String requested, String heard) {
        worker.submit(() -> {
            String wanted = requested == null ? "" : requested.trim();
            List<Song> songs = new ArrayList<>();
            String label = wanted;
            if (isFavoritesAlias(wanted)) {
                songs = library.favorites();
                label = "收藏";
            } else if (isHistoryAlias(wanted)) {
                songs = library.history();
                label = "最近播放";
            } else {
                ImportedPlaylist best = bestPlaylist(wanted, library.playlists());
                if (best != null) { songs = new ArrayList<>(best.songs); label = best.name; }
            }
            final List<Song> queue = songs;
            final String finalLabel = label;
            if (queue.isEmpty()) { main.post(() -> failCommand(heard, "没有找到 “" + wanted + "” 歌单")); return; }
            withPlayback(() -> { if (playback != null) playback.playQueue(queue, 0); });
            main.post(() -> finishResult(heard, "正在播放 “" + finalLabel + "” · " + queue.size() + " 首", "", false));
        });
    }

    private void playArtist(String artist, String heard) {
        if (artist == null || artist.trim().isEmpty()) { failCommand(heard, "没有听到歌手名字"); return; }
        worker.submit(() -> {
            List<Song> songs = musicSearch.searchArtist(artist, 30);
            if (songs.isEmpty()) { main.post(() -> failCommand(heard, "没有找到 “" + artist + "” 的歌曲")); return; }
            withPlayback(() -> { if (playback != null) playback.playQueue(songs, 0); });
            main.post(() -> finishResult(heard, "正在播放 " + artist + " · " + songs.size() + " 首", "", false));
        });
    }

    private void playQuery(String query, String heard) {
        if (query == null || query.trim().isEmpty()) { failCommand(heard, "没有听到歌名"); return; }
        worker.submit(() -> {
            // Exact/fuzzy local playlist names get priority only when the phrase clearly matches one.
            ImportedPlaylist local = bestPlaylist(query, library.playlists());
            if (local != null && playlistScore(local.name, query) >= 90 && !local.songs.isEmpty()) {
                List<Song> songs = new ArrayList<>(local.songs);
                withPlayback(() -> { if (playback != null) playback.playQueue(songs, 0); });
                main.post(() -> finishResult(heard, "正在播放歌单 “" + local.name + "”", "", false));
                return;
            }
            Song song = musicSearch.bestSong(query);
            if (song == null) { main.post(() -> failCommand(heard, "没有找到 “" + query + "”")); return; }
            withPlayback(() -> { if (playback != null) playback.insertAndPlay(song); });
            main.post(() -> finishResult(heard, "正在播放 “" + song.title + "” · " + song.artist, "", false));
        });
    }

    private void withPlayback(Runnable action) {
        main.post(() -> {
            if (playback != null) action.run();
            else {
                pendingPlaybackAction = action;
                try { bindService(new Intent(this, PlaybackService.class), playbackConnection, BIND_AUTO_CREATE); }
                catch (Exception ignored) { }
            }
        });
    }

    private void finishResult(String heard, String message, String query, boolean openSearch) {
        commandMode = false;
        resultQuery = query == null ? "" : query;
        resultOpensSearch = openSearch;
        publish(VoiceAssistantContract.PHASE_RESULT, heard, message, .08f, resultQuery, openSearch);
        main.removeCallbacks(returnToArmed);
        main.postDelayed(returnToArmed, openSearch ? 4200L : 3200L);
    }

    private void failCommand(String heard, String message) {
        commandMode = false;
        publish(VoiceAssistantContract.PHASE_ERROR, heard, message, 0f, "", false);
        main.removeCallbacks(returnToArmed);
        main.postDelayed(returnToArmed, 2700L);
    }

    private final Runnable returnToArmed = () -> {
        if (stopping || !VoiceAssistantContract.enabled(this)) return;
        recognitionPaused = false;
        resultQuery = "";
        resultOpensSearch = false;
        publish(VoiceAssistantContract.PHASE_ARMED, "", "后台唤醒已开启 · " + VoiceAssistantContract.wakeLabel(this), 0f, "", false);
        scheduleListen(180L);
    };

    private void publish(String newPhase, String heard, String message, float level, String query, boolean openSearch) {
        phase = newPhase;
        transcript = heard == null ? "" : heard;
        detail = message == null ? "" : message;
        resultQuery = query == null ? "" : query;
        resultOpensSearch = openSearch;
        Intent update = new Intent(VoiceAssistantContract.ACTION_STATE)
                .setPackage(getPackageName())
                .putExtra(VoiceAssistantContract.EXTRA_PHASE, phase)
                .putExtra(VoiceAssistantContract.EXTRA_TRANSCRIPT, transcript)
                .putExtra(VoiceAssistantContract.EXTRA_DETAIL, detail)
                .putExtra(VoiceAssistantContract.EXTRA_LEVEL, level)
                .putExtra(VoiceAssistantContract.EXTRA_QUERY, resultQuery)
                .putExtra(VoiceAssistantContract.EXTRA_OPEN_SEARCH, resultOpensSearch);
        sendBroadcast(update);
        updateOverlay(level);
        maybeUpdateNotification();
    }

    private void maybeUpdateNotification() {
        if (VoiceAssistantContract.PHASE_OFF.equals(phase)) return;
        String notificationText = notificationText();
        String signature = phase + "|" + notificationText + "|" + (resultOpensSearch ? resultQuery : "");
        long now = SystemClock.uptimeMillis();
        // RMS updates can arrive ~10 times/second. Never rebuild the system notification at audio-frame rate.
        if (signature.equals(lastNotificationSignature) && now - lastNotificationUpdate < 2200L) return;
        lastNotificationSignature = signature;
        lastNotificationUpdate = now;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(notificationText, resultOpensSearch ? resultQuery : null));
    }

    private String notificationText() {
        if (VoiceAssistantContract.PHASE_LISTENING.equals(phase) || VoiceAssistantContract.PHASE_WAKE.equals(phase)) return "正在聆听音乐指令";
        if (VoiceAssistantContract.PHASE_PROCESSING.equals(phase)) return transcript.isEmpty() ? "正在理解指令" : transcript;
        if (VoiceAssistantContract.PHASE_RESULT.equals(phase) || VoiceAssistantContract.PHASE_ERROR.equals(phase)) return detail;
        return "后台唤醒已开启 · " + VoiceAssistantContract.wakeLabel(this);
    }

    private Notification buildNotification(String text, String openSearchQuery) {
        Intent stopIntent = VoiceAssistantContract.serviceIntent(this, VoiceAssistantContract.ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 71, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent listenIntent = VoiceAssistantContract.serviceIntent(this, VoiceAssistantContract.ACTION_TEST_LISTEN);
        PendingIntent listen = PendingIntent.getService(this, 74, listenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent launch = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (openSearchQuery != null && !openSearchQuery.trim().isEmpty()) launch.putExtra(VoiceAssistantContract.EXTRA_QUERY, openSearchQuery.trim());
        if (VoiceAssistantContract.PHASE_ERROR.equals(phase)) launch.putExtra(VoiceAssistantContract.EXTRA_OPEN_VOICE_SETTINGS, true);
        PendingIntent content = PendingIntent.getActivity(this, 72, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Lunaxy Voice")
                .setContentText(text == null ? "语音唤醒" : text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(R.drawable.ic_media_play, "立即聆听", listen).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_media_pause, "关闭语音唤醒", stop).build());
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lunaxy Voice", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("后台语音唤醒与音乐控制");
        channel.enableVibration(false);
        channel.setSound(null, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void updateOverlay(float level) {
        if (!shouldShowSystemOverlay()) { removeSystemOverlay(); return; }
        if (VoiceAssistantContract.PHASE_ARMED.equals(phase) || VoiceAssistantContract.PHASE_OFF.equals(phase)) { removeSystemOverlay(); return; }
        if (overlayManager == null) return;
        if (overlayPanel == null) {
            overlayPanel = new VoiceAssistantPanel(this);
            overlayPanel.setCloseAction(this::removeSystemOverlay);
            overlayPanel.setCollapseAction(() -> setOverlayCompact(true, true));
            overlayPanel.setExpandAction(() -> setOverlayCompact(false, true));
            overlayPanel.setOpenAppAction(this::openAppFromOverlay);
            overlayPanel.setSnoozeAction(() -> snoozeSystemOverlay(10L * 60L * 1000L));
        }
        overlayPanel.render(phase, transcript, detail, level);
        if (resultOpensSearch && !resultQuery.isEmpty()) {
            overlayPanel.setPrimaryAction("查看搜索", () -> {
                PendingIntent pending = PendingIntent.getActivity(this, 73,
                        new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(VoiceAssistantContract.EXTRA_QUERY, resultQuery),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try { pending.send(); } catch (Exception ignored) { }
                removeSystemOverlay();
            });
        } else if (VoiceAssistantContract.PHASE_ERROR.equals(phase)) {
            overlayPanel.setPrimaryAction("语音设置", () -> {
                PendingIntent pending = PendingIntent.getActivity(this, 75,
                        new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(VoiceAssistantContract.EXTRA_OPEN_VOICE_SETTINGS, true),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try { pending.send(); } catch (Exception ignored) { }
                removeSystemOverlay();
            });
        } else overlayPanel.setPrimaryAction("", null);
        if (!overlayAttached) {
            boolean compact = VoiceAssistantContract.overlayCompact(this);
            overlayLayoutParams = newOverlayLayoutParams(compact);
            if (compact) overlayPanel.applyCompactImmediate();
            else overlayPanel.applyExpandedImmediate();
            try { overlayManager.addView(overlayPanel, overlayLayoutParams); overlayAttached = true; }
            catch (Exception ignored) { overlayAttached = false; }
        }
    }

    private WindowManager.LayoutParams newOverlayLayoutParams(boolean compact) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                compact ? Ui.dp(this, 156) : WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        if (compact) {
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Ui.dp(this, 12);
            lp.y = Ui.dp(this, 16);
            lp.horizontalMargin = 0f;
        } else {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.x = 0;
            lp.y = Ui.dp(this, 58);
            lp.horizontalMargin = .04f;
        }
        return lp;
    }

    private void applyOverlayLayout(boolean compact) {
        if (!overlayAttached || overlayPanel == null || overlayManager == null) return;
        WindowManager.LayoutParams next = newOverlayLayoutParams(compact);
        overlayLayoutParams = next;
        try { overlayManager.updateViewLayout(overlayPanel, next); } catch (Exception ignored) { }
    }

    private void setOverlayCompact(boolean compact, boolean animate) {
        if (overlayPanel == null) return;
        VoiceAssistantContract.setOverlayCompact(this, compact);
        if (!overlayAttached) return;
        if (!animate || com.xingyu.music.ui.SpringMotion.isReducedMotion()) {
            applyOverlayLayout(compact);
            if (compact) overlayPanel.applyCompactImmediate();
            else overlayPanel.applyExpandedImmediate();
            return;
        }
        if (compact) {
            overlayPanel.animateToCompact(() -> {
                applyOverlayLayout(true);
                overlayPanel.applyCompactImmediate();
            });
        } else {
            // Resize the real system window first, then grow the same panel from the compact
            // top-left geometry. Alpha is suppressed only until the next UI turn to avoid a single
            // full-width flash before the inverse morph is prepared.
            overlayPanel.setAlpha(0f);
            applyOverlayLayout(false);
            overlayPanel.post(() -> {
                overlayPanel.prepareExpandedFromCompact();
                overlayPanel.setAlpha(1f);
                overlayPanel.animateToExpanded();
            });
        }
    }

    private void snoozeSystemOverlay(long durationMs) {
        VoiceAssistantContract.snoozeOverlay(this, durationMs);
        removeSystemOverlay();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID,
                buildNotification("悬浮提示已稍后 10 分钟 · 语音仍在后台待命", null));
    }

    private void openAppFromOverlay() {
        Intent launch = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (resultOpensSearch && !resultQuery.isEmpty()) launch.putExtra(VoiceAssistantContract.EXTRA_QUERY, resultQuery);
        else if (VoiceAssistantContract.PHASE_ERROR.equals(phase)) launch.putExtra(VoiceAssistantContract.EXTRA_OPEN_VOICE_SETTINGS, true);
        try { startActivity(launch); } catch (Exception ignored) { }
        removeSystemOverlay();
    }

    private boolean shouldShowSystemOverlay() {
        if (!VoiceAssistantContract.backgroundOverlayEnabled(this)) return false;
        if (VoiceAssistantContract.overlaySnoozed(this)) return false;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return false;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return true;
        List<ActivityManager.RunningAppProcessInfo> list = manager.getRunningAppProcesses();
        if (list == null) return true;
        for (ActivityManager.RunningAppProcessInfo info : list) {
            if (info.pid == Process.myPid()) return info.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        }
        return true;
    }

    private void removeSystemOverlay() {
        if (!overlayAttached || overlayPanel == null || overlayManager == null) return;
        try { overlayManager.removeView(overlayPanel); } catch (Exception ignored) { }
        overlayAttached = false;
        overlayLayoutParams = null;
    }

    private void vibrateWake() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(32L, 72));
            else vibrator.vibrate(32L);
        } catch (Exception ignored) { }
    }

    private ImportedPlaylist bestPlaylist(String query, List<ImportedPlaylist> playlists) {
        if (query == null || query.trim().isEmpty() || playlists == null) return null;
        ImportedPlaylist best = null;
        int bestScore = 0;
        for (ImportedPlaylist playlist : playlists) {
            if (playlist == null || playlist.songs == null || playlist.songs.isEmpty()) continue;
            int score = playlistScore(playlist.name, query);
            if (score > bestScore) { bestScore = score; best = playlist; }
        }
        return bestScore >= 42 ? best : null;
    }

    private int playlistScore(String name, String query) {
        String n = normalize(name), q = normalize(query);
        if (q.isEmpty() || n.isEmpty()) return 0;
        if (n.equals(q)) return 120;
        if (n.startsWith(q) || q.startsWith(n)) return 92;
        if (n.contains(q) || q.contains(n)) return 70;
        return commonPrefix(n, q) >= Math.min(3, q.length()) ? 45 : 0;
    }

    private int commonPrefix(String a, String b) {
        int count = 0, max = Math.min(a.length(), b.length());
        while (count < max && a.charAt(count) == b.charAt(count)) count++;
        return count;
    }

    private String normalize(String value) {
        return (value == null ? "" : value.toLowerCase(Locale.ROOT))
                .replaceAll("[\\s\\p{Punct}，。！？：；、“”‘’《》【】（）()]+", "");
    }

    private boolean isFavoritesAlias(String value) {
        String n = normalize(value);
        return n.equals("收藏") || n.contains("我的收藏") || n.contains("喜欢的歌") || n.contains("我喜欢");
    }

    private boolean isHistoryAlias(String value) {
        String n = normalize(value);
        return n.contains("最近播放") || n.contains("最近听") || n.contains("历史");
    }

    private void stopVoiceService() {
        stopping = true;
        commandMode = false;
        recognitionPaused = true;
        main.removeCallbacksAndMessages(null);
        try { if (recognizer != null) recognizer.cancel(); } catch (Exception ignored) { }
        recognizerListening = false;
        removeSystemOverlay();
        publish(VoiceAssistantContract.PHASE_OFF, "", "语音唤醒已关闭", 0f, "", false);
        stopForeground(true);
        stopSelf();
    }

    @Override public void onReadyForSpeech(Bundle params) {
        if (recognitionPaused) return;
        recognizerReady = true;
        consecutiveRecognizerFailures = 0;
        if (!usingOnDeviceRecognizer) systemRecognizerProviderRotations = 0;
        main.removeCallbacks(recognizerReadyWatchdog);
        if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                "已连接 · " + currentEngineLabel() + " · 请说音乐指令", .08f, "", false);
    }
    @Override public void onBeginningOfSpeech() {
        if (recognitionPaused) return;
        // Some OEM recognizers skip onReadyForSpeech but still begin delivering speech/audio
        // callbacks. That is real readiness; cancel the watchdog instead of reporting a false mic
        // timeout while audio is already flowing.
        recognizerReady = true;
        consecutiveRecognizerFailures = 0;
        main.removeCallbacks(recognizerReadyWatchdog);
        if (commandMode)
            publish(VoiceAssistantContract.PHASE_LISTENING, transcript, "听到了 · 继续说", .22f, "", false);
    }
    @Override public void onRmsChanged(float rmsdB) {
        if (recognitionPaused || !commandMode) return;
        if (!recognizerReady) {
            recognizerReady = true;
            consecutiveRecognizerFailures = 0;
            main.removeCallbacks(recognizerReadyWatchdog);
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastRmsBroadcast < 90L) return;
        lastRmsBroadcast = now;
        float level = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 11f));
        publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                recognizerReady ? "已连接 · " + currentEngineLabel() + " · 请说音乐指令" : "正在连接麦克风…", level, "", false);
    }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() {
        if (!recognitionPaused && commandMode)
            publish(VoiceAssistantContract.PHASE_PROCESSING, transcript, "正在识别…", .08f, "", false);
    }
    @Override public void onError(int error) {
        recognizerListening = false;
        recognizerReady = false;
        main.removeCallbacks(recognizerReadyWatchdog);
        if (stopping || recognitionPaused || !VoiceAssistantContract.enabled(this)) return;
        if (commandMode && SystemClock.uptimeMillis() > commandDeadline) {
            commandMode = false;
            publish(VoiceAssistantContract.PHASE_ARMED, "", "没有听到指令 · 继续等待唤醒词", 0f, "", false);
        }
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            VoiceAssistantContract.prefs(this).edit().putBoolean(VoiceAssistantContract.KEY_ENABLED, false).apply();
            publish(VoiceAssistantContract.PHASE_ERROR, "麦克风权限已失效", "重新打开 Lunaxy 并授权后再开启", 0f, "", false);
            stopVoiceService();
            return;
        }

        consecutiveRecognizerFailures++;

        if (Build.VERSION.SDK_INT >= 31 && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
            if (usingOnDeviceRecognizer) {
                requestLocalModelDownload();
                if (VoiceAssistantContract.isLocalOnlyMode(this)) {
                    if (commandMode) publish(VoiceAssistantContract.PHASE_ERROR,
                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ? "中文离线模型还没准备好" : "本地引擎暂不支持中文",
                            Build.VERSION.SDK_INT >= 33
                                    ? "已向系统请求准备中文离线模型 · 完成后再试"
                                    : "请在系统语音设置中下载中文离线识别包",
                            0f, "", false);
                    scheduleListen(4500L);
                    return;
                }
                if (automaticFallbackAllowed()) {
                    switchRecognizer(false, error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                            ? "本地中文模型未就绪，本次暂用系统识别" : "本地中文暂不可用，本次暂用系统识别");
                    return;
                }
            }
            if (commandMode) publish(VoiceAssistantContract.PHASE_ERROR, "中文语音识别不可用",
                    "请切换识别模式，或在系统语音设置中准备中文语言包", 0f, "", false);
            scheduleListen(3000L);
            return;
        }

        if (error == SpeechRecognizer.ERROR_AUDIO) {
            if (commandMode) publish(VoiceAssistantContract.PHASE_ERROR, "麦克风暂不可用",
                    "请关闭正在录音/通话的其他应用后重试", 0f, "", false);
            scheduleListen(1800L);
            return;
        }

        if (Build.VERSION.SDK_INT >= 31 && error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) {
            if (!usingOnDeviceRecognizer && hasUsableNetwork()
                    && rotateSystemRecognizerProvider("系统语音服务刚刚断开")) return;
            createRecognizer(false);
            if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                    "语音服务刚刚断开 · 正在重连", .04f, "", false);
            scheduleListen(900L);
            return;
        }

        if ((error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER) && !usingOnDeviceRecognizer
                && automaticFallbackAllowed() && !alternateRecognizerTried && canTryOnDeviceRecognizer()) {
            switchRecognizer(true, "系统在线识别不可用，改用本地识别");
            return;
        }

        if ((error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER) && !usingOnDeviceRecognizer && hasUsableNetwork()) {
            if (rotateSystemRecognizerProvider("手机网络正常，但当前语音服务没有响应")) return;
        }

        if (error == SpeechRecognizer.ERROR_CLIENT && consecutiveRecognizerFailures >= 2) {
            createRecognizer(false);
            if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                    "语音引擎已重置 · 正在重新连接", .04f, "", false);
            scheduleListen(900L);
            return;
        }

        long delay;
        if (Build.VERSION.SDK_INT >= 31 && error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS) {
            delay = 8_000L;
            if (commandMode) publish(VoiceAssistantContract.PHASE_ERROR, "系统语音服务请求过快",
                    "正在等待系统恢复，请稍后再说一次", 0f, "", false);
        } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            delay = 1_500L;
            if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                    "语音服务正忙 · 正在重试", .04f, "", false);
        } else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER) {
            delay = 2_500L;
            if (commandMode) {
                boolean networkOk = hasUsableNetwork();
                String title = networkOk ? "系统语音服务连接失败" : "当前网络不可用";
                String hint;
                if (networkOk) {
                    hint = VoiceAssistantContract.isSystemOnlyMode(this)
                            ? "手机网络正常 · 是系统 SpeechRecognizer 服务未连上，可切换“本地优先”"
                            : "手机网络正常 · 已尝试系统识别服务，可改用仅本地识别";
                } else {
                    hint = VoiceAssistantContract.isSystemOnlyMode(this)
                            ? "当前是“系统识别”模式 · 联网后重试，或切换到“本地优先”"
                            : "联网后自动恢复；设备支持本地识别时也可使用“仅本地”";
                }
                publish(VoiceAssistantContract.PHASE_ERROR, title, hint, 0f, "", false);
            }
        } else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            delay = commandMode ? 260L : 620L;
            if (commandMode) publish(VoiceAssistantContract.PHASE_LISTENING, transcript,
                    "没有听清 · 请再说一次", .04f, "", false);
        } else {
            delay = 900L;
            if (commandMode) publish(VoiceAssistantContract.PHASE_ERROR, "语音识别暂时失败",
                    "错误码 " + error + " · 正在自动恢复", 0f, "", false);
        }
        scheduleListen(delay);
    }
    @Override public void onResults(Bundle results) {
        recognizerListening = false;
        recognizerReady = false;
        main.removeCallbacks(recognizerReadyWatchdog);
        consecutiveRecognizerFailures = 0;
        if (recognitionPaused) return;
        String heard = bestResult(results);
        if (heard.isEmpty()) { scheduleListen(commandMode ? 180L : 420L); return; }
        if (commandMode) executeRecognizedCommand(heard);
        else if (VoiceCommandParser.containsWakePhrase(heard, VoiceAssistantContract.wakeAliases(this))) wakeFromTranscript(heard);
        else scheduleListen(260L);
    }
    @Override public void onPartialResults(Bundle partialResults) {
        if (recognitionPaused) return;
        String heard = bestResult(partialResults);
        if (heard.isEmpty()) return;
        if (commandMode) {
            String command = VoiceCommandParser.stripWakePhrase(heard, VoiceAssistantContract.wakeAliases(this));
            transcript = command.isEmpty() ? heard : command;
            publish(VoiceAssistantContract.PHASE_LISTENING, transcript, "请说音乐指令", .12f, "", false);
        } else if (VoiceCommandParser.containsWakePhrase(heard, VoiceAssistantContract.wakeAliases(this))) wakeFromTranscript(heard);
    }
    @Override public void onEvent(int eventType, Bundle params) { }

    private String bestResult(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (results == null || results.isEmpty() || results.get(0) == null) return "";
        return results.get(0).trim();
    }

    @Override public void onDestroy() {
        stopping = true;
        main.removeCallbacksAndMessages(null);
        removeSystemOverlay();
        destroyRecognizer();
        musicSearch.destroy();
        worker.shutdownNow();
        if (playbackBound) try { unbindService(playbackConnection); } catch (Exception ignored) { }
        playbackBound = false;
        playback = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
