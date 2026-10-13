#!/usr/bin/env python3
"""V144 Cloud-only audio coexistence patch. Strict source assertions, no player edits."""
from pathlib import Path
import hashlib

root=Path(__file__).resolve().parents[2]
p=root/'app/src/main/java/com/xingyu/music/agent/AgentVoiceWakeService.java'
g=root/'app/build.gradle.kts'
data=p.read_text()
oldhash=hashlib.sha256(data.encode()).hexdigest()
assert oldhash=='f0f1bc65ec8e44fd07e6afefa0c41f1e418986f60083363134c4435182d7c7ac', 'Wrong V143 Cloud service baseline'

def replace(a,b):
    global data
    n=data.count(a)
    assert n==1, ('expected 1 occurrence, got '+str(n),a[:90])
    data=data.replace(a,b,1)

replace('import android.app.Service;', 'import android.app.Service;\nimport android.content.Context;\nimport android.media.AudioManager;')

replace('    private int speechMode;\n', '''    private int speechMode;
    // V144: some OEM SpeechRecognizer implementations interrupt or duck media audio on
    // every new recognition session. Do NOT continually restart compatibility STT
    // while music is audible. Enrolled personal templates remain always-on.
    private boolean compatPausedForMusic;
    private boolean musicMonitorRunning;
    private final Runnable musicMonitor = new Runnable() {
        @Override public void run() {
            if (!musicMonitorRunning) return;
            if (running && !pausedForForeground && !globalActive && config != null
                    && config.wakeEnabled() && config.wakeTemplateCount() < 2) {
                if (isMusicActive()) {
                    if (!compatPausedForMusic) {
                        compatPausedForMusic = true;
                        stopSpeechRecognizer();
                        updateNotification("音乐播放保护 · 兼容语音监听已暂缓，暂停音乐后恢复；训练个人唤醒可边听边唤醒");
                    }
                } else if (compatPausedForMusic) {
                    compatPausedForMusic = false;
                    scheduleWakeStart(280L);
                }
            }
            main.postDelayed(this, 1000L);
        }
    };

    /** Poll only for compatibility STT; does not change player audio focus or pause playback. */
    private boolean isMusicActive() {
        try {
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            return audio != null && audio.isMusicActive();
        } catch (RuntimeException ignored) { return false; }
    }

    private void startMusicMonitor() {
        if (musicMonitorRunning) return;
        musicMonitorRunning = true;
        main.post(musicMonitor);
    }

    private void stopMusicMonitor() {
        musicMonitorRunning = false;
        main.removeCallbacks(musicMonitor);
        compatPausedForMusic = false;
    }
''')

replace('''            running = false; pausedForForeground = false;
            stopListeningSources();''','''            running = false; pausedForForeground = false;
            stopMusicMonitor();
            stopListeningSources();''')

replace('''            running = true; pausedForForeground = false; speechMode = 0;
            try { startForeground''','''            running = true; pausedForForeground = false; speechMode = 0;
            startMusicMonitor();
            try { startForeground''')

replace('''        running = true; pausedForForeground = false; speechMode = 0;
        try { startForeground''','''        running = true; pausedForForeground = false; speechMode = 0;
        startMusicMonitor();
        try { startForeground''')

replace('''        running = false;
        main.removeCallbacksAndMessages(null);''','''        running = false;
        stopMusicMonitor();
        main.removeCallbacksAndMessages(null);''')

replace('''        if (config.wakeTemplateCount() >= 2) startPersonalWake();
        else startSpeechWake();''','''        if (config.wakeTemplateCount() >= 2) {
            compatPausedForMusic = false;
            startPersonalWake();
        } else if (isMusicActive()) {
            // A fresh Cloud install has no personal templates. Don't activate the
            // repeatedly restarting OEM STT recognizer during media playback.
            if (!compatPausedForMusic) {
                compatPausedForMusic = true;
                stopSpeechRecognizer();
                updateNotification("音乐播放保护 · 兼容语音监听已暂缓，训练个人唤醒可边听边唤醒");
            }
        } else {
            compatPausedForMusic = false;
            startSpeechWake();
        }''')

replace('''        personalThread = new Thread(() -> {
            try { listener.runLoop(); }''','''        personalThread = new Thread(() -> {
            // Low-priority computation; never touch ExoPlayer, focus or playback policy.
            // Only one notch below default to avoid starving 20ms mic reads.
            try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT + 1); }
            catch (RuntimeException ignored) { }
            try { listener.runLoop(); }''')

replace('''    private void startSpeechWake() {
        if (!running || pausedForForeground || restarting) return;''','''    private void startSpeechWake() {
        if (!running || pausedForForeground || restarting || compatPausedForMusic) return;
        if (isMusicActive()) {
            compatPausedForMusic = true;
            stopSpeechRecognizer();
            updateNotification("音乐播放保护 · 已暂缓兼容语音识别");
            return;
        }''')

replace('''            if (!running || pausedForForeground || globalActive) return;
            try {
                stopSpeechRecognizer();''','''            if (!running || pausedForForeground || globalActive || compatPausedForMusic) return;
            if (isMusicActive()) {
                compatPausedForMusic = true;
                stopSpeechRecognizer();
                updateNotification("音乐播放保护 · 兼容监听暂停中");
                return;
            }
            try {
                stopSpeechRecognizer();''')

replace('''    private void scheduleRestart(long delay) {
        if (!running || pausedForForeground) return;''','''    private void scheduleRestart(long delay) {
        if (!running || pausedForForeground || compatPausedForMusic) return;''')

replace('''    @Override public void onError(int error) {
        if (!running || pausedForForeground) return;''','''    @Override public void onError(int error) {
        if (!running || pausedForForeground || compatPausedForMusic) return;''')

replace('''    @Override public void onResults(Bundle results) { inspect(results); scheduleRestart(480L); }''','''    @Override public void onResults(Bundle results) {
        if (compatPausedForMusic) return;
        inspect(results);
        scheduleRestart(480L);
    }''')

after=hashlib.sha256(data.encode()).hexdigest()
assert after=='ffa02b6b2e62b7d3a6c7eb5bd927fdd62d86c4628050aca68b5ab62f15f021e1', 'V144 patch differs from independently validated service'
p.write_text(data)
q=root/'qa/android-project/app/src/main/java/com/xingyu/music/agent/AgentVoiceWakeService.java'
assert q.is_file()
q.write_text(data)

for file in (g, root/'qa/android-project/app/build.gradle.kts'):
    config=file.read_text()
    assert config.count('versionCode = 1433')==1 and config.count('versionName = "143-cloud"')==1
    config=config.replace('versionCode = 1433','versionCode = 1443')
    config=config.replace('versionName = "143-cloud"','versionName = "144-cloud"')
    config=config.replace('LunaxyMusic-V143-Cloud-debug.apk','LunaxyMusic-V144-Cloud-debug.apk')
    config=config.replace('LunaxyMusic-V143-Cloud-release.apk','LunaxyMusic-V144-Cloud-release.apk')
    file.write_text(config)

assert 'applicationId = "com.xingyu.music.cloud"' in g.read_text()
print('Applied validated V144 Cloud coexistence patch; player and original wake templates untouched')
