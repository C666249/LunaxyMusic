# Lunaxy Music — 长期开发工作流

本文件记录 Lunaxy Music 项目特有的长期规则。上位规则见 `App开发规范与交付标准.md`。

## 1. 唯一主线与交付

- 每轮只以用户本轮指定/确认的最新完整源码 ZIP 为基线，不凭聊天记忆重造源码。
- 每轮尽量闭环完成：Baseline Audit → Design → Implementation → First Review → Fix → Regression → Delivery Audit → Stable/Beta 双 ZIP。
- Stable 固定 `com.xingyu.music`；Beta 固定 `com.xingyu.music.beta1`，两者必须能同机共存且数据隔离。
- Stable/Beta 功能源码同源；除身份、显示名、β 图标、版本和必要测试差异外不制造行为分叉。

## 2. 稳定性红线

除非本轮需求必需，不主动改动：`PlaybackService`、`SourceCoordinator`、`PlaybackUrlStore`、`RecommendationEngine`、`SearchPerformanceStore`、`ImageLoader`、`LyricCacheStore`、下载/缓存/Provider/桌面歌词等已稳定核心。

排序/播放详情 Motion 的既有红线继续有效：
- 拖拽只允许一个浮动实体；命中使用 layout-space，不读 translation 后视觉位置；拖动期间不反复 `notifyDataSetChanged()`；落位不能在同帧重建父页面。
- 参与 RenderThread/硬件动画的 Bitmap 不做激进显式 recycle；Hero/切歌取消必须使待执行回调失效。
- 唱片浏览坚持 direct manipulation：手指移动直接映射 presentation，真实播放只在最终 commit 时发生。

## 3. Apple Fluid / Lunaxy Visual System 原则（V92.9 起）

### 3.1 连续性优先
- 用户触摸 ACTION_DOWN 即获得视觉响应；不要等 click 结束才反馈。
- Tab 切换必须展示“从哪里到哪里”的空间过程，不做瞬时替换；Root Tab 使用相邻整页位移，旧页/新页保持不透明且不重叠，禁止用两个半透明页面叠加制造连续性。
- Root Tabs 具有稳定横向空间关系：首页 → 搜索 → 歌单 → 收藏；向右目标从右侧进入，向左目标从左侧进入。
- Tab 选中载体必须按真实 Tab bounds/中心坐标连续迁移，不能靠硬编码偏移；图标/文字与页面状态同步响应。
- 快速连续点击要从当前 presentation retarget，禁止把旧动画排队播完。
- 每个 Root Tab 保留自己的滚动位置和一层 detail 状态；切走再返回应像页面一直存在。
- 同 Tab 内 push/pop 也必须保持明确可逆的空间关系；V92.9.5 起使用不透明、整 viewport 相邻的左右 push/pop，禁止完整页面 alpha 交叉。跨 Tab 即使恢复的是 detail，也仍保持横向空间语义。
- V92.9.9 起，对“列表对象 → 详情 Hero”这类存在明确对象身份的导航，优先使用 Shared Object / Container Transform：容器、封面、标题、元信息在真实几何之间连续变化；返回必须走同一路径，快速 Back 从当前 presentation 直接反向。若几何不可用再降级为空间 push/pop。
- 共享对象不得把小尺寸 View 截图放大充当 Hero；优先复用 ImageLoader 原始 Bitmap / 同源高分辨率内容，视觉所有权在目的位置落稳后再交还真实 View。

### 3.1.1 即时导航与渐进内容
- “立即响应”不是取消动画：按下后应立即开始有因果关系的空间运动；重数据在运动期间或页面落位后后台准备。
- 重页面先出现最终尺寸的结构/Skeleton，再以 Curtain Reveal 等无布局跳动方式交接真实内容；滚动过程中继续小批次补齐，不用文字“正在加载”阻塞阅读。
- V92.9.13 起，密集歌曲列表的 Curtain Reveal 以“行”为最小视觉单元并做短 stagger；每日推荐、搜索全部结果等不得再使用贯穿整个 viewport 的单一移动光缝。Skeleton shimmer 也应错相，避免多行同步形成机械光柱。
- V92.9.14 起，逐行加载的 Skeleton shimmer 与 Curtain 光缝优先跟随“当前播放歌曲封面”的实际取色；新封面未解析时禁止沿用上一首歌的旧 accent。歌单详情 Hero 则跟随“该歌单封面”取色，并与 Shared Object 代理保持同一颜色来源。
- Shared Object 若目标 Hero 首帧尚未完成测量，应先保留源对象并等待若干 pre-draw/animation frame，再决定是否降级；不得因为一次几何读取为空就直接跳过共享元素。
- 冷启动先交付品牌化第一帧和可持续的场景（V92.9.9 为 Startup Starfield），大型 Library JSON / 索引恢复不得占住主线程再进入 Activity。

### 3.2 Motion
- 页面只选语义 token，不新增散落的随意 duration：press / selection / fade / spatial / page / sheet / theme reveal。
- Motion 要表达状态关系，而不是装饰；不通过更大 overshoot/更长时长掩盖生命周期、触摸所有权或布局问题。
- Mini Player → Full Player、Hero、唱片、队列等现有成熟共享对象/直接操纵逻辑优先保护。

### 3.3 Material hierarchy
- 内容层：稳定、可读、相对实的 `contentSurface`。
- 功能悬浮层：Navigation / Mini Player / Snackbar 使用 `functionalGlass`。
- 临时交互层：Sheet/Popover 使用 `transientGlass`。
- 玻璃是层级工具，不是所有卡片的默认装饰；Lunaxy 原有推荐卡表达性材质可保留。

### 3.4 Appearance & accessibility
- 主题：深空（升级默认）/ OLED / 月白 / 跟随系统。
- 老用户升级默认保持深空，避免系统浅色导致突然换白主题；只有用户主动选“跟随系统”后才跟随系统深浅。
- 主题切换从点击源点做 circular reveal；Reduced Motion 时降级为短 crossfade。
- Reduced Motion：保留状态变化，但缩短/取消持续星空、流光、唱片装饰运动和大空间位移。
- Reduced Transparency：玻璃层退化为更实的 tonal surface，同时保持层级和对比度。

## 4. Back / Overlay

系统 Back 始终逐级返回：临时层/Sheet/Detail/Player 优先关闭，Home 根层才退后台。新增 Overlay 必须参与该层级，不得抢占或绕过既有返回语义。

## 5. Review 重点

每轮至少检查：快速连续 Tab、Tab reselect、detail↔root、滚动恢复、主题切换中断、Reduced Motion/Transparency、Mini→Full morph 与 Nav 协调、拖拽长时间稳定性、动画完成后的真实状态、Stable/Beta 同源与身份隔离。

## 6. Moonlight 与播放控件补充（V92.9.2 起）

- 暗色专用 Drawable/Shader 不能直接复用到月白；若材质包含深底、白色低 alpha 或暗色 glow，必须有明确的 light branch。
- 月白里的弱化不等于灰化：二级文字可降权，但可操作控件应通过语义 accent、surface 或形变保持可识别性。
- 播放页按钮反馈遵循“业务动作立即执行 + presentation 短反馈”：上一/下一体现方向，模式体现状态变化，加入/队列体现层级打开；快速重复输入 cancel/retarget，禁止队列动画。
- 已经有稳定 morph 的 Play/Pause、Favorite 不重复叠加第二套大型动效；新动效只补足触感和语义。

## 7. Lunaxy Voice 合并约束（V92.9.3 起）

Lunaxy Voice 是音乐能力的**输入层**，不是第二套播放器。语音命令最终继续走现有 `PlaybackService / LibraryStore / SourceCoordinator` 能力，禁止为了 Voice 复制播放状态机、音源路由或数据存储。

- Voice runtime：`voice/VoiceAssistantService`、`VoiceAssistantContract`、`VoiceCommandParser`、`VoiceMusicSearch`；UI：`VoiceAssistantPanel`、`VoiceOrbView`、`VoiceGlassDrawable`。
- 保留三种识别模式：`本地优先 / 仅本地 / 系统识别`。本地能力取决于 ROM 的 on-device recognizer 与中文模型；不得把“系统识别”宣传成保证离线。
- 麦克风后台待命必须是用户主动开启的 foreground service；不保存原始录音。Stable/Beta 各自在自己的 applicationId 沙箱内保存 Voice 设置。
- Voice 入口属于输入层：V92.9.5 起 Mic 不再放进搜索框，而是收进 Home 标题右上角的纵向渐进工具抽屉；同一抽屉还包含引擎矩阵。已启用时 Mic 轻点直接聆听，未启用时进入设置，长按始终进入 Voice 设置。
- Voice 状态浮层属于 transient layer：使用当前 Appearance/Reduced Motion/Reduced Transparency tokens；Back 可先收起；快速 phase 更新必须 cancel/retarget，不能排队动画。
- Voice 搜索跳转要先保存当前 Root Tab context，再进入 Search root，并继续使用 V92.9.1 起的不透明相邻整页导航；不能瞬时替换页面。
- Voice UI 在月白下必须使用独立的浅色材质和可读文本，不得复用固定深色面板；系统浮窗服务独立启动时也必须先 `AppearanceSystem.load()`。
- 语音识别、解析、搜索和 UI 的失败要清晰可恢复；网络错误要说明当前识别路径并提供设置入口，不无限在本地/系统引擎之间震荡。


## 8. Progressive disclosure 补充（V92.9.5 起）

- 首页二级工具默认不永久占位：推荐区使用单一调整 hub 横向生长出“卡片样式 / 主题”，首页右上角使用单一工具 hub 纵向生长出“Voice / 引擎矩阵”。
- 一级 hub 与展开后的子动作必须保持同一物体来源；展开和收回是互逆过程，快速反点从当前 progress retarget，不排队。
- 可见图标可以紧凑，但 Android 实际触控目标不得小于 48dp。
- 图标优先使用项目 `IconView` 的一致线性语言；可参考 Material Symbols / Lucide / Iconsax 的 24×24 rounded-linear 语义，但外部资源只作设计参考，未核许可时不直接复制资产。
- 二级动作展开需有可追踪顺序；V92.9.7 起 2 项抽屉采用单一 progress + 短 stagger 重叠级联：第一项先启动、尚未落位时第二项就跟进；收回按同一 progress 反向形成逆序，不用两个独立 Animator 排队，也不允许出现视觉死区。
- detail 页面 push/pop 不再使用透明整页 crossfade。若页面背景本身允许星空透出，仍必须通过完整 viewport 邻接避免两个页面内容在同一屏幕区域重叠。

## 9. Player shared-object / controls 补充（V92.9.6 起）

- Mini Player → Full Player 的 artwork proxy 优先使用已有原始缓存 Bitmap，不放大 Mini ImageView 的低分辨率屏幕快照；缓存不可用时才回退 snapshot。
- 共享对象交接允许保留一个显示帧的同源 proxy overlap，避免 GPU texture 上传/首帧绘制造成空白或模糊跳变；不得以 blur/crossfade 掩盖分辨率错误。
- Full Player 次要操作默认“图标即控件”，常驻材质圈只保留给确实需要的主操作/选中状态。42–50dp 触控区可透明，按压时用 bounded ripple + 语义 icon motion 提供即时反馈。
- Play/Pause 继续作为唯一主要 filled transport；上一/下一、队列、循环、收藏、添加、更多等不应同时争夺视觉主层级。
