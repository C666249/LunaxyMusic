# Lunaxy Music V92.9.14 Stable

这是 **V92.9.14 Stable** 完整 Android Studio 源码工程，ZIP 根目录即工程根（包含 `settings.gradle.kts`）。

本版把 V92.9.13 的逐行楼梯式加载继续统一到“唱片封面驱动”的色彩语言：搜索全部结果、每日推荐/私人电台/天气电台/最近播放、歌单详情的逐行 Skeleton shimmer 与 Curtain Reveal 移动光缝，不再固定为统一蓝色；当存在当前播放歌曲时，优先使用当前歌曲封面的实际 accent。封面色稍后解析完成时，仍在屏幕上的加载行会原地换色，不重启 shimmer 相位。

歌单详情顶部大卡片改为按该歌单首张封面取色。缓存中已有封面时首帧直接使用封面色；封面异步返回时用短色彩过渡更新材质，并同步 Shared Hero 代理，避免卡片飞行过程中出现两套颜色。

身份：`com.xingyu.music` / `92.9.14` / `versionCode 993`。

**无数据迁移。** Stable 沿用既有包名、数据库/偏好/Library JSON、下载缓存与 Voice 数据格式。
