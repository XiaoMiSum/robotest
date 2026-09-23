# 软件测试平台——（总览分册）

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台中的**空间管理业务模块**进行详细设计，定义数据结构、接口规范、业务逻辑、前端组件及交互流程，为开发实现提供完整依据。

### 1.2 范围

空间管理业务模块面向**业务用户**，包含**我的工作空间**、**工作空间成员管理**及**项目管理**三个子模块。所有操作限定在当前活跃工作空间上下文中，工作空间上下文通过请求头 `X-Active-Workspace` 传递。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》
- 《软件测试平台概要设计说明书》
- 《全局导航与菜单交互系统设计》
- 《空间管理业务模块页面交互设计》

---


## 2. 数据设计

### 2.1 数据库表设计

数据库字段使用 snake_case，接口 JSON 使用 camelCase。

#### 2.1.1 工作空间表（workspace）

| 字段          | 类型                         | 约束                                    | 说明     |
| ----------- | -------------------------- | ------------------------------------- | ------ |
| id          | binary(16)                 | PK                                     | 工作空间ID |
| name        | varchar(50)                | UNIQUE, NOT NULL                      | 名称     |
| description | varchar(500)               | NULL                                  | 描述     |
| status      | enum('active','dissolved') | NOT NULL, DEFAULT 'active'            | 状态     |
| created_at  | datetime                   | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间   |
| updated_at  | datetime                   | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间   |
| is_deleted  | tinyint(1)                 | NOT NULL, DEFAULT 0                   | 是否删除   |

**索引**：`uk_name` UNIQUE (name)

#### 2.1.2 邀请链接表（workspace_invitation）

| 字段           | 类型                       | 约束                                  | 说明     |
| ------------ | ------------------------ | ----------------------------------- | ------ |
| id           | binary(16)  | PK                                   | 邀请ID   |
| workspace_id | binary(16)  | NOT NULL                             | 目标工作空间 |
| token        | varchar(64) | UNIQUE, NOT NULL                    | 邀请令牌   |
| created_by   | binary(16)  | NOT NULL                             | 创建者    |
| expires_at   | datetime    | NULL                                | 过期时间   |
| max_uses     | int         | NULL                                | 最大使用次数 |
| use_count    | int         | NOT NULL, DEFAULT 0                 | 已使用次数  |
| status       | enum('active','revoked') | NOT NULL, DEFAULT 'active' | 状态     |
| created_at   | datetime    | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间   |
| updated_at   | datetime    | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| is_deleted   | tinyint(1)  | NOT NULL, DEFAULT 0                 | 是否删除   |

**索引**：`uk_token` UNIQUE (token), `idx_workspace_id` (workspace_id)

#### 2.1.3 用户-工作空间关联表（workspace_user）

| 字段                 | 类型                     | 约束                                  | 说明      |
| ------------------ | ---------------------- | ----------------------------------- | ------- |
| id                 | binary(16)             | PK                                   | 关联ID    |
| user_id            | binary(16)             | NOT NULL                             | 用户ID    |
| workspace_id       | binary(16)             | NOT NULL                             | 工作空间ID  |
| workspace_role     | binary(16)             | NOT NULL                             | 工作空间内角色（引用 sys_role.id，type='workspace'） |
| default_project_id | binary(16)             | NULL                                 | 个人默认项目  |
| joined_at          | datetime               | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 加入时间    |
| is_deleted         | tinyint(1)             | NOT NULL, DEFAULT 0                  | 是否删除    |

**索引**：`uk_user_workspace` UNIQUE (user_id, workspace_id), `idx_workspace_id` (workspace_id), `idx_default_project_id` (default_project_id)

#### 2.1.4 项目表（project）

| 字段           | 类型                        | 约束                                    | 说明     |
| ------------ | ------------------------- | ------------------------------------- | ------ |
| id           | binary(16)                 | PK                                     | 项目ID   |
| workspace_id | binary(16)                 | NOT NULL                               | 所属工作空间 |
| name         | varchar(100)              | NOT NULL                              | 名称     |
| description  | text                      | NULL                                  | 描述     |
| status       | enum('active','archived') | NOT NULL, DEFAULT 'active'            | 状态     |
| start_time   | datetime                  | NULL                                  | 开始时间   |
| end_time     | datetime                  | NULL                                  | 结束时间   |
| created_by   | binary(16)                 | NOT NULL                               | 创建者    |
| created_at   | datetime                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间   |
| updated_at   | datetime                  | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间   |
| is_deleted   | tinyint(1)                | NOT NULL, DEFAULT 0                   | 是否删除   |

**索引**：`uk_workspace_name` UNIQUE (workspace_id, name), `idx_workspace_id` (workspace_id), `idx_status` (status)

---


### 3.1 通用约定

- 命名风格：camelCase
- 分页：`page`、`pageSize` → `{ records: [], total: number }`
- 通用响应：`{ "code": 200, "message": "success", "data": {} }`


### 4.5 权限矩阵

> 下表中"普通成员"对应预置 `workspace_member` 角色，"空间管理员"对应预置 `workspace_admin` 角色。

| 操作               | 普通成员 (workspace_member) | 空间管理员 (workspace_admin) |
| ---------------- | ------------- | ------------- |
| 查看工作空间信息         | ✅             | ✅             |
| 编辑工作空间名称/描述      | ❌             | ✅             |
| 设置个人默认项目（仅项目列表页） | ✅             | ✅             |
| 查看成员列表           | ✅             | ✅             |
| 直接添加成员           | ❌             | ✅             |
| 创建/撤销邀请链接        | ❌             | ✅             |
| 查看邀请链接列表         | ❌             | ✅             |
| 变更成员角色           | ❌             | ✅             |
| 移除其他成员           | ❌             | ✅             |
| 自行退出工作空间         | ✅             | ✅             |
| 查看项目列表           | ✅             | ✅             |
| 创建项目             | ✅             | ✅             |
| 编辑项目             | 仅自己创建         | ✅             |
| 归档/启封项目          | ❌             | ✅             |
| 删除项目             | ❌             | ✅             |

---


### 5.1 总体布局

业务端采用“顶部导航 + 内容区”布局。进入工作空间后，平台处于“工作空间模式”，顶部动态菜单显示：**空间信息**、**成员管理**、**项目列表**。

```
┌──────────────────────────────────────────────────────────┐
│ [Logo] [空间信息] [成员管理] [项目列表]                    │
│        [📁 我的空间] [⚙ 空间管理] [🔧 系统管理]           │
│        [🔔 消息] [👤 张三]                               │
├──────────────────────────────────────────────────────────┤
│                                                          │
│                      内容区                               │
│                                                          │
└──────────────────────────────────────────────────────────┘
```


### 5.2 路由规划

| 路由                        | 页面      | 说明              |
| ------------------------- | ------- | --------------- |
| `/workspaces`             | 我的空间页面  | 展示用户所有工作空间，支持分页 |
| `/workspace/:workspaceId` | 工作空间详情页 | 查看/编辑空间信息       |
| `/workspace/members`      | 成员管理页   | 成员列表 + 邀请链接管理   |
| `/workspace/projects`     | 项目列表页   | 项目列表、引导、选择功能    |
| `/join?token=xxx`         | 邀请加入页面  | 公开页面，无需登录       |


### 5.4 状态管理（Pinia Store）

- `activeWorkspaceId`：当前活跃工作空间ID
- `activeProjectId`：当前活跃项目ID
- `workspaceList`：用户归属的工作空间列表（分页缓存，含每个空间的项目数量信息）
- `currentWorkspaceRole`：当前工作空间内角色
- `currentMode`：当前模式（'workspace' | 'project' | 'none'）


### 5.5 核心组件

- **WorkspaceListPage**：卡片列表展示用户所有工作空间，分页显示。每张卡片显示名称、描述、角色标签、成员数、项目数（真实统计）、默认项目名称（未设置默认项目时显示空）。点击[进入工作空间]触发切换流程（纯前端操作：更新 activeWorkspaceId → 跳转项目列表页）。

- **WorkspaceDetailPage**：通过路由参数 `:workspaceId` 获取空间ID，展示该空间信息。管理员可编辑名称和描述并保存。注意：此页面**不提供**默认项目设置功能。

- **MemberListPage**：操作当前活跃工作空间的成员。包含成员列表和邀请链接两个标签页。成员列表支持搜索、添加、角色变更、移除；邀请链接标签页支持创建、复制、撤销链接。

- **InviteMemberModal**：远程搜索用户组件，多选后提交添加成员，已在本空间的用户自动跳过。

- **InvitationCreateModal**：设置过期时间和最大使用次数，生成后显示URL和复制按钮。

- **ProjectListPage**：根据工作空间内项目数量自动切换视图。空间无项目时显示引导创建区域；有项目时显示项目卡片列表。项目卡片支持搜索筛选、创建、编辑、归档、启封、删除。每张卡片提供“设为默认”按钮（调用 `PUT /api/workspace/default-project`）和“进入”按钮。当前默认项目卡片有⭐标识。

- **ProjectFormModal**：创建/编辑项目的表单弹窗，包含名称、描述、开始时间、结束时间字段。

- **JoinPage**：公开的邀请加入页面，验证token、展示空间信息、填写邮箱密码、提交后直接加入并登录，跳转至项目列表页。

---


## 6. 错误码定义

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
| 1000002002  | 403      | 不能移除自己（当自己是唯一管理员时） |

---


## 7. 安全设计

- 所有接口通过中间件验证 JWT Token。
- 空间管理接口（/api/workspace）强制校验 `X-Active-Workspace` 头，并验证用户是否属于该工作空间。
- 管理员专属操作额外校验 `workspaceRole` 对应预置 `workspace_admin` 角色 ID。
- 写操作记录操作日志（操作人、时间、IP、操作类型、变更内容）。
- 邀请链接 token 使用安全的随机生成算法。
- 邀请加入接口设置频率限制（同一 IP 每分钟最多 5 次），防止暴力破解密码。
- 密码传输使用 HTTPS，后端哈希存储。

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `14-space-management-overview.md` | 前言、1. 引言、2. 数据设计、3.1 通用约定、4.5 权限矩阵、5.1 总体布局、5.2 路由规划、5.4 状态管理（Pinia Store）、5.5 核心组件、6. 错误码定义、7. 安全设计 |
| 我的空间 | `15-space-management-my-workspace.md` | 3.2 我的空间相关接口、4.1 工作空间切换流程、4.2 个人默认项目设置规则、5.3.1 我的空间页面 |
| 空间与成员管理 | `16-space-management-workspace-admin.md` | 3.3 空间管理相关接口、4.3 项目归档/删除联动、4.4 邀请成员流程、5.3.2 工作空间详情页、5.3.3 成员管理页、5.3.4 项目列表页 |
| 公开邀请 | `17-space-management-invite-join.md` | 3.4 公开接口、5.3.5 邀请加入页面 |
