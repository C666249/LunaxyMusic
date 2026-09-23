# Lunaxy Music V92.8.10

当前源码版本：Stable `92.8.10`，versionCode `962`，applicationId `com.xingyu.music`。
本版更新见 [RELEASE_NOTES_V92.8.10.md](RELEASE_NOTES_V92.8.10.md)。

## 构建

使用 Android Studio 打开仓库根目录，按 `app/build.gradle.kts` 和 Gradle Wrapper 的要求安装 SDK / JDK 后同步并构建。源码采用 Java 17，compileSdk / targetSdk 为 36。

## 开源配置与许可

项目原创代码采用 [MIT License](LICENSE)。第三方组件、音源脚本及其原有版权声明仍适用各自许可。

公开源码已清空 `app/src/main/assets/source/huibq-latest.js` 的默认 `API_KEY`。该音源如要求访问密钥，需自行取得授权并在本地配置；没有有效密钥时该音源可能不可用。请勿将个人凭据或签名密钥提交到仓库。

本次发布仅验证源码导入和凭据清理，未重新验证 Android 构建；原始版本验证记录见 `dev-logs/V92.8.10-validation.md`。

## 历史说明：V92.8.3

此目录是完整 Android Studio 源码工程。版本与渠道见 `app/build.gradle.kts`。

本轮说明见 `RELEASE_NOTES_V92.8.3.md`；验证记录见 `dev-logs/V92.8.3-validation.md`。

V92.8.3 继续保留 V92.8 系列 Mini Player → Full Player Morph，并修复 Hero 动画抵达前真实唱片提前出现导致的“双层唱片”问题。星空实验室 450–1000 ms 动画时长设置保持不变。
