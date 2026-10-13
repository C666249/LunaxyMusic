#!/usr/bin/env python3
"""V152 Cloud voice rollback: V151 -> V144-style Vosk stream; preserve UI/playback."""
from pathlib import Path
import zipfile,hashlib
root=Path.cwd()
assert (root/"app/build.gradle.kts").is_file()
v143=Path("../input/LunaxyMusic-V143-Cloud-Source.zip")
assert v143.is_file(), "Missing pinned V143 source release"
with zipfile.ZipFile(v143) as z:
    original=z.read("LunaxyMusic-V143-Cloud/app/src/main/java/com/xingyu/music/agent/AgentSpeechRecognizer.java")
assert hashlib.sha256(original).hexdigest()=="78da45918bc99e22bbc2650c95372f7f502ef834a6e6fa5b45a0f0fbed3ecb5b"
for prefix in ("app","qa/android-project/app"):
    agent=root/prefix/"src/main/java/com/xingyu/music/agent"
    (agent/"AgentSpeechRecognizer.java").write_bytes(original)
    for name in ("MultilingualOfflineSpeech.java","SherpaWakeListener.java","SherpaModelStore.java"):
        (agent/name).unlink()
    f=agent/"AgentVoiceWakeService.java";s=f.read_text(encoding="utf-8")
    def sub(a,b):
        global s
        if s.count(a)!=1:raise SystemExit("Unexpected wake source: "+a[:90]+" count "+str(s.count(a)))
        s=s.replace(a,b,1)
    sub('''    private SherpaWakeListener sherpaWake;
    private Thread sherpaThread;
    private volatile boolean sherpaUnavailable;
''',"")
    s=s.replace(" && !shouldUseSherpa()","")
    s=s.replace(" || sherpaThread != null","").replace("sherpaThread != null || ","").replace(" && sherpaThread == null","")
    s=s.replace("final Thread oldMic = sherpaThread != null ? sherpaThread : personalThread;","final Thread oldMic = personalThread;")
    # Remove prewarm which required SenseVoice and extra hot background CPU.
    for indent in ("            ","        "):
        s=s.replace(indent+'''main.postDelayed(() -> { if(running&&!globalActive&&!isMusicActive())
'''+indent+'''    MultilingualOfflineSpeech.prewarm(this); }, 2200L);''',"")
    sub('''        if (shouldUseSherpa()) return "双语 KWS · 等待 “" + config.wakeWord() + "”";
''',"")
    start=s.index("    private boolean shouldUseSherpa() {")
    end=s.index("    private void startListening() {",start)
    s=s[:start]+s[end:]
    sub('''        if (shouldUseSherpa()) {
            compatPausedForMusic = false;
            startSherpaWake();
        } else if (config.wakeTemplateCount() >= 2) {''',
        '''        if (config.wakeTemplateCount() >= 2) {''')
    sub('''        SherpaWakeListener newWake=sherpaWake;sherpaWake=null;
        if(newWake!=null)newWake.stop();
        Thread kws=sherpaThread;sherpaThread=null;
        if(kws!=null)kws.interrupt();
''',"")
    for forbidden in ("SherpaWake","sherpaWake","sherpaThread","shouldUseSherpa","MultilingualOfflineSpeech","SherpaModelStore"):
        if forbidden in s:raise SystemExit("Modern hotword residue: "+forbidden)
    f.write_text(s,encoding="utf-8")
    f=agent/"PersonalWakeListener.java";s=f.read_text(encoding="utf-8")
    assert s.count("MIN_MATCH_THRESHOLD = .49f")==1
    assert s.count("intraMin-.095f")==1
    s=s.replace("MIN_MATCH_THRESHOLD = .49f","MIN_MATCH_THRESHOLD = .63f")
    s=s.replace("intraMin-.095f","intraMin-.055f")
    f.write_text(s,encoding="utf-8")
    f=root/prefix/"build.gradle.kts";s=f.read_text(encoding="utf-8")
    assert s.count('implementation(files("libs/sherpa-onnx-1.13.8.aar"))')==1
    s=s.replace('    implementation(files("libs/sherpa-onnx-1.13.8.aar"))\n',"")
    s=s.replace("versionCode = 1513","versionCode = 1523").replace('versionName = "151-cloud"','versionName = "152-cloud"')
    if 'versionCode = 1523' not in s:raise SystemExit("Unexpected Gradle version")
    assert 'applicationId = "com.xingyu.music.cloud"' in s
    f.write_text(s,encoding="utf-8")
p=root/"scripts/download_models_v148.py"
if p.exists():p.unlink()
for f in ("AgentVoiceWakeService.java","PersonalWakeListener.java","AgentSpeechRecognizer.java"):
    a=(root/"app/src/main/java/com/xingyu/music/agent"/f).read_bytes()
    b=(root/"qa/android-project/app/src/main/java/com/xingyu/music/agent"/f).read_bytes()
    assert a==b,f
print("V152 voice rollback: verified original Vosk streaming, strict 0.63 template floor, no external KWS/ASR; player untouched")
