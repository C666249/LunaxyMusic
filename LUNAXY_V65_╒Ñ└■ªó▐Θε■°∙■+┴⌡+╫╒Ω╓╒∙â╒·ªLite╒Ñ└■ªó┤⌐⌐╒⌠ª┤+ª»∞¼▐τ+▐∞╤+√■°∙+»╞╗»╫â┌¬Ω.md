# Lunaxy Music V65 — Download Matrix Lite + Playback-First Adaptive Transfer

基线：`LunaxyMusic-V64-Full.zip`。

## 1. 旧 READY 失效自动自愈
V64 下载会优先复用 PlaybackUrlStore 中曾经 Media3 READY 的非 TX URL。如果该临时 URL 后来返回 403/404，旧逻辑会把本次下载直接判失败，用户往往必须先在线播放一次，让播放链刷新 READY URL 后才能重新下载。

V65 把 READY Cache 明确定义为“快速路径，不是真理”：
- 401/403/404/410 视为该精确 URL 对当前下载计划 stale；
- 只在 Download Plan 的 plan-song 状态中保存 stale URL 指纹；
- 不调用 PlaybackUrlStore.invalidate，不写 SourceHealthStore / TrackRouteStore，不修改播放 Matrix；
- 立刻进入受预算约束的 Cold Discovery。

如果后来在线播放生成了一个不同的新 READY URL，它的 URL 指纹不同，下一轮下载仍可正常使用。

## 2. Download Matrix Lite：8 + 8
Cold Discovery 不再只有 4 次 Provider 尝试。V65 每首歌曲采用两阶段预算：

### 第一轮
- 最多 8 个真实 Provider × Catalog × Quality Matrix Cell；
- 活跃发现窗口约 15 秒；
- Exact Gate < 0.98 仍拒绝；
- TX 仍完全排除；
- Provider cooldown / 429 / init / network 保护继续生效；
- 成功即停止；预算耗尽立即跳下一首。

### 第二轮
整张 Download Plan 第一轮全部跑完以后，只对“等待线路 / 待重试”歌曲自动补一轮：
- 最多再 8 个新 Matrix Cell；
- 约 15 秒；
- 第一轮已实际调用过的 route cell 持久化在 plan-song 状态中，第二轮不会重复。

第二轮仍失败后停止自动请求，状态进入失败，等待用户手动“重试失败项”。这避免后台围着少数难歌无限请求音源。

## 3. Playback-First 动态带宽
V64/Beta5 在线稳定播放时固定将下载压到约 160KB/s。真机已经验证：暂停在线播放后下载速度会瞬间恢复到网络/CDN可用速度。

V65 删除固定 160KB/s 人工限速：
- 播放稳定：单线程下载允许全速共存；
- 播放解析 / Media3 BUFFERING：下载读流立即等待；
- 播放从关键阶段恢复后保留约 5 秒 recovery hold；
- recovery hold 到期后恢复全速，且不会因为已经恢复稳定而不断重新延长 hold。

P0 Playback > P1 Download 的优先级不变，只是不再在播放稳定时长期浪费网络带宽。

## 4. 教程同步
首次总 Onboarding 仍保持 5 步，不扩成长教程；第 2 步更新“播放自动换路 + 下载 stale READY 自愈”的概念。
下载中心 Context Coach 新增/更新：
- stale READY 403/404 会主动换路；
- 首轮 8 路 / 15 秒；整单后第二轮再 8 条新路；
- 单首失败会跳过，不阻塞大歌单；
- 第二轮后停止自动请求，避免 Provider 请求风暴；
- 播放解析/BUFFERING时下载自动让路。

## 5. Locked Core
本轮授权修改下载平面及其 UI 教程；在线播放 PlaybackService / AudioProviderHub / SourceCoordinator / ExactTrackMatcher / SearchRanker / PlaybackUrlStore / SourceHealthStore / TrackRouteStore / TrackVariantStore / 四平台 Catalog API 均不修改。
V64 Ghost Swipe 手势实现保持原样。
