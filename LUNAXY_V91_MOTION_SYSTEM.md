# Lunaxy Music V91 — Motion System 1.0

## 目标
把 V90 已有的“功能正确的拖动排序”升级为更接近商业级音乐 App 的物理手感，同时完成首页引擎入口最终位置调整。

## 拖动排序
### 歌单 / 收藏
- 入口仍是原有“手动排序”，收藏与本地歌单共用一套实现。
- 长按右侧拖动柄后，卡片先轻微放大并抬升；系统 drag shadow 使用约 1.045 倍视觉尺寸。
- 原位置保留低透明度占位，不让列表突然塌陷。
- 拖动穿过其它歌曲时，不再每个槽位重建整个 ListView；只对受影响的可见邻居做一张卡高度的弹簧位移，形成“主动让位”的真实空槽。
- 每跨过一个目标槽位给一次轻微 CLOCK_TICK 触觉，避免连续震动。
- 松手后只做一次真实数据顺序变更与持久化，目标卡片使用短距离 overshoot + settle 落位。

### 播放队列
- 保留原有长按歌曲排序和边缘自动滚动。
- 使用与歌单一致的 lift / placeholder / neighbor shift / landing 动画。
- 排序完成仍调用既有 `PlaybackService.moveQueueItem()`；Motion 层不决定、不修改播放 URL 或换链。

## Motion Token
新增 `ui/SpringMotion.java`：
- `SNAPPY`：拖动让位与轻微状态切换。
- `SOFT`：Bottom Sheet / Modal 进场。
- `LAND`：排序落位与恢复。
- 自定义阻尼振子插值，仅使用 Android 平台动画 API，不引入额外 Maven 依赖。

## 首页入口
- V90 标题右侧 SCAN 图标移除。
- V91 将同一引擎入口放到随机歌词同一排最右侧，视觉尺寸约 34dp。
- 点击仍进入三矩阵中心；三矩阵自身的播放链/个性化/搜索能力不变。

## 不改动
- PlaybackService / SourceCoordinator / PlaybackUrlStore
- Personalization / ListenBrainz 推荐算法
- Search Engine V2 调度与缓存
- 封面缓存 / 歌词缓存

## 真机重点
1. 收藏或歌单 → 手动排序：连续拖过 3~5 个槽位，观察邻居是否顺滑让位而不是闪烁重建。
2. 播放队列：拖动跨越当前播放项，确认队列顺序正确、播放不中断。
3. 松手：目标卡片只有克制的一次回弹，不出现多次抖动。
4. 快速拖到列表顶部/底部：自动滚动仍可用。
5. 首页：引擎 SCAN 图标与随机歌词同排最右侧；系统返回仍逐级返回。
