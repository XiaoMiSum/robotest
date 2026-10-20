# 软件测试平台——AI 辅助功能

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**辅助功能**能力域进行详细设计：定义用例补全、用例级别推荐、测试用例执行顺序推荐三项能力的任务输入、提案结构与采纳落库规则。任务资源、提案确认协议与错误码分段见总册 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`，本文档只定义本域的业务入口与提案语义。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/06-srs-ai-assisted-features.md`（US-AI-017 ~ 019）。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`。
- 权限：用例补全与级别推荐、执行顺序推荐的入口由前端按既有**脑图编辑权限** / **计划执行权限**与 `ai:task` 显隐；后端任务发起与采纳不做权限校验；范围限于当前项目。

### 1.3 参考资料

- 总册：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（3.6 任务资源、6 错误码分段）

---

## 2. 数据设计

本分册**不新增表**，仅补一列：

- 辅助任务、执行与轨迹：`ai_task / ai_task_execution / ai_task_trace_event`（`type = case_complete / case_priority / plan_order`）；
- 提案与采纳：`ai_task_proposal / ai_task_adoption`（提案 `kind`：`case_suggestion / priority_suggestion / order_suggestion`）；
- 采纳落库写既有用例 / 节点 / 计划表与用例修改记录；
- 用例标签随补全建议采纳写入 `ft_case_node.tags`（`jsonb` 数组，允许 NULL、不设默认值、不建物理外键），列 DDL 以 `server/src/main/resources/db/schema.sql` 为唯一事实源，满足 C5；该列不进向量索引，也不参与脑图渲染。

---

## 3. 接口详细设计

### 3.1 通用约定

- 全部执行经总册 3.6 AI Task 任务资源；本文档只定义三类任务的 `type`、`input`、提案与承接语义。
- 上下文头：`X-Active-Workspace` + `X-Active-Project`（发起、确认一致），发起缺 `X-Active-Project` 头回 1000002008（缺少上下文请求头）；响应 `Result<T>`，示例**仅展示 `data`**。
- 权限：任务发起与采纳后端不做资源权限与 `ai:confirm` 校验（仅保留任务归属可见性）；入口与动作显隐由前端按既有权限和 `ai:task` / `ai:confirm` 控制。

### 3.2 用例补全

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "case_complete",
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
| scope.requirementIds | uuid[] | 否 | 显式上游需求范围，作为补全建议的需求上下文 |

- **提案**（`kind = case_suggestion`，逐节点一条，`confirmStatus = pending`）：

```json
{
  "key": "node-3",
  "kind": "case_suggestion",
  "parentKey": null,
  "title": "验证码 60 秒重发限制",
  "content": {
    "nodeId": "…",
    "fields": {
      "precondition": { "existing": null, "suggested": "手机号已输入并获取过一次验证码" },
      "steps": { "existing": ["点击获取验证码"], "suggested": ["点击获取验证码", "60 秒内再次点击"] },
      "expected": { "existing": ["发送成功提示"], "suggested": ["发送成功提示", "提示 60 秒后重试"] },
      "tags": { "existing": ["登录"], "suggested": ["登录", "限制"] }
    },
    "extraNodes": [
      { "title": "验证码 10 分钟后过期校验", "isTestCase": true, "parentNodeId": "…" },
      { "title": "网络中断时重发", "isTestCase": false, "parentNodeId": "…" }
    ],
    "sourceRefs": [{ "type": "requirement", "id": "…", "title": "REQ-001", "quote": "验证码 60 秒内不可重发…" }]
  },
  "confirmStatus": "pending"
}
```

- **确认**：总册 3.6.5，动作 `adopted / adopted_edited / rejected`；本类型 `target`：

```json
{ "documentId": "…", "extraNodePosition": "sibling" }
```

- **承接**（用例采纳服务）：逐项事务更新节点字段（`adopted_edited` 以编辑值落库）、新增 `extraNodes` 节点；采纳写 `adopted_ref = { nodeId / createdNodeIds }`。

### 3.3 用例级别推荐

- **提交**：`POST /api/ai/tasks`

```json
{
  "type": "case_priority",
  "input": { "documentId": "…", "nodeIds": ["…", "…"] },
  "waitSeconds": 10
}
```

- **提案**（`kind = priority_suggestion`，`confirmStatus = pending`）：

```json
{
  "key": "node-3",
  "kind": "priority_suggestion",
  "title": "级别推荐：验证码重发限制",
  "content": {
    "nodeId": "…",
    "current": "medium",
    "suggested": "high",
    "reason": "上游需求 REQ-001 优先级为高；该模块近 30 天 3 个缺陷",
    "sourceRefs": [{ "type": "requirement", "id": "…", "title": "REQ-001" }]
  },
  "confirmStatus": "pending"
}
```

- **确认**：总册 3.6.5；承接服务**仅更新采纳项的用例优先级**（`target = { documentId }`），逐项事务；`current == suggested` 的项由前端折叠展示、默认不随批量采纳写库（无变化即无写入）。

### 3.4 测试用例执行顺序推荐

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

### 4.2 建议采纳的覆盖口径

- **已有人工填写的字段默认不被覆盖**：`existing` 非空的字段仅在 `adopted_edited` 且编辑值与 `suggested` 一致时写入（即人工显式采纳）；空缺字段的补全同样须采纳确认后生效；
- 已有人工设定的优先级不自动改变：级别推荐的批量采纳仅写 `current != suggested` 且人工点击确认的项；
- 采纳结果作为**用例修改记录留痕**（调用既有修改记录服务）。

### 4.3 顺序推荐的边界

- 推荐依据限定平台内数据（上游需求优先级、历史缺陷热点、执行依赖），不引入外部信息；
- 采纳只改排序，不改快照、不改关联集合；后续计划同步机制（用例增删）按既有规则重新排布，建议结果不持久复用。

---

## 5. 前端设计

### 5.1 入口

```
脑图编辑器工具栏
├── 「AI 补全」（选中节点 → case_complete）
└── 「级别推荐」（选中节点 → case_priority）

测试计划详情
└── 「执行顺序建议」（plan_order）
```

### 5.2 组件结构

```
AssistComparePanel（通用对照面板，补全与级别推荐复用）
├── Header（标题与处理状态；多节点发起时下拉切换核对节点，逐条采纳后自动推进到下一个待处理节点）
├── SuggestList（现有 vs AI 建议两栏对照；无变化项折叠为「无变化 N 项」，可展开核对）
├── ExtraNodesPreview（补充节点树预览，用例/结构图标区分）
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
- 状态分支：任务进行中（超交互等待展示进度入口）、全部建议与现状一致（折叠 + 提示「无变化」）、部分采纳回执、补全/推荐入口对只读节点置灰、权限不足隐藏入口、AI 未启用入口隐藏（总册 4.5）；入口放行后面板动作区仍对只读 / 无权限 / 已处理态置灰并常驻展示原因，避免入口与实际可采纳状态不一致。

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
| 1000018307 | 400 | 节点类型不支持（仅用例节点可补全或推荐） |

> 上下文头缺失（`X-Active-Project` 未携带或未解析）回公共错误码 **1000002008**（缺少上下文请求头），不占用本号段；本号段错误码仅指输入取值、归属与状态问题。

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../service/ai/task/type/CaseCompleteTypeAdapter`、`CasePriorityTypeAdapter`、`PlanOrderTypeAdapter` | 辅助域任务类型适配器（工具白名单与预算默认、提案结构，共用 `CaseAssistSupport` 解析输入） |
| `server/.../service/ai/task/adopt/CaseCompleteAdopter`、`CasePriorityAdopter`、`PlanOrderAdopter` | 采纳落库（共用 `AssistAdoptSupport`，调用既有用例 / 计划服务与修改记录服务） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018301–1000018307 |
| `server/src/main/resources/db/schema.sql` | `ft_case_node.tags` 列 DDL（唯一事实源，存量库增量 SQL 按 runbook §10 手工执行） |
| `web/src/components/project/ai/AssistComparePanel.vue`、`PlanOrderPanel.vue` | 对照与排序面板（含各自单测） |
| `web/src/composables/project/ai/useAssistTask.ts`、`assistPresentation.ts` + 单测 | 任务提交（等待 + 超时转进度）、回执处理与面板视图模型 |

- **数据库**：无新增表；`ft_case_node.tags` 补列以 `server/src/main/resources/db/schema.sql` 为唯一事实源（见 2 节），落库均为既有业务表。
- **OpenAPI**：三个任务 type 的 `input / 提案` 结构随任务分组暴露；确认复用总册 3.6.5。

**测试要点（C8 ≥ 70%）**

- 后端：节点归属与类型校验矩阵、数量上限、已有字段不被覆盖（采纳动作 × existing 组合矩阵）、顺序采纳只改排序不碰集合、计划空集错误码、越权 404；
- 前端：对照面板两栏渲染与折叠、逐条/批量/驳回回执、拖拽序号与前后对照、只读置灰与权限隐藏、任务超时转进度入口。

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
