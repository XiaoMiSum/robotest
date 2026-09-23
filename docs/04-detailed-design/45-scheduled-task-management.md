# 软件测试平台——任务管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

### 3.1 定时任务管理

#### 3.1.1 查询定时任务列表

- **路径**：`GET /api/project/scheduled-tasks?taskType=&page=1&pageSize=20`
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "taskType": "scene_execute",
      "name": "每日回归测试",
      "description": "每日凌晨执行模块A与场景X回归",
      "executionScope": "modules",
      "moduleIds": ["018d...", "018c..."],
      "sceneIds": [],
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
      "openapiUrl": "https://example.com/v3/api-docs",
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

- **时间口径**：`nextExecutions` 为 cron 触发时刻（服务器本地钟面）换算到 UTC 钟面后的值；前端按浏览器时区还原展示，保证与服务器实际触发时刻一致。创建接口响应 `nextExecutionAt`、校验接口响应 `nextExecutions` 同口径。

#### 3.1.2 创建定时任务

- **路径**：`POST /api/project/scheduled-tasks`
- **请求体**：

```json
{
  "taskType": "scene_execute",
  "name": "每日回归测试",
  "executionScope": "modules",
  "moduleIds": ["018d...", "018c..."],
  "environmentId": "018g...",
  "cronExpression": "0 2 * * *",
  "enabled": true
}
```

- **校验**：
  - Cron 表达式合法性（解析后校验语法）。
  - `taskType = scene_execute` 时，`environmentId` 必填且为同项目环境；`executionScope` 必填：
    - `all`：执行项目下全部可执行场景（已发布状态）；
    - `modules`：`moduleIds` 必填（多选模块，均须为同项目模块，执行其下场景）；
    - `scenes`：`sceneIds` 必填（多选场景，均须为同项目可执行场景）；
  - `taskType = import_swagger` 时，`openapiUrl` 必填（OpenAPI/Swagger JSON 文件的 URL 地址），保存时校验可达性与合法性。
  - 返回下次执行时间预览。
- **响应**：

```json
{
  "id": "018f...",
  "nextExecutionAt": "2026-08-18T02:00:00Z"
}
```

#### 3.1.3 更新定时任务

- **路径**：`PUT /api/project/scheduled-tasks/:id`
- **请求体**：同 3.1.2。

#### 3.1.4 启停定时任务

- **路径**：`PATCH /api/project/scheduled-tasks/:id/toggle`
- **请求体**：`{ "enabled": false }`

#### 3.1.5 删除定时任务

- **路径**：`DELETE /api/project/scheduled-tasks/:id`
- **响应**：`{ "success": true }`

#### 3.1.6 立即执行

- **路径**：`POST /api/project/scheduled-tasks/:id/execute-now`
- **说明**：手动触发一次执行，不受 Cron 调度影响。立即执行与 Cron 触发一致，聚合生成**套件报告**（`source = schedule`、`report_type = suite`），保证所产报告保留在报告列表（区别于场景页 [运行] 的 `source = scene` 场景报告）。调度页展示的 `triggerType` 仍为 manual，场景执行记录语义不变。
- **校验**：若上一次执行未结束（`lastExecutionStatus = running`），返回错误码 7603（`API_SCHEDULED_TASK_RUNNING`）。
- **响应**：

```json
{
  "executionId": "018f...",
  "status": "running"
}
```

#### 3.1.7 查询执行记录

- **路径**：`GET /api/project/scheduled-tasks/:id/executions?page=1&pageSize=20`
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "triggerType": "scheduled",
      "status": "success",
      "triggeredAt": "2026-08-17T02:00:00Z",
      "durationMs": 5230,
      "reportId": "018g..."
    }
  ],
  "total": 30
}
```

#### 3.1.8 校验 Cron 表达式

- **路径**：`POST /api/project/scheduled-tasks/validate-cron`
- **请求体**：`{ "cronExpression": "0 2 * * *" }`
- **响应**：

```json
{
  "valid": true,
  "description": "每天凌晨 2:00",
  "nextExecutions": [
    "2026-08-18T02:00:00Z",
    "2026-08-19T02:00:00Z",
    "2026-08-20T02:00:00Z"
  ]
}
```

---


### 4.2 删除保护

- 被定时任务选中的接口测试场景（`task_type = scene_execute` 且 `execution_scope = scenes` 时选中，或 `execution_scope = all / modules` 时涵盖）受删除保护，需先删除任务或移除选中。
- 被定时任务选中的模块（`task_type = scene_execute` 且 `execution_scope = modules`）删除前需校验其下场景是否存在任务引用。


### 5.1 定时任务管理页

- **任务列表**：展示任务名称、类型、执行范围、Cron 表达式、启用状态、上次执行。
- **任务编辑表单**：
  - 任务类型选择（测试计划 / 接口同步）。
  - 测试计划：选择执行方式（全部 / 指定模块（多选）/ 指定场景（多选））+ 选择目标环境；多选时展示对应选择器（模块树 / 场景列表）。
  - 接口同步：填写 OpenAPI/Swagger JSON 文件的 URL 地址。
  - Cron 编辑器（预设下拉 + 自定义输入 + 下次执行时间预览）。
- **执行记录列表**：触发时间、触发方式、状态、耗时、关联报告/导入结果链接；测试计划任务的关联报告为**套件报告**（点击跳转套件报告详情）。
- **立即执行按钮**：手动触发一次。

---


