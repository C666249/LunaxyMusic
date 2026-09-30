# Lunaxy Music PC · V92.9.23 Desktop 1

Windows desktop port based on the Lunaxy Music Android V92.9.23 source baseline.

## First desktop build scope
- Windows x64 portable EXE via Electron.
- Starfield / glass desktop UI adapted from the mobile visual language.
- Four-catalog search: NetEase, QQ, Kuwo, Kugou.
- FMV-SELECT-JELLY-001-style provider filter feedback.
- Huibq-compatible streaming URL resolver route (320k -> 128k fallback).
- HTML5 audio playback, queue previous/next, seek, recent history, favorites.
- NetEase LRC lyric display.
- Local audio import.

## Deliberate first-version gaps
The Android-only voice assistant, ringtone maker, Media3-specific route learning, notification controls,
mobile desktop-lyric overlay and full playlist import pipeline are not silently emulated. They require
platform-native desktop replacements and will be ported separately.

## Run locally
```bash
npm install
npm start
```

## Build Windows portable EXE
```bash
npm ci
npm run dist:win
```
