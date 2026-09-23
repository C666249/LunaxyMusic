# Sunflower Music V3.1 Test — 来源标签自适应 UI 修复

基线：`SunflowerMusic-V3-Full.zip`

本轮只处理 V3 真机验收发现的搜索/歌曲卡片来源标签显示不完整问题，不修改已经跑通的 Multi-Catalog Route Matrix 播放架构。

## 修改

- 搜索结果来源标签由固定 `68dp/72dp` 宽度改为 `wrap_content`。
- 标签设置最小宽度、最大宽度与最多两行：短标签保持紧凑，`网易 · QQ · 酷我 · 酷狗` 等多 Catalog 标签会在胶囊内部自然换行并完整显示。
- 搜索全部结果、通用歌曲卡片、手动排序卡片统一使用同一自适应规则，避免同一 `Song.sourceLabel()` 在不同页面再次被裁切。
- 版本号更新为 `3.1.0-test` / `versionCode 310`，测试包 applicationId 仍为 `com.xingyu.sunflowermusic`。

## 明确未修改

- TX/KW/KG/WY Catalog 搜索与 Exact Track Gate
- Provider × Variant Route Matrix
- AudioProviderHub / SourceCoordinator / TrackRouteStore / SourceHealthStore
- Media3 READY 成功判定与时长核验
- TrackVariantStore / 后台 enrichment
- LibraryStore / 收藏 / 歌单 / 最近播放数据格式
- 歌词链路、推荐、播放页、星空、唱片等成熟功能

V3 已真机验证多平台矩阵能够让此前无法播放的歌曲正常播放，本轮因此坚持最小 UI Diff，不触碰播放黄金核心。
