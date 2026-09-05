# 软件测试平台——Mock 服务详细设计说明书

**文档版本**：V1.2
**日期**：2026-08-17
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台 V1.2 接口测试业务域的 **Mock 服务**进行详细设计，定义 Mock 定义、匹配规则、响应、调试、访问的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。接口测试报告（报告查看、分享、导出、清理）相关设计见《测试报告详细设计说明书》（`docs/详细设计/测试报告详细设计说明书.md`）。

### 1.2 范围

覆盖 SRS 3.3（Mock 服务）与概要设计对应模块：

- **Mock 服务**：Mock 定义 CRUD、匹配规则、响应定义、调试、命中统计、访问地址管理；
- **Mock 访问**：免登录响应服务、变量解析、命中统计、访问日志。

### 1.3 参考资料

- 《接口测试需求规格说明书 V1.2》（`docs/需求/接口测试需求规格说明书.md`，3.3）
- 《概要设计说明书 V1.2》（`docs/概要/概要设计说明书.md`）
- 《API 测试基础设施详细设计说明书》（`docs/详细设计/API测试基础设施详细设计说明书.md`）
- 《测试报告详细设计说明书》（`docs/详细设计/测试报告详细设计说明书.md`）

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
| 7301 | API_MOCK_NOT_FOUND | Mock 定义不存在 |
| 7302 | API_MOCK_ADDR_CONFLICT | Mock 地址冲突（同路径同方法已启用） |

---

## 3. 接口详细设计

### 3.1 Mock 管理

#### 3.1.1 查询 Mock 列表

- **路径**：`GET /api/project/mocks?interfaceId=&search=&enabled=&page=1&pageSize=20`
- **参数说明**：`interfaceId` 按关联接口过滤；`search` 按名称/路径模糊搜索；`enabled` 按启用状态过滤（true/false，可选，不传返回全部）。
- **响应**：

```json
{
  "records": [
    {
      "id": "018f...",
      "name": "登录成功 Mock",
      "interfaceId": "018e...",
      "method": "POST",
      "path": "/api/auth/login",
      "priority": 1,
      "enabled": true,
      "followApi": false,
      "responseStatus": 200,
      "hitCount": 156,
      "lastHitAt": "2026-08-17T10:30:00Z",
      "updatedAt": "2026-08-16T15:00:00Z"
    }
  ],
  "total": 8
}
```

#### 3.1.2 查询 Mock 详情

- **路径**：`GET /api/project/mocks/:id`
- **响应**：包含完整匹配规则、响应定义、关联接口信息。

```json
{
  "id": "018f...",
  "name": "登录成功 Mock",
  "interfaceId": "018e...",
  "interfaceName": "用户登录",
  "method": "POST",
  "path": "/api/auth/login",
  "priority": 1,
  "description": "模拟登录成功响应",
  "matchRules": [
    { "type": "header", "name": "Content-Type", "value": "application/json" },
    { "type": "body", "name": "$.username", "value": "admin" }
  ],
  "enabled": true,
  "followApi": false,
  "responseStatus": 200,
  "responseHeaders": { "Content-Type": "application/json" },
  "responseBodyType": "json",
  "responseBody": "{\n  \"code\": 200,\n  \"data\": {\n    \"token\": \"mock-token-${uuid()}\"\n  }\n}",
  "delayMs": 0,
  "hitCount": 156,
  "lastHitAt": "2026-08-17T10:30:00Z"
}
```

#### 3.1.3 创建 Mock

- **路径**：`POST /api/project/mocks`
- **请求体**：同 3.1.2（不含 id、hitCount、lastHitAt）。
- **校验规则**：
  - 名称必填，长度不超过 200；
  - 路径必填且以 `/` 开头，长度不超过 500；
  - 状态码为合法 HTTP 状态码（100–599）；
  - 同项目下同路径同方法已启用 Mock 时返回错误码 7302（`API_MOCK_ADDR_CONFLICT`）；
  - `priority` 缺省时取同路径同方法组内最大值 + 1。

#### 3.1.4 从接口定义创建 Mock

- **路径**：`POST /api/project/mocks/from-interface/:interfaceId`
- **说明**：继承接口定义的 path 与 method，并设置 `interface_id` 关联接口，创建 Mock 定义。
- **前端流程**：接口管理页行内 [创建 Mock] → 跳转 Mock 管理页并打开新建抽屉，自动填充关联接口、方法、路径 → 用户确认保存时调用本接口创建。
- **响应**：`{ "id": "018f..." }`

#### 3.1.5 更新 Mock

- **路径**：`PUT /api/project/mocks/:id`
- **请求体**：同 3.1.2。
- **校验**：同 3.1.3 创建校验规则；Mock 不存在时返回错误码 7301（`API_MOCK_NOT_FOUND`）。

#### 3.1.6 启停 Mock

- **路径**：`PATCH /api/project/mocks/:id/toggle`
- **请求体**：`{ "enabled": false }`
- **说明**：即时生效，不重启服务；Mock 不存在时返回错误码 7301（`API_MOCK_NOT_FOUND`）。

#### 3.1.7 删除 Mock

- **路径**：`DELETE /api/project/mocks/:id`
- **响应**：`{ "success": true }`

#### 3.1.8 重置命中统计

- **路径**：`POST /api/project/mocks/:id/reset-hit-count`
- **响应**：`{ "success": true }`

#### 3.1.9 查询 Mock 地址

- **路径**：`GET /api/project/mocks/:id/address`
- **说明**：返回 Mock 的完整访问地址（含平台 Mock 域名）。
- **响应**：

```json
{
  "mockUrl": "https://mock.robotest.example.com/api/auth/login",
  "method": "POST",
  "headers": {}
}
```

#### 3.1.10 复制 Mock

- **路径**：`POST /api/project/mocks/:id/duplicate`
- **说明**：复制原 Mock 全部配置生成新规则，名称自动追加「- 副本」，`enabled` 默认停用（避免与源规则地址冲突），`priority` 取同路径同方法组内最大值 + 1。
- **前端流程**：行内 [复制] → 打开抽屉并预填原规则全部配置（名称追加「- 副本」）→ 用户确认保存时调用本接口创建副本。
- **响应**：`{ "id": "018f..." }`

#### 3.1.11 批量启停 Mock

- **路径**：`POST /api/project/mocks/batch-toggle`
- **请求体**：

```json
{
  "ids": ["018f...", "018g..."],
  "enabled": false
}
```

- **响应**：

```json
{
  "success": true,
  "updatedCount": 2
}
```

- **说明**：批量启用/停用，即时生效，不重启服务；逐条校验，Mock 不存在时跳过并计入失败数。

### 3.2 Mock 调试

#### 3.2.1 执行 Mock 调试

- **路径**：`POST /api/project/mocks/:id/debug`
- **说明**：模拟请求命中该 Mock，返回配置的响应（不计入 hit_count）。
- **请求体**：

```json
{
  "headers": { "Content-Type": "application/json" },
  "body": { "username": "admin", "password": "123456" }
}
```

- **响应**：

```json
{
  "status": 200,
  "headers": { "Content-Type": "application/json" },
  "body": { "code": 200, "data": { "token": "mock-token-xxx" } },
  "durationMs": 5
}
```

### 3.3 Mock 访问（免登录）

#### 3.3.1 Mock 响应服务

- **路径**：`{MOCK_BASE_URL}/{path}`（与真实接口同构）
- **方法**：匹配的 HTTP 方法
- **说明**：
  - 不要求平台登录态。
  - 路径支持 `*` 通配符（如 `/api/users/*` 匹配 `/api/users/1`、`/api/users/2`）。
  - 按「请求方法 + 路径 + 匹配条件」顺序取第一条命中规则；同路径同方法多条规则按 `priority` 升序匹配。
  - 无命中时返回 404。
  - 支持响应延迟（`delay_ms`）。
  - 支持变量引用（环境变量、内置函数）动态生成响应内容。
  - 命中后更新 `hit_count` 与 `last_hit_at`，写入 `mock_access_log`。
  - Mock 限流：单路径 QPS 上限可配置（超限返回 429）。

---

## 4. 业务逻辑设计

### 4.1 Mock 匹配引擎

Mock 请求处理流程：

```
收到请求
  ↓ 按 method + path 查询启用的 Mock 列表（idx_mock_path_method，路径支持 `*` 通配符）
  ↓ 按 priority 升序排序
  ↓ 逐条检查 match_rules
  ↓ 命中 → 生成响应（变量解析 → 延迟等待 → 返回）
  ↓ 未命中 → 返回 404
```

**匹配规则类型**：

| type | 匹配逻辑 | 示例 |
| ---- | -------- | ---- |
| header | 请求头包含指定 key 且 value 匹配 | `{ "type": "header", "name": "X-Request-Id", "value": ".*" }` |
| param | Query 参数或 REST 路径参数匹配 | `{ "type": "param", "name": "page", "value": "1" }` |
| body | 请求体 JSON 字段匹配（JSONPath） | `{ "type": "body", "name": "$.username", "value": "admin" }` |

> `value` 支持普通值或正则表达式（如 `.*`），与交互设计「值/表达式」单字段输入一致。

**匹配规则为空时**：仅按 method + path 匹配，命中即返回。

**优先级匹配**：同路径同方法存在多条规则时，按 `priority` 升序逐条匹配，取第一条命中规则；`priority` 相同时按创建时间先后排序。

**跟随 API 模式**：Mock 自身未配置响应（`response_body` 为空）且 `follow_api = true` 时，使用关联接口定义的 `response_example` 作为响应。

---

## 5. 前端设计

### 5.1 Mock 管理页

- **Mock 列表**：表格展示，包含启停开关、命中统计、最后命中时间、优先级序号；支持按关联接口/启用状态筛选与批量启停。
- **Mock 规则编辑器**：表单式编辑器，分「匹配条件」与「响应定义」两个区域。
  - 匹配条件：动态添加/删除行（类型、名称、值/表达式）。
  - 响应定义：状态码、响应头（键值对）、响应体（代码编辑器，支持 JSON 格式化）、延迟配置。
- **优先级**：创建时自动分配（同路径同方法组内最大值 + 1），不支持手动调整。
- **复制 Mock**：行内 [复制] 预填原规则配置，名称追加「- 副本」后保存生成新规则。
- **Mock 调试面板**：输入模拟请求，查看 Mock 响应结果。
- **Mock 地址复制按钮**：一键复制 Mock 完整访问地址。

---

## 6. 实施说明

### 6.1 Mock 服务实现

Mock 服务内嵌于平台应用进程，通过 Spring MVC 的 `/**` 通配路由或独立的 Netty 服务拦截请求。匹配逻辑在 Filter/Interceptor 层实现，优先级高于平台业务路由。

**路由优先级**：
1. 平台业务路由（`/api/**`）。
2. Mock 路由（排除 `/api/**`、`/ws/**`、静态资源）。
3. 未匹配 → 404。

> Mock 服务端口配置见《API 测试基础设施详细设计说明书》6.4（`api-test.mock.port`，为空时复用主端口）。

### 6.2 Mock 变量解析

Mock 响应体中的变量引用在返回时实时解析：
- `${uuid()}` → 生成唯一 ID。
- `${timestamp()}` → 当前时间戳。
- `${env:VAR}` → 从环境变量读取。
- 未定义变量 → 保留原始 `${...}` 文本。

---

**文档结束**
