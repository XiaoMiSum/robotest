# 软件测试平台——AI 执行与功能测试环境管理

**文档版本**：V1.0  
**日期**：2026-11-27  
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

定义 AI 执行（UI 自动化测试）与功能测试环境管理的服务端详细设计：数据表与迁移、接口报文、业务逻辑、前端页面与实施说明，为开发提供可直接实现的依据。

### 1.2 范围与对应文档

- 对应概设 `docs/02-high-level-design/03-function-testing/03-hld-ai-ui-testing.md`、需求 `docs/01-requirements/03-function-testing/03-srs-ai-ui-testing.md`。
- **通道协议（服务端 ↔ 执行端帧与原因码）的单一事实源在 `docs/04-detailed-design/09-runner/02-runner-core.md`**，本文只定义服务侧对接与消费逻辑，不重复定义帧结构。
- AI 任务通用资源（任务 · 执行 · 轨迹 · 提案双平面，含提交、进度、终止、任务类型枚举与提示词场景）以 `docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md` 为公共约定，双平面机制见 `docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`；本文只定义 `ui_execution` 类型的输入与业务校验，不新增提案类型。
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
| name | varchar(100) | NOT NULL | 环境名称，项目内非重复（忽略大小写） |
| description | varchar(500) | NULL | 描述 |
| is_default | boolean | NOT NULL, DEFAULT false | 项目内默认环境（发起弹窗回显优先级，项目内唯一） |
| entry_urls | jsonb | NOT NULL, DEFAULT '[]' | `[{"name":"Web端","url":"https://..."}]`，构成 origin 白名单；`name` 环境内唯一 |
| accounts | jsonb | NOT NULL, DEFAULT '[]' | `[{"username":"","alias":"","password":"<密文>","role":"","note":""}]`；`alias` 环境内唯一 |
| data_state | text | NULL | 已知数据状态说明 |
| tips | text | NULL | 操作提示 / 已知问题 |
| preset_mode | varchar(20) | NOT NULL, DEFAULT 'inherit' | `inherit` / `override` / `disabled`；三态预置动作 |
| initial_actions | jsonb | NOT NULL, DEFAULT '[]' | 环境级预置动作序列；仅 `preset_mode='override'` 时生效 |
| exec_params | jsonb | NOT NULL, DEFAULT '{}' | `{viewport:{width,height},actionTimeoutMs,allowEvaluate:false,readOnly:false}` |
| created_at / updated_at / is_deleted | — | 公共字段 | C5 |

**索引**（3 个，C9）：`idx_ft_environment_project (project_id)`（列表与隔离过滤）；`uk_ft_environment_project_name` UNIQUE `(project_id, lower(name)) WHERE is_deleted = FALSE`（名称去重由数据库兜底）；`uk_ft_environment_project_default` UNIQUE `(project_id) WHERE is_default AND is_deleted = FALSE`（项目内单默认，防并发穿透）。逻辑删除不阻塞重建。

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
    preset_mode     varchar(20) NOT NULL DEFAULT 'inherit',
    initial_actions jsonb NOT NULL DEFAULT '[]',
    exec_params     jsonb NOT NULL DEFAULT '{}',
    created_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ft_environment_project ON ft_environment (project_id);
CREATE UNIQUE INDEX uk_ft_environment_project_name ON ft_environment (project_id, lower(name)) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX uk_ft_environment_project_default ON ft_environment (project_id) WHERE is_default AND is_deleted = FALSE;
COMMENT ON TABLE ft_environment IS '功能测试环境（AI 执行世界模型与安全配置载体）';
COMMENT ON COLUMN ft_environment.accounts IS '账号列表，password 为密文，接口永不明文回显；alias 环境内唯一';
COMMENT ON COLUMN ft_environment.preset_mode IS '预置动作三态：inherit 继承项目默认 / override 自定义覆盖 / disabled 禁用';
COMMENT ON COLUMN ft_environment.initial_actions IS '环境级预置动作，仅 preset_mode=override 时生效';
COMMENT ON COLUMN ft_environment.exec_params IS '执行参数；allowEvaluate 默认 false（环境级显式开启），readOnly 默认 false';
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
| execution_id | uuid | NOT NULL | `ai_task_execution.id`，与执行实例 / 重试对账；按批次独立匿名 |
| plan_id | uuid | NOT NULL | 计划 |
| snapshot_node_id | uuid | NOT NULL | 用例快照节点 |
| case_sort | int | NOT NULL | 用例在批次内的顺序（1..N，冻结快照排序） |
| phase | varchar(20) | NOT NULL | `precondition` / `step_N`（N 从 1 起）/ `verify` / `decompose` |
| seq | int | NOT NULL | 用例内序号（阶段内递增，跨阶段连续） |
| kind | varchar(20) | NOT NULL | `action` / `action_failed` / `verdict` / `note` |
| action | varchar(30) | NULL | 原语名（`action`/`action_failed` 必填） |
| target_ref | varchar(20) | NULL | 元素临时引用 |
| params | jsonb | NULL | 占位符原文；evaluate 即 JS 源码逐字留痕 |
| reason_code | varchar(30) | NULL | 失败原因码（枚举见 Runner 分册 3.2.4） |
| observation_digest | text | NULL | 观察摘要（元素树与语义摘要的压缩留痕） |
| observation | jsonb | NULL | 完整观察；仅 `action_failed` 与 `verdict` 行留存，其余为 null |
| screenshot_ref | varchar(500) | NULL | 文件域对象 key（非 URL）；截图本体在对象存储，访问经服务端签名 |
| tokens_in / tokens_out | int | NULL | 该动作所在步的模型调用消耗；明细口径，不与任务级汇总对账 |

**索引**（3 个，C9）：`idx_ui_action_log_project_task (project_id, task_id, case_sort, seq)`（隔离过滤 + 批次内用例与动作排序）、`idx_ui_action_log_execution (execution_id, case_sort, seq)`（按执行分页 / 对账）、`idx_ui_action_log_case (snapshot_node_id, seq)`（按用例过滤）。`plan_id` 为归属字段、非高频过滤条件，不单独建索引。

```sql
CREATE TABLE ui_action_log (
    id                 uuid PRIMARY KEY,
    project_id         uuid NOT NULL,
    task_id            uuid NOT NULL,
    execution_id       uuid NOT NULL,
    plan_id            uuid NOT NULL,
    snapshot_node_id   uuid NOT NULL,
    case_sort          int NOT NULL,
    phase              varchar(20) NOT NULL,
    seq                int NOT NULL,
    kind               varchar(20) NOT NULL,
    action             varchar(30) NULL,
    target_ref         varchar(20) NULL,
    params             jsonb NULL,
    reason_code        varchar(30) NULL,
    observation_digest text NULL,
    observation        jsonb NULL,
    screenshot_ref     varchar(500) NULL,
    tokens_in          int NULL,
    tokens_out         int NULL,
    created_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted         boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ui_action_log_project_task ON ui_action_log (project_id, task_id, case_sort, seq);
CREATE INDEX idx_ui_action_log_execution ON ui_action_log (execution_id, case_sort, seq);
CREATE INDEX idx_ui_action_log_case ON ui_action_log (snapshot_node_id, seq);
COMMENT ON TABLE ui_action_log IS 'AI 执行动作明细（唯一事实源，按批次/执行/用例/步骤归档，追加式）';
COMMENT ON COLUMN ui_action_log.case_sort IS '用例在批次内的顺序，任务级轨迹按 (case_sort, seq) 排序';
COMMENT ON COLUMN ui_action_log.observation IS '完整观察，仅失败动作与验证阶段留存，用于审计与回放';
COMMENT ON COLUMN ui_action_log.screenshot_ref IS '文件域对象 key，非 file:// 或直链；访问经服务端签名';
COMMENT ON COLUMN ui_action_log.tokens_in IS '动作级用量明细，不参与 ai_task / ai_task_execution 汇总对账';
```

### 2.2 既有表改造

| 表 | 变更 | DDL |
| ---- | ---- | ---- |
| ft_plan_execution_record | 新增来源维度 | `ALTER TABLE ft_plan_execution_record ADD COLUMN source varchar(10) NOT NULL DEFAULT 'manual';`（既有行自动为 `manual`，**无需回填脚本**——已确认无存量 AI 数据） |
| ft_plan_execution_record | 结果枚举扩展 | `result` 既有 `VARCHAR(20)`，直接接受 `skipped`，**无 DDL**；仅更新列注释为「pass/fail/block/untested/skipped」 |
| ft_plan_node_snapshot | 最后结果扩展 | `last_result` 同为 `VARCHAR(20)`，接受 `skipped`，无 DDL；列注释同步 |
| ft_plan | 执行环境列类型 | `ALTER TABLE ft_plan ALTER COLUMN environment TYPE uuid USING NULL::uuid;`——列类型由 `varchar(200)` 改为 `uuid`，存 `ft_environment.id`；本期不考虑存量自由文本转换，旧值统一置空、不回填；展示时关联取名称，关联不上（环境已删）时展示「已删除环境」 |
| ai_task | 过程补充记录落点 | `ALTER TABLE ai_task ADD COLUMN abort_meta jsonb NULL;`——失败 / 取消时写中断阶段、已处理条目、token 与成本、中断原因；任务详情接口透出，避免复用 `result_meta` 污染批次汇总 |
| ai_task | 同计划唯一运行中批次 | `CREATE UNIQUE INDEX uk_ai_task_ui_running_plan ON ai_task (project_id, (input->>'planId')) WHERE type = 'ui_execution' AND status = 'running' AND is_deleted = FALSE;`——并发提交由数据库兜底；批次进入 `closed` / `aborted` 后自动释放 |

> `ft_plan.environment` 由 `varchar(200)` 改 `uuid` 需全表重写，按小表评估后执行（当前计划量级可接受）；迁移脚本先备份、再改列、后建索引，回滚为反向 `USING environment::text` 并删除新增索引。唯一索引表达式依赖 `input->>'planId'`，要求发起时 `planId` 必填（3.8 已校验）。

### 2.3 执行端实例表（ai_runner_instance）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| id | uuid | PK | 框架默认策略 |
| runner_id | varchar(64) | NOT NULL, UNIQUE | 实例标识（`register.ack` 下发，重连保持不变） |
| mode | varchar(20) | NOT NULL | `desktop-hosted` / `cli` |
| owner_id | uuid | NULL | 绑定用户；`cli` 为 NULL（共享） |
| status | varchar(10) | NOT NULL, DEFAULT 'offline' | `online` / `offline`；忙闲由 `current_task_id` 与租约派生 |
| current_task_id | uuid | NULL | 当前占用批次；空闲为 NULL |
| lease_expires_at | timestamp | NULL | 批次租约到期时间；到期未续租由清扫回收 |
| capacity | int | NOT NULL, DEFAULT 1 | 并发容量；V1 固定 1 |
| protocol_version | varchar(10) | NOT NULL | 注册时上报 |
| last_heartbeat_at | timestamp | NULL | 最后心跳，失活判定依据 |
| created_at / updated_at / is_deleted | — | 公共字段 | C5 |

**索引**（3 个，C9）：`uk_ai_runner_instance_runner` UNIQUE (runner_id)；`idx_ai_runner_instance_owner (owner_id, status)`（分派查询：在线 + 归属）；`idx_ai_runner_instance_lease (status, lease_expires_at)`（租约过期清扫）。忙闲判定与抢占均为条件更新，不新增索引。

```sql
CREATE TABLE ai_runner_instance (
    id                uuid PRIMARY KEY,
    runner_id         varchar(64) NOT NULL,
    mode              varchar(20) NOT NULL,
    owner_id          uuid NULL,
    status            varchar(10) NOT NULL DEFAULT 'offline',
    current_task_id   uuid NULL,
    lease_expires_at  timestamp NULL,
    capacity          int NOT NULL DEFAULT 1,
    protocol_version  varchar(10) NOT NULL,
    last_heartbeat_at timestamp NULL,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_runner_instance_runner ON ai_runner_instance (runner_id);
CREATE INDEX idx_ai_runner_instance_owner ON ai_runner_instance (owner_id, status);
CREATE INDEX idx_ai_runner_instance_lease ON ai_runner_instance (status, lease_expires_at);
COMMENT ON TABLE ai_runner_instance IS 'AI 执行端实例注册表（cli 共享优先分派 / desktop-hosted 用户绑定兜底）';
COMMENT ON COLUMN ai_runner_instance.owner_id IS 'desktop-hosted 绑定用户；cli 为 NULL（任意用户可用）';
COMMENT ON COLUMN ai_runner_instance.current_task_id IS '当前占用批次；与 lease_expires_at 共同表达忙闲，空闲均为 NULL';
COMMENT ON COLUMN ai_runner_instance.capacity IS '并发容量；V1 固定 1，为后续多批次调度预留';
```

同步更新 `server/src/main/resources/db/schema.sql` 全量基线（新增表至此共四张：`ft_environment`、`ft_preset_actions`、`ui_action_log`、`ai_runner_instance`），并交付版本化迁移脚本（数据库规范 §5）：`server/src/main/resources/db/migration/V<yyyyMMddHHmmss>__ai_ui_testing.sql`，存量库按序执行、附回滚说明（6.2）。

**归属口径**：`ai_runner_instance` 为**部署级注册表，不归属工作空间**（无 `workspace_id`、不附加数据隔离过滤）——执行端是运维侧资源，任意工作空间的批次均可分派到同一实例，可分派范围仅由 `owner_id` 决定（专属仅本人、共享任意用户）。注册时机：`desktop-hosted` 实例**用户登录成功即注册**（桌面版登录会话签发 `ownerToken`）；`cli` 实例进程启动即注册。**下线触发**（两类实例同规则）：进程主动退出（desktop-hosted 用户登出 / 关闭桌面版，cli 进程退出）→ 断连即置 `offline`；**超时未连接**（断连或 60s 心跳缺失）→ 心跳清扫置 `offline` 摘除出分派池。

实例生命周期：`register` 校验通过 upsert（按 `runner_id`）并置 `online`；心跳刷新 `last_heartbeat_at`，会话中同时续租 `lease_expires_at`；断连或 60s 无心跳由心跳清扫置 `offline` 并释放租约（与批次失活落存口径同源，见 4.1）。**分派抢占**：以条件 UPDATE 原子占用——仅当 `status='online'`、`current_task_id IS NULL` 且租约为空或已过期时写入 `current_task_id` 与 `lease_expires_at`（默认 120s，心跳续租）；抢占影响 0 行视为忙。租约到期未续租：释放实例，占用批次转 `aborted` 并写 `abort_meta`，V1 不自动重派。

### 2.4 数据隔离与生命周期

- 新增表中 `ft_environment`、`ft_preset_actions`、`ui_action_log` 经 `project_id` 归属项目 → 工作空间，查询层自动附加上下文过滤（沿用数据与接口设计分册 1.3）；`ai_runner_instance` 为部署级注册表，不做工作空间过滤（归属口径见 2.3）。
- `ui_action_log` 为追加式，批次结束后只读；计划删除时级联删除轨迹（事务显式处理，无物理外键）；截图对象随轨迹清理任务回收。

## 3. 接口详细设计

响应均为 `Result<T>`，示例只展示 `data`；上下文经 `X-Active-Project` / `X-Active-Workspace` 请求头传递（C4），不出现在 URL。接口按域分组：环境管理 `/api/project/ft-environments`，AI 执行复用 `/api/ai/tasks` 统一任务资源（API 规范 2.1：不新增平行基础路径）。

### 3.1 环境列表

- **路径**：`GET /api/project/ft-environments`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）
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
      "presetMode": "inherit",
      "updatedAt": "2026-10-14T03:00:00Z"
    }
  ],
  "total": 3
}
```

- **口径**：`presetMode` 为三态（`inherit` / `override` / `disabled`），前端据此显示「使用项目默认 / 已覆盖 / 已禁用」；密码与入口/账号明细不在列表返回。

### 3.2 环境详情

- **路径**：`GET /api/project/ft-environments/:id`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）
- **响应**：`{ "id", "name", "description", "isDefault", "entryUrls": [{"name","url"}], "accounts": [{"username","alias","role","note","passwordSet": true}], "dataState", "tips", "presetMode": "inherit", "initialActions": [...], "execParams": {"viewport": {...}, "actionTimeoutMs": 10000, "allowEvaluate": false, "readOnly": false}, "createdAt", "updatedAt" }`
- **口径**：**密码永不出现在任何响应**——账号只回 `passwordSet` 布尔（掩码展示由前端据此渲染 ●●●●●）；`initialActions` 为环境级原值（`presetMode=inherit` / `disabled` 时不参与解析）；`execParams.allowEvaluate` 缺省为 `false`。

### 3.3 创建环境

- **路径**：`POST /api/project/ft-environments`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）
- **请求体**：

```json
{
  "name": "staging",
  "description": "预发环境",
  "entryUrls": [{ "name": "Web端", "url": "https://app.staging.example.com" }],
  "accounts": [{ "username": "admin", "alias": "管理员", "password": "<明文，仅此一次>", "role": "admin", "note": "" }],
  "dataState": "订单表已清空",
  "tips": "新建按钮在列表右上",
  "presetMode": "inherit",
  "initialActions": [],
  "execParams": { "viewport": { "width": 1440, "height": 900 }, "actionTimeoutMs": 10000, "allowEvaluate": false, "readOnly": false }
}
```

- **校验**：`name` 非空且项目内非重复（忽略大小写，重复 → 1000011031，数据库唯一索引兜底）；`entryUrls[].name` 与 `accounts[].alias` 环境内非重复（重复 → 1000011046）；`entryUrls[].url` 须为 http(s) 且同条目内 origin 合法（非法 → 1000011032）；`accounts[].password` 创建时必填（明文仅入站一次，立即加密落库）；`presetMode` ∈ {inherit, override, disabled}，`override` 时 `initialActions` 结构校验（见 4.4，失败 → 1000011033），非 `override` 时忽略 `initialActions`；`execParams.allowEvaluate` 缺省 `false`，`readOnly` 缺省 `false`。
- **处理**：写入后 `is_default` 若为 true 则同项目其余环境置 false（项目内单默认，唯一索引兜底）。
- **响应**：201，结构同 3.2。

### 3.4 更新环境

- **路径**：`PUT /api/project/ft-environments/:id`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）
- **口径**：部分更新（C11）——未传字段不改；`accounts` 中 `password` 未传（`passwordSet=true` 的既有账号）保持原密文，**传空字符串视为非法**（1000011034）；`isDefault=true` 时同项目互斥置 false（唯一索引兜底）。`presetMode` 由 `inherit` / `disabled` 改为 `override` 时必须同时提交非空 `initialActions`（否则 1000011033）；改为 `disabled` 时保留原 `initialActions` 但不再参与解析。
- **重命名引用校验**：修改 `entryUrls[].name` 或 `accounts[].alias` 时，若旧名称被项目级或本环境预置动作引用，与删除同口径拒绝（1000011041「条目被预置动作引用，请先修改」）；入口 / 账号名称重复校验同创建（1000011046）。
- **删除保护**：无（环境删除不影响已发起批次——批次持冻结快照；见 4.2 取消与失败口径）。

### 3.5 删除与设默认

- **删除**：`DELETE /api/project/ft-environments/:id`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）——逻辑删除；**被计划引用不阻止删除**（计划侧展示降级为「已删除环境」，概设 3.3 改动清单 #1 口径）；正在执行中的批次不受影响。
- **设默认**：`PUT /api/project/ft-environments/:id/default`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）——项目内单默认互斥更新，唯一索引兜底并发。

### 3.6 项目级预置动作

- **查询**：`GET /api/project/ft-preset-actions`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）→ `{ "steps": [...], "updatedAt" }`（无行返回空数组）。
- **保存**：`PUT /api/project/ft-preset-actions`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码）——全量覆盖式，`steps` 结构校验（4.4，失败 1000011033）；引用式参数（`entryName` / `credentialAlias`）须在项目内至少一个环境可解析（否则 1000011047），避免执行期才暴露失效引用。
- **解析优先级**（服务端在 `session.start` 组装时执行，按环境 `preset_mode` 三态）：`override` → 本环境 `initial_actions`；`disabled` → 不下发 `initial_actions.execute`；`inherit` → 项目级 `steps`；继承且项目级为空 → 不下发，登录由 AI 自主决策（概设 4.9 三态）。

### 3.7 发起前置信息

- **路径**：`GET /api/project/plans/:id/ai-exec/precheck`（仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码；计划须属于当前项目）
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
  "cases": [{ "id": "…", "title": "销售订单新建-正常流程", "priority": "P1", "documentId": "…", "lastResult": "block" }],
  "params": { "stepActionLimit": 50, "stepActionLimitMax": 200, "batchActionLimit": 500, "batchTokenLimit": 3000000, "batchCostLimit": 50 },
  "runner": { "available": true, "busy": false, "preferred": "cli", "count": 2, "capacity": 2, "busyCount": 0 },
  "progress": { "runningTaskId": null }
}
```

- **校验顺序**（`runnable=false` 时给 `blockReason`）：**当前用户项目成员校验（第一步，先于任何资源读取）** → AI 总开关与模型可用性（沿总册，复用 `GET /api/ai/status` 口径）→ 计划状态（blocked/completed/closed → `plan_not_allowed`，1000011035）→ 同计划存在进行中批次（`runningTaskId` 非空 → `duplicate_running`，提交时服务端拒绝，1000011043）→ 执行端可用性与忙闲（无在线 → `no_runner`；有在线但全部忙 → `runner_busy`，1000011045）→ 环境必填（无环境或环境无入口 URL → `environment_required`，1000011037）。
- **说明**：`boundEnvironment*` 为计划绑定环境回显（3.9 改造）；未绑定或环境已删则为 null，发起弹窗默认选项目默认环境。`runner` 为执行端可用性快照（与 4.1 发起前置校验同规则）：`available=false` 时 `blockReason=no_runner`（提示「无可用执行端，启动桌面版或联系运维部署」）；`busy=true` 时 `blockReason=runner_busy`（提示「执行端忙，请稍后重试」）；`preferred` 为按分派规则将命中的实例模式（`cli` 共享优先 / `desktop-hosted` 专属兜底）。`cases[].lastResult` 供停批后重发时默认勾选未执行与阻塞用例。

### 3.8 发起批次（提交任务）

- **路径**：`POST /api/ai/tasks`（复用 AI Task 统一任务资源；仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码；计划须属于当前项目）
- **请求体**：

```json
{
  "type": "ui_execution",
  "input": {
    "planId": "…",
    "caseIds": [],
    "environmentId": "…",
    "modelId": "…",
    "stepActionLimit": 50,
    "batchActionLimit": 500,
    "batchTokenLimit": 3000000,
    "batchCostLimit": 50
  },
  "waitSeconds": 0
}
```

- **input 校验**（按 `ui_execution` 分发，AI 底座 3.6.2 的 type→input 校验点）：`planId` 必填且存在属于当前项目（1000011010 复用）；`caseIds` 空数组 = 计划关联全部用例，非空时须为本计划关联的快照节点（否则 1000011036）；`environmentId` 必填、属于当前项目且至少配置一个入口 URL（否则 1000011037）；`modelId` 可选，缺省取系统默认模型，传入时须为当前可用模型（不可用 → 1000011044），**所选模型即本批次决策与判定的实际模型**，提交期解析并写入执行行 `model_id`；`stepActionLimit ∈ [1, 200]`，缺省 50（1000011038）；`batchActionLimit ∈ [1, 2000]` 缺省 500、`batchTokenLimit` 缺省 3,000,000、`batchCostLimit` 缺省 50，均只允许下调（超出 → 1000011038）。
- **预算签发**：`ui_execution` 适配器声明专用预算 profile（`maxSteps = batchActionLimit + 用例数 × 拆解步数上限 + 预留`，`maxTokens` / `maxCost` 不低于批次上限），由 `BudgetGuard` 正常签发；批次上限与通用预算同时生效、任一先到即终止，通用预算先到视为配置异常并记录。
- **重复发起**：提交时服务端按 `planId` 查同计划进行中批次，存在则直接拒绝（1000011043），不创建任务；数据库部分唯一索引 `uk_ai_task_ui_running_plan` 兜底并发提交。precheck 的 `progress.runningTaskId` 仅作前端提前呈现。
- **停批后重发**：不单独设接口，仍走本接口；默认由前端按 `precheck.cases[].lastResult` 预勾选「未执行 + 阻塞」用例（用户可改），服务端只校验 `caseIds` 属于本计划，不做“已通过不重跑”的强制拦截。每次重发生成独立任务与执行记录，历史判定与轨迹保留，计划统计按用例最新判定聚合。
- **处理**：发起即冻结当前计划用例快照——服务端从 `ft_plan_node_snapshot` 按 `case / precondition / step / expected` 节点提取**结构化用例数据**（节点 ID、type、title、parent_id、sort_order，不经既有 120 字符截断），模块锚点由用例快照的 `document_snapshot_id` 定位文档模块后沿 `ft_plan_module_snapshot.parent_id` 上溯得到树路径，环境内容取环境快照、预算与模型入参，一并写入 `ai_task.input`；正文在每次模型调用前由处理器按冻结结构拼装为 markdown（见 4.2）、不预先落库（未同步的脑图修改不进本次执行）。`type=ui_execution`；执行行 `promptScene` 按 AI Task 现有规则记 `ui_execution`，两个模型场景 `ui_execution_decision` / `ui_execution_verdict` 由处理器调用 `AiModelClient` 时直接传入并用于用量归因。任务创建即启动执行（`status=running`，执行实例 `running`），提交即返回。
- **响应**：AI 底座 3.6.2 同构（`taskId` / `executionId` / `status` / ...）。
- **进度 / 终止**：复用现有 REST——任务详情 `GET /api/ai/tasks/{taskId}`（2s 轮询取终态）、执行轨迹 `GET /api/ai/tasks/{taskId}/executions/{executionId}/events?afterSeq=`（游标轮询取进度）与终止 `POST /api/ai/tasks/{taskId}/executions/{executionId}/abort`（AI 底座 3.6.3/3.6.4）；不新增 SSE / WS 端点、不改 DTO 与契约，UI 执行不消费轨迹的 WebSocket 推送；进度经 `status_change` 事件承载，`detail.kind=ui_progress`，含用例序号 / 标题、步骤 x/y、动作数 / 上限、四值计数与 `status`，`summary` 为「第 N/M 条用例 · 步骤 x/y · 已 a 动作」（概设 4.1）；分母取自冻结快照；轮询失败保留上次值并自动续拉，≤2s 可见；无步骤用例的拆解结果写入 `status_change` 事件 `detail.kind=ui_decompose`，不新增事件类型、不回写用例；终止在当前 `action.result` 返回后退出循环并按 4.2 落存，已写入判定不回滚。

### 3.9 计划接口改造（既有接口变更点）

| 接口 | 变更 |
| ---- | ---- |
| `POST /api/project/plans`（创建）与 `PUT …/:id` | `environment` 请求字段改为 `environmentId`（UUID，须属于当前项目，否则 1000011037）；可空 |
| `GET …/:id`（详情）、`GET …`（列表） | 响应 `environment` 改为 `{ "environmentId", "environmentName" }`；关联不上时 `environmentName=null` |
| `GET …/:id/progress` | 响应新增 `"skipped": 0`；`last_result ∈ {pass, fail, block, untested, skipped}`，`skipped` 计入已执行（`progressPercent` 分母不变、分子含 skipped），**不计入 passed** |
| `POST …/:id/records`（提交执行记录） | `result` 取值新增 `skipped`，但**手动来源拒绝 `skipped`**（1000011039，「跳过仅由 AI 执行产生」）；AI 回写经服务内部通道写入并置 `source='ai'`；人工改判写入置 `source='manual'` |

### 3.10 轨迹查询

- **路径**：`GET /api/ai/tasks/{taskId}/action-logs`（仅校验当前用户是否为当前项目成员及 AI Task 任务归属可见性，不新增 RBAC 权限码）
- **参数**：`executionId`（缺省为最新执行，指定时按 `execution_id` 过滤）、`snapshotNodeId`（可选，按用例过滤）、`pageNo`、`pageSize`（默认 100，轨迹为追加流，按 `(caseSort, seq)` 升序）。
- **响应**：

```json
{
  "list": [
    {
      "id": "…", "executionId": "…", "snapshotNodeId": "…", "caseSort": 3,
      "phase": "step_2", "seq": 5, "kind": "action_failed",
      "action": "click", "targetRef": "e12",
      "params": null, "reasonCode": "covered",
      "observationDigest": "「新建」被遮罩层覆盖",
      "observation": null,
      "screenshotRef": null, "tokensIn": 0, "tokensOut": 0,
      "createdAt": "2026-10-14T03:01:12Z"
    }
  ],
  "total": 143
}
```

- **口径**：`params` 中密码为占位符原文、`evaluate` 为 JS 源码逐字（概设 4.7 护栏 1）；`observation` 仅 `action_failed` / `verdict` 行返回完整观察，其余为 null（列表默认不展开，按需回填）；`screenshotRef` 为对象 key，前端经服务端签名 URL 取图，不直接拼接存储地址；批次汇总（pass/fail/block/skipped/untested 计数）取自任务 `result_meta`，不另设接口。`ui_action_log` 为 UI 执行动作明细的**唯一事实源**（含 `execution_id`，与执行实例 / 重试对账）；通用执行轨迹 `GET /api/ai/tasks/{taskId}/executions/{executionId}/events` 在 UI 执行批次中仅承载进度与状态摘要（`ui_progress` / `ui_decompose` / `status_change`），不重复落动作明细；两者按 `taskId` / `executionId` 关联。动作明细的 token 仅作展示，不与任务 / 执行级汇总对账。

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
| 1000011042 | 无可用执行端 | 3.7 / 4.1 |
| 1000011043 | 同计划已有进行中批次，禁止重复发起 | 3.7、3.8 |
| 1000011044 | 所选模型不可用 | 3.8 |
| 1000011045 | 执行端忙，请稍后重试 | 3.7 / 4.1 |
| 1000011046 | 入口名称或账号别名重复 | 3.3 / 3.4 |
| 1000011047 | 预置动作引用条目不存在 | 3.6 / 4.4 |

## 4. 业务逻辑设计

### 4.1 通道服务（服务端侧）

- **独立通道**：新增 `RunnerWebSocketConfigurer` / `RunnerHandler` / `RunnerHandshakeInterceptor` / `RunnerConnectionRegistry`，端点 `/ws/runner`；**不复用**现有文档协作 WS 的 `/ws/documents` 配置、用户 JWT 认证拦截器、单 `MiGooWebSocketHandler` 与按用户路由（`DistributedWebSocketSessionManager`）。连接注册表按 `runner_id` 路由；V1 明确单节点部署并在多节点配置下启动失败（保护性校验），多节点粘性路由 / 跨节点转发列为升级项。
- **握手鉴权**：Runner 在 WS 握手阶段经 `Authorization` 头提交 `ownerToken` 或 `sharedKey`，服务端建连前完成验签并按凭据类型交叉校验 `mode`（ownerToken ↔ desktop-hosted、sharedKey ↔ cli），失败直接拒绝、不创建连接面。
  - `ownerToken`：桌面版登录会话签发，`aud=runner`、TTL 10 分钟、单次握手（`jti` 一次性消费）、可撤销；载荷仅含用户标识与实例模式，不承载业务权限。
  - `sharedKey`：≥256 bit 随机密钥，仅用于实例注册、不授予业务权限，90 天轮换、泄露可即时吊销；共享实例建议叠加 mTLS 或网络层准入（部署选项）。
- `register`：首帧仅声明 `mode` / `protocolVersion`（可选 `runnerId` 用于重连），按 `runner_id` upsert 执行端实例（2.3）并回 `register.ack`；注册为部署级行为，不校验工作空间上下文，帧内不承载凭据。
- **分派与原子抢占**：批次 `session.start` 前按序选择「在线且空闲」实例——① 任一在线 `cli` 共享实例（按 `last_heartbeat_at` 最新优先）；② 提交人本人的 `desktop-hosted` 在线实例；③ 皆无在线 → 1000011042；④ 有在线但均忙 → 1000011045，V1 不排队。选中后以条件 UPDATE 原子占用 `current_task_id` + `lease_expires_at`（默认 120s），影响 0 行则回到 ④。
- **发起前置校验（后端强制，`POST /api/ai/tasks` 提交时）**：校验「存在在线且空闲的共享实例或提交人本人的在线空闲专属实例」，否则拒绝（1000011042 / 1000011045）；Web 版与桌面版一视同仁。precheck（3.7）提供同一规则的只读快照，但不替代本校验。
- `session.start` 组装：签发单批次 `batchToken`；**不下发用例正文**（Runner 不需要，正文只在服务端组包使用）；`originWhitelist` = 所选环境 `entry_urls` 全部 origin 并集；`execParams` 取环境配置；附截图上传授权（批次级预签名 URL 或一次性上传令牌）与 `budgetSeq` 起始值。
- **凭据下发（最小化）**：`session.start` 不携带凭据；服务端在进入用例或预置动作需要时下发 `credential.grant`，仅含当前用例引用账号的「别名 → 明文」映射与 `expiresAt`（≤10 分钟）；用例切换、批次结束或令牌到期由 Runner 清零，服务端同步记录授权、使用与撤销审计。帧与异常堆栈不落日志。
- **归属校验**：分派仅向目标实例的连接发送 `session.start`；`desktop-hosted` 实例绑定 `owner_id`，服务端只分派该用户批次（Runner 侧二次校验 `OWNER_MISMATCH` 兜底）；`cli` 实例接受任意用户批次；同一实例同一时刻仅一个进行中批次（容量 + 租约保证）。
- `action.execute` 分发前完成**白名单与枚举二次校验**（15 执行原语，`done` 为服务端语义原语不下发，Runner 分册 4.1）、`budgetSeq` 分配与预算计数（**计数在服务端**，Runner 不决策预算）；`evaluate` 在环境开关关闭时直接拒绝并记为动作失败。
- `action.result`：成功 → `ui_action_log` 落 `action`；失败 → 落 `action_failed` + 原因码，交步进决策循环决定重试/换路/止损；通用轨迹仅记进度 / 状态摘要事件。
- **心跳与租约**：心跳 60s 缺失 → 实例置 `offline`、释放租约、占用批次转 `aborted` 并写 `abort_meta`；会话中失活按 4.2 落存口径处理；V1 不自动重派，用户重新发起。
- **日志**：覆写基类 `handleTextMessage` 的 INFO 打印，帧内容（凭据、页面观察、JS 源码）一律不落日志，仅记录 `type` / `runnerId` / `budgetSeq` 等元数据。

### 4.2 批次执行主循环（步进式）

实现形态：新建**独立步进式执行策略**，与通用 Agent 工具循环并列注册（AI 底座 4.2、2.6.5；**不复用 `AgentExecutionRunner` 的 tool-loop**、不恢复旧 `TaskHandler` 引擎或兼容双轨）；仅任务状态、预算护栏、轨迹、取消与失活清扫复用 AI Task 双平面既有机制，进度按 3.8 的 REST 事件口径上报，单任务内驱动：

1. **冻结结构化上下文**：发起即从 `ft_plan_node_snapshot` 按 `case / precondition / step / expected` 节点提取结构化用例数据（节点 ID、type、title、parent_id、sort_order，不截断），模块锚点由用例快照的 `document_snapshot_id` 定位文档模块后沿 `ft_plan_module_snapshot.parent_id` 上溯（未关联模块则为空），环境内容、模型与步级 / 批次预算一并写入 `ai_task.input`；未同步修改不进本次执行。正文不预先落库，在每次模型调用前由本策略按冻结结构拼装为 markdown。
2. **预置动作**：按 3.6 三态解析 → 下发 `initial_actions.execute` → 失败即批次终止（`aborted`，`abort_meta.reason=preset_failed`，`error_msg` 写明第几步与原因码），未执行用例保持待执行、不写判定，不烧 AI 预算。
3. **用例级循环**（`sort_order`）：前置阶段 → 逐步动作循环 → 验证阶段 → 判定回写 → 止损检查（连续 3 条 `block` 自动停批 → `aborted`，`abort_meta.reason=consecutive_blocked` 并记录已完成 / 未执行条目）。
4. **动作循环**：组包（从 `ai_task.input` 读结构化用例数据拼 markdown 正文 + 当前步骤 + 压缩后的轨迹历史 + 最新观察，含 80% 收敛注入与同动作 3 败头铁注入）→ 单次模型调用（`AiModelClient` 门面，处理器直接传场景 `ui_execution_decision`，调用前后刷新任务心跳）→ 解析模型输出为「下一动作」或「步骤完成」（JSON 契约见 4.5，解析失败至多重试 2 次，仍失败转用例 `block`）→ 下发 → 等 `action.result` → 预算/取消检查（每动作一次，`budgetSeq` 对账）→ 写一条 `status_change` 进度事件（`detail.kind=ui_progress`，见 3.8）；动作明细写 `ui_action_log`（唯一事实源，含 `case_sort`；失败动作留存完整 `observation`）。无步骤用例在拆解完成时写 `detail.kind=ui_decompose` 事件，步骤分母取自拆解结果但不回写用例。
5. **验证阶段**：`observe(reason=verify)` 取证（强制截图）→ 组包判定场景（处理器直接传 `ui_execution_verdict`，多模态单图）→ 四值输出 → 服务端校验证据引用（观察序号 / 截图 key 存在且属于本用例验证阶段，校验失败转 `block`）→ 证据与结论写执行记录 `note`；判定明细写 `ui_action_log`（留存完整 `observation`）。
6. **回写**：复用既有 `submitExecutionRecord` 内部链路，`result` 取 `pass/fail/block/skipped`、`source='ai'`、`executor_id`=批次提交人；同步快照 `last_result`；进度统计经既有聚合自动生效（skipped 分支见 3.9）。
7. **批次收尾**：`result_meta` 写 `{counts:{pass,fail,block,skipped,untested}, caseResults:[…], budget:{actions,tokens,cost}}`（键名与执行结果枚举一致，不使用 `blocked` / `result` 等旧字段）；正常跑完任务置 `closed`；终止类走下方落存口径，已写判定不回滚。
- **终止**：`POST /api/ai/tasks/{taskId}/executions/{executionId}/abort` 置于中止 → 主循环在当前 `action.result` 返回后退出 → 下发 `session.end` → 任务置 `aborted`，已写判定与轨迹保留。
- **总时长 4h / 批次预算**：UI 执行处理器单独计时与计数，超时或批次动作数 / token / 成本任一耗尽即 `aborted`，原因写 `abort_meta`。
- **失败 / 终止落存**（仅 UI 执行批次口径）：任务失败、用户终止、预算耗尽或会话失活时由执行中转入已终止（`aborted`），在 `ai_task.abort_meta` 写过程补充记录——中断阶段、已处理用例、已写记录清单、token 与成本消耗、中断原因——可在任务详情回看；已写入的执行记录、轨迹与快照状态不回滚、不清理。本能力不产生提案，过程补充记录只含轨迹与判定明细，不建立正式业务数据写入。执行判定直接写入执行记录，不属 AI 生成物 / 提案，不适用待采纳。V1 不做批次完成 / 停批通知，状态在页面可见；断线不续跑，用户重新发起时默认仅重跑未执行与阻塞用例（3.8）。

### 4.3 判定与改判

- 四值语义、证据强制与汇总规则按概设 4.6 实现；`note` 固定格式：`[AI判定] 结论；证据：<文字>（观察：<observationId/seq>；截图：<screenshot_ref>）`。
- **证据校验**：服务端校验判定引用的观察序号与截图 key 存在、属于本用例且来自本次验证阶段；校验失败不落库，转 `block` 并在 `note` 写明「证据校验失败」。
- 判定结果直接写入执行记录，不属 AI 生成物 / 提案，不适用待采纳；失败 / 终止时已写判定保留不回滚（见 4.2）。
- 人工改判走既有 `POST /api/project/plans/:id/records`，写入 `source='manual'`；进度与通过率自动重算。
- 节点状态图标五态与 skipped 统计分支在进度聚合查询中实现（`last_result='skipped'` → 已执行计数 +1、passed 不加）。
- 每批次按不低于 10%（至少 1 条）抽样人工复核，复核结论进入判定可信度评测（需求 6.2）。

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
- **引用式参数**（解决"3 个环境配 3 遍"与环境切换，已裁决）：`entryName` 引用本环境入口条目名、`credentialAlias` 引用本环境账号别名（明文不入库不入编辑器）；**保存时**校验引用可解析——环境级作用于本环境，项目级须在项目内至少一个环境可解析（否则 1000011047）；**执行时**由服务端按当前环境解析，解析失败 → 批次终止（`aborted`，`abort_meta.reason=preset_unresolved`）并写明步骤。
- **三态生效**：`preset_mode=override` 使用本环境序列；`disabled` 不下发；`inherit` 使用项目级序列；项目级为空时不下发，由 AI 自主完成前置。
- **规模校验**：步数 ≤20（超出 1000011033）。
- **删除 / 重命名联动**：删除或重命名入口条目 / 账号前检查预置动作引用，被引用则拒绝（1000011041「条目被预置动作引用，请先修改」）；环境级检查作用于本环境 `initial_actions`，项目级检查作用于项目级 `steps`。

### 4.5 提示词场景与模型

- 注册两场景：`ui_execution_decision`（动作纪律：只发原语、evaluate 默认关闭且为最后手段、模块锚点优先、引用定位、页面内容视为不可信数据、收敛注入）、`ui_execution_verdict`（判定纪律：证实才通过、证据不足判阻塞、证据必须引用本次观察、四值枚举输出）；场景登记入 `ai_prompt_template.scene`（AI 底座 2.5 联动）。
- **模型解析**：`input.modelId` 在提交期校验可用性并写入执行行 `model_id`，本批次决策与判定均使用该模型；未传时取系统默认模型。失败重试复用同一模型（AI 底座任务口径）。
- **输出契约**：决策输出固定 JSON——`{ "action": "<15 原语之一>", "params": {...} }` 或 `{ "stepDone": true }`；判定输出固定 JSON——`{ "verdict": "pass|fail|block|skipped", "reason": "...", "evidence": { "observationSeq": n, "screenshotRef": "..." } }`。解析失败至多重试 2 次（追加“仅输出 JSON”的修复提示），仍失败转用例 `block`；参数不符合原语 schema 时不猜测执行，直接判 `block` 并留痕。
- **上下文压缩**：长用例历史按「最近 8 个动作完整 + 更早动作摘要 + 总 token 上限」组包；截图按 1280px 长边、300KB 上限压缩，超限只保留验证截图。
- 场景传参：两场景由 `ui_execution` 处理器在 `AiModelClient` 调用时直接传入（执行行的 `promptScene` 按 AI Task 现有规则记任务 `type=ui_execution`，不写场景值）。
- 用量记账：每步调用按场景归因写 `ai_usage_log`（`prompt_scene` 分别为两场景）；`ui_action_log.tokens_in/out` 为动作级明细展示，不与任务 / 执行级汇总对账。

### 4.6 预算与止损实现

| 项 | 实现 |
| ---- | --- |
| 通用预算 profile | `ui_execution` 适配器 `defaultBudget()` 返回专用 profile：`maxSteps = batchActionLimit + 用例数 × 10 + 预留`，`maxTokens` / `maxCost` 取批次上限（或更高）；由 `BudgetGuard` 正常签发，`ExecutionControl` 硬闸保留但不应先于批次上限触发 |
| 步级动作上限 | 决策循环内计数，达上限 → 步 `block` → 用例 `block`；80% 时组包注入收敛提示 |
| 批次动作总数 | 批次级计数，达上限 → `aborted`，`abort_meta.reason=batch_actions_exhausted` |
| 批次 token / 成本 | 每次模型调用后按 `control.afterModelCall` 累计；达上限 → `aborted`，`abort_meta.reason=budget_exhausted` 并记录维度 |
| 同动作 3 败 | 轨迹内滑动窗口计数，达阈值注入头铁提示 |
| 批次 4h | UI 执行处理器单独计时，超时 → `aborted`（`abort_meta.reason=timeout`）；与失活清扫 600s 无心跳阈值相互独立 |
| 心跳 | 等待动作、模型调用前后刷新 `heartbeat_at`（≤60 秒），避免长动作被清扫误杀 |
| 连续 3 条 block | 用例级循环检查，停批 → `aborted`（`abort_meta.reason=consecutive_blocked`）并标注 |
| 取消 / 失活 | 见 4.2；失活清扫对进度上报活跃任务不误杀（沿用既有机制） |

## 5. 前端设计

### 5.1 功能测试环境管理页

**路由**：`/workspace/projects/functional-testing?tab=ft-environments`（挂功能测试页子菜单「测试环境」，仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码；菜单项按成员归属与业务状态呈现，不按权限码显隐）。**双端均可用**：环境管理属配置能力而非执行入口，桌面版与 Web 版一致渲染。

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
│   dev         │ 预置动作：(•)继承默认 ( )自定义 ( )禁用        │
│   prod        │                              [保存][删除]    │
└──────────────┴──────────────────────────────────────────────┘
```

**交互与状态**：

| 操作 | 行为 |
| ---- | --- |
| 新建 | 空表单；账号密码保存后仅回 `passwordSet`，编辑时留空=不改；`allowEvaluate` 默认关闭，开启时显示安全提示 |
| **连通性测试** | 保存后提供 [测试连接]：只读探测入口 URL 可达与登录页可加载，结果 Toast 提示，不阻断保存 |
| **复制** | 选中源环境 → [复制所选环境]：读既有详情填充**新建表单**（name 清空必填、`isDefault` 重置否、entryUrls/dataState/tips/presetMode/initialActions/execParams 带入、accounts 带入用户名/别名/角色/备注**密码全部留空**、id 类字段不带）→ 用户改名补密码后**走既有创建接口**（纯前端行为，无专用复制接口） |
| 预置动作编辑 | 三态单选，见 5.2 |
| 删除 | 二次确认；执行中批次不受影响，被引用条目删除 / 重命名由服务端 1000011041 拦截并提示 |
| 空态/加载/错误 | 列表 loading、空态「暂无环境，点击[新建]」、操作失败消息提示；表单按当前用户是否为当前项目成员开放维护，非成员只读（不按权限码显隐、不校验项目角色） |

### 5.2 预置动作编辑器（方案 ② 表单化步骤编辑器）

- **两级编辑入口**：页面顶部「预置动作（项目默认）」区块编辑项目级；环境表单内三态单选「继承默认 / 自定义覆盖 / 禁用」，选覆盖才展开同一编辑器编辑环境级。
- **形态**：步骤列表，每行 = 原语下拉（`navigate/click/fill/press/select/wait/find_text`）+ 按原语渲染的参数表单 + 上移/下移/删除；[+ 添加步骤] 追加。
- **参数表单**：
  - `navigate`：地址 = **下拉选择本环境入口条目**（存 `entryName`，不填字面 URL）；
  - `fill`：定位 = 文本输入（`hint`）；内容 = 普通文本 或 **账号下拉**（选中存 `credentialAlias`，明文不出现）；
  - `click/select`：定位 = 文本输入；`wait`：秒数（≤10）；`press`：键名下拉；`find_text`：文本输入。
- **校验（保存前前端即拦）**：原语与参数必填、步数 ≤20、引用条目存在；服务端 3.6 同步双校验（1000011033），错误定位到具体步骤序号。
- **数据来源**：下拉选项即当前页面已加载的入口/账号子表数据，零额外请求。

### 5.3 发起确认弹窗（计划详情页，双端一致）

**客户端形态判别**：平台为双形态——桌面版（Electron 壳包裹现有 `web/` 前端）与 Web 版，同一前端代码按运行环境判别形态（`renderer` 进程存在 Electron 全局桥即桌面版）。**[🤖 AI 执行] 按钮与本弹窗双端一致渲染**；批次进度条与执行详情（5.4）同样双端可见。发起的强制约束在服务端：**执行端可用性由 `POST /api/ai/tasks` 提交时后端校验**（4.1 发起前置校验，无在线共享或本人专属实例则拒绝 1000011042），前端显隐与禁用只是呈现策略、非安全边界；precheck（3.7）的 `runner` 快照供前端提前禁用按钮与提示，`no_runner` 分支双端行为一致。

**入口**：计划详情页操作组新增 [🤖 AI 执行]（活跃态 new/in_progress 显示；仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码，入口按成员归属与业务状态呈现、不按权限码显隐；前置状态由 3.7 `runnable/blockReason` 决定，总开关关闭时按钮禁用并提示沿总册）。

**弹窗结构**：

```
┌─ AI 执行 ────────────────────────────────┐
│ 模型    [系统默认模型 ▼]（所选即执行模型）  │
│ 用例范围 [✓]全选（已选 12/12）  树形勾选   │
│ 执行环境 [staging ▼]（必填，回显计划绑定）  │
│ 步级动作上限 [50]（1–200）                │
│ 批次上限  动作 [500] · token [3,000,000] · 成本 [50] │
│ 执行端提示：将由共享执行端执行（灰字）      │
│                        [取消] [开始执行]  │
└──────────────────────────────────────────┘
```

- 用例范围不勾选 = 全部（提交 `caseIds:[]`）；勾选态按 `precheck.cases` 树形展示（按文档分组）；**停批后重发**时默认勾选 `lastResult` 为未执行 / `block` 的用例，通过 / 失败默认不勾选，用户可改。
- 执行端提示：按 `precheck.runner.preferred` 一行灰字（`cli` →「将由共享执行端执行」/ `desktop-hosted` →「将在本机执行（桌面版）」）；`available=false`（`no_runner`）时红色提示条「无可用执行端」+ [重试]，[开始执行] 禁用（双端同规则）；`busy=true`（`runner_busy`）时黄色提示条「执行端忙，请稍后重试」+ [重试]，[开始执行] 禁用。
- 提交成功 → 关弹窗 → 页头出现进行中批次条（进度通道，见 5.4）；`blockReason=duplicate_running` 时提示「该计划已有进行中的批次」并禁用发起、可跳进行中批次，绕开预检提交亦由服务端以 1000011043 拒绝；`plan_not_allowed` 时入口不出现；`environment_required` 时提示先创建 / 选择环境并禁用 [开始执行]。
- 模型不可用（1000011044）时 Toast 提示并保留弹窗，刷新模型列表。
- 加载失败/提交失败消息提示；`runnable=false` 原因文案映射。

### 5.4 执行中呈现与执行详情（双端一致）

- **执行中**：复用现有 AI Task REST 接口呈现进度——任务详情 2s 轮询取终态、轨迹 `afterSeq` 游标轮询取 `status_change` / `detail.kind=ui_progress` 事件，显示「3/12 · 当前用例 · 步骤 2/5 · 已 15 动作」（≤2s 可见，无「SSE 断连」状态；轮询失败保留上次值并自动续拉）；[取消] 走既有执行终止接口（3.6.4，二次确认）；批次条随任务状态流转（进行中 running / 已完成 closed / 已终止 aborted）。取消入口与发起一致，仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码、不校验项目角色，按成员归属与业务状态呈现、不按权限码显隐；批次条与取消按钮双端一致渲染。
- **执行详情**：复用任务详情页，`ui_execution` 类型渲染：
  - **轨迹列表**（按用例 → 阶段分组的时间线，排序 `(caseSort, seq)`）：`kind=action` 行显示「动作 + 目标 + 摘要」，`action_failed` 红色标原因码中文映射，`verdict` 行显示四值徽标与证据文字，`note` 行灰字；`action_failed` / `verdict` 行可展开完整观察（元素表折叠）；截图引用经签名 URL 渲染缩略图点开大图；`evaluate` 的 `params` 源码等宽展示逐字可查；密码位置显示占位符原文。
  - **批次汇总**：pass/fail/block/skipped 四色计数（取自任务 `result_meta`，`untested` 单列）。
  - **改判**：跳转计划详情既有执行记录入口（无新代码）。
- **状态分支**：轨迹 loading/空态（未产生轨迹）/加载失败消息；失败 / 终止时展示过程补充记录（`abort_meta`：中断阶段、已处理用例、已写记录清单、token 与成本消耗、原因），已写判定与轨迹保留；本能力不产生提案，不展示提案区；断线导致批次终止时提示「连接中断，批次已终止，可重新发起（默认仅重跑未执行与阻塞用例）」。
- **通知**：V1 不做批次完成 / 停批通知（既有完成通知规则以本批次为唯一例外），状态在页面可见。

### 5.5 计划详情页与列表改造

- 页头统计由「通过/失败/阻塞/待执行/共 N」扩为「通过/失败/阻塞/**跳过**/待执行/共 N」（消费 3.9 progress 新增字段）；节点图标五态（`skipped` 新增徽标样式）。
- 手动标记工具栏仍为四态按钮（不提供跳过）；服务端 3.9 双保险拒绝。
- 列表与详情的 `environment` 列改渲染 `environmentName`（null 显示「已删除环境」或 `—`）。
- 创建计划表单「执行环境」由自由文本改为**环境下拉**（数据源 `GET /api/project/ft-environments`，无环境时提示先建并给跳转）。

## 6. 实施说明

### 6.1 权限与迁移说明

环境与项目级预置动作的查询 / 维护，以及 AI 执行前置查询、发起、进度、轨迹与终止，均仅校验当前用户是否为当前项目成员，不新增 RBAC 权限码、不校验项目角色；服务端按活动工作空间与项目上下文（C4）确认资源归属，任务详情与轨迹沿用 AI Task 任务归属可见性（AI 底座 3.6.3）。前端功能测试侧栏「测试环境」入口、创建 / 保存 / 删除表单与计划详情 [🤖 AI 执行] 入口按成员归属与业务状态呈现，不按权限码显隐。迁移只涉及 6.2 的表结构、列注释与执行环境语义调整，不涉及权限点登记或菜单权限迁移。

### 6.2 数据库变更清单

1. 新增 `ft_environment`、`ft_preset_actions`、`ui_action_log`、`ai_runner_instance`（2.1、2.3，含索引）；
2. `ft_plan_execution_record` 加 `source` 列（2.2）；
3. `ai_task` 加 `abort_meta` 列与同计划运行中批次部分唯一索引 `uk_ai_task_ui_running_plan`（2.2）；
4. `ft_plan.environment` 列类型由 `varchar(200)` 改为 `uuid`（2.2，存量值统一置空）；
5. 更新 `schema.sql` 两处列注释（`result`、`last_result`）与新增表 / 新列 / 新索引；
6. **无数据回填**（本期不考虑存量自由文本环境与 AI 数据）；
7. **基线 + 版本化脚本双交付**（数据库规范 §5）：全量基线 `server/src/main/resources/db/schema.sql` 与版本化迁移目录 `server/src/main/resources/db/migration/` 同步维护；
   - 基线版本号：本期以 `V20261127000000__ai_ui_testing.sql` 作为迁移目录首个脚本，内容与基线增量一致；
   - 已有环境升级路径：存量库按版本号升序执行 `db/migration` 下全部脚本，执行记录表由框架迁移工具维护；新库直接以 `schema.sql` 初始化并登记基线版本为当前最大版本；
   - CI 迁移测试：CI 增加「空库跑 `schema.sql` → 与「空库跑全量迁移脚本」结构比对一致」与「存量库升级后跑一遍本模块接口冒烟」两个检查项，任一处结构漂移即失败；
8. 回滚：按迁移脚本逆序执行对应回滚说明（`DROP` 新表 + `DROP COLUMN source` + `DROP COLUMN abort_meta` + `DROP INDEX uk_ai_task_ui_running_plan` + `environment` 列类型回滚为 `varchar(200)`），新功能未上线即回滚、无业务数据损失。

### 6.3 错误码与契约

- `ErrorCodeConstants` 登记 1000011031–1000011047（3.11），号段 1000011031–1000011060 已同步 `docs/04-detailed-design/02-project-module.md` 6.3 号段表。
- OpenAPI：新接口（3.1–3.8、3.10）与改造接口（3.9）均补注解与模型，`pnpm run contract:gen` 重新生成基线；前端类型由基线生成。
- 任务类型 `ui_execution` 与两提示词场景登记 AI 底座详设（单一事实源联动）；场景由处理器在模型调用时传入，执行行 `promptScene` 沿用任务类型；本类型判定不生成提案，失败 / 终止只写 `abort_meta` 过程补充记录，任务详情不展示提案区。

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
| V1.0 | 2026-10-14 | 执行端实例表去掉 `workspace_id`：注册表为部署级、不做工作空间隔离，可分派范围仅由 `owner_id` 决定；明确注册时机为登录即注册（desktop-hosted）/ 启动即注册（cli）（2.3、2.4、4.1） |
| V1.0 | 2026-10-14 | 明确执行端下线触发：主动退出（登出 / 关闭 / 进程退出）与超时未连接（断连或 60s 心跳缺失清扫）两类（2.3） |
| V1.0 | 2026-11-25 | 同步至功能测试现行口径：全篇免 RBAC 权限码、按成员归属与项目角色校验；失败 / 取消按 AI Task 双平面落存（过程补充记录、已写数据不回滚、阶段性产物保留为待采纳态，判定不属提案）；通道协议以 Runner 详设为单一事实源 |
| V1.0 | 2026-11-26 | 冻结源改为计划用例快照（未同步修改不进执行）；进度改为 REST 详情 + 轨迹 `afterSeq` 游标轮询（`status_change` / `ui_progress`），无步骤拆解经 `ui_decompose` 事件留痕；总时长 4h 由策略单独熔断；服务端拒绝重复发起（1000011043）；过程补充记录仅 UI 执行批次；权限仅校验项目成员；`modelId` 入参保留、实际按系统默认解析 |
| V1.0 | 2026-11-26 | 冻结数据明确为写入 `ai_task.input` 的结构化用例节点（正文在模型调用前拼装）；执行循环改为独立步进式策略、不复用通用 Agent tool-loop；执行行 `promptScene` 沿用 `ui_execution`，两场景在模型调用时传入；`ui_action_log` 补 `execution_id` 与索引并确立为动作明细唯一事实源；Runner 凭据前移 WS 握手校验、`register` 退化为身份声明；`ft_plan.environment` 列类型改 UUID、本期不回填 |
