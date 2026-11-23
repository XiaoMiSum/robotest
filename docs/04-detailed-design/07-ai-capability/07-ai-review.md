# 软件测试平台——AI 评审

**文档版本**：V1.0
**日期**：2026-11-22
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对「AI 评审」能力进行详细设计：定义任务输入、评审上下文装配、评审建议提案结构、采纳写回口径与错误码。任务资源、执行机制、提案确认协议与公共约定见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档只定义本任务的业务入口与提案语义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/07-srs-ai-review.md`（US-AI-020）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`；任务机制见 `docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`。
- 业务基线：`docs/04-detailed-design/03-function-testing/05-project-workspace-test-review.md`（评审快照、评审记录与进度口径）；交互基线：`docs/05-interaction-design/03-function-testing/05-workspace-ui-test-review.md`。
- 权限：免 RBAC 权限码校验（登录即可）；发起与采纳保留评审归属当前项目校验及项目成员校验，入口显隐由前端按评审状态与 AI 可用性控制。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（3.6 任务资源、6 错误码分段）
- API 与数据库约定：`docs/00-spec/20-contracts/01-api.md`、`docs/00-spec/20-contracts/02-database.md`

---

## 2. 数据设计

本分册**不新增表、不新增列**：

- 任务、执行与轨迹：`ai_task / ai_task_execution / ai_task_trace_event`（`type = review_case_review`）；
- 提案与采纳：`ai_task_proposal / ai_task_adoption`（提案 `kind = review_suggestion`）；
- 评审上下文读取：`ft_review_node_snapshot`（规划用例快照）、评审关联需求（`ft_review.requirement_ids` 及需求详情）；
- 采纳写回：`ft_review_record`（新增标记类评审记录）、`ft_review_node_snapshot`（更新 `last_mark / last_mark_ai_adopted / last_reviewer_id / last_reviewed_at`）；
- 溯源：`ai_task_adoption.adopted_ref` 写入 `{ "recordId": "…", "snapshotNodeId": "…" }`，反查「这条标记来自哪个提案」，不新增业务列；
- 需求快照随任务 `input` 一并保存，不新增表。

---

## 3. 接口详细设计

### 3.1 通用约定

- 全部执行经总册 3.6 AI Task 任务资源；本文档只定义本任务的 `type`、`input` 与提案语义，不新增平行端点。
- 上下文头：`X-Active-Workspace` + `X-Active-Project`（发起、确认一致）；发起缺 `X-Active-Project` 头回公共错误码 1000002008（缺少上下文请求头）。
- 响应 `Result<T>`，示例**仅展示 `data`**。
- 权限：任务发起与采纳免 RBAC 权限码校验（仅保留评审归属与项目成员校验）；评审终态与发起人身份等既有业务校验不因此放宽。

### 3.2 提交任务

- **路径**：`POST /api/ai/tasks`

```json
{
  "type": "review_case_review",
  "input": { "reviewId": "…" },
  "waitSeconds": 10
}
```

| input 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| reviewId | uuid | 是 | 评审 id（须属当前项目，否则 1000018313；终态 1000018314；无关联规划用例 1000018315） |

- **校验顺序**：总开关（1000018101）→ 类型 → 输入（评审归属 / 状态 / 用例集）→ 预算 → 可用模型（1000018118）；上下文头缺失回 1000002008。
- **响应**：结构同总册 3.6.2；提交时按总册 3.6.1 生成「AI 评审 - 评审标题」任务标题，执行不改写。
- **需求快照**：校验通过后读取关联需求文本写入任务 `input.requirementSnapshot`，执行期以此为准；快照结构为 `{ "requirements": [{ "id", "title", "content" }] }`。

### 3.3 任务复用

- **路径**：`GET /api/ai/tasks/{type}/latest`
- 扩展参数 `reviewId`（可选）：传 `reviewId` 时按任务输入中的 `reviewId` 匹配本类型任务，返回 `created_at` 最新的一条（结构同总册 3.6.3 详情）；`documentId` / `nodeId` 仍按原口径用于其他任务类型。
- 可见范围：当前项目内任意成员提交的 `review_case_review` 任务（`project_id = X-Active-Project`），以及当前用户自己的个人任务（`project_id IS NULL`）；无匹配时 `data` 为 `null`。
- 前端命中后按任务状态回显进度、提案或终止原因，不再弹出确认，不重复发起。

### 3.4 提案确认（采纳写回）

- **路径**：`POST /api/ai/tasks/{taskId}/proposals/confirm`（总册 3.6.5），本类型承接服务 `ReviewCaseReviewAdopter`。
- **动作**：`adopted / adopted_edited / rejected`。

```json
{
  "items": [
    { "key": "case-1", "action": "adopted" },
    { "key": "case-2", "action": "adopted_edited", "content": { "suggestedMark": "pass", "reason": "人工复核后认为预期充分" } },
    { "key": "case-3", "action": "rejected", "note": "该用例本期不评审" }
  ]
}
```

- `adopted` 按提案 `suggestedMark` 写回；`adopted_edited` 的 `content` 为 `{ "suggestedMark": "pass|fail", "reason": "…" }`，人工可修正结论与理由，未传 `content` 或取值非法时该项失败；`rejected` 仅写驳回原因。
- **写回范围**：采纳不修改评审关联用例集合、快照结构与既有标记记录，仅新增一条标记记录并更新快照节点的最后标记状态；进度与通过率由既有聚合口径派生，不新增字段。

**提案结构**（`kind = review_suggestion`，逐用例一条，`confirmStatus = pending`）：

```json
{
  "key": "case-1",
  "kind": "review_suggestion",
  "parentKey": null,
  "title": "TC-001 获取验证码",
  "content": {
    "reviewId": "…",
    "snapshotNodeId": "…",
    "caseTitle": "获取验证码",
    "suggestedMark": "fail",
    "reason": "缺少验证码错误分支的预期结果",
    "issues": [
      {
        "severity": "medium",
        "category": "预期不完整",
        "description": "未覆盖验证码输入错误的预期",
        "path": "获取验证码 › 步骤 2"
      }
    ],
    "sourceRefs": [
      { "type": "requirement", "id": "…", "title": "REQ-001", "quote": "验证码错误时提示…" }
    ]
  },
  "confirmStatus": "pending"
}
```

| content 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| reviewId | uuid | 所属评审，作为归属校验锚点 |
| snapshotNodeId | uuid | 评审快照中的用例节点，作为采纳定位锚点；非 case 节点或未关联节点在清洗阶段丢弃 |
| caseTitle | string | 用例标题，供提案列表展示 |
| suggestedMark | `pass` / `fail` | 建议标记；与评审三态一致，不产出 `pending` 建议 |
| reason | string | 建议理由；采纳时写入评审记录评论并冠以「AI 建议」前缀 |
| issues | array | 问题清单，每项含 `severity`（`high` / `medium` / `low`）、`category`、`description`、`path`；`suggestedMark = fail` 时至少一项，`pass` 时可为空数组 |
| sourceRefs | array | 来源引用，须指向当前项目需求；缺失的提案在清洗阶段丢弃 |

- 每个关联 case 快照节点必产一条提案（无问题时产出 `suggestedMark = pass`、`issues = []`）；引用节点不存在、跨评审或类型非法的提案在清洗阶段丢弃。
- 任务标题在提交创建时取评审标题（`reviewId` → `TestReview.title`），按总册 3.6.1 生成「AI 评审 - 评审标题」；执行不改写。

---

## 4. 业务逻辑设计

### 4.1 输入与触发校验

- 评审按 `id` 与 `X-Active-Project` 校验归属，非当前项目回 1000018313；评审状态须为 new / in_progress，终态回 1000018314；`ft_review_node_snapshot` 中无 `is_associated = true` 且 `type = case` 的节点时回 1000018315。
- 同一评审存在进行中任务时重复发起按任务通用规则提示（3.3 的 `/latest` 供前端预检）。
- 预算默认值沿用总册普通目标型任务默认（提交仅可下调）。

### 4.2 上下文装配与提案清洗

- `ReviewCaseReviewTypeAdapter` 装配上下文：按 `ft_review_node_snapshot` 读取规划用例的标题、前置条件、操作步骤、预期结果与优先级，按祖先路径组织；按 `ft_review.requirement_ids` 取任务 `input.requirementSnapshot` 中的需求文本，经 RAG 检索带来源引用拼入上下文。
- 不读取评审记录、历史缺陷或执行数据；依据缺失维度显式留空，不臆造。
- 提案清洗：校验 `snapshotNodeId` 属于本评审、为 case 节点且 `is_associated = true`；`sourceRefs` 指向当前项目需求；`suggestedMark` 与 `issues` 满足约束；非法提案丢弃，不进入待采纳态。

### 4.3 采纳写回

- 每项「`ai_task_adoption` 采纳记录 + 评审记录写入 + 快照节点更新」在同一事务内完成（逐项事务，失败仅该项回滚）。
- `adopted` / `adopted_edited`：按 `snapshotNodeId` 定位快照用例节点，校验仍属本评审、为 case 节点、评审未进入终态，否则该项返回 `success = false` 与对应业务错误码；通过后：
  1. 新增 `ft_review_record`：`operationType = 'mark'`、`mark = suggestedMark`、`reviewerId = 采纳操作人`、`comment = '[AI 建议] ' + reason`；
  2. 更新 `ft_review_node_snapshot`：`last_mark = suggestedMark`、`last_mark_ai_adopted = true`、`last_reviewer_id = 采纳操作人`、`last_reviewed_at = 当前时间`；人工再次标记或重置待评审时将 `last_mark_ai_adopted` 置为 `false`；
  3. `ai_task_adoption.adopted_ref` 写入 `{ recordId, snapshotNodeId }`，`action` 取 `adopted` / `adopted_edited`。
- `rejected`：仅写 `ai_task_adoption`（`action = rejected`、`note` 为驳回原因），不写评审记录与快照。
- 幂等由 `uk_ai_task_adoption (execution_id, proposal_key)` 兜底，重复提交回 1000018113。
- 采纳结果即时反映到评审脑图徽标与页头进度统计；不触发评审状态变更，完成评审仍由发起人按既有门槛执行。

### 4.4 状态边界

- 评审进入终态后，既有提案不可再采纳，写回校验返回 1000018314；进行中任务按总册 3.6.4 中止或继续。
- 快照在提案产出后被同步或调整，导致 `snapshotNodeId` 失效时，该项处置失败并提示，其余项继续；不自动重建提案。
- 来源需求在任务执行期间变更时按发起时快照继续，提案 `sourceRefs` 可追溯；需求已删除时来源引用按快照内容展示，不阻断采纳。
- 任务被中止、执行错误或预算耗尽时不产出提案、不产生写回数据；任务详情展示原因并提供再次执行。

---

## 5. 前端设计

### 5.1 入口

```
评审详情页页头操作组
└── [AI 评审]（评审 new/in_progress 且 available=true 时展示）
    └── 按 reviewId 查询最近任务 → 命中回显进度/提案/终止原因；未命中发起 review_case_review
```

入口与评审既有操作组并列；终态隐藏，AI 未启用隐藏（总册 4.5）。点击后先查询最近任务，查询失败只提示错误、不自动发起。

### 5.2 组件结构

```
AiReviewPanel（AI 评审面板，评审详情页内呈现）
├── Header（关联用例数、任务进度 / 失败原因 / 来源变更提示）
├── ReviewList（逐用例建议卡片：用例标题、建议标记徽标、理由、问题清单（严重度 / 类别 / 路径）、来源引用跳转）
└── Actionbar（驳回附反馈、采纳、再次执行；处置按钮右对齐 link，驳回危险色 / 采纳成功色；置灰原因与逐项回执）
```

面板结构块落在对应 `.vue` 组件文件内，不拆独立组件文件。任务详情页对 `review_case_review` 注册只读展示策略，展示执行过程与提案只读明细，不提供采纳 / 驳回动作；提案状态由本面板的采纳动作驱动，与本面板共用同一确认协议。

### 5.3 状态管理与状态分支

- 复用总册 `aiTask` store 轮询任务执行；面板私有状态（当前定位、驳回表单、回执）挂组件本地，不入全局 store。
- 任务状态非 `running` 即停轮；`proposals_ready` 时按 `confirmStatus` 分区展示待处置与已处置提案；已处置提案只读回看，不重复处置。
- 采纳 / 驳回后刷新提案清单，并同步刷新评审脑图徽标与页头进度统计；逐项失败保留失败原因展示。
- 状态分支：任务中止展示原因并提供再次执行；已采纳提案只读；提案项节点失效时该项标红提示；评审进入终态后面板入口隐藏、既有提案不可处置；查询过期响应切换评审时丢弃，避免旧任务提案写入新评审。

---

## 6. 错误码定义

号段：**1000018301–1000018329（辅助功能）**，沿用辅助功能分册号段，以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018313 | 404 | 评审不存在或不属当前项目 |
| 1000018314 | 409 | 评审非活跃态（终态不可发起 / 采纳） |
| 1000018315 | 400 | 评审无关联规划用例，无法评审 |

> 上下文头缺失回公共错误码 1000002008，不占用本号段。任务状态、提案存在性与幂等冲突分别复用框架错误码 1000018111 / 1000018112 / 1000018113。

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../service/ai/task/type/ReviewCaseReviewTypeAdapter` | 本任务类型适配器：输入校验（评审归属 / 状态 / 用例集）、工具白名单与预算默认、评审建议提案结构与清洗 |
| `server/.../service/ai/task/handler/ReviewContextSupport` | 评审快照用例与关联需求上下文装配、需求快照保存 |
| `server/.../service/ai/task/adopt/ReviewCaseReviewAdopter` | 采纳承接：写回评审记录与快照节点、记录 `adopted_ref`、逐项事务与幂等 |
| `server/.../service/ai/config/AiPromptScenes`、评审场景默认提示词资源 | 新增 `review_case_review` 场景与默认提示词（逐用例建议结构与来源引用口径；提示词文件随实现新增） |
| `server/.../framework/common/Constants`、`ErrorCodeConstants` | 登记任务类型 `review_case_review`、提案 kind `review_suggestion` 与错误码 1000018313–1000018315 |
| `web/src/components/project/functional-testing/review/AiReviewPanel.vue` + 单测 | 逐用例建议列表、采纳 / 编辑后采纳 / 驳回、逐项回执 |
| `web/src/composables/project/ai/useReviewTask.ts` + 单测 | 最近任务查询、任务提交、轮询、提案映射与采纳回执处理 |
| `web/src/types/ai.ts` | 新增 `AiReviewSuggestion`（评审建议 content 结构），任务详情旧类型保持不变 |

- **数据库**：无新增表、无新增列；任务 / 执行 / 提案 / 采纳复用 `ai_task_` 六表，写回目标为既有 `ft_review_record` 与 `ft_review_node_snapshot`。
- **OpenAPI**：本任务 `input` 与提案 `content` 结构随任务分组暴露；确认复用总册 3.6.5；`/latest` 参数扩展同步登记。

**测试要点（C8 ≥ 70%）**

- 后端：评审归属 / 状态 / 空用例校验矩阵、需求快照装配、来源缺失与非法节点提案清洗丢弃、采纳写回（评审记录字段、快照节点更新、`adopted_ref`）、节点失效逐项失败不回滚、终态拒绝、幂等冲突；
- 前端：入口显隐、最近任务复用回显、建议列表与问题清单渲染、采纳 / 编辑后采纳 / 驳回回执、进度刷新、任务中止原因展示、切换评审丢弃过期响应。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-11-22 | 初始版本 |
| V1.0 | 2026-11-23 | 任务标题改提交创建时生成：按总册 3.6.1 取评审标题拼接「AI 评审 - 评审标题」，执行不再回填 |
| V1.0 | 2026-11-23 | 任务详情页改只读展示策略：不渲染通用审核区与采纳 / 编辑后采纳 / 驳回动作，处置统一由评审详情页 AI 评审面板承担 |
| V1.0 | 2026-11-23 | 5.2 处置动作收敛为右对齐 link 的驳回（危险色）与采纳（成功色），移除编辑后采纳 |
