# Lunaxy Music Delivery Workflow

1. 每轮必须以用户提供/确认的最新完整源码 ZIP 为唯一基线，不凭记忆重建。
2. 一轮尽量完成：读源码/交接 → 风险检查 → 实现 → 两轮自审/回归 → Stable/Beta 身份审计 → 尽可能真实构建 → 双 ZIP 打包校验。
3. Stable 固定 `com.xingyu.music`；Beta 固定独立 `com.xingyu.music.beta1`，数据隔离、App 名带 Beta、启动图标有明显 β。
4. Stable/Beta 功能代码同源；除身份与测试渠道差异外禁止制造行为分叉。
5. 系统返回键逐级返回；Home 根层只退后台。
6. 不触碰用户未点名的稳定播放、推荐、搜索、缓存、下载链；必要改动保持最小边界。
