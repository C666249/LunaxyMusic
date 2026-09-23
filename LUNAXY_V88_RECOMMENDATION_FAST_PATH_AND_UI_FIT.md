# Lunaxy Music V88 — Recommendation Fast Path + Personalization UI Fit

## 本轮来源

V88 直接从 V87 Stable/Beta 完整源码继续。用户真机反馈两件事：

1. 每日推荐、私人电台进入后载入时间过长；
2. 首页推荐卡片 / 个性化矩阵统计卡存在内容显示不全。

本轮目标不是削弱 Personalization Engine V2 / ListenBrainz，而是把商业 App 常用的“已缓存内容立即可用，网络负责后台更新”应用到推荐链。

## 1. V87 为什么慢

V87 的完整推荐生成链会依次涉及：本地 Taste Profile → 熟悉歌手目录扩展 → MusicBrainz Artist MBID → ListenBrainz LB Radio → 相关歌手再映射回网易/QQ → 排序/多样性重排。

这些步骤都适合作为后台 enrichment，却不应该成为“打开推荐页”的阻塞条件。尤其原实现对多位种子/相关歌手的目录查询存在明显串行等待。

## 2. V88 Fast Path

### 已有快照

打开页面立即读取 SharedPreferences 中已经物化的 `daily_songs` / `private_songs`。

- 当天日推快照仍新鲜：直接使用，不再联网生成。
- 日推快照来自上一日/旧 Engine：先显示旧快照，同时后台更新今天的新列表。
- 私人电台：先显示最近一次快照，同时根据最新 Session 后台重算。

### 没有任何快照

只读取本地收藏、歌单、最近播放与 Personalization 数据库，先生成 local preview；这个路径明确不调用 MusicBrainz、ListenBrainz、网易或 QQ。

因此即使外网很差，用户也应该先进入一个可操作页面，而不是一直等全链完成。

### 后台 enrichment

完整推荐继续保留：熟悉歌手新发现 + ListenBrainz 协同发现 + 长短期/Session 画像 + 未听账本 + 多样性重排。后台结果完成后，只有用户仍停留在对应推荐页才刷新，并关闭页面切换动画、恢复原滚动位置。

## 3. 目录扩展加速

- 歌手目录结果：18 小时新鲜缓存；网络瞬时失败允许回退 7 天内旧元数据。
- 多个独立歌手搜索：最多 4 个固定后台 worker 并行处理。
- 单批 job 有 6.5 秒等待预算，超时任务请求取消，不能无限拖住整批候选。
- 搜索缓存仍然只是 Song 元数据，绝不缓存播放 URL。

## 4. ListenBrainz / MusicBrainz 不再阻塞首屏

`ListenBrainzDiscoveryClient` 先合并本地已经缓存的 MBID + LB Radio 关系。若仍需联网，一次推荐 refresh 最多只允许 1 个未热/过期种子进入 MusicBrainz / ListenBrainz 网络路径。

这既保留全局协同发现，又避免一次私人电台打开对 2~3 个种子连续支付 MusicBrainz 1 req/s 限流和 LB 网络延迟。常用种子会随使用逐步暖缓存。

## 5. 卡片裁切修复

首页 `libraryActionCard` 的内部垂直需求约为：15 padding + 38 icon + 10 gap + 32 title + 13 padding = 108dp，但 V87 外层写死为 100dp，因此真机会裁掉底部。V88 把四张推荐入口卡统一改到 114dp。

个性化矩阵的推荐构成原为一行四列，在手机 modal 内 `ListenBrainz` 等标签空间不足。V88 改成 2×2 指标格，并允许标签最多两行、数据说明自然换行。

## 6. 不变的红线

V88 不修改：

- `PlaybackService.java`
- `SourceCoordinator.java`
- `PlaybackUrlStore.java`
- `ImageLoader.java`
- `LyricCacheStore.java`

因此推荐加速不会改变 V84 起已经稳定的音频 URL 失效换链，也不会影响 V85 的封面/歌词缓存。
