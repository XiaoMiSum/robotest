# 软件测试平台——API 测试基础设施详细设计说明书

**文档版本**：V1.3
**日期**：2026-08-26
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台 V1.2 接口测试业务域的**公共基础设施**进行详细设计，定义公共数据结构、接口规范、错误码与执行引擎机制，为开发实现提供完整依据。各接口测试业务功能（接口管理、测试场景、环境管理、Mock 服务、GitLab 集成、定时任务等）的详细设计见对应的独立文档，它们均构建在本文档定义的基础设施之上。

### 1.2 范围

覆盖 SRS 3.8–3.12「公共需求」与概要设计第 4.1 章对应机制：

- **执行引擎**：Ryze 框架集成、格式转换（平台模型 → Ryze 标准 JSON）、资源池与并发调度、多场景组合执行、执行结果收集；
- **公共数据表**：调试记录、变更历史、执行记录、报告、全局资产、导入记录；
- **通用 API 约定**：请求/响应规范、分页、错误码号段；
- **格式转换机制**：平台自有模型与 Ryze TestSuite 的映射关系、配置继承与优先级、转换失败处理。

所有接口测试接口的鉴权、上下文传递（`X-Active-Workspace` / `X-Active-Project` 请求头）沿用平台既有约定（C4）。

### 1.3 参考资料

- 《接口测试需求规格说明书 V1.2》（`docs/需求/接口测试需求规格说明书.md`，3.8–3.12）
- 《概要设计说明书 V1.2》（`docs/概要/概要设计说明书.md`，4.1–4.2）
- 《工程规范 — API 设计》（`docs/spec/api.md`）
- 《工程规范 — 数据库》（`docs/spec/database.md`）
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
| execution_mode | VARCHAR(20) | NOT NULL | 执行方式：platform / pipeline |
| status | VARCHAR(20) | NOT NULL DEFAULT 'pending' | pending / running / success / failed / cancelled / timeout |
| trigger_type | VARCHAR(20) | NOT NULL | 触发方式：manual / scheduled / pipeline |
| report_id | UUID | NULL | 关联报告（api_report.id，1:1） |
| pipeline_id | VARCHAR(100) | NULL | GitLab 流水线 ID（pipeline 执行时） |
| pipeline_url | VARCHAR(500) | NULL | 流水线链接 |
| error_message | VARCHAR(2000) | NULL | 失败原因 |
| executed_at | TIMESTAMP | NOT NULL | 执行时间 |
| duration_ms | INT | NULL | 执行耗时（毫秒） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_exec_scene_id` (scene_id), `idx_exec_project_executed` (project_id, executed_at DESC), `idx_exec_status` (status)

> 执行记录与报告共享清理策略（默认 90 天）；清理后执行记录保留元数据，报告详情置为「执行结果被清理」。

#### 2.1.4 报告表（api_report）

存储场景执行结果的完整快照，包括步骤级明细。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| execution_record_id | UUID | NOT NULL | 关联执行记录（api_execution_record.id） |
| scene_id | UUID | NOT NULL | 关联场景 |
| scene_name | VARCHAR(200) | NOT NULL | 场景名称快照（执行时固化） |
| environment_name | VARCHAR(100) | NULL | 环境名称快照 |
| execution_mode | VARCHAR(20) | NOT NULL | 执行方式：platform / pipeline |
| status | VARCHAR(20) | NOT NULL | success / failed / partial |
| summary | JSONB | NOT NULL | 结果汇总 `{total, passed, failed, skipped, duration_ms}` |
| step_results | JSONB | NOT NULL | 步骤级结果明细数组 `[{stepId, name, type, status, request, response, duration_ms, validators}]` |
| ryze_snapshot | JSONB | NULL | 执行时的 Ryze 标准 JSON 快照（用于结果回溯与转换问题定位） |
| share_enabled | BOOLEAN | NOT NULL DEFAULT FALSE | 分享是否开启 |
| share_token | VARCHAR(64) | NULL | 分享链接令牌 |
| share_expires_at | TIMESTAMP | NULL | 分享链接过期时间 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_report_scene_id` (scene_id), `idx_report_project_created` (project_id, created_at DESC), `idx_report_share_token` UNIQUE (share_token) WHERE share_token IS NOT NULL

> `step_results` 中每个步骤包含完整的请求/响应快照（请求头、请求体、响应状态码、响应头、响应体截断），供详情查看与导出。`ryze_snapshot` 为执行时生成的完整 Ryze JSON，仅平台内执行时保留，流水线执行时为空。

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
| config | JSONB | NOT NULL | 组件配置内容（结构与平台内同类型组件一致） |
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

记录每次导入操作的结果，支持文件导入、Swagger URL 导入、可执行导入、元数据导入。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| import_type | VARCHAR(30) | NOT NULL | 导入方式：file_swagger / file_postman / file_har / file_jmeter / url_swagger / executable / metadata |
| source_name | VARCHAR(500) | NOT NULL | 导入源名称（文件名或 URL） |
| status | VARCHAR(20) | NOT NULL | success / partial / failed |
| summary | JSONB | NOT NULL | 导入结果 `{created, updated, failed, skipped}` |
| error_details | JSONB | NULL | 失败明细 `[{path, message}]` |
| repository_id | UUID | NULL | 关联 GitLab 仓库配置（executable/metadata 导入时） |
| created_by | UUID | NOT NULL | 导入人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_import_project_created` (project_id, created_at DESC)

### 2.2 错误码定义

接口测试业务域使用错误码号段 **7001–7799**，与既有号段不冲突：

> 本表错误码为**文档简写**。接口实际返回平台统一十位全码（形如 `1000017701`），简写与全码的映射在 `server/src/main/java/io/github/xiaomisum/robotest/framework/common/ErrorCodeConstants.java` 各号段注释中逐一登记（如 7701 ≙ 1000017701）；前端与联调以实际响应 `code` 为准。

| 错误码 | 常量名 | 说明 |
| ------ | ------ | ---- |
| **7001** | API_EXECUTOR_BUSY | 执行引擎繁忙（超出并发数，任务排队超长） |
| **7002** | API_EXEC_TIMEOUT | 执行超时 |
| **7003** | API_FORMAT_CONVERT_FAILED | 格式转换失败（平台模型 → Ryze JSON） |
| **7004** | API_EXEC_TASK_NOT_FOUND | 执行任务不存在 |
| **7005** | API_EXEC_TASK_STATE_INVALID | 执行任务状态不允许当前操作 |
| **7006** | API_ENV_NOT_FOUND | 环境不存在 |
| **7007** | API_REPORT_NOT_FOUND | 报告不存在 |
| **7008** | API_SHARE_NOT_ENABLED | 报告分享未开启 |
| **7009** | API_SHARE_EXPIRED | 分享链接已过期 |
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
| **7104** | API_INTERFACE_STEP_NOT_FOUND | 公共步骤不存在 |
| **7201** | API_SCENE_NOT_FOUND | 场景不存在 |
| **7202** | API_SCENE_STEP_NOT_FOUND | 场景步骤不存在 |
| **7203** | API_SCENE_REFERENCED | 场景被定时任务引用无法删除 |
| **7204** | API_LINK_SOURCE_MISSING | 链接引用源不存在 |
| **7301** | API_MOCK_NOT_FOUND | Mock 定义不存在 |
| **7302** | API_MOCK_ADDR_CONFLICT | Mock 地址冲突 |
| **7401** | API_ENV_NAME_EXISTS | 环境名称重复 |
| **7402** | API_ENV_REFERENCED | 环境被场景引用无法删除 |
| **7403** | API_DATASOURCE_CONN_FAILED | 数据源连接测试失败 |
| **7501** | API_GITLAB_REPO_NOT_FOUND | 仓库配置不存在 |
| **7502** | API_GITLAB_REPO_UNREACHABLE | 仓库地址不可达 |
| **7503** | API_GITLAB_TOKEN_INVALID | 令牌无效 |
| **7504** | API_GITLAB_METADATA_MISSING | 测试类元数据不存在 |
| **7601** | API_SCHEDULED_TASK_NOT_FOUND | 定时任务不存在 |
| **7602** | API_CRON_INVALID | Cron 表达式无效 |
| **7603** | API_SCHEDULED_TASK_RUNNING | 任务上一次执行未结束 |
| **7701** | API_SETTING_KEY_INVALID | 设置项标识非法（不在注册表白名单） |
| **7702** | API_SETTING_VALUE_INVALID | 设置值非法（格式或取值范围不满足注册表约束） |

---

## 3. 接口详细设计

### 3.1 通用约定

- 项目级：`/api/project/**`，头 `Authorization` + `X-Active-Workspace` + `X-Active-Project`。
- 通用响应：`{ "code": 200, "message": "success", "data": {} }`；命名 camelCase。下文各接口的响应示例**仅展示 `data` 字段内容**，省略外层 `code` / `message` 包裹。
- 分页请求：`?page=1&pageSize=20`；分页响应 `{ records: [], total: N }`。
- 所有接口的错误响应遵循统一格式：`{ "code": 7001, "message": "执行引擎繁忙" }`。

### 3.2 执行引擎接口

#### 3.2.1 触发场景执行

- **路径**：`POST /api/project/scenes/:sceneId/execute`
- **说明**：触发单个场景或组合执行。支持单场景执行、批量执行。
- **请求体**：

```json
{
  "environmentId": "018f...",
  "executionMode": "platform",
  "sceneIds": ["018f..."],
  "variableOverrides": { "base_url": "https://staging.example.com" }
}
```

- `environmentId`：目标环境 ID（可选，缺省使用项目默认环境）。
- `executionMode`：`platform`（平台内执行）；`pipeline`（仓库流水线执行，需 sceneIds 中的场景为元数据导入场景）。
- `sceneIds`：批量执行时传入多个场景 ID；单场景执行时传入单个 ID 或通过路径参数指定。
- `variableOverrides`：运行时变量覆盖（可选）。
- **响应**：

```json
{
  "executionHistoryId": "018f...",
  "status": "pending"
}
```

#### 3.2.2 查询执行状态

- **路径**：`GET /api/project/executions/:executionId`
- **响应**：

```json
{
  "id": "018f...",
  "sceneId": "018f...",
  "sceneName": "登录接口测试",
  "status": "running",
  "executionMode": "platform",
  "triggerType": "manual",
  "progress": 60,
  "executedAt": "2026-08-17T10:30:00Z",
  "durationMs": null
}
```

#### 3.2.3 取消执行

- **路径**：`POST /api/project/executions/:executionId/cancel`
- **说明**：取消进行中的执行任务。已执行的步骤结果保留，标记为 cancelled。
- **响应**：`{ "success": true }`

### 3.3 调试记录接口

#### 3.3.1 查询调试记录列表

- **路径**：`GET /api/project/debug-records?page=1&pageSize=20`
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "name": "登录接口调试",
      "method": "POST",
      "url": "/api/auth/login",
      "status": "success",
      "responseStatus": 200,
      "durationMs": 230,
      "executedAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 15
}
```

#### 3.3.2 删除调试记录

- **路径**：`DELETE /api/project/debug-records/:id`
- **响应**：`{ "success": true }`

### 3.4 报告接口

#### 3.4.1 查询报告列表

- **路径**：`GET /api/project/reports?page=1&pageSize=20&status=success`
- **筛选参数**：`status`（可选）、`sceneId`（可选）、`startDate` / `endDate`（可选）。
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "sceneName": "登录接口测试",
      "executionMode": "platform",
      "status": "success",
      "summary": { "total": 10, "passed": 9, "failed": 1, "skipped": 0, "durationMs": 5230 },
      "environmentName": "测试环境",
      "createdAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 42
}
```

#### 3.4.2 查询报告详情

- **路径**：`GET /api/project/reports/:id`
- **响应**：包含完整 `stepResults` 数组（步骤级请求/响应快照）。

#### 3.4.3 生成分享链接

- **路径**：`POST /api/project/reports/:id/share`
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
  "expiresAt": "2026-08-24T10:30:00Z"
}
```

#### 3.4.4 访问分享报告（免登录）

- **路径**：`GET /api/public/api-reports/:id?token=abc123`
- **说明**：不需要 Authorization 头，通过 token 校验访问权限。token 无效或过期返回 403。

#### 3.4.5 导出报告

- **路径**：`GET /api/project/reports/:id/export?format=json`
- **说明**：支持 `json` / `html` 格式。响应为文件流。

#### 3.4.6 删除报告

- **路径**：`DELETE /api/project/reports/:id`
- **响应**：`{ "success": true }`

### 3.5 公共组件接口（三级作用域）

#### 3.5.1 分页查询公共组件列表

- **路径**：`GET /api/project/components?pageNo=1&pageSize=20&type=preprocessor&scope=project&keyword=Token`
- **筛选参数**：`type`（可选，preprocessor/postprocessor/validator/extractor）、`scope`（可选，project/workspace/global）、`keyword`（可选，名称模糊搜索）、`enabled`（可选）。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "scope": "project",
      "type": "preprocessor",
      "name": "Token 预置",
      "description": "从环境变量获取 Token 并注入请求头",
      "config": "{\"handlerType\":\"http\",\"method\":\"POST\",\"url\":\"https://api.example.com/token\"}",
      "enabled": true,
      "updatedAt": "2026-08-17 10:30:00"
    }
  ],
  "total": 8
}
```

#### 3.5.2 创建公共组件

- **路径**：`POST /api/project/components`
- **请求体**：

```json
{
  "type": "preprocessor",
  "name": "Token 预置",
  "description": "从环境变量获取 Token 并注入请求头",
  "scope": "project",
  "config": {
    "handlerType": "http",
    "method": "POST",
    "url": "https://api.example.com/token",
    "contentType": "application/json",
    "headers": [],
    "body": "",
    "enabled": true,
    "sortOrder": 0,
    "extractors": []
  }
}
```

- **响应**：`{ "id": "018f..." }`

#### 3.5.3 更新公共组件

- **路径**：`PUT /api/project/components/:id`
- **请求体**：同 3.5.2（`scope` 和 `type` 编辑态不可变更）。

#### 3.5.4 启停公共组件

- **路径**：`PATCH /api/project/components/:id/toggle?enabled=false`
- **响应**：`{ "success": true }`

#### 3.5.5 删除公共组件

- **路径**：`DELETE /api/project/components/:id`
- **响应**：`{ "success": true }`

#### 3.5.6 批量启停

- **路径**：`PATCH /api/project/components/batch/toggle?enabled=false`
- **请求体**：`{ "ids": ["018f...", "018g..."] }`
- **响应**：`{ "success": true }`

#### 3.5.7 批量删除

- **路径**：`DELETE /api/project/components/batch`
- **请求体**：`{ "ids": ["018f...", "018g..."] }`
- **响应**：`{ "success": true }`

### 3.6 公共组件复制接口

#### 3.6.1 复制公共组件

- **路径**：`POST /api/project/components/:id/copy`
- **说明**：将公共组件复制为同一作用域下的新组件，产生独立副本，名称追加" (副本)"，默认停用。
- **响应**：

```json
{
  "id": "018f...",
  "type": "preprocessor",
  "name": "Token 预置 (副本)",
  "sourceAssetId": "018g..."
}
```

### 3.7 导入记录接口

#### 3.7.1 查询导入记录

- **路径**：`GET /api/project/import-records?page=1&pageSize=20`
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "importType": "file_swagger",
      "sourceName": "petstore.yaml",
      "status": "success",
      "summary": { "created": 12, "updated": 3, "failed": 0, "skipped": 1 },
      "createdAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 8
}
```

---

## 4. 业务逻辑设计

### 4.1 执行引擎与格式转换

执行引擎是接口测试的核心基础设施，基于 Ryze 框架构建。

#### 4.1.1 执行模式

| 模式 | 说明 | 资源消耗 |
| ---- | ---- | ---- |
| 平台内执行 | 调试请求与场景执行由平台执行引擎在服务端执行，格式转换后交 Ryze 引擎运行 | 消耗平台执行引擎资源 |
| 仓库流水线执行 | 平台通过 GitLab API 触发仓库 CI 流水线执行 | 不占用平台执行引擎资源 |

#### 4.1.2 格式转换机制

平台以自有字段模型存储全部接口测试数据，不持久化 Ryze 文档格式。Ryze 标准 JSON 仅在执行时由平台实时解析生成。

**平台模型 → Ryze TestSuite 映射**：

| 平台模型 | Ryze 标准 JSON |
| -------- | -------------- |
| 场景 | TestSuite（顶层集合） |
| 场景参数 | variables |
| 环境 HTTP 配置（多个） | configelements（http 类型，挂载到 root testsuite） |
| 环境数据源（多个） | configelements（data_source 类型，挂载到 root testsuite） |
| 全局前置/后置处理器 | preprocessors / postprocessors |
| 场景步骤（http 取样器） | children（testclass: http） |
| 场景步骤（jdbc 取样器） | children（testclass: jdbc） |
| 步骤级处理器 | 步骤级 preprocessors / postprocessors |
| 步骤级验证器 | validators |
| 步骤级提取器 | extractors |
| 请求头、请求体、Query 参数 | config 对应字段 |

**环境配置 → configelements 转换规则**：

环境中的 HTTP 配置和数据源在执行时转为 Ryze configelements，挂载到 root testsuite 级别，由 Ryze 框架自动处理继承与覆盖：

| 环境配置 | Ryze configelement type | 挂载字段 |
| -------- | ----------------------- | -------- |
| api_environment_http（HTTP 配置） | `http_config` | configelements 数组 |
| api_environment_data_source（数据源） | `data_source` | configelements 数组 |

**示例**：

```json
{
  "title": "测试场景",
  "configelements": [
    { "type": "http_config", "name": "内部API", "base_url": "https://api.internal.com", "headers": {"Authorization": "${token}"} },
    { "type": "http_config", "name": "第三方支付", "base_url": "https://pay.third.com", "headers": {} },
    { "type": "data_source", "name": "测试库", "driver": "com.mysql.cj.jdbc.Driver", "url": "jdbc:mysql://staging-db:3306/test" }
  ],
  "children": [...]
}
```

**步骤级 request_config 与 configelements 的关系**：

步骤的 `request_config` 保存步骤自身的差异配置（api 路径、额外 headers/params/body 等）。`base_url` 允许为空，为空时继承环境 HTTP 配置的 base_url。步骤无需重复配置环境中已有的值，配置了也没关系——Ryze 以最低层级优先（步骤级 > 环境级）。

**多场景组合执行的层级映射**：

Ryze TestSuite 支持多层嵌套（项目级 → 模块级 → 用例级），子级集合自动继承父级的变量、配置元件与处理器。

| 平台组合执行 | Ryze TestSuite |
| ------------ | -------------- |
| 执行任务（批量） | 顶层 TestSuite（项目级） |
| 共享变量 | variables（顶层） |
| 共享配置元件 | configelements（顶层） |
| 场景 A | TestSuite（模块级子集合） |
| 场景 A 的步骤 | children（testclass: http/jdbc） |
| 场景 B | TestSuite（模块级子集合） |

**配置继承与优先级**（遵循 Ryze 原生语义）：

子级集合自动继承父级的变量、配置元件与处理器，同名配置项子级覆盖父级。执行时的合并优先级（从低到高）：

```
环境默认配置 < 顶层组合配置 < 场景级配置 < 步骤级配置
```

**格式转换失败处理**：

转换失败（平台模型存在 Ryze 无法表达的配置）时，执行引擎拒绝执行并返回错误码 7003（`API_FORMAT_CONVERT_FAILED`），不产生部分执行结果。错误信息包含具体失败原因与定位信息（如不支持的处理器类型、缺失的必填字段等）。

#### 4.1.3 资源池与并发调度

- 执行任务统一纳入资源池管理，最大并发数由系统配置项控制（默认 5）。
- 超出并发数的任务排队等待，队列长度可配置（默认 100），超出队列长度时返回错误码 7001（`API_EXECUTOR_BUSY`）。
- 定时触发的场景执行与手动执行统一排队。
- 组合执行作为一个整体任务入队，内部各场景按 Ryze 引擎串行或并行执行（由场景设置中的执行模式配置）。
- 执行超时按请求级「响应超时」配置控制，超时任务标记为超时失败（错误码 7002），记录错误信息。

#### 4.1.4 执行结果收集

Ryze 引擎执行完成后，平台收集执行结果并转换为平台自有格式：

1. **步骤级结果**：每个步骤的请求/响应快照、耗时、验证器结果、提取器结果。
2. **结果汇总**：总步骤数、通过数、失败数、跳过数、总耗时。
3. **Ryze 快照**：执行时生成的完整 Ryze JSON，保存至 `api_report.ryze_snapshot`，用于结果回溯与转换问题定位。
4. **报告生成**：将结果写入 `api_report` 表，同时更新 `api_execution_record` 状态。

### 4.2 公共步骤机制

公共步骤是接口定义下维护的可复用请求步骤集合，供场景配置时选择添加。

- **存储**：公共步骤归属于接口定义（`api_interface.id`），存储于 `api_interface_step` 表（定义见《接口管理详细设计说明书》）。
- **引用方式**：场景步骤通过 `sourceId` + 引用模式（copy/link）引用公共步骤。
  - **复制（copy）**：创建公共步骤的独立副本，后续修改互不影响。
  - **链接引用（link）**：步骤内容跟随源公共步骤变化同步更新，但当前场景中的启用状态、参数值可独立调整。
- **删除保护**：被场景引用的公共步骤受删除保护，需先解除引用。

### 4.3 数据清理策略

报告与执行记录共享清理策略，默认保留 90 天（系统配置项）：

- 清理由系统每日定时任务执行（复用 V1.1 AI 基础设施的每日清理任务框架）。
- 清理逻辑：先逻辑删除（`is_deleted = true`），次日物理删除（避免长事务）。
- 清理后执行记录保留元数据，报告详情置为「执行结果被清理」。
- 调试记录与变更历史按同一策略清理。

---

## 5. 前端设计

### 5.1 执行状态轮询

场景执行触发后，前端通过轮询（2 秒间隔）查询执行状态，直到状态变为终态（success/failed/cancelled/timeout）：

```
触发执行 → 获得 executionHistoryId
  ↓ 轮询 GET /api/project/executions/:id
  status = pending/running → 继续轮询
  status = success → 跳转报告详情
  status = failed/cancelled/timeout → 展示错误信息
```

### 5.2 报告详情渲染

报告详情页根据 `stepResults` 数组渲染步骤树，每个步骤可展开查看：

- 请求信息：方法、URL、请求头、请求体。
- 响应信息：状态码、响应头、响应体（格式化展示）。
- 验证器结果：断言通过/失败明细。
- 提取器结果：提取的变量名与值。
- 耗时信息。

### 5.3 公共组件新建/编辑

新建与编辑组件使用抽屉（宽 640px），公共字段 + 随类型切换的配置表单。公共字段：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 名称 | text | 是 | 同作用域同类型内唯一（7322） |
| 类型 | select | 是 | preprocessor / postprocessor / validator / extractor；编辑态置灰不可改 |
| 作用域 | select | 是 | project / workspace / global；编辑态隐藏，仅新建时可选 |
| 描述 | textarea | 否 | 组件用途说明 |
| 启用 | switch | 仅处理器类 | 启用/禁用开关，禁用时不参与执行；仅前置/后置处理器类组件显示 |
| 排序号 | number | 仅处理器类 | 多处理器执行顺序，升序；仅前置/后置处理器类组件显示 |

#### 5.3.1 前置处理器 / 后置处理器

切换类型为前置/后置处理器时，展示处理器配置区。采用与验证器/提取器一致的平台设计原则：

基础信息包含启用与排序号（见 5.3 公共字段表，仅处理器类组件显示），配置区仅保留处理器核心参数与提取器：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 处理器类型 | select | 是 | `发送 HTTP 请求` / `执行 SQL`；首期支持两种，其余协议随多协议扩展预留 |

> **说明**：首期不提供处理器级异步与条件字段；启用/禁用与执行顺序统一通过基础信息的启用、排序号控制。

**「发送 HTTP 请求」处理器配置：**

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 请求方法 | select | 是 | GET / POST / PUT / PATCH / DELETE |
| URL | text | 是 | 支持 `${变量名}` 引用 |
| 请求头 | kv-table | 否 | 键值对编辑器，支持变量引用 |
| Content-Type | select | 否 | application/json / application/x-www-form-urlencoded / multipart/form-data |
| 请求体 | textarea / kv-table | 否 | 根据 Content-Type 切换：JSON 为 textarea，form-data 为 kv-table（支持文件类型） |

**「执行 SQL」处理器配置：**

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 数据源 | select | 是 | 从环境管理配置的数据源中选择；未配置时提示「请先在环境管理中配置数据源」 |
| SQL 语句 | textarea | 是 | 支持 `${变量名}` 引用；执行前校验数据源连接 |
| 参数 | list | 否 | SQL 占位符参数，仅值列表（对应 Ryze `args` 数组，按 `?` 占位顺序传入） |

**平台 → Ryze 转换规则**（执行引擎层）：

| 平台处理器类型 | → Ryze testclass | config 映射 |
| ------------- | ----------------- | ----------- |
| 发送 HTTP 请求 | `http` | method/method, URL/url, 请求头/headers, 请求体/body, Content-Type/body_type |
| 执行 SQL | `jdbc` | 数据源/datasourceId, SQL 语句/sql, 参数/args |

**提取器（可选）：** 处理器可携带提取器，从处理器响应中提取变量供后续步骤使用。提取器列表以子表形式嵌入处理器配置区底部，每行结构与 5.3.3 提取器配置一致（唯一区别：嵌入处理器时无「启用」开关，处理器自身启用状态统一）。支持：

| 操作 | 说明 |
| ---- | ---- |
| 添加提取器 | 在子表内新增一行，手动逐字段填写（来源/表达式/目标变量名/提取描述） |
| 删除提取器 | 删除子表中任意一行 |
| 从公共组件获取 | 点击弹出「引入选择器」（见 `docs/交互设计/全局资产交互设计.md` 2.4）：范围为本作用域同项目内类型为 extractor 且启用的公共组件，支持搜索；选中引入为**复制**，得到独立副本，与源资产无关联，副本内容平铺追加到当前处理器提取器子表 |

#### 5.3.2 验证器

切换类型为验证器时，展示验证器配置区。采用**平台自有数据结构**，遵循三大原则：

1. **自然语言化**：使用用户理解的术语（如「返回码」「等于」），隐藏 Ryze 内部概念
2. **最小化暴露**：仅暴露用户必须配置的字段，其余由平台推断
3. **启用禁用**：每个验证器可独立启用/禁用

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 启用 | switch | 是 | 启用/禁用开关，禁用时不参与执行 |
| 验证目标 | select | 是 | `返回码` / `JSON 字段` / `响应头` / `响应体` / `正则匹配` / `XPath` / `Groovy 脚本` |
| 表达式 | text | 视目标 | JSONPath / XPath / 正则 / 响应头名（仅部分目标需要） |
| 比较条件 | select | 是 | `等于` / `不等于` / `大于` / `小于` / `大于等于` / `小于等于` / `包含` / `不包含` / `以...开头` / `以...结尾` / `匹配正则` |
| 期望值 | text | 视目标 | 期望值（仅部分目标需要） |
| 断言描述 | text | 否 | 用于报告展示的断言说明 |

**目标与条件的联动关系**：

| 验证目标 | 表达式是否必填 | 表达式说明 | 期望值是否必填 | 可选条件 |
| ---- | ---- | ---- | ---- | ---- |
| 返回码 | 否 | — | 是 | 等于/不等于/大于/小于 |
| JSON 字段 | 是 | JSONPath，如 `$.code` | 是 | 等于/不等于/包含/匹配正则 |
| 响应头 | 是 | 头名，如 `Content-Type` | 是 | 等于/不等于 |
| 响应体 | 否 | — | 是 | 包含/不包含/以...开头/以...结尾 |
| 正则匹配 | 是 | 正则表达式 | 否 | 匹配正则 |
| XPath | 是 | XPath 表达式 | 是 | 等于/不等于 |
| Groovy 脚本 | 是 | 脚本内容 | 否 | — |

#### 5.3.3 提取器

切换类型为提取器时，展示提取器配置区。采用**平台自有数据结构**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| 启用 | switch | 是 | 启用/禁用开关 |
| 提取来源 | select | 是 | `JSON 字段` / `XPath` / `正则捕获` / `两个标记之间` / `完整响应体` / `Groovy 脚本` |
| 表达式 | text | 视来源 | JSONPath / XPath / 正则 / 边界标记（仅部分来源需要） |
| 目标变量名 | text | 是 | 提取结果存入的变量名，后续步骤通过 `${变量名}` 引用 |
| 提取描述 | text | 否 | 用于报告展示的提取说明 |

**来源与表达式的联动关系**：

| 提取来源 | 表达式是否必填 | 表达式说明 | UI 控件 |
| ---- | ---- | ---- | ---- |
| JSON 字段 | 是 | JSONPath，如 `$.data.token` | 输入 JSONPath |
| XPath | 是 | XPath 表达式 | 输入 XPath |
| 正则捕获 | 是 | 正则表达式（含捕获组） | 输入正则 |
| 两个标记之间 | 是 | `左边界\|\|右边界` | 两个输入框 |
| 完整响应体 | 否 | — | 无需输入 |
| Groovy 脚本 | 是 | 脚本内容 | 代码编辑器 |

---

## 6. 实施说明

### 6.1 迁移脚本

新建 DDL 迁移脚本 `server/src/main/resources/db/v1.2.sql`，包含本文档定义的全部公共表（2.1.1–2.1.6）以及其余详细设计文档定义的业务表。脚本随本文档同步修订。

### 6.2 Ryze 依赖引入

在 `server/pom.xml` 中引入 Ryze 框架 Maven 依赖，版本锁定。Ryze 依赖 Java 21+，与平台技术栈一致。

### 6.3 执行引擎线程池配置

执行引擎线程池参数通过 `application.yml` 配置化：

```yaml
api-test:
  executor:
    max-concurrency: 5
    queue-capacity: 100
    thread-name-prefix: api-test-executor-
```

### 6.4 Mock 服务端口

Mock 服务随应用进程运行，通过平台 HTTP 端口或独立端口提供 Mock 响应。端口配置：

```yaml
api-test:
  mock:
    port: 8081  # 独立端口；为空时复用主端口
```

---

**文档结束**
