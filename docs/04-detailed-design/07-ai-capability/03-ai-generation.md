# 软件测试平台——AI+需求 生成链

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对 **AI+需求 生成链**能力域进行详细设计：定义测试设计生成与评审 / 计划圈选建议两类任务的输入、产物结构、阶段进度、确认落库与追溯建边规则。任务资源、产物确认协议、配置与用量的公共约定见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档不重复定义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/03-srs-ai-generation.md`（US-AI-005 ~ 009）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md` 第 4.2 / 4.3 节。
- 追溯建边、缺口列表与影响处置接口：`docs/04-detailed-design/05-trace-matrix.md`。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（任务资源 3.6、错误码分段 6）
- 需求模块：`docs/04-detailed-design/06-requirement-management/02-requirement-management.md`（生成输入的来源实体）
- 评审 / 计划快照机制：`docs/02-high-level-design/03-function-testing/02-hld-function-testing.md` 第 3.3 节

---

## 2. 数据设计

本分册**不新增表**：

- 任务与产物：存 `ai_task`（`type = test_design_generation / review_selection / plan_selection`，产物明细在 `result`，确认记录在 `ai_artifact_confirm`）；
- 追溯边：采纳落库与圈选确认时经矩阵服务写入 `trace_edge`（DDL 见 `docs/04-detailed-design/05-trace-matrix.md` 2.2）；
- 生成目标（模块 / 脑图文档 / 用例）落库为既有业务表数据，本分册只定义写入规则（4.3）。

---

## 3. 接口详细设计

### 3.1 通用约定

- 全部执行经总册 3.6 统一任务资源；本文档只定义两类任务的 `type`、`input`、产物与承接语义。
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

- **响应**：总册 3.6.2 任务对象；前端跳转任务进度页。
- **校验**（错误码见 6）：需求存在（1000018201）、全部已确认（1000018202）、落位模块合法（1000018203）、参数取值合法（1000018205）、同项目同输入去重（1000018209）。

### 3.3 生成进度与阶段

- **查询**：总册 3.6.3 `GET /api/ai/tasks/{taskId}`；本任务类型的 `phase` 枚举（按序推进，`progress` 按阶段加权）：

| 序 | phase | 说明 |
| ---- | ---- | ---- |
| 1 | 解析需求 | 需求快照解析与 RAG 上下文装配 |
| 2 | 生成模块结构 | 产出 `module_suggestion` |
| 3 | 生成脑图文档 | 产出 `mindmap_document_suggestion` |
| 4 | 标记用例节点 | 产出 `test_case_suggestion` 骨架 |
| 5 | 填充用例属性 | 优先级 / 前置条件 / 步骤 / 预期 / 标签 |
| 6 | 建立追溯边 | 采纳时随落库事务写入（此处为预校验） |

- 取消与重试按总册 3.6.4：取消 / 失败任务**不产生任何落库数据与追溯边**。

### 3.4 产物结构与审核详情

- **产物清单**：总册 3.6.3 `artifacts` 摘要；**单个产物详情** `GET /api/ai/tasks/{taskId}/artifacts/{artifactKey}`。
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

- `sourceRefs` 为来源引用（需求 + 原文引语），**来源缺失的产物不得进入待确认态**（概要 8 安全设计）；
- `suspectedDuplicateOf` 非空表示与既有模块 / 用例疑似重复，前端在采纳时高亮提示（1000018204 由人工决定：改名采纳或驳回）。

### 3.5 产物确认落库

- **路径**：总册 3.6.5 `POST /api/ai/tasks/{taskId}/artifacts/confirm`。
- **本类型 `target` 结构**：

```json
{ "moduleId": "…", "position": "child" }
```

- **动作**：单条采纳 / 整树采纳（前端按 `parentKey` 展开为逐条请求批次）/ 编辑后采纳（`content` 传入编辑值）/ 驳回（`note` 可选）。
- **承接**（总册 3.6.1「生成链采纳服务」）：
  1. 逐项事务：写模块 / 脑图文档 / 用例节点与用例实体（**均为新增，不覆盖既有数据**）；
  2. 每个 `isTestCase = true` 的节点生成一条测试用例实体，回写 `adopted_ref = { moduleId / documentId / nodeId / caseId }`；
  3. 同事务内经 `TraceEdgeWriter` 写 `derivation` 边：`requirement → module / mindmap_document / test_case`，`status = ai_created`、`established_by = ai`、`target_version` 取新实体版本；
  4. 任一步失败整项回滚，确认状态保持 `pending` 可重试。

### 3.6 评审 / 计划圈选建议

- **发起**：`POST /api/ai/tasks`

```json
{
  "type": "review_selection",
  "input": { "requirementIds": ["…"], "moduleIds": ["…"], "roundCount": 1 }
}
```

| type | input | 产物 kind | 说明 |
| ---- | ---- | ---- | ---- |
| review_selection | `requirementIds?`、`moduleIds?`（至少一项）、`roundCount?`（默认 1，仅 plan） | `review_selection` / `plan_selection` | 建议纳入圈选的用例子集，附理由；plan 可按轮次分组 |

- **产物详情**：

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
- **承接**：沿用**既有评审 / 计划创建流程**生成快照（AI 不改变快照机制），创建成功后经矩阵服务写 `snapshot_ref` 边（`test_case → test_review / test_plan`，记录圈选时用例 `target_version`）；评审意见与计划项挂到用例级引用边。

---

## 4. 业务逻辑设计

### 4.1 输入有效性与快照执行

- 仅 `status = confirmed` 的需求可作为输入；`draft / changed / archived` 不可选（入口置灰并说明原因，服务端 1000018202 兜底）。
- **发起时对输入需求做版本快照**（`input.requirementSnapshots` 存关键字段摘要）：执行期间需求被变更，任务按发起时快照继续；产物确认时比对当前状态，`sourceRefs` 区域展示「来源需求已变更」标记，由人工决定采纳或驳回。

### 4.2 产物确认后的状态

- 确认后 AI 不再自动覆盖；人工编辑过的产物仅在**显式重新生成并二次确认**后可被覆盖——重新生成 = 对驳回产物附 `note` 走重试（总册 3.6.4），生成的新任务完成后需再次确认。
- 驳回产物保留在 `ai_task.result` 与审计中供回查，不落库。

### 4.3 采纳落库与建边事务

1. 落库目标项目 = 确认请求 `X-Active-Project`；目标模块按 `target.moduleId`，缺省时按 `input.placement`；
2. 疑似重复（`suspectedDuplicateOf` 非空）的产物**不阻断**采纳，但前端强提示，人工可改为编辑后采纳；
3. 「业务落库 + `ai_artifact_confirm` + `trace_edge` 写入」同一事务（详见 3.5 与 `05-trace-matrix.md` 4.1），保证落库必有边；
4. 编辑后采纳以编辑值落库，`adopted_edited` 记录原 AI 值与编辑差异（审计）。

### 4.4 圈选确认与快照语义

- 圈选建议**不自动创建**评审 / 计划；确认时走既有创建流程，快照内容 = 圈选用例的当前版本；
- 用例集合校验：`caseIds` 必须存在于当前项目（1000018207），圈选外增删由人工在创建流程中调整（调整后采纳 = 以调整结果提交 `target`）；
- 上游用例后续变更 → 引用边转 `stale`（`05-trace-matrix.md` 4.1），计划 / 评审侧按既有同步机制处理。

### 4.5 缺口引导与影响处置

- 缺口列表（未覆盖需求 / 孤儿用例 / 未经评审 / 未纳入计划）与「发起生成 / 发起评审 / 加入计划」引导：接口见 `05-trace-matrix.md` 3.9，前端在矩阵页与生成入口联动；
- 影响处置（重新生成建议 / 标记需重新评审 / 确认无影响）：接口见 `05-trace-matrix.md` 3.10；处置只改追溯边标记，不修改下游内容。

---

## 5. 前端设计

### 5.1 入口与发起配置

- 需求列表 / 详情多选后「AI 生成测试设计」→ `GenerationConfigDialog`（目标项目与模块落位、生成范围回显、用例粒度偏好）；不可选需求置灰并提示原因（草稿 / 已变更 / 已归档）。

### 5.2 任务进度（任务详情页上半区）

```
AiTaskDetailPage（/workspace/projects/ai/tasks/:taskId）
├── StageTimeline（六阶段时间线：当前阶段高亮，失败阶段标红）
├── ProgressHeader（进度条、产物数量、失败原因摘要）
├── Actionbar（取消、失败后重试）
└── ArtifactCountBadges（模块/文档/用例计数）
```

提交成功后由发起对话框跳转本页，页面结构与状态分支见总册 5.2。

### 5.3 产物审核区（三层树，详情页下半区）

- 复用总册 5.2 任务详情页内的 `AiArtifactReviewPanel`，生成链扩展：
  - 三层树渲染（模块 → 文档 → 节点，用例节点带属性徽标）；
  - 内容对比视图：AI 建议 vs 既有数据（编辑后采纳时），`sourceRefs` 引用可点击回跳需求原文；
  - 「来源需求已变更」标记、`suspectedDuplicateOf` 疑似重复警示；
  - 单条采纳 / 整树采纳 / 编辑后采纳 / 驳回附反馈（触发重试）。

### 5.4 圈选建议审核

- 圈选建议卡片列表（用例条目 + 理由），支持增删调整后提交既有评审 / 计划创建弹窗；确认前展示将创建的记录数量。

### 5.5 状态分支

- 任务排队 / 进行中（阶段进度）/ 失败（原因 + 重试）/ 已取消（灰态）；
- 产物全驳回、部分采纳的回执列表（逐项成败）；
- AI 未启用 / 未配置模型时入口隐藏（总册 4.5）。

---

## 6. 错误码定义

号段：**1000018201–1000018249（生成链）**，以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018201 | 404 | 输入需求不存在 |
| 1000018202 | 400 | 存在非已确认需求，不可作为生成输入 |
| 1000018203 | 400 | 落位目标模块不存在或不属当前项目 |
| 1000018204 | 409 | 产物与既有数据疑似重复，需人工处理 |
| 1000018205 | 400 | 生成参数非法（粒度、条数、落位方式） |
| 1000018206 | 400 | 圈选建议输入非法（范围为空或参数越界） |
| 1000018207 | 404 | 圈选用例不存在 |
| 1000018208 | 403 | 无产物落库资源权限 |
| 1000018209 | 409 | 已存在进行中的同输入生成任务 |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../service/ai/task/handler/TestDesignGenerationHandler` | 生成任务执行器（阶段上报、产物抽取） |
| `server/.../service/ai/task/handler/ReviewSelectionHandler`、`PlanSelectionHandler` | 圈选建议执行器 |
| `server/.../service/ai/task/adopt/GenerationAdoptService + Impl` | 三层产物采纳落库（调用既有模块 / 文档 / 用例服务） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018201–1000018209 |
| `web/src/components/ai/GenerationConfigDialog.vue`、`ArtifactTreeReview.vue` 及子组件 | 发起与审核前端 |
| `web/src/composables/useGenerationTask.ts` + 单测 | 任务进度与阶段推进逻辑 |

- **数据库**：无新增表、无迁移（写入均为既有业务表 + `trace_edge`，见 2 节）。
- **OpenAPI**：`POST /api/ai/tasks` 的 `input` 按 `type` 给出 oneOf 说明；产物详情返回结构随任务详情分组暴露。

**测试要点（C8 ≥ 70%）**

- 后端：输入状态校验矩阵、快照执行（执行中变更不中断）、落库 + 建边事务回滚、编辑后采纳差异记录、圈选用例存在性与创建参数校验、同输入去重、取消不留数据；
- 前端：配置对话框校验与置灰、六阶段时间线、三层树审核四类动作、疑似重复与来源变更提示、圈选调整后提交。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-03 | 任务进度与产物审核统一为任务详情页（AiTaskDetailPage），删除抽屉/全屏页并存口径 |
