# 软件测试平台——用户管理

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 用户管理接口

### 1.1 获取用户列表

- **路径**：`GET /api/admin/users`
- **请求参数**：

| 参数          | 类型     | 必填  | 说明                |
| ----------- | ------ | --- | ----------------- |
| keyword     | string | 否   | 按用户名/邮箱模糊搜索       |
| status      | string | 否   | active / disabled / locked（三态，见 2.2） |
| roleId      | bigint | 否   | 筛选拥有该角色的用户        |
| page        | int    | 否   | 页码，默认 1           |
| pageSize    | int    | 否   | 每页数量，默认 20，最大 100 |

- **响应数据**：
  
  ```json
  {
  "records": [
    {
      "id": 1,
      "username": "zhangsan",
      "email": "zhangsan@example.com",
      "avatarUrl": "https://...",
      "status": "active",
      "roles": [
        { "id": 2, "name": "用户管理员", "type": "system" }
      ],
      "workspaces": [
        { "id": 1, "name": "电商平台", "workspaceRole": "member" }
      ],
      "createdAt": "2026-01-15T10:30:00Z",
      "updatedAt": "2026-06-20T14:00:00Z"
    }
  ],
  "total": 45
  }
  ```

### 1.2 创建用户

- **路径**：`POST /api/admin/users`

- **请求体**：
  
  ```json
  {
  "username": "lisi",
  "name": "李四",
  "email": "lisi@example.com",
  "password": "Abc@123456",
  "roleIds": [2, 3]
  }
  ```

- **校验规则**：
  
  - `username`：必填，3~30 字符，正则 `^[a-zA-Z0-9_-]+$`，不可与已有用户名重复。
  - `name`：必填，1~50 字符，用户显示名称。
  - `email`：必填，合法邮箱格式，不可与已有邮箱重复。
  - `password`：必填，8~64 字符。
  - `roleIds`：可选，允许为空数组。若不为空，所有ID必须存在于 sys_role 表且 `type='system'`。

- **处理逻辑**：密码哈希后，在事务中插入 sys_user，若 roleIds 非空批量插入 sys_user_role。

- **响应**：返回创建的用户完整信息，状态码 201。

### 1.3 获取用户详情

- **路径**：`GET /api/admin/users/:id`
- **响应**：返回单个用户的完整信息，含 roles 列表和 workspaces 列表（字段同上）。

### 1.4 更新用户

- **路径**：`PUT /api/admin/users/:id`

- **请求体**：
  
  ```json
  {
  "email": "newemail@example.com",
  "roleIds": [2]
  }
  ```

- **校验规则**：
  
  - 不可修改 `username`。
  - `roleIds` 可选，允许为空数组；若不为空，所有角色必须为系统角色。
  - 不允许移除自己的最后一个系统角色。

- **处理逻辑**：更新 email，全量替换 sys_user_role（删除旧关联，插入新关联）。若角色发生变更，强制该用户所有 Token 失效。

- **响应**：返回更新后的用户信息。

### 1.5 更新用户状态

- **路径**：`PATCH /api/admin/users/:id/status`
- **请求体**：`{ "status": "disabled" }`
- **校验**：`status` 取值由 `active | disabled` **扩展**为 `active | disabled | locked`（见 2.2）；Service 层校验 `status ∈ {active, disabled, locked}`，非法值返回错误码 `1000001010`（见 6. 错误码定义）。不引入「不可操作自身」的限制（见 4）。
- **处理**：更新 status 字段，若置为 disabled/locked 则立即将用户所有 Token 加入黑名单或递增版本号（见 4）。
- **响应**：返回更新后的用户信息。

### 1.6 批量操作

- **路径**：`PATCH /api/admin/users/batch-status`
- **请求体**：`{ "userIds": [1, 2, 3], "status": "disabled" }`
- **校验**：`status` 取值同 1.5（`active | disabled | locked`），非法值返回错误码 `1000001010`（见 6. 错误码定义）。
- **响应**：返回成功和失败列表。

### 1.7 重置密码

- **路径**：`POST /api/admin/users/:id/reset-password`
- **请求体**：`{ "newPassword": "NewPass@123" }`
- **校验**：新密码仅需 8~64 字符长度。
- **处理**：更新密码哈希，强制该用户所有 Token 失效。
- **响应**：操作成功提示，不返回密码。


## 2. 用户创建流程

```
POST /api/admin/users
  ├── 1. 参数校验（用户名格式及唯一性、邮箱格式及唯一性、密码强度）
  ├── 2. 校验 roleIds 若不为空，角色必须存在且 type='system'
  ├── 3. 密码哈希处理（bcrypt / argon2）
  ├── 4. 开启事务
  │     ├── 插入 sys_user 表
  │     └── 若 roleIds 非空，批量插入 sys_user_role 表
  ├── 5. 提交事务
  └── 6. 返回用户完整信息（含 roles 和 workspaces）
```


## 3. 用户更新流程

```
PUT /api/admin/users/:id
  ├── 1. 校验目标用户存在，且不允许修改 username
  ├── 2. 校验 roleIds（若不为空）有效且为系统角色
  ├── 3. 不能移除自身的最后一个系统角色
  ├── 4. 开启事务
  │     ├── 更新 sys_user.email
  │     └── 全量替换 sys_user_role（删除旧关联，插入新关联）
  ├── 5. 若角色发生变更，强制该用户所有 Token 失效
  └── 6. 返回更新后信息
```


## 4. 用户状态变更、禁用与强制下线

- `updateUserStatus` / `batchUpdateStatus`：先校验状态取值合法性（`status ∈ {active, disabled, locked}`，非法值返回错误码 `1000001010`，见 1.5 与 6. 错误码定义），其余沿用既有逻辑（存在性校验、仅更新 `status` 字段，载体为新建实体，C11）。
- 不引入「不可操作自身」的限制，与既有禁用行为保持一致（管理员可禁用/锁定自身账户；后续如需收紧另行立项）。
- 管理员将用户状态置为 disabled（或 locked）后，系统立即将该用户所有活跃 Token 加入 Redis 黑名单或递增 token 版本号。
- 网关中间件验证 Token 时，检查用户状态及 token 版本，不匹配返回 401（错误码 2005）。
- 密码重置后同样触发 Token 失效。


