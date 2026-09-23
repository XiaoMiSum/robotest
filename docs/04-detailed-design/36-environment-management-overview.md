# 软件测试平台——（总览分册）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的**环境管理**进行详细设计，定义环境、默认配置、全局变量、数据源、全局前置/后置处理器的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.7.1（环境管理）与概要设计第 3.1 章对应模块：

- **环境管理**：环境 CRUD、默认配置（http 默认配置）、数据源配置、全局变量、全局前置/后置处理器、环境导入导出。

测试场景编排（SRS 3.4、3.9–3.11）的相关设计见《测试场景详细设计说明书》（`docs/04-detailed-design/01-readme.md`）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/01-readme.md`，3.5）
- 《概要设计说明书》（`docs/02-high-level-design/02-high-level-design.md`，3.1）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/01-readme.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 环境表（api_environment）

项目级环境配置集合，配置项与 Ryze 配置元件对齐。环境的 HTTP 配置、变量、数据源、前置/后置处理器**全部以 JSONB 聚合存储在主表**（`http_configs` / `variables` / `data_sources` / `processors` 列），不再拆分独立子表，随环境整体读写。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目（project 维度，项目模块树详见《项目模块详细设计说明书》） |
| name | VARCHAR(100) | NOT NULL | 环境名称（如「测试环境」「预发环境」） |
| description | VARCHAR(500) | NULL | 环境描述 |
| scope | VARCHAR(10) | NOT NULL DEFAULT 'project' | 环境归属范围：project（项目级）/ global（全局级，预留扩展） |
| is_default | BOOLEAN | NOT NULL DEFAULT FALSE | 是否默认环境（项目内唯一） |
| sort_order | INT | NOT NULL DEFAULT 0 | 排序序号 |
| http_configs | JSONB | NOT NULL DEFAULT '[]' | HTTP 配置数组 `[{name, refName, baseUrl, headers, isDefault}]`，详见 2.1.2 |
| variables | JSONB | NOT NULL DEFAULT '[]' | 环境变量数组 `[{name, value, description}]`，详见 2.1.3 |
| data_sources | JSONB | NOT NULL DEFAULT '[]' | 数据源数组 `[{name, refName, driver, url, connectionProperties, maxPoolSize, isDefault}]`，详见 2.1.4 |
| processors | JSONB | NOT NULL DEFAULT '[]' | 处理器数组 `[{processorType, name, config, sortOrder, enabled}]`，详见 2.1.5 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_env_project` (project_id)

> **全局环境（预留扩展）**：环境均为项目级（`scope = project`），界面与接口为全局环境保留扩展位（类型徽标「全局」样式与展示位），维护入口与切换逻辑待后续版本补充（见 `docs/05-interaction-design/01-readme.md` 2.4）。

#### 2.1.2 HTTP 配置（api_environment.http_configs）

环境的 HTTP 配置以 **JSONB 数组形式存储在环境主表的 `http_configs` 列**，支持多个（如：内部系统、外部系统、第三方 API 等不同目标系统的 HTTP 配置），随环境整体创建/更新。

数组元素结构：

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | NOT NULL | 配置名称（如：「内部 API」「第三方支付」） |
| refName | VARCHAR(100) | NOT NULL | 引用名称（对应 Ryze `refName`，用于步骤中引用该配置） |
| baseUrl | VARCHAR(2000) | NOT NULL | Base URL（如：`https://api.internal.com`） |
| headers | JSON | NOT NULL | 默认请求头 `[{key, value, enabled}]` |
| isDefault | BOOLEAN | NOT NULL DEFAULT FALSE | 是否默认 HTTP 配置（同一环境内至多一个） |

> 每个环境至少有一个 HTTP 配置（创建环境时自动生成默认配置）。步骤中通过 HTTP 配置 `refName` 关联使用哪个 HTTP 配置；步骤未指定时使用该环境的默认 HTTP 配置（`isDefault = true`，若环境内未设默认则取第一条）。
>
> 请求方法缺省 GET；响应超时固定 30000ms、连接超时固定 10000ms、跟随重定向开启、SSL 校验开启（`default_method`/`timeout_ms`/`connect_timeout_ms`/`follow_redirects`/`verify_ssl` 字段已移除，见 `docs/05-interaction-design/01-readme.md` 2.3）。

#### 2.1.3 环境变量（api_environment.variables）

环境变量以 **JSONB 数组形式存储在环境主表的 `variables` 列**，随环境整体读写。数组元素结构：

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | NOT NULL | 变量名 |
| value | TEXT | NULL | 变量值 |
| description | VARCHAR(500) | NULL | 变量描述 |

> 变量值存明文，前端明文展示，无类型概念（`type` 字段已移除）。

#### 2.1.4 数据源（api_environment.data_sources）

数据源以 **JSONB 数组形式存储在环境主表的 `data_sources` 列**，随环境整体读写。数组元素结构：

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | NOT NULL | 数据源名称 |
| refName | VARCHAR(100) | NOT NULL | 引用名称（对应 Ryze `refName`，用于步骤中引用该数据源） |
| driver | VARCHAR(100) | NOT NULL | JDBC 驱动类名；Redis 数据源无需驱动（Ryze 内置客户端），存空串 |
| url | VARCHAR(500) | NOT NULL | JDBC 连接 URL（用户名密码直接写入 URL）；Redis 为 `redis://[password@]host:port/db` 格式，按协议识别类型 |
| connectionProperties | JSON | NOT NULL DEFAULT '{}' | 附加连接参数 |
| maxPoolSize | INT | NOT NULL DEFAULT 5 | 连接池最大连接数 |
| isDefault | BOOLEAN | NOT NULL DEFAULT FALSE | 是否默认数据源（同一环境内至多一个） |

#### 2.1.5 处理器（api_environment.processors）

前置/后置处理器以 **JSONB 数组形式存储在环境主表的 `processors` 列**，随环境整体读写。数组元素结构：

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| processorType | VARCHAR(20) | NOT NULL | 处理器类型：preprocessor / postprocessor |
| name | VARCHAR(100) | NOT NULL | 处理器名称 |
| config | JSON | NOT NULL | 处理器配置 |
| sortOrder | INT | NOT NULL DEFAULT 0 | 排序序号 |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE | 启用状态 |

### 2.2 错误码补充

| 错误码 | 常量名 | 说明 |
| ------ | ------ | ---- |
| 7401 | API_ENV_NAME_EXISTS | 环境名称重复 |
| 7402 | API_ENV_REFERENCED | 环境被场景引用无法删除 |
| 7403 | API_DATASOURCE_CONN_FAILED | 数据源连接测试失败 |
| 7404 | API_ENV_TASK_BOUND | 环境被定时任务绑定无法删除 |
| 7405 | API_ENV_NOT_FOUND | 环境不存在或已删除 |
| 7410 | API_ENV_VARIABLE_EXISTS | 变量名重复 |

---


## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `36-environment-management-overview.md` | 前言、1. 引言、2. 数据设计 |
| 环境管理 | `37-environment-management-env.md` | 3.1 环境管理、5.1 环境配置页、6.3 环境删除保护 |
| 全局处理器 | `38-environment-management-processor.md` | 3.2 全局处理器管理 |
| 变量与 HTTP 配置 | `39-environment-management-variable-http.md` | 3.3 环境变量管理、3.4 HTTP 配置与数据源管理、4.1 变量解析优先级、4.2 数据源连接池、6.1 数据源连接池、6.2 敏感数据加密 |
