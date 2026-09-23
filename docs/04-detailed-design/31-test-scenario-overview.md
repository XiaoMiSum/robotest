# 软件测试平台——测试场景详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的**测试场景编排**进行详细设计，定义场景、步骤、参数、处理器、验证器、提取器的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.4（测试场景）、3.9（前置与后置处理器）、3.10（验证器）、3.11（内置函数与变量引用）与概要设计第 3.1 章对应模块：

- **测试场景**：场景 CRUD、步骤编排（http/jdbc 取样器）、参数管理、处理器配置、复制与链接引用、执行记录与变更历史；
- **处理器与验证器**：前置处理器、后置处理器、验证器、提取器的配置模型与执行语义。

环境管理（SRS 3.7.1）的相关设计见《环境管理详细设计说明书》（`docs/04-detailed-design/01-readme.md`）。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/01-readme.md`，3.4、3.9–3.11）
- 《概要设计说明书》（`docs/02-high-level-design/02-high-level-design.md`，3.1、4.2–4.3）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/01-readme.md`）
- Ryze 多协议测试框架文档（`https://xiaomisum.github.io/ryze/`）

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 场景模块说明

测试场景通过 `api_scene.module_id` 字段引用项目级统一模块树（`project_module`），模块树的 DDL、索引与 CRUD 接口详见《项目模块详细设计说明书》（`docs/04-detailed-design/18-project-module.md` 2.1、3.1）。

> 测试场景页面左侧模块树复用 `GET /api/project/modules` 接口，无需独立的模块表。模块名同级唯一校验（错误码 7051）由统一模块管理提供。

#### 2.1.2 测试场景表（api_scene）

场景为多步骤编排的自动化测试单元，归属场景模块树组织。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| module_id | UUID | NULL | 归属模块（project_module.id，null 为未分组，详见《项目模块详细设计说明书》） |
| name | VARCHAR(200) | NOT NULL | 场景名称 |
| description | TEXT | NULL | 场景描述 |
| environment_id | UUID | NULL | 默认执行环境（api_environment.id，可选，详见《环境管理详细设计说明书》） |
| priority | VARCHAR(2) | NULL | 优先级：P0/P1/P2/P3（字母大写），NULL 表示未设置，见 3.1.3 |
| status | VARCHAR(10) | NOT NULL DEFAULT 'draft' | 状态：draft（草稿）/ published（已发布），新增/编辑时可选保存为草稿或发布 |
| variables | JSONB | NOT NULL DEFAULT '[]' | 场景变量列表 `[{name, value, description}]`（唯一权威源，随场景整体读写，结构见 2.1.4） |
| processors | JSONB | NOT NULL DEFAULT '[]' | 场景级处理器列表（元素含 `type` 为 `pre`/`post` 区分前置/后置） |
| steps | JSONB | NOT NULL DEFAULT '[]' | 步骤聚合列表（合并自原 api_scene_step 与 api_scene_step_variable，结构见 2.1.3） |
| change_version | INT | NOT NULL DEFAULT 1 | 变更版本号（每次保存递增，乐观锁） |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_scene_project` (project_id), `idx_scene_module` (module_id)

> 合计 2 个索引，符合 C9。

#### 2.1.3 场景步骤（api_scene.steps JSONB）

场景步骤即取样器（http/jdbc 两种类型），支持从接口定义导入系统请求、创建自定义请求。步骤与步骤级变量不再单独建表，统一合并到 `api_scene.steps` JSONB 列：每步为数组元素，步骤级变量内嵌为步骤的 `variables` 子数组。

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| id | UUID | 步骤 ID（应用层生成） |
| name | VARCHAR（JSONB 内文本） | 步骤名称 |
| stepType | VARCHAR | 步骤类型：http / jdbc |
| sortOrder | INT | 排序序号 |
| enabled | BOOLEAN | 启用状态 |
| sourceType | VARCHAR | 来源类型：system（系统请求）/ custom（自定义）/ copy（复制）/ link（链接引用） |
| sourceId | UUID | 来源对象 ID（接口定义 ID） |
| sourceInterfaceId | UUID | 来源接口定义 ID（卡片来源 tag 与跳转用） |
| sourceInterfaceName | VARCHAR | 来源接口名称冗余 |
| sourceSnapshot | JSONB | 来源快照（链接引用时记录创建时的快照，用于源被删除后展示） |
| requestConfig | JSONB | 请求配置（http: method/url/headers/params/body；jdbc: datasourceId/sql） |
| processors | JSONB | 步骤级处理器列表 |
| validators | JSONB | 验证器列表 |
| extractors | JSONB | 提取器列表 |
| variables | JSONB | 步骤级变量列表 `[{id, name, value, source, description, sortOrder}]`（合并自原 api_scene_step_variable；source：custom） |

> 步骤数组视作整体读写（保存时全量覆盖 `steps` 列），不再维护步骤独立索引；步骤级变量与 `request_config` 中的 `query_params` 的区别：后者是请求的固定 Query 参数，前者是可配置的变量，允许在执行时传入不同值。

#### 2.1.4 场景变量（api_scene.variables JSONB）

场景变量用于步骤间的变量传递，以 **JSONB 数组形式存储在场景主表的 `variables` 列**，随场景整体创建/更新（聚合提交），不再单独建表。数组元素结构：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| name | VARCHAR(100) | 变量名（`${name}` 引用，仅字母/数字/下划线且不得重名） |
| value | TEXT | 变量值（支持变量引用与内置函数） |
| description | VARCHAR(500) | 变量描述 |

> `variables` 列为场景级变量唯一权威源，随场景详情返回、随场景整体写入（见 3.5.1），列表页直接复用场景 JSONB 列，不再维护独立变量表与冗余快照。

### 2.2 错误码补充

| 错误码 | 常量名 | 说明 |
| ------ | ------ | ---- |
| 7201 | API_SCENE_NOT_FOUND | 场景不存在 |
| 7202 | API_SCENE_STEP_NOT_FOUND | 场景步骤不存在 |
| 7203 | API_SCENE_REFERENCED | 场景被定时任务引用无法删除 |
| 7204 | API_LINK_SOURCE_MISSING | 链接引用源不存在 |
| 7210 | API_SCENE_SETTING_INVALID | 场景基础信息校验失败（如优先级取值非法） |

---


### 2.3 复制与链接引用语义

详见《概要设计说明书》4.3。实现要点：

**复制（copy）**：
- 创建步骤的完整独立副本（`source_type = "copy"`）。
- 后续修改互不影响。
- 源被删除后，复制步骤仍可编辑（`source_id` 置灰无法跳转）。

**链接引用（link）**：
- 步骤内容（`request_config`、`validators`、`extractors`）跟随源变化同步更新（`source_type = "link"`）。
- 当前场景中的 `enabled` 状态、参数值可独立调整。
- 源被删除时，引用步骤置灰并仅支持「移除」操作。
- 引用步骤的 `source_snapshot` 记录创建时的快照，用于源被删除后的展示。

**刷新机制**：链接引用步骤在执行时实时拉取源最新内容；若拉取失败（源已删除），使用 `source_snapshot` 快照降级执行。


### 2.4 配置继承与合并

执行时，平台将环境配置、场景配置、步骤配置按优先级合并后生成 Ryze 运行时配置：

```
环境默认配置 < 场景级配置 < 步骤级配置
```

同名配置项以高优先级覆盖低优先级。合并范围包括：
- 请求头（env.headers + scene.processors + step.headers）
- 超时配置（步骤级 timeout；为空使用固定默认值：响应 30000ms / 连接 10000ms，见环境管理详设 2.1.2）
- 变量（env.variables → scene.variables → step.variables → step.extractors 产出）
- 处理器 `ref` 缺省回退（场景级 HTTP 前置/后置处理器）：元素 `config` 未显式指定 `ref`、且无 `base_url`/绝对地址时，注入场景关联环境默认 http 配置的 `refName`（`isDefault=true` 优先，否则取首条，见《环境管理详细设计说明书》2.1.2），语义与步骤取样器一致；避免引擎以空主机名拼出无效地址

---


### 2.5 链接引用刷新策略

链接引用步骤在执行前实时拉取源最新内容。拉取逻辑：
1. 根据 `source_id` 查询源对象（接口定义）。
2. 若源存在，使用源最新内容覆盖步骤的 `request_config`、`validators`、`extractors`。
3. 若源不存在（已删除），使用 `source_snapshot` 快照降级执行。
4. 执行结果中标注「源已删除，使用快照」。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `31-test-scenario-overview.md` | 前言、1. 引言、2. 数据设计、2.3 复制与链接引用语义、2.4 配置继承与合并、2.5 链接引用刷新策略 |
| 场景管理 | `32-test-scenario-management.md` | 3.1 场景管理 |
| 步骤与验证器提取器 | `33-test-scenario-step.md` | 3.3 步骤管理、3.10 步骤复制、3.12 从全局资产引入、4.2 验证器配置模型、4.3 提取器配置模型、4.4 请求配置、5.1 场景编排器 |
| 变量体系 | `34-test-scenario-variable.md` | 3.4 步骤级变量管理、3.5 场景变量管理、4.1 变量引用与内置函数、6.1 Ryze 变量映射 |
| 执行与历史 | `35-test-scenario-execution.md` | 3.6 场景执行与单步骤调试、3.11 执行历史与变更历史 |
