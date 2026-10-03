# 软件测试平台——认证

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 认证接口

> **接口限流（安全规范 6.1）**：认证与公开接口统一使用框架 `@RateLimit`（按 IP 计数）与框架登录失败锁定；超限返回框架全局错误码 **429**，账号连续登录失败达阈值返回框架全局错误码 **423**（`ACCOUNT_LOCKED`）。各接口的 limit/window 及账号锁定口径见《安全规范》6.1 实施口径。

### 1.1 登录

- **路径**：`POST /api/auth/login`
- **请求体**：`{ "identifier": "admin", "password": "xxx" }`（`identifier` 为用户名或邮箱）
- **响应**（框架 `LoginResult`）：

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
      "name": "系统管理员",
      "email": "admin@robotest.local",
      "avatarUrl": null,
      "permissions": ["user:view", "role:view", "..."],
      "hasWorkspace": true
    }
  }
}
```

> **注意**：用户信息由框架 `LoginResult` 直接以 `user` 字段承载（`LoginUser`）。前端通过 `user.hasWorkspace` 决定登录后跳转目标（分支见 6.3），登录响应的 `accessToken` / `refreshToken` 由前端存入 sessionStorage；登录成功同时写入登录审计（见 4）。

### 1.2 获取当前用户权限

- **路径**：`POST /api/auth/permissions`
- **说明**：返回当前用户的合并权限码列表（系统权限 + 当前工作空间权限）。前端请求拦截器按接口域自动附加 `X-Active-Workspace` 请求头（存在活动空间时），无活动空间时仅返回系统权限。
- **请求头**：`X-Active-Workspace: <workspaceId>`（可选，用于获取工作空间权限）
- **响应**（`data` 为字符串数组，前端经响应拦截器解包后直接得到权限码列表）：

```json
{
  "code": 200,
  "msg": "success",
  "data": ["user:view", "case:create"]
}
```

- **处理逻辑**：合并 `LoginUser` 的系统权限与 `WorkspaceRoleInterceptor` 注入的空间角色权限，去重并过滤 `ROLE_` 前缀后返回。

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
- **说明**：改密成功后调用 `UserDetailsBridge.revokeByUserId(userId)` 写入**签发截止**（见 §5），使该用户此前签发的 access/refresh token 全部失效；重新登录签发的新 token 不受影响。
- **前端（`ChangePasswordDialog` + `useChangePassword`）**：
  - 表单字段：原密码、新密码、确认密码（均 `show-password`），新密码下方展示 `PasswordStrengthBar` 强度条（仅展示、不阻断）；打开对话框时三个字段重置为空。
  - 提交校验（依次）：原密码必填 → 新密码长度 8-64 → 两次输入一致，不满足时 `ElMessage.warning` 并不发请求。
  - 成功：关闭对话框，提示“密码已修改，请重新登录”，调用 `authStore.logout()`（best-effort 撤销服务端令牌）后 `router.push('/login')`。
  - 失败：`ElMessage.error(后端 msg 或“修改密码失败”)`，对话框保持打开。

### 1.6 刷新令牌

- **路径**：`POST /api/auth/refresh`
- **请求头**：`X-Refresh-Token: <refreshToken>`（无请求体，匿名可达，仅信任请求头中的 refresh token）
- **响应**：同 1.1 的 `LoginResult`，前端仅取 `accessToken` / `refreshToken` 覆盖 sessionStorage 中的旧令牌。
- **前端触发时机（`web/src/services/index.ts` 响应拦截器）**：
  - HTTP 状态 401 与「HTTP 200 + 业务码 401」两路统一汇聚到刷新逻辑；
  - 单飞：并发 401 只发起一次刷新，其余请求排队，刷新成功后按原配置重放；
  - 刷新失败：判定会话失效（后续 401 直接失败，不再重复刷新），清理令牌与活动上下文，并一次性跳转 `/login`；
  - 任意成功响应会解除会话失效标记（覆盖重新登录后的场景）。


## 2. 密码策略

- 强制口径：8~64 字符，初始化、改密、新建/重置用户的前后端同口径校验（后端 `@Size(min=8, max=64)`，前端提交前先行校验）。
- 建议口径：包含大写字母、小写字母、数字、特殊字符中至少三种；仅由 `PasswordStrengthBar`（4 段强度条：弱/较弱/中/强，不足 8 字符按长度比例展示）与初始化页提示条可视化提示，不阻断提交。
- 存储：使用 bcrypt（cost ≥ 10）或 argon2id 单向哈希。
- 重置密码：管理员输入新密码，后端不返回明文。


## 3. 系统初始化流程

**触发条件**：平台首次部署后，`sys_user` 表无记录。

```
用户访问受保护页面 → 路由守卫（无 token）→ /login
  ↓
LoginPage onMounted → GET /api/auth/init/status
  ├─ 网络错误 → 静默失败，继续展示登录表单
  ↓
未初始化（initialized=false）→ router.replace('/init')
  ↓
InitPage onMounted 再次 GET /api/auth/init/status
  ├─ 已初始化（initialized=true）→ router.replace('/login')
  ├─ 网络错误 → 继续展示初始化表单
  ├─ 检查中 → 展示“检查系统状态...”加载态
  ↓
展示初始化表单：登录名（admin，禁用）、管理员密码、确认密码
  ├─ PasswordStrengthBar 密码强度条（弱/较弱/中/强，仅展示）
  ├─ 实时提示（仅提示，不阻断）：8-64 个字符 / 包含字母 / 包含数字与符号更安全
  ↓
提交校验（依次，未通过 ElMessage.warning 且不发请求）：
  1. 密码必填
  2. 密码长度 8-64
  3. 两次输入一致
  ↓
POST /api/auth/init/setup({ password })
  ↓
后端校验：
  1. sys_user 表已存在记录 → 返回 SYSTEM_ALREADY_INITIALIZED
  2. 无记录 → 创建 admin 用户（username=admin, name=系统管理员,
     email=admin@robotest.local, status=active）
  3. bcrypt/argon2 哈希密码
  4. sys_user_role 插入 admin 与系统管理员角色关联
  ↓
成功 → 提示“系统初始化成功，请登录” → router.push('/login')
失败 → ElMessage.error(后端 msg 或“初始化失败”)，停留在初始化页
  ↓
用户使用 admin / 已设密码登录
```

**业务规则**：

- 初始化接口无需认证，所有路径均允许匿名访问。
- `/login`、`/init` 均为公共路由（`meta.public`）；已持有 access token 时访问任一公共路由会被守卫重定向到 `/`（见 6.1），因此初始化页只在未登录状态下可达。
- 系统管理员角色预置 UUID `b0000000-0000-0000-0000-000000000001`，初始化时硬编码引用。
- admin 账号为安装时自动创建，不存在手动注册管理员的功能。
- 初始化完成后登录页正常展示登录表单，不再出现初始化引导；登录页页脚固定展示“首次部署？系统将自动引导完成初始化”。


## 4. 登录审计写入

见 `docs/04-detailed-design/01-system-management/08-audit-query.md` §4.3（AuditLogWriter 共享写入、`ClientIpResolver`、`operation='LOGIN'` 记录结构）。数据概览仅消费其 `LOGIN` 记录做 3.6 口径统计。

## 5. 登出与 Token 撤销

### 5.1 登出

- **路径**：`POST /api/auth/logout`（无需业务鉴权，匿名可访问）
- **请求头**：`Authorization: Bearer <accessToken>`，可选 `X-Refresh-Token: <refreshToken>`
- **处理**：分别对 access token 与 refresh token 调用 `UserDetailsBridge.clean(token)` 写入黑名单后返回成功。
- **说明**：
  - 框架 `LogoutFilter` 仅撤销 Authorization 中的 access token，**不承载**本接口，`migoo.security.logout-url` 配置为 `/logout`（框架默认路径）；
  - 黑名单写入失败不阻断登出返回（框架 `StateStore` 失败开放），仅记录 WARN；
  - 前端 `revokeSession()` 对调用时的双令牌做请求头快照、3 秒超时并绕过拦截器（避免过期 access 触发无谓刷新）；`authStore.logout()` 先 best-effort 调用本接口，失败（离线/超时）也不阻断本地状态清理；跳转登录页由调用方完成（顶栏菜单登出、改密成功，见 1.5）。

### 5.2 撤销模型

撤销通过 `UserDetailsBridge` 钩子走框架 `StateStore`（Redis），两类键：

| 语义 | 键 | 值 | TTL | 使用点 |
| --- | --- | --- | --- | --- |
| 单 token 黑名单 | `security:token:blacklist:{token}` | `1` | 7 天 | 登出 `clean(token)` |
| 签发截止 | `security:user:revoked-before:{userId}` | 撤销时刻（epoch 秒） | 30 天 | 踢人 `revokeByUserId(userId)` |

> 黑名单 TTL 取 7 天（不短于 Refresh Token 上限有效期），签发截止 TTL 取 30 天（远长于 Token 最长有效期）：两者键到期自然清理，无需额外回收任务。

校验规则（`isTokenRevoked`）：命中字面黑名单，或 token 的 `iat` 早于该用户的签发截止时刻，即视为已撤销；`isUserRevoked` 恒返回 `false`，避免封死重新登录。

### 5.3 触发点

| 触发点 | 动作 | 效果 |
| --- | --- | --- |
| 登出 | `clean(access)` + `clean(refresh)` | 仅本次会话的两个 token 失效 |
| 管理员禁用 / 锁定用户、批量状态变更 | `revokeByUserId(userId)` | 该用户全部存量 token 失效，且无法重新登录（状态仍禁用） |
| 管理员重置密码、自助改密 | `revokeByUserId(userId)` | 存量 token 失效，重新登录后新 token 生效 |
| 用户启用 | 不清理键 | 旧的签发截止早已早于新签发时间，自然失效 |

## 6. 前端登录与权限加载

### 6.1 公共路由与导航守卫

| 路径 | 名称 | 组件 | meta |
| --- | --- | --- | --- |
| `/login` | `Login` | `pages/auth/LoginPage.vue` | `public: true`、`title: 登录` |
| `/init` | `Init` | `pages/auth/InitPage.vue` | `public: true`、`title: 初始化` |

`router.beforeEach` 顺序判定：

1. 目标为公共路由：已持有 access token → 重定向 `/`（`/` 再重定向 `/workspaces`）；否则放行。
2. `requiresAuth` 且无 token → 重定向 `/login` 并写入 `query.redirect = to.fullPath`。
3. `requiresAdmin`：权限列表为空时先 `await loadPermissions()` 再判定 `hasSystemRole || hasSystemPermission`（`hasSystemRole` = `user.roles` 含 `system`/`SYSTEM`，`hasSystemPermission` 见 6.3），不满足 → 重定向 `/`（避免管理员刷新 `/admin` 被误判）。
4. `afterEach`：`document.title = "<title> - RoboTest"`。

> 守卫写入的 `redirect` 查询参数仅记录来源，登录页不读取；登录成功后的去向按 6.3 分支决定。

### 6.2 登录表单与反馈

- 表单字段：账号（`identifier`，占位“用户名 / 邮箱”，可清空）、密码（`show-password`）。
- 前置校验：账号或密码为空 → `ElMessage.warning('请输入用户名/邮箱和密码')`，不发起请求。
- 提交：`POST /api/auth/login` → `authStore.setLogin(accessToken, refreshToken, user, null)` 持久化令牌与用户 → `await authStore.loadPermissions()`。密码框回车与点击“登 录”等价；请求期间按钮为 loading 态。
- 成功：`ElMessage.success('登录成功')`，按 6.3 跳转。
- 失败：`ElMessage.error(后端 msg 或“登录失败”)`，停留在登录页；认证过期（业务码/HTTP 401）先走 1.6 的刷新重放。

### 6.3 登录成功跳转分支

按顺序判定（权限在 `loadPermissions()` 完成后可用）：

1. `user.hasWorkspace === true` → `Workspaces`（我的空间，`/workspaces`）；
2. 否则持系统权限（权限码以 `user:` / `workspace:` / `role:` 开头的任一项）→ `AdminDashboard`（`/admin/dashboard`）；
3. 否则 → `Workspaces`。

### 6.4 权限加载时序

- **store 创建**：本地已恢复用户 → 立即异步 `loadPermissions()`（页面刷新后权限从远端回拉，不以活动空间为前置条件）；无用户 → 直接置 `permissionsLoaded = true`。
- **`loadPermissions()`**：并发调用复用同一次在途拉取；拉取期间 `permissionsLoaded = false`，完成（含失败）后置就绪，失败时权限置空。
- **登录**：`setLogin` 写入令牌与用户并清空活动上下文（登录时尚无工作空间），登录页随后 `await loadPermissions()`，以 `POST /api/auth/permissions` 的返回作为登录后的权限集。
- **切换工作空间**：`setActiveWorkspace(space)` 重新拉取，请求经拦截器附带 `X-Active-Workspace`；退出空间则清空权限列表。
- **管理端守卫**：见 6.1 第 3 条，权限未就绪时先等待拉取完成再判定。

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-02 | 对齐前端实现：登录请求字段与响应结构、权限接口响应形态、初始化与改密表单校验及反馈、新增刷新令牌与前端登录/权限加载时序章节 |
