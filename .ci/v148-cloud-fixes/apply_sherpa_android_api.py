#!/usr/bin/env python3
"""V148 Cloud hotfix: use Kotlin-based Android AAR API instead of Java builder API.
Verified on V148 Release sources, original playback and wake architecture untouched.
"""
from pathlib import Path

root = Path.cwd()
if not (root / "app/build.gradle.kts").is_file():
    raise SystemExit("Execute in extracted V148 Cloud Android source root")
replacements = {
    "SherpaWakeListener.java": (
        '''            OnlineTransducerModelConfig transducer=OnlineTransducerModelConfig.builder()
                .setEncoder(base+"encoder.onnx").setDecoder(base+"decoder.onnx")
                .setJoiner(base+"joiner.onnx").build();
            OnlineModelConfig model=OnlineModelConfig.builder().setTransducer(transducer)
                .setTokens(base+"tokens.txt").setNumThreads(1).setDebug(false).build();
            KeywordSpotterConfig cfg=KeywordSpotterConfig.builder().setOnlineModelConfig(model)
                .setKeywordsFile(base+"keywords.txt").setKeywordsScore(1.5f)
                .setKeywordsThreshold(.30f).build();
            kws=new KeywordSpotter(cfg);
            stream=kws.createStream();''',
        '''            // Android AAR v1.13.8 exports Kotlin data classes, not Java builders.
            OnlineTransducerModelConfig transducer=new OnlineTransducerModelConfig();
            transducer.setEncoder(base+"encoder.onnx");
            transducer.setDecoder(base+"decoder.onnx");
            transducer.setJoiner(base+"joiner.onnx");
            OnlineModelConfig model=new OnlineModelConfig();
            model.setTransducer(transducer);
            model.setTokens(base+"tokens.txt");
            model.setNumThreads(1);
            model.setDebug(false);
            KeywordSpotterConfig cfg=new KeywordSpotterConfig();
            cfg.setModelConfig(model);
            cfg.setKeywordsFile(base+"keywords.txt");
            cfg.setKeywordsScore(1.5f);
            cfg.setKeywordsThreshold(.30f);
            kws=new KeywordSpotter(null,cfg);
            stream=kws.createStream("");'''
    ),
    "MultilingualOfflineSpeech.java": (
        '''            OfflineSenseVoiceModelConfig sense=OfflineSenseVoiceModelConfig.builder()
                .setModel(new File(root,"model.int8.onnx").getAbsolutePath())
                .setLanguage("auto").setInverseTextNormalization(true).build();
            OfflineModelConfig model=OfflineModelConfig.builder().setSenseVoice(sense)
                .setTokens(new File(root,"tokens.txt").getAbsolutePath())
                .setNumThreads(1).setDebug(false).build();
            recognizer=new OfflineRecognizer(OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(model).build());''',
        '''            // SenseVoice Java bridge to the official Kotlin-class Android AAR.
            OfflineSenseVoiceModelConfig sense=new OfflineSenseVoiceModelConfig();
            sense.setModel(new File(root,"model.int8.onnx").getAbsolutePath());
            sense.setLanguage("auto");
            sense.setUseInverseTextNormalization(true);
            OfflineModelConfig model=new OfflineModelConfig();
            model.setSenseVoice(sense);
            model.setTokens(new File(root,"tokens.txt").getAbsolutePath());
            model.setNumThreads(1);
            model.setDebug(false);
            OfflineRecognizerConfig cfg=new OfflineRecognizerConfig();
            cfg.setModelConfig(model);
            recognizer=new OfflineRecognizer(null,cfg);'''
    )
}
for name, (old, new) in replacements.items():
    production = root / "app/src/main/java/com/xingyu/music/agent" / name
    qa = root / "qa/android-project/app/src/main/java/com/xingyu/music/agent" / name
    code = production.read_text(encoding="utf-8")
    if code.count(old) != 1: raise SystemExit(f"Source drift for {name}: {code.count(old)} matches")
    code = code.replace(old, new)
    if ".builder()" in code: raise SystemExit(f"Unadapted Java builder in {name}")
    production.write_text(code, encoding="utf-8")
    if not qa.is_file(): raise SystemExit("QA mirror missing: " + str(qa))
    qa.write_text(code, encoding="utf-8")
    print("Patched SDK API:", name)
assert 'applicationId = "com.xingyu.music.cloud"' in (root/"app/build.gradle.kts").read_text()
print("V148 official sherpa-onnx Android AAR API adapter ready")
