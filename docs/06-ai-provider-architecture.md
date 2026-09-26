# 06 AI Provider 多模型架构设计

## 1. 目标与约束

| 目标 | 说明 |
|---|---|
| 多模型 | 通过 API 接入 GPT、Claude 等多家模型；新增厂商只需增加适配器和配置，不改业务代码 |
| 能力感知【R6】 | 系统知道每个模型能做什么（能否看图、是否支持结构化输出和流式输出、图片数量和尺寸上限……），据此过滤可选模型、预处理输入并选择输出策略 |
| 可追溯【R2】 | 每个结果记录 Provider、模型、Prompt 版本、Schema 版本和输入快照 |
| 可信度【R3】 | 模型自报的可信度与系统信号结合，得出可信度等级，驱动用户确认流程 |
| 安全 | Key 只存在于后端环境变量；前端只能看到模型的 `id` 和展示信息 |
| 可测试 | 业务层只依赖 `AiGateway`；测试时使用 `FakeAiProvider` |

**不做**：Agent 框架、工具调用编排、RAG、多模型投票。

## 2. 分层

```
┌───────────────────────── 业务层 ─────────────────────────┐
│ AnalysisService / AnalysisExecutor      ChatService       │
│        │ 组装 Prompt（PromptRegistry）  │ ContextAssembler │
│        │ 准备输入（AiInputPreparer）    │                  │
└────────┼────────────────────────────────┼─────────────────┘
         ▼                                ▼
┌─────────────────────────── AiGateway ─────────────────────┐
│ 解析 modelId → ResolvedModel（Provider + 模型名 + 能力）    │
│ 能力校验 · 超时 · 重试 · 用量与耗时记录 · 错误归一化         │
└───────────────────────────┬───────────────────────────────┘
                            ▼  AiProvider SPI（按 provider type 分派）
   ┌───────────────────┬────────────────────┬────────────────────────┐
   │ openai-responses  │ anthropic-messages │ fake（测试）            │  ← MVP
   ├───────────────────┼────────────────────┼────────────────────────┤
   │ openai-chat-compatible（DeepSeek/通义/Kimi/Ollama…） │ gemini │  ← 扩展
   └───────────────────┴────────────────────┴────────────────────────┘
```

## 3. 核心抽象（ai.provider 包，与业务无关）

```java
public interface AiProvider {
    String type();                                        // "openai-responses"
    AiResponse generate(ResolvedModel model, AiRequest request);
    StreamHandle stream(ResolvedModel model, AiRequest request, AiStreamListener listener);
}

public record AiRequest(
    String systemPrompt,
    List<AiMessage> messages,
    OutputSpec output,             // TextOutput | JsonOutput(schemaName, schemaJson)
    Integer maxOutputTokens,
    Double temperature,            // 模型不支持时由网关丢弃（见能力 supportsTemperature）
    Duration timeout) {}

public record AiMessage(Role role, List<ContentPart> parts) {}   // Role: USER / ASSISTANT

public sealed interface ContentPart permits TextPart, ImagePart, DocumentPart {}
public record TextPart(String text) {}
public record ImagePart(byte[] data, String mimeType) {}
public record DocumentPart(byte[] data, String mimeType) {}      // 预留：原生 PDF 输入，MVP 不使用

public record AiResponse(
    String text, FinishReason finishReason,   // STOP / LENGTH / CONTENT_FILTER / OTHER
    Usage usage, String providerModel,        // 厂商返回的实际模型标识
    String providerRequestId, long latencyMs) {}

public interface AiStreamListener {
    void onDelta(String text);
    void onComplete(AiResponse finalResponse);
    void onError(AiProviderException e);
}

public interface StreamHandle { void cancel(); }
```

统一异常 `AiProviderException(kind, retryable, httpStatus, message)`，其中 `kind` 取值：`AUTH`、`RATE_LIMIT`、`TIMEOUT`、`BAD_REQUEST`、`CONTENT_FILTER`、`SERVER`、`NETWORK`、`UNKNOWN`。

每个适配器负责把统一模型映射成厂商协议：

| 统一概念 | OpenAI Responses | Anthropic Messages |
|---|---|---|
| systemPrompt | `instructions` | `system` |
| TextPart / ImagePart | `input_text` / `input_image`（data URL） | `text` / `image`（base64 source） |
| JsonOutput | `text.format = json_schema (strict)` | 原生结构化输出；不支持时用强制工具调用（`tool_choice` 指定工具，工具的 `input_schema` = schema） |
| 流式 | SSE 事件 `response.output_text.delta` | SSE 事件 `content_block_delta` |
| 用量 | `usage.input_tokens / output_tokens` | `usage.input_tokens / output_tokens` |

## 4. 模型能力（Model Capability）【评审 R6】

### 4.1 能力模型

```java
public record ModelCapabilities(
    Set<InputModality> inputModalities,     // TEXT, IMAGE, PDF
    StructuredOutputMode structuredOutput,  // JSON_SCHEMA > TOOL_CALL > JSON_MODE > PROMPT_ONLY
    boolean streaming,
    boolean supportsTemperature,            // 部分推理模型不接受 temperature
    ImageLimits image,                      // 不支持图片时为 null
    PdfLimits pdf,                          // 不支持原生 PDF 时为 null（MVP 不使用）
    int contextWindowTokens,
    int maxOutputTokens) {}

public record ImageLimits(
    int maxImagesPerRequest,
    long maxBytesPerImage,
    int maxLongEdgePx,                      // 超过就缩小；模型内部也会缩放，提前缩小可以省带宽
    Set<String> mimeTypes) {}               // 例如 image/jpeg, image/png, image/webp

public record PdfLimits(int maxPages, long maxBytes) {}
```

### 4.2 能力从哪里来

- **配置即事实**：能力在 `application.yml` 中声明，不在运行时探测（厂商 API 没有统一可靠的能力查询接口）。
- **分两层**：`provider-types.<type>.default-capabilities` 给出协议级默认值，`models[].capabilities` 按模型覆盖。
- 启动时校验配置（例如声明了 IMAGE 就必须有 `image` 限制），不合法时拒绝启动。
- 每次分析都把用到的能力快照写进 `ai_analysis.input_snapshot.capabilities`，方便以后追溯「当时为什么这样预处理」。

### 4.3 任务需求（TaskType）

```java
enum TaskType { QUESTION_ANALYSIS, CHAT, JSON_REPAIR }
```

| 任务 | 硬性要求 | 偏好 | 按请求动态检查 |
|---|---|---|---|
| `QUESTION_ANALYSIS` | IMAGE 输入；`structuredOutput ≠ PROMPT_ONLY`；上下文窗口 ≥ 32k | JSON_SCHEMA | 来源图片数 ≤ `maxImagesPerRequest` |
| `CHAT` | TEXT 输入 | streaming（不支持时降级为一次性返回） | 「附带原图」时需要 IMAGE 能力，图片数不超过上限 |
| `JSON_REPAIR` | TEXT 输入；`structuredOutput ≠ PROMPT_ONLY` | 优先使用与原分析相同的模型 | — |

### 4.4 能力的用途

| 用途 | 位置 | 行为 |
|---|---|---|
| 过滤可选模型 | `GET /api/ai/models?task=` → 前端下拉框 | 只列出满足硬性要求的模型；其他模型灰显并说明原因 |
| 提前拒绝 | `AnalysisService.start` / `AiGateway` | 不满足时返回 `MODEL_CAPABILITY_MISMATCH`，给出具体原因（如「该模型不支持图片输入」「来源 7 张，超过该模型上限 5 张」） |
| 输入预处理 | `AiInputPreparer` | 按 `maxLongEdgePx`、`maxBytesPerImage` 和 `mimeTypes` 缩放、转码；PDF 页按限制渲染（07 §5） |
| 输出策略 | 各适配器 | 按 `structuredOutput` 选择：原生 schema、强制工具调用、JSON 模式或只靠 Prompt |
| 参数裁剪 | `AiGateway` | 模型不支持 `temperature` 时丢弃这个参数；`maxOutputTokens` 取请求值与能力上限中较小的一个 |

## 5. 结构化输出：`question-analysis/v1`

### 5.1 Schema

Schema 文件：`resources/schemas/question-analysis.v1.json`（兼容 OpenAI strict 模式：所有字段都列为 required，可空字段用 `["string","null"]`，并设置 `additionalProperties: false`）。

```json
{
  "recognition": {
    "questionText": "题干（Markdown，公式用 $...$ / $$...$$）或 null",
    "userWork": "我的解答过程或 null（图片中没有时）",
    "officialSolution": "标准答案或 null",
    "annotations": "批注/老师批改或 null",
    "unreadable": [
      { "sourceIndex": 2, "location": "第 3 行右侧", "description": "积分上限无法辨认", "impact": "HIGH" }
    ]
  },
  "knowledgePoints": ["洛必达法则", "等价无穷小替换"],
  "errorAnalysis": "错因分析",
  "correctApproach": "正确解题思路",
  "relatedKnowledge": [ { "concept": "泰勒展开", "note": "与等价无穷小的适用条件区别" } ],
  "errorType": "CONCEPT",
  "difficulty": "MEDIUM",
  "confidence": 0.72,
  "uncertainties": ["无法确定第二步是笔误还是概念错误"],
  "reviewSuggestions": ["重做同类题：……"]
}
```

| 字段 | 规则 |
|---|---|
| `knowledgePoints` | 1–5 个，每个不超过 30 字；去掉首尾空白并去重 |
| `relatedKnowledge` | 0–5 个 |
| `errorType` | 取值必须属于当前 `ai_profile` 声明的集合。通用集合：`CONCEPT` 概念理解、`METHOD` 方法选择、`CALCULATION` 计算/推导、`CONDITION` 审题/条件、`MEMORY` 记忆、`CARELESS` 粗心、`INCOMPLETE` 未完成、`NO_ERROR` 未发现错误、`UNDETERMINED` 无法判断 |
| `difficulty` | `EASY` / `MEDIUM` / `HARD` / null |
| `confidence` | 0–1 |
| `uncertainties` | 0–10 条；`reviewSuggestions` 0–3 条 |
| 文本长度 | 每段不超过 8000 字 |

### 5.2 校验流程（「永远不盲信模型输出」）

```
原始文本
  → 提取 JSON（PROMPT_ONLY / JSON_MODE 模式下取第一个完整的 JSON 对象）
  → Jackson 反序列化为 QuestionAnalysisResultV1（Java record，忽略未知字段并记日志）
  → Bean Validation（@NotBlank、@Size、@DecimalMin/Max……）
  → 业务规则（errorType 属于 profile 的集合；sourceIndex 在有效范围内；知识点清洗）
  → 软检查（不导致失败，只作为可信度信号）：LaTeX 定界符不配对、finishReason=LENGTH
  ├─ 通过 → SUCCEEDED
  └─ 不通过 → JSON_REPAIR 重试 1 次（纯文本：原输出 + 错误列表，要求「只修格式，不改内容」）
              ├─ 通过 → SUCCEEDED，并记录信号 repaired=true
              └─ 不通过 → FAILED(AI_OUTPUT_INVALID)，保存 raw_output
```

- 用单元测试保证 Schema 文件与 Java record 一致：示例 JSON 能通过双方的校验，缺少必填字段时双方都判为失败。
- Schema 升级（v2）时新增文件和 record，旧版本的结果继续按 `schema_version` 解析显示。

## 6. Prompt 管理

### 6.1 目录结构

```
resources/prompts/
├─ _common/
│  ├─ question-analysis.v1.md     # 通用规则：诚实、标注不确定、区分角色、输出格式、LaTeX 约定
│  └─ chat.v1.md
├─ generic/profile.yml
├─ math/
│  ├─ profile.yml                 # 名称、各任务当前版本、错误类型集合与中文标签
│  └─ question-analysis.v1.md     # 数学侧重：概念、方法、推导、计算、条件解读
└─ cs408/
   ├─ profile.yml
   └─ question-analysis.v1.md     # 408 侧重：概念辨析、算法、数据结构、OS、组成原理、网络、复杂度
```

```yaml
# math/profile.yml
name: 数学
tasks:
  question-analysis: v1
  chat: v1
errorTypes: [CONCEPT, METHOD, CALCULATION, CONDITION, CARELESS, INCOMPLETE, NO_ERROR, UNDETERMINED]
```

新增学科配置只需新建一个目录，**不需要改代码**；学科差异不写成代码里的分支（对应 AGENTS.md §10）。

### 6.2 组装顺序（QUESTION_ANALYSIS）

1. **system**：`_common` 模板 + profile 模板 + 空间 `ai_instructions` + Subject 名称与 `ai_instructions`
2. **system 附录**：空间内已有知识点的名称（设了 Subject 时优先列出同 Subject 的；最多 200 个），并要求「优先复用这些名称」
3. **user**：按 `sort_order` 交替放置标签和图片：
   `【来源 1｜角色：题目】` + 图片 → `【来源 2｜角色：我的解答】` + 图片 → ……
   然后是位置提示、用户输入的文本字段和补充说明；如果选择「以我确认的识别文本为准」，再附上已确认的题干和解答，并注明「以此为准，图片仅作参考」。

`_common` 模板中的关键规则（节选）：

- 严格区分题目、用户解答、标准答案、批注和老师批改，不要把所有笔迹都当成题目内容。
- 看不清的内容写进 `unreadable`，**不得猜测补全**，尤其是公式。
- 图片中找不到用户解答时，`userWork` 为 null，`errorType` 为 `UNDETERMINED`，并说明原因。
- 如果题目明显和位置提示不符，或一页有多道题但无法确定是哪一道，要在 `uncertainties` 中说明。

### 6.3 版本规则

- `prompt_key = {profile}/{task}`，`prompt_version` 取自 `profile.yml`；`prompt_hash` = 所用模板文件内容（变量替换前）的 SHA-256。
- 修改模板内容就必须升级版本号（新建 `*.v2.md`，旧文件保留），便于对比和回溯。
- 题目的确认版本所用的 `prompt_version` 落后于当前版本时，详情页提示「有新版分析 Prompt，可以重新分析」（重新分析的原因记为 `PROMPT_CHANGE`）。

## 7. 可信度模型【评审 R3】

模型自报的 `confidence` **没有经过校准**，不能单独作为依据。系统把它与客观信号结合，得出 `confidence_level`：

| 信号 | 来源 | 影响 |
|---|---|---|
| `modelConfidence` | 输出中的 `confidence` | < 0.5 → LOW；0.5–0.8 → 最高 MEDIUM |
| `unreadableHigh` | `unreadable` 中存在 `impact=HIGH` | → LOW |
| `unreadableAny` | `unreadable` 不为空 | 最高 MEDIUM |
| `missingQuestion` | `questionText` 为 null | → LOW |
| `missingUserWork` | 来源中有 USER_WORK/MIXED，但 `userWork` 为 null | 最高 MEDIUM |
| `undetermined` | `errorType = UNDETERMINED` | → LOW |
| `repaired` | 经过 JSON 修复重试 | 最高 MEDIUM |
| `truncated` | `finishReason = LENGTH` | → LOW |
| `latexUnbalanced` | 软检查不通过 | 最高 MEDIUM |
| `hasUncertainties` | `uncertainties` 不为空 | 最高 MEDIUM |

- 计算规则：从 HIGH 开始，逐条应用信号，**取最低**。阈值可以在 `app.ai.confidence.*` 中配置。
- 命中的信号保存在 `ai_analysis.confidence_signals`（JSON），界面据此给出具体原因（例如「低可信度：来源 2 有无法辨认的内容」）。
- 可信度只影响**展示和确认要求**（01 §5.2 第 3 条），不会自动确认或驳回任何结果。
- `USER_EDIT` 版本不计算可信度（`confidence_level` 为 NULL，界面显示「用户修正」）。

## 8. 可追溯字段【评审 R2】

每个 `ai_analysis`（以及助手的 `chat_message`）都记录：

| 字段 | 示例 |
|---|---|
| `provider_id` / `provider_type` | `anthropic` / `anthropic-messages` |
| `model_id` / `model_name` / `model_version` | `claude-main` / `claude-sonnet-5` / 响应中返回的实际模型标识 |
| `prompt_key` / `prompt_version` / `prompt_hash` | `math/question-analysis` / `v1` / `9f2c…` |
| `schema_version` | `question-analysis/v1` |
| `input_snapshot` | `{sources:[{index,fileId,page,role,sha256,sentBytes,sentSize}], textFields:{…}, spaceInstructions:true, subjectId:3, kpCandidates:42, capabilities:{…}, preprocess:{maxLongEdgePx:…}}` |
| `input_tokens` / `output_tokens` / `latency_ms` | |
| `trigger_reason` / `parent_id` | `MODEL_CHANGE` / — |

有了这些字段，就可以回答「这个结论是哪个模型、在什么输入和哪个 Prompt 下得出的」，也可以比较同一道题在不同模型、不同 Prompt 下的结果。

## 9. 适配器实现选型

| 方案 | 结论 | 理由 |
|---|---|---|
| **官方 Java SDK（openai-java、anthropic-java），封装在各自的适配器包内** | ✅ 采用 | 鉴权、重试、SSE 解析和类型都是现成的；厂商的新特性（Responses API、结构化输出）跟进最快；SDK 类型不会泄漏到适配器包之外 |
| Spring AI | ❌ | 又多一层抽象，Responses API、结构化输出、图片限制这些细节常常要绕过它；版本迭代快；和「不引入框架」的原则冲突 |
| 手写 `RestClient` | 备选 | 适合 OpenAI 兼容类厂商，或者 SDK 不满足需求的时候 |

OpenAI 兼容类厂商（DeepSeek、通义、Kimi、智谱、Ollama）大多只提供 Chat Completions 协议，将来用单独的 `openai-chat-compatible` 适配器（可配置 `base-url`）接入，不和 Responses 适配器混在一起。

## 10. 配置示例

```yaml
app:
  ai:
    providers:
      openai:
        type: openai-responses
        base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
        api-key: ${OPENAI_API_KEY:}          # 为空时该 Provider 不可用，但不影响启动
        timeout: 120s
      anthropic:
        type: anthropic-messages
        base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}
        api-key: ${ANTHROPIC_API_KEY:}
        timeout: 120s
    models:
      - id: gpt-main
        provider: openai
        model: ${OPENAI_MODEL:}               # 填写你账号可用的多模态模型名
        label: GPT（主力）
        capabilities:
          input: [TEXT, IMAGE, PDF]
          structured-output: JSON_SCHEMA
          streaming: true
          supports-temperature: false
          image: { max-images: 10, max-bytes: 20MB, max-long-edge-px: 2048, mime-types: [image/jpeg, image/png, image/webp] }
          context-window-tokens: 200000
          max-output-tokens: 16000
      - id: claude-main
        provider: anthropic
        model: ${ANTHROPIC_MODEL:claude-sonnet-5}
        label: Claude Sonnet
        capabilities:
          input: [TEXT, IMAGE, PDF]
          structured-output: JSON_SCHEMA
          streaming: true
          supports-temperature: true
          image: { max-images: 20, max-bytes: 5MB, max-long-edge-px: 1568, mime-types: [image/jpeg, image/png, image/webp] }
          context-window-tokens: 200000
          max-output-tokens: 16000
    defaults:
      QUESTION_ANALYSIS: ${AI_DEFAULT_ANALYSIS_MODEL:claude-main}
      CHAT: ${AI_DEFAULT_CHAT_MODEL:claude-main}
    confidence:
      low-below: 0.5
      high-at-least: 0.8
    executor: { core: 2, max: 4, queue: 20 }
    retry: { max-attempts: 3, initial-backoff: 1s, multiplier: 3 }
```

> 以上能力数值只是**示意**。实施时需要按各模型的官方文档核对后再填写，这是 M4 的一项任务。

`GET /api/ai/models` 只返回 `id`、`label`、`provider`、`available`（是否配置了 Key）和能力摘要，**永远不返回** `base-url` 和 Key。

## 11. 对话上下文组装（ContextAssembler）

| 作用域 | 上下文内容 | MVP |
|---|---|---|
| `QUESTION` | 题目字段（已确认的；没有时用最新识别结果并注明「未经用户确认」）+ **确认版本**分析（没有时用最新成功版本并注明「AI 草稿」）+ 已关联的知识点 + Subject；「附带原图」时在本轮用户消息中加入来源图片 | ✅ |
| `ARCHIVE` | 归档元数据 + 文件列表 + 该归档下题目的摘要（有上限） | ❌ |
| `SPACE` | 空间和 Subject 信息 + 按需检索的题目摘要 | ❌ |
| `GLOBAL` | 空间列表，不带具体数据 | ❌ |

- 历史：最近 20 条消息，并控制在上下文窗口的 50% 以内，超出时从最早的开始丢弃。
- 原图只在勾选的那一轮发送，不在后续轮次的历史中重复发送（历史中只保留文字说明「[此轮附带了原图]」）。
- **绝不**把其他题目或整个空间的数据放进 QUESTION 作用域的请求（AGENTS.md §2.3）。
- 实际使用的上下文摘要写入 `chat_message.context_snapshot`。

## 12. 超时、重试与降级

| 情况 | 策略 |
|---|---|
| `RATE_LIMIT` / `SERVER` / `NETWORK` / `TIMEOUT` | 重试最多 2 次，指数退避（1s、3s），遵守 `Retry-After` |
| `AUTH` / `BAD_REQUEST` / `CONTENT_FILTER` | 不重试，直接失败并给出明确原因 |
| 流式输出已经开始 | 不重试；保存已生成的部分并标记为 FAILED |
| 所选模型不可用 | 不自动切换到其他模型（避免结果来源不透明），由用户手动选择 |

## 13. 测试策略

| 层 | 方法 |
|---|---|
| 业务层 | `FakeAiProvider`（type=`fake`）：按场景返回固定的 JSON（正常、低可信度、非法 JSON、截断、超时），测试结果完全确定 |
| 结果校验 | 大量非法输出样例：缺字段、类型错误、枚举越界、超长、多余文本包着 JSON |
| 可信度计算 | 按信号组合逐条测试 |
| 适配器 | WireMock 模拟厂商的 HTTP 接口：校验请求映射（图片编码、schema、system）和响应解析（普通响应、流式响应、错误响应） |
| 能力匹配 | 给定能力和任务，断言可选模型列表和拒绝原因 |
| 真实调用 | 带 `@Tag("live")`，默认不运行；需要手动提供 Key 执行冒烟测试 |

## 14. 新增一个 Provider 的步骤

1. 实现 `AiProvider`（新建包 `ai.provider.xxx`）并注册为 Spring Bean，`type()` 返回新的类型名。
2. 在 `provider-types` 中给出默认能力。
3. 在配置中添加 provider 和 models 条目，在 `.env.example` 中添加 Key 变量。
4. 编写 WireMock 适配器测试。

业务代码、数据库和前端都不需要改动（前端的模型列表来自接口）。
