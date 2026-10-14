# 软件测试平台——AI 执行与功能测试环境管理

**文档版本**：V1.0  
**日期**：2026-10-14  
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

定义 AI 执行（UI 自动化测试）与功能测试环境管理的服务端详细设计：数据表与迁移、接口报文、业务逻辑、前端页面与实施说明，为开发提供可直接实现的依据。

### 1.2 范围与对应文档

- 对应概设 `docs/02-high-level-design/03-function-testing/03-hld-ai-ui-testing.md`、需求 `docs/01-requirements/03-function-testing/03-srs-ai-ui-testing.md`。
- **通道协议（服务端 ↔ 执行端帧与原因码）的单一事实源在 `docs/04-detailed-design/09-runner/02-runner-core.md`**，本文只定义服务侧对接与消费逻辑，不重复定义帧结构。
- AI 任务通用资源（提交、进度、取消、任务类型枚举、提示词场景、权限）以 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md` 为公共约定，本文只定义 `ui_execution` 类型的输入与业务校验。
- 计划与执行记录既有接口的改造点在本文 3.6 集中给出，其余计划接口见 `docs/04-detailed-design/03-function-testing/06-project-workspace-test-plan.md`。

### 1.3 定义与缩写

沿用概设 1.3 的术语（AI 执行批次、执行上下文、模块锚点、动作原语、观察、轨迹、凭据占位符、预置动作、步级预算、执行端），另补：

| 术语 | 定义 |
| ---- | ---- |
| 项目级预置动作 | 每项目一份的默认确定性动作序列，环境级非空时被覆盖 |
| 预算序号 `budgetSeq` | 服务端为每次动作下发分配的序号，与 Runner 回传对账 |

## 2. 数据设计

### 2.1 新增表

#### 2.1.1 功能测试环境表（ft_environment）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | PK | 框架默认策略 |
| project_id | uuid | NOT NULL | 所属项目 |
| name | varchar(100) | NOT NULL | 环境名称，项目内非重复 |
| description | varchar(500) | NULL | 描述 |
| is_default | boolean | NOT NULL, DEFAULT false | 项目内默认环境（发起弹窗回显优先级） |
| entry_urls | jsonb | NOT NULL, DEFAULT '[]' | `[{"name":"Web端","url":"https://..."}]`，构成 origin 白名单 |
| accounts | jsonb | NOT NULL, DEFAULT '[]' | `[{"username":"","alias":"","password":"<密文>","role":"","note":""}]` |
| data_state | text | NULL | 已知数据状态说明 |
| tips | text | NULL | 操作提示 / 已知问题 |
| initial_actions | jsonb | NOT NULL, DEFAULT '[]' | 环境级预置动作序列；**非空时覆盖项目级** |
| exec_params | jsonb | NOT NULL, DEFAULT '{}' | `{viewport:{width,height},actionTimeoutMs,allow_evaluate:true}` |
| created_at / updated_at / is_deleted | — | 公共字段 | C5 |

**索引**（1 个，C9）：`idx_ft_environment_project (project_id)`；名称查重经该索引定位后在服务层校验（逻辑删除参与去重）。

```sql
CREATE TABLE ft_environment (
    id              uuid PRIMARY KEY,
    project_id      uuid NOT NULL,
    name            varchar(100) NOT NULL,
    description     varchar(500) NULL,
    is_default      boolean NOT NULL DEFAULT FALSE,
    entry_urls      jsonb NOT NULL DEFAULT '[]',
    accounts        jsonb NOT NULL DEFAULT '[]',
    data_state      text NULL,
    tips            text NULL,
    initial_actions jsonb NOT NULL DEFAULT '[]',
    exec_params     jsonb NOT NULL DEFAULT '{}',
    created_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ft_environment_project ON ft_environment (project_id);
COMMENT ON TABLE ft_environment IS '功能测试环境（AI 执行世界模型与安全配置载体）';
COMMENT ON COLUMN ft_environment.accounts IS '账号列表，password 为密文，接口永不明文回显';
COMMENT ON COLUMN ft_environment.initial_actions IS '环境级预置动作，非空覆盖项目级 ft_preset_actions';
```

#### 2.1.2 项目级预置动作表（ft_preset_actions）

项目级配置**每项目一份**，用 JSON 序列兼容不同预置步骤（登录等），不做多模板（V1 无命名多流程需求）。

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | PK | 框架默认策略 |
| project_id | uuid | NOT NULL, UNIQUE | 项目内唯一（单行聚合） |
| steps | jsonb | NOT NULL, DEFAULT '[]' | 确定性原语序列（结构见 4.4） |
| created_at / updated_at / is_deleted | — | 公共字段 | C5 |

**索引**（1 个）：`uk_ft_preset_actions_project` UNIQUE (project_id) WHERE is_deleted = FALSE（逻辑删除不阻塞重建）。

```sql
CREATE TABLE ft_preset_actions (
    id         uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    steps      jsonb NOT NULL DEFAULT '[]',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ft_preset_actions_project ON ft_preset_actions (project_id) WHERE is_deleted = FALSE;
COMMENT ON TABLE ft_preset_actions IS '项目级默认预置动作（单行聚合，环境级 initial_actions 非空时覆盖）';
```

#### 2.1.3 动作轨迹表（ui_action_log）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | PK | 框架默认策略 |
| project_id | uuid | NOT NULL | 归属项目（隔离过滤） |
| task_id | uuid | NOT NULL | `ai_task.id`（批次） |
| plan_id | uuid | NOT NULL | 计划 |
| snapshot_node_id | uuid | NOT NULL | 用例快照节点 |
| phase | varchar(20) | NOT NULL | `precondition` / `step_N`（N 从 1 起）/ `verify` / `decompose` |
| seq | int | NOT NULL | 用例内序号（阶段内递增，跨阶段连续） |
| kind | varchar(20) | NOT NULL | `action` / `action_failed` / `verdict` / `note` |
| action | varchar(30) | NULL | 原语名（`action`/`action_failed` 必填） |
| target_ref | varchar(20) | NULL | 元素临时引用 |
| params | jsonb | NULL | 占位符原文；evaluate 即 JS 源码逐字留痕 |
| reason_code | varchar(30) | NULL | 失败原因码（枚举见 Runner 分册 3.2.4） |
| observation_digest | text | NULL | 观察摘要（元素树与语义摘要的压缩留痕） |
| screenshot_ref | varchar(200) | NULL | 文件域引用，截图本体在对象存储 |
| tokens_in / tokens_out | int | NULL | 该动作所在步的模型调用消耗 |

**索引**（2 个，C9）：`idx_ui_action_log_task (task_id)`、`idx_ui_action_log_case (snapshot_node_id, seq)`。

```sql
CREATE TABLE ui_action_log (
    id                 uuid PRIMARY KEY,
    project_id         uuid NOT NULL,
    task_id            uuid NOT NULL,
    plan_id            uuid NOT NULL,
    snapshot_node_id   uuid NOT NULL,
    phase              varchar(20) NOT NULL,
    seq                int NOT NULL,
    kind               varchar(20) NOT NULL,
    action             varchar(30) NULL,
    target_ref         varchar(20) NULL,
    params             jsonb NULL,
    reason_code        varchar(30) NULL,
    observation_digest text NULL,
    screenshot_ref     varchar(200) NULL,
    tokens_in          int NULL,
    tokens_out         int NULL,
    created_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted         boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ui_action_log_task ON ui_action_log (task_id);
CREATE INDEX idx_ui_action_log_case ON ui_action_log (snapshot_node_id, seq);
COMMENT ON TABLE ui_action_log IS 'AI 执行动作轨迹（按批次/用例/步骤归档，追加式）';
```

### 2.2 既有表改造

| 表 | 变更 | DDL |
| ---- | ---- | ---- |
| test_plan_execution_record | 新增来源维度 | `ALTER TABLE test_plan_execution_record ADD COLUMN source varchar(10) NOT NULL DEFAULT 'manual';`（既有行自动为 `manual`，**无需回填脚本**——已确认无存量 AI 数据） |
| test_plan_execution_record | 结果枚举扩展 | `result` 既有 `VARCHAR(20)`，直接接受 `skipped`，**无 DDL**；仅更新列注释为「pass/fail/block/untested/skipped」 |
| test_plan_node_snapshot | 最后结果扩展 | `last_result` 同为 `VARCHAR(20)`，接受 `skipped`，无 DDL；列注释同步 |
| test_plan | 执行环境语义 | `environment varchar(200)` **无 DDL**：语义从自由文本改为存 `ft_environment.id`（UUID 字符串）；展示时关联取名称，关联不上（环境已删）时展示「已删除环境」。已确认**无存量自由文本数据需转换**，不做回填 |

### 2.3 执行端实例表（ai_runner_instance）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | PK | 框架默认策略 |
| workspace_id | uuid | NOT NULL | 归属工作空间（隔离过滤） |
| runner_id | varchar(64) | NOT NULL, UNIQUE | 实例标识（`register.ack` 下发，重连保持不变） |
| mode | varchar(20) | NOT NULL | `desktop-hosted` / `cli` |
| owner_id | uuid | NULL | 绑定用户；`cli` 为 NULL（共享） |
| status | varchar(10) | NOT NULL, DEFAULT 'offline' | `online` / `offline`；`busy` 为派生态（关联进行中任务） |
| protocol_version | varchar(10) | NOT NULL | 注册时上报 |
| last_heartbeat_at | timestamp | NULL | 最后心跳，失活判定依据 |
| created_at / updated_at / is_deleted | — | 公共字段 | C5 |

**索引**（2 个，C9）：`uk_ai_runner_instance_runner` UNIQUE (runner_id)；`idx_ai_runner_instance_owner (owner_id, status)`（分派查询：在线 + 归属）。

```sql
CREATE TABLE ai_runner_instance (
    id                uuid PRIMARY KEY,
    workspace_id      uuid NOT NULL,
    runner_id         varchar(64) NOT NULL,
    mode              varchar(20) NOT NULL,
    owner_id          uuid NULL,
    status            varchar(10) NOT NULL DEFAULT 'offline',
    protocol_version  varchar(10) NOT NULL,
    last_heartbeat_at timestamp NULL,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_runner_instance_runner ON ai_runner_instance (runner_id);
CREATE INDEX idx_ai_runner_instance_owner ON ai_runner_instance (owner_id, status);
COMMENT ON TABLE ai_runner_instance IS 'AI 执行端实例注册表（cli 共享优先分派 / desktop-hosted 用户绑定兜底）';
COMMENT ON COLUMN ai_runner_instance.owner_id IS 'desktop-hosted 绑定用户；cli 为 NULL（任意用户可用）';
```

同步更新 `server/src/main/resources/db/schema.sql` 基线（新增表至此共四张：`ft_environment`、`ft_preset_actions`、`ui_action_log`、`ai_runner_instance`），迁移以初始化脚本为基线（仓库尚无版本化迁移目录，数据库规范 5.1 口径）。

实例生命周期：`register` 校验通过 upsert（按 `runner_id`）并置 `online`；心跳刷新 `last_heartbeat_at`；断连或 60s 无心跳由心跳清扫置 `offline`（与批次失活落存口径同源，见 4.1）。

### 2.4 数据隔离与生命周期

- 四张新表经 `project_id` / `workspace_id` 归属项目或工作空间，查询层自动附加上下文过滤（沿用数据与接口设计分册 1.3）。
- `ui_action_log` 为追加式，批次结束后只读；计划删除时级联删除轨迹（事务显式处理，无物理外键）；截图对象随轨迹清理任务回收。

## 3. 接口详细设计

响应均为 `Result<T>`，示例只展示 `data`；上下文经 `X-Active-Project` / `X-Active-Workspace` 请求头传递（C4），不出现在 URL。接口按域分组：环境管理 `/api/project/ft-environments`，AI 执行复用 `/api/ai/tasks` 统一任务资源（API 规范 2.1：不新增平行基础路径）。

### 3.1 环境列表

- **路径**：`GET /api/project/ft-environments`（权限：`funcenv:view`）
- **响应**：

```json
{
  "list": [
    {
      "id": "0b1f…",
      "name": "staging",
      "isDefault": true,
      "description": "预发环境",
      "entryCount": 2,
      "accountCount": 3,
      "hasCustomActions": false,
      "updatedAt": "2026-10-14T03:00:00Z"
    }
  ],
  "total": 3
}
```

- **口径**：`hasCustomActions` = 环境级 `initial_actions` 非空（前端据此显示「使用项目默认 / 已覆盖」）；密码与入口/账号明细不在列表返回。

### 3.2 环境详情

- **路径**：`GET /api/project/ft-environments/:id`（权限：`funcenv:view`）
- **响应**：`{ "id", "name", "description", "isDefault", "entryUrls": [{"name","url"}], "accounts": [{"username","alias","role","note","passwordSet": true}], "dataState", "tips", "initialActions": [...], "execParams": {"viewport": {...}, "actionTimeoutMs": 10000, "allowEvaluate": true}, "createdAt", "updatedAt" }`
- **口径**：**密码永不出现在任何响应**——账号只回 `passwordSet` 布尔（掩码展示由前端据此渲染 ●●●●●）；`initialActions` 为环境级原值（空数组即未覆盖，不合并项目级）。

### 3.3 创建环境

- **路径**：`POST /api/project/ft-environments`（权限：`funcenv:edit`）
- **请求体**：

```json
{
  "name": "staging",
  "description": "预发环境",
  "entryUrls": [{ "name": "Web端", "url": "https://app.staging.example.com" }],
  "accounts": [{ "username": "admin", "alias": "管理员", "password": "<明文，仅此一次>", "role": "admin", "note": "" }],
  "dataState": "订单表已清空",
  "tips": "新建按钮在列表右上",
  "initialActions": [],
  "execParams": { "viewport": { "width": 1440, "height": 900 }, "actionTimeoutMs": 10000, "allowEvaluate": true }
}
```

- **校验**：`name` 非空且项目内非重复（重复 → 1000011031）；`entryUrls[].url` 须为 http(s) 且同条目内 origin 合法（非法 → 1000011032）；`accounts[].password` 创建时必填（明文仅入站一次，立即加密落库）；`initialActions` 结构校验（见 4.4，失败 → 1000011033）；`execParams.allowEvaluate` 缺省 `true`。
- **处理**：写入后 `is_default` 若为 true 则同项目其余环境置 false（项目内单默认）。
- **响应**：201，结构同 3.2。

### 3.4 更新环境

- **路径**：`PUT /api/project/ft-environments/:id`（权限：`funcenv:edit`）
- **口径**：部分更新（C11）——未传字段不改；`accounts` 中 `password` 未传（`passwordSet=true` 的既有账号）保持原密文，**传空字符串视为非法**（1000011034）；`isDefault=true` 时同项目互斥置 false。
- **删除保护**：无（环境删除不影响已发起批次——批次持冻结快照；见 4.2 取消与失败口径）。

### 3.5 删除与设默认

- **删除**：`DELETE /api/project/ft-environments/:id`（`funcenv:edit`）——逻辑删除；**被计划引用不阻止删除**（计划侧展示降级为「已删除环境」，概设 3.3 改动清单 #1 口径）；正在执行中的批次不受影响。
- **设默认**：`PUT /api/project/ft-environments/:id/default`（`funcenv:edit`）——项目内单默认互斥更新。

### 3.6 项目级预置动作

- **查询**：`GET /api/project/ft-preset-actions`（`funcenv:view`）→ `{ "steps": [...], "updatedAt" }`（无行返回空数组）。
- **保存**：`PUT /api/project/ft-preset-actions`（`funcenv:edit`）——全量覆盖式，`steps` 结构校验（4.4，失败 1000011033）。
- **解析优先级**（服务端在 `session.start` 组装时执行）：环境级 `initial_actions` 非空 → 环境级；否则项目级 `steps`；两级皆空 → 不下发 `initial_actions.execute`，登录由 AI 自主决策（概设 4.9 三档兜底）。

### 3.7 发起前置信息

- **路径**：`GET /api/project/plans/:id/ai-exec/precheck`（权限：`ai:exec` + 计划可见）
- **响应**：

```json
{
  "runnable": true,
  "blockReason": null,
  "models": [{ "id": "…", "name": "gpt-…", "isDefault": true }],
  "environments": [{ "id": "…", "name": "staging", "isDefault": true }],
  "boundEnvironmentId": "…",
  "boundEnvironmentName": "staging",
  "totalAssociated": 12,
  "cases": [{ "id": "…", "title": "销售订单新建-正常流程", "priority": "P1", "documentId": "…" }],
  "params": { "stepActionLimit": 50, "stepActionLimitMax": 200 },
  "runner": { "available": true, "preferred": "cli", "count": 2 },
  "progress": { "runningTaskId": null }
}
```

- **校验顺序**（`runnable=false` 时给 `blockReason`）：AI 总开关与模型可用性（沿总册，复用 `GET /api/ai/status` 口径）→ 计划状态（blocked/completed/closed → `plan_not_allowed`，1000011035）→ 同计划存在进行中批次（`runningTaskId` 非空 → `duplicate_running`，前端提示不拦截，沿总册重复发起提示口径）→ 权限。
- **说明**：`boundEnvironment*` 为计划绑定环境回显（3.9 改造）；未绑定或环境已删则为 null，发起弹窗默认选项目默认环境。`runner` 为执行端可用性快照（与 4.1 发起前置校验同规则）：`available=false` 时 `blockReason` 增加 `no_runner`（提示「无可用执行端，启动桌面版或联系运维部署」，桌面版拉起本机托管实例或运维部署共享实例后可消除）；`preferred` 为按分派规则将命中的实例模式（`cli` 共享优先 / `desktop-hosted` 专属兜底），供前端提示「将由共享执行端执行 / 将在本机执行（桌面版）」。

### 3.8 发起批次（提交任务）

- **路径**：`POST /api/ai/tasks`（复用统一任务资源，权限：`ai:exec` + 计划上下文）
- **请求体**：

```json
{
  "type": "ui_execution",
  "input": {
    "planId": "…",
    "caseIds": [],
    "environmentId": "…",
    "modelId": "…",
    "stepActionLimit": 50
  },
  "waitSeconds": 0
}
```

- **input 校验**（按 `ui_execution` 分发，AI 底座 3.6.2 的 type→input 校验点）：`planId` 必填且存在属于当前项目（1000011010 复用）；`caseIds` 空数组 = 计划关联全部用例，非空时须为本计划关联的快照节点（否则 1000011036）；`environmentId` 必填且属于当前项目（1000011037）；`modelId` 缺省取系统默认；`stepActionLimit ∈ [1, 200]`，缺省 50（1000011038）。
- **处理**：组装冻结执行上下文（用例正文读脑图实时内容、模块锚点按「用例节点 → 所属文档 → 文档关联模块」解析、环境内容取环境快照、预算入参）写入 `ai_task.input`；`type=ui_execution`、`prompt_scene=ui_execution_decision`；`status=pending`，提交即返回。
- **响应**：AI 底座 3.6.2 同构（`taskId/status/progress/...`）。
- **进度 / 取消**：完全复用 `GET /api/ai/tasks/{taskId}`、SSE 进度通道与 `POST /api/ai/tasks/{taskId}/cancel`（AI 底座 3.6.3/3.6.4），不另设接口；进度 `phase` 语义为「第 N/M 条用例 · 步骤 x/y · 已 a 动作」（概设 4.1）。

### 3.9 计划接口改造（既有接口变更点）

| 接口 | 变更 |
| ---- | ---- |
| `POST /api/project/plans`（创建）与 `PUT …/:id` | `environment` 请求字段改为 `environmentId`（UUID，须属于当前项目，否则 1000011037）；可空 |
| `GET …/:id`（详情）、`GET …`（列表） | 响应 `environment` 改为 `{ "environmentId", "environmentName" }`；关联不上时 `environmentName=null` |
| `GET …/:id/progress` | 响应新增 `"skipped": 0`；`last_result ∈ {pass, fail, block, untested, skipped}`，`skipped` 计入已执行（`progressPercent` 分母不变、分子含 skipped），**不计入 passed** |
| `POST …/:id/records`（提交执行记录） | `result` 取值新增 `skipped`，但**手动来源拒绝 `skipped`**（1000011039，「跳过仅由 AI 执行产生」）；AI 回写经服务内部通道写入并置 `source='ai'`；人工改判写入置 `source='manual'` |

### 3.10 轨迹查询

- **路径**：`GET /api/ai/tasks/{taskId}/action-logs`（权限：`ai:exec` + 任务归属校验）
- **参数**：`snapshotNodeId`（可选，按用例过滤）、`pageNo`、`pageSize`（默认 100，轨迹为追加流，按 `seq` 升序）。
- **响应**：

```json
{
  "list": [
    {
      "id": "…", "phase": "step_2", "seq": 5, "kind": "action_failed",
      "action": "click", "targetRef": "e12",
      "params": null, "reasonCode": "covered",
      "observationDigest": "「新建」被遮罩层覆盖",
      "screenshotRef": null, "tokensIn": 0, "tokensOut": 0,
      "createdAt": "2026-10-14T03:01:12Z"
    }
  ],
  "total": 143
}
```

- **口径**：`params` 中密码为占位符原文、`evaluate` 为 JS 源码逐字（概设 4.7 护栏 1）；批次汇总（pass/fail/blocked/skipped 计数）取自任务 `result`，不另设接口。

### 3.11 错误码（10 位，号段 1000011031–1000011060，登记 `docs/04-detailed-design/02-project-module.md` 6.3）

| 错误码 | 说明 | 触发点 |
| ---- | ---- | ---- |
| 1000011031 | 环境名称已存在 | 3.3 |
| 1000011032 | 入口 URL 不合法 | 3.3/3.4 |
| 1000011033 | 预置动作结构不合法 | 3.3/3.4/3.6 |
| 1000011034 | 账号密码不能为空 | 3.4 |
| 1000011035 | 当前计划状态不可发起 AI 执行 | 3.7 |
| 1000011036 | 用例不属于该计划 | 3.8 |
| 1000011037 | 执行环境不存在或不属于当前项目 | 3.8/3.9 |
| 1000011038 | 步级动作上限超出范围 | 3.8 |
| 1000011039 | 跳过仅由 AI 执行产生 | 3.9 |
| 1000011040 | 环境不存在 | 3.2/3.4/3.5 |
| 1000011041 | 条目被预置动作引用，不能删除 | 4.4 / 5.1 |
| 1000011042 | 无可用执行端 | 4.1 |

## 4. 业务逻辑设计

### 4.1 通道服务（服务端侧）

- 服务端监听 Runner 连接（协议与帧见 Runner 分册 3，此处只写服务侧动作）：
  - `register`：校验 `ownerToken`（桌面版登录会话签发，验签后置 `owner_id`）或 `sharedKey`（CLI 注册密钥，`owner_id` 置空），按 `runner_id` upsert 执行端实例（2.3）并回 `register.ack`；失败断开；
  - **执行端分派规则**（批次 `session.start` 前选择实例，按序取第一个满足项）：
    1. 任一在线 `cli` 共享实例（**共享优先**，按 `last_heartbeat_at` 最新优先，V1 单实例即固定命中）；
    2. 提交人本人的 `desktop-hosted` 在线实例（专属兜底——无在线共享实例时本机执行）；
    3. 皆无 → 任务失败落存（错误码 1000011042「无可用执行端」，`error_msg` 引导「启动桌面版或联系运维部署执行端」），不进入执行循环、不烧 AI 预算；
  - **发起前置校验（后端强制，`POST /api/ai/tasks` 提交时）**：校验「存在在线共享实例或提交人本人的在线专属实例」，皆无则直接拒绝（1000011042）；Web 版与桌面版一视同仁——发起入口双端渲染（5.3），入口显隐只是前端呈现策略，执行端可用性以本校验为准；precheck（3.7）提供同一规则的只读快照供前端提示与按钮禁用，但不替代本校验；
  - `session.start` 组装：**凭据解密**——服务端用框架加密工具解密 `accounts.password`，构造 `账号别名 → 明文` 映射明文经 WSS 下发（已裁决口径），帧不落日志、异常堆栈不携带凭据；`originWhitelist` = 所选环境 `entry_urls` 全部 origin 并集；`execParams` 取环境配置；
  - **归属校验**：分派仅向目标实例的连接发送 `session.start`；`desktop-hosted` 实例绑定 `owner_id`，服务端只分派该用户批次（Runner 侧二次校验 `OWNER_MISMATCH` 兜底）；`cli` 实例接受任意用户批次；同一实例同一时刻仅一个进行中批次（`busy` 派生态防并发）；
  - `action.execute` 分发前完成**白名单与枚举二次校验**（15 执行原语，`done` 为服务端语义原语不下发，Runner 分册 4.1）、`budgetSeq` 分配与预算计数（**计数在服务端**，Runner 不决策预算）；
  - `action.result`：成功 → 轨迹落 `action`；失败 → 轨迹落 `action_failed` + 原因码，交步进决策循环决定重试/换路/止损；
  - 心跳 60s 缺失 → 实例置 `offline` 摘除出分派池；会话中失活按任务失败落存口径（总册 1.5）写过程补充记录。

### 4.2 批次执行主循环（步进式）

实现形态：新 UI 执行任务处理器挂入既有 `TaskHandler` 注册机制（进度上报 / SSE / 超时 / `AiTaskSweeper` 失活清扫全复用），单任务内驱动：

1. **组装冻结**：逐条目标用例读脑图实时内容 → 冻结上下文写 `ai_task.input`（中途编辑不漂移）；解析模块锚点（文档未关联模块则为空）。
2. **预置动作**：按 3.6 优先级解析 → 下发 `initial_actions.execute` → 失败即批次 blocked（`error_msg` 写明第几步与原因码，不烧 AI 预算）。
3. **用例级循环**（`sort_order`）：前置阶段 → 逐步动作循环 → 验证阶段 → 判定回写 → 止损检查（连续 3 条 blocked 停批，`phase` 标注原因）。
4. **动作循环**：组包（冻结上下文 + 当前步骤 + 轨迹历史 + 最新观察，含 80% 收敛注入与同动作 3 败头铁注入）→ 单次模型调用（`AiModelClient` 门面，场景 `ui_execution_decision`）→ 解析模型输出为「下一动作」或「步骤完成」→ 下发 → 等 `action.result` → 预算/取消检查（每动作一次，`budgetSeq` 对账）。
5. **验证阶段**：`observe(reason=verify)` 取证（强制截图）→ 组包判定场景（`ui_execution_verdict`，多模态单图）→ 四值输出 → 证据与结论写执行记录 `note`（无证据不落库）。
6. **回写**：复用既有 `submitExecutionRecord` 内部链路，`result` 取 pass/fail/block/skipped、`source='ai'`、`executor_id`=批次提交人；同步快照 `last_result`；进度统计经既有聚合自动生效（skipped 分支见 3.9）。
7. **批次收尾**：`result` 写 `{counts:{pass,fail,blocked,skipped}, caseResults:[…]}`；失败/取消按总册 1.5 落过程补充记录（中断阶段、已处理用例、已写记录清单、token 消耗、原因），已写判定不回滚。
- **取消**：`POST /api/ai/tasks/{taskId}/cancel` 置取消标记 → 主循环在当前 `action.result` 返回后退出 → 下发 `session.end` → 已写结果保留。
- **总时长 2h**：任务处理器 `enforce`，超时同失败落存口径。

### 4.3 判定与改判

- 四值语义、证据强制与汇总规则按概设 4.6 实现；`note` 固定格式：`[AI判定] 结论；证据：<文字>（截图：<screenshot_ref>）`。
- 人工改判走既有 `POST /api/project/plans/:id/records`，写入 `source='manual'`；进度与通过率自动重算。
- 节点状态图标五态与 skipped 统计分支在进度聚合查询中实现（`last_result='skipped'` → 已执行计数 +1、passed 不加）。

### 4.4 预置动作结构与校验（两级共用）

```json
[
  { "action": "navigate", "params": { "entryName": "Web端" } },
  { "action": "fill", "params": { "hint": "placeholder=用户名", "text": "admin" } },
  { "action": "fill", "params": { "hint": "placeholder=密码", "credentialAlias": "admin" } },
  { "action": "press", "params": { "key": "Enter" } },
  { "action": "wait", "params": { "seconds": 3 } }
]
```

- **允许原语子集**：`navigate / click / fill / press / select / wait / find_text`（禁 `evaluate` 与其余原语，违反 → 1000011033）。
- **引用式参数**（解决"3 个环境配 3 遍"与环境切换，已裁决）：`entryName` 引用本环境入口条目名、`credentialAlias` 引用本环境账号别名（明文不入库不入编辑器）；**保存时**校验引用存在于目标环境（项目级保存时校验存在于项目默认环境或至少一个环境，标注告警）；**执行时**由服务端按当前环境解析，解析失败 → 批次 blocked 写明步骤。
- **规模校验**：步数 ≤20（超出 1000011033）。
- **删除联动**：删除入口条目 / 账号前检查预置动作引用，被引用则拒绝（1000011041「条目被预置动作引用，请先修改」）；环境级检查作用于本环境 `initial_actions`，项目级检查作用于项目级 `steps`。

### 4.5 提示词场景与模型

- 注册两场景：`ui_execution_decision`（动作纪律：只发原语、evaluate 最后手段、模块锚点优先、引用定位、收敛注入）、`ui_execution_verdict`（判定纪律：证实才通过、证据不足 blocked、四值枚举输出）；场景登记入 `ai_prompt_template.scene`（AI 底座 2.5 联动）。
- 模型解析：`input.modelId` 缺省取系统默认模型；失败重试复用同模型（AI 底座任务口径）。
- 用量记账：每步调用按场景归因写 `ai_usage_log`（`prompt_scene` 分别为两场景，任务级 `prompt_scene` 记 decision）。

### 4.6 预算与止损实现

| 项 | 实现 |
| ---- | --- |
| 步级动作上限 | 决策循环内计数，达上限 → 步 blocked → 用例 blocked；80% 时组包注入收敛提示 |
| 同动作 3 败 | 轨迹内滑动窗口计数，达阈值注入头铁提示 |
| 批次 2h | 任务处理器 enforce |
| 连续 3 条 blocked | 用例级循环检查，停批并 `error_msg` 标注 |
| 取消 / 失活 | 见 4.2；失活清扫对进度上报活跃任务不误杀（沿用既有机制） |

## 5. 前端设计

### 5.1 功能测试环境管理页

**路由**：`/workspace/projects/functional-testing?tab=ft-environments`（挂功能测试页子菜单「测试环境」，权限 `funcenv:view`；菜单项显隐同权限码）。**双端均可用**：环境管理属配置能力而非执行入口，桌面版与 Web 版一致渲染。

**结构**（ASCII 示意）：

```
┌─────────────────────────────────────────────────────────────┐
│ 测试环境                          [+ 新建] [复制所选环境]      │
│ AI 执行的入口、账号与预置动作配置                              │
├─────────────────────────────────────────────────────────────┤
│ 预置动作（项目默认）：5 步 · [编辑]                            │
├──────────────┬──────────────────────────────────────────────┤
│ 环境列表      │ 表单区：基本信息 / 入口 URL / 账号 /           │
│ ● staging(默认)│ 已知数据状态 / 操作提示 / 执行参数             │
│   dev         │ 预置动作：( )使用项目默认 (•)自定义覆盖         │
│   prod        │                              [保存][删除]    │
└──────────────┴──────────────────────────────────────────────┘
```

**交互与状态**：

| 操作 | 行为 |
| ---- | --- |
| 新建 | 空表单；账号密码保存后仅回 `passwordSet`，编辑时留空=不改 |
| **复制** | 选中源环境 → [复制所选环境]：读既有详情填充**新建表单**（name 清空必填、`isDefault` 重置否、entryUrls/dataState/tips/initialActions/execParams 带入、accounts 带入用户名/别名/角色/备注**密码全部留空**、id 类字段不带）→ 用户改名补密码后**走既有创建接口**（纯前端行为，无专用复制接口） |
| 预置动作编辑 | 见 5.2 |
| 删除 | 二次确认；执行中批次不受影响，被引用条目删除由服务端 1000011041 拦截并提示 |
| 空态/加载/错误 | 列表 loading、空态「暂无环境，点击[新建]」、操作失败消息提示；无 `funcenv:edit` 权限时表单只读 |

### 5.2 预置动作编辑器（方案 ② 表单化步骤编辑器）

- **两级编辑入口**：页面顶部「预置动作（项目默认）」区块编辑项目级；环境表单内「使用项目默认 / 自定义覆盖」单选，选覆盖才展开同一编辑器编辑环境级。
- **形态**：步骤列表，每行 = 原语下拉（`navigate/click/fill/press/select/wait/find_text`）+ 按原语渲染的参数表单 + 上移/下移/删除；[+ 添加步骤] 追加。
- **参数表单**：
  - `navigate`：地址 = **下拉选择本环境入口条目**（存 `entryName`，不填字面 URL）；
  - `fill`：定位 = 文本输入（`hint`）；内容 = 普通文本 或 **账号下拉**（选中存 `credentialAlias`，明文不出现）；
  - `click/select`：定位 = 文本输入；`wait`：秒数（≤10）；`press`：键名下拉；`find_text`：文本输入。
- **校验（保存前前端即拦）**：原语与参数必填、步数 ≤20、引用条目存在；服务端 3.6 同步双校验（1000011033），错误定位到具体步骤序号。
- **数据来源**：下拉选项即当前页面已加载的入口/账号子表数据，零额外请求。

### 5.3 发起确认弹窗（计划详情页，双端一致）

**客户端形态判别**：平台为双形态——桌面版（Electron 壳包裹现有 `web/` 前端）与 Web 版，同一前端代码按运行环境判别形态（`renderer` 进程存在 Electron 全局桥即桌面版）。**[🤖 AI 执行] 按钮与本弹窗双端一致渲染**；批次进度条与执行详情（5.4）同样双端可见。发起的强制约束在服务端：**执行端可用性由 `POST /api/ai/tasks` 提交时后端校验**（4.1 发起前置校验，无在线共享或本人专属实例则拒绝 1000011042），前端显隐与禁用只是呈现策略、非安全边界；precheck（3.7）的 `runner` 快照供前端提前禁用按钮与提示，`no_runner` 分支双端行为一致。

**入口**：计划详情页操作组新增 [🤖 AI 执行]（活跃态 new/in_progress 显示；入口显隐权限 `ai:exec`，前置状态由 3.7 `runnable/blockReason` 决定，总开关关闭时按钮禁用并提示沿总册）。

**弹窗结构**：

```
┌─ AI 执行 ────────────────────────────────┐
│ 模型    [系统默认模型 ▼]                  │
│ 用例范围 [✓]全选（已选 12/12）  树形勾选   │
│ 执行环境 [staging ▼]（回显计划绑定，可改）  │
│ 步级动作上限 [50]（1–200）                │
│ 执行端提示：将由共享执行端执行（灰字）      │
│                        [取消] [开始执行]  │
└──────────────────────────────────────────┘
```

- 用例范围不勾选 = 全部（提交 `caseIds:[]`）；勾选态按 `precheck.cases` 树形展示（按文档分组）。
- 执行端提示：按 `precheck.runner.preferred` 一行灰字（`cli` →「将由共享执行端执行」/ `desktop-hosted` →「将在本机执行（桌面版）」）；`available=false`（`no_runner`）时红色提示条「无可用执行端」+ [重试]，[开始执行] 禁用（双端同规则）。
- 提交成功 → 关弹窗 → 页头出现进行中批次条（进度 SSE，见 5.4）；`blockReason=duplicate_running` 时提示「该计划已有进行中的批次」仍允许查看进行中批次；`plan_not_allowed` 时入口不出现。
- 加载失败/提交失败消息提示；`runnable=false` 原因文案映射。

### 5.4 执行中呈现与执行详情（双端一致）

- **执行中**：复用 AI 任务既有 SSE 进度（AI 底座 3.6）显示「3/12 · 当前用例 · 步骤 2/5 · 已 15 动作」，[取消] 走既有任务取消（二次确认）；批次条随任务状态流转（进行中/已完成/已失败/已取消）。取消权限与发起一致（`ai:exec`），批次条与取消按钮双端一致渲染。
- **执行详情**：复用任务详情页，`ui_execution` 类型渲染：
  - **轨迹列表**（按用例 → 阶段分组的时间线）：`kind=action` 行显示「动作 + 目标 + 摘要」，`action_failed` 红色标原因码中文映射，`verdict` 行显示四值徽标与证据文字，`note` 行灰字；截图引用渲染缩略图点开大图；`evaluate` 的 `params` 源码等宽展示逐字可查；密码位置显示占位符原文。
  - **批次汇总**：pass/fail/blocked/skipped 四色计数。
  - **改判**：跳转计划详情既有执行记录入口（无新代码）。
- **状态分支**：轨迹 loading/空态（未产生轨迹）/加载失败消息；任务失败时展示过程补充记录（总册 1.5 任务详情口径）。
- **通知**：V1 不做（总册例外口径），状态在页面可见。

### 5.5 计划详情页与列表改造

- 页头统计由「通过/失败/阻塞/待执行/共 N」扩为「通过/失败/阻塞/**跳过**/待执行/共 N」（消费 3.9 progress 新增字段）；节点图标五态（`skipped` 新增徽标样式）。
- 手动标记工具栏仍为四态按钮（不提供跳过）；服务端 3.9 双保险拒绝。
- 列表与详情的 `environment` 列改渲染 `environmentName`（null 显示「已删除环境」或 `—`）。
- 创建计划表单「执行环境」由自由文本改为**环境下拉**（数据源 `GET /api/project/ft-environments`，无环境时提示先建并给跳转）。

## 6. 实施说明

### 6.1 权限点与迁移

| 权限码 | 作用域 | 说明 |
| ---- | --- | --- |
| `funcenv:view` | project | 环境与项目级预置动作查询 |
| `funcenv:edit` | project | 环境与项目级预置动作维护（限项目管理角色） |
| `ai:exec` | workspace | 发起 AI 执行批次（与既有 `ai:task` 并列，发起同时校验两者） |

权限点迁移脚本（角色权限表登记、菜单显隐）随实现落地；总览附录 A 角色权限矩阵待权限码定稿后补登记（既有遗留项，本册不重复处理）。

### 6.2 数据库变更清单

1. 新增 `ft_environment`、`ft_preset_actions`、`ui_action_log`、`ai_runner_instance`（2.1、2.3，含索引）；
2. `test_plan_execution_record` 加 `source` 列（2.2）；
3. 更新 `schema.sql` 两处列注释（`result`、`last_result`）与新增表；
4. **无数据回填**（已确认无存量自由文本环境与 AI 数据）；
5. 回滚：`DROP` 新表 + `DROP COLUMN source`（新功能未上线即回滚，无业务数据损失）；`environment` 语义回滚仅涉及读写口径，列未变。

### 6.3 错误码与契约

- `ErrorCodeConstants` 登记 1000011031–1000011042（3.11），号段 1000011031–1000011060 已同步 `docs/04-detailed-design/02-project-module.md` 6.3 号段表。
- OpenAPI：新接口（3.1–3.8、3.10）与改造接口（3.9）均补注解与模型，`pnpm run contract:gen` 重新生成基线；前端类型由基线生成。
- 任务类型 `ui_execution` 与两提示词场景登记 AI 底座详设（单一事实源联动）。

### 6.4 依赖与组件

- 服务端无新增依赖（复用 Spring AI 门面、任务引擎、文件域）。
- 新增独立组件 `runner/`（Playwright），见 `docs/04-detailed-design/09-runner/02-runner-core.md` 6.1；M1 过会项。
- 前端无新依赖（表单化步骤编辑器用 Element Plus 组件拼装）。
- **桌面版**：Electron 壳包裹现有 `web/` 构建产物，一套代码双形态；新增顶层目录 `desktop/`（壳工程、打包配置与 Runner 子进程托管），引入 `electron` / `electron-builder` 依赖——过会项；形态判别与渲染差异见 5.3。**桌面版托管 Runner 子进程**（`desktop-hosted` 模式，随登录注册为本人专属实例、共享实例缺失时兜底分派，见 4.1；生命周期与注册协议见 Runner 分册 5）；桌面版与 Runner 之间**只经服务端 WSS 通道交互，渲染进程不直连 Runner**，决策面仍在服务端。

### 6.5 开发批次建议

数据与环境管理（2.1、3.1–3.6、5.1–5.2）→ 任务接入与主循环（3.7–3.8、4.1–4.6）→ 轨迹与统计改造（3.9–3.10、5.3–5.5）→ Runner 联调（协议 3 与 Runner 分册）。每批以对应接口的契约测试锁定行为。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-14 | 初始版本 |
| V1.0 | 2026-10-14 | 明确客户端形态约束：AI 执行发起入口与弹窗仅桌面版（Electron 壳）渲染、批次查看双端一致（5.1、5.3、5.4）、桌面版组件登记（6.4） |
| V1.0 | 2026-10-14 | 桌面版集成执行端：新增执行端实例表（2.3）与注册 / 分派规则（4.1，desktop-hosted 用户绑定优先、cli 共享），precheck 补执行端可用性（3.7），错误码补 1000011042，桌面版托管口径更新（6.4） |
| V1.0 | 2026-10-14 | 分派顺序反转为共享实例优先、专属实例兜底（4.1、3.7）；发起入口改为双端一致渲染，`POST /api/ai/tasks` 后端强制校验执行端可用性（4.1、5.3）；取消按钮双端一致（5.4） |
