# 软件测试平台——项目工作区详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台中的**功能测试模块**进行详细设计，定义数据结构、接口规范、业务逻辑、前端组件及交互流程，为开发实现提供完整依据。

### 1.2 范围

功能测试模块面向**业务用户**，在已选定的项目内提供测试用例管理、测试评审、测试计划、需求池及缺陷管理功能。所有操作限定在当前活跃工作空间和项目上下文中，活动上下文通过请求头 `X-Active-Workspace` 与 `X-Active-Project` 传递。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》
- 《软件测试平台概要设计说明书》
- 《项目工作区页面交互设计》
- 《脑图组件实现设计》

---


## 2. 数据设计

### 2.1 数据库表设计

数据库字段使用 snake_case，接口 JSON 使用 camelCase。

#### 2.1.1 项目模块表（project_module）

项目级统一模块树：功能用例文档、接口定义、测试场景等测试资产共享同一棵树组织。目录节点存于本表，用例文档作为独立资产存于 `test_case_document`（见 2.1.3），接口与场景通过各自 `module_id` 挂载。表结构、索引与同级名称唯一约束见 `docs/04-detailed-design/02-project-module.md` 2.1，此处不重复描述。

各资产表通过 `module_id` 引用 `project_module.id`，`NULL` 表示未分组。

#### 2.1.2 测试用例节点表（test_case_node）

| 字段          | 类型                                                     | 约束                                    | 说明             |
| ----------- | ------------------------------------------------------ | ------------------------------------- | -------------- |
| id          | binary(16)                                             | PK                                     | 节点ID           |
| document_id | binary(16)                                             | NOT NULL                               | 所属文档           |
| parent_id   | binary(16)                                             | NULL                                   | 父节点ID          |
| type        | enum('case','normal','precondition','step','expected') | NOT NULL, DEFAULT 'normal'            | 节点类型           |
| title       | varchar(200)                                           | NOT NULL                              | 标题             |
| priority    | varchar(2)                                             | NULL                                  | 优先级编码（仅case节点） |
| sort_order  | int                                                    | NOT NULL, DEFAULT 0                   | 排序号            |
| version     | int                                                    | NOT NULL, DEFAULT 1                   | 乐观锁版本号         |
| created_at  | datetime                                               | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间           |
| updated_at  | datetime                                               | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间           |
| is_deleted  | tinyint(1)                                             | NOT NULL, DEFAULT 0                   | 是否删除           |

**索引**：`idx_document_id` (document_id), `idx_parent_id` (parent_id)

#### 2.1.3 用例文档表（test_case_document）

用例以脑图文档形式存储，`module_id` 引用 `project_module.id`（见 2.1.1），脑图布局以 `layout` 列内嵌保存（原 `test_case_document_layout` 表已废弃，布局数据合并至本表）。表结构、`layout` JSON 格式与索引见 `docs/04-detailed-design/03-function-testing/07-document-management.md` 2.1，此处不重复描述。

#### 2.1.4 测试计划表（test_plan）

| 字段          | 类型                                             | 约束                                    | 说明   |
| ----------- | ---------------------------------------------- | ------------------------------------- | ---- |
| id          | binary(16)                                         | PK                                     | 计划ID |
| project_id  | binary(16)                                         | NOT NULL                               | 所属项目 |
| name        | varchar(100)                                   | NOT NULL                              | 计划名称 |
| description | text                                           | NULL                                  | 描述   |
| status      | enum('new','in_progress','completed','closed') | NOT NULL, DEFAULT 'new'               | 状态   |
| executor_id | binary(16)                                         | NULL                                   | 负责人  |
| start_time  | datetime                                       | NULL                                  | 开始时间 |
| end_time    | datetime                                       | NULL                                  | 结束时间 |
| environment | varchar(200)                                   | NULL                                  | 执行环境 |
| created_at  | datetime                                       | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间 |
| updated_at  | datetime                                       | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| is_deleted  | tinyint(1)                                     | NOT NULL, DEFAULT 0                   | 是否删除 |

#### 2.1.5 计划模块快照表（test_plan_module_snapshot）

| 字段                 | 类型                           | 约束                                      | 说明      |
| ------------------ | ---------------------------- | --------------------------------------- | ------- |
| id                 | binary(16)                  | PK                                     | 快照模块ID  |
| plan_id            | binary(16)                  | NOT NULL                               | 关联计划    |
| original_module_id | binary(16)                  | NULL                                   | 原始模块ID  |
| parent_id          | binary(16)                  | NULL                                   | 父快照模块ID |
| name               | varchar(100)                 | NOT NULL                                | 名称      |
| type               | enum('directory','document') | NOT NULL                                | 类型      |
| sort_order         | int                          | NOT NULL, DEFAULT 0                     | 排序      |
| is_deleted         | tinyint(1)                   | NOT NULL, DEFAULT 0                     | 是否已删除   |
| created_at         | datetime                     | NOT NULL, DEFAULT CURRENT_TIMESTAMP     | 创建时间    |
| updated_at         | datetime                     | NOT NULL, ON UPDATE CURRENT_TIMESTAMP   | 更新时间    |

#### 2.1.6 计划节点快照表（test_plan_node_snapshot）

| 字段                   | 类型                                                     | 约束                                    | 说明          |
| -------------------- | ------------------------------------------------------ | ------------------------------------- | ----------- |
| id                   | binary(16)                                             | PK                                     | 快照节点ID      |
| plan_id              | binary(16)                                             | NOT NULL                               | 关联计划        |
| original_node_id     | binary(16)                                             | NULL                                   | 原始节点ID      |
| document_snapshot_id | binary(16)                                             | NOT NULL                               | 所属文档快照模块    |
| parent_id            | binary(16)                                             | NULL                                   | 父快照节点ID     |
| title                | varchar(200)                                           | NOT NULL                              | 标题          |
| type                 | enum('case','normal','precondition','step','expected') | NOT NULL                              | 类型          |
| priority             | varchar(2)                                             | NULL                                  | 优先级编码（完整拷贝） |
| is_associated        | tinyint(1)                                             | NOT NULL, DEFAULT 0                   | 是否为关联节点     |
| is_deleted           | tinyint(1)                                             | NOT NULL, DEFAULT 0                   | 是否已删除       |
| last_result          | enum('pass','fail','block','untested')                 | DEFAULT 'untested'                    | 最后执行结果      |
| last_executor_id     | binary(16)                                             | NULL                                   | 最后执行人       |
| last_executed_at     | datetime                                               | NULL                                  | 最后执行时间      |
| sort_order           | int                                                    | NOT NULL, DEFAULT 0                   | 排序          |
| created_at           | datetime                                               | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间        |
| updated_at           | datetime                                               | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间        |

#### 2.1.7 计划执行记录表（test_plan_execution_record）

| 字段               | 类型                                     | 约束                                        | 说明     |
| ---------------- | -------------------------------------- | ----------------------------------------- | ------ |
| id               | binary(16)                                             | PK                                     | 记录ID   |
| plan_id          | binary(16)                                             | NOT NULL                               | 计划ID   |
| snapshot_node_id | binary(16)                                             | NOT NULL                               | 快照节点ID |
| executor_id      | binary(16)                                             | NOT NULL                               | 执行人    |
| result           | enum('pass','fail','block','untested')                 | NOT NULL                              | 执行结果   |
| note             | text                                                   | NULL                                   | 备注     |
| executed_at      | datetime                                               | NOT NULL, DEFAULT CURRENT_TIMESTAMP    | 执行时间   |
| created_at       | datetime                                               | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间   |
| updated_at       | datetime                                               | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间   |
| is_deleted       | tinyint(1)                                             | NOT NULL, DEFAULT 0                   | 是否删除   |

#### 2.1.8 测试评审表（test_review）

| 字段              | 类型                              | 约束                                  | 说明      |
| --------------- | ------------------------------- | ----------------------------------- | ------- |
| id              | binary(16)                     | PK                                     | 评审ID    |
| project_id      | binary(16)                     | NOT NULL                               | 所属项目    |
| title           | varchar(200)                    | NOT NULL                            | 标题      |
| description     | text                            | NULL                                | 描述      |
| initiator_id    | binary(16)                     | NOT NULL                             | 发起人     |
| participant_ids | json                            | NOT NULL                            | 参与者ID列表 |
| status          | enum('new','in_progress','completed') | NOT NULL, DEFAULT 'new'     | 状态      |
| created_at      | datetime                        | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间    |
| updated_at      | datetime                        | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间  |
| is_deleted      | tinyint(1)                      | NOT NULL, DEFAULT 0                 | 是否删除    |

#### 2.1.9 评审模块快照表（test_review_module_snapshot）

| 字段                 | 类型                           | 约束                                        | 说明      |
| ------------------ | ---------------------------- | ----------------------------------------- | ------- |
| id                 | binary(16)                  | PK                                     | 快照模块ID  |
| review_id          | binary(16)                  | NOT NULL                               | 关联评审    |
| original_module_id | binary(16)                  | NULL                                   | 原始模块ID  |
| parent_id          | binary(16)                  | NULL                                   | 父快照模块ID |
| name               | varchar(100)                 | NOT NULL                                  | 名称      |
| type               | enum('directory','document') | NOT NULL                                  | 类型      |
| sort_order         | int                          | NOT NULL, DEFAULT 0                       | 排序      |
| is_deleted         | tinyint(1)                   | NOT NULL, DEFAULT 0                       | 是否已删除   |
| created_at         | datetime                     | NOT NULL, DEFAULT CURRENT_TIMESTAMP       | 创建时间    |
| updated_at         | datetime                     | NOT NULL, ON UPDATE CURRENT_TIMESTAMP     | 更新时间    |

#### 2.1.10 评审节点快照表（test_review_node_snapshot）

| 字段                   | 类型                                                     | 约束                                      | 说明          |
| -------------------- | ------------------------------------------------------ | --------------------------------------- | ----------- |
| id                   | binary(16)                                             | PK                                     | 快照节点ID      |
| review_id            | binary(16)                                             | NOT NULL                               | 关联评审        |
| original_node_id     | binary(16)                                             | NULL                                   | 原始节点ID      |
| document_snapshot_id | binary(16)                                             | NOT NULL                               | 所属文档快照模块    |
| parent_id            | binary(16)                                             | NULL                                   | 父快照节点ID     |
| title                | varchar(200)                                           | NOT NULL                                | 标题          |
| type                 | enum('case','normal','precondition','step','expected') | NOT NULL                                | 类型          |
| priority             | varchar(2)                                             | NULL                                    | 优先级编码（完整拷贝） |
| is_associated        | tinyint(1)                                             | NOT NULL, DEFAULT 0                     | 是否为关联节点     |
| is_deleted           | tinyint(1)                                             | NOT NULL, DEFAULT 0                     | 是否已删除       |
| last_mark            | enum('pass','fail')                                    | NULL                                    | 最后评审标记      |
| last_reviewer_id     | binary(16)                                             | NULL                                   | 最后评审人       |
| last_reviewed_at     | datetime                                               | NULL                                    | 最后评审时间      |
| sort_order           | int                                                    | NOT NULL, DEFAULT 0                     | 排序          |
| created_at           | datetime                                               | NOT NULL, DEFAULT CURRENT_TIMESTAMP     | 创建时间        |
| updated_at           | datetime                                               | NOT NULL, ON UPDATE CURRENT_TIMESTAMP   | 更新时间        |

#### 2.1.11 评审记录表（test_review_record）

| 字段               | 类型                     | 约束                                          | 说明     |
| ---------------- | ---------------------- | ------------------------------------------- | ------ |
| id               | binary(16)                     | PK                                      | 记录ID   |
| review_id        | binary(16)                     | NOT NULL                               | 评审ID   |
| snapshot_node_id | binary(16)                     | NOT NULL                               | 快照节点ID |
| reviewer_id      | binary(16)                     | NOT NULL                               | 评审人    |
| operation_type   | enum('mark','comment')         | NOT NULL                                    | 操作类型   |
| mark             | enum('pass','fail')            | NULL                                        | 标记     |
| comment          | text                           | NULL                                        | 评论内容   |
| created_at       | datetime                       | NOT NULL, DEFAULT CURRENT_TIMESTAMP         | 创建时间   |
| updated_at       | datetime                       | NOT NULL, ON UPDATE CURRENT_TIMESTAMP       | 更新时间   |
| is_deleted       | tinyint(1)                     | NOT NULL, DEFAULT 0                         | 是否删除   |

#### 2.1.12 缺陷表（bug）

| 字段                  | 类型                                        | 约束                                    | 说明                    |
| ------------------- | ----------------------------------------- | ------------------------------------- | --------------------- |
| id                  | binary(16)                                | PK                                     | 缺陷ID                  |
| project_id          | binary(16)                                | NOT NULL                               | 所属项目                  |
| title               | varchar(300)                              | NOT NULL                               | 标题                    |
| severity            | enum('fatal','serious','general','minor') | NOT NULL                               | 严重等级                  |
| priority            | enum('high','medium','low')               | NOT NULL                               | 优先级                   |
| status              | enum('active','resolved','rejected','closed') | NOT NULL, DEFAULT 'active'         | 状态（激活/已解决/已拒绝/已关闭）   |
| bug_type            | varchar(30)                               | NOT NULL, DEFAULT 'code_error'         | 缺陷类型，枚举见下文           |
| repro_steps         | text                                      | NULL                                   | 重现步骤（Markdown 原文）    |
| module_id           | binary(16)                                | NULL                                   | 所属模块，引用 project_module 统一模块树（见 2.1.1） |
| keywords            | varchar(255)                              | NULL                                   | 关键词                   |
| due_date            | date                                      | NULL                                   | 截止日期                  |
| confirmed           | tinyint(1)                                | NOT NULL, DEFAULT 0                    | 是否确认                  |
| reopen_count        | int                                       | NOT NULL, DEFAULT 0                    | 重开（激活）次数            |
| last_reopened_at    | datetime                                  | NULL                                   | 最近重开时间                |
| resolution          | varchar(30)                               | NULL                                   | 解决方案，枚举见下文           |
| duplicate_of_bug_id | binary(16)                                | NULL                                   | resolution=duplicate 时指向的原始缺陷 |
| resolved_by         | binary(16)                                | NULL                                   | 解决人                   |
| resolved_at         | datetime                                  | NULL                                   | 解决时间                  |
| rejected_by         | binary(16)                                | NULL                                   | 拒绝人（重开时处理人回设为此人）       |
| closed_by           | binary(16)                                | NULL                                   | 关闭人                   |
| closed_at           | datetime                                  | NULL                                   | 关闭时间                  |
| reporter_id         | binary(16)                                | NOT NULL                               | 发现人                   |
| assignee_id         | binary(16)                                | NULL                                   | 处理人                   |
| related_case_id     | binary(16)                                | NULL                                   | 关联原始用例ID             |
| related_plan_id     | binary(16)                                | NULL                                   | 关联计划ID               |
| created_at          | datetime                                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP    | 创建时间                  |
| updated_at          | datetime                                  | NOT NULL, ON UPDATE CURRENT_TIMESTAMP  | 更新时间                  |
| is_deleted          | tinyint(1)                                | NOT NULL, DEFAULT 0                    | 是否删除                  |

枚举取值（业界惯例的语义化 snake_case 命名）：

- **bug_type**：`code_error` 代码错误、`ui_improvement` 界面优化、`design_defect` 设计缺陷、`configuration` 配置相关、`installation` 安装部署、`security` 安全相关、`performance` 性能问题、`standard_spec` 标准规范、`other` 其他。
- **resolution**：`fixed` 已修复、`by_design` 设计如此、`duplicate` 重复缺陷、`external` 外部原因、`cannot_reproduce` 无法重现、`deferred` 延期处理、`wont_fix` 不予解决。

状态机（禅道式四态模型）：

```
                    ┌──────────┐
                    │  active  │
                    └────┬─────┘
                  ┌──────┴──────┐
                  ▼              ▼
            ┌──────────┐  ┌──────────┐
            │ resolved │  │ rejected │
            └─────┬────┘  └─────┬────┘
                  │              │
                  └──────┬──────┘
                         ▼
                   ┌──────────┐
                   │  closed  │
                   └────┬─────┘
                        │
                        ▼
                     active（重开）
```

- **解决** active → resolved：必填 resolution + 备注说明；resolution=duplicate 时必填 duplicate_of_bug_id（校验存在、非自身且同项目）；记录 resolved_by/resolved_at；**处理人回设为创建人**（由创建人验证）。
- **拒绝** active → rejected：必填说明；处理人回设为创建人；记录 rejected_by（拒绝人），重开时处理人回设给拒绝人。
- **关闭** resolved/rejected → closed：必填说明；记录 closed_by/closed_at。
- **重开（激活）** resolved/rejected/closed → active：必填说明；reopen_count+1、last_reopened_at=now，清空 resolution/duplicate_of_bug_id/resolved_by/resolved_at/rejected_by/closed_by/closed_at；**处理人流转**：若从 resolved 重开则回设给解决人，若从 rejected 重开则回设给拒绝人，无可追溯的修复/拒绝人时保持原处理人不变。
- **确认**：独立操作，仅 active 且未确认时可执行，confirmed=true，写 bug_log。

#### 2.1.13 缺陷日志表（bug_log）

| 字段             | 类型          | 约束                         | 说明   |
| -------------- | ----------- | -------------------------- | ---- |
| id             | binary(16)  | PK                                  | 日志ID |
| bug_id         | binary(16)  | NOT NULL                            | 缺陷ID |
| operator_id    | binary(16)  | NOT NULL                            | 操作人  |
| operation_type | varchar(50) | NOT NULL                            | 操作类型 |
| content        | text        | NULL                                | 变更内容 |
| created_at     | datetime    | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 创建时间 |
| updated_at     | datetime    | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| is_deleted     | tinyint(1)  | NOT NULL, DEFAULT 0                 | 是否删除 |

#### 2.1.14 缺陷附件表（bug_attachment）

| 字段           | 类型           | 约束                                    | 说明       |
| ------------ | ------------ | ------------------------------------- | -------- |
| id           | binary(16)   | PK                                     | 附件ID     |
| bug_id       | binary(16)   | NOT NULL                               | 所属缺陷     |
| file_name    | varchar(255) | NOT NULL                               | 原始文件名    |
| storage_path | varchar(500) | NOT NULL                               | 磁盘相对路径   |
| file_size    | bigint       | NOT NULL                               | 文件大小（字节） |
| content_type | varchar(100) | NULL                                   | MIME 类型  |
| uploader_id  | binary(16)   | NOT NULL                               | 上传人      |
| created_at   | datetime     | NOT NULL, DEFAULT CURRENT_TIMESTAMP    | 创建时间     |
| updated_at   | datetime     | NOT NULL, ON UPDATE CURRENT_TIMESTAMP  | 更新时间     |
| is_deleted   | tinyint(1)   | NOT NULL, DEFAULT 0                    | 是否删除     |

附件文件存储于服务端本地磁盘（目录由 `robotest.upload.dir` 配置，默认 `./uploads/bug`），按 `{bugId}/{uuid}.{ext}` 落盘，单文件上限 10MB。

---


### 2.2 通用约定

- 项目内接口基础路径：`/api/project`
- 认证：`Authorization: Bearer <token>`
- 业务请求头：`X-Active-Workspace` + `X-Active-Project`（项目域请求两者必带，见 2.2.1）
- 命名风格：camelCase
- 分页：`pageNo`、`pageSize` → `{ list: [], total: number }`
- 通用响应：`{ "code": 200, "msg": "success", "data": {} }`

### 2.2.1 项目域上下文边界

项目域的活动上下文由 `X-Active-Workspace` 和 `X-Active-Project` 表示；本域各接口按以下业务边界执行：

| 场景 | 上下文入口 | 允许出现在路径/请求体中的 ID | 校验要求 |
| --- | --- | --- | --- |
| 项目、模块、用例、评审、计划、缺陷等资源操作 | 两个活动上下文 Header | 被操作资源自身的 `moduleId`、`docId`、`reviewId`、`planId`、`bugId` 等 | 服务端以 Header 归属为权威，资源 ID 只能定位资源，不能改变上下文 |
| 项目列表筛选和分页 | 两个活动上下文 Header | 筛选条件中的资源 ID（如 `moduleId`） | 必须验证资源属于当前项目 |
| 项目工作台和统计 | 两个活动上下文 Header | 无 | 缺少或非法上下文时拒绝请求 |

工作空间切换和个人默认项目设置属于工作空间域，见 `../02-space-management/02-space-management-overview.md` 2.2.1；项目域接口不得自行在请求体中接收活动项目 ID。


### 2.3 评审/执行记录与状态更新

每次提交记录时，事务中插入记录表并更新快照节点的最后状态字段。评审中仅用例节点可标记，所有节点可评论。执行中仅关联用例节点可标记执行结果，默认根节点不可执行。


### 2.4 权限控制

所有接口校验用户是否属于当前项目所属的工作空间。评审操作仅发起人可完成/同步/删除，参与者均可提交记录。计划操作负责人可编辑、同步、完成/关闭、删除，执行人可提交执行记录。

前后端共用同一套权限码口径：项目工作台数据接口校验 `project:view`；用例、模块与用例文档接口校验 `case:view`（写操作为 `case:edit`）；缺陷接口校验 `bug:view`；需求池接口校验 `requirement:view`（编辑、归档为 `requirement:edit`）；接口测试各子模块按 `api-debug:view`、`api-interface:view`、`api-mock:view`、`api-scene:view`、`api-report:view`、`api-timer:view`、`api-env:view`、`api-func:view`、`api-component:view` 分别控制。前端顶部动态菜单与页面入口按同一权限码显隐（见 2.5）。

---


### 2.5 总体布局

从项目列表点击「进入项目」，或切换到设有默认项目的工作空间后，平台进入**项目模式**（路由 `meta.mode` 为 `project`，导航状态由路由注册表派生），并默认落在项目工作台。顶栏自左向右为：Logo（点击回项目工作台）→ 空间名 / 项目名胶囊 → 顶部动态菜单 → 我的空间、我的项目、空间管理、系统管理、消息中心 → 用户头像下拉（修改密码、退出登录）。

顶部动态菜单取 `mode: 'project'` 且带 `meta.menu` 的路由记录，按 `order` 升序排列并按权限码过滤，权限不满足的菜单项不展示：

| 顺序 | 菜单项 | 路由 | 图标 | 权限码 |
| --- | --- | --- | --- | --- |
| 10 | 功能测试 | `/workspace/projects/functional-testing` | Monitor | `case:view` |
| 20 | 缺陷管理 | `/workspace/projects/bugs` | Warning | `bug:view` |
| 30 | 接口测试 | `/workspace/projects/api-testing` | Connection | 任一 `api-*:view`（api-debug / api-interface / api-mock / api-scene / api-report / api-timer / api-env / api-func / api-component） |

项目工作台、评审/计划/缺陷详情、需求池等页面的路由不带 `meta.menu`，不进顶部菜单，由菜单项或页面跳转进入。功能测试与接口测试均为独立框架页，进入后子模块在框架内部切换，不再依赖顶部菜单。

#### 2.5.1 功能测试工作区

功能测试采用独立的框架页（FunctionalTestingPage）承载，路由 `/workspace/projects/functional-testing`。页面内部分为左右结构：左侧为模块侧栏白卡（el-menu，含测试用例、测试评审、测试计划、需求池四个入口），右侧为主内容卡，按选中项渲染 TestCasePage、ReviewListPage、PlanListPage、RequirementPoolPage。侧栏与内容卡等高并排，当前选中项为浅蓝底胶囊高亮；该路由下业务布局内容区 padding 归零，由页内两张白卡自行成形。

```
┌─────────────────────────────────────────────────────┐
│ ┌──────────────┐  ┌───────────────────────────────┐ │
│ │  测试用例      │  │                               │ │
│ │  测试评审      │  │         子模块内容区            │ │
│ │  测试计划      │  │    (按侧栏选中项渲染对应页面)     │ │
│ │  需求池        │  │                               │ │
│ └──────────────┘  └───────────────────────────────┘ │
│  ←模块侧栏白卡→      ←──────── 主内容卡 ────────→      │
└─────────────────────────────────────────────────────┘
```

子模块切换只更新 `?tab=cases|reviews|plans|requirements` 查询参数，不新增路由；刷新或外部链接经 `?tab=` 还原，默认进入测试用例。处于用例文档编辑态时切换子模块或离开页面需二次确认，取消则停留在原页面。

#### 2.5.2 接口测试

接口测试为独立框架页（ApiTestingPage），路由 `/workspace/projects/api-testing`，与功能测试同构：左侧模块侧栏白卡 + 右侧内容卡，子模块经 `?tab=` 还原，菜单项按权限码过滤。主菜单：快速调试、接口管理、Mock 服务、测试场景、测试报告、定时任务；「项目设置」分组：环境管理、函数管理、公共组件（依次对应 `api-debug:view`、`api-interface:view`、`api-mock:view`、`api-scene:view`、`api-report:view`、`api-timer:view`、`api-env:view`、`api-func:view`、`api-component:view`）。任一 `api-*:view` 都不具备时入口菜单隐藏，直接访问路由则内容区展示「暂无可用功能模块」空态提示。测试报告分享页 `/share/api-report/:id` 为公开路由，不经本框架页。

#### 2.5.3 缺陷管理

独立页面（BugListPage），包含看板和列表两种视图：视图切换按钮组、状态快捷筛选（全部、未修复的、由我创建、指派给我、由我修复、由我关闭）、关键字搜索与更多筛选（状态、类型、严重等级、优先级）弹层；看板按状态分列，列间拖拽按四态状态机流转状态，拖至「已解决」弹解决对话框，其余合法目标列弹说明输入，非法目标列置灰不可落。相关操作页为提交缺陷与缺陷详情（见 2.6）。

#### 2.5.4 模块树与项目上下文状态

- **模块树**：`ProjectModuleTree` 组件封装 el-tree，逻辑下沉到 `useProjectModuleTree`。`assetType=testcase` 为文档模式：目录可新建、重命名、删除与拖拽排序，用例文档为叶子节点，点击文档在右侧打开脑图，切换已打开文档需二次确认；顶部关键字过滤命中后自动展开祖先链。`assetType=interface|scene` 为筛选模式：只有目录节点，点击目录仅过滤父页列表。树数据来自 `GET /api/project/modules?assetType=`，状态（treeData、loading、currentDocId）由 composable 持有，组件通过 emit 通知页面选中项。
- **项目上下文状态**：活动项目 ID 与名称由 auth store 持有并持久化（`robotest_active_project`），切换或退出空间时先清空项目；请求层按 URL scope 注入 `X-Active-Workspace`、`X-Active-Project` 头，活动项目 ID 不出现在 URL 与请求体中（C4）。导航模式与顶部菜单由 nav store 从当前路由 `meta.mode`、`meta.menu` 派生，`dynamicMenuItems` 即当前模式下按权限过滤后的菜单项。


### 2.6 路由规划

项目域路由均挂在业务布局（BusinessLayout）下，`meta.mode` 为 `project`；带 `meta.menu` 的路由同时是顶部动态菜单注册项（权限码见 2.5）。

| 路由 | 页面 | 菜单/权限 | 说明 |
| --------------------------------------------- | ------------------ | ---------------- | ------------------------------------ |
| `/workspace/projects/dashboard` | 项目工作台（DashboardPage） | 不进菜单；数据接口 `project:view` | 进入项目默认落地页，展示统计、快捷入口与最近动态 |
| `/workspace/projects/functional-testing` | 功能测试框架页（FunctionalTestingPage） | 菜单「功能测试」，`case:view` | `?tab=cases\|reviews\|plans\|requirements` 切换子模块，默认测试用例 |
| `/workspace/projects/reviews` | 评审列表（ReviewListPage） | 不进菜单 | 独立直达路由，内容同功能测试「测试评审」子模块 |
| `/workspace/projects/reviews/:reviewId` | 评审详情（ReviewDetailPage） | 不进菜单 | 快照树 + 评审标记/评论 |
| `/workspace/projects/plans` | 计划列表（PlanListPage） | 不进菜单 | 独立直达路由，内容同功能测试「测试计划」子模块 |
| `/workspace/projects/plans/:planId` | 计划详情（PlanDetailPage） | 不进菜单 | 快照树 + 执行跟踪 |
| `/workspace/projects/requirements` | 需求池（RequirementPoolPage） | 不进菜单；接口 `requirement:view` | 独立直达路由，内容同功能测试「需求池」子模块 |
| `/workspace/projects/api-testing` | 接口测试框架页（ApiTestingPage） | 菜单「接口测试」，任一 `api-*:view` | `?tab=` 还原子模块 |
| `/workspace/projects/interfaces/:interfaceId` | 接口定义编辑（旧路由） | 不进菜单 | 重定向至 `/workspace/projects/api-testing?tab=interfaces`（`new` 转 `action=create`） |
| `/workspace/projects/bugs` | 缺陷管理（BugListPage） | 菜单「缺陷管理」，`bug:view` | 看板/列表视图 |
| `/workspace/projects/bugs/create` | 提交缺陷（BugCreatePage） | 不进菜单 | 禅道式表单，重现步骤 Markdown 编辑 |
| `/workspace/projects/bugs/:bugId` | 缺陷详情（BugDetailPage） | 不进菜单 | 状态流转、日志与附件 |

项目上下文入口为项目列表 `/workspace/projects`（workspace 模式，菜单权限 `project:view`）：「进入项目」时写入活动项目并跳转 `/workspace/projects/dashboard`；工作空间设有默认项目时切换空间也直接进入工作台。


## 3. 错误码补充

| 错误码 | 说明 |
| --- | --- |
| 1000011010 | 测试计划不存在 |
| 1000011011 | 评审不存在 |
| 1000011012 | 非发起人不能执行该操作 |
| 1000011013 | 计划关闭时存在未执行用例 |
| 1000011014 | 节点版本冲突，请刷新后重试 |
| 1000011015 | 只有用例节点可标记评审结果 |
| 1000011016 | 只有关联的用例节点可标记执行结果 |
| 1000011017 | 默认根节点不可执行 |
| 1000011020 | 仅未开始或进行中的计划可阻塞 |
| 1000011024 | 仅已阻塞的计划可恢复 |
| 1000011025 | 计划已阻塞，请先恢复后再操作 |
| 1000011026 | 评审已完成，无法执行该操作 |
| 1000011027 | 计划已结束，无法执行该操作 |

缺陷相关错误码（1000012xxx 系列）：

| 错误码        | 说明                  |
| ---------- | ------------------- |
| 1000012001 | 缺陷状态流转不合法           |
| 1000012003 | 重开缺陷时必须填写说明         |
| 1000012004 | 关闭缺陷时必须填写关闭说明       |
| 1000012005 | 处理人不在当前工作空间中        |
| 1000012011 | 解决缺陷时必须选择解决方案       |
| 1000012012 | 解决方案不合法             |
| 1000012013 | 解决方案为重复缺陷时必须指定原始缺陷 |
| 1000012014 | 指定的原始缺陷不存在或不合法      |
| 1000012015 | 所属模块不存在或不属于当前项目     |
| 1000012016 | 缺陷已确认，无需重复确认        |
| 1000012017 | 仅激活状态的缺陷可确认         |
| 1000012018 | 缺陷类型不合法             |
| 1000012019 | 缺陷已关闭，不可编辑         |
| 1000012020 | 解决缺陷时必须填写备注说明       |
| 1000012021 | 拒绝缺陷时必须填写说明         |
| 1000012022 | 关联用例或计划标识不合法        |

---

**文档结束**

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `02-project-workspace-overview.md` | 1. 引言、2. 数据设计（2.1 数据库表设计、2.2 通用约定、2.2.1 项目域上下文边界、2.3 评审/执行记录与状态更新、2.4 权限控制、2.5 总体布局（2.5.1 功能测试工作区、2.5.2 接口测试、2.5.3 缺陷管理、2.5.4 模块树与项目上下文状态）、2.6 路由规划）、3. 错误码补充 |
| 项目工作台 | `03-project-workspace-workbench.md` | 1. 项目工作台接口（1.1 获取项目工作台数据、1.2 项目动态数据、1.3 项目工作台页面）、2. 约束与实施说明 |
| 测试用例管理 | `04-project-workspace-test-case.md` | 1. 测试用例管理接口、2. 文档创建与默认根节点、3. 脑图实时协作（3.1 用例管理页） |
| 测试评审管理 | `05-project-workspace-test-review.md` | 1. 测试评审管理接口、2. 快照生成与裁剪（2.1 评审列表页、2.2 评审详情页） |
| 测试计划管理 | `06-project-workspace-test-plan.md` | 1. 测试计划管理接口、2. 同步最新用例（2.1 计划列表页、2.2 计划详情页） |
| 用例资产管理 | `07-document-management.md` | 1. 引言、2. 数据设计、3. 接口详细设计、4. 业务逻辑设计（4.3 页面集成与状态分支）、5. 实施说明 |
| 脑图组件 | `08-mindmap-component.md` | 1. 概述、2. 技术选型、3. 组件架构、4. 数据模型与模式映射、5. 工具栏设计、6. 右键菜单设计、7. 评审状态与执行状态展示、8. 关联 Bug 标签与跳转、9. 评论功能、10. 初始化加载流程（10.4 状态分支）、11. 实时协作设计、12. 与后端交互总结、13. Vue 组件代码骨架、14. 实施要点 |
| 缺陷管理 | `../04-bug-management/02-project-workspace-bug.md` | 1. 缺陷管理接口（1.13 缺陷管理页、1.14 提交缺陷页、1.15 缺陷详情页） |

---

## 修改记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| V1.0 | 2026-10-02 | 对齐前端实现：模块划分与总体布局、项目态路由与顶部菜单权限码、模块树与项目上下文状态、数据表口径及分册-章节对照表 |
| V1.0 | 2026-10-03 | 分册-章节对照表与交叉引用一致性复检 |
