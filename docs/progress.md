# 开发进度

> 每次开发会话**开始前**先读本文件，**结束前**必须更新（见 `AGENTS.md` §20）。
> 会话记录按时间倒序排列，最新的在最前面。

## 当前状态

| 项目 | 内容 |
|---|---|
| 当前阶段 | M0 已完成，下一步 M1 |
| 当前里程碑 | M1 学习空间 + Subject（未开始） |
| 最近更新 | 2026-09-27 |
| 阻塞项 | 无 |

## 里程碑状态

状态取值：`未开始` / `进行中` / `已完成` / `已暂停`。只有满足 `docs/08-mvp-roadmap.md` 中的验收标准且测试通过，才能标为「已完成」。

| 里程碑 | 主题 | 状态 | 完成日期 | 备注 |
|---|---|---|---|---|
| 设计 | 产品与架构设计文档 | 已完成 | 2026-09-26 | `docs/01`–`08`、`AGENTS.md` |
| M0 | 项目骨架 | 已完成 | 2026-09-27 | 已在本机 MySQL 上完成端到端验证；`docker compose` 启动方式和 Testcontainers 测试因没有 Docker 未验证（见已知问题 1） |
| M1 | 学习空间 + Subject | 未开始 | | |
| M2 | 归档 + 文件上传 | 未开始 | | |
| M3 | 题目管理 | 未开始 | | |
| M4 | AI 分析（单个 Provider） | 未开始 | | |
| M5 | 确认流程 + 可信度 + 知识点 + 多模型 | 未开始 | | |
| M6 | 题目级对话 | 未开始 | | |
| M7 | 打磨与交付 | 未开始 | | |

## 已知问题与技术债

| # | 描述 | 发现日期 | 状态 |
|---|---|---|---|
| 1 | 本机没有 Docker：Testcontainers 集成测试（`ApplicationIntegrationTest`）被跳过，`docker compose up -d mysql` 也没有实际运行过。Flyway 和数据库连接已于 2026-09-27 在本机 MySQL 上手动验证 | 2026-09-26 | 部分解决 |
| 2 | 数据库不可用时后端无法启动（Flyway 在启动时连接数据库），所以健康检查的 `DEGRADED` 只会在运行中途数据库断开时出现 | 2026-09-26 | 接受（符合预期） |
| 3 | 全量引入 Element Plus，前端主 chunk 约 1 MB（构建时有警告） | 2026-09-26 | 推迟到 M7 |
| 4 | `.claude/settings.local.json` 已被 Git 跟踪，本地权限配置的变更会出现在 diff 中 | 2026-09-26 | 已解决（用户在 66a4765 中移出版本库，并加入 `.gitignore`） |
| 5 | 本机数据库是 MySQL 9.4，设计和 `docker-compose.yml` 使用的是 8.4。当前的 Flyway（随 Spring Boot 3.5.6）启动时会警告「MySQL 9.4 未经测试」，目前运行正常。开发时依赖 9.x 独有特性的写法可能在 8.4 上无法运行 | 2026-09-27 | 观察中 |
| 6 | 本机 `aistudy` 库的默认排序规则是 `utf8mb4_unicode_ci`，设计是 `utf8mb4_0900_ai_ci`。M1 的迁移脚本会在建表时显式指定字符集和排序规则，因此不影响使用 | 2026-09-27 | 观察中 |

---

## 会话记录

<!--
新会话请复制下面的模板，粘贴到本行下方（最新的在最前）：

### YYYY-MM-DD · 简短标题

- **里程碑**：Mx
- **已完成**：
  - …
- **未完成**：
  - …（包括失败的测试、已知问题）
- **下一步建议**：
  1. …
- **相关决策 / 变更**：D-xxx；CHANGELOG 是否更新
-->

### 2026-09-27 · M0 真实 MySQL 验证并提交

- **里程碑**：M0 → 已完成
- **已完成**：
  - 修正 `.gitignore`：删除末尾重复的规则（其中的 `.env.*` 又把 `.env.example` 忽略了）；`.claude/` 保留。用 `git check-ignore` 确认 `.env`、`.env.*`、`.claude/`、`.idea/`、`*.iml`、`.vscode/*`、`target/`、`dist/`、`node_modules/` 都会被忽略，`.env.example` 不会
  - 用户创建了 `aistudy` 库和 `.env`；在本机 MySQL 9.4 上运行 `./mvnw spring-boot:run`：Flyway 创建 `flyway_schema_history`（0 个迁移），JPA 校验通过，2.6 秒启动完成
  - `GET /api/health` → 200 `{"status":"UP","database":"UP"}`；未知路径 → 404 ProblemDetail，`code=NOT_FOUND`；后端只监听 `127.0.0.1:8080`
  - 前端 `vite` 开发服务器只监听 `127.0.0.1:5173`；页面返回 200；`/api/health` 经代理返回 UP
  - 提交 M0：`Complete M0 project initialization`
- **未完成**：
  - 没有 Docker，`docker compose` 启动 MySQL 和 Testcontainers 测试仍未验证（已知问题 1）
  - 首页只用 curl 验证了页面和接口，没有在浏览器中查看渲染效果
  - 本机 MySQL 版本（9.4）与设计（8.4）不一致（已知问题 5）
- **下一步建议**：
  1. 在浏览器中打开 http://127.0.0.1:5173 ，确认布局和「系统状态」卡片显示正常
  2. 开始 M1：学习空间 + Subject（`V1__workspace.sql`、workspace 模块的 CRUD、前端空间切换器和空间管理页）
  3. 可选：安装 Docker Desktop，补跑集成测试并验证 `docker compose`
- **相关决策 / 变更**：无新增决策；CHANGELOG 无新变化（M0 条目已在上一次会话中写入）

### 2026-09-26 · M0 项目骨架

- **里程碑**：M0
- **已完成**：
  - 发现目录已是 Git 仓库（有提交和远程仓库）；恢复并合并了原有的 `.gitignore` 规则，新增 `.gitattributes`、`.env.example`、`docker-compose.yml`（MySQL 8.4，只绑定 127.0.0.1）
  - 后端 `backend/`：Spring Boot 3.5.6 + Java 21、自带 Maven Wrapper、各业务模块的空包（含 `package-info`）、统一错误模型（`ErrorCode`、`ApiException`、`GlobalExceptionHandler` 返回带 `code`/`errors` 的 ProblemDetail）、`PageResult`、`GET /api/health`、Flyway 目录、`server.address=127.0.0.1`
  - 后端测试：错误处理 6 项、健康检查 2 项、ErrorCode 2 项、PageResult 1 项，全部通过；Testcontainers 集成测试 2 项因没有 Docker 被跳过（`./mvnw verify` → BUILD SUCCESS，共 13 项：通过 11，跳过 2）
  - 前端 `frontend/`：Vite + Vue 3 + TS + Element Plus + Pinia + Vue Router；`AppLayout`（学习空间切换占位、导航、「AI 助手」「学习画像」禁用）、首页显示健康状态、占位页、axios 实例、统一错误提示、`toApiError` 解析 ProblemDetail
  - 前端检查：type-check、lint、vitest（4 项）、build 全部通过
  - README 快速开始、D-013、CHANGELOG
- **未完成**：
  - 尚未在真实 MySQL 上启动后端：本机 3306 端口有 MySQL 在监听，但还没有 `.env`，也没有建库和用户
  - 前后端联调（首页显示 UP）尚未手动验证
  - 尚未提交 Git（等待用户指示）
- **下一步建议**：
  1. 在本机 MySQL 中建库建用户（`CREATE DATABASE aistudy CHARACTER SET utf8mb4; CREATE USER 'aistudy'@'localhost' IDENTIFIED BY '…'; GRANT ALL ON aistudy.* TO 'aistudy'@'localhost';`），复制 `.env.example` 为 `.env` 并填写密码
  2. 运行 `./mvnw spring-boot:run` 和 `npm run dev`，确认首页显示后端、数据库都是 UP，然后把 M0 标为已完成
  3. 可选：安装 Docker Desktop，让集成测试不再被跳过
  4. 开始 M1：学习空间 + Subject（第一个 Flyway 迁移脚本）
- **相关决策 / 变更**：D-013；CHANGELOG 已更新

### 2026-09-26 · 产品与架构设计

- **里程碑**：设计
- **已完成**：
  - 阅读项目规范，与用户澄清需求（多模型、无登录、图片/PDF 识图、知识点精简）
  - 编写设计文档 `docs/01`–`08`：需求、架构、数据库、后端模块、前端页面、AI 多模型、文件管理、MVP 路线
  - 落实评审意见 R1–R6：Subject 层、分析可追溯字段、可信度与确认流程、PDF 范围、复习预留字段、模型能力设计
  - 把 `Agent.md` 重命名为 `AGENTS.md`，并同步更新规范内容；按保守方案确认剩余的待定事项（README C5–C10）
  - 建立长期开发记录机制：`AGENTS.md` §20 会话规则、`docs/progress.md`、`docs/decisions.md`、`CHANGELOG.md`
  - 根目录 `README.md` 改为项目介绍（供 GitHub 首页显示）；设计文档索引放在 `docs/README.md`
- **未完成**：
  - 尚未编写任何代码；目录还不是 Git 仓库
  - 06 §10 配置示例中各模型的能力数值只是示意，没有核对
- **下一步建议**：
  1. 开始 M0：`git init`，建立 `backend/`（Spring Boot）、`frontend/`（Vite + Vue）、`docker-compose.yml`、`.env.example`、`.gitignore`
  2. 准备至少一家 Provider 的 API Key
  3. 收集约 10 道真实样例题，供 M4 调试 Prompt 用
- **相关决策 / 变更**：D-001–D-012（初始决策）；CHANGELOG 已建立
