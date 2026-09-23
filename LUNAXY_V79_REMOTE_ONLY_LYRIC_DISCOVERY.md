# Lunaxy Music V79 — Remote-only Lyric Discovery

V79 与 Beta V11 使用同一功能主体。歌词反查的候选发现现在只依赖网易 `type=1006` 与 QQ `search_type=7`，不再扫描正式版本地收藏、历史、歌单或歌词缓存。远程发现同时使用独立线程池，与真实 LRC 二次核验隔离，避免前一轮核验占满线程后导致下一轮远程索引在 deadline 内拿不到执行机会。

正式包名仍为 `com.xingyu.music`，同签名可覆盖 V78 并继承数据；这些既有数据不再参与歌词候选发现。
