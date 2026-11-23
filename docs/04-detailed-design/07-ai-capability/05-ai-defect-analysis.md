# 软件测试平台——缺陷分析

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**缺陷分析**能力域进行详细设计：定义新建缺陷 AI 建议（含重复缺陷检测）的任务输入、提案结构与执行规则。任务资源、提案确认协议与错误码分段见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档只定义缺陷域的业务语义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/05-srs-ai-defect-analysis.md`（US-AI-015）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`；缺陷实体与状态流转沿用 `docs/01-requirements/04-bug-management/02-srs-bug-management.md`。
- 权限：免 RBAC 权限码校验（登录即可）；建议入口由前端按业务状态显隐，后端仅保留项目成员校验；范围限于当前项目。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（错误码分段）
- 缺陷管理详细设计：`docs/04-detailed-design/04-bug-management/02-project-workspace-bug.md`（缺陷列表筛选与字段口径）

---

## 2. 数据设计

本分册**不新增表**：

- 新建缺陷建议通过通用 AI 任务资源执行：`ai_task / ai_task_execution / ai_task_trace_event / ai_task_proposal`（`type = bug_draft_suggest`，`kind = bug_draft_suggestion`）；
- 建议提案为只读建议（`confirmStatus = not_applicable`），用户在提交缺陷页采纳时仅写入当前表单，不调用提案确认落库，不写入缺陷业务数据；
- 重复缺陷检测读 `ai_vector_index`（`entity_type = bug`，DDL 见总册 2.10），结果随建议提案内容返回，不单独落库。

---

## 3. 接口详细设计

### 3.1 通用约定

- 新建缺陷建议走通用 AI 任务资源 `/api/ai/tasks`（`X-Active-Workspace` + `X-Active-Project` 头，免 RBAC 权限码校验，保留项目成员校验）；重复缺陷检测并入同一任务，不提供独立路由；
- 响应 `Result<T>`，示例**仅展示 `data`**；任务提交、详情、提案详情与轮询语义沿用总册 3.6。

### 3.4 新建缺陷建议（含重复缺陷检测，AI 任务）

- **提交**：`POST /api/ai/tasks`
- **请求**：

```json
{
  "type": "bug_draft_suggest",
  "input": {
    "title": "登录页点击登录无响应",
    "steps": "1. 打开登录页…",
    "moduleId": "…"
  },
  "waitSeconds": 10
}
```

| input 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| title | string | 是 | 当前输入的缺陷标题（≤300，为空或超长按 1000018115 拒绝） |
| steps | string | 否 | 已填写的重现步骤（未改动默认模板时前端不传） |
| moduleId | string | 否 | 已选择的所属模块（须属于当前项目） |

- **任务语义**：`type = bug_draft_suggest`，提交后创建即启动执行；`waitSeconds` 允许短等待快路径，等待期内到达 `proposals_ready` 时响应直接携带提案摘要，否则返回运行中状态由前端轮询任务详情。
- **提案**（`kind = bug_draft_suggestion`，单条，`confirmStatus = not_applicable`）：

```json
{
  "key": "bug-draft-1",
  "kind": "bug_draft_suggestion",
  "parentKey": null,
  "title": "登录页点击登录无响应",
  "content": {
    "title": { "value": "登录页点击登录按钮后无任何响应", "reason": "…" },
    "reproSteps": { "value": "##### 执行步骤\n1. 打开登录页…\n##### 实际结果\n点击登录按钮后页面无任何响应\n##### 期望结果\n跳转首页并提示登录成功", "reason": "…" },
    "suggestions": {
      "bugType": { "value": "code_error", "reason": "重现步骤描述功能失效" },
      "severity": { "value": "serious", "reason": "阻断登录主流程" },
      "priority": { "value": "high", "reason": "…" },
      "moduleId": { "value": "…", "reason": "…" },
      "keywords": { "value": ["登录", "无响应"], "reason": "…" },
      "relatedCase": { "value": "…", "caseTitle": "登录-正常登录", "reason": "…" }
    },
    "duplicates": [
      { "bugId": "…", "title": "登录按钮无反应", "status": "active", "similarity": 0.87, "basis": "命中原文分块：登录页点击登录按钮后无任何响应…" }
    ]
  },
  "confirmStatus": "not_applicable"
}
```

- **校验规则**：`title` 必填且 ≤300（否则 1000018115）；`moduleId` 非空时须属于当前项目；任务类型、输入、模型、预算校验沿用总册 3.6.2。建议任务为纯文本调用，不随附截图。
- **上下文装配**：可用模块取当前项目模块清单（`id|名称`）；固定选项取缺陷类型 / 严重等级 / 优先级取值域（常量枚举）；候选用例先按标题向量检索项目内用例文档（top 8）再展开为用例节点清单（`id|标题`，≤30，按文档序截断），向量能力未就绪时无用例候选（不报错、不产出 `relatedCase`）。
- **重复缺陷检测**：同一任务执行内检索当前项目的 `entity_type = bug` 向量（先业务归属过滤再相似检索，总册 4.3），最多 5 条；先按相似度排序，再按缺陷去重并跳过已删除缺陷；`basis` 为命中原文分块（截断至 200 字符）。相似度仅作排序，阈值不对外承诺。向量能力未就绪或请求失败时 `duplicates` 为空且不作提示，字段建议照常返回。用户确认为重复时，走既有「重复缺陷」解决方案提交（须指定原始缺陷）。
- **清洗与采纳**：枚举值非法剔除，模块 id 不属当前项目、用例 id 非本项目用例节点剔除，`relatedCase` 至多一条。建议值在审核弹窗中展示，用户点击采纳后立即写入表单，**用户填写值与建议不一致时以用户值为准**；采纳不调用 `/api/ai/tasks/{taskId}/proposals/confirm`，不写入缺陷业务数据。

---

## 4. 业务逻辑设计

### 4.1 数据边界与实时性

- 建议与重复检索的范围限于当前项目，检索先做业务归属过滤；
- **建议与重复检测结果仅作参考，不写入业务数据**。

### 4.2 入口与作用域

- 新建缺陷建议（含重复缺陷检测）走通用 AI Task 任务资源：`type = bug_draft_suggest`，产出单条只读建议提案（`bug_draft_suggestion`），重复检测在任务执行中完成并写入提案 content；
- 免 RBAC 权限码校验（仅保留项目成员校验与任务可见性）；作用域一律取请求头 `X-Active-Project`，不从请求体信任项目范围。

---

## 5. 前端设计

### 5.1 路由与页面

```
项目工作区
└── /workspace/projects/bugs/create   → BugCreatePage（提交缺陷页，承载新建建议与重复缺陷检测）
```

### 5.2 组件结构

```
新建缺陷表单（既有 BugCreatePage 扩展）
├── 「AI 建议」入口按钮（标题输入框 append，点击开启审核弹窗并发起 AI 任务）
└── BugAiSuggestionDialog（AI 建议审核弹窗）
    ├── 任务状态区（提交中 / 运行中 / 失败 / 终止）
    ├── 字段建议项（优化后标题 / 重现步骤 / 类型 / 等级 / 优先级 / 模块 / 关联用例 / 关键词，展示建议值与理由，点击采纳立即写入表单）
    └── DuplicateSuggestList（疑似重复缺陷列表：短 ID / 标题 / 状态 / 相似依据，点击跳转；无命中不渲染）
```

### 5.3 状态管理与状态分支

- 新建表单建议与重复检测结果存提交缺陷页本地状态，弹窗关闭不强制清空已获取结果，提交或离开页面后释放；
- 状态分支：提交中 / 运行中（弹窗展示加载态）、建议就绪（展示提案）、无命中（不渲染列表）、向量未就绪（`duplicates` 为空、静默）、任务失败 / 终止或请求错误（弹窗内提示可重试，关闭后再次点击重新获取）、AI 未启用（入口隐藏）。

---

## 6. 错误码定义

号段：**1000018281–1000018299（缺陷分析）**，以 `ErrorCodeConstants` 实际登记为准，冲突时顺延；1000018281–1000018285、1000018287 已注销不复用。

本分册不新增缺陷分析专用业务错误码。新建缺陷建议任务的输入非法复用通用 AI 任务错误码 `1000018115`，模型/能力/预算/任务状态等错误沿用总册 3.6 与 6 的统一口径。

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../service/ai/task/type/BugDraftSuggestTypeAdapter.java` | 新增 `bug_draft_suggest` 任务类型：输入校验、上下文装配、重复检索、提示词、输出清洗与单条只读提案 |
| `server/.../framework/common/Constants.java` | 新增 `AiTaskType.BUG_DRAFT_SUGGEST` 与 `AiProposalKind.BUG_DRAFT_SUGGESTION` |
| `server/.../service/ai/task/AiTaskTitleResolver.java` | 补充 `bug_draft_suggest` 任务标题类型描述 |
| `server/.../controller/project/BugAnalysisController.java` | 移除建议 SSE 路由；若无剩余路由则删除控制器 |
| `server/.../service/domain/bug/*BugDraftSuggest*`、`BugSuggestStream*`、DTO | 删除旧独立建议 SSE 链路与专用 DTO |
| `server/.../service/domain/bug/BugAnalysisService + Impl` | 复用或收敛为重复检索支撑，避免 suggest-only 悬空接口 |
| `server/.../service/ai/config/AiPromptScenes.java` | 登记 `bug_draft_suggest` 任务场景变量，提示词由 TypeAdapter 单轨提供 |
| `server/.../framework/common/ErrorCodeConstants.java` | 注销 `BUG_ANALYSIS_SUGGEST_INPUT_INVALID`（1000018287） |
| `web/src/composables/project/bug/useBugCreateSuggest.ts` | 改为提交 AI 任务、等待/轮询、读取提案并暴露采纳动作 |
| `web/src/pages/project/bug/BugCreatePage.vue` | 新建页扩展（AI 建议入口、审核弹窗挂载、采纳写入表单） |
| `web/src/components/project/bug/BugAiSuggestionDialog.vue` | AI 建议审核弹窗（任务状态、字段建议、疑似重复缺陷、逐项采纳） |
| `web/src/services/project/bug.ts`、`web/src/types/project/bugAnalysis.ts`、`web/src/types/ai.ts` | 移除旧 SSE 建议接口，补充任务提案内容类型 |

- **数据库**：无新增表、无迁移（向量读既有 `ai_vector_index`）。
- **OpenAPI**：移除 `/api/project/bugs/analysis/suggest`；AI 任务接口沿用既有 `/api/ai/tasks` 契约。

**测试要点（C8 ≥ 70%）**

- 后端：`bug_draft_suggest` 输入校验（1000018115 / 项目上下文）、TypeAdapter 提示词与输出清洗、重复检索的项目内过滤 / 去重 / 跳过已删除 / 向量未就绪降级、提案为单条 `not_applicable`、旧 suggest 路由/DTO/错误码清理无残留；
- 前端：提交任务快路径与轮询分支、任务失败/终止提示、审核弹窗展示、建议项逐项采纳立即写入、疑似重复缺陷列表渲染与跳转、无命中与向量未就绪静默。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-02 | 前端路由对齐全局导航约定，改为 /workspace/projects/bugs/analysis |
| V1.0 | 2026-10-03 | 批量分类发起后跳转任务详情页，审核在详情页审核区（列表模式）完成 |
| V1.0 | 2026-10-08 | 勘误与口径补齐：3.2 `groupBy` 非法对齐错误码表改为 1000018285（1000018286 归重复扫描范围）；3.3 补比率与分布分母、修复时长样本与激活时刻、逐日存量口径；7 实施文件路径随仓库分层改为 `service/domain/bug/` |
| V1.0 | 2026-10-08 | 3.8 重复检测响应移除无数据来源的 `code` 字段、`basis` 改为命中原文分块，3.4 / 3.5 引用示例 `title` 改用缺陷标题 |
| V1.0 | 2026-10-16 | vision 能力落地：`bug_classify` 接入截图入模（3.5 draft 补 `fileIds` ≤3 张、3.6 批量每缺陷 ≤2 张 / 总量 ≤8 截断），选型与降级走总册 4.2 与 1000018124 |
| V1.0 | 2026-10-16 | 同步 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`）：2 节数据设计改按 `ai_task_` 域六表拆分；产物改称提案；执行进度改为步数与轨迹口径；失败改「终止（1000018117）+ 再次执行」；确认事务承接表改 `ai_task_adoption`；7 节执行器改类型适配器 |
| V1.0 | 2026-10-16 | 术语与状态口径残词勘误：5.3 状态分支改「批量任务执行与部分采纳回执」 |
| V1.0 | 2026-10-19 | 移除任务权限校验：1 概述与 3.1 入口改前端显隐口径，3.5 确认、4.2 改「任务 type 与提案确认映射」（删资源权限两列与 `ai:task` / `ai:confirm` 要求），7 测试要点同步 |
| V1.0 | 2026-10-19 | 3.8 录入重复检测改标题失焦触发 + 弹窗展示（组件 BugDuplicateDialog），不可用/无命中静默；5.2 组件树与 5.3 状态分支同步 |
| V1.0 | 2026-10-20 | 新建缺陷建议实时化：3.5 改实时同步接口 `POST /api/project/bugs/analysis/suggest`（新增标题 / 重现步骤 / 关联用例建议，移除指派候选与草稿截图入模，登记错误码 1000018287）；2 数据设计、3.6 提案结构去引用、4.2 映射、5.2 组件树、7 实施说明与测试要点同步 |
| V1.0 | 2026-10-20 | 3.5 新建建议不再检索相似存量缺陷（响应去 `sourceRefs`，相似性判断由 3.8 录入时重复检测承担），1 概述与校验规则同步 |
| V1.0 | 2026-10-20 | 3.5 传输通道改 SSE 流式（`text/event-stream`：`progress` / `done` / `error` 事件 + 15s 心跳，无客户端总超时；流建立前校验仍为同步 HTTP 错误），1 概述、3.x、5.1 接口清单、7 实施说明与测试要点同步 |
| V1.0 | 2026-11-18 | AI 能力域免 RBAC 权限码校验：1.2 / 3.1 / 3.5 / 3.8 / 4.2 / 5.3 移除 `ai:task` · `bug:view` 与权限拒绝 / 权限不足分支，改登录即可并保留任务归属可见性与项目成员校验 |
| V1.0 | 2026-11-21 | 移除趋势摘要与存量批量分类：删除 3.4 / 3.6 两节及对应任务类型、提案种类与采纳承接，后续小节顺延为 3.4–3.7，数据设计、接口约定、业务逻辑、前端组件与实施 / 测试要点同步收窄 |
| V1.0 | 2026-11-21 | 移除存量重复扫描任务：删除 3.7 节与 4.3 节，数据设计、接口约定、提案映射、前端组件与状态、错误码 1000018286、实施文件清单同步收窄，接口节保留 3.1–3.6 |
| V1.0 | 2026-11-23 | 重复缺陷检测并入新建缺陷建议：删除独立检测路由 3.6，建议 `done` 事件新增 `duplicates` 字段；前端改为建议区内列表展示，注销 1000018284，向量未就绪静默降级 |
| V1.0 | 2026-11-23 | 新建缺陷 AI 建议改为审核弹窗：点击开启弹窗审核 AI 产物，采纳后立即写入表单，前端组件结构同步调整 |
| V1.0 | 2026-11-23 | 新建缺陷 AI 建议接入通用 AI 任务资源：新增 `bug_draft_suggest` 任务与 `bug_draft_suggestion` 只读提案，移除独立建议 SSE 接口与 1000018287 |
