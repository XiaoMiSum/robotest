# 软件测试平台——（总览分册）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的**接口管理**进行详细设计，定义接口定义、导入的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.2（接口管理）与概要设计第 3.1 章对应模块：

- **接口定义**：接口资产 CRUD、模块组织、请求参数模型、响应示例、引用关系；
- **导入**：文件导入（Swagger/OpenAPI、Postman Collection、HAR、JMeter）、Swagger URL 导入。

快速调试（单请求调试、cURL 命令解析、调试记录管理）详见《快速调试详细设计说明书》（`docs/04-detailed-design/30-quick-debug.md`）。

所有接口测试接口的鉴权、上下文传递沿用平台既有约定（C4），详见《API 测试基础设施详细设计说明书》3.1。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/01-readme.md`，3.1–3.2）
- 《概要设计说明书》（`docs/02-high-level-design/02-high-level-design.md`，3.1）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/01-readme.md`）
- 《快速调试详细设计说明书》（`docs/04-detailed-design/30-quick-debug.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 接口模块说明

接口定义通过 `api_interface.module_id` 字段引用项目级统一模块树（`project_module`），模块树的 DDL、索引与 CRUD 接口详见《项目模块详细设计说明书》（`docs/04-detailed-design/18-project-module.md` 2.1、3.1）。

> 接口管理页面左侧模块树复用 `GET /api/project/modules` 接口，无需独立的模块表。

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
| auth | JSONB | NULL | 认证配置 `{type: none/basic/digest, username, password}`（存储加密） |
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
| source_id | VARCHAR(500) | NOT NULL | 源标识（Swagger operationId / Postman item ID 等） |
| source_name | VARCHAR(500) | NOT NULL | 源名称（接口名/路径） |
| target_type | VARCHAR(20) | NOT NULL | 目标类型：interface / scene |
| target_id | UUID | NOT NULL | 目标对象 ID |
| action | VARCHAR(10) | NOT NULL | 操作：created / updated / skipped |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_imapping_project_source` (project_id, source_type, source_id), `idx_imapping_import` (import_record_id)

#### 2.1.6 接口关注表（api_interface_follow）

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

#### 2.1.7 接口变更历史表（api_interface_change_log）

记录接口定义每次保存产生的变更，支撑详情页「变更历史」视图（每次保存生成一条记录并递增 `change_version`）。

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

接口管理模块复用《API 测试基础设施详细设计说明书》2.2 定义的错误码号段（7001–7099），本模块使用以下错误码：

| 错误码 | 常量名 | 说明 |
| ------ | ------ | ---- |
| 7101 | API_INTERFACE_NOT_FOUND | 接口定义不存在 |
| 7102 | API_INTERFACE_NAME_EXISTS | 接口定义名称重复 |
| 7103 | API_INTERFACE_REFERENCED | 接口定义被引用无法删除 |
| 7105 | API_INTERFACE_VERSION_CONFLICT | 接口已被他人修改（版本冲突，乐观锁） |
| 7010 | API_IMPORT_FORMAT_UNSUPPORTED | 导入格式不支持 |
| 7011 | API_IMPORT_PARSE_FAILED | 导入内容解析失败 |
| 7012 | API_IMPORT_URL_UNREACHABLE | URL 导入目标不可达 |

---


### 4.3 URL 安全策略

Swagger URL 导入的 SSRF 防护采用**配置文件可切换的两级策略**（配置项 `robotest.api-test.import.url-policy`，环境变量 `IMPORT_URL_POLICY`；实测默认 `strict`）。拉取入口统一为 `ImportSourceFetcher`（含 Swagger URL 保存校验、URL 导入/预览、定时任务 `import_swagger`）。

**`strict` 策略（默认，公网 / 云上部署适用）**：

1. **协议白名单**：仅允许 `http` / `https` 协议。
2. **内网地址黑名单**：禁止本地回环、A/B/C 类私网地址、链路本地地址（含云元数据服务地址）与 IPv6 本地回环。
3. **DNS 解析后校验**：解析域名后检查 IP 是否在黑名单范围内，防止 DNS 重绑定攻击。
4. **超时控制**：拉取超时 10 秒，超时返回错误码 7012。

**`intranet` 策略（内网测试环境适用）**：

测试环境（被测系统测试环境、内网 Mock 服务等）通常部署于内网，`strict` 策略会阻断正常使用，此时可切换 `intranet` 策略：

1. **协议白名单**：依然仅允许 `http` / `https` 协议。
2. **放行内网地址**：允许访问本地回环与 A/B/C 类私网地址，适配内网测试环境。
3. **高危地址仍禁止**：链路本地地址（含云元数据服务地址）、任意本地地址、组播地址等在任何策略下均禁止。
4. **路径白名单**：仅允许拉取 Swagger/OpenAPI 文档特征路径，杜绝借此探测内网任意服务：
   - 完全匹配 `{contextPath}/v2/api-docs`、`{contextPath}/v3/api-docs`；
   - 完全匹配 `{contextPath}/swagger.json`、`{contextPath}/openapi.json`、`{contextPath}/swagger.yaml`、`{contextPath}/openapi.yaml`；
   - 以 `.json` / `.yaml` / `.yml` 结尾的任意路径（通用 OpenAPI 文件）。
   - 其余路径一律拒绝（错误码 7012）。
5. **DNS 解析后复核**：解析域名后逐 IP 复核第 3 条高危地址，防止 DNS 重绑定指向云元数据服务。
6. **超时控制**：同 `strict`，10 秒超时返回 7012。

> **安全权衡**：`intranet` 策略放宽了目标地址范围以满足内网测试场景，但通过「路径白名单 + 云元数据/组播恒禁用」将访问面收敛到 Swagger 文档本身，避免将服务端变成内网任意资产扫描器。公网 / 云上部署应保持默认 `strict` 策略，除非确认全部被测环境仅存在于内网。


### 6.3 实施边界

- **协议范围**：本期仅 http；`protocol` 字段保留并缺省 `http`，jdbc 协议的编辑区与执行随测试场景模块（梯队三）提供。
- **解析依赖**：引入 `io.swagger.parser.v3:swagger-parser`（Apache-2.0）解析 OpenAPI 2.0/3.0 的 JSON/YAML（含 `$ref`）；cURL 由前端 `curlParser` 解析（复用快速调试），后端不引入 cURL 解析依赖。
- **编辑器裁剪**：请求体对齐快速调试，仅 `none / x-www-form-urlencoded / raw`（raw 带 subtype + 深色编辑器）；接口级 `验证器、提取器` 在编辑器渲染（复用场景步骤的共享面板 `ValidatorsExtractorsPanes`），**本期仅定义存储与回显**，不带入请求执行链路（执行消费留待测试场景模块）。
- **DB 迁移**：`api_interface` 表新增 `validators / extractors` 两列（JSONB NOT NULL DEFAULT '[]'）为纯增量列，需按 §5 迁移说明提供 `ALTER TABLE ... ADD COLUMN` 并评估索引（两列不作查询过滤条件，无需新增索引，避免超 C9 单表索引上限）。
- **乐观锁口径**：版本冲突以业务错误码 7105 表达（框架统一 Result 封装），前端按错误码识别提示刷新。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `27-interface-management-overview.md` | 前言、1. 引言、2. 数据设计、4.3 URL 安全策略、6.3 实施边界 |
| 接口定义管理 | `28-interface-management-definition.md` | 3.1 接口定义管理、5.1 接口模块树组件、5.2 接口预览页 |
| 导入 | `29-interface-management-import.md` | 3.2 导入接口、4.1 导入格式解析、4.4 cURL 解析规则、5.3 导入弹窗、6.1 文件解析库选型、6.2 增量导入策略 |
