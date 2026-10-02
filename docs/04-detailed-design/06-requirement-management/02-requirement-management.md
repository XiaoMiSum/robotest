# 软件测试平台——需求管理

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对**需求管理模块**进行详细设计，定义需求域数据结构、接口规范、业务逻辑与前端组件，为开发实现提供完整依据。需求条目是平台追溯链的起点，拆解、影响分析与覆盖状态由 AI 能力经统一任务资源供给，本文档只定义需求域自身的行为与接口。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/06-requirement-management/02-srs-requirement-management.md`（US-RM-001 ~ 005）。
- 对应概要：`docs/02-high-level-design/06-requirement-management/02-hld-requirement-management.md`。
- 关联设计：
  - AI 任务与产物确认接口：`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md`（导入 / 拆分 / 影响分析任务的提交与采纳走该资源）；
  - 追溯链路查询：`docs/04-detailed-design/05-trace-matrix.md` 3.3（本文档 3.11 为其委托入口）。

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
| system_version | varchar(50) | NULL | 被测业务系统版本（项目即被测业务系统，如 `V2.3`）；按属性变更处理，不触发状态流转与影响分析 |
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
| change_type | varchar(30) | NOT NULL | `title / description / module / status / attribute`——只有前三类触发影响标记，本字段支撑"哪类变更触发了什么"的回查 |
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
| source_type | varchar(20) | NOT NULL | 拆解来源：`document`（导入文档）/ `requirement`（条目内 AI 拆分） |
| source_file_id | uuid | NULL | 导入的原始文档附件（来源为 document 时） |
| source_requirement_id | uuid | NULL | 原条目标识（来源为 requirement 时；采纳后原条目归档，靠此保留关联） |
| ai_task_id | uuid | NOT NULL | 关联 AI 任务（逻辑外键 → ai_task）；**拆分建议明细存任务产物侧，本表不复制**（单一事实源） |
| status | varchar(20) | NOT NULL, DEFAULT 'pending' | `pending`（建议待确认，含部分未处理）/ `adopted`（全部处理完且至少采纳一条）/ `rejected`（全部驳回） |
| adopt_result | jsonb | NULL | 采纳结果：逐条产物动作（adopted / adopted_edited / rejected）与生成的新需求 ID 列表、操作人 |

**索引**（3 个）：

- `idx_requirement_split_project` (project_id, created_at) —— 拆解记录列表；
- `idx_requirement_split_source` (source_requirement_id) —— "这个需求从哪来 / 拆出了谁"反查；
- `idx_requirement_split_task` (ai_task_id) —— 任务下钻。

```sql
CREATE TABLE requirement_split_record (
    id                    uuid PRIMARY KEY,
    project_id            uuid NOT NULL,
    source_type           varchar(20) NOT NULL,
    source_file_id        uuid NULL,
    source_requirement_id uuid NULL,
    ai_task_id            uuid NOT NULL,
    status                varchar(20) NOT NULL DEFAULT 'pending',
    adopt_result          jsonb NULL,
    created_at            timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted            boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_requirement_split_project ON requirement_split_record (project_id, created_at);
CREATE INDEX idx_requirement_split_source ON requirement_split_record (source_requirement_id);
CREATE INDEX idx_requirement_split_task ON requirement_split_record (ai_task_id);
```

> 追溯边（`trace_edge`）与覆盖结论（`trace_coverage_result`）为平台级业务资源，DDL 见 `docs/04-detailed-design/05-trace-matrix.md` 第 2 节；本模块不持久化链路数据。

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
| /api/project/requirements/{id}/split | POST | requirement:edit |
| /api/project/requirements/{id}/change-logs | GET | requirement:view |
| /api/project/requirements/{id}/split-records | GET | requirement:view |
| /api/project/requirements/{id}/trace | GET | requirement:view |

### 3.2 需求列表

- **路径**：`GET /api/project/requirements`
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| status | string | 否 | 状态筛选，逗号分隔多选 |
| moduleIds | uuid | 否 | 所属模块筛选，逗号分隔 |
| ownerId | uuid | 否 | 负责人筛选 |
| systemVersion | string | 否 | 版本筛选（精确匹配） |
| coverage | string | 否 | 覆盖状态筛选：`covered / partial / uncovered / pending`（联查 `trace_coverage_result`） |
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
      "coverageStatus": "partial",
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

- **校验规则**：强制 `project_id = X-Active-Project`；`coverageStatus` 无记录返回 `pending`，AI 总开关关闭返回 `null`（前端展示「—」）。

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
  "coverageStatus": "partial",
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
  - 传入 `title / description / moduleId` 且当前 `status = confirmed` → 状态自动转 `changed`，写变更记录并提交影响分析任务（见 4.4）；
  - 仅传 `systemVersion / priority / ownerId / tags` 等属性 → 状态不变，只写 `change_type = attribute` 变更记录。

### 3.6 确认需求

- **路径**：`POST /api/project/requirements/{id}/confirm`
- **响应**：需求详情对象，`status = confirmed`、`confirmedAt` 回填。
- **校验规则**：仅 `draft` 或 `changed` 可确认（其他态返回 1000018003）；确认后若存在未刷新的影响标记，触发影响分析刷新任务（异步，不阻塞响应）。

### 3.7 归档 / 取消归档

- **归档**：`POST /api/project/requirements/{id}/archive`
  - 校验：非归档态均可归档（前端二次确认）；归档后条目只读，不再作为 AI 生成与覆盖分析输入，既有追溯关系保留可查。
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
  "status": "pending"
}
```

- **校验规则**：类型不符 1000018006、超限 1000018007、不可解析 1000018008；同项目已有 `pending/running` 的导入任务时拒绝重复提交（1000018013）。任务进度、产物确认与采纳走 AI 任务资源（`GET /api/ai/tasks/{taskId}`、`POST /api/ai/tasks/{taskId}/artifacts/confirm`），采纳落库由本模块服务承接（见 4.5）。任务 `result` 含文档级 `documentMeta.detectedVersion`（AI 从文档识别的系统版本，附识别依据引语），确认面板预填、可修改或清空；识别不到时为 `null`，采纳后留空待手工补录。

### 3.9 条目内 AI 拆分

- **路径**：`POST /api/project/requirements/{id}/split`
- **响应**：

```json
{
  "taskId": "…",
  "splitRecordId": "…",
  "status": "pending"
}
```

- **校验规则**：条目须属当前项目且非 `archived`（否则 1000018009）；同一条目已有进行中拆分任务时拒绝（1000018013）。采纳后：新条目按 4.1 分配编号并进入 `confirmed`（`system_version` 继承原条目，可经确认面板覆盖），原条目自动归档，`source_requirement_id` 保留关联（见 4.5）。

### 3.10 变更记录

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

### 3.11 拆解记录

- **路径**：`GET /api/project/requirements/{id}/split-logs`（按原条目反查）与 `GET /api/project/requirements/split-records`（项目内列表，参数 `status`、`sourceType`、分页）
- **响应**（列表项）：

```json
{
  "id": "…",
  "sourceType": "requirement",
  "sourceRequirementId": "…",
  "sourceRequirementCode": "REQ-007",
  "aiTaskId": "…",
  "status": "adopted",
  "adoptResult": {
    "adopted": [{ "artifactKey": "item-1", "requirementId": "…" }],
    "rejected": ["item-3"],
    "operatorId": "…"
  },
  "createdAt": "2026-10-02T09:00:00Z"
}
```

- **校验规则**：建议明细（标题、描述、模块、原文定位）经 `aiTaskId` 下钻任务产物读取，本接口不复制内容。

### 3.12 追溯视图（委托）

- **路径**：`GET /api/project/requirements/{id}/trace`
- **参数**：`direction`（可选，同链路视图）、`depth`（可选，默认 6）。
- **响应**：与 `docs/04-detailed-design/05-trace-matrix.md` 3.3 链路视图**同构**（`root / nodes / edges / hasMore`），服务端以 `sourceType = requirement`、`sourceId = {id}` 转发矩阵服务后返回。
- **校验规则**：本接口为只读委托，无 `trace:edit` 语义；矩阵服务调用失败返回 1000018014（前端提示稍后重试，不降级为无数据空态）；空链路返回 `nodes = []`（前端展示空态与「发起 AI 生成」引导）。

---

## 4. 业务逻辑设计

### 4.1 编号分配（REQ-<项目内序号>）

1. 插入事务内查询 `SELECT max(seq) FROM (regexp 落在 code 上的项目内既有编号)`，序号 = max + 1，格式化为 `REQ-` + 三位起零填充（超 999 位数自然增长）；
2. 依赖 `uk_requirement_project_code` 防并发：唯一冲突时重试最多 3 次；
3. 归档条目占用的编号**不回收**，保证追溯链中的编号稳定可引用。

### 4.2 状态机

    draft ──confirm──> confirmed ──改标题/描述/模块──> changed
      ^                    │                              │
      │                    └──────────────────────────────┘
      │                     （改属性不动状态）
    unarchive ←──archive── confirmed / changed / draft ──archive──> archived（只读）

- 流转仅经 3.6 / 3.7 专用接口与 3.5 的自动触发产生，`PUT` 请求体不接受 `status` 字段；
- `archived` 为只读终态：拒绝一切更新（1000018004）；取消归档回到 `draft`；
- 状态流转不打断既有追溯边，归档条目的链路保留可查。

### 4.3 关键词匹配

- `keyword` 命中规则：以 `REQ-` 开头且去掉分隔符后可命中编号前缀，或编号后缀匹配（便于 `001` / `REQ-001` 输入），或标题包含；
- 实现为 `code LIKE '<kw>%' OR code LIKE '%<kw>' OR title ILIKE '%<kw>'`，前缀命中可走 `uk_requirement_project_code` 的索引前缀扫描。

### 4.4 变更与影响分析触发

1. `PUT` 更新成功且变更字段 ∈ {`title`, `description`, `moduleId`} 且变更前 `status = confirmed`（`systemVersion` 等属性变更不触发，见 3.5）：
   - 状态转 `changed`；
   - 写 `requirement_change_log`（`before_summary / after_summary` 为字段级前后值）；
   - **事务提交后**异步提交 `POST /api/ai/tasks`（`type = impact_analysis`，输入 `requirementId`），提交失败只记日志不回滚需求更新（下次确认时 3.6 兜底再触发）；
2. 影响项计算与处置展示由追溯矩阵承载（`docs/04-detailed-design/05-trace-matrix.md` 3.10）；
3. `changed` 态需求不可作为生成输入（生成链校验见 `docs/04-detailed-design/07-ai-capability/03-ai-generation.md`），直至重新 `confirm`。

### 4.5 导入与拆解的采纳落库

1. 任务产物逐条确认（`POST /api/ai/tasks/{taskId}/artifacts/confirm`，`action = adopted / adopted_edited / rejected`）；
2. `adopted / adopted_edited` 时由本模块 `RequirementAdoptService` 承接落库：
   - 事务内分配编号、写 `requirement`（`status = confirmed`、`source = import/requirement`、`source_file_id` 继承来源文档）；`system_version` 取确认请求 `target.systemVersion`（确认面板修改值），缺省回退任务 `documentMeta.detectedVersion`（导入）或继承原条目（拆分），两者皆无则留空；
   - 写 `adopted_ref` 回填（任务侧记录新需求 ID），更新 `requirement_split_record.adopt_result` 与 `status`；
   - 来源为 `requirement` 时，全部产物处理完成后将原条目归档；
3. 全部驳回：`split_record.status = rejected`，不产生任何需求；
4. 采纳落库失败（如编号重试耗尽）时，该产物确认动作整体回滚，任务侧保持待确认态，可重试。

### 4.6 权限与隔离

- 所有查询强制 `project_id = X-Active-Project` 过滤，越权返回 404；
- `ownerId` 指派校验当前工作空间成员资格；
- 导入 / 拆分 / 影响分析任务的提交与结果读取复用 AI 任务资源的权限校验（发起人可见自己的任务）。

---

## 5. 前端设计

### 5.1 路由与页面

```
项目工作区
├── /projects/:projectId/requirements            → RequirementListPage（需求列表）
└── /projects/:projectId/requirements/:id        → RequirementDetailPage（需求详情）
```

### 5.2 组件结构

```
RequirementListPage
├── ListToolbar（新建需求、导入需求）
├── RequirementFilterBar（状态/模块/负责人/版本筛选 + keyword，角标显示条件数，查询/重置）
└── RequirementTable
    ├── 覆盖状态徽标（covered/partial/uncovered/pending/—）
    ├── 行操作：详情 / 确认（draft|changed 可用）/ 归档 / 更多（编辑、取消归档）
    └── 分页（20/50/100）

RequirementDetailPage
├── DetailHeader（编号、标题内联编辑、状态徽标、确认/归档按钮）
├── AttributePanel（模块、版本、负责人、优先级、标签、来源；仅提交变更字段）
├── MarkdownDescription（描述渲染与编辑）
├── SourceAttachment（来源附件预览与下载，source=import 时）
├── ChangeTimeline（变更记录时间线）
├── SplitDialog（AI 拆分入口 → 任务进度 → 建议审核，复用 AI 产物审核面板）
└── TraceEntry（查看追溯 → TraceMatrixPage 只读链路模式）
```

### 5.3 状态管理与交互

- Pinia store `requirement`：列表分页与筛选条件、详情缓存、导入/拆分任务引用；`aiTask` store 提供任务进度订阅，任务完成回调刷新列表与拆解记录。
- 导入流程：文件选择（类型/大小前端预校验）→ 提交返回 `taskId` → 进度弹窗 → 审核面板（预填 AI 识别的版本，可修改或清空）逐条/批量采纳 → 刷新列表。
- 全部状态分支：列表空态（引导新建/导入）、覆盖状态 `—`（AI 关闭）、归档只读降级、权限不足隐藏入口、关键词 1 秒防抖自动查询、任务失败重试入口。

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
| 1000018009 | 400 | 拆分输入不满足条件（条目不可拆分） |
| 1000018010 | 400 | 需求属性取值非法（优先级、版本超长等） |
| 1000018011 | 404 | 来源附件不存在 |
| 1000018012 | 403 | 无需求管理权限 |
| 1000018013 | 409 | 已存在进行中的导入或拆分任务 |
| 1000018014 | 502 | 追溯服务调用失败 |

### 6.1 权限点（新增，业务侧 scope = workspace）

| code | 名称 |
| ---- | ---- |
| requirement:view | 查看需求 |
| requirement:create | 新建与导入需求 |
| requirement:edit | 编辑需求（含取消归档、发起拆分） |
| requirement:confirm | 确认与归档需求 |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../entity/requirement/Requirement`、`RequirementChangeLog`、`RequirementSplitRecord` + Mapper | 实体与数据访问 |
| `server/.../controller/project/RequirementController` | 仅路由与参数绑定（C2） |
| `server/.../service/requirement/RequirementService + Impl` | 列表、详情、部分更新、状态机 |
| `server/.../service/requirement/RequirementAdoptService + Impl` | 导入/拆解产物采纳落库 |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018001–1000018014 |
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
CREATE INDEX idx_requirement_split_project / _source / _task ...;
```

- UUID 主键使用框架默认策略；无物理外键；单表索引数 4 / 1 / 3，均 ≤ 5（C9）。
- **存量接口对齐**：若既有 `/api/project/requirements*` 接口与本文档不一致（字段、路径、错误码），以本文档为目标态迁移，差异清单在实现时随迁移说明提交。

**OpenAPI**：接口随本次交付经 springdoc 暴露，分组 `requirement`；`import` 标注 multipart，`split` 标注异步任务返回。

**测试要点（C8 ≥ 70%）**

- 后端：编号分配并发与重试、状态机非法流转矩阵、归档只读、部分更新不覆盖未传字段、变更触发影响任务（提交失败不回滚）、拆解采纳事务与原条目归档、导入大小/类型校验、关键词三段匹配、越权 404；
- 前端：筛选与防抖查询、状态按钮可用性矩阵、导入与拆分全流程弹窗、归档只读降级、空态与权限隐藏。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-02 | 需求增加业务系统版本属性，导入支持 AI 识别版本，列表、筛选与属性栏同步 |
