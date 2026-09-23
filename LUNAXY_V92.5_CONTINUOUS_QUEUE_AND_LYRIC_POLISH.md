# Lunaxy Music V92.5 — Continuous Queue Browse & Lyric Boundary Polish

基线：V92.4 Stable/Beta Full Source，同源修改。

## 本轮改动

1. **侧唱片清晰度可调**
   - 星空实验室新增「唱片景深 / 侧唱片清晰度」。
   - 默认 100% 清晰；Android 12+ 可连续调节 RenderEffect 景深虚化。
   - 只影响播放详情视觉，不修改封面缓存、音源或播放 URL。

2. **连续浏览播放队列**
   - 播放详情横向手势不再限制为上一首/下一首。
   - 一个手势可跨过多个唱片槽；五个 retained VinylRecordView 轮换角色，并持续预热新的边缘封面。
   - 拖动阶段只浏览，不触发真实音源解析；松手后按最终位置 + 轻量 velocity 投影一次性 `playAt()` 到目标队列歌曲。
   - 随机模式下主动浏览仍展示当前队列顺序；自动随机播放逻辑保持 PlaybackService 原语义。

3. **歌词初始露出与边界闪烁**
   - 歌词区域整体进一步下移。
   - 播放页停在顶部时歌词卡保持 0 alpha，不再在底边露出半句。
   - 用户向下滚动后歌词卡渐进出现。
   - 外层播放页 ScrollView 关闭系统 overscroll EdgeEffect；歌词卡去掉描边式玻璃边缘，消除顶部“墙”闪一下。
   - 透明歌词区域不会再抢占横滑手势。

4. **播放失败 3 秒自动下一首**
   - 出现「这首暂时没接通」后开始绑定当前 songKey 的 3 秒任务。
   - 3 秒后仍是同一首且仍有 error、队列至少两首时，自动切下一首。
   - 已手动换歌、错误恢复或 Activity 销毁时任务自动失效。

## 稳定性边界

- 未修改 PlaybackService / SourceCoordinator / PlaybackUrlStore 的解析与失效换链实现。
- 未修改 Personalization Engine / Search Engine V2。
- 横向队列浏览阶段只操作 UI retained views 与封面元数据缓存。
- 系统返回键继续返回上一级；Home 根页只退后台。
