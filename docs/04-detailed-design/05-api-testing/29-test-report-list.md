# 软件测试平台——报告列表

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 报告列表筛选参数

列表查询接口 `GET /api/project/reports` 的路径、权限（`api-report:view`）、排序（按 `created_at` 倒序）与响应结构见《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md`）1.1，本节定义列表页实际使用的筛选参数与列表范围。

### 1.1 筛选参数

| 参数 | 类型 | 必填 | 页面来源 | 说明 |
| ---- | ---- | ---- | ---- | ---- |
| pageNo / pageSize | int | 是 | 分页组件 | pageNo 从 1 起；pageSize 前端固定 20 |
| status | string | 否 | 状态下拉 | `success` / `failed` / `partial`（口径见该文档 1.6） |
| reportType | string | 否 | 页面不提交 | 报告粒度：`scene` / `suite`；列表固定为套件报告，传 `scene` 查不到数据 |
| executionMode | string | 否 | 执行方式下拉 | `platform`（平台内执行） |
| sceneId | string | 否 | 场景下拉 | 页面提交该参数，后端不接收、不产生过滤效果（见该文档 1.1 注） |
| keyword | string | 否 | 关键字输入框 | 模糊匹配报告名称 `name`（任务名 + 执行时间戳）；输入框占位文案为「搜索报告名称或场景名称」 |
| startDate / endDate | string | 否 | 日期范围 | 创建时间下界 / 上界，ISO-8601 无时区墙钟值，提交格式 `YYYY-MM-DDT00:00:00` |

- 下拉可选值：状态为「全部状态（空值）/ 通过 `success` / 失败 `failed` / 部分通过 `partial`」；执行方式为「全部执行方式（空值）/ 平台内执行 `platform`」；场景下拉数据取自场景列表接口（第 1 页、pageSize 100），加载失败时下拉为空。
- 状态、执行方式、场景、日期任一变更即重置 `pageNo = 1`、清空勾选并重新查询；关键字在回车或点击清空图标时触发查询；空值筛选项不传参。
- 列表当前只含套件报告（见 1.2），其状态仅有 `success` / `failed`，选「部分通过」查不到数据。

### 1.2 列表范围与响应字段

- **列表范围**：只返回 `source = 'schedule'` 且 `report_type = 'suite'` 的**套件报告**（见该文档 1.1）。场景页 [运行] 直接产生的报告（`source = 'scene'`、`report_type = 'scene'`）与定时任务聚合所依赖的场景级报告均不进列表，前者经场景执行记录弹窗查看（见 `docs/04-detailed-design/05-api-testing/30-test-report-detail.md` 2）；定时任务（含调度页「立即执行」）聚合生成的套件报告正常展示。因此列表条目 `reportType` 恒为 `suite`、`sceneName` 恒为 `null`。
- **响应字段**：`id` / `reportType` / `externalId` / `name` / `sceneName` / `executionMode` / `status` / `summary` / `environmentName` / `createdAt`（响应示例见该文档 1.1）。套件报告 `name` = 任务名 + 执行时间戳，`summary` 为套件口径汇总键（`totalScenes` / `passedScenes` / `failedScenes` / `totalSteps` / `passedSteps` / `failedSteps` / `skippedSteps` / `durationMs`，字段结构见 `docs/04-detailed-design/05-api-testing/28-test-report-overview.md` 2.3.2），不含 `total` / `passed` 键。


## 2. 批量删除报告

- **路径**：`POST /api/project/reports/batch-delete`（接口定位见 `docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md` 1.5）
- **权限**：`api-report:delete`；服务端另校验项目成员身份与每条报告的项目归属，报告不存在或不属于当前项目返回 **1000017311**（`API_REPORT_NOT_FOUND`「报告不存在或不属于当前项目」，号段见 `docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md` 2.2）。
- **请求体**：

```json
{
  "ids": ["018f...", "019a..."]
}
```

`ids` 不能为空（校验消息「报告 ID 列表不能为空」）。

- **响应**（`data`）：`true`
- **交互**：勾选任意行后，工具栏右侧出现「已选 N 项」与 [批量删除]（危险按钮）；点击弹出确认框「批量删除 N 份报告？删除后不可恢复。」，确认后调用接口，成功提示「已删除」并清空勾选——当前页无剩余数据且 `pageNo > 1` 时回退一页，否则刷新当前页；失败顶部弹出错误消息（缺省「删除失败」）。未勾选时不渲染该按钮。


## 3. 报告列表页

### 3.1 页面位置与结构

列表为接口测试模块（`ApiTestingPage.vue`）左侧菜单「测试报告」子页，组件为 `ReportsPage.vue`；子页切换不走路由，仅以 `?tab=reports` 记录激活项。点击报告名称或 [查看] 切换到详情子页（`ReportDetailPage.vue`），经 [关闭] 或切换左侧菜单返回列表，返回后重新加载第 1 页（筛选与勾选重置）。

```
┌────────────────────────────────────────────────────────────────────┐
│ [状态▾][执行方式▾][场景▾][日期范围][搜索报告名称…]  已选2项 [批量删除] │ 卡片头工具栏
├────────────────────────────────────────────────────────────────────┤
│ ☐ │ 报告名称      │ 类型 │ 状态 │ 通过率 │ 耗时 │ 创建时间 │ 操作    │ 表头
│ ☐ │ 每日回归-…   │ 套件 │ 通过 │   -   │15.8s│ 时间    │ 查看 删除│ 数据行
├────────────────────────────────────────────────────────────────────┤
│                       共 42 条    ‹ 1 2 3 ›                        │ 分页
└────────────────────────────────────────────────────────────────────┘
```

### 3.2 列表列与状态徽标

| 列 | 宽度 | 内容 |
| -- | ---- | ---- |
| 多选 | 40 | 勾选框，用于批量删除 |
| 报告名称 | min-width 220 | 主色文字链接（超出省略并悬浮提示），点击打开详情；取 `name`，缺失时回退为场景名 + 创建时间 |
| 类型 | 90 | 标签：`suite` →「套件报告」（info），否则「场景报告」（primary）；列表恒为「套件报告」 |
| 状态 | 110 | 状态徽标（映射见下表） |
| 通过率 | 90 | `summary.passed / summary.total × 100%`（保留 1 位小数），`summary.failed > 0` 时标红；`summary.total` 缺失或为 0 时显示 `-`（套件报告汇总无 `total` 键，实际显示 `-`） |
| 耗时 | 90 | `summary.durationMs`：小于 1000 显示 `Xms`，否则显示 `X.Xs`；缺失显示 `-` |
| 创建时间 | 170 | `createdAt` 按浏览器时区格式化为日期时间 |
| 操作 | 120（固定右侧） | [查看]（主色文字按钮）、[删除]（危险文字按钮） |

**状态徽标**：

| 取值 | 文案 | 标签类型 |
| ---- | ---- | ---- |
| `success` | 通过 | success（绿） |
| `failed` | 失败 | danger（红） |
| `partial` | 部分通过 | warning（黄） |
| 其他取值 | 原样展示 | info |

**行内删除**：点击 [删除] 弹出确认框，文案为 `删除报告「<报告名>」？删除后不可恢复。`（确认按钮为危险样式），确认后调用 `DELETE /api/project/reports/:id`（权限 `api-report:delete`，`data` 为 `true`，接口定义见 `docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md` 1.5），成功提示「已删除」，当前页无剩余数据且 `pageNo > 1` 时回退一页，否则刷新当前页；失败顶部弹出错误消息（缺省「删除失败」）。

### 3.3 分页与状态分支

- **分页**：`total, prev, pager, next`（总数 + 上一页 / 页码 / 下一页），pageSize 固定 20、无每页大小切换；切换页码在保持当前筛选条件的前提下重新加载。
- **加载中**：卡片级 loading 遮罩。
- **加载失败**：顶部弹出错误消息（缺省「报告列表加载失败」），内容区保持原数据（首次加载则停留空态）。
- **空态**：「暂无测试报告，定时任务执行后生成；场景页运行报告可在场景执行历史中查看」。
- **删除失败 / 批量删除失败**：顶部弹出错误消息，列表数据与勾选保持不变。

### 3.4 权限口径

| 操作 | 权限码 |
| ---- | ---- |
| 侧边栏「测试报告」菜单入口与列表查询 | `api-report:view` |
| 打开报告详情、生成分享链接 | `api-report:view` |
| 行内删除、批量删除 | `api-report:delete` |

> 页面不按权限码隐藏 [查看] / [删除] / [批量删除]，越权调用由后端 `@PreAuthorize` 鉴权拒绝（HTTP 403），口径见 `docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md` 1.6；用户无任何接口测试模块权限时，模块主区域显示「暂无可用功能模块」。


## 4. 报告清理

复用《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.4 定义的数据清理策略：

- 报告与执行记录保留期限默认 90 天（系统配置项）。
- 清理后执行记录保留元数据，报告详情置为「执行结果被清理」。
- `api_report.ryze_snapshot` 随报告一起清理。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 对齐实现：按前端实际调用校正筛选参数、删除与批量删除接口，补全列表页列/状态徽标/分页/状态分支/权限口径，删除未实现的报告刷新接口并修正交叉引用 |
