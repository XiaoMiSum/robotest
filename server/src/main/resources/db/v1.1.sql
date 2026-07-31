-- Robotest V1.1 数据库增量 DDL
-- PostgreSQL
-- V1.1 AI 能力域新增表结构，首次建库按 v1.sql → v1.1.sql 顺序执行
-- 依据：《AI 基础设施详细设计说明书 V1.1》2.1

-- pgvector 扩展（为 V1.1 向量表铺垫，需数据库已安装 pgvector）
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. AI 基础设施
-- ============================================================

-- AI 配置表（系统级单行表：全系统仅一条有效记录，存放总开关、系统配置项与 Embedding 单一配置；对话模型多行独立存于 ai_chat_model）
CREATE TABLE ai_config (
    id                       UUID          PRIMARY KEY,
    embedding_provider       VARCHAR(50)   NULL,
    embedding_base_url       VARCHAR(500)  NULL,
    embedding_api_key_cipher VARCHAR(1000) NULL,
    embedding_key_suffix     VARCHAR(4)    NULL,
    embedding_model          VARCHAR(100)  NULL,
    embedding_dimension      INT           NULL,
    embedding_extra_params   JSONB         NOT NULL DEFAULT '{}',
    enabled                  BOOLEAN       NOT NULL DEFAULT FALSE,
    settings                 JSONB         NOT NULL DEFAULT '{}',
    is_deleted               BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 智能体提示词模板表（仅存自定义覆盖记录，默认模板内置于代码）
CREATE TABLE ai_prompt_template (
    id                UUID        PRIMARY KEY,
    function_type     VARCHAR(50) NOT NULL,
    role_instruction  TEXT        NOT NULL,
    format_constraint TEXT        NOT NULL,
    format_editable   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_by        UUID        NOT NULL,
    is_deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_prompt_function_type ON ai_prompt_template (function_type) WHERE is_deleted = false;

-- AI 异步任务表
CREATE TABLE ai_analysis_task (
    id                UUID         PRIMARY KEY,
    workspace_id      UUID         NULL,
    project_id        UUID         NULL,
    type              VARCHAR(30)  NOT NULL, -- review_check / review_summary / bug_clustering / embedding_rebuild / plan_order_recommend
    target_id         UUID         NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'pending', -- pending / running / success / failed / cancelled
    progress          INT          NOT NULL DEFAULT 0,
    result            JSONB        NULL,
    error_message     VARCHAR(500) NULL,
    executor_instance VARCHAR(100) NULL,
    created_by        UUID         NOT NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_project_id ON ai_analysis_task (project_id);
CREATE INDEX idx_task_type_target ON ai_analysis_task (type, target_id);
CREATE INDEX idx_task_status ON ai_analysis_task (status);

-- AI 调用审计表（只记录调用元数据，不存储 Prompt 与生成内容）
CREATE TABLE ai_invocation_log (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL,
    workspace_id      UUID         NULL,
    project_id        UUID         NULL,
    function_type     VARCHAR(50)  NOT NULL,
    model             VARCHAR(100) NULL,
    duration_ms       INT          NULL,
    prompt_tokens     INT          NULL,
    completion_tokens INT          NULL,
    status            VARCHAR(20)  NOT NULL, -- success / failed / cancelled / rate_limited / schema_invalid
    error_code        VARCHAR(50)  NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_log_user_id ON ai_invocation_log (user_id);
CREATE INDEX idx_log_workspace_created ON ai_invocation_log (workspace_id, created_at);
CREATE INDEX idx_log_function_type ON ai_invocation_log (function_type);
CREATE INDEX idx_log_created_at ON ai_invocation_log (created_at);

-- AI 对话模型配置表（多行：每行一个可用对话模型，全系统有且仅有一行 is_default = true，由应用层保证）
CREATE TABLE ai_chat_model (
    id             UUID          PRIMARY KEY,
    name           VARCHAR(50)   NOT NULL,
    provider       VARCHAR(50)   NOT NULL DEFAULT 'custom',
    base_url       VARCHAR(500)  NOT NULL,
    api_key_cipher VARCHAR(1000) NOT NULL,
    key_suffix     VARCHAR(4)    NULL,
    model          VARCHAR(100)  NOT NULL,
    extra_params   JSONB         NOT NULL DEFAULT '{}',
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    is_default     BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_by     UUID          NOT NULL,
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 显示名全局唯一（逻辑删除后可复用）；行数为个位数量级，默认/启用清单全表扫描即可，不为 is_default/enabled 建索引（C9 从简）
CREATE UNIQUE INDEX uk_chat_model_name ON ai_chat_model (name) WHERE is_deleted = false;

-- ============================================================
-- 2. 既有表变更（AI 标识字段，见《智能用例生成与脑图智能编辑详细设计》2.1.3）
-- ============================================================

-- AI 生成标识随节点数据落库并被评审/计划快照继承；仅用于渲染与 DSL 筛选，不建索引（C9）
ALTER TABLE test_case_node            ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE test_review_node_snapshot ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE test_plan_node_snapshot   ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN test_case_node.ai_generated IS 'AI 生成标识（挂载执行器写入，可手动移除）';
COMMENT ON COLUMN test_review_node_snapshot.ai_generated IS 'AI 生成标识（随评审快照继承）';
COMMENT ON COLUMN test_plan_node_snapshot.ai_generated IS 'AI 生成标识（随计划快照继承）';

-- ============================================================
-- 3. 种子数据（AI 管理权限点，并回补系统管理员角色）
-- ============================================================

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000018', 'ai',      'AI 管理',           NULL, 'AI 管理', 'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000019', 'ai:view', '查看 AI 配置与智能体', 'ai', 'AI 管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000020', 'ai:edit', '编辑 AI 配置与智能体', 'ai', 'AI 管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

UPDATE sys_role SET permissions = permissions || '["ai","ai:view","ai:edit"]'::jsonb, updated_at = CURRENT_TIMESTAMP
WHERE id = 'b0000000-0000-0000-0000-000000000001' AND NOT permissions @> '["ai:view"]'::jsonb;

-- ============================================================
-- 4. 表与列注释
-- ============================================================

COMMENT ON TABLE ai_config IS 'AI 配置表（系统级单行：总开关、系统配置项与 Embedding 单一配置）';
COMMENT ON COLUMN ai_config.id IS '主键';
COMMENT ON COLUMN ai_config.embedding_provider IS 'Embedding 供应商标识（未配置时为空）';
COMMENT ON COLUMN ai_config.embedding_base_url IS 'Embedding 服务地址（未配置则语义检索能力不可用）';
COMMENT ON COLUMN ai_config.embedding_api_key_cipher IS 'Embedding 服务密钥（加密）';
COMMENT ON COLUMN ai_config.embedding_key_suffix IS 'Embedding 密钥末 4 位（脱敏展示）';
COMMENT ON COLUMN ai_config.embedding_model IS 'Embedding 模型名';
COMMENT ON COLUMN ai_config.embedding_dimension IS '向量维度（1-2000，保存时强制校验）';
COMMENT ON COLUMN ai_config.embedding_extra_params IS 'Embedding 请求附加参数';
COMMENT ON COLUMN ai_config.enabled IS 'AI 能力总开关';
COMMENT ON COLUMN ai_config.settings IS 'AI 系统配置项键值集，缺省键取代码内置默认值';
COMMENT ON COLUMN ai_config.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_config.created_at IS '创建时间';
COMMENT ON COLUMN ai_config.updated_at IS '更新时间';

COMMENT ON TABLE ai_prompt_template IS '智能体提示词模板表（仅存自定义覆盖，恢复默认即逻辑删除）';
COMMENT ON COLUMN ai_prompt_template.id IS '主键';
COMMENT ON COLUMN ai_prompt_template.function_type IS '功能类型枚举，每功能至多一条有效记录';
COMMENT ON COLUMN ai_prompt_template.role_instruction IS '角色指令段';
COMMENT ON COLUMN ai_prompt_template.format_constraint IS '输出格式约束段';
COMMENT ON COLUMN ai_prompt_template.format_editable IS '格式约束段编辑开关（高级开关，默认关闭锁定）';
COMMENT ON COLUMN ai_prompt_template.updated_by IS '最后更新人，关联 sys_user.id';
COMMENT ON COLUMN ai_prompt_template.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_prompt_template.created_at IS '创建时间';
COMMENT ON COLUMN ai_prompt_template.updated_at IS '更新时间';

COMMENT ON TABLE ai_analysis_task IS 'AI 异步任务表（任务状态机与结果快照）';
COMMENT ON COLUMN ai_analysis_task.id IS '任务 ID';
COMMENT ON COLUMN ai_analysis_task.workspace_id IS '归属工作空间（embedding_rebuild 全局任务为空）';
COMMENT ON COLUMN ai_analysis_task.project_id IS '归属项目（embedding_rebuild 全局任务为空）';
COMMENT ON COLUMN ai_analysis_task.type IS '任务类型：review_check/review_summary/bug_clustering/embedding_rebuild/plan_order_recommend';
COMMENT ON COLUMN ai_analysis_task.target_id IS '目标对象 ID（评审 ID 等；聚类/回填以项目为目标时为空）';
COMMENT ON COLUMN ai_analysis_task.status IS '任务状态：pending/running/success/failed/cancelled';
COMMENT ON COLUMN ai_analysis_task.progress IS '进度百分比（0-100）';
COMMENT ON COLUMN ai_analysis_task.result IS '结果快照（结构由各任务类型在对应文档定义）';
COMMENT ON COLUMN ai_analysis_task.error_message IS '失败原因';
COMMENT ON COLUMN ai_analysis_task.executor_instance IS '执行实例标识（多实例防重复消费）';
COMMENT ON COLUMN ai_analysis_task.created_by IS '发起人，关联 sys_user.id';
COMMENT ON COLUMN ai_analysis_task.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_analysis_task.created_at IS '创建时间';
COMMENT ON COLUMN ai_analysis_task.updated_at IS '更新时间';

COMMENT ON TABLE ai_invocation_log IS 'AI 调用审计表（仅调用元数据，不存 Prompt 与生成内容）';
COMMENT ON COLUMN ai_invocation_log.id IS '主键';
COMMENT ON COLUMN ai_invocation_log.user_id IS '调用用户，关联 sys_user.id';
COMMENT ON COLUMN ai_invocation_log.workspace_id IS '工作空间（管理端调用为空）';
COMMENT ON COLUMN ai_invocation_log.project_id IS '项目（工作空间级功能为空）';
COMMENT ON COLUMN ai_invocation_log.function_type IS '功能类型枚举';
COMMENT ON COLUMN ai_invocation_log.model IS '实际调用的模型名';
COMMENT ON COLUMN ai_invocation_log.duration_ms IS '端到端耗时（毫秒）';
COMMENT ON COLUMN ai_invocation_log.prompt_tokens IS '输入 token（取上游 usage，缺失为空）';
COMMENT ON COLUMN ai_invocation_log.completion_tokens IS '输出 token';
COMMENT ON COLUMN ai_invocation_log.status IS '调用状态：success/failed/cancelled/rate_limited/schema_invalid';
COMMENT ON COLUMN ai_invocation_log.error_code IS '失败错误码或上游错误摘要';
COMMENT ON COLUMN ai_invocation_log.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_invocation_log.created_at IS '创建时间';
COMMENT ON COLUMN ai_invocation_log.updated_at IS '更新时间';

COMMENT ON TABLE ai_chat_model IS 'AI 对话模型配置表（多行：每行一个可用对话模型，全系统唯一默认）';
COMMENT ON COLUMN ai_chat_model.id IS '主键（业务请求中的模型标识 modelId）';
COMMENT ON COLUMN ai_chat_model.name IS '显示名（管理端与用户模型选择器展示，全局唯一）';
COMMENT ON COLUMN ai_chat_model.provider IS '供应商标识（预设注册表键，custom 为通用 OpenAI 兼容）';
COMMENT ON COLUMN ai_chat_model.base_url IS '服务地址（OpenAI 兼容根路径，不含 /chat/completions）';
COMMENT ON COLUMN ai_chat_model.api_key_cipher IS '服务密钥（AES-256-GCM 加密）';
COMMENT ON COLUMN ai_chat_model.key_suffix IS '密钥末 4 位（脱敏展示）';
COMMENT ON COLUMN ai_chat_model.model IS '模型名（请求体 model 字段值）';
COMMENT ON COLUMN ai_chat_model.extra_params IS '请求附加参数（厂商非标参数透传）';
COMMENT ON COLUMN ai_chat_model.enabled IS '启用状态（停用后不出现在用户模型清单）';
COMMENT ON COLUMN ai_chat_model.is_default IS '是否系统默认模型（全系统唯一）';
COMMENT ON COLUMN ai_chat_model.updated_by IS '最后更新人，关联 sys_user.id';
COMMENT ON COLUMN ai_chat_model.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN ai_chat_model.created_at IS '创建时间';
COMMENT ON COLUMN ai_chat_model.updated_at IS '更新时间';
