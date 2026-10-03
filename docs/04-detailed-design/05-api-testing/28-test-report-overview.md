# 软件测试平台——测试报告详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的 **接口测试报告**进行详细设计，定义报告的数据结构、结果数据集模型、页面入口与权限口径，为开发实现提供完整依据。报告表（`api_report`）的完整 DDL 见《API 测试基础设施详细设计说明书》2.1.4，报告基础接口（列表、详情、生成分享链接、免登录访问、删除）与状态/权限口径见其《测试报告》分册（`docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md` 1），本文档不重复描述。Mock 服务相关设计见《Mock 服务详细设计说明书》（`docs/04-detailed-design/05-api-testing/21-mock-service-overview.md`）。

### 1.2 范围

覆盖 SRS 3.5（接口测试报告，需求分册 `docs/01-requirements/05-api-testing/07-api-srs-test-report.md`）与概要设计对应模块：

- **接口测试报告**：报告列表与详情、分享链接、批量删除、报告清理，以及报告结果数据集模型；
- **公共组件**：项目级可复用组件资产库（前置处理器、后置处理器、验证器、提取器）的管理与复制由基础设施分册承载，本文在 2.1.2 仅给出表索引指引（表结构与接口见《API 测试基础设施详细设计说明书》2.1.5 及其《公共组件》分册 `docs/04-detailed-design/05-api-testing/06-api-testing-infra-common-component.md`）。

模块页面构成与职责边界见《接口测试报告概要设计》（`docs/02-high-level-design/05-api-testing/07-hld-api-test-report.md` 2）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/05-api-testing/07-api-srs-test-report.md` 1，总览与分册编号映射见 `docs/01-requirements/05-api-testing/02-api-srs-overview.md` 附录 A）
- 《概要设计说明书——接口测试报告》（`docs/02-high-level-design/05-api-testing/07-hld-api-test-report.md`）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）及其《测试报告》分册（`docs/04-detailed-design/05-api-testing/05-api-testing-infra-report.md`）
- 《Mock 服务详细设计说明书》（`docs/04-detailed-design/05-api-testing/21-mock-service-overview.md`）
- 本详细设计分册：报告列表（`docs/04-detailed-design/05-api-testing/29-test-report-list.md`）、详情与生成（`docs/04-detailed-design/05-api-testing/30-test-report-detail.md`）、分享（`docs/04-detailed-design/05-api-testing/31-test-report-share.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

### 1.4 页面入口与权限口径

- **壳路由与子模块**：`/workspace/projects/api-testing`（路由名 `ApiTesting`），项目菜单按 `permissionAny` 中任一接口测试 `*:view` 权限码开放入口；测试报告是壳页内的子模块，左侧侧边栏菜单项「测试报告」（图标 `DataAnalysis`）挂 `api-report:view`，切换时以 `?tab=reports` 同步记录（`router.replace`，不产生额外浏览器历史），刷新或外部改写 query 时按当前可见权限还原，tab 不可见时回落到首个可见子模块；无任何接口测试权限时内容区显示「暂无可用功能模块」。
- **页面组织**：报告列表（`web/src/pages/project/api-testing/report/ReportsPage.vue`）与报告详情（同目录 `ReportDetailPage.vue`）是同一 tab 下的两个状态——点击列表报告名或行内 [查看] 触发 `view` 事件进入详情，详情 [关闭] 返回列表，URL 始终保持 `?tab=reports`，详情不占用独立路由；免登录分享页为独立公开路由 `/share/api-report/:id`（路由名 `ShareReport`，`meta.public`），token 经 `?token=` 传递，已登录状态打开该路由会被导航守卫重定向到首页。
- **权限码**：前端仅在侧边栏子菜单挂 `api-report:view`，列表行内 [删除]、[批量删除] 与详情页 [分享] 不按权限码隐藏；服务端报告列表、详情、生成分享链接按 `api-report:view` 校验，删除与批量删除按 `api-report:delete` 校验，全部操作再经项目成员校验（非项目成员返回 `1000002001`「无权限执行此操作」）；免登录分享访问不挂权限码，依赖 `share_token` 校验与限流（60 次/分）。
- **列表范围与操作**：列表仅收录定时任务来源的套件报告（`source = 'schedule'`、`report_type = 'suite'`），场景页 [运行] 产生的报告不经列表，由场景执行历史弹窗查看（见 `docs/04-detailed-design/05-api-testing/30-test-report-detail.md` 2）；工具栏提供状态、执行方式、场景与时间范围筛选及名称搜索（场景下拉提交的 `sceneId` 不被服务端接口接收，口径见《测试报告》分册 1.1），行操作为 [查看]、[删除]，勾选行后出现 [批量删除]，删除与批量删除均二次确认。
- **状态分支**：列表空态提示「暂无测试报告，定时任务执行后生成；场景页运行报告可在场景执行历史中查看」，加载与操作失败经统一错误消息提示；分享页分加载中、正常渲染与错误态（标题「分享链接无效或已过期」，副标题「请联系报告分享者重新生成链接」）。

---

## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 报告表（api_report）

报告表（`api_report`）的完整字段定义、索引与说明见《API 测试基础设施详细设计说明书》2.1.4，此处不重复描述。本文档涉及的关键字段：

| 字段 | 说明 |
| ---- | ---- |
| id | 报告主键 |
| project_id | 归属项目 |
| execution_record_id | 关联执行记录（api_execution_record.id）。场景报告 1:1；套件报告对应多条执行记录，此字段为空 |
| report_type | 报告粒度：`scene`（场景报告，单场景）/ `suite`（套件报告，定时任务含立即执行聚合多场景） |
| external_id | 外部关联 ID，随 `report_type`：`suite` = 任务 ID；`scene` = 场景 ID |
| name | 报告名称（场景报告 = 场景名 + 执行时间戳；套件报告 = 任务名 + 执行时间戳，生成时固化） |
| environment_name | 环境名称快照。场景报告写入执行环境名；套件报告该列为空，环境名存于套件数据集 `result.environmentName` |
| execution_mode | 执行方式：platform（平台内执行） |
| status | success / failed / partial——三态为场景报告口径（partial = 存在跳过步骤或执行被取消但无引擎异常）；套件报告仅 success / failed，任一场景失败即 failed |
| source | 报告来源：scene（场景页运行）/ schedule（定时任务含立即执行），报告列表仅收录 schedule |
| summary | 结果汇总（场景报告 `{total, passed, failed, skipped, durationMs}`；套件报告为场景级汇总，见 2.3.2） |
| result | 结果明细数据集（场景数据集 / 套件数据集，字段结构见 2.3） |
| ryze_snapshot | 执行时的 Ryze 结果树序列化快照 |
| share_token | 分享链接令牌 |
| share_expires_at | 分享链接过期时间 |
| share_user_id | 生成分享链接的用户（分享者），用于分享记录展示与复制文本 |

**结果数据集**：`api_report.result` 按 `report_type` 存储场景/套件两种数据集，字段结构见 **2.3 结果数据集模型**（含步骤状态枚举、验证器明细、提取器结果结构）。

**索引**：见《API 测试基础设施详细设计说明书》2.1.4（`idx_report_type_external`、`idx_report_project_created`、`uk_report_share_token` UNIQUE、`idx_report_share_user`）。

> `uk_report_share_token` 支撑免登录分享访问按 token 定位报告（见 `docs/04-detailed-design/05-api-testing/31-test-report-share.md` 1.2）。

#### 2.1.2 公共组件表（api_component）

公共组件表（`api_component`）的字段定义与索引见《API 测试基础设施详细设计说明书》2.1.5；公共组件的接口与新建/编辑表单见其《公共组件》分册（`docs/04-detailed-design/05-api-testing/06-api-testing-infra-common-component.md`）。

### 2.2 错误码引用

报告相关错误码（`1000017311` `API_REPORT_NOT_FOUND`、`1000017312` `API_SHARE_EXPIRED`）统一登记于《API 测试基础设施详细设计说明书》2.2（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`），本文不重复登记号段。

> 分享访问未匹配或已过期统一返回 `1000017312`（不区分具体原因，避免枚举探测，见 `docs/04-detailed-design/05-api-testing/31-test-report-share.md` 1.2）；报告不存在或不属于当前项目返回 `1000017311`。权限不足（缺少权限码或非项目成员）由服务端拒绝——`@PreAuthorize` 拒绝返回 HTTP 403，项目成员校验返回 `1000002001`——均不占用报告号段。

### 2.3 结果数据集模型

`api_report.result`（JSONB）按 `report_type` 存储两类数据集，均由执行引擎基于 **Ryze 结果树**（`io.github.xiaomisum.ryze`，框架文档见 `https://xiaomisum.github.io/ryze/`）构建：`SampleResult` → 步骤，`AssertionResult` → 验证器，`ExtractorResult` → 提取器。`result` 本身为平台结构化视图，**不包含** ryze 快照（快照独立存于 `api_report.ryze_snapshot`）。

#### 2.3.1 场景数据集（report_type = 'scene'）

单个场景执行结果的平面化结构：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| sceneId | UUID | 场景 ID（external_id） |
| sceneName | String | 场景名称快照 |
| status | String | success / failed / partial（执行被取消或部分步骤跳过归 partial） |
| summary | Object | 见下文场景汇总 |
| environmentName | String/null | 环境名称快照 |
| executedAt | String | 执行时间（ISO-8601 UTC） |
| steps | Array | 步骤明细（见步骤元素） |
| preprocessors | Array | 场景前置处理器执行明细（元素形状同步骤元素，渲染规则复用步骤明细；无处理器时为空数组） |
| postprocessors | Array | 场景后置处理器执行明细（元素形状同步骤元素；无处理器时为空数组） |

**场景汇总** `summary`：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| total | int | 启用步骤总数 |
| passed | int | 通过数（所有验证器通过） |
| failed | int | 失败数（任一验证器失败或引擎异常） |
| skipped | int | 跳过数（禁用/后续跳过） |
| durationMs | long | 执行耗时 |

**步骤元素**（`steps[]`，对应 ryze `SampleResult`）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| stepId | String/null | 步骤 ID（平台步骤 ID） |
| name | String | 步骤名称 |
| type | String | 协议类型（当前固定 `HTTP`） |
| status | String | success / failed / error / skipped（断言失败为 failed，其余引擎异常为 error） |
| durationMs | long/null | 步骤耗时（无起止时间时为 null） |
| request | Object | 请求快照（Real*Request 按协议 getter 序列化，如 url/method/query/version/headers/body/format） |
| response | Object/null | 响应快照（Real*Response 按协议 getter 序列化，如 status/headers/body/format；body 按 `maxResponseBodyChars` 截断） |
| assertions | Array | 验证器明细（AssertionResult） |
| extractors | Array | 提取器明细（ExtractorResult） |
| errorMessage | String/null | 异常/失败信息 |

**验证器明细**（`assertions[]`）：`{field, rule, expected, actual, status, message}`——验证字段、规则（`==`/`contains` 等）、期望值（已求值）、实际值、状态（passed/failed/skipped）、失败消息。

**提取器明细**（`extractors[]`）：`{refName, field, value, defaultValue|boolean, message}`——变量名、提取表达式（JSONPath/正则）、提取值、是否走默认值分支、失败信息。

#### 2.3.2 套件数据集（report_type = 'suite'）

定时任务（含立即执行）执行多个场景聚合生成，内嵌多个场景形成「场景 → 步骤」两级：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| reportType | String | 固定 `suite` |
| taskId | UUID | 关联任务 ID（external_id） |
| taskName | String | 任务名称快照（报告名称来源） |
| source | String | 报告来源：schedule（定时任务含立即执行） |
| status | String | success / failed——任一场景失败则整体 failed（套件层面不产生 partial） |
| summary | Object | 见下文套件汇总 |
| environmentName | String/null | 环境名称快照（任务绑定环境） |
| triggeredAt | String | 触发时间（ISO-8601 UTC） |
| scenes | Array | 场景明细数组，每项即 **2.3.1 场景数据集**（未产出结果的场景为占位数据集，status = failed、汇总全 0） |
| preprocessors | Array | 环境前置处理器执行明细（任务绑定环境处理器，挂顶层 suite 执行一次；元素形状同步骤元素） |
| postprocessors | Array | 环境后置处理器执行明细（任务绑定环境处理器，挂顶层 suite 执行一次；元素形状同步骤元素） |

**套件汇总** `summary`：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| totalScenes | int | 执行场景总数 |
| passedScenes | int | 通过场景数 |
| failedScenes | int | 失败场景数 |
| totalSteps | int | 全部场景启用步骤总数 |
| passedSteps | int | 通过步骤总数 |
| failedSteps | int | 失败步骤总数 |
| skippedSteps | int | 跳过步骤总数 |
| durationMs | long | 一次触发总耗时 |

> 套件数据集（report_type='suite'）通过 `external_id`（任务 ID）与共享 `report_id` 关联各场景执行记录；报告列表一行一报告（名称 = 任务名 + 执行时间戳）。

### 2.4 权限设计

报告按项目隔离，菜单入口与接口按权限码控制，与《测试报告》分册 1.6 的权限口径一致：

| 操作 | 权限码 / 校验 |
| ---- | ---- |
| 接口测试侧边栏「测试报告」菜单入口 | `api-report:view` |
| 报告列表、详情、生成分享链接 | `api-report:view` + 项目成员校验 |
| 删除报告、批量删除报告 | `api-report:delete` + 项目成员校验 |
| 免登录分享访问 | 无权限码（`share_token` 校验 + 60 次/分限流） |

**服务端拒绝与前端反馈**：越权调用由服务端 `@PreAuthorize` 拒绝（HTTP 403）；非项目成员或报告不属于当前项目由业务校验拦截（分别返回 `1000002001`、`1000017311`）。前端不按权限码隐藏 [分享]、[删除] 等操作按钮，失败时在当前页面反馈——列表与详情以统一错误消息提示，免登录分享页切换到错误结果页——不提供独立的 403 页面。

---

## 3. 实施说明

- **前端页面**：`web/src/pages/project/api-testing/report/` —— `ReportsPage.vue`（列表）、`ReportDetailPage.vue`（详情与分享弹窗）、`ShareReportPage.vue`（免登录分享页）；场景执行历史的报告弹窗复用同一结果视图（`web/src/pages/project/api-testing/scene/ReportDetailDialog.vue`）。
- **前端组件**：`web/src/components/project/api-testing/report/` —— `ReportResultView.vue`（结果视图编排，含 Hero、统计卡、处理器页签、场景卡、步骤卡，经 `hero-actions` 插槽承接详情页操作按钮，并接收 `focusSceneId` 定位单场景）、`ReportProcessorsTabs.vue`、`ReportProcCard.vue`、`ReportStepCard.vue`、`ReportStepAssertions.vue`、`ReportAssertionsTable.vue`、`ReportStepExtractors.vue`、`ReportExtractorsTable.vue`。
- **组合式与服务**：`web/src/composables/project/api-testing/report/`（`useReportDisplay` / `useReportResultView` / `useReportStepCard` / `useReportProcessors`）、`web/src/services/project/api-testing/report.ts`、类型 `web/src/types/project/api-testing/report.ts`。
- **后端**：`ApiReportController`（列表、详情、生成分享链接、删除、批量删除）、`ApiReportPublicController`（免登录分享访问，`@RateLimit` 60 次/分）、`ApiReportServiceImpl`（查询、分享、免登录校验、删除）与 `ApiTestRetentionCleaner`（保留期清理，每日 03:00 物理删除，保留天数取 `robotest.api-test.scheduler.report-retention-days`，缺省 90 天）。
- **数据库**：`server/src/main/resources/db/schema.sql` 中的 `api_report` 表与 `idx_report_type_external`、`idx_report_project_created`、`uk_report_share_token`、`idx_report_share_user` 索引。

---

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `28-test-report-overview.md` | 1. 引言（含 1.4 页面入口与权限口径）、2. 数据设计（2.1 数据库表设计、2.2 错误码引用、2.3 结果数据集模型、2.4 权限设计）、3. 实施说明 |
| 报告列表 | `29-test-report-list.md` | 1. 报告列表筛选参数、2. 批量删除报告、3. 报告列表页（3.1 页面位置与结构、3.2 列表列与状态徽标、3.3 分页与状态分支、3.4 权限口径）、4. 报告清理 |
| 详情与生成 | `30-test-report-detail.md` | 1. 报告生成、2. 场景执行记录弹窗查看报告、3. 报告查看页、4. 执行记录弹窗查看报告 |
| 分享 | `31-test-report-share.md` | 1. 分享机制（1.1 分享链接生成、1.2 分享访问校验（免登录）、1.3 分享记录展示与复制、1.4 分享访问页（免登录页）） |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 按实现对齐：补 1.4 页面入口与权限口径（`?tab=reports`、`api-report:view`/`api-report:delete`）、修正索引名与数据集状态枚举、错误码改为引用、重写权限设计与实施说明、校准分册-章节对照表 |
| V1.0 | 2026-10-03 | 分册-章节对照表与交叉引用一致性复检 |
