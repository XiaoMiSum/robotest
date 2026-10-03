# 软件测试平台——空间管理业务模块详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台中的**空间管理业务模块**进行详细设计，定义数据结构、接口规范、业务逻辑、前端组件及交互流程，为开发实现提供完整依据。

### 1.2 范围

空间管理业务模块面向**业务用户**，包含**我的空间**、**空间信息**、**成员管理**、**项目列表**及**公开邀请加入**五个子模块。除我的空间列表与公开邀请接口外，其余操作均限定在当前活跃工作空间上下文中，工作空间上下文通过请求头 `X-Active-Workspace` 传递（边界见 2.2.1）。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》
- 《软件测试平台概要设计说明书》
- 《全局导航与菜单交互系统设计》
- 《空间管理业务模块页面交互设计》

---


## 2. 数据设计

### 2.1 数据库表设计

数据库使用 PostgreSQL，字段使用 snake_case，接口 JSON 使用 camelCase；建表语句见 `server/src/main/resources/db/schema.sql`。

#### 2.1.1 工作空间表（ws_workspace）

| 字段          | 类型                         | 约束                                    | 说明     |
| ----------- | -------------------------- | ------------------------------------- | ------ |
| id          | UUID                       | PK                                     | 工作空间ID |
| name        | VARCHAR(50)                | NOT NULL                               | 名称     |
| description | VARCHAR(500)               | NULL                                   | 描述     |
| status      | VARCHAR(20)                | NOT NULL, DEFAULT 'active'             | 状态（active / dissolved） |
| created_by  | UUID                       | NULL                                   | 创建者    |
| created_at  | TIMESTAMP                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP    | 创建时间   |
| updated_at  | TIMESTAMP                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP    | 更新时间   |
| is_deleted  | BOOLEAN                    | NOT NULL, DEFAULT FALSE                | 是否删除   |

**索引**：`uk_ws_workspace_name` UNIQUE (name) WHERE is_deleted = false、`idx_ws_workspace_created` (created_at DESC)、`idx_ws_workspace_created_by` (created_by)

#### 2.1.2 邀请链接表（ws_invitation）

| 字段           | 类型                       | 约束                                  | 说明     |
| ------------ | ------------------------ | ----------------------------------- | ------ |
| id           | UUID  | PK                                   | 邀请ID   |
| workspace_id | UUID  | NOT NULL                             | 目标工作空间 |
| token        | VARCHAR(64) | NOT NULL                            | 邀请令牌   |
| created_by   | UUID  | NOT NULL                             | 创建者    |
| expires_at   | TIMESTAMP    | NULL                                | 过期时间   |
| max_uses     | INT         | NULL                                | 最大使用次数 |
| use_count    | INT         | NOT NULL, DEFAULT 0                 | 已使用次数  |
| status       | VARCHAR(20) | NOT NULL, DEFAULT 'active' | 状态（active / revoked） |
| created_at   | TIMESTAMP    | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间   |
| updated_at   | TIMESTAMP    | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 更新时间   |
| is_deleted   | BOOLEAN  | NOT NULL, DEFAULT FALSE                 | 是否删除   |

**索引**：`uk_ws_invitation_token` UNIQUE (token) WHERE is_deleted = false、`idx_ws_invitation_ws_created` (workspace_id, created_at DESC)

#### 2.1.3 用户-工作空间关联表（ws_user）

| 字段                 | 类型                     | 约束                                  | 说明      |
| ------------------ | ---------------------- | ----------------------------------- | ------- |
| id                 | UUID             | PK                                   | 关联ID    |
| user_id            | UUID             | NOT NULL                             | 用户ID    |
| workspace_id       | UUID             | NOT NULL                             | 工作空间ID  |
| workspace_role     | UUID             | NOT NULL, DEFAULT 'c0000000-0000-0000-0000-000000000002' | 工作空间内角色（引用 sys_role.id，type='workspace'） |
| default_project_id | UUID             | NULL                                 | 个人默认项目  |
| joined_at          | TIMESTAMP               | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 加入时间    |
| last_accessed_at   | TIMESTAMP               | NULL                                 | 当前用户最近一次成功进入该空间的时间；未进入过为空 |
| is_deleted         | BOOLEAN             | NOT NULL, DEFAULT FALSE                  | 是否删除    |
| created_at         | TIMESTAMP             | NOT NULL, DEFAULT CURRENT_TIMESTAMP      | 创建时间    |
| updated_at         | TIMESTAMP             | NOT NULL, DEFAULT CURRENT_TIMESTAMP      | 更新时间    |

**索引**：`uk_ws_user_user_workspace` UNIQUE (user_id, workspace_id) WHERE is_deleted = false、`idx_ws_user_workspace_id` (workspace_id)、`idx_ws_user_ws_role` (workspace_id, workspace_role)、`idx_ws_user_default_project_id` (default_project_id)、`idx_ws_user_user_last_accessed` (user_id, last_accessed_at DESC) WHERE is_deleted = false；单表索引共 5 个，符合 C9。

#### 2.1.4 项目表（ws_project）

| 字段           | 类型                        | 约束                                    | 说明     |
| ------------ | ------------------------- | ------------------------------------- | ------ |
| id           | UUID                      | PK                                     | 项目ID   |
| workspace_id | UUID                      | NOT NULL                               | 所属工作空间 |
| name         | VARCHAR(100)              | NOT NULL                              | 名称     |
| description  | TEXT                      | NULL                                  | 描述     |
| status       | VARCHAR(20)               | NOT NULL, DEFAULT 'active'            | 状态（active / archived） |
| start_time   | TIMESTAMP                 | NULL                                  | 开始时间   |
| end_time     | TIMESTAMP                 | NULL                                  | 结束时间   |
| created_by   | UUID                      | NOT NULL                               | 创建者    |
| created_at   | TIMESTAMP                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间   |
| updated_at   | TIMESTAMP                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 更新时间   |
| is_deleted   | BOOLEAN                | NOT NULL, DEFAULT FALSE                   | 是否删除   |

**索引**：`uk_ws_project_workspace_name` UNIQUE (workspace_id, name) WHERE is_deleted = false、`idx_ws_project_ws_created` (workspace_id, created_at DESC)、`idx_ws_project_status` (status)

#### 2.1.5 项目动态表（ws_project_activity）

记录项目域写操作流水（创建 / 更新 / 归档 / 启封 / 删除等），供项目工作台动态展示。

| 字段            | 类型          | 约束                                 | 说明       |
| ------------- | ----------- | ---------------------------------- | -------- |
| id            | UUID        | PK                                  | 动态ID     |
| project_id    | UUID        | NOT NULL                            | 所属项目     |
| actor_id      | UUID        | NOT NULL                            | 操作人ID    |
| actor_name    | VARCHAR(100)| NOT NULL                            | 操作人姓名    |
| resource_type | VARCHAR(32) | NOT NULL                            | 资源类型（PROJECT 等） |
| resource_id   | UUID        | NOT NULL                            | 资源ID     |
| resource_name | VARCHAR(200)| NOT NULL                            | 资源名称     |
| action        | VARCHAR(32) | NOT NULL                            | 动作（如 PROJECT_CREATED） |
| summary       | VARCHAR(500)| NOT NULL                            | 变更摘要     |
| occurred_at   | TIMESTAMP   | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 发生时间     |
| is_deleted    | BOOLEAN     | NOT NULL, DEFAULT FALSE             | 是否删除     |
| created_at    | TIMESTAMP   | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间     |
| updated_at    | TIMESTAMP   | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 更新时间     |

**索引**：`idx_project_activity_project_occurred` (project_id, occurred_at DESC, id DESC) WHERE is_deleted = false

---


### 2.2 通用约定

- 命名风格：camelCase
- 分页：`pageNo`、`pageSize` → `{ list: [], total: number }`
- 通用响应：`{ "code": 200, "msg": "success", "data": {} }`

### 2.2.1 工作空间域上下文边界

工作空间域的活动上下文和目标资源分开处理：

| 场景 | 上下文入口 | 目标/资源 ID | 说明 |
| --- | --- | --- | --- |
| 当前空间详情、更新、成员和项目列表 | `X-Active-Workspace` | 资源自身的 `userId`、`projectId`、邀请 `id` 等可出现在路径或请求体 | Header 是当前空间的唯一授权依据；资源 ID 必须验证属于该空间 |
| 切换最近活跃空间 | `PUT /api/workspaces/active` 的 `X-Active-Workspace` | 目标空间 ID 仍通过 Header 传递 | 请求体为空；切换成功后前端再更新本地活动状态 |
| 设置个人默认项目 | `X-Active-Workspace` | 请求体允许 `projectId` | 这是“目标项目偏好”而非活动项目上下文；服务端必须验证项目属于当前空间且为 active |
| 公开邀请加入 | 无活动空间 Header | `token` | Token 绑定目标空间；响应中的 `activeWorkspace` 仅为结果数据，不作为后续请求的授权依据 |

路由参数 `:workspaceId` 只用于页面导航或明确的空间资源定位；后端工作空间操作仍按上表校验活动 Header 和资源归属。


### 2.3 权限矩阵

> 下表中"普通成员"对应预置空间角色 `成员`（ID `c0000000-0000-0000-0000-000000000002`，type='workspace'），"空间管理员"对应预置空间角色 `管理员`（ID `c0000000-0000-0000-0000-000000000001`，type='workspace'）；"权限口径"列给出前端挂载的权限码与服务端校验依据。

| 操作               | 普通成员 (成员) | 空间管理员 (管理员) | 权限口径 |
| ---------------- | ------------- | ------------- | --- |
| 查看工作空间信息         | ✅             | ✅             | `ws-info:view`（菜单与页面门禁） |
| 编辑工作空间名称/描述      | ❌             | ✅             | 前端 `ws-info:edit`；服务端校验空间管理员角色 |
| 设置个人默认项目（仅项目列表页） | ✅             | ✅             | 无权限码；服务端校验项目属于当前空间且为 active |
| 查看成员列表           | ✅             | ✅             | `ws-member:view`（菜单与页面门禁） |
| 直接添加成员           | ❌             | ✅             | 前端 `ws-member:manage`；服务端校验空间管理员角色 |
| 创建/撤销邀请链接        | ❌             | ✅             | 前端 `ws-invitation:manage`；服务端校验空间管理员角色 |
| 查看邀请链接列表         | ❌             | ✅             | 同上（前端仅在持码时渲染邀请面板） |
| 变更成员角色           | ❌             | ✅             | 前端 `ws-member:manage`；服务端校验空间管理员角色 |
| 移除其他成员           | ❌             | ✅             | 前端 `ws-member:manage`；服务端校验空间管理员角色 |
| 自行退出工作空间         | ✅             | ✅             | 成员列表中移除自身；唯一管理员不可退出（1000001009） |
| 查看项目列表           | ✅             | ✅             | `project:view`（菜单与页面门禁） |
| 创建项目             | ✅             | ✅             | 前端 `project:create`；服务端仅校验空间成员归属 |
| 编辑项目             | 仅自己创建         | ✅             | 服务端校验空间管理员角色或 `createdBy` 本人 |
| 归档/启封项目          | ❌             | ✅             | 服务端校验空间管理员角色 |
| 删除项目             | ❌             | ✅             | 服务端校验空间管理员角色 |

补充口径：

- 「新建空间」入口挂全局权限 `workspace:create`，与空间内角色无关；预置仅系统管理员、空间管理员两个系统角色持有该码。
- 预置「成员」角色的权限集中虽含 `ws-invitation:manage`，但邀请链接列表、创建与撤销在服务端按空间管理员角色二次校验，普通成员实际不可用。
- `project:create` 未登记于预置权限表，预置角色亦未授予，因此默认不显示新建项目入口；服务端创建项目仅要求调用方为该空间成员。

---


### 2.4 总体布局

业务端采用“顶部导航 + 内容区”布局（`web/src/layouts/BusinessLayout.vue`），空间态不设侧边栏。进入工作空间后，平台处于 `workspace` 模式，顶部动态菜单由路由注册表 `meta.menu` 按 `order` 生成并经权限过滤，显示：**空间信息**、**成员管理**、**项目列表**；无任何可见项时显示“请选择一个工作空间”占位提示。

```
┌──────────────────────────────────────────────────────────────────────┐
│ [Logo] [空间名胶囊] [项目名胶囊]                                       │
│        [空间信息] [成员管理] [项目列表]                                 │
│        [📁 我的空间] [📂 我的项目] [⚙ 空间管理] [🔧 系统管理]            │
│        [🔔 消息中心] | [👤 用户名 ▾]                                   │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│                     内容区（悬浮白卡）                                 │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

顶栏右侧固定入口与显示条件：

- **我的空间**：已归属空间（`hasWorkspace`）时显示，回到 `/workspaces`；
- **我的项目**：`project` 模式下显示，进入 `/workspace/projects`；
- **空间管理**：`workspace` / `project` 模式且持有任一 `ws-*` 权限（`hasWorkspaceAccess`）时显示，进入当前空间的空间信息页；
- **系统管理**：持有系统权限（`hasSystemPermission`）时显示，进入管理端；
- **消息中心** 与用户下拉（修改密码、退出登录）常驻；Logo 点击回当前上下文首页（`project` 模式回项目工作台，否则回我的空间）。

上下文胶囊展示当前空间名，`project` 模式下追加当前项目名。功能测试 / 接口测试页面改用页内“模块侧栏 + 主内容”布局，顶栏结构不变。


### 2.5 路由规划

| 路由                        | 页面（name）      | 模式（meta.mode） | 顶部菜单（label / icon / order / 权限码） | 说明              |
| ------------------------- | ------------- | -------------- | -------------------------------- | --------------- |
| `/workspaces`             | 我的空间（Workspaces） | `none`         | 无                                | 展示用户所有工作空间，支持检索、范围筛选与分页 |
| `/workspace/:workspaceId` | 空间信息（WorkspaceInfo） | `workspace`    | 空间信息 / InfoFilled / 10 / `ws-info:view` | 查看/编辑空间信息       |
| `/workspace/members`      | 成员管理（WorkspaceMembers） | `workspace`    | 成员管理 / UserFilled / 20 / `ws-member:view` | 成员列表 + 邀请链接管理   |
| `/workspace/projects`     | 项目列表（WorkspaceProjects） | `workspace`    | 项目列表 / Folder / 30 / `project:view` | 项目列表、引导、选择功能    |
| `/join?token=xxx`         | 邀请加入页面（Join） | 公开（`public`）  | 无                                | 公开页面，无需登录       |

说明：`/` 重定向到 `/workspaces`；`/workspace/projects/*` 下的项目内功能路由（项目工作台、功能测试、接口测试、缺陷管理等）属功能测试 / 接口测试模块，仅通过 `meta.mode='project'` 影响本布局顶栏，未配置 `meta.menu` 的路由不进入动态菜单。


### 2.6 状态管理（Pinia Store）

- `web/src/stores/auth.ts`（认证与上下文）：`activeWorkspace`（`{ id, name, workspaceRole }`，本地持久化）、`activeWorkspaceId`、`activeProject` / `activeProjectId`、`activeProjectName`、`permissions` / `permissionsLoaded`、`hasWorkspace`、`hasWorkspaceAccess`（任一 `ws-*` 权限）、`hasPermission(code)`；动作 `setActiveWorkspace` / `setActiveProject` / `loadPermissions`。
- `web/src/stores/nav.ts`（导航）：`currentMode` 由当前路由 `meta.mode` 派生（`'none' | 'workspace' | 'project' | 'admin'`），并派生 `isWorkspaceMode` / `isProjectMode` / `isAdminMode`；`dynamicMenuItems` 按当前模式从路由注册表取 `meta.menu` 项、按 `order` 升序排列并经 `permission` / `permissionAny` 过滤；`adminSidebarSections` 供管理端侧边栏分组。
- 列表数据不进全局 Store，由页面级 composable 持有：`web/src/composables/workspace/useWorkspaceListPage.ts`（scope、keyword、pageNo/pageSize 默认 12）、`useMemberList.ts`、`useInvitationList.ts`（pageNo/pageSize 默认 20）、`useProjectListPage.ts`（pageSize 默认 20，滚动追加）。


### 2.7 核心组件

- **WorkspaceListPage**（`web/src/pages/workspace/`）：卡片网格展示用户归属的工作空间；工具栏提供“全部 / 我管理的 / 已归档”分段（附各范围数量）、名称模糊搜索与“按最近访问排序”提示；分页默认 12 条（可选 12 / 24 / 48）。加载中显示 6 张骨架卡，失败显示重试态，空结果区分“无归属”与“无匹配筛选”两种空态（后者可清除筛选）。卡片（`WorkspaceCard`）显示名称、描述、真实角色名称、成员数、项目数、用例数与归档状态；已归档卡片只读不可点击。具备 `workspace:create` 权限时，页头与网格末尾提供创建入口（`WorkspaceCreateDialog`）。点击未归档卡片触发切换流程（`PUT /api/workspaces/active` → 更新 `activeWorkspaceId` → 有默认项目时进入项目工作台，否则进入项目列表页）。

- **WorkspaceInfoPage**：通过路由参数 `:workspaceId` 同步当前空间并展示其信息；顶部“成员 / 项目”两个统计卡分别链接 `/workspace/members`、`/workspace/projects`。基础信息表单含名称（2–50 字）、空间 ID（只读）、描述（≤500 字），下方展示创建人与创建时间；仅具 `ws-info:edit` 权限时可编辑并保存。注意：此页面**不提供**默认项目设置功能。

- **MemberListPage**：操作当前活跃工作空间的成员。页头提供“邀请成员”（`ws-member:manage`）、“生成链接”与“复制邀请链接”（`ws-invitation:manage`，复制最近一条）；页头之下并排展示成员列表与邀请链接两张卡片（等高对齐，表头与表尾在同一水平线；无 `ws-invitation:manage` 时仅显示成员卡片）。成员列表（`MemberListPanel`）支持姓名/邮箱搜索、角色筛选、角色变更与移除（移除自己即退出工作空间），每页 20 条；邀请链接卡片（`InvitationListPanel`）展示邀请链接、使用次数、过期时间、状态与创建时间，支持复制、撤销与分页，每页 20 条。

- **MemberInviteDialog**：远程搜索平台用户（姓名 / 用户名 / 邮箱）多选后提交添加成员，服务端跳过已在本空间的用户并按 `skippedUserIds` 返回跳过提示。

- **InvitationCreateDialog / InvitationLinkDialog**：设置过期时间和最大使用次数生成链接；生成后弹出链接对话框显示 URL 与复制按钮（复制内容附带空间名与邀请人说明文案）。

- **ProjectListPage**：以卡片网格展示当前工作空间项目，支持活跃/已归档状态分段、独立状态数量统计、项目名称或描述搜索及最近更新排序；默认每页 20 条，滚动到底部（IntersectionObserver 提前 240px）自动追加下一页。空间无项目时显示引导创建区域，无匹配时显示筛选空态，加载失败可重试。项目卡片（`ProjectCard`）含默认项目标识、状态标签、描述、起止日期与创建人，支持进入、设为默认、编辑（管理员或创建者）、归档（管理员）；归档卡片只读并提供启封、删除（均限管理员）。新建项目入口为页头按钮与网格末尾的新建卡片，挂 `project:create` 权限；创建 / 编辑使用页内 `el-dialog` 表单（名称必填 ≤100 字、描述、开始时间、结束时间），归档与删除均需二次确认。

- **JoinPage**：公开的邀请加入页面（`/join?token=…`）。流程为验证 token → 输入邮箱查询账号是否存在 → 已有账号输入密码（8–64 字符，带强度条）或新账号填写姓名与密码 → 加入并登录后跳转至项目列表页；令牌无效时显示“邀请链接无效”态并可返回登录。

---


## 3. 错误码定义

| 错误码       | HTTP 状态码 | 说明                 |
| ----------- | -------- | ------------------ |
| 1000001001  | 400      | 参数校验失败             |
| 1000001004  | 400      | 工作空间名称已存在          |
| 1000001009  | 400      | 必须保留至少一个空间管理员      |
| 1000010020  | 400      | 项目名称在当前工作空间已存在     |
| 1000010021  | 404      | 项目不存在或不属于当前工作空间    |
| 1000010022  | 400      | 项目下存在进行中的测试计划，无法归档 |
| 1000010023  | 400      | 项目下存在数据，无法删除       |
| 1000010024  | 400      | 用户已在工作空间中          |
| 1000010025  | 400      | 用户不存在或已被禁用         |
| 1000010026  | 400      | 已归档项目不可编辑          |
| 1000010027  | 400      | 默认项目必须是活跃项目        |
| 1000010028  | 400      | 密码错误，请重新输入         |
| 1000010029  | 400      | 邀请链接已失效            |
| 1000010030  | 400      | 邀请链接已达到最大使用次数      |
| 1000010031  | 400      | 邀请链接已过期            |
| 1000010032  | 400      | 邀请链接已被撤销           |
| 1000002001  | 403      | 无权限执行此操作           |
| 1000002002  | 403      | 不可操作自身账户（保留码，当前未使用） |

---


## 4. 安全设计

- 所有接口通过框架安全层验证 JWT 双令牌。
- 空间管理接口（`/api/workspace/*`）由 `ContextHeaderInterceptor` 强制解析并校验 `X-Active-Workspace` 头（公开邀请三接口 verify / check-email / join 豁免），`WorkspaceRoleInterceptor` 校验调用方是否为该工作空间成员并追加空间角色权限码；成员或角色缺失时拒绝访问（1000002001）。`/api/workspaces/*`（我的空间列表、切换活动空间）按 2.2.1 的边界处理。
- 管理员专属操作（编辑空间信息、成员增删改、邀请链接列表/创建/撤销、归档与删除项目）额外校验 `workspaceRole` 是否为预置空间管理员角色 ID `c0000000-0000-0000-0000-000000000001`；项目编辑放宽为管理员或创建者本人。
- 项目写操作记录项目动态流水（`ws_project_activity`：操作人、时间、类型、变更摘要），供项目工作台动态展示。
- 邀请链接 token 使用安全的随机生成算法（两个 UUID 去连字符拼接，共 64 个十六进制字符）。
- 公开邀请接口（verify / check-email / join）按 IP 限流：同一 IP 每 60 秒最多 20 次（`@RateLimit(limit = 20, window = 60)`），防止暴力破解密码。
- 密码传输使用 HTTPS，后端哈希存储。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `02-space-management-overview.md` | 1. 引言（1.1 编写目的 / 1.2 范围 / 1.3 参考资料）、2. 数据设计（2.1 数据库表设计、2.2 通用约定、2.2.1 工作空间域上下文边界、2.3 权限矩阵、2.4 总体布局、2.5 路由规划、2.6 状态管理（Pinia Store）、2.7 核心组件）、3. 错误码定义、4. 安全设计 |
| 我的空间 | `03-space-management-my-workspace.md` | 1. 数据设计变更、2. 我的空间接口（/api/workspaces）、3. 工作空间切换流程、4. 我的空间页面设计、5. 实施与验证 |
| 空间与成员管理 | `04-space-management-workspace-admin.md` | 1. 空间管理相关接口（/api/workspace）、2. 项目归档/删除联动、3. 邀请成员流程（3.1 直接添加流程、3.2 邀请链接流程、3.3 工作空间详情页、3.4 成员管理页、3.5 项目列表页） |
| 公开邀请 | `05-space-management-invite-join.md` | 1. 公开接口（无需认证）（1.1 验证邀请令牌、1.2 通过邀请链接加入并登录、1.2.1 公开邀请上下文边界、1.3 邀请加入页面） |

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-02 | 按前端实现对齐模块划分、数据表与索引、权限矩阵与权限码口径、总体布局、路由与菜单、状态管理、核心组件命名与交互、错误码与安全设计，并重建分册-章节对照表 |
