# 软件测试平台——AI 需求工作流开发方案与计划

**文档版本**：V1.0
**日期**：2026-10-03
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

将「需求管理 → AI 生成 → 追溯闭环 + 智能助手 + 缺陷分析 + 辅助功能」的文档链（需求、概要、详细、交互）转化为可执行的开发方案与阶段计划：明确总体实施策略、模块依赖与实施顺序、工作包清单、交付批次与验收门禁，作为开发排期、PR 审查与人工验收的共同依据。

### 1.2 范围

本计划覆盖以下交付内容：

1. **需求管理**：需求 CRUD、导入（Markdown / Word / 图片 ≤20MB）、拆分、属性与版本变更、变更记录；
2. **追溯矩阵**：追溯关系维护、覆盖率统计、影响分析及其页面；
3. **AI 底座**：AI 配置中心（模型、向量 API、场景提示词、用量、总开关）、AI 任务框架、向量与 RAG、重建引导；
4. **AI 场景**：生成链（六阶段）、智能助手、缺陷分析、辅助功能、导入 AI 识别文档级版本；
5. **横切内容**：全局导航与菜单注册、权限点接入、任务中心与任务详情、视觉规范落地。

不覆盖：本计划不描述功能行为、表结构、接口报文与页面交互细节，它们分别以需求、详细设计、交互设计文档为准。

**计划外独立任务登记（本期仅记录）——文件管理模块改造**：引入 MinIO 对象存储、设计泛化附件资源（不挂工作空间 / 项目，使用方以访问 URL 关联）与文件管理模块，并以 docker-compose 全家桶发行；统一覆盖需求导入源文件的存储与回看下载、需求详情（含图片）、既有缺陷附件与缺陷详情（含图片）的迁移改造。该任务另行计划展开，其落地前上述实现不进入本计划各工作包。

### 1.3 基线文档

| 层 | 文档 | 用途 |
| ---- | ---- | ---- |
| 需求 | `docs/01-requirements/06-requirement-management/02-srs-requirement-management.md` | 需求管理功能行为与验收口径 |
| 需求 | `docs/01-requirements/07-ai-capability/02-srs-ai-capability.md` | AI 能力总册（底座与公共口径） |
| 需求 | `docs/01-requirements/07-ai-capability/03-srs-ai-generation.md` | 生成链需求 |
| 需求 | `docs/01-requirements/07-ai-capability/04-srs-ai-assistant.md` | 智能助手需求 |
| 需求 | `docs/01-requirements/07-ai-capability/05-srs-ai-defect-analysis.md` | 缺陷分析需求 |
| 需求 | `docs/01-requirements/07-ai-capability/06-srs-ai-assisted-features.md` | 辅助功能需求 |
| 概要 | `docs/02-high-level-design/06-requirement-management/02-hld-requirement-management.md` | 需求管理模块划分与数据概要 |
| 概要 | `docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md` | AI 能力模块划分与机制概要 |
| 概要 | `docs/02-high-level-design/04-hld-data-interface.md` | 数据与接口概要 |
| 详细 | `docs/04-detailed-design/06-requirement-management/02-requirement-management.md` | 需求管理 DDL 与接口 |
| 详细 | `docs/04-detailed-design/05-trace-matrix.md` | 追溯矩阵 DDL 与接口 |
| 详细 | `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md` | AI 底座 DDL、任务框架与公共协议 |
| 详细 | `docs/04-detailed-design/07-ai-capability/03-ai-generation.md` | 生成链编排与接口 |
| 详细 | `docs/04-detailed-design/07-ai-capability/04-ai-assistant.md` | 助手会话、SSE 与确认执行 |
| 详细 | `docs/04-detailed-design/07-ai-capability/05-ai-defect-analysis.md` | 缺陷分析接口 |
| 详细 | `docs/04-detailed-design/07-ai-capability/06-ai-assisted-features.md` | 辅助功能接口 |
| 交互 | `docs/05-interaction-design/06-requirement-management/02-requirement-ui.md` | 需求管理页面行为 |
| 交互 | `docs/05-interaction-design/04-trace-matrix-ui.md` | 追溯矩阵页面行为 |
| 交互 | `docs/05-interaction-design/07-ai-capability/01-readme.md` | AI 能力交互分册入口 |
| 交互 | `docs/05-interaction-design/02-global-navigation.md` | 全局导航、菜单与助手入口 |
| 交互 | `docs/05-interaction-design/03-visual-design.md` | 视觉与 CSS 变量唯一来源 |

### 1.4 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| 生成链 | 需求到用例与追溯的六阶段 AI 生成：解析需求 → 生成模块结构 → 生成脑图文档 → 标记用例节点 → 填充用例属性 → 建立追溯边 |
| AI 底座 | 模型配置、任务框架、向量与 RAG、提示词与用量等平台级 AI 基础能力 |
| 阶段 / 工作包 | 计划的两级实施单元，编号分别为 P* 与 WP-*；本文只描述阶段与依赖，不排期、不给人天 |
| 交付批次 | 面向验收与合入的分组，一批对应一个可独立验收的范围 |
| SSE | Server-Sent Events，助手流式输出协议（唯一定义处为助手详细设计） |

## 2. 开发方案

### 2.1 总体实施策略

1. **文档驱动**：实现严格以 1.3 基线文档为唯一依据；发现实现与文档冲突时暂停，先由用户确认以哪边为准，再改文档或改代码，禁止静默偏离。
2. **数据先行、底座先行**：先交付数据库迁移（DDL 与索引），再做后端接口，最后按交互稿做前端；AI 场景功能统一排在 AI 底座之后。
3. **后端先行于前端**：接口经 springdoc 暴露 OpenAPI 后，前端按契约并行开发；API 变更先后端后前端。
4. **生成物人工确认**：所有 AI 生成物走「预览 → 人工确认 → 生效」，确认前不落业务数据。
5. **供给可配置、不锁定**：LLM 与向量供给经配置中心自由配置，代码中不硬编码任何供应商；开发环境准备至少一个兼容端点用于联调。
6. **提交粒度**：一个提交只做一件事（C7），前后端变更分属不同提交，严格目录隔离（只改本端目录）。

### 2.2 分支与协作

- **分支模型**：`master` ← `develop` ← `feature/*`（总则 `docs/00-spec/30-quality-delivery/02-workflow.md` 第 1 章）；当前工作分支 `feature/requirement-ai-workflow`，批次收口后向 `develop` 发 PR，不在本地直接合入。
- **提交规范**：`<emoji> <type>(<scope>): <description>`，scope 按业务域（如 `requirement`、`trace`、`ai`、`task`）。
- **门禁**：提交前运行本端验证脚本至 EXIT=0（文档端 `bash scripts/validate-docs.sh`，前端 / 后端各自脚本），覆盖率满足 C8。
- **跨端同步**：接口经 OpenAPI 分组同步（AI 相关分组 `ai`）；实时消息遵循 `docs/00-spec/20-contracts/03-realtime-protocol.md` 与 `docs/04-detailed-design/03-realtime-websocket.md`。

### 2.3 模块依赖与实施顺序

```mermaid
flowchart TD
  P0["P0 准备与环境"] --> P1["P1 数据层迁移"]
  P1 --> P2["P2 需求管理"]
  P1 --> P3["P3 追溯矩阵"]
  P1 --> P4["P4 AI 底座"]
  P2 --> P5["P5 AI 生成链"]
  P3 --> P5
  P4 --> P5
  P4 --> P6["P6 智能助手"]
  P4 --> P7["P7 缺陷分析与辅助功能"]
  P2 --> P8["P8 导入 AI 识别版本"]
  P4 --> P8
  P2 --> P9["P9 联调与验收"]
  P3 --> P9
  P5 --> P9
  P6 --> P9
  P7 --> P9
  P8 --> P9
```

### 2.4 技术实施要点

按模块列出实施要点；细节一律以对应基线文档为准，本文不重复定义。

#### 2.4.1 数据层与迁移

- PostgreSQL 14+；需求表族、追溯表、AI 表族（任务、产物、会话消息、提示词、用量）DDL 见 `docs/04-detailed-design/06-requirement-management/02-requirement-management.md`、`docs/04-detailed-design/05-trace-matrix.md`、`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`；
- 符合 C5（`id` / `created_at` / `updated_at` / `is_deleted`、UUID 框架默认策略、无物理外键）与 C9（索引规范）；迁移说明随首个涉及数据库的提交交付；
- 向量依赖 pgvector 扩展，扩展可用性属环境前置（见 2.5）。

#### 2.4.2 需求管理

- 树形与列表 CRUD、拆分、属性变更（含自由文本版本字段）与变更记录；数据更新只更新传入字段（C11）；
- 导入支持 Markdown / Word / 图片，单文件 ≤20MB，超限明确拒绝；
- 业务异常统一 `ServiceExceptionUtil.get(ErrorCode)` 10 位错误码（C3）；工作空间 / 项目上下文经请求头传递，不出现在 URL（C4）；
- 权限点 `requirement:*`。

#### 2.4.3 追溯矩阵

- 追溯边按逻辑外键建索引（C9），覆盖率与影响分析为查询侧统计；
- 页面交互按 `docs/05-interaction-design/04-trace-matrix-ui.md`；权限点 `trace:view` / `trace/edit`。

#### 2.4.4 AI 底座

- 配置中心页头常驻全局项 + 左侧分组导航 + 子路由 `/admin/ai/{models,embedding,prompts,usage}`（`/admin/ai` 重定向默认分组），权限 `ai:admin`；
- 任务框架统一状态机 `pending / running / succeeded / failed / cancelled`，终态停止轮询，任务详情 2s 轮询；任务创建校验顺序为总开关 → 权限 → 类型 → 输入 → 可用模型；
- SSE 流式协议以 `docs/04-detailed-design/07-ai-capability/04-ai-assistant.md` 为唯一定义处，断线后按消息轮询补齐；
- 向量维度变更触发全量重建，重建走通用任务进度口径，期间维度输入只读；
- 密钥类字段不回明文，仅回 `keyConfigured` 标记；管理配置接口不携带工作空间 / 项目上下文头，任务与助手接口按需携带（C4）。

#### 2.4.5 AI 生成链

- 六阶段逐段任务化：解析需求 → 生成模块结构 → 生成脑图文档 → 标记用例节点 → 填充用例属性 → 建立追溯边；每阶段产物落 `ai_artifact`；
- 预览 → 人工确认 → 生效；确认前不写入需求 / 用例 / 追溯业务数据；
- 脑图编辑复用既有脑图组件与 Yjs 协同（`docs/04-detailed-design/03-function-testing/08-mindmap-component.md`）。

#### 2.4.6 智能助手

- 会话与消息按用户归属隔离（唯一隔离维度为归属人）；
- 发送消息走 SSE 流式，断线标记中断并轮询补齐；意图解析产出变更预览，确认执行需 `ai:confirm` 权限并回执结果；
- 只读问答走限权检索，数据超权限范围时拒绝并说明。

#### 2.4.7 缺陷分析与辅助功能

- 趋势、总结、批量分类、重复检测均任务化，复用任务框架与状态口径；
- 辅助面板（用例补全、等级建议、计划排序）嵌入既有用例 / 计划页面，生成物同样人工确认后生效。

#### 2.4.8 前端与导航

- 菜单经路由注册表声明式注册（名称、图标、排序、分组、权限码），管理端设「AI 能力」分组，项目菜单「需求管理」居首，AI 助手悬浮球为平台级入口（`docs/05-interaction-design/02-global-navigation.md`）；
- Pinia store 分域装载：`aiAdmin` 仅管理端装载、离开管理模式即卸载；分组路由激活态由路由派生；
- 视觉只引用 `docs/05-interaction-design/03-visual-design.md` 的 CSS 变量，不自造颜色值；类型安全满足 C1（无 `any`）。

### 2.5 环境与依赖管控

| 项 | 要求 |
| ---- | ---- |
| 数据库 | PostgreSQL 14+，启用 pgvector 扩展（开发与生产一致） |
| LLM 供给 | 至少一个可连通的兼容端点 + 启用模型，经配置中心接入；供给故障可切换，不锁定供应商 |
| 实时通道 | 沿用既有 WebSocket / Yjs 服务；SSE 由后端按助手详细设计提供 |
| 构建与配置 | dev / prod profile 照旧；新增配置项随对应详细设计提交 |
| 依赖管控 | 尽量不新增外部依赖，确有必要时经团队讨论（总则第 5 节） |

## 3. 开发计划

### 3.1 阶段划分

| 阶段 | 名称 | 内容概要 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| P0 | 准备与环境 | 分支与基线同步、pgvector 与 LLM 端点连通 | — |
| P1 | 数据层迁移 | 需求、追溯、AI 三族 DDL 与索引迁移 | P0 |
| P2 | 需求管理 | CRUD / 属性 / 导入后端与前端 | P1（需求表族） |
| P3 | 追溯矩阵 | 追溯与覆盖率后端、矩阵前端 | P1（追溯表族） |
| P4 | AI 底座 | 配置中心、任务框架与 SSE、用量、任务中心前端 | P1（AI 表族） |
| P5 | AI 生成链 | 六阶段编排后端与生成预览确认前端 | P4、P2、P3 |
| P6 | 智能助手 | 会话 SSE、预览确认执行、悬浮球面板 | P4 |
| P7 | 缺陷分析与辅助功能 | 分析后端与页面、三个辅助面板 | P4、既有用例 / 计划模块 |
| P8 | 导入 AI 识别版本 | 导入文档级版本 AI 识别 | P4、P2 |
| P9 | 联调与验收 | 分批联调、权限与覆盖率校验、人工验收 | 各批次 |

### 3.2 工作包清单

| 编号 | 工作包 | 阶段 | 端 | 输入文档 | 依赖 | 完成判据 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| WP-0.1 | 分支与基线同步 | P0 | 全 | `docs/00-spec/30-quality-delivery/02-workflow.md` | — | 工作区干净，feature 分支基于 develop 最新 |
| WP-0.2 | 环境准备（pgvector + LLM 端点） | P0 | 后端 | AI 底座详细设计环境章节 | — | 迁移可执行，配置中心连通性测试通过 |
| WP-1.1 | 需求管理 DDL 迁移 | P1 | 后端 | `docs/04-detailed-design/06-requirement-management/02-requirement-management.md` | WP-0.2 | 迁移执行成功，索引符合 C5 / C9 |
| WP-1.2 | 追溯矩阵 DDL 迁移 | P1 | 后端 | `docs/04-detailed-design/05-trace-matrix.md` | WP-0.2 | 同上 |
| WP-1.3 | AI 底座 DDL 迁移（含向量表） | P1 | 后端 | `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md` | WP-0.2 | 同上，pgvector 向量列可用 |
| WP-2.1 | 需求 CRUD、属性与变更记录后端 | P2 | 后端 | 需求管理详设接口章节 | WP-1.1 | OpenAPI 暴露，10 位错误码，部分更新 |
| WP-2.2 | 需求导入后端（三类文件） | P2 | 后端 | 需求管理详设导入章节 | WP-1.1、WP-4.2、文件管理模块 | 三类样例导入成功，>20MB 拒绝 |
| WP-2.3 | 需求管理前端 | P2 | 前端 | `docs/05-interaction-design/06-requirement-management/02-requirement-ui.md` | WP-2.1、WP-2.2 | 状态分支齐全，C1 无 `any` |
| WP-3.1 | 追溯关系与覆盖率后端 | P3 | 后端 | 追溯矩阵详设接口章节 | WP-1.2 | CRUD 与统计接口通过，`trace:*` 校验生效，需求侧追溯委托（需求管理详设 3.12）与需求列表 / 详情 `coverageStatus` 填充补齐；`trace_edge` 影响处置字段组补列迁移、`impact_analysis` 处理器注册、评审 / 计划快照事务内写 `snapshot_ref` 边 |
| WP-3.2 | 追溯矩阵前端 | P3 | 前端 | `docs/05-interaction-design/04-trace-matrix-ui.md`、`docs/05-interaction-design/03-function-testing/04-workspace-ui-test-case.md` | WP-3.1 | 覆盖率、影响分析交互达标，脑图「关联需求」经追溯边接通 |
| WP-4.1 | AI 配置与用量后端 | P4 | 后端 | `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md` | WP-1.3 | `ai:admin` 生效，密钥不回明文 |
| WP-4.2 | AI 任务框架与 SSE 后端 | P4 | 后端 | AI 底座详设任务章节、助手详设 SSE 章节 | WP-1.3 | 状态机与轮询口径达标，OpenAPI 分组 `ai` |
| WP-4.3 | 配置中心前端（分组子路由） | P4 | 前端 | `docs/05-interaction-design/07-ai-capability/02-ai-admin-ui.md` | WP-4.1 | 分组导航、页头全局项与状态分支齐全 |
| WP-4.4 | 任务中心与任务详情前端 | P4 | 前端 | `docs/05-interaction-design/07-ai-capability/03-ai-task-ui.md` | WP-4.2 | 2s 轮询、终态停轮询 |
| WP-5.1 | 生成链六阶段编排后端 | P5 | 后端 | `docs/04-detailed-design/07-ai-capability/03-ai-generation.md` | WP-4.2、WP-2.1、WP-3.1 | 六阶段任务化，产物落 `ai_artifact` |
| WP-5.2 | 生成预览与确认前端 | P5 | 前端 | `docs/05-interaction-design/07-ai-capability/04-ai-generation-ui.md` | WP-5.1 | 人工确认前不落业务数据 |
| WP-6.1 | 助手会话与 SSE 后端 | P6 | 后端 | `docs/04-detailed-design/07-ai-capability/04-ai-assistant.md` | WP-4.2 | 流式、断线补齐、确认执行回执通过 |
| WP-6.2 | 助手面板与悬浮球前端 | P6 | 前端 | `docs/05-interaction-design/07-ai-capability/05-ai-assistant-ui.md` | WP-6.1 | 入口显隐随总开关与配置就绪态 |
| WP-7.1 | 缺陷分析后端 | P7 | 后端 | `docs/04-detailed-design/07-ai-capability/05-ai-defect-analysis.md` | WP-4.2 | 四类分析任务化并可下钻 |
| WP-7.2 | 缺陷分析前端 | P7 | 前端 | `docs/05-interaction-design/07-ai-capability/06-defect-analysis-ui.md` | WP-7.1 | 状态分支齐全 |
| WP-7.3 | 辅助功能前后端 | P7 | 全 | `docs/04-detailed-design/07-ai-capability/06-ai-assisted-features.md`、`docs/05-interaction-design/07-ai-capability/07-assisted-features-ui.md` | WP-4.2、既有用例 / 计划模块 | 三面板嵌入且确认后生效 |
| WP-8.1 | 导入 AI 识别文档级版本 | P8 | 全 | 需求 SRS 版本字段条目、需求管理详设导入章节 | WP-4.2、WP-2.2 | 导入识别出版本并可确认 |
| WP-9.1 | 分批联调与验收 | P9 | 全 | 各批对应文档 | 各批工作包 | 验证脚本 EXIT=0、C8 达标、人工验收通过 |

> 说明：WP-2.2 的端到端执行依赖任务框架，故 WP-4.2 的任务框架段（任务提交、状态机、进度轮询、执行引擎与产物确认）与 WP-4.4（任务详情前端）随批次一提前交付，SSE 流式段与配置中心仍按 P4 批次交付；存量需求池接口（批量创建、删除、body 形态归档与文档关联）及对应前端页面随 WP-2.1 / WP-2.3 按需求管理详设目标态下线。此外，需求导入源文件的存储与回看下载由文件管理模块任务承载（计划外独立任务，见 1.2 登记），WP-2.2 的端到端验收依赖该任务先行。

### 3.3 交付批次建议

按依赖关系建议三批交付，每批在 `develop` 收口一次 PR 并保留可发布状态：

| 批次 | 覆盖阶段 | 范围 | 验收重点 |
| ---- | ---- | ---- | ---- |
| 批次一 | P0、P1、P2、P3，另随批提前 WP-4.2（任务框架段）与 WP-4.4（任务详情前端） | 需求管理与追溯矩阵（业务不依赖 AI 场景，导入执行经任务框架） | 需求导入端到端全流程（提交、进度、采纳）、属性与版本变更、追溯矩阵人工闭环、覆盖率统计 |
| 批次二 | P4（其余）、P5、P8 | AI 底座与生成主链路 | 配置中心分组与连通性、六阶段生成人工确认、导入识别版本 |
| 批次三 | P6、P7 | AI 场景扩展 | 助手 SSE 与确认执行、缺陷分析四类、辅助三面板 |

批次间关系：批次二、三均依赖批次一的数据层与业务数据；批次内部后端可先于前端联调，前端按 OpenAPI 契约并行。

### 3.4 验收门禁

每批收口必须全部满足：

1. 本端验证脚本 EXIT=0（C7 提交格式 + lint / typecheck / 测试 + 覆盖率 C8 + 契约一致性）；
2. 无 `any`（C1）、Controller 无业务逻辑（C2）、异常与错误码合规（C3）、上下文头传递（C4）、迁移与索引合规（C5 / C9）；
3. API 文档随接口变更同步（springdoc），实现与文档冲突已按 2.1 第 1 条处理；
4. 关键路径人工验收，验收结果回填本计划的修改记录说明。

人工验收关键路径（每批至少覆盖）：

- 批次一：三类文件导入、属性与版本变更、追溯边编辑与覆盖率；
- 批次二：模型连通性测试、维度变更重建引导、六阶段生成预览确认、任务失败重试；
- 批次三：助手流式与断线恢复、超权限只读问答拒绝、缺陷分析下钻、辅助面板确认生效。

## 4. 风险与应对

| 风险 | 影响 | 应对 |
| ---- | ---- | ---- |
| LLM 供给不稳定或延迟 | 任务超时、流式中断 | 任务失败态支持重试；SSE 断线按消息轮询补齐；供给经配置切换不锁定 |
| 向量维度变更全量重建 | 重建期间检索不可用 | 重建引导条常驻显示进度，期间维度输入只读，进度复用任务口径 |
| 生成物质量不达预期 | 误写业务数据 | 一律预览 → 人工确认 → 生效，确认前不落数据 |
| 大文件导入解析失败（图片 ≤20MB） | 用户重复操作 | 超限与解析失败均明确提示原因，保留原文件可重试 |
| 长周期分支偏离 develop | 合入冲突 | 批次收口即发 PR 合入 develop，期间定期同步 |
| 跨端契约漂移 | 联调返工 | OpenAPI 先行，前端按契约开发，验收门禁含契约一致性检查 |
| 新增外部依赖 | 违反依赖管控 | 默认不新增；确有必要先经团队讨论（总则第 5 节） |

## 5. 计划维护

- 阶段、工作包与批次随基线文档变更同步调整，调整走文档流程二并追加修改记录；
- 本文只维护阶段与依赖，不登记人天与日历排期；排期由管理者在本文之外按资源换算；
- 批次实际收口情况（验收结果、遗留项）记入对应修改记录行的说明。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 初始版本：总体实施策略、九阶段依赖、23 个工作包与三批交付建议 |
| V1.0 | 2026-10-03 | 实施顺序调整：P1 三包（含 AI DDL）全部并入批次一；WP-4.2 任务框架段与 WP-4.4 任务详情前端随批次一提前，WP-2.2 依赖补 WP-4.2；需求侧追溯委托与 `coverageStatus`、脑图「关联需求」改造归 WP-3.1 / WP-3.2 随 P3 补齐 |
| V1.0 | 2026-10-03 | 登记计划外独立任务「文件管理模块改造」（MinIO、泛化附件资源与访问 URL 关联、docker-compose 全家桶），统一覆盖导入源文件与需求 / 缺陷详情图片、既有缺陷附件，本期仅记录；WP-2.2 依赖与端到端验收补该任务先行 |
| V1.0 | 2026-10-04 | WP-3.1 范围补充：`trace_edge` 影响处置字段组补列迁移（详设 2.2）、`impact_analysis` 处理器注册、评审 / 计划快照事务内写 `snapshot_ref` 边；`coverage_analysis` 处理器依赖 LLM 语义比对，随批次二补 |
