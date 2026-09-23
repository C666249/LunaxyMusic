# Lunaxy Voice — V92.9.12 集成架构与边界

## 目标

Lunaxy Voice 是 Lunaxy Music 的语音输入层：用中文语音触发已有音乐能力，而不是复制一套播放器。播放、暂停、切歌、队列与歌曲播放仍落到现有 `PlaybackService`；本地收藏/历史/歌单继续由 `LibraryStore` 提供；语音歌曲/歌手搜索通过独立 `VoiceMusicSearch` 调用现有目录 API。

V92.9.3 将 V92.8.14 Voice 分支完整并入 V92.9.2 Apple-fluid / Visual System 主线，并让 Voice UI 遵守当前主题、材质层级、Root Tab 连续性、Reduced Motion 与 Reduced Transparency 规则。

## 识别路由

### 本地优先（默认）
- Android 12+ 且设备暴露 on-device recognizer 时优先创建本地识别器。
- 中文模型未下载/不可用时：Android 13+ 可请求系统准备模型；自动模式允许本次临时回退系统识别。
- 系统识别网络错误时，自动模式可尝试本地通道，但不会无限来回切换。

### 仅本地
- 只创建 `SpeechRecognizer.createOnDeviceSpeechRecognizer()`。
- 不主动回退默认系统/在线识别。
- `ERROR_LANGUAGE_UNAVAILABLE` 时请求 `triggerModelDownload()`；设置页也可主动“准备中文离线模型”。
- 若 ROM 没有本地 recognition service，会明确显示本机不可用。

### 系统识别
- 使用 `SpeechRecognizer.createSpeechRecognizer()`。
- Android 默认实现可能把音频交给远端服务，因此此路径可能需要联网；它是 ROM 兼容 fallback，不等于“离线”。

## Runtime 结构

- `voice/VoiceAssistantService.java`：microphone foreground service、识别路由、唤醒状态机、错误恢复、通知与可选系统浮窗。
- `voice/VoiceAssistantContract.java`：Voice preference、识别模式、广播/Intent 常量。
- `voice/VoiceCommand.java` / `VoiceCommandParser.java`：纯 JVM、中文优先、确定性命令解析。
- `voice/VoiceMusicSearch.java`：歌曲/歌手候选发现；复用现有 Netease/QQ/Kuwo/Kugou API 类。
- `ui/VoiceAssistantPanel.java` / `VoiceOrbView.java` / `VoiceGlassDrawable.java`：主题感知的 transient Voice 层。

## 状态机

`ARMED → WAKE → LISTENING → PROCESSING → RESULT/ERROR → ARMED`

只有系统真正回调 `onReadyForSpeech()` 后才显示已连接；错误态提供 Voice 设置入口。快速 phase 更新会从当前 presentation 状态 retarget，不依赖装饰动画完成。

## 当前指令

- 下一首 / 上一首 / 下或上 2–10 首
- 暂停 / 继续播放
- 播放指定本地歌单 / 收藏 / 最近播放
- 搜索歌曲 / 搜索歌手
- 放某歌手的歌
- 播放指定歌曲

## V92.9.3 UI 集成

- V92.9.5 起 Home 搜索框不再承载 Mic。Mic 与引擎矩阵统一收进标题右上角纵向渐进工具抽屉；Voice 已启用时轻点 Mic 直接聆听，未启用时进入设置，长按始终进入 Voice 设置。
- Mic 使用当前主题的渐进工具 surface；一级工具按钮保持 48dp hit target，视觉 surface 更紧凑。
- In-app Voice 面板和后台可选液态浮窗都支持深空 / OLED / 月白 / 跟随系统，并遵守 Reduced Transparency。
- Voice Orb 的持续呼吸/音量动画在 Reduced Motion 或系统动画关闭时退化为静态/低幅状态反馈。
- Voice 搜索会保存当前 Root Tab context，再以现有相邻整页动画进入 Search root；搜索结果仍使用现有 Search 页，而不是建立第二套结果 UI。
- Back 在 Voice 浮层可见时优先收起该 transient layer，不破坏 Player/Sheet/Tab 的既有返回层级。

## 隐私与现实边界

- Lunaxy 不保存原始麦克风录音。
- 后台唤醒使用 microphone foreground service，Android 会显示麦克风/前台服务指示。
- 系统 SpeechRecognizer 可能联网，也不适合承诺无限持续识别；当前实现是普通 App 能力边界内的 best-effort 增强。
- 完全跨 ROM、完全离线中文仍需要随 App 分发独立 ASR 模型；V92.9.3 没有引入该类大型依赖。
- Stable 与 Beta 不建议同时开启后台监听，避免两个独立沙箱同时竞争麦克风。


## V92.9.4 Overlay / Network refinement

- `SpeechRecognizer` errors are no longer assumed to mean the phone is offline. The service first checks validated connectivity, discovers installed `RecognitionService` providers, and may rotate providers before surfacing a provider/server failure.
- System overlay supports direct-manipulation gestures: left = snooze 10 min, right = open Lunaxy, down = compact.
- Compact state is a real top-left WindowManager capsule; tapping expands it. Collapse and expansion are staged inverse transforms rather than unrelated hide/show animations.
- Compact/snooze preferences are persisted in `lunaxy_voice_v1`; snooze hides the overlay without stopping Voice itself.


## V92.9.5 Recognizer / continuity refinement

- 系统识别首次连接使用 Android 默认 `SpeechRecognizer.createSpeechRecognizer()`，不预先强制显式 RecognitionService；只有真实 provider/server/network 错误后才枚举并轮换显式 provider。
- 网络判定以活动网络的 `NET_CAPABILITY_INTERNET` 为核心，不再要求 `NET_CAPABILITY_VALIDATED`，避免 OEM/地区网络下普通联网可用却被 Android 验证探针误判离线。
- ready watchdog 调整为本地约 3.8 秒、系统约 5.2 秒。若 OEM 跳过 `onReadyForSpeech()` 但已经发送 RMS 或 `onBeginningOfSpeech()`，这些回调直接计为 ready 并取消 watchdog。
- 桌面 Banner 向下拖动时 `VoiceGlassDrawable.compactProgress` 与手势同步，放手后从当前几何状态 settle；正式 compact / expand 也走同一连续变量，避免材质形状中途跳变。


## V92.9.6 UI-only note

本版没有改变 Voice recognizer / command parser / foreground-service 行为；仅延续既有首页工具抽屉入口。V92.9.6 的主要变更位于 Search / Full Player / FluidToolDock / Mini→Full 视觉连续性。


## V92.9.7 production-performance note

本版没有改变 Voice recognizer / command parser / foreground-service / overlay gesture 行为。正式版性能收口集中在长歌单虚拟列表、歌单内后台筛选、搜索结果稳定增量，以及首页 FluidToolDock 的重叠级联节奏；Voice 数据与 `lunaxy_voice_v1` 偏好保持原样。


## V92.9.8 progressive-loading note

本版未修改 Voice 指令、识别、网络 fallback、桌面 Banner 手势或持久化 key；变更集中在音乐页面的渐进装载与导航响应。


## V92.9.9 shared-object / startup note

- Voice 识别、命令 parser、Foreground Service、桌面 Banner 手势与播放路由没有在本版改写。
- 冷启动 Library hydration 移到 UI shell / Starfield 已挂载之后执行；Voice launch Intent 会在首批 Library 状态接管后处理，避免大型正式数据让启动窗口停在系统灰底。
- Voice 仍复用既有 PlaybackService / LibraryStore / SourceCoordinator 能力，不建立第二套播放器或数据格式。


## V92.9.10 splash compile hotfix note

本版不修改 Voice recognizer、parser、foreground service、overlay gesture 或 `lunaxy_voice_v1` 持久化；仅修复 Android 12+ Splash exit callback 的编译错误。
