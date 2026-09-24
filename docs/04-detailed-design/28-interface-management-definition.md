# 软件测试平台——接口定义管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 接口定义管理

### 1.1 查询接口列表

- **路径**：`GET /api/project/interfaces?moduleId=&search=&pageNo=1&pageSize=20`
- **筛选参数**：`moduleId`（可选，模块 ID）、`search`（可选，模糊匹配名称/路径）、`status`（可选，启用/停用）、`view`（可选，视图切换：followed=我关注的 / created=我创建的 / all=全部，缺省 all；followed 按当前用户关注关系过滤，created 按 `created_by` 过滤）。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "name": "用户登录",
      "protocol": "http",
      "method": "POST",
      "path": "/api/auth/login",
      "moduleId": "018e...",
      "status": "enabled",
      "referenceCount": 3,
      "updatedAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 25
}
```

### 1.2 查询接口详情

- **路径**：`GET /api/project/interfaces/:id`
- **响应**：包含完整请求参数模型、响应示例。

```json
{
  "id": "018f...",
  "name": "用户登录",
  "protocol": "http",
  "method": "POST",
  "path": "/api/auth/login",
  "description": "用户登录接口",
  "headers": [
    { "id": "018i...", "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "body": {
    "type": "json",
    "content": { "username": "", "password": "" }
  },
  "params": [],
  "restParams": [],
  "auth": { "type": "none" },
  "status": "enabled",
  "changeVersion": 1,
  "responseExample": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": { "code": 200, "data": { "token": "xxx" } }
  }
}
```

### 1.3 创建接口定义

- **路径**：`POST /api/project/interfaces`
- **请求体**：

```json
{
  "name": "用户登录",
  "protocol": "http",
  "method": "POST",
  "path": "/api/auth/login",
  "moduleId": "018f...",
  "description": "用户登录接口",
  "headers": [
    { "key": "Content-Type", "value": "application/json", "enabled": true }
  ],
  "body": {
    "type": "json",
    "content": { "username": "", "password": "" }
  },
  "params": [],
  "restParams": [],
  "auth": { "type": "none" },
  "status": "enabled",
  "responseExample": null
}
```

- **响应**：`{ "id": "018f..." }`
- **说明**：当前版本仅支持 http 协议，请求体 `protocol` 缺省为 `http`；jdbc 协议随测试场景模块（梯队三）开放。

### 1.4 更新接口定义

- **路径**：`PUT /api/project/interfaces/:id`
- **请求体**：同 1.3，另携带 `changeVersion`（当前版本号）。
- **校验**：`changeVersion` 与库中当前版本不一致时返回错误码 7105（`API_INTERFACE_VERSION_CONFLICT`），前端提示「接口已被他人修改，请刷新后重试」；保存成功后版本号递增，并写入一条变更历史记录（见 1.13）。

> **乐观锁口径**：框架统一 Result 响应封装（业务错误码 ≠200），不使用 HTTP 状态码表达冲突；7105 由前端按错误码识别。

### 1.5 删除接口定义

- **路径**：`DELETE /api/project/interfaces/:id`
- **校验**：若接口被场景或 Mock 引用（`referenceCount > 0`），返回错误码 7103（`API_INTERFACE_REFERENCED`）。

### 1.6 复制接口定义

- **路径**：`POST /api/project/interfaces/:id/copy`
- **请求体**：`{ "name": "用户登录（副本）" }`
- **说明**：复制接口定义，产生独立副本。

### 1.7 查询引用关系

- **路径**：`GET /api/project/interfaces/:id/references`
- **响应**：

```json
{
  "scenes": [
    { "id": "018f...", "name": "登录流程测试" }
  ],
  "mocks": [
    { "id": "018g...", "name": "登录 Mock" }
  ]
}
```

### 1.8 查询引用场景

- **路径**：`GET /api/project/interfaces/:id/scenes`
- **说明**：查询引用本接口定义的场景列表（通过步骤来源引用）。

### 1.9 批量移动接口

- **路径**：`PUT /api/project/interfaces/batch/move`
- **请求体**：

```json
{
  "ids": ["018f...", "018g..."],
  "moduleId": "018e..."
}
```

- **说明**：将所选接口批量移动至目标模块（`moduleId` 为 null 时移入未分组）。

### 1.10 批量删除接口

- **路径**：`DELETE /api/project/interfaces/batch`
- **请求体**：

```json
{
  "ids": ["018f...", "018g..."]
}
```

- **校验**：所选接口中存在被场景或 Mock 引用（`referenceCount > 0`）时整体拒绝，返回错误码 7103（`API_INTERFACE_REFERENCED`）并列出引用方清单。

### 1.11 启用/停用接口

- **路径**：`PUT /api/project/interfaces/:id/status`
- **请求体**：`{ "status": "enabled" }`（enabled / disabled）
- **说明**：切换接口启用状态，列表页「状态」筛选与徽标即时生效。

### 1.12 关注/取消关注接口

- **路径**：`POST /api/project/interfaces/:id/follow`（关注）、`DELETE /api/project/interfaces/:id/follow`（取消关注）
- **说明**：关注关系按用户记录（`api_interface_follow`），列表页「我关注的」视图按关注关系过滤。

### 1.13 查询变更历史

- **路径**：`GET /api/project/interfaces/:id/change-logs?pageNo=1&pageSize=20`
- **说明**：创建、更新、复制、导入更新、状态切换均写入一条记录；按 `change_version` 倒序分页。
- **响应**：

```json
{
  "list": [
    {
      "id": "018f...",
      "changeVersion": 3,
      "action": "update",
      "summary": "修改请求路径与默认请求头",
      "operatorId": "018c...",
      "createdAt": "2026-08-17T10:30:00Z"
    }
  ],
  "total": 1
}
```


## 2. 接口模块树组件

左侧树形目录，支持：
- 拖拽排序（同级与跨级）。
- 右键菜单：新建子模块、重命名、删除、新建接口。
- 搜索过滤（模糊匹配接口名称/路径）。
- 未分组区域（`module_id = null` 的接口归入）。


## 3. 接口预览页

右侧详情区域，Tab 切换：
- **基本信息**：名称、协议、方法、路径、描述、启用状态。
- **请求参数**：请求头、请求体、Query 参数（编辑态）。
- **响应示例**：响应状态码、响应头、响应体。
- **引用关系**：被哪些场景/Mock 引用（只读列表）。
- **引用场景**：引用本接口的场景列表（只读列表）。
- **变更历史**：变更记录列表（只读）。


