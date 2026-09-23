# Lunaxy Music V92.9.13 Stable

这是 **V92.9.13 Stable** 完整 Android Studio 源码工程，ZIP 根目录即工程根（包含 `settings.gradle.kts`）。

本版统一了密集歌曲列表的入场语言：每日推荐等 Smart Collection 与搜索“全部结果”不再使用整页从左到右的机械 Curtain 光缝，而是与歌单详情一致，按歌曲行逐条错峰、楼梯式展开；Skeleton shimmer 也按行错相。

歌单 Tab 卡片 → 歌单详情大卡片的 Shared Object 同时增强：源卡片会保留到目标 Hero 几何稳定，代理元素再接管并缓慢滑动、扩大、圆角/封面/标题同步形变，目标未在首帧测量完成时会等待数个 animation frame，而不是直接退化。

身份：`com.xingyu.music` / `92.9.13` / `versionCode 991`。

**无数据迁移。** Stable 沿用既有包名与本地数据格式。
