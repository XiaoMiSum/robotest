# 软件测试平台——AI Task 迁移计划

**文档版本**：V1.0  
**日期**：2026-10-16  
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

定义 AI Task 由旧「应用编排任务引擎」迁移至新双平面架构（执行编排归 AI、写库编排归应用层）的实施路径、工作包与**旧代码深度清理方案**，作为迁移实施与收口验收的唯一执行依据。行为、表结构、接口与页面口径一律以上游设计文档为准，本计划只写实施路径与依赖。

### 1.2 范围

- **覆盖**：`server/` 旧任务引擎（执行核心、任务资源、产物确认与采纳链、数据层、错误码）与 `web/` 旧任务前端（状态类型、store、任务中心与详情页、审核面板、组合式函数、跨域消费方、测试）的整体切换与深度清理；数据清空重建；OpenAPI 与契约重生成；清理门禁。
- **不含**：智能助手工具调用重构（见 `docs/06-implementation-plan/05-ai-assistant-tool-invocation-plan.md`，本计划仅保证其 `assistant_parse` 依附的 SPI 同步替换）；vision 能力落地（`docs/06-implementation-plan/04-ai-vision-capability-plan.md`）；GitLab 集成（`docs/06-implementation-plan/03-api-gitlab-integration-plan.md`）；接口测试域自有执行引擎（`DispatchableTask` 族，同名不同族，不涉）。

### 1.3 基线文档

| 层 | 文档 | 用途 |
| ---- | ---- | ---- |
| 概要 | `docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md` | 机制唯一上位依据（双平面、状态机、不变式） |
| 概要 | `docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md` | AI 模块划分与生命周期 |
| 详细 | `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md` | 2.6 六表 DDL、3.6 任务资源、4.2 执行核与护栏、6 错误码、7 实施说明 |
| 详细 | `docs/04-detailed-design/07-ai-capability/03-ai-generation.md`、`docs/04-detailed-design/07-ai-capability/05-ai-defect-analysis.md`、`docs/04-detailed-design/07-ai-capability/06-ai-assisted-features.md`、`docs/04-detailed-design/03-function-testing/09-requirement-management.md` | 各域类型适配器、提案结构与承接规则 |
| 交互 | `docs/05-interaction-design/07-ai-capability/03-ai-task-ui.md` | 任务中心与提案审核新交互（状态机、轨迹、终止 / 再次执行） |
| 需求 | `docs/01-requirements/07-ai-capability/02-srs-ai-capability.md` | 1.5 任务通用规则（验收口径来源） |
| 计划 | `docs/06-implementation-plan/02-ai-requirement-development-plan.md` | 既有工作包状态与批次编排（收口联动回填） |

### 1.4 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| 直接切换 | 同一版本内完成新旧替换，不保留兼容端点与双写路径，回滚靠 `git revert` |
| 清空重建 | 旧 `ai_task` / `ai_artifact_confirm` 数据不迁移、不回填，删表重建为新结构（用户裁决，开发环境无保留数据） |
| 类型适配器 | 新 SPI（TypeAdapter）：注册 input 校验、工具白名单与预算默认、提案结构、落库承接 |
| 清理门禁 | 以 grep 零命中为判据的旧概念清除检查清单（见 2.7） |

## 2. 开发方案

### 2.1 现状分析

**server（涉及约 110 文件：主源码 ~78、测试 ~31、SQL 4）**

- 执行核心：`service/ai/task/` 根 15 文件——`TaskHandler` SPI（15 个具体 Handler，含 8 个分散各域）、`TaskHandlerRegistry`、`AiTaskExecutor`（@Async + CAS 状态流转 + 内嵌进度上报）、`AiTaskServiceImpl`（556 行统一资源：submit / 分页 / 详情 / cancel / retry / confirm、waitSeconds 同步等待、startTask 双触发）、`AiTaskSweeper`（超时置 `failed`）、`ArtifactAdopter` SPI（14 个承接实现）。
- 资源与端点：`AiTaskController` 8 端点（含 `/{taskId}/cancel`、`/{taskId}/retry`、`/{taskId}/artifacts/confirm`、`/{taskId}/artifacts/{artifactKey}`），DTO 全族携带 `progress / phase / retryOfTaskId / result / artifacts`。
- 数据层：`AiTask` / `AiArtifactConfirm` 实体与 Mapper（内联状态字面量）、`schema.sql` L1122–1162 与 `V20261003150200__ai_capability.sql` L121–161。
- 已存在的可复用能力：模型能力选型 `requiredCapability`（落在旧 SPI 上）、`AiSettingsReader.resolveModel`、用量记账 `AiUsageLog` 族、助手域独立工具注册表（`AssistantToolRegistry`，不属任务引擎）。
- **不存在的能力（新架构需新建）**：Agent 循环、执行 / 轨迹 / 提案 / 采纳 / 工具注册表五表与对应服务、预算护栏、轨迹事件流。
- 盘点确认的死代码与重复路径（迁移时一并清除）：
  1. `GenerationAdoptService` 接口零调用方（死接口）；
  2. 6 个 type 有 Handler 无 Adopter（`vector_reindex / assistant_parse / bug_triage / bug_trend_summary`，另有 `coverage_analysis / impact_analysis` 两类已取消——追溯矩阵需求整体下线，其 Handler 随需求删除），confirm 必然 1000018114（死路确认路径）；
  3. 状态字面量三套并存（`Constants.AiTaskStatus`、`AiTaskMapper` 内联、`AiTaskServiceImpl:515` 硬编码）；
  4. `startTask()` 直调 + `afterCommit` 双触发重复路径；
  5. 提示词默认值双轨（`AiPromptAdminService.builtinContent` vs `AiPromptScenes` vs `Handler.defaultPrompt()`）；
  6. `RequirementServiceImpl` 直读 `ai_task` 状态（跨层依赖 `AiTaskMapper`）；
  7. `AiUsageLogMapper.pageUsageTasks` `LEFT JOIN ai_task` 强耦合旧表列。

**web（涉及约 75–80 文件：实现 ~52、测试 24）**

- 全仓无任何新设计代码（`proposals / afterSeq / proposals_ready / waiting_confirmation` 零命中）。
- 核心面：`types/ai.ts`（五态枚举 + 产物全族类型）、`services/ai.ts`（9 函数对应旧端点）、`stores/aiTask.ts`（`isTerminalTaskStatus` 单点定义被 trace / bug / admin 三域 7 处引用）、`AiTaskCenterPage` / `AiTaskDetailPage`（五态筛选、进度条、阶段时间线、取消 / 重试）、`AiArtifactReviewPanel`（666 行）、`GenerationArtifactTreeReview` / `SelectionReviewPanel` / `BugClassifyReviewPanel`、`useAiArtifactReview` 确认基座、`taskPresentation`（硬编码阶段表与五态徽标）、`AiTaskIndicator`。
- 孤立链路（无生产引用，仅 spec）：`AssistComparePanel` / `PlanOrderPanel` / `useAssistTask` / `assistPresentation`——按设计文档仍为辅助功能的目标 UI，随迁改造并接线（见 4 风险 R4）。

### 2.2 总体结构

```mermaid
flowchart LR
    subgraph 执行平面（编排归 AI）
        A[任务受理] --> B[Agent 执行核<br/>计划 · 白名单工具 · 预算护栏]
        B --> T[执行轨迹 ai_task_trace_event]
        B --> P[提案 ai_task_proposal]
    end
    subgraph 采纳平面（编排归应用层）
        P --> U[人工采纳 AiProposalReviewPanel]
        U --> D[类型适配器承接服务<br/>校验 + 权限 + 事务落库]
        D --> ADOPT[ai_task_adoption]
    end
```

- **切换策略（裁决）**：直接切换——本计划各步骤在同一版本内顺序落地，旧端点、旧枚举、旧组件随新路径上线即删除，不留运行时开关与兼容别名；回滚只靠 `git revert`。
- **数据策略（裁决）**：清空重建——删除 `ai_artifact_confirm`、重建 `ai_task` 并新建其余五表，无存量转换、无回填；破坏性仅作用于开发环境数据。

### 2.3 后端迁移

#### 2.3.1 新增文件

| 文件 | 职责 |
| ---- | ---- |
| `service/ai/task/core/AgentExecutionRunner` | 通用 Agent 循环驱动：计划快照入轨迹、白名单内工具调用（参数校验 → 权限与作用域鉴权 → 执行 → 结果回喂）、预算闸门、心跳 |
| `service/ai/task/core/TraceRecorder` | 轨迹事件写入（`plan / ai_decision / tool_call / tool_result / proposal_emitted / status_change`），只增不改 |
| `service/ai/task/core/BudgetGuard` | 预算签发（类型默认 + 请求下调）与四维（步数 / 时长 / token / 成本）超限判定 |
| `service/ai/task/type/TaskTypeAdapter` + `TaskTypeAdapterRegistry` | 新 SPI：input 校验、工具白名单与预算默认、提案结构、承接器注册（替代 `TaskHandler`） |
| `service/ai/task/type/*TypeAdapter`（15 个） | 各 type 改造落地（详见 2.3.2） |
| `model/entity/ai/AiTaskExecution` `AiTaskTraceEvent` `AiTaskProposal` `AiTaskAdoption` `AiTaskTool` + 各 Mapper | 新五表数据访问 |
| `controller/ai` 新 DTO 族 | `AiTaskExecutionRespDTO`（含 `plan / budget / consumed`）、`AiTaskTraceEventRespDTO`、`AiTaskProposal*`、`AiTaskAbortReqDTO` 等 |

#### 2.3.2 改造文件

| 文件 | 改动 |
| ---- | ---- |
| `AiTaskController` | 8 端点切换为：提交（创建即启动）、列表、running-count（`status = running`）、详情（`latestExecution + proposals + resultMeta`）、`GET …/executions/{executionId}/events`、`POST …/executions`（再次执行）、`POST …/executions/{executionId}/abort`、`GET …/proposals/{proposalKey}`、`POST …/proposals/confirm`；删除 cancel / retry / artifacts 三旧端点 |
| `AiTaskServiceImpl` | 重写为任务 · 执行 · 提案三资源服务：启动执行单入口（收敛双触发）、waitSeconds 同步等待语义保留、逐项采纳事务改写 `ai_task_adoption`、确认门禁改 `proposals_ready` |
| `AiTaskExecutor` → `AgentExecutionRunner` | CAS 状态机改新状态机（执行 `running / waiting_confirmation / succeeded / aborted`，任务 `running / proposals_ready / closed / aborted`）；进度上报改轨迹事件；模型选型（`requiredCapability`）迁至类型适配器声明并复用 `AiSettingsReader` |
| `AiTaskSweeper` | 扫描 `status + heartbeat_at`（执行表），超时置 `aborted`（1000018117）；总开关关闭 `failActive` → `abortActive` |
| `ArtifactAdopter` → `ProposalAdopter`（SPI 更名） | `AdoptContext` 产物字段改提案（`proposalKey`）、回写 `ai_task_adoption.adopted_ref`；14 个承接实现逐一对齐（`GenerationAdoptServiceImpl` 双角色拆解为纯承接服务） |
| 15 个旧 Handler → TypeAdapter | `TestDesignGeneration / ReviewSelection / PlanSelection / PlanOrder / CaseComplete / CasePriority / RequirementImport / BugClassify / BugTriage / BugTrendSummary / BugDuplicateScan / CoverageAnalysis / ImpactAnalysis / VectorReindex` 逐个改造：去 `context.report` 进度上报、产出改提案行、只读结论类（coverage / impact / trend / triage）执行完成即任务 `closed`；`AssistantParse` 最小适配为 TypeAdapter（执行语义仍为对话式解析，不进 Agent 循环，助手链路重构归 05 计划） |
| `Constants` | `AiTaskStatus` 五态 → 新任务 / 执行双状态机；`AiArtifactKind` → `AiProposalKind`（**kind 取值保持不变**，避免提示词与提案结构连锁变更）；`AiArtifactAction` → `AiProposalAction`（取值不变） |
| `ErrorCodeConstants` | 112 / 113 文案改提案；登记空号 1000018125（执行不存在）/ 126（预算非法）/ 127（执行已终止 / 预算耗尽）；111 语义扩展至执行态 |
| `RequirementServiceImpl` 等跨层点 | 删除对 `AiTaskMapper` 的直读，导入进行中判定改经任务服务新状态口径；`ImpactAnalysisTaskPublisher` 依赖新提交入口 |
| `AiUsageLogMapper` / `AiUsageAdminService` | 用量下钻 `LEFT JOIN` 改挂最新执行（modelName / scene 归因），明细写入补 `execution_id` |

#### 2.3.3 数据库变更（C5，清空破坏性重建）

- **迁移方式**：不建增量迁移脚本，六表重建 DDL 直接并入 `server/src/main/resources/db/schema.sql`（唯一事实源）；存量库人工执行，流程见 `docs/00-spec/30-quality-delivery/04-deployment-runbook.md` §10。
- **DDL**（引总册 2.6–2.7，此处仅列动作）：`DROP TABLE ai_artifact_confirm`；`DROP TABLE ai_task`；按总册新建 `ai_task / ai_task_execution / ai_task_trace_event / ai_task_proposal / ai_task_adoption / ai_task_tool` 六表及索引。
- **种子**：`ai_task_tool` 按总册 3.6.1 十五个 type 登记工具白名单与预算默认值（代码注册 + `schema.sql` 种子）；`ai_config` 的 `task_timeout_seconds / task_max_retries` 保留（语义改为心跳超时与模型调用自动短重试）。
- **数据回填**：无（清空重建，裁决记录于本计划）；`requirement_split_record.ai_task_id`、`trace_coverage_result.analyzed_task_id` 逻辑外键指向重建后的 `ai_task`，旧环境数据随清空失效。
- **锁表与发布窗口**：开发环境无窗口要求；生产化发布前须重新评估（见 4 风险 R1）。

### 2.4 前端迁移

| 层 | 改动 |
| ---- | ---- |
| `types/ai.ts` | 全量重写：新任务 / 执行双状态机、`AiTaskExecutionItem`（plan / budget / consumed）、`AiTaskTraceEvent`、`AiTaskProposalSummary`（`artifacts` → `proposals`）、`AiTaskProposalPayload`（`proposalKey`）；保留各域提案内容类型 |
| `services/ai.ts` | 端点切换：`abortAiExecution / reExecuteAiTask / fetchAiTaskEvents / fetchAiProposal / confirmAiProposals`；删除 `cancelAiTask / retryAiTask / fetchAiArtifact / confirmAiArtifacts` |
| `stores/aiTask.ts` | 轮询终态改 `aborted / closed`；新增轨迹增量拉取（`afterSeq` 游标 + WS 合并预留）；running-count 语义改 `status = running` |
| `AiTaskCenterPage` | 状态筛选改四态，进度 / 阶段列改执行摘要（步数与最近动作），操作改终止 / 再次执行 |
| `AiTaskDetailPage` | 进度区改 `ExecutionPanel`（步数与预算消耗、终止原因）、阶段时间线改 `PlanTimeline`（`plan` 快照驱动）、新增 `TraceEventList`、审核区改 `proposals_ready` 门禁 |
| `AiArtifactReviewPanel` → `AiProposalReviewPanel` | 文件与组件更名，props / 行模型改提案，确认调用改 `confirmAiProposals` |
| `GenerationArtifactTreeReview` / `SelectionReviewPanel` / `BugClassifyReviewPanel` | 类型与基座切换（保留组件名，符合各域详设文件清单） |
| `useAiArtifactReview` → `useAiProposalReview` | 基座更名改造：`syncRows` 按提案 `confirmStatus`、内容拉取改提案详情、分批确认改新端点、回执语义不变 |
| `taskPresentation.ts` | 删除五态徽标表与硬编码阶段表，改新状态机徽标 + 计划步骤视图模型 |
| `useAiTaskCenter / useAiTaskDetail` | 取消 / 重试动作改终止 / 再次执行（再次执行沿用同一任务引用）；轮询与审核区门禁改新状态 |
| 孤立链路四件 | `AssistComparePanel / PlanOrderPanel / useAssistTask / assistPresentation` 按新口径改造并接回生产路由（产品可裁剪，见 R4） |
| `AiTaskIndicator` | 计数注释与兜底口径改 `running`；删除「后端置 failed」注释 |
| 跨域消费方 | `useTraceCoverage / useTraceImpact`（retry → 再次执行、进度条改执行状态与轨迹）、`bugAnalysis` store 与 `useBugCreateSuggest / useBugBatchActions / useBugClassifyReview`（succeeded / failed 分支改新状态）、`AiSettingsPage`（reindex 分支改 `closed / aborted`）、`AiUsageGroup`（任务详情路由名不变，状态徽标更新） |

### 2.5 OpenAPI 与契约

- springdoc 随端点切换重生成，分组 `ai`；`waitSeconds` 与轨迹游标参数单独标注。
- `web/src/types/generated/contract.d.ts` 随后端构建重导出，删除旧 `retry / cancel / artifacts` 契约段（1687–2875、4672–4689、12685 等旧行号段整体消失）。

### 2.6 测试策略（C8 ≥ 70%）

- 后端：新增 `AgentExecutionRunner / BudgetGuard / TraceRecorder / TaskTypeAdapterRegistry` 单测；`AiTaskServiceImplTest` 按新状态机与端点重写；15 个 TypeAdapter 测试逐个改造（提案结构断言替换 artifacts 断言）；清扫器改心跳口径测试；采纳事务改 `ai_task_adoption` 幂等测试；`AiPromptAdminServiceTest / AiSettingsReaderTest` 随 SPI 与提示词单轨化调整。
- 前端：24 个既有 spec 按新状态机 / 新端点 / 新组件名重写；新增 `AiProposalReviewPanel` 与页面级 spec（现为测试缺口）。

### 2.7 旧代码深度清理方案

> **边界**：只清理「新路径上线后成为死代码、死配置、死契约」的存量；助手域工具注册表、接口测试域同名 `TaskHandler`、用量记账等共用能力不在清理范围。清理随各步骤提交完成，**不留运行时兜底开关**。

**层 A：后端代码（随包删除 / 改写，归属 S2–S4）**

| 清理对象 | 处置 | 归属 |
| ---- | ---- | ---- |
| `TaskHandler` SPI、`TaskHandlerRegistry`、15 个旧 Handler | 删除 SPI 与 Registry；Handler 改造为 TypeAdapter 后更名（`*Handler` → `*TypeAdapter`），不留双接口 | S4 |
| `AiTaskExecutor`（CAS + 进度上报） | 删除，由 `AgentExecutionRunner` 替代 | S2 |
| `AiTaskMapper` 内联状态字面量与 `casStart / casSucceed / casFail / casCancel / reportProgress / failActive / failStale` | 删除重写：新状态机更新路径 + 执行表心跳扫描；状态常量单点收口至 `Constants` | S1–S2 |
| `AiArtifactConfirm` 实体 / Mapper / 全部引用（`artifactKey` 链） | 删除，由 `AiTaskAdoption`（`proposalKey`）替代 | S3 |
| `/{taskId}/cancel`、`/{taskId}/retry`、`/{taskId}/artifacts/**` 端点与 `retryOfTaskId` 字段 | 删除；对应 DTO 字段（`progress / phase / result / artifacts / retryOfTaskId`）随 DTO 重写清除 | S3 |
| `GenerationAdoptService` 死接口 | 直接删除（零调用方） | S4 |
| 6 个 type 的死路确认路径（有 Handler 无 Adopter 的 confirm 分支） | 随确认门禁改 `proposals_ready` 自然失效，删除对应 1000018114 兜底分支中属于确认端点的部分 | S3 |
| `startTask()` 双触发（直调 + afterCommit） | 收敛为单入口：创建即启动，事务提交后触发一次，删除双保险分支 | S3 |
| 提示词双轨（`AiPromptAdminService.builtinContent` 回落链 / `AiPromptScenes` / `Handler.defaultPrompt()`） | 单轨化：默认提示词唯一登记点收敛至 TypeAdapter 声明，删除重复登记 | S4 |
| `RequirementServiceImpl` 直读 `AiTaskMapper`（跨层） | 删除直读，改经任务服务查询新状态 | S3 |
| `Constants` 旧五态、`AiArtifactKind / AiArtifactAction` 命名 | 更名并删除旧常量；kind / action **取值不变** | S1 |

**层 B：前端代码（归属 S5）**

| 清理对象 | 处置 |
| ---- | ---- |
| `AiTaskStatus` 五态枚举、`isTerminalTaskStatus` 旧判定（7 处引用） | 重写为新状态机工具函数，全量替换引用 |
| `progress / phase / retryOfTaskId / result` 类型与使用点（taskPresentation 阶段表、两页面进度区、trace 两面板进度条、admin reindex 分支） | 随类型重写清除，替代为 `plan / steps / consumed / terminateReason` |
| `AiArtifact*` 类型族与 `artifacts` 使用面（20+ 文件） | 更名 `AiTaskProposal*`，端点函数与 props 同步替换 |
| `cancelAiTask / retryAiTask / fetchAiArtifact / confirmAiArtifacts` | 删除，替代函数见 2.4 |
| `AiConfirmReceipt` 单项「重试」 | 保留语义（采纳回执单项重试，非任务重试），仅类型随基座更新 |
| 合同生成物 | `contract.d.ts` 重生成，旧契约段消失即视为清除 |

**层 C：数据与配置（归属 S1）**

1. 旧表：`DROP ai_artifact_confirm`、`DROP ai_task`，新六表重建（2.3.3）；`schema.sql` 与列 COMMENT 全量同步，删除 `progress / phase / result / retry_of_task_id / error_code / error_msg` 旧列注释。
2. `ai_config.task_timeout_seconds / task_max_retries` 保留，注释改心跳超时与模型自动重试口径；`schema.sql` 补 `ai_task_tool` 种子并核对 type 覆盖 15/15。
3. 错误码空号启用：1000018125 / 126 / 127 登记（总册 6 已定义）。

**层 D：测试（归属 S2–S5 同步提交）**

- server 31 个、web 24 个旧测试类：改造随实现同步提交，**禁止保留新旧双份断言**；旧字面量（`"pending" / "succeeded" / "cancelled"`、`artifactKey`）随重写清零。

**层 E：无须清理（明确保留）**

- 助手域 `AssistantToolRegistry / AssistantTool*` 工具注册表（助手自有，与 `ai_task_tool` 无关）；
- 接口测试域 `TestPlanTaskHandler / ApiSyncTaskHandler / GitLabSyncTaskHandler`（`DispatchableTask` 族，同名不同族）；
- 用量记账 `AiUsageLog` 族与 `AiSettingsReader.resolveModel` 能力选型（新执行核复用）；
- `RequirementSplitRecord.status = 'pending'` 等需求域自有状态机（与任务状态同名不同义）。

**清理门禁（2.7-Grep，S6 收口全绿，命令为示意，排除集以实际目录为准）**

```bash
# server（排除 apitest 域同名类与测试资源）
grep -rn --include=*.java -E "TaskHandler|AiTaskExecutor|AiArtifactConfirm|artifactKey|retryOfTaskId|casStart|casSucceed|casFail|casCancel|reportProgress|failStale" server/src/main | grep -v "apitest"   # 期望 0 命中
grep -rn --include=*.java -E "\"pending\"|\"succeeded\"|\"failed\"|\"cancelled\"" server/src/main/java/io/github/xiaomisum/robotest/{service/ai,repository/ai,framework/common}    # AI 语境期望 0 命中
grep -rn --include=*.java -E "artifacts/confirm|/cancel|/retry" server/src/main/java/io/github/xiaomisum/robotest/controller/ai    # 期望 0 命中

# web（排除接口测试 / 评审计划等无关域）
grep -rn --include=*.ts --include=*.vue -E "AiArtifact|cancelAiTask|retryAiTask|fetchAiArtifact|isTerminalTaskStatus\(.*succeeded" web/src    # 期望 0 命中
grep -rn --include=*.ts --include=*.vue -E "\bphase\b|\bprogress\b" web/src/stores/aiTask.ts web/src/pages/project/ai web/src/composables/project/ai    # AI 任务域期望 0 命中

# DDL 残留
grep -rn "ai_artifact_confirm\|retry_of_task_id" server/src/main/resources    # 期望 0 命中
```

### 2.8 在途计划联动

- **vision 计划的「Agent 执行核承接注」**：`docs/06-implementation-plan/04-ai-vision-capability-plan.md` 现状分析段有两处承接注记——`:52`（「现状代码仍为任务失败态，随 AI Task 迁移对齐」）与 `:125`（「`AiTaskExecutor` 用 `resolveModel(modelId, capability)` 复验……AI Task 迁移后由 Agent 执行核承接」）。其语义为：vision 在途计划引用的**执行期模型能力复验**职责目前长在旧执行引擎 `AiTaskExecutor` 上，而旧引擎是本计划 2.7 层 A 的删除对象，故以注记声明该职责随迁移平移，防止在旧类上扩展注定被删的死路径。承接落点即 S2：`AgentExecutionRunner` 在循环调用模型前复用 `AiSettingsReader.resolveModel` 完成执行期复验，`requiredCapability` 声明同步由旧 `TaskHandler` SPI 迁至类型适配器（与 vision 计划 `:115` 的 TypeAdapter 改写同一逻辑）。**无额外动作**：vision 工作包先落地则随 S2 平移，迁移先完成则 vision 直接在执行核上实现；S2 收口后删除 vision 计划两处注记。
- **助手计划的 SPI 耦合**：`assistant_parse` 依赖的 `TaskHandler` SPI 随 S4 删除，最小适配让位 `docs/06-implementation-plan/05-ai-assistant-tool-invocation-plan.md` 的 WP-6.3（详见 4 风险 R2）。

## 3. 开发计划

### 3.1 实施步骤

| 步骤 | 内容 | 端 | 依赖 |
| ---- | ---- | ---- | ---- |
| S0 | 基线登记：本计划入索引、裁决（直接切换 + 清空重建）落档、清理门禁脚本草稿 | 文档 | — |
| S1 | 数据层重建：迁移脚本 + schema.sql、新五实体与 Mapper、`AiTask` 实体重写、`Constants` 新状态机、`ai_task_tool` 种子、错误码 125–127 | 后端 | S0 |
| S2 | Agent 执行核：`AgentExecutionRunner / BudgetGuard / TraceRecorder`、心跳清扫、总开关 abortActive、用量 execution_id 归因 | 后端 | S1 |
| S3 | 任务资源切换：Controller / DTO / Service 重写（新端点集）、提案确认事务、`ProposalAdopter` SPI 与 14 承接实现换表、旧端点删除、`RequirementServiceImpl` 解耦 | 后端 | S2 |
| S4 | 类型适配器改造：15 个 TypeAdapter 落地（提案结构、轨迹事件、预算默认、提示词单轨）、死接口与死路确认分支删除 | 后端 | S3 |
| S5 | 前端切换：types / services / store / 两页面 / 面板更名 / 基座 composable / 孤立链路四件 / 指示器 / trace、bug、admin 跨域消费方 | 前端 | S3（契约） |
| S6 | 契约与收口：OpenAPI 重生成、contract.d.ts 重导出、清理门禁 2.7-Grep 全绿、双端测试与覆盖率门禁、主计划 02 状态联动回填 | 全 | S4、S5 |

### 3.2 提交拆分（C7，一个提交只做一件事）

| 序 | 提交信息 | 覆盖步骤 |
| ---- | ---- | ---- |
| 1 | `📝 docs(ai): 新增 AI Task 迁移计划并登记索引` | S0 |
| 2 | `✨ feat(ai): AI Task 数据层清空重建——六表 DDL、新实体与工具种子、错误码 125–127` | S1 |
| 3 | `✨ feat(ai): Agent 执行核落地——循环驱动、预算护栏、轨迹记录与心跳清扫` | S2 |
| 4 | `✨ feat(ai): 任务资源切换——任务/执行/轨迹/提案端点与采纳事务，删除 cancel/retry/确认旧路径` | S3 |
| 5 | `✨ feat(ai): 十五个任务类型适配器改造——提案结构与轨迹上报，删除 TaskHandler SPI 与死代码` | S4 |
| 6 | `✨ feat(ai): 前端任务框架切换——新状态机、执行区与轨迹回看、提案审核面板` | S5（类型与核心） |
| 7 | `✨ feat(ai): 前端跨域消费方切换——trace/bug/admin 任务态与再次执行` | S5（跨域） |
| 8 | `🔧 chore(ai): OpenAPI 与前端契约重生成` | S6 |
| 9 | `✅ test(ai): 迁移测试收口——旧字面量与旧组件引用清零断言` | S6 |

### 3.3 实施顺序与门禁

- **顺序**：S0 → S1 → S2 → S3 →（S4 ∥ S5-核心）→ S5-跨域 → S6；挂入主计划 `docs/06-implementation-plan/02-ai-requirement-development-plan.md` 批次三之后、WP-9.1 收口之前，与 WP-6.3（助手）互不阻塞但须在 S4 前确认 `assistant_parse` 适配口径。
- **门禁**：每提交跑本端脚本至 EXIT=0（后端 `node scripts/validate.mjs --backend`、前端 `--frontend`、文档 `--docs`；C7 / C8 / C1 随行）；S6 完成后双端全量 + 文档脚本，并执行 2.7 清理门禁 grep 清单全绿。
- **人工验收重点**（交付时逐项向用户确认并回填）：
  1. 任务中心：四态筛选、终止（二次确认）、再次执行沿用同一任务；
  2. 任务详情：预算消耗与计划步骤展示、轨迹逐事件回看、`proposals_ready` 审核区门禁；
  3. 提案采纳：单条 / 整树 / 编辑后采纳 / 驳回回执，采纳即落库建边，全驳回「未创建任何数据」；
  4. 挂起闸门：确认型工具触发人工批准后续跑（如已注册）；
  5. 总开关关闭：进行中执行批量终止（aborted），提案与轨迹保留可查；
  6. 清理门禁 grep 全绿 + 旧端点 404；
  7. 破坏性重建确认：开发环境旧任务历史清空符合预期。

## 4. 风险与应对

| 风险 | 影响 | 应对 |
| ---- | ---- | ---- |
| R1 清空重建不可回滚 | 旧任务 / 采纳历史永久丢失 | 裁决已确认开发环境可清空；生产化前重评估本节并补数据迁移方案 |
| R2 `assistant_parse` 与助手重构（WP-6.3）时序耦合 | 两侧同改 `AssistantParseHandler` 易冲突 | S4 前与 05 计划对齐：本计划仅做 SPI 最小适配，语义重构让位 WP-6.3 |
| R3 状态字面量三套 + 前端 7 处 `isTerminalTaskStatus` 引用 | 改漏即运行时分支错乱 | 以门禁 grep（2.7）兜底；状态常量单点收口后全量替换 |
| R4 孤立链路四件（无生产引用） | 改造投入可能白费 | 随迁改造并接线；S5 收口前与产品确认保留 / 裁剪，裁剪则整链删除 |
| R5 `AiUsageLogMapper` join 旧表 | 换表即破用量下钻 | S2 同步改 join 最新执行并补 `execution_id` 写入，用量页回归列入验收 |
| R6 前端测试面大（24 spec） | 覆盖率门禁（C8）波动 | 测试重写随各提交同步，禁止堆积至 S6 |
| R7 无提案任务（`vector_reindex` 等）的 `closed` 语义 | 前端刷新时机误判 | S5 明确「执行完成即 closed、以详情刷新为准」，任务详情页验收覆盖 |

## 5. 计划维护

### 5.1 实施状态

| 步骤 | 状态 | 备注 |
| ---- | ---- | ---- |
| S0 | ❌ | — |
| S1 | ❌ | — |
| S2 | ❌ | — |
| S3 | ❌ | — |
| S4 | ❌ | — |
| S5 | ❌ | — |
| S6 | ❌ | — |

- 每步骤收口：状态改 ✅ 并登记提交 hash；整包收口同步回填主计划 `docs/06-implementation-plan/02-ai-requirement-development-plan.md` 3.6 中 WP-4.2 / WP-4.4 / WP-5.1 等状态行（其含义变更为「新架构对齐后复验」），验收结果按主计划 3.4 第 4 条回填。
- 只维护步骤与依赖，不登记人天与日历排期。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-16 | 初始版本：直接切换 + 清空重建裁决、S0–S6 实施步骤与提交拆分、旧代码五层深度清理方案与 grep 门禁 |
| V1.0 | 2026-10-16 | 新增 2.8 在途计划联动：展开 vision 计划「Agent 执行核承接注」语义（旧 `AiTaskExecutor` 执行期模型能力复验随 S2 平移至 `AgentExecutionRunner`，收口后删除注记）与助手 SPI 耦合指向 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消，相关任务迁移标注「已取消——追溯矩阵需求整体下线」：1.3 基线文档移除追溯详设，2.1 死路确认路径中两类 type 标注取消，风险 R7 收敛为无提案任务通用口径 |
| V1.0 | 2026-10-18 | 2.3.3 数据库变更改为 `schema.sql` 唯一事实源口径：不建增量迁移脚本，`ai_task_tool` 种子并入 schema.sql，移除 `seed-ai.sql` 引用 |
