# Lunaxy Music — 项目续作工作流记忆

此文件必须随每一份 Full Source ZIP 一起保留。新会话接手时先读本文件，并继续按此流程开发；不要从旧版本或记忆重造源码。

## 固定交付方式

1. 每轮以用户提供/确认的**最新完整源码 ZIP**作为唯一主线底座。
2. 一次对话内尽量完成多阶段工作：完整读源码与交接文档 → 架构/回归风险检查 → 方案定位 → 实现 → 第一轮自审 → 修复 → 第二轮回归 → Stable/Beta 身份审计 → 尽可能真实构建 → 打包校验 → 双 ZIP 交付。
3. 每轮必须交付两份**完整、可独立直接用 Android Studio 打开的源码 ZIP**：
   - Stable / 正式版：固定 `applicationId=com.xingyu.music`，不得为了安装方便改正式身份；同签名覆盖升级时应继承正式版数据。
   - Beta / 测试版：固定使用独立测试 applicationId（当前 `com.xingyu.music.beta1`），可与 Stable 并存，数据隔离；App 名必须带 Beta，启动图标必须有明显 β 标识。
4. Beta 与 Stable 的功能源码应同源；除包名、版本/App 名、β 图标和必要测试身份差异外，不制造功能行为分叉。
5. ZIP 命名默认使用：`LunaxyMusic-V<版本>-Stable.zip` / `LunaxyMusic-V<版本>-Beta.zip`。
6. 每份 ZIP 保留本工作流文件、当轮变更说明、自测结果与 `SHA256SUMS.txt`。

## 构建与命名

- 官方支持路径：**Android Studio 直接打开含 `settings.gradle.kts` 的根目录构建**。
- 不默认依赖或随包新增 `BUILD_APK.bat` / `build-apk.bat` / `build-both.bat`。
- Android Studio/Gradle 默认 `app-debug.apk` / `app-release.apk` 不能作为长期正式识别名；额外导出的 APK 文件名至少包含项目、渠道、versionName、buildType。
- 若当前执行环境缺 Android SDK、Gradle 分发或网络，必须明确写入自测结果，不能把词法/静态检查冒充 Android 真编译。

## 修改边界

- 用户没有点名的 UI、播放、歌词、下载、Provider、缓存、路由、桌面歌词等稳定链默认保持不动。
- 若需求必须触及核心，只做满足需求所需的最小改动；不顺手大重构、不私自改变产品逻辑。
- 能从最新源码和既有约定明确推导的直接完成；只有会明显改变用户行为且无法唯一判断时才询问。
- Stable 不新增仅为开发方便的测试入口；Beta 的测试身份不得污染正式数据。

## 验证要求

每轮至少检查：
- 关键 Java/Kotlin 语法/词法与高风险表达式；
- AndroidManifest / XML 可解析；
- 关键需求路径断言与边界条件；
- 旧稳定链是否被意外修改；
- Stable/Beta 功能源码一致性；
- applicationId / versionCode / versionName / App 名 / β 图标；
- APK 输出命名；
- ZIP 可解压与 `SHA256SUMS.txt` 一致性。

## 当前主线

V85 在 V84“播放会话历史记忆”上增加：
- 远程封面 24 MB 内存 + 160 MB 磁盘 LRU 缓存；
- 歌词缓存身份增强（provider 原生 ID + 跨平台元数据兜底，并兼容 V20-V84 旧缓存）；
- 播放详情歌手文字保持原视觉但可点击，直达搜索页并自动搜索对应歌手。

V85 **没有改动** PlaybackService / SourceCoordinator / PlaybackUrlStore 的既有音频播放与失效换链机制；封面和歌词缓存与音频播放 URL 解析保持隔离。


## V85.1 热修续作说明

V85.1 仅修复播放详情 `歌手 · 专辑` 信息行的可靠显示与点击搜索：恢复普通 TextView 文本渲染，使用透明点击热区进入歌手搜索。V85 的封面/歌词缓存与 V84 播放核心保持不变。后续版本以 V85.1 为最新主线。

## V86 主线续作说明

V86 将推荐升级为 `Lunaxy Personalization Engine V2`：SQLite 行为数据库（原始事件 + 歌曲/歌手累计统计 + artist_daily_stats 日聚合）、明确的 7/30/180 天与 Session Taste Profile、多路候选/多样性排序、日推与私人电台分策略，并加入匿名只读 ListenBrainz LB Radio 协同发现（MusicBrainz Artist Search 仅用于 Artist MBID 映射，带本地长缓存和限流）。自然播放 progress 约 30 秒聚合写盘；原始事件有界保留，长期趋势由日聚合承担，避免重度听歌挤掉长期画像。

重要红线：个性化数据库与 ListenBrainz 只处理行为/歌曲元数据，不保存、不生成、不复用音频临时 URL；`PlaybackService / SourceCoordinator / PlaybackUrlStore` 仍是独立播放平面。外部推荐服务失败必须自动退回本地推荐，不得阻断搜索、播放或旧推荐兜底。

## V87 主线续作说明

V87 在 V86 Personalization Engine V2 上增加 Intelligence Surface 与 Search Engine V2：

- `Personalization Engine V2.1` 用完整长期 `track_stats` 作为 Lunaxy 本机“听过”账本；候选充足时日推目标约 55% 未听过、私人电台约 25% 未听过，并把每首歌的 origin / why / unheard / score 固化到推荐快照。
- 首页增加「个性化矩阵」，可视化 Session / 7天 / 30天 / 180天兴趣、学习成熟度、当前会话行为、日推/私人电台新发现占比与 ListenBrainz 构成；推荐合集支持“为什么推荐这首”。
- `Search Engine V2` 新增 24h / 32 query 的 metadata stale-while-revalidate 缓存、自适应 Provider 延迟/成功率学习、最快两源首波 + 约420ms补全、首屏每源20条、旧搜索任务取消、意图驱动歌词、持久搜索 Shell、结果内容 signature 抑制无效重建，以及「搜索矩阵」性能诊断。
- V87 继续冻结 `PlaybackService / SourceCoordinator / PlaybackUrlStore`，V85 的 `ImageLoader / LyricCacheStore` 也不改；推荐和搜索仍不得缓存或决定临时音频播放 URL。

后续开发以 **V87** 为最新主线。若继续优化搜索，优先根据「搜索矩阵」与真机 Frame/CPU Trace 判断瓶颈，再决定是否把结果列表迁移到 RecyclerView/ListAdapter；不要仅为“架构现代”而主动触碰稳定播放网络层。


## V88 主线续作说明

V88 针对 V87 真机反馈的“每日推荐 / 私人电台首屏很慢”和推荐卡片裁切做性能/适配收口：

- `Personalization Engine V2.2` 改为推荐 Fast Path：进入日推/私人电台时优先读取已物化推荐快照；没有快照时只用本地收藏/歌单/历史/行为数据库先生成可用预览，首屏不再等待 MusicBrainz / ListenBrainz / 网易 / QQ。
- 日推仍保持当天稳定；旧日快照可先显示并在后台刷新今日推荐。私人电台先显示最近快照，再按最新 Session 在后台重新计算。后台结果只在用户仍停留于对应页面时无动画替换，并尽量保留滚动位置。
- 推荐目录扩展增加 18 小时歌手搜索元数据缓存，瞬时故障可回退最长 7 天旧缓存；多个独立歌手目录查询使用小型固定池并行处理，避免 V87 一位歌手接一位歌手串行等待。
- ListenBrainz 改为缓存优先；一次推荐刷新最多允许 1 个未热种子触发 MusicBrainz / ListenBrainz 网络请求，后续会逐步把常用种子暖起来。协同发现质量保留，但不再成为首屏门槛。
- 首页「每日推荐 / 私人电台 / 天气电台 / 个性化矩阵」卡片高度从 100dp 修正到 114dp，解决内容实际需要约 108dp 却被容器裁切的问题。
- 个性化矩阵与“推荐构成”的四项指标从一行四列改为 2×2 自适应格，`ListenBrainz` 等长标签不再被窄列裁掉；数据说明行允许自然换行。
- 播放核心 `PlaybackService / SourceCoordinator / PlaybackUrlStore` 继续冻结，V85 的 `ImageLoader / LyricCacheStore` 也不改。推荐缓存仍只保存歌曲元数据/原因，不保存、不复用临时音频 URL。

后续开发以 **V88** 为最新主线。推荐性能问题优先遵循“本地/快照先出，远端后台补全”的原则，不再把外部协同服务放回首屏关键路径。


## V89 主线续作说明
V89 将 V87/V88 的搜索矩阵与个性化矩阵从正文主流程中收起，统一为「引擎洞察」上下文交互：页面右上角复用 SCAN 引擎键，轻点显示快速洞察 Bottom Sheet，长按直达完整矩阵；快速洞察内仍提供进入完整矩阵的显式按钮。首页不再长期占据“个性化矩阵”卡片，日推/私人电台长按原卡片仍可直达完整音乐画像。搜索来源筛选行不再额外塞“矩阵”文字 Chip。此轮只调整分析/解释层入口与展示，不修改播放链、封面缓存、歌词缓存、推荐召回算法或 Search Engine V2 调度。

V89 同轮视觉收口：完整音乐画像的固定宽普通条形图升级为自适应轨道式 Insight Rail（四分位刻度、渐进填充、终点节点、克制进场动画）；Session 指标使用 2×2 玻璃统计格；歌手概览增加 Session/7D/30D/180D 四段时间光谱；推荐页增加“熟悉 ↔ 新发现”平衡轨。视觉层只读取已有画像，不修改推荐权重和数据。


## V90 主线续作说明
V90 继续遵守单轮最大完成度 + Stable/Beta 双 ZIP 工作流。首页移除智能音源大卡，统一使用右上角引擎键进入“播放链 / 个性化 / 搜索”三页矩阵中心；顶部支持点击/左右滑动切换；系统返回永远退回上一级。后续不得恢复首页常驻大矩阵卡，也不得因为矩阵 UI 改动触碰稳定播放链。

## V91 主线续作说明
V91 在 V90 三矩阵中心基础上新增 `Lunaxy Motion System 1.0`，重点升级真实拖动排序手感而不触碰播放/推荐/搜索算法：

- 歌单“手动排序”和收藏排序共用弹簧物理拖拽：长按提起、放大/抬升、源位半透明占位、邻近卡片按目标位置弹簧让位、跨槽轻触觉反馈、松手后目标卡片回弹吸附。
- 播放队列使用同一套弹簧排序语言；拖动过程中不再每经过一个槽位都 `notifyDataSetChanged()` 重建整列，减少闪烁和卡顿。
- 新增轻量 `SpringMotion`，仅使用平台 `ViewPropertyAnimator` + 自定义阻尼插值，不新增第三方动画依赖，不进入播放线程。
- 常用 Bottom Sheet / Modal 的进场改为克制的位移 + 缩放弹簧落位；三矩阵顶部切换落位也使用同一 Motion token。
- 首页引擎入口按用户最终确认从大标题右侧移到“随机歌词”同一排最右侧，尺寸降为更低调的 34dp；首页仍不恢复 V89 以前的智能音源大卡。
- 系统返回键逻辑保持 V90：临时层/矩阵先返回上一级，Home 根层只退到后台。

稳定红线继续冻结：`PlaybackService / SourceCoordinator / PlaybackUrlStore / RecommendationEngine / SearchPerformanceStore / ImageLoader / LyricCacheStore`。
后续若继续调 Motion，优先真机观察 60/90/120Hz 上的跟手、邻居让位距离与触觉节奏，不以增加夸张弹跳为目标。



## V91.1 拖动排序热修主线说明
V91.1 根据真机录像回退并重做 V91 的拖动排序实现。V91 使用 Android 系统 DragShadow + 源卡 ghost + `pointToPosition()` 与邻居 translation 叠加，跨槽阈值会在视觉位移和命中计算之间形成反馈，导致卡片在边界附近反复切槽，表现为明显“抽搐/抖动”。

V91.1 改为单一物理拖拽模型：
- 被抓起的卡片使用 Activity 顶层自绘浮动代理，**1:1 跟随手指**，不再给被抓卡片本身加滞后弹簧；
- 原位置保留固定高度的轻量占位槽，原卡内容完全隐藏，不再同时出现“源卡 + 系统拖影”两个实体；
- 邻居卡片只在跨过稳定中点阈值后弹簧让位，并加入 8dp hysteresis，避免边界来回切槽；
- 槽位判断使用 ListView 原始 layout top/center，不读取 translation 后的视觉位置，消除“邻居动了 → 命中阈值也跟着动”的反馈环；
- 拖动过程不 `notifyDataSetChanged()`；松手后浮动卡先吸附到目标槽，再一次性提交真实顺序；
- V91 原先约 12% 的过冲阻尼下调到约 2% 级小过冲，保留弹性感但不产生二次晃动；
- 播放队列与歌单/收藏手动排序共用同一套模型。

稳定红线继续冻结：播放链、推荐、ListenBrainz、Search Engine V2、封面/歌词缓存均不因本热修改变。后续 Motion 调优必须以真机录像为准，优先“跟手 + 稳定让位 + 单一实体”，不再使用系统 DragShadow 叠加 ghost 的方案。


## V92 主线续作说明

V92 以 V91.1 为基线将交互升级为 Motion System 2.0：共享封面 Hero、Mini→Full 连续拖动 Morph、Play/Pause 与 Heart 路径 Morph、唱片惯性角速度、歌词呼吸聚焦与真实逐词 timing、手势跟随切歌、三矩阵 finger-driven 分页、短暂 SourceFlow、推荐轻视差、Bottom Sheet 物理拖动、尺寸感知按钮 Press Physics、Shared Axis 页面层级和 Skeleton→Content 原位过渡。

最高稳定性红线：V91.1 真机出现过“拖拽落位后退出 App”，V92 已将排序提交、Modal 收尾、父页面刷新拆成三个阶段，并对 ACTION_UP、Bitmap RenderThread 回收和排序提交做保护。后续任何 Motion 优化都不得再次在拖拽落位同帧重建父页面。系统 Back 继续严格返回上一级，Home 根层只退后台。

播放/推荐/搜索核心继续冻结：PlaybackService / SourceCoordinator / PlaybackUrlStore / RecommendationEngine / SearchPerformanceStore / ImageLoader / LyricCacheStore 未因 Motion 改造而变化。

后续开发以 **V92** 为最新主线。若真机出现动画问题，优先修触摸所有权、View 生命周期和渲染时序，不通过增加更大 overshoot/更长动画掩盖问题。

## V92.1 构建热修主线说明

V92.1 是 V92 Motion System 2.0 的纯构建兼容热修。真机 Android Studio 编译暴露 `LyricLineView.java` 中对 `TextView` 误调用不存在的 `clearShadowLayer()`；修正为对 `TextView#getPaint()` 返回的 `TextPaint/Paint` 调用 `clearShadowLayer()`。此热修不改 Motion 参数、不改播放链、不改推荐、不改 Search Engine V2、不改缓存。

V92.1 同时重新核对 V92 新增 Motion Java 文件中的 Android 平台 API 使用；所有源码继续以 compileSdk 36 / minSdk 26 为基线。后续发布不得只依赖语法 parse 冒充 Android API 符号级编译，若本地 Android Studio 暴露编译错误必须优先修复并回写自测记录。

后续开发以 **V92.1** 为最新主线。



## V92.2 稳定性主线说明

V92.2 以 V92.1 为基线专项修复真机反馈的两条 Motion 回归：歌曲卡→播放详情 Hero 在大唱片落位时顿一下，以及队列/歌单长时间拖拽后退出 App。Hero 必须对齐 `VinylRecordView` 内真实封面圆而不是整个波纹容器，并使用单一进度时钟驱动位置/缩放/形状/交叉淡入。排序拖拽必须保持单一浮动实体、Activity 级手势所有权、layout-space 命中阈值、连续 overlap 挤压；长拖期间排序模型按帧合并、边缘滚动限频、禁止产生新的封面加载任务，松手后再一次性恢复可见封面。

V92.2 不再显式回收刚参与硬件动画的 drag/Hero Bitmap，交由现代 Android GC 管理，避免部分 OEM RenderThread 生命周期竞争。`ImageLoader` 仅新增 recycled-row stale request 早退，不改变封面缓存容量和播放 URL 隔离。

后续 Motion 调整第一优先级仍是：长拖 60–120 秒不退出 > 连续挤压自然 > Hero 无落位顿挫 > 其他视觉润色。若真机仍退出，必须优先结合 Logcat 的 FATAL/ANR/SIGSEGV 定位，不继续靠动画参数猜测。


## V92.3 主线续作说明

V92.3 以 V92.2 为基线继续修真机 Motion 细节：队列/歌单 drop 收尾使用“先提交真实顺序并完成新布局，再淡出 drag proxy”的连续状态，不再恢复旧态后刷新；当前播放歌曲跨行时使用短生命周期连续覆盖层消除 Halo 一帧闪烁。当前播放行升级为封面主色驱动的 Fluid Halo，辅助色仅从封面主色相邻色域生成，保持克制。播放详情升级为 previous/current/next 三圆形唱片横向空间堆栈，切歌由手指位移直接控制 translation/scale/alpha/blur，松手按距离与 velocity settle。Android 12+ 的 RenderEffect 通过 API31 内部实现隔离，minSdk26 外层类不暴露新 API 类型。

后续以 **V92.3** 为最新主线。拖拽第一优先仍是长拖不退出与落位无闪烁；播放详情 Motion 必须保持直接操纵感，不退回“松手以后再播放动画”的模式。


## V92.4 主线

本版以用户指定的 V92.3 Beta 源码为基线，同源制作 Stable/Beta。当前主线为 V92.4。五个保留唱片 View 共享连续堆叠空间并在切歌后交换角色；歌曲状态持有流体框调色和播放时钟；拖拽以实际 OnPreDraw 布局交接收尾。Hero/切歌取消必须使待执行回调失效，Mini 位置必须使用固定起止坐标。详见 RELEASE_NOTES_V92.4.md 和 dev-logs/V92.4-validation.md。后续优先结合真机录像和 Logcat 调整，不凭增加过冲或延长动画掩盖问题。


## V92.5 主线

V92.5 以 V92.4 为基线继续精修播放详情：星空实验室新增侧唱片清晰度（默认完全清晰）；横向唱片手势由单步上一/下一首升级为 retained-window 连续队列浏览，拖动阶段不触发真实播放解析，松手后一次性 playAt 最终队列位置。歌词区域顶部状态完全隐藏并随外层滚动渐显，外层 overscroll EdgeEffect 与歌词卡描边墙被移除。确定播放失败后绑定 songKey，3 秒后若仍是同一失败歌曲且队列可前进则自动下一首。播放链、推荐、搜索与缓存隔离原则继续保持。


## V92.8 主线续作说明

V92.8 以用户提供的 V92.7 Stable 完整源码为唯一基线，专项优化 Mini Player 点击进入播放详情的空间转场。点击路径不再只有“封面 Hero + 整页淡入”，而是新增独立的 PlayerSurfaceMorph：播放器表面从 Mini Player 的圆角矩形原位向上、向四周连续舒展到全屏；封面仍精确落到真实唱片封面圆，旧页面、Mini、星空与播放详情内容全部由同一个单调时间轴协调交接，避免多 Animator 采样不同步造成的顿挫。

本轮只修改播放详情进入 Motion 层：`MainActivity`、`SpringMotion` 与新增 `PlayerSurfaceMorphView`。`PlaybackService / SourceCoordinator / PlaybackUrlStore / RecommendationEngine / SearchPerformanceStore / ImageLoader / LyricCacheStore` 等播放、推荐、搜索和缓存核心未改。后续以 **V92.8** 为最新主线。
