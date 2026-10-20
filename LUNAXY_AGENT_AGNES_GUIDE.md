# Lunaxy Agent 工具层与 Agnes 接入说明

> 状态：Agent Tool Layer v1  
> 目标：不重写 Lunaxy 播放器与推荐引擎，把现有能力暴露为 OpenAI-compatible Tool Calling 工具，供 Agnes 或其他兼容模型调用。

## 1. 设计原则

Lunaxy Agent 不是第二套播放器，也不是把所有业务逻辑交给大模型。

职责分工：

- **模型（Agnes）**：理解自然语言、规划步骤、选择工具、解释结果。
- **LunaxyAgentTools**：把模型的工具调用映射到 Lunaxy 真实能力。
- **现有 Lunaxy 业务层**：继续负责搜索、收藏、歌单、个性化、推荐和播放。
- **PlaybackService**：仍然是唯一播放器；Agent 通过 AgentPlaybackBridge 复用它。
- **PersonalizationStore / TasteProfileEngine / RecommendationEngine**：仍然是口味画像与推荐的事实源。

因此以后更换模型时，Lunaxy 的工具层基本不用改。

## 2. 已暴露工具

### 个性化与资料读取

- `lunaxy_get_listening_profile`  
  返回本地学习成熟度、近期行为，以及歌手的 overall / 180 天 / 30 天 / 7 天 / 当前 Session 偏好。
- `lunaxy_get_recently_played`
- `lunaxy_get_favorites`
- `lunaxy_list_playlists`
- `lunaxy_daily_recommendations`  
  直接复用现有每日推荐引擎。
- `lunaxy_private_radar`  
  直接复用现有私人电台/当前 Session 敏感推荐。

### 搜索与播放

- `lunaxy_search_music`  
  复用 Lunaxy 已有的网易、QQ、酷我、酷狗搜索，返回真实歌曲和短期 `song_ref`。
- `lunaxy_search_artist`
- `lunaxy_get_playback_state`
- `lunaxy_play_song`
- `lunaxy_play_queue`
- `lunaxy_playback_control`

工具返回的 `song_ref` 是 Agent 会话内临时引用。模型必须使用真实 `song_ref` 播放，不能自行编造歌曲 ID。这样可以避免模型修改平台 ID、来源和播放路由。

### 歌单写入

- `lunaxy_create_playlist`
- `lunaxy_add_to_playlist`

系统提示已经要求：只有用户明确提出写入请求时才调用这些工具。

### Web / 浏览器

- `lunaxy_web_search`  
  后台做轻量公开网页检索，把文本真正返回给模型。当前默认实现使用 DuckDuckGo HTML，仅用于发现和补充资料；网页结果不是“可播放事实”，最终歌曲仍要调用 `lunaxy_search_music` 验证。
- `lunaxy_fetch_web_page`  
  读取一个明确的公开 URL。拒绝 localhost、局域网、链路本地和 CGNAT 地址，降低 SSRF 风险。
- `lunaxy_open_browser_search`
- `lunaxy_open_url`

特别注意：`open_browser_search/open_url` 只是用 Android ACTION_VIEW 打开用户可见的外部浏览器，**不会把 Chrome/浏览器页面内容返回给模型**。模型需要读网页时必须调用 `web_search/fetch_web_page`。如果以后需要像 Operit 那样真正点击/输入/读取浏览器 DOM，应再接入 WebView Browser Agent、Accessibility 自动化或 MCP Browser，而不是假装 ACTION_VIEW 能读取页面。

## 3. Agnes / OpenAI-compatible 接入方式

在一个 Agent 会话里应复用同一个 `LunaxyAgentTools` 实例：

```java
LunaxyAgentTools tools = new LunaxyAgentTools(context);

String systemPrompt = LunaxyAgentPrompt.SYSTEM_PROMPT;
JSONArray toolSchemas = tools.openAiTools();
```

然后把：

1. `systemPrompt`
2. 对话历史
3. 当前用户消息
4. `toolSchemas`

一起放进模型请求。

请求结构遵循 OpenAI-compatible Chat Completions 习惯：

```json
{
  "model": "<Agnes 模型名>",
  "messages": [
    {
      "role": "system",
      "content": "<LunaxyAgentPrompt.SYSTEM_PROMPT>"
    },
    {
      "role": "user",
      "content": "根据我最近常听给我推荐几首 R&B"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "lunaxy_get_listening_profile",
        "description": "...",
        "parameters": {
          "type": "object",
          "properties": {}
        }
      }
    }
  ],
  "tool_choice": "auto"
}
```

API Base URL、API Key、模型名不要写死进 Tool Layer。它们应该保存在独立的 Agent Provider 设置中。这样 Agnes 免费策略、域名或模型变化时，不需要修改音乐能力代码。

## 4. Agent Loop

收到模型响应后：

1. 如果模型没有 `tool_calls`，把普通文本作为最终回复。
2. 如果有 `tool_calls`：
   - 读取 `function.name`
   - 读取 `function.arguments`
   - 在后台线程执行：

```java
String result = tools.execute(
    functionName,
    functionArgumentsJson
);
```

3. 把模型本次 assistant tool-call 消息原样加入历史。
4. 对每个工具结果追加一条 `role=tool` 消息，`tool_call_id` 必须对应原调用 ID，`content` 就是上面的 JSON 字符串。
5. 再请求模型。
6. 直到模型返回普通文本，或达到最大步骤数。

推荐每轮任务最多 6～8 个 Agent step，避免模型错误循环和免费 API 的请求频率被快速耗尽。

## 5. “按最近常听推荐 R&B”的真实流程

用户：

> 根据我最近常听的歌，给我推荐 5 首 R&B，别全是我已经听过的。

建议调用链：

```text
用户自然语言
   ↓
lunaxy_get_listening_profile
   ↓
Agnes 分析 180d / 30d / 7d / Session 口味
   ↓
必要时 lunaxy_web_search
   ↓
Agnes 形成 R&B 候选
   ↓
lunaxy_search_music（验证候选在 Lunaxy 多平台中存在）
   ↓
输出 5 首 + 推荐理由
   ↓
用户说“就放这几首”
   ↓
lunaxy_play_queue
```

这里 Agnes 负责“音乐理解”，Lunaxy 负责“事实和执行”。这样不会出现模型推荐了一首不存在/无法搜索的歌却直接声称能播放。

## 6. “每日推荐 / 私人雷达”如何真正落地

现有 Lunaxy 已经有本地行为库和推荐引擎，因此 Agent 不需要重新学习一套画像。

每日推荐：

```text
lunaxy_daily_recommendations
→ RecommendationEngine.daily(...)
→ TasteProfileEngine
→ PersonalizationStore
→ 收藏 / 歌单 / 最近播放 / 播完 / 跳过 / 重播 / 收听时长
→ ListenBrainz / 现有目录发现
→ 返回真实 Song + song_ref
```

私人雷达：

```text
lunaxy_private_radar
→ RecommendationEngine.privateRadio(...)
→ 当前 Session 权重更高
→ 返回可以直接继续播放的 song_ref
```

因此以后首页“每日推荐”“私人雷达”既可以保持原 UI，也可以让 Agent 用自然语言解释“为什么今天推荐这些”。

## 7. 本地正则与 Agent 的推荐路由

不要删除现有 `VoiceCommandParser`。

建议保持“双通道”：

```text
“下一首 / 暂停 / 继续”
        ↓
VoiceCommandParser 本地直接执行
        ↓
低延迟、离线、零 API

“最近我都在听什么？”
“按我最近口味推荐几首偏丝滑的 R&B”
“找一些和这首氛围接近但我没听过的”
        ↓
Agnes Agent
        ↓
Tool Calling
```

简单确定性命令不值得消耗一次 LLM 请求；复杂语义才交给 Agent。

## 8. 生命周期

`VoiceMusicSearch` 与播放桥会持有线程/Service 连接。Agent 页面或长期 Agent Service 销毁时调用：

```java
tools.close();
```

如果做常驻助手，可以把 `LunaxyAgentTools` 放在一个 App 级 Agent Service 中，一个会话始终复用同一实例，这样 `song_ref` 能在多步 Tool Calling 中保持有效。

## 9. 隐私边界

推荐只把**压缩后的口味画像**发送给大模型，不要把 Personalization SQLite 全库上传。

当前 `lunaxy_get_listening_profile` 返回的是：
- 学习成熟度
- 聚合后的歌手偏好
- 当前 Session 行为计数
- 推荐摘要

它不会返回完整原始事件日志，也不会返回音频 URL。

## 10. 下一阶段

当前 Tool Layer 已经足够接 OpenAI-compatible Agent。

真正接入 Agnes 时只需要再补一层很薄的：
- Provider 设置：Base URL / API Key / Model
- Chat Completions HTTP Client
- Tool-call Agent Loop
- Streaming UI
- Tool 执行卡片 / 用户确认 UI
- 失败重试与请求频率控制

工具层不需要因模型供应商变化而重写。
