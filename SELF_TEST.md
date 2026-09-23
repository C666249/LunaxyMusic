# SELF_TEST — V92.9.13

Recommended validation order:

1. `python tests/V92913MotionUnificationRegressionTest.py`
2. `python tests/V92912PlaylistHostTypeRegressionTest.py`
3. `python tests/V92911PlaylistChoreographyRegressionTest.py`
4. `python tests/V92910SplashCompileRegressionTest.py`
5. `python tests/V9299SharedHeroAndStartupRegressionTest.py`
6. `python tests/V9298ProgressiveLoadingRegressionTest.py`
7. V92.9.7 → V92.8.10 regression policies
8. Voice policy / compile / UI compatibility tests
9. JVM Motion / Vinyl / Player / Voice Parser tests
10. XML parse + Java structure + Stable/Beta parity + ZIP integrity audit
11. Android Studio `assembleDebug` / `assembleRelease` when Android SDK + Gradle distribution are available

V92.9.13 specifically protects three UI contracts: no full-screen Curtain seam for Daily/Search detail, row-level staircase reveal for those song lists, and reliable playlist Shared Object geometry acquisition before fallback.
