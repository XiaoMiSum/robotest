# 软件测试平台——系统管理模块详细设计说明书总览

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台中的**系统管理模块**进行详细设计，定义数据结构、接口规范、业务逻辑、前端组件及交互流程，为开发实现提供完整依据。本版本在既有设计基础上扩展三部分变更：

1. 「数据概览」页重构所需的**统计接口**与各指标口径；
2. 用户状态取值扩展 `locked`（锁定）与空间创建人 `created_by` 字段；
3. **登录审计写入**（记录登录用户 IP）——写入设计归属《审计查询详细设计说明书》，本文档只引用并定义其对统计的消费口径。

### 1.2 范围

系统管理模块面向**拥有系统角色的用户**，提供用户管理、全局工作空间管理、系统角色与权限管理功能。模块独立于业务功能，通过 `/api/admin` 路径访问。

本版本变更对应的交互设计见 `docs/05-interaction-design/01-readme.md`；登录审计写入与审计查询接口变更见 `docs/04-detailed-design/20-audit-query.md`。用户/空间/角色的既有接口与数据设计，除本文档明确标注「新增」「扩展」之处外均保持不变。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》
- 《软件测试平台概要设计说明书》
- 《系统管理模块页面交互设计》（`docs/05-interaction-design/01-readme.md`）
- 《审计查询详细设计说明书》（`docs/04-detailed-design/20-audit-query.md`）
- `docs/06-spec/05-api.md`（URL/方法/分页规范）、`docs/06-spec/04-backend.md`（分层、Mapper 封装、部分更新）、`docs/06-spec/06-database.md`（DDL/索引规范）、`docs/06-spec/03-frontend.md`
- 示例页面 `web/demos/admin/dashboard.html`

---


## 2. 数据设计

### 2.1 数据库表设计

数据库字段使用 snake_case，接口 JSON 使用 camelCase。

#### 2.1.1 用户表（sys_user）

| 字段            | 类型                        | 约束                                    | 说明    |
| ------------- | ------------------------- | ------------------------------------- | ----- |
| id            | binary(16)                | PK                                     | 用户ID  |
| username      | varchar(30)               | UNIQUE, NOT NULL                      | 用户名   |
| name          | varchar(50)               | NOT NULL                              | 显示名称  |
| email         | varchar(255)              | NOT NULL                              | 邮箱    |
| password_hash | varchar(255)              | NOT NULL                              | 密码哈希  |
| avatar_url    | varchar(500)              | NULL                                  | 头像URL |
| status        | varchar(20)               | NOT NULL, DEFAULT 'active'            | 状态（`active` / `disabled` / `locked`，取值扩展见 2.2） |
| created_at    | datetime                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间  |
| updated_at    | datetime                  | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间  |
| is_deleted    | tinyint(1)                | NOT NULL, DEFAULT 0                   | 是否删除  |

**索引**：`uk_username` UNIQUE (username), `idx_status` (status)

#### 2.1.2 角色表（sys_role）

| 字段          | 类型                        | 约束                                    | 说明        |
| ----------- | ------------------------- | ------------------------------------- | --------- |
| id          | binary(16)                | PK                                     | 角色ID      |
| name        | varchar(50)               | UNIQUE, NOT NULL                      | 角色名称      |
| description | varchar(200)              | NULL                                  | 描述        |
| type        | enum('system','workspace') | NOT NULL, DEFAULT 'system'  | 角色类型（system=系统, workspace=空间） |
| scope       | enum('global','workspace') | NOT NULL, DEFAULT 'global'   | 权限作用域（global=系统级, workspace=空间级） |
| full_access | tinyint(1)                | NOT NULL, DEFAULT 0                   | 是否全量权限（拥有对应作用域下所有权限码） |
| is_system   | tinyint(1)                | NOT NULL, DEFAULT 0                   | 是否预置角色    |
| permissions | json                      | NOT NULL                              | 权限点code列表 |
| created_at  | datetime                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间      |
| updated_at  | datetime                  | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间      |
| is_deleted  | tinyint(1)                | NOT NULL, DEFAULT 0                   | 是否删除      |

**索引**：`uk_name` UNIQUE (name), `idx_type` (type), `idx_scope` (scope)

> **迁移说明**：V8 迁移脚本新增 `scope` 和 `full_access` 字段；`workspace_admin` 角色设为 `full_access=true`。

#### 2.1.3 用户-角色关联表（sys_user_role）

| 字段          | 类型       | 约束                                  | 说明   |
| ----------- | -------- | ----------------------------------- | ---- |
| id          | binary(16) | PK                                   | 关联ID |
| user_id     | binary(16) | NOT NULL                             | 用户ID |
| role_id     | binary(16) | NOT NULL                             | 角色ID |
| assigned_at | datetime   | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 分配时间 |
| created_at  | datetime   | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 创建时间 |
| updated_at  | datetime   | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| is_deleted  | tinyint(1) | NOT NULL, DEFAULT 0                  | 是否删除 |

**索引**：`uk_user_role` UNIQUE (user_id, role_id), `idx_role_id` (role_id)

#### 2.1.4 工作空间表（ws_workspace）

| 字段          | 类型                         | 约束                                    | 说明     |
| ----------- | -------------------------- | ------------------------------------- | ------ |
| id          | binary(16)                 | PK                                     | 工作空间ID |
| name        | varchar(50)                | UNIQUE, NOT NULL                      | 名称     |
| description | varchar(500)               | NULL                                  | 描述     |
| status      | enum('active','dissolved') | NOT NULL, DEFAULT 'active'            | 状态     |
| created_by  | uuid                       | NULL                                  | 创建人 user id（逻辑外键 → sys_user.id，**本版本新增**，DDL 与迁移说明见 2.3） |
| created_at  | datetime                   | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间   |
| updated_at  | datetime                   | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间   |
| is_deleted  | tinyint(1)                 | NOT NULL, DEFAULT 0                   | 是否删除   |

**索引**：`uk_name` UNIQUE (name) WHERE is_deleted = 0、`idx_ws_workspace_created_by` (created_by)（**新增**，DDL 见 2.3）

#### 2.1.5 用户-工作空间关联表（workspace_user）

| 字段             | 类型                     | 约束                                  | 说明      |
| -------------- | ---------------------- | ----------------------------------- | ------- |
| id             | binary(16)                 | PK                                   | 关联ID    |
| user_id        | binary(16)                 | NOT NULL                             | 用户ID    |
| workspace_id   | binary(16)                 | NOT NULL                             | 工作空间ID  |
| workspace_role | binary(16)                 | NOT NULL                             | 工作空间内角色（引用 sys_role.id，type='workspace'） |
| joined_at      | datetime                   | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 加入时间    |
| is_deleted     | tinyint(1)                 | NOT NULL, DEFAULT 0                  | 是否删除    |

**索引**：`uk_user_workspace` UNIQUE (user_id, workspace_id), `idx_workspace_id` (workspace_id)

> **迁移说明**：`V5__workspace_role_rbac.sql` 预置 workspace 角色至 `sys_role` 表，将 `workspace_user.workspace_role` 从字符串值（`member`/`admin`）迁移为对应角色 UUID，并修改列类型为 `binary(16)`。

#### 2.1.6 权限点表（sys_permission）

| 字段          | 类型           | 约束                             | 说明     |
| ----------- | ------------ | ------------------------------ | ------ |
| id          | binary(16)  | PK                                  | 主键ID   |
| code        | varchar(100) | UNIQUE, NOT NULL               | 权限点编码  |
| name        | varchar(100) | NOT NULL                       | 权限点名称  |
| parent_code | varchar(100) | NULL                          | 父权限点编码 |
| module      | varchar(50)  | NOT NULL                       | 所属模块   |
| scope       | enum('global','workspace') | NOT NULL, DEFAULT 'global' | 权限作用域（global=系统级, workspace=空间级） |
| sort_order  | int          | NOT NULL, DEFAULT 0            | 排序号    |
| created_at  | datetime     | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 创建时间   |
| updated_at  | datetime     | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间   |
| is_deleted  | tinyint(1)   | NOT NULL, DEFAULT 0                  | 是否删除   |

**索引**：`uk_code` UNIQUE (code), `idx_parent_code` (parent_code), `idx_module` (module), `idx_scope` (scope)

> **迁移说明**：V8 迁移脚本新增 `scope` 字段，将所有业务权限点标记为 `workspace`，系统管理权限点标记为 `global`。

### 2.2 用户状态取值扩展（无 DDL）

`sys_user.status` 为 `VARCHAR(20)`（`server/src/main/resources/db/schema.sql:18`），取值**扩展**为：

| 取值 | 含义 | 能否登录 |
| ---- | ---- | -------- |
| `active` | 启用 | ✅ |
| `disabled` | 禁用 | ❌ |
| `locked` | 锁定（本版本新增） | ❌ |

- 登录拦截**无代码变更**：`UserDetailsBridgeImpl#toLoginUser` 以 `enabled = active` 判定，`locked`/`disabled` 均被拒绝，沿用既有错误码 `ACCOUNT_DISABLED`（1000002005，不区分禁用/锁定，防账号枚举）。
- 既有索引 `idx_status` 覆盖状态过滤，**不新增索引**。

### 2.3 空间创建人 `ws_workspace.created_by`（DDL）

```sql
-- 变更 1：新增创建人字段（可空，历史数据无创建人 → NULL）
ALTER TABLE ws_workspace ADD COLUMN created_by UUID NULL;
COMMENT ON COLUMN ws_workspace.created_by IS '创建人 user id（逻辑外键 → sys_user.id，无物理外键，C5）';

-- 变更 2：关联字段（逻辑外键）必须建索引（C9）
CREATE INDEX idx_ws_workspace_created_by ON ws_workspace (created_by);
```

- 同步修改 `schema.sql` 的 `CREATE TABLE ws_workspace`（新增 `created_by UUID NULL` 与 `CREATE INDEX idx_ws_workspace_created_by`），保证全量建库一致；**存量库执行上述 ALTER 迁移**（迁移说明见 8. 实施说明，随本交付提交）。
- 索引数：该表唯一索引 1 + `idx_ws_workspace_created` + 本次新增 = 3 ≤ 5（C9）。
- 无物理外键，引用完整性由 Service 层保证（C5）。

### 2.4 审计日志复用（无 DDL、不新增索引）

- 登录审计复用 `sys_audit_log`（写入与查询设计见《审计查询详细设计说明书》§2/§4.3）：`operation = 'LOGIN'`，`entity_type = 'User'`，`entity_id` = 用户 ID，`request_ip` 记录登录 IP。
- 数据概览统计按 `created_at` 做 14 日窗口聚合，既有 `idx_sys_audit_log_created` 覆盖；表为低量级审计数据，**不新增索引**（C9 单表索引维持 3 个）。

---


### 3.1 通用约定

- 基础路径：`/api/admin`（管理端）、`/api/auth`（认证）；仅需 `Authorization` 请求头（系统管理域无上下文头，C4）。
- 认证：`Authorization: Bearer <token>`
- 命名风格：camelCase
- 分页：`page`、`pageSize` → `{ records: [], total: number }`
- 通用响应：`{ "code": 200, "message": "success", "data": {} }`；响应沿用平台通用 `Result<T>`，除特别说明（展示完整报文）外，接口响应示例仅展示 `data` 字段内容。
- 数据概览接口与用户/空间列表一致，**不设独立权限点**（进入 `/admin` 即可见菜单，路由守卫 `requiresAdmin` 把关）。


### 4.4 权限校验中间件

- 后端实现授权中间件，在管理端路由组统一挂载。
- 从 JWT 提取 userId 和所有角色，查询合并后的权限 code 列表。
- 判断当前请求所需的权限 code 是否在列表中。所需权限 code 由路径和方法映射确定。

权限 code 与路径映射：

| 路径                                  | 方法     | 所需权限 code                |
| ----------------------------------- | ------ | ------------------------ |
| /api/admin/users                    | GET    | user:view                |
| /api/admin/users                    | POST   | user:create              |
| /api/admin/users/:id                | PUT    | user:edit                |
| /api/admin/users/:id/status         | PATCH  | user:disable             |
| /api/admin/users/:id/reset-password | POST   | user:reset-password      |
| /api/admin/workspaces               | GET    | workspace:view           |
| /api/admin/workspaces               | POST   | workspace:create         |
| /api/admin/workspaces/:id           | DELETE | workspace:delete         |
| /api/admin/workspaces/:id/members   | POST   | workspace:manage-members |
| /api/admin/roles/tree               | GET    | role:view                |
| /api/admin/roles                    | POST   | role:create              |
| /api/admin/roles/:id                | PUT    | role:edit                |
| /api/admin/roles/:id                | DELETE | role:delete              |


### 5.1 路由规划

**公开路由**（无需认证）：

| 路径    | 页面             | 说明               |
| ------ | -------------- | ---------------- |
| /login | LoginPage      | 登录表单，挂载时检测初始化状态 |
| /init  | InitPage       | 系统初始化密码设置页面      |
| /join  | JoinPage       | 工作空间邀请链接页面       |

**管理端路由**（需要系统角色）：

```
/admin
├── /dashboard            → DashboardPage（数据概览，meta.title「数据概览」）
├── /users                → UserListPage（用户列表）
├── /users/create         → UserCreatePage（新建用户）
├── /users/:id            → UserDetailPage（用户详情/编辑）
├── /workspaces           → WorkspaceListPage（工作空间列表）
├── /workspaces/create    → WorkspaceCreatePage（新建工作空间）
├── /workspaces/:id       → WorkspaceDetailPage（工作空间详情/成员管理）
└── /roles                → RoleManagementPage（角色管理，左右分栏）
```


#### 5.2.1 AdminLayout

```
AdminLayout
├── Sidebar
│   ├── Logo
│   ├── NavItem (仪表盘)
│   ├── NavItem (用户管理)
│   ├── NavItem (工作空间管理)
│   └── NavItem (角色管理)
├── Header
│   ├── Breadcrumb
│   └── UserMenu (当前管理员信息/退出)
└── Content (RouterView)
```


### 5.3 状态管理（Pinia Store）

- `adminUser`：当前管理员信息及系统角色列表
- `systemRoles`：系统角色列表（用于选择器）
- `permissionTableData`：权限表格数据缓存
- `roleTreeData`：角色树数据


### 5.6 数据绑定与格式化

- 时间一律走 `utils/format.ts`：`formatDateTime(generatedAt)`、`formatDateTime(createdAt)`、图表轴标签用 `MM-dd`（由 `date` 字符串截取或 `formatShortDateTime` 派生），后端 UTC+0 → 本地时区（`docs/06-spec/03-frontend.md` §8）。
- 图表颜色使用既有设计令牌（`variables.scss`）：折线 `--color-primary-500`（#409eff）、面积 `rgba(51,112,255,.08)`、环图三段 `--color-success` / `--color-info` / `--color-danger`，不在组件内硬编码新色值。


## 6. 错误码定义

| 错误码       | HTTP 状态码 | 说明               |
| ----------- | -------- | ---------------- |
| 1000001001  | 400      | 参数校验失败           |
| 1000001002  | 400      | 用户名已存在           |
| 1000001003  | 400      | 邮箱已存在            |
| 1000001004  | 400      | 工作空间名称已存在        |
| 1000001005  | 400      | 角色名称已存在          |
| 1000001006  | 400      | 密码强度不符合要求        |
| 1000001007  | 400      | 原密码错误            |
| 1000001008  | 400      | 角色类型错误（只能选择系统角色） |
| 1000001009  | 400      | 必须保留至少一个空间管理员    |
| 1000001010  | 400      | 用户状态不合法（单个/批量状态接口传入三态之外的取值） |
| 1000002001  | 403      | 无权限执行此操作         |
| 1000002002  | 403      | 不可操作自身账户         |
| 1000002003  | 403      | 系统预置角色不可删除       |
| 1000002004  | 403      | 系统预置角色权限不可修改     |
| 1000002005  | 401      | 账户已被禁用或登录凭证失效    |
| 1000002006  | 403      | 不能移除自己的最后一个系统角色  |
| 1000003001  | 404      | 用户不存在            |
| 1000003002  | 404      | 工作空间不存在          |
| 1000003003  | 404      | 角色不存在            |
| 1000004001  | 409      | 工作空间下存在项目，无法解散（归档语义下保留定义，暂不触发）   |
| 1000010033  | 409      | 工作空间已归档，不可操作   |
| 1000010034  | 409      | 仅已归档的工作空间可重新启用 |
| 1000002007  | 400      | 系统已初始化，禁止重复初始化   |
| 1000004002  | 409      | 角色被用户引用，无法删除     |
| 1000005000  | 500      | 服务器内部错误          |

> 号段归属：通用参数校验段 1000001001–1000001010（在既有 1000001001–1000001009 基础上本版本新增 1000001010），`ErrorCodeConstants` 注释同步更新。

---


## 7. 安全设计

- 管理端所有接口要求 Token 中包含系统角色。
- 任何角色变更、密码重置、禁用/锁定操作都强制相关用户 Token 失效（Redis 黑名单或 token 版本号递增）。
- 所有写操作（创建、更新、删除）记录操作日志（操作人、时间、IP、操作对象、变更内容）。
- 敏感操作（重置密码、归档/重新启用工作空间、删除角色）额外记录详细日志。
- 登录成功写入审计日志（含登录 IP），写入设计见 `docs/04-detailed-design/20-audit-query.md`，本模块仅消费其记录做数据概览统计（见 2.4、3.6、4.10）。

---


## 8. 实施说明

**新增文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../controller/admin/DashboardController` | 仅路由（C2） |
| `server/.../service/admin/dashboard/DashboardStatsService` + `Impl` | 统计聚合 |
| `server/.../model/dto/response/admin/DashboardStatsRespDTO` | 响应 DTO（含嵌套 `UsersStats/WorkspacesStats/ProjectsStats/ActivityStats/DailyActiveUsers`） |
| `web/src/composables/admin/useDashboard.ts` + `.spec.ts` | 页面逻辑与单测 |
| `docs/05-interaction-design/01-readme.md`、`docs/04-detailed-design/01-readme.md` | 对应交互设计与本设计文档 |

**修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../framework/common/ErrorCodeConstants.java` | 新增 1000001010，参数校验段注释顺延 |
| `server/.../framework/common/Constants.java` | `Status` 增加 `LOCKED = "locked"` |
| `server/.../service/admin/UserServiceImpl` | 状态取值校验 |
| `server/.../model/entity/workspace/Workspace`、`WorkspaceRespDTO`、`WorkspaceServiceImpl`、`WorkspaceMapper` | `created_by` 字段、写入、`createdByName` 回填 |
| `server/.../controller/admin/AdminWorkspaceController` | 创建接口传入当前用户 ID |
| `server/src/main/resources/db/schema.sql` | `ws_workspace` 建表与索引同步 |
| `server/.../AuthController`、审计相关类 | 登录审计接入（详见审计查询详细设计 §6） |
| `web/src/types/admin.ts`、`services/admin.ts`、`pages/admin/DashboardPage.vue`、`pages/admin/UserListPage.vue`、`router/index.ts` | 见 5.5 |

**数据库迁移说明（C5）**

```sql
-- 存量库迁移（随本次交付执行；全量建库以 schema.sql 为准）
ALTER TABLE ws_workspace ADD COLUMN created_by UUID NULL;
CREATE INDEX idx_ws_workspace_created_by ON ws_workspace (created_by);
```

**约束核对**：无新增外部依赖（图表为手写 SVG）；无新增权限点；无物理外键；上下文标识不出现在 URL（C4）。

**测试要点（C8 ≥ 70%）**

- 后端：`DashboardStatsServiceImplTest`（口径聚合、14 日补 0、除零补零）、`UserServiceImplTest` 增补状态非法值用例、`WorkspaceServiceImplTest` 增补 `created_by` 写入/回填用例、登录审计写入用例（失败不影响登录）。
- 前端：`useDashboard.spec.ts` 覆盖格式化、脚注文案、折线/环图几何计算与加载状态。

---


## 9. 附录

### 9.1 初始权限点定义

| code                     | 名称      | parent_code | module | scope     | 排序  |
| ------------------------ | ------- | ----------- | ------ | --------- | --- |
| user                     | 用户管理    | null        | admin  | global    | 1   |
| user:view                | 查看用户    | user        | admin  | global    | 1   |
| user:create              | 创建用户    | user        | admin  | global    | 2   |
| user:edit                | 编辑用户    | user        | admin  | global    | 3   |
| user:disable             | 禁用/启用用户 | user        | admin  | global    | 4   |
| user:reset-password      | 重置密码    | user        | admin  | global    | 5   |
| workspace                | 工作空间管理  | null        | admin  | global    | 2   |
| workspace:view           | 查看工作空间  | workspace   | admin  | global    | 1   |
| workspace:create         | 创建工作空间  | workspace   | admin  | global    | 2   |
| workspace:edit           | 编辑工作空间  | workspace   | admin  | global    | 3   |
| workspace:delete         | 解散工作空间（归档语义，同时用于重新启用）  | workspace   | admin  | global    | 4   |
| workspace:manage-members | 管理成员    | workspace   | admin  | global    | 5   |
| role                     | 角色管理    | null        | admin  | global    | 3   |
| role:view                | 查看角色    | role        | admin  | global    | 1   |
| role:create              | 创建角色    | role        | admin  | global    | 2   |
| role:edit                | 编辑角色    | role        | admin  | global    | 3   |
| role:delete              | 删除角色    | role        | admin  | global    | 4   |

> **业务侧权限点**由 V7 迁移脚本定义（33 个节点），`scope='workspace'`，涵盖我的空间、项目、测试用例、测试评审、测试计划、缺陷等模块。

### 9.2 预置角色

- **系统管理员**（type='system', scope='global', is_system=true）：拥有 admin 模块所有全局权限。
- **空间管理员**（type='workspace', scope='workspace', is_system=true, full_access=true）：拥有所有空间级权限，运行时自动注入。
- **普通成员**（type='workspace', scope='workspace', is_system=true, full_access=false）：拥有空间级基础权限。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `02-system-management-overview.md` | 前言、1. 引言、2. 数据设计、3.1 通用约定、4.4 权限校验中间件、5.1 路由规划、5.2.1 AdminLayout、5.3 状态管理（Pinia Store）、5.6 数据绑定与格式化、6. 错误码定义、7. 安全设计、8. 实施说明、9. 附录 |
| 认证 | `03-system-management-auth.md` | 3.2 认证接口、4.6 密码策略、4.7 系统初始化流程、4.10 登录审计写入 |
| 用户管理 | `04-system-management-user.md` | 3.3 用户管理接口、4.1 用户创建流程、4.2 用户更新流程、4.5 用户状态变更、禁用与强制下线 |
| 工作空间管理 | `05-system-management-workspace.md` | 3.4 工作空间管理接口、4.9 空间创建人写入与回填 |
| 角色与权限管理 | `06-system-management-role.md` | 3.5 角色与权限管理接口、4.3 角色管理流程、5.2.2 角色管理页、5.2.3 PermissionTable组件、5.2.4 RoleUsersTable组件、5.4 关键组件交互 |
| 数据概览 | `07-system-management-dashboard.md` | 3.6 数据概览统计、4.8 DashboardStatsService 端口、5.5 数据概览与状态扩展的文件分层、5.7 图表计算（composable 纯函数） |
