# 工程规范索引

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 用途

本目录是工程规范的唯一索引。规范正文按治理、工程、跨端契约、质量与交付、安全和 UI 分类维护；本文件只维护目录、阅读顺序、统一决策和权威边界，不重复定义具体规则。

## 2. 分类目录

| 分类 | 目录 | 主要内容 |
| --- | --- | --- |
| 规范治理 | [00-governance](00-governance/00-readme.md) | 规则登记、任务模板、冲突处理和待办 |
| 工程规范 | [10-engineering](10-engineering/00-readme.md) | 前端、后端和框架集成 |
| 跨端契约 | [20-contracts](20-contracts/00-readme.md) | API、数据库和实时通信 |
| 质量与交付 | [30-quality-delivery](30-quality-delivery/00-readme.md) | 质量、流程、构建、部署和 Runbook |
| 安全规范 | [40-security](40-security/00-readme.md) | 认证、授权、隔离、密钥和审计 |
| UI 规范 | [50-ui](50-ui/00-readme.md) | 视觉设计、页面开发和滚动容器 |

每个分类目录都包含自己的 `00-readme.md` 分类索引。

## 3. 阅读顺序

1. `00-governance/01-overview.md`：了解规则优先级、登记册和冲突处理方式。
2. `10-engineering/01-frontend.md` / `02-backend.md`：了解两端工程实现边界。
3. `20-contracts/01-api.md` / `02-database.md` / `03-realtime-protocol.md`：确认跨端契约。
4. `30-quality-delivery/01-quality.md` / `02-workflow.md` / `03-deploy.md` / `04-deployment-runbook.md`：确认质量、协作和交付要求。
5. `40-security/01-security.md`：确认安全基线。
6. `50-ui/01-frontend-design.md` / `02-page-development.md` / `03-scroll-container.md`：确认 UI 设计、页面开发和专项组件规则。
7. `00-governance/02-task-template.md`：执行任务时使用流程模板。
8. `00-governance/03-improvement-backlog.md`：查看未完成工作和验收状态。

## 4. 文档清单

### 4.1 规范治理

| 文档 | 职责 | 状态 |
| --- | --- | --- |
| [00-governance/01-overview](00-governance/01-overview.md) | 规则登记、权威边界和冲突处理 | 已发布 |
| [00-governance/02-task-template](00-governance/02-task-template.md) | 任务执行和交付检查模板 | 已发布 |
| [00-governance/03-improvement-backlog](00-governance/03-improvement-backlog.md) | 未执行事项、优先级和验收标准 | 起草中 |

### 4.2 工程规范

| 文档 | 职责 | 状态 |
| --- | --- | --- |
| [10-engineering/01-frontend](10-engineering/01-frontend.md) | 前端分层、组件、路由、请求和测试 | 已发布 |
| [10-engineering/02-backend](10-engineering/02-backend.md) | 后端分层、DTO、数据访问和更新 | 已发布 |
| [10-engineering/03-migoo-framework](10-engineering/03-migoo-framework.md) | 框架组件选择、适配和升级 | 已发布 |
| [10-engineering/04-migoo-api-reference](10-engineering/04-migoo-api-reference.md) | 框架 API 速查和接入边界 | 已发布 |

### 4.3 跨端契约

| 文档 | 职责 | 状态 |
| --- | --- | --- |
| [20-contracts/01-api](20-contracts/01-api.md) | HTTP、响应、分页、错误码和 OpenAPI | 已发布 |
| [20-contracts/02-database](20-contracts/02-database.md) | 数据类型、表结构、索引和迁移 | 已发布 |
| [20-contracts/03-realtime-protocol](20-contracts/03-realtime-protocol.md) | WebSocket 连接、帧和生命周期 | 已发布 |

### 4.4 质量与交付

| 文档 | 职责 | 状态 |
| --- | --- | --- |
| [30-quality-delivery/01-quality](30-quality-delivery/01-quality.md) | 测试、覆盖率、检查和质量红线 | 已发布 |
| [30-quality-delivery/02-workflow](30-quality-delivery/02-workflow.md) | 分支、提交、PR、发布和回滚 | 已发布 |
| [30-quality-delivery/03-deploy](30-quality-delivery/03-deploy.md) | 构建、环境、部署和回滚原则 | 已发布 |
| [30-quality-delivery/04-deployment-runbook](30-quality-delivery/04-deployment-runbook.md) | 具体项目部署操作和故障排查 | 已发布 |

### 4.5 安全规范

| 文档 | 职责 | 状态 |
| --- | --- | --- |
| [40-security/01-security](40-security/01-security.md) | 认证、授权、隔离、密钥和审计 | 已发布 |

### 4.6 UI 规范

| 文档 | 职责 | 状态 |
| --- | --- | --- |
| [50-ui/01-frontend-design](50-ui/01-frontend-design.md) | 视觉层级、设计令牌、状态和可访问性 | 已发布 |
| [50-ui/02-page-development](50-ui/02-page-development.md) | 大页面、小组件、状态归属和数据流 | 已发布 |
| [50-ui/03-scroll-container](50-ui/03-scroll-container.md) | 滚动容器、滚动拥有者和滚动条 | 已发布 |

## 5. 当前统一决策

以下决策是跨文档的单一事实源，其他规范只引用，不重复定义：

| 主题 | 统一决策 | 主规范 |
| --- | --- | --- |
| HTTP 响应 | `Result<T>`，字段为 `code`、`msg`、`data` | `20-contracts/01-api.md` |
| 分页参数 | `pageNo/pageSize` | `20-contracts/01-api.md` |
| 分页数据 | `PageResult<T>.list` 和 `total` | `20-contracts/01-api.md` |
| 错误码 | 10 位错误码，按模块统一登记 | `20-contracts/01-api.md` / `10-engineering/03-migoo-framework.md` |
| 数据库 | PostgreSQL 14+ 为优先正式方案，MySQL 仅保留兼容说明 | `20-contracts/02-database.md` |
| UUID | 使用框架默认生成策略，不强制 UUID 版本 | `20-contracts/02-database.md` / `10-engineering/03-migoo-framework.md` |
| MapStruct | 转换器统一放在 `model/convert/` | `10-engineering/02-backend.md` / `10-engineering/03-migoo-framework.md` |
| 上下文 | 作用域和资源归属必须可验证，客户端声明不能作为授权依据 | `40-security/01-security.md` / `20-contracts/01-api.md` |
| 规范冲突 | 当前实现与目标规范冲突时暂停，由用户确认后再修改 | `00-governance/01-overview.md` |
| Git 分支 | 保留 `master`，以 `develop` 管理日常集成 | `30-quality-delivery/02-workflow.md` |
| WebSocket | 使用短时、一次性连接 Ticket；实时协议独立成文 | `20-contracts/03-realtime-protocol.md` |
| 静态分析 | 当前不引入 SpotBugs、ArchUnit、JaCoCo | `30-quality-delivery/01-quality.md` |

## 6. 文档权威边界

- `docs/00-spec/` 定义工程目标、约束和检查方式。
- `web/package.json`、`web/pnpm-lock.yaml`、`server/pom.xml` 和实际配置文件记录当前技术实现事实。
- 需求、设计和交互文档定义业务目标与用户体验；工程规范不得擅自改变业务语义。
- 当实现与规范不一致时，不能静默选择一方，必须记录差异并请求用户确认。

## 7. 维护规则

- 新增或修改规范后，同步更新根索引、对应分类索引和规则登记册。
- 规则必须有唯一编号、适用范围和检查方式。
- 同一事实只允许在一个主规范中定义，其他文档使用相对链接引用。
- 文档中的命令、脚本、profile 和配置项必须能够在仓库中找到，或明确标记为“计划能力”。
- `docs/00-spec/` 原位持续维护，不参与 `docs/07-archive/` 基线归档。

## 8. 已知下游同步项

以下内容不改变本规范的决策，但需要后续独立任务处理：

- 部分历史架构/设计文档仍有旧端口、旧前端版本和旧部署命令；
- 详细设计和交互设计的 API 契约已完成本轮同步，后续新增接口仍需按 `20-contracts/01-api.md` 校验；
- 代码和配置中仍存在 SQL 参数日志、未接通的覆盖率/静态分析和其余安全整改项；
- `merged` Maven profile、版本化数据库迁移和 CI 分支保护尚未建立。

这些项目在完成前不得被描述为“已实现”或“已通过”。完整任务、优先级、依赖和验收标准见 [`00-governance/03-improvement-backlog.md`](00-governance/03-improvement-backlog.md)。

---

**文档结束**
