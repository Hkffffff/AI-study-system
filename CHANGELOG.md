# Changelog

本文件记录面向用户的功能变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

- 分类：`新增`、`变更`、`弃用`、`移除`、`修复`、`安全`。
- 开发中的变更写在 `[Unreleased]` 下；完成一个里程碑或发布时，归入对应的版本号。
- 只记录用户能感知到的变化。架构决策记在 `docs/decisions.md`，开发过程记在 `docs/progress.md`。

## [Unreleased]

### 新增

- 产品与架构设计文档（`docs/01`–`08`），以及开发进度和决策记录机制。
- 项目骨架（M0）：可以启动的后端（Spring Boot）和前端（Vue 3）；首页显示后端和数据库的连接状态；侧边导航包含首页、资料库、错题（后两项为占位页），「AI 助手」「学习画像」显示为规划中；接口出错时统一弹出中文提示。
- 本地开发配置：`.env.example`、MySQL 的 `docker-compose.yml`、README 中的启动步骤。
