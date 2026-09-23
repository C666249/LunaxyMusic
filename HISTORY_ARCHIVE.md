# HISTORY_ARCHIVE — Lunaxy Music

> 近期 Stable 详细历史由根目录最近 10 份 Release Notes 保留。本文件只压缩仍影响当前架构、兼容和回归判断的更早长期事实。

## 长期身份与升级链

- Stable identity 长期固定为 `com.xingyu.music`；不能为了并装/测试改变正式包名。
- Beta 使用独立 `com.xingyu.music.beta1`，与 Stable 数据隔离。
- 播放数据、数据库、SharedPreferences、Provider authority、签名升级链都属于高风险兼容面。

## V85–V90：数据、推荐、搜索与洞察主线

- V85：远程封面 LRU、歌词缓存 identity 强化、播放详情歌手直搜；播放 URL 解析与歌词/封面缓存保持隔离。
- V86：Personalization Engine V2，行为数据库 + 7/30/180 天画像 + 匿名只读 ListenBrainz 协同发现。外部协同失败必须退回本地，绝不能阻断播放；个性化层不保存/生成/复用临时音频 URL。
- V87：Intelligence Surface + Search Engine V2，引入本地听过账本、推荐 why/origin、搜索 SWR metadata cache、自适应 Provider 与持久搜索 Shell。播放平面继续冻结。
- V88：推荐 Fast Path / snapshot-first，本地或缓存必须先出首屏，远端后台补全，不把外部协同重新放回首屏关键路径。
- V89/V90：矩阵/洞察从首页正文收起，统一到上下文入口和三页矩阵中心；不因分析 UI 修改播放、缓存或推荐召回核心。

## V91–V92.5：Motion 稳定性红线

- V91 引入平台 ViewPropertyAnimator 驱动的 SpringMotion。
- V91.1 修复系统 DragShadow + ghost 叠加、translation 命中反馈导致的抖动：拖拽必须单一浮动实体、1:1 跟手、layout-space 命中、hysteresis、拖动期不 notify 重建，松手一次性提交。
- V92 Motion System 2.0 引入 Mini→Full、Hero、图标 morph、唱片手势、Bottom Sheet 直接拖动等。拖拽落位后退出 App 的历史问题决定了“提交真实顺序 / Modal 收尾 / 父页面刷新”必须分阶段。
- V92.1 的 TextView `clearShadowLayer()` 构建问题说明：静态 parse 不能冒充 Android API 符号级编译。
- V92.2：不再激进显式 recycle 刚参与硬件动画的 Bitmap；若真机崩溃，先看 Logcat FATAL/ANR/SIGSEGV，不靠改动画参数猜。
- V92.3–V92.5：唱片/队列采用 retained-window 连续浏览和真实 layout handoff；拖动阶段不触发真实播放解析，最终 commit 才 `playAt`。当前歌曲 Halo 与队列落位强调“同一对象/同一时钟”的视觉连续性。

## 仍有效的开发判断

1. 播放、推荐、搜索、缓存是相互隔离的稳定平面；视觉需求不得顺手侵入。
2. Motion 问题优先修触摸所有权、View 生命周期、布局命中和渲染时序，不通过增大 overshoot 或时长掩盖。
3. 推荐性能坚持 local/snapshot-first、remote background enrichment。
4. 真机 60/90/120Hz 录像与 Logcat 比桌面静态推断更有价值。

## V92.5.1：唱片浏览手势兼容事实

- 唱片一首默认拖动距离提升至屏宽约 36%，提交阈值与速度投影受到约束；切歌滑动灵敏度与手机晃动灵敏度分离。
- 浏览过程中按歌曲 identity 回填封面、保持五槽 retained window；取消/提交后以真实播放中心重新绑定。拖动阶段不触发真实播放解析，最终 commit 才进入播放链。

## V92.6：唱片翻面歌词与列表滚动长期事实

- 常态单唱片、点击唱片翻面进入歌词；长按唱片或播放列表入口可展开多唱片浏览。歌词滚动/定位与唱片横向浏览保持触摸所有权隔离。
- 列表快速滚动与拖拽边缘自动滚动继续依赖原生列表/惯性，不另造第二套物理系统；拖拽仍遵守“视觉阶段不反复重建、最终一次提交”的长期红线。
- 桌面歌词简化为单遍原生文字绘制并用半透明底保证清晰度；播放解析、下载、歌词解析、封面缓存算法未因该视觉调整改变。

## V92.7：详情返回与滚动恢复

- V92.7 的长期价值是 Root/Detail 返回后保持阅读位置、列表状态与播放上下文；这一点后来成为 V92.9 Root Tab context preservation 的前置基础。
- 此版本详细 Release Notes 已在 V92.9.3 历史窗口滚动时压缩进入本节。

## Lunaxy Voice 独立分支 V92.8.11–V92.8.14 → 合并至 V92.9.3

- Voice 分支从用户指定的 V92.8.10 播放主线开发，核心目标是把语音作为音乐输入层，而不是创建第二套播放器。
- V92.8.11 建立 microphone foreground service + SpeechRecognizer、唤醒/聆听/处理/结果状态机、中文音乐命令与液态 Voice 面板。
- V92.8.12–V92.8.13 继续修复识别 ready/watchdog、错误恢复、通知节流、direct-listen 与 ROM 兼容。
- V92.8.14 引入 `本地优先 / 仅本地 / 系统识别` 三模式、Android 13+ 中文模型准备与更简洁的 Voice 设置页；系统 SpeechRecognizer 可能联网，完全跨 ROM 离线中文仍需要随 App 分发的 ASR 模型。
- 该分支的 Beta 已使用到 versionCode `970`，因此 V92.9.3 合并版从 Stable `971` / Beta `972` 继续，确保两条历史都可覆盖升级。

## V92.9.4 历史窗口维护
- V92.9.4 加入详细 Release Notes 后，Stable 的 V92.8 详细文件滚出最近 10 版窗口；Beta 的 beta49 滚出最近 3 个 Beta 窗口。更早但仍影响兼容/架构的规则继续由本归档保留。

- V92.8.1 detailed release note archived when V92.9.5 entered the 10-Stable rolling window.

- V92.8.2 detailed release note archived when V92.9.6 entered the 10-Stable rolling window; its still-relevant search/playback/UI compatibility facts remain summarized above.

- V92.8.3 detailed release note archived when V92.9.7 entered the 10-Stable rolling window. Its still-relevant playback/search/UI compatibility rules are already represented in the V85–V92.5 long-term sections above.

- V92.8.9 detailed release note archived when V92.9.8 entered the 10-Stable rolling window. Its still-relevant compatibility and Motion findings remain preserved by the later regression suites and this archive.

- V92.8.10 detailed release note archived when V92.9.9 entered the 10-Stable rolling window. Its UI/policy compatibility contract remains actively enforced by `V92810UiPolicyTest.py`, and the Stable identity/data-upgrade rules remain preserved by current project documentation.


- V92.9.6-beta54 detailed Beta note archived when beta57 entered the rolling 3-Beta window. Beta runtime remains same-source with Stable apart from channel identity.


## V92.9.10 历史窗口维护

- V92.9.0 detailed Stable release note rolled out of the latest-10 window when V92.9.10 entered; its Visual System / Root Tab continuity rules remain preserved in `WORKFLOW.md` and regression tests.
- Beta keeps the latest three detailed notes (beta56–beta58); older beta55 remains represented by current regressions and long-term project documentation.


## V92.9.10：SplashScreenView API compile regression

- V92.9.9 的 Android 12+ Splash exit handoff 错把 `SplashScreenView` 当作 provider 并调用不存在的 `getView()`，真 Android Studio `compileDebugJavaWithJavac` 因此失败。
- Framework `setOnExitAnimationListener` 回调参数本身就是 `SplashScreenView`（一个 `FrameLayout`/View），应直接动画该对象并在结束时调用 `remove()`。
- 这再次确认：lexical/parser 静态检查不能替代 Android SDK 符号级编译；以后新增 framework API 路径必须尽可能接受真实 Android Studio/SDK 编译验证，并保留专门回归。

## Archived Beta detail: V92.9.8-beta56

Beta56 introduced the progressive Skeleton/Curtain loading architecture used as the base for later large-playlist polish. Current behavior is protected by V92.9.8+ regressions.

## V92.9.12：Shared-transition Java type compile regression

- V92.9.11 declared `activePlaylistDetailSongHost` as plain `View` but enumerated child rows with `getChildCount()/getChildAt()`, causing six Android Studio javac errors.
- The host is created as `LinearLayout`; V92.9.12 aligns the field type with the real container and adds a regression scan for plain-`View` fields used with child-container APIs.
- This is a compile-only correction; playlist data, Shared Element choreography and progressive loading behavior remain unchanged.

- Beta rolling window now keeps beta58–beta60; beta57 remains represented by current regressions and long-term docs.

## V92.9.14 历史窗口维护

- V92.9.1–V92.9.4 的独立 validation 记录已滚出最近 10 个 Stable 详细窗口；其中仍影响当前实现的 Root Tab/Light Theme、播放控件、Voice 合并、Moonlight/Fluid 规则继续由 `WORKFLOW.md`、现有回归测试和本归档长期保留。
- V92.9.14 的长期新增规则是：密集歌曲列表的行级加载色跟随当前播放封面，且必须按歌曲 artwork identity 防止上一首颜色串入；歌单详情 Hero/Shared Object 跟随歌单封面取色。
- Beta 详细窗口继续只保留当前 Beta62 与最近少量 Beta 关键记录；Stable/Beta 业务 Java 保持同源。
