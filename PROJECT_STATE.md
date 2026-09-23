# Lunaxy Music Project State — V92.9.14 / Beta62

Project: Lunaxy Music
Current baseline: **V92.9.14 Artwork-Driven Loading & Playlist Hero Color**
Stable applicationId: `com.xingyu.music`
Beta applicationId: `com.xingyu.music.beta1`
Stable display name: `Lunaxy Music`
Beta display name: `Lunaxy Music Beta`
Stable versionName: `92.9.14`
Stable versionCode: `993`
Beta versionName: `92.9.14-beta62`
Beta versionCode: `994`
Android Studio root: ZIP root / directory containing `settings.gradle.kts`
JDK/source level: Java 17
Gradle: 9.5.0 wrapper
AGP: 9.2.1
compileSdk: 36
targetSdk: 36
minSdk: 26
Stable signing identity: not embedded; continue using the existing Stable release key
Data migration: **none**. Existing `xingyu_library_v3` song/library JSON, favorites/history/search history, appearance/playback preferences, offline/download state and `lunaxy_voice_v1` remain compatible and are not cleared or renamed.

## V92.9.14 completed

- Row-level progressive loading now uses the current playing song artwork accent for both Skeleton shimmer and the travelling `CurtainRevealFrame` seam. Search-All, Smart Collection and playlist-detail staircase rows share this color source.
- `playlistPlaybackAccentKey` binds the accent to the exact current song/artwork identity so an unresolved new cover cannot accidentally reuse the previous track's color.
- When the current artwork resolves after the row animation has started, visible progressive rows are retinted in place without restarting shimmer phase or reveal timing.
- Playlist detail Hero material now derives from the playlist's first artwork. Cached art colors the first frame; async resolution performs a short material color interpolation and updates the shared-object proxy/snapshot as well.
- Existing V92.9.13 row-by-row motion, playlist Shared Hero geometry retries, 800+ playlist bounded loading, playback/search/recommendation engines, Startup Starfield, Root Tab physics, Mini→Full shared artwork and Lunaxy Voice are preserved.

## Stability boundary

No LibraryStore schema/key changes and no playlist/favorite/history/search-history/playback/source/download/cache/database/Voice persistence migration. Runtime changes are limited to presentation/color behavior in `MainActivity`, `FluidPlaceholderView`, `CurtainRevealFrame`, and `PlaylistHeroMorphView`.

## Validation boundary

Runnable regressions and structural checks are recorded in `dev-logs/V92.9.14-validation.md`. A real Android compile/assemble is attempted when possible; if the local environment lacks the Gradle 9.5.0 distribution or Android SDK, only static/JVM/regression validation is claimed and Android Studio remains the authoritative symbol-level build check.

Stable and Beta runtime Java must remain byte-identical; only channel identity/resources/build metadata differ.

Next baseline: final V92.9.14 Stable / Beta62 pair after on-device confirmation.
