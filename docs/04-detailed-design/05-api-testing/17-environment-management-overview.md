# 软件测试平台——环境管理详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的**环境管理**进行详细设计，定义环境、默认配置、全局变量、数据源、全局前置/后置处理器的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖需求分册中的环境管理（SRS 3.7.1 / US-API-005）与概要设计对应模块：

- **环境管理**：环境 CRUD、默认配置（http 默认配置）、数据源配置、环境变量、全局前置/后置处理器、环境导入导出；作为接口测试壳页内「项目设置」分组的子模块，页面入口与权限口径见 1.4。

测试场景编排的相关设计见《测试场景详细设计说明书》（`docs/04-detailed-design/05-api-testing/12-test-scenario-overview.md`）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/05-api-testing/10-api-srs-project-settings.md`，1.1 环境管理 / SRS 3.7.1）
- 《概要设计说明书》（`docs/02-high-level-design/02-hld-overview.md`，3.1；`docs/02-high-level-design/05-api-testing/10-hld-api-project-settings.md`）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`，2.2 错误码）
- 《环境管理交互设计》（`docs/05-interaction-design/05-api-testing/11-environment-ui-overview.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

### 1.4 页面入口与权限口径

- **壳路由与子模块**：`/workspace/projects/api-testing`（路由名 `ApiTesting`），项目菜单按 `permissionAny` 中任一接口测试 `*:view` 权限码开放入口。环境管理是壳页左侧侧边栏「项目设置」分组下的子模块（与函数管理、公共组件同组），由侧边栏切换并以 `?tab=environments` 同步记录；刷新或外部改写 query 时按当前可见权限还原，tab 不可见时回落到首个可见子模块；无任何接口测试权限时内容区显示「暂无可用功能模块」。
- **权限码**：前端仅在环境管理子菜单挂 `api-env:view`；服务端按接口校验——环境列表/详情查询、数据源与 HTTP 连接测试、环境导出按 `api-env:view`，环境创建/更新/删除、设为默认、排序、导入按 `api-env:edit`。前端以 `api-env:edit` 控制编辑类操作：行内 [上移]/[下移]/[编辑]/[复制]/[删除] 与 [设为默认] 仅在有编辑权限时展示，下拉中的 [导入环境] 无编辑权限时禁用，[新建]/[复制]/[编辑] 无编辑权限时点击提示「无环境编辑权限」；[导出当前环境] 与列表、详情的查看不受编辑权限限制，无编辑权限时详情面板整体只读。
- **页面结构**：左侧环境列表（首行搜索框 + [新建▾] 分裂按钮，其下为唯一滚动区）＋右侧详情面板。列表项展示名称、「默认」标签（非默认环境悬浮 [设为默认]）、摘要 `N HTTP · N 变量 · N 数据源 · N 处理器`；详情面板吸顶头部（详情态：名称 + 「默认」标签 +「未保存」标记 + [保存全部]；新建态：名称/描述/设为默认 + [取消]/[创建]）与其下五个计数页签：HTTP、变量、数据源、前置处理器、后置处理器；新建态由 [＋ 新建] 或 [复制] 进入，预填源环境整体内容并排到列表末尾。
- **对话框与状态分支**：编辑环境弹窗（名称必填、描述、设为默认）；导入环境弹窗（拖拽/选择 JSON 文件 + 「重名时覆盖」开关，提交后弹窗展示导入结果）。列表态含加载骨架、加载失败重试、空态与搜索无匹配（可清除搜索）；详情态含加载失败重试；新建态有改动时离开（取消、切换选中、再次新建/复制）先确认放弃；新建态无环境 ID，连接测试禁用并提示「创建环境后可测试连接」。

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 环境表（api_environment）

项目级环境配置集合，配置项与 Ryze 配置元件对齐。环境的 HTTP 配置、变量、数据源、前置/后置处理器**全部以 JSONB 聚合存储在主表**（`http_configs` / `variables` / `data_sources` / `processors` 列），不拆分独立子表，随环境整体读写。

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

> **全局环境（预留扩展）**：环境均为项目级（`scope = project`），数据模型与接口保留 `scope` 扩展位，列表与详情不展示类型标识；全局环境的维护入口与切换逻辑待后续版本补充（见 `docs/05-interaction-design/05-api-testing/12-environment-ui-page.md` 1.4）。

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
> 请求方法缺省 GET；响应超时固定 30000ms、连接超时固定 10000ms、跟随重定向开启、SSL 校验开启（配置不含 `default_method`/`timeout_ms`/`connect_timeout_ms`/`follow_redirects`/`verify_ssl` 字段，见 `docs/05-interaction-design/05-api-testing/12-environment-ui-page.md` 1.3）。

#### 2.1.3 环境变量（api_environment.variables）

环境变量以 **JSONB 数组形式存储在环境主表的 `variables` 列**，随环境整体读写。数组元素结构：

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | NOT NULL | 变量名 |
| value | TEXT | NULL | 变量值 |
| description | VARCHAR(500) | NULL | 变量描述 |

> 变量值存明文，前端明文展示，无类型概念（元素不含 `type` 字段）。

#### 2.1.4 数据源（api_environment.data_sources）

数据源以 **JSONB 数组形式存储在环境主表的 `data_sources` 列**，随环境整体读写。数组元素结构：

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| name | VARCHAR(100) | NOT NULL | 数据源名称 |
| refName | VARCHAR(100) | NOT NULL | 引用名称（对应 Ryze `refName`，用于步骤中引用该数据源） |
| driver | VARCHAR(100) | NOT NULL | JDBC 驱动类名；Redis 数据源无需驱动（Ryze 内置客户端），以 `-` 占位满足必填，连接测试按 `redis://` 协议识别 |
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

### 2.2 错误码

环境管理相关错误码（`1000017401`–`1000017410`：`API_ENV_NAME_EXISTS`、`API_ENV_REFERENCED`、`API_DATASOURCE_CONN_FAILED`、`API_ENV_TASK_BOUND`、`API_ENV_NOT_FOUND`、`API_ENV_HTTP_CONFIG_NOT_FOUND`、`API_ENV_DATASOURCE_NOT_FOUND`、`API_ENV_PROCESSOR_NOT_FOUND`、`API_ENV_VARIABLE_NOT_FOUND`、`API_ENV_VARIABLE_EXISTS`）统一登记于《API 测试基础设施详细设计说明书》2.2（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`），本文不重复登记。

---


## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `17-environment-management-overview.md` | 1. 引言（含 1.4 页面入口与权限口径）、2. 数据设计 |
| 环境管理 | `18-environment-management-env.md` | 1. 环境管理、2. 环境配置页、3. 环境删除保护 |
| 全局处理器 | `19-environment-management-processor.md` | 1. 引言、2. 数据设计、3. 接口详细设计、4. 业务逻辑设计、5. 前端设计、6. 实施说明 |
| 变量与 HTTP 配置 | `20-environment-management-variable-http.md` | 1. 环境变量管理、2. HTTP 配置与数据源管理、3. 变量解析优先级、4. 数据源连接池、5. 数据源连接池（实现注意点）、6. 敏感数据加密 |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 对齐实现：补页面入口与权限口径、错误码改为引用基础设施总览、修正分册章节号与 Redis 驱动占位等表述 |
| V1.0 | 2026-10-03 | 分册-章节对照表与交叉引用一致性复检 |
