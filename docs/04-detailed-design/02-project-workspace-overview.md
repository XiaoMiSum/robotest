# 软件测试平台——项目工作区详细设计说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：已发布

---

## 1. 引言

### 1.1 编写目的

本文档对软件测试平台中的**功能测试模块**进行详细设计，定义数据结构、接口规范、业务逻辑、前端组件及交互流程，为开发实现提供完整依据。

### 1.2 范围

功能测试模块面向**业务用户**，在已选定的项目内提供测试用例管理、测试评审、测试计划及缺陷管理功能。所有操作限定在当前活跃工作空间和项目上下文中，项目上下文通过请求头 `X-Active-Project` 传递。

### 1.3 参考资料

- 《软件测试平台需求规格说明书》
- 《软件测试平台概要设计说明书》
- 《项目工作区页面交互设计》
- 《脑图组件实现设计》

---


## 2. 数据设计

### 2.1 数据库表设计

数据库字段使用 snake_case，接口 JSON 使用 camelCase。

#### 2.1.1 测试用例模块表（test_case_module）

| 字段         | 类型                           | 约束                                    | 说明     |
| ---------- | ---------------------------- | ------------------------------------- | ------ |
| id         | binary(16)                  | PK                                     | 模块节点ID |
| project_id | binary(16)                  | NOT NULL                               | 所属项目   |
| parent_id  | binary(16)                  | NULL                                   | 父节点ID  |
| type       | enum('directory','document') | NOT NULL                              | 节点类型   |
| name       | varchar(100)                 | NOT NULL                              | 名称     |
| sort_order | int                          | NOT NULL, DEFAULT 0                   | 排序号    |
| created_at | datetime                     | NOT NULL, DEFAULT CURRENT_TIMESTAMP   | 创建时间   |
| updated_at | datetime                     | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间   |
| is_deleted | tinyint(1)                   | NOT NULL, DEFAULT 0                   | 是否删除   |

**索引**：`idx_project_id` (project_id), `idx_parent_id` (parent_id)

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

#### 2.1.3 文档布局表（test_case_document_layout）

| 字段          | 类型     | 约束                           | 说明   |
| ----------- | ------ | ---------------------------- | ---- |
| id          | binary(16)  | PK                                  | 主键ID   |
| document_id | binary(16)  | NOT NULL                            | 文档ID   |
| layout_json | json        | NOT NULL                            | 布局信息 |
| created_at  | datetime    | NOT NULL, DEFAULT CURRENT_TIMESTAMP  | 创建时间 |
| updated_at  | datetime    | NOT NULL, ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| is_deleted  | tinyint(1)  | NOT NULL, DEFAULT 0                 | 是否删除 |

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
| module_id           | binary(16)                                | NULL                                   | 所属模块，关联 test_case_module 树 |
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
- 业务请求头：必须带 `X-Active-Project`
- 命名风格：camelCase
- 分页：`pageNo`、`pageSize` → `{ list: [], total: number }`
- 通用响应：`{ "code": 200, "msg": "success", "data": {} }`


### 2.3 评审/执行记录与状态更新

每次提交记录时，事务中插入记录表并更新快照节点的最后状态字段。评审中仅用例节点可标记，所有节点可评论。执行中仅关联用例节点可标记执行结果，默认根节点不可执行。


### 2.4 权限控制

所有接口校验用户是否属于当前项目所属的工作空间。评审操作仅发起人可完成/同步/删除，参与者均可提交记录。计划操作负责人可编辑、同步、完成/关闭、删除，执行人可提交执行记录。

---


### 2.5 总体布局

进入项目后，平台处于“项目模式”。顶部动态菜单显示：**功能测试**、**缺陷管理**（接口测试暂未开放）。功能测试为独立框架页（FunctionalTestingPage），进入后呈现深色侧边栏导航布局，子模块（测试用例、测试评审、测试计划）在框架内部切换，不再依赖顶部菜单。默认进入项目工作台。

#### 2.5.1 功能测试工作区

功能测试采用独立的框架页（FunctionalTestingPage）承载。页面内部分为左右结构：左侧为深色渐变侧边栏（包含测试用例、测试评审、测试计划三个入口），右侧为对应子模块内容区。侧边栏使用深色渐变背景，当前选中项带有蓝色高亮指示条。

```
┌──────────────────────────────────────────────────┐
│ ┌──────────┐  ┌────────────────────────────────┐ │
│ │  测试用例 │  │                                │ │
│ │  测试评审 │  │        子模块内容区              │ │
│ │  测试计划 │  │     (根据侧边栏切换)             │ │
│ │          │  │                                │ │
│ └──────────┘  └────────────────────────────────┘ │
│  ←深色侧边栏→    ←──────── 内容区 ────────→       │
└──────────────────────────────────────────────────┘
```

#### 2.5.2 接口测试

暂未开放，显示占位提示。

#### 2.5.3 缺陷管理

独立功能面板，包含看板和列表两种视图。


### 2.6 路由规划

| 路由                                      | 页面    | 说明              |
| --------------------------------------- | ----- | --------------- |
| `/workspace/projects/dashboard`         | 项目工作台 | 默认进入页面，展示项目统计概览 |
| `/workspace/projects/cases`             | 用例管理  | 模块树 + 脑图编辑器     |
| `/workspace/projects/plans`             | 计划列表  | 测试计划列表          |
| `/workspace/projects/plans/:planId`     | 计划详情  | 快照树 + 执行跟踪      |
| `/workspace/projects/reviews`           | 评审列表  | 测试评审列表          |
| `/workspace/projects/reviews/:reviewId` | 评审详情  | 快照树 + 评审标记/评论   |
| `/workspace/projects/api-test`          | 接口测试  | 占位提示页面          |
| `/workspace/projects/bugs`              | 缺陷管理  | 看板/列表视图         |


## 3. 错误码补充

| 错误码  | 说明               |
| ---- | ---------------- |
| 1010 | 测试计划不存在          |
| 1011 | 评审不存在            |
| 1012 | 非发起人不能执行该操作       |
| 1013 | 计划关闭时存在未执行用例     |
| 1014 | 节点版本冲突，请刷新后重试    |
| 1015 | 只有用例节点可标记评审结果    |
| 1016 | 只有关联的用例节点可标记执行结果 |
| 1017 | 默认根节点不可执行        |
| 1026 | 评审已完成，无法执行该操作    |
| 1027 | 计划已结束，无法执行该操作    |

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
| 总览 | `02-project-workspace-overview.md` | 前言、1. 引言、2. 数据设计、2.2 通用约定、2.3 评审/执行记录与状态更新、2.4 权限控制、2.5 总体布局、2.6 路由规划、6. 错误码补充 |
| 项目工作台 | `03-project-workspace-workbench.md` | 3.2 项目工作台接口、5.3.1 项目工作台 |
| 测试用例管理 | `04-project-workspace-test-case.md` | 3.3 测试用例管理接口、4.1 文档创建与默认根节点、4.2 脑图实时协作、5.3.2 用例管理页 |
| 测试评审管理 | `05-project-workspace-test-review.md` | 3.4 测试评审管理接口、4.3 快照生成与裁剪、5.3.3 评审列表页、5.3.4 评审详情页 |
| 测试计划管理 | `06-project-workspace-test-plan.md` | 3.5 测试计划管理接口、4.5 同步最新用例、5.3.5 计划列表页、5.3.6 计划详情页 |
| 缺陷管理 | `07-project-workspace-bug.md` | 3.6 缺陷管理接口、5.3.7 缺陷管理页 |
