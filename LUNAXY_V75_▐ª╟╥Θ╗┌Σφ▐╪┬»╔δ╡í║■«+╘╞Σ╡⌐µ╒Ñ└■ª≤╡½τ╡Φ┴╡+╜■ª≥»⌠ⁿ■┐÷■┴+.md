# Lunaxy Music V75：播放详情两侧贴边

V75 严格基于上一轮实际交付 V74 完整源码增量修改；V60 继续永久保留为 Golden Recovery。

## 1. 只修正播放详情操作行位置

V74 的“歌词 / 收藏 / 添加”顺序保持不变，但不再使用三个等权重槽位把左右按钮向内收。

V75 改为一个占满可用内容宽度的 FrameLayout：

- 左侧“歌词”42dp 按钮贴齐播放页可用区域左边缘；
- 中间“♡”42dp 按钮严格锚定整行中心；
- 右侧“＋”42dp 按钮贴齐播放页可用区域右边缘；
- 仍尊重播放页既有 20dp 安全内边距，不侵入屏幕系统边缘。

视觉关系固定为：

`歌词　　　　　　　　　　　♡　　　　　　　　　　　＋`

因此左右操作会重新拉满整行，而不是停在各自 1/3 槽位中心。

## 2. 功能不变

- 歌词仍调用 `enableDesktopLyricsDirectly()`；
- 收藏 / 取消收藏及粉色状态逻辑不变；
- 加号仍调用 `showAddToPlaylist()`；
- 播放详情平台来源继续隐藏；
- “更多 → 歌曲信息 → 可用来源”继续保留；
- V72 精确睡眠定时完整保留。

## 3. Locked Core

本轮只允许修改 MainActivity 播放详情布局，以及版本 / 说明 / Guard 元数据。PlaybackService、Catalog、SearchRanker、Provider/READY 路线、下载链、Desktop Lyrics Service、Sleep Timer 均不得修改。

## 4. 版本

- versionCode = 750
- versionName = 75.0.0
- applicationId / namespace = com.xingyu.music
- assembleDebug / assembleRelease / BUILD_APK.bat 额外输出：`Lunaxy Music V75.apk`
