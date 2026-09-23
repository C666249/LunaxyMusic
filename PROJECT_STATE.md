# Lunaxy Music Project State — V92.8.10

当前基线：V92.8.9。

本轮只扩展首页视觉系统：
- “为你推荐”保留原版并新增四种可切换卡片材质，共五种；入口为标题右侧纯图标，底部抽屉选择并持久化。
- 最近播放封面使用 ImageLoader 已有封面取色结果生成低强度环境光；横向滚动仅做 1.00→0.965 的轻微缩放和小幅透明度衰减，不做 3D 倾斜。
- 首页搜索框复用播放列表当前歌曲 FluidTrackHaloDrawable，因此边框/流光跟随当前播放封面主色。
- V92.8.9 的播放详情歌名点击直搜保持不变。

运行时代码相对 V92.8.9：仅修改 `MainActivity.java`，新增 `ui/CoverAmbientDrawable.java` 与 `ui/RecommendationGlassDrawable.java`。
Stable/Beta 功能 Java 源码保持字节级同源。

验证详情见 `dev-logs/V92.8.10-validation.md`。
