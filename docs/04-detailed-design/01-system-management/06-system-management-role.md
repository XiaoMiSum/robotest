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

- **说明**：返回角色扁平列表，前端按 `type` 字段分组构建树结构（见 3.1）。
- **权限**：`role:view`（兼 `workspace:view`、`workspace:manage-members`）。

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
- **校验**：`name` 非空且唯一；`type` 必填，仅允许 `"system"` 或 `"workspace"`（前端按所点击的分组类型提交）；`permissions` 初始化为空数组，`isSystem` 固定为 `false`。
- **权限**：`role:create`
- **响应**：返回新角色的 id 字符串（HTTP 200），前端用于选中并刷新树节点。

### 1.3 更新角色名称

- **路径**：`PUT /api/admin/roles/:id`
- **请求体**：`{ "name": "新名称" }`
- **校验**：`name` 非空且唯一（同名的其他角色拒绝）；后端不区分角色类型，前端仅对自定义角色（`isSystem=false`）提供重命名入口，预置角色不展示入口。
- **权限**：`role:edit`
- **响应**：返回更新后的角色信息（含 `userCount`）。

### 1.4 删除角色

- **路径**：`DELETE /api/admin/roles/:id`
- **校验**：系统预置角色（is_system=true）不可删除；若存在用户引用该角色，禁止删除并返回错误提示（前端 toast 展示，不返回引用用户列表）。前端仅对自定义角色展示删除入口。
- **权限**：`role:delete`
- **响应**：无数据。

### 1.5 获取角色详情

- **路径**：`GET /api/admin/roles/:id`
- **权限**：`role:view`
- **响应**：`{ id, name, description, type, isSystem, permissions: [], userCount }`

### 1.6 获取角色关联用户

- 系统角色：**复用用户分页接口** `GET /api/admin/users?roleId=:roleId`（可叠加 `keyword`、`status`、`pageNo`、`pageSize` 参数），响应为用户分页结构；按角色过滤时，每条记录的 `grantedAt` 取 `sys_user_role.updated_at`，未按角色过滤时为 `null`。权限为 `user:view` 或 `role:view`。
- 工作空间角色：`GET /api/admin/roles/:id/workspace-users?pageNo=1&pageSize=20`，权限为 `role:view`；按用户聚合后返回分页结构；`grantedAt` 取该用户各归属空间关联记录 `workspace_user.updated_at` 的最近值：

  ```json
  {
    "list": [
      {
        "userId": "uuid",
        "username": "zhangsan",
        "name": "张三",
        "workspaces": [{ "workspaceId": "uuid", "workspaceName": "电商平台" }],
        "grantedAt": "2026-05-20T08:30:00Z"
      }
    ],
    "total": 1
  }
  ```

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
  - 系统预置角色（is_system=true）整体拒绝修改权限，返回错误码；前端对预置角色不展示编辑入口（见 2.4）。
- **权限**：`role:edit`
- **响应**：返回更新后的角色信息。

### 1.10 获取权限点表格数据

- **路径**：`GET /api/admin/roles/permissions/table`

- **说明**：返回按「一级模块 → 二级模块 → 权限点」两级分组的权限点列表，供表格形式展示。前端按所选角色的 `type` 传入 `roleType`。

- **请求参数**：

| 参数       | 类型     | 必填  | 说明                                                        |
| -------- | ------ | --- | ----------------------------------------------------------- |
| roleType | string | 否   | workspace=空间作用域（scope=workspace）权限点；其余值（含 system、不传）=全局作用域（scope=global）权限点 |

- **权限**：`role:view`

- **响应**（示例为摘录，`roleType=system` 时一级模块为"系统管理"，`roleType=workspace` 时为"我的空间 / 功能测试 / 接口测试 / 缺陷管理"）：
  
  ```json
  [
    {
      "topModule": "系统管理",
      "modules": [
        {
          "module": "用户管理",
          "permissions": [
            { "code": "user:view", "name": "查看用户" },
            { "code": "user:create", "name": "创建用户" },
            { "code": "user:edit", "name": "编辑用户" },
            { "code": "user:disable", "name": "禁用/启用用户" },
            { "code": "user:reset-password", "name": "重置密码" }
          ]
        },
        {
          "module": "角色管理",
          "permissions": [
            { "code": "role:view", "name": "查看角色" },
            { "code": "role:create", "name": "创建角色" },
            { "code": "role:edit", "name": "编辑角色" },
            { "code": "role:delete", "name": "删除角色" }
          ]
        }
      ]
    },
    {
      "topModule": "我的空间",
      "modules": [
        {
          "module": "我的空间",
          "permissions": [
            { "code": "ws-info:view", "name": "查看空间信息" },
            { "code": "ws-info:edit", "name": "编辑空间信息" }
          ]
        }
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

- 点击类型分组节点（"系统角色"或"工作空间角色"）的[+]按钮，弹出输入框（`ElMessageBox.prompt`），提示"请输入角色名称"，非空校验（`\S+`）。
- 确认后调用 `POST /api/admin/roles`，提交 `name`（去除首尾空白）与该分组的 `type`（`system` 或 `workspace`）。
- 后端校验名称唯一性与类型合法性，permissions 初始化为空数组、is_system=false，插入 sys_role 表。
- 成功后提示"角色已创建"，刷新角色树并自动选中新角色（右侧显示"权限点"Tab）；失败则 toast 错误信息。

### 2.2 编辑角色名称

- 仅自定义角色（非预置）：选中节点后点击[重命名]按钮，弹出输入框（默认值为原名称，非空校验），确认后调用 `PUT /api/admin/roles/:id`。
- 后端校验名称唯一性，更新 sys_role.name。
- 成功后提示"已重命名"并刷新树节点；预置角色不展示重命名按钮。

### 2.3 删除角色

- 仅自定义角色（非预置）：选中节点后点击[删除]按钮，弹出二次确认弹窗。
- 确认后调用 `DELETE /api/admin/roles/:id`。
- 后端校验：系统预置角色（is_system=true）不可删除；若 sys_user_role 中存在引用，返回错误提示，阻止删除。
- 成功后提示"已删除"并刷新树；若删除的是当前选中角色，右侧详情清空为空态。

### 2.4 权限点配置

- 单击角色节点，右侧加载角色详情，默认激活"权限点"Tab。
- 并行调用 `GET /api/admin/roles/:id` 获取角色已有权限列表、`GET /api/admin/roles/permissions/table?roleType=<type>` 获取对应作用域的权限点。
- 前端渲染权限表格：一级模块（组合并单元格）、二级模块、权限点（复选框），按 `topModule → module → permissions` 分组；已在角色权限列表中的 code 勾选。
- 系统预置角色（isSystem=true）：权限表格只读——复选框全部禁用、表头全选框禁用、不显示[撤销修改]/[保存权限]按钮；后端同样拒绝其权限修改。
- 自定义角色：可勾选/取消复选框；表头右侧显示[撤销修改]与[保存权限]，仅当本地勾选与上次保存快照不一致（dirty）时可用，保存中按钮 loading。
- 表头全选框：对当前表格全部未禁用权限点整选/整消，带半选（indeterminate）态。
- 点击[撤销修改]：恢复到上次保存时的权限快照。
- 点击[保存权限]：调用 `PUT /api/admin/roles/:id/permissions`，提交当前选中的权限 code 列表；成功后以响应更新本地快照并提示"权限已保存"。
- 未选中角色时，右侧显示空态（"请选择左侧角色查看详情"）；加载过程显示 v-loading 遮罩。

### 2.5 关联用户管理

- 切换到"关联用户"Tab，系统角色调用 `GET /api/admin/users?roleId=:roleId&pageNo=&pageSize=` 分页加载关联用户列表；工作空间角色调用 `GET /api/admin/roles/:id/workspace-users?pageNo=&pageSize=` 聚合分页（见 1.6）。列表展示授权时间：系统角色取角色关联更新时间，工作空间角色取多空间关联记录中的最近更新时间，经 `formatDateTime` 按浏览器时区展示。
- 服务端分页：默认每页 20 条，可切换 20 / 50 / 100；切换角色时回到第 1 页；当前页无数据且总条数大于 0 时自动回退到最后一页。
- 点击[添加用户]（操作列表头）弹出搜索弹窗，支持多选和远程搜索活跃用户（按姓名搜索，已关联用户从候选中过滤）；空间角色弹窗标题为"添加关联用户与空间"，还需至少选择一个空间；提交前校验至少选择一个用户。
- 提交后：系统角色调用 `POST /api/admin/roles/:id/users` 批量插入 sys_user_role；工作空间角色调用 `POST /api/admin/roles/:id/workspace-users`（同时携带 `workspaceIds`）。成功后提示"已添加用户"、关闭弹窗、回到第 1 页刷新列表。
- 每行用户有[移除]按钮：
  - 系统角色：二次确认后调用 `DELETE /api/admin/roles/:id/users/:userId`；
  - 工作空间角色：该用户仅关联 1 个空间时，二次确认后直接调用按空间移除接口；关联多个空间时，弹出"选择要移除的空间"复选框弹窗（未选空间时提示），确认后按选中空间批量调用 `DELETE /api/admin/roles/:id/users/:userId/workspace/:workspaceId`。
- 操作完成后刷新列表。


### 2.6 角色管理页（RolePage）

- **路由与菜单**：路由 `/admin/roles`（name `AdminRoles`，`meta.title` 角色管理，mode `admin`）；菜单 label 角色管理 / icon Key / order 20 / section 组织与权限 / permission `role:view`。

```
RolePage
├── 页头（标题「角色管理」+ 描述「系统角色的权限点配置与关联用户」）
└── role-layout（grid：280px | 1fr，gap 24px；≤768px 单列堆叠）
    ├── RoleTreePanel（左侧卡片）
    │   ├── 卡头：角色列表 + 具体角色数
    │   └── el-tree（前端按 type 分组构建，默认全部展开）
    │       ├── 系统角色（分组节点） [+ 按钮常驻]
    │       │   ├── 系统管理员（预置 tag、人数徽标）
    │       │   └── 自定义角色（选中时显示 [重命名] [删除]）
    │       └── 工作空间角色（分组节点） [+ 按钮常驻]
    │           └── ...
    └── 右侧卡片（el-tabs，Tab 头固定）
        ├── Tab: 权限点 (PermissionTable，表头含 全选 + [撤销修改] [保存权限])
        └── Tab: 关联用户 (RoleUsersTable，表格 + 分页钉底)
```

- **状态分支**：未选中角色时两个 Tab 均显示 `el-empty`"请选择左侧角色查看详情"；树与各表格加载中显示 v-loading；接口失败 toast 错误信息；点击分组节点仅切换高亮，不改变右侧已选角色的详情。


### 2.7 PermissionTable组件

使用 el-table 渲染权限点，列为"一级模块"（组合并单元格，垂直居中）、"二级模块"和"权限点"（el-checkbox 复选框横向换行排列）。表头含全选框与列名（左侧）、[撤销修改]/[保存权限]按钮（右侧，仅非预置角色显示）；系统预置角色的所有复选框添加 disabled 属性且隐藏操作按钮。表头固定，权限点较多时仅表格内容区内部滚动，不滚动外层面板。


### 2.8 RoleUsersTable组件

系统角色表格展示关联用户（用户名、姓名、邮箱、状态、授权时间、操作）；工作空间角色表格展示用户名、姓名、归属空间（el-tag 列表）、授权时间、操作。两类表格均采用服务端分页，表头固定、内容区内部滚动，分页条（layout：total / sizes / prev / pager / next，页大小档位 20 / 50 / 100）固定在表格底部；[添加用户]按钮位于右侧固定操作列的表头，每行[移除]按钮。


## 3. 关键组件交互

### 3.1 角色树组件（RoleTreePanel）

- **数据源**：`GET /api/admin/roles` 返回角色扁平列表，前端按 `type` 分组构建树结构（分组节点 id 为 `type-system` / `type-workspace`，两组恒显示）；卡头展示具体角色总数。
- **节点展示**：`isSystem` 的角色显示"预置"标签；有 `userCount` 的角色显示人数徽标。分组节点的[+]按钮常驻；叶节点的[重命名]/[删除]按钮仅在节点被选中时显示，且仅自定义角色显示。
- **添加角色**：点击分组节点的[+]按钮，弹出输入框校验名称非空，通过则调用创建角色接口，成功后刷新列表并选中新角色。
- **编辑角色**：点击[重命名]按钮，弹出输入框（默认值为原名称），确认后调用更新接口并刷新列表。
- **删除角色**：点击[删除]按钮，二次确认后调用删除接口；删除当前选中角色时同时清空右侧详情。
- **选择角色**：单击角色节点，右侧加载角色详情，默认激活"权限点"Tab；单击分组节点仅切换高亮。
- **状态分支**：加载中显示 v-loading；加载失败 toast"加载角色树失败"，创建/重命名/删除失败 toast 对应错误信息；取消弹窗静默返回。

### 3.2 权限表格组件（PermissionTable）

- **数据源**：`GET /api/admin/roles/permissions/table?roleType=<type>` 返回的一级模块分组数组（见 1.10），与角色详情并行加载。
- **勾选状态**：根据角色已有权限 code 列表初始化复选框状态。用户勾选/取消时更新本地勾选集合，并与已保存快照比对得出 dirty 态。
- **全选**：表头全选框作用于全部未禁用权限点，支持半选态；预置角色禁用。
- **撤销修改**：点击[撤销修改]按钮（仅 dirty 可用），恢复到上次保存时的权限快照。
- **保存权限**：点击[保存权限]按钮（仅 dirty 可用，保存中 loading），收集所有选中 code，调用更新角色权限接口。成功后以响应更新本地快照，提示"权限已保存"。
- **只读分支**：预置角色（isSystem=true）复选框全部禁用、全选框禁用、操作按钮不渲染。
- **滚动区域**：表头固定，权限点内容在 el-table 内部滚动，外层 TabPane 不产生纵向滚动条。

### 3.3 关联用户组件（RoleUsersTable）

- **数据源**：系统角色走 `GET /api/admin/users?roleId=:roleId&pageNo=&pageSize=` 分页加载；工作空间角色走 `GET /api/admin/roles/:id/workspace-users?pageNo=&pageSize=` 分页加载（见 1.6）。授权时间按接口返回的 `grantedAt` 展示。
- **分页**：两类角色默认每页 20 条，支持切换 20 / 50 / 100 条；切换角色时回到第 1 页并清空旧数据；当前页为空且有数据时回退到最后一页。
- **添加用户**：点击[添加用户]弹出搜索弹窗（见 1.7、1.11），支持多选和远程搜索活跃用户（按姓名搜索，过滤已关联用户）；空间角色还需选择至少一个空间，提交后调用对应批量添加接口。
- **移除用户**：每行[移除]按钮——系统角色二次确认后调用移除接口（见 1.8）；空间角色单空间直接确认移除，多空间先弹"选择要移除的空间"弹窗（见 1.12），确认后批量移除，完成均刷新列表。

### 3.4 角色选项（用户管理中使用）

- **用户表单**："系统角色"卡片使用复选框组（el-checkbox-group）多选系统角色，允许不选任何角色，无搜索过滤。
- **用户列表筛选**：角色单选下拉，支持搜索（filterable）与清空，选择后触发查询。
- **数据源**：两处选项均调用 `GET /api/admin/roles?type=system`，仅包含系统角色；选项加载失败不阻塞表单。


## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-02 | 对齐角色管理页实现：修正权限点表格接口路径与两级分组响应结构、增删改交互改为弹窗、权限点 Tab 预置角色只读与按钮 dirty 门控、关联用户多空间移除分支、角色选项改为复选框组 |
