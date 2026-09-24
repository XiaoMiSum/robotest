# 软件测试平台——工程规范索引

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 用途

本目录是 RoboTest 工程规范的唯一索引。规范正文分散在前端、后端、API、数据库、质量、发布和安全文档中，本文件只维护目录、阅读顺序和权威边界，不重复定义具体规则。

## 2. 阅读顺序

1. `02-overview.md`：了解规范优先级、规则登记册和冲突处理方式。
2. `03-frontend.md` / `04-backend.md`：了解两端工程实现边界。
3. `05-api.md` / `06-database.md`：确认跨端契约和持久化约束。
4. `07-quality.md` / `08-workflow.md` / `09-deploy.md`：确认质量、协作和交付门禁。
5. `10-security.md` / `11-migoo-framework.md`：确认安全基线和框架约束。
6. `12-task-template.md` / `13-scroll-container.md`：按任务或专项需要查阅。
7. `15-realtime-protocol.md`：查看通用 WebSocket 实时通信协议。
8. `14-improvement-backlog.md`：查看尚未执行的规范、代码、配置和交付任务。

## 3. 文档清单

| 文档 | 类型 | 适用范围 | 版本 | 日期 | 状态 |
| --- | --- | --- | --- | --- | --- |
| [02-overview](02-overview.md) | 治理总纲 | 全仓库 | V1.0 | 2026-09-24 | 已发布 |
| [03-frontend](03-frontend.md) | 工程规范 | `web/` | V1.0 | 2026-09-24 | 已发布 |
| [04-backend](04-backend.md) | 工程规范 | `server/` | V1.0 | 2026-09-24 | 已发布 |
| [05-api](05-api.md) | 跨端契约 | HTTP / WebSocket | V1.0 | 2026-09-24 | 已发布 |
| [06-database](06-database.md) | 数据规范 | PostgreSQL / MySQL 兼容说明 | V1.1 | 2026-09-24 | 已发布 |
| [07-quality](07-quality.md) | 质量门禁 | 测试、检查、覆盖率 | V1.0 | 2026-09-24 | 已发布 |
| [08-workflow](08-workflow.md) | 研发流程 | Git / PR / 发布 | V1.0 | 2026-09-24 | 已发布 |
| [09-deploy](09-deploy.md) | 交付手册 | 构建 / 环境 / 部署 | V1.0 | 2026-09-24 | 已发布 |
| [10-security](10-security.md) | 安全基线 | 认证 / 授权 / 数据安全 | V1.0 | 2026-09-24 | 已发布 |
| [11-migoo-framework](11-migoo-framework.md) | 集成参考 | migoo 框架 | V1.0 | 2026-09-24 | 已发布 |
| [12-task-template](12-task-template.md) | 流程模板 | AI / 开发任务 | V1.0 | 2026-09-24 | 已发布 |
| [13-scroll-container](13-scroll-container.md) | 专项规范 | 前端滚动容器 | V1.0 | 2026-09-24 | 已发布 |
| [14-improvement-backlog](14-improvement-backlog.md) | 待办清单 | 未执行的规范、代码、配置和交付任务 | V1.0 | 2026-09-24 | 起草中 |
| [15-realtime-protocol](15-realtime-protocol.md) | 实时协议 | 通用 WebSocket API | V1.0 | 2026-09-24 | 已发布 |

## 4. 当前统一决策

以下决策是跨文档的单一事实源，其他规范只引用，不重复定义：

| 主题 | 统一决策 | 主规范 |
| --- | --- | --- |
| HTTP 响应 | `Result<T>`，字段为 `code`、`msg`、`data` | `05-api.md` |
| 分页参数 | `pageNo/pageSize` | `05-api.md` |
| 分页数据 | `PageResult<T>.list` 和 `total` | `05-api.md` |
| 错误码 | 10 位错误码，按模块统一登记 | `05-api.md` / `11-migoo-framework.md` |
| 数据库 | PostgreSQL 14+ 为优先正式方案，MySQL 仅保留兼容说明 | `06-database.md` |
| UUID | 使用框架默认生成策略，不在项目规范中强制 UUID v7 | `06-database.md` / `11-migoo-framework.md` |
| MapStruct | 转换器统一放在 `model/convert/` | `04-backend.md` / `11-migoo-framework.md` |
| 上下文 | `X-Active-Workspace` / `X-Active-Project` 仅通过请求头传递 | `05-api.md` / `10-security.md` |
| 规范冲突 | 当前实现与目标规范冲突时暂停，由用户确认后再修改 | `02-overview.md` |
| Git 分支 | 保留 `master`，以 `develop` 管理日常集成 | `08-workflow.md` |
| WebSocket | 使用短时、一次性连接 Ticket；实时协议独立成文 | `15-realtime-protocol.md` |
| MapStruct 实例化 | 使用 Spring Bean 注入，不使用静态 `INSTANCE` | `04-backend.md` / `11-migoo-framework.md` |
| Wrapper 边界 | Service 允许简单动态条件，复杂/复用查询下沉 Mapper | `04-backend.md` |
| 静态分析 | 当前不引入 SpotBugs、ArchUnit、JaCoCo | `07-quality.md` |

## 5. 文档权威边界

- `docs/06-spec/` 定义工程目标、约束和检查方式。
- `web/package.json`、`web/pnpm-lock.yaml`、`server/pom.xml` 和实际配置文件记录当前技术实现事实。
- 需求、设计和交互文档定义业务目标与用户体验；工程规范不得擅自改变业务语义。
- 当实现与规范不一致时，不能静默选择一方，必须记录差异并请求用户确认。

## 6. 维护规则

- 新增或修改规范后，同步更新本索引。
- 规则必须有唯一编号、适用范围和检查方式。
- 同一事实只允许在一个主规范中定义，其他文档使用相对链接引用。
- 文档中的命令、脚本、profile 和配置项必须能够在仓库中找到，或明确标记为“计划能力”。
- `docs/06-spec/` 原位持续维护，不参与 `docs/07-archive/` 基线归档。

## 7. 已知下游同步项

以下内容不改变本规范的决策，但需要后续独立任务处理：

- `docs/03-architecture/` 和部分历史设计文档仍有 8080、旧前端版本和旧部署命令；
- 部分 `docs/04-detailed-design/` 文档仍使用 `page/records` 或 UUID v7 描述；
- 代码和配置中仍存在默认密钥、SQL 参数日志、未接通的覆盖率/静态分析和安全整改项；
- MapStruct 从历史目录迁移到 `model/convert/` 需要单独完成代码迁移和测试；
- `merged` Maven profile、版本化数据库迁移和 CI 分支保护尚未建立。

这些项目在完成前不得被描述为“已实现”或“已通过”。完整任务、优先级、依赖和验收标准见 [`14-improvement-backlog.md`](14-improvement-backlog.md)。

---

**文档结束**
