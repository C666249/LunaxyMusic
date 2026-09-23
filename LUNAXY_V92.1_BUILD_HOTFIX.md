# LunaxyMusic V92.1 Build Hotfix

## Trigger
Android Studio real compile of V92 failed at `LyricLineView.java:107`:

`cannot find symbol: method clearShadowLayer()`

## Root cause
`LyricLineView` extends `TextView`. `TextView` exposes `setShadowLayer(...)`, but does not expose a matching `clearShadowLayer()` method. The clear API belongs to the backing `Paint/TextPaint`.

## Fix
V92:
`else clearShadowLayer();`

V92.1:
`else getPaint().clearShadowLayer();`

The official Android `Paint` API has provided `clearShadowLayer()` since API 1, so this is compatible with Lunaxy's minSdk 26.

## Scope freeze
This is deliberately a build-only hotfix. Playback, provider routing, Personalization Engine, ListenBrainz, Search Engine V2, cover cache, lyric cache, and V92 Motion behavior are unchanged.
