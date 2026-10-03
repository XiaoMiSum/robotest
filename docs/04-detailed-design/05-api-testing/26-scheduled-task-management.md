# 软件测试平台——任务管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 定时任务管理

> **通用口径**：上下文标识（workspace / project）经请求头传递，不出现在 URL；下文响应示例只展示 `data` 字段内容；分页参数统一为 `pageNo` / `pageSize`，响应为 `{ "list": [...], "total": n }`。

### 1.1 查询定时任务列表

- **路径**：`GET /api/project/scheduled-tasks?taskType=&pageNo=1&pageSize=20`
- **权限**：`api-timer:view`
- **参数**：`taskType` 可选（`scene_execute` / `import_swagger`），缺省查询全部；前端类型筛选变化时重置 `pageNo = 1` 重新拉取。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "taskType": "scene_execute",
      "name": "每日回归测试",
      "description": "每日凌晨执行模块A与场景X回归",
      "boundObjectId": null,
      "boundObjectName": null,
      "executionScope": "modules",
      "moduleIds": ["018d...", "018c..."],
      "sceneIds": [],
      "openapiUrl": null,
      "environmentId": "018g...",
      "environmentName": "staging",
      "cronExpression": "0 2 * * *",
      "enabled": true,
      "lastExecutionStatus": "success",
      "lastExecutionAt": "2026-08-17T02:00:00Z",
      "nextExecutions": ["2026-08-18T02:00:00Z", "2026-08-19T02:00:00Z", "2026-08-20T02:00:00Z"],
      "createdAt": "2026-08-01T10:00:00Z"
    },
    {
      "id": "018f...",
      "taskType": "import_swagger",
      "name": "订单接口同步",
      "description": "同步订单模块接口",
      "executionScope": null,
      "moduleIds": [],
      "sceneIds": [],
      "openapiUrl": "https://example.com/v3/api-docs",
      "environmentId": null,
      "environmentName": null,
      "cronExpression": "0 * * * *",
      "enabled": true,
      "lastExecutionStatus": "success",
      "lastExecutionAt": "2026-08-17T02:00:00Z",
      "nextExecutions": ["2026-08-18T02:00:00Z"],
      "createdAt": "2026-08-01T10:00:00Z"
    }
  ],
  "total": 5
}
```

- `boundObjectId` / `boundObjectName` 为历史遗留字段（旧版绑定对象），新任务为 `null`。
- `nextExecutions` 仅对启用任务计算（最多 3 个），停用任务返回空数组。
- **时间口径**：`nextExecutions` 为 cron 触发时刻（服务器本地钟面）换算到 UTC 钟面后的值；前端按浏览器时区还原展示，保证与服务器实际触发时刻一致。创建接口响应 `nextExecutionAt`、校验接口响应 `nextExecutions` 同口径。

### 1.2 创建定时任务

- **路径**：`POST /api/project/scheduled-tasks`
- **权限**：`api-timer:edit`
- **请求体**：

```json
{
  "taskType": "scene_execute",
  "name": "每日回归测试",
  "description": "每日凌晨执行模块A与场景X回归",
  "executionScope": "modules",
  "moduleIds": ["018d...", "018c..."],
  "environmentId": "018g...",
  "cronExpression": "0 2 * * *",
  "enabled": true
}
```

- **校验**：
  - `taskType` 必填，仅 `scene_execute` / `import_swagger`；`name` 必填且 ≤ 200 字符；`description` ≤ 500 字符；`cronExpression` 必填且 ≤ 50 字符（解析后校验语法）；`openapiUrl` ≤ 2000 字符。
  - `taskType = scene_execute` 时，`environmentId` 必填且为同项目环境；`executionScope` 必填：
    - `all`：执行项目下全部可执行场景（已发布状态）；
    - `modules`：`moduleIds` 必填（多选模块，均须为同项目模块，执行其下场景）；
    - `scenes`：`sceneIds` 必填（多选场景，均须为同项目可执行场景）；
  - `taskType = import_swagger` 时，`openapiUrl` 必填（OpenAPI/Swagger JSON 文件的 URL 地址），保存时校验可达性与合法性；此类型不落执行范围、模块/场景与环境字段。
  - `enabled` 可选，缺省为 `true`。
  - 返回下次执行时间预览。
- **响应**：

```json
{
  "id": "018f...",
  "nextExecutionAt": "2026-08-18T02:00:00Z"
}
```

### 1.3 更新定时任务

- **路径**：`PUT /api/project/scheduled-tasks/:id`
- **权限**：`api-timer:edit`
- **请求体**：同 1.2。
- **响应**：`true`
- **说明**：启停状态不由本接口维护（请求中的 `enabled` 被忽略，启停仅走 1.4）；任务类型切换时，后端清空该类型用不到的字段（执行范围 / 模块 / 场景 / 环境与接口文档 URL 互斥）。

### 1.4 启停定时任务

- **路径**：`PUT /api/project/scheduled-tasks/:id/toggle`
- **权限**：`api-timer:edit`
- **请求体**：`{ "enabled": false }`
- **响应**：`true`

### 1.5 删除定时任务

- **路径**：`DELETE /api/project/scheduled-tasks/:id`
- **权限**：`api-timer:edit`
- **响应**：`true`

### 1.6 立即执行

- **路径**：`POST /api/project/scheduled-tasks/:id/execute`
- **权限**：`api-timer:edit`
- **说明**：手动触发一次执行，不受 Cron 调度影响。立即执行与 Cron 触发一致，聚合生成**套件报告**（`source = schedule`、`report_type = suite`），保证所产报告保留在报告列表（区别于场景页 [运行] 的 `source = scene` 场景报告）。调度页展示的 `triggerType` 仍为 manual，场景执行记录语义不变。
- **校验**：若上一次执行未结束（`lastExecutionStatus = running`），返回错误码 1000017504（`API_SCHEDULED_TASK_RUNNING`）；前端在列表行内已将「立即执行」按钮置灰并提示"上一次执行未结束，请稍后再试"，未置灰时先弹确认框，成功后提示"已触发执行"并刷新列表。
- **响应**：
  - 测试计划任务异步入队，`executionId` 回填任务 ID 作为本次触发句柄：

```json
{
  "executionId": "018f...",
  "status": "running"
}
```

  - 接口同步任务同步执行完成，`executionId` 为导入记录 ID，`status` 为导入最终状态（`success` / `failed`）。

### 1.7 查询执行记录

- **路径**：`GET /api/project/scheduled-tasks/:id/executions?pageNo=1&pageSize=10`
- **权限**：`api-timer:view`
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "triggerType": "scheduled",
      "status": "success",
      "errorMessage": null,
      "reportId": "018g...",
      "importRecordId": null,
      "importSummary": null,
      "triggeredAt": "2026-08-17T02:00:00Z",
      "durationMs": 5230
    }
  ],
  "total": 30
}
```

- `reportId` 为测试计划任务产生的套件报告 ID；`importRecordId` / `importSummary`（新增 / 更新 / 失败数）仅接口同步任务有值，测试计划任务为 `null`。

### 1.8 校验 Cron 表达式

- **路径**：`POST /api/project/scheduled-tasks/validate-cron`
- **权限**：`api-timer:view`
- **请求体**：`{ "cronExpression": "0 2 * * *" }`
- **响应（合法）**：

```json
{
  "valid": true,
  "description": "每天凌晨 2:00",
  "nextExecutions": [
    "2026-08-18T02:00:00Z",
    "2026-08-19T02:00:00Z",
    "2026-08-20T02:00:00Z",
    "2026-08-21T02:00:00Z",
    "2026-08-22T02:00:00Z"
  ]
}
```

- **响应（不合法）**：`{ "valid": false }`（`description`、`nextExecutions` 缺省）；`nextExecutions` 合法时最多 5 个。

### 1.9 权限口径

- 页面入口：接口测试模块侧边菜单「定时任务」挂 `api-timer:view`；接口测试路由入口对任一接口测试 `*:view` 权限开放。
- 后端：列表、执行记录、Cron 校验要求 `api-timer:view`；创建、更新、启停、删除、立即执行要求 `api-timer:edit`。
- 前端页面内操作按钮（新建任务 / 启停开关 / 立即执行 / 编辑 / 删除）不按 `api-timer:edit` 裁剪，无编辑权限时点击由后端鉴权拦截。

---

## 2. 删除保护

- 被定时任务选中的接口测试场景（`task_type = scene_execute`）受删除保护：`execution_scope = scenes` 时直接选中的场景、`execution_scope = all` 时项目全部场景、`execution_scope = modules` 时被选中模块下的场景均禁止删除，需先删除任务或调整执行范围。
- 被定时任务选中的模块（`task_type = scene_execute`）删除前校验引用：`all` 覆盖全部模块；`modules` 范围下模块自身或任一祖先模块被选中即视为被引用；`scenes` 范围下模块内场景被选中即视为被引用。存在引用时禁止删除。

---

## 3. 定时任务管理页

### 3.1 页面位置与权限

- 接口测试模块子页（路由 `/workspace/projects/api-testing`，子页切换经 `?tab=schedules`，不走路由跳转），侧边菜单「定时任务」，菜单项挂 `api-timer:view`。
- 无权限时菜单项不渲染；接口测试模块下所有菜单均不可见时，主区展示"暂无可用功能模块"空态。

### 3.2 页面结构

```
+--------------------------------------------------+
| 工具栏：[类型筛选下拉] ...... [＋ 新建任务]        |
+--------------------------------------------------+
| 任务表格                                          |
|  任务名称 | 类型 | 执行范围 | 调度 | 状态 | 上次执行 | 操作 |
|  ...(空态：el-empty 提示新建)...                 |
+--------------------------------------------------+
| 分页：total / prev / pager / next（pageSize=20）  |
+--------------------------------------------------+
| 新建/编辑弹窗（720px） · 场景选择弹窗              |
| 执行记录抽屉（600px，右侧）                       |
+--------------------------------------------------+
```

### 3.3 任务列表

- **筛选**：工具栏仅一个「类型」下拉（全部类型 / 测试计划 / 接口同步，可清空），变化即重置到第 1 页刷新。
- **列**：
  | 列 | 展示 |
  | --- | --- |
  | 任务名称 | 超长省略 + tooltip |
  | 类型 | `scene_execute` → 测试计划；`import_swagger` → 接口同步 |
  | 执行范围 | 测试计划：全部场景 / 指定模块×N / 指定场景×N；接口同步：接口文档 URL |
  | 调度 | Cron 表达式原文 |
  | 状态 | 行内启用开关（el-switch），切换即调用启停接口，成功提示"已启用 / 已停用"并刷新 |
  | 上次执行 | 状态标签（成功 / 失败 / 已跳过 / 执行中）+ 上次执行时间；无记录显示 `-` |
  | 操作 | 立即执行（`lastExecutionStatus = running` 时禁用）、执行记录、更多（编辑 / 删除） |
- **分页**：`pageSize` 固定 20，布局 `total, prev, pager, next`。
- **空态**：`暂无定时任务，点击右上角「新建任务」创建第一个任务`。
- **状态分支**：列表加载中卡片 loading；加载、筛选、启停、删除、执行失败均由页面捕获并以统一错误消息提示。

### 3.4 新建 / 编辑表单

弹窗 720px，点击遮罩不关闭，关闭即销毁内容；标题按 `editingId` 区分「新建定时任务 / 编辑定时任务」。

- **任务名称**：必填，≤ 200 字符。
- **任务描述**：可选，多行文本，≤ 500 字符。
- **任务类型**：单选必填 —— 测试计划（`scene_execute`）/ 接口同步（`import_swagger`）。
- **测试计划分支**：
  - **执行方式**：单选必填 —— 全部 / 指定模块 / 指定场景；
  - **指定模块**（`executionScope = modules` 时展示，必填）：模块树多选（勾选父模块包含其全部子模块），选项按场景维度的项目模块树加载，加载中显示 loading；
  - **指定场景**（`executionScope = scenes` 时展示，必填）：按钮打开场景选择弹窗，已选场景以可关闭标签展示（最多展示 8 个，超出折叠为 `+N`，可展开 / 收起）；编辑回填时按场景分页回查名称，已删除场景显示「（已删除场景）」；
  - **目标环境**：必填、可搜索，提示文案"测试计划任务需指定目标环境"。
- **接口同步分支**：**接口文档 URL** 必填，≤ 2000 字符，提示"接口同步任务必填；保存时校验 URL 可达性与合法性"；切换任务类型会清空另一分支的字段（执行范围 / 模块 / 场景 / 环境 与 URL 互斥）。
- **调度配置**（分隔线分区）：
  - **预设表达式**下拉：每小时、每天凌晨 2:00、每周一 2:00、每月 1 号 2:00、工作日 2:00，选中即回填并自动校验；
  - **Cron 表达式**：必填，≤ 50 字符，右侧「校验」「构建器」按钮；校验结果展示 `合法` + 自然语言描述，或 `表达式不合法`；下方展示下次执行预览（取前 3 次，浏览器时区）；
  - **Cron 构建器**：五段输入（分钟 0-59 / 小时 0-23 / 日 1-31 / 月 1-12 / 星期 0-7）+「应用」，应用后拼接为表达式并自动校验、收起。
- **启用状态**：表单不提供启停控件 —— 新建默认启用，编辑回填当前值但保存不改变启停（启停仅由列表状态开关维护）。
- **动作**：取消 / 保存（保存中 loading）；表单规则校验 + 保存前二次校验（Cron 非空、目标环境、模块 / 场景、URL 必填），成功提示"已创建 / 已更新"，关闭弹窗并刷新列表。

### 3.5 执行记录抽屉

- 右侧抽屉 600px，标题「执行记录 — {任务名}」，打开即加载第 1 页。
- **列**：触发时间、触发方式（手动 / 定时）、状态（标签）、耗时（< 1s 显示 `xxms`，否则 `x.xs`，空显示 `-`）、失败原因（`errorMessage`，无则 `-`）。
- **分页**：`pageSize = 10`，布局 `total, prev, pager, next`。
- 接口返回的 `reportId` / `importRecordId` / `importSummary` 当前**不在抽屉内展示**，无关联报告或导入结果跳转入口；加载失败以统一错误消息提示。

### 3.6 动作与交互

- **立即执行**：确认框（标题"立即执行"，正文"立即执行定时任务「{任务名}」？"）→ 调用 1.6 → 成功提示"已触发执行"并刷新列表；上一次执行未结束时按钮禁用，直接提示"上一次执行未结束，请稍后再试"。
- **编辑**：更多 → 编辑，回填表单后打开弹窗（执行方式为指定场景时回查已选场景名称）。
- **删除**：确认框（危险样式），文案"删除定时任务「{任务名}」？删除不影响已产生的执行记录与报告。"；成功提示"已删除"，当前页删空且非首页时回退一页，否则刷新当前页。

---

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-03 | 对齐实现：修正启停/立即执行接口路径与方法、补全执行记录字段与权限口径，重写管理页列表、表单、执行记录抽屉与动作交互描述 |
