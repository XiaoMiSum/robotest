# 软件测试平台——AI 辅助功能

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**辅助功能**能力域进行详细设计：定义用例优化、测试用例执行顺序推荐、脑图生成用例三项能力的任务输入、提案结构与采纳执行口径。任务资源、提案确认协议与错误码分段见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档只定义本域的业务入口与提案语义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/06-srs-ai-assisted-features.md`（US-AI-017 ~ 019）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`。
- 权限：免 RBAC 权限码校验（登录即可）；三项能力的入口由前端按节点只读 / 已处理等业务状态、AI 启用状态显隐，后端任务发起与采纳仅保留任务归属可见性与项目成员校验；范围限于当前项目。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（3.6 任务资源、6 错误码分段）

---

## 2. 数据设计

本分册**不新增表**，仅补一列：

- 辅助任务、执行与轨迹：`ai_task / ai_task_execution / ai_task_trace_event`（`type = case_optimize / mindmap_case_generation / plan_order`）；
- 提案与采纳：`ai_task_proposal / ai_task_adoption`（提案 `kind`：`case_optimize_suggestion / mindmap_case_suggestion / order_suggestion`）；
- 采纳写既有节点 / 计划表：用例优化经脑图 WS 节点持久化管道落库、不写用例修改记录，执行顺序推荐经采纳服务只改排序字段；
- 用例标签随补全建议采纳写入 `ft_case_node.tags`（`jsonb` 数组，允许 NULL、不设默认值、不建物理外键），列 DDL 以 `server/src/main/resources/db/schema.sql` 为唯一事实源，满足 C5；该列不进向量索引，也不参与脑图渲染。

---

## 3. 接口详细设计

### 3.1 通用约定

- 全部执行经总册 3.6 AI Task 任务资源；本文档只定义三类任务的 `type`、`input` 与提案语义，提案的节点操作由脑图前端执行、总册 3.6.5 确认协议仅记录采纳状态。
- 上下文头：`X-Active-Workspace` + `X-Active-Project`（发起、确认一致），发起缺 `X-Active-Project` 头回 1000002008（缺少上下文请求头）；响应 `Result<T>`，示例**仅展示 `data`**。
- 权限：任务发起与采纳免 RBAC 权限码校验（仅保留任务归属可见性与项目成员校验）；入口与动作显隐由前端按节点只读 / 已处理等业务状态控制。

### 3.2 用例优化

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "case_optimize",
  "input": {
    "documentId": "…",
    "nodeIds": ["…", "…"],
    "scope": { "requirementIds": ["…"] }
  },
  "waitSeconds": 10
}
```

| input 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| documentId | uuid | 是 | 所属脑图用例文档（节点归属校验，否则 1000018301） |
| nodeIds | uuid[] | 是 | 选中的用例节点（1–50，否则 1000018302）；非用例节点拒绝（1000018307） |
| scope.requirementIds | uuid[] | 否 | 显式上游需求范围，作为字段补全与级别推荐的需求上下文 |

- **提案**（`kind = case_optimize_suggestion`，逐节点一条，`confirmStatus = pending`）：

```json
{
  "key": "node-3",
  "kind": "case_optimize_suggestion",
  "parentKey": null,
  "title": "验证码 60 秒重发限制",
  "content": {
    "nodeId": "…",
    "operations": [
      { "op": "update", "nodeId": "…", "field": "title",
        "path": "登录用例 › 步骤 1", "existing": "输入邮箱", "suggested": "输入邮箱和密码",
        "reason": "补充账号类型说明" },
      { "op": "update", "nodeId": "…", "field": "priority",
        "path": "登录用例", "existing": "P1", "suggested": "P0",
        "reason": "上游需求 REQ-001 优先级为高" },
      { "op": "update", "nodeId": "…", "field": "tags",
        "path": "登录用例", "existing": ["登录"], "suggested": ["登录", "限制"],
        "reason": "补充限制类标签" },
      { "op": "add", "parentId": "…", "nodeType": "step",
        "path": "登录用例", "title": "输入图形验证码", "sortOrder": 2,
        "reason": "上游需求要求校验图形验证码" }
    ],
    "sourceRefs": [{ "type": "requirement", "id": "…", "title": "REQ-001", "quote": "验证码 60 秒内不可重发…" }]
  },
  "confirmStatus": "pending"
}
```

- `nodeId` 为被优化用例节点，作为归属 / 类型校验锚点（否则 1000018301 / 1000018307）；`operations` 为该用例的操作指令清单，`op` 取 `update` / `add`：
  - `update`：按 `nodeId` 定位目标节点（须取自本次产出的「可操作节点清单」，即目标用例及其后代；非法引用在清洗阶段丢弃），`field` 取 `title`（用例节点或属性子节点文本）/ `priority` / `tags`（后两者仅用例节点有效）；`existing` 为节点当前值（`null` 或空数组表示空缺），仅供人工核对，`suggested` 为建议值；`priority` 的 `existing / suggested` 取值 `P0 / P1 / P2 / P3`，与落库级别一致，`suggested == existing` 时同样产出并正常展示，不折叠隐藏；
  - `add`：按 `parentId` 定位父节点（目标用例或其任一后代，非法引用在清洗阶段丢弃），`nodeType` 取 `precondition / step / expected / normal`（不新增 `case` 节点，非法类型在清洗阶段丢弃），`title` 为新节点文本，`sortOrder` 为可选组尾序号；
  - 每条操作携带 `path`（祖先路径，供对照面板定位展示）与 `reason`（建议理由）；
- 每个选中节点必产一条提案，`operations` 可为空（无建议时按无变化展示）；模型输出引用的节点不存在 / 跨文档 / 字段或类型非法时，该条指令在清洗阶段丢弃，不产生无效建议。

- **确认**：总册 3.6.5，动作 `adopted / adopted_edited / rejected`；本类型 `target`：

```json
{ "documentId": "…" }
```

  - 脑图面板采纳时，先由前端按 `operations` 在当前脑图树上执行节点操作（`update` 原地改写目标节点 `field`，`add` 在 `parentId` 下创建新节点），再调用确认协议记录采纳状态；确认协议的后端 adopter 不执行任何节点变更。本类型操作仅在脑图面板执行，任务详情页不承接用例优化采纳。
  - `adopted_edited` 的 `content` 为编辑后的操作清单 `{ "operations": [ { "op": "update", "nodeId": "…", "field": "title", "suggested": "…" } | { "op": "add", "parentId": "…", "nodeType": "step", "title": "…" } ] }`，前端以人工编辑值执行；编辑载荷仅覆盖建议值，定位字段（`op` / `nodeId` / `parentId` / `field` / `nodeType`）须与提案一致，否则整条按输入非法处理。

- **前端执行**（脑图面板）：`update` 按 `nodeId` 定位节点后原地改写 `field`（`title` 写节点文本、`priority` 写级别列、`tags` 写标签列），节点 ID、层级与类型不变，不删旧插新；`add` 按 `parentId` 定位父节点并用脑图既有新建节点口径生成 UUID 追加子节点（`ai_generated = true`，无物理外键）。两类变更经脑图既有 WS 节点持久化管道落库并触发向量索引重嵌，`title` / `priority` 走 `update_attrs`、`add` 走 `add_node`、`tags` 走扩展后的 `update_attrs`；提案的 `existing` 仅用于对照展示，不作为写入前置条件。采纳时另按提案 `nodeId` 给被优化用例节点写入 `ai_generated = true`（复用既有 AI 生成标识，不新增字段、不扩展评审 / 计划快照），与节点操作同批经 `update_attrs` 落库，目标节点已不存在时跳过。

### 3.3 测试用例执行顺序推荐

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "plan_order",
  "input": { "planId": "…", "round": 1 },
  "waitSeconds": 10
}
```

| input 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| planId | uuid | 是 | 测试计划（须属当前项目，否则 1000018303） |
| round | number | 否 | 轮次，默认当前激活轮次 |

- **提案**（`kind = order_suggestion`，`confirmStatus = pending`）：

```json
{
  "key": "order-1",
  "kind": "order_suggestion",
  "title": "执行顺序建议（12 项）",
  "content": {
    "planId": "…",
    "items": [
      { "nodeId": "…", "caseTitle": "TC-001 获取验证码", "suggestedRank": 1, "reason": "登录前置，失败快反馈" }
    ],
    "beforeOrder": ["TC-002", "TC-001"],
    "afterOrder": ["TC-001", "TC-002"]
  },
  "confirmStatus": "pending"
}
```

- **确认**：总册 3.6.5；承接服务按 `suggestedRank` 更新计划内执行顺序（`target = { planId, round }`）；**不修改计划关联用例集合与快照内容**，仅改排序字段，一次事务完成，人工最终顺序为准。

### 3.4 脑图生成用例

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "mindmap_case_generation",
  "input": {
    "documentId": "…",
    "requirementId": "…"
  },
  "waitSeconds": 10
}
```

| input 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| documentId | uuid | 是 | 当前脑图用例文档（须属当前项目，否则 1000018311） |
| requirementId | uuid | 是 | 单条已确认需求（不属当前项目 1000018309，非已确认 1000018310） |

- **提案**（`kind = mindmap_case_suggestion`，逐用例一条，`confirmStatus = pending`）：

```json
{
  "key": "case-1",
  "kind": "mindmap_case_suggestion",
  "parentKey": null,
  "title": "获取验证码",
  "content": {
    "priority": "P0",
    "precondition": ["用户处于登录页"],
    "steps": ["输入手机号", "点击获取验证码"],
    "expected": ["提示验证码已发送"],
    "tags": ["登录"],
    "sourceRefs": [{ "type": "requirement", "id": "…", "title": "REQ-001", "quote": "支持短信验证码…" }]
  },
  "confirmStatus": "pending"
}
```

- `priority` 取 `P0 / P1 / P2 / P3`；`precondition / steps / expected / tags` 为字符串数组，`steps` 与 `expected` 一一对应按短边截齐；`sourceRefs` 必填且指向当前项目需求，来源缺失的提案在清洗阶段丢弃；
- **确认**：总册 3.6.5，动作 `adopted / rejected`；本类型 `target` 为 `{ "documentId": "…" }`。前端在脑图面板勾选提案后，先按提案在当前树上创建用例节点及其前置 / 步骤 / 预期子节点（追加到根节点下，`aiGenerated = true`），再调用确认协议记录采纳状态；后端 adopter 不执行任何节点变更，任务详情页不提供审核操作，只回看执行过程与提案。
- 同输入（同 `documentId` + `requirementId`）存在进行中任务时重复发起返回 1000018312；任务被中止、执行错误或预算耗尽时不产生提案与节点。

---

## 4. 业务逻辑设计

### 4.1 输入有效性

- 节点集合按 `documentId` 归属校验（1000018301 / 1000018307）；单次上限 50 个节点（1000018302），超限提示分批执行；
- `plan_order` 输入的计划关联用例为空时返回 1000018304，入口置灰说明原因。

### 4.2 操作指令的覆盖口径

- **原地更新**：`update` 由脑图前端按 `nodeId` 定位目标节点并原地改写 `field`，节点 ID、层级与类型不变，不采用删旧插新；建议值与现状一致时不写，避免无谓写入；建议值为空（空白串 / 空数组）时视为无建议，不修改目标字段；
- **直接新增**：`add` 由脑图前端在 `parentId` 下创建新节点，节点 ID 沿用脑图新建节点的 UUID 生成口径、`ai_generated = true`、`version = 1`，序号追加到同类型组尾（或按给定 `sortOrder`）；父节点可为目标用例或其任一后代；不新增 `case` 节点；
- **以当前树为准**：提案的 `existing` 仅供人工核对写入前的现状；采纳在脑图当前树上按 ID 定位，目标节点或父节点已不存在时跳过该条指令并在回执中标记；
- **优先级与标签**：`update` 的 `field = priority` / `tags` 按 `suggested` 写入对应列；级别一致项照常展示并可执行，采纳后由脑图持久化管道落库并触发向量索引重嵌；
- **AI 标识**：采纳时按提案 `nodeId` 给被优化用例节点写入 `ai_generated = true`，复用既有 AI 生成标识列，与节点操作同批经脑图 `update_attrs` 落库；不新增字段、不扩展评审 / 计划快照，目标节点已不存在时跳过打标，已为 true 时幂等不重复写入；
- 同一用例的全部操作合并为一条提案、一次采纳动作；非法节点引用在提案生成阶段清洗丢弃，不在采纳阶段产生跨文档写入；
- 节点变更经脑图既有 WS 持久化管道落库（含权限重校验与向量索引重嵌），不写用例修改记录；确认协议仅记录提案采纳状态，任务详情页不参与用例优化的节点执行。

### 4.3 顺序推荐的边界

- 推荐依据限定平台内数据：关联用例的前置条件 / 步骤 / 预期文本、祖先路径（依赖线索）、`ft_plan_execution_record` 的用例级历史失败率、用例优先级；生成时由 `PlanOrderTypeAdapter` 从计划快照树与执行记录实际读取并拼入 `planContext`，依据缺失的维度显式留空、不臆造，不引入外部信息；
- 采纳只改快照节点的 `sort_order`，不改关联集合与快照的标题 / 类型 / 优先级 / `is_associated` / `last_result` 等属性，一次事务完成，人工最终顺序为准；
- 采纳或人工调整后的 `sort_order` 为计划的稳定排序：`syncPlan` / 编辑重规划 / 差量调整用例同步快照属性时不覆盖已有顺序，仅对补入的用例按原始节点顺序补位，保证建议顺序不因同步丢失；
- 计划详情页按当前 `sort_order` 在用例节点展示执行序号并提供「下一个待执行用例」引导：按排序定位并高亮当前应执行的未执行用例，标记结果后自动推进到下一个；引导仅作辅助，用户仍可自由点选任意用例标记，不改变执行记录提交方式。

### 4.4 脑图生成用例的边界

- 需求与文档均按当前项目校验；需求仅接受 `confirmed`，发起时对需求做快照，执行期间变更按快照继续并在面板提示「来源需求已变更」；
- 提案在勾选插入前不落库；插入为新增节点，不覆盖既有节点，不修改既有树的层级与 ID；
- 插入位置固定为当前文档根节点下：每个被勾选提案生成一个 `case` 节点（`priority` / `tags` 写入列、`aiGenerated = true`、`requirementId` 随 `add_node` 帧写入），其下依次追加 `precondition` / `step` / `expected` 子节点（`aiGenerated = true`）；`steps` 与 `expected` 按短边截齐；
- 节点变更经脑图既有 WS 持久化管道落库并触发向量索引重嵌；确认协议只记录采纳状态，后端不写业务节点；
- 采纳时为新建用例节点自动写入与来源需求的关联（需求取提案 `sourceRefs` 首项，缺失回退任务输入 `requirementId`）：来源需求 id 写入用例节点 data 的 `requirementId`，随 `add_node` 帧与节点一同落库，不再单独调用接口、不再需要「节点未落库」重试；需求不属于当前项目（1000018001）时由后端错误帧提示，不回滚已插入节点与采纳状态；
- 任务详情对 `mindmap_case_generation` 注册只读展示策略，不提供采纳 / 编辑后采纳 / 驳回动作；提案状态由脑图面板的采纳动作驱动。

---

## 5. 前端设计

### 5.1 入口

```
脑图用例节点悬浮窗
└── 「AI 优化」（当前节点 → case_optimize）

脑图编辑器工具栏（最右侧）
└── 「AI覆盖雷达」→ 查当前文档最近任务 → 命中回显 / 未命中选需求（单选）→ AI 生成面板（mindmap_case_generation）

测试计划详情
└── 「执行顺序建议」（plan_order）
```

点击「AI 优化」先按当前节点查询最近一次任务（`GET /api/ai/tasks/{type}/latest`，项目内任意成员发起的任务均可复用）：命中后按状态回显——`running` 仅回显进度、不新建任务或执行实例；`aborted` 展示原因与「再次执行」（沿用原任务新建执行实例）；`proposals_ready` / `closed` 展示提案并提供「重新执行」（按同一输入新建任务，原任务及提案、采纳记录保留只读回看）；无任务时发起 `case_optimize`（交互 2.4）。「AI覆盖雷达」按当前文档查询最近一次 `mindmap_case_generation` 任务（同一 `/latest` 端点省略 `nodeId`，按 `documentId` 匹配）：命中则直接打开面板按状态回显进度 / 提案 / 终止原因，不再弹出需求选择；未命中才经需求单选弹窗确认后按 `{ documentId, requirementId }` 发起新任务，结果在独立面板内回显。查询失败只提示错误、不自动发起；仅取最近一次，更早任务在任务中心回看。来源需求从任务数据回显，`proposals_ready` / `closed` 态「重新执行」按该需求与当前文档新建任务。

### 5.2 组件结构

```
AssistComparePanel（通用对照面板，用例优化使用）
├── Header（标题与处理状态；多节点发起时下拉切换核对节点，采纳后自动推进到下一个待处理节点）
├── OperationList（节点操作指令列表：每条显示「节点路径 · 字段」两栏对照（现有 vs 建议）或「在节点路径下新增类型节点」，附理由与新增 / 变更徽标；无变化项折叠为「无变化 N 项」，可展开核对）
├── SourceRefsLinks（来源引用，点击跳需求详情）
└── Actionbar（采纳、驳回；置灰原因行与逐项回执）

PlanOrderPanel
├── OrderList（拖拽调整，序号徽标，建议理由悬浮）
├── BeforeAfterDiff（采纳前/后顺序对照，按当前顺序实时刷新）
└── Actionbar（应用建议、放弃，应用后 toast 提示）

AiCaseGenerationPanel（脑图生成用例，以对话框呈现，高度固定约 88vh，上下留白各约 6vh）
├── Header（所选需求、任务进度 / 失败原因 / 来源变更提示）
├── CaseMindTree（复用用例选择器的脑图式勾选树，展示提案的用例与前置 / 步骤 / 预期子节点，支持关键字、优先级过滤与全选；已完成任务默认隐藏已采纳用例，勾选「展示已采纳用例」后以只读勾选回显；候选卡片区内部滚动，外层不额外包裹容器）
└── Actionbar（采纳、重新执行、关闭；采纳后自动关联来源需求并回执逐项成败）
```

各结构块落在对应 `.vue` 组件文件内，不拆独立组件文件（见 7 节文件清单）。用例选择器增加「注入树」模式：传入 AI 提案映射的 `TestCaseNode` 树时跳过后端文档加载，确认回传所选用例节点，复用既有勾选与脑图式渲染能力；勾选用例时按整棵子树定位属性子节点，插入与回执仍只按用例提案 key 计数。注入树模式接收「展示已采纳用例」开关与已采纳用例 key 集合：关闭时提案树剔除已采纳用例，开启时这些用例默认勾选且不可取消，仅作回看；「采纳」按提案确认状态区分，先对新勾选用例建节点，再回传采纳状态，已采纳用例不重复插入也不重复回传。每个注入节点随行携带其来源需求 id（提案 `sourceRefs[0].id`，缺失回退任务输入）；插入执行器把该 id 写入用例节点 data 的 `requirementId`，随 `add_node` 帧落库。AI 生成面板与 AI 优化面板的对话框容器（header / body / footer）四边 padding 统一收紧，内容区自身的间距与滚动口径不变。

### 5.3 状态管理与状态分支

- 复用总册 `aiTask` store 轮询任务执行；本域私有状态挂 `AssistComparePanel` 本地（不入全局 store）；
- 脑图生成用例的所选需求、勾选集合、全选状态、展示已采纳开关与采纳回执挂 `AiCaseGenerationPanel` 本地；提交后由 `aiTask` store 按 `taskId` 轮询，状态非 `running` 即停轮；`proposals_ready` 时按提案确认状态分区：未采纳用例映射为可勾选的 `TestCaseNode` 树，已采纳（含编辑后采纳）用例在开关关闭时不入树，开启时作为默认勾选的只读节点入树；失败与终止态提供再次执行；
- 脑图生成用例的「采纳」只作用于本次新勾选的未采纳用例：创建脑图节点与属性子节点时把来源需求 id 写入节点 data 的 `requirementId` 并随 `add_node` 帧一同落库，随后回传采纳状态；已采纳用例不重复建节点、不重复关联、不重复回传状态；勾选集全为已采纳用例时动作区置灰；
- 需求关联随节点 `add_node` 帧落库，无独立写入与「节点未落库」重试；需求归属校验失败经服务端错误帧提示，采纳状态照常回传；
- 用例优化按用例节点、脑图生成用例按当前文档查询后端最近一次任务（同一 `GET /api/ai/tasks/{type}/latest`，前者传 `nodeId`、后者省略 `nodeId`，项目内任意成员发起的任务均可复用）：命中后按任务状态回显进度 / 提案，无任务时才进入各自的发起流程；仅取最近一次，不查询更早历史；查询失败只提示错误、不自动发起；切换节点 / 文档时丢弃过期查询响应，避免旧任务提案内容写入新任务；
- 重新执行（总册 3.6.4 分叉）：`running` 仅回显原任务进度，不新建任务或执行实例；`aborted` 沿用同一任务新建执行实例；`proposals_ready` / `closed` 按原输入发起新任务，原任务及其执行、提案、采纳记录保留只读回看。后端对 `case_optimize` / `mindmap_case_generation` 在 `proposals_ready` 态拒绝再次执行端点（1000018111），任务中心与详情页仅对 `aborted` 态提供「再次执行」；
- 状态分支：任务进行中（超交互等待展示进度入口）、全部建议与现状一致（折叠 + 提示「无变化」）、部分采纳回执、用例优化入口对只读节点置灰、AI 未启用入口隐藏（总册 4.5）；入口放行后面板动作区仍对只读 / 已处理态置灰并常驻展示原因，避免入口与实际可采纳状态不一致。

---

## 6. 错误码定义

号段：**1000018301–1000018329（辅助功能）**，以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018301 | 404 | 用例节点不存在或不属目标文档 |
| 1000018302 | 400 | 未选中节点或超出单次数量上限 |
| 1000018303 | 404 | 测试计划不存在 |
| 1000018304 | 400 | 计划关联用例为空，无法推荐顺序 |
| 1000018305 | 409 | 建议已采纳或已失效 |
| 1000018307 | 400 | 节点类型不支持（仅用例节点可优化） |
| 1000018309 | 404 | 输入需求不存在或不属当前项目 |
| 1000018310 | 400 | 输入需求非已确认状态，不可生成 |
| 1000018311 | 404 | 输入文档不存在或不属当前项目 |
| 1000018312 | 409 | 已存在进行中的同输入脑图生成用例任务 |

> 上下文头缺失（`X-Active-Project` 未携带或未解析）回公共错误码 **1000002008**（缺少上下文请求头），不占用本号段；本号段错误码仅指输入取值、归属与状态问题。

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../service/ai/task/type/CaseOptimizeTypeAdapter`、`PlanOrderTypeAdapter` | 辅助域任务类型适配器（工具白名单与预算默认、节点操作指令提案结构与清洗，共用 `CaseAssistSupport` 解析输入） |
| `server/.../service/ai/task/handler/CaseAssistSupport`、`CaseOptimizeOps` | 子树节点清单上下文 `nodeContext`（id / type / title / path）与操作指令常量、清洗归一口径 |
| `server/.../model/dto/request/tcase/DocumentAddNodeReqDTO`、`DocumentUpdateAttrsReqDTO`、`repository/tcase/TestCaseNodeMapper`、`service/websocket/DocumentPersistenceHandler` | 脑图帧扩展 `requirementId`：`add_node` / `update_attrs` DTO 增字段、`updateAttrsWithVersion` 增参数与条件写入、处理器写入 `ft_case_node.requirement_id`（`add_node` 同时携带 `aiGenerated`），校验需求归属并经错误帧返回 1000018001 |
| `server/.../service/ai/task/adopt/PlanOrderAdopter` | 执行顺序推荐采纳落库（用例优化采纳由前端执行节点操作，后端 adopter 不改） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018301–1000018307 |
| `server/src/main/resources/db/schema.sql` | `ft_case_node.tags`、`ft_case_node.requirement_id` 列及索引 DDL（唯一事实源）；删除 `ft_case_requirement` 表与索引 |
| `web/src/components/project/ai/AssistComparePanel.vue`、`PlanOrderPanel.vue` | 操作指令对照与排序面板（含各自单测） |
| `web/src/composables/project/ai/useAssistTask.ts`、`assistPresentation.ts` + 单测 | 任务提交（等待 + 超时转进度）、回执处理与操作指令视图模型 |
| `web/src/composables/project/functional-testing/mindmap/useCaseOptimizeExecution.ts` + 单测 | 按 operations 在 kityminder 树上原地更新 / 新增节点并触发持久化 |
| `web/src/composables/project/functional-testing/mindmap/useMindmapPersistence.ts` | `PersistSnap` 携带 `tags` / `requirementId`，`add_node` 帧携带 `aiGenerated` / `requirementId`，`update_attrs` 帧写入标签列与需求列（清空显式传 null） |
| `web/src/composables/project/functional-testing/mindmap/useCaseOptimizeExecution.ts` | 采纳时按提案 `nodeId` 给被优化用例节点写 `aiGenerated`，与节点操作同批触发落库 |
| `web/src/composables/project/ai/useCaseAssistEntry.ts` | 采纳 / 编辑后采纳透传提案锚点 `nodeId` 给执行器 |
| `web/src/types/project/shared.ts`、`web/src/minder/adapter.ts` | `TestCaseNode` 携带 `aiGenerated` 并映射到脑图节点 data |
| `web/src/types/ai.ts` | 新增 `AiCaseOptimizeOperationSuggestion`（operations 联合类型），任务详情旧类型保持不变 |
| `server/.../service/ai/task/type/MindmapCaseGenerationTypeAdapter`、`handler/CaseGenerationSupport` | 脑图生成用例类型适配器：输入校验（文档 / 需求归属与状态）、需求快照与 RAG 上下文、提案清洗（完整用例属性 + 来源引用）、工具白名单与预算默认 |
| `server/.../service/ai/config/AiPromptScenes`、`server/src/main/resources/ai/guide/case-mindmap.md` | 新增 `mindmap_case_generation` 场景与默认提示词（脑图节点结构与用例属性口径） |
| `server/.../service/ai/task/adopt/MindmapCaseGenerationAdopter` | 确认协议后端承接：仅记录采纳状态，不执行节点变更 |
| `server/.../framework/common/Constants`、`ErrorCodeConstants` | 登记任务类型 `mindmap_case_generation`、提案 kind `mindmap_case_suggestion` 与错误码 1000018309–1000018312 |
| `web/src/components/project/functional-testing/case/AiCaseGenerationPanel.vue` + 单测 | 生成面板：进度 / 提案勾选树 / 插入回执 / 重新执行 |
| `web/src/components/project/functional-testing/case/CaseSelector.vue` + 单测 | 增加注入树模式（传入 AI 提案映射的节点树时跳过后端文档加载） |
| `web/src/composables/project/functional-testing/mindmap/useCaseGenerationInsert.ts` + 单测 | 按提案在根节点下创建用例与属性子节点，来源需求 id 写入用例节点 data 的 `requirementId`、`aiGenerated = true`，`contentchange` + 持久化随 `add_node` 落库 |
| `web/src/composables/project/ai/useCaseGenerationEntry.ts` + 单测 | 需求单选 → 任务提交 → 轮询 → 提案映射 → 采纳建节点（需求关联随节点落库）→ 回传采纳状态 |
| `web/src/types/project/shared.ts`、`web/src/minder/adapter.ts` | `TestCaseNode` 携带 `requirementId` 并映射到脑图节点 data |
| `web/src/services/project/functional-testing/testcase.ts`、`web/src/composables/project/functional-testing/mindmap/useCaseRequirementLink.ts` | 删除 `fetchCaseRequirements` / `updateCaseRequirements` 服务与独立关联保存；选取器回显改按 id 调需求详情、确认后写节点 `requirementId` 并触发持久化 |
| `web/src/composables/project/ai/reviewProfiles.ts`、`web/src/types/ai.ts` | 新任务类型登记只读展示策略（任务详情无审核动作）与新类型 / 提案类型 |
| `server/.../service/ai/task/AiTaskServiceImpl` | 再次执行按任务类型拒绝 `proposals_ready`：`case_optimize` / `mindmap_case_generation` 复用 1000018111（分册 07 覆盖 `review_case_review`） |
| `web/src/composables/project/ai/useCaseAssistEntry.ts`、`useCaseGenerationEntry.ts` + 单测 | 重新执行按任务状态分叉：`running` 仅回显、`aborted` 沿用原任务、`proposals_ready` / `closed` 按原输入发起新任务 |

- **数据库**：用例关联需求复用 `ft_case_node.requirement_id` 列（详见 `docs/04-detailed-design/03-function-testing/04-project-workspace-test-case.md` 4 节），删除 `ft_case_requirement` 表；任务 / 执行 / 提案 / 采纳记录复用 `ai_task_` 六表，节点落库均为既有 `ft_case_node` 业务表。
- **OpenAPI**：两个任务 type 的 `input / 提案` 结构随任务分组暴露；确认复用总册 3.6.5。

**测试要点（C8 ≥ 70%）**

- 后端：节点归属与类型校验矩阵、数量上限、非法节点 / 字段 / 类型引用在提案清洗阶段丢弃、`update_attrs` 携带 `tags` 落库与版本冲突处理、顺序采纳只改排序不碰集合、计划空集错误码、越权 404；
- 前端：操作指令对照列表渲染与折叠（更新两栏 / 新增行）、采纳 / 驳回回执、执行器按 `nodeId` 原地改写与按 `parentId` 新增节点（ID / 层级不变、类型白名单）、标签随 `update_attrs` 落库、节点不存在时跳过、拖拽序号与前后对照、只读置灰、任务超时转进度入口；生成用例采纳时来源需求 id 随 `add_node` 帧落库、子节点 `aiGenerated` 同帧写入、需求归属错误经错误帧提示不回滚。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-10 | 随落地实现勘误：2 节补 `ft_case_node.tags` 补列迁移并撤销「无迁移」口径，5.2 补多节点切换与结构块落位，5.3 补面板动作区置灰原因，7 节文件清单对齐实际类名与目录 |
| V1.0 | 2026-10-16 | 同步 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`）：2 节数据设计改按 `ai_task_` 域六表拆分；产物改称提案；执行器改类型适配器口径 |
| V1.0 | 2026-10-16 | 术语与状态口径残词勘误：5.3 状态管理改「复用总册 `aiTask` store 轮询任务执行」 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：删除补充节点派生边继承（原 4.3 整节，4.4 顺延为 4.3）与 `trace_edge` 写入引用，补全输入的上游需求范围改为显式传入 |
| V1.0 | 2026-10-18 | 功能测试域表名统一 `ft_` 前缀：`test_case_node.tags` → `ft_case_node.tags` |
| V1.0 | 2026-10-18 | 迁移基线收敛：`ft_case_node.tags` 列 DDL 以 `schema.sql` 为唯一事实源，移除对 `db/migration/V20261009100000__assisted_case_tags.sql` 的引用 |
| V1.0 | 2026-10-19 | 移除任务权限校验：1.2 概述与 3.1 通用约定改为「后端不校验资源权限与 `ai:confirm`、前端显隐控制」，6 错误码表注销 1000018306 |
| V1.0 | 2026-10-20 | 发起校验补上下文头口径：缺 `X-Active-Project` 回公共码 1000002008（3.1 / 6），10000183xx 仅指输入、归属与状态问题 |
| V1.0 | 2026-11-18 | AI 能力域免 RBAC 权限码校验：1.2 / 3.1 / 5.3 / 7 移除 `ai:task` · `ai:confirm` 与权限不足 / 权限隐藏分支，改登录即可并保留任务归属可见性与项目成员校验 |
| V1.0 | 2026-11-18 | 用例级别推荐：产物级别改 P0–P3 直通、移除历史缺陷热点依据与关联工具、每个选中节点必产结果（含同级项，不折叠）、同级项采纳写用例修改记录留痕（from=to） |
| V1.0 | 2026-11-18 | 用例补全与级别推荐合并为「用例优化」：`type = case_optimize`、提案 `kind = case_optimize_suggestion`，单条提案同时含四类字段补全与级别建议；删除补充节点建议，保留标签；采纳目标收敛为 `{ documentId }`，字段与级别同事务落库 |
| V1.0 | 2026-11-18 | 5.1 / 5.3 任务复用改为后端按节点查询最近一次任务（项目内成员共享）：未完成回显进度、已产出提案 / 已处理完毕展示提案并支持重新执行（已关闭任务按同一输入发起新任务），并加固任务切换丢弃过期响应 |
| V1.0 | 2026-11-19 | 用例优化采纳口径：前置条件 / 操作步骤 / 预期结果在建议值与现状不同时删除原有关联子节点再插入新数据（任务详情页与脑图面板共用同一承接），标签与优先级维持原规则，同步 3.2 / 4.2 / 7 测试要点 |
| V1.0 | 2026-11-19 | 用例优化改为节点操作指令模型：提案 operations（update 原地更新含标题 / 属性 / 优先级 / 标签、add 任意节点下新增，均带 path / reason），取消删旧插新，新增陈旧值保护与跨文档校验，登记 1000018308，同步 3.2 / 4.2 / 5.2 / 6 / 7 |
| V1.0 | 2026-11-19 | 用例优化节点操作收敛为脑图前端执行：后端只产出 operations 提案、确认协议仅记录采纳状态，取消 `applyCaseOptimize` / 陈旧值保护 / 1000018308；`update_attrs` 扩展 `tags` 供标签更新随脑图持久化落库，任务详情页不参与节点执行；同步 1.1 / 2 / 3.1 / 3.2 / 4.2 / 6 / 7 |
| V1.0 | 2026-11-19 | 用例优化采纳给被优化用例节点打 AI 标识：复用 `ai_generated`、按提案 `nodeId` 经 `update_attrs` 落库，不新增字段与快照继承；同步 3.2 / 4.2 / 7 |
| V1.0 | 2026-11-19 | 新增脑图生成用例：登记 `mindmap_case_generation` / `mindmap_case_suggestion`、输入与提案结构（3.4）、边界（4.4）、入口与面板（5.1–5.3）、错误码 1000018309–1000018312（6）与实施文件清单（7）；任务详情只读、无审核动作 |
| V1.0 | 2026-11-20 | 用例优化对照面板动作收敛为单个「采纳」：移除批量采纳，采纳按当前核对节点提交人工核对后的操作并自动推进，驳回保留，同步 5.2 组件结构、7 文件清单与测试要点 |
| V1.0 | 2026-11-20 | 「AI测试用例」更名「AI覆盖雷达」；5.1 / 5.3 明确脑图生成用例入口按文档复用最近一次任务（`/latest` 省略 `nodeId` 按 `documentId` 匹配），命中直接回显、未命中才选需求发起，查询失败不自动发起 |
| V1.0 | 2026-11-20 | 5.2 / 5.3 补齐生成面板布局与选择口径：固定弹窗高度、候选卡片区内部滚动、结果区不额外包裹容器、全选 / 半选态与子节点整枝插入 |
| V1.0 | 2026-11-20 | 5.2 / 5.3 补齐脑图生成用例已完成态：默认隐藏已采纳用例，可勾选展示且默认勾选只读；动作「插入选中用例」改「采纳」，仅对新勾选用例建节点并回传状态，不重复插入脑图 |
| V1.0 | 2026-11-20 | 4.4 / 5.2 / 5.3 / 7 补齐采纳时自动关联来源需求：插入执行器返回新建节点 id，前端按既有接口逐项写入并只对节点未落库做有界重试，失败不回滚；同步测试要点 |
| V1.0 | 2026-11-20 | 用例关联需求保存接口入参改为单个 `requirementId`，4.4 / 7 中来源需求关联写入口径同步为单值 |
| V1.0 | 2026-11-20 | 5.2 生成面板弹窗高度调至约 88vh、上下留白一致（各约 6vh）；AI 生成与 AI 优化面板对话框容器 padding 统一收紧，内容区间距不变 |
| V1.0 | 2026-11-20 | 用例关联需求改为 `ft_case_node.requirement_id` 随脑图帧落库：4.4 / 5.2 / 5.3 / 7 采纳时来源需求 id 写入节点 data 随 `add_node` 同帧落库，删除独立关联接口与有界重试，`add_node` 同时携带 `aiGenerated`，需求详情按 id 回显 |
| V1.0 | 2026-11-21 | US-AI-018 执行顺序推荐补齐真实智能：4.3 推荐依据改为实际读取前置 / 步骤 / 预期、祖先路径与 `ft_plan_execution_record` 历史失败率；采纳排序改为稳定排序（同步 / 重规划 / 差量调整保留，补入用例按原序补位）；新增按排序的执行序号展示与「下一个待执行用例」引导 |
| V1.0 | 2026-11-23 | 5.1 / 5.3 重新执行改按任务状态分叉：`running` 仅回显、`aborted` 沿用原任务、`proposals_ready` / `closed` 按原输入新建任务且保留原记录；后端拒绝这三类轻量能力任务的 `proposals_ready` 再次执行，任务中心与详情页仅 `aborted` 态提供再次执行 |
