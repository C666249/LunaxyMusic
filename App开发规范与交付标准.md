# App开发规范与交付标准

> 适用于 ChatGPT、Claude 或其他能够读取完整源码 ZIP 并进行 Android 项目开发的助手。  
> 本文档定义的是**跨账号、跨模型、跨项目长期有效的总开发规范**。  
> 它不替代单个项目自己的 `WORKFLOW.md`、`DELIVERY_RULE.txt`、`PROJECT_STATE.md`，而是作为所有 Android App 项目的上位规范。  
> 项目事实以**用户提供/确认的最新完整源码 ZIP**和其中的真实源码/构建配置为最高优先级来源。

---

## 1. 核心目标

每一轮开发都应尽可能在**单轮对话内完成高质量闭环**：

**读取基线 → 理解项目 → 设计 → 实现 → Review → 修复 → 回归 → 构建/静态验证 → Stable/Beta 身份审计 → 更新交接文档 → 打包完整源码 ZIP → 最终交付**

除非存在真正互斥且无法从现有行为推断的产品决策、可能不可逆破坏 Stable 身份/用户数据/签名升级链，或缺少必须由用户提供的外部凭据/素材/业务事实，否则应自行推进，不把可在同一轮完成的工作机械拆成多轮。

---

# 2. 基线与版本继承规则

## 2.1 唯一开发基线

每轮必须按以下优先级确定基线：
1. 用户本轮明确上传/指定的最新完整源码 ZIP；
2. 当前对话中助手上一轮刚交付且用户确认继续使用的最新完整源码 ZIP；
3. 跨账号/跨会话时，仅使用用户重新提供的最新完整源码 ZIP。

禁止凭聊天记忆重建、从旧 ZIP 开始修改、混用多个历史版本代码、仅根据 README 猜测当前实现。

## 2.2 最新完整 ZIP 自动成为下一轮基线

正式交付完成后，新 Stable/Beta 完整源码 ZIP 自动成为下一轮基线。项目长期状态必须尽量写进源码 ZIP，而不是只存在于聊天记录。

---

# 3. 项目内必须长期保留的记忆文件

每份正式交付 ZIP 根目录至少包含：
- `App开发规范与交付标准.md`
- `WORKFLOW.md`
- `DELIVERY_RULE.txt`
- `PROJECT_STATE.md`

根据项目需要还应包含：`BUILD.md`、`CHANGELOG.md`、`SELF_TEST.md`、`HISTORY_ARCHIVE.md`、最新 `RELEASE_NOTES_<VERSION>.md`、`dev-logs/` 或等价验证日志、`SHA256SUMS.txt`。

## 3.1 强制嵌入规则

每一份 Stable 和 Beta 完整源码 ZIP 根目录都必须包含当前最新版 `App开发规范与交付标准.md`。若规范更新，下一轮正式交付必须替换 ZIP 内旧版。

---

# 4. 各项目记忆文件职责

- `App开发规范与交付标准.md`：跨项目、跨模型、长期不轻易变化的总开发规范。
- `WORKFLOW.md`：项目特有长期开发方式、架构约束、关键交互原则、修改策略和 Review 重点。
- `DELIVERY_RULE.txt`：不可随意改变的交付合同，如 Stable/Beta identity、包名/显示名/图标、ZIP/APK 命名、构建要求、Stable 身份保护项。
- `PROJECT_STATE.md`：只记录当前真实版本、身份、架构、最近完成内容、已知问题、未完成事项、数据兼容状态和下一轮入口，不无限堆历史。

---

# 5. 新账号 / 新会话 / 新模型接手协议

开发前依次读取：本规范 → `WORKFLOW.md` → `DELIVERY_RULE.txt` → `PROJECT_STATE.md` → 最新 Release Notes → 最新验证日志/SELF_TEST → 根目录与 Android 构建配置 → 本轮相关源码。

正式修改代码前应汇报：固定工作流、最新基线、Stable/Beta applicationId 与版本、Stable 升级链/数据兼容要求、Android Studio 根目录、当前架构、本轮目标、文档与真实源码是否冲突。冲突时以真实源码为准并在本轮修正文档。ZIP 内资料足够时不要求用户重讲历史。

---

# 6. AI 平台记忆原则

源码 ZIP 内交接文档是长期项目规则的权威载体；平台 Memory、Project Memory、聊天历史只作辅助。平台长期记忆可以保存“单轮高完成度、Stable/Beta 双线、每轮完整源码 ZIP、Stable 身份与数据链优先、Android Studio 为正式构建路径、项目自带交接文档、滚动历史窗口”等长期偏好；动态版本和当轮实现仍必须落入 `PROJECT_STATE.md`。

---

# 7. 单轮高完成度开发规范

### Phase A — Baseline Audit
解压最新版源码；读交接；检查 Gradle/Manifest/package/目录；理解 UI/UX 与功能；确定边界和潜在回归点。

### Phase B — Product / Technical Design
在既有风格上设计；优先复用已有组件、状态和交互；避免无必要重构；明确迁移与兼容风险。

### Phase C — Implementation
完成本轮主要需求；必要时同步 UI、原生层、assets、资源、测试和文档；保持 Stable/Beta 功能逻辑同源。

### Phase D — First Review
代码 Review、编译层静态检查、空引用/符号/资源检查、重复监听/状态覆盖/异步竞态、Back/Drawer/Overlay/Keyboard/Scroll 层级检查。

### Phase E — Fix & Refactor
修复 Review 问题，清理死代码/临时补丁，在不扩大风险面的前提下改善可维护性。

### Phase F — Regression
重点检查目标功能、相邻旧功能、数据保存/恢复、重启、返回键、列表/滚动、动画结束后的真实状态、Stable/Beta 共存、升级兼容。

### Phase G — Delivery Audit
检查 Stable/Beta 身份、versionName/versionCode、applicationId、App 名称/Beta 图标、Manifest authority、FileProvider/Deep Link/数据目录/WebView 数据隔离、历史精简、交接文档和 ZIP 完整性。

---

# 8. Stable / Beta 双渠道交付合同

每轮结束必须同时交付 Stable 正式版完整源码 ZIP 与 Beta 测试版完整源码 ZIP，不是 patch/diff。两份 ZIP 解压后都必须是完整、独立、可直接用 Android Studio 打开的工程，不能互相依赖。

---

# 9. Stable 身份长期保护

最高优先级保护：Stable `applicationId`、签名身份、`versionCode` 递增链、本地数据兼容、数据库 schema/migration、SharedPreferences key、私有文件目录、WebView 数据、导入导出格式、Provider authority、历史数据可读性和覆盖安装能力。

除非用户明确授权并理解后果，禁止随意修改 Stable applicationId、重置 versionCode、为了重构换数据 key、无兼容迁移地清空/迁数据、更换签名导致不可覆盖安装、让 Beta 数据路径污染 Stable。

正式 keystore、密码、私钥、发布 token 不进入公开源码 ZIP；签名身份由用户安全保管。

---

# 10. Beta 独立性要求

Beta 必须可与 Stable 同机安装，至少具有独立 applicationId、app data/cache/database/SharedPreferences/WebView 数据、Provider authority/必要 permission，不污染 Stable 公共导出目录，显示名明确带 Beta，图标有明显 β。业务代码尽量同源，只允许身份、测试入口、诊断等必要差异。

---

# 11. Android Studio 正式构建标准

正式路径：**解压 → Android Studio → Open 含 `settings.gradle.kts` 的项目根目录 → Gradle Sync → 构建 APK**。

必查：`settings.gradle.kts`、根/module `build.gradle.kts`、`gradle.properties`、wrapper、`gradlew(.bat)`、Manifest、namespace/applicationId/version/min/target/compileSdk、JDK/AGP/Gradle 兼容、repositories/dependencies、资源/sourceSets、package 路径、R8/ProGuard。

只有实际完成 Android/Gradle 构建才能声称 Sync/assemble/APK 成功。若环境缺 SDK/JDK/依赖下载能力，只能写“完成静态/结构/脚本检查，但未完成真实 Android 构建验证”。

---

# 12. 质量保证清单

交付前尽可能完成：功能实现、UI/UX 打磨、两轮 Review、静态检查、自动测试、关键回归、真实 Android 构建（环境允许时）、XML/Manifest/Java/Kotlin/JS 语法检查、资源引用、Stable/Beta identity/version、Stable 数据升级链、assets 镜像一致性（如适用）、凭据泄漏、结构、历史清理、交接文档、ZIP 解压复检、SHA256。

---

# 13. 历史记录滚动窗口与 ZIP 体积控制

## 13.1 原则
源码 ZIP 只保存当前完整源码，不保存旧版完整源码、旧 ZIP、完整备份工程、旧 APK/AAB 或无价值临时产物。

## 13.2 Stable 历史窗口
默认只保留最近 10 个 Stable 正式版本的详细记录；超出窗口的长期有价值信息压缩进入 `HISTORY_ARCHIVE.md`。

## 13.3 Beta 历史窗口
默认只保留当前 Beta + 最近 2～3 个 Beta 版本详细记录；更早 Beta 过程信息压缩总结。Beta 是研发过程，Stable 才是长期产品历史主线。

## 13.4 历史归档
`HISTORY_ARCHIVE.md` 保留重大架构迁移、身份/数据库 schema/数据迁移链、关键兼容事实、重大功能里程碑、严重回归根因、仍影响当前代码理解的历史决策。不得因超过窗口删除仍影响兼容/升级的信息。

## 13.5 `PROJECT_STATE.md` 只写当前状态
版本历史移入 `CHANGELOG.md`/Release Notes/`HISTORY_ARCHIVE.md`。

## 13.6 构建缓存与临时文件清理
源码 ZIP 默认不得包含 `.gradle/`、`build/`、`app/build/`、`.idea/caches/`、`*.apk`、`*.aab`、`*.log`、`tmp/`、`temp/`、旧源码 ZIP/安装包；通常也不包含 `local.properties`。

## 13.7 Lunaxy Music 下一版起执行
从本规范确立后的 Lunaxy Music 正式开发开始：Stable 最近 10 个正式版本详记、Beta 当前+最近 2～3 个详记，更早信息进入 `HISTORY_ARCHIVE.md`；清理旧 ZIP/APK/cache/build/重复历史；两份最终 ZIP 根目录都嵌入最新版规范。

---

# 14. 凭据与敏感信息

交付前检查 API Key、Access Token、OAuth Secret、私钥、keystore、密码、真实 Cookie、内部凭据。运行确实依赖本地凭据时优先使用不打包真实值的 local config / `.env` example / Android Keystore / runtime 输入，不将真实密钥放入公开交付物。

---

# 15. 版本与命名规范

源码 ZIP：`<Project>-V<Version>-Stable.zip` / `<Project>-V<Version>-Beta.zip`。APK：Stable `<应用显示名> V<版本>.apk`；Beta `<应用显示名> V<版本> Beta<编号>.apk`。内部 debug/release 仍需可追踪 buildType。

---

# 16. 文档一致性原则

源码真实状态优先于历史文档。每轮交付前修复 README 版本滞后、PROJECT_STATE 与 Gradle 不一致、Stable/Beta 包名记录错、已完成仍写 TODO、废弃架构仍写当前、历史无限累积、规范副本过期。最终 ZIP 中交接文档必须描述**当前实际代码**。

---

# 17. 多项目组织原则

总规范与各 App 动态状态隔离。全局/Monkeys 保存通用开发规范；Nexa、Lunaxy 等各项目分别保存自己的最新源码、PROJECT_STATE、版本/架构/Bug 历史和规范副本，不把不同 App 动态状态混入同一项目记忆。

---

# 18. 项目级信息模板

```text
Project:
Current baseline:
Stable applicationId:
Beta applicationId:
Stable display name:
Beta display name:
Stable versionName:
Stable versionCode:
Beta versionName:
Beta versionCode:
Android Studio root:
JDK:
Gradle:
AGP:
compileSdk:
targetSdk:
minSdk:
Stable signing identity:
Data migration status:
Current architecture:
Current unfinished work:
Known regressions:
Last validated build:
```

---

# 19. Definition of Done

尽可能满足：需求实现；关键功能无已知回归；Stable identity 未误改；Beta 可共存；version 正确；Android Studio 结构正确；可执行测试已执行；无明显未处理编译错误；交接文档更新；历史窗口检查/清理；Stable/Beta 两份完整 ZIP 生成；两包根目录包含最新版规范；ZIP 解压可直接作为下一轮基线；真实构建状态和限制已明确。

---

# 20. 给新助手的直接指令

收到本文件和最新源码 ZIP 后：先读规范/项目交接/真实构建配置，再汇报固定工作流、基线、Stable/Beta identity/version、Android Studio 根、当前状态和目标；冲突以真实源码为准；随后同一轮尽可能完成设计、实现、多轮 Review、修复、回归和交付；最终交付两份完整源码 ZIP；执行历史滚动窗口；更新交接文档；确保两包根目录均含最新版本规范。

---

## 最终原则

**聊天可以丢，账号可以换，模型可以换；只要最新版完整源码 ZIP 仍在，项目就应该能够无损接手并继续开发。**

**AI 平台 Memory 是辅助；源码 ZIP 内的项目文档才是长期项目记忆的权威来源。**

**项目历史应保留价值，而不是保留体积：当前源码完整保留，近期历史高分辨率保留，远期历史压缩归档。**
