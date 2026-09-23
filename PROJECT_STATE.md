# Lunaxy Music Project State — V92.9.13 / Beta61

Project: Lunaxy Music
Current baseline: **V92.9.13 Motion Unification & Playlist Shared-Hero Reliability**
Stable applicationId: `com.xingyu.music`
Beta applicationId: `com.xingyu.music.beta1`
Stable display name: `Lunaxy Music`
Beta display name: `Lunaxy Music Beta`
Stable versionName: `92.9.13`
Stable versionCode: `991`
Beta versionName: `92.9.13-beta61`
Beta versionCode: `992`
Android Studio root: ZIP root / directory containing `settings.gradle.kts`
JDK/source level: Java 17
Gradle: 9.5.0 wrapper
AGP: 9.2.1
compileSdk: 36
targetSdk: 36
minSdk: 26
Stable signing identity: not embedded; continue using the existing Stable release key
Data migration: **none**. Existing `xingyu_library_v3` song/library JSON, favorites/history/search history, appearance/playback preferences, offline/download state and `lunaxy_voice_v1` remain compatible and are not cleared or renamed.

## V92.9.13 completed

- Removed the full-screen Curtain Reveal from Smart Collection detail pages (including 每日推荐) and Search “全部结果”. These detail pages now enter through normal depth motion while dense music content resolves row by row.
- 每日推荐/私人电台/天气电台/最近播放 reuse the playlist-style per-row `CurtainRevealFrame` staircase, with 62 ms stagger and per-row shimmer phase offsets. Recommendation enrichment remembers already-resolved row identities so later snapshots do not replay the whole entrance.
- Search “全部结果” ListView rows now use the same Curtain Reveal primitive on first presentation instead of a full-viewport travelling light seam; recycled/resolved rows settle immediately.
- Playlist card → detail Shared Object reliability is strengthened: the source card stays visible while destination hero geometry is measured for up to several animation frames, the proxy takes ownership before the source is hidden, and the transform duration is lengthened to 840 ms for a readable slide/grow/deform path.
- Existing 800+ playlist bounded loading, playback/search/recommendation engines, Startup Starfield, Root Tab physics, Mini→Full shared artwork and Lunaxy Voice are preserved.

## Stability boundary

No LibraryStore schema/key changes, no playlist/favorite/history/search-history migration and no playback/source/download/cache/database/Voice persistence migration. Runtime changes are limited to presentation/motion code in `MainActivity`, `CurtainRevealFrame`, and `FluidPlaceholderView`.

## Validation boundary

Runnable regressions and structural checks are recorded in `dev-logs/V92.9.13-validation.md`. A real Android compile/assemble was attempted but cannot complete in this environment because Gradle 9.5.0 is not cached and `services.gradle.org` cannot be resolved. Android Studio remains the authoritative symbol-level build check.

Stable and Beta runtime Java must remain byte-identical; only channel identity/resources/build metadata differ.

Next baseline: final V92.9.13 Stable / Beta61 pair after on-device confirmation.
