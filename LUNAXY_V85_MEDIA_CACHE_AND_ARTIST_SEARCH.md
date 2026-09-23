# Lunaxy Music V85 — 封面/歌词持久缓存与播放页歌手直达搜索

基线：**V84 Stable Full Source**。

本轮先审计 V84 的真实代码，再做最小风险改动。结论是：播放解析链已经有自己独立的 `PlaybackUrlStore -> SourceCoordinator -> PlaybackService` READY URL 缓存、自愈与失效重解析机制；歌词也已有 `LyricCacheStore`。因此 V85 **不重构播放链、不把封面/歌词缓存接进音频 URL 解析**，只补展示资源缓存与歌词身份稳定性。

## 1. 封面：24 MB 内存缓存 + 160 MB 磁盘 LRU

V84 `ImageLoader` 只有 24 MB `LruCache<String, Bitmap>` 内存缓存，App 进程退出后缓存全部消失，所以再次打开时封面取决于网络。

V85 保留原内存缓存，并为远程封面增加：

- `cacheDir/lunaxy_cover_cache_v85` 磁盘缓存；
- 上限约 160 MB，按最近访问时间 LRU 淘汰；
- 同一 URL 的并发请求做单 URL 合并，避免列表/播放页同时重复下载；
- 单张远程图片最大 24 MB 防护；
- 磁盘文件损坏会自动删除并回源；
- `content://` / `file://` 本地图片仍走原本地读取路径，不复制进远程缓存；
- 缓存目录属于 Android cache，系统在存储紧张时可以清理，不会无限增长。

**边界：**这里只缓存封面图片字节，不读取、不写入、不替换任何音频播放 URL。

## 2. 歌词：保留 V84 缓存，修复“同一首歌却命不中”

V84 已经会在歌词第一次成功获取后写入 `LyricCacheStore`，命中时播放详情可以直接本地显示；问题在于旧 key 只有 `Song.key()`，其中包含 2 秒时长桶。跨平台同一录音的时长元数据只要略有差异，就可能被当成新的歌词缓存项。

V85 不更换歌词获取链，只增强缓存身份：

1. 优先使用歌曲已有的 provider 原生 ID（网易/QQ/酷我/酷狗等 `source + sourceId`）；
2. 再用“规范化歌名 + 歌手 + 6 秒时长桶”作为跨平台兜底；
3. V20-V84 的旧 `song:<Song.key()>` 缓存继续读取；首次命中旧缓存后自动迁移到 V85 key；
4. 缓存内容仍然只是 LRC 文本，不包含音频地址。

这样不会因为首选播放来源变化就强迫歌词重新联网，同时兼容旧版本已经缓存过的歌词。

## 3. 播放详情：歌手文字保持原 UI，但可点击搜索

视觉完全保持原来的：

`歌手  ·  专辑`

- 不改颜色；
- 不加下划线；
- 不做按钮/胶囊；
- 常见多歌手分隔符下，每个歌手名分别成为透明点击区域；
- 点击歌手后关闭播放详情，切到“搜索”Tab，自动填入该歌手并直接执行搜索；
- 专辑文字保持普通文本，本轮不新增专辑页或专辑搜索行为；
- 不主动拉起键盘。

## 4. 为什么不会把已经稳定的播放链弄断

V85 对以下 V84 核心播放文件保持字节级不改：

- `playback/PlaybackService.java`
- `data/SourceCoordinator.java`
- `data/PlaybackUrlStore.java`
- Provider / Resolver / Media3 播放矩阵

V84 现有音频 URL READY cache 的行为仍然是：先尝试历史 READY URL；如果 Media3 拒绝，立即 `invalidate()`，缓存命中只允许一次无惩罚 fresh refresh，然后重新走正常矩阵。这套逻辑 V85 不改变。

因此 V85 的“封面缓存”和“歌词缓存”只是 UI/文本资源加速层，不会让某个旧封面 URL 或旧歌词条目决定音频从哪条链播放。

## 5. 版本与身份

Stable：
- applicationId：`com.xingyu.music`
- versionCode：850
- versionName：`85.0.0`

Beta：
- applicationId：`com.xingyu.music.beta1`
- versionCode：851
- versionName：`85.0.0-beta16`
- App 名：`Lunaxy Music Beta`
- 启动图标继续使用明显 β 标识，可与 Stable 并存。
