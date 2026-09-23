# 软件测试平台——角色与权限管理

**文档版本**：V1.0  
**日期**：2026-09-23  
**状态**：起草中

---

## 1. 角色与权限管理接口

### 1.1 获取角色列表

- **路径**：`GET /api/admin/roles`

- **请求参数**：

| 参数   | 类型     | 必填  | 说明                        |
| ----- | ------ | --- | ------------------------- |
| type  | string | 否   | system=系统角色, workspace=空间角色, 不传=全部 |

- **说明**：返回所有角色的扁平列表，前端按 `type` 字段自行分组构建树结构。不再使用 `/tree` 后缀，改为可选 `type` 参数过滤。

- **响应**：
  
  ```json
  [
    {
      "id": "uuid-1",
      "name": "系统管理员",
      "type": "system",
      "isSystem": true,
      "userCount": 2
    },
    {
      "id": "uuid-2",
      "name": "用户管理员",
      "type": "system",
      "isSystem": false,
      "userCount": 5
    },
    {
      "id": "uuid-3",
      "name": "空间管理员",
      "type": "workspace",
      "isSystem": true,
      "userCount": 3
    }
  ]
  ```

### 1.2 创建角色

- **路径**：`POST /api/admin/roles`
- **请求体**：`{ "name": "新角色", "type": "system" }`
- **校验**：`name` 唯一；`type` 仅允许 `"system"`；`permissions` 初始为空数组。
- **响应**：返回创建的角色信息，状态码 201。

### 1.3 更新角色名称

- **路径**：`PUT /api/admin/roles/:id`
- **请求体**：`{ "name": "新名称" }`
- **校验**：名称唯一；仅允许修改系统角色。
- **响应**：返回更新后的角色信息。

### 1.4 删除角色

- **路径**：`DELETE /api/admin/roles/:id`
- **校验**：系统预置角色（is_system=true）不可删除；若存在用户引用该角色，禁止删除并返回引用用户列表。
- **响应**：成功提示。

### 1.5 获取角色详情

- **路径**：`GET /api/admin/roles/:id`
- **响应**：`{ id, name, description, type, isSystem, permissions: [], userCount }`

### 1.6 获取角色关联用户

- 系统角色：**复用用户分页接口** `GET /api/admin/users?roleId=:roleId`（可叠加 `keyword`、`status`、`pageNo`、`pageSize` 参数），响应为用户分页结构。
- 工作空间角色：`GET /api/admin/roles/:id/workspace-users`，响应为按用户聚合的关联清单：

  ```json
  [
    {
      "userId": "uuid",
      "username": "zhangsan",
      "name": "张三",
      "workspaces": [{ "workspaceId": "uuid", "workspaceName": "电商平台" }]
    }
  ]
  ```

- **权限**：`role:view`

### 1.7 添加角色关联用户（系统角色）

- **路径**：`POST /api/admin/roles/:id/users`
- **请求体**：`{ "userIds": ["uuid-1", "uuid-2"] }`
- **权限**：`role:edit`
- **响应**：成功提示；批量插入 `sys_user_role` 关联。

### 1.8 移除角色关联用户（系统角色）

- **路径**：`DELETE /api/admin/roles/:id/users/:userId`
- **权限**：`role:edit`
- **响应**：成功提示。

### 1.9 更新角色权限

- **路径**：`PUT /api/admin/roles/:id/permissions`
- **请求体**：`{ "permissions": ["user:view", "user:create"] }`
- **校验**：
  - 系统预置角色的已有权限不可移除，只能追加新权限。
  - 全量权限（full_access=true）的角色不可编辑权限，返回 400 错误。
- **响应**：返回更新后的角色信息。

### 1.10 获取权限点表格数据

- **路径**：`GET /api/admin/permissions/table`

- **说明**：返回按模块分组的权限点列表，供表格形式展示。可通过 `roleType` 参数过滤对应作用域的权限点。

- **请求参数**：

| 参数       | 类型     | 必填  | 说明                        |
| -------- | ------ | --- | ------------------------- |
| roleType | string | 否   | system=仅系统权限, workspace=仅空间权限, 不传=全部 |

- **响应**：
  
  ```json
  [
  {
    "module": "用户管理",
    "permissions": [
      { "code": "user:view", "name": "查看用户", "scope": "global" },
      { "code": "user:create", "name": "创建用户", "scope": "global" },
      { "code": "user:edit", "name": "编辑用户", "scope": "global" },
      { "code": "user:disable", "name": "禁用/启用用户", "scope": "global" },
      { "code": "user:reset-password", "name": "重置密码", "scope": "global" }
    ]
  },
  {
    "module": "工作空间管理",
    "permissions": [
      { "code": "workspace:view", "name": "查看工作空间", "scope": "global" },
      { "code": "workspace:create", "name": "创建工作空间", "scope": "global" },
      { "code": "workspace:edit", "name": "编辑工作空间", "scope": "global" },
      { "code": "workspace:delete", "name": "解散工作空间", "scope": "global" },
      { "code": "workspace:manage-members", "name": "管理成员", "scope": "global" }
    ]
  },
  {
    "module": "角色管理",
    "permissions": [
      { "code": "role:view", "name": "查看角色", "scope": "global" },
      { "code": "role:create", "name": "创建角色", "scope": "global" },
      { "code": "role:edit", "name": "编辑角色", "scope": "global" },
      { "code": "role:delete", "name": "删除角色", "scope": "global" }
    ]
  }
  ]
  ```

### 1.11 批量添加角色关联用户（工作空间角色）

- **路径**：`POST /api/admin/roles/:id/workspace-users`
- **请求体**：

  ```json
  {
    "userIds": ["uuid-1", "uuid-2"],
    "workspaceIds": ["ws-uuid-1", "ws-uuid-2"]
  }
  ```

- **校验**：`userIds`、`workspaceIds` 均必填非空（`@NotEmpty`）；为指定用户在指定工作空间上批量绑定该工作空间角色。
- **权限**：`role:edit`
- **响应**：成功提示。

### 1.12 移除角色关联用户（工作空间角色，按空间维度）

- **路径**：`DELETE /api/admin/roles/:id/users/:userId/workspace/:workspaceId`
- **权限**：`role:edit`
- **响应**：成功提示；仅解除该用户在此工作空间上的该角色绑定，不影响其在其他工作空间的关联。


## 2. 角色管理流程

### 2.1 创建角色

- 点击类型根节点（如“系统角色”）的[+]按钮，前端在树中新增可编辑节点。
- 输入角色名称，失焦后调用 `POST /api/admin/roles`，提交 `name` 和 `type='system'`。
- 后端校验名称唯一性，permissions 初始化为空数组，插入 sys_role 表。
- 返回新角色信息，前端更新树节点。

### 2.2 编辑角色名称

- 双击树节点进入编辑模式，修改名称后失焦自动调用 `PUT /api/admin/roles/:id`。
- 后端校验名称唯一性，更新 sys_role.name。
- 成功后更新树节点显示。

### 2.3 删除角色

- 右键角色节点或选中后点击删除按钮，前端弹出二次确认弹窗。
- 确认后调用 `DELETE /api/admin/roles/:id`。
- 后端校验：系统预置角色（is_system=true）不可删除；若 sys_user_role 中存在引用，返回引用用户列表，阻止删除。
- 成功后从树中移除节点。

### 2.4 权限配置

- 单击角色节点，右侧加载角色详情，默认激活"权限配置"Tab。
- 调用 `GET /api/admin/roles/:id` 获取角色已有权限列表，调用 `GET /api/admin/permissions/table?roleType=<type>` 获取对应作用域的权限点。
- 前端渲染权限表格，每行一个模块，权限点列为复选框。已在角色权限列表中的 code 勾选。
- 全量权限（full_access=true）的角色：权限配置面板锁定，复选框全部禁用，显示"全量权限"提示，不可手动编辑。
- 非全量权限的角色：用户可勾选/取消复选框。系统预置角色的既有权限复选框禁用（灰显不可取消）。
- 点击[撤销修改]：恢复到上次保存时的权限快照。
- 点击[保存权限]：调用 `PUT /api/admin/roles/:id/permissions`，提交当前选中的权限 code 列表。
- 后端校验：全量权限角色拒绝编辑；系统预置角色权限不可移除。保存并返回更新后信息。

### 2.5 关联用户管理

- 切换到“关联用户”Tab，系统角色调用 `GET /api/admin/users?roleId=:roleId` 分页加载关联用户列表；工作空间角色调用 `GET /api/admin/roles/:id/workspace-users` 聚合加载（见 1.6）。
- 点击[添加用户]弹出搜索弹窗，支持多选和远程搜索活跃用户，系统角色提交后调用 `POST /api/admin/roles/:id/users` 批量插入 sys_user_role；工作空间角色调用 `POST /api/admin/roles/:id/workspace-users`（同时携带 `workspaceIds`）。
- 每行用户有[移除]按钮，点击二次确认后系统角色调用 `DELETE /api/admin/roles/:id/users/:userId`，工作空间角色按空间行调用 `DELETE /api/admin/roles/:id/users/:userId/workspace/:workspaceId`。
- 操作完成后刷新列表。


### 2.6 角色管理页（RoleManagementPage）

```
RoleManagementPage
├── RoleTreePanel（左侧，宽度260px）
│   ├── el-tree（前端按 type 分组构建）
│   │   ├── 系统角色（分组节点） [+ 按钮]
│   │   │   ├── 系统管理员
│   │   │   ├── 用户管理员
│   │   │   └── ...
│   │   └── 工作空间角色（分组节点） [+ 按钮]
│   │       ├── 空间管理员
│   │       └── ...
│   └── 右键菜单（删除角色）
└── RoleDetailPanel（右侧）
    ├── el-tabs
    │   ├── Tab1: 权限配置 (PermissionTable)
    │   └── Tab2: 关联用户 (RoleUsersTable)
    └── 操作按钮 ([撤销修改] [保存权限])
```


### 2.7 PermissionTable组件

使用 el-table 渲染权限点，列为“操作对象”和“权限点”。权限点列中每个权限渲染为 el-checkbox。系统预置角色的已有权限复选框添加 disabled 属性。


### 2.8 RoleUsersTable组件

表格展示关联用户（用户名、邮箱、状态、操作）。顶部[添加用户]按钮，每行[移除]按钮。


## 3. 关键组件交互

### 3.1 角色树组件（RoleTreePanel）

- **数据源**：`GET /api/admin/roles/tree` 返回角色扁平列表，前端按 `type` 分组构建树结构（系统角色、工作空间角色）。
- **添加角色**：点击分组节点的[+]按钮，在对应类型下新增角色。输入名称后失焦，校验通过则调用创建角色接口，成功后刷新列表。
- **编辑角色**：点击编辑按钮，弹窗输入新名称，确认后调用更新接口。
- **删除角色**：点击删除按钮，二次确认后调用删除接口。
- **选择角色**：单击角色节点，右侧加载角色详情，默认激活“权限配置”Tab。

### 3.2 权限表格组件（PermissionTable）

- **数据源**：`GET /api/admin/permissions/table` 返回的模块分组数组。
- **勾选状态**：根据角色已有权限 code 列表初始化复选框状态。用户勾选/取消时更新本地快照。
- **撤销修改**：点击[撤销修改]按钮，恢复到上次保存时的权限快照。
- **保存权限**：点击[保存权限]按钮，收集所有选中 code，调用更新角色权限接口。成功后更新本地快照，Toast提示。

### 3.3 关联用户组件（RoleUsersTable）

- **数据源**：系统角色走 `GET /api/admin/users?roleId=:roleId` 分页加载；工作空间角色走 `GET /api/admin/roles/:id/workspace-users`（见 1.6）。
- **添加用户**：点击[添加用户]弹出搜索弹窗，支持多选和远程搜索活跃用户，提交后调用对应批量添加接口（见 1.7、1.11）。
- **移除用户**：每行[移除]按钮，二次确认后调用移除接口（见 1.8、1.12），刷新列表。

### 3.4 角色选择器（用户表单中使用）

- 多选下拉，仅显示系统角色（调用 `GET /api/admin/roles` 过滤 type='system'）。
- 允许不选任何角色。
- 创建/编辑用户时使用，支持搜索过滤。


