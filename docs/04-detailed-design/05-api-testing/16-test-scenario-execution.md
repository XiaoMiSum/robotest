# 软件测试平台——执行与历史

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 场景执行与单步骤调试

> 权限口径：触发场景执行、单步调试、草稿执行需 `api-scene:execute`，执行历史与变更历史需 `api-scene:view`，均由服务端 `@PreAuthorize` 强制；前端不做按钮级权限隐藏（见 `docs/04-detailed-design/05-api-testing/12-test-scenario-overview.md`）。上下文（工作空间 / 项目）经请求头传递，不出现在 URL 与请求体中。

### 1.1 触发场景执行

- **路径**：`POST /api/project/api-scenes/{sceneId}/executions`
- **权限**：`api-scene:execute`
- **说明**：触发场景执行，公共执行引擎语义（异步提交立即返回、失败即停）见 `docs/04-detailed-design/05-api-testing/03-api-testing-infra-engine.md` §1、§2.3，此处补充场景侧约定：
  - 执行入口有二：**列表页行 [更多] → [执行]**（直接触发，toast `场景「×」执行已启动` 后刷新列表，无执行确认弹窗）与**编辑页顶部 [▶ 运行场景]**（创建态与编辑态均提供；编辑态存在未保存的基础信息改动时先执行保存，保存失败则不执行，乐观锁版本以保存后为准）；
  - 请求体可选，可携带 `environmentId` 与 `variableOverrides`：`environmentId` 缺省时后端回退项目默认环境（编辑页 [▶ 运行场景] 传入页面当前选择的环境，编辑态缺省为场景已保存的 `environmentId`，创建态默认取项目默认环境）；`variableOverrides`（运行时变量覆盖，优先级最高）为接口契约预留，当前执行链路未消费、前端也不传入（口径见 `docs/04-detailed-design/05-api-testing/15-test-scenario-variable.md` 3.1）；
  - `triggerType` / `source` 由定时任务链路内部携带（`scheduled` / `schedule`），页面手动执行不传，缺省落 `manual` / `scene`；
  - 场景不存在返回 `1000017301`，场景没有可执行步骤返回参数校验失败（`1000001001`），执行队列满返回 `1000017001`；
  - 场景执行失败语义固定为「停止运行」：任一步骤失败即终止后续步骤，后续步骤在报告中标记未执行（不提供失败规则配置与字段）；
  - 手动执行产生的报告在报告生成侧标记 `source = scene`、`report_type = scene`（见《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.1.4），不进报告列表页，通过执行历史气泡的 [报告] 打开详情（见 `docs/04-detailed-design/05-api-testing/30-test-report-detail.md` 4）；定时任务（含调度页“立即执行”）由调度器聚合生成套件报告（`report_type = suite`），不受影响。
- **响应**：`{ "executionId": "018f...", "status": "pending" }`

### 1.2 执行状态与结果查看（不轮询）

- **状态查询**：执行状态**无独立 HTTP 查询接口**，也**没有取消执行的 HTTP 接口**；前端触发后即结束（toast `场景已触发执行（executionId）`），状态与结果通过执行历史与报告按需查看（见 `docs/04-detailed-design/05-api-testing/03-api-testing-infra-engine.md` §1、§3）。
- **执行记录状态机**：`pending → running → success / failed / error`，由 `SceneExecutionLauncher` 随执行推进持久化（详见同文档 3.1）。
- **报告状态**：`success` / `partial` / `failed`（判定与前端渲染口径见 `docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md` 1.6）；步骤状态 `success` / `failed` / `error` / `skipped` 渲染为「成功 / 失败 / 执行异常 / 跳过」。
- **查看路径**：
  - 编辑页顶部 [执行历史] 气泡（见 2.1）：`pending` / `running` 记录无 `reportId`，不显示 [报告] 按钮，执行结束后出现；
  - [报告] 打开「报告详情」弹窗（`ReportDetailDialog`），调用 `GET /api/project/reports/:reportId`（权限 `api-report:view`，接口见 `docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md` 1.2）；共享的套件报告默认定位当前场景明细，并提供 [查看完整套件] 切换；
  - 列表页「最近执行」列渲染执行记录 `status` 原值（`success` 成功 / `failed` 失败 / `running` 执行中 / `pending` 等待中 / `error` 异常，无记录显示「—」，筛选项含「未执行」），仅在进入列表、筛选变更或触发执行后刷新，**不轮询**。

### 1.3 单步骤调试

- **路径**：`POST /api/project/api-scenes/{sceneId}/steps/{stepId}/debug`
- **权限**：`api-scene:execute`
- **请求体**：

```json
{
  "environmentId": "018f..."
}
```

- **说明**：仅执行指定步骤（跳过前置步骤），同步返回结果，不产生执行记录与报告；`environmentId` 必填（`@NotNull` 校验）。仅保存态场景可用，创建态点击 [调试] 提示「创建成功后可在编辑页单步调试」，不发起请求。场景不存在返回 `1000017301`，步骤不存在或不属于该场景返回 `1000017305`（`API_SCENE_STEP_NOT_FOUND`）；步骤缺少 `requestConfig`（或配置为空）时 `stepResult.status = error` 且不含请求 / 响应。结果在「调试结果」对话框（`StepDebugResultDialog.vue`）展示状态 tag 与耗时、请求 / 响应 JSON、验证器结果（验证器名 + 通过 / 失败）、提取的变量（变量名 / 值）。
- **响应**：`validatorResults` 与 `extractedVariables` 当前恒为空；`response.body` 超过 `api-test.debug.max-response-body-chars`（默认 1 MB）时截断。

```json
{
  "stepResult": {
    "stepId": "018a...",
    "status": "success",
    "durationMs": 230,
    "request": { "method": "POST", "url": "/api/auth/login" },
    "response": { "status": 200, "headers": {}, "body": "...", "errorMessage": null },
    "validatorResults": [],
    "extractedVariables": {}
  }
}
```

### 1.4 草稿场景执行（创建态 [▶ 运行场景]）

- **路径**：`POST /api/project/api-scenes/draft/execute`
- **权限**：`api-scene:execute`
- **请求体**：

```json
{
  "name": "草稿场景",
  "environmentId": "018f...",
  "sceneVariables": [ { "name": "token", "value": "abc" } ],
  "steps": [
    {
      "name": "登录",
      "stepType": "http",
      "sourceType": "system",
      "enabled": true,
      "requestConfig": { "method": "GET", "url": "/api/auth/login" },
      "validators": [],
      "extractors": [],
      "stepVariables": []
    },
    { "name": "下单", "enabled": true, "requestConfig": { "method": "POST", "url": "/api/order" } }
  ]
}
```

- **说明**：供创建态未保存场景「运行场景」使用：以页面实时编排的步骤体执行，**不落库、不产生执行记录与报告**，同步返回全量结果，仅用于创建前校验编排正确性；`steps` 为空返回参数校验失败（“场景没有可执行步骤”）。步骤内嵌字段为 `name` / `stepType` / `sourceType` / `sourceId` / `enabled` / `requestConfig` / `validators` / `extractors` / `stepVariables`（步骤级变量，运行时覆盖场景同名变量）；不携带场景前置 / 后置处理器（落库后生效）。`enabled = false` 的步骤标记 `skipped`，步骤缺少 `requestConfig` 时标记 `error` 并计入失败（`errorMessage` 为「步骤缺少请求配置」）；整体按「停止运行」执行，任一失败即中止，后续步骤标记 `skipped`。前端仅提示 `草稿运行完成：通过 × · 失败 × · 跳过 ×`，不弹出报告。
- **响应**：`status` 取值 `success`（无失败步骤）/ `failed`（存在失败或解析失败步骤）/ `error`（引擎级异常），无 `skipped` 取值；全部步骤禁用时仍返回 `success` 且 `skipped` 等于步骤数。`steps[].status` 取值 `success` / `failed` / `skipped` / `error`。

```json
{
  "status": "failed",
  "passed": 1,
  "failed": 1,
  "skipped": 0,
  "durationMs": 520,
  "steps": [
    { "status": "success", "name": "登录", "durationMs": 210, "request": { "method": "GET", "url": "/api/auth/login" }, "response": { "status": 200, "headers": {}, "body": "..." } },
    { "status": "error", "name": "下单", "errorMessage": "步骤缺少请求配置" }
  ]
}
```


## 2. 执行历史与变更历史

### 2.1 查询执行历史

- **路径**：`GET /api/project/api-scenes/{sceneId}/executions?pageNo=1&pageSize=20`
- **权限**：`api-scene:view`
- **数据来源**：`api_execution_record`（见《API 测试基础设施详细设计说明书》2.1.3），按 `executedAt` 倒序。
- **前端行为**：编辑页顶部 [执行历史] 气泡按需加载（打开气泡触发，20 条 / 页，切换页码重新拉取），逐行展示 `status` 原值、`executedAt` 时间（本地时区格式化）与 [报告] 按钮（仅 `reportId` 非空时显示）；加载失败在气泡内展示错误并提供 [重试]；创建态不显示 [执行历史]。
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

- **路径**：`GET /api/project/api-scenes/{sceneId}/change-history?pageNo=1&pageSize=20`
- **权限**：`api-scene:view`
- **数据来源**：`api_change_history`（见《API 测试基础设施详细设计说明书》2.1.2，只读追溯，不提供编辑 / 删除），按 `version` 倒序。
- **说明**：前端在打开 [执行历史] 时与执行历史一并请求（`useSceneHistory` 并发拉取、失败各自提示并可重试）；变更数据当前仅保留在页面状态中，编辑页未提供变更历史展示入口。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "version": 3,
      "operatorName": "张三",
      "changeType": "update",
      "changeSummary": "更新步骤：发送登录请求（请求配置）",
      "changedAt": "2026-08-17T09:30:00Z"
    }
  ],
  "total": 5
}
```

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 对齐执行与历史实现：修正执行 / 单步调试 / 历史接口路径与权限码，移除状态轮询与草稿单步调试，统一不轮询查看口径与草稿执行状态语义 |
