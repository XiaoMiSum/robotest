# 软件测试平台——工作空间管理

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 工作空间管理接口

### 1.1 获取工作空间列表

- **路径**：`GET /api/admin/workspaces`
- **请求参数**：

| 参数       | 类型     | 必填  | 说明                |
| -------- | ------ | --- | ----------------- |
| keyword  | string | 否   | 按名称模糊搜索           |
| status   | string | 否   | active / dissolved（展示口径：活跃 / 归档） |
| page     | int    | 否   | 页码，默认 1           |
| pageSize | int    | 否   | 每页数量，默认 20，最大 100 |

- **响应**（既有接口，本版本响应**新增字段**，向后兼容）：
  
  ```json
  {
  "list": [
    {
      "id": 1,
      "name": "电商平台测试",
      "description": "负责电商平台相关测试",
      "memberCount": 15,
      "projectCount": 5,
      "status": "active",
      "createdByName": "zhangsan",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "total": 12
  }
  ```

- **新增字段** `createdByName`（String，可空）：由 `created_by` 批量回查 `sys_user.username`；历史数据无创建人时为 `null`（前端展示 `—`）。
- 详情接口 `GET /api/admin/workspaces/:id` 同步新增该字段。

### 1.2 创建工作空间

- **路径**：`POST /api/admin/workspaces`
- **请求体**：`{ "name": "新项目工作空间", "description": "工作空间描述", "adminUserId": "uuid" }`
- **校验**：`name` 必填，2~50 字符，全局唯一；`adminUserId` 必填，指定的管理员必须存在且为启用状态。
- **处理**：写入当前登录用户为 `created_by`（见 2）；空间落库后立即将 `adminUserId` 绑定为空间管理员成员（创建即绑定，保证「至少保留一个空间管理员」从源头成立；先校验管理员后插库，避免半成品数据）。
- **响应**：返回创建的工作空间信息，状态码 201。

### 1.3 获取工作空间详情

- **路径**：`GET /api/admin/workspaces/:id`
- **响应**：工作空间基本信息 + 成员列表（分页，成员信息含 workspaceRole），基本信息含**新增字段** `createdByName`（见 1.1）。

### 1.4 更新工作空间

- **路径**：`PUT /api/admin/workspaces/:id`
- **请求体**：可更新 `name`、`description`。
- **校验**：名称唯一性，已归档（`status='dissolved'`）的工作空间不可编辑（返回 `1000010033`）。
- **说明**：部分更新，不触碰 `created_by`（见 2）。

### 1.5 归档工作空间

- **路径**：`DELETE /api/admin/workspaces/:id`
- **权限**：`workspace:delete`
- **前置校验**：仅存在性校验（不存在返回 `1000003002`）；**不校验项目与成员**——归档不要求空间下没有项目，也不解除成员归属。
- **处理逻辑**：更新 `status='dissolved'` 将空间置为冻结态；**成员行保留**，供重新启用后原样还原。
- **响应**：成功提示。

### 1.6 获取工作空间成员

- **路径**：`GET /api/admin/workspaces/:id/members`

- **请求参数**：`pageNo`、`pageSize`

- **响应**：
  
  ```json
  {
  "list": [
    {
      "userId": 1,
      "username": "zhangsan",
      "email": "zhangsan@example.com",
      "workspaceRole": "admin",
      "joinedAt": "2026-01-15T10:30:00Z"
    }
  ],
  "total": 8
  }
  ```

### 1.7 添加成员

- **路径**：`POST /api/admin/workspaces/:id/members`

- **请求体**：
  
  ```json
  {
  "members": [
    { "userId": 1, "workspaceRole": "member" },
    { "userId": 2, "workspaceRole": "admin" }
  ]
  }
  ```

- **校验**：用户ID必须存在且为活跃状态；已在本工作空间的用户跳过；`workspaceRole` 必须为预置 `workspace_admin` 或 `workspace_member` 角色 ID，默认引用 `workspace_member`；归档空间拒绝操作（`1000010033`）。

- **响应**：返回成功添加和跳过的用户列表。

### 1.8 更新成员角色

- **路径**：`PUT /api/admin/workspaces/:id/members/:userId`
- **请求体**：`{ "workspaceRole": "admin" }`
- **校验**：降级唯一管理员时阻止；归档空间拒绝操作（`1000010033`）。

### 1.9 移除成员

- **路径**：`DELETE /api/admin/workspaces/:id/members/:userId`
- **校验**：不能移除空间最后一位管理员；归档空间拒绝操作（`1000010033`）。
- **响应**：成功提示。

### 1.10 重新启用工作空间（归档恢复）

- **路径**：`POST /api/admin/workspaces/:id/restore`
- **权限**：`workspace:delete`
- **校验**：工作空间不存在返回 `1000003002`；**必须处于已归档状态**（`status='dissolved'`），活跃空间调用返回 `1000010034`。
- **处理逻辑**：将 `status` 置回 `active`；归档期间保留的成员行原样生效，无需重新添加。
- **响应**：成功提示，前端刷新详情状态与操作入口。


## 2. 空间创建人写入与回填

- `POST /api/admin/workspaces`：Controller 通过 `@AuthenticationPrincipal LoginUser` 取当前用户 ID 传入 Service（仅传参，不含业务逻辑，C2），`createWorkspace` 写入 `created_by`。
- `getWorkspacePage` / `getWorkspaceDetail`：收集 `created_by` 集合后 `userMapper` 批量回查用户名，回填 `createdByName`；`created_by` 为 null 时置 null。
- 遵循部分更新原则（C11）：更新空间时**不触碰** `created_by`。


