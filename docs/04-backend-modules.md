# 04 后端模块划分

根包：`com.aistudy`。模块化单体，按业务域分包。

## 1. 模块总览

| 模块 | 职责 | 主要实体 / 组件 | MVP |
|---|---|---|---|
| `common` | 通用基础设施：错误模型、分页、时间、ID 校验、JSON 工具、Web 配置 | `ApiException`、`ErrorCode`、`PageResult`、`GlobalExceptionHandler` | ✅ |
| `workspace` | 学习空间与 Subject【R1】 | `LearningSpace`、`Subject`、`SpaceService`、`SubjectService` | ✅ |
| `archive` | 归档 | `Archive`、`ArchiveService` | ✅ |
| `file` | 文件元数据、存储抽象、校验、PDF 渲染 | `StoredFile`、`StorageService`、`FileValidator`、`PdfRenderer`、`FileService` | ✅ |
| `question` | 题目、来源、知识点及其关联 | `Question`、`QuestionSource`、`KnowledgePoint`、`QuestionService`、`KnowledgePointService` | ✅ |
| `ai` | Provider 抽象与适配器、模型能力、Prompt、分析版本、确认流程 | `AiGateway`、`AiProvider`、`ModelRegistry`、`PromptRegistry`、`AiAnalysis`、`AnalysisService`、`AnalysisReviewService` | ✅ |
| `chat` | 会话、消息、作用域上下文组装、SSE | `ChatSession`、`ChatMessage`、`ChatService`、`ContextAssembler` | ✅ |
| `auth` | 登录鉴权 | — | ❌ 只保留包名占位和设计（03 §5.1） |

> KnowledgePoint 暂时放在 `question` 模块中（它只在题目上下文中使用）。当知识点管理和学习画像变复杂时，再拆成独立的 `knowledge` 模块。

## 2. 依赖规则

```
            chat ─────────┐
              │           ▼
              ├──────►   ai  ──► (ai.provider：不依赖任何业务模块)
              ▼           │
          question ◄──────┘
              │
      ┌───────┼────────┐
      ▼       ▼        ▼
   archive   file   workspace
      │       │        ▲
      └───────┴────────┘
             common（所有模块都可以依赖）
```

1. 上层依赖下层，**禁止反向依赖**：`workspace` 不依赖 `question`，`question` 不依赖 `ai`。
2. 跨模块访问**只能通过对方的 `*Service` 公共方法**，不能直接注入对方的 Repository 或修改对方的实体。
3. `ai.provider` 子包是纯技术层：只认识 `AiRequest`/`AiResponse`/`ModelCapabilities`，不认识 Question 等业务概念。
4. 下层需要感知上层事件时（例如删除文件前检查是否被题目引用），由下层定义接口、上层实现（例如 `FileReferenceChecker`，由 `question` 实现），避免循环依赖。
5. 规则用 ArchUnit 测试固化（可选；M3 以后模块变多时引入）。

## 3. 模块内部结构

每个模块采用扁平但分层清晰的结构，避免全局的 controller/service/repository 包：

```
com.aistudy.question
├─ QuestionController.java          # REST 边界，只做 DTO 转换和校验
├─ QuestionService.java             # 事务、业务规则（公共 API）
├─ Question.java                    # JPA 实体（包内可见的 setter）
├─ QuestionRepository.java
├─ QuestionSource.java / QuestionSourceRepository.java
├─ knowledge/
│  ├─ KnowledgePoint.java / KnowledgePointRepository.java
│  ├─ KnowledgePointService.java
│  └─ KnowledgePointController.java
└─ dto/                             # 请求/响应 DTO（record）
```

`ai` 模块较大，按子包拆分：

```
com.aistudy.ai
├─ provider/            # 技术层：AiProvider SPI、AiRequest/AiResponse、ModelCapabilities
│  ├─ openai/           # OpenAiResponsesProvider（只在这里使用 openai-java）
│  ├─ anthropic/        # AnthropicMessagesProvider（只在这里使用 anthropic-java）
│  └─ fake/             # FakeAiProvider（测试和离线开发用）
├─ gateway/             # AiGateway：路由、超时、重试、用量记录、能力校验
├─ model/               # ModelRegistry、AiProperties（配置绑定）、TaskType、能力匹配
├─ prompt/              # PromptRegistry、模板渲染、版本和哈希
├─ input/               # AiInputPreparer：来源 → 图片部件（依赖 file 模块）
├─ analysis/            # AiAnalysis 实体、AnalysisService、AnalysisExecutor、结果校验、可信度计算
├─ review/              # AnalysisReviewService：确认、修正、驳回
└─ AiController / AnalysisController
```

## 4. 各模块要点

### 4.1 workspace

- `SpaceService`：增删改查、归档；`ai_profile` 必须是 `PromptRegistry` 中存在的配置（通过 `ai` 模块暴露的 `AiProfileCatalog` 接口校验。为避免 workspace 依赖 ai，这个接口由 workspace 定义、ai 实现）。
- `SubjectService`：增删改查、停用；`assertBelongsTo(subjectId, spaceId)` 供其他模块调用。

### 4.2 archive

- 创建、编辑、软删除（只能删除空归档：通过 `FileService.countByArchive` 判断）。

### 4.3 file

- 上传、校验、存储、元数据、按页渲染、缩略图。详见 07。
- 对外提供 `FileService.loadForAi(fileId, pageNo, ImageConstraints)`，供 `ai.input` 使用。

### 4.4 question

- 建题：校验来源文件属于同一空间、页码有效、来源数量不超过上限，Subject 属于同一空间。
- 可选「建题后立即分析」：Controller 编排——先调 `QuestionService.create`，再调 `AnalysisService.start`。`question` 模块本身不依赖 `ai`。
- `QuestionService.applyConfirmedAnalysis(...)`：供 `ai.review` 回写 `confirmed_analysis_id`、`error_type`，以及用户选择采用的识别文本。
- 知识点：`normalize(name)`、按名称匹配、创建和关联。

### 4.5 ai

- `AnalysisService.start(questionId, modelId, reason, extraContext)`：能力校验 → 新建 PENDING 版本（写入模型、Provider、Prompt 快照）→ 事务提交后派发给 `AnalysisExecutor`。
- `AnalysisExecutor`：准备输入 → 调用 `AiGateway` → 解析和校验 → 计算可信度等级 → 写入结果。
- `AnalysisReviewService`：`confirm`、`correct`、`reject`（01 §5）。
- 启动恢复：`ApplicationReadyEvent` 时把残留的 PENDING/RUNNING 记录标记为 FAILED。

### 4.6 chat

- `ContextAssembler`：按 `scope_type` 选择策略（MVP 只实现 `QuestionContextStrategy`）。
- `ChatService.send(...)`：保存用户消息 → 组装上下文 → `AiGateway.stream` → 通过 `SseEmitter` 推送 → 保存助手消息。
- 客户端断开时取消上游请求，把消息标记为 `CANCELLED` 并保存已生成的部分。

## 5. REST API 清单（MVP）

约定：前缀 `/api`；JSON；分页参数 `page`（从 0 开始）、`size`，响应为 `{items,page,size,total}`；错误统一为 `ProblemDetail`。

### 5.1 空间与 Subject

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/spaces` | 列表（`?includeArchived`） |
| POST | `/spaces` | 创建 `{name,description,color,aiProfile,aiInstructions}` |
| GET / PATCH | `/spaces/{spaceId}` | 查看 / 修改 |
| POST | `/spaces/{spaceId}/archive` · `/unarchive` | 归档 / 恢复空间 |
| GET / POST | `/spaces/{spaceId}/subjects` | Subject 列表 / 创建 |
| PATCH / DELETE | `/subjects/{id}` | 修改（含停用）/ 删除（没有引用时） |
| GET | `/ai/profiles` | 可选的 AI 分析配置（`math`/`cs408`/`generic`） |

### 5.2 归档与文件

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / POST | `/spaces/{spaceId}/archives` | |
| GET / PATCH / DELETE | `/archives/{id}` | |
| POST | `/spaces/{spaceId}/files` | multipart：`files[]`、`archiveId?`；返回每个文件的结果（部分失败时逐个报告） |
| GET | `/spaces/{spaceId}/files` | `?archiveId=` / `?unassigned=true` |
| GET / PATCH / DELETE | `/files/{id}` | 元数据 / 移动归档 / 软删除（被引用时返回 409） |
| GET | `/files/{id}/content` | 原件（inline） |
| GET | `/files/{id}/thumbnail` | 缩略图 |
| GET | `/files/{id}/pages/{pageNo}/image` | PDF 页渲染图（`?size=thumb|view`） |

### 5.3 题目与知识点

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/spaces/{spaceId}/questions` | `?subjectId&archiveId&knowledgePointId&errorType&reviewState(unanalyzed/unreviewed/confirmed)&keyword` |
| POST | `/spaces/{spaceId}/questions` | `{subjectId,archiveId,locateHint,sources:[{fileId,pageNo,role}],userAnswerText,officialAnswerText,userContext,analyze:{modelId}?}` |
| GET / PATCH / DELETE | `/questions/{id}` | 详情（包含来源、知识点、最新版本和确认版本的摘要） |
| PUT | `/questions/{id}/sources` | 整体替换来源 |
| POST | `/questions/{id}/knowledge-points` | `{knowledgePointId}` 或 `{name}`（不存在则创建） |
| DELETE | `/questions/{id}/knowledge-points/{kpId}` | |
| GET / POST | `/spaces/{spaceId}/knowledge-points` | `?subjectId&keyword` |
| PATCH / DELETE | `/knowledge-points/{id}` | 删除时一并删除关联 |

### 5.4 AI 分析与确认

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ai/models` | 可用模型及其能力（`?task=QUESTION_ANALYSIS` 只返回满足任务的模型），附带各任务的默认模型 |
| POST | `/questions/{id}/analyses` | 发起分析 `{modelId?,reason,extraContext?,useConfirmedRecognition?}` → 202 |
| GET | `/questions/{id}/analyses` | 版本列表（摘要） |
| GET | `/analyses/{id}` | 完整版本（轮询用） |
| POST | `/analyses/{id}/confirm` | `{adoptRecognition:{stemText,userAnswerText,officialAnswerText}?, knowledgePoints:{linkIds[],createNames[]}}` |
| POST | `/analyses/{id}/corrections` | 提交修正后的完整 result → 生成新的 `USER_EDIT` 版本（CONFIRMED） |
| POST | `/analyses/{id}/reject` | `{note}` |

### 5.5 对话

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/chat/sessions` | `{scopeType:"QUESTION",scopeRefId,modelId?}`（MVP 只接受 QUESTION） |
| GET | `/chat/sessions` | `?scopeType&scopeRefId` |
| GET | `/chat/sessions/{id}/messages` | |
| POST | `/chat/sessions/{id}/messages` | `{content,modelId?,attachOriginals:boolean}`，响应为 `text/event-stream`：`delta` / `done` / `error` 事件 |
| DELETE | `/chat/sessions/{id}` | 软删除 |

## 6. 错误码示例

| code | HTTP | 场景 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 参数校验失败 |
| `NOT_FOUND` | 404 | |
| `CROSS_SPACE_REFERENCE` | 400 | 引用了其他空间的 Subject、文件或归档 |
| `FILE_TYPE_NOT_ALLOWED` / `FILE_TOO_LARGE` / `PDF_ENCRYPTED` / `PDF_TOO_MANY_PAGES` | 400 / 413 | |
| `FILE_IN_USE` / `ARCHIVE_NOT_EMPTY` / `SUBJECT_IN_USE` | 409 | |
| `ANALYSIS_IN_PROGRESS` | 409 | 同一道题已有进行中的分析 |
| `MODEL_NOT_AVAILABLE` | 400 | 模型未配置或没有 Key |
| `MODEL_CAPABILITY_MISMATCH` | 400 | 所选模型不满足任务需要的能力 |
| `AI_PROVIDER_ERROR` / `AI_OUTPUT_INVALID` | 502 | 记录在分析版本中；同步接口才返回 |
| `TOO_MANY_TASKS` | 429 | 分析队列已满 |
