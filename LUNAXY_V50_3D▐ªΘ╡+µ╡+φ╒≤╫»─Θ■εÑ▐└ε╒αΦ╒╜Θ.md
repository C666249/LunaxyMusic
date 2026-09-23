# Lunaxy Music V50 — 360° 3D 星空视觉引擎

## 唯一基线
本轮严格基于 `LunaxyMusic-V49-Full.zip` 增量开发。

本轮是纯视觉升级：V49 已验证的播放矩阵、Progressive Search、下载隔离、离线歌单、本地优先、铃声裁剪、系统声音、Context Coach 全部视为 Locked Core，不参与重构。

## 为什么 V49 的“3D 星空”转动幅度仍然小
V49 `StarfieldView` 虽然有透视深度和 rotation-vector sensor，但传感器最终只被压缩为 pitch/roll 两个有限的 screen-space parallax 值：

- 没有使用完整 yaw/pitch/roll 相机姿态；
- 横向/纵向最多只是屏幕尺寸的一小段偏移；
- 星星本质存在于当前视锥体里，离开屏幕后会重新生成；
- 所以无法通过拿手机转 90°/180°/360° 看见同一个宇宙的其他方向。

## V50 新架构
播放详情页改用新增的 `Starfield3DView`，首页/普通页面继续保留旧 `StarfieldView`。

### 1. 固定 360° 宇宙
- 固定随机 seed `LUNAXYP2`；
- 在完整球面均匀生成 2400 个星体；
- 星星位置不会因切歌重新生成，只改变随封面过渡的 tint；
- 转 360° 后理论上回到进入播放页时看到的原星区。

### 2. 完整 3D Camera Quaternion
优先：
`TYPE_GAME_ROTATION_VECTOR`

fallback：
`TYPE_ROTATION_VECTOR`

进入播放页时记录当前设备姿态作为 baseline；之后使用：
`inverse(currentDeviceToWorld) × baselineDeviceToWorld`
得到完整相机相对旋转。

不再把传感器压缩成有限的 X/Y 偏移，因此 yaw/pitch/roll 都能改变真实观察方向。

### 3. 三层空间深度
- Far：1500 星，距离约 52–94；
- Mid：650 星，距离约 22–44；
- Near：250 星，距离约 7.5–17。

相机投影使用约 74° vertical FOV。距离、大小、glow 都参与透视，因此近星视觉变化更明显、远星更稳定。

### 4. 微量实体位移视差
若设备有 `TYPE_LINEAR_ACCELERATION`，只用于很小的短时 camera translation cue，并强阻尼归零。

它不是 AR/6DoF 定位，也不累计绝对位置；只是让拿手机轻微移动时近星比远星产生更自然的物理视差。

### 5. 真实转向拖尾
快速旋转时，近/中景星根据上一帧到当前帧的真实 screen-space displacement 绘制非常短的低透明度 trail；不是“warp speed”特效，不会破坏 Lunaxy 安静的视觉语言。

### 6. 音频响应继续保留
原 Media3 PCM `AudioLevelProvider` 单向视觉桥继续保留：
- energy/beat 只轻微影响星光大小、亮度和封面 tint；
- 不参与音频数据/播放状态写回；
- 不需要麦克风/RECORD_AUDIO。

### 7. 性能与生命周期
- 只在播放详情页使用新 3D View；
- View 不 click/focus，不抢歌词手势和播放器按钮；
- 页面隐藏、App 后台时注销 rotation / linear-acceleration sensor；
- 快速转向/音频活跃时约 60fps；
- 静止时自动降向约 30fps，降低发热与耗电；
- 新 View 仍使用普通 Android hardware Canvas，避免 SurfaceView 独立 surface 改变 V49 UI 层级/触摸行为。

## 实际 Runtime Diff
`app/src/main` 相对 V49 只有：
1. `MainActivity.java`：播放页背景类型/实例从 `StarfieldView` 换为 `Starfield3DView`；
2. 新增 `ui/Starfield3DView.java`；
3. 新增纯 Java `ui/Starfield3DMath.java`。

旧 `StarfieldView.java` 保持 V49 字节级不变。

## 版本
- applicationId: `com.xingyu.music`
- versionCode: `500`
- versionName: `50.0.0`
- APK 分享产物：继续自动生成 `Lunaxy Music.apk`
