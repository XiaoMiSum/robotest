# 软件测试平台——AI 能力详细设计总览

**文档版本**：V1.0
**日期**：2026-10-02
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

对 **AI 能力模块的 AI 底座**进行详细设计：定义 AI 域 10 张表的 DDL、AI 配置与用量接口、统一任务资源及通用约定，作为四个能力域分册（生成链、智能助手、缺陷分析、辅助功能）的公共事实源；分册只定义各自的接口与业务逻辑，不重复底座约定。

### 1.2 范围与对应设计

- 对应需求：`docs/01-requirements/07-ai-capability/02-srs-ai-capability.md`（总册）及四个分册。
- 对应概要：`docs/02-high-level-design/07-ai-capability/02-hld-ai-capability.md`。
- 追溯矩阵（`trace_edge` / `trace_coverage_result`、矩阵与覆盖接口）为业务领域资源，见 `docs/04-detailed-design/05-trace-matrix.md`，不属于本文档的 10 张 AI 表。
- 能力域分册：[03-ai-generation](03-ai-generation.md)、[04-ai-assistant](04-ai-assistant.md)、[05-ai-defect-analysis](05-ai-defect-analysis.md)、[06-ai-assisted-features](06-ai-assisted-features.md)。

### 1.3 参考资料

- `docs/00-spec/20-contracts/01-api.md`、`docs/00-spec/20-contracts/02-database.md`（C5/C9、pgvector 约定）、`docs/00-spec/40-security/01-security.md`（密钥与审计）
- `docs/02-high-level-design/04-hld-data-interface.md`（上下文头、`/api/ai` 分类）
- `docs/04-detailed-design/01-readme.md`（通用响应与分页约定）

---

## 2. 数据设计

### 2.1 公共约定

- 域前缀：`ai_`（AI 实现类资源：配置、任务、用量、助手、向量）；索引前缀 `idx_ai_*` / `uk_ai_*`。
- 公共字段（C5，每表必有，下文不再重复）：`id uuid PRIMARY KEY`（框架默认策略）、`created_at timestamp NOT NULL`、`updated_at timestamp NOT NULL`、`is_deleted boolean NOT NULL DEFAULT FALSE`；时间列 UTC 语义。
- 无物理外键（C5）：`model_id / task_id / conversation_id` 等均为逻辑外键，引用完整性由 Service 层保证。
- 本文档全部为**新建表**，随本交付一次性迁移，无存量结构变更。

### 2.2 AI 总开关表（ai_config，单例）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| enabled | boolean | NOT NULL, DEFAULT FALSE | AI 总开关；关闭后全平台 AI 入口不可用、进行中任务终止（4.2） |
| default_model_id | uuid | NULL | 默认模型（逻辑外键 → ai_model_config），各能力域可覆盖 |
| task_timeout_seconds | int | NOT NULL, DEFAULT 600 | 任务超时，超时由清扫器置失败 |
| task_max_retries | int | NOT NULL, DEFAULT 2 | 自动重试上限（调用失败的短重试，与用户手动重试独立） |

**索引**（1 个）：`uk_ai_config_singleton` UNIQUE ((true)) WHERE is_deleted = FALSE —— 保证单例行。

```sql
CREATE TABLE ai_config (
    id                   uuid PRIMARY KEY,
    enabled              boolean NOT NULL DEFAULT FALSE,
    default_model_id     uuid NULL,
    task_timeout_seconds int NOT NULL DEFAULT 600,
    task_max_retries     int NOT NULL DEFAULT 2,
    created_at           timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted           boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_config_singleton ON ai_config ((true)) WHERE is_deleted = FALSE;
```

### 2.3 模型配置表（ai_model_config）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| name | varchar(50) | NOT NULL, UNIQUE | 配置名称（展示用），唯一 |
| provider | varchar(30) | NOT NULL | 供应商类别：`openai / anthropic / azure / gemini / ollama / custom` |
| base_url | varchar(500) | NOT NULL | 模型端点，可为云端 API 或私有化部署 |
| api_key_encrypted | varchar(500) | NOT NULL | 密钥密文（AES 加密存储；接口永不回显，仅支持替换） |
| model_name | varchar(100) | NOT NULL | 实际调用的模型标识 |
| capabilities | jsonb | NOT NULL, DEFAULT '[]' | 能力标签：`chat / vision / embedding` |
| priority | int | NOT NULL, DEFAULT 100 | 兜底顺序（默认模型失效时按 priority 升序尝试） |
| enabled | boolean | NOT NULL, DEFAULT TRUE | 启停开关 |
| input_price | numeric(14,6) | NOT NULL, DEFAULT 0 | 输入侧每百万 token 单价（0 = 不计价；仅作用量记账值，不设币种口径） |
| output_price | numeric(14,6) | NOT NULL, DEFAULT 0 | 输出侧每百万 token 单价（同上） |
| last_test_at | timestamp | NULL | 最近连通性测试时间 |
| last_test_result | jsonb | NULL | 最近连通性测试结果 `{ success, latencyMs, msg }` |

**索引**（2 个）：

- `uk_ai_model_config_name` UNIQUE (name) WHERE is_deleted = FALSE；
- `idx_ai_model_config_enabled` (enabled, priority) —— 选模型扫描。

```sql
CREATE TABLE ai_model_config (
    id                 uuid PRIMARY KEY,
    name               varchar(50) NOT NULL,
    provider           varchar(30) NOT NULL,
    base_url           varchar(500) NOT NULL,
    api_key_encrypted  varchar(500) NOT NULL,
    model_name         varchar(100) NOT NULL,
    capabilities       jsonb NOT NULL DEFAULT '[]',
    priority           int NOT NULL DEFAULT 100,
    enabled            boolean NOT NULL DEFAULT TRUE,
    input_price        numeric(14,6) NOT NULL DEFAULT 0,
    output_price       numeric(14,6) NOT NULL DEFAULT 0,
    last_test_at       timestamp NULL,
    last_test_result   jsonb NULL,
    created_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted         boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_model_config_name ON ai_model_config (name) WHERE is_deleted = FALSE;
CREATE INDEX idx_ai_model_config_enabled ON ai_model_config (enabled, priority);
```

### 2.4 向量 API 配置表（ai_embedding_config，单例）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| provider | varchar(30) | NOT NULL | 供应商类别，取值同 2.3 `provider` |
| base_url | varchar(500) | NOT NULL | 向量端点（独立于生成模型，可来自不同供应商） |
| api_key_encrypted | varchar(500) | NOT NULL | 密钥密文（永不回显，仅支持替换） |
| embedding_model | varchar(100) | NOT NULL | 嵌入模型标识 |
| dimensions | int | NOT NULL | 向量维度（决定 `ai_vector_index.embedding` 的列维度） |
| operator | varchar(20) | NOT NULL, DEFAULT 'cosine' | 距离算子：`cosine / l2 / inner_product` |
| index_type | varchar(20) | NOT NULL, DEFAULT 'hnsw' | 索引类型：`hnsw / ivfflat` |
| enabled | boolean | NOT NULL, DEFAULT FALSE | 启停；未启用则 RAG 与相似检测不可用 |
| versions | jsonb | NOT NULL, DEFAULT '[]' | 历史版本记录：`[{ version, embeddingModel, dimensions, operator, retiredAt }]` |
| last_test_at / last_test_result | timestamp / jsonb | NULL | 最近连通性测试 |

**索引**（1 个）：`uk_ai_embedding_config_singleton` UNIQUE ((true)) WHERE is_deleted = FALSE。

```sql
CREATE TABLE ai_embedding_config (
    id                uuid PRIMARY KEY,
    provider          varchar(30) NOT NULL,
    base_url          varchar(500) NOT NULL,
    api_key_encrypted varchar(500) NOT NULL,
    embedding_model   varchar(100) NOT NULL,
    dimensions        int NOT NULL,
    operator          varchar(20) NOT NULL DEFAULT 'cosine',
    index_type        varchar(20) NOT NULL DEFAULT 'hnsw',
    enabled           boolean NOT NULL DEFAULT FALSE,
    versions          jsonb NOT NULL DEFAULT '[]',
    last_test_at      timestamp NULL,
    last_test_result  jsonb NULL,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_embedding_config_singleton ON ai_embedding_config ((true)) WHERE is_deleted = FALSE;
```

> **维度变更是重建级变更**（见 4.4）：`ai_vector_index.embedding` 为固定维度列，维度 / 算子变更后旧数据不可继续写入同列——变更时旧版本追加进 `versions`，检索临时置不可用（1000018122），由全量重建任务用新维度重嵌全部实体后恢复。

### 2.5 场景提示词表（ai_prompt_template）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| scene | varchar(50) | NOT NULL, UNIQUE | 场景编码（如 `requirement_split`、`test_design_generation`、`case_complete`），与任务 `type` 对应 |
| name | varchar(100) | NOT NULL | 场景名称（展示用） |
| content | text | NOT NULL | 模板正文，支持 `{{variable}}` 占位 |
| variables | jsonb | NOT NULL, DEFAULT '[]' | 可用变量清单 `[{ name, desc, required }]`，保存时校验占位与清单一致 |
| source | varchar(20) | NOT NULL, DEFAULT 'custom' | `default`（内置默认）/ `custom`（自定义覆盖） |
| version | int | NOT NULL, DEFAULT 1 | 自定义版本号，重置后归 1 |
| updated_by | uuid | NULL | 最近更新人（交互「更新人」列，按用户表回填展示名） |

**索引**（1 个）：`uk_ai_prompt_template_scene` UNIQUE (scene) WHERE is_deleted = FALSE。

```sql
CREATE TABLE ai_prompt_template (
    id         uuid PRIMARY KEY,
    scene      varchar(50) NOT NULL,
    name       varchar(100) NOT NULL,
    content    text NOT NULL,
    variables  jsonb NOT NULL DEFAULT '[]',
    source     varchar(20) NOT NULL DEFAULT 'custom',
    version    int NOT NULL DEFAULT 1,
    updated_by uuid NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_prompt_template_scene ON ai_prompt_template (scene) WHERE is_deleted = FALSE;
```

- 无行即用内置默认模板（代码常量）；自定义覆盖仅影响该场景**后续**调用，不改变已生成产物；`reset` 将行恢复为 `source = default`、`version` 归 1（或删除自定义行）。

### 2.6 AI 任务表（ai_task，统一任务收口）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| type | varchar(30) | NOT NULL | 任务类型，全量枚举见 3.6.1 |
| status | varchar(20) | NOT NULL, DEFAULT 'pending' | `pending / running / succeeded / failed / cancelled` |
| progress | int | NOT NULL, DEFAULT 0 | 进度 0–100 |
| phase | varchar(50) | NULL | 当前阶段（如「生成脑图文档」），进度页阶段展示用 |
| project_id | uuid | NULL | 项目内任务的隔离归属（NULL = 不限项目的个人任务，如助手解析） |
| workspace_id | uuid | NULL | 执行作用域（助手类任务经 `X-Active-Workspace` 头写入），RAG 限权过滤依据 |
| submitted_by | uuid | NOT NULL | 发起人：任务中心「我的任务」、完成通知与重试的归属 |
| prompt_scene | varchar(50) | NULL | 实际使用的提示词场景（关联 `ai_prompt_template.scene`），用量按场景归因 |
| model_id | uuid | NULL | 实际调用的模型（关联 `ai_model_config` 稳定 ID），**失败重试复用同模型** |
| input | jsonb | NOT NULL | 任务输入：源引用 + 参数（存引用不复制全文） |
| result | jsonb | NULL | **产物明细**（单一事实源），确认/驳回只指回这里 |
| tokens_in | int | NOT NULL, DEFAULT 0 | 任务级 token 汇总 |
| tokens_out | int | NOT NULL, DEFAULT 0 | 任务级 token 汇总 |
| error_code | int | NULL | 失败业务错误码（10 位） |
| error_msg | varchar(500) | NULL | 失败原因摘要 |
| retry_of_task_id | uuid | NULL | 手动重试时指向原任务（重试保留原任务记录） |

**索引**（3 个）：

- `idx_ai_task_project` (project_id, created_at) —— 项目任务中心列表；
- `idx_ai_task_submitter` (submitted_by, created_at) —— 「我的任务」列表与完成通知；
- `idx_ai_task_status` (status, updated_at) —— 超时清扫器扫描。

```sql
CREATE TABLE ai_task (
    id               uuid PRIMARY KEY,
    type             varchar(30) NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'pending',
    progress         int NOT NULL DEFAULT 0,
    phase            varchar(50) NULL,
    project_id       uuid NULL,
    workspace_id     uuid NULL,
    submitted_by     uuid NOT NULL,
    prompt_scene     varchar(50) NULL,
    model_id         uuid NULL,
    input            jsonb NOT NULL,
    result           jsonb NULL,
    tokens_in        int NOT NULL DEFAULT 0,
    tokens_out       int NOT NULL DEFAULT 0,
    error_code       int NULL,
    error_msg        varchar(500) NULL,
    retry_of_task_id uuid NULL,
    created_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ai_task_project ON ai_task (project_id, created_at);
CREATE INDEX idx_ai_task_submitter ON ai_task (submitted_by, created_at);
CREATE INDEX idx_ai_task_status ON ai_task (status, updated_at);
```

### 2.7 产物确认记录表（ai_artifact_confirm）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| project_id | uuid | NULL | 落库目标项目（确认列表过滤），经请求头上报 |
| task_id | uuid | NOT NULL | 所属任务 |
| artifact_key | varchar(100) | NOT NULL | 产物在 `result` 中的定位键 |
| action | varchar(20) | NOT NULL | `adopted / adopted_edited / rejected` |
| operator_id | uuid | NOT NULL | 确认操作人（审计：AI 产物必经人工） |
| adopted_ref | jsonb | NULL | 采纳落库后的目标实体引用（新建 ID、变更版本），反查"这条用例来自哪个产物" |
| note | varchar(500) | NULL | 驳回 / 编辑原因 |

**索引**（2 个）：

- `uk_ai_artifact_confirm` UNIQUE (task_id, artifact_key) WHERE is_deleted = FALSE —— 同一产物仅一条确认记录，重复提交幂等拦截（1000018113）；
- `idx_ai_artifact_confirm_project` (project_id, created_at) —— 跨任务审核列表。

```sql
CREATE TABLE ai_artifact_confirm (
    id           uuid PRIMARY KEY,
    project_id   uuid NULL,
    task_id      uuid NOT NULL,
    artifact_key varchar(100) NOT NULL,
    action       varchar(20) NOT NULL,
    operator_id  uuid NOT NULL,
    adopted_ref  jsonb NULL,
    note         varchar(500) NULL,
    created_at   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted   boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_artifact_confirm ON ai_artifact_confirm (task_id, artifact_key) WHERE is_deleted = FALSE;
CREATE INDEX idx_ai_artifact_confirm_project ON ai_artifact_confirm (project_id, created_at);
```

### 2.8 用量明细表（ai_usage_log）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| project_id | uuid | NULL | 项目内调用的统计维度；管理端测试等全局调用为 NULL |
| task_id | uuid | NULL | 关联任务（统计 → 单次调用 → 任务详情的下钻链）；助手交互调用为 NULL |
| user_id | uuid | NOT NULL | 调用者 |
| model_id | uuid | NULL | 调用的模型，**按模型分组统计**维度；向量 API 调用无模型配置行，为 NULL |
| prompt_scene | varchar(50) | NULL | 场景归因，按场景统计维度 |
| call_type | varchar(20) | NOT NULL | `chat / embedding`（向量重建同样计用量） |
| prompt_tokens / completion_tokens / total_tokens | int | NOT NULL, DEFAULT 0 | token 消耗 |
| latency_ms | int | NOT NULL, DEFAULT 0 | 单次调用耗时 |
| status | varchar(20) | NOT NULL | `success / failed` |
| error_code | int | NULL | 失败归因 |
| cost | numeric(14,6) | NOT NULL, DEFAULT 0 | 本次调用成本 = `prompt_tokens / 1e6 × input_price + completion_tokens / 1e6 × output_price`（单价取调用模型的当时配置，4.3；未配置单价恒 0） |

**索引**（3 个）：`idx_ai_usage_log_project` (project_id, created_at)、`idx_ai_usage_log_model` (model_id, created_at)、`idx_ai_usage_log_task` (task_id)。

```sql
CREATE TABLE ai_usage_log (
    id                uuid PRIMARY KEY,
    project_id        uuid NULL,
    task_id           uuid NULL,
    user_id           uuid NOT NULL,
    model_id          uuid NULL,
    prompt_scene      varchar(50) NULL,
    call_type         varchar(20) NOT NULL,
    prompt_tokens     int NOT NULL DEFAULT 0,
    completion_tokens int NOT NULL DEFAULT 0,
    total_tokens      int NOT NULL DEFAULT 0,
    latency_ms        int NOT NULL DEFAULT 0,
    status            varchar(20) NOT NULL,
    error_code        int NULL,
    cost              numeric(14,6) NOT NULL DEFAULT 0,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ai_usage_log_project ON ai_usage_log (project_id, created_at);
CREATE INDEX idx_ai_usage_log_model ON ai_usage_log (model_id, created_at);
CREATE INDEX idx_ai_usage_log_task ON ai_usage_log (task_id);
```

> 概要设计中的 `AiUsageStat` 为**统计口径实体**，落地为本明细表 + 聚合查询（3.7），不建预聚合表。

### 2.9 助手表（ai_assistant_conversation / ai_assistant_message）

**ai_assistant_conversation（会话，按登录用户归属）**

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| title | varchar(100) | NOT NULL | 会话标题（首问自动生成，可重命名） |
| user_id | uuid | NOT NULL | **归属人 = 唯一隔离维度**：列表、可见性、权限校验只看它；仅本人可见可操作 |
| status | varchar(20) | NOT NULL, DEFAULT 'active' | `active / archived` |
| context_snapshot | jsonb | NULL | 会话创建时的活跃工作空间与页面实体引用：默认上下文、预览来源展示、消息 `attachments` 兜底；**不参与权限判定** |

**索引**（1 个）：`idx_ai_assistant_conversation_user` (user_id, created_at)。

**ai_assistant_message（消息）**

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| conversation_id | uuid | NOT NULL | 所属会话（逻辑外键） |
| role | varchar(20) | NOT NULL | `user / assistant / system` |
| content | text | NULL | 消息正文（Markdown）；流式结束后落盘，断线重连续读 |
| attachments | jsonb | NULL | 用户选中的上下文实体引用集合（可含所属项目标识，属数据引用非 API 上下文） |
| intent | jsonb | NULL | 意图解析结构化预览：动作、目标、字段级变更、影响数量 |
| citations | jsonb | NULL | 来源引用集合（只读问答与预览附带的可跳转引用） |
| execution | jsonb | NULL | 执行回执：`previewed → executed / rejected`、逐项成功失败、执行人与时间 |
| status | varchar(20) | NOT NULL, DEFAULT 'done' | `streaming / done / interrupted / error` |

**索引**（1 个）：`idx_ai_assistant_message_conversation` (conversation_id, created_at)。

```sql
CREATE TABLE ai_assistant_conversation (
    id               uuid PRIMARY KEY,
    title            varchar(100) NOT NULL,
    user_id          uuid NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'active',
    context_snapshot jsonb NULL,
    created_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ai_assistant_conversation_user ON ai_assistant_conversation (user_id, created_at);

CREATE TABLE ai_assistant_message (
    id              uuid PRIMARY KEY,
    conversation_id uuid NOT NULL,
    role            varchar(20) NOT NULL,
    content         text NULL,
    attachments     jsonb NULL,
    intent          jsonb NULL,
    citations       jsonb NULL,
    execution       jsonb NULL,
    status          varchar(20) NOT NULL DEFAULT 'done',
    created_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ai_assistant_message_conversation ON ai_assistant_message (conversation_id, created_at);
```

### 2.10 向量索引表（ai_vector_index，pgvector）

| 字段 | 类型 | 约束 | 说明 |
| ---- | ---- | ---- | ---- |
| project_id | uuid | NOT NULL | **业务归属之一**：先按项目过滤再做向量检索，禁止全库比对 |
| entity_type | varchar(30) | NOT NULL | **业务归属之二**：`requirement / test_case / mindmap_node / bug / review_comment` |
| entity_id | uuid | NOT NULL | 业务实体 ID（逻辑外键，重建时定位源） |
| chunk_index | int | NOT NULL, DEFAULT 0 | 分块序号（同实体多块向量） |
| content | text | NOT NULL | 嵌入原文分块（命中后直接回显上下文） |
| embedding | vector | NOT NULL | 向量本体，列维度取 `ai_embedding_config.dimensions`（建表时以 `vector(n)` 生成） |
| embedding_version | varchar(64) | NOT NULL | 生成时的「模型 + 维度 + 算子」版本标识，审计与重建判断依据 |
| indexed_at | timestamp | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 最近重建时间 |

**索引**（3 个）：

- `uk_ai_vector_index_chunk` UNIQUE (entity_type, entity_id, chunk_index) WHERE is_deleted = FALSE；
- `idx_ai_vector_index_scope` (project_id, entity_type) —— **业务归属过滤先行**；
- `idx_ai_vector_index_embedding` USING hnsw (embedding vector_cosine_ops) —— 向量检索（算子按 2.4 `operator` 取 `vector_cosine_ops / vector_l2_ops / vector_ip_ops`）。

```sql
-- n = ai_embedding_config.dimensions（部署时以配置值生成；变更走 4.4 全量重建）
CREATE TABLE ai_vector_index (
    id               uuid PRIMARY KEY,
    project_id       uuid NOT NULL,
    entity_type      varchar(30) NOT NULL,
    entity_id        uuid NOT NULL,
    chunk_index      int NOT NULL DEFAULT 0,
    content          text NOT NULL,
    embedding        vector({n}) NOT NULL,
    embedding_version varchar(64) NOT NULL,
    indexed_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_vector_index_chunk ON ai_vector_index (entity_type, entity_id, chunk_index) WHERE is_deleted = FALSE;
CREATE INDEX idx_ai_vector_index_scope ON ai_vector_index (project_id, entity_type);
CREATE INDEX idx_ai_vector_index_embedding ON ai_vector_index USING hnsw (embedding vector_cosine_ops);
```

---

## 3. 接口详细设计

### 3.1 通用约定

- 基础路径：`/api/ai`（`docs/02-high-level-design/04-hld-data-interface.md` 2.1 已登记）：
  - **管理配置与用量**（3.2–3.5、3.7）：需系统角色 + `ai:admin` 权限，**不携带**工作空间 / 项目上下文头；
  - **任务与助手**（3.6、各分册）：需 `ai:task` / `ai:confirm` 权限；涉及业务数据时按需附 `X-Active-Workspace` / `X-Active-Project`（C4，上下文不出 URL）。
- 响应使用平台通用 `Result<T>`，下文示例**仅展示 `data` 字段**；分页 `pageNo` / `pageSize` → `{ list, total }`；字段 camelCase。
- 密钥字段（`apiKey`）**写入可用、读取永不返回**，列表与详情仅返回 `keyConfigured` 布尔。
- 任务是全部 AI 执行的唯一收口：能力域分册的业务动作统一映射到 3.6 的任务资源，不另设平行的 AI 执行端点。

### 3.2 总开关与全局设置

- **查询**：`GET /api/ai/settings`（`ai:admin`）

```json
{
  "enabled": false,
  "defaultModelId": "…",
  "defaultModelName": "gpt-x",
  "taskTimeoutSeconds": 600,
  "taskMaxRetries": 2,
  "modelReady": true,
  "embeddingReady": false,
  "available": true
}
```

  - `modelReady / embeddingReady / available` 为入口可见性依据：`available = enabled && modelReady`；RAG 类能力额外要求 `embeddingReady`。

- **业务端可用性**：`GET /api/ai/status`（登录即可，无需 `ai:admin`）→ `{ "enabled": true, "modelReady": true, "available": true }`
  - 仅回这三个布尔口径（`available = enabled && modelReady`），供业务端入口显隐与覆盖状态列「—」展示；不返回任何配置明细或模型信息。

- **更新**：`PUT /api/ai/settings`（部分更新）

```json
{ "enabled": true, "defaultModelId": "…", "taskTimeoutSeconds": 900 }
```

  - 校验：`defaultModelId` 须存在且启用（否则 1000018104）；`taskTimeoutSeconds ∈ [30, 3600]`、`taskMaxRetries ∈ [0, 5]`（超出由参数校验统一拒绝）；
  - **开关关闭的副作用**（4.2）：`running / pending` 任务批量置 `failed`（`error_msg = "AI 总开关关闭"`）；`available` 变为 false 后业务端入口隐藏。

### 3.3 模型配置

- **列表**：`GET /api/ai/models`

```json
{
  "list": [
    {
      "id": "…",
      "name": "gpt-x",
      "provider": "openai",
      "baseUrl": "https://api.example.com/v1",
      "modelName": "gpt-x-mini",
      "capabilities": ["chat", "vision"],
      "priority": 100,
      "enabled": true,
      "inputPrice": 0.5,
      "outputPrice": 1.5,
      "keyConfigured": true,
      "lastTest": { "success": true, "latencyMs": 420, "at": "2026-10-02T08:00:00Z" }
    }
  ],
  "total": 3
}
```

- **创建**：`POST /api/ai/models`

```json
{
  "name": "gpt-x",
  "provider": "openai",
  "baseUrl": "https://api.example.com/v1",
  "apiKey": "sk-…",
  "modelName": "gpt-x-mini",
  "capabilities": ["chat", "vision"],
  "priority": 100,
  "enabled": true,
  "inputPrice": 0.5,
  "outputPrice": 1.5
}
```

  - 校验：`name` 唯一（冲突 1000018103）；`baseUrl` 合法 URL；`apiKey / modelName` 必填；`capabilities ⊆ {chat, vision, embedding}`；`inputPrice / outputPrice ≥ 0`（否则 1000018103）。
- **更新**：`PUT /api/ai/models/{modelId}`（部分更新；`apiKey` 传入即替换、不传保持原值）。
- **删除**：`DELETE /api/ai/models/{modelId}`（逻辑删除）——被 `default_model_id` 引用时拒绝（1000018104），须先切换默认模型。
- **连通性测试**：`POST /api/ai/models/{modelId}/test`

```json
{ "success": true, "latencyMs": 420, "msg": "ok" }
```

  - 以该配置发起最小 chat 调用（不计业务用量，计入 `ai_usage_log.call_type = chat`、`task_id = NULL`）；失败返回 1000018105，响应含失败摘要。

### 3.4 向量 API 配置

- **查询**：`GET /api/ai/embedding`

```json
{
  "provider": "openai",
  "baseUrl": "https://api.example.com/v1",
  "embeddingModel": "text-embedding-x",
  "dimensions": 1536,
  "operator": "cosine",
  "indexType": "hnsw",
  "enabled": true,
  "keyConfigured": true,
  "versions": [],
  "requiresReindex": false,
  "lastTest": null
}
```

- **保存（单例创建或更新）**：`PUT /api/ai/embedding`

```json
{
  "provider": "openai",
  "baseUrl": "https://api.example.com/v1",
  "apiKey": "sk-…",
  "embeddingModel": "text-embedding-x",
  "dimensions": 1536,
  "enabled": true
}
```

  - 校验：`dimensions ∈ [64, 4096]`；**`dimensions / operator` 相对原值发生变化** → 旧配置追加进 `versions`，响应 `requiresReindex = true`，由前端引导发起全量重建任务（`type = vector_reindex`）；
- **连通性测试**：`POST /api/ai/embedding/test` → `{ success, dimensions, latencyMs, msg }`；失败返回 1000018107。

### 3.5 场景提示词

- **列表**：`GET /api/ai/prompts`（按 `scene` 升序）
- **详情**：`GET /api/ai/prompts/{scene}` → `{ scene, name, content, variables, source, version, updatedAt }`（无自定义行时返回内置默认内容，`source = default`）。
- **保存**：`PUT /api/ai/prompts/{scene}`

```json
{ "content": "你是测试设计助手，基于需求 {{requirementText}} 生成用例…" }
```

  - 校验：场景已登记（否则 1000018108）；`content` 中全部 `{{变量}}` 必须在 `variables` 清单内且必填变量齐全（否则 1000018109）；保存后 `source = custom`、`version + 1`。
- **重置**：`POST /api/ai/prompts/{scene}/reset` → 恢复内置默认。
- 生效规则：仅影响该场景**后续**任务调用，不改变已生成产物。

### 3.6 统一任务资源

> 生成链、拆解、覆盖 / 影响分析、圈选推荐、用例补全、级别 / 顺序建议、缺陷批量分析、助手解析等**全部 AI 执行**均通过本资源提交与管理；各分册只定义 `type` 取值、`input` 结构与产物语义。

#### 3.6.1 任务类型枚举（全量，单一事实源）

| type | 能力域 | input 要点 | 产物 kind | 落库承接 |
| ---- | ---- | ---- | ---- | ---- |
| requirement_import | 需求 | `fileId` | `requirement_suggestion` | 需求模块采纳服务 |
| requirement_split | 需求 | `requirementId` | `requirement_suggestion` | 需求模块采纳服务 |
| test_design_generation | 生成链 | `requirementIds`、`targetModuleId?`、`granularity` | `module_suggestion / mindmap_document_suggestion / test_case_suggestion` | 生成链采纳服务 |
| review_selection | 生成链 | `scope` | `review_selection` | 评审创建流程 |
| plan_selection | 生成链 | `scope`、`rounds?` | `plan_selection` | 计划创建流程 |
| coverage_analysis | 矩阵 | `requirementIds?` | 无（直接写 `trace_coverage_result`，4.3） | 矩阵服务 |
| impact_analysis | 矩阵 | `requirementId` | 无（写影响标记） | 矩阵服务 |
| case_complete | 辅助 | `nodeIds` | `case_suggestion` | 用例采纳服务 |
| case_priority | 辅助 | `nodeIds` | `priority_suggestion` | 用例采纳服务 |
| plan_order | 辅助 | `planId` | `order_suggestion` | 计划排序采纳 |
| bug_classify | 缺陷 | `bugIds?` / 筛选 | `classify_suggestion` | 缺陷采纳服务 |
| bug_duplicate_scan | 缺陷 | `scope` | `duplicate_group` | 人工按重复缺陷机制处理 |
| bug_triage | 缺陷 | 无 | `triage_order`（只读建议，无确认） | 不落库 |
| bug_trend_summary | 缺陷 | `from / to / groupBy` | `summary`（只读，无确认） | 不落库 |
| assistant_parse | 助手 | `messageId` | `intent_preview / answer` | 助手执行接口（分册 04） |
| vector_reindex | 底座（系统） | `scope?` | 无 | 向量索引重建 |

#### 3.6.2 提交任务

- **路径**：`POST /api/ai/tasks`（权限：`ai:task` + 对应资源权限）
- **请求**：

```json
{
  "type": "test_design_generation",
  "input": { "requirementIds": ["…"], "granularity": "standard" },
  "waitSeconds": 0
}
```

- **参数**：`type` 必填（枚举见 3.6.1，否则 1000018114）；`input` 按 type 分发校验（否则 1000018115）；`waitSeconds ∈ [0, 10]`（否则 1000018123）——**同步等待**：任务在等待期内完成则响应直接携带 `result / artifacts`，超时返回 `pending` 转轮询（交互式建议的快路径，如新建缺陷建议、重复检测）。
- **响应**：

```json
{
  "taskId": "…",
  "type": "test_design_generation",
  "status": "pending",
  "progress": 0,
  "phase": null,
  "retryOfTaskId": null,
  "result": null,
  "artifacts": null
}
```

- **校验顺序**：总开关（1000018101）→ 权限（1000018116）→ 类型 → 输入 → 可用模型（1000018118）；RAG 依赖型任务在向量不可用时拒绝（1000018119 / 1000018122）。

#### 3.6.3 任务列表与详情

- **列表**：`GET /api/ai/tasks`
  - 参数：`type`、`status`、`projectId` 不传（项目上下文经 `X-Active-Project` 头过滤，未附带则返回本人提交的任务）、`pageNo`、`pageSize`。
  - 响应列表项：`{ taskId, type, status, progress, phase, submittedBy, retryOfTaskId, tokensIn, tokensOut, createdAt, error }`。
- **详情**：`GET /api/ai/tasks/{taskId}`
  - `status = succeeded` 时附产物清单摘要：

```json
{
  "taskId": "…",
  "type": "test_design_generation",
  "status": "succeeded",
  "progress": 100,
  "phase": null,
  "submittedBy": "…",
  "tokensIn": 1200,
  "tokensOut": 3400,
  "error": null,
  "artifacts": [
    { "key": "module-1", "kind": "module_suggestion", "title": "登录模块", "parentKey": null, "confirmStatus": "pending" },
    { "key": "doc-1", "kind": "mindmap_document_suggestion", "title": "登录用例文档", "parentKey": "module-1", "confirmStatus": "pending" },
    { "key": "case-3", "kind": "test_case_suggestion", "title": "获取验证码", "parentKey": "doc-1", "confirmStatus": "adopted" }
  ]
}
```

- **产物详情**：`GET /api/ai/tasks/{taskId}/artifacts/{artifactKey}` → 单个产物的完整内容（审核预览用，内容结构由各分册定义）。

#### 3.6.4 取消与重试

- **取消**：`POST /api/ai/tasks/{taskId}/cancel` —— 仅 `pending / running` 可取消（否则 1000018111）；取消任务不产生任何落库数据与追溯边。响应为置 `cancelled` 后的任务对象。
- **重试**：`POST /api/ai/tasks/{taskId}/retry` —— 仅 `failed` 可重试；**保留原任务记录**，创建新任务复制 `input`、`model_id = 原 model_id`（复用同模型），`retry_of_task_id` 指向原任务；响应新任务对象。

#### 3.6.5 产物确认（采纳 / 驳回）

- **路径**：`POST /api/ai/tasks/{taskId}/artifacts/confirm`（权限：`ai:confirm`）
- **请求**（批量，部分采纳支持逐条动作与编辑内容；落库目标项目经 `X-Active-Project` 头传递，不出请求体）：

```json
{
  "items": [
    { "key": "module-1", "action": "adopted" },
    { "key": "case-3", "action": "adopted_edited", "content": { "priority": "high" } },
    { "key": "case-4", "action": "rejected", "note": "与 TC-002 重复" }
  ],
  "target": { "moduleId": "…", "position": "child" }
}
```

- **响应**（回执式，整体 200，逐项成败；请求级错误才返回错误码）：

```json
{
  "results": [
    { "key": "module-1", "action": "adopted", "success": true, "createdId": "…" },
    { "key": "case-3", "action": "adopted_edited", "success": true, "createdId": "…" },
    { "key": "case-4", "action": "rejected", "success": false, "errorCode": 1000018113, "errorMsg": "产物已确认，不可重复操作" }
  ]
}
```

- **校验与事务**：
  - 任务须 `succeeded`（否则 1000018111）、产物存在（1000018112）、未确认过（1000018113，`uk_ai_artifact_confirm` 兜底）；
  - **逐项事务**：每项「确认记录 + 业务落库」在同一事务，失败仅该项回滚，其余项继续（部分失败，成功项保留）；
  - 落库由 3.6.1 的承接服务执行；采纳写 `adopted_ref`，驳回写 `note`；每次动作记审计。

### 3.7 用量统计

- **聚合**：`GET /api/ai/usage/statistics`（`ai:admin`）
- **参数**：

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| from / to | date | 否 | 时间范围（UTC），默认最近 30 天；`to - from ≤ 366 天`（否则 1000018120） |
| groupBy | string | 否 | `day`（默认）/ `model` / `scene` / `callType` |

- **响应**：

```json
{
  "summary": { "totalCalls": 1523, "failedCalls": 31, "successRate": 0.98, "totalTokens": 4210000, "avgLatencyMs": 1830, "totalCost": 12.34 },
  "series": [
    { "key": "2026-10-01", "calls": 60, "failed": 1, "tokens": 180000, "avgLatencyMs": 1700, "cost": 0.42 }
  ]
}
```

  - `groupBy = model` 时 `key` 形如 `modelId`，响应附 `keyName`（模型名）；`scene` 同理。

- **下钻**：`GET /api/ai/usage/tasks`
  - 参数：`from / to`、`modelId`、`scene`、`status`、`callType`（`chat / embedding`，按调用类型下钻）、`pageNo`、`pageSize`；
  - 响应列表项：`{ taskId, type, status, modelName, scene, totalTokens, latencyMs, createdAt }`，点击跳任务详情（3.6.3）。

### 3.8 权限点（新增）

| code | 名称 | scope | 用于 |
| ---- | ---- | ---- | ---- |
| ai:admin | AI 配置与用量管理 | global（系统角色） | 3.2–3.5、3.7 |
| ai:task | 发起与管理 AI 任务 | workspace | 3.6.2–3.6.4 |
| ai:confirm | AI 产物确认 | workspace | 3.6.5 |

---

## 4. 业务逻辑设计

### 4.1 配置生效与密钥安全

- 配置读取带短 TTL 缓存（≤ 30s）或写后失效通知；任务提交时以最新配置解析模型与提示词（`prompt_scene / model_id` 回写任务，保证用量可归因）。
- `api_key_encrypted` 以 AES 加密落库（密钥管理沿用平台既有密钥派生机制）；接口、日志、审计一律脱敏，只记录"已配置 / 已替换"事件。
- 全部配置写操作记审计（操作人、时间、配置项、变更摘要，不含密钥值）。

### 4.2 任务引擎

- **分发**：`type → TaskHandler`（每个 handler 注册：input 校验器、执行器、进度/阶段上报、产物抽取器、落库承接器）；执行在平台既有异步执行能力上运行，不引入新的外部中间件依赖。
- **生命周期**：`pending → running → succeeded / failed`，`pending / running → cancelled`；`progress / phase` 由 handler 上报；超时清扫器按 `task_timeout_seconds` 扫描 `status + updated_at` 索引（2.6），超时置 `failed`（`error_code = 1000018117`）。
- **同步等待**：`waitSeconds > 0` 时提交线程等待任务完成，超时返回进行中状态；等待不改变异步语义。
- **失败与重试**：模型调用失败先按 `task_max_retries` 自动短重试（同 `model_id`），耗尽后置 `failed`；用户手动重试走 3.6.4（新任务）。
- **总开关联动**：开关关闭 → 新提交拒绝（1000018101）；存量 `pending / running` 批量置 `failed`（原因「AI 总开关关闭」）；任务与产物数据保留可查。
- **审计**：提交、取消、重试、确认、驳回全部记审计（含任务 ID、产物键、操作人）。

### 4.3 用量记录

- 每次模型 / 向量调用（成功与失败）写一条 `ai_usage_log`：延迟为调用端到端耗时，token 以响应账单为准（缺省按字符估算并标记）；任务级汇总同步累加到 `ai_task.tokens_in / tokens_out`。
- 成本 `cost` 按调用模型的当时单价计：`prompt_tokens / 1e6 × input_price + completion_tokens / 1e6 × output_price`，连通性测试等无任务调用同样计价；单价未配置（0）时 `cost = 0`。
- 任务的多次调用（解析、生成、后处理）各记一条明细并共享 `task_id`；助手交互调用 `task_id = NULL`、`prompt_scene` 归因到助手场景。
- 统计查询按 UTC 日期分组；失败率 = `failed / total`；空区间补 0（前端图表不出现断点）。

### 4.4 RAG 与向量索引维护

- **写侧**：业务实体变更事件（需求保存、用例保存、缺陷保存等）触发分块重嵌——按实体维度 upsert（`entity_type + entity_id` 旧分块先逻辑删除）；归档与逻辑删除的实体**移出索引**，不再被检索。
- **读侧（限权）**：检索先按业务归属过滤——项目内任务取 `project_id = X-Active-Project`；助手类任务取活跃工作空间的项目集（`workspace_id` → 项目集）——**再**执行向量相似检索；无权限数据不得进入任何 AI 上下文。
- **维度 / 算子变更**：置向量能力不可用（查询返回 1000018122）→ 提交 `vector_reindex` 全量重建（新 `embedding_version`）→ 完成后恢复；重建期间依赖检索的能力提示不可用，**不降级为无引用生成**。

### 4.5 降级与容错

- 模型不可用 / 超时 / 限流 → 任务 `failed` 并给出原因（`1000018117`），可重试；配置页连通性测试失败给出摘要与排查提示。
- 向量 API 未配置或未启用 → `embeddingReady = false`，RAG 问答、相似检测、覆盖分析等依赖检索的能力入口不可用并说明原因。
- 总开关关闭 → `available = false`，业务端 AI 入口隐藏；既有数据（产物、确认记录、追溯关系）全部保留可查。

---

## 5. 前端设计

### 5.1 管理端路由（AI 配置中心）

```
/admin
└── /ai                        → AiSettingsPage（AI 配置与用量，/admin/ai 重定向 /admin/ai/models）
    ├── 页头全局项（SettingsSwitch、DefaultModelSelect）与重建引导条（requiresReindex 全宽横幅），跨分组常驻
    ├── /models     模型配置区块（ModelListTable + ModelEditDialog + 连通性测试）
    ├── /embedding  向量 API 区块（EmbeddingForm + 维度变更重建引导）
    ├── /prompts    场景提示词区块（ScenePromptTable + PromptEditor + 重置）
    └── /usage      用量分析区块（UsageRangePicker + UsageChart + TaskDrillTable）
```

### 5.2 业务端组件与状态

```
任务中心（项目工作区 /workspace/projects/ai/tasks → AiTaskCenterPage）
├── TaskFilter（类型/状态筛选）
└── TaskTable（进度条、阶段、失败原因、取消/重试，「详情 / 审核」跳转详情页）

任务详情页（/workspace/projects/ai/tasks/:taskId → AiTaskDetailPage）
├── TaskProgress（进度条、阶段时间线、产物计数、取消 / 重试）
└── AiArtifactReviewPanel（通用产物审核区，各能力域复用，succeeded 且有产物时渲染）
    ├── ArtifactTree（产物树，kind 图标区分）
    ├── ArtifactDetail（内容预览 / 与既有数据对比）
    └── Actionbar（单条采纳、整树采纳、编辑后采纳、驳回、批量操作）
```

- Pinia store：
  - `aiAdmin`：设置、模型、向量、提示词、用量（仅管理端装载）；
  - `aiTask`：任务轮询（详情 `GET /api/ai/tasks/{id}` 按 2s 间隔轮询，`terminal` 态停止）、任务中心列表、产物确认状态；
- 密钥输入框：`keyConfigured = true` 时显示占位「已配置（输入新值以替换）」，提交为空则不修改。

### 5.3 状态分支

- 配置未就绪（`available = false`）：业务端 AI 入口隐藏或置灰并提示"AI 能力未启用/未配置模型"；
- 任务态：排队（等待动画）、进行中（进度 + 阶段）、失败（原因 + 重试）、已取消（灰态）；
- 用量空区间补 0、图表加载与无数据态；向量重建中的 `requiresReindex` 引导条。

---

## 6. 错误码定义

号段申请：**1000018101–1000018150（AI 配置与任务引擎）**，登记于 `docs/04-detailed-design/02-project-module.md` 6.3；以 `ErrorCodeConstants` 实际登记为准，冲突时顺延。各能力域分册错误码号段见其对应章节（生成链 201–249、助手 251–279、缺陷分析 281–299、辅助功能 301–329）。

| 错误码 | HTTP | 说明 |
| ---- | ---- | ---- |
| 1000018101 | 403 | AI 能力未启用（总开关关闭） |
| 1000018102 | 404 | 模型配置不存在 |
| 1000018103 | 400 | 模型配置参数非法 |
| 1000018104 | 409 | 默认模型引用冲突（不可删除或停用默认模型） |
| 1000018105 | 502 | 模型连通性测试失败 |
| 1000018106 | 400 | 向量 API 配置参数非法 |
| 1000018107 | 502 | 向量 API 连通性测试失败 |
| 1000018108 | 404 | 提示词场景不存在 |
| 1000018109 | 400 | 提示词模板校验失败（变量缺失或非法） |
| 1000018110 | 404 | 任务不存在 |
| 1000018111 | 409 | 任务状态不允许该操作 |
| 1000018112 | 404 | 任务产物不存在 |
| 1000018113 | 409 | 产物已确认，不可重复操作 |
| 1000018114 | 400 | 不支持的任务类型 |
| 1000018115 | 400 | 任务输入参数非法 |
| 1000018116 | 403 | 无 AI 能力使用权限 |
| 1000018117 | 500 | 模型服务调用失败（不可用、超时或限流） |
| 1000018118 | 400 | 未配置可用模型 |
| 1000018119 | 400 | 向量 API 未配置或未启用，检索能力不可用 |
| 1000018120 | 400 | 用量查询时间范围非法 |
| 1000018121 | 403 | 无 AI 配置管理权限 |
| 1000018122 | 409 | 向量索引不可用（全量重建中） |
| 1000018123 | 400 | 同步等待参数超限（0–10 秒） |

---

## 7. 实施说明

**新增 / 修改文件**

| 文件 | 说明 |
| ---- | ---- |
| `server/.../entity/ai/*`（10 实体）+ Mapper | 实体与数据访问 |
| `server/.../controller/ai/AiConfigController`、`AiModelController`、`AiEmbeddingController`、`AiPromptController`、`AiUsageController` | 管理配置路由（仅路由，C2） |
| `server/.../controller/ai/AiTaskController` | 任务资源路由 |
| `server/.../service/ai/config/*`、`service/ai/task/*`、`service/ai/rag/*` | 配置、任务引擎、向量索引服务 |
| `server/.../service/ai/task/handler/*TaskHandler` | 各 `type` 的执行器（分册实现，注册到引擎） |
| `server/.../framework/common/ErrorCodeConstants` | 登记 1000018101–1000018123 及各分册号段 |
| 权限点迁移脚本 | 新增 `ai:admin`（global）、`ai:task` / `ai:confirm`（workspace） |
| `web/src/pages/admin/AiSettingsPage.vue`、`web/src/pages/project/AiTaskCenterPage.vue`、`AiTaskDetailPage.vue` 及组件 | 管理端配置中心、任务中心与任务详情页 |
| `web/src/stores/aiAdmin.ts`、`aiTask.ts`、`web/src/services/ai.ts`、`web/src/types/ai.ts` | 状态、API 与类型 |

**数据库迁移说明（C5）**

```sql
-- 全部为新建表，完整 DDL 见 2.2–2.10；全量建库同步进 schema.sql
-- 向量列维度 n 以 ai_embedding_config 配置值生成（初始部署默认 1536）
CREATE TABLE ai_config / ai_model_config / ai_embedding_config / ai_prompt_template / ai_task / ai_artifact_confirm / ai_usage_log / ai_assistant_conversation / ai_assistant_message / ai_vector_index ( ... );

-- 存量库补列（首次建库随 schema.sql 直接含列，无需本迁移）
ALTER TABLE ai_model_config ADD COLUMN input_price numeric(14,6) NOT NULL DEFAULT 0;
ALTER TABLE ai_model_config ADD COLUMN output_price numeric(14,6) NOT NULL DEFAULT 0;
ALTER TABLE ai_usage_log ADD COLUMN cost numeric(14,6) NOT NULL DEFAULT 0;
```

- UUID 主键使用框架默认策略；无物理外键；单表索引数均 ≤ 5（C9）；
- 依赖：PostgreSQL + pgvector 扩展启用（`docs/00-spec/20-contracts/02-database.md` 既定决策）；未启用 pgvector 时仅向量索引表不可建，AI 配置、任务与用量功能不受影响（RAG 类能力按 4.5 降级）。

**OpenAPI**：接口随本次交付经 springdoc 暴露，分组 `ai`；`apiKey` 字段标注 writeOnly；`waitSeconds` 与 SSE（助手分册）单独标注。

**测试要点（C8 ≥ 70%）**

- 后端：单例配置唯一性、密钥不回显与替换语义、默认模型引用校验、任务状态机与超时清扫、同步等待超时转异步、确认逐项事务与幂等冲突、总开关关闭批量终止、用量聚合口径（空区间补 0）、向量限权过滤（跨工作空间不可检索）、维度变更重建流程；
- 前端：配置表单校验与密钥占位、任务轮询启停、产物审核批量操作、用量图表与下钻、`available = false` 各降级分支。

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-02 | 初始版本 |
| V1.0 | 2026-10-02 | 前端路由对齐全局导航约定，改为 /workspace/projects/ai/tasks |
| V1.0 | 2026-10-03 | AI 配置中心改为页头全局项 + 左侧分组导航 + 子路由（/admin/ai/{models,embedding,prompts,usage}） |
| V1.0 | 2026-10-03 | 任务详情抽屉与产物审核面板合并为任务详情页（/workspace/projects/ai/tasks/:taskId → AiTaskDetailPage） |
| V1.0 | 2026-10-03 | 新增业务端可用性查询 `GET /api/ai/status`（登录即可，三布尔口径），供业务端入口显隐 |
| V1.0 | 2026-10-04 | 用量成本口径落地：`ai_model_config` 补 `input_price / output_price` 单价、`ai_usage_log` 补 `cost`，模型接口与用量统计响应补对应字段 |
| V1.0 | 2026-10-04 | 勘误 2.8：`ai_usage_log.model_id` 约束改为 NULL——向量 API 调用按 4.3 记账但无模型配置行 |
| V1.0 | 2026-10-04 | 2.5 补 `updated_by` 列：场景提示词列表「更新人」列的数据来源 |
| V1.0 | 2026-10-04 | 3.7 下钻补 `callType` 筛选参数（`chat / embedding`），支撑按调用类型分组的图表下钻 |
