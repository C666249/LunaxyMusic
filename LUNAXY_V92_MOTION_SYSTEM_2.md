# Lunaxy Music V92 — Motion System 2.0 / 连续交互大升级

V92 以 V91.1 为唯一源码基线，目标不是增加装饰性动画，而是把 Lunaxy 的运动语言改成“内容连续、手势跟手、物理收敛、层级可感知”。已经稳定的播放解析、推荐召回、Search Engine V2、封面/歌词缓存保持冻结。

## 1. 拖拽排序稳定性优先热修

- 修复 V91.1 真机反馈的最高风险：拖拽落位后 App 退出/崩溃。
- 拖拽期间禁止 Bottom Sheet 外部点击误收尾；ACTION_UP 不再同时触发排序与弹层关闭。
- 顺序先持久化，浮动代理/Modal 完全结束后才刷新父页面，避免同一帧拆 ListView、Modal、页面三层 View。
- 拖拽/落位提交均做 Throwable 隔离；排序 UI 异常不能直接杀 Activity。
- 拖拽 Bitmap 与共享转场 Bitmap 均延迟回收，避免 RenderThread 仍持有硬件纹理时提前 recycle。
- 卡片本体 1:1 跟手；邻居才使用软弹簧让位；8dp hysteresis 保持边界稳定。
- 邻居加入最多十几毫秒的轻传播延迟，让位更像弹性连接，而不是同时机械位移。

## 2. Shared Artwork / Mini Player → Full Player Morph

- Mini Player 点击进入播放器时使用独立顶层 ArtworkMorphView：原封面从 Mini 的真实屏幕坐标连续放大到 Full Player 圆形唱片位置。
- 圆角矩形裁切同步连续过渡到圆形，不再“旧封面消失→新封面出现”。
- Mini Player 支持直接向上拖动：页面、封面、标题、Seek 位置/透明度随手指连续变化；距离 + velocity 决定完成或回弹。
- 子级播放/队列按钮保持原点击语义，Morph 手势不改播放业务链。

## 3. 播放控件 / 收藏 / 唱片物理

- Play ↔ Pause 使用连续 morph progress，不再瞬间替换图形。
- 收藏 Heart 从轮廓连续过渡为填充，收藏时仅有克制的轻光点扩散。
- VinylRecordView 改成角速度状态：播放缓慢加速至约 80 秒/圈，暂停惯性减速，恢复再缓慢加速。
- 主要按钮统一尺寸感知 Press Physics：小按钮压得更明显，大卡片更克制，松手统一阻尼回弹。

## 4. 歌词 Motion / Karaoke

- 当前歌词行通过 scale、opacity、letter spacing、轻 shadow/blur 共同聚焦；上下歌词按距离渐弱。
- 有增强 LRC 逐词/逐音节时间戳时，优先按真实 LyricWord 时间逐步点亮。
- 普通 LRC 只有行时间时，才退回当前行→下一行时间的稳定线性扫光，不伪造单词 timing。
- 歌词更新 ticker 只在完整播放器可见时运行。

## 5. 封面切歌 / 三矩阵分页

- Full Player 横滑封面时，当前封面直接跟手、轻 scale/rotate；提交时带惯性甩出，下一首从反方向轻缩放进入。
- 播放链 / 个性化 / 搜索三矩阵顶部切换改为 finger-driven：拖动直接控制页面 progress，松手综合距离和速度决定翻页，再 spring settle。
- 矩阵拖动期间不重建页面，只有提交页变化后才替换内容。

## 6. 智能音源节点流动

- 播放链矩阵加入 SourceFlowView，用四个克制节点表达“搜索 → 匹配 → 选源 → 播放”。
- 只在线路状态发生改变时短暂 pulse，随后回到静态；不做持续科技大屏。
- 不修改 SourceCoordinator / PlaybackService；这里只观察现有状态。

## 7. 推荐 Peek / Bottom Sheet / Shared Axis / Skeleton

- 日推/私人电台横向卡片加入轻微视觉视差：图像层移动略多于文字层，保持幅度很小。
- Bottom Sheet 统一增加顶部 grab zone、直接下拉、velocity 判定和轻 spring 回位/关闭。
- 同层级根页面使用 Shared Axis 横移；父→子使用轻缩放纵深；返回反向表达层级。
- 日推、私人电台、搜索首次无内容时使用稳定 Skeleton；真实内容到达时原位 cross-dissolve/morph，避免转圈后整页突然出现。

## 8. 核心冻结红线

V92 不修改以下稳定核心：
- PlaybackService.java
- SourceCoordinator.java
- PlaybackUrlStore.java
- RecommendationEngine.java
- SearchPerformanceStore.java
- ImageLoader.java
- LyricCacheStore.java

因此 Motion 层不保存、不改变、不决定临时音频播放 URL。
