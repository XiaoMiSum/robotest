# 软件测试平台——接口管理详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的**接口管理**进行详细设计，定义接口定义、导入的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖需求分册《接口管理》与概要设计 3.1 模块总览中的「接口管理」模块：

- **接口定义**：接口资产 CRUD、复制、批量移动/删除、启用/停用、模块组织（复用项目统一模块树）、请求参数模型、响应示例、引用计数（删除拦截依据）、关注与「我关注的 / 我创建的」视图、变更历史；
- **导入**：Swagger URL 导入（先预览解析结果再写入）、cURL 命令导入（前端解析后提交解析结果）。

快速调试（单请求调试、cURL 命令解析、调试记录管理）详见《快速调试详细设计说明书》（`docs/04-detailed-design/05-api-testing/11-quick-debug.md`）。

所有接口测试接口的鉴权、上下文传递沿用平台既有约定（C4），详见《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.3。

### 1.3 参考资料

- 《接口测试需求规格说明书·接口管理》（`docs/01-requirements/05-api-testing/04-api-srs-interface-management.md`，1. 接口管理）
- 《概要设计说明书》（`docs/02-high-level-design/02-hld-overview.md`，3.1 模块总览）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）
- 《快速调试详细设计说明书》（`docs/04-detailed-design/05-api-testing/11-quick-debug.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

### 1.4 页面入口与权限口径

- **壳路由与子模块**：`/workspace/projects/api-testing`（路由名 `ApiTesting`），项目菜单按 `permissionAny` 中任一接口测试 `*:view` 权限码开放入口；接口管理是壳页内的子模块，由侧边菜单切换并以 `?tab=` 同步记录（接口管理为 `?tab=interfaces`），刷新或外部改写 query 时按当前可见权限还原；无任何接口测试权限时壳页显示「暂无可用功能模块」。
- **权限码**：前端仅在接口管理子菜单挂 `api-interface:view`，页内按钮不做前端权限隐藏；写操作（创建 / 更新 / 导入 / 批量移动 / 启停 / 关注）由服务端按 `api-interface:edit` 校验，删除按 `api-interface:delete` 校验。
- **页面结构与直链**：卡片式多 Tab 工作区（固定不可关闭的列表 Tab + 每个编辑器一个可关闭 Tab）、头部常驻「导入 / 新建接口」按钮，以及 `?tab=interfaces&interfaceId=<id>`、`?tab=interfaces&action=create[&moduleId=|&copyFrom=]` 的直链恢复与旧独立路由 `/workspace/projects/interfaces/:interfaceId`（`new` 转 `action=create`）的重定向，详见《接口定义管理》（`docs/04-detailed-design/05-api-testing/09-interface-management-definition.md` 3.1）。

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 接口模块说明

接口定义通过 `api_interface.module_id` 字段引用项目级统一模块树（`project_module`），模块树的 DDL、索引与 CRUD 接口详见《项目模块详细设计说明书》（`docs/04-detailed-design/02-project-module.md` 2.1、3.1）。

> 接口管理页面左侧模块树复用 `GET /api/project/modules?assetType=interface` 接口，无需独立的模块表。

#### 2.1.2 接口定义表（api_interface）

接口定义为核心数据底座，承载协议、方法、路径、请求参数模型与响应示例。支持从导入或手工创建。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| module_id | UUID | NULL | 归属模块（project_module.id，null 为未分组，详见《项目模块详细设计说明书》） |
| name | VARCHAR(200) | NOT NULL | 接口名称 |
| protocol | VARCHAR(20) | NOT NULL DEFAULT 'http' | 协议：http / jdbc |
| method | VARCHAR(10) | NULL | HTTP 方法（GET/POST/PUT/PATCH/DELETE） |
| path | VARCHAR(500) | NULL | 请求路径（不含域名，如 `/api/users`） |
| description | TEXT | NULL | 接口描述 |
| headers | JSONB | NOT NULL DEFAULT '[]' | 默认请求头 `[{key, value, enabled}]` |
| body_type | VARCHAR(20) | NULL | 默认请求体类型（编辑器已对齐快速调试：none / x-www-form-urlencoded / raw；落库子域映射：raw+json→json、raw+其余→raw、urlencoded→form） |
| body | JSONB | NULL | 默认请求体内容 |
| query_params | JSONB | NOT NULL DEFAULT '[]' | 默认 Query 参数 `[{key, value, enabled}]` |
| rest_params | JSONB | NOT NULL DEFAULT '[]' | REST 路径参数 `[{key, value}]`（对应路径占位符 `{id}`） |
| auth | JSONB | NULL | 认证配置 `{type, ...}`：`none` / `bearer`（`token`）/ `apiKey`（`apiKeyName`、`apiKeyValue`）/ `basic`（`username`、`password`），`digest` 在编辑器中禁用（存储加密） |
| validators | JSONB | NOT NULL DEFAULT '[]' | 接口级验证器列表（结构对齐场景步骤：`[{id, name, enabled, target, condition, expected, expression}]`） |
| extractors | JSONB | NOT NULL DEFAULT '[]' | 接口级提取器列表（结构对齐场景步骤：`[{id, name, enabled, source, expression, variableName}]`） |
| status | VARCHAR(10) | NOT NULL DEFAULT 'enabled' | 启用状态：enabled / disabled |
| created_by | UUID | NULL | 创建人（列表页「我创建的」视图过滤依据） |
| change_version | INT | NOT NULL DEFAULT 1 | 变更版本号（每次保存递增，乐观锁） |
| response_example | JSONB | NULL | 响应示例 `{status, headers, body}` |
| reference_count | INT | NOT NULL DEFAULT 0 | 被引用计数（场景/Mock 引用） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_interface_project` (project_id), `idx_interface_module` (module_id), `idx_interface_path_method` (project_id, path, method), `idx_interface_creator` (project_id, created_by)

> `idx_interface_path_method` 支撑 Mock 匹配与导入去重时按路径+方法查询；`idx_interface_creator` 支撑「我创建的」视图过滤。合计 4 个索引，符合 C9。

#### 2.1.3 导入映射表（api_import_mapping）

记录导入时的源数据与平台对象的映射关系，支持增量更新时的去重与覆盖。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| import_record_id | UUID | NOT NULL | 关联导入记录（api_import_record.id） |
| source_type | VARCHAR(30) | NOT NULL | 源类型：swagger_operation / curl_operation |
| source_id | VARCHAR(500) | NOT NULL | 源标识（Swagger 优先 `operationId`，缺失则 `{method}:{path}`；cURL 为 `{method}:{path}`） |
| source_name | VARCHAR(500) | NOT NULL | 源名称（接口名/路径） |
| target_type | VARCHAR(20) | NOT NULL | 目标类型：interface（接口导入只写入 interface） |
| target_id | UUID | NOT NULL | 目标对象 ID |
| action | VARCHAR(10) | NOT NULL | 操作：created / updated（导入预览阶段为 create / update / skip，skip 不落映射） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_imapping_project_source` (project_id, source_type, source_id), `idx_imapping_import` (import_record_id)

#### 2.1.4 接口关注表（api_interface_follow）

记录用户对接口定义的关注关系，支撑列表页「我关注的」视图切换。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| interface_id | UUID | NOT NULL | 归属接口定义（api_interface.id） |
| user_id | UUID | NOT NULL | 关注用户 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_ifollow_interface_user` (interface_id, user_id), `idx_ifollow_user` (user_id)

> 关注关系按用户维度记录，取消关注即逻辑删除对应记录。

#### 2.1.5 接口变更历史表（api_interface_change_log）

记录接口定义每次保存产生的变更（每次保存生成一条记录并递增 `change_version`），支撑变更历史查询接口 `GET /api/project/interfaces/{id}/change-logs`。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| interface_id | UUID | NOT NULL | 归属接口定义（api_interface.id） |
| change_version | INT | NOT NULL | 该次保存后的版本号（与 api_interface.change_version 对应） |
| action | VARCHAR(20) | NOT NULL | 动作：create / update / copy / import / status |
| summary | VARCHAR(500) | NULL | 变更摘要（字段级差异简述或导入来源） |
| operator_id | UUID | NULL | 操作人 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_ichangelog_interface` (interface_id, change_version)

### 2.2 错误码补充

接口管理模块使用《API 测试基础设施详细设计说明书》2.2 中已登记的十位错误码，本模块使用以下错误码：

| 错误码 | 常量名 | 说明 |
| --- | --- | --- |
| 1000017101 | API_INTERFACE_NOT_FOUND | 接口定义不存在 |
| 1000017102 | API_INTERFACE_NAME_EXISTS | 接口定义名称重复 |
| 1000017103 | API_INTERFACE_REFERENCED | 接口定义被引用无法删除 |
| 1000017105 | API_INTERFACE_VERSION_CONFLICT | 接口已被他人修改（版本冲突，乐观锁） |
| 1000017010 | API_IMPORT_FORMAT_UNSUPPORTED | 导入格式不支持 |
| 1000017011 | API_IMPORT_PARSE_FAILED | 导入内容解析失败 |
| 1000017012 | API_IMPORT_URL_UNREACHABLE | URL 导入目标不可达 |

---


### 2.3 URL 安全策略

Swagger URL 导入的 SSRF 防护采用**配置文件可切换的两级策略**（配置项 `robotest.api-test.import.url-policy`，环境变量 `IMPORT_URL_POLICY`；`server/src/main/resources/application-prod.yaml` 缺省 `strict`，`server/src/main/resources/application.yaml` 与 `server/src/main/resources/application-dev.yaml` 缺省 `intranet`）。拉取入口统一为 `ImportSourceFetcher`（含 Swagger URL 保存校验、URL 导入/预览、定时任务 `import_swagger`），校验顺序：协议白名单 → 端口白名单 → 按策略复核目标地址 → DNS 解析后逐 IP 复核 → 10 秒超时。

**`strict` 策略（公网 / 云上部署适用）**：

1. **协议白名单**：仅允许 `http` / `https` 协议。
2. **端口白名单**：写明端口时须落在 `robotest.api-test.import.allowed-ports`（环境变量 `IMPORT_ALLOWED_PORTS`，默认 80 / 443 / 8080 / 8443）内；未写明端口按协议默认端口处理。
3. **内网地址黑名单**：禁止本地回环、A/B/C 类私网地址、链路本地地址（含云元数据服务地址）、任意本地地址与组播地址（含 IPv6 本地回环）。
4. **DNS 解析后校验**：解析域名后逐 IP 检查是否在黑名单范围内，防止 DNS 重绑定攻击。
5. **超时控制**：拉取超时 10 秒，超时返回错误码 1000017012。

**`intranet` 策略（内网测试环境适用）**：

测试环境（被测系统测试环境、内网 Mock 服务等）通常部署于内网，`strict` 策略会阻断正常使用，此时可切换 `intranet` 策略：

1. **协议白名单**：依然仅允许 `http` / `https` 协议。
2. **端口校验**：不套用端口白名单，但超出 1–65535 的非法端口在任何策略下均拒绝。
3. **放行内网地址**：允许访问本地回环与 A/B/C 类私网地址，适配内网测试环境。
4. **高危地址仍禁止**：链路本地地址（含云元数据服务地址）、任意本地地址、组播地址等在任何策略下均禁止。
5. **路径白名单**：仅允许拉取 Swagger/OpenAPI 文档特征路径，杜绝借此探测内网任意服务：
   - 路径以 `/v2/api-docs`、`/v3/api-docs`、`/swagger.json`、`/swagger.yaml`、`/swagger.yml`、`/openapi.json`、`/openapi.yaml`、`/openapi.yml`、`/swagger-resources`、`/swagger-ui.json`、`/swagger-ui.html` 结尾（等价于 `{contextPath}/<上述路径>`，支持任意 contextPath 前缀）；
   - 以 `.json` / `.yaml` / `.yml` 结尾的任意路径（通用 OpenAPI 文件）。
   - 其余路径一律拒绝（错误码 1000017012）。
6. **DNS 解析后复核**：解析域名后逐 IP 复核第 4 条高危地址，防止 DNS 重绑定指向云元数据服务。
7. **超时控制**：同 `strict`，10 秒超时返回 1000017012。

> **安全权衡**：`intranet` 策略放宽了目标地址范围以满足内网测试场景，但通过「路径白名单 + 云元数据/组播恒禁用」将访问面收敛到 Swagger 文档本身，避免将服务端变成内网任意资产扫描器。公网 / 云上部署应保持 `prod` 配置缺省的 `strict` 策略，除非确认全部被测环境仅存在于内网。


### 2.4 实施边界

- **协议范围**：本期仅 http；`protocol` 字段保留并缺省 `http`，jdbc 协议的编辑区与执行随测试场景模块（梯队三）提供。
- **解析依赖**：引入 `io.swagger.parser.v3:swagger-parser`（Apache-2.0）解析 OpenAPI 2.0/3.0 的 JSON/YAML（含 `$ref`）；cURL 由前端 `curlParser` 解析（复用快速调试），后端不引入 cURL 解析依赖。
- **编辑器裁剪**：请求体对齐快速调试，仅 `none / x-www-form-urlencoded / raw`（raw 带 subtype + 深色编辑器）；接口级 `验证器、提取器` 在编辑器渲染（复用场景步骤的共享面板 `ValidatorsExtractorsPanes`），**本期仅定义存储与回显**，不带入请求执行链路（执行消费留待测试场景模块）。
- **DB 迁移**：`api_interface` 表新增 `validators / extractors` 两列（JSONB NOT NULL DEFAULT '[]'）为纯增量列，需提供 `ALTER TABLE api_interface ADD COLUMN ...` 迁移（全量结构随 `server/src/main/resources/db/schema.sql` 维护）并评估索引（两列不作查询过滤条件，无需新增索引，避免超 C9 单表索引上限）。
- **乐观锁口径**：版本冲突以业务错误码 1000017105 表达（框架统一 Result 封装），前端按错误码识别提示刷新。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `08-interface-management-overview.md` | 1. 引言、2. 数据设计（2.3 URL 安全策略、2.4 实施边界） |
| 接口定义管理 | `09-interface-management-definition.md` | 1. 接口定义管理、2. 接口模块树组件、3. 接口管理页与编辑器 |
| 导入 | `10-interface-management-import.md` | 1. 导入接口、2. 导入格式解析、3. cURL 解析规则、4. 导入弹窗、5. 解析库选型、6. 增量导入策略 |
| 快速调试 | `11-quick-debug.md` | 1. 引言、2. 数据设计、3. 接口详细设计、4. 业务逻辑设计、5. 前端设计 |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-09-23 | 初始版本 |
| V1.0 | 2026-10-02 | 对齐实现：范围改为 Swagger URL/cURL 导入，补页面入口与权限口径，URL 安全策略补端口白名单与配置缺省，修正引用与对照表章节号 |
| V1.0 | 2026-10-03 | 分册-章节对照表与交叉引用一致性复检 |
