# V92.8.2 — 播放器展开动画可调

## 本轮变化

继续使用 V92.8 / V92.8.1 已确认的 Mini Player → Full Player Morph，不更换动画设计。

新增 **星空实验室 → 动态 → 播放器展开动画**：

- 可调范围：**450 ms–1000 ms**。
- 默认值：**900 ms**，与 V92.8.1 当前慢速体验保持一致。
- 滑杆右侧实时显示当前毫秒数。
- 设置写入本地 SharedPreferences，重启后保留。
- “重置”星空实验室时恢复 900 ms。
- 点击 Mini Player 的完整 Morph 直接读取该时长。
- Mini Player 上滑后松手的自动完成/回退按相同比例调整；手指拖动过程仍实时跟手。
- 该设置只驱动 View 动画，**不改变 PlaybackService、歌曲播放速度、进度、seek、切歌和队列**。

## 兼容性

Stable 保持生产 applicationId 与升级链；Beta 继续使用独立 applicationId、独立数据与 β 图标。
