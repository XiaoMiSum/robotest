# 软件测试平台——测试报告

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 报告接口

上下文（工作空间 / 项目）经请求头 `X-Active-Workspace` / `X-Active-Project` 传递，下列路径不含上下文标识；资源自身 ID 位于路径中。响应均使用统一响应体（`{code, msg, data}`），下文示例仅展示 `data` 内容。分页遵循 `pageNo`（从 1 起）/`pageSize`（默认 20，最大 100）。

### 1.1 查询报告列表

- **路径**：`GET /api/project/reports?pageNo=1&pageSize=20&status=success`
- **权限**：`api-report:view`
- **筛选参数**：

| 参数 | 说明 |
| ---- | ---- |
| `status` | 状态：`success` / `failed` / `partial`（口径见 1.6） |
| `reportType` | 报告粒度：`scene` / `suite`；列表固定为套件报告，传 `scene` 查不到数据 |
| `executionMode` | 执行方式：`platform` |
| `keyword` | 模糊匹配报告名称（`name` 字段） |
| `startDate` / `endDate` | 创建时间范围，ISO-8601 无时区墙钟值（前端以 `YYYY-MM-DDT00:00:00` 提交） |

> 不支持按场景 ID 筛选（报告列表页场景下拉传入的 `sceneId` 不被后端接收）；列表按 `created_at` 倒序返回。列表筛选参数的业务补充见 `docs/04-detailed-design/05-api-testing/29-test-report-list.md` 1。

- **列表范围**：只返回 `source = 'schedule'` 且 `report_type = 'suite'` 的**套件报告**。场景页 [运行] 直接产生的报告（`source = 'scene'`）与定时任务聚合所依赖的场景级报告均不进列表，前者可在场景执行记录弹窗中查看（见 `docs/04-detailed-design/05-api-testing/30-test-report-detail.md` 2）；定时任务（含调度页「立即执行」）聚合生成的套件报告正常展示。因此列表条目中 `reportType` 恒为 `suite`、`sceneName` 恒为 `null`。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "reportType": "suite",
      "externalId": "task-uuid",
      "name": "每日回归-登录支付-20260909-0200",
      "sceneName": null,
      "executionMode": "platform",
      "status": "failed",
      "summary": { "totalScenes": 3, "passedScenes": 2, "failedScenes": 1, "totalSteps": 15, "passedSteps": 12, "failedSteps": 2, "skippedSteps": 1, "durationMs": 15800 },
      "environmentName": "测试环境",
      "createdAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 42
}
```

> 响应不含 `source` 字段（详情 / 免登录响应同样不含，来源由套件数据集 `result.source` 表达）；套件报告 `summary` 采用场景级汇总键（`totalScenes` 等，字段结构见 `docs/04-detailed-design/05-api-testing/28-test-report-overview.md` 2.3.2）、`name` = 任务名 + 时间戳、`sceneName` 为 `null`。

### 1.2 查询报告详情

- **路径**：`GET /api/project/reports/:id`
- **权限**：`api-report:view`
- **响应**：字段为 `id` / `reportType` / `externalId` / `name` / `executionMode` / `status` / `summary` / `environmentName` / `result` / `share` / `createdAt`。`data.result` 为按 `reportType` 构建的结果数据集（`scene` 为场景数据集、`suite` 为套件数据集，字段结构见 `docs/04-detailed-design/05-api-testing/28-test-report-overview.md` 2.3），前端据此按「场景 → 步骤」两级或单场景渲染（见 2）；响应不含 `ryze_snapshot`（内部字段，仅后端保留），也不含旧的 `stepResults` 数组字段。当报告存在**未过期分享**时 `data.share` 为 `{shareUrl, expiresAt, shareBy}`，供分享弹窗直接复用展示；无分享 / 已过期为 `null`：

```json
{
  "id": "018f...",
  "reportType": "scene",
  "externalId": "019a...",
  "name": "登录链路-2026-08-17 10:30",
  "executionMode": "platform",
  "status": "success",
  "summary": { "total": 4, "passed": 4, "failed": 0, "skipped": 0, "durationMs": 2300 },
  "environmentName": "测试环境",
  "result": { "sceneId": "019a...", "sceneName": "登录链路", "status": "success", "steps": [] },
  "share": {
    "shareUrl": "/share/api-report/018f...?token=abc123",
    "expiresAt": "2026-08-24T10:30:00Z",
    "shareBy": "zhangsan"
  },
  "createdAt": "2026-08-17T10:30:00Z"
}
```

### 1.3 生成分享链接

- **路径**：`POST /api/project/reports/:id/share`
- **权限**：`api-report:view`
- **说明**：无全局分享开关，具备报告查看权限即可生成；`expiresInDays` 缺省 7 天（前端提供 1 / 7 / 30 / 90 天选项），有效期写入 `share_expires_at`，并写入分享者 `share_user_id`。接口**每次重新生成** token 并覆盖旧分享；前端在存在未过期分享时复用详情接口的 `share` 记录展示，仅 [重新生成] 才调用本接口（见 `docs/04-detailed-design/05-api-testing/31-test-report-share.md` 1.3）。
- **请求体**：

```json
{
  "expiresInDays": 7
}
```

- **响应**（`shareUrl` 为相对路径，前端拼接站点来源）：

```json
{
  "shareUrl": "/share/api-report/018f...?token=abc123",
  "expiresAt": "2026-08-24T10:30:00Z",
  "shareBy": "zhangsan"
}
```

### 1.4 访问分享报告（免登录）

- **路径**：`GET /api/public/api-reports/:id?token=abc123`
- **说明**：不需要 Authorization 头，路径在免登录白名单内，鉴权完全依赖 token，接口限流 60 次 / 分钟。token 不匹配或已过期统一返回业务错误码 **1000017312**（`API_SHARE_EXPIRED`，消息「分享链接已过期或不存在」），不区分具体原因，避免枚举探测。
- **响应**（`data`）：`id` / `reportType` / `name` / `environmentName` / `status` / `summary` / `result` / `createdAt`——不含 `share`、`executionMode`、`externalId`。
- **前端**：分享页路由 `/share/api-report/:id?token=...`（免登录）复用详情渲染（见 2）；缺少 `id` 或 `token` 时直接展示「分享链接无效」。

### 1.5 删除报告

- **路径**：`DELETE /api/project/reports/:id`
- **权限**：`api-report:delete`
- **响应**：`data` 为 `true`。
- 批量删除 `POST /api/project/reports/batch-delete`（请求体 `{ "ids": [...] }`，权限 `api-report:delete`）由 `docs/04-detailed-design/05-api-testing/29-test-report-list.md` 2 定义。

### 1.6 状态取值与权限口径

**状态判定**（落库于 `api_report.status`，场景数据集与套件内嵌场景同口径）：

| 取值 | 场景报告 / 场景数据集 | 套件报告 | 前端展示 |
| ---- | ---- | ---- | ---- |
| `success` | 无失败步骤且无引擎异常 | 全部场景通过 | 列表「通过」、详情 Hero「执行成功」 |
| `partial` | 存在失败步骤或执行被取消，但无引擎异常 | 套件层面不产生该取值 | 「部分通过」 |
| `failed` | 存在引擎异常（步骤配置解析失败、引擎结果为 error、结果树与步骤数不匹配等） | 存在结果状态非 `success` 的场景（含无结果的占位场景） | 列表「失败」、详情 Hero「执行失败」 |

> 报告列表当前只含套件报告（见 1.1），其状态只有 `success` / `failed`，`status=partial` 筛选查不到数据；`partial` 出现在场景报告与套件数据集的场景级状态中。

**权限码**：

| 操作 | 权限码 |
| ---- | ---- |
| 接口测试侧边栏「测试报告」菜单入口 | `api-report:view` |
| 报告列表、详情、生成分享链接 | `api-report:view` |
| 删除报告、批量删除报告 | `api-report:delete` |
| 免登录分享访问 | 无权限码（token 校验 + 限流） |

> 前端不按权限码隐藏「分享」「删除」等操作按钮，越权调用由后端 `@PreAuthorize` 鉴权拒绝（HTTP 403）。


## 2. 报告详情渲染

报告详情页、免登录分享页、场景执行记录弹窗三处复用同一套结果展示组件，按 `report.reportType` 取数：`scene` 读取单场景数据集，`suite` 读取 `result.scenes[]`。两类报告统一走「Hero → 统计卡 → 处理器页签 → 场景卡 → 步骤卡」的结构，场景报告相当于只有一张场景卡且默认展开。

```
┌──────────────────────────────────────────────────────┐
│ 报告名称            ✓ 执行失败   定时任务   [分享][关闭]│ Hero
│ 环境 · 触发/执行时间 · 总耗时 · Task ID / 场景 ID      │
├──────────────────────────────────────────────────────┤
│ 统计卡 ×5（场景总数/通过场景/失败场景/通过率/总耗时，   │ 执行概览
│            或 总步骤/通过/失败/通过率/总耗时）          │
├──────────────────────────────────────────────────────┤
│ ℹ 当前展示场景「××」的执行明细  [查看完整套件报告]      │ 聚焦横幅（可选）
├──────────────────────────────────────────────────────┤
│ [前置(n)●] [后置(n)●]  执行顺序  环境 → 场景 →  处理器小卡│ 环境级处理器页签
├──────────────────────────────────────────────────────┤
│ ▸ 场景卡：场景名 · 场景 ID · 执行时间                  │ 场景卡（可展开）
│   步骤 / 通过 / 失败 / 耗时   状态徽标          [▾]    │
│   ├─ 场景级前置 / 后置处理器页签                        │
│   └─ 测试步骤（N）  [全部展开/收起]                     │
│      ├ 序号 名称 方法徽标 状态 验证器数 耗时            │ 步骤卡
│      └ 展开：请求 / 响应 / 验证器表 / 提取器表 / 错误    │
├──────────────────────────────────────────────────────┤
│ 报告生成时间 ×× · 来源 ×× · 环境 ××                    │ 页脚
└──────────────────────────────────────────────────────┘
```

- **Hero 概览**：报告名称、状态章（执行成功 / 执行失败 / 部分通过）、来源章（套件数据集 `source = 'schedule'` 显示「定时任务」，否则「平台内执行」）；元信息行为环境、触发时间（套件）/ 执行时间（场景）、总耗时、Task ID（套件）/ 场景 ID（场景，等宽字体）。详情页在 Hero 右侧动作位挂 [分享]、[关闭]；分享页与执行记录弹窗无动作位。
- **统计卡（5 张）**：套件整体为场景口径（场景总数 / 通过场景 / 失败场景 / 通过率 / 总耗时）；场景报告与定位到单场景时为步骤口径（总步骤 / 通过 / 失败 / 通过率 / 总耗时）。
- **聚焦横幅**：执行记录弹窗传入 `focusSceneId` 时只渲染对应场景并提示「当前展示场景「××」的执行明细」，附 [查看完整套件报告] 切换为完整套件视图；完整套件视图下由弹窗提供 [返回当前场景明细]。
- **处理器页签**：套件顶层（环境级，仅套件整体视图且存在处理器）与场景卡内（场景级）复用同一组件——[前置] / [后置] 两个页签（计数 + 状态圆点）、执行顺序提示链（当前项高亮）、处理器小卡（可折叠：名称、方法徽标、状态徽标、URL 行、请求头表、Query / 请求体 / 响应体格式化代码块、提取器表、验证器表）。
- **场景卡**：头部整体可点击展开 / 收起，含场景名、场景 ID、执行时间、四项迷你指标（步骤 / 通过 / 失败 / 耗时）、状态徽标；失败场景头部带红色左边框。场景报告与聚焦场景默认展开，套件场景卡默认收起；展开体为「场景级处理器页签 + 步骤卡列表」。
- **步骤卡**：工具栏「测试步骤（N）」+ [全部展开 / 收起]；无步骤时空态「该场景无步骤明细」。头部含序号、步骤名、方法徽标（GET / POST / PUT / DELETE，缺省显示 `HTTP`）、状态徽标、验证器数（存在失败验证器时标红）、耗时。展开体依次为：请求块（方法 + URL、请求头表、Query 参数、请求体）、响应块（状态码、`HTTP/1.1 · Content-Type`、格式化响应体、响应头表）、验证器表（字段 / 规则 / 期望值 / 实际值 / 结果）、提取器表（引用名 / 字段 / 提取值 / 默认值 / 结果）、`errorMessage` 错误提示条。失败 / 错误步骤默认展开。
- **页脚**：报告生成时间、来源、环境。

**状态徽标文案**：

| 层级 | 取值 → 文案 |
| ---- | ---- |
| 报告 / 场景 | `success` / `passed` → 通过、`failed` → 失败、`partial` → 部分通过、`skipped` → 跳过（Hero 用「执行成功 / 执行失败 / 部分通过」） |
| 步骤 | `success` / `passed` → 成功、`failed` → 失败、`error` → 错误、`skipped` → 跳过、`not_executed` → 未执行 |
| 验证器 | `passed` / `success` → 通过、`failed` → 失败、`skipped` → 跳过 |
| 提取器 | 无独立状态：`message` 有值 → 提取失败，否则 → 成功 |

**状态分支**：

- `result` 为空 → 空态「暂无报告内容」；详情加载中显示遮罩，加载失败仅顶部错误消息提示、内容区留空。
- 分享页：加载中显示遮罩；缺少 `id` 或 `token` → 「分享链接无效」；接口返回错误码 1000017312 → 「分享链接无效或已过期」，副标题「请联系报告分享者重新生成链接」。

`ApiReportDetailRespDTO` 以 `result`（Object，对应 `api_report.result` JSONB）承载结果数据集，数据集字段定义见 `docs/04-detailed-design/05-api-testing/28-test-report-overview.md` 2.3。


## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐实现：修正列表范围与筛选参数、补充状态判定与权限口径、按共享渲染组件重写详情渲染章节 |
