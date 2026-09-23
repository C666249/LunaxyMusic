# Lunaxy Music V84 — Playback Session Memory

基线：用户提供的 **LunaxyMusic V83 Full Source**。

本轮只新增“商业音乐 App 式播放会话恢复”，不改 UI 设计，不重构 Provider / Resolver / 下载 / 歌词核心。

## 新增能力

- 持久化当前播放队列的完整顺序。
- 持久化当前曲目索引与稳定 Song key。
- 持久化当前播放进度和已知时长。
- 播放中每 5 秒做轻量 checkpoint；暂停、拖动进度、切歌、改队列、任务被移除、Service 销毁时也会保存。
- App / 播放服务下次启动时恢复队列、当前曲目与进度。
- 恢复后默认保持暂停，**不会擅自自动播放**；再次点击播放后才走原有 SourceCoordinator / Media3 链路，并在 prepare 前恢复 seek 位置。
- 若持久化内容损坏，启动时自动忽略并清理损坏 checkpoint，不阻塞 App。
- 用户把播放队列最后一首显式删除时会清空持久化会话；仅停止播放或移除通知则保留最后听歌上下文。

## 数据边界

新会话文件只保存 `Song` 队列、当前索引、position/duration 与更新时间；不保存临时音频 URL、音源健康状态、失败路由、错误信息，也不保存“上次是否正在播放”作为自动播放指令。

原有 `LibraryStore` 已负责收藏、最近播放、导入/本地歌单等长期数据，本轮不改变这些结构。

## 代码范围

- 新增：`app/src/main/java/com/xingyu/music/data/PlaybackSessionStore.java`
- 修改：`app/src/main/java/com/xingyu/music/playback/PlaybackService.java`
- 版本与发布文档同步升级为 V84。
