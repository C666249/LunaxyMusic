# V92.8 — Mini Player → Full Player 连续表面转场

本轮以用户提供的 **LunaxyMusic V92.7 Stable 完整源码**为唯一基线，只处理“点击底部 Mini Player 进入播放详情时过渡不够连续、高级”的问题。

## 交互调整

- 新增 `PlayerSurfaceMorphView`：点击 Mini Player 后，播放器表面从 Mini Player 当前圆角矩形原位扩展，连续铺满整个屏幕，而不是先出现黑色全屏页再让封面飞入。
- 保留真实封面 Shared Hero，但几何、圆角形变、播放器表面扩展、旧页面淡出、Mini Player 收束、唱片/侧唱片揭示统一由 **同一个 450 ms 单调时间轴**驱动。
- 使用无回弹的 `PLAYER_OPEN` 贝塞尔曲线，避免大唱片落位阶段的二次弹跳；页面内容在表面展开建立空间关系后再渐显，视觉上更像 Mini Player 本身“长成”全屏播放器。
- 表面 Morph 独立于真实 Full Player 控件层，不对播放器控件做非等比压缩，不改变手势命中和唱片目标几何。
- 动画取消、系统返回或重复打开时会主动清理临时 Surface/Artwork Morph，避免透明层或残影遗留。

## 修改边界

本轮仅改：
- `MainActivity.java`
- `SpringMotion.java`
- 新增 `PlayerSurfaceMorphView.java`

未改播放解析、音源路由、推荐、搜索、歌词缓存、封面缓存、下载、数据库及 Provider 核心。

## 版本身份

- Stable：`com.xingyu.music` / `92.8.0` / versionCode `938`
- Beta：`com.xingyu.music.beta1` / `92.8.0-beta34` / versionCode `939`
- Beta App 名带 `Beta`，启动图标使用明显 β 标识；Stable/Beta 功能源码同源。
