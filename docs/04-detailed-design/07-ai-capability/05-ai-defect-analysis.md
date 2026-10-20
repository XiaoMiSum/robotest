# 软件测试平台——缺陷分析

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**缺陷分析**能力域进行详细设计：定义趋势与质量度量查询接口、AI 摘要与批量分析任务、新建建议、分诊队列与重复缺陷检测的接口与规则。任务提交、提案确认协议与错误码分段见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档只定义缺陷域的业务入口与提案语义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/05-srs-ai-defect-analysis.md`（US-AI-014 ~ 016）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`；缺陷实体与状态流转沿用 `docs/01-requirements/04-bug-management/02-srs-bug-management.md`。
- 权限：分析入口与可发起任务由前端按既有**缺陷查看权限**与 `ai:task` 显隐；后端任务发起与采纳不做权限校验（仅保留任务归属可见性）；范围限于当前项目。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（3.6 任务资源、6 错误码分段）
- 缺陷管理详细设计：`docs/04-detailed-design/04-bug-management/02-project-workspace-bug.md`（缺陷列表筛选与字段口径）

---

## 2. 数据设计

本分册**不新增表**：

- 分析任务、执行与轨迹：`ai_task / ai_task_execution / ai_task_trace_event`（`type = bug_classify / bug_triage / bug_duplicate_scan / bug_trend_summary`）；
- 提案与采纳：`ai_task_proposal / ai_task_adoption`（提案 `kind`：`classify_suggestion / duplicate_group / triage_order / summary`）；
- 录入时相似检测为**同步向量检索**，读 `ai_vector_index`（`entity_type = bug`，DDL 见总册 2.10），不产生任务；
- 新建缺陷建议为**实时 SSE 流式接口**（单跳流式模型调用，向量检索仅取候选用例上下文），不产生任务、不落库；
- 趋势与度量为实时聚合查询，**不落中间表、不缓存过期结论**。

---

## 3. 接口详细设计

### 3.1 通用约定

- 查询类入口走缺陷业务路径 `/api/project/bugs/analysis/*`（`X-Active-Workspace` + `X-Active-Project` 头，权限：既有 `bug:view`）；
- AI 摘要与批量分析为任务，统一 `POST /api/ai/tasks` 提交（本域 `type` 与提案确认映射见 4.2），结果经总册 3.6.3 查询；
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

- **校验规则**：口径与缺陷管理分册的状态定义一致（新增 = 创建时间落区间，关闭 = 关闭时间落区间，存量 = 区间末激活数）；`groupBy` 非法返回 1000018285；数据实时计算，前端点击数据点下钻复用缺陷列表既有筛选（跳转带参）。

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

- **校验规则**：修复时长 = 激活 → 已修复的首条有效流转（同缺陷多次流转取首次）；重开率 = 激活次数 ≥ 1 的缺陷占比；重复缺陷占比 = 解决方案为「重复缺陷」的占比；分母为 0 时比率返回 0（前端不显示除零）。比率与三张分布的分母统一取**区间内新增缺陷**（创建时间落区间）；修复时长样本取修复时间落区间的缺陷，激活时刻取该次修复前最近一次激活日志（无则取创建时间）；`axis` 逐日存量 = 创建 ≤ 当日且（关闭时间为空或晚于当日）的缺陷数。

### 3.4 AI 摘要（任务）

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "bug_trend_summary",
  "input": { "from": "2026-09-01", "to": "2026-10-02", "groupBy": "module" },
  "waitSeconds": 10
}
```

- **提案**（`kind = summary`，**只读**，`confirmStatus = not_applicable`，无确认动作）：

```json
{
  "key": "summary-1",
  "kind": "summary",
  "title": "缺陷趋势摘要",
  "content": {
    "text": "本期新增 42 个缺陷，环比 +18%；登录模块缺陷集中（18 个）…重开率环比突增…",
    "citations": [{ "type": "bug", "id": "…", "title": "登录按钮无反应" }]
  },
  "confirmStatus": "not_applicable"
}
```

- **校验规则**：摘要为只读建议，不写入任何数据；必附数据来源引用（缺引用的提案为不合格产出，不进入结果）；执行失败按总册 3.6.4 终止（1000018117），可再次执行。

### 3.5 新建缺陷建议（实时 SSE 流式）

- **路径**：`POST /api/project/bugs/analysis/suggest`，`produces: text/event-stream`（权限：`bug:view`，项目上下文经 `X-Active-Project` 头）
- **请求**：

```json
{ "title": "登录页点击登录无响应", "steps": "1. 打开登录页…", "moduleId": "…" }
```

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| title | string | 是 | 当前输入的缺陷标题（≤300，为空或超长 1000018287） |
| steps | string | 否 | 已填写的重现步骤（未改动默认模板时前端不传） |
| moduleId | string | 否 | 已选择的所属模块 |

- **响应（SSE 事件流，`Content-Type: text/event-stream`）**：

| 事件 | data 载荷 | 说明 |
| ---- | ---- | ---- |
| `progress` | `{"stage":"context"}` / `{"stage":"generating"}` | 上下文装配完成、模型生成开始等进度提示（可缺省） |
| `done` | 完整建议 JSON（下例） | 成功终态，收到后渲染徽标并关闭连接 |
| `error` | `{"code":1000018117,"message":"…"}` | 流内失败终态（模型调用失败 / 输出不可解析），收到后关闭连接 |
| 心跳 | 注释行 `:` 前缀，每 15s 一次 | 防中间层空闲断连，不承载语义 |

```json
{
  "title": { "value": "登录页点击登录按钮后无任何响应", "reason": "…" },
  "reproSteps": { "value": "##### 执行步骤\n1. 打开登录页…\n##### 实际结果\n点击登录按钮后页面无任何响应\n##### 期望结果\n跳转首页并提示登录成功", "reason": "…" },
  "suggestions": {
    "bugType": { "value": "code_error", "reason": "重现步骤描述功能失效" },
    "severity": { "value": "serious", "reason": "阻断登录主流程" },
    "priority": { "value": "high", "reason": "…" },
    "moduleId": { "value": "…", "reason": "…" },
    "keywords": { "value": ["登录", "无响应"], "reason": "…" },
    "relatedCase": { "value": "…", "caseTitle": "登录-正常登录", "reason": "…" }
  }
}
```

> 事件流建立前的**前置校验失败仍为同步 HTTP 错误**（非流式响应）：输入非法 1000018287、无可用模型 1000018118、权限拒绝，客户端按普通错误处理后可重试。

- **校验规则**：`title` 必填且 ≤300（否则 1000018287）；SSE 流式单跳模型调用，**不产生任务、不落库**；**无客户端总超时**（事件持续到达即保活，后端 15s 心跳兜底），模型调用失败 / 无可用模型按总册 4.2 映射（流内失败走 `error` 事件 1000018117，流建立前失败同步返回 1000018118），前端轻提示可重试。**上下文装配**：可用模块取当前项目模块清单（`id|名称`）；固定选项取缺陷类型 / 严重等级 / 优先级取值域（常量枚举）；候选用例先按标题向量检索项目内用例文档（top 8）再展开为用例节点清单（`id|标题`，≤30，按文档序截断），向量能力未就绪时无用例候选（不报错、不产出 `relatedCase`）。**不检索相似存量缺陷**（相似性判断由录入时重复检测 3.8 承担，建议入口不重复检索）。**清洗**：枚举值非法剔除，模块 id 不属当前项目、用例 id 非本项目用例节点剔除，`relatedCase` 至多一条。建议值仅在表单中以徽标呈现，用户一键采纳或修改后提交，**用户填写值与建议不一致时以用户值为准**；实时通道为纯文本调用，不随附截图。

### 3.6 存量批量分类（任务）

- **提交**：`POST /api/ai/tasks`

```json
{ "type": "bug_classify", "input": { "bugIds": ["…"] }, "waitSeconds": 0 }
```

  - 也可传筛选条件（`input.filter`：未分类 / 分类存疑的状态条件），服务端解析为缺陷集合（上限 500，空集返回 1000018283）。
  - **截图入模**：每缺陷取图像类附件（png / jpg / jpeg / gif / webp / bmp）≤2 张，批量图片总量 ≤8 张（按缺陷序截断，超出部分仅文本）；字节直接读缺陷附件存储，随对应缺陷上下文直传 vision 模型；提交期按总册 4.2 选型，无可用视觉模型返回 1000018124。
- **提案**：逐缺陷一条 `classify_suggestion`（`content.suggestions` 仅含 `bugType / severity / priority / moduleId / keywords`，`content.bugId` 指向目标，`confirmStatus = pending`）。
- **确认**：总册 3.6.5，动作 `adopted / adopted_edited / rejected`；承接服务**更新对应缺陷字段**（类型、严重等级、优先级、模块、关键词、指派），逐项事务；采纳写 `adopted_ref`（后端不做权限校验）。

### 3.7 分诊队列建议（同步）

- **提交**：`POST /api/ai/tasks`

```json
{ "type": "bug_triage", "input": {}, "waitSeconds": 10 }
```

- **提案**（`kind = triage_order`，只读，`confirmStatus = not_applicable`）：

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
    { "bugId": "…", "title": "登录按钮无反应", "status": "active", "similarity": 0.87, "basis": "命中原文分块：登录页点击登录按钮后无任何响应…" }
  ]
}
```

- **前端触发**：标题输入框失焦时发起（标题为空不触发）；命中时弹窗展示相似缺陷明细，无命中或功能不可用（AI 未启用 / 向量未就绪 / 请求失败）不作任何提示（交互口径见 `docs/05-interaction-design/07-ai-capability/06-defect-analysis-ui.md` 2.2、3.1）。
- **校验规则**：仅检索当前项目的 `entity_type = bug` 向量（先业务归属过滤再相似检索，总册 4.4）；相似度仅作排序，阈值不对外承诺；`title` 必填（否则 1000018284）；向量能力未就绪时返回 1000018258 口径错误（前端按功能不可用静默处理）。用户确认为重复时，走既有「重复缺陷」解决方案提交（须指定原始缺陷）。

### 3.9 存量重复扫描（任务）

- **提交**：`POST /api/ai/tasks`

```json
{ "type": "bug_duplicate_scan", "input": { "scope": "active" }, "waitSeconds": 0 }
```

- **提案**（`kind = duplicate_group`，`confirmStatus = pending`）：

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

### 4.2 任务 type 与提案确认映射

| type | 提案确认 |
| ---- | ---- |
| bug_classify（批量） | 逐条 `pending → adopted/rejected` |
| bug_triage | `not_applicable` |
| bug_trend_summary | `not_applicable` |
| bug_duplicate_scan | 分组 `adopted/rejected` 留痕 |

- 新建缺陷建议不走任务（实时 SSE 流式接口，见 3.5），`bug_classify` 仅存量批量一种输入形态；

- 任务发起与采纳均不做后端权限校验（仅保留任务归属可见性）；作用域一律取请求头 `X-Active-Project`。

### 4.3 批量任务的执行与确认

1. 批量分析按缺陷分片执行，进度以执行步数与轨迹事件呈现（总册 3.6.3 / 3.6.4）；
2. 确认走总册 3.6.5 逐项事务：采纳即按既有缺陷服务更新字段（同一事务写 `ai_task_adoption` + `adopted_ref`），失败项回滚、成功项保留；
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
├── AiSummaryCard（生成 / 重新生成 + 引用链接，失败可再次执行）
├── TriageQueueCard（分诊顺序建议列表，可拖拽调整，只读落库）
└── BatchClassifyEntry（批量分类发起 → 跳转任务详情页，审核区列表模式）

新建缺陷表单（既有 BugCreatePage 扩展）
├── TitleSuggestBadge（优化后标题建议：标题输入框下方行内徽标，点击采纳覆盖标题）
├── ReproStepsSuggestBadge（重现步骤建议：编辑器下方行内徽标，点击采纳写入执行步骤 / 实际结果 / 期望结果）
├── SuggestBadge（分类建议值标记：类型 / 等级 / 优先级 / 模块 / 关键词，一键采纳）
├── RelatedCaseSuggestBadge（关联用例建议：「选择用例」按钮旁徽标，点击采纳写入关联用例）
└── BugDuplicateDialog（标题失焦触发的相似检测弹窗：相似度排序列表 + 跳转）

存量扫描入口 → DuplicateScanPanel（分组审核，确认留痕后引导既有重复缺陷流程）
```

### 5.3 状态管理与状态分支

- Pinia store `bugAnalysis`：范围与分组条件、趋势/度量数据、摘要任务状态、批量任务引用（轮询复用 `aiTask` store）；
- 状态分支：范围无数据（空图表补 0）、摘要生成中/失败、批量任务执行与部分采纳回执、向量未就绪（录入检测静默不弹窗、扫描入口置灰）、权限不足（隐藏分析入口）。

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
| 1000018287 | 400 | 建议输入非法（标题为空或超长） |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../controller/project/BugAnalysisController` | 趋势 / 度量 / 录入检测 / 新建建议路由（仅路由，C2） |
| `server/.../service/domain/bug/BugAnalysisService + Impl` | 实时聚合与口径计算 |
| `server/.../service/domain/bug/BugDraftSuggestService + Impl` | 新建建议实时 SSE：上下文装配（模块 / 用例候选 / 枚举）、流式单跳模型调用与清洗、SseEmitter 事件写出 |
| `server/.../service/ai/config/AiPromptScenes` | 登记实时建议提示词场景（`bug_draft_suggest`，变量：bugContext / moduleOptions / caseOptions） |
| `server/.../service/domain/bug/BugTrendSummaryTypeAdapter`、`BugClassifyTypeAdapter`（仅批量）、`BugTriageTypeAdapter`、`BugDuplicateScanTypeAdapter` | 缺陷域任务类型适配器（工具白名单与预算默认、提案结构，随对应能力域就近注册） |
| `server/.../service/domain/bug/BugClassifyAdopter + Impl`、`BugDuplicateScanAdopter` | 批量分类采纳落库（调用既有缺陷服务）与重复扫描留痕承接 |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018281–1000018287 |
| `web/src/pages/project/BugAnalysisPage.vue` 及组件、`web/src/stores/bugAnalysis.ts` | 分析页与状态 |
| `web/src/composables/project/bug/useBugCreateSuggest.ts` | 改同步接口调用（移除任务提交与轮询） |
| `web/src/pages/project/BugCreatePage.vue` 扩展（标题 / 重现步骤 / 关联用例建议徽标，移除指派候选） | 新建页扩展 |

- **数据库**：无新增表、无迁移（统计实时计算，向量读既有 `ai_vector_index`）。
- **OpenAPI**：`analysis/suggest` 标注 SSE 流式（`text/event-stream`）；`duplicates/check` 标注同步检索；批量任务 type 的 `input / 提案` 结构随任务分组暴露。

**测试要点（C8 ≥ 70%）**

- 后端：趋势口径（新增/关闭/存量边界）、修复时长首次流转取值、除零补零、分组参数校验、实时建议的输入校验（1000018287）与 SSE 事件写出（`done` / 流内 `error` 映射 1000018117、心跳保活、流建立前同步错误）、用例候选展开与向量未就绪降级、清洗（枚举 / 项目归属剔除）、批量采纳逐项事务与幂等冲突、录入检测项目内过滤、空集错误码、`bug_classify` 截图附带与总量截断、无视觉模型快速失败（1000018124）；
- 前端：图表下钻带参、建议徽标与用户值优先（标题 / 重现步骤 / 关联用例采纳写入）、实时建议流式读取（事件解析、流中断提示与再次点击重试）、摘要引用跳转、分诊拖拽不落库、批量审核回执、向量未就绪降级。

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
