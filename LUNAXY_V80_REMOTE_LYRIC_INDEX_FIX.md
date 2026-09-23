# Lunaxy Music V80 — Remote Lyric Index Fix

基线：Stable V79。

本轮只修歌词反查的远程发现适配层：

- 本地收藏 / 歌单 / 历史 / 本地歌词缓存继续 **不参与候选歌曲发现**。
- 网易歌词搜索主路切换为 EAPI CloudSearch：`/api/cloudsearch/pc`，`type=1006`；旧 WEAPI / legacy 仅作回退。
- QQ 歌词搜索主路切换为当前 Desktop SearchCgiService 请求形状：`DoSearchForQQMusicDesktop` + `search_type=7` + `grp=1`；signed mobile 仅作回退。
- 播放 / 下载 / Provider / Canonical Track / Lyrics Hub 核心链不改。

目标：让“冷咖啡离开了杯垫”这类歌词句子真正依赖网易/QQ远程全文索引发现歌曲，而不是依赖 Beta 本地缓存。
