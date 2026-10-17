# 软件测试平台——需求管理

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**需求管理模块**进行详细设计，定义需求域数据结构、接口规范、业务逻辑与前端组件，为开发实现提供完整依据。导入拆解建议由 AI 能力经 AI Task 任务资源供给，本文档只定义需求域自身的行为与接口。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/06-requirement-management/02-srs-requirement-management.md`（US-RM-001 ~ 004）。
- 对应概要：`docs/02-high-level-design/06-requirement-management/02-hld-requirement-management.md`。
- 关联设计：
  - AI 任务与提案确认接口：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（导入任务的提交与采纳走该资源）。

### 1.3 参考资料

- `docs/00-spec/20-contracts/01-api.md`、`docs/00-spec/20-contracts/02-database.md`（C5/C9）、`docs/00-spec/10-engineering/02-backend.md`（C11 部分更新）
- `docs/02-high-level-design/04-hld-data-interface.md`（上下文头与数据隔离口径）
- `docs/04-detailed-design/01-readme.md`（通用响应与分页约定）

---

## 2. 数据设计

### 2.1 公共约定

- 域前缀：`requirement_`（根表 `requirement` 为域门面，子表以 `requirement_` 为前缀）。
- 公共字段（C5，每表必有，下文不再重复）：`id uuid PRIMARY KEY`（框架默认策略）、`created_at timestamp NOT NULL`、`updated_at timestamp NOT NULL`、`is_deleted boolean NOT NULL DEFAULT FALSE`；时间列 UTC 语义。
- 无物理外键（C5）：`project_id / module_id / owner_id` 等均为逻辑外键，引用完整性由 Service 层保证。
- 需求条目**不提供删除**，`is_deleted` 仅作数据兜底，业务上归档为终态入口。

### 2.2 需求条目表（requirement）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| project_id | uuid | NOT NULL | 所属项目，**隔离边界**，查询强制过滤 |
| module_id | uuid | NULL | 归属模块（逻辑外键 → 项目统一模块树节点），拆解按模块归属、列表按模块筛选 |
| system_version | varchar(50) | NULL | 被测业务系统版本（项目即被测业务系统，如 `V2.3`）；按属性变更处理，不触发状态流转 |
| code | varchar(20) | NOT NULL | 需求编号 `REQ-001`，项目内唯一（分配规则见 4.1） |
| title | varchar(300) | NOT NULL | 需求标题 |
| description | text | NULL | 需求描述正文（Markdown） |
| status | varchar(20) | NOT NULL, DEFAULT 'draft' | 状态机：`draft / confirmed / changed / archived`（见 4.2） |
| priority | varchar(10) | NULL | 优先级：`high / medium / low` |
| owner_id | uuid | NULL | 负责人（逻辑外键） |
| tags | jsonb | NULL | 标签集合，筛选与 AI 检索用 |
| source | varchar(20) | NOT NULL, DEFAULT 'manual' | 来源：`manual / import` |
| source_file_id | uuid | NULL | 导入来源附件 ID（复用既有附件资源），详情回看原文 |
| confirmed_at | timestamp | NULL | 最近一次进入已确认状态的时间 |

**索引**（4 个，≤ 5，C9）：

- `uk_requirement_project_code` UNIQUE (project_id, code) WHERE is_deleted = FALSE；
- `idx_requirement_project_status` (project_id, status) —— 列表主查询（隔离 + 状态筛选）；
- `idx_requirement_module` (module_id) —— 模块筛选与拆解归属查询；
- `idx_requirement_system_version` (project_id, system_version) —— 版本筛选。

```sql
CREATE TABLE requirement (
    id             uuid PRIMARY KEY,
    project_id     uuid NOT NULL,
    module_id      uuid NULL,
    system_version varchar(50) NULL,
    code           varchar(20) NOT NULL,
    title          varchar(300) NOT NULL,
    description    text NULL,
    status         varchar(20) NOT NULL DEFAULT 'draft',
    priority       varchar(10) NULL,
    owner_id       uuid NULL,
    tags           jsonb NULL,
    source         varchar(20) NOT NULL DEFAULT 'manual',
    source_file_id uuid NULL,
    confirmed_at   timestamp NULL,
    created_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted     boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_requirement_project_code ON requirement (project_id, code) WHERE is_deleted = FALSE;
CREATE INDEX idx_requirement_project_status ON requirement (project_id, status);
CREATE INDEX idx_requirement_module ON requirement (module_id);
CREATE INDEX idx_requirement_system_version ON requirement (project_id, system_version);
```

### 2.3 需求变更记录表（requirement_change_log）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| requirement_id | uuid | NOT NULL | 所属需求（逻辑外键），时间线主查询维度 |
| operator_id | uuid | NULL | 操作人（系统触发时为发起人） |
| change_type | varchar(30) | NOT NULL | `title / description / module / status / attribute`——只有前三类触发状态转 `changed`，本字段支撑"哪类变更触发了什么"的回查 |
| before_summary | jsonb | NULL | 变更前字段级摘要（原值） |
| after_summary | jsonb | NULL | 变更后字段级摘要（新值） |

**索引**（1 个）：

- `idx_requirement_change_log_req` (requirement_id, created_at) —— 变更记录时间线倒序拉取。

```sql
CREATE TABLE requirement_change_log (
    id             uuid PRIMARY KEY,
    requirement_id uuid NOT NULL,
    operator_id    uuid NULL,
    change_type    varchar(30) NOT NULL,
    before_summary jsonb NULL,
    after_summary  jsonb NULL,
    created_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted     boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_requirement_change_log_req ON requirement_change_log (requirement_id, created_at);
```

### 2.4 拆解记录表（requirement_split_record）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| project_id | uuid | NOT NULL | 项目隔离边界 |
| source_type | varchar(20) | NOT NULL | 拆解来源：`document`（导入文档） |
| source_file_id | uuid | NULL | 导入的原始文档附件 |
| ai_task_id | uuid | NOT NULL | 关联 AI 任务（逻辑外键 → ai_task）；**拆分建议明细存任务提案侧，本表不复制**（单一事实源） |
| status | varchar(20) | NOT NULL, DEFAULT 'pending' | `pending`（建议待采纳，含部分未处理）/ `adopted`（全部处理完且至少采纳一条）/ `rejected`（全部驳回） |
| adopt_result | jsonb | NULL | 采纳结果：逐条提案动作（adopted / adopted_edited / rejected）与生成的新需求 ID 列表、操作人 |

**索引**（2 个）：

- `idx_requirement_split_project` (project_id, created_at) —— 拆解记录列表；
- `idx_requirement_split_task` (ai_task_id) —— 任务下钻。

```sql
CREATE TABLE requirement_split_record (
    id                    uuid PRIMARY KEY,
    project_id            uuid NOT NULL,
    source_type           varchar(20) NOT NULL,
    source_file_id        uuid NULL,
    ai_task_id            uuid NOT NULL,
    status                varchar(20) NOT NULL DEFAULT 'pending',
    adopt_result          jsonb NULL,
    created_at            timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted            boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_requirement_split_project ON requirement_split_record (project_id, created_at);
CREATE INDEX idx_requirement_split_task ON requirement_split_record (ai_task_id);
```

---

## 3. 接口详细设计

### 3.1 通用约定

- 基础路径：`/api/project/requirements`；请求需 `Authorization` + `X-Active-Workspace` + `X-Active-Project` 上下文头（C4，上下文不出 URL），服务端校验项目归属与需求权限。
- 响应使用平台通用 `Result<T>`，下文示例**仅展示 `data` 字段**；分页 `pageNo`（从 1）/ `pageSize`（默认 20，最大 100）→ `{ list, total }`；字段 camelCase。
- 更新一律**部分更新（C11）**：请求体只包含发生变化的字段，服务端仅更新传入字段，未传字段保持原值。

**权限码与方法映射**（GET 为并集；权限点登记见 6.2）：

| 路径 | 方法 | 所需权限 |
| ---- | ---- | ---- |
| /api/project/requirements | GET | requirement:view |
| /api/project/requirements | POST | requirement:create |
| /api/project/requirements/{id} | GET | requirement:view |
| /api/project/requirements/{id} | PUT | requirement:edit |
| /api/project/requirements/{id}/confirm | POST | requirement:confirm |
| /api/project/requirements/{id}/archive | POST | requirement:confirm |
| /api/project/requirements/{id}/unarchive | POST | requirement:edit |
| /api/project/requirements/import | POST | requirement:create |
| /api/project/requirements/{id}/change-logs | GET | requirement:view |
| /api/project/requirements/split-records | GET | requirement:view |

### 3.2 需求列表

- **路径**：`GET /api/project/requirements`
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| status | string | 否 | 状态筛选，逗号分隔多选 |
| moduleIds | uuid | 否 | 所属模块筛选，逗号分隔 |
| ownerId | uuid | 否 | 负责人筛选 |
| systemVersion | string | 否 | 版本筛选（精确匹配） |
| keyword | string | 否 | 关键词：编号前缀/后缀或标题包含匹配（编号命中规则见 4.3） |
| pageNo / pageSize | number | 否 | 分页 |

- **响应**：

```json
{
  "list": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "REQ-001",
      "title": "登录验证码",
      "moduleId": "…",
      "moduleName": "登录模块",
      "systemVersion": "V2.3",
      "status": "confirmed",
      "priority": "high",
      "ownerId": "…",
      "ownerName": "张三",
      "source": "import",
      "updatedAt": "2026-10-02T08:30:00Z"
    }
  ],
  "total": 37
}
```

- **校验规则**：强制 `project_id = X-Active-Project`。

### 3.3 创建需求

- **路径**：`POST /api/project/requirements`
- **请求**：

```json
{
  "title": "登录验证码",
  "description": "## 背景\n用户登录需支持短信验证码…",
  "moduleId": "…",
  "systemVersion": "V2.3",
  "priority": "high",
  "ownerId": "…",
  "tags": ["登录", "安全"]
}
```

- **响应**：需求详情对象（同 3.5），`status = draft`、`code` 已分配（见 4.1）。
- **校验规则**：`title` 必填 ≤ 300 字符；`moduleId` 非空时须属当前项目（否则 1000018005）；`priority` ∈ `high/medium/low`；`systemVersion` ≤ 50 字符（超出返回 1000018010）；`ownerId` 须为当前工作空间成员。

### 3.4 需求详情

- **路径**：`GET /api/project/requirements/{id}`
- **响应**：

```json
{
  "id": "…",
  "code": "REQ-001",
  "title": "登录验证码",
  "description": "## 背景…",
  "moduleId": "…",
  "moduleName": "登录模块",
  "systemVersion": "V2.3",
  "status": "confirmed",
  "priority": "high",
  "ownerId": "…",
  "ownerName": "张三",
  "tags": ["登录", "安全"],
  "source": "import",
  "sourceFile": { "fileId": "…", "name": "PRD-v2.docx" },
  "confirmedAt": "2026-10-01T03:00:00Z",
  "createdAt": "2026-09-30T10:00:00Z",
  "updatedAt": "2026-10-02T08:30:00Z"
}
```

- **校验规则**：不存在或不属当前项目返回 404（1000018001），不泄露跨项目存在性。

### 3.5 更新需求（部分更新）

- **路径**：`PUT /api/project/requirements/{id}`
- **请求**（只传变化字段）：

```json
{
  "title": "登录验证码（短信+邮箱）",
  "description": "…"
}
```

- **响应**：更新后的需求详情对象；若触发状态流转，响应内 `status` 为流转后取值。
- **校验规则**：
  - `archived` 条目拒绝更新（1000018004）；
  - 传入 `title / description / moduleId` 且当前 `status = confirmed` → 状态自动转 `changed`，写变更记录（见 4.4）；
  - 仅传 `systemVersion / priority / ownerId / tags` 等属性 → 状态不变，只写 `change_type = attribute` 变更记录。

### 3.6 确认需求

- **路径**：`POST /api/project/requirements/{id}/confirm`
- **响应**：需求详情对象，`status = confirmed`、`confirmedAt` 回填。
- **校验规则**：仅 `draft` 或 `changed` 可确认（其他态返回 1000018003）。

### 3.7 归档 / 取消归档

- **归档**：`POST /api/project/requirements/{id}/archive`
  - 校验：非归档态均可归档（前端二次确认）；归档后条目只读，不再作为 AI 生成输入。
  - 响应：需求详情对象，`status = archived`。
- **取消归档**：`POST /api/project/requirements/{id}/unarchive`
  - 校验：仅归档态可取消（否则 1000018003）；恢复为归档前的业务状态需重新确认，**取消归档后一律回到 `draft`**，由人工重新确认，避免依赖已过期的 `changed` 态。

### 3.8 导入需求文档

- **路径**：`POST /api/project/requirements/import`
- **请求**：`multipart/form-data`，字段 `file`（必填，Markdown / Word / 图片，单文件 ≤ 20MB）。
- **响应**（提交即返回任务入口）：

```json
{
  "taskId": "…",
  "splitRecordId": "…",
  "status": "running"
}
```

- **校验规则**：类型不符 1000018006、超限 1000018007、不可解析 1000018008；同项目已有进行中（`running`）的导入任务时拒绝重复提交（1000018013）。任务执行过程与提案确认走 AI Task 任务资源（`GET /api/ai/tasks/{taskId}`、`POST /api/ai/tasks/{taskId}/proposals/confirm`），采纳落库由本模块服务承接（见 4.5）。任务 `result_meta` 含文档级 `documentMeta.detectedVersion`（AI 从文档识别的系统版本，附识别依据引语），经任务详情响应 `documentMeta` 字段下发（见 AI 底座详设 3.6.3），确认面板预填、可修改或清空；识别不到时为 `null`，采纳后留空待手工补录。

### 3.9 变更记录

- **路径**：`GET /api/project/requirements/{id}/change-logs`
- **参数**：`pageNo`、`pageSize`。
- **响应**：

```json
{
  "list": [
    {
      "id": "…",
      "changeType": "title",
      "operatorId": "…",
      "operatorName": "张三",
      "beforeSummary": { "title": "登录验证码" },
      "afterSummary": { "title": "登录验证码（短信+邮箱）" },
      "createdAt": "2026-10-02T08:30:00Z"
    }
  ],
  "total": 6
}
```

### 3.10 拆解记录

- **路径**：`GET /api/project/requirements/split-records`（项目内列表，参数 `status`、分页）
- **响应**（列表项）：

```json
{
  "id": "…",
  "sourceType": "document",
  "sourceFileId": "…",
  "aiTaskId": "…",
  "status": "adopted",
  "adoptResult": {
    "adopted": [{ "proposalKey": "item-1", "requirementId": "…" }],
    "rejected": ["item-3"],
    "operatorId": "…"
  },
  "createdAt": "2026-10-02T09:00:00Z"
}
```

- **校验规则**：建议明细（标题、描述、模块、原文定位）经 `aiTaskId` 下钻任务提案读取，本接口不复制内容。

---

## 4. 业务逻辑设计

### 4.1 编号分配（REQ-<项目内序号>）

1. 插入事务内查询 `SELECT max(seq) FROM (regexp 落在 code 上的项目内既有编号)`，序号 = max + 1，格式化为 `REQ-` + 三位起零填充（超 999 位数自然增长）；
2. 依赖 `uk_requirement_project_code` 防并发：唯一冲突时重试最多 3 次；
3. 归档条目占用的编号**不回收**，保证编号稳定可引用。

### 4.2 状态机

    draft ──confirm──> confirmed ──改标题/描述/模块──> changed
      ^                    │                              │
      │                    └──────────────────────────────┘
      │                     （改属性不动状态）
    unarchive ←──archive── confirmed / changed / draft ──archive──> archived（只读）

- 流转仅经 3.6 / 3.7 专用接口与 3.5 的自动触发产生，`PUT` 请求体不接受 `status` 字段；
- `archived` 为只读终态：拒绝一切更新（1000018004）；取消归档回到 `draft`。

### 4.3 关键词匹配

- `keyword` 命中规则：以 `REQ-` 开头且去掉分隔符后可命中编号前缀，或编号后缀匹配（便于 `001` / `REQ-001` 输入），或标题包含；
- 实现为 `code LIKE '<kw>%' OR code LIKE '%<kw>' OR title ILIKE '%<kw>'`，前缀命中可走 `uk_requirement_project_code` 的索引前缀扫描。

### 4.4 变更与状态流转触发

1. `PUT` 更新成功且变更字段 ∈ {`title`, `description`, `moduleId`} 且变更前 `status = confirmed`（`systemVersion` 等属性变更不触发，见 3.5）：
   - 状态转 `changed`；
   - 写 `requirement_change_log`（`before_summary / after_summary` 为字段级前后值）；
2. `changed` 态需求不可作为生成输入（生成链校验见 `docs/04-detailed-design/07-ai-capability/03-ai-generation.md`），直至重新 `confirm`。

### 4.5 导入拆解的采纳落库

1. 任务提案逐条确认（`POST /api/ai/tasks/{taskId}/proposals/confirm`，`action = adopted / adopted_edited / rejected`）；
2. `adopted / adopted_edited` 时由本模块 `RequirementAdoptService` 承接落库：
   - 事务内分配编号、写 `requirement`（`status = confirmed`、`source = import`、`source_file_id` 继承来源文档）；`system_version` 取确认请求 `target.systemVersion`（确认面板修改值），缺省回退任务 `documentMeta.detectedVersion`，识别不到则留空待手工补录；
   - 写 `adopted_ref` 回填（任务侧记录新需求 ID），更新 `requirement_split_record.adopt_result` 与 `status`；
3. 全部驳回：`split_record.status = rejected`，不产生任何需求；
4. 采纳落库失败（如编号重试耗尽）时，该提案采纳动作整体回滚，任务侧保持待采纳态，可重试。

### 4.6 权限与隔离

- 所有查询强制 `project_id = X-Active-Project` 过滤，越权返回 404；
- `ownerId` 指派校验当前工作空间成员资格；
- 导入任务的提交与结果读取复用 AI 任务资源的权限校验（发起人可见自己的任务）。

---

## 5. 前端设计

### 5.1 路由与页面

```
项目工作区
├── /workspace/projects/requirements                          → RequirementShellPage（需求管理壳层：需求列表单页）
├── /workspace/projects/requirements/create                   → RequirementCreatePage（新建需求）
└── /workspace/projects/requirements/:requirementId           → RequirementDetailPage（需求详情）
```

### 5.2 组件结构

```
RequirementShellPage（需求管理壳层：需求列表单页）
└── RequirementListPage（见下）

RequirementListPage
├── ListToolbar（新建需求、导入需求）
├── RequirementFilterBar（状态/模块/负责人/版本筛选 + keyword，角标显示条件数，查询/重置）
└── RequirementTable
    ├── 行操作：详情 / 确认（draft|changed 可用）/ 更多（编辑、归档、取消归档；操作列右对齐）
    └── 分页（20/50/100）

RequirementDetailPage
├── DetailHeader（面包屑与操作区同行吸顶：查看态按频次分组「编辑 │ AI 生成用例 │ 确认 / 更多（归档、取消归档）」，编辑态「取消 / 保存」；编号、标题、状态徽标）
├── AttributeCard（卡片容器「属性 | 变更记录」页签；属性表单两态同款控件，查看态禁用、编辑态启用，「保存」一次性提交变更字段；卡片与描述区按窗口动态同高）
├── MarkdownDescription（描述渲染，父容器高度按窗口动态取剩余可用空间 90%；编辑态切换为编辑器）
├── SourceAttachment（来源附件预览与下载，source=import 时）
└── ChangeTimeline（变更记录时间线，页签首次激活时加载，支持「加载更多」分页）
```

### 5.3 状态管理与交互

- Pinia store `requirement`：列表分页与筛选条件、详情缓存、导入任务引用；`aiTask` store 提供任务执行与轨迹订阅，任务完成回调刷新列表与拆解记录。
- 导入流程：文件选择（类型/大小前端预校验）→ 提交返回 `taskId` → 跳转任务详情页（`/workspace/projects/ai/tasks/:taskId`）→ 执行与轨迹轮询 → 同页审核区（预填 AI 识别的版本，可修改或清空）逐条/批量采纳 → 刷新列表。
- 全部状态分支：列表空态（引导新建/导入）、归档只读降级、权限不足隐藏入口、关键词 1 秒防抖自动查询、任务终止后再次执行入口。

---

## 6. 错误码定义

号段申请：**1000018001–1000018050（需求管理）**，登记于 `docs/04-detailed-design/02-project-module.md` 6.3；以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018001 | 404 | 需求不存在 |
| 1000018002 | 409 | 需求编号分配冲突（并发重试后仍失败） |
| 1000018003 | 409 | 当前状态不允许该操作 |
| 1000018004 | 409 | 归档条目只读 |
| 1000018005 | 400 | 所属模块不存在或不属当前项目 |
| 1000018006 | 400 | 导入文件类型不支持 |
| 1000018007 | 400 | 导入文件超过 20MB 限制 |
| 1000018008 | 400 | 导入文件为空或不可解析 |
| 1000018010 | 400 | 需求属性取值非法（优先级、版本超长等） |
| 1000018011 | 404 | 来源附件不存在 |
| 1000018012 | 403 | 无需求管理权限 |
| 1000018013 | 409 | 已存在进行中的导入任务 |

### 6.1 权限点（新增，业务侧 scope = workspace）

| code | 名称 |
| ---- | ---- |
| requirement:view | 查看需求 |
| requirement:create | 新建与导入需求 |
| requirement:edit | 编辑需求（含取消归档） |
| requirement:confirm | 确认与归档需求 |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../entity/requirement/Requirement`、`RequirementChangeLog`、`RequirementSplitRecord` + Mapper | 实体与数据访问 |
| `server/.../controller/project/RequirementController` | 仅路由与参数绑定（C2） |
| `server/.../service/requirement/RequirementService + Impl` | 列表、详情、部分更新、状态机 |
| `server/.../service/requirement/RequirementAdoptService + Impl` | 导入拆解提案采纳落库 |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018001–1000018013 |
| 权限点迁移脚本 | 新增 `requirement:*` 四个权限点（scope = workspace） |
| `web/src/pages/project/RequirementListPage.vue`、`RequirementDetailPage.vue` 及子组件 | 前端页面 |
| `web/src/stores/requirement.ts`、`web/src/services/requirement.ts`、`web/src/types/requirement.ts` | 状态、API 与类型 |

**数据库迁移说明（C5）**

```sql
-- 新建表（随本次交付；全量建库同步进 schema.sql，完整 DDL 见 2.2–2.4）
CREATE TABLE requirement ( ... );
CREATE UNIQUE INDEX uk_requirement_project_code ON requirement (project_id, code) WHERE is_deleted = FALSE;
CREATE INDEX idx_requirement_project_status ON requirement (project_id, status);
CREATE INDEX idx_requirement_module ON requirement (module_id);
CREATE INDEX idx_requirement_system_version ON requirement (project_id, system_version);
CREATE TABLE requirement_change_log ( ... );
CREATE INDEX idx_requirement_change_log_req ON requirement_change_log (requirement_id, created_at);
CREATE TABLE requirement_split_record ( ... );
CREATE INDEX idx_requirement_split_project / _task ...;
```

- UUID 主键使用框架默认策略；无物理外键；单表索引数 4 / 1 / 2，均 ≤ 5（C9）。
- **存量接口对齐**：若既有 `/api/project/requirements*` 接口与本文档不一致（字段、路径、错误码），以本文档为目标态迁移，差异清单在实现时随迁移说明提交。

**OpenAPI**：接口随本次交付经 springdoc 暴露，分组 `requirement`；`import` 标注 multipart（异步任务返回）。

**测试要点（C8 ≥ 70%）**

- 后端：编号分配并发与重试、状态机非法流转矩阵、归档只读、部分更新不覆盖未传字段、变更触发状态流转与变更记录、拆解采纳事务、导入大小/类型校验、关键词三段匹配、越权 404；
- 前端：筛选与防抖查询、状态按钮可用性矩阵、导入全流程与任务详情页审核、归档只读降级、空态与权限隐藏。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-02 | 需求增加业务系统版本属性，导入支持 AI 识别版本，列表、筛选与属性栏同步 |
| V1.0 | 2026-10-02 | 前端路由对齐全局导航约定，改为 /workspace/projects/requirements |
| V1.0 | 2026-10-03 | 导入/拆分进度与建议审核统一到任务详情页（/workspace/projects/ai/tasks/:taskId），删除进度弹窗与审核面板浮层口径 |
| V1.0 | 2026-10-03 | 权限映射表拆解记录行对齐 3.11 路径（`{id}/split-logs` 与项目级 `/split-records`） |
| V1.0 | 2026-10-06 | 3.8 补识别版本下发通道：`documentMeta` 经任务详情响应字段回传，确认面板据此预填 |
| V1.0 | 2026-10-15 | 5.1 / 5.2 增需求管理壳层（页内侧栏承载需求列表与追溯矩阵子页）与旧路径重定向，TraceEntry 改为页内切至追溯子页 |
| V1.0 | 2026-10-15 | 5.2 详情页组件对齐新建页骨架：操作区与面包屑同行、标题与描述并入统一编辑态、属性改为「属性 \| 变更记录」页签卡片，变更记录按页签激活加载 |
| V1.0 | 2026-10-15 | 5.2 描述与属性卡片高度改为按窗口动态同高；属性查看态改为同款表单控件禁用态，不再以只读文本替换组件 |
| V1.0 | 2026-10-15 | 5.2 详情页操作区按频次分组（编辑/追溯改 link、归档收进「更多」下拉），移除「AI 拆分」详情页入口（接口 3.9 保留） |
| V1.0 | 2026-10-15 | 5.2 列表行操作：「归档」收进「更多」下拉，操作列右对齐 |
| V1.0 | 2026-10-15 | 5.2 详情页 AI 入口按钮更名「AI 生成用例」 |
| V1.0 | 2026-10-15 | 下线条目内 AI 拆分：删除原 3.9 接口与 split-logs 反查（后续章节顺次前移编号），拆解记录表移除 `source_requirement_id` 及其索引，4.5 收敛为导入采纳落库，错误码 1000018009 注销、1000018013 收敛为导入口径 |
| V1.0 | 2026-10-15 | 5.1 / 5.2 增链路明细页路由（`/workspace/projects/requirements/trace/:nodeType/:nodeId`），TraceEntry 改为跳转该页 |
| V1.0 | 2026-10-16 | 同步 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`）：统一任务资源改 AI Task 任务资源，确认端点 `artifacts/confirm` → `proposals/confirm`，任务 `result` → `result_meta`、`pending/running` 去重收敛为 `running`；产物改称提案，4.5 承接与前端状态分支同步 |
| V1.0 | 2026-10-16 | 术语与状态口径残词勘误：3.8 导入响应示例 `status` 改 `running`（任务创建即启动）、3.10 `adoptResult` 键改 `proposalKey`、5.3 `aiTask` store 改任务执行与轨迹订阅 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：删除 3.11 追溯视图委托接口（含 `{id}/trace` 权限行与错误码 1000018014，登记收敛为 1000018001–1000018013）、4.4 影响分析触发（改为纯状态流转）与确认/更新处的影响任务表述，移除覆盖率筛选与 `coverageStatus` 字段、追溯子页路由与旧路径重定向、TraceEntry 与查看追溯入口 |
