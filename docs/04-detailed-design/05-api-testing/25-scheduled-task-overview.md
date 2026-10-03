# 软件测试平台——定时任务详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的 **定时任务**进行详细设计，定义测试计划（场景批量执行）与接口同步的统一 Cron 调度、执行记录、状态管理与删除保护的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.6（定时任务）与概要设计第 3.1 章对应模块：

- **定时任务管理**：测试计划（场景批量执行）与接口同步两类定时任务的 CRUD、启停、Cron 校验与立即执行，页面入口与权限口径见 1.4；
- **定时调度器**：JVM 内 ScheduledExecutorService 统一调度，执行记录与状态管理；
- **删除保护**：被定时任务选中的接口测试场景与模块、被任务绑定的目标环境受删除保护；
- **接口同步**：定时任务直接指定 OpenAPI/Swagger JSON 文件的 URL 地址。

> 场景执行引擎见《测试场景详细设计说明书》（`docs/04-detailed-design/05-api-testing/12-test-scenario-overview.md`）与《执行与历史》分册（`docs/04-detailed-design/05-api-testing/16-test-scenario-execution.md`）；接口导入引擎见《接口管理详细设计说明书》（`docs/04-detailed-design/05-api-testing/08-interface-management-overview.md`）与《导入》分册（`docs/04-detailed-design/05-api-testing/10-interface-management-import.md`）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/05-api-testing/08-api-srs-scheduled-task.md`，1. 定时任务（US-API-007））
- 《概要设计说明书》（`docs/02-high-level-design/02-hld-overview.md`，3.1；`docs/02-high-level-design/05-api-testing/08-hld-api-scheduled-task.md`，2 模块划分）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`，2.1 数据库表设计 / 2.2 错误码定义）
- 《定时任务交互设计》（`docs/05-interaction-design/05-api-testing/17-scheduled-task-ui.md`）

### 1.4 页面入口与权限口径

- **壳路由与子模块**：`/workspace/projects/api-testing`（路由名 `ApiTesting`），项目菜单按 `permissionAny` 中任一接口测试 `*:view` 权限码开放入口。定时任务是壳页左侧侧边栏的一级子模块（菜单项「定时任务」，图标 `Timer`，与快速调试、接口管理、Mock 服务、测试场景、测试报告同组，不在「项目设置」分组内），由侧边栏切换并以 `?tab=schedules` 同步记录；刷新或外部改写 query 时按当前可见权限还原，tab 不可见时回落到首个可见子模块；无任何接口测试权限时内容区显示「暂无可用功能模块」。
- **权限码**：前端仅在定时任务子菜单挂 `api-timer:view`，页内按钮不做前端权限隐藏；服务端按接口校验——任务列表、执行记录查询与 Cron 表达式校验按 `api-timer:view`，任务创建、更新、启停、删除与立即执行按 `api-timer:edit`。
- **页面结构**：单卡片列表页。卡片头部工具栏为类型筛选下拉（「全部类型」，切换即回到第 1 页重查）与 [新建任务]；表格列为任务名称、类型（测试计划 / 接口同步）、执行范围（测试计划展示圈选口径「全部场景 / 指定模块×N / 指定场景×N」，接口同步展示接口文档 URL）、调度（Cron 表达式）、状态（启停开关）、上次执行（状态标签 + 时间）与操作；底部分页每页 20 条；空态提示「暂无定时任务，点击右上角「新建任务」创建第一个任务」。
- **行内动作**：[立即执行] 在上次执行状态为 `running` 时禁用，点击经二次确认后触发并提示「已触发执行」；[执行记录] 打开右侧抽屉；「更多」下拉含 [编辑] 与 [删除]（删除二次确认，提示删除不影响已产生的执行记录与报告，删除后按当前页是否为空决定回退页码或原地刷新）；启停开关切换成功后提示并刷新列表，失败按错误码统一 Toast。
- **新建 / 编辑弹窗**：任务名称（必填，≤200）、任务描述（≤500）、任务类型（测试计划 / 接口同步）；测试计划任务另填执行方式（全部 / 指定模块 / 指定场景，模块用模块树多选、场景用场景选择弹窗确认后以标签回显，超过 8 个折叠展示并可展开）与目标环境（必填）；接口同步任务填接口文档 URL（必填，≤2000，保存时校验可达性与合法性）。调度配置区含预设表达式下拉（每小时 / 每天凌晨 2:00 / 每周一 2:00 / 每月 1 号 2:00 / 工作日 2:00）、Cron 表达式输入 + [校验]（回显合法/不合法、描述与下次执行时间预览）与 [构建器]（分钟 / 小时 / 日 / 月 / 星期五段拼接，精度到分钟）。
- **执行记录抽屉**：右侧 600px 抽屉，列为触发时间、触发方式（定时 / 手动）、状态（成功 / 失败 / 已跳过）、耗时（ms / s）与失败原因；每页 10 条分页。

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 定时任务表（api_scheduled_task）

统一管理测试计划（场景批量执行）与接口同步两类定时任务。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| task_type | VARCHAR(30) | NOT NULL | 任务类型：scene_execute / import_swagger |
| name | VARCHAR(200) | NOT NULL | 任务名称 |
| description | VARCHAR(500) | NULL | 任务描述 |
| bound_object_id | UUID | NULL | 历史遗留列（旧版绑定 Swagger URL 配置 / 场景 ID），新任务不再写入，置 NULL |
| bound_object_name | VARCHAR(200) | NULL | 历史遗留列，新任务不再写入 |
| execution_scope | VARCHAR(20) | NULL | 执行方式（scene_execute 任务）：all / modules / scenes |
| module_ids | JSON | NULL | 指定模块（多选），execution_scope=modules 时必填 |
| scene_ids | JSON | NULL | 指定场景（多选），execution_scope=scenes 时必填 |
| openapi_url | VARCHAR(2000) | NULL | OpenAPI/Swagger JSON 文件 URL（import_swagger 任务必填） |
| environment_id | UUID | NULL | 目标环境（scene_execute 任务必填） |
| cron_expression | VARCHAR(50) | NOT NULL | Cron 表达式（精度为分钟） |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE | 启用状态 |
| last_execution_status | VARCHAR(20) | NULL | 上一次执行状态：success / failed / running |
| last_execution_at | TIMESTAMP | NULL | 上一次执行时间 |
| created_by | UUID | NOT NULL | 创建人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_stask_project` (project_id), `idx_stask_enabled` (enabled, task_type)

> `idx_stask_enabled` 支撑调度器批量查询已启用任务。合计 2 个索引，符合 C9。

#### 2.1.2 定时任务执行记录表（api_scheduled_task_execution）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| task_id | UUID | NOT NULL | 关联定时任务（api_scheduled_task.id） |
| project_id | UUID | NOT NULL | 归属项目 |
| trigger_type | VARCHAR(20) | NOT NULL | 触发方式：scheduled（定时）/ manual（手动） |
| status | VARCHAR(20) | NOT NULL | 执行结果：success / failed / skipped |
| error_message | VARCHAR(2000) | NULL | 失败原因 |
| report_id | UUID | NULL | 关联套件报告（api_report.id，测试计划任务时报告类型为 suite） |
| import_record_id | UUID | NULL | 关联导入记录（接口同步任务时，api_import_record.id） |
| triggered_at | TIMESTAMP | NOT NULL | 触发时间 |
| duration_ms | INT | NULL | 执行耗时 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_stexec_task` (task_id), `idx_stexec_project_triggered` (project_id, triggered_at DESC)

#### 2.1.3 ~~Swagger URL 配置表（api_swagger_url）~~

> ~~用于定时导入任务绑定的 Swagger URL 配置。~~ **已废弃**：接口同步任务直接在定时任务中指定 URL，不再维护独立的 Swagger URL 配置表。既有实现迁移：原已存在的 Swagger URL 配置数据可保留（历史数据不强制清理），但新任务不再依赖该表。

### 2.2 错误码补充

> 定时任务错误码号段 `1000017501`–`1000017510`（`API_SCHEDULED_TASK_*`）统一登记于《API 测试基础设施详细设计说明书》2.2（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`），本文不重复登记；常用码：`1000017501` 定时任务不存在、`1000017502` Cron 表达式不合法、`1000017504` 任务上一次执行未结束。

---


## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `25-scheduled-task-overview.md` | 1. 引言（含 1.4 页面入口与权限口径）、2. 数据设计 |
| 任务管理 | `26-scheduled-task-management.md` | 1. 定时任务管理（含 1.9 权限口径）、2. 删除保护、3. 定时任务管理页 |
| 调度执行 | `27-scheduled-task-scheduler.md` | 1. 定时调度器、2. 测试计划任务执行（场景批量执行）、3. 接口同步任务执行、4. 调度器线程池 |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-09-23 | 初始版本 |
| V1.0 | 2026-10-02 | 移除失效的概要设计核心机制引用 |
| V1.0 | 2026-10-03 | 对齐实现：补页面入口与权限口径、修正分册对照表章节号与参考资料路径、错误码改为引用基础设施总览、删除保护范围补目标环境 |
| V1.0 | 2026-10-03 | 分册-章节对照表与交叉引用一致性复检 |
