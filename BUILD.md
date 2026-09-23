# BUILD — Lunaxy Music V92.9.14 Stable

Android Studio is the supported build path.

Current channel: **Stable** · versionName `92.9.14` · versionCode `993` · applicationId `com.xingyu.music`.

- JDK/source: Java 17
- compileSdk / targetSdk: 36 / 36
- minSdk: 26
- Gradle wrapper: 9.5.0
- AGP: 9.2.1

Stable keeps the production package identity and existing app data. Use the existing Stable release signing key for release builds.

## Validation note

本次交付环境无法解析 `services.gradle.org`，Gradle 9.5.0 wrapper bootstrap 无法完成，因此未声称 `assembleDebug` / `assembleRelease` 成功，也未包含伪造 APK。源码级、XML、JVM、回归与双渠道同源审计结果见 `dev-logs/V92.9.14-validation.md`。
