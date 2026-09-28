# 软件测试平台——认证

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 认证接口

> **接口限流（安全规范 6.1）**：认证与公开接口统一使用框架 `@RateLimit`（按 IP 计数）与框架登录失败锁定；超限返回框架全局错误码 **429**，账号连续登录失败达阈值返回框架全局错误码 **423**（`ACCOUNT_LOCKED`）。各接口的 limit/window 及账号锁定口径见《安全规范》6.1 实施口径。

### 1.1 登录

- **路径**：`POST /api/auth/login`
- **请求体**：`{ "username": "admin", "password": "xxx" }`
- **响应**（`LoginRespDTO`）：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "accessToken": "eyJhb...",
    "refreshToken": "eyJhb...",
    "accessExpiry": "2026-07-26T12:00:00Z",
    "refreshExpiry": "2026-08-09T12:00:00Z",
    "user": {
      "id": "uuid",
      "username": "admin",
      "email": "admin@example.com",
      "avatarUrl": null,
      "roles": ["system-admin"],
      "permissions": ["user:view", "role:view", ...],
      "workspaces": [],
      "activeWorkspace": null
    },
    "hasWorkspace": true
  }
}
```

> **注意**：框架 `LoginResult` 中用户信息字段名为 `userInfo`，后端通过 `LoginRespDTO` 映射为 `user`。前端通过 `hasWorkspace` 决定登录后跳转目标。

> **本版本补充**：`POST /api/auth/login` 请求/响应结构**不变**；登录成功后新增登录审计写入（见《审计查询详细设计说明书》§4.3，详见 4），对客户端透明。

### 1.2 获取当前用户权限

- **路径**：`POST /api/auth/permissions`
- **说明**：返回当前用户的合并权限码列表（系统权限 + 当前工作空间权限）。需要 `X-Active-Workspace` 请求头获取空间级权限。
- **请求头**：`X-Active-Workspace: <workspaceId>`（可选，用于获取工作空间权限）
- **响应**：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "permissions": ["user:view", "case:create", ...]
  }
}
```

### 1.3 检查初始化状态

- **路径**：`GET /api/auth/init/status`
- **说明**：检查系统是否已初始化，用于首次部署后的初始化引导。调用者无需认证。
- **响应**：
  ```json
  {
    "code": 200,
    "msg": "success",
    "data": {
      "initialized": false
    }
  }
  ```

- **处理逻辑**：查询 `sys_user` 表记录数，若大于 0 则视为已初始化。

### 1.4 初始化设置

- **路径**：`POST /api/auth/init/setup`
- **说明**：首次部署时创建 admin 账号并分配系统管理员角色。调用者无需认证。
- **请求体**：
  ```json
  {
    "password": "Admin@12345678"
  }
  ```
- **校验**：
  - `password`：必填，8~64 字符。
- **处理逻辑**：
  1. 校验 `sys_user` 表是否已有记录，若有则返回错误码 `SYSTEM_ALREADY_INITIALIZED`。
  2. 创建 admin 用户：username=`admin`，name=`系统管理员`，email=`admin@robotest.local`，status=`active`。
  3. 使用 bcrypt/argon2 哈希密码并存入 `password_hash`。
  4. 在 `sys_user_role` 表中插入 admin 与系统管理员角色（UUID `b0000000-0000-0000-0000-000000000001`）的关联。
- **响应**：
  ```json
  { "code": 200, "msg": "success", "data": {} }
  ```

### 1.5 修改密码（登录用户自助）

- **路径**：`POST /api/auth/change-password`
- **请求体**：`{ "oldPassword": "xxx", "newPassword": "xxx" }`
- **校验**：新密码长度 8-64 字符（后端不做字符类型三选四强度校验，强度仅作前端提示）；原密码不匹配返回错误码 1000001007（“原密码错误”）。
- **处理**：取当前登录用户，校验原密码后重新加密存储 password_hash。
- **说明**：改密成功后调用 `UserDetailsBridge.revokeByUserId(userId)` 写入**签发截止**（见 §5），使该用户此前签发的 access/refresh token 全部失效；重新登录签发的新 token 不受影响。前端改密成功后仍强制跳转登录页。


## 2. 密码策略

- 强度：8~64 字符，包含大写字母、小写字母、数字、特殊字符中至少三种。
- 存储：使用 bcrypt（cost ≥ 10）或 argon2id 单向哈希。
- 重置密码：管理员输入新密码，后端不返回明文。


## 3. 系统初始化流程

**触发条件**：平台首次部署后，`sys_user` 表无记录。

```
用户访问任意页面 → 路由守卫（无 token）→ /login
  ↓
LoginPage onMounted → GET /api/auth/init/status
  ↓
未初始化（initialized=false）→ router.replace('/init')
  ↓
InitPage 展示密码设置表单
  ↓
用户输入密码并确认 → POST /api/auth/init/setup({ password })
  ↓
后端校验：
  1. sys_user 表已存在记录 → 返回 SYSTEM_ALREADY_INITIALIZED
  2. 无记录 → 创建 admin 用户（username=admin, name=系统管理员, email=admin@robotest.local）
  3. bcrypt/argon2 哈希密码
  4. sys_user_role 插入 admin 与系统管理员角色关联
  ↓
返回成功 → 前端跳转 /login
  ↓
用户使用 admin / 已设密码登录
```

**业务规则**：

- 初始化接口无需认证，所有路径均允许匿名访问。
- 系统管理员角色预置 UUID `b0000000-0000-0000-0000-000000000001`，初始化时硬编码引用。
- admin 账号为安装时自动创建，不存在手动注册管理员的功能。
- 初始化完成后登录页正常展示登录表单，不再出现初始化引导。


## 4. 登录审计写入

见 `docs/04-detailed-design/20-audit-query.md` §4.3（AuditLogWriter 共享写入、`ClientIpResolver`、`operation='LOGIN'` 记录结构）。数据概览仅消费其 `LOGIN` 记录做 3.6 口径统计。

## 5. 登出与 Token 撤销

### 5.1 登出

- **路径**：`POST /api/auth/logout`（无需业务鉴权，匿名可访问）
- **请求头**：`Authorization: Bearer <accessToken>`，可选 `X-Refresh-Token: <refreshToken>`
- **处理**：分别对 access token 与 refresh token 调用 `UserDetailsBridge.clean(token)` 写入黑名单后返回成功。
- **说明**：
  - 框架 `LogoutFilter` 仅撤销 Authorization 中的 access token，**不承载**本接口，`migoo.security.logout-url` 配置为 `/logout`（框架默认路径）；
  - 黑名单写入失败不阻断登出返回（框架 `StateStore` 失败开放），仅记录 WARN；
  - 前端 `authStore.logout()` 先调用本接口（best-effort），再清理本地状态并跳转登录页。

### 5.2 撤销模型

撤销通过 `UserDetailsBridge` 钩子走框架 `StateStore`（Redis），两类键：

| 语义 | 键 | 值 | TTL | 使用点 |
| --- | --- | --- | --- | --- |
| 单 token 黑名单 | `security:token:blacklist:{token}` | `1` | 7 天 | 登出 `clean(token)` |
| 签发截止 | `security:user:revoked-before:{userId}` | 撤销时刻（epoch 秒） | 不设置 | 踢人 `revokeByUserId(userId)` |

校验规则（`isTokenRevoked`）：命中字面黑名单，或 token 的 `iat` 早于该用户的签发截止时刻，即视为已撤销；`isUserRevoked` 恒返回 `false`，避免封死重新登录。

### 5.3 触发点

| 触发点 | 动作 | 效果 |
| --- | --- | --- |
| 登出 | `clean(access)` + `clean(refresh)` | 仅本次会话的两个 token 失效 |
| 管理员禁用 / 锁定用户、批量状态变更 | `revokeByUserId(userId)` | 该用户全部存量 token 失效，且无法重新登录（状态仍禁用） |
| 管理员重置密码、自助改密 | `revokeByUserId(userId)` | 存量 token 失效，重新登录后新 token 生效 |
| 用户启用 | 不清理键 | 旧的签发截止早已早于新签发时间，自然失效 |

---

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-09-23 | 初版起草 |
| V1.0 | 2026-09-28 | 限流口径改为框架 `@RateLimit` / 登录失败锁定（429 / 423）；改密改为服务端签发截止撤销；新增 §5 登出与 Token 撤销 |

---


