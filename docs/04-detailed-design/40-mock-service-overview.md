# 软件测试平台——Mock服务详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对接口测试业务域的 **Mock 服务**进行详细设计，定义 Mock 定义、匹配规则、响应、调试、访问的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。接口测试报告（报告查看、分享、清理）相关设计见《测试报告详细设计说明书》（`docs/04-detailed-design/01-readme.md`）。

### 1.2 范围

覆盖 SRS 3.3（Mock 服务）与概要设计对应模块：

- **Mock 服务**：Mock 定义 CRUD、匹配规则、响应定义、调试、命中统计、访问地址管理；
- **Mock 访问**：免登录响应服务、变量解析、命中统计、访问日志。

### 1.3 参考资料

- 《接口测试需求规格说明书》（`docs/01-requirements/01-readme.md`，3.3）
- 《概要设计说明书》（`docs/02-high-level-design/02-high-level-design.md`）
- 《API 测试基础设施详细设计说明书》（`docs/04-detailed-design/01-readme.md`）
- 《测试报告详细设计说明书》（`docs/04-detailed-design/01-readme.md`）

---


## 2. 数据设计

### 2.1 数据库表设计

#### 2.1.1 Mock 定义表（mock_definition）

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

**索引**：`idx_mock_project` (project_id), `idx_mock_interface` (interface_id), `idx_mock_path_method` (project_id, path, method, priority)

> `idx_mock_path_method` 支撑 Mock 匹配引擎按路径+方法快速查询并按优先级排序。合计 3 个索引，符合 C9。

#### 2.1.2 Mock 访问日志表（mock_access_log）

记录 Mock 访问审计日志，用于统计与排查。

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
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 访问时间 |

**索引**：`idx_mlog_mock` (mock_id), `idx_mlog_project_created` (project_id, created_at DESC)

> 访问日志按项目清理策略自动清理（默认 90 天）。

### 2.2 错误码补充

| 错误码 | 常量名 | 说明 |
| ------ | ------ | ---- |
| 1000017201 | API_MOCK_NOT_FOUND | Mock 定义不存在 |
| 1000017202 | API_MOCK_ADDR_CONFLICT | Mock 地址冲突（同路径同方法已启用） |

---


## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `40-mock-service-overview.md` | 前言、1. 引言、2. 数据设计 |
| 规则管理 | `41-mock-service-rules.md` | 3.1 Mock 管理、5.1 Mock 管理页 |
| 调试 | `42-mock-service-debug.md` | 3.2 Mock 调试 |
| 访问与匹配引擎 | `43-mock-service-serving.md` | 3.3 Mock 访问、4.1 Mock 匹配引擎、6.1 Mock 服务实现、6.2 Mock 变量解析 |
