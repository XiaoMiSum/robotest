# 软件测试平台——执行与历史

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 场景执行与单步骤调试

### 1.1 触发场景执行

- **路径**：`POST /api/project/api-scenes/{sceneId}/executions`
- **说明**：触发场景执行，公共执行引擎语义见《API 测试基础设施详细设计说明书》3.2.1，此处补充场景侧约定：
  - 执行入口有二：**列表页行内 [执行]**（快速执行弹窗）与**编辑页顶部 [运行 ]**（采纳交互原型）；编辑页 [运行 ] 以页面实时配置执行，执行前由前端自动保存未保存的修改（乐观锁版本以保存后为准）；
  - 请求体可携带 `environmentId`（缺省使用场景默认环境 `api_scene.environment_id`）与 `variableOverrides`（运行时变量覆盖，优先级最高）；
  - 场景执行失败语义固定为「停止运行」：任一步骤失败即终止后续步骤，后续步骤在报告中标记未执行（不再提供失败规则配置与字段）；
  - 场景页手动执行（编辑页 [运行 ] 与列表页行内 [执行 ]）产生的报告在报告生成侧标记 `source = scene`、`report_type = scene`（见《API 测试基础设施详细设计说明书》2.1.4），不进报告列表页，通过执行记录弹窗查看（见《测试报告详细设计说明书》4.6）；定时任务（含调度页"立即执行"）由调度器聚合生成套件报告（`report_type = suite`），不受影响；
- **响应**：`{ "executionId": "018f...", "status": "pending" }`

### 1.2 查询执行状态（轮询）

- **路径**：`GET /api/project/executions/:executionId`
- **说明**：前端以 2 秒间隔轮询执行状态（见《API 测试基础设施详细设计说明书》3.2.2）。状态机：`pending → running → success / failed / error`；结束后更新列表页与编排页的状态徽标，并提供报告入口（报告接口见《测试报告详细设计说明书》）。

### 1.3 单步骤调试

- **路径**：`POST /api/project/scenes/:sceneId/steps/:stepId/debug`
- **请求体**：

```json
{
  "environmentId": "018f..."
}
```

- **说明**：仅执行指定步骤（使用当前环境配置），仅执行单步骤、不产生执行记录与报告；支持未保存配置的即时调试（前端先临时保存步骤配置再调试，失败可回滚）。步骤不存在返回 7202。
- **响应**：

```json
{
  "stepResult": {
    "stepId": "018a...",
    "status": "success",
    "durationMs": 230,
    "request": { "method": "POST", "url": "/api/auth/login", "headers": {}, "body": {} },
    "response": { "statusCode": 200, "headers": {}, "body": "..." },
    "validatorResults": [ { "name": "验证返回码", "passed": true } ],
    "extractedVariables": { "token": "eyJhbGciOi..." }
  }
}
```

### 1.4 草稿单步骤调试（创建态未保存）

- **路径**：`POST /api/project/api-scenes/draft/debug-step`
- **请求体**：

```json
{
  "environmentId": "018f...",
  "sceneVariables": [ { "name": "token", "value": "abc" } ],
  "step": {
    "name": "登录",
    "stepType": "link",
    "sourceType": "link",
    "sourceId": "018a...",
    "requestConfig": { "method": "GET", "url": "/api/auth/login" },
    "validators": [],
    "extractors": [],
    "stepVariables": []
  }
}
```

- **说明**：仅供创建态未保存场景在编辑页调试单步骤；步骤体由前端实时编排数据构造，无需先落库。返回结构同 1.3 的 `stepResult`。
- **响应**：`{ "stepResult": { "status": "success", "request": {...}, "response": {...} } }`

### 1.5 草稿场景执行（编辑页 [运行 ]，创建态未保存）

- **路径**：`POST /api/project/api-scenes/draft/execute`
- **请求体**：

```json
{
  "name": "草稿场景",
  "environmentId": "018f...",
  "sceneVariables": [ { "name": "token", "value": "abc" } ],
  "steps": [
    { "name": "登录", "stepType": "http", "requestConfig": { "method": "GET", "url": "/api/auth/login" } },
    { "name": "下单", "enabled": true, "requestConfig": { "method": "POST", "url": "/api/order" } }
  ]
}
```

- **说明**：供创建态未保存场景「运行场景」使用：以页面实时编排的步骤体执行，**不落库、不产生执行记录与报告**；仅用于创建前校验编排正确性。步骤维度字段对齐 `steps` 内嵌步骤对象（name/stepType/sourceType/sourceId/requestConfig/validators/extractors/variables/enabled），`enabled=false` 或解析失败（如链接源已删除且无快照）的步骤标记 `skipped`/`error`，整体状态按「停止运行」语义：任一失败步骤 → `failed`，全部跳过 → `skipped`，否则 `success`。
- **响应**：

```json
{
  "status": "failed",
  "passed": 1,
  "failed": 1,
  "skipped": 0,
  "durationMs": 520,
  "steps": [
    { "status": "success", "name": "登录", "durationMs": 210, "request": {...}, "response": {...} },
    { "status": "error", "name": "下单", "errorMessage": "步骤缺少请求配置" }
  ]
}
```


## 2. 执行历史与变更历史

### 2.1 查询执行历史

- **路径**：`GET /api/project/scenes/:sceneId/executions?pageNo=1&pageSize=20`
- **数据来源**：`api_execution_record`（见《API 测试基础设施详细设计说明书》2.1.3）。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "status": "success",
      "executionMode": "platform",
      "triggerType": "manual",
      "executedAt": "2026-08-17T10:30:00Z",
      "durationMs": 3200,
      "reportId": "018e..."
    }
  ],
  "total": 12
}
```

### 2.2 查询变更历史

- **路径**：`GET /api/project/scenes/:sceneId/change-history?pageNo=1&pageSize=20`
- **数据来源**：`api_change_history`（见《API 测试基础设施详细设计说明书》2.1.2，只读追溯，不提供编辑/删除）。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "version": 3,
      "operatorName": "张三",
      "changeSummary": "更新步骤：发送登录请求（请求配置）",
      "changedAt": "2026-08-17T09:30:00Z"
    }
  ],
  "total": 5
}
```


