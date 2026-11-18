# 软件测试平台——测试计划管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 测试计划管理接口

### 1.1 获取计划列表

- **路径**：`GET /api/project/plans`
- **参数**：`status`（可选，取值 `new` / `in_progress` / `completed` / `blocked` / `closed`）、`keyword`（可选，名称模糊匹配）、`pageNo`（默认 1）、`pageSize`（默认 20）
- **响应**：
  
  ```json
  {
  "list": [
    {
    "id": "6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e01",
    "name": "V1.4.2 回归测试",
    "status": "in_progress",
    "environment": "staging",
    "startTime": "2026-09-20T00:00:00",
    "endTime": "2026-09-24T00:00:00",
    "executor": { "id": "6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e10", "name": "张明" },
    "createdAt": "2026-09-18T10:00:00",
    "totalAssociated": 50,
    "passed": 28,
    "progressPercent": 58.0,
    "passRate": 56.0,
    "requirementCount": 3
    }
  ],
  "total": 18
  }
  ```

- **进度字段口径**：`totalAssociated` 为关联 case 快照数，`passed` 为通过数；`progressPercent` =（总数 − 未执行数）/ 总数 × 100，`passRate` = 通过数 / 总数 × 100，均为 0–100 的百分数，后端四舍五入保留两位。后端按本页计划 ID 批量查询关联用例快照聚合计算，避免逐行 N+1。前端进度列取整显示百分比，通过率列保留一位小数，未开始（new）行通过率固定显示 `—`。`requirementCount` 为本页计划批量回填的关联需求数量，需求已删除时不计数。
- **状态取值**：`new`（未开始）/ `in_progress`（执行中）/ `completed`（已完成）/ `blocked`（已阻塞）/ `closed`（已关闭），展示文案映射见 `docs/05-interaction-design/03-function-testing/06-workspace-ui-test-plan.md` 1.1。
- **前端调用**：计划列表页的查询、筛选与分页均走本接口（`status`、`keyword`、`pageNo`、`pageSize` 由页面维护）。

### 1.2 创建计划

- **路径**：`POST /api/project/plans`

- **请求体**（`executorId`、`documentId`、`caseIds` 均为 UUID 字符串）：
  
  ```json
  {
  "name": "功能测试计划",
  "description": "...",
  "executorId": "6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e10",
  "startTime": "2026-08-01T00:00:00Z",
  "endTime": "2026-08-10T00:00:00Z",
  "environment": "staging",
  "requirementIds": ["6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e30"],
  "selectedNodes": [
    { "documentId": "6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e20", "caseIds": ["6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e21", "6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e22"] }
  ]
  }
  ```

- **校验**：`name` 非空；`requirementIds` 与 `selectedNodes` 均可为空（`selectedNodes` 保留供 AI 助手创建链路使用）；每个 `caseIds` 非空。前端创建表单另做校验：名称必填（输入框限 100 字）、至少关联一个需求，未满足时拦截并提示；所选需求无用例时允许创建空计划。
- **处理**：校验 `requirementIds` 均属于当前项目，否则返回 1000018001；按 `ft_case_requirement` 解析所选需求的关联用例（仅 `type='case'` 节点），按 `documentId → caseIds` 分组后与显式 `selectedNodes` 合并去重；生成模块和节点快照（完整拷贝用例属性），按最终 `caseIds` 标记 `is_associated`，初始化 `last_result='untested'`，状态置 new；持久化 `requirement_ids`；记录项目活动 `PLAN_CREATED`。
- **响应**：201，返回计划详情（结构同 1.3）。
- **前端**：创建成功后关闭弹窗并跳转至该计划详情页 `/workspace/projects/plans/:id`。

### 1.3 获取计划详情

- **路径**：`GET /api/project/plans/:id`
- **响应**：`{ "id", "name", "description", "status", "environment", "startTime", "endTime", "executor": { "id", "name" }, "requirementIds", "requirements": [{ "id", "code", "title" }], "replanStatus", "replanMessage", "replanFinishedAt", "createdAt" }`；`requirements` 按创建时所选顺序返回，需求已删除时跳过；`requirementIds` 为创建 / 最近一次编辑保存的关联需求原始 ID 列表（含已删除需求），供编辑表单判定关联需求是否变化；`replanStatus` 取 `idle` / `running` / `succeeded` / `failed`（未发起过重规划时为 `idle`）；`replanMessage` 为失败原因或结果摘要，`replanFinishedAt` 为完成时间（未完成时 null）。
- **前端**：详情页首屏与进度、快照模块树并行加载（见 1.4、1.8）；标记执行结果后仅重拉详情与进度，避免整页重载导致脑图丢失选中态。

### 1.4 获取计划进度

- **路径**：`GET /api/project/plans/:id/progress`
- **响应**：
  
  ```json
  {
  "totalAssociated": 10,
  "passed": 3,
  "failed": 1,
  "blocked": 1,
  "untested": 5,
  "progressPercent": 50.0
  }
  ```

- **口径**：`totalAssociated` 为已关联 case 快照数；`last_result` 为 `pass` 计入 `passed`、`fail` 计入 `failed`、`block` 计入 `blocked`，空值或 `untested` 计入 `untested`（历史脏数据值 `blocked` 亦计入 `blocked`）；`progressPercent` =（总数 − 未执行数）/ 总数 × 100，保留两位。
- **前端**：详情页页头进度条与「通过 / 失败 / 阻塞 / 待执行 / 共 N」统计取自本接口；标记执行结果后刷新，刷新失败不打断标记操作。

### 1.5 获取计划快照树

- **路径**：`GET /api/project/plans/:id/modules`
- **参数**：`documentId`（可选，按文档过滤）
- **响应**：裁剪后的节点树数组，数组元素为嵌套的根节点对象，字段为 `id`、`originalNodeId`、`parentId`、`title`、`type`、`priority`、`isAssociated`、`lastResult`、`lastExecutorId`、`lastExecutedAt`、`sortOrder`、`children`；节点为完整拷贝属性，其中 `isAssociated` 标识是否为规划用例，`lastResult` 取 `pass`/`fail`/`block`/`untested` 或 `null`。
- **前端**：详情页脑图始终传入当前选中文档的 `documentId` 加载单文档快照；不传时后端返回多文档多根，前端只取首个根节点，页面不使用该形态。

### 1.6 提交执行记录

- **路径**：`POST /api/project/plans/:id/records`

- **请求体**：
  
  ```json
  {
  "snapshotNodeId": "6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e21",
  "result": "pass",
  "note": "正常通过"
  }
  ```

- **校验**：仅 `is_associated=true` 且 `type='case'` 的节点可标记（否则返回错误码 1000011016，“只有关联的用例节点可标记执行结果”）；`result` 取值限 `pass` / `fail` / `block` / `untested`（untested 即重置回待执行）；除计划状态为 blocked（返回错误码 1000011025，“计划已阻塞，请先恢复后再操作”）外不校验计划状态；待开始（new）计划首次标记后自动转入 in_progress（已取代单独的“开始执行”操作），并记录项目活动 `PLAN_STARTED`。`note` 为可选备注，当前前端标记入口不提交备注。前端选中非 case 节点时先行拦截并提示“仅关联用例节点可标记执行结果”，标记通过脑图工具栏四态按钮或右键菜单直接提交，结果以徽标形式回显在节点右侧（待执行不渲染徽标）。
- **处理**：新增 ft_plan_execution_record，更新快照节点的 last_result、last_executor_id、last_executed_at；成功后前端刷新页头进度与详情。

### 1.7 同步最新用例

- **路径**：`POST /api/project/plans/:id/sync`
- **权限**：`plan:execute`（`@PreAuthorize` 校验，缺码返回 HTTP 403）；计划须未结束（new/in_progress），已结束返回错误码 1000011027；已阻塞返回错误码 1000011025（“计划已阻塞，请先恢复后再操作”）。
- **处理**：与评审同步逻辑一致，更新快照节点属性，保留执行记录。
- **前端**：二次确认（“同步将更新快照节点属性，已有执行记录不受影响”）→ 成功后重新拉取详情、进度与快照模块树并重载脑图。

### 1.8 获取模块快照树

- **路径**：`GET /api/project/plans/:id/module-tree`
- **响应**：模块快照树（目录/文档层级，含 `id`、`parentId`、`name`、`type`、`sortOrder`、`children`），供计划详情页左侧文档树展示与多文档切换（选中文档后以 `documentId` 调用 1.5 加载单文档快照脑图）。
- **前端**：加载后默认选中首个文档；同步或移除用例后重载，当前文档已被移除时回退到首个文档。

### 1.9 获取规划用例

- **路径**：`GET /api/project/plans/:id/cases`
- **响应**：`[{ "documentId": "...", "caseIds": ["..."] }]`，以原始文档/节点 ID 表示当前关联的用例，供调整弹窗回显预选。

### 1.10 调整规划用例

- **路径**：`PUT /api/project/plans/:id/cases`
- **前置**：计划状态为 new 或 in_progress（未结束即可调整，不限负责人）；已阻塞返回错误码 1000011025（“计划已阻塞，请先恢复后再操作”），已结束返回错误码 1000011027（“计划已结束，无法执行该操作”）。
- **请求体**：`{ "selectedNodes": [{ "documentId": "...", "caseIds": ["..."] }] }`（与创建计划的 selectedNodes 同构，为全量覆盖式提交）。
- **处理**：差量更新快照——新增文档生成模块/节点快照；移除文档删除其快照并自底向上清理空目录快照（执行记录保留作审计）；保留文档补全快照后新增的节点并按新选择重刷 `is_associated`（取消关联的节点保留已有执行结果，仅退出统计），新补节点快照初始 `last_result='untested'`；记录项目活动 `PLAN_CASES_UPDATED`。
- **前端**：详情页 [调整用例] 打开 CaseSelector 并以 1.9 结果回显，确认后提交，成功后刷新详情、进度、快照模块树与脑图。脑图内 [移除用例] 亦复用本接口：从当前规划中剔除单个用例，所属文档无剩余用例时整文档移除。

### 1.11 完成计划

- **路径**：`POST /api/project/plans/:id/complete`
- **权限**：`plan:execute`（`@PreAuthorize` 校验，缺码返回 HTTP 403），不校验计划负责人。
- **校验**：计划状态为 blocked 时返回错误码 1000011025（“计划已阻塞，请先恢复后再操作”）。
- **处理**：状态变更为 completed；不做未执行用例门槛校验，前端二次确认（提示“完成后将不可再标记执行结果”）后提交，记录项目活动 `PLAN_COMPLETED`；完成后详情页活跃态操作组（调整用例/同步用例/阻塞/完成执行）收起。

### 1.12 删除计划

- **路径**：`DELETE /api/project/plans/:id`
- **权限**：仅计划负责人（业务层校验，非负责人返回错误码 1000002001），任意状态可删（含已阻塞）。
- **处理**：无物理外键，事务中显式级联删除执行记录、节点快照、模块快照后删除计划本体，记录项目活动 `PLAN_DELETED`。

### 1.13 阻塞计划

- **路径**：`POST /api/project/plans/:id/block`
- **权限**：`plan:block`（`@PreAuthorize` 校验，缺码返回 HTTP 403），不校验计划负责人。
- **校验**：计划状态须为 new 或 in_progress，否则返回错误码 1000011020（“仅未开始或进行中的计划可阻塞”）。
- **处理**：状态置为 blocked（部分更新载体，仅回写 `id` + `status`）；记录项目活动 `PLAN_BLOCKED`（“阻塞测试计划「名称」”）。
- **说明**：阻塞期间提交执行记录、调整规划用例、同步、完成均返回错误码 1000011025；删除不受影响。

### 1.14 恢复计划

- **路径**：`POST /api/project/plans/:id/resume`
- **权限**：`plan:block`（`@PreAuthorize` 校验，缺码返回 HTTP 403），不校验计划负责人。
- **校验**：计划状态须为 blocked，否则返回错误码 1000011024（“仅已阻塞的计划可恢复”）。
- **处理**：状态置为 in_progress（部分更新载体）；记录项目活动 `PLAN_RESUMED`（“恢复测试计划「名称」”）。


### 1.15 编辑计划

- **路径**：`PUT /api/project/plans/{id}`
- **权限**：`plan:execute`（`@PreAuthorize` 校验，缺码返回 HTTP 403）；不校验计划负责人。
- **状态校验**：计划须为 new/in_progress；已阻塞返回错误码 1000011025（“计划已阻塞，请先恢复后再操作”），已结束返回错误码 1000011027（“计划已结束，无法执行该操作”）。
- **请求体**（字段均可选，仅提交发生变化的字段）：

  ```json
  {
    "name": "V1.4.2 回归测试（调整）",
    "description": "...",
    "executorId": "6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e10",
    "startTime": "2026-09-20T00:00:00",
    "endTime": "2026-09-24T00:00:00",
    "environment": "staging",
    "requirementIds": ["6f1a2c3e-8b41-4d2a-9c66-1a2b3c4d5e30"],
    "replan": true
  }
  ```

- **`replan`**：可选布尔值，默认 false。仅当提交的 `requirementIds` 与当前关联不同且用户确认重新规划时为 true；为 false 时既不重新解析用例，也不改动任何快照。
- **校验**：`name` 非空且不超过 100 字；`executorId` 为当前工作空间成员；`requirementIds` 均属于当前项目，否则返回 1000018001。
- **处理**：部分更新（C11），只回写实际传入的字段。`replan=true` 时事务内先将 `replan_status` 置 `running`、`replan_requested_at` 置当前时间，提交后触发后台异步任务：按最新 `requirement_ids` 调用 `resolvePlannedCases` 重新解析用例，复用 1.10 的快照差量逻辑更新快照并回写 `snapshot_synced_at`（保留既有执行记录），最终回写 `succeeded` / `failed`、`replan_message`、`replan_finished_at`。`replan_status=running` 期间再次提交编辑返回错误码 1000011028。
- **响应**：200，结构同 1.3（含 `replanStatus`）。
- **前端**：列表页 [编辑]（需 `plan:execute`）回显详情 → 提交时对比新旧 `requirementIds`，变化则提示「检测到关联需求发生变化，是否重新规划测试用例？」→ 确认后带 `replan=true` 提交并提示「已保存，正在后台重新规划测试用例」；选择否仅提示「已保存」。随后轮询 1.16 直至终态，刷新列表并提示结果。

### 1.16 查询重规划状态

- **路径**：`GET /api/project/plans/{id}/replan-status`
- **响应**：`{ "status": "idle|running|succeeded|failed", "message": "...", "requestedAt": "...", "finishedAt": "..." }`；`status=idle` 表示从未发起过重规划。
- **前端**：编辑提交后每 2 秒轮询一次，终态停止轮询并刷新列表；页面卸载时停止轮询。

## 2. 同步最新用例

触发后批量对比原始节点与当前节点，更新快照属性；若原始节点不存在，快照节点 is_deleted=1。执行记录不受影响，最终状态保留。


### 2.1 计划列表页（功能测试 - 测试计划）

**路由**：`/workspace/projects/plans`（`meta.title` 测试计划、`mode` project，无独立菜单项）

列表同时作为功能测试页的子模块渲染：功能测试页左侧菜单选中“测试计划”时展示，地址为 `/workspace/projects/functional-testing?tab=plans`（菜单挂载于功能测试页，权限 `case:view`）。详情页面包屑返回该地址。

**页面布局**（验收基准 `web/demos/project/plans.html`，裁剪口径见交互设计 `docs/05-interaction-design/03-function-testing/06-workspace-ui-test-plan.md` 1.1）：

```
┌──────────────────────────────────────────────────────────────────┐
│  测试计划                                        [+ 新建计划]     │
│  计划维度组织用例执行与通过率统计                                  │
├──────────────────────────────────────────────────────────────────┤
│  [🔍 计划名称…] [状态: 全部 ▼] [查询] [重置]      共 18 个计划     │
├──────────────────────────────────────────────────────────────────┤
│  名称（含关联需求/用例副行）│负责人│起止时间│环境│状态│进度│通过率│创建时间│操作│
│  V1.4.2 回归测试    │张明 │09-20 ~ 09-24│staging│●执行中│[58%]│94.6%│…│
│    └ 关联需求 3 项 · 关联用例 47 条                                    │
│  支付网关专项       │李华 │09-19 ~ 09-23│staging│●已阻塞│[31%]│87.2%│…│
│  营销活动冒烟       │王芳 │09-22 09:00  │production│●未开始│[0%]│  — │…│
├──────────────────────────────────────────────────────────────────┤
│  共 18 个计划 · 每页 20 条                          ‹  1  2  3  › │
└──────────────────────────────────────────────────────────────────┘
```

**交互说明**：

| 操作   | 触发方式     | 反馈                           |
| ---- | -------- | ---------------------------- |
| 筛选   | 关键字回车/清空即查询；状态下拉选择后点[查询]；[重置] | 重置清空条件并回到首页，其余查询在触发时刷新列表 |
| 分页   | 点击页码     | 按当前筛选条件加载对应页（每页 20 条，仅切换页码，不提供每页条数切换） |
| 新建计划 | 点击页头[+ 新建计划] | 弹出创建表单：名称（必填）、描述、负责人（成员下拉）、开始/结束时间、执行环境、关联需求（需求选取器，多选，至少一个需求），提交成功后跳转详情页 |
| 编辑 | 点击[编辑]（new/in_progress 且具备 `plan:execute`） | 弹出回显当前名称 / 描述 / 负责人 / 起止时间 / 环境 / 关联需求的编辑表单；提交时若关联需求变化则提示是否重新规划 → 保存后轮询状态并刷新列表 |
| 进入执行 | 点击[进入执行]（执行中）或计划名称链接 | 跳转至计划详情页                     |
| 查看详情 | 点击[查看详情]（其余状态）   | 跳转至计划详情页                     |
| 完成 | 点击[完成]（未开始/执行中且具备 `plan:execute`） | 二次确认 → 调用 1.11，状态置 completed，列表刷新 |
| 恢复 | 点击[恢复]（已阻塞且具备 `plan:block`） | 二次确认 → 调用 1.14，状态置 in_progress，列表刷新 |
| 删除 | 点击[删除] | 二次确认 → 级联删除快照与执行记录，列表刷新        |

状态显示文案：未开始（new）/ 执行中（in_progress）/ 已完成（completed）/ 已阻塞（blocked）/ 已关闭（closed）；进度按状态配色（执行中橙、已阻塞红），通过率未开始显示 `—`、已完成绿色加粗。

**状态分支**：列表卡片加载中显示 loading；空数据展示“暂无计划”；查询、完成、恢复、删除失败均以错误消息提示；缺 `plan:execute` / `plan:block` 时对应按钮不展示，会话期间权限被撤销则由服务端以 HTTP 403 拒绝并提示。


### 2.2 计划详情页

**路由**：`/workspace/projects/plans/:planId`（`meta.title` 计划详情、`mode` project）

**页面布局**：

```
┌────────────────────────────────────────────────────────────────────┐
│ 测试计划 › 功能测试计划                                              │
│ ┌────────────────────────────────────────────────────────────────┐ │
│ │ 功能测试计划 [执行中]  [━━━]  通过 3  失败 1  阻塞 1  待执行 5  共 10 │ │
│ │                      [调整用例][同步用例][阻塞][完成执行]          │ │
│ ├─────────────┬──────────────────────────────────────────────────┤ │
│ │ 快照文档 只读 │  [✅通过][❌失败][❓阻塞][🔄待执行][🗑移除用例]        │ │
│ │ ▾ 测试目录   │                                                  │ │
│ │   📄 文档 A  │        快照脑图（只读，禁用画布编辑）               │ │
│ │   📄 文档 B  │        节点右侧徽标回显 ✓通过 / ✕失败 / ⚠阻塞       │ │
│ │             │        右键：标记执行结果 / 从计划中移除            │ │
│ └─────────────┴──────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

页头为面包屑 + 单行标题区：面包屑「测试计划 › 计划名」，点击「测试计划」返回功能测试页并激活测试计划子模块（`?tab=plans`）；标题区含标题与状态标签，右侧为进度条与分色统计（通过绿 / 失败红 / 阻塞深红 / 待执行默认 / 共 N）、操作按钮组，统计与按钮组以竖线分隔。活跃态（new/in_progress）展示 [调整用例][同步用例][阻塞][完成执行]，已阻塞仅展示 [恢复]，已完成/已关闭无操作按钮；按钮按「计划状态 + 权限码」双条件显隐——[同步用例][完成执行] 需 `plan:execute`，[阻塞][恢复] 需 `plan:block`，[调整用例] 仅按状态，缺码不展示入口。删除计划仍由后端校验计划负责人（非负责人返回错误码 1000002001）。

详情页使用独立的计划脑图组件（`PlanMindMap`，只读画布），快照树数据以嵌套根节点对象形式返回，节点右侧显示执行结果徽标；左侧为只读快照文档树（目录点击仅展开/收起），未选中文档时右侧展示“请在左侧选择一个文档”，快照树为空时展示“暂无快照文档”。

**交互说明**：

| 操作     | 触发方式                 | 反馈                 |
| ------ | -------------------- | ------------------ |
| 标记执行结果 | 选中关联用例节点 → 工具栏四态按钮或右键「标记执行结果」 | 提交执行记录，节点徽标与页头进度实时刷新（无备注输入） |
| 移除用例   | 工具栏[🗑移除用例]或右键「从计划中移除」（活跃态 + 已关联 case 节点） | 二次确认 → 复用 1.10 从规划剔除该用例并重载快照树/脑图/进度，执行记录保留作审计 |
| 调整用例   | 点击[调整用例]（活跃态）        | CaseSelector 回显当前规划 → 确认后全量覆盖更新并刷新 |
| 同步用例   | 点击[同步用例]（活跃态 + `plan:execute`） | 确认 → 更新快照属性，重载详情、进度、快照模块树与脑图，保留执行记录 |
| 阻塞     | 点击[阻塞]（活跃态 + `plan:block`）    | 二次确认 → 状态变更为已阻塞，操作组收敛为 [恢复] |
| 恢复     | 点击[恢复]（已阻塞 + `plan:block`）    | 二次确认 → 已阻塞回到执行中（in_progress） |
| 完成执行   | 点击[完成执行]（活跃态 + `plan:execute`） | 二次确认 → 状态置 completed，活跃态操作组收起 |
| 切换文档   | 点击左侧文档树文档节点          | 右侧脑图切换为该文档的快照（目录点击仅展开/收起，未选中文档时不切换） |

**状态分支**：页面加载中整体 loading；详情、进度、快照模块树任一加载失败以错误消息提示；标记/调整/同步/完成在已阻塞计划上由后端拦截并返回错误码 1000011025，已结束计划的调整与同步返回错误码 1000011027；缺 `plan:execute` / `plan:block` 时入口不展示，会话期间权限被撤销时由 `@PreAuthorize` 以 HTTP 403 拒绝并以错误消息提示；删除计划非负责人由后端返回错误码 1000002001；标记执行成功后进度刷新失败不打断操作。

## 3. 权限点与实施说明

### 3.1 权限点

计划模块权限点挂 `plan` 节点，`scope = workspace`：

| code | 名称 | 状态 |
| ---- | ---- | ---- |
| `plan:view` | 查看计划 | 既有 |
| `plan:create` | 创建计划 | 既有 |
| `plan:execute` | 执行计划（同步、完成执行） | 既有，本次起由服务端收口 |
| `plan:close` | 关闭计划 | 既有（暂无接口消费） |
| `plan:block` | 阻塞与恢复计划 | **新增** |

### 3.2 权限码与方法映射

| 接口 | 动作 | 服务端校验 |
| ---- | ---- | ---- |
| `POST /api/project/plans/:id/sync` | 同步用例 | `plan:execute`（`@PreAuthorize`）→ 项目成员 → 状态校验 |
| `POST /api/project/plans/:id/complete` | 完成执行 | `plan:execute`（`@PreAuthorize`）→ 项目成员 → 状态校验 |
| `POST /api/project/plans/:id/block` | 阻塞计划 | `plan:block`（`@PreAuthorize`）→ 项目成员 → 状态校验 |
| `POST /api/project/plans/:id/resume` | 恢复计划 | `plan:block`（`@PreAuthorize`）→ 项目成员 → 状态校验 |
| `DELETE /api/project/plans/:id` | 删除计划 | 项目成员 → 计划负责人（1000002001），不挂权限码 |
| 其余计划接口（列表、详情、快照、进度、规划用例、提交执行记录） | 读与执行标记 | 项目成员校验（1000002001），不挂权限码 |

`@PreAuthorize` 缺码时由安全链返回 HTTP 403（框架全局错误码），不占用计划号段；`WorkspaceRoleInterceptor` 注入的即 `sys_role.permissions` 中登记的码，故种子必须同时登记权限点与角色授予。前端入口与服务端共用同一权限码，显隐口径见 `docs/05-interaction-design/03-function-testing/06-workspace-ui-test-plan.md` 1.2。

### 3.3 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/src/main/java/io/github/xiaomisum/robotest/controller/project/TestPlanController.java` | sync / complete 挂 `hasAuthority('plan:execute')`，block / resume 挂 `hasAuthority('plan:block')`，仅注解不含业务逻辑（C2） |
| `server/src/main/java/io/github/xiaomisum/robotest/service/domain/plan/TestPlanServiceImpl.java` | 移除同步、完成、阻塞、恢复的计划负责人校验，删除计划仍校验负责人 |
| `server/src/main/resources/db/schema.sql` | 18.3 新增 `plan:block` 权限点，18.8 预置「管理员」「成员」角色 `permissions` 追加同码 |
| `web/src/composables/project/functional-testing/plan/usePlanDetail.ts`、`web/src/pages/project/functional-testing/PlanDetailPage.vue` | 详情页操作按钮按「状态 + 权限码」显隐 |
| `web/src/pages/project/functional-testing/PlanListPage.vue` | 列表 [完成] / [恢复] 按权限码显隐 |

**数据库迁移说明（C5）**

```sql
-- 新增权限点（全量建库同步进 schema.sql 18.3）
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted)
VALUES ('c0000000-0000-0000-0000-000000000032', 'plan:block', '阻塞与恢复计划',
        'plan', '测试计划', '功能测试', 'workspace', 5,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT DO NOTHING;

-- 存量库：预置空间角色授予（幂等，可重复执行）
UPDATE sys_role
SET permissions = permissions || '["plan:block"]'::jsonb, updated_at = CURRENT_TIMESTAMP
WHERE is_deleted = FALSE AND type = 'workspace' AND NOT (permissions ? 'plan:block');
```

- UUID 主键使用框架默认策略，无物理外键，不新增表与索引（C5 / C9）。

**测试要点（C8 ≥ 70%）**

- `TestPlanServiceImplTest`：同步、完成、阻塞、恢复的「非负责人拒绝」用例改为权限码口径（服务层不再校验负责人，授权由 `@PreAuthorize` 承担），删除计划保留负责人用例。
- `SchemaSeedConsistencyTest`：增补 `plan:block` 权限点已登记、且预置「管理员」「成员」两角色均已授予的断言。
- 前端 `usePlanDetail.spec.ts` 与计划列表页 spec：增补缺权限码时 [阻塞][恢复][同步用例][完成执行] 不展示的用例。

## 4. 编辑与异步重规划实施说明

### 4.1 数据变更（C5 / C9）

```sql
ALTER TABLE ft_plan
    ADD COLUMN replan_status VARCHAR(20),
    ADD COLUMN replan_message TEXT,
    ADD COLUMN replan_requested_at TIMESTAMP,
    ADD COLUMN replan_finished_at TIMESTAMP;
```

列均可空，`replan_status` 取 `running` / `succeeded` / `failed`，空值对外映射为 `idle`；`replan_finished_at` 在终态写入。全量建库同步进 `schema.sql` 的 `ft_plan` 建表定义；无新表、无物理外键、无新索引（状态仅按主键详情读取，不参与列表筛选）。与既有 `snapshot_synced_at` 并列，前者表示快照新鲜度，本次新增字段只表示重规划任务状态。

### 4.2 异步执行边界

- 编辑接口所在事务只负责基础信息、关联需求与 `running` 状态的持久化；异步任务通过独立 `@Async` 执行器触发，避免自调用导致异步失效。
- 异步任务按 `planId` 读取最新 `requirement_ids` 并解析用例，调用差量快照更新并回写 `snapshot_synced_at`；取消关联用例时保留历史执行结果作审计。
- 任务成功 / 失败均以部分更新载体回写状态字段；失败原因写入 `replan_message`，前端轮询到 `failed` 后提示用户，可重新编辑再触发。
- 应用重启时残留的 `running` 状态由启动扫描置为 `failed`（`replan_message` 记「服务重启导致中断」），避免前端无限轮询。

### 4.3 测试要点（C8 ≥ 70%）

- `TestPlanServiceImplTest`：编辑部分更新、 blocked / 终态 / 缺权限拒绝、`replan` 并发拒绝。
- 异步执行器单测：成功回写 `succeeded`、解析失败回写 `failed`、快照差量更新保留执行记录并回写 `snapshot_synced_at`。
- 前端计划列表页测试：关联需求变化时提示、确认 / 取消分支、轮询终态后刷新列表，缺 `plan:execute` 时 [编辑] 不展示。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 与前端实现对齐：补录计划详情与进度接口，删除未实现的节点执行记录与关闭计划接口，修正快照树字段与 UUID 口径，重写列表页与详情页结构、交互及状态分支 |
| V1.0 | 2026-10-18 | 功能测试域表名统一 `ft_` 前缀：`test_plan*` → `ft_plan*` |
| V1.0 | 2026-10-20 | 计划授权改为权限码：阻塞/恢复挂 `plan:block`、同步/完成挂 `plan:execute`，删除仍限负责人；新增第 3 章权限点、方法映射与迁移说明，列表页与详情页入口改「状态 + 权限码」双条件显隐 |
| V1.0 | 2026-11-15 | 创建计划改为关联需求：新增 `requirementIds`（`selectedNodes` 改为可选）、按 `ft_case_requirement` 反查用例，详情返回 `requirements`、列表返回 `requirementCount` |
| V1.0 | 2026-11-18 | 新增计划编辑：1.3 详情补 `requirementIds` / 重规划状态字段，1.15 编辑与 1.16 状态查询接口，2.1 列表 [编辑] 入口，第 4 章数据变更与异步执行边界，错误码 1000011028 |
