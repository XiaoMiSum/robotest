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

- **定时任务管理**：测试计划（场景批量执行）与接口同步两类定时任务的 CRUD、启停、Cron 校验与立即执行；
- **定时调度器**：JVM 内 ScheduledExecutorService 统一调度，执行记录与状态管理；
- **删除保护**：被定时任务选中的接口测试场景/模块受删除保护；
- **接口同步**：定时任务直接指定 OpenAPI/Swagger JSON 文件的 URL 地址。

> 场景执行引擎见《测试场景详细设计说明书》（`docs/04-detailed-design/01-readme.md`）与《场景执行详细设计说明书》（`docs/04-detailed-design/场景执行详细设计说明书.md`）；接口导入引擎见《接口管理详细设计说明书》（`docs/04-detailed-design/01-readme.md`）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/01-readme.md`，3.6）
- 《概要设计说明书》（`docs/02-high-level-design/02-high-level-design.md`，4.4、4.7）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/01-readme.md`）

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
| report_id | UUID | NULL | 关联套件报告（api_report.id，测试计划任务时，`:报告类型为 suite`） |
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

> 7601–7603 的定义见《API 测试基础设施详细设计说明书》2.2，此处不重复描述。

---


## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `44-scheduled-task-overview.md` | 前言、1. 引言、2. 数据设计 |
| 任务管理 | `45-scheduled-task-management.md` | 3.1 定时任务管理、4.2 删除保护、5.1 定时任务管理页 |
| 调度执行 | `46-scheduled-task-scheduler.md` | 4.1 定时调度器、4.3 测试计划任务执行、4.4 接口同步任务执行、6.1 调度器线程池 |
