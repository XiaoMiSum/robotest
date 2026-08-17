# 软件测试平台——Mock 服务与报告详细设计说明书

**文档版本**：V1.2
**日期**：2026-08-17
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台 V1.2 接口测试业务域的 **Mock 服务与接口测试报告**进行详细设计，定义 Mock 规则、报告、分享、导出的数据结构、接口规范与业务逻辑，为开发实现提供完整依据。

### 1.2 范围

覆盖 SRS 3.3（Mock 服务）、3.6（接口测试报告）与概要设计第 3.1 章对应模块：

- **Mock 服务**：Mock 定义 CRUD、匹配规则、响应定义、调试、命中统计、访问地址管理；
- **接口测试报告**：报告列表与详情、分享链接、导出（JSON/HTML）、报告清理；
- **全局资产**：项目级可复用组件资产库（前置处理器、后置处理器、验证器、提取器）的管理与复制。

### 1.3 参考资料

- 《接口测试需求规格说明书 V1.2》（`docs/需求/接口测试需求规格说明书.md`，3.3、3.6）
- 《概要设计说明书 V1.2》（`docs/概要/概要设计说明书.md`，4.5–4.6）
- 《API 测试基础设施详细设计说明书》（`docs/详细设计/API测试基础设施详细设计说明书.md`）

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
| path | VARCHAR(500) | NOT NULL | 匹配的请求路径 |
| match_rules | JSONB | NOT NULL DEFAULT '[]' | 匹配条件列表 `[{type, name, value, expression}]`（type: header/param/body） |
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

**索引**：`idx_mock_project` (project_id), `idx_mock_interface` (interface_id), `idx_mock_path_method` (project_id, path, method)

> `idx_mock_path_method` 支撑 Mock 匹配引擎按路径+方法快速查询。合计 3 个索引，符合 C9。

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

- **路径**：`GET /api/project/api/mocks?interfaceId=&search=&page=1&pageSize=20`
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

- **路径**：`GET /api/project/api/mocks/:id`
- **响应**：包含完整匹配规则、响应定义、关联接口信息。

```json
{
  "id": "018f...",
  "name": "登录成功 Mock",
  "interfaceId": "018e...",
  "interfaceName": "用户登录",
  "method": "POST",
  "path": "/api/auth/login",
  "description": "模拟登录成功响应",
  "matchRules": [
    { "type": "header", "name": "Content-Type", "value": "application/json" },
    { "type": "body", "name": "username", "expression": "admin" }
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

- **路径**：`POST /api/project/api/mocks`
- **请求体**：同 3.1.2（不含 id、hitCount、lastHitAt）。
- **校验**：同项目下同路径同方法已启用 Mock 时返回错误码 7302（`API_MOCK_ADDR_CONFLICT`）。

#### 3.1.4 从接口定义创建 Mock

- **路径**：`POST /api/project/api/mocks/from-interface/:interfaceId`
- **说明**：继承接口定义的 path 与 method，创建 Mock 定义。
- **响应**：`{ "id": "018f..." }`

#### 3.1.5 更新 Mock

- **路径**：`PUT /api/project/api/mocks/:id`
- **请求体**：同 3.1.2。

#### 3.1.6 启停 Mock

- **路径**：`PATCH /api/project/api/mocks/:id/toggle`
- **请求体**：`{ "enabled": false }`
- **说明**：即时生效，不重启服务。

#### 3.1.7 删除 Mock

- **路径**：`DELETE /api/project/api/mocks/:id`
- **响应**：`{ "success": true }`

#### 3.1.8 重置命中统计

- **路径**：`POST /api/project/api/mocks/:id/reset-hit-count`
- **响应**：`{ "success": true }`

#### 3.1.9 查询 Mock 地址

- **路径**：`GET /api/project/api/mocks/:id/address`
- **说明**：返回 Mock 的完整访问地址（含平台 Mock 域名）。
- **响应**：

```json
{
  "mockUrl": "https://mock.robotest.example.com/api/auth/login",
  "method": "POST",
  "headers": {}
}
```

### 3.2 Mock 调试

#### 3.2.1 执行 Mock 调试

- **路径**：`POST /api/project/api/mocks/:id/debug`
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
  - 按「请求方法 + 路径 + 匹配条件」顺序取第一条命中规则。
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
  ↓ 按 method + path 查询启用的 Mock 列表（idx_mock_path_method）
  ↓ 逐条检查 match_rules
  ↓ 命中 → 生成响应（变量解析 → 延迟等待 → 返回）
  ↓ 未命中 → 返回 404
```

**匹配规则类型**：

| type | 匹配逻辑 | 示例 |
| ---- | -------- | ---- |
| header | 请求头包含指定 key 且 value 匹配 | `{ "type": "header", "name": "X-Request-Id", "value": ".*" }` |
| param | Query 参数或 REST 参数匹配 | `{ "type": "param", "name": "page", "value": "1" }` |
| body | 请求体 JSON 字段匹配（JSONPath） | `{ "type": "body", "name": "$.username", "value": "admin" }` |

**匹配规则为空时**：仅按 method + path 匹配，命中即返回。

**跟随 API 模式**：Mock 自身未配置响应（`response_body` 为空）且 `follow_api = true` 时，使用关联接口定义的 `response_example` 作为响应。

### 4.2 报告生成

场景执行完成后，执行引擎生成报告：

1. **步骤级结果收集**：每个步骤的请求/响应快照、验证器结果、提取器结果、耗时。
2. **结果汇总计算**：总步骤数、通过数（所有验证器通过）、失败数（任一验证器失败）、跳过数（步骤 disabled）、总耗时。
3. **报告写入**：将结果写入 `api_report` 表，同时更新 `api_execution_history.report_id`。
4. **Ryze 快照保存**：平台内执行时保存完整 Ryze JSON 至 `api_report.ryze_snapshot`。

**报告状态判定**：
- `success`：所有启用步骤的所有验证器均通过。
- `failed`：任一验证器失败。
- `partial`：部分步骤被跳过（disabled），其余通过。

### 4.3 分享机制

#### 4.3.1 分享链接生成

1. 校验项目设置中是否开启分享功能（未开启返回错误码 7008）。
2. 生成唯一 `share_token`（UUID v4，32 字符十六进制）。
3. 设置过期时间（`share_expires_at = now + expiresInDays`）。
4. 返回完整分享 URL。

#### 4.3.2 分享访问校验

1. 通过 `share_token` 查询报告（`idx_report_share_token` 唯一索引）。
2. 校验 `share_expires_at > now`，过期返回错误码 7009（`API_SHARE_EXPIRED`）。
3. 校验报告未被删除。
4. 返回报告内容（步骤级结果，不含 Ryze 快照）。

### 4.4 导出

#### 4.4.1 JSON 导出

将报告完整内容导出为 JSON 文件，包含：
- 报告元数据（场景名称、执行时间、环境、状态）。
- 结果汇总。
- 步骤级结果明细（请求/响应快照）。

#### 4.4.2 HTML 导出

生成独立 HTML 文件（内联 CSS，不依赖外部资源），包含：
- 报告头部信息（场景名称、执行时间、状态、环境）。
- 结果汇总卡片（通过/失败/跳过/耗时）。
- 步骤列表（可折叠展开），每步展示：名称、状态、请求/响应、验证器结果。
- 支持打印友好样式。

### 4.5 报告清理

复用《API 测试基础设施详细设计说明书》4.3 定义的数据清理策略：
- 报告与执行历史保留期限默认 90 天（系统配置项）。
- 清理后执行历史保留元数据，报告详情置为「执行结果被清理」。
- `api_report.ryze_snapshot` 随报告一起清理。

---

## 5. 前端设计

### 5.1 Mock 管理页

- **Mock 列表**：表格展示，包含启停开关、命中统计、最后命中时间。
- **Mock 规则编辑器**：表单式编辑器，分「匹配条件」与「响应定义」两个区域。
  - 匹配条件：动态添加/删除行（类型、名称、值）。
  - 响应定义：状态码、响应头（键值对）、响应体（代码编辑器，支持 JSON 格式化）、延迟配置。
- **Mock 调试面板**：输入模拟请求，查看 Mock 响应结果。
- **Mock 地址复制按钮**：一键复制 Mock 完整访问地址。

### 5.2 报告查看页

- **报告列表**：分页、筛选（状态/场景/时间范围）。
- **报告详情**：
  - 头部：场景名称、执行时间、环境、状态、耗时。
  - 汇总卡片：通过/失败/跳过数字。
  - 步骤树：垂直排列的步骤卡片，每个卡片可展开查看请求/响应详情。
  - 验证器结果：断言通过/失败明细，失败项高亮。
  - 提取器结果：提取的变量名与值。
- **分享操作**：生成分享链接按钮（默认禁止时按钮禁用，提示需管理员开启）。
- **导出操作**：选择导出格式（JSON/HTML），下载文件。

---

## 6. 实施说明

### 6.1 Mock 服务实现

Mock 服务内嵌于平台应用进程，通过 Spring MVC 的 `/**` 通配路由或独立的 Netty 服务拦截请求。匹配逻辑在 Filter/Interceptor 层实现，优先级高于平台业务路由。

**路由优先级**：
1. 平台业务路由（`/api/**`）。
2. Mock 路由（排除 `/api/**`、`/ws/**`、静态资源）。
3. 未匹配 → 404。

### 6.2 Mock 变量解析

Mock 响应体中的变量引用在返回时实时解析：
- `${uuid()}` → 生成唯一 ID。
- `${timestamp()}` → 当前时间戳。
- `${env:VAR}` → 从环境变量读取。
- 未定义变量 → 保留原始 `${...}` 文本。

### 6.3 HTML 报告模板

HTML 报告使用内联 CSS 的静态模板（Thymeleaf 或手写字符串模板），不依赖外部 CDN。模板包含：
- 响应式布局（桌面/打印）。
- JSON 响应体格式化（`<pre><code>` + JS 语法高亮）。
- 步骤卡片折叠/展开交互。

---

**文档结束**
