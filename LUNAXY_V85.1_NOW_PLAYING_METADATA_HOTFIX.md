# Lunaxy Music V85.1：播放详情歌曲信息显示热修

本版严格基于 V85 Stable 完整源码修复。

## 修复
- 播放详情歌曲名下方恢复稳定显示 `歌手 · 专辑`。
- 视觉保持原有灰色小字，不增加蓝色、下划线、胶囊或按钮外观。
- 点击该信息行使用主歌手名进入搜索页并直接搜索。
- 放弃 V85 的 ClickableSpan/LinkMovementMethod 文字内部超链接实现，改为普通 TextView 渲染 + 透明点击热区，避免部分设备出现信息行不显示。

## Locked Core
- PlaybackService / SourceCoordinator / PlaybackUrlStore 不修改。
- V85 封面磁盘缓存与歌词缓存策略不修改。
- 不缓存或改变音频播放 URL。
- 不改变现有播放详情构图、按钮位置、星空、歌词卡片与播放控制。

## 版本
- Channel: Stable
- versionName: 85.1.0
