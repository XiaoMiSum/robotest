# 软件测试平台——文档管理详细设计说明书

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档定义用例管理（`test_case_document`）的详细设计。用例为脑图型用例文档，作为独立资产挂载在项目模块树（`project_module`）节点下，不存储在模块表中。本文档涵盖用例表结构、布局与节点存储、用例管理接口（创建、查询、更新、删除）、业务规则，以及用例页面的集成方式与状态分支。

> 项目模块树（`project_module`）本身的详细设计见 `docs/04-detailed-design/02-project-module.md`；用例管理页与脑图编辑器的交互明细见 `docs/04-detailed-design/03-function-testing/04-project-workspace-test-case.md`。

### 1.2 与基线的关系

本文档为当前版本新增文档，替代以下已有设计：

| 原设计 | 位置 | 替代方式 |
| --- | --- | --- |
| `test_case_module`（功能测试模块中的文档节点） | `docs/04-detailed-design/02-project-module.md` 4.3（与现有功能测试模块的兼容） | type=document 的行迁移至 `test_case_document` 表；type=directory 的行迁移至 `project_module`，见 `docs/04-detailed-design/02-project-module.md` |

### 1.3 定义与缩写

| 术语 | 定义 |
| --- | --- |
| 用例（Test Case） | 挂载在模块下的脑图型用例文档，节点存储在 `test_case_node`，布局存储在 `test_case_document.layout` |
| 未分组 | 模块 ID 为 NULL 时的默认归类，适用于不属于任何模块的资产 |

> 模块（Module）定义见 `docs/04-detailed-design/02-project-module.md` 1.3；未分组文档在模块树中的展示口径见 4.2。

---

## 2. 数据设计

数据库字段使用 snake_case，接口 JSON 使用 camelCase。所有表遵循 C5（UUID 主键、`created_at`/`updated_at`/`is_deleted`，禁止物理外键）。

### 2.1 用例表（test_case_document）

用例为脑图型用例文档，挂载在模块树节点下。用例**不存储**在模块表中，而是独立建表通过 `module_id` 关联（`module_id` 引用 `project_module.id`，见 `docs/04-detailed-design/02-project-module.md` 2.1）。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | UUID | PK | 主键 |
| project_id | UUID | NOT NULL | 归属项目 |
| module_id | UUID | NULL | 归属模块（project_module.id，null 为未分组，文档在模块树中挂根层级） |
| name | VARCHAR(100) | NOT NULL | 用例名称 |
| layout | JSONB | NULL | 脑图布局（模板名 + 节点偏移），格式见下文 |
| sort_order | INT | NOT NULL DEFAULT 0 | 同层级排序序号 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 是否删除 |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_tcd_project` (project_id), `idx_tcd_module` (module_id)

> 合计 2 个索引，符合 C9。

**layout JSON 格式**：

```json
{
  "template": "right",
  "offsets": {
    "node-uuid-1": { "layout_right_offset": { "x": 120, "y": 50 } },
    "node-uuid-2": { "layout_left_offset": { "x": 300, "y": -80 } }
  }
}
```

> `template` 为脑图排版模板名，取值 `default`（思维导图）、`right`（右侧分布）、`structure`（组织结构）、`filetree`（目录）、`fish-bone`（鱼骨图）、`tianpan`（天盘）；服务端新建文档初始为 `right`，前端加载时模板为空则回退 `default`。
> `offsets` 以节点 id 为键，内层键为该节点 data 中匹配 `layout_<模板名>_offset` 的原始键名，值为节点相对自动排版的自由摆放偏移；`offsets` 为空对象 `{}` 表示全部自动排版。
> 布局由 WebSocket 文本帧 `update_layout` 整体 upsert 落库（见 `docs/04-detailed-design/03-function-testing/04-project-workspace-test-case.md` 3），节点加载时回填到节点 data；节点与布局的读取经 `GET /api/project/documents/{docId}/nodes`（同上 1.4），读写帧协议与防抖策略见 `docs/04-detailed-design/03-function-testing/08-mindmap-component.md`。

**用例节点存储**：

| 表 | 说明 |
| --- | --- |
| `test_case_node` | 脑图节点树，`document_id` → `test_case_document.id`，`parent_id`（NULL 为根节点）、`type`、`title`、`priority`、`sort_order`、`version`（乐观锁） |

> 节点表索引：`idx_test_case_node_document_id` (document_id)、`idx_test_case_node_parent_id` (parent_id)、`idx_test_case_node_document_type` (document_id, type) 部分索引。
> 创建用例时自动创建根节点（title = name，type = normal，sort_order = 0，version = 1），删除用例级联删除其节点。`test_case_document_layout` 表已废弃，布局数据合并至本表 `layout` 列。

---

## 3. 接口详细设计

所有接口通过 `X-Active-Project` 请求头传递项目上下文（C4），不出现在 URL 或请求体中；成功响应统一为 `{ "code": 200, "msg": "success", "data": ... }` 结构，下文示例仅展示 `data` 内容。写接口（创建、更新、删除）均校验 `case:edit` 权限，读接口校验 `case:view` 权限。

### 3.1 用例管理

#### 3.1.1 创建用例

```
POST /api/project/testcases
```

**请求体**：

```json
{
  "moduleId": "uuid-module",
  "name": "登录流程用例"
}
```

- `moduleId`：可空，`null` 表示挂根层级（未分组）；非空时必须存在且属于当前项目
- `name`：必填，1–100 字符

**响应** `201 Created`：

```json
{
  "id": "uuid-tc",
  "projectId": "uuid-project",
  "moduleId": "uuid-module",
  "name": "登录流程用例",
  "sortOrder": 0,
  "layout": { "template": "right", "offsets": {} },
  "createdAt": "2026-08-17T10:30:00Z"
}
```

> 响应字段：id / projectId / moduleId / name / sortOrder / layout / createdAt。

**事务行为**：
1. 校验 `moduleId` 指向同项目的已有模块，否则抛 `1000017051`（`PROJECT_MODULE_NOT_FOUND`）
2. 同模块下用例名称唯一（错误码 `1000017061`，`TEST_CASE_DOCUMENT_NAME_EXISTS`）
3. 创建 `test_case_document` 记录（`sort_order` = 0）
4. 自动创建根 `test_case_node`（title = name，type = normal，sort_order = 0，version = 1）
5. `layout` 初始值为 `{ "template": "right", "offsets": {} }`
6. 记录项目动态（`CASE_CREATED`）

#### 3.1.2 查询用例列表

```
GET /api/project/testcases
```

**Query 参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| moduleId | UUID | 否 | 按模块筛选（不传则返回当前项目全部用例文档） |

> 本接口不分页，返回当前项目（或指定模块）下的全部用例文档元数据。

**响应** `200 OK`：

```json
[
  {
    "id": "uuid-tc",
    "projectId": "uuid-project",
    "moduleId": null,
    "name": "未分组用例",
    "sortOrder": 0,
    "layout": { "template": "right", "offsets": {} },
    "createdAt": "2026-08-17T10:30:00Z"
  }
]
```

> 前端用例页不直接调用本接口：左侧模块树与文档节点由 `GET /api/project/modules?assetType=testcase` 合并返回（文档为 `type=document` 节点），见 `docs/04-detailed-design/03-function-testing/04-project-workspace-test-case.md` 1.1。本接口供按模块或全量拉取用例文档元数据使用。

#### 3.1.3 更新用例

```
PUT /api/project/testcases/{id}
```

**请求体**（字段可选，仅更新传入字段，可组合提交）：

```json
{
  "name": "新用例名",
  "moduleId": "uuid-other-module",
  "targetIndex": 1,
  "layout": { "template": "right", "offsets": {} }
}
```

- `name`：1–100 字符，同模块下唯一（排除自身），重复抛 `1000017061`
- `targetIndex`：非空（≥ 0）才视为移动操作；`moduleId` 为目标模块，`null` 表示根层级；超出目标层级范围按边界收敛；目标模块不存在或不属于当前项目抛 `1000017051`；移动后目标层级 `sort_order` 自 0 连续重写
- `layout`：非空时整体覆盖文档布局 JSON，不与既有值做字段级合并

**响应** `200 OK`：更新后的文档信息（字段同 3.1.1）。

> 仅传 `moduleId` 而不传 `targetIndex` 不触发移动；文档不存在或不属于当前项目抛 `1000011021`（`TEST_CASE_DOCUMENT_NOT_FOUND`）。节点内容与布局的高频更新不经本接口，走 WebSocket 文本帧。

#### 3.1.4 删除用例

```
DELETE /api/project/testcases/{id}
```

**响应** `200 OK`（无数据）。

**事务行为**：
1. 校验文档存在且属于当前项目，否则抛 `1000011021`
2. 级联删除 `test_case_node`（WHERE document_id = ?）
3. 删除 `test_case_document` 记录（layout 数据随之删除）
4. 记录项目动态（`CASE_DELETED`）

> 删除用例为级联删除；被删除的是当前打开的文档时，前端清空编辑区。

---

## 4. 业务逻辑设计

### 4.1 错误码

| 错误码 | 常量名 | 含义 | 场景 |
| --- | --- | --- | --- |
| 1000011021 | TEST_CASE_DOCUMENT_NOT_FOUND | 文档不存在 | 更新/删除的文档不存在或不属于当前项目 |
| 1000017051 | PROJECT_MODULE_NOT_FOUND | 模块不存在 | 创建/移动用例时 `moduleId` 不存在或不属于当前项目 |
| 1000017061 | TEST_CASE_DOCUMENT_NAME_EXISTS | 用例名称重复 | 同模块下已存在同名用例 |

> 文档管理使用已登记的 `1000017061`；文档不存在复用功能测试域错误码，功能测试域错误码清单见 `docs/04-detailed-design/03-function-testing/02-project-workspace-overview.md` 3；模块管理错误码及号段分配见 `docs/04-detailed-design/02-project-module.md` 6.3。

### 4.2 业务规则

| 编号 | 规则 | 说明 |
| --- | --- | --- |
| BR-M04 | 创建用例自动创建根节点 | 根节点 title = 用例名、type = normal、sort_order = 0、version = 1，是脑图唯一顶层节点 |
| BR-TD01 | 未分组文档挂根层级 | `module_id` 为 NULL 的文档在模块树中 `parentId` 为 NULL，直接显示在根层级，无独立「未分组」节点 |
| BR-TD02 | 移动按目标下标重排 | 仅 `targetIndex` 非空触发移动，移动后目标层级 `sort_order` 自 0 连续重写（与模块树同口径） |
| BR-TD03 | 删除文档级联节点 | 删除文档时级联删除其全部 `test_case_node`，布局数据随行删除 |
| BR-TD04 | 布局整体覆盖 | `layout` 经 PUT 请求或 WebSocket `update_layout` 帧整体 upsert，不与既有值合并 |

> 模块相关业务规则（BR-M01~M03、M05、M06）见 `docs/04-detailed-design/02-project-module.md` 4.2。

### 4.3 页面集成与状态分支

用例页（`TestCasePage`）挂载在功能测试框架页（路由 `/workspace/projects/functional-testing`，菜单权限 `case:view`）下，子模块经 `?tab=cases` 切换，页面为「左模块树卡 + 右脑图编辑卡」结构。

| 项 | 实现口径 |
| --- | --- |
| 模块树数据 | `GET /api/project/modules?assetType=testcase`，目录与文档混合返回，文档为 `type=document` 叶子节点，未分组文档位于根层级 |
| 树加载 | `v-loading` 遮罩；失败提示「加载模块树失败」；空态「暂无模块，点击[新建]创建」 |
| 树刷新 | 每 60 秒静默刷新（无遮罩、失败不提示），保证多人协同下左侧树最终一致 |
| 文档选中 | 已打开其他文档时二次确认「确定离开当前文档，切换到其他文档吗？」，取消则回退树的高亮 |
| 未选文档 | 右侧 `el-empty`，提示「请在左侧模块树中选择一个文档」 |
| 离开与切换 | 离开页面（路由守卫）与切换功能测试子菜单均二次确认「确定离开当前文档吗？」，取消则停留 |
| 外部直达 | `?documentId=` 定位文档并随即清除参数，文档不存在或加载失败停留在空态 |
| 脑图加载 | 加载中 `v-loading`，失败提示「加载脑图失败」 |
| 协同断线 | 断线时顶部横幅「连接已断开，正在重连...」，重连成功后触发一次未提交变更的持久化冲刷 |
| 保存失败 | 服务端错误帧提示「文档保存失败」，权限被拒提示「无文档编辑权限」 |
| 权限分支 | 读操作 `case:view`；节点与布局写入 `case:edit`（WebSocket 每帧复查）；工具栏[关联需求]仅 `requirement:view` 可见；树上的新建/重命名/删除/拖拽入口不按 `case:edit` 前端隐藏，越权由服务端拒绝 |

> 模块树操作、脑图工具栏与右键菜单的完整交互表见 `docs/04-detailed-design/03-function-testing/04-project-workspace-test-case.md` 3.1；脑图组件拆分与协作设计见 `docs/04-detailed-design/03-function-testing/08-mindmap-component.md`；权限码口径见 `docs/04-detailed-design/03-function-testing/02-project-workspace-overview.md` 2.4。

---

## 5. 实施说明

### 5.1 数据迁移

`test_case_document` 的迁移为整体迁移（Phase 1–4）的一部分，迁移计划、执行顺序与事务要求见 `docs/04-detailed-design/02-project-module.md` 6.1。本文档仅列出用例相关的 DDL 与迁移步骤。

**Phase 1：创建新表**

```sql
-- 创建 test_case_document 表
CREATE TABLE test_case_document (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    module_id UUID NULL,
    name VARCHAR(100) NOT NULL,
    layout JSONB NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tcd_project ON test_case_document(project_id);
CREATE INDEX idx_tcd_module ON test_case_document(module_id);
```

**Phase 2：数据迁移**

```sql
-- 2. test_case_module (type=document) → test_case_document
INSERT INTO test_case_document (id, project_id, module_id, name, sort_order, is_deleted, created_at, updated_at)
SELECT id, project_id, parent_id, name, sort_order, is_deleted, created_at, updated_at
FROM test_case_module WHERE type = 'document' AND is_deleted = 0;

-- 2.1 回填 layout（从旧布局表迁移，如存在）
UPDATE test_case_document d
SET layout = l.layout
FROM test_case_document_layout l
WHERE l.document_id = d.id;
```

> **Phase 2 迁移说明**：步骤 2 必须在 `project_module` 迁移（`docs/04-detailed-design/02-project-module.md` 6.1 Phase 2 步骤 1）之后执行，因为 `test_case_document.module_id` 需指向迁移后的 `project_module.id`（目录节点迁移时保留原 ID，故此处可直接使用原 `parent_id`）。`test_case_node` 表沿用现有数据，迁移后需将 `document_id` 更新为新的 `test_case_document.id`。

**Phase 4（对应）：废弃旧表**

```sql
-- 确认布局数据已回填后，删除旧布局表
DROP TABLE test_case_document_layout;
```

> 废弃 `test_case_document_layout` 前需确认布局数据已回填至 `test_case_document.layout`（见 2.1）。`test_case_module` 的废弃策略见 `docs/04-detailed-design/02-project-module.md` 6.1。

### 5.2 迁移注意事项

1. **布局回填**：旧 `test_case_document_layout` 表若存在历史数据，必须先回填（Phase 2 步骤 2.1）再废弃
2. **软删除**：`is_deleted` 字段确保迁移期间的并发安全
3. **回滚方案**：保留旧表数据至确认新表稳定后再清理
4. **索引**：新表索引命名遵循 `idx_{table}_{field}` 规范（C9）

> 通用迁移注意事项（ID 映射、执行顺序、事务一致性）见 `docs/04-detailed-design/02-project-module.md` 6.2。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 对齐前后端实现：修正 layout（JSONB 与 offsets 结构）、用例接口路径/字段/分页口径与错误码，补挂载与未分组口径、页面集成与状态分支，修复失效交叉引用 |
