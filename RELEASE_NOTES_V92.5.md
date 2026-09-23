# Lunaxy Music V92.5 — 连续队列浏览 / 唱片清晰度 / 歌词边界 / 失败自动下一首

## 主要改动

- 星空实验室新增「唱片景深 / 侧唱片清晰度」，默认完全清晰；Android 12+ 可连续调节真实 RenderEffect 景深。
- 播放详情横向手势由单步切歌升级为连续队列浏览：同一手势可跨多个 retained 唱片槽，边缘持续补入播放列表中的更远歌曲；松手后一次性跳到最终目标。
- 随机播放模式下，主动横向浏览仍按当前播放列表顺序展示；自动随机下一首仍由 PlaybackService 原逻辑决定。
- 歌词区进一步下移；播放页处于顶部时歌词卡完全透明，不再露出半句。向下滚动后歌词渐进出现。
- 播放页外层 ScrollView 禁用 overscroll EdgeEffect；歌词卡改为无描边玻璃，减少顶部“墙”式闪烁。透明歌词区域不再抢横向手势。
- 出现「这首暂时没接通」后显示“3 秒后自动切到下一首”；只有 3 秒后仍是同一失败歌曲、仍存在 error 且队列至少两首时才执行 next。

## 未改动

PlaybackService 音源解析、SourceCoordinator、PlaybackUrlStore、Personalization Engine、Search Engine V2、歌词缓存与封面缓存策略保持 V92.4。
