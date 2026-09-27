# AI-study · 个人 AI 学习管理系统

一个供个人使用的 AI 学习管理系统，首个场景是考研备考（数学一、408）。

上传题目照片或 PDF → AI 识别题目和你的解题过程 → 分析错因、给出正确思路 → 你确认或修正 → 针对这道题继续和 AI 对话 → 日后复盘。

> 项目状态：**M0 项目骨架已完成**（前后端可以启动，尚无业务功能），下一步是 M1。当前进度见 [docs/progress.md](docs/progress.md)。

## 核心概念

| 概念 | 说明 |
|---|---|
| 学习空间（LearningSpace） | 数学、408、Java……一个应用，多个彼此隔离的空间；空间下可以有可选的学科（Subject） |
| 资料库（Archive） | 原始图片和 PDF，是事实来源，永不修改 |
| 错题（Question） | 从资料中建立的结构化题目，标注每个来源是题目、我的解答还是标准答案 |
| AI 分析 | 识别结果 + 知识点、错因、正确思路、易混淆概念；有版本、可追溯、需确认 |
| 多模型 | 通过 API 接入 GPT、Claude 等，按模型能力自动筛选和适配 |

## 技术栈

- 后端：Java 21 · Spring Boot 3 · Spring Data JPA · MySQL 8 · Flyway
- 前端：Vue 3 · TypeScript · Vite · Element Plus · Pinia
- AI：OpenAI Responses API、Anthropic Messages API（可扩展）
- 架构：模块化单体；文件存储在本地，可换成 S3/MinIO

## 快速开始

环境要求：JDK 21+、Node.js 22+、MySQL 8.4（推荐用 Docker）。不需要预先安装 Maven，项目自带 Maven Wrapper。

```bash
# 1. 配置环境变量
cp .env.example .env          # 修改其中的数据库密码；AI Key 在 M4 之前可以留空

# 2. 启动 MySQL（二选一）
docker compose up -d mysql    # 使用 Docker
# 或者使用本机 MySQL 8.4：手动建库建用户，名称和密码与 .env 保持一致（utf8mb4）

# 3. 启动后端（http://127.0.0.1:8080）
cd backend
./mvnw spring-boot:run        # Windows 命令提示符 / PowerShell：mvnw.cmd spring-boot:run

# 4. 启动前端（http://127.0.0.1:5173，/api 请求会代理到后端）
cd frontend
npm install
npm run dev
```

打开 http://127.0.0.1:5173 ，首页会显示后端和数据库的连接状态。

常用检查命令：

| 位置 | 命令 | 说明 |
|---|---|---|
| `backend/` | `./mvnw verify` | 编译并运行测试；没有 Docker 时会自动跳过基于 Testcontainers 的集成测试 |
| `frontend/` | `npm run type-check` / `npm run lint` / `npm test` / `npm run build` | 类型检查、代码检查、单元测试、生产构建 |

## 文档

| 文档 | 说明 |
|---|---|
| [docs/README.md](docs/README.md) | 设计文档索引、需求澄清记录、评审记录 |
| [docs/08-mvp-roadmap.md](docs/08-mvp-roadmap.md) | MVP 开发路线 |
| [docs/progress.md](docs/progress.md) | 开发进度 |
| [docs/decisions.md](docs/decisions.md) | 架构决策记录 |
| [CHANGELOG.md](CHANGELOG.md) | 变更日志 |
| [AGENTS.md](AGENTS.md) | 项目规范（供 AI 编码助手和开发者遵循） |

## 安全提示

本项目没有登录功能，默认只监听 `127.0.0.1`。API Key 只通过环境变量提供（参考 `.env.example`），不要提交 `.env`。
