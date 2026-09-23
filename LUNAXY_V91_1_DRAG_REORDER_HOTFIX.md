# Lunaxy Music V91.1 — Drag Reorder Motion Hotfix

## 真机反馈根因
V91 的拖动效果与参考视频不一致，并出现明显抽搐。对用户录像逐帧检查后确认，V91 同时使用了：
1. Android 系统 `startDragAndDrop()` 的 DragShadow；
2. 原列表源卡的半透明 ghost；
3. 对邻居卡片做 `translationY`；
4. 再用 ListView 的命中结果持续计算 drop slot。

这会产生两个问题：
- 画面中同时存在“拖影”和“源卡残影”，看起来像两个卡片叠在一起；
- 跨槽后邻居已经位移，但拖动命中仍持续更新，边界附近容易来回切槽，动画不断 cancel/restart，形成抽搐。

## V91.1 新实现
- 移除排序链路中的系统 DragShadow。
- 长按右侧拖动柄后，在 Activity content 顶层创建一张卡片快照作为唯一浮动实体。
- 浮动卡 1:1 跟随手指 Y，不给它加追随滞后。
- 源行保留尺寸，但隐藏真实内容，仅显示非常轻的占位槽。
- 目标槽使用原始 layout center + 8dp hysteresis；邻居的 translation 不参与阈值计算。
- 只有跨越真实阈值时才改变 target，邻居才启动一次让位动画。
- 邻居动画过冲由 V91 的约 12% 降低到约 2%，更接近参考视频里的克制弹性。
- 松手时浮动卡先落到目标槽，再一次性提交数据顺序并移除浮层。
- 队列仍调用既有 `PlaybackService.moveQueueItem()`；歌单/收藏仍调用既有本地持久化接口。

## 未触碰
PlaybackService、SourceCoordinator、PlaybackUrlStore、RecommendationEngine、SearchPerformanceStore、ImageLoader、LyricCacheStore。
