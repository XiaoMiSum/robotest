# 软件测试平台——AI+需求 生成链

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对 **AI+需求 生成链**能力域进行详细设计：定义测试设计生成与评审 / 计划圈选建议两类任务的输入、提案结构、执行过程与采纳落库规则。任务资源、提案确认协议、配置与用量的公共约定见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档不重复定义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/03-srs-ai-generation.md`（US-AI-005 ~ 009）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md` 第 4.2 节。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（任务资源 3.6、错误码分段 6）
- 需求模块：`docs/04-detailed-design/03-function-testing/09-requirement-management.md`（生成输入的来源实体）
- 评审 / 计划快照机制：`docs/02-high-level-design/03-function-testing/02-hld-function-testing.md` 第 3.3 节

---

## 2. 数据设计

本分册**不新增表**：

- 任务、执行与轨迹：存 `ai_task / ai_task_execution / ai_task_trace_event`（`type = test_design_generation / review_selection / plan_selection`）；
- 提案与采纳：存 `ai_task_proposal / ai_task_adoption`（本分册提案 `kind`：`module_suggestion / mindmap_document_suggestion / test_case_suggestion / review_selection / plan_selection`）；
- 生成目标（模块 / 脑图文档 / 用例）落库为既有业务表数据，本分册只定义写入规则（4.3）。

---

## 3. 接口详细设计

### 3.1 通用约定

- 全部执行经总册 3.6 AI Task 任务资源；本文档只定义两类任务的 `type`、`input`、提案与承接语义。
- 发起入口在需求列表 / 需求详情（`X-Active-Project` 为目标项目），落库确认时的目标项目以确认请求的 `X-Active-Project` 头为准（C4）。

### 3.2 发起测试设计生成

- **路径**：`POST /api/ai/tasks`
- **请求**：

```json
{
  "type": "test_design_generation",
  "input": {
    "requirementIds": ["…", "…"],
    "targetModuleId": null,
    "placement": "new_top_level",
    "granularity": "standard"
  },
  "waitSeconds": 0
}
```

| input 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| requirementIds | uuid[] | 是 | 已确认需求（1–50 条）；每条须 `status = confirmed` 且属当前项目 |
| targetModuleId | uuid | 否 | 目标落位模块；`placement = attach` 时必填且须属当前项目 |
| placement | string | 否 | `new_top_level`（默认，新建顶级目录）/ `attach`（挂到既有模块节点） |
| granularity | string | 否 | `concise / standard / detailed`（默认 `standard`） |

- **响应**：总册 3.6.2 任务对象；前端跳转任务详情页。
- **校验**（错误码见 6）：需求存在（1000018201）、全部已确认（1000018202）、落位模块合法（1000018203）、参数取值合法（1000018205）、同项目同输入去重（1000018209）。

### 3.3 执行过程与轨迹

- **查询**：总册 3.6.3 `GET /api/ai/tasks/{taskId}`；执行过程由 AI 自主编排，**不承诺固定阶段序列**：
  - 详情页展示执行的计划步骤快照（`plan`；典型序列为解析需求 → 生成模块结构 → 生成脑图文档 → 标记用例节点 → 填充用例属性，仅供展示参考）、当前步数与预算消耗；
  - 完整过程经轨迹查询（总册 3.6.4 `events`）逐事件回看（AI 决策、工具调用与结果、提案产出）。
- 终止与再次执行按总册 3.6.4：终止态执行**不产生任何提案与落库数据**；再次执行产生新执行实例与新提案，原提案与轨迹保留。

### 3.4 提案结构与审核详情

- **提案清单**：总册 3.6.3 `proposals` 摘要；**单个提案详情** `GET /api/ai/tasks/{taskId}/proposals/{proposalKey}`。
- 三层树的 `kind` 与 `parentKey` 结构：

```json
{
  "key": "case-3",
  "kind": "test_case_suggestion",
  "parentKey": "doc-1",
  "title": "获取验证码",
  "content": {
    "isTestCase": true,
    "attributes": {
      "priority": "medium",
      "precondition": "用户处于登录页",
      "steps": ["输入手机号", "点击获取验证码"],
      "expected": ["提示验证码已发送"],
      "tags": ["登录"]
    },
    "sourceRefs": [{ "requirementId": "…", "quote": "支持短信验证码…" }],
    "suspectedDuplicateOf": null
  },
  "confirmStatus": "pending"
}
```

- `sourceRefs` 为来源引用（需求 + 原文引语），**来源缺失的提案不得进入待采纳态**（概要 8 安全设计）；
- `suspectedDuplicateOf` 非空表示与既有模块 / 用例疑似重复，前端在采纳时高亮提示（1000018204 由人工决定：改名采纳或驳回）。

### 3.5 提案确认落库

- **路径**：总册 3.6.5 `POST /api/ai/tasks/{taskId}/proposals/confirm`。
- **本类型 `target` 结构**：

```json
{ "moduleId": "…", "position": "child" }
```

- **动作**：单条采纳 / 整树采纳（前端按 `parentKey` 展开为逐条请求批次）/ 编辑后采纳（`content` 传入编辑值）/ 驳回（`note` 可选）。
- **承接**（总册 3.6.1「生成链采纳服务」）：
  1. 逐项事务：写模块 / 脑图文档 / 用例节点与用例实体（**均为新增，不覆盖既有数据**）；
  2. 每个 `isTestCase = true` 的节点生成一条测试用例实体，回写 `adopted_ref = { moduleId / documentId / nodeId / caseId }`；
  3. 任一步失败整项回滚，确认状态保持 `pending` 可重试。

### 3.6 评审 / 计划圈选建议

- **发起**：`POST /api/ai/tasks`

```json
{
  "type": "review_selection",
  "input": { "requirementIds": ["…"], "moduleIds": ["…"], "roundCount": 1 }
}
```

| type | input | 提案 kind | 说明 |
| ---- | ---- | ---- | ---- |
| review_selection | `requirementIds?`、`moduleIds?`（至少一项）、`roundCount?`（默认 1，仅 plan） | `review_selection` / `plan_selection` | 建议纳入圈选的用例子集，附理由；plan 可按轮次分组 |

- **提案详情**：

```json
{
  "key": "sel-1",
  "kind": "review_selection",
  "title": "评审圈选建议（8 条）",
  "content": {
    "items": [
      { "caseId": "…", "title": "TC-001 获取验证码", "reason": "新增需求 REQ-001 覆盖" }
    ],
    "round": null
  },
  "confirmStatus": "pending"
}
```

- **确认**：走总册 3.6.5；本类型 `target` 为既有创建流程的请求参数（评审名称、参与者、起止时间等，**不含项目上下文**）。
- **承接**：沿用**既有评审 / 计划创建流程**生成快照（AI 不改变快照机制）。

---

## 4. 业务逻辑设计

### 4.1 输入有效性与快照执行

- 仅 `status = confirmed` 的需求可作为输入；`draft / changed / archived` 不可选（入口置灰并说明原因，服务端 1000018202 兜底）。
- **发起时对输入需求做版本快照**（`input.requirementSnapshots` 存关键字段摘要）：执行期间需求被变更，任务按发起时快照继续；提案采纳时比对当前状态，`sourceRefs` 区域展示「来源需求已变更」标记，由人工决定采纳或驳回。

### 4.2 提案采纳后的状态

- 确认后 AI 不再自动覆盖；人工编辑过的产出仅在**显式重新生成并二次确认**后可被覆盖——重新生成 = 任务「再次执行」（总册 3.6.4），新执行产出新提案后需再次确认。
- 驳回提案保留在 `ai_task_proposal` 与轨迹审计中供回查，不落库。

### 4.3 采纳落库事务

1. 落库目标项目 = 确认请求 `X-Active-Project`；目标模块按 `target.moduleId`，缺省时按 `input.placement`；
2. 疑似重复（`suspectedDuplicateOf` 非空）的提案**不阻断**采纳，但前端强提示，人工可改为编辑后采纳；
3. 「业务落库 + `ai_task_adoption` 写入」同一事务（详见 3.5），保证业务数据与采纳记录一致；
4. 编辑后采纳以编辑值落库，`adopted_edited` 记录原 AI 值与编辑差异（审计）。

### 4.4 圈选确认与快照语义

- 圈选建议**不自动创建**评审 / 计划；确认时走既有创建流程，快照内容 = 圈选用例的当前版本；
- 用例集合校验：`caseIds` 必须存在于当前项目（1000018207），圈选外增删由人工在创建流程中调整（调整后采纳 = 以调整结果提交 `target`）。

---

## 5. 前端设计

### 5.1 入口与发起配置

- 需求列表 / 详情多选后「AI 生成用例」→ `GenerationConfigDialog`（目标项目与模块落位、生成范围回显、用例粒度偏好）；不可选需求置灰并提示原因（草稿 / 已变更 / 已归档）。
- 需求列表多选后「AI 圈选建议」→ 圈选范围配置对话框（范围回显同上；计划类型可配轮次 1–10，默认 1），提交 `review_selection` / `plan_selection` 任务后跳转任务详情页。

### 5.2 执行过程（任务详情页上半区）

```
AiTaskDetailPage（/workspace/projects/ai/tasks/:taskId）
├── PlanTimeline（AI 计划步骤时间线：当前步骤高亮，终止原因标红）
├── ExecutionPanel（步数与预算消耗、提案数量、终止 / 再次执行）
├── TraceEventList（轨迹事件流，可回看）
└── ProposalCountBadges（模块/文档/用例计数）
```

提交成功后由发起对话框跳转本页，页面结构与状态分支见总册 5.2。

### 5.3 提案审核区（三层树，详情页下半区）

- 复用总册 5.2 任务详情页内的 `AiProposalReviewPanel`，生成链扩展：
  - 三层树渲染（模块 → 文档 → 节点，用例节点带属性徽标）；
  - 内容对比视图：AI 建议 vs 既有数据（编辑后采纳时），`sourceRefs` 引用可点击回跳需求原文；
  - 「来源需求已变更」标记、`suspectedDuplicateOf` 疑似重复警示；
  - 单条采纳 / 整树采纳 / 编辑后采纳 / 驳回附反馈（触发再次执行）。

### 5.4 圈选建议发起与审核

- 需求列表「AI 圈选建议」发起（输入 `requirementIds`，计划另附 `roundCount`）；
- 圈选建议卡片列表（用例条目 + 理由），支持增删调整后提交既有评审 / 计划创建弹窗；确认前展示将创建的记录数量。

### 5.5 状态分支

- 任务执行中（步骤与预算消耗）/ 挂起（待批准）/ 待采纳 / 已终止（原因 + 再次执行）/ 已结束（灰态）；
- 提案全驳回、部分采纳的回执列表（逐项成败）；
- AI 未启用 / 未配置模型时入口隐藏（总册 4.5）。

---

## 6. 错误码定义

号段：**1000018201–1000018249（生成链）**，以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018201 | 404 | 输入需求不存在 |
| 1000018202 | 400 | 存在非已确认需求，不可作为生成输入 |
| 1000018203 | 400 | 落位目标模块不存在或不属当前项目 |
| 1000018204 | 409 | 提案与既有数据疑似重复，需人工处理 |
| 1000018205 | 400 | 生成参数非法（粒度、条数、落位方式） |
| 1000018206 | 400 | 圈选建议输入非法（范围为空或参数越界） |
| 1000018207 | 404 | 圈选用例不存在 |
| 1000018209 | 409 | 已存在进行中的同输入生成任务 |
| 1000018210 | 409 | 父级提案尚未采纳，无法落库 |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../service/ai/task/type/TestDesignGenerationTypeAdapter` | 生成链类型适配器（工具白名单与预算默认、提案结构定义） |
| `server/.../service/ai/task/type/ReviewSelectionTypeAdapter`、`PlanSelectionTypeAdapter` | 圈选建议类型适配器 |
| `server/.../service/ai/task/adopt/GenerationAdoptService + Impl` | 三层提案采纳落库（调用既有模块 / 文档 / 用例服务） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018201–1000018210 |
| `web/src/components/project/ai/GenerationConfigDialog.vue`、`SelectionConfigDialog.vue`、`GenerationArtifactTreeReview.vue`、`SelectionReviewPanel.vue` 及子组件 | 发起与审核前端 |
| `web/src/composables/project/ai/useGenerationSubmit.ts`、`useAiProposalReview.ts`、`useGenerationArtifactTree.ts`、`useSelectionSubmit.ts`、`useSelectionReview.ts` + 单测 | 发起提交、提案审核与圈选调整逻辑 |
| `web/src/components/project/functional-testing/review/ReviewCreateDialog.vue`、`plan/PlanCreateDialog.vue` | 圈选确认复用的评审 / 计划创建弹窗 |

- **数据库**：无新增表、无迁移（写入均为既有业务表，见 2 节）。
- **OpenAPI**：`POST /api/ai/tasks` 的 `input` 按 `type` 给出 oneOf 说明；提案详情返回结构随任务详情分组暴露。

**测试要点（C8 ≥ 70%）**

- 后端：输入状态校验矩阵、快照执行（执行中变更不中断）、落库事务回滚、编辑后采纳差异记录、圈选用例存在性与创建参数校验、同输入去重、终止不留数据；
- 前端：配置对话框校验与置灰、计划步骤时间线与轨迹回看、三层树审核四类动作、疑似重复与来源变更提示、圈选调整后提交。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-03 | 任务进度与产物审核统一为任务详情页（AiTaskDetailPage），删除抽屉/全屏页并存口径 |
| V1.0 | 2026-10-04 | 快照引用边取值 `snapshot_ref` 更名为 `case_snapshot`（对齐追溯矩阵详设） |
| V1.0 | 2026-10-06 | 前端设计补圈选建议发起入口（需求列表「AI 圈选建议」，5.1 / 5.4） |
| V1.0 | 2026-10-06 | 错误码表补 1000018210；实施说明文件清单对齐前端实际实现（组件与组合式路径、圈选创建弹窗） |
| V1.0 | 2026-10-15 | 发起入口按钮与配置对话框更名「AI 生成用例」，长语义由 tooltip 与对话框首行说明承载 |
| V1.0 | 2026-10-16 | 同步 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`）：2 节数据设计改按 `ai_task_` 域六表拆分（执行 / 轨迹 / 提案 / 采纳）；3.3 固定六阶段改为 AI 计划步骤 + 轨迹回看；产物全面改称提案，确认端点改为 `proposals/confirm`；重试改「再次执行」（新执行实例）；5.2 详情页组件改为 PlanTimeline / ExecutionPanel / TraceEventList；实施说明改类型适配器口径 |
| V1.0 | 2026-10-16 | 残词勘误：1.1 引言（产物结构 / 阶段进度 / 确认落库 → 提案结构 / 执行过程 / 采纳落库）、3.2「任务进度页」与 3.4「不得进入待确认态」对齐新口径 |
| V1.0 | 2026-10-16 | 术语与状态口径残词勘误：3.6 表头改「提案 kind」，4.1 / 4.2 改提案采纳口径与「产出」，4.3 改疑似重复提案不阻断，5.2 改「执行过程」、5.3 改「提案审核区」与提案全驳回，6 错误码 1000018204 / 1000018208 / 1000018210 文案改提案，7 组合式改 `useAiProposalReview.ts` |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：删除追溯建边引用（1.1 / 1.2 / 2 节）、`TraceEdgeWriter` 建边步骤与 `trace_edge` / `case_snapshot` 写入规则（3.5 / 3.6 / 4.3 / 4.4），4.5 缺口引导与影响处置整节删除 |
| V1.0 | 2026-10-17 | 概要引用同步：`02-hld-ai-capability.md` 删除追溯矩阵节后章节顺延，对应概要由「第 4.2 / 4.3 节」改「第 4.2 节」 |
| V1.0 | 2026-10-19 | 移除任务权限校验：任务发起与提案采纳后端不做权限校验（入口显隐由前端按权限点控制），6 错误码表注销 1000018208 |
