#!/usr/bin/env python3
"""Bridge V148 Release's original Sherpa KWS Java API to pinned v1.13.8 Kotlin AAR."""
from pathlib import Path
root=Path.cwd()
old='''            OnlineTransducerModelConfig transducer=OnlineTransducerModelConfig.builder()
                .setEncoder(base+"encoder.onnx").setDecoder(base+"decoder.onnx")
                .setJoiner(base+"joiner.onnx").build();
            OnlineModelConfig model=OnlineModelConfig.builder().setTransducer(transducer)
                .setTokens(base+"tokens.txt").setNumThreads(1).setDebug(false).build();
            KeywordSpotterConfig cfg=KeywordSpotterConfig.builder().setOnlineModelConfig(model)
                .setKeywordsFile(base+"keywords.txt").setKeywordsScore(1.5f)
                .setKeywordsThreshold(.30f).build();
            kws=new KeywordSpotter(cfg);
            stream=kws.createStream();'''
new='''            // Official Android AAR uses Kotlin data classes, not Java builders.
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
for prefix in ('app','qa/android-project/app'):
    path=root/prefix/'src/main/java/com/xingyu/music/agent/SherpaWakeListener.java'
    txt=path.read_text()
    assert txt.count(old)==1,('Unexpected KWS source',path)
    path.write_text(txt.replace(old,new))
print('KWS v1.13.8 Android Kotlin API adapter applied to production and QA')
