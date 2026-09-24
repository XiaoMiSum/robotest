# 工程规范总览

**文档版本**：V1.0
**日期**：2026-09-24
**状态**：已发布

---

## 1. 总则

### 1.1 编写目的

本文件定义工程规范的治理规则、权威边界、规则登记册和冲突处理方式。具体实现要求分别由前端、后端、API、数据库、质量、发布和安全规范维护。

### 1.2 适用范围

本规范适用于 `web/`、`server/`、`scripts/`、数据库脚本、CI 配置以及 AI 辅助开发任务。具体端专属实现以 `web/AGENTS.md` 和 `server/AGENTS.md` 为补充。

### 1.3 规范语言

- **必须 / MUST**：违反即不得合并或发布。
- **应该 / SHOULD**：原则上必须遵守；偏离时必须记录理由。
- **可以 / MAY**：按场景选择，不作为默认门禁。
- 示例代码仅在标注“可执行示例”时要求编译或运行；否则必须明确标注为伪代码。

### 1.4 权威性与事实源

规范描述目标约束，代码和配置描述当前实现事实。两者冲突时不得静默覆盖：

1. 先记录差异、影响范围和可选方案。
2. 判断是否改变业务语义或公共契约。
3. 暂停后续实现，提交用户确认。
4. 用户确认后，先更新对应设计/规范文档，再修改代码或配置。

同一主题只能有一个主规范定义。其他文档使用链接引用，不复制契约。

## 2. 规则登记册

以下编号是全局兼容编号。新增领域规则应使用领域前缀，例如 `API-`、`DB-`、`SEC-`、`UI-`。

| 编号 | 规则摘要 | 主规范 | 检查方式 |
| --- | --- | --- | --- |
| C1 | 前端禁止 `any`，优先使用 `unknown`、类型守卫或明确类型 | `docs/00-spec/10-engineering/01-frontend.md` | ESLint、TypeScript、代码审查 |
| C2 | Controller 只负责路由、参数校验和响应包装，不承载业务逻辑 | `docs/00-spec/10-engineering/02-backend.md` | 代码审查、依赖检查 |
| C3 | 业务异常统一通过 migoo 的 `ErrorCode` 与 `ServiceExceptionUtil` 抛出 | `docs/00-spec/10-engineering/03-migoo-framework.md` | 编译、单元测试、代码审查 |
| C4 | 上下文和资源归属必须可验证，服务端不得信任客户端声明 | `docs/00-spec/20-contracts/01-api.md` / `docs/00-spec/40-security/01-security.md` | 请求检查、代码审查 |
| C5 | 业务表具备 `id`、`created_at`、`updated_at`、`is_deleted`，禁止物理外键 | `docs/00-spec/20-contracts/02-database.md` | DDL 检查、数据库审查 |
| C6 | 注释只解释为什么，不复述代码行为 | `docs/00-spec/00-governance/01-overview.md` | 代码审查 |
| C7 | 提交遵循 `<emoji> <type>(<scope>): <description>`，提交保持原子性 | `docs/00-spec/30-quality-delivery/02-workflow.md` | 提交检查、PR 审查 |
| C8 | 核心代码覆盖率目标不低于 70%，具体阻断范围由质量门禁配置 | `docs/00-spec/30-quality-delivery/01-quality.md` | 覆盖率报告、CI |
| C9 | 关联字段和高频查询字段建立合理索引，单表索引数量受控 | `docs/00-spec/20-contracts/02-database.md` | DDL 检查、性能审查 |
| C10 | 后端优先复用 migoo 已提供的响应、异常、校验和数据访问能力 | `docs/00-spec/10-engineering/03-migoo-framework.md` | 依赖检查、代码审查 |
| C11 | 更新只写入调用方实际提交的字段，禁止整行查询结果直接作为更新载体 | `docs/00-spec/10-engineering/02-backend.md` | 单元测试、代码审查 |
| UI-SC-01～09 | 滚动容器和滚动条专项规则 | `docs/00-spec/50-ui/03-scroll-container.md` | 浏览器验收、代码审查 |
| UI-DS-01～08 | 前端视觉、布局、状态、响应式和可访问性规则 | `docs/00-spec/50-ui/01-frontend-design.md` | 设计验收、浏览器验收、代码审查 |
| UI-PAGE-01～11 | 页面拆分、组件边界、状态归属、数据流和列表错误可见性规则 | `docs/00-spec/50-ui/02-page-development.md` | 代码审查、组件测试、页面验收 |

规则登记册只保存摘要和链接；正例、反例、例外和完整说明以主规范为准。

## 3. 文档分类与职责

| 分类目录 | 文档 | 权威范围 |
| --- | --- | --- |
| `00-governance/` | `01-overview.md`、`02-task-template.md`、`03-improvement-backlog.md` | 规则登记、任务流程、冲突处理和待办 |
| `10-engineering/` | `01-frontend.md`、`02-backend.md`、`03-migoo-framework.md`、`04-migoo-api-reference.md` | 前端、后端和框架工程边界 |
| `20-contracts/` | `01-api.md`、`02-database.md`、`03-realtime-protocol.md` | HTTP、数据和实时通信契约 |
| `30-quality-delivery/` | `01-quality.md`、`02-workflow.md`、`03-deploy.md`、`04-deployment-runbook.md` | 质量、协作、构建、部署和项目操作 |
| `40-security/` | `01-security.md` | 认证、授权、隔离、密钥和审计 |
| `50-ui/` | `01-frontend-design.md`、`02-page-development.md`、`03-scroll-container.md` | 视觉设计、页面开发和专项组件边界 |

## 4. 技术事实来源

工程规范不重复维护易漂移的依赖版本和命令：

- 前端版本：以 `web/package.json`、`web/pnpm-lock.yaml` 为准。
- 后端版本和依赖：以 `server/pom.xml` 及 Maven BOM 为准。
- 本地命令：以 `web/package.json` 和 `scripts/*.sh` 为准。
- 端口和环境变量：以 `server/src/main/resources/application.yaml`、`web/vite.config.ts` 和环境文件为准。
- 数据库结构：以 `server/src/main/resources/db/` 和版本化迁移脚本为准。
- API 契约：以 `docs/00-spec/20-contracts/01-api.md`、SpringDoc 生成的 OpenAPI 和前端生成类型为准。

## 5. 变更与例外

任何规范变更必须说明：

1. 变更原因和影响范围。
2. 是否改变 API、数据库、权限或部署行为。
3. 迁移方式、回滚方式和验证方式。
4. 需要同步的代码、配置、AGENTS 和设计文档。

例外必须记录批准人、原因、失效日期和替代控制措施。临时例外不得演变为无期限的默认实现。

## 6. 术语

| 术语 | 定义 |
| --- | --- |
| 主规范 | 某一主题唯一负责定义要求的文档 |
| 当前实现 | 代码、配置、锁文件和数据库脚本反映的事实 |
| 目标规范 | 经用户确认后必须实现的工程要求 |
| 活动上下文 | 当前请求所属的租户或资源作用域；传递和资源归属必须由服务端验证 |
| 资源 ID | 用于定位被操作资源的标识，不等同于活动作用域 |
| 规则登记册 | 规则编号、摘要、主规范和检查方式的索引 |

## 7. 参考

- 目录索引：`docs/00-spec/00-readme.md`
- 文档管理约定：`docs/AGENTS.md`
- 前端约定：`web/AGENTS.md`
- 后端约定：`server/AGENTS.md`

---

**文档结束**
