# 软件测试平台——Mock服务详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的 **Mock 服务**进行详细设计，定义 Mock 定义、匹配规则、响应、调试、访问的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。接口测试报告（报告查看、分享、清理）相关设计见《测试报告详细设计说明书》（`docs/04-detailed-design/05-api-testing/28-test-report-overview.md`）。

### 1.2 范围

覆盖需求分册中的 Mock 服务（SRS 3.3 / US-API-003）与概要设计对应模块：

- **Mock 服务**：Mock 定义 CRUD、匹配规则、响应定义、调试、命中统计、访问地址管理；作为接口测试壳页左侧侧边栏的主功能子模块，页面入口与权限口径见 1.4；
- **Mock 访问**：免登录响应服务、变量解析、命中统计、访问日志。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/05-api-testing/05-api-srs-mock-service.md`，1. Mock 服务 / SRS 3.3）
- 《概要设计说明书》（`docs/02-high-level-design/05-api-testing/05-hld-api-mock-service.md`，2. 模块划分）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`，2.2 错误码、2.6 Mock 服务端口）
- 《Mock 服务交互设计》（`docs/05-interaction-design/05-api-testing/14-mock-ui-overview.md`）
- 《测试报告详细设计说明书》（`docs/04-detailed-design/05-api-testing/28-test-report-overview.md`）

### 1.4 页面入口与权限口径

- **壳路由与子模块**：`/workspace/projects/api-testing`（路由名 `ApiTesting`），项目菜单按 `permissionAny` 中任一接口测试 `*:view` 权限码开放入口。Mock 服务是壳页左侧侧边栏主功能区子模块（与快速调试、接口管理、测试场景、测试报告、定时任务同列，不在「项目设置」分组内），菜单项标签「Mock 服务」、图标 `Cpu`，由侧边栏切换并以 `?tab=mocks` 同步记录；刷新或外部改写 query 时按当前可见权限还原，tab 不可见时回落到首个可见子模块；无任何接口测试权限时内容区显示「暂无可用功能模块」。
- **权限码**：前端仅在 Mock 服务子菜单挂 `api-mock:view`，页内按钮不做前端权限隐藏；服务端按接口校验——Mock 列表/详情查询与调试执行按 `api-mock:view` 校验，创建、从接口定义创建、更新、启停、批量启停、删除按 `api-mock:edit` 校验。
- **页面结构**：卡片列表页。顶部工具栏左侧为名称或路径搜索框、状态筛选（已启用 / 已停用）、[查询]、[重置]，右侧常驻 [新建 Mock]，选中行后追加 [批量启用] / [批量停用]；表格列为多选框、名称、方法标签、路径、状态码、优先级、启停开关、命中次数、最后命中与操作列（[编辑]、[调试]、[地址]、[复制]，「更多」下拉含 [重置命中]、[删除]）；底部分页提供总数、页大小（10 / 20 / 50）与翻页。
- **对话框与状态分支**：编辑抽屉（640px，标题按场景为「新建 Mock / 编辑 Mock / 复制 Mock」，分「基本信息 / 匹配条件 / 响应定义」三区；名称与请求路径必填，状态码限 100–599、延迟限 0–60000ms，匹配条件类型为请求头 / Query 参数 / 请求体（JSONPath）；复制模式预填源规则全部配置、名称追加「- 副本」并默认停用，避免与源规则地址冲突）；调试抽屉（560px，请求头支持 JSON 或 `Header: Value` 逐行输入、请求体按 JSON 解析，[发送调试请求] 展示状态码、耗时、响应头与响应体）；地址弹窗（展示免登录访问地址与需携带的请求头，[复制地址] 一键复制）。列表态含加载中、加载失败错误条与 [重试]、空数据表格默认空态；启停、批量启停、删除、重置命中以成功消息反馈，删除前二次确认。

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 Mock 定义表（api_mock_definition）

Mock 归属接口管理模块，支持从接口定义创建（继承路径与方法）或独立创建。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| interface_id | UUID | NULL | 关联接口定义（api_interface.id，可选） |
| name | VARCHAR(200) | NOT NULL | Mock 名称 |
| description | VARCHAR(500) | NULL | Mock 描述 |
| method | VARCHAR(10) | NOT NULL | 匹配的 HTTP 方法 |
| path | VARCHAR(500) | NOT NULL | 匹配的请求路径（支持 `*` 通配符） |
| priority | INT | NOT NULL DEFAULT 0 | 匹配优先级（同路径同方法组内排序，数值越小优先级越高） |
| match_rules | JSONB | NOT NULL DEFAULT '[]' | 匹配条件列表 `[{type, name, value}]`（type: header/param/body，value 支持普通值或正则表达式） |
| enabled | BOOLEAN | NOT NULL DEFAULT TRUE | 启用状态 |
| follow_api | BOOLEAN | NOT NULL DEFAULT FALSE | 跟随 API（Mock 自身未配置响应时，使用关联接口定义的响应示例） |
| response_status | INT | NOT NULL DEFAULT 200 | 响应状态码 |
| response_headers | JSONB | NOT NULL DEFAULT '{}' | 响应头 `{key: value}` |
| response_body_type | VARCHAR(20) | NOT NULL DEFAULT 'json' | 响应体类型：json / text / xml / binary |
| response_body | TEXT | NULL | 响应体内容（支持变量引用） |
| delay_ms | INT | NOT NULL DEFAULT 0 | 响应延迟（毫秒） |
| hit_count | BIGINT | NOT NULL DEFAULT 0 | 命中次数统计 |
| last_hit_at | TIMESTAMP | NULL | 最后命中时间 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_api_mock_project` (project_id), `idx_api_mock_interface` (interface_id), `idx_api_mock_path_method` (project_id, path, method, priority)

> `idx_api_mock_path_method` 支撑 Mock 匹配引擎按路径+方法快速查询并按优先级排序。合计 3 个索引，符合 C9。

#### 2.1.2 Mock 访问日志表（api_mock_access_log）

记录 Mock 访问日志，由免登录访问链路按命中写入，用于统计与排查。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| mock_id | UUID | NOT NULL | 命中的 Mock 定义 |
| project_id | UUID | NOT NULL | 归属项目 |
| method | VARCHAR(10) | NOT NULL | 请求方法 |
| path | VARCHAR(500) | NOT NULL | 请求路径 |
| request_headers | JSONB | NULL | 请求头 |
| request_body | TEXT | NULL | 请求体（截断） |
| response_status | INT | NOT NULL | 返回的状态码 |
| response_body | TEXT | NULL | 返回的响应体（截断） |
| duration_ms | INT | NULL | 响应耗时（毫秒） |
| client_ip | VARCHAR(50) | NULL | 客户端 IP |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 访问时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_api_mlog_mock` (mock_id), `idx_api_mlog_project_created` (project_id, created_at DESC)

### 2.2 错误码补充

> Mock 服务的错误码以《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/05-api-testing/02-api-testing-infra-overview.md`）2.2 已登记的十位错误码为准：`1000017201`（`API_MOCK_NOT_FOUND`，Mock 定义不存在）、`1000017202`（`API_MOCK_ADDR_CONFLICT`，同路径同方法地址冲突）。

---


## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `21-mock-service-overview.md` | 1. 引言（含 1.4 页面入口与权限口径）、2. 数据设计 |
| 规则管理 | `22-mock-service-rules.md` | 1. Mock 管理、2. Mock 管理页 |
| 调试 | `23-mock-service-debug.md` | 1. Mock 调试、2. Mock 调试面板（前端） |
| 访问与匹配引擎 | `24-mock-service-serving.md` | 1. Mock 访问（免登录）、2. Mock 匹配引擎、3. Mock 服务实现、4. Mock 变量解析 |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-03 | 对齐实现：补页面入口与权限口径，表名与索引名对齐 schema.sql，错误码改为引用基础设施总览，校准分册-章节对照表章节号 |
| V1.0 | 2026-10-03 | 分册-章节对照表与交叉引用一致性复检 |
