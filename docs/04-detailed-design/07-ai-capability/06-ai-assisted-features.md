# 软件测试平台——AI 辅助功能

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**辅助功能**能力域进行详细设计：定义用例优化、测试用例执行顺序推荐两项能力的任务输入、提案结构与采纳执行口径。任务资源、提案确认协议与错误码分段见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档只定义本域的业务入口与提案语义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/06-srs-ai-assisted-features.md`（US-AI-017 ~ 018）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`。
- 权限：免 RBAC 权限码校验（登录即可）；用例优化与执行顺序推荐的入口由前端按节点只读 / 已处理等业务状态显隐，后端任务发起与采纳仅保留任务归属可见性与项目成员校验；范围限于当前项目。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（3.6 任务资源、6 错误码分段）

---

## 2. 数据设计

本分册**不新增表**，仅补一列：

- 辅助任务、执行与轨迹：`ai_task / ai_task_execution / ai_task_trace_event`（`type = case_optimize / plan_order`）；
- 提案与采纳：`ai_task_proposal / ai_task_adoption`（提案 `kind`：`case_optimize_suggestion / order_suggestion`）；
- 采纳写既有节点 / 计划表：用例优化经脑图 WS 节点持久化管道落库、不写用例修改记录，执行顺序推荐经采纳服务只改排序字段；
- 用例标签随补全建议采纳写入 `ft_case_node.tags`（`jsonb` 数组，允许 NULL、不设默认值、不建物理外键），列 DDL 以 `server/src/main/resources/db/schema.sql` 为唯一事实源，满足 C5；该列不进向量索引，也不参与脑图渲染。

---

## 3. 接口详细设计

### 3.1 通用约定

- 全部执行经总册 3.6 AI Task 任务资源；本文档只定义两类任务的 `type`、`input` 与提案语义，提案的节点操作由脑图前端执行、总册 3.6.5 确认协议仅记录采纳状态。
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

- **前端执行**（脑图面板）：`update` 按 `nodeId` 定位节点后原地改写 `field`（`title` 写节点文本、`priority` 写级别列、`tags` 写标签列），节点 ID、层级与类型不变，不删旧插新；`add` 按 `parentId` 定位父节点并用脑图既有新建节点口径生成 UUID 追加子节点（`ai_generated = true`，无物理外键）。两类变更经脑图既有 WS 节点持久化管道落库并触发向量索引重嵌，`title` / `priority` 走 `update_attrs`、`add` 走 `add_node`、`tags` 走扩展后的 `update_attrs`；提案的 `existing` 仅用于对照展示，不作为写入前置条件。

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
- 同一用例的全部操作合并为一条提案、一次采纳动作；非法节点引用在提案生成阶段清洗丢弃，不在采纳阶段产生跨文档写入；
- 节点变更经脑图既有 WS 持久化管道落库（含权限重校验与向量索引重嵌），不写用例修改记录；确认协议仅记录提案采纳状态，任务详情页不参与用例优化的节点执行。

### 4.3 顺序推荐的边界

- 推荐依据限定平台内数据（上游需求优先级、历史缺陷热点、执行依赖），不引入外部信息；
- 采纳只改排序，不改快照、不改关联集合；后续计划同步机制（用例增删）按既有规则重新排布，建议结果不持久复用。

---

## 5. 前端设计

### 5.1 入口

```
脑图用例节点悬浮窗
└── 「AI 优化」（当前节点 → case_optimize）

测试计划详情
└── 「执行顺序建议」（plan_order）
```

点击「AI 优化」先按当前节点查询最近一次任务（`GET /api/ai/tasks/{type}/latest`，项目内任意成员发起的任务均可复用）：查到则未完成回显进度或已有提案，`proposals_ready` / `closed` 展示提案并提供「重新执行」，`aborted` 展示原因与「再次执行」；无任务时发起 `case_optimize`（交互 2.4）。

### 5.2 组件结构

```
AssistComparePanel（通用对照面板，用例优化使用）
├── Header（标题与处理状态；多节点发起时下拉切换核对节点，逐条采纳后自动推进到下一个待处理节点）
├── OperationList（节点操作指令列表：每条显示「节点路径 · 字段」两栏对照（现有 vs 建议）或「在节点路径下新增类型节点」，附理由与新增 / 变更徽标；无变化项折叠为「无变化 N 项」，可展开核对）
├── SourceRefsLinks（来源引用，点击跳需求详情）
└── Actionbar（逐条采纳、批量采纳、驳回；置灰原因行与逐项回执）

PlanOrderPanel
├── OrderList（拖拽调整，序号徽标，建议理由悬浮）
├── BeforeAfterDiff（采纳前/后顺序对照，按当前顺序实时刷新）
└── Actionbar（应用建议、放弃，应用后 toast 提示）
```

各结构块落在对应 `.vue` 组件文件内，不拆独立组件文件（见 7 节文件清单）。

### 5.3 状态管理与状态分支

- 复用总册 `aiTask` store 轮询任务执行；本域私有状态挂 `AssistComparePanel` 本地（不入全局 store）；
- 任务按用例节点查询后端最近一次任务（`GET /api/ai/tasks/{type}/latest`，项目内任意成员发起的任务均可复用）：命中后按任务状态回显进度 / 提案，无任务时发起；仅取最近一次，不查询更早历史；切换节点时丢弃过期查询响应，避免旧任务提案内容写入新任务；
- 重新执行：`proposals_ready` / `aborted` 沿用同一任务新建执行实例（3.6.4）；`closed` 因后端不允许再次执行，按同一输入发起新任务；
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

> 上下文头缺失（`X-Active-Project` 未携带或未解析）回公共错误码 **1000002008**（缺少上下文请求头），不占用本号段；本号段错误码仅指输入取值、归属与状态问题。

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../service/ai/task/type/CaseOptimizeTypeAdapter`、`PlanOrderTypeAdapter` | 辅助域任务类型适配器（工具白名单与预算默认、节点操作指令提案结构与清洗，共用 `CaseAssistSupport` 解析输入） |
| `server/.../service/ai/task/handler/CaseAssistSupport`、`CaseOptimizeOps` | 子树节点清单上下文 `nodeContext`（id / type / title / path）与操作指令常量、清洗归一口径 |
| `server/.../model/dto/request/tcase/DocumentUpdateAttrsReqDTO`、`repository/tcase/TestCaseNodeMapper`、`service/websocket/DocumentPersistenceHandler` | `update_attrs` 扩展 `tags`：DTO 增字段、`updateAttrsWithVersion` 增参数与条件写入、处理器透传，供前端标签更新随脑图持久化落库 |
| `server/.../service/ai/task/adopt/PlanOrderAdopter` | 执行顺序推荐采纳落库（用例优化采纳由前端执行节点操作，后端 adopter 不改） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018301–1000018307 |
| `server/src/main/resources/db/schema.sql` | `ft_case_node.tags` 列 DDL（唯一事实源，存量库增量 SQL 按 runbook §10 手工执行） |
| `web/src/components/project/ai/AssistComparePanel.vue`、`PlanOrderPanel.vue` | 操作指令对照与排序面板（含各自单测） |
| `web/src/composables/project/ai/useAssistTask.ts`、`assistPresentation.ts` + 单测 | 任务提交（等待 + 超时转进度）、回执处理与操作指令视图模型 |
| `web/src/composables/project/functional-testing/mindmap/useCaseOptimizeExecution.ts` + 单测 | 按 operations 在 kityminder 树上原地更新 / 新增节点并触发持久化 |
| `web/src/composables/project/functional-testing/mindmap/useMindmapPersistence.ts` | `PersistSnap` 携带 `tags`，`update_attrs` 帧写入标签列 |
| `web/src/types/ai.ts` | 新增 `AiCaseOptimizeOperationSuggestion`（operations 联合类型），任务详情旧类型保持不变 |

- **数据库**：无新增表、无新增列；落库均为既有 `ft_case_node` 业务表（原地更新与新增节点复用现有列，`tags` 列为既有补列）。
- **OpenAPI**：两个任务 type 的 `input / 提案` 结构随任务分组暴露；确认复用总册 3.6.5。

**测试要点（C8 ≥ 70%）**

- 后端：节点归属与类型校验矩阵、数量上限、非法节点 / 字段 / 类型引用在提案清洗阶段丢弃、`update_attrs` 携带 `tags` 落库与版本冲突处理、顺序采纳只改排序不碰集合、计划空集错误码、越权 404；
- 前端：操作指令对照列表渲染与折叠（更新两栏 / 新增行）、逐条 / 批量 / 驳回回执、执行器按 `nodeId` 原地改写与按 `parentId` 新增节点（ID / 层级不变、类型白名单）、标签随 `update_attrs` 落库、节点不存在时跳过、拖拽序号与前后对照、只读置灰、任务超时转进度入口。

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
