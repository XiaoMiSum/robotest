# 软件测试平台——环境管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 环境管理

### 1.1 查询环境列表

- **路径**：`GET /api/project/environments?keyword=xxx`
- **参数**：`keyword` 环境名称模糊搜索（前端输入防抖 300ms 后触发请求）。
- **分页约定**：该接口为有限配置项的非分页列表，响应 `data` 直接为环境数组，不使用 `PageResult.list/total`。
- **响应**：

```json
[
  {
    "id": "018f...",
    "name": "测试环境",
    "description": "日常测试",
    "isDefault": true,
    "sortOrder": 0,
    "variableCount": 5,
    "dataSourceCount": 2,
    "processorCount": 1
  }
]
```

### 1.2 查询环境详情

- **路径**：`GET /api/project/environments/:id`
- **响应**：包含 HTTP 配置列表、变量列表、数据源列表（脱敏）、处理器列表。

### 1.3 创建环境

- **路径**：`POST /api/project/environments`
- **请求体**：

```json
{
  "name": "测试环境",
  "description": "日常测试",
  "isDefault": true,
  "httpConfigs": [
    {
      "name": "内部 API",
      "refName": "http_1",
      "baseUrl": "https://staging.example.com",
      "headers": [{ "key": "Content-Type", "value": "application/json", "enabled": true }]
    }
  ],
  "variables": [
    { "name": "TEST_PASSWORD", "value": "123456", "description": "测试密码" }
  ],
  "dataSources": [
    {
      "name": "测试库",
      "refName": "db_1",
      "driver": "com.mysql.cj.jdbc.Driver",
      "url": "jdbc:mysql://staging-db:3306/test?user=test_user&password=123456",
      "connectionProperties": {},
      "maxPoolSize": 5
    }
  ],
  "processors": []
}
```

- **响应**：`{ "id": "018f..." }`
- **说明**：新建与复制共用本接口。复制不设独立接口，前端以选中环境详情预填面板新建态，用户确认后按本接口落库（见 34 §1.4）；副本不携带源环境 `id`，`sortOrder` 取列表末尾序号（`max(sortOrder)+1`），`isDefault` 恒为 `false`，不抢占源环境默认标记。数据源随创建负载一并复制：凭据内嵌于连接 URL，复制仅在项目内流转、不越项目边界（导出/导入仍排除数据源段，见 1.9 / 1.10）。HTTP 配置、变量与处理器同样全量随副本。

### 1.4 更新环境

- **路径**：`PUT /api/project/environments/:id`
- **请求体**：同 1.3。
- **说明**：聚合全量保存。HTTP 配置、变量、数据源、处理器均以 JSONB 随环境整体提交，一次性写入主表 `http_configs` / `variables` / `data_sources` / `processors` 列；供环境编辑、导入/导出及全部配置面板（HttpConfig / 变量 / 数据源 / 处理器）的整体保存使用（见 3.2 / 3.3 / 3.4）。

### 1.5 删除环境

- **路径**：`DELETE /api/project/environments/:id`
- **校验**：若环境被场景引用，返回错误码 1000017402（`API_ENV_REFERENCED`）；若环境被定时任务绑定，返回错误码 1000017404（`API_ENV_TASK_BOUND`），需先在定时任务中解除绑定。

### 1.6 设置默认环境

- **路径**：`PATCH /api/project/environments/:id/set-default`
- **响应**：`{ "success": true }`

### 1.7 测试数据源连接

- **路径**：`POST /api/project/environments/:id/data-sources/test`
- **说明**：连接测试改为请求体传入**完整数据源配置**进行测试（免保存）：请求体传 `name`、`refName`、`driver`、`url`、`connectionProperties`、`maxPoolSize`、`isDefault`，按表单当前值试连，新建中或已修改未保存的数据源亦可直接验证。尝试建立 JDBC 连接，成功返回连接信息，失败返回错误码 1000017403（`API_DATASOURCE_CONN_FAILED`）并附带详细错误。
- **前置条件**：路径需 `:id`，环境尚未创建（面板新建态）时无 ID，连接测试不可用，前端置灰并提示创建后再试（见 34 §1.6）。
- **判定口径**：URL 以 `redis://` 或 `rediss://` 开头的数据源不走 JDBC、不校验驱动：按 RESP 协议建立连接后发送 `PING` 验证连通性（复用框架内置 Redis 客户端），成功时通过 `INFO server` 提取 `redis_version` 填入 `databaseVersion`。JDBC 数据源仅放行服务端内置驱动，其余拒绝测试。
- **请求体**：

```json
{
  "name": "测试库",
  "refName": "db_1",
  "driver": "com.mysql.cj.jdbc.Driver",
  "url": "jdbc:mysql://staging-db:3306/test?user=test_user&password=123456",
  "connectionProperties": {},
  "maxPoolSize": 5,
  "isDefault": false
}
```

- **响应**：

```json
{
  "success": true,
  "message": "连接成功",
  "databaseVersion": "MySQL 8.0.35"
}
```

Redis 数据源成功响应示例：

```json
{
  "success": true,
  "message": "连接成功",
  "databaseVersion": "Redis 7.2.4"
}
```

### 1.8 测试 HTTP 连接

- **路径**：`POST /api/project/environments/:id/http-configs/test`
- **说明**：连接测试改为请求体传入**完整 HTTP 配置**进行测试（免保存）：请求体传 `name`、`refName`、`baseUrl`、`headers`、`isDefault`，向配置的 base_url 发送 GET 请求验证连通性，新建中或已修改未保存的 HTTP 配置亦可直接验证。
- **前置条件**：同 1.7，环境尚未创建时连接测试不可用。
- **请求体**：

```json
{
  "name": "内部 API",
  "refName": "http_1",
  "baseUrl": "https://staging.example.com",
  "headers": [{ "key": "Content-Type", "value": "application/json", "enabled": true }],
  "isDefault": false
}
```

- **响应**：

```json
{
  "success": true,
  "message": "连接成功",
  "statusCode": 200,
  "durationMs": 125
}
```

### 1.9 导出环境

- **路径**：`GET /api/project/environments/:id/export`
- **说明**：导出环境配置为 JSON 文件。数据源段整段排除：凭据内嵌于连接 URL 无法导出一部分，导入端亦不消费该段，环境导入后需重新配置数据源。变量值明文导出（无敏感值概念）。

### 1.10 导入环境

- **路径**：`POST /api/project/environments/import`
- **Content-Type**：`multipart/form-data`
- **请求参数**：`file`（环境配置 JSON 文件）、`overwrite`（重名处理：`true` 覆盖 / `false` 跳过）。
- **说明**：导入环境配置 JSON 文件，空文件与超过 10MB 的文件拒绝（大小限制独立于容器 multipart 配置）；重名环境按 `overwrite` 开关处理：开启时覆盖，关闭时跳过（不新增）。文件中的 `dataSources` 段被忽略（见 1.9 导出规则），环境导入后需重新配置数据源。变量值与 HTTP 配置、处理器一并导入。
- **响应**：

```json
{
  "createdCount": 1,
  "overwrittenCount": 0,
  "skippedCount": 1
}
```

### 1.11 调整环境排序

- **路径**：`PATCH /api/project/environments/:id/sort`
- **请求体**：

```json
{
  "sortOrder": 1
}
```

- **说明**：调整环境排序序号，列表按 `sort_order` 升序展示（默认环境置顶）。


## 2. 环境配置页

- **环境列表**：左侧环境列表，支持新建、排序；默认环境显示「默认」标签，非默认环境行内悬浮 [设为默认]。
- **新建态面板**：新建与复制共用右侧详情面板的**新建态**——吸顶区填写名称、描述与「设为默认」，标签页照常编辑，右侧 [取消]/[创建] 就地提交（见 1.3）。新建态无环境 ID，HTTP 与数据源的连接测试不可用（见 1.7 / 1.8）；离开新建态（取消、切换选中、编辑、删除、再次新建/复制）在有改动时先确认放弃。创建成功后选中新环境，副本取 `（副本）` 后缀名。
- **HTTP 配置列表**：多个 HTTP 配置的列表 + 编辑表单（名称、引用名、Base URL、请求头）+ 连接测试按钮；面板随环境整体保存（见 1.4 / 3.4），不再逐条独立保存，新增/编辑/删除均在提交时整体写入 `http_configs`；执行侧超时/重定向/SSL 为固定值（见 2.1.2）。
- **变量编辑器**：表格编辑器（变量名、值、描述），取值明文展示，随环境整体保存（见 3.3），支持环境的导入/导出与从执行结果添加变量。
- **数据源配置**：数据源列表 + 编辑表单（引用名称、驱动、URL）+ 连接测试按钮；面板随环境整体保存（见 3.4）；切换驱动时自动填充对应数据库类型的URL示例（含用户名密码占位符）。Redis 选项免驱动选择，按 `redis://` 协议识别。
- **全局处理器配置**：前置/后置处理器的列表编辑器，随环境整体保存（见 3.2）。

---


## 3. 环境删除保护

- 删除环境前校验场景引用（错误码 1000017402）与定时任务绑定（错误码 1000017404），被引用或绑定时禁止删除，需先解除引用/绑定。

---

**文档结束**

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-09-29 | 新建与复制统一走创建接口：移除独立复制接口，副本由面板新建态预填后按 1.3 落库（数据源随之复制，仅项目内）；补充新建态与连接测试的前置条件 |

