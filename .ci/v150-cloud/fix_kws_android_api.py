#!/usr/bin/env python3
"""V150 CI-only repair: official sherpa-onnx Android Kotlin data classes, not Java builder."""
from pathlib import Path

root=Path.cwd()
p=root/"app/src/main/java/com/xingyu/music/agent/SherpaWakeListener.java"
old="""            OnlineTransducerModelConfig transducer=OnlineTransducerModelConfig.builder()
                .setEncoder(base+"encoder.onnx").setDecoder(base+"decoder.onnx")
                .setJoiner(base+"joiner.onnx").build();
            OnlineModelConfig model=OnlineModelConfig.builder().setTransducer(transducer)
                .setTokens(base+"tokens.txt").setNumThreads(1).setDebug(false).build();
            KeywordSpotterConfig cfg=KeywordSpotterConfig.builder().setOnlineModelConfig(model)
                .setKeywordsFile(base+"keywords.txt").setKeywordsScore(1.5f)
                .setKeywordsThreshold(.30f).build();
            kws=new KeywordSpotter(cfg);
            stream=kws.createStream();"""
new="""            // Official Android AAR exposes Kotlin data classes through mutable Java setters.
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
            stream=kws.createStream("");"""
s=p.read_text()
if s.count(old)!=1:
    raise SystemExit("Unexpected source drift; refusing to patch sherpa KWS")
s=s.replace(old,new)
p.write_text(s)
q=root/"qa/android-project/app/src/main/java/com/xingyu/music/agent/SherpaWakeListener.java"
assert q.is_file()
q.write_text(s)
assert ".builder()" not in s
print("V150 KWS Android Kotlin AAR adapter applied to production + QA")
