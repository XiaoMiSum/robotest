# 软件测试平台——用户管理

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 用户管理接口

> 通用约定：响应示例仅展示 `Result<T>` 的 `data` 字段内容（通用响应与分页协议见 `docs/00-spec/20-contracts/01-api.md`）；资源 ID 均为 UUID 字符串；本域接口不涉及活动上下文，请求不携带 `X-Active-Workspace` / `X-Active-Project` 头，URL 与请求体中亦无上下文字段。管理端各接口的权限码汇总见 `docs/04-detailed-design/01-system-management/02-system-management-overview.md` 2.6。

### 1.1 获取用户列表

- **路径**：`GET /api/admin/users`
- **权限**：`user:view` 或 `role:view`（角色分册的系统角色关联用户列表复用本接口）
- **请求参数**：

| 参数       | 类型     | 必填  | 说明                                  |
| -------- | ------ | --- | ----------------------------------- |
| keyword  | string | 否   | 按用户名 / 姓名 / 邮箱模糊匹配                  |
| status   | string | 否   | active / disabled / locked 三态（取值口径见 `docs/04-detailed-design/01-system-management/02-system-management-overview.md` 2.2），不传则不过滤 |
| roleId   | string | 否   | 筛选拥有该角色的用户（UUID）；命中记录回填 `grantedAt`   |
| pageNo   | int    | 否   | 页码，默认 1                             |
| pageSize | int    | 否   | 每页数量，默认 20，最大 100                   |

- **响应数据**：

  ```json
  {
    "list": [
      {
        "id": "8f1d3c2e-9a4b-4c6d-8e7f-0a1b2c3d4e5f",
        "username": "zhangsan",
        "name": "张三",
        "email": "zhangsan@example.com",
        "avatarUrl": "https://...",
        "status": "active",
        "roles": [
          { "id": "2f6a1b2c-3d4e-4f50-8a9b-1c2d3e4f5a6b", "name": "用户管理员", "type": "system" }
        ],
        "workspaces": [
          { "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "name": "电商平台", "workspaceRole": "member" }
        ],
        "createdAt": "2026-01-15T10:30:00",
        "updatedAt": "2026-06-20T14:00:00",
        "grantedAt": null
      }
    ],
    "total": 45
  }
  ```

- **说明**：`grantedAt` 仅在按 `roleId` 过滤时回填为 `sys_user_role.updated_at`，其余场景为 `null`；`name` 为用户显示姓名，列表与表单均依赖该字段。

### 1.2 创建用户

- **路径**：`POST /api/admin/users`
- **权限**：`user:create`

- **请求体**：

  ```json
  {
    "username": "lisi",
    "name": "李四",
    "email": "lisi@example.com",
    "password": "Abc@123456",
    "roleIds": ["2f6a1b2c-3d4e-4f50-8a9b-1c2d3e4f5a6b"]
  }
  ```

- **校验规则**：

  - `username`：必填，3~30 字符，不可与已有用户名重复（错误码 1000001002）；字符集 `^[a-zA-Z0-9_-]+$` 由前端表单校验。
  - `name`：必填，不超过 50 字符，用户显示名称。
  - `email`：必填，合法邮箱格式，不可与已有邮箱重复（错误码 1000001003）。
  - `password`：必填，8~64 字符。
  - `roleIds`：可选，省略或空数组表示不分配角色；选项来自系统角色列表 `GET /api/admin/roles?type=system`，后端按传入 ID 直接写入关联，不校验角色存在性与类型。

- **处理逻辑**：用户名 / 邮箱唯一性校验 → 密码哈希（Spring `PasswordEncoder`）→ 事务内插入 `sys_user`（`status` 置 `active`），`roleIds` 非空时逐条插入 `sys_user_role`。

- **响应**：`Result<String>`，`data` 为新建用户 ID 字符串，HTTP 200。

### 1.3 获取用户详情

- **路径**：`GET /api/admin/users/:id`
- **权限**：`user:view`
- **响应**：返回单个用户的完整信息（字段同 1.1，含 `roles` 与 `workspaces` 列表）；用户不存在返回错误码 1000003001。

### 1.4 更新用户

- **路径**：`PUT /api/admin/users/:id`
- **权限**：`user:edit`

- **请求体**：

  ```json
  {
    "name": "李四",
    "email": "newemail@example.com",
    "roleIds": ["2f6a1b2c-3d4e-4f50-8a9b-1c2d3e4f5a6b"]
  }
  ```

- **校验规则**：

  - 不可修改 `username`：请求体不含该字段，前端编辑态用户名输入框禁用。
  - `email` 传入时须为合法邮箱格式；`name` 传入时非空才更新。前端表单对 `name` 限 50 字符、对 `email` 做必填与格式校验。
  - `roleIds` 可选：传入（含空数组）则全量替换角色关联，不传则保持原关联不变。

- **处理逻辑**：部分更新（C11）——仅更新实际传入的 `name` / `email` 字段，更新载体为新建实体；`roleIds` 传入时先删除该用户旧的 `sys_user_role` 关联，再逐条插入新关联。

- **响应**：返回更新后的用户信息（含 `roles`、`workspaces`）。

### 1.5 更新用户状态

- **路径**：`PATCH /api/admin/users/:id/status`
- **权限**：`user:disable`
- **请求体**：`{ "status": "disabled" }`
- **校验**：`status` 取值为 `active | disabled | locked` 三态，非法值返回错误码 1000001010（错误码定义见 `docs/04-detailed-design/01-system-management/02-system-management-overview.md` 3. 错误码定义，取值口径见其 2.2）；不限制操作自身账户（见 4）。
- **处理**：存在性校验后仅更新 `status` 字段（新建更新载体，C11）；置为 disabled / locked 时立即写入该用户签发截止，撤销全部存量 Token；置为 active 不写入清理（见 4）。
- **响应**：返回更新后的用户信息。

### 1.6 批量操作

- **路径**：`PATCH /api/admin/users/batch-status`
- **权限**：`user:disable`
- **请求体**：`{ "userIds": ["...", "..."], "status": "disabled" }`
- **校验**：`status` 取值同 1.5，非法值返回错误码 1000001010。
- **处理**：事务内逐用户仅更新 `status` 字段；置为 disabled / locked 时同样撤销该用户存量 Token；目标用户不存在时跳过该用户，不中断整体处理。
- **响应**：`Result<Void>`，无返回数据。

### 1.7 重置密码

- **路径**：`POST /api/admin/users/:id/reset-password`
- **权限**：`user:reset-password`；接口限流 10 次 / 300 秒
- **请求体**：`{ "newPassword": "NewPass@123" }`
- **校验**：新密码必填，8~64 字符。
- **处理**：更新密码哈希，并写入签发截止，强制该用户全部存量 Token 失效（触发点见 `docs/04-detailed-design/01-system-management/03-system-management-auth.md` 5.3）。
- **响应**：`Result<Void>`，不返回密码。

### 1.8 用户精简列表

- **路径**：`GET /api/admin/users/simple`
- **权限**：`user:view` 或 `workspace:create` 或 `workspace:manage-members` 或 `role:view`
- **请求参数**：`keyword`（string，可选，按姓名 / 用户名 / 邮箱模糊匹配；仅返回 `active` 用户）
- **响应**：`[{ "id": "8f1d3c2e-9a4b-4c6d-8e7f-0a1b2c3d4e5f", "name": "张三" }]`
- **用途**：用户选择器组件（`web/src/components/admin/UserPickerDialog.vue`）的远程搜索数据源。


## 2. 用户创建流程

```
POST /api/admin/users
  ├── 1. 参数校验（@Valid：用户名 3-30、姓名 ≤50、邮箱格式、密码 8-64）
  ├── 2. 唯一性校验（用户名重复 → 1000001002；邮箱重复 → 1000001003）
  ├── 3. 密码哈希处理（Spring PasswordEncoder）
  ├── 4. 开启事务
  │     ├── 插入 sys_user 表（status = active）
  │     └── roleIds 非空时逐条插入 sys_user_role 表
  ├── 5. 提交事务
  └── 6. 返回新建用户 ID 字符串
```


## 3. 用户更新流程

```
PUT /api/admin/users/:id
  ├── 1. 校验目标用户存在（1000003001）；请求体不含 username，不可修改用户名
  ├── 2. 校验 email 格式（若传入）
  ├── 3. 开启事务
  │     ├── 部分更新 sys_user（仅写入实际传入的 name / email，C11）
  │     └── roleIds 传入（含空数组）时全量替换 sys_user_role（先删后插）
  └── 4. 返回更新后的用户信息（含 roles / workspaces）
```


## 4. 用户状态变更、禁用与强制下线

- `updateUserStatus` / `batchUpdateStatus`：先校验状态取值合法性（`status ∈ {active, disabled, locked}`，非法值返回错误码 1000001010，见 1.5 与总览分册 3. 错误码定义），单个操作另做目标用户存在性校验（1000003001）、批量操作跳过不存在的用户；更新仅写 `status` 字段，载体为新建实体（C11）。
- 无「不可操作自身」限制：管理员可对自己的账户执行禁用 / 锁定 / 批量状态变更，与禁用其他账户行为一致。
- 管理员将用户状态置为 disabled（或 locked）后，系统立即写入该用户的**签发截止**（框架 `StateStore`），该用户此前签发的 access/refresh Token 全部失效；置为 active 时不清理键，旧的签发截止早于新签发时间、自然失效。
- 框架认证时经 `UserDetailsBridge.isTokenRevoked(token)` 校验（黑名单或签发截止），命中返回 401（错误码 1000002005）；账号仍处于 disabled/locked 时重新登录同样被拒。
- 管理员重置密码后同样触发签发截止撤销（触发点见 `docs/04-detailed-design/01-system-management/03-system-management-auth.md` 5.3）。


## 5. 前端设计

### 5.1 路由与菜单

| 路径 | 路由 name | meta.title | 组件 | 菜单 |
| ---- | ---- | ---- | ---- | ---- |
| /admin/users | AdminUsers | 用户管理 | `web/src/pages/admin/UserListPage.vue` | ✅ 侧栏菜单「用户管理」（icon `User`、order 10、section「组织与权限」、权限 `user:view`） |
| /admin/users/create | AdminUserCreate | 新建用户 | `web/src/pages/admin/UserFormPage.vue` | ✗ 不进菜单 |
| /admin/users/:id | AdminUserEdit | 编辑用户 | `web/src/pages/admin/UserFormPage.vue` | ✗ 不进菜单 |

- 三条路由均挂在 `/admin` 下（`meta.requiresAuth` + `meta.requiresAdmin`），完整路由与菜单注册表见 `docs/04-detailed-design/01-system-management/02-system-management-overview.md` 2.7。
- 新建与编辑共用 `UserFormPage.vue`，以有无 `:id` 参数区分；面包屑由页面自身渲染（`用户管理 > 新建用户 / 编辑用户`，回退链指向 `/admin/users`）。
- 导航守卫在权限列表未加载时先 `loadPermissions()` 再判定 `hasSystemRole || hasSystemPermission`，不满足重定向 `/`；侧栏菜单项按权限码 `user:view` 过滤。

### 5.2 用户列表页（`web/src/pages/admin/UserListPage.vue`）

```
用户列表（页头：标题「用户管理」+ 描述「平台账号、角色与启用状态」+「新建用户」按钮）
└── 单卡片
    ├── 工具栏：关键词输入（回车查询）｜状态下拉（启用/禁用/锁定）｜角色下拉（仅系统角色，可搜索）
    │          ｜查询按钮｜重置按钮 …………………… 右侧「共 N 个账号」
    ├── 表格（v-loading，多选列 row-key="id"）
    │   列：选择｜用户（头像首字 + 用户名链接 + 姓名）｜邮箱｜状态（圆点 + 三态文案）
    │       ｜系统角色（描边标签，无角色显示 -）｜创建时间（formatDateTime）｜操作（fixed 右侧）
    ├── 批量操作区（勾选后出现「批量禁用 / 批量启用」，未勾选时保留占位避免分页条跳动）
    └── 分页条：total, sizes, prev, pager, next；可选 10 / 20 / 50 / 100，默认 20
```

- **筛选**：`keyword`（用户名 / 邮箱搜索框，回车触发）、`status`（三态下拉，变更即查询）、`roleId`（系统角色下拉，选项来自 `GET /api/admin/roles?type=system`，加载失败静默忽略、不阻塞主列表）；「查询」重置到第 1 页，「重置」清空三个条件后重新加载。
- **操作列**：编辑（跳转 `/admin/users/:id`）；启用 / 禁用 / 锁定按钮按当前状态互斥显示（已是该状态则不显示对应按钮），均需 `ElMessageBox.confirm` 二次确认，成功后提示并刷新列表；重置密码打开弹窗。
- **重置密码弹窗**：提示「8-64 个字符」，前端校验非空与长度（8-64），提交中按钮 loading，成功关闭弹窗；失败以 `ElMessage.error` 展示后端消息。
- **批量操作**：勾选行后页脚出现「批量禁用 / 批量启用」（无批量锁定），二次确认后提交选中 ID，成功刷新列表；未勾选时点击不发起请求。
- **分页**：切换页码直接加载；切换每页条数回到第 1 页。

### 5.3 新建 / 编辑表单页（`web/src/pages/admin/UserFormPage.vue`）

- **结构**：面包屑 → 页头（标题 + 描述 + 取消 / 保存）→ 双列卡片表单（基本信息 ｜ 系统角色）。
- **字段与校验**：

| 字段 | 新建态 | 编辑态 | 校验规则 |
| ---- | ---- | ---- | ---- |
| 用户名 | 可编辑，提示「3-30 个字符，允许字母、数字、_、-」 | 输入框禁用（只读） | 必填、3-30、`^[a-zA-Z0-9_-]+$` |
| 姓名 | ✅ | ✅ | 必填、≤50 字符（`maxlength` 50 + 字数统计） |
| 邮箱 | ✅ | ✅ | 必填、邮箱格式 |
| 密码 | 输入框（`show-password`）+ 密码强度条 | 无输入框，仅「修改密码」入口 | 必填（仅新建态）、8-64 字符 |
| 系统角色 | 复选框组（选项 `GET /api/admin/roles?type=system`） | 同左，回填用户当前 `roles` | 可空，无必填约束 |

- **空间归属**：表单不提供空间字段；用户与空间的归属关系由空间管理侧维护，详情响应中的 `workspaces` 仅作为数据返回。
- **保存**：新建提交 `{ username, name, email, password, roleIds }` → `POST /api/admin/users`；编辑提交 `{ name, email, roleIds }` → `PUT /api/admin/users/:id`。成功提示「保存成功」并返回 `/admin/users`，失败以 `ElMessage.error` 展示消息，提交期间保存按钮 loading。
- **编辑加载**：整页 `v-loading` 拉取 `GET /api/admin/users/:id` 回填表单，失败以 `ElMessage.error` 提示；角色选项加载失败静默忽略、不阻塞表单。
- **修改密码弹窗（编辑态）**：调用 `POST /api/admin/users/:id/reset-password`，前端仅校验非空，8-64 长度由后端校验；带密码强度条，成功后关闭弹窗并清空输入。

### 5.4 状态分支与权限

| 状态 | 列表页 | 表单页 |
| ---- | ---- | ---- |
| 加载中 | 表格 `v-loading` | 整页 `v-loading` |
| 空态 | Element Plus 表格默认空态文案（无自定义空态插槽） | 新建态为空表单；编辑态见加载中 |
| 错误 | 列表 / 状态变更 / 批量 / 重置密码失败均由 `ElMessage.error` 展示；角色筛选项加载失败静默忽略 | 详情加载 / 保存 / 修改密码失败均由 `ElMessage.error` 展示 |
| 提交中 | 重置密码确定按钮 loading | 保存、修改密码按钮 loading |
| 权限不足 | 菜单项按 `user:view` 过滤不展示；`/admin` 守卫不满足重定向 `/`；接口鉴权或业务错误经统一拦截器转为 `Error`，由页面消息提示呈现，无独立 403 视图 | 同左（创建 / 编辑权限由后端接口 `user:create` / `user:edit` 校验） |

> 列表的加载、筛选与操作失败必须可见（UI-PAGE-11）；创建、编辑、状态变更与重置密码的最终把关在后端权限码。

### 5.5 用户选择器组件（`web/src/components/admin/UserPickerDialog.vue`）

- 复用方：角色管理的关联用户表（`web/src/components/admin/RoleUsersTable.vue`）等；本域数据源为 1.8 的精简列表。
- 远程多选搜索：输入姓名触发 `GET /api/admin/users/simple?keyword=`（仅活跃用户），候选按 `excludeIds` 过滤已关联用户；打开弹窗时重置选择与候选。
- 可选空间多选（`showWorkspace`）：展示「选择要关联的空间」分隔行，选项来自 `GET /api/admin/workspaces?pageNo=1&pageSize=100`。
- 确定：未选用户提示「请至少选择一个用户」；启用空间选择且未选空间提示「请至少选择一个空间」，均不提交；通过后向父组件 emit `confirm(userIds, workspaceIds?)`。


## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 按前端实现对齐用户管理接口路径/字段/权限码、业务流程与前端页面设计（列表、表单、状态分支、用户选择器） |
