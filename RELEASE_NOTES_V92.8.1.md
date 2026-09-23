# V92.8.1 — V92.8 播放器转场慢速版

本轮明确回到 **V92.8 的 Mini Player → Full Player 连续表面 Morph** 作为唯一动画基线，不采用 V92.9 的幕布揭示，也不采用 V92.10 的 Immersive Focus。

## 本轮只调整时间尺度

- 点击 Mini Player 打开播放详情：保持 V92.8 原有 Surface Morph、Artwork Hero、内容显现比例与 `PLAYER_OPEN` 曲线不变，主时间轴由 **450 ms 调整为 900 ms**。
- Mini Player 上滑手势仍然是实时 1:1 跟手，不增加输入延迟；仅在松手后需要自动完成/回退时放慢：打开收束 **260 → 520 ms**，回退 **220 → 440 ms**。
- 不改变唱片目标位置、Surface 几何、透明度分段、侧唱片揭示或页面背景逻辑，因此视觉结果等同 V92.8，只是整体速度约慢一倍。

## 播放与动画完全解耦

- 动画仅更新 View 的位置、缩放、透明度和 Morph 进度。
- `PlaybackService`、歌曲播放进度、解码、音源切换、自动切歌与队列逻辑未接入动画时钟。
- 即使 900 ms 转场仍在进行，音乐照常播放，播放进度照常前进。

## 修改边界

功能代码只修改：
- `MainActivity.java`：新增 3 个播放器转场时长常量，并替换 V92.8 原有 450/260/220 ms。

V92.8 的 `PlayerSurfaceMorphView`、`ArtworkMorphView`、`SpringMotion.PLAYER_OPEN` 与其他播放 UI 几何均保持不变；播放/推荐/搜索/缓存/下载核心未改。

## 版本身份

- Stable：`com.xingyu.music` / `92.8.1` / versionCode `944`
- Beta：`com.xingyu.music.beta1` / `92.8.1-beta37` / versionCode `945`

versionCode 继续高于已产生的 V92.10（942/943），确保同包名 Stable/Beta 在 Android 上仍可正常覆盖升级，而不会因为回到 V92.8 动画基线被系统判定为降级。
