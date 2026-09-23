# Lunaxy Music V73：列表去来源化与播放页文字来源

V73 严格基于上一轮实际交付的 V72 完整源码增量修改；V72 精确睡眠定时完整保留；V60 继续永久保留为 Golden Recovery。

## 1. 歌曲列表不再显示平台球

搜索综合结果、普通歌单/导入歌单/收藏/最近播放等通用歌曲行、手动排序列表，全部移除 V72 的 3D 平台光泽球。释放出来的横向宽度全部回到歌曲标题/歌手/专辑区域，减少长歌名省略。搜索页顶部的平台筛选器仍保留。

## 2. “更多 → 歌曲信息”查看可用来源

歌曲操作菜单新增“歌曲信息”。信息页显示歌名、歌手、专辑、时长，以及该 Canonical Song 当前携带的全部 Catalog 来源。来源只读取 Song variants，不改 variants/source/preferSource/搜索合并/播放路线。

## 3. 播放详情保留来源文字

播放详情中间来源区保留，但由 3D 球改成无底框、无胶囊、无圆点的轻量文字：

- 网易：淡紫
- QQ：淡蓝
- 酷我：淡绿
- 酷狗：淡金黄

只显示当前歌曲实际拥有的来源；其他少见来源使用次级文字色。文字带极弱柔光，只承担二级信息，不抢歌名/封面视觉层级。这里展示的是 Catalog 身份，不是 Huibq / 全豆要 / juhe 等底层 Audio Provider。

## 4. Locked Core

app/src/main 相对 V72：changed=1、added=0、deleted=1。唯一改动原文件为 MainActivity.java；删除的 PlatformOrbView.java 是 V72 新增的纯展示控件。PlaybackService 与 V72 字节级一致。25/25 Locked Core 文件一致；MainActivity 11/11 播放/搜索关键方法体一致；PlaybackService 18/18 播放核心方法体一致。

## 5. 版本

- versionCode = 730
- versionName = 73.0.0
- applicationId / namespace = com.xingyu.music
- assembleDebug / assembleRelease / BUILD_APK.bat 额外输出：`Lunaxy Music V73.apk`
