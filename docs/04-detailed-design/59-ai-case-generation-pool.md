# 软件测试平台——需求工作流

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 通用约定

- 本分册接口均为**常规业务接口**：不经 AI 网关、不受 AI 总开关影响（AI 不可用时条目管理、阶段浏览、提案与血缘查看照常可用，仅阶段作业不可发起）；路径为项目级 `/api/project/requirements/**` 与 `/api/project/lineage/**`，请求头 `Authorization` + `X-Active-Workspace` + `X-Active-Project`，上下文标识不进入 URL 与请求体（C4）。
- 通用响应 `{ "code": 200, "msg": "success", "data": … }`；下文响应示例**仅展示 `data`**；分页参数 `pageNo` / `pageSize`，分页结果 `{ "list": [], "total": 0 }`；错误码为 `ErrorCodeConstants` 十位码（C3），异常统一 `ServiceExceptionUtil.get(ErrorCode)` 抛出。
- 阶段语义、出口证据、阶段作业触发规则、提案生命周期与 payload 结构见总览《智能用例生成与需求工作流》2.2 / 2.3；本分册只定义接口契约。
- **权限两档**：
  - **条目管理权限**：条目创建人或具备项目管理权限的成员（违规返回 1000002001）——条目 CRUD、归档、批量创建适用；
  - **阶段操作权限**：同条目管理权限——阶段推进/跳过/回退、阶段作业发起、提案处置、血缘手动关联/解除适用；各接口另有附加业务权限时逐处标注。
- **归档态限制**：`archived` 条目禁止更新与一切阶段操作（1000002001），删除不受限；条目不存在或不属于当前项目返回 **1000011028**。

---

## 2. 条目接口

### 2.1 条目列表

- **路径**：`GET /api/project/requirements`
- **参数**：`keyword`（可选，标题模糊）、`stage`（可选，阶段枚举）、`status`（可选，active / archived，缺省返回全部）、`pageNo`、`pageSize`
- **响应**：

```json
{
  "list": [
    {
      "id": "0198…",
      "title": "登录模块需求",
      "sourceUrl": null,
      "stage": "design",
      "status": "active",
      "aiGenerated": false,
      "pendingProposals": 3,
      "createdBy": "0195…",
      "creatorName": "张三",
      "updatedAt": "2026-10-01T08:00:00Z"
    }
  ],
  "total": 12
}
```

- `pendingProposals` = 该条目 pending + expired 提案数（阶段看板角标数据源）。
- 排序：`updatedAt` 倒序。

### 2.2 条目详情

- **路径**：`GET /api/project/requirements/:id`
- **响应**：列表项字段 + `content`（Markdown 全文）+ `createdAt`。

### 2.3 创建条目

- **路径**：`POST /api/project/requirements`
- **请求体**：`{ "title": "登录模块需求", "content": "……", "sourceUrl": null }`
- **校验**：`title` ≤ 200；`content` 长度 ≤ `requirementContentMaxLength`（默认 20000 字符，见基础设施 2.2；本接口不受 AI 开关影响，AI 配置记录不存在时取代码内置默认值），超限返回 1000001001 并明确提示；`sourceUrl` 仅格式校验，不访问。
- **状态**：创建即 `stage = intake`、`status = active`（均不接受客户端传入）。
- **响应**：创建后的条目，201。

### 2.4 更新条目

- **路径**：`PUT /api/project/requirements/:id`
- **权限**：条目管理权限，违规返回 1000002001；`archived` 条目禁止更新（1000002001）。
- **部分更新**：仅更新实际传入字段（C11），整行结果不作 `updateById` 载体。
- **联动**：标题/内容变更时，同事务将所处阶段及下游阶段（阶段顺序 ≥ 当前阶段，见总览 2.3.1）的 pending 提案置 `expired`；变更完成后按总览 2.3.3 尝试**自动重跑**当前阶段作业（仅当项目 `requirement_auto_advance = true`，去抖与去重规则同前，best-effort、失败不报错）。
- **响应**：更新后的条目。

### 2.5 删除条目

- **路径**：`DELETE /api/project/requirements/:id`
- **权限**：同 2.4；删除不受归档态限制。
- **处理**：逻辑删除条目，同事务逻辑删除其全部提案、阶段事件与血缘记录；**不影响**已生成的用例、评审、计划与缺陷对象本身。
- **响应**：`null`。

### 2.6 归档 / 取消归档

- **路径**：`PUT /api/project/requirements/:id/archive`
- **请求体**：`{ "archived": true }`（false 为取消归档，恢复 active）
- **权限**：同 2.4。
- **幂等**：重复归档/取消归档同一状态不报错；归档后禁止编辑与阶段操作，取消归档后恢复。
- **响应**：更新后的条目。

### 2.7 批量创建条目（US-AI-019）

- **路径**：`POST /api/project/requirements/batch`
- **请求体**：

```json
{
  "items": [
    {
      "title": "用户管理·新增用户",
      "content": "……",
      "sourceUrl": null,
      "aiGenerated": true
    }
  ]
}
```

- **校验**：`items` 非空且 ≤ 100 条；每项 `title` ≤ 200、`content` 长度 ≤ `requirementContentMaxLength`（同 2.3），超限返回 1000001001；`aiGenerated` 缺省 false（仅作展示标记，不影响业务规则，接受客户端透传）。
- **权限**：与 2.3 一致；批量接口不做逐条创建人差异。
- **响应**：`{ "count": 5 }`（实际入库条数）。
- **说明**：AI 拆分预览勾选后的批量入库走此接口；标题前缀（模块名 · 需求点标题）由前端按预览分组拼接后提交，接口不感知模块概念；入库条目一律 `stage = intake`。

---

## 3. 阶段接口

### 3.1 阶段状态预检

- **路径**：`GET /api/project/requirements/:id/stage-status`
- **响应**：

```json
{
  "stage": "design",
  "nextStage": "coverage",
  "canAdvance": false,
  "missingEvidence": [
    { "code": "case_missing", "label": "尚未关联任何用例" },
    { "code": "proposal_pending", "label": "存在 3 条未处置提案" },
    { "code": "proposal_expired", "label": "存在 1 条过期提案" }
  ],
  "stageJob": { "type": "case_generation", "taskId": "0199…", "status": "running", "progress": 40, "createdAt": "…" },
  "autoAdvance": false
}
```

- `missingEvidence[].code` 枚举：`case_missing`（血缘无用例）/ `proposal_pending` / `proposal_expired` / `review_not_passed`（无通过的关联评审）/ `execution_not_passed`（关联用例未全部执行通过）；`canAdvance` = 缺失清单为空（与总览 2.3.2 一一对应）。
- `stageJob` 为当前阶段作业的最新记录（见 4.2），当前阶段无作业或无记录时为 `null`；`verified` 阶段 `nextStage` 为 `null`。
- 权限：条目查看即可（`requirement:view`）。

### 3.2 阶段推进

- **路径**：`POST /api/project/requirements/:id/advance`
- **权限**：阶段操作权限；`archived` 条目返回 1000002001。
- **校验**：当前阶段为 `verified`（终态）返回 **1000011030**；出口证据不满足返回 **1000011029**（防御性兜底，前端以 3.1 预检置灰按钮）。
- **处理**（单事务）：写阶段事件（`action = advance`，`evidence` 附缺失清单为空的证据快照）→ 更新 `stage` 为下一阶段 → 尽力发起进入阶段的阶段作业（总览 2.3.3 规则 1）。
- **响应**：

```json
{
  "stage": "coverage",
  "job": { "taskId": "019a…", "type": "missing_point_analysis", "status": "pending" },
  "jobSkipReason": null
}
```

- `job` 为本次触发的作业；`jobSkipReason` ∈ `ai_disabled`（AI 未启用，阶段操作照常成功）/ `duplicate_in_flight`（同类型作业进行中，沿用既有任务，此时 `job` 为该进行中任务）/ `no_job_for_stage`（进入阶段无阶段作业）/ `null`（已发起，`job` 非空）。

### 3.3 阶段跳过

- **路径**：`POST /api/project/requirements/:id/skip`
- **请求体**：`{ "reason": "手工用例已覆盖，无需 AI 生成" }`（必填，≤ 500 字符，缺失返回 1000001001）
- **权限与阶段边界**：同 3.2（`verified` 拒绝 1000011030）；**免出口证据校验**（显式豁免，留痕）。
- **处理**：写阶段事件（`action = skip`，`evidence` 附被豁免的缺失清单）→ 更新 `stage` → 尽力发起阶段作业。
- **响应**：同 3.2。

### 3.4 阶段回退

- **路径**：`POST /api/project/requirements/:id/rollback`
- **请求体**：`{ "reason": "评审被打回，重新确认覆盖" }`（必填，≤ 500 字符）
- **权限与边界**：同 3.2；当前阶段为 `intake`（无可回退阶段）返回 1000011030；单步回退到上一阶段，多步回退多次调用。
- **处理**：写阶段事件（`action = rollback`，`reason` 必填）→ 更新 `stage` 为上一阶段 → 尽力发起进入阶段的阶段作业（重新增量计算，重复抑制规则见总览 2.3.3 / 2.3.4）。
- **响应**：同 3.2。

### 3.5 阶段时间线

- **路径**：`GET /api/project/requirements/:id/stage-events`
- **参数**：`pageNo`、`pageSize`
- **响应**：

```json
{
  "list": [
    {
      "id": "019b…",
      "fromStage": "intake",
      "toStage": "design",
      "action": "advance",
      "reason": null,
      "evidence": { "missing": [] },
      "operatedBy": "0195…",
      "operatedByName": "张三",
      "createdAt": "2026-10-01T08:10:00Z"
    }
  ],
  "total": 4
}
```

- 排序：`createdAt` 倒序；条目创建动作不产生事件（时间线首条即首次阶段操作）。

---

## 4. 阶段作业接口

### 4.1 发起阶段作业（手动发起 / 重跑）

- **路径**：`POST /api/project/requirements/:id/stage-jobs`
- **请求体**：

```json
{
  "stage": "design",
  "type": "case_generation",
  "extraText": "本次生成重点关注退款分支",
  "targetMode": "document",
  "documentId": "0198…"
}
```

- `stage` 必填，须等于条目当前阶段且为作业阶段（design / coverage / review / execution），否则返回 1000011030；`type` 缺省取该阶段默认作业（总览 2.3.3 映射表），显式传入时不匹配同样返回 1000011030；
- `type = case_generation` 时 `targetMode` 必填 ∈ `document` / `auto`：`document` 模式 `documentId` 必填（须属同项目，否则 1000011021）；`auto` 模式不接受 `documentId`；其余作业类型忽略这两个字段；`extraText` 可选（仅本次作业使用的附加需求文本，不入池），全部类型均可携带；
- **权限**：阶段操作权限 + 对应作业业务权限——`case_generation` = 目标文档编辑权限（`auto` 模式仅需条目侧权限）、`missing_point_analysis` = 项目成员即可、`review_planning` = 评审发起权限、`plan_planning` = 计划创建权限；违规返回 1000002001；
- **校验**：AI 未启用返回 **1000013001**；同 `type` + `target_id` 已有进行中任务返回 **1000013005**（非 best-effort，手动发起必须得到明确结果）；`archived` 条目返回 1000002001；
- **处理**：覆盖式新建——逻辑删除同 `(type, target_id)` 的既往终态记录（success / failed / cancelled）后创建任务（总览 2.3.3 规则 4），`target_id` = 条目 ID、`created_by` = 当前用户；执行逻辑按 `type` 分发至对应分册（`case_generation` 见《AI 生成用例》、`missing_point_analysis` 见《AI 评审与测试计划辅助》、`review_planning` / `plan_planning` 见《AI 评审与测试计划辅助》2.7）；
- **响应**：任务摘要（201）：

```json
{ "id": "0199…", "type": "case_generation", "targetId": "0198…", "status": "pending", "progress": 0, "createdAt": "2026-10-01T08:00:00Z" }
```

### 4.2 查询阶段作业

- **路径**：`GET /api/project/requirements/:id/stage-jobs`
- **响应**：每类型最新一条记录（覆盖式保留，见总览 2.3.3 规则 4），按类型固定顺序返回：

```json
[
  {
    "type": "case_generation",
    "stage": "design",
    "taskId": "0199…",
    "status": "success",
    "progress": 100,
    "errorMessage": null,
    "createdBy": "0195…",
    "createdAt": "2026-10-01T08:00:00Z",
    "updatedAt": "2026-10-01T08:02:10Z"
  }
]
```

- 不返回 `result`（作业产出已物化为提案，见 5.1；结果快照仅作审计，经通用任务接口查询）；`failed` 的 `errorMessage` 直接展示并提供重试。

### 4.3 取消 / 重试

- 复用《AI 基础设施详细设计说明书》异步任务通用接口：`POST /api/project/ai/tasks/:id/cancel` 与 `POST /api/project/ai/tasks/:id/retry`（权限与状态约束同该文档 1.2 / 1.3；阶段作业的进行中去重沿用 1000013005）。

---

## 5. 提案接口

路径前缀 `/api/project/requirements/proposals`；提案不存在或不属于当前项目返回 1000011028。

### 5.1 提案列表

- **路径**：`GET /api/project/requirements/:id/proposals`
- **参数**：`stage`（可选，缺省返回全部阶段）、`kind`（可选）、`status`（可选，可传 `active` = pending + expired）、`pageNo`、`pageSize`
- **响应**：

```json
{
  "list": [
    {
      "id": "019c…",
      "stage": "design",
      "kind": "case",
      "seq": 2,
      "taskId": "0199…",
      "payload": { "deltaType": "new", "targetMode": "document", "documentId": "0198…", "targetNodeId": "0199…", "modulePath": "…", "nodes": { "type": "case", "title": "支付失败回滚", "priority": "P1", "children": [] } },
      "status": "pending",
      "resolution": null,
      "reason": null,
      "resolvedBy": null,
      "resolvedByName": null,
      "createdAt": "2026-10-01T08:02:10Z",
      "updatedAt": "2026-10-01T08:02:10Z"
    }
  ],
  "total": 7
}
```

- 排序：`stage` 升序 → `kind` 分组 → `seq` 升序（同组内 `createdAt` 升序）；payload 结构见总览 2.2.2。

### 5.2 采纳（单条，编辑后接受）

- **路径**：`POST /api/project/requirements/proposals/:id/accept`
- **请求体**：`{ "payload": { … } }`（最终 payload，缺省沿用原 payload；部分合并仅覆盖传入字段，C11）
- **适用 kind**：`review_plan` / `plan_plan` / `structure` / `clarify`；`case` 提案必须经 5.3（携带挂载产物回执）。
- **状态校验**：提案状态须为 `pending` / `expired`，否则返回 **1000011031**；条目 `archived` 返回 1000002001。
- **处理**（单事务，失败整体回滚）：
  - `review_plan` / `plan_plan`：payload 校验（`title` 非空 ≤ 200；`participantIds` 过滤为同项目成员；`caseNodeIds` 须属同项目，越界返回 1000011021；时间可空）→ 按既有创建逻辑创建评审/计划实例 → 写血缘（`source = generated`）→ 提案置 `accepted`。**附加权限**：`review_plan` 需评审发起权限、`plan_plan` 需计划创建权限（1000002001）；
  - `structure`：按 `payload.modules` 经常规文档创建通道创建/复用项目模块与用例文档（不写脑图节点）→ 逐文档写血缘（artifact_type = document，`source = generated`）→ 提案置 `accepted`。**附加权限**：项目管理权限；
  - `clarify`：提案置 `accepted`（无落库，提示用户补充条目内容）。
- **响应**：

```json
{
  "proposalId": "019c…",
  "status": "accepted",
  "created": { "type": "review", "id": "019d…", "name": "登录模块回归评审" },
  "createdDocuments": [ { "documentId": "019e…", "title": "支付用例集", "modulePath": "订单模块/支付流程" } ]
}
```

（`created` 与 `createdDocuments` 按 kind 二选一出现，无关字段为 `null`。）

### 5.3 批量采纳（用例提案挂载回执）

- **路径**：`POST /api/project/requirements/proposals/accept-batch`
- **请求体**：

```json
{
  "proposalIds": ["019c…", "019d…"],
  "artifacts": [
    {
      "proposalId": "019c…",
      "documentId": "0198…",
      "createdCaseNodeIds": ["01a0…", "01a1…"],
      "modifiedCaseNodeIds": [],
      "removedCaseNodeIds": []
    }
  ]
}
```

- **适用 kind**：仅 `case`；每个 `proposalId` 必须有对应 `artifacts` 条目（缺失返回 1000001001）。
- **状态校验**：全部提案须为 `pending` / `expired`，否则 1000011031；**幂等**：已 `accepted` 的提案重复提交直接跳过（计入 `skipped`，不报错，支撑回执重试）。
- **校验**：`documentId` 须属同项目（1000011021）；节点 ID 须存在于库且属同项目（1000011022）；条目 `archived` 返回 1000002001。
- **处理**（单事务）：逐提案置 `accepted`（`resolved_by` / `resolved_at`）；`createdCaseNodeIds` 逐节点写血缘（artifact_type = case，`source = generated`）；`modifiedCaseNodeIds` 血缘已存在、仅登记采纳；`removedCaseNodeIds` 逻辑删除对应 `source = generated` 的血缘行（stale 提案的节点删除已在前端编辑内核完成，回执只负责血缘收敛）。
- **说明**：挂载（写节点）发生在前端编辑内核、先于本回执；回执失败**不回滚挂载**（已挂载节点保留，提案维持待处置，可重复提交回执直至成功）。
- **权限**：阶段操作权限（挂载本身已由编辑内核按文档编辑权限校验）。
- **响应**：`{ "accepted": 2, "skipped": 0, "lineageAdded": 14, "lineageRemoved": 0 }`

### 5.4 驳回

- **路径**：`POST /api/project/requirements/proposals/:id/reject`
- **请求体**：`{ "reason": "该场景已由既有用例覆盖" }`（可选，≤ 500 字符）
- **状态校验**：`pending` / `expired` → 否则 1000011031；`gap` 提案不适用本接口（走 5.5），返回 1000011031。
- **处理**：置 `rejected`（`resolved_by` / `resolved_at` / `reason`）；保留历史记录，重复提议抑制按指纹生效（总览 2.3.3 规则 5）。
- **响应**：`{ "proposalId": "019c…", "status": "rejected" }`

### 5.5 遗漏点处置（gap 专用）

- **路径**：`POST /api/project/requirements/proposals/:id/dispose`
- **请求体**：

```json
{ "action": "to_case", "text": "补充文本……", "targetMode": "document", "documentId": "0198…", "reason": null }
```

- `kind` 必须为 `gap`，否则 1000011031；状态须为 `pending` / `expired`，否则 1000011031。
- `action = to_case`（转为用例）：`targetMode` 必填（语义同 4.1 `case_generation`；`document` 模式 `documentId` 必填且校验归属）；`text` 可选（前端默认拼接该点 `title + description`，用户可编辑）。**处理**：与 4.1 相同的规则发起 `case_generation` 作业（同类型进行中返回 1000013005，此时处置不生效）→ 成功后同事务置 `accepted`（`resolution = to_case`）。附加权限同 4.1 `case_generation`。
- `action = wontfix`（标记不覆盖）：`reason` 必填（≤ 500 字符，缺失返回 1000001001）→ 置 `rejected`（`resolution = wontfix`）。
- `action = ignored`（忽略）：**不调用本接口**——前端本地隐藏，提案保持 pending（保留待处理），仍计入出口证据，确需通过时使用 3.3 `skip` 留痕（总览 2.3.4）。
- **响应**：`{ "proposalId": "019c…", "status": "accepted", "resolution": "to_case", "job": { "taskId": "019f…", "type": "case_generation", "status": "pending" } }`（`wontfix` 时 `job` 为 `null`）

---

## 6. 血缘接口

### 6.1 血缘查询（条目 → 对象）

- **路径**：`GET /api/project/requirements/:id/lineage`
- **参数**：`artifactType`（可选，缺省返回全部分组）
- **响应**：

```json
{
  "groups": [
    {
      "artifactType": "case",
      "total": 14,
      "items": [
        { "artifactId": "01a0…", "source": "generated", "name": "支付失败回滚", "location": "支付用例集" },
        { "artifactId": "01a1…", "source": "manual", "name": "手工补充场景", "location": "通用用例集" }
      ]
    },
    {
      "artifactType": "review",
      "total": 1,
      "items": [ { "artifactId": "019d…", "source": "generated", "name": "登录模块回归评审", "location": null } }
    }
  ]
}
```

- 分组固定顺序：`case` → `document` → `review` → `plan` → `bug`；`name` / `location` 由后端按对象类型联表补全（case = 节点标题 + 所属文档名，document = 文档名，review / plan = 实例名，bug = 缺陷标题）；已删除对象的血缘行由对象删除联动清理（见 6.4），查询不做过滤兜底。

### 6.2 手动关联

- **路径**：`POST /api/project/requirements/:id/lineage`
- **请求体**：`{ "artifactType": "review", "artifactId": "019d…" }`
- **发起两侧**：
  - 对象表单侧（评审/计划/缺陷/文档的创建与编辑流程）：由对象自身接口完成创建/编辑鉴权后调用本接口，权限即对象自身操作权限（SRS 1.6 口径）；后端校验对象归属当前项目——`document` → 1000011021、`case` → 1000011022、`review` → 1000011011、`bug` → 1000011023、`plan` → 1000011010；
  - 条目工作台侧（LineagePanel）：阶段操作权限 + 对象同项目归属校验。
- **幂等**：同 `(requirement, artifactType, artifactId)` 已存在时直接返回现有记录（`source` 保持原值，不覆盖）；条目 `archived` 返回 1000002001。
- **响应**：新建的血缘记录 `{ "id": "01a2…", "artifactType": "review", "artifactId": "019d…", "source": "manual" }`。

### 6.3 解除关联

- **路径**：`DELETE /api/project/requirements/:id/lineage/:artifactType/:artifactId`
- **校验**：仅 `source = manual` 可解除，`generated` 返回 **1000011032**（自动血缘随条目或对象删除而清除，不可手动解除）。
- **权限**：条目阶段操作权限，或目标对象的编辑权限。
- **幂等**：无对应记录时直接成功（200）。
- **响应**：`null`。

### 6.4 反向追溯（对象 → 条目）

- **路径**：`GET /api/project/lineage/artifacts/:artifactType/:artifactId/requirements`
- **参数**：`includeArchived`（可选，缺省 false——仅返回 active 条目；true 时含归档条目并在响应中标注）
- **响应**：

```json
[
  { "requirementId": "0198…", "title": "登录模块需求", "stage": "design", "status": "active", "source": "generated" }
]
```

- **删除联动**：评审、计划、缺陷、文档、用例节点删除时，同事务逻辑删除其全部对应血缘行（经 `idx_rl_artifact` 定位）；血缘不阻断任何对象的删除。

---

## 7. 文档关联（脑图文档 ↔ 条目默认上下文）

- **路径**：`GET / PUT /api/project/documents/:docId/requirements`
- **GET**：返回该文档关联的 active 条目摘要（`[{ "id", "title", "stage", "source" }]`，archived 条目过滤不展示、关联记录保留），由血缘查询实现（artifact_type = document）；供脑图 AI 入口（补全等）默认带入上下文。
- **PUT 请求体**：`{ "requirementIds": ["0198…", "0199…"] }`（全量设置，差量增删血缘行，`source = manual`）
- **归属校验**：`:docId` 必须属于 `X-Active-Project` 对应项目（联表 test_case_module 校验），不一致返回 1000011021；`requirementIds` 中的条目须属于同一项目，且仅接受 active 条目（含 archived 返回 1000002001）。
- **权限**：文档编辑权限即可维护。

---

## 8. 项目开关

### 8.1 查询开关

- **路径**：`GET /api/project/requirements/settings`
- **响应**：`{ "autoAdvance": false }`（字段语义见总览 2.3.3 规则 2；项目无记录时取代码内置默认 false）

### 8.2 设置开关

- **路径**：`PUT /api/project/requirements/settings`
- **请求体**：`{ "autoAdvance": true }`
- **权限**：项目管理权限（违规返回 1000002001）。
- **响应**：`{ "autoAdvance": true }`

---

## 9. 错误码

本分册新增错误码（登记 `ErrorCodeConstants`，并回补《项目工作区详细设计说明书》总览错误码表）：

| 错误码 | 说明 | HTTP 状态 |
| ---- | ---- | ---- |
| 1000011028 | 需求条目不存在或不属于当前项目 | 404 |
| 1000011029 | 阶段出口证据未满足，无法推进 | 409 |
| 1000011030 | 当前阶段状态不允许该操作 | 409 |
| 1000011031 | 提案当前状态不允许该操作 | 409 |
| 1000011032 | 生成来源的血缘记录不可手动解除 | 409 |

沿用：1000001001（参数校验）、1000002001（无权限）、1000011010 / 1000011011 / 1000011021 / 1000011022 / 1000011023（对象不存在）、1000013001（AI 未启用）、1000013005（同类任务进行中）。

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-01 | 补建修改记录；由轻量需求池接口重构为需求工作流接口：条目（含阶段）、阶段状态预检/推进/跳过/回退与时间线、阶段作业发起与查询、提案处置（采纳/批量回执/驳回/遗漏点处置）、血缘正反向追溯与手动关联、文档关联改为血缘实现、项目自动推进开关、新增错误码 1000011028–1000011032 |
