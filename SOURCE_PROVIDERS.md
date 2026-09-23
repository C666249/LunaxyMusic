# Sunflower Music — Provider Pool（V2）

当前内置/社区 Provider Pool：

- Huibq（APK 内置 V47 历史快照）
- 全豆要 QDY
- Juhe 聚合
- LX Source
- Flower 野花
- Grass
- SixYin 六音
- ikun

这些名称代表**可替换的 LX Script Adapter**，不是 8 个被承诺长期稳定的独立商业服务。服务器、脚本、平台策略都可能变化。

## V2 与 V1 最大区别

V2 不再把 Provider 的健康看成一个总数字，而是至少拆到：

`provider + catalog platform + quality`

例如：

`QDY|wy|320k READY` 不会提升 `QDY|tx|320k` 的排名。

状态页会分别显示 QQ(tx) 与网易(wy) 实播结果。

## LX Host

Sunflower V2 的 `LxSourceRuntime` 是**自研的 LX Mobile 2.0 公共协议兼容 Host**：

- 继续使用受限 WebView JS engine；
- 对齐 `lx` 对象、Promise event API、request callback、Buffer/Crypto、binary HTTP、初始化过滤等公开行为；
- 禁止脚本文件/Content 访问，并阻断 WebView 直接联网，HTTP 统一经 Native bridge；
- 不声称与 LX 官方 QuickJS 引擎逐字节等价。

## 脚本版本策略

- Huibq：继续使用 APK 内置历史快照，不自动替换。
- 其他社区源：首次使用时下载并 pin 到本机。
- 只有用户显式点击“更新社区源”才清除 pin，下一次重新下载。

这样可以在“上游修复”与“已验证版本可复现”之间取得平衡。

## QQ 原版原则

Sunflower 的目标不是让任意同名音频发声。QQ 独家曲（尤其周杰伦等版权集中曲目）默认不进行低置信跨平台替代。未来若加入跨 Catalog Provider，必须先通过 Exact Track Gate；未来若具备正式 QQ/QPlay 授权条件，则优先使用 `OfficialPlaybackProvider` seam。
