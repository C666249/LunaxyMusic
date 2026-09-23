# SELF_TEST — V92.9.14

Recommended validation order:

1. `python tests/V92914ArtworkAccentRegressionTest.py`
2. `python tests/V92913MotionUnificationRegressionTest.py`
3. `python tests/V92912PlaylistHostTypeRegressionTest.py`
4. `python tests/V92911PlaylistChoreographyRegressionTest.py`
5. `python tests/V92910SplashCompileRegressionTest.py`
6. `python tests/V9299SharedHeroAndStartupRegressionTest.py`
7. `python tests/V9298ProgressiveLoadingRegressionTest.py`
8. V92.9.7 → V92.8.10 regression policies
9. Voice policy / compile / UI compatibility tests
10. JVM Motion / Vinyl / Player / Voice Parser tests
11. XML parse + Java structure + Stable/Beta parity + ZIP integrity audit
12. Android Studio `assembleDebug` / `assembleRelease` when Android SDK + Gradle distribution are available

V92.9.14 specifically protects four contracts: row-level Curtain seams use current-playing artwork color, row Skeleton shimmer uses the same accent without phase restart, stale previous-track accents are not leaked while a new cover is unresolved, and playlist detail/Shared Hero material follows the playlist cover color.
