# 软件测试平台——追溯矩阵

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对平台级**追溯矩阵**业务域进行详细设计，定义 `trace_` 域数据结构、矩阵 / 链路 / 覆盖 / 影响接口、业务逻辑与前端组件，为开发实现提供依据。追溯矩阵是需求、功能测试、AI 能力三个模块共同读写的跨模块公共资源，本文档是其 DDL 与接口的唯一事实源，其余文档只引用不重复定义。

### 1.2 范围与对应设计

- 对应概要设计：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md` 第 4.3 节（矩阵服务为数据维护方）、`docs/02-high-level-design/06-requirement-management/02-hld-requirement-management.md` 第 3.4 节（需求模块只提供入口）。
- 追溯数据由 AI 底座矩阵服务**唯一维护**；需求管理、缺陷管理等模块不持久化链路数据。
- 覆盖分析与影响分析的**任务发起**统一走 AI 任务资源（`docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md` 3.6），本文档只定义查询、修正与处置接口。

### 1.3 参考资料

- `docs/00-spec/20-contracts/01-api.md`（URL/方法/分页/错误码规范）
- `docs/00-spec/20-contracts/02-database.md`（DDL/索引规范，C5/C9）
- `docs/02-high-level-design/04-hld-data-interface.md`（上下文头与数据隔离口径）
- `docs/04-detailed-design/01-readme.md`（通用响应与分页约定）

---

## 2. 数据设计

### 2.1 域登记与公共约定

- **域前缀**：`trace_`，登记为**平台级业务域**（跨模块公共业务资源，与 `project_` 同级），本域建表前缀与索引前缀统一使用 `trace_` / `idx_trace_*` / `uk_trace_*`。
- **公共字段（C5，每表必有）**：`id uuid PRIMARY KEY`（框架默认策略生成）、`created_at timestamp NOT NULL`、`updated_at timestamp NOT NULL`、`is_deleted boolean NOT NULL DEFAULT FALSE`；时间列 UTC 语义，无时区。
- **无物理外键（C5）**：对需求、用例、评审、计划的关联全部为逻辑外键（`type + id` 多态关联），引用完整性由 Service 层保证。
- 下文字段表不再重复列出四个公共字段。

### 2.2 追溯边表（trace_edge）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| project_id | uuid | NOT NULL | 所属项目，**隔离边界**；矩阵 / 链路 / 影响查询强制按此过滤 |
| edge_type | varchar(20) | NOT NULL | 边类型：`derivation`（派生边）/ `snapshot_ref`（快照引用边） |
| source_type | varchar(30) | NOT NULL | 源节点类型：`requirement` / `module` / `mindmap_document` / `test_case` |
| source_id | uuid | NOT NULL | 源节点 ID（逻辑外键，与 `source_type` 组合定位） |
| target_type | varchar(30) | NOT NULL | 目标节点类型：`module` / `mindmap_document` / `test_case` / `test_review` / `test_plan` |
| target_id | uuid | NOT NULL | 目标节点 ID（逻辑外键） |
| target_version | varchar(64) | NULL | 引用时目标内容的版本标识；与当前版本比对不一致 → 边转 `stale` |
| status | varchar(20) | NOT NULL, DEFAULT 'ai_created' | 边状态机，取值见 2.4 |
| established_by | varchar(20) | NOT NULL, DEFAULT 'ai' | 建立方式：`ai` / `manual` |
| confirmed_by | uuid | NULL | 最近一次人工确认 / 修正的操作人 |
| confirmed_at | timestamp | NULL | 最近一次人工确认 / 修正时间 |

**索引**（4 个，≤ 5，C9）：

- `uk_trace_edge_pair` UNIQUE (source_type, source_id, target_type, target_id) WHERE is_deleted = FALSE —— 同一对节点仅一条有效边；
- `idx_trace_edge_project` (project_id) —— 矩阵全量拉取与隔离过滤；
- `idx_trace_edge_source` (source_type, source_id) —— 链路视图正向回溯；
- `idx_trace_edge_target` (target_type, target_id) —— 反向回溯与影响分析。

```sql
CREATE TABLE trace_edge (
    id             uuid PRIMARY KEY,
    project_id     uuid NOT NULL,
    edge_type      varchar(20) NOT NULL,
    source_type    varchar(30) NOT NULL,
    source_id      uuid NOT NULL,
    target_type    varchar(30) NOT NULL,
    target_id      uuid NOT NULL,
    target_version varchar(64) NULL,
    status         varchar(20) NOT NULL DEFAULT 'ai_created',
    established_by varchar(20) NOT NULL DEFAULT 'ai',
    confirmed_by   uuid NULL,
    confirmed_at   timestamp NULL,
    created_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted     boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_trace_edge_pair ON trace_edge (source_type, source_id, target_type, target_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_trace_edge_project ON trace_edge (project_id);
CREATE INDEX idx_trace_edge_source ON trace_edge (source_type, source_id);
CREATE INDEX idx_trace_edge_target ON trace_edge (target_type, target_id);
```

**设计说明**：

1. **多态关联（type + id）**：一条边连接 5 类实体，建物理外键既违反 C5 也不可行（目标表不同）；关联完整性由矩阵服务在写入与查询时校验节点存在性与项目归属。
2. **派生边直连"需求 → 各层产物"**：`requirement → module`、`requirement → mindmap_document`、`requirement → test_case` 各存一条，避免"用例挂在 A 模块、源自 B 需求"时相邻层链路断裂；相邻层父子关系由既有模块树与文档表表达，本表不重复存储结构关系。
3. **`status` 承载断开而非复用 `is_deleted`**：人工断开的边保留 `detached` 状态，唯一约束对 `is_deleted = FALSE` 的行始终生效——AI 重新生成时对同一对节点的插入将命中 `uk_trace_edge_pair` 冲突，只能走恢复 / 改挂接口，从而实现「人工断开后 AI 不得自动重建」。
4. `target_version` 由目标域提供版本摘要（用例内容版本、快照版本等），无版本概念的节点类型允许为 NULL（不参与变更感知）。

### 2.3 覆盖结论表（trace_coverage_result）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| project_id | uuid | NOT NULL | 所属项目，隔离边界 |
| requirement_id | uuid | NOT NULL | 需求条目 ID（逻辑外键），每需求至多一条结论 |
| coverage_status | varchar(20) | NOT NULL | `covered`（已覆盖）/ `partial`（部分覆盖）/ `uncovered`（未覆盖） |
| evidence | jsonb | NULL | 判定依据：命中的用例集合、缺口说明、AI 理由摘要 |
| analyzed_task_id | uuid | NULL | 来源覆盖分析任务 ID（逻辑外键 → ai_task），下钻用 |
| ai_analyzed_at | timestamp | NULL | AI 分析时间 |
| reviewed_by | uuid | NULL | 人工复核修正人；**非空即人工判定优先** |
| reviewed_note | varchar(500) | NULL | 人工修正说明 |
| reviewed_at | timestamp | NULL | 人工修正时间 |

**索引**（1 个）：

- `uk_trace_coverage_requirement` UNIQUE (project_id, requirement_id) WHERE is_deleted = FALSE。

```sql
CREATE TABLE trace_coverage_result (
    id               uuid PRIMARY KEY,
    project_id       uuid NOT NULL,
    requirement_id   uuid NOT NULL,
    coverage_status  varchar(20) NOT NULL,
    evidence         jsonb NULL,
    analyzed_task_id uuid NULL,
    ai_analyzed_at   timestamp NULL,
    reviewed_by      uuid NULL,
    reviewed_note    varchar(500) NULL,
    reviewed_at      timestamp NULL,
    created_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_trace_coverage_requirement ON trace_coverage_result (project_id, requirement_id) WHERE is_deleted = FALSE;
```

**设计说明**：

1. 本表是**矩阵单元格的业务状态**（需求 × 用例集合的覆盖质量结论），与 `trace_edge`（结构事实）分工：边回答"谁和谁有关系"，本表回答"覆盖了没有、差在哪"。
2. AI 分析写入时必须跳过 `reviewed_by IS NOT NULL` 的行——**人工判定优先，AI 分析不得覆盖**（概要设计 4.3）。
3. 需求列表的覆盖状态列直接查询本表；无记录展示「待分析」，AI 总开关关闭展示「—」（需求分册 1.2）。

### 2.4 枚举与状态机

**边类型 `edge_type`**：

| 取值 | 含义 | 建立时机 |
| ---- | ---- | ---- |
| derivation | 派生边（需求 → 模块 / 脑图文档 / 测试用例） | 生成链产物采纳落库时建立 |
| snapshot_ref | 快照引用边（测试用例 ⇢ 评审 / 计划，带版本） | 评审 / 计划创建生成快照时建立（AI 圈选确认后走既有创建流程） |

**边状态 `status`**：

    ai_created ──人工确认──> confirmed
        │                      │
        │                      └──上游内容版本变更──> stale ──重新同步/确认──> confirmed
        ├──AI 与人工判定不一致──> conflict ──人工处理──> confirmed / detached
        └──人工断开──> detached ──人工恢复/改挂──> confirmed

| 取值 | 含义 |
| ---- | ---- |
| ai_created | AI 建立，待人工确认；不计入"已确认覆盖"统计 |
| confirmed | 人工确认有效；人工新建的边直接进入该态 |
| conflict | AI 建立与人工判断不一致；覆盖统计以人工判定为准 |
| stale | 待重新确认：上游内容版本与 `target_version` 不一致，矩阵高亮 |
| detached | 人工断开；不参与任何遍历与统计，且不可被 AI 自动重建 |

**覆盖状态 `coverage_status`**：`covered` / `partial` / `uncovered`（无记录为「待分析」）。

---

## 3. 接口详细设计

### 3.1 通用约定

- 基础路径：`/api/project/trace`；请求需 `Authorization` + `X-Active-Workspace` + `X-Active-Project` 上下文头（C4，上下文不出 URL），服务端校验项目与工作空间归属及追溯权限。
- 响应使用平台通用 `Result<T>`，下文示例**仅展示 `data` 字段**；分页 `pageNo`（从 1）/ `pageSize`（默认 20，最大 100）→ `{ list, total }`；字段 camelCase。
- 覆盖分析与影响分析的**任务发起**不走本文档接口：`POST /api/ai/tasks`（`type = coverage_analysis / impact_analysis`），发起后通过任务进度轮询获知完成，再刷新本文档的查询接口。
- 时间字段格式与状态取值以 `docs/00-spec/20-contracts/01-api.md` 4.3 为准。

### 3.2 矩阵视图

- **路径**：`GET /api/project/trace/matrix`
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| requirementStatus | string | 否 | 需求状态筛选：`draft / confirmed / changed / archived` |
| coverage | string | 否 | 覆盖状态筛选：`covered / partial / uncovered / pending`（pending=待分析） |
| keyword | string | 否 | 需求编号前缀/后缀或标题包含匹配 |
| pageNo / pageSize | number | 否 | 分页 |

- **响应**：

```json
{
  "list": [
    {
      "requirementId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "code": "REQ-001",
      "title": "登录验证码",
      "status": "confirmed",
      "coverageStatus": "partial",
      "edgeCounts": { "module": 1, "document": 1, "testCase": 5, "review": 1, "plan": 1 },
      "staleCount": 1,
      "conflictCount": 0
    }
  ],
  "total": 37
}
```

- **校验规则**：只统计 `is_deleted = FALSE` 且 `status <> 'detached'` 的边；`coverageStatus` 由 `trace_coverage_result` 联查，无记录为 `pending`。

### 3.3 链路视图

- **路径**：`GET /api/project/trace/chain`
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| sourceType | string | 是 | 起点类型：`requirement / module / mindmap_document / test_case / test_review / test_plan` |
| sourceId | uuid | 是 | 起点 ID |
| direction | string | 否 | `down`（默认，向下游）/ `up`（向上游回溯）/ `both` |

- **响应**（节点与边分离，前端按边渲染连线）：

```json
{
  "root": { "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "type": "requirement", "title": "REQ-001 登录验证码" },
  "nodes": [
    { "id": "…", "type": "test_case", "title": "TC-001 获取验证码", "version": "v3", "level": 3 }
  ],
  "edges": [
    {
      "edgeId": "…",
      "sourceId": "…", "targetId": "…",
      "edgeType": "derivation", "status": "confirmed",
      "targetVersion": "v3", "versionMatched": true
    }
  ],
  "hasMore": false
}
```

- **校验规则**：起点节点必须属于当前项目；遍历深度上限 6 层，节点数上限 2000，超出置 `hasMore = true`。

### 3.4 追溯边列表

- **路径**：`GET /api/project/trace/edges`
- **参数**：`edgeType`、`status`、`sourceType`、`sourceId`、`targetType`、`targetId`（以上均可选）、`pageNo`、`pageSize`。
- **响应**：

```json
{
  "list": [
    {
      "edgeId": "…",
      "edgeType": "derivation",
      "source": { "type": "requirement", "id": "…", "title": "REQ-001 登录验证码" },
      "target": { "type": "test_case", "id": "…", "title": "TC-001 获取验证码", "version": "v4" },
      "targetVersion": "v3",
      "status": "stale",
      "establishedBy": "ai",
      "confirmedBy": null,
      "confirmedAt": null
    }
  ],
  "total": 12
}
```

- **响应节点 title** 由矩阵服务按 `target_type` 分发到对应服务批量解析（逻辑外键回填）；解析不到的节点展示占位并置 `status = conflict` 提示人工处理。

### 3.5 人工新建追溯边

- **路径**：`POST /api/project/trace/edges`
- **方法**：`POST`（幂等：命中 `uk_trace_edge_pair` 返回既有边并置 `code = 1000018153`，引导改走 3.6 恢复 / 改挂）
- **请求**：

```json
{
  "edgeType": "derivation",
  "sourceType": "requirement",
  "sourceId": "…",
  "targetType": "test_case",
  "targetId": "…",
  "targetVersion": "v4"
}
```

- **响应**：新建边对象（同 3.4 单条结构），`status = confirmed`、`establishedBy = manual`、`confirmedBy = 当前用户`。
- **校验规则**：两端节点存在、同属当前项目、类型组合合法（`derivation` 仅允许需求侧起点，`snapshot_ref` 仅允许用例侧起点）；需要 `trace:edit` 权限。

### 3.6 追溯边修正（确认 / 改挂 / 断开 / 恢复）

- **路径**：`PATCH /api/project/trace/edges/{edgeId}`
- **请求**（按 `action` 分发，三选一）：

```json
{ "action": "confirm" }
```

```json
{ "action": "reattach", "targetType": "module", "targetId": "…", "targetVersion": "v2" }
```

```json
{ "action": "detach", "reason": "AI 误建，TC-007 不覆盖 REQ-001" }
```

```json
{ "action": "restore" }
```

| action | 语义 | 状态变化 |
| ---- | ---- | ---- |
| confirm | 人工确认该边有效 | 任意态 → `confirmed`，记录 `confirmed_by / confirmed_at` |
| reattach | 改挂目标（人工纠正指向） | 改写 target 与版本 → `confirmed`；旧指向唯一约束经更新释放 |
| detach | 人工断开 | → `detached`（留痕，不删除），AI 之后不得自动重建 |
| restore | 恢复被断开的边 | `detached` → `confirmed` |

- **校验规则**：`reattach / restore` 校验新目标存在且同项目；`action = confirm` 在 `detached` 态下拒绝（须先 `restore`）；需要 `trace:edit` 权限。
- **响应**：修正后的边对象。

### 3.7 覆盖状态查询

- **路径**：`GET /api/project/trace/coverage`
- **参数**：`requirementIds`（uuid 逗号分隔，可选，最多 100 个）；不传时按当前项目全量分页。
- **响应**：

```json
{
  "list": [
    {
      "requirementId": "…",
      "coverageStatus": "partial",
      "evidence": { "coveredBy": ["…", "…"], "gaps": ["缺少 60 秒重发限制用例"] },
      "aiAnalyzedAt": "2026-10-02T08:30:00Z",
      "reviewedBy": null,
      "analyzedTaskId": "…"
    }
  ],
  "total": 37
}
```

### 3.8 覆盖结论人工修正

- **路径**：`PATCH /api/project/trace/coverage/{requirementId}`
- **请求**：

```json
{ "coverageStatus": "covered", "note": "TC-004 已覆盖重发限制，AI 判定过时" }
```

- **响应**：修正后的结论对象（`reviewedBy = 当前用户`、`reviewedAt` 回填）。
- **校验规则**：需求存在且属当前项目；结论不存在时以 `aiAnalyzedAt = NULL` 创建人工结论；修正后该行 AI 分析不再覆盖（2.3 设计说明 2）；需要 `trace:edit` 权限。

### 3.9 缺口列表

- **路径**：`GET /api/project/trace/gaps`
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| type | string | 是 | `uncovered_requirement`（未覆盖需求）/ `orphan_case`（孤儿用例，无派生入边）/ `unreviewed_case`（有派生入边但无评审引用边）/ `unscheduled_case`（有派生入边但无计划引用边） |
| pageNo / pageSize | number | 否 | 分页 |

- **响应**：

```json
{
  "list": [
    {
      "targetType": "requirement",
      "targetId": "…",
      "title": "REQ-003 密码重置",
      "coverageStatus": "uncovered",
      "suggestedAction": "generate"
    }
  ],
  "total": 5
}
```

- **校验规则**：`suggestedAction` 按缺口类型给出引导：`generate`（发起生成）/ `review`（发起评审）/ `schedule`（加入计划）；统计排除已归档需求与 `detached` 边。

### 3.10 受影响项查询与处置

- **查询**：`GET /api/project/trace/impact-items`
  - 参数：`requirementId`（必填）、`disposition`（可选：`pending / regenerate / re_review / no_impact`）、`pageNo`、`pageSize`。
  - 响应：

```json
{
  "list": [
    {
      "edgeId": "…",
      "target": { "type": "test_case", "id": "…", "title": "TC-003 验证码过期" },
      "impactType": "derivation",
      "disposition": "pending",
      "reason": null,
      "disposedBy": null
    }
  ],
  "total": 9
}
```

- **处置**：`PATCH /api/project/trace/impact-items/{edgeId}`
  - 请求：

```json
{ "disposition": "no_impact", "reason": "描述变更不影响该用例断言" }
```

  - 响应：处置后的受影响项对象。
  - 校验规则：受影响项由 `impact_analysis` 任务产出（写入边的处置标记字段组）；`disposition` 取值：`regenerate`（重新生成建议，转为任务入口）/ `re_review`（标记需重新评审）/ `no_impact`（确认无影响，记录 `reason` 必填）；处置只改变边的标记与状态，不修改下游内容；需要 `trace:edit` 权限。

---

## 4. 业务逻辑设计

### 4.1 建边与状态维护

- **建边时机**：生成链产物采纳落库时批量写入 `derivation` 边（`established_by = ai`、`status = ai_created`）；评审 / 计划创建生成快照时写入 `snapshot_ref` 边并记录圈选用例的 `target_version`。
- **写入事务**：建边与业务落库在同一事务内；任一失败整体回滚，保证"落库必有边、边必指向已落库节点"。
- **版本感知（stale）**：目标域内容版本变更事件（用例保存、快照同步）触发比对——`target_version` 与当前版本不一致的引用边置 `stale` 并回填新版本号到比对字段；人工在矩阵中「重新同步」或计划执行「同步最新用例」后回 `confirmed`。
- **冲突（conflict）**：覆盖分析或人工复核发现边语义错误时置 `conflict`；`conflict` 与 `detached` 的边不计入覆盖统计，但保留展示与审计。

### 4.2 覆盖分析流程

1. 通过 `POST /api/ai/tasks`（`type = coverage_analysis`，输入 `requirementIds`）发起后台任务；
2. 任务执行：按 `requirementId` 拉取派生边关联的用例集合 → 与需求正文语义比对 → 产出 `covered / partial / uncovered` + `evidence`；
3. 写入前逐行检查 `reviewed_by IS NULL`，人工已判定的行跳过写入；
4. 任务完成通知发起人，前端刷新 3.7 查询接口；结果同时供需求列表覆盖状态列读取。

### 4.3 影响分析与处置闭环

1. 需求模块在标题 / 描述 / 模块变更且状态转入「已变更」后，提交 `type = impact_analysis` 任务（输入 `requirementId`）；
2. 任务从需求正向遍历 `derivation` 边，再从命中的用例正向遍历 `snapshot_ref` 边，得到受影响项集合，逐项写入处置标记（`disposition = pending`）；
3. 用户在受影响项列表逐项处置（3.10），处置只写标记不改内容；
4. 需求重新确认后重新发起分析刷新标记：**未刷新前保留原标记并提示**（需求分册 1.6）；已人工处置为 `no_impact` 的项在刷新时保留处置结果（按 source+target 对匹配）。

### 4.4 权限与隔离

- 全部查询与写入强制附加 `project_id = X-Active-Project` 归属校验，越权返回 404（不泄露存在性）；
- 读权限复用追溯查看权限点，修正 / 处置需要 `trace:edit`；权限点清单见 6.2；
- 遍历、查询均排除 `is_deleted = TRUE` 与 `status = 'detached'` 的边。

---

## 5. 前端设计

### 5.1 路由与页面

```
项目工作区
└── /workspace/projects/trace    → TraceMatrixPage（追溯矩阵）
```

- 需求详情的「查看追溯」入口跳转 `TraceMatrixPage` 并以 `requirementId` 定位起点（只读链路模式，复用链路视图组件）。

### 5.2 组件结构

```
TraceMatrixPage
├── MatrixFilterBar（状态/覆盖/关键词筛选，角标显示条件数）
├── TraceMatrixTable（需求 × 边计数矩阵，覆盖状态徽标，stale/conflict 高亮）
├── TraceChainDrawer（链路视图抽屉：节点树 + 连线状态图例）
├── Edge修正弹窗（confirm / reattach / detach / restore，detach 须填 reason）
├── CoveragePanel（覆盖依据展示 + 人工修正表单）
├── GapList（缺口列表 + 「发起生成 / 发起评审 / 加入计划」引导）
└── ImpactDispositionPanel（受影响项列表 + 三类处置 + 理由录入）
```

### 5.3 状态管理与交互

- Pinia store `traceMatrix`：矩阵分页数据、当前链路缓存、缺口与受影响项；与 `aiTask` store（任务进度）联动——覆盖 / 影响分析任务完成后触发数据刷新。
- 任务驱动刷新：发起分析后轮询 `GET /api/ai/tasks/{id}`，`status = succeeded` 时刷新矩阵与覆盖数据，失败展示失败原因与重试入口。
- 全部状态分支：矩阵空态（无需求时引导「新建需求」）、链路空态（引导「发起 AI 生成」）、`stale / conflict` 图例、权限不足只读降级、任务进行中进度条。

---

## 6. 错误码定义

号段申请：**1000018151–1000018199（追溯矩阵）**，登记于 `docs/04-detailed-design/02-project-module.md` 6.3；以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018151 | 404 | 追溯边不存在 |
| 1000018152 | 400 | 源或目标节点不存在 |
| 1000018153 | 409 | 同一对节点已存在有效边 |
| 1000018154 | 409 | 当前边状态不允许该操作 |
| 1000018155 | 404 | 覆盖分析结论不存在 |
| 1000018156 | 400 | 覆盖分析任务参数非法 |
| 1000018157 | 400 | 影响分析任务参数非法 |
| 1000018158 | 404 | 受影响项不存在 |
| 1000018159 | 409 | 受影响项已处置 |
| 1000018160 | 400 | 不支持的节点类型 |
| 1000018161 | 403 | 无权限操作追溯数据 |

### 6.1 权限点（新增，业务侧 scope = workspace）

| code | 名称 | 说明 |
| ---- | ---- | ---- |
| trace:view | 查看追溯矩阵 | 矩阵 / 链路 / 覆盖 / 缺口 / 受影响项查询 |
| trace:edit | 编辑追溯 | 人工建边、确认 / 改挂 / 断开 / 恢复、覆盖修正、影响处置 |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../entity/trace/TraceEdge`、`TraceCoverageResult` + Mapper | 实体与数据访问（无物理外键） |
| `server/.../controller/project/TraceController` | 仅路由与参数绑定（C2） |
| `server/.../service/trace/TraceMatrixService + Impl` | 矩阵 / 链路 / 边维护 / 覆盖 / 影响逻辑 |
| `server/.../service/trace/TraceEdgeWriter` | 建边写入端口（供生成链与快照流程事务内调用） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018151–1000018161 |
| 权限点迁移脚本 | 新增 `trace:view` / `trace:edit`（scope = workspace） |
| `web/src/pages/project/TraceMatrixPage.vue` 及组件、`web/src/stores/traceMatrix.ts` | 前端页面与状态 |
| `web/src/services/trace.ts`、`web/src/types/trace.ts` | API 与类型 |

**数据库迁移说明（C5）**

```sql
-- 新建表（随本次交付；全量建库同步进 schema.sql）
-- 完整 DDL 见 2.2 / 2.3
CREATE TABLE trace_edge ( ... );
CREATE UNIQUE INDEX uk_trace_edge_pair ON trace_edge (...) WHERE is_deleted = FALSE;
CREATE INDEX idx_trace_edge_project / idx_trace_edge_source / idx_trace_edge_target ...;
CREATE TABLE trace_coverage_result ( ... );
CREATE UNIQUE INDEX uk_trace_coverage_requirement ON trace_coverage_result (...) WHERE is_deleted = FALSE;
```

- UUID 主键使用框架默认策略，不显式赋值；无物理外键；单表索引数 4 / 1，均 ≤ 5（C9）。

**OpenAPI**：接口随本次交付经 springdoc 暴露，路径分组 `trace`；矩阵与链路查询标注 `X-Active-Project` 必需头。

**测试要点（C8 ≥ 70%）**

- 后端：建边事务回滚、唯一约束冲突引导、断开后 AI 重建被拒、版本比对转 `stale`、人工结论不被 AI 覆盖、影响刷新保留 `no_impact` 处置、隔离越权 404；
- 前端：矩阵筛选与徽标渲染、链路抽屉连线状态、修正弹窗四类 action 分支、空态与权限降级。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-02 | 前端路由对齐全局导航约定，改为 /workspace/projects/trace |
