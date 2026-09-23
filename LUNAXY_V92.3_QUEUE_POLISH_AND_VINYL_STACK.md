# Lunaxy Music V92.3 — Queue Polish + Vinyl Stack

渠道：Beta
版本：92.3.0-beta28（versionCode 927）

## 本轮目标

V92.3 只围绕真机反馈继续收口播放队列/歌单拖动视觉与播放详情唱片空间表现，不改变已经稳定的播放链、推荐、ListenBrainz、Search Engine V2、歌词/封面缓存策略。

## 1. 拖拽落位无闪烁收尾

- 浮动拖拽代理在 Adapter 真实顺序提交并完成新一帧布局前保持不透明。
- 不再在 drop 时先恢复旧行、清空位移后再 notifyDataSetChanged，避免旧位置正常态闪出一帧。
- 当前播放歌曲如果因其它歌曲跨过而换物理行，使用短生命周期 RowContinuityOverlay 保持当前播放 Halo 的视觉连续性。
- Drop 后只请求 invalidate，不再 invalidateViews 全量重绑。
- 拖动过程继续保持：Activity 级手势所有权、按帧合并、边缘自动滚动限频、拖动时禁止新封面加载。

## 2. Album Reactive Fluid Halo

- 当前播放行改为 FluidTrackHaloDrawable。
- 主色直接来自当前歌曲封面缓存回调。
- 辅助色由封面主色的相邻 Hue 动态生成，不使用固定 RGB/蓝紫灯带。
- 深色主体不变，只在外沿、内层柔光与极慢 Sweep 上体现颜色。
- 播放时缓慢流动；暂停时保持静态。切歌主色约 620ms 平滑插值。

## 3. 播放详情 Vinyl Stack

- 当前唱片、上一首、下一首使用三个真实 VinylRecordView 组成横向空间堆栈。
- 左右唱片缩小、降低亮度/透明度并轻度虚化；当前唱片保持最清晰、最大。
- 小型粒子层位于侧唱片上方、当前唱片下方，产生前后穿越的空间关系。
- 横向切歌改为直接操纵堆栈几何：手指位移同步控制 translation / scale / alpha / blur。
- ACTION_UP 使用位移 + velocity 决定回弹或切歌，完成时只做 settle，不重新播放一段脱节动画。
- 切歌提交后，已经滑到中央的侧唱片与真实新中心唱片做短交叉接管，避免第二次“重新出现”。
- Android 12+ 使用 RenderEffect 虚化；API 31 类型隔离在内部实现，保证 Android 8–11 可安全加载外层 VinylStackView。

## 稳定性红线

- PlaybackService / SourceCoordinator / PlaybackUrlStore 不改。
- RecommendationEngine / SearchPerformanceStore 不改。
- 音频临时 URL 仍不进入任何 UI/Motion 缓存。
- 系统返回键继续严格返回上一级。
