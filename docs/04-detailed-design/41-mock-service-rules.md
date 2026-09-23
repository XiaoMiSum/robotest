# 软件测试平台——（分册：规则管理）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

> 本分册由《》按功能模块拆分而来。前言、引言、数据设计与公共约定见总览分册 `40-mock-service-overview.md`；原章节编号保持不变，分册-章节对照见总览分册。

---

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


