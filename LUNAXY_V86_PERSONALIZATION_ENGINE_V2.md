# Lunaxy Music V86 — Personalization Engine V2 + ListenBrainz

## 本轮目标

V86 不再继续给旧的 `收藏 +7 / 歌单 +3 / 最近 +1` 规则打补丁，而是建立一套与播放链完全隔离的个性化推荐基础设施，并在同一轮接入 ListenBrainz 的开放协同发现能力。

## 阶段 1：本地行为数据层

新增 `PersonalizationStore`，使用 Android SQLite（WAL）保存推荐所需的行为元数据：

- play_start：开始播放
- progress：真实自然播放时长增量（不是拖动后的进度位置）
- complete：达到有效听完阈值
- skip：短时主动切歌
- replay：明显重复播放
- favorite_add / favorite_remove
- playlist_add / playlist_remove

数据库只保存歌曲元数据、行为和聚合统计，**绝不保存音频 URL、解析 token、SourceCoordinator 健康状态或 PlaybackUrlStore 数据**。

数据库采用“两级保留”而不是无限堆原始日志：

- 原始行为事件最多保留约 12000 条，主要服务 Session、最近歌曲、最近跳过等需要事件顺序的信号；
- 新增 `artist_daily_stats` 日聚合表，把歌手行为按天压缩，支持明确的 7 / 30 / 180 天画像，并保留约 400 天后自动清理；
- 歌曲/歌手累计统计长期保留；自然 progress 降低为约 30 秒一次聚合写入，避免高频写盘挤掉长期历史。

因此重度听歌也不会因为 progress 事件太多导致长期画像突然“失忆”，数据库仍保持元数据级的小体积。

`MainActivity` 的播放行为追踪、LibraryStore 的收藏/歌单行为、RecommendationEngine 共享同一个 `PersonalizationStore` 写入队列。真正生成推荐前在后台线程执行一个短写入 barrier，保证刚刚发生的 skip / complete / 收藏能进入本次排序，同时正常播放回调仍是异步写盘，不阻塞 UI。

## 阶段 2：长短期 + Session 用户画像

新增 `TasteProfileEngine`：

- 长期画像：累计播放、听完、重复、收藏、歌单、跳过等
- 180 天耐久趋势（日聚合 + 慢衰减）
- 30 天中期趋势（日聚合 + 中衰减）
- 7 天短期趋势（日聚合 + 快衰减）
- 当前 Session 强权重（原始事件即时反应）
- V85.1 以前已有的收藏、歌单、最近播放继续作为冷启动 bootstrap

因此升级到 V86 后第一天就能推荐，不需要重新听几周；之后行为数据库会逐渐取代旧的粗粒度规则。

为避免把拖动到结尾误判成“喜欢”，complete 同时要求足够的真实自然播放时长；短时切歌只有在无播放错误时才作为负反馈。

## 阶段 3：ListenBrainz 协同发现

新增 `ListenBrainzDiscoveryClient`，匿名只读使用：

1. 根据 Lunaxy 本地画像选出种子歌手。
2. 通过 MusicBrainz Artist Search 将歌手名映射为 Artist MBID，并本地缓存约 120 天。
3. 使用 ListenBrainz `GET /1/lb-radio/artist/{seed_artist_mbid}` 获取基于开放听歌数据的相近歌手。
4. 相关歌手再通过 Lunaxy 已有网易/QQ元数据搜索适配器映射成正常 `Song`。
5. 真正播放时仍然交给原 `SourceCoordinator` 实时解析，不使用 ListenBrainz/MusicBrainz 参与播放 URL。

ListenBrainz 失败、429、离线或 MusicBrainz 匹配失败时均为 soft fallback：推荐继续使用本地画像 + 原有音乐目录，不阻塞 App。

隐私边界：V86 **不会向 ListenBrainz 上传 Lunaxy 的听歌历史**，也不要求 ListenBrainz 用户 token。

接口依据（2026-08-31 检查）：
- ListenBrainz API root: https://api.listenbrainz.org
- ListenBrainz LB Radio artist endpoint: `/1/lb-radio/artist/{artist_mbid}`
- MusicBrainz WS/2 Artist Search 用于匿名 MBID 映射

发布/商业化提示：MusicBrainz 公共 Web Service 的官方说明要求客户端遵守限流和 User-Agent，并指出免费公共服务面向非商业使用；若 Lunaxy 未来商业发行，应在发布前重新确认 MusicBrainz/MetaBrainz 当前服务条款或切换到合适授权/镜像。代码层已把该依赖做成可失败的外部发现增强，不是 App 的硬依赖。

## 阶段 4：日推与私人电台分脑

### 每日推荐

偏发现：
- 强兴趣歌手的未近期歌曲
- ListenBrainz 相关歌手发现
- 少量旧收藏/歌单 rediscovery
- 最近 7 天歌曲降权
- 最近跳过歌曲强降权
- 同歌手数量上限，避免列表被一位歌手占满
- 每天稳定缓存，当天顺序不反复变化

### 私人电台

偏当前口味：
- 当前 Session、最近 7 天、长期兴趣混合
- 本地歌曲作为稳定锚点
- 熟悉歌手扩展
- ListenBrainz Easy 模式提供较保守的相近歌手探索
- 每次重新打开都会重新读取最新行为，不做整日缓存
- 私人电台生成改到 IO 线程，外部发现不会卡 UI 主线程

### 天气电台

保留天气/时段场景逻辑，但本地锚点改用 V2 Taste Profile，不再只依赖旧 +7/+3/+1 权重。

## 播放链隔离红线

V86 未修改：

- `PlaybackService`
- `SourceCoordinator`
- `PlaybackUrlStore`

推荐系统只回答“推荐哪首歌”，播放系统仍然回答“这首歌此刻从哪条线路播放”。

封面/歌词缓存、V84 播放会话历史、V85.1 播放详情歌手跳搜索均继续保留。


## 第二轮数据层 Review 收口

初版实现 review 时发现：若把自然播放 progress 高频长期保存在原始事件表里，重度用户会更快把历史事件挤出上限，这与“长期画像”目标冲突。V86 最终版因此增加 `artist_daily_stats` 日聚合层，并把 progress 持久化节奏收敛到约 30 秒。原始事件负责顺序/Session，日聚合负责 7/30/180 天，累计表负责长期。三层职责明确，避免既浪费空间又丢长期趋势。
