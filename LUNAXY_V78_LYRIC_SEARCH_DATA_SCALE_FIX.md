# Lunaxy Music V78

## 根因
V77 与 Beta V9 的 runtime 源码相同，但两者 applicationId 不同，Android 私有数据完全隔离。正式版继承长期收藏、歌单、历史与歌词缓存。旧实现会在远程歌词索引结果合并前同步遍历本地候选，因此正式数据规模越大，远程结果越容易被本地扫描拖住。

## 修复
- local lyric scan 与 NetEase/QQ lyric index 并行。
- local candidate cap=220，scan budget≈650ms。
- 本地 scan 不得延长远程 4.8s deadline。
- 6.5s UI fail-safe，杜绝永久 pending。
- Locked Core 不动。
