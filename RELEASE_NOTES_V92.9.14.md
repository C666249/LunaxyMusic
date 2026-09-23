# RELEASE NOTES — Lunaxy Music V92.9.14 Stable

## Artwork-colored staircase loading

- 搜索“全部结果”、每日推荐/私人电台/天气电台/最近播放、歌单详情继续沿用 V92.9.13 的逐行楼梯式加载，但行级 Skeleton shimmer 与 `CurtainRevealFrame` 的移动光缝不再固定使用统一青蓝色。
- 当有歌曲正在播放时，加载色优先来自当前播放歌曲封面的 `ImageLoader.accent()`；如果新歌封面尚未解析，不会错误沿用上一首歌的颜色，而是暂时使用当前页面自己的 fallback。
- 当前封面稍后解析完成时，屏幕上仍在运行的逐行占位与 Curtain 光缝会原地更新颜色，不重新启动 shimmer，因此楼梯节奏不会跳回起点。

## Playlist Hero artwork color

- 歌单详情顶部大卡片从“按平台固定色”改为“按该歌单首张封面取色”。封面已经在内存缓存时首帧直接使用真实封面 accent。
- 若封面异步返回，Hero 材质在短时间内平滑过渡到封面色；同一颜色同时写入 `PlaylistHeroSnapshot` 和正在运行的 `PlaylistHeroMorphView`，保证列表卡片 → 详情大卡片的 Shared Object 不出现颜色断层。
- 无封面时继续使用原平台色作为安全 fallback。

## Compatibility

No data migration. Stable applicationId remains `com.xingyu.music`; existing library/preferences/download/cache/Voice data formats are unchanged. Playback/source/recommendation/search engines are not modified.
