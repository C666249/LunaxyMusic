# RELEASE NOTES — Lunaxy Music V92.9.13 Stable

## Motion unification

- 每日推荐等 Smart Collection 详情不再使用整页从左到右的 Curtain 光缝；页面先按正常同 Tab depth motion 进入，歌曲再像歌单详情一样逐行、错峰展开。
- 搜索“全部结果”移除整页 Curtain，首屏歌曲行改用与歌单一致的 `CurtainRevealFrame` 级联呈现；滚动回收后的已出现歌曲不会反复重播整套入场。
- Skeleton shimmer 增加逐行相位偏移，避免多行同步形成机械的整齐光柱。

## Playlist Shared Object

- 歌单列表卡片进入详情 Hero 时，源卡片会保持可见，直到目标大卡片几何真正可用；最多等待多个 animation frame 再决定是否降级，修复部分设备/首帧上共享元素没有真正出现的问题。
- Shared proxy 在隐藏源卡片前先绘制到完全相同的起始几何，保证视觉身份连续。
- 共享元素时长调整到约 840 ms：卡片边滑向目标位置边扩大、圆角/容器/封面/标题同步形变，落位后再把所有权交还真实详情 Hero。

## Compatibility

No data migration. Stable applicationId remains `com.xingyu.music`; existing library/preferences/download/cache/Voice data formats are unchanged.
