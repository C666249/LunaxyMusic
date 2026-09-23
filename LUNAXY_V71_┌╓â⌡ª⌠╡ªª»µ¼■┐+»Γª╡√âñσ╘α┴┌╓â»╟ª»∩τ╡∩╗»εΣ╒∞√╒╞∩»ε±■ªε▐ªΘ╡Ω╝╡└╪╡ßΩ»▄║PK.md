# Lunaxy Music V71：全局平台圆点与版本化 APK

V71 严格基于 V70 增量修改，播放/搜索/Provider/下载/学习系统均保持 V70 行为。

## 1. 每首歌曲的平台身份统一改为四色圆点

所有以 `Song.sourceLabel()` 作为歌曲身份标签的主要可见位置统一改为紧凑圆点：

- 网易：紫色
- QQ：蓝色
- 酷我：绿色
- 酷狗：黄色

覆盖：搜索综合结果、搜索“全部”页、普通歌单/导入歌单/收藏/历史等通用歌曲行、手动排序列表、正在播放详情中的歌曲平台身份。

顶部平台筛选器、智能音源矩阵坐标轴、诊断文字仍保留平台名称，因为这些位置承担筛选/解释功能，不会挤压歌曲标题。

本轮圆点只读取 `Song.variant()`；不会更改 variants、source、sourceLabel、Catalog merge、SearchRanker、Provider Matrix 或播放路线。

## 2. APK 文件名带版本号

- versionCode = 710
- versionName = 71.0.0
- assembleDebug / assembleRelease 额外导出：`Lunaxy Music V71.apk`
- `BUILD_APK.bat` 的 dist 产物也改为：`Lunaxy Music V71.apk`

