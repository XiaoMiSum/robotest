# 软件测试平台——（总览分册）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的**公共基础设施**进行详细设计，定义公共数据结构、接口规范、错误码与执行引擎机制，为开发实现提供完整依据。各接口测试业务功能（接口管理、测试场景、环境管理、Mock 服务、定时任务等）的详细设计见对应的独立文档，它们均构建在本文档定义的基础设施之上。

### 1.2 范围

覆盖 SRS 3.8–3.12「公共需求」与概要设计第 4.1 章对应机制：

- **执行引擎**：Ryze 框架集成、格式转换（平台模型 → Ryze 标准 JSON）、资源池与并发调度、多场景组合执行、执行结果收集；
- **公共数据表**：调试记录、变更历史、执行记录、报告、全局资产、导入记录；
- **通用 API 约定**：请求/响应规范、分页、错误码号段；
- **格式转换机制**：平台自有模型与 Ryze TestSuite 的映射关系、配置继承与优先级、转换失败处理。

所有接口测试接口的鉴权、上下文传递（`X-Active-Workspace` / `X-Active-Project` 请求头）沿用平台既有约定（C4）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/01-readme.md`，3.8–3.12）
- 《概要设计说明书》（`docs/02-high-level-design/02-high-level-design.md`，4.1–4.2）
- 《工程规范 — API 设计》（`docs/06-spec/05-api.md`）
- 《工程规范 — 数据库》（`docs/06-spec/06-database.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

---


## 2. 数据设计

### 2.1 数据库表设计

数据库为 PostgreSQL，字段 snake_case，接口 JSON 使用 camelCase。全部新表遵循平台规范：`id`（UUID v7，应用层生成）、`created_at`、`updated_at`、`is_deleted`，禁止物理外键（C5）；索引遵循 C9。

表名域前缀统一使用 `api_`（接口测试业务域），公共组件使用 `api_component`。

#### 2.1.1 调试记录表（api_debug_record）

记录用户在快速调试面板中发起的请求快照。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目（ws_project.id） |
| user_id | UUID | NOT NULL | 发起人（sys_user.id） |
| name | VARCHAR(200) | NULL | 调试请求名称（用户可选保存） |
| protocol | VARCHAR(20) | NOT NULL | 协议：http / jdbc |
| method | VARCHAR(10) | NULL | HTTP 方法（GET/POST/PUT/PATCH/DELETE；jdbc 时为空） |
| url | VARCHAR(2000) | NULL | 请求 URL（含路径与 Query） |
| headers | JSONB | NOT NULL DEFAULT '[]' | 请求头列表 `[{key, value, enabled}]` |
| body_type | VARCHAR(20) | NULL | 请求体类型：none / json / form / raw / binary |
| body | JSONB | NULL | 请求体内容（结构随 body_type） |
| query_params | JSONB | NOT NULL DEFAULT '[]' | Query 参数列表 `[{key, value, enabled}]` |
| jdbc_config | JSONB | NULL | JDBC 取样器配置 `{datasourceId, sql, queryType}` |
| processors | JSONB | NOT NULL DEFAULT '[]' | 前置/后置处理器列表 |
| environment_id | UUID | NULL | 执行引用的环境 ID（相对 URL 拼接与变量来源） |
| timeout_ms | INT | NULL | 响应超时（毫秒） |
| executed_at | TIMESTAMP | NOT NULL | 执行时间 |
| duration_ms | INT | NULL | 执行耗时（毫秒） |
| status | VARCHAR(20) | NOT NULL | 执行结果：success / failed / error |
| response_status | INT | NULL | HTTP 响应状态码 |
| response_headers | JSONB | NULL | 响应头 |
| response_body | TEXT | NULL | 响应体（截断存储，最大 1MB） |
| response_size | INT | NULL | 响应体字节数 |
| error_message | VARCHAR(2000) | NULL | 错误信息（连接失败/超时等） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_debug_project_user` (project_id, user_id), `idx_debug_executed_at` (executed_at)

> 调试记录按项目清理策略自动清理（默认 90 天），与报告共用清理任务。

#### 2.1.2 变更历史表（api_change_history）

记录接口定义与测试场景每次保存产生的变更，用于只读追溯。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| target_type | VARCHAR(20) | NOT NULL | 变更对象类型：interface / scene |
| target_id | UUID | NOT NULL | 变更对象 ID |
| version | INT | NOT NULL | 变更序号（从 1 递增，同一对象内唯一） |
| change_type | VARCHAR(20) | NOT NULL | 变更类型：create / update / import / copy |
| content_diff | JSONB | NULL | 变更内容快照（完整或差异，由实现确定） |
| created_by | UUID | NOT NULL | 变更人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_change_target` (target_type, target_id, version DESC)

> 变更历史为只读追溯信息，不提供编辑/删除接口；按项目清理策略自动清理。

#### 2.1.3 执行记录表（api_execution_record）

记录场景每次执行的元数据，与报告一对一关联。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键（执行记录 ID） |
| project_id | UUID | NOT NULL | 归属项目 |
| scene_id | UUID | NOT NULL | 关联场景（api_scene.id） |
| environment_id | UUID | NULL | 使用的环境（api_environment.id） |
| execution_mode | VARCHAR(20) | NOT NULL | 执行方式：platform |
| status | VARCHAR(20) | NOT NULL DEFAULT 'pending' | pending / running / success / failed / cancelled / timeout |
| trigger_type | VARCHAR(20) | NOT NULL | 触发方式：manual / scheduled |
| source | VARCHAR(20) | NOT NULL DEFAULT 'scene' | 报告来源：scene（场景页运行）/ schedule（定时任务含立即执行）。场景页 [运行] 产生的报告不进报告列表（见 3.4.1） |
| report_id | UUID | NULL | 关联报告（api_report.id）。场景执行：场景报告 1:1；套件执行（定时任务）：同一套件下每个场景的执行记录共享同一套件报告 ID |
| error_message | VARCHAR(2000) | NULL | 失败原因 |
| executed_at | TIMESTAMP | NOT NULL | 执行时间 |
| duration_ms | INT | NULL | 执行耗时（毫秒） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_exec_scene_id` (scene_id), `idx_exec_project_executed` (project_id, executed_at DESC), `idx_exec_status` (status)

> 执行记录与报告共享清理策略（默认 90 天）；清理后执行记录保留元数据，报告详情置为「执行结果被清理」。

#### 2.1.4 报告表（api_report）

存储场景/套件执行结果的完整快照，包括明细数据集。报告按粒度分**场景报告**（`report_type='scene'`）与**套件报告**（`report_type='suite'`，定时任务含立即执行聚合生成，内嵌多个场景）。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| execution_record_id | UUID | NULL | 关联执行记录（api_execution_record.id）。场景报告关联单一执行记录；套件报告对应多条执行记录，此字段置空，聚合通过 `external_id`（= 任务 ID）关联 |
| report_type | VARCHAR(20) | NOT NULL | 报告粒度：`scene`（场景报告，单场景）/ `suite`（套件报告，定时任务含立即执行聚合多场景） |
| external_id | UUID | NULL | 外部关联 ID，语义随 `report_type`：`suite` 时为任务 ID（api_scheduled_task.id）；`scene` 时为场景 ID（api_scene.id）。场景页 [运行] 产生场景报告时填充场景 ID |
| name | VARCHAR(200) | NOT NULL | 报告名称：场景报告 = 场景名 + 执行时间戳；套件报告 = 任务名 + 执行时间戳（执行时固化） |
| environment_name | VARCHAR(100) | NULL | 环境名称快照 |
| execution_mode | VARCHAR(20) | NOT NULL | 执行方式：platform |
| status | VARCHAR(20) | NOT NULL | success / failed / partial（scene）；套件报告按整体判定 |
| source | VARCHAR(20) | NOT NULL DEFAULT 'scene' | 报告来源：scene（场景页运行）/ schedule（定时任务含立即执行）。场景页 [运行] 产生的报告不进报告列表（见 3.4.1） |
| summary | JSONB | NOT NULL | 结果汇总 `{total, passed, failed, skipped, duration_ms}`；套件报告额外含场景级汇总 `{totalScenes, passedScenes, failedScenes, totalSteps, passedSteps, failedSteps, skippedSteps}` |
| result | JSONB | NOT NULL | 结果明细数据集，按 `report_type` 分别构建：`scene` 为**场景数据集**（单场景步骤明细 `{sceneId, sceneName, status, summary, steps[]}`）；`suite` 为**套件数据集**（`{taskId, taskName, status, summary, scenes[]}`，每项即一份场景数据集，形成「场景 → 步骤」两级）。字段结构分别定义于《测试报告详细设计说明书》2.3 |
| ryze_snapshot | JSONB | NULL | 执行时序列化后的完整 Ryze 结果树（TestSuiteResult 树，getter 序列化，原始留档）。场景报告存单场景树；套件报告存聚合的套件树（若聚合为一份）。用于结果回溯与转换问题定位 |
| share_token | VARCHAR(64) | NULL | 分享链接令牌（生成分享链接时写入，无全局开关） |
| share_expires_at | TIMESTAMP | NULL | 分享链接过期时间（生成时由 expiresInDays 计算） |
| share_user_id | UUID | NULL | 分享者（最后一次生成分享链接的用户），用于分享记录展示与复制文本（见《测试报告详细设计说明书》4.2.3） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_report_type_external` (report_type, external_id)、`idx_report_project_created` (project_id, created_at DESC)、`idx_report_share_token` UNIQUE (share_token) WHERE share_token IS NOT NULL、`idx_report_share_user` (share_user_id) WHERE share_user_id IS NOT NULL

> `result` 数据集的步骤/接口/协议快照取自 Ryze 结果树（`SampleResult`），`request`/`response` 按对应协议 Real 类 getter 序列化（响应体截断防撑爆 JSONB）；`assertions`/`extractors` 分别来自 `AssertionResult`/`ExtractorResult`。`ryze_snapshot` 为执行时生成的完整 Ryze 树 JSON。
>
> **状态口径（执行异常重构后）**：平台通过 `RyzeResultAdapter` 将 Ryze 状态映射为报告状态——`passed→success`、`failed→failed`、`skipped/disabled→skipped`、`broken→error`（引擎异常）。**验证器失败**（采样器/处理器 `broken` + 失败验证器记录或 `AssertionError`）步骤映射为 `failed`（报告 `partial`）；处理器条件不满足显式 `skipped` 时，处理器条目映射 `skipped` 且不计入步骤失败。**多提取器异常**聚合为 `ExceptionGroup`，步骤归 `error`，`errorMessage` 展开各子异常消息（`提取器执行失败：<明细1>；<明细2>`）而非引导语；`ryze_snapshot` 中对 `ExceptionGroup` 序列化 `exceptions[]`、对链包装异常序列化 `suppressed[]`，普通异常保持 `{type, message}` 不变。

#### 2.1.5 公共组件表（api_component）

三级作用域（项目/空间/公共）的可复用组件资产库，资产类型包括前置处理器、后置处理器、验证器、提取器。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| scope | VARCHAR(10) | NOT NULL DEFAULT 'project' | 作用域：project / workspace / global |
| workspace_id | UUID | NULL | 归属空间（scope=workspace 时必填） |
| project_id | UUID | NULL | 归属项目（scope=project 时必填） |
| type | VARCHAR(30) | NOT NULL | 组件类型：preprocessor / postprocessor / validator / extractor |
| name | VARCHAR(100) | NOT NULL | 组件名称（同作用域同类型下唯一） |
| description | VARCHAR(500) | NULL | 组件描述 |
| sort_order | INT | NOT NULL DEFAULT 0 | 组件排序号（仅前置/后置处理器类使用，场景引入时决定处理器执行顺序） |
| config | JSONB | NOT NULL | 组件配置内容（结构与平台内同类型组件一致，不承载排序号） |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE | 启用状态（停用后不可再引入） |
| updated_by | UUID | NOT NULL | 最后维护人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：
- `idx_api_component_project` (project_id, type) WHERE scope = 'project' AND is_deleted = FALSE
- `idx_api_component_workspace` (workspace_id, type) WHERE scope = 'workspace' AND is_deleted = FALSE
- `uk_api_component_global` UNIQUE (type, name) WHERE scope = 'global' AND is_deleted = FALSE
- `uk_api_component_project` UNIQUE (project_id, type, name) WHERE scope = 'project' AND is_deleted = FALSE
- `uk_api_component_workspace` UNIQUE (workspace_id, type, name) WHERE scope = 'workspace' AND is_deleted = FALSE

> 公共组件支持三级作用域：项目级（仅项目内可见）、空间级（空间内所有项目可见）、全局级（全平台可见）。组件启用/停用状态仅影响资产选择器的可见性，不影响已配置场景的正常执行。维护权限分级：project → `api-component:edit`、workspace → `api-component:edit-space`、global → `api-component:edit-global`。

#### 2.1.6 导入记录表（api_import_record）

记录每次导入操作的结果，支持文件导入、Swagger URL 导入。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| import_type | VARCHAR(30) | NOT NULL | 导入方式：url_swagger / curl |
| source_name | VARCHAR(500) | NOT NULL | 导入源名称（文件名或 URL） |
| status | VARCHAR(20) | NOT NULL | success / partial / failed |
| summary | JSONB | NOT NULL | 导入结果 `{created, updated, failed, skipped}` |
| error_details | JSONB | NULL | 失败明细 `[{path, message}]` |
| created_by | UUID | NOT NULL | 导入人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_import_project_created` (project_id, created_at DESC)

### 2.2 错误码定义

接口测试业务域使用错误码号段 **7001–7799**，与既有号段不冲突：

> 本表错误码为**文档简写**。接口实际返回平台统一十位全码（形如 `1000017009`），简写与全码的映射在 `server/src/main/java/io/github/xiaomisum/robotest/framework/common/ErrorCodeConstants.java` 各号段注释中逐一登记（如 7009 ≙ 1000017009）；前端与联调以实际响应 `code` 为准。

| 错误码 | 常量名 | 说明 |
| ------ | ------ | ---- |
| **7001** | API_EXECUTOR_BUSY | 执行引擎繁忙（超出并发数，任务排队超长） |
| **7002** | API_EXEC_TIMEOUT | 执行超时 |
| **7003** | API_FORMAT_CONVERT_FAILED | 格式转换失败（平台模型 → Ryze JSON） |
| **7004** | API_EXEC_TASK_NOT_FOUND | 执行任务不存在 |
| **7005** | API_EXEC_TASK_STATE_INVALID | 执行任务状态不允许当前操作 |
| **7006** | API_ENV_NOT_FOUND | 环境不存在 |
| **7007** | API_REPORT_NOT_FOUND | 报告不存在 |
| **7009** | API_SHARE_EXPIRED | 分享链接无效或已过期 |
| **7010** | API_IMPORT_FORMAT_UNSUPPORTED | 导入格式不支持 |
| **7011** | API_IMPORT_PARSE_FAILED | 导入内容解析失败 |
| **7012** | API_IMPORT_URL_UNREACHABLE | URL 导入目标不可达 |
| **7013** | API_DEBUG_RECORD_NOT_FOUND | 调试记录不存在 |
| **7321** | API_COMMON_COMPONENT_NOT_FOUND | 公共组件不存在或不属于当前可见范围 |
| **7322** | API_COMMON_COMPONENT_NAME_EXISTS | 同作用域下已存在同名公共组件 |
| **7016** | API_IMPORT_RECORD_NOT_FOUND | 导入记录不存在 |
| **7101** | API_INTERFACE_NOT_FOUND | 接口定义不存在 |
| **7102** | API_INTERFACE_NAME_EXISTS | 接口定义名称重复 |
| **7103** | API_INTERFACE_REFERENCED | 接口定义被引用无法删除 |
| **7201** | API_SCENE_NOT_FOUND | 场景不存在 |
| **7202** | API_SCENE_STEP_NOT_FOUND | 场景步骤不存在 |
| **7203** | API_SCENE_REFERENCED | 场景被定时任务引用无法删除 |
| **7204** | API_LINK_SOURCE_MISSING | 链接引用源不存在 |
| **7301** | API_MOCK_NOT_FOUND | Mock 定义不存在 |
| **7302** | API_MOCK_ADDR_CONFLICT | Mock 地址冲突 |
| **7401** | API_ENV_NAME_EXISTS | 环境名称重复 |
| **7402** | API_ENV_REFERENCED | 环境被场景引用无法删除 |
| **7403** | API_DATASOURCE_CONN_FAILED | 数据源连接测试失败 |
| **7601** | API_SCHEDULED_TASK_NOT_FOUND | 定时任务不存在 |
| **7602** | API_CRON_INVALID | Cron 表达式无效 |
| **7603** | API_SCHEDULED_TASK_RUNNING | 任务上一次执行未结束 |

---


### 3.1 通用约定

- 项目级：`/api/project/**`，头 `Authorization` + `X-Active-Workspace` + `X-Active-Project`。
- 通用响应：`{ "code": 200, "message": "success", "data": {} }`；命名 camelCase。下文各接口的响应示例**仅展示 `data` 字段内容**，省略外层 `code` / `message` 包裹。
- 分页请求：`?page=1&pageSize=20`；分页响应 `{ records: [], total: N }`。
- 所有接口的错误响应遵循统一格式：`{ "code": 7001, "message": "执行引擎繁忙" }`。


### 4.2 数据清理策略

报告与执行记录共享清理策略，默认保留 90 天（系统配置项）：

- 清理由系统每日定时任务执行（复用 AI 基础设施的每日清理任务框架）。
- 清理逻辑：先逻辑删除（`is_deleted = true`），次日物理删除（避免长事务）。
- 清理后执行记录保留元数据，报告详情置为「执行结果被清理」。
- 调试记录与变更历史按同一策略清理。

---


### 6.1 迁移脚本

新建 DDL 迁移脚本 `server/src/main/resources/db/v1.2.sql`，包含本文档定义的全部公共表（2.1.1–2.1.6）以及其余详细设计文档定义的业务表。脚本随本文档同步修订。

**app_report 结构性变更迁移（场景/套件两级报告模型）**——本迭代由单场景报告升级为场景/套件两类报告，需将既有 `api_report` 结构调整如下（无物理外键，符合 C5）：

```sql
-- 1) 类型化：新增 report_type（默认按旧数据回填为 scene）
ALTER TABLE api_report ADD COLUMN report_type VARCHAR(20) NOT NULL DEFAULT 'scene';
ALTER TABLE api_report ALTER COLUMN report_type DROP DEFAULT;

-- 2) 场景 ID 通配化：scene_id 语义随 report_type，suite 时为任务 ID
ALTER TABLE api_report RENAME COLUMN scene_id TO external_id;

-- 3) scene_id 原为 NOT NULL，suite 报告不再强绑单场景
ALTER TABLE api_report ALTER COLUMN external_id DROP NOT NULL;

-- 4) 执行记录关联可空：套件报告对应多条执行记录
ALTER TABLE api_report ALTER COLUMN execution_record_id DROP NOT NULL;

-- 5) 步骤明细升级为结果数据集：旧 step_results 数据回写为 result（scene 数据集包裹）
ALTER TABLE api_report RENAME COLUMN step_results TO result;

-- 6) 新增/维持索引：以 report_type+external_id 作聚合/场景维度查询入口
CREATE INDEX IF NOT EXISTS idx_report_type_external ON api_report (report_type, external_id);
CREATE INDEX IF NOT EXISTS idx_report_project_created ON api_report (project_id, created_at DESC);
DROP INDEX IF EXISTS idx_report_scene_id;

-- 7) 分享者记录：分享复制文本需展示分享人（测试报告详细设计 4.2.3）
ALTER TABLE api_report ADD COLUMN share_user_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_report_share_user ON api_report (share_user_id) WHERE share_user_id IS NOT NULL;
```

> 迁移需由 v1.2 脚本按上述 DDL 执行；旧 `step_results`（扁平步骤数组）在迁移或读取层包装为 `scene` 数据集（`{sceneId, sceneName, status, summary, steps: step_results}`）以兼容历史报告展示。


### 6.4 Mock 服务端口

Mock 服务随应用进程运行，通过平台 HTTP 端口或独立端口提供 Mock 响应。端口配置：

```yaml
api-test:
  mock:
    port: 8081  # 独立端口；为空时复用主端口
```

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `21-api-test-infra-overview.md` | 前言、1. 引言、2. 数据设计、3.1 通用约定、4.2 数据清理策略、6.1 迁移脚本、6.4 Mock 服务端口 |
| 执行引擎 | `22-api-test-infra-engine.md` | 3.2 执行引擎接口、4.1 执行引擎与格式转换、5.1 执行状态轮询、6.2 Ryze 依赖引入、6.3 执行引擎线程池配置 |
| 调试记录 | `23-api-test-infra-debug-record.md` | 3.3 调试记录接口 |
| 测试报告 | `24-api-test-infra-report.md` | 3.4 报告接口、5.2 报告详情渲染 |
| 公共组件 | `25-api-test-infra-common-component.md` | 3.5 公共组件接口、3.6 公共组件复制接口、5.3 公共组件新建/编辑 |
| 导入记录 | `26-api-test-infra-import-record.md` | 3.7 导入记录接口 |
