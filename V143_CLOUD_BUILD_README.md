# Lunaxy Music V143 · three independent Android builds

This repository's existing `main` branch is an older code baseline. **Do not build main as V143.** The newer V143 full source is delivered as three independent source ZIPs and a combined, SHA-256-verified source bundle.

## Build V143 APKs on GitHub Actions

1. Upload `LunaxyMusic-V143-Three-Sources-Bundle.zip` (the ZIP supplied in the ChatGPT delivery) to a **GitHub Release asset** in this repository. A source archive may contain proprietary code or large offline speech models; check repository visibility first.
2. Copy the direct HTTPS URL for the Release asset.
3. Open Actions → **Lunaxy V143 Three-Channel APK Build** → Run workflow.
4. Enter `bundle_url` and the exact `bundle_sha256` from the checksum text supplied with the delivery.
5. The workflow downloads/validates the bundle, extracts each independent Android project, compiles 3 apps, signs Cloud independently, verifies APK metadata, and uploads 3 APK artifacts.

Source bundle SHA256:
`624f782deb57709bf4503b876f20ad4073f80bebd97a4d57bc6bce40c92d5bb8`

## Package identities

| Channel | Android applicationId | versionCode | Note |
|---|---|---|---|
| Beta | `com.xingyu.music.beta1` | 1431 | independent Beta app data |
| Stable | `com.xingyu.music` | 1432 | update requires the **original** Stable signing certificate |
| Cloud | `com.xingyu.music.cloud` | 1433 | independent Cloud app identity & signing key |

**Signer warning:** Beta and Stable are assembled as CI debug APKs by the generic build workflow. Debug signatures generated on CI may **not** update previously installed builds. Do not uninstall Stable or clear local data to work around a signature mismatch. To update Stable in place, reuse the existing original Stable certificate, which is not stored in this repository.

For persistent Cloud signing, add GitHub Actions repository secrets:
- `LUNAXY_CLOUD_KEYSTORE_BASE64`
- `LUNAXY_CLOUD_KEYSTORE_PASSWORD`
- `LUNAXY_CLOUD_KEY_PASSWORD`

The dedicated Cloud JKS is distributed **separately** in the private signing backup, and must never be committed to this public GitHub repository. Without secrets the workflow generates a temporary independent Cloud signing key, which **cannot guarantee future in-place upgrades**.

## Safety and limits

- Only V142's global floating Agent card and wake service flow are modified for V143 UI/input.
- Personal wake templates, MFCC/DTW, Vosk speech recognition, API configuration, music playback, and main activity are preserved.
- The floating card is limited to a third of the screen and supports text send, voice dictation, Agent traces, manual cancellation, and a 5-second inactivity fade after a turn.
- Although all three apps can be installed together, only **one** should be enabled for always-on microphone wake detection to avoid AudioRecord contention.
- Source tests are not a substitute for Android compile or real-device QA.
