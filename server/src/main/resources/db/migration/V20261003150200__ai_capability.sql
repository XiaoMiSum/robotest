-- =============================================================================
-- V20261003150200__ai_capability.sql — AI 能力数据层迁移
-- 依据：docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md（2.2–2.10、3.8、5 实施说明）
--
-- 1. 变更目的与影响范围
--    建立 AI 能力十表（配置 / 模型 / 向量 API / 提示词 / 任务 / 产物确认 / 用量明细 /
--    助手会话 / 助手消息 / 向量索引）及索引；新增权限点 ai:admin（global）与
--    ai:task / ai:confirm（workspace）并授权至系统管理员与两个 workspace 预置角色。
--    清理旧版 AI 残留九表（已下线功能的孤儿表，代码零引用；ai_config / ai_prompt_template 与新表
--    同名必须先删除），其中 ai_prompt_template 含 16 行旧模板数据，已随执行前全量备份保存；
--    同时清理旧版 AI 权限三行（ai / ai:view / ai:edit，旧基线种子，现行 schema.sql 已移除）
--    并将系统管理员角色权限中的旧码 ai:view / ai:edit 替换为 ai:admin，与新基线收敛。
--    需要 pgvector 扩展（CREATE EXTENSION 须以超级用户执行，本库为 postgres）；
--    向量列初始维度 1536（与 ai_embedding_config.dimensions 初始配置一致，维度变更走详设 4.4 全量重建）。
--    影响：新建十表 + 删除九张孤儿表，对存量接口无破坏（存量接口不引用任何旧 AI 表）。
-- 2. 正向 DDL 与数据回填
--    本文件仅含 DDL 与种子变更，无业务数据回填；旧表数据不迁移，执行前按 runbook §9 备份。
-- 3. 回滚方案
--    依次 DROP 新十表（连同其索引）；从执行前备份恢复旧九表、旧权限三行
--    （ai / ai:view / ai:edit）与系统管理员角色的旧码；
--    删除权限行 a0000000-…-0023、c0000000-…-0079/0080/0081；
--    角色 permissions 中剔除 ai / ai:admin / ai:task / ai:confirm；
--    如无其他依赖可 DROP EXTENSION IF EXISTS vector（保留亦无副作用）。
-- 4. 兼容窗口与部署顺序
--    仅在缺少新表的存量库执行一次；先执行本脚本，再部署引用新表的服务端版本。
--    全新建库不执行本文件，直接使用 server/src/main/resources/db/schema.sql 全量基线。
-- 5. 大表锁影响评估
--    全部为新建表，无锁；ai_vector_index 的 HNSW 索引建于空表，瞬时完成；
--    权限 / 角色种子为小表行锁。
-- 6. 索引 / 逻辑删除 / 租户隔离检查
--    每表含 id / created_at / updated_at / is_deleted（C5）；单表索引最多 3 个 ≤ 5（C9）；
--    任务 / 用量 / 向量索引以 project_id 为隔离边界，助手两表以 user_id 为唯一隔离维度。
--
-- 本地执行：docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

-- ---------- 0. 清理旧版 AI 残留表（已下线功能，代码零引用；同名冲突必须先删） ----------

DROP TABLE IF EXISTS ai_analysis_task;
DROP TABLE IF EXISTS ai_bug_embedding;
DROP TABLE IF EXISTS ai_case_embedding;
DROP TABLE IF EXISTS ai_chat_model;
DROP TABLE IF EXISTS ai_config;
DROP TABLE IF EXISTS ai_conversation;
DROP TABLE IF EXISTS ai_invocation_log;
DROP TABLE IF EXISTS ai_message;
DROP TABLE IF EXISTS ai_prompt_template;

-- ---------- 1. pgvector 扩展（超级用户） ----------

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------- 2. 新表（DDL 见详设 2.2–2.10） ----------

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
    last_test_at       timestamp NULL,
    last_test_result   jsonb NULL,
    created_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted         boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_model_config_name ON ai_model_config (name) WHERE is_deleted = FALSE;
CREATE INDEX idx_ai_model_config_enabled ON ai_model_config (enabled, priority);

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

CREATE TABLE ai_prompt_template (
    id         uuid PRIMARY KEY,
    scene      varchar(50) NOT NULL,
    name       varchar(100) NOT NULL,
    content    text NOT NULL,
    variables  jsonb NOT NULL DEFAULT '[]',
    source     varchar(20) NOT NULL DEFAULT 'custom',
    version    int NOT NULL DEFAULT 1,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_prompt_template_scene ON ai_prompt_template (scene) WHERE is_deleted = FALSE;

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

CREATE TABLE ai_usage_log (
    id                uuid PRIMARY KEY,
    project_id        uuid NULL,
    task_id           uuid NULL,
    user_id           uuid NOT NULL,
    model_id          uuid NOT NULL,
    prompt_scene      varchar(50) NULL,
    call_type         varchar(20) NOT NULL,
    prompt_tokens     int NOT NULL DEFAULT 0,
    completion_tokens int NOT NULL DEFAULT 0,
    total_tokens      int NOT NULL DEFAULT 0,
    latency_ms        int NOT NULL DEFAULT 0,
    status            varchar(20) NOT NULL,
    error_code        int NULL,
    created_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        boolean NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_ai_usage_log_project ON ai_usage_log (project_id, created_at);
CREATE INDEX idx_ai_usage_log_model ON ai_usage_log (model_id, created_at);
CREATE INDEX idx_ai_usage_log_task ON ai_usage_log (task_id);

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

-- n = ai_embedding_config.dimensions（初始 1536；维度变更走详设 4.4 全量重建）
CREATE TABLE ai_vector_index (
    id               uuid PRIMARY KEY,
    project_id       uuid NOT NULL,
    entity_type      varchar(30) NOT NULL,
    entity_id        uuid NOT NULL,
    chunk_index      int NOT NULL DEFAULT 0,
    content          text NOT NULL,
    embedding        vector(1536) NOT NULL,
    embedding_version varchar(64) NOT NULL,
    indexed_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       boolean NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uk_ai_vector_index_chunk ON ai_vector_index (entity_type, entity_id, chunk_index) WHERE is_deleted = FALSE;
CREATE INDEX idx_ai_vector_index_scope ON ai_vector_index (project_id, entity_type);
CREATE INDEX idx_ai_vector_index_embedding ON ai_vector_index USING hnsw (embedding vector_cosine_ops);

-- ---------- 3. 权限点（详设 3.8；先清理旧版 AI 权限行，再按新基线写入，与 schema.sql 收敛） ----------

DELETE FROM sys_permission WHERE code IN ('ai', 'ai:view', 'ai:edit');

INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000023', 'ai:admin',  'AI 配置与用量管理',  'ai', 'AI 能力', 'AI 能力', 'global',    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000079', 'ai',        'AI 能力',           NULL, 'AI 能力', 'AI 能力', 'workspace', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000080', 'ai:task',   '发起与管理 AI 任务', 'ai',  'AI 能力', 'AI 能力', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000081', 'ai:confirm', 'AI 产物确认',       'ai',  'AI 能力', 'AI 能力', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ---------- 4. 预置角色授权 ----------

-- 系统管理员：剔除旧码 ai:view / ai:edit，保留/补全 ai 与 ai:admin（DISTINCT 去重）
UPDATE sys_role
SET permissions = (
        SELECT jsonb_agg(DISTINCT e ORDER BY e)
        FROM jsonb_array_elements(
            permissions - 'ai:view' - 'ai:edit' || '["ai","ai:admin"]'::jsonb
        ) AS e
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'b0000000-0000-0000-0000-000000000001';

-- workspace 管理员 / 成员：补 ai / ai:task / ai:confirm
UPDATE sys_role
SET permissions = permissions || COALESCE((
        SELECT jsonb_agg(e)
        FROM jsonb_array_elements_text('["ai","ai:task","ai:confirm"]'::jsonb) AS e
        WHERE NOT (sys_role.permissions @> jsonb_build_array(e))
    ), '[]'::jsonb),
    updated_at = CURRENT_TIMESTAMP
WHERE id IN ('c0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002');

COMMIT;
