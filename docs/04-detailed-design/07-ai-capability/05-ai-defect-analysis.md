# 软件测试平台——缺陷分析

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**缺陷分析**能力域进行详细设计：定义趋势与质量度量查询接口、AI 摘要与批量分析任务、新建建议、分诊队列与重复缺陷检测的接口与规则。任务提交、产物确认协议与错误码分段见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档只定义缺陷域的业务入口与产物语义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/05-srs-ai-defect-analysis.md`（US-AI-014 ~ 016）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`；缺陷实体与状态流转沿用 `docs/01-requirements/04-bug-management/02-srs-bug-management.md`。
- 权限：拥有既有**缺陷查看权限**的用户可见分析入口与可发起分析任务；采纳更新字段需既有**缺陷编辑权限**；范围限于当前项目。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（3.6 任务资源、6 错误码分段）
- 缺陷管理详细设计：`docs/04-detailed-design/04-bug-management/02-project-workspace-bug.md`（缺陷列表筛选与字段口径）

---

## 2. 数据设计

本分册**不新增表**：

- 分析任务与产物：`ai_task`（`type = bug_classify / bug_triage / bug_duplicate_scan / bug_trend_summary`，产物在 `result`）、确认记录 `ai_artifact_confirm`；
- 录入时相似检测为**同步向量检索**，读 `ai_vector_index`（`entity_type = bug`，DDL 见总册 2.10），不产生任务；
- 趋势与度量为实时聚合查询，**不落中间表、不缓存过期结论**。

---

## 3. 接口详细设计

### 3.1 通用约定

- 查询类入口走缺陷业务路径 `/api/project/bugs/analysis/*`（`X-Active-Workspace` + `X-Active-Project` 头，权限：既有 `bug:view`）；
- AI 摘要与批量分析为任务，统一 `POST /api/ai/tasks` 提交（本域 `type` 与资源权限映射见 4.2），结果经总册 3.6.3 查询；
- 响应 `Result<T>`，示例**仅展示 `data`**；分页同总册。

### 3.2 趋势查询

- **路径**：`GET /api/project/bugs/analysis/trends`
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| from / to | date | 否 | 时间范围，默认最近 30 天（否则 1000018281） |
| groupBy | string | 否 | `none`（默认）/ `module` / `severity` / `type` —— 分组对比维度 |

- **响应**：

```json
{
  "axis": ["2026-10-01", "2026-10-02"],
  "series": [
    { "key": "all", "label": "全部", "created": [6, 4], "closed": [3, 5], "active": [21, 20] }
  ],
  "groupBy": "module"
}
```

- **校验规则**：口径与缺陷管理分册的状态定义一致（新增 = 创建时间落区间，关闭 = 关闭时间落区间，存量 = 区间末激活数）；`groupBy` 非法返回 1000018286；数据实时计算，前端点击数据点下钻复用缺陷列表既有筛选（跳转带参）。

### 3.3 质量度量查询

- **路径**：`GET /api/project/bugs/analysis/metrics`
- **参数**：`from`、`to`（同 3.2）。
- **响应**：

```json
{
  "fixDuration": { "avgHours": 14.2, "p50Hours": 8.5, "p90Hours": 41.0, "sample": 96 },
  "reopenRate": 0.06,
  "duplicateRate": 0.04,
  "severityDist": [{ "key": "high", "count": 12 }],
  "moduleDist": [{ "key": "登录模块", "count": 18 }],
  "typeDist": [{ "key": "functional", "count": 30 }]
}
```

- **校验规则**：修复时长 = 激活 → 已修复的首条有效流转（同缺陷多次流转取首次）；重开率 = 激活次数 ≥ 1 的缺陷占比；重复缺陷占比 = 解决方案为「重复缺陷」的占比；分母为 0 时比率返回 0（前端不显示除零）。

### 3.4 AI 摘要（任务）

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "bug_trend_summary",
  "input": { "from": "2026-09-01", "to": "2026-10-02", "groupBy": "module" },
  "waitSeconds": 10
}
```

- **产物**（`kind = summary`，**只读**，`confirmStatus = not_applicable`，无确认动作）：

```json
{
  "key": "summary-1",
  "kind": "summary",
  "title": "缺陷趋势摘要",
  "content": {
    "text": "本期新增 42 个缺陷，环比 +18%；登录模块缺陷集中（18 个）…重开率环比突增…",
    "citations": [{ "type": "bug", "id": "…", "title": "BUG-017" }]
  },
  "confirmStatus": "not_applicable"
}
```

- **校验规则**：摘要为只读建议，不写入任何数据；必附数据来源引用（缺引用的产物为不合格产出，不进入结果）；生成失败走总册任务失败与重试（1000018117）。

### 3.5 新建缺陷建议（同步快路径）

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "bug_classify",
  "input": { "draft": { "title": "登录页点击登录无响应", "steps": "1. 打开登录页…", "moduleId": "…" } },
  "waitSeconds": 10
}
```

- **产物**（`kind = classify_suggestion`，单条）：

```json
{
  "key": "draft",
  "kind": "classify_suggestion",
  "title": "新建缺陷建议",
  "content": {
    "suggestions": {
      "bugType": { "value": "functional", "reason": "重现步骤描述功能失效" },
      "severity": { "value": "high", "reason": "阻断主流程" },
      "priority": { "value": "high", "reason": "…" },
      "moduleId": { "value": "…", "reason": "…" },
      "keywords": { "value": ["登录", "无响应"], "reason": "…" }
    },
    "assigneeCandidates": [{ "userId": "…", "name": "李四", "reason": "同模块历史缺陷处理人", "memberValid": true }],
    "sourceRefs": [{ "type": "bug", "id": "…", "title": "BUG-017", "quote": "…" }]
  },
  "confirmStatus": "not_applicable"
}
```

- **校验规则**：建议值仅在表单中以标记呈现，用户一键采纳或修改后提交，**用户填写值与建议不一致时以用户值为准**；`assigneeCandidates` 服务端过滤，仅保留当前工作空间成员（否则剔除，候选为空不报错）；表单场景产物不落库，`confirmStatus = not_applicable`。

### 3.6 存量批量分类（任务）

- **提交**：`POST /api/ai/tasks`

```json
{ "type": "bug_classify", "input": { "bugIds": ["…"] }, "waitSeconds": 0 }
```

  - 也可传筛选条件（`input.filter`：未分类 / 分类存疑的状态条件），服务端解析为缺陷集合（上限 500，空集返回 1000018283）。
- **产物**：逐缺陷一条 `classify_suggestion`（结构同 3.5，`content.bugId` 指向目标，`confirmStatus = pending`）。
- **确认**：总册 3.6.5，动作 `adopted / adopted_edited / rejected`；承接服务**更新对应缺陷字段**（类型、严重等级、优先级、模块、关键词、指派），逐项事务；采纳写 `adopted_ref`，需要既有 `bug:edit` 权限。

### 3.7 分诊队列建议（同步）

- **提交**：`POST /api/ai/tasks`

```json
{ "type": "bug_triage", "input": {}, "waitSeconds": 10 }
```

- **产物**（`kind = triage_order`，只读，`confirmStatus = not_applicable`）：

```json
{
  "key": "triage",
  "kind": "triage_order",
  "title": "分诊顺序建议",
  "content": {
    "items": [
      { "bugId": "…", "rank": 1, "reason": "高严重等级 + 存放 6 天 + 登录模块热点" }
    ],
    "scope": "active_unassigned"
  },
  "confirmStatus": "not_applicable"
}
```

- **校验规则**：输入范围固定为「激活未指派」缺陷；空集返回 1000018283；建议仅作排序展示，人工可自由调整，**不写入任何数据**。

### 3.8 录入时重复检测（同步向量检索，非任务）

- **路径**：`POST /api/project/bugs/duplicates/check`（权限：`bug:view`）
- **请求**：

```json
{ "title": "登录页点击登录无响应", "steps": "1. 打开登录页…", "limit": 5 }
```

- **响应**：

```json
{
  "list": [
    { "bugId": "…", "code": "BUG-017", "title": "登录按钮无反应", "status": "active", "similarity": 0.87, "basis": "标题与重现步骤语义相近" }
  ]
}
```

- **校验规则**：仅检索当前项目的 `entity_type = bug` 向量（先业务归属过滤再相似检索，总册 4.4）；相似度仅作排序，阈值不对外承诺；`title` 必填（否则 1000018284）；向量能力未就绪时返回 1000018258 口径错误（入口置灰）。用户确认为重复时，走既有「重复缺陷」解决方案提交（须指定原始缺陷）。

### 3.9 存量重复扫描（任务）

- **提交**：`POST /api/ai/tasks`

```json
{ "type": "bug_duplicate_scan", "input": { "scope": "active" }, "waitSeconds": 0 }
```

- **产物**（`kind = duplicate_group`，`confirmStatus = pending`）：

```json
{
  "key": "group-1",
  "kind": "duplicate_group",
  "title": "疑似重复分组（3 条）",
  "content": {
    "canonicalBugId": "…",
    "items": [{ "bugId": "…", "similarity": 0.83, "reason": "…" }]
  },
  "confirmStatus": "pending"
}
```

- **确认**：人工确认分组（`adopted` / `rejected`，`note` 记录结论）——承接服务**只写确认留痕，不修改任何缺陷**；确认后由人工在缺陷中按既有「重复缺陷」解决方案逐条处理；检测结果不自动合并、不改状态。

---

## 4. 业务逻辑设计

### 4.1 统计口径与实时性

- 全部统计按当前项目实时计算，时间字段取 UTC 日期；口径（状态定义、修复时长、重开、重复）与缺陷管理分册完全一致，每张图表标注口径与时间范围；
- AI 摘要基于同一次查询的数据快照生成，附来源引用；**摘要与建议均不写入业务数据**。

### 4.2 任务 type 与资源权限映射

| type | 资源权限（发起） | 资源权限（采纳） | 产物确认 |
| ---- | ---- | ---- | ---- |
| bug_classify（draft 单条） | 既有 `bug:view` | —（表单内采纳，不走确认端点） | `not_applicable` |
| bug_classify（批量） | 既有 `bug:view` | 既有 `bug:edit` | 逐条 `pending → adopted/rejected` |
| bug_triage | 既有 `bug:view` | — | `not_applicable` |
| bug_trend_summary | 既有 `bug:view` | — | `not_applicable` |
| bug_duplicate_scan | 既有 `bug:view` | —（人工走既有缺陷流程处理） | 分组 `adopted/rejected` 留痕 |

- 发起同时要求总册 `ai:task`、确认要求 `ai:confirm`；作用域一律取请求头 `X-Active-Project`。

### 4.3 批量任务的执行与确认

1. 批量分析按缺陷分片执行，进度 = 已处理 / 总数（总册 `progress / phase`）；
2. 确认走总册 3.6.5 逐项事务：采纳即按既有缺陷服务更新字段（同一事务写 `ai_artifact_confirm` + `adopted_ref`），失败项回滚、成功项保留；
3. AI 不自动改写任何已提交缺陷；采纳与驳回全部记审计；
4. 指派候选仅展示 `memberValid = true` 的当前空间成员。

---

## 5. 前端设计

### 5.1 路由与页面

```
项目工作区
└── /workspace/projects/bugs/analysis   → BugAnalysisPage（缺陷分析页，或缺陷页内 Tab）
```

### 5.2 组件结构

```
BugAnalysisPage
├── AnalysisRangePicker（时间范围 + 分组维度，口径脚注）
├── TrendChart（新增/关闭/存量折线，数据点点击 → 缺陷列表下钻带参）
├── MetricCards（修复时长 p50/p90、重开率、重复占比、分布环图）
├── AiSummaryCard（生成/重新生成 + 引用链接 + 失败重试）
├── TriageQueueCard（分诊顺序建议列表，可拖拽调整，只读落库）
└── BatchClassifyEntry（批量分类发起 → 复用 AiArtifactReviewPanel 列表模式审核）

新建缺陷表单（既有 BugCreatePage 扩展）
├── SuggestBadge（建议值标记：类型/等级/优先级/模块/关键词，一键采纳）
├── AssigneeCandidateChips（指派候选，附理由）
└── DuplicateCheckPanel（录入检测结果：相似度排序列表 + 跳转）

存量扫描入口 → DuplicateScanPanel（分组审核，确认留痕后引导既有重复缺陷流程）
```

### 5.3 状态管理与状态分支

- Pinia store `bugAnalysis`：范围与分组条件、趋势/度量数据、摘要任务状态、批量任务引用（轮询复用 `aiTask` store）；
- 状态分支：范围无数据（空图表补 0）、摘要生成中/失败、批量任务进度与部分采纳回执、向量未就绪（检测入口置灰 + 提示）、权限不足（隐藏分析入口）。

---

## 6. 错误码定义

号段：**1000018281–1000018299（缺陷分析）**，以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018281 | 400 | 分析时间范围非法 |
| 1000018282 | 404 | 缺陷不存在 |
| 1000018283 | 400 | 分析输入为空（无可分析的缺陷） |
| 1000018284 | 400 | 检测输入非法（标题为空或超长） |
| 1000018285 | 400 | 分组参数非法（groupBy 取值越界） |
| 1000018286 | 400 | 重复扫描范围参数非法 |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../controller/project/BugAnalysisController` | 趋势 / 度量 / 录入检测路由（仅路由，C2） |
| `server/.../service/bug/analysis/BugAnalysisService + Impl` | 实时聚合与口径计算 |
| `server/.../service/ai/task/handler/BugClassifyHandler`、`BugTriageHandler`、`BugDuplicateScanHandler`、`BugTrendSummaryHandler` | 任务执行器 |
| `server/.../service/ai/task/adopt/BugAdoptService + Impl` | 批量分类采纳落库（调用既有缺陷服务） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018281–1000018286 |
| `web/src/pages/project/BugAnalysisPage.vue` 及组件、`web/src/stores/bugAnalysis.ts` | 分析页与状态 |
| `web/src/pages/project/BugCreatePage.vue` 扩展（建议标记、录入检测） | 新建页扩展 |

- **数据库**：无新增表、无迁移（统计实时计算，向量读既有 `ai_vector_index`）。
- **OpenAPI**：`duplicates/check` 标注同步检索；四个任务 type 的 `input / 产物` 结构随任务分组暴露。

**测试要点（C8 ≥ 70%）**

- 后端：趋势口径（新增/关闭/存量边界）、修复时长首次流转取值、除零补零、分组参数校验、draft 建议的成员过滤、批量采纳逐项事务与权限、录入检测项目内过滤、空集错误码；
- 前端：图表下钻带参、建议标记与用户值优先、摘要引用跳转、分诊拖拽不落库、批量审核回执、向量未就绪降级。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-02 | 前端路由对齐全局导航约定，改为 /workspace/projects/bugs/analysis |
