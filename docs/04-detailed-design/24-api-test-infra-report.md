# 软件测试平台——测试报告

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 报告接口

### 1.1 查询报告列表

- **路径**：`GET /api/project/reports?pageNo=1&pageSize=20&status=success`
- **筛选参数**：`status`（可选）、`reportType`（可选，scene / suite）、`sceneId`（可选，仅筛场景报告）、`keyword`（可选，报告名称/套件内场景名模糊搜索）、`startDate` / `endDate`（可选）。
- **列表范围**：只返回 `source IN ('schedule')` 的报告。场景页 [运行] 直接产生的报告（`source = 'scene'`、`report_type = 'scene'`）不进列表，但可在对应场景的执行记录中通过弹窗查看（见《测试报告详细设计说明书》4.6）；定时任务（含调度页"立即执行"）聚合生成套件报告，正常展示。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "reportType": "suite",
      "name": "每日回归-登录支付-20260909-0200",
      "sceneName": null,
      "externalId": "task-uuid",
      "executionMode": "platform",
      "source": "schedule",
      "status": "failed",
      "summary": { "totalScenes": 3, "passedScenes": 2, "failedScenes": 1, "totalSteps": 15, "passedSteps": 12, "failedSteps": 2, "skippedSteps": 1, "durationMs": 15800 },
      "environmentName": "测试环境",
      "createdAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 42
}
```

> `reportType`：`scene`（场景报告）/ `suite`（套件报告）。场景报告 `name` = 场景名 + 时间戳、`sceneName` 有值；套件报告 `name` = 任务名 + 时间戳、`sceneName` 为 null、`summary` 采用场景级汇总。

### 1.2 查询报告详情

- **路径**：`GET /api/project/reports/:id`
- **响应**：`data.result` 为按 `reportType` 构建的结果数据集（`scene`/`suite`，字段结构见《测试报告详细设计说明书》2.3），前端按「场景 → 步骤」两级或单场景步骤渲染；`stepResults` 旧字段废弃。详情不含 `ryze_snapshot`（内部字段，仅保留后端）。当报告存在**未过期分享**时附带 `data.share`（`{shareUrl, expiresAt, shareBy}`），供分享弹窗直接复用展示；无分享/已过期为 `null`：

```json
{
  "id": "018f...",
  "reportType": "scene",
  "name": "登录链路-2026-08-17 10:30",
  "share": {
    "shareUrl": "/share/api-report/018f...?token=abc123",
    "expiresAt": "2026-08-24T10:30:00Z",
    "shareBy": "zhangsan"
  }
}
```

### 1.3 生成分享链接

- **路径**：`POST /api/project/reports/:id/share`
- **说明**：无全局分享开关，具备报告查看/分享权限（`api-report:view`）即可生成；`expiresInDays` 缺省 7 天，有效期写入 `share_expires_at`，并写入分享者 `share_user_id`（参照邀请链接生成时选择过期时间）。接口**每次重新生成** token 并覆盖旧分享；前端在存在未过期分享时复用展示，不调用本接口（见《测试报告详细设计说明书》4.2）。
- **请求体**：

```json
{
  "expiresInDays": 7
}
```

- **响应**：

```json
{
  "shareUrl": "/share/api-report/018f...?token=abc123",
  "expiresAt": "2026-08-24T10:30:00Z",
  "shareBy": "zhangsan"
}
```

### 1.4 访问分享报告（免登录）

- **路径**：`GET /api/public/api-reports/:id?token=abc123`
- **说明**：不需要 Authorization 头，通过 token 校验访问权限。token 不匹配或过期统一返回 403（错误码 7009），不区分具体原因避免枚举探测。

### 1.5 删除报告

- **路径**：`DELETE /api/project/reports/:id`
- **响应**：`{ "success": true }`


## 2. 报告详情渲染

报告详情页根据 `result` 数据集（按 `reportType`）渲染：

- **场景报告**（`report_type='scene'`）：渲染步骤表，遍历 `result.steps[]`，每个步骤可展开查看请求信息（方法/URL/请求头/请求体）、响应信息（状态码/响应头/响应体格式化）、验证器结果（验证通过/失败明细）、提取器结果（变量名与值）、耗时。
- **套件报告**（`report_type='suite'`）：先渲染场景列表（`result.scenes[]`，含各场景状态/通过率/耗时），点击场景展开其 `steps[]` 步骤表（渲染规则同场景报告），形成「场景 → 步骤」两级。

`ApiReportDetailRespDTO` 以 `result`（Object）承载数据集，替代原 `stepResults` 数组。


