-- ============================================================
-- Robotest 数据库初始化 DDL（全量合并版）
-- PostgreSQL 14+
-- 用途：首次建库时一次性执行，不要在已迁移的库上运行
-- ============================================================

-- ============================================================
-- 0. 扩展
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. 系统管理
-- ============================================================

-- 用户表
CREATE TABLE sys_user (
                          id                      UUID         PRIMARY KEY,
                          username                VARCHAR(30)  NOT NULL,
                          name                    VARCHAR(50)  NOT NULL,
                          email                   VARCHAR(255) NOT NULL,
                          password_hash           VARCHAR(255) NOT NULL,
                          avatar_url              VARCHAR(500),
                          status                  VARCHAR(20)  NOT NULL DEFAULT 'active',
                          last_active_workspace_id UUID,
                          is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
                          created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_username ON sys_user (username) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_email ON sys_user (email) WHERE is_deleted = false;
CREATE INDEX idx_status ON sys_user (status);
CREATE INDEX idx_user_last_active_workspace ON sys_user (last_active_workspace_id);

-- 角色表
CREATE TABLE sys_role (
                          id           UUID         PRIMARY KEY,
                          name         VARCHAR(50)  NOT NULL,
                          description  VARCHAR(200),
                          type         VARCHAR(20)  NOT NULL,
                          is_system    BOOLEAN      NOT NULL DEFAULT FALSE,
                          permissions  JSONB        NOT NULL DEFAULT '[]',
                          is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
                          created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_role_name ON sys_role (name) WHERE is_deleted = false;
CREATE INDEX idx_role_type ON sys_role (type);

-- 用户-角色关联表
CREATE TABLE sys_user_role (
                               id          UUID      PRIMARY KEY,
                               user_id     UUID      NOT NULL,
                               role_id     UUID      NOT NULL,
                               assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
                               created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_user_role ON sys_user_role (user_id, role_id) WHERE is_deleted = false;
CREATE INDEX idx_user_role_role_id ON sys_user_role (role_id);

-- 权限点表
CREATE TABLE sys_permission (
                                id          UUID         PRIMARY KEY,
                                code        VARCHAR(100) NOT NULL,
                                name        VARCHAR(100) NOT NULL,
                                parent_code VARCHAR(100),
                                module      VARCHAR(50)  NOT NULL,
                                scope       VARCHAR(20)  NOT NULL DEFAULT 'global',
                                sort_order  INT          NOT NULL DEFAULT 0,
                                is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_permission_code ON sys_permission (code) WHERE is_deleted = false;
CREATE INDEX idx_permission_parent_code ON sys_permission (parent_code);
CREATE INDEX idx_permission_module ON sys_permission (module);
CREATE INDEX idx_permission_scope ON sys_permission (scope);

-- 审计日志表
CREATE TABLE sys_audit_log (
                               id            UUID         PRIMARY KEY,
                               operator_id   VARCHAR(64)  NOT NULL,
                               operator_name VARCHAR(64)  NOT NULL DEFAULT '',
                               operation     VARCHAR(32)  NOT NULL,
                               entity_type   VARCHAR(64)  NOT NULL,
                               entity_id     VARCHAR(64)  NOT NULL DEFAULT '',
                               changes       JSONB        NOT NULL DEFAULT '{}',
                               request_ip    VARCHAR(64)  NOT NULL DEFAULT '',
                               created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_sys_audit_log_operator ON sys_audit_log (operator_id);
CREATE INDEX idx_sys_audit_log_entity   ON sys_audit_log (entity_type, entity_id);
CREATE INDEX idx_sys_audit_log_created  ON sys_audit_log (created_at);

-- ============================================================
-- 2. 工作空间
-- ============================================================

-- 工作空间表
CREATE TABLE ws_workspace (
                              id          UUID         PRIMARY KEY,
                              name        VARCHAR(50)  NOT NULL,
                              description VARCHAR(500),
                              status      VARCHAR(20)  NOT NULL DEFAULT 'active',
                              is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
                              created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ws_workspace_name ON ws_workspace (name) WHERE is_deleted = false;
CREATE INDEX idx_ws_workspace_created ON ws_workspace (created_at DESC);

-- 用户-工作空间关联表
CREATE TABLE ws_user (
                         id                UUID      PRIMARY KEY,
                         user_id           UUID      NOT NULL,
                         workspace_id      UUID      NOT NULL,
                         workspace_role    UUID      NOT NULL DEFAULT 'c0000000-0000-0000-0000-000000000002',
                         default_project_id UUID,
                         joined_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         is_deleted        BOOLEAN   NOT NULL DEFAULT FALSE,
                         created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ws_user_user_workspace ON ws_user (user_id, workspace_id) WHERE is_deleted = false;
CREATE INDEX idx_ws_user_workspace_id ON ws_user (workspace_id);
CREATE INDEX idx_ws_user_ws_role ON ws_user (workspace_id, workspace_role);
CREATE INDEX idx_ws_user_default_project_id ON ws_user (default_project_id);

-- 邀请链接表
CREATE TABLE ws_invitation (
                               id UUID PRIMARY KEY,
                               workspace_id UUID NOT NULL,
                               token VARCHAR(64) NOT NULL,
                               created_by UUID NOT NULL,
                               expires_at TIMESTAMP NULL,
                               max_uses INT NULL,
                               use_count INT NOT NULL DEFAULT 0,
                               status VARCHAR(20) NOT NULL DEFAULT 'active',
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ws_invitation_token ON ws_invitation (token) WHERE is_deleted = false;
CREATE INDEX idx_ws_invitation_ws_created ON ws_invitation (workspace_id, created_at DESC);

-- 项目表
CREATE TABLE ws_project (
                            id UUID PRIMARY KEY,
                            workspace_id UUID NOT NULL,
                            name VARCHAR(100) NOT NULL,
                            description TEXT NULL,
                            status VARCHAR(20) NOT NULL DEFAULT 'active',
                            start_time TIMESTAMP NULL,
                            end_time TIMESTAMP NULL,
                            created_by UUID NOT NULL,
                            is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ws_project_workspace_name ON ws_project (workspace_id, name) WHERE is_deleted = false;
CREATE INDEX idx_ws_project_ws_created ON ws_project (workspace_id, created_at DESC);
CREATE INDEX idx_ws_project_status ON ws_project (status);

-- ============================================================
-- 3. 功能测试 — 测试用例
-- ============================================================

-- 测试用例节点表（脑图节点）
CREATE TABLE test_case_node (
                                id           UUID         PRIMARY KEY,
                                document_id  UUID         NOT NULL,
                                parent_id    UUID         NULL,
                                type         VARCHAR(20)  NOT NULL DEFAULT 'normal',
                                title        VARCHAR(200) NOT NULL,
                                priority     VARCHAR(2)   NULL,
                                sort_order   INT          NOT NULL DEFAULT 0,
                                version      INT          NOT NULL DEFAULT 1,
                                ai_generated BOOLEAN      NOT NULL DEFAULT FALSE,
                                is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_case_node_document_id ON test_case_node (document_id);
CREATE INDEX idx_test_case_node_parent_id ON test_case_node (parent_id);

-- ============================================================
-- 4. 功能测试 — 测试计划
-- ============================================================

-- 测试计划表
CREATE TABLE test_plan (
                           id                UUID         PRIMARY KEY,
                           project_id        UUID         NOT NULL,
                           name              VARCHAR(100) NOT NULL,
                           description       TEXT         NULL,
                           status            VARCHAR(20)  NOT NULL DEFAULT 'new',
                           executor_id       UUID         NULL,
                           start_time        TIMESTAMP    NULL,
                           end_time          TIMESTAMP    NULL,
                           environment       VARCHAR(200) NULL,
                           snapshot_synced_at TIMESTAMP  NULL,
                           is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
                           created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_plan_project_created ON test_plan (project_id, created_at DESC);
CREATE INDEX idx_test_plan_status ON test_plan (status);

-- 计划模块快照表
CREATE TABLE test_plan_module_snapshot (
                                           id                  UUID         PRIMARY KEY,
                                           plan_id             UUID         NOT NULL,
                                           original_module_id  UUID         NULL,
                                           parent_id           UUID         NULL,
                                           name                VARCHAR(100) NOT NULL,
                                           type                VARCHAR(20)  NOT NULL,
                                           sort_order          INT          NOT NULL DEFAULT 0,
                                           is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
                                           created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_plan_module_snapshot_plan_id ON test_plan_module_snapshot (plan_id);

-- 计划节点快照表
CREATE TABLE test_plan_node_snapshot (
                                         id                     UUID         PRIMARY KEY,
                                         plan_id                UUID         NOT NULL,
                                         original_node_id       UUID         NULL,
                                         document_snapshot_id   UUID         NOT NULL,
                                         parent_id              UUID         NULL,
                                         title                  VARCHAR(200) NOT NULL,
                                         type                   VARCHAR(20)  NOT NULL,
                                         priority               VARCHAR(2)   NULL,
                                         is_associated          BOOLEAN      NOT NULL DEFAULT FALSE,
                                         last_result            VARCHAR(20)  DEFAULT 'untested',
                                         last_executor_id       UUID         NULL,
                                         last_executed_at       TIMESTAMP    NULL,
                                         sort_order             INT          NOT NULL DEFAULT 0,
                                         ai_generated           BOOLEAN      NOT NULL DEFAULT FALSE,
                                         is_deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
                                         created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_plan_node_snapshot_plan_id ON test_plan_node_snapshot (plan_id);
CREATE INDEX idx_test_plan_node_snapshot_document_snapshot_id ON test_plan_node_snapshot (document_snapshot_id);

-- 计划执行记录表
CREATE TABLE test_plan_execution_record (
                                            id                UUID      PRIMARY KEY,
                                            plan_id           UUID      NOT NULL,
                                            snapshot_node_id  UUID      NOT NULL,
                                            executor_id       UUID      NOT NULL,
                                            result            VARCHAR(20) NOT NULL,
                                            note              TEXT      NULL,
                                            executed_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            is_deleted        BOOLEAN   NOT NULL DEFAULT FALSE,
                                            created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_plan_execution_record_plan_id ON test_plan_execution_record (plan_id);
CREATE INDEX idx_test_plan_execution_record_snapshot_node_id ON test_plan_execution_record (snapshot_node_id);

-- ============================================================
-- 5. 功能测试 — 测试评审
-- ============================================================

-- 测试评审表
CREATE TABLE test_review (
                             id              UUID         PRIMARY KEY,
                             project_id      UUID         NOT NULL,
                             title           VARCHAR(200) NOT NULL,
                             description     TEXT         NULL,
                             initiator_id    UUID         NOT NULL,
                             participant_ids JSONB        NOT NULL DEFAULT '[]',
                             status          VARCHAR(20)  NOT NULL DEFAULT 'new',
                             is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                             created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_review_project_created ON test_review (project_id, created_at DESC);
CREATE INDEX idx_test_review_status ON test_review (status);

-- 评审模块快照表
CREATE TABLE test_review_module_snapshot (
                                             id                  UUID         PRIMARY KEY,
                                             review_id           UUID         NOT NULL,
                                             original_module_id  UUID         NULL,
                                             parent_id           UUID         NULL,
                                             name                VARCHAR(100) NOT NULL,
                                             type                VARCHAR(20)  NOT NULL,
                                             sort_order          INT          NOT NULL DEFAULT 0,
                                             is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
                                             created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                             updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_review_module_snapshot_review_id ON test_review_module_snapshot (review_id);

-- 评审节点快照表
CREATE TABLE test_review_node_snapshot (
                                           id                     UUID         PRIMARY KEY,
                                           review_id              UUID         NOT NULL,
                                           original_node_id       UUID         NULL,
                                           document_snapshot_id   UUID         NOT NULL,
                                           parent_id              UUID         NULL,
                                           title                  VARCHAR(200) NOT NULL,
                                           type                   VARCHAR(20)  NOT NULL,
                                           priority               VARCHAR(2)   NULL,
                                           is_associated          BOOLEAN      NOT NULL DEFAULT FALSE,
                                           last_mark              VARCHAR(10)  NULL,
                                           last_reviewer_id       UUID         NULL,
                                           last_reviewed_at       TIMESTAMP    NULL,
                                           sort_order             INT          NOT NULL DEFAULT 0,
                                           ai_generated           BOOLEAN      NOT NULL DEFAULT FALSE,
                                           is_deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
                                           created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_review_node_snapshot_review_id ON test_review_node_snapshot (review_id);
CREATE INDEX idx_test_review_node_snapshot_document_snapshot_id ON test_review_node_snapshot (document_snapshot_id);

-- 评审记录表
CREATE TABLE test_review_record (
                                    id                UUID         PRIMARY KEY,
                                    review_id         UUID         NOT NULL,
                                    snapshot_node_id  UUID         NOT NULL,
                                    reviewer_id       UUID         NOT NULL,
                                    operation_type    VARCHAR(20)  NOT NULL,
                                    mark              VARCHAR(10)  NULL,
                                    comment           TEXT         NULL,
                                    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
                                    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_review_record_review_id ON test_review_record (review_id);
CREATE INDEX idx_test_review_record_snapshot_node_id ON test_review_record (snapshot_node_id);

-- ============================================================
-- 6. 缺陷管理
-- ============================================================

-- 缺陷表
CREATE TABLE bug (
                     id                UUID         PRIMARY KEY,
                     project_id        UUID         NOT NULL,
                     title             VARCHAR(300) NOT NULL,
                     severity          VARCHAR(20)  NOT NULL,
                     priority          VARCHAR(20)  NOT NULL,
                     status            VARCHAR(20)  NOT NULL DEFAULT 'active',
                     repro_steps       TEXT         NULL,
                     reporter_id       UUID         NOT NULL,
                     assignee_id       UUID         NULL,
                     related_case_id   UUID         NULL,
                     related_plan_id   UUID         NULL,
                     bug_type          VARCHAR(30)  NOT NULL DEFAULT 'code_error',
                     module_id         UUID         NULL,
                     keywords          VARCHAR(255) NULL,
                     due_date          DATE         NULL,
                     confirmed         BOOLEAN      NOT NULL DEFAULT FALSE,
                     reopen_count      INT          NOT NULL DEFAULT 0,
                     last_reopened_at  TIMESTAMP    NULL,
                     resolution        VARCHAR(30)  NULL,
                     duplicate_of_bug_id UUID       NULL,
                     resolved_by       UUID         NULL,
                     resolved_at       TIMESTAMP    NULL,
                     rejected_by       UUID         NULL,
                     closed_by         UUID         NULL,
                     closed_at         TIMESTAMP    NULL,
                     is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
                     created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                     updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bug_project_id ON bug (project_id);
CREATE INDEX idx_bug_project_created ON bug (project_id, created_at DESC);
CREATE INDEX idx_bug_status ON bug (status);
CREATE INDEX idx_bug_assignee_id ON bug (assignee_id);
CREATE INDEX idx_bug_reporter_id ON bug (reporter_id);
CREATE INDEX idx_bug_module_id ON bug (module_id);

-- 缺陷日志表
CREATE TABLE bug_log (
                         id             UUID         PRIMARY KEY,
                         bug_id         UUID         NOT NULL,
                         operator_id    UUID         NOT NULL,
                         operation_type VARCHAR(50)  NOT NULL,
                         content        TEXT         NULL,
                         is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
                         created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bug_log_bug_id ON bug_log (bug_id);

-- 缺陷附件表
CREATE TABLE bug_attachment (
                                id           UUID         PRIMARY KEY,
                                bug_id       UUID         NOT NULL,
                                file_name    VARCHAR(255) NOT NULL,
                                storage_path VARCHAR(500) NOT NULL,
                                file_size    BIGINT       NOT NULL,
                                content_type VARCHAR(100) NULL,
                                uploader_id  UUID         NOT NULL,
                                is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bug_attachment_bug_id ON bug_attachment (bug_id);

-- ============================================================
-- 7. AI 基础设施
-- ============================================================

-- AI 配置表（系统级单行）
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

-- AI 提示词模板表
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
                                  type              VARCHAR(30)  NOT NULL,
                                  target_id         UUID         NULL,
                                  status            VARCHAR(20)  NOT NULL DEFAULT 'pending',
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

-- AI 调用审计表
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
                                   status            VARCHAR(20)  NOT NULL,
                                   error_code        VARCHAR(50)  NULL,
                                   is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
                                   created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_log_user_id ON ai_invocation_log (user_id);
CREATE INDEX idx_log_workspace_created ON ai_invocation_log (workspace_id, created_at);
CREATE INDEX idx_log_function_type ON ai_invocation_log (function_type);
CREATE INDEX idx_log_created_at ON ai_invocation_log (created_at);

-- AI 对话模型配置表
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

CREATE UNIQUE INDEX uk_chat_model_name ON ai_chat_model (name) WHERE is_deleted = false;

-- 缺陷向量表
CREATE TABLE ai_bug_embedding (
                                  id          UUID          PRIMARY KEY,
                                  bug_id      UUID          NOT NULL,
                                  project_id  UUID          NOT NULL,
                                  embedding   vector(1024)  NOT NULL,
                                  source_hash VARCHAR(64)   NOT NULL,
                                  model       VARCHAR(100)  NOT NULL,
                                  is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
                                  created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ai_bug_embedding_bug_id ON ai_bug_embedding (bug_id) WHERE is_deleted = false;
CREATE INDEX idx_ai_bug_embedding_project_id ON ai_bug_embedding (project_id);
CREATE INDEX idx_ai_bug_embedding_hnsw ON ai_bug_embedding USING hnsw (embedding vector_cosine_ops);

-- 用例向量表
CREATE TABLE ai_case_embedding (
                                   id          UUID          PRIMARY KEY,
                                   node_id     UUID          NOT NULL,
                                   project_id  UUID          NOT NULL,
                                   embedding   vector(1024)  NOT NULL,
                                   source_hash VARCHAR(64)   NOT NULL,
                                   model       VARCHAR(100)  NOT NULL,
                                   is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
                                   created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ai_case_embedding_node_id ON ai_case_embedding (node_id) WHERE is_deleted = false;
CREATE INDEX idx_ai_case_embedding_project_id ON ai_case_embedding (project_id);
CREATE INDEX idx_ai_case_embedding_hnsw ON ai_case_embedding USING hnsw (embedding vector_cosine_ops);

-- 助手会话表
CREATE TABLE ai_conversation (
                                 id             UUID          PRIMARY KEY,
                                 user_id        UUID          NOT NULL,
                                 workspace_id   UUID          NOT NULL,
                                 title          VARCHAR(100)  NOT NULL,
                                 last_active_at TIMESTAMP     NOT NULL,
                                 is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
                                 created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conv_user_ws ON ai_conversation (user_id, workspace_id, last_active_at DESC);

-- 助手消息表
CREATE TABLE ai_message (
                            id              UUID         PRIMARY KEY,
                            conversation_id UUID         NOT NULL,
                            role            VARCHAR(10)  NOT NULL,
                            content         TEXT         NULL,
                            tool_calls      JSONB        NULL,
                            tool_call_id    VARCHAR(64)  NULL,
                            is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                            created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_msg_conversation_id ON ai_message (conversation_id);

-- ============================================================
-- 8. 需求池
-- ============================================================

CREATE TABLE requirement_pool_item (
                                       id          UUID          PRIMARY KEY,
                                       project_id  UUID          NOT NULL,
                                       title       VARCHAR(200)  NOT NULL,
                                       content     TEXT          NOT NULL,
                                       source_url  VARCHAR(500)  NULL,
                                       status      VARCHAR(20)   NOT NULL DEFAULT 'active',
                                       ai_generated BOOLEAN      NOT NULL DEFAULT FALSE,
                                       created_by  UUID          NOT NULL,
                                       updated_by  UUID          NOT NULL,
                                       is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
                                       created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rpi_project_id ON requirement_pool_item (project_id);

CREATE TABLE requirement_document_rel (
                                          id             UUID       PRIMARY KEY,
                                          document_id    UUID       NOT NULL,
                                          requirement_id UUID       NOT NULL,
                                          is_deleted     BOOLEAN    NOT NULL DEFAULT FALSE,
                                          created_at     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at     TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_requirement_document_rel ON requirement_document_rel (document_id, requirement_id) WHERE is_deleted = false;
CREATE INDEX idx_requirement_document_rel_requirement_id ON requirement_document_rel (requirement_id);

-- ============================================================
-- 9. 项目模块与用例文档（V1.2 重构）
-- ============================================================

-- 项目模块表（纯目录树节点）
CREATE TABLE project_module (
                                id          UUID         PRIMARY KEY,
                                project_id  UUID         NOT NULL,
                                parent_id   UUID         NULL,
                                name        VARCHAR(100) NOT NULL,
                                sort_order  INT          NOT NULL DEFAULT 0,
                                is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pmod_project ON project_module(project_id);
CREATE INDEX idx_pmod_parent ON project_module(parent_id);

-- 用例文档表（布局内嵌 JSONB）
CREATE TABLE test_case_document (
                                    id          UUID         PRIMARY KEY,
                                    project_id  UUID         NOT NULL,
                                    module_id   UUID         NULL,
                                    name        VARCHAR(100) NOT NULL,
                                    layout      JSONB        NULL,
                                    sort_order  INT          NOT NULL DEFAULT 0,
                                    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
                                    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tcd_project ON test_case_document(project_id);
CREATE INDEX idx_tcd_module ON test_case_document(module_id);

-- ============================================================
-- 10. 项目设置
-- ============================================================

CREATE TABLE project_setting (
                                 id            UUID         PRIMARY KEY,
                                 project_id    UUID         NOT NULL,
                                 domain        VARCHAR(20)  NOT NULL,
                                 setting_key   VARCHAR(100) NOT NULL,
                                 setting_value VARCHAR(500) NOT NULL,
                                 updated_by    UUID         NOT NULL,
                                 is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
                                 created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_project_setting
    ON project_setting(project_id, domain, setting_key) WHERE is_deleted = FALSE;

-- ============================================================
-- 11. 接口测试 — 环境管理
-- ============================================================

CREATE TABLE api_environment (
                                 id          UUID         PRIMARY KEY,
                                 project_id  UUID         NOT NULL,
                                 name        VARCHAR(100) NOT NULL,
                                 description VARCHAR(500) NULL,
                                 scope       VARCHAR(10)  NOT NULL DEFAULT 'project',
                                 is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
                                 sort_order  INT          NOT NULL DEFAULT 0,
                                 is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
                                 created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_env_project ON api_environment(project_id);

CREATE TABLE api_environment_http (
                                      id                 UUID           PRIMARY KEY,
                                      environment_id     UUID           NOT NULL,
                                      protocol           VARCHAR(10)    NOT NULL DEFAULT 'http',
                                      base_url           VARCHAR(2000)  NOT NULL,
                                      connect_timeout    INT            NOT NULL DEFAULT 5000,
                                      read_timeout       INT            NOT NULL DEFAULT 30000,
                                      follow_redirects   BOOLEAN        NOT NULL DEFAULT TRUE,
                                      trust_all_certs    BOOLEAN        NOT NULL DEFAULT FALSE,
                                      default_headers    JSONB          NOT NULL DEFAULT '[]',
                                      is_deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
                                      created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ehttp_env ON api_environment_http(environment_id);

CREATE TABLE api_environment_variable (
                                          id              UUID          PRIMARY KEY,
                                          environment_id  UUID          NOT NULL,
                                          name            VARCHAR(100)  NOT NULL,
                                          value           TEXT          NULL,
                                          description     VARCHAR(500)  NULL,
                                          sensitive       BOOLEAN       NOT NULL DEFAULT FALSE,
                                          sort_order      INT           NOT NULL DEFAULT 0,
                                          is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
                                          created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evar_env ON api_environment_variable(environment_id, sort_order);

CREATE TABLE api_data_source (
                                 id              UUID          PRIMARY KEY,
                                 environment_id  UUID          NOT NULL,
                                 name            VARCHAR(100)  NOT NULL,
                                 type            VARCHAR(20)   NOT NULL,
                                 connection_url  VARCHAR(2000) NOT NULL,
                                 username        VARCHAR(200)  NULL,
                                 password_cipher VARCHAR(1000) NULL,
                                 driver_class   VARCHAR(200)  NULL,
                                 pool_size       INT           NOT NULL DEFAULT 5,
                                 is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
                                 created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ds_env ON api_data_source(environment_id);

CREATE TABLE api_environment_processor (
                                           id              UUID         PRIMARY KEY,
                                           environment_id  UUID         NOT NULL,
                                           processor_type  VARCHAR(20)  NOT NULL,
                                           name            VARCHAR(100) NOT NULL,
                                           enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
                                           sort_order      INT          NOT NULL DEFAULT 0,
                                           config          JSONB        NOT NULL,
                                           is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                                           created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eproc_env ON api_environment_processor(environment_id, sort_order);

-- ============================================================
-- 12. 接口测试 — 接口管理
-- ============================================================

CREATE TABLE api_interface (
                               id          UUID         PRIMARY KEY,
                               project_id  UUID         NOT NULL,
                               module_id   UUID         NULL,
                               name        VARCHAR(200) NOT NULL,
                               method      VARCHAR(10)  NOT NULL,
                               url         VARCHAR(2000) NOT NULL,
                               description TEXT         NULL,
                               status      VARCHAR(20)  NOT NULL DEFAULT 'draft',
                               headers     JSONB        NOT NULL DEFAULT '[]',
                               params      JSONB        NOT NULL DEFAULT '[]',
                               body_type   VARCHAR(20)  NULL,
                               body        JSONB        NULL,
                               auth_type   VARCHAR(20)  NULL,
                               auth_config JSONB        NULL,
                               timeout     INT          NOT NULL DEFAULT 30000,
                               follow_redirects BOOLEAN NOT NULL DEFAULT TRUE,
                               is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
                               created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_intf_project ON api_interface(project_id);
CREATE INDEX idx_intf_module ON api_interface(module_id);

CREATE TABLE api_interface_step (
                                    id              UUID         PRIMARY KEY,
                                    interface_id    UUID         NOT NULL,
                                    name            VARCHAR(200) NOT NULL,
                                    sort_order      INT          NOT NULL DEFAULT 0,
                                    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
                                    request_config  JSONB        NOT NULL,
                                    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                                    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_istep_interface ON api_interface_step(interface_id, sort_order);

CREATE TABLE api_interface_variable (
                                        id            UUID          PRIMARY KEY,
                                        interface_id  UUID          NOT NULL,
                                        name          VARCHAR(100)  NOT NULL,
                                        value         TEXT          NULL,
                                        description   VARCHAR(500)  NULL,
                                        sort_order    INT           NOT NULL DEFAULT 0,
                                        is_deleted    BOOLEAN       NOT NULL DEFAULT FALSE,
                                        created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ivar_interface ON api_interface_variable(interface_id, sort_order);

CREATE TABLE api_import_mapping (
                                    id             UUID         PRIMARY KEY,
                                    project_id     UUID         NOT NULL,
                                    source_type    VARCHAR(20)  NOT NULL,
                                    source_id      VARCHAR(200) NOT NULL,
                                    target_intf_id UUID         NULL,
                                    status         VARCHAR(20)  NOT NULL DEFAULT 'pending',
                                    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
                                    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_imap_project ON api_import_mapping(project_id);

CREATE TABLE api_interface_follow (
                                      id            UUID      PRIMARY KEY,
                                      interface_id  UUID      NOT NULL,
                                      user_id       UUID      NOT NULL,
                                      is_deleted    BOOLEAN   NOT NULL DEFAULT FALSE,
                                      created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_intf_follow ON api_interface_follow(interface_id, user_id) WHERE is_deleted = false;

CREATE TABLE api_interface_change_log (
                                          id            UUID         PRIMARY KEY,
                                          interface_id  UUID         NOT NULL,
                                          version       INT          NOT NULL,
                                          change_type   VARCHAR(20)  NOT NULL,
                                          content_diff  JSONB        NULL,
                                          created_by    UUID         NOT NULL,
                                          is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
                                          created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_iclog_interface ON api_interface_change_log(interface_id, version DESC);

CREATE TABLE api_import_record (
                                   id              UUID         PRIMARY KEY,
                                   project_id      UUID         NOT NULL,
                                   repository_id   UUID         NULL,
                                   source_type     VARCHAR(20)  NOT NULL,
                                   source_name     VARCHAR(200) NOT NULL,
                                   status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
                                   imported_count  INT          NOT NULL DEFAULT 0,
                                   skipped_count   INT          NOT NULL DEFAULT 0,
                                   error_message   TEXT         NULL,
                                   is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                                   created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_irecord_project ON api_import_record(project_id, created_at DESC);
CREATE INDEX idx_irecord_repository ON api_import_record(repository_id) WHERE repository_id IS NOT NULL AND is_deleted = FALSE;

CREATE TABLE api_debug_record (
                                  id            UUID         PRIMARY KEY,
                                  project_id    UUID         NOT NULL,
                                  user_id       UUID         NOT NULL,
                                  name          VARCHAR(200) NULL,
                                  method        VARCHAR(10)  NOT NULL,
                                  url           VARCHAR(2000) NOT NULL,
                                  headers       JSONB        NOT NULL DEFAULT '[]',
                                  params        JSONB        NOT NULL DEFAULT '[]',
                                  body_type     VARCHAR(20)  NULL,
                                  body          JSONB        NULL,
                                  auth_type     VARCHAR(20)  NULL,
                                  auth_config   JSONB        NULL,
                                  last_status   INT          NULL,
                                  last_duration INT          NULL,
                                  is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
                                  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_drec_project_user ON api_debug_record(project_id, user_id);

-- ============================================================
-- 13. 接口测试 — Swagger URL 配置
-- ============================================================

CREATE TABLE api_swagger_url (
                                 id                 UUID           PRIMARY KEY,
                                 project_id         UUID           NOT NULL,
                                 name               VARCHAR(200)   NOT NULL,
                                 url                VARCHAR(2000)  NOT NULL,
                                 format             VARCHAR(20)    NOT NULL DEFAULT 'swagger',
                                 last_import_status VARCHAR(20)    NULL,
                                 last_import_at     TIMESTAMP      NULL,
                                 is_deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
                                 created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_surl_project ON api_swagger_url(project_id);

-- ============================================================
-- 14. 接口测试 — 定时任务
-- ============================================================

CREATE TABLE api_scheduled_task (
                                    id                      UUID          PRIMARY KEY,
                                    project_id              UUID          NOT NULL,
                                    task_type               VARCHAR(30)   NOT NULL,
                                    name                    VARCHAR(200)  NOT NULL,
                                    description             VARCHAR(500)  NULL,
                                    bound_object_id         UUID          NOT NULL,
                                    bound_object_name       VARCHAR(200)  NOT NULL,
                                    environment_id          UUID          NULL,
                                    cron_expression         VARCHAR(50)   NOT NULL,
                                    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
                                    last_execution_status   VARCHAR(20)   NULL,
                                    last_execution_at       TIMESTAMP     NULL,
                                    created_by              UUID          NOT NULL,
                                    is_deleted              BOOLEAN       NOT NULL DEFAULT FALSE,
                                    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stask_project ON api_scheduled_task(project_id);
CREATE INDEX idx_stask_enabled ON api_scheduled_task(enabled, task_type);

CREATE TABLE api_scheduled_task_execution (
                                              id                UUID           PRIMARY KEY,
                                              task_id           UUID           NOT NULL,
                                              project_id        UUID           NOT NULL,
                                              trigger_type      VARCHAR(20)    NOT NULL,
                                              status            VARCHAR(20)    NOT NULL,
                                              error_message     VARCHAR(2000)  NULL,
                                              report_id         UUID           NULL,
                                              import_record_id  UUID           NULL,
                                              triggered_at      TIMESTAMP      NOT NULL,
                                              duration_ms       INT            NULL,
                                              is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
                                              created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stexec_task ON api_scheduled_task_execution(task_id);
CREATE INDEX idx_stexec_project_triggered ON api_scheduled_task_execution(project_id, triggered_at DESC);

-- ============================================================
-- 15. 接口测试 — Mock 服务
-- ============================================================

CREATE TABLE api_mock_definition (
                                 id                 UUID          PRIMARY KEY,
                                 project_id         UUID          NOT NULL,
                                 interface_id       UUID          NULL,
                                 name               VARCHAR(200)  NOT NULL,
                                 description        VARCHAR(500)  NULL,
                                 method             VARCHAR(10)   NOT NULL,
                                 path               VARCHAR(500)  NOT NULL,
                                 priority           INT           NOT NULL DEFAULT 0,
                                 match_rules        JSONB         NOT NULL DEFAULT '[]',
                                 enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
                                 follow_api         BOOLEAN       NOT NULL DEFAULT FALSE,
                                 response_status    INT           NOT NULL DEFAULT 200,
                                 response_headers   JSONB         NOT NULL DEFAULT '{}',
                                 response_body_type VARCHAR(20)   NOT NULL DEFAULT 'json',
                                 response_body      TEXT          NULL,
                                 delay_ms           INT           NOT NULL DEFAULT 0,
                                 hit_count          BIGINT        NOT NULL DEFAULT 0,
                                 last_hit_at        TIMESTAMP     NULL,
                                 is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
                                 created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_mock_project ON api_mock_definition(project_id);
CREATE INDEX idx_api_mock_interface ON api_mock_definition(interface_id);
CREATE INDEX idx_api_mock_path_method ON api_mock_definition(project_id, path, method, priority);

CREATE TABLE api_mock_access_log (
                                 id               UUID          PRIMARY KEY,
                                 mock_id          UUID          NOT NULL,
                                 project_id       UUID          NOT NULL,
                                 method           VARCHAR(10)   NOT NULL,
                                 path             VARCHAR(500)  NOT NULL,
                                 request_headers  JSONB         NULL,
                                 request_body     TEXT          NULL,
                                 response_status  INT           NOT NULL,
                                 response_body    TEXT          NULL,
                                 duration_ms      INT           NULL,
                                 client_ip        VARCHAR(50)   NULL,
                                 is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
                                 created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_mlog_mock ON api_mock_access_log(mock_id);
CREATE INDEX idx_api_mlog_project_created ON api_mock_access_log(project_id, created_at DESC);

-- ============================================================
-- 16. 接口测试 — 测试场景与执行
-- ============================================================

CREATE TABLE api_scene (
                           id             UUID          PRIMARY KEY,
                           project_id     UUID          NOT NULL,
                           module_id      UUID          NULL,
                           name           VARCHAR(200)  NOT NULL,
                           description    TEXT          NULL,
                           environment_id UUID          NULL,
                           variables      JSONB         NOT NULL DEFAULT '[]',
                           processors     JSONB         NOT NULL DEFAULT '[]',
                           failure_rule   VARCHAR(20)   NOT NULL DEFAULT 'all',
                           cookie_config  JSONB         NOT NULL DEFAULT '{}',
                           change_version INT           NOT NULL DEFAULT 1,
                           is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
                           created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scene_project ON api_scene(project_id);
CREATE INDEX idx_scene_module ON api_scene(module_id);

CREATE TABLE api_scene_step (
                                id              UUID          PRIMARY KEY,
                                scene_id        UUID          NOT NULL,
                                name            VARCHAR(200)  NOT NULL,
                                step_type       VARCHAR(20)   NOT NULL DEFAULT 'http',
                                sort_order      INT           NOT NULL DEFAULT 0,
                                enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
                                source_type     VARCHAR(20)   NULL,
                                source_id       UUID          NULL,
                                source_snapshot JSONB         NULL,
                                source_interface_id   UUID         NULL,
                                source_interface_name VARCHAR(200) NULL,
                                request_config  JSONB         NOT NULL,
                                processors      JSONB         NOT NULL DEFAULT '[]',
                                validators      JSONB         NOT NULL DEFAULT '[]',
                                extractors      JSONB         NOT NULL DEFAULT '[]',
                                is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
                                created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sstep_scene ON api_scene_step(scene_id, sort_order);

CREATE TABLE api_scene_step_variable (
                                         id                    UUID          PRIMARY KEY,
                                         step_id               UUID          NOT NULL,
                                         name                  VARCHAR(100)  NOT NULL,
                                         value                 TEXT          NULL,
                                         source                VARCHAR(20)   NOT NULL DEFAULT 'custom',
                                         interface_variable_id UUID          NULL,
                                         description           VARCHAR(500)  NULL,
                                         sort_order            INT           NOT NULL DEFAULT 0,
                                         is_deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
                                         created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_svstep_step ON api_scene_step_variable(step_id, sort_order);
CREATE INDEX idx_svstep_source ON api_scene_step_variable(step_id, source);

CREATE TABLE api_scenario_variable (
                                       id          UUID          PRIMARY KEY,
                                       scene_id    UUID          NOT NULL,
                                       name        VARCHAR(100)  NOT NULL,
                                       value       TEXT          NULL,
                                       description VARCHAR(500)  NULL,
                                       sort_order  INT           NOT NULL DEFAULT 0,
                                       is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
                                       created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_svar_scene ON api_scenario_variable(scene_id, sort_order);

CREATE TABLE api_scene_interface (
                                     id             UUID          PRIMARY KEY,
                                     scene_id       UUID          NOT NULL,
                                     interface_id   UUID          NOT NULL,
                                     sync_mode      VARCHAR(10)   NOT NULL DEFAULT 'copy',
                                     last_synced_at TIMESTAMP     NULL,
                                     is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
                                     created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sintf_scene ON api_scene_interface(scene_id);
CREATE INDEX idx_sintf_interface ON api_scene_interface(interface_id);

CREATE TABLE api_execution_record (
                                      id             UUID          PRIMARY KEY,
                                      project_id     UUID          NOT NULL,
                                      scene_id       UUID          NOT NULL,
                                      environment_id UUID          NULL,
                                      execution_mode VARCHAR(20)   NOT NULL DEFAULT 'platform',
                                      status         VARCHAR(20)   NOT NULL DEFAULT 'pending',
                                      trigger_type   VARCHAR(20)   NOT NULL DEFAULT 'manual',
                                      report_id      UUID          NULL,
                                      pipeline_id    VARCHAR(100)  NULL,
                                      pipeline_url   VARCHAR(500)  NULL,
                                      error_message  VARCHAR(2000) NULL,
                                       executed_at    TIMESTAMP     NOT NULL,
                                       duration_ms    INT           NULL,
                                       repository_id  UUID          NULL,
                                       is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
                                      created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exec_scene_id ON api_execution_record(scene_id, executed_at DESC);
CREATE INDEX idx_exec_project_executed ON api_execution_record(project_id, executed_at DESC);
CREATE INDEX idx_exec_status ON api_execution_record(status);
CREATE INDEX idx_api_execution_record_repository_id ON api_execution_record(repository_id) WHERE repository_id IS NOT NULL AND is_deleted = FALSE;

CREATE TABLE api_report (
                            id                 UUID          PRIMARY KEY,
                            project_id         UUID          NOT NULL,
                            execution_record_id UUID         NOT NULL,
                            scene_id           UUID          NOT NULL,
                            scene_name         VARCHAR(200)  NOT NULL,
                            environment_name   VARCHAR(100)  NULL,
                            execution_mode     VARCHAR(20)   NOT NULL DEFAULT 'platform',
                            status             VARCHAR(20)   NOT NULL,
                            summary            JSONB         NOT NULL,
                            step_results       JSONB         NOT NULL,
                            ryze_snapshot      JSONB         NULL,
                            share_enabled      BOOLEAN       NOT NULL DEFAULT FALSE,
                            share_token        VARCHAR(64)   NULL,
                            share_expires_at   TIMESTAMP     NULL,
                            is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
                            created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_report_scene_id ON api_report(scene_id, created_at DESC);
CREATE INDEX idx_report_project_created ON api_report(project_id, created_at DESC);
CREATE UNIQUE INDEX uk_report_share_token ON api_report(share_token) WHERE share_token IS NOT NULL;

CREATE TABLE api_change_history (
                                    id           UUID          PRIMARY KEY,
                                    project_id   UUID          NOT NULL,
                                    target_type  VARCHAR(20)   NOT NULL,
                                    target_id    UUID          NOT NULL,
                                    version      INT           NOT NULL,
                                    change_type  VARCHAR(20)   NOT NULL,
                                    content_diff JSONB         NULL,
                                    created_by   UUID          NOT NULL,
                                    is_deleted   BOOLEAN       NOT NULL DEFAULT FALSE,
                                    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_target ON api_change_history(target_type, target_id, version DESC);

-- ============================================================
-- 17. 接口测试 — 公共组件
-- ============================================================

CREATE TABLE api_component (
                                  id           UUID         PRIMARY KEY,
                                  scope        VARCHAR(10)  NOT NULL DEFAULT 'project',
                                  workspace_id UUID         NULL,
                                  project_id   UUID         NULL,
                                  type         VARCHAR(30)  NOT NULL,
                                  name         VARCHAR(100) NOT NULL,
                                  description  VARCHAR(500) NULL,
                                  config       JSONB        NOT NULL,
                                  enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
                                  updated_by   UUID         NOT NULL,
                                  is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
                                  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_component_project ON api_component(project_id, type) WHERE scope = 'project' AND is_deleted = FALSE;
CREATE INDEX idx_api_component_workspace ON api_component(workspace_id, type) WHERE scope = 'workspace' AND is_deleted = FALSE;
CREATE UNIQUE INDEX uk_api_component_global ON api_component(type, name) WHERE scope = 'global' AND is_deleted = FALSE;
CREATE UNIQUE INDEX uk_api_component_project ON api_component(project_id, type, name) WHERE scope = 'project' AND is_deleted = FALSE;
CREATE UNIQUE INDEX uk_api_component_workspace ON api_component(workspace_id, type, name) WHERE scope = 'workspace' AND is_deleted = FALSE;

-- ============================================================
-- 18. 接口测试 — 函数表（内置 + 自定义）
-- ============================================================

CREATE TABLE api_function (
                              id           UUID         PRIMARY KEY,
                              scope        VARCHAR(10)  NOT NULL DEFAULT 'project',
                              workspace_id UUID         NULL,
                              project_id   UUID         NULL,
                              name         VARCHAR(100) NOT NULL,
                              description  VARCHAR(500) NULL,
                              params_desc  VARCHAR(500) NULL,
                              script       TEXT         NOT NULL,
                              type         VARCHAR(20)  NOT NULL DEFAULT 'custom',
                              enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
                              updated_by   UUID         NOT NULL,
                              is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
                              created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_function_project ON api_function(project_id, name) WHERE scope = 'project' AND is_deleted = FALSE;
CREATE INDEX idx_function_workspace ON api_function(workspace_id, name) WHERE scope = 'workspace' AND is_deleted = FALSE;
CREATE UNIQUE INDEX uk_function_global ON api_function(name) WHERE scope = 'global' AND is_deleted = FALSE;

-- ============================================================
-- 19. 接口测试 — GitLab 仓库配置
-- ============================================================

CREATE TABLE api_gitlab_repository (
                                       id                    UUID         PRIMARY KEY,
                                       project_id            UUID         NOT NULL,
                                       name                  VARCHAR(100) NOT NULL,
                                       repo_url              VARCHAR(500) NOT NULL,
                                       branch                VARCHAR(200) NOT NULL DEFAULT 'main',
                                       access_token_cipher   VARCHAR(1000) NOT NULL,
                                       token_suffix          VARCHAR(4)   NULL,
                                       test_source_path      VARCHAR(500) NULL,
                                       last_import_status    VARCHAR(20)  NULL,
                                       last_import_at        TIMESTAMP    NULL,
                                       last_metadata_sync_at TIMESTAMP    NULL,
                                       last_commit_sha       VARCHAR(40)  NULL,
                                       annotation_filter      VARCHAR(500) NULL,
                                       only_with_resource_path BOOLEAN      NOT NULL DEFAULT FALSE,
                                       auto_sync_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
                                       is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
                                       created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_glab_repository_project ON api_gitlab_repository(project_id) WHERE is_deleted = FALSE;

CREATE TABLE api_gitlab_test_class_metadata (
                                                id                   UUID         PRIMARY KEY,
                                                repository_id        UUID         NOT NULL,
                                                full_class_name      VARCHAR(500) NOT NULL,
                                                class_annotations    JSONB        NOT NULL DEFAULT '[]',
                                                display_name         VARCHAR(200) NULL,
                                                description          TEXT         NULL,
                                                resource_path        VARCHAR(500) NULL,
                                                is_executable        BOOLEAN      NOT NULL DEFAULT FALSE,
                                                methods              JSONB        NOT NULL DEFAULT '[]',
                                                is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
                                                created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gmeta_repository ON api_gitlab_test_class_metadata(repository_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_gmeta_class_name ON api_gitlab_test_class_metadata(repository_id, full_class_name) WHERE is_deleted = FALSE;

CREATE TABLE api_gitlab_test_scope (
                                       id                   UUID         PRIMARY KEY,
                                       repository_id        UUID         NOT NULL,
                                       variable_name        VARCHAR(100) NOT NULL,
                                       scope_type           VARCHAR(20)  NOT NULL,
                                       description          VARCHAR(500) NULL,
                                       is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
                                       created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gscope_repository ON api_gitlab_test_scope(repository_id) WHERE is_deleted = FALSE;

CREATE TABLE api_gitlab_sync_history (
                                         id                   UUID         PRIMARY KEY,
                                         repository_id        UUID         NOT NULL,
                                         sync_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         class_count          INT          NOT NULL DEFAULT 0,
                                         method_count         INT          NOT NULL DEFAULT 0,
                                         commit_sha           VARCHAR(100) NULL,
                                         status               VARCHAR(20)  NOT NULL DEFAULT 'success',
                                         is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
                                         created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gsync_repository ON api_gitlab_sync_history(repository_id, sync_at DESC) WHERE is_deleted = FALSE;

-- ============================================================
-- 20. 表与列注释
-- ============================================================

-- 系统管理
COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.id IS '用户唯一标识';
COMMENT ON COLUMN sys_user.username IS '登录用户名，唯一';
COMMENT ON COLUMN sys_user.name IS '用户显示名称';
COMMENT ON COLUMN sys_user.email IS '邮箱地址，用于登录和通知';
COMMENT ON COLUMN sys_user.password_hash IS '密码 BCrypt 哈希值';
COMMENT ON COLUMN sys_user.avatar_url IS '头像 URL';
COMMENT ON COLUMN sys_user.status IS '用户状态：active=正常, disabled=禁用';
COMMENT ON COLUMN sys_user.last_active_workspace_id IS '上次登录默认进入的工作空间 ID';

COMMENT ON TABLE sys_role IS '角色表（系统级 + 空间级）';
COMMENT ON COLUMN sys_role.id IS '角色唯一标识';
COMMENT ON COLUMN sys_role.name IS '角色名称，唯一';
COMMENT ON COLUMN sys_role.description IS '角色描述';
COMMENT ON COLUMN sys_role.type IS '角色类型：system=系统级, workspace=空间级';
COMMENT ON COLUMN sys_role.is_system IS '是否系统预置角色（不可删除）';
COMMENT ON COLUMN sys_role.permissions IS '权限点代码列表（JSON 数组）';

COMMENT ON TABLE sys_user_role IS '用户-角色关联表';
COMMENT ON COLUMN sys_user_role.id IS '关联唯一标识';
COMMENT ON COLUMN sys_user_role.user_id IS '用户 ID，关联 sys_user.id';
COMMENT ON COLUMN sys_user_role.role_id IS '角色 ID，关联 sys_role.id';
COMMENT ON COLUMN sys_user_role.assigned_at IS '角色分配时间';

COMMENT ON TABLE sys_permission IS '权限点表（树形结构，通过 parent_code 组织层级）';
COMMENT ON COLUMN sys_permission.id IS '权限点唯一标识';
COMMENT ON COLUMN sys_permission.code IS '权限点代码，如 user:view';
COMMENT ON COLUMN sys_permission.name IS '权限点显示名称';
COMMENT ON COLUMN sys_permission.parent_code IS '父级权限点代码，NULL=根节点';
COMMENT ON COLUMN sys_permission.module IS '所属模块';
COMMENT ON COLUMN sys_permission.scope IS '作用域：global=全局, workspace=空间级';
COMMENT ON COLUMN sys_permission.sort_order IS '同级排序序号';

COMMENT ON TABLE sys_audit_log IS '审计日志表（记录关键操作的字段级变更）';
COMMENT ON COLUMN sys_audit_log.id IS '日志唯一标识（雪花算法）';
COMMENT ON COLUMN sys_audit_log.operator_id IS '操作人 ID';
COMMENT ON COLUMN sys_audit_log.operator_name IS '操作人名称（冗余，便于快速展示）';
COMMENT ON COLUMN sys_audit_log.operation IS '操作类型';
COMMENT ON COLUMN sys_audit_log.entity_type IS '操作实体类型（如 Bug/TestPlan）';
COMMENT ON COLUMN sys_audit_log.entity_id IS '操作实体 ID';
COMMENT ON COLUMN sys_audit_log.changes IS '字段变更详情（JSON，记录旧值/新值）';
COMMENT ON COLUMN sys_audit_log.request_ip IS '请求 IP 地址';

-- 工作空间
COMMENT ON TABLE ws_workspace IS '工作空间表（多租户隔离单元）';
COMMENT ON COLUMN ws_workspace.id IS '工作空间唯一标识';
COMMENT ON COLUMN ws_workspace.name IS '工作空间名称，唯一';
COMMENT ON COLUMN ws_workspace.description IS '工作空间描述';
COMMENT ON COLUMN ws_workspace.status IS '工作空间状态：active=正常, dissolved=已解散';

COMMENT ON TABLE ws_user IS '用户-工作空间关联表';
COMMENT ON COLUMN ws_user.id IS '关联唯一标识';
COMMENT ON COLUMN ws_user.user_id IS '用户 ID，关联 sys_user.id';
COMMENT ON COLUMN ws_user.workspace_id IS '工作空间 ID，关联 ws_workspace.id';
COMMENT ON COLUMN ws_user.workspace_role IS '空间角色 ID，关联 sys_role.id（预置角色 UUID）';
COMMENT ON COLUMN ws_user.default_project_id IS '空间内默认项目 ID，关联 ws_project.id';
COMMENT ON COLUMN ws_user.joined_at IS '加入时间';

COMMENT ON TABLE ws_invitation IS '邀请链接表';
COMMENT ON COLUMN ws_invitation.id IS '邀请唯一标识';
COMMENT ON COLUMN ws_invitation.workspace_id IS '所属工作空间 ID，关联 ws_workspace.id';
COMMENT ON COLUMN ws_invitation.token IS '邀请令牌，唯一';
COMMENT ON COLUMN ws_invitation.created_by IS '创建人 ID，关联 sys_user.id';
COMMENT ON COLUMN ws_invitation.expires_at IS '过期时间，NULL=永不过期';
COMMENT ON COLUMN ws_invitation.max_uses IS '最大使用次数，NULL=不限';
COMMENT ON COLUMN ws_invitation.use_count IS '已使用次数';
COMMENT ON COLUMN ws_invitation.status IS '邀请状态：active=有效, revoked=已撤销';

COMMENT ON TABLE ws_project IS '项目表（工作空间内的业务项目）';
COMMENT ON COLUMN ws_project.id IS '项目唯一标识';
COMMENT ON COLUMN ws_project.workspace_id IS '所属工作空间 ID，关联 ws_workspace.id';
COMMENT ON COLUMN ws_project.name IS '项目名称（同一工作空间内唯一）';
COMMENT ON COLUMN ws_project.description IS '项目描述';
COMMENT ON COLUMN ws_project.status IS '项目状态：active=正常, archived=已归档';
COMMENT ON COLUMN ws_project.start_time IS '项目开始时间';
COMMENT ON COLUMN ws_project.end_time IS '项目结束时间';
COMMENT ON COLUMN ws_project.created_by IS '创建人 ID，关联 sys_user.id';

-- 功能测试
COMMENT ON TABLE test_case_node IS '测试用例节点表（脑图节点，支持树形嵌套）';
COMMENT ON COLUMN test_case_node.id IS '节点唯一标识';
COMMENT ON COLUMN test_case_node.document_id IS '所属文档 ID，关联 test_case_module.id（type=document 的模块）';
COMMENT ON COLUMN test_case_node.parent_id IS '父级节点 ID，NULL=根节点';
COMMENT ON COLUMN test_case_node.type IS '节点类型：case=用例, normal=普通, precondition=前置条件, step=步骤, expected=预期结果';
COMMENT ON COLUMN test_case_node.title IS '节点标题';
COMMENT ON COLUMN test_case_node.priority IS '用例优先级：P0/P1/P2/P3，仅 case 节点有值';
COMMENT ON COLUMN test_case_node.sort_order IS '排序序号';
COMMENT ON COLUMN test_case_node.version IS '乐观锁版本号，用于并发冲突检测';
COMMENT ON COLUMN test_case_node.ai_generated IS 'AI 生成标识（挂载执行器写入，可手动移除）';

COMMENT ON TABLE test_plan IS '测试计划表（用例执行计划）';
COMMENT ON COLUMN test_plan.id IS '计划唯一标识';
COMMENT ON COLUMN test_plan.project_id IS '所属项目 ID，关联 ws_project.id';
COMMENT ON COLUMN test_plan.name IS '计划名称';
COMMENT ON COLUMN test_plan.description IS '计划描述';
COMMENT ON COLUMN test_plan.status IS '计划状态：new=新建, in_progress=进行中, completed=已完成, closed=已关闭';
COMMENT ON COLUMN test_plan.executor_id IS '执行人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_plan.start_time IS '计划开始时间';
COMMENT ON COLUMN test_plan.end_time IS '计划结束时间';
COMMENT ON COLUMN test_plan.environment IS '测试环境描述';
COMMENT ON COLUMN test_plan.snapshot_synced_at IS '计划快照最近同步时间（快照新建/调整/同步后写入，执行顺序推荐据此提示快照过期）';

COMMENT ON TABLE test_plan_module_snapshot IS '计划模块快照表（创建计划时固化模块结构）';
COMMENT ON COLUMN test_plan_module_snapshot.id IS '快照唯一标识';
COMMENT ON COLUMN test_plan_module_snapshot.plan_id IS '所属计划 ID，关联 test_plan.id';
COMMENT ON COLUMN test_plan_module_snapshot.original_module_id IS '原始模块 ID（用于差量同步定位变更）';
COMMENT ON COLUMN test_plan_module_snapshot.parent_id IS '快照父级模块 ID';
COMMENT ON COLUMN test_plan_module_snapshot.name IS '模块名称（固化原值，不随源数据变化）';
COMMENT ON COLUMN test_plan_module_snapshot.type IS '模块类型：directory=目录, document=文档';
COMMENT ON COLUMN test_plan_module_snapshot.sort_order IS '排序序号';

COMMENT ON TABLE test_plan_node_snapshot IS '计划节点快照表（创建计划时固化节点内容）';
COMMENT ON COLUMN test_plan_node_snapshot.id IS '快照节点唯一标识';
COMMENT ON COLUMN test_plan_node_snapshot.plan_id IS '所属计划 ID，关联 test_plan.id';
COMMENT ON COLUMN test_plan_node_snapshot.original_node_id IS '原始节点 ID（用于差量同步定位变更）';
COMMENT ON COLUMN test_plan_node_snapshot.document_snapshot_id IS '所属文档快照 ID，关联 test_plan_module_snapshot.id';
COMMENT ON COLUMN test_plan_node_snapshot.parent_id IS '快照父级节点 ID';
COMMENT ON COLUMN test_plan_node_snapshot.title IS '节点标题（固化原值）';
COMMENT ON COLUMN test_plan_node_snapshot.type IS '节点类型：case/normal/precondition/step/expected';
COMMENT ON COLUMN test_plan_node_snapshot.priority IS '用例优先级（固化原值）';
COMMENT ON COLUMN test_plan_node_snapshot.is_associated IS '是否被选入计划';
COMMENT ON COLUMN test_plan_node_snapshot.last_result IS '最新执行结果：pass/fail/block/untested';
COMMENT ON COLUMN test_plan_node_snapshot.last_executor_id IS '最近一次执行人 ID';
COMMENT ON COLUMN test_plan_node_snapshot.last_executed_at IS '最近一次执行时间';
COMMENT ON COLUMN test_plan_node_snapshot.ai_generated IS 'AI 生成标识（随计划快照继承）';

COMMENT ON TABLE test_plan_execution_record IS '计划执行记录表（每次执行的完整历史）';
COMMENT ON COLUMN test_plan_execution_record.id IS '执行记录唯一标识';
COMMENT ON COLUMN test_plan_execution_record.plan_id IS '所属计划 ID，关联 test_plan.id';
COMMENT ON COLUMN test_plan_execution_record.snapshot_node_id IS '快照节点 ID，关联 test_plan_node_snapshot.id';
COMMENT ON COLUMN test_plan_execution_record.executor_id IS '执行人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_plan_execution_record.result IS '执行结果：pass/fail/block/untested';
COMMENT ON COLUMN test_plan_execution_record.note IS '执行备注';
COMMENT ON COLUMN test_plan_execution_record.executed_at IS '执行时间';

COMMENT ON TABLE test_review IS '测试评审表（用例评审流程）';
COMMENT ON COLUMN test_review.id IS '评审唯一标识';
COMMENT ON COLUMN test_review.project_id IS '所属项目 ID，关联 ws_project.id';
COMMENT ON COLUMN test_review.title IS '评审标题';
COMMENT ON COLUMN test_review.description IS '评审描述';
COMMENT ON COLUMN test_review.initiator_id IS '发起人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_review.participant_ids IS '参与者 ID 列表（JSON 数组）';
COMMENT ON COLUMN test_review.status IS '评审状态：new=待评审, in_progress=评审中, completed=已完成';

COMMENT ON TABLE test_review_module_snapshot IS '评审模块快照表（创建评审时固化模块结构）';
COMMENT ON COLUMN test_review_module_snapshot.id IS '快照唯一标识';
COMMENT ON COLUMN test_review_module_snapshot.review_id IS '所属评审 ID，关联 test_review.id';
COMMENT ON COLUMN test_review_module_snapshot.original_module_id IS '原始模块 ID';
COMMENT ON COLUMN test_review_module_snapshot.parent_id IS '快照父级模块 ID';
COMMENT ON COLUMN test_review_module_snapshot.name IS '模块名称（固化原值）';
COMMENT ON COLUMN test_review_module_snapshot.type IS '模块类型：directory/document';
COMMENT ON COLUMN test_review_module_snapshot.sort_order IS '排序序号';

COMMENT ON TABLE test_review_node_snapshot IS '评审节点快照表（创建评审时固化节点内容）';
COMMENT ON COLUMN test_review_node_snapshot.id IS '快照节点唯一标识';
COMMENT ON COLUMN test_review_node_snapshot.review_id IS '所属评审 ID，关联 test_review.id';
COMMENT ON COLUMN test_review_node_snapshot.original_node_id IS '原始节点 ID';
COMMENT ON COLUMN test_review_node_snapshot.document_snapshot_id IS '所属文档快照 ID，关联 test_review_module_snapshot.id';
COMMENT ON COLUMN test_review_node_snapshot.parent_id IS '快照父级节点 ID';
COMMENT ON COLUMN test_review_node_snapshot.title IS '节点标题（固化原值）';
COMMENT ON COLUMN test_review_node_snapshot.type IS '节点类型：case/normal/precondition/step/expected';
COMMENT ON COLUMN test_review_node_snapshot.priority IS '用例优先级（固化原值）';
COMMENT ON COLUMN test_review_node_snapshot.is_associated IS '是否被选入评审';
COMMENT ON COLUMN test_review_node_snapshot.last_mark IS '最新评审标记：pass/fail，NULL=待评审';
COMMENT ON COLUMN test_review_node_snapshot.last_reviewer_id IS '最近一次评审人 ID';
COMMENT ON COLUMN test_review_node_snapshot.last_reviewed_at IS '最近一次评审时间';
COMMENT ON COLUMN test_review_node_snapshot.ai_generated IS 'AI 生成标识（随评审快照继承）';

COMMENT ON TABLE test_review_record IS '评审记录表（每次评审操作的完整历史）';
COMMENT ON COLUMN test_review_record.id IS '评审记录唯一标识';
COMMENT ON COLUMN test_review_record.review_id IS '所属评审 ID，关联 test_review.id';
COMMENT ON COLUMN test_review_record.snapshot_node_id IS '快照节点 ID，关联 test_review_node_snapshot.id';
COMMENT ON COLUMN test_review_record.reviewer_id IS '评审人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_review_record.operation_type IS '操作类型：mark=标记, comment=评论';
COMMENT ON COLUMN test_review_record.mark IS '评审标记：pass/fail，NULL=仅评论无标记';
COMMENT ON COLUMN test_review_record.comment IS '评论内容';

-- 缺陷管理
COMMENT ON TABLE bug IS '缺陷表（三态模型：active ↔ resolved/closed，可重开）';
COMMENT ON COLUMN bug.id IS '缺陷唯一标识';
COMMENT ON COLUMN bug.project_id IS '所属项目 ID，关联 ws_project.id';
COMMENT ON COLUMN bug.title IS '缺陷标题';
COMMENT ON COLUMN bug.severity IS '严重等级：fatal/serious/general/minor';
COMMENT ON COLUMN bug.priority IS '优先级：high/medium/low';
COMMENT ON COLUMN bug.status IS '缺陷状态：active/resolved/rejected/closed';
COMMENT ON COLUMN bug.repro_steps IS '重现步骤（Markdown 格式）';
COMMENT ON COLUMN bug.reporter_id IS '报告人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug.assignee_id IS '处理人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug.related_case_id IS '关联用例节点 ID';
COMMENT ON COLUMN bug.related_plan_id IS '关联计划 ID';
COMMENT ON COLUMN bug.bug_type IS '缺陷类型';
COMMENT ON COLUMN bug.module_id IS '所属模块 ID，关联 project_module.id';
COMMENT ON COLUMN bug.keywords IS '搜索关键词（逗号分隔）';
COMMENT ON COLUMN bug.due_date IS '期望解决日期';
COMMENT ON COLUMN bug.confirmed IS '是否已确认';
COMMENT ON COLUMN bug.reopen_count IS '重开次数';
COMMENT ON COLUMN bug.last_reopened_at IS '最近一次重开时间';
COMMENT ON COLUMN bug.resolution IS '解决方案';
COMMENT ON COLUMN bug.duplicate_of_bug_id IS '重复的原始缺陷 ID';
COMMENT ON COLUMN bug.resolved_by IS '解决人 ID';
COMMENT ON COLUMN bug.resolved_at IS '解决时间';
COMMENT ON COLUMN bug.rejected_by IS '拒绝人 ID';
COMMENT ON COLUMN bug.closed_by IS '关闭人 ID';
COMMENT ON COLUMN bug.closed_at IS '关闭时间';

COMMENT ON TABLE bug_log IS '缺陷操作日志表';
COMMENT ON COLUMN bug_log.id IS '日志唯一标识';
COMMENT ON COLUMN bug_log.bug_id IS '缺陷 ID，关联 bug.id';
COMMENT ON COLUMN bug_log.operator_id IS '操作人 ID';
COMMENT ON COLUMN bug_log.operation_type IS '操作类型（create/assign/resolve/reject/close/reopen）';
COMMENT ON COLUMN bug_log.content IS '操作内容详情';

COMMENT ON TABLE bug_attachment IS '缺陷附件表';
COMMENT ON COLUMN bug_attachment.id IS '附件唯一标识';
COMMENT ON COLUMN bug_attachment.bug_id IS '缺陷 ID，关联 bug.id';
COMMENT ON COLUMN bug_attachment.file_name IS '文件名';
COMMENT ON COLUMN bug_attachment.storage_path IS '存储路径';
COMMENT ON COLUMN bug_attachment.file_size IS '文件大小（字节）';
COMMENT ON COLUMN bug_attachment.content_type IS 'MIME 类型';
COMMENT ON COLUMN bug_attachment.uploader_id IS '上传人 ID';

-- AI 基础设施
COMMENT ON TABLE ai_config IS 'AI 配置表（系统级单行：总开关、Embedding 配置）';
COMMENT ON COLUMN ai_config.id IS '主键';
COMMENT ON COLUMN ai_config.embedding_provider IS 'Embedding 供应商标识';
COMMENT ON COLUMN ai_config.embedding_base_url IS 'Embedding 服务地址';
COMMENT ON COLUMN ai_config.embedding_api_key_cipher IS 'Embedding 服务密钥（加密）';
COMMENT ON COLUMN ai_config.embedding_key_suffix IS 'Embedding 密钥末 4 位（脱敏展示）';
COMMENT ON COLUMN ai_config.embedding_model IS 'Embedding 模型名';
COMMENT ON COLUMN ai_config.embedding_dimension IS '向量维度（1-2000）';
COMMENT ON COLUMN ai_config.embedding_extra_params IS 'Embedding 请求附加参数';
COMMENT ON COLUMN ai_config.enabled IS 'AI 能力总开关';
COMMENT ON COLUMN ai_config.settings IS 'AI 系统配置项键值集';

COMMENT ON TABLE ai_prompt_template IS '智能体提示词模板表（仅存自定义覆盖，恢复默认即逻辑删除）';
COMMENT ON COLUMN ai_prompt_template.id IS '主键';
COMMENT ON COLUMN ai_prompt_template.function_type IS '功能类型枚举，每功能至多一条有效记录';
COMMENT ON COLUMN ai_prompt_template.role_instruction IS '角色指令段';
COMMENT ON COLUMN ai_prompt_template.format_constraint IS '输出格式约束段';
COMMENT ON COLUMN ai_prompt_template.format_editable IS '格式约束段编辑开关（默认关闭锁定）';
COMMENT ON COLUMN ai_prompt_template.updated_by IS '最后更新人';

COMMENT ON TABLE ai_analysis_task IS 'AI 异步任务表（任务状态机与结果快照）';
COMMENT ON COLUMN ai_analysis_task.id IS '任务 ID';
COMMENT ON COLUMN ai_analysis_task.workspace_id IS '归属工作空间';
COMMENT ON COLUMN ai_analysis_task.project_id IS '归属项目';
COMMENT ON COLUMN ai_analysis_task.type IS '任务类型';
COMMENT ON COLUMN ai_analysis_task.target_id IS '目标对象 ID';
COMMENT ON COLUMN ai_analysis_task.status IS '任务状态：pending/running/success/failed/cancelled';
COMMENT ON COLUMN ai_analysis_task.progress IS '进度百分比（0-100）';
COMMENT ON COLUMN ai_analysis_task.result IS '结果快照';
COMMENT ON COLUMN ai_analysis_task.error_message IS '失败原因';
COMMENT ON COLUMN ai_analysis_task.executor_instance IS '执行实例标识';
COMMENT ON COLUMN ai_analysis_task.created_by IS '发起人';

COMMENT ON TABLE ai_invocation_log IS 'AI 调用审计表（仅调用元数据，不存 Prompt 与生成内容）';
COMMENT ON COLUMN ai_invocation_log.id IS '主键';
COMMENT ON COLUMN ai_invocation_log.user_id IS '调用用户';
COMMENT ON COLUMN ai_invocation_log.workspace_id IS '工作空间';
COMMENT ON COLUMN ai_invocation_log.project_id IS '项目';
COMMENT ON COLUMN ai_invocation_log.function_type IS '功能类型枚举';
COMMENT ON COLUMN ai_invocation_log.model IS '实际调用的模型名';
COMMENT ON COLUMN ai_invocation_log.duration_ms IS '端到端耗时（毫秒）';
COMMENT ON COLUMN ai_invocation_log.prompt_tokens IS '输入 token';
COMMENT ON COLUMN ai_invocation_log.completion_tokens IS '输出 token';
COMMENT ON COLUMN ai_invocation_log.status IS '调用状态';
COMMENT ON COLUMN ai_invocation_log.error_code IS '失败错误码';

COMMENT ON TABLE ai_chat_model IS 'AI 对话模型配置表（多行：每行一个可用对话模型）';
COMMENT ON COLUMN ai_chat_model.id IS '主键';
COMMENT ON COLUMN ai_chat_model.name IS '显示名（全局唯一）';
COMMENT ON COLUMN ai_chat_model.provider IS '供应商标识';
COMMENT ON COLUMN ai_chat_model.base_url IS '服务地址';
COMMENT ON COLUMN ai_chat_model.api_key_cipher IS '服务密钥（AES-256-GCM 加密）';
COMMENT ON COLUMN ai_chat_model.key_suffix IS '密钥末 4 位（脱敏展示）';
COMMENT ON COLUMN ai_chat_model.model IS '模型名';
COMMENT ON COLUMN ai_chat_model.extra_params IS '请求附加参数';
COMMENT ON COLUMN ai_chat_model.enabled IS '启用状态';
COMMENT ON COLUMN ai_chat_model.is_default IS '是否系统默认模型（全系统唯一）';
COMMENT ON COLUMN ai_chat_model.updated_by IS '最后更新人';

COMMENT ON TABLE ai_bug_embedding IS '缺陷向量表（缺陷语义索引，1:1）';
COMMENT ON COLUMN ai_bug_embedding.id IS '主键';
COMMENT ON COLUMN ai_bug_embedding.bug_id IS '对应缺陷 ID';
COMMENT ON COLUMN ai_bug_embedding.project_id IS '冗余项目归属';
COMMENT ON COLUMN ai_bug_embedding.embedding IS '语义向量（默认 1024 维）';
COMMENT ON COLUMN ai_bug_embedding.source_hash IS '源文本 SHA-256';
COMMENT ON COLUMN ai_bug_embedding.model IS '生成向量的模型名';

COMMENT ON TABLE ai_case_embedding IS '用例向量表（type=case 节点语义索引，1:1）';
COMMENT ON COLUMN ai_case_embedding.id IS '主键';
COMMENT ON COLUMN ai_case_embedding.node_id IS '对应用例节点 ID';
COMMENT ON COLUMN ai_case_embedding.project_id IS '冗余项目归属';
COMMENT ON COLUMN ai_case_embedding.embedding IS '语义向量';
COMMENT ON COLUMN ai_case_embedding.source_hash IS '源文本 SHA-256';
COMMENT ON COLUMN ai_case_embedding.model IS '生成向量的模型名';

COMMENT ON TABLE ai_conversation IS '助手会话表（归属用户+工作空间，仅本人可见）';
COMMENT ON COLUMN ai_conversation.id IS '会话 ID';
COMMENT ON COLUMN ai_conversation.user_id IS '归属用户';
COMMENT ON COLUMN ai_conversation.workspace_id IS '归属工作空间';
COMMENT ON COLUMN ai_conversation.title IS '会话标题（首条用户消息前 30 字自动生成）';
COMMENT ON COLUMN ai_conversation.last_active_at IS '最后活跃时间';

COMMENT ON TABLE ai_message IS '助手消息表（对话内容，仅会话归属者可见）';
COMMENT ON COLUMN ai_message.id IS '消息 ID';
COMMENT ON COLUMN ai_message.conversation_id IS '所属会话';
COMMENT ON COLUMN ai_message.role IS '消息角色：user/assistant/tool';
COMMENT ON COLUMN ai_message.content IS '文本内容';
COMMENT ON COLUMN ai_message.tool_calls IS 'assistant 消息发起的工具调用载荷';
COMMENT ON COLUMN ai_message.tool_call_id IS 'tool 消息对应的调用 ID';

-- 需求池
COMMENT ON TABLE requirement_pool_item IS '需求池条目表（项目级轻量需求条目库）';
COMMENT ON COLUMN requirement_pool_item.id IS '条目 ID';
COMMENT ON COLUMN requirement_pool_item.project_id IS '归属项目 ID';
COMMENT ON COLUMN requirement_pool_item.title IS '条目标题';
COMMENT ON COLUMN requirement_pool_item.content IS '需求文本（Markdown）';
COMMENT ON COLUMN requirement_pool_item.source_url IS '来源 URL';
COMMENT ON COLUMN requirement_pool_item.ai_generated IS 'AI 拆分入库标识';
COMMENT ON COLUMN requirement_pool_item.created_by IS '创建人';
COMMENT ON COLUMN requirement_pool_item.updated_by IS '最后更新人';

COMMENT ON TABLE requirement_document_rel IS '文档-需求关联表（脑图文档 ⇄ 需求池条目）';
COMMENT ON COLUMN requirement_document_rel.document_id IS '脑图文档 ID';
COMMENT ON COLUMN requirement_document_rel.requirement_id IS '需求池条目 ID';

-- 项目模块
COMMENT ON TABLE project_module IS '项目模块表（纯目录树节点，跨功能测试/接口管理/测试场景共享）';
COMMENT ON COLUMN project_module.id IS '模块唯一标识';
COMMENT ON COLUMN project_module.project_id IS '所属项目 ID';
COMMENT ON COLUMN project_module.parent_id IS '父级模块 ID，NULL=根节点';
COMMENT ON COLUMN project_module.name IS '模块名称（同级唯一）';
COMMENT ON COLUMN project_module.sort_order IS '同级排序序号';

COMMENT ON TABLE test_case_document IS '用例文档表（布局内嵌 JSONB，替代旧 test_case_document_layout 表）';
COMMENT ON COLUMN test_case_document.id IS '文档唯一标识';
COMMENT ON COLUMN test_case_document.project_id IS '所属项目 ID';
COMMENT ON COLUMN test_case_document.module_id IS '所属模块 ID，关联 project_module.id';
COMMENT ON COLUMN test_case_document.name IS '文档名称';
COMMENT ON COLUMN test_case_document.layout IS '布局数据 JSONB（template/offsets）';
COMMENT ON COLUMN test_case_document.sort_order IS '同层级排序序号';

-- 项目设置
COMMENT ON TABLE project_setting IS '项目设置表（域+键 统一存储）';
COMMENT ON COLUMN project_setting.id IS '设置项唯一标识';
COMMENT ON COLUMN project_setting.project_id IS '所属项目 ID';
COMMENT ON COLUMN project_setting.domain IS '业务域：common/api_test/func_test';
COMMENT ON COLUMN project_setting.setting_key IS '设置项标识';
COMMENT ON COLUMN project_setting.setting_value IS '设置值（字符串化存储）';
COMMENT ON COLUMN project_setting.updated_by IS '最后维护人';

-- 接口测试 — 环境管理
COMMENT ON TABLE api_environment IS '接口测试环境表';
COMMENT ON COLUMN api_environment.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_environment.name IS '环境名称';
COMMENT ON COLUMN api_environment.description IS '环境描述';
COMMENT ON COLUMN api_environment.scope IS '归属范围：project/global';
COMMENT ON COLUMN api_environment.is_default IS '是否默认环境';
COMMENT ON COLUMN api_environment.sort_order IS '排序序号';

COMMENT ON TABLE api_environment_http IS '环境 HTTP 默认配置';
COMMENT ON COLUMN api_environment_http.environment_id IS '关联环境 ID';
COMMENT ON COLUMN api_environment_http.protocol IS '协议：http/https';
COMMENT ON COLUMN api_environment_http.base_url IS '基础 URL';
COMMENT ON COLUMN api_environment_http.connect_timeout IS '连接超时（毫秒）';
COMMENT ON COLUMN api_environment_http.read_timeout IS '读取超时（毫秒）';
COMMENT ON COLUMN api_environment_http.follow_redirects IS '跟随重定向';
COMMENT ON COLUMN api_environment_http.trust_all_certs IS '信任所有证书';
COMMENT ON COLUMN api_environment_http.default_headers IS '默认请求头';

COMMENT ON TABLE api_environment_variable IS '环境变量表';
COMMENT ON COLUMN api_environment_variable.environment_id IS '关联环境 ID';
COMMENT ON COLUMN api_environment_variable.name IS '变量名';
COMMENT ON COLUMN api_environment_variable.value IS '变量值';
COMMENT ON COLUMN api_environment_variable.description IS '变量描述';
COMMENT ON COLUMN api_environment_variable.sensitive IS '是否敏感值（脱敏展示）';
COMMENT ON COLUMN api_environment_variable.sort_order IS '排序序号';

COMMENT ON TABLE api_data_source IS '数据源配置表';
COMMENT ON COLUMN api_data_source.environment_id IS '关联环境 ID';
COMMENT ON COLUMN api_data_source.name IS '数据源名称';
COMMENT ON COLUMN api_data_source.type IS '数据源类型：mysql/postgresql';
COMMENT ON COLUMN api_data_source.connection_url IS 'JDBC 连接 URL';
COMMENT ON COLUMN api_data_source.username IS '用户名';
COMMENT ON COLUMN api_data_source.password_cipher IS '密码（加密）';
COMMENT ON COLUMN api_data_source.driver_class IS 'JDBC 驱动类名';
COMMENT ON COLUMN api_data_source.pool_size IS '连接池大小';

COMMENT ON TABLE api_environment_processor IS '环境级前置/后置处理器';
COMMENT ON COLUMN api_environment_processor.environment_id IS '关联环境 ID';
COMMENT ON COLUMN api_environment_processor.processor_type IS '处理器类型：http/jdbc';
COMMENT ON COLUMN api_environment_processor.name IS '处理器名称';
COMMENT ON COLUMN api_environment_processor.enabled IS '启用状态';
COMMENT ON COLUMN api_environment_processor.sort_order IS '排序序号';
COMMENT ON COLUMN api_environment_processor.config IS '处理器配置';

-- 接口测试 — 接口管理
COMMENT ON TABLE api_interface IS '接口定义表';
COMMENT ON COLUMN api_interface.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_interface.module_id IS '归属模块 ID';
COMMENT ON COLUMN api_interface.name IS '接口名称';
COMMENT ON COLUMN api_interface.method IS 'HTTP 方法';
COMMENT ON COLUMN api_interface.url IS '请求 URL';
COMMENT ON COLUMN api_interface.description IS '接口描述';
COMMENT ON COLUMN api_interface.status IS '状态：draft/published';
COMMENT ON COLUMN api_interface.headers IS '请求头';
COMMENT ON COLUMN api_interface.params IS '查询参数';
COMMENT ON COLUMN api_interface.body_type IS '请求体类型';
COMMENT ON COLUMN api_interface.body IS '请求体';
COMMENT ON COLUMN api_interface.auth_type IS '认证类型';
COMMENT ON COLUMN api_interface.auth_config IS '认证配置';
COMMENT ON COLUMN api_interface.timeout IS '超时时间（毫秒）';
COMMENT ON COLUMN api_interface.follow_redirects IS '跟随重定向';

COMMENT ON TABLE api_interface_step IS '接口公共步骤表';
COMMENT ON COLUMN api_interface_step.interface_id IS '关联接口 ID';
COMMENT ON COLUMN api_interface_step.name IS '步骤名称';
COMMENT ON COLUMN api_interface_step.sort_order IS '排序序号';
COMMENT ON COLUMN api_interface_step.enabled IS '启用状态';
COMMENT ON COLUMN api_interface_step.request_config IS '请求配置';

COMMENT ON TABLE api_interface_variable IS '接口级变量表';
COMMENT ON COLUMN api_interface_variable.interface_id IS '关联接口 ID';
COMMENT ON COLUMN api_interface_variable.name IS '变量名';
COMMENT ON COLUMN api_interface_variable.value IS '变量值';
COMMENT ON COLUMN api_interface_variable.description IS '变量描述';
COMMENT ON COLUMN api_interface_variable.sort_order IS '排序序号';

COMMENT ON TABLE api_import_mapping IS '导入映射表';
COMMENT ON COLUMN api_import_mapping.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_import_mapping.source_type IS '导入源类型';
COMMENT ON COLUMN api_import_mapping.source_id IS '导入源 ID';
COMMENT ON COLUMN api_import_mapping.target_intf_id IS '目标接口 ID';
COMMENT ON COLUMN api_import_mapping.status IS '映射状态';

COMMENT ON TABLE api_interface_follow IS '接口关注表';
COMMENT ON COLUMN api_interface_follow.interface_id IS '关联接口 ID';
COMMENT ON COLUMN api_interface_follow.user_id IS '关注用户 ID';

COMMENT ON TABLE api_interface_change_log IS '接口变更历史表';
COMMENT ON COLUMN api_interface_change_log.interface_id IS '关联接口 ID';
COMMENT ON COLUMN api_interface_change_log.version IS '变更版本号';
COMMENT ON COLUMN api_interface_change_log.change_type IS '变更类型';
COMMENT ON COLUMN api_interface_change_log.content_diff IS '变更内容';
COMMENT ON COLUMN api_interface_change_log.created_by IS '变更人';

COMMENT ON TABLE api_import_record IS '导入记录表';
COMMENT ON COLUMN api_import_record.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_import_record.repository_id IS '关联仓库 ID';
COMMENT ON COLUMN api_import_record.source_type IS '导入源类型';
COMMENT ON COLUMN api_import_record.source_name IS '导入源名称';
COMMENT ON COLUMN api_import_record.status IS '导入状态';
COMMENT ON COLUMN api_import_record.imported_count IS '成功导入数';
COMMENT ON COLUMN api_import_record.skipped_count IS '跳过数';
COMMENT ON COLUMN api_import_record.error_message IS '失败原因';

COMMENT ON TABLE api_debug_record IS '调试历史记录表';
COMMENT ON COLUMN api_debug_record.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_debug_record.user_id IS '操作用户 ID';
COMMENT ON COLUMN api_debug_record.name IS '记录名称';
COMMENT ON COLUMN api_debug_record.method IS 'HTTP 方法';
COMMENT ON COLUMN api_debug_record.url IS '请求 URL';
COMMENT ON COLUMN api_debug_record.last_status IS '最近响应状态码';
COMMENT ON COLUMN api_debug_record.last_duration IS '最近响应耗时（毫秒）';

-- 接口测试 — Swagger URL
COMMENT ON TABLE api_swagger_url IS 'Swagger URL 配置表';
COMMENT ON COLUMN api_swagger_url.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_swagger_url.name IS '配置名称';
COMMENT ON COLUMN api_swagger_url.url IS 'Swagger/OpenAPI 文档 URL';
COMMENT ON COLUMN api_swagger_url.format IS '格式：swagger/openapi';
COMMENT ON COLUMN api_swagger_url.last_import_status IS '最近导入状态';
COMMENT ON COLUMN api_swagger_url.last_import_at IS '最近导入时间';

-- 接口测试 — 定时任务
COMMENT ON TABLE api_scheduled_task IS '定时任务表';
COMMENT ON COLUMN api_scheduled_task.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_scheduled_task.task_type IS '任务类型：import_swagger/scene_execute';
COMMENT ON COLUMN api_scheduled_task.name IS '任务名称';
COMMENT ON COLUMN api_scheduled_task.description IS '任务描述';
COMMENT ON COLUMN api_scheduled_task.bound_object_id IS '绑定对象 ID';
COMMENT ON COLUMN api_scheduled_task.bound_object_name IS '绑定对象名称快照';
COMMENT ON COLUMN api_scheduled_task.environment_id IS '目标环境 ID';
COMMENT ON COLUMN api_scheduled_task.cron_expression IS 'Cron 表达式';
COMMENT ON COLUMN api_scheduled_task.enabled IS '启用状态';
COMMENT ON COLUMN api_scheduled_task.last_execution_status IS '上次执行状态';
COMMENT ON COLUMN api_scheduled_task.last_execution_at IS '上次执行时间';
COMMENT ON COLUMN api_scheduled_task.created_by IS '创建人';

COMMENT ON TABLE api_scheduled_task_execution IS '定时任务执行记录表';
COMMENT ON COLUMN api_scheduled_task_execution.task_id IS '关联任务 ID';
COMMENT ON COLUMN api_scheduled_task_execution.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_scheduled_task_execution.trigger_type IS '触发方式：scheduled/manual';
COMMENT ON COLUMN api_scheduled_task_execution.status IS '执行结果：success/failed/skipped';
COMMENT ON COLUMN api_scheduled_task_execution.error_message IS '失败原因';
COMMENT ON COLUMN api_scheduled_task_execution.report_id IS '关联报告 ID';
COMMENT ON COLUMN api_scheduled_task_execution.import_record_id IS '关联导入记录 ID';
COMMENT ON COLUMN api_scheduled_task_execution.triggered_at IS '触发时间';
COMMENT ON COLUMN api_scheduled_task_execution.duration_ms IS '执行耗时（毫秒）';

-- 接口测试 — Mock 服务
COMMENT ON TABLE api_mock_definition IS 'Mock 定义表';
COMMENT ON COLUMN api_mock_definition.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_mock_definition.interface_id IS '关联接口定义 ID';
COMMENT ON COLUMN api_mock_definition.name IS 'Mock 名称';
COMMENT ON COLUMN api_mock_definition.description IS 'Mock 描述';
COMMENT ON COLUMN api_mock_definition.method IS '匹配的 HTTP 方法';
COMMENT ON COLUMN api_mock_definition.path IS '匹配的请求路径（支持 * 通配符）';
COMMENT ON COLUMN api_mock_definition.priority IS '匹配优先级（数值越小越高）';
COMMENT ON COLUMN api_mock_definition.match_rules IS '匹配条件列表';
COMMENT ON COLUMN api_mock_definition.enabled IS '启用状态';
COMMENT ON COLUMN api_mock_definition.follow_api IS '跟随 API 响应';
COMMENT ON COLUMN api_mock_definition.response_status IS '响应状态码';
COMMENT ON COLUMN api_mock_definition.response_headers IS '响应头';
COMMENT ON COLUMN api_mock_definition.response_body_type IS '响应体类型：json/text/xml/binary';
COMMENT ON COLUMN api_mock_definition.response_body IS '响应体内容';
COMMENT ON COLUMN api_mock_definition.delay_ms IS '响应延迟（毫秒）';
COMMENT ON COLUMN api_mock_definition.hit_count IS '命中次数统计';
COMMENT ON COLUMN api_mock_definition.last_hit_at IS '最后命中时间';

COMMENT ON TABLE api_mock_access_log IS 'Mock 访问日志表';
COMMENT ON COLUMN api_mock_access_log.mock_id IS '命中的 Mock 定义 ID';
COMMENT ON COLUMN api_mock_access_log.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_mock_access_log.method IS '请求方法';
COMMENT ON COLUMN api_mock_access_log.path IS '请求路径';
COMMENT ON COLUMN api_mock_access_log.request_headers IS '请求头快照';
COMMENT ON COLUMN api_mock_access_log.request_body IS '请求体快照';
COMMENT ON COLUMN api_mock_access_log.response_status IS '返回的状态码';
COMMENT ON COLUMN api_mock_access_log.response_body IS '返回的响应体';
COMMENT ON COLUMN api_mock_access_log.duration_ms IS '响应耗时（毫秒）';
COMMENT ON COLUMN api_mock_access_log.client_ip IS '客户端 IP';

-- 接口测试 — 测试场景
COMMENT ON TABLE api_scene IS '测试场景表';
COMMENT ON COLUMN api_scene.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_scene.module_id IS '归属模块 ID';
COMMENT ON COLUMN api_scene.name IS '场景名称';
COMMENT ON COLUMN api_scene.description IS '场景描述';
COMMENT ON COLUMN api_scene.environment_id IS '默认执行环境 ID';
COMMENT ON COLUMN api_scene.variables IS '场景变量列表';
COMMENT ON COLUMN api_scene.processors IS '场景级处理器列表';
COMMENT ON COLUMN api_scene.failure_rule IS '失败规则：all/continue';
COMMENT ON COLUMN api_scene.cookie_config IS 'Cookie 配置';
COMMENT ON COLUMN api_scene.change_version IS '变更版本号（乐观锁）';

COMMENT ON TABLE api_scene_step IS '场景步骤表';
COMMENT ON COLUMN api_scene_step.scene_id IS '归属场景 ID';
COMMENT ON COLUMN api_scene_step.name IS '步骤名称';
COMMENT ON COLUMN api_scene_step.step_type IS '步骤类型：http/jdbc';
COMMENT ON COLUMN api_scene_step.sort_order IS '排序序号';
COMMENT ON COLUMN api_scene_step.enabled IS '启用状态';
COMMENT ON COLUMN api_scene_step.source_type IS '来源类型：system/custom/public_step/copy/link';
COMMENT ON COLUMN api_scene_step.source_id IS '来源对象 ID';
COMMENT ON COLUMN api_scene_step.source_snapshot IS '来源快照';
COMMENT ON COLUMN api_scene_step.source_interface_id IS '来源接口 ID';
COMMENT ON COLUMN api_scene_step.source_interface_name IS '来源接口名称';
COMMENT ON COLUMN api_scene_step.request_config IS '请求配置';
COMMENT ON COLUMN api_scene_step.processors IS '处理器列表';
COMMENT ON COLUMN api_scene_step.validators IS '验证器列表';
COMMENT ON COLUMN api_scene_step.extractors IS '提取器列表';

COMMENT ON TABLE api_scene_step_variable IS '步骤级变量表';
COMMENT ON COLUMN api_scene_step_variable.step_id IS '归属步骤 ID';
COMMENT ON COLUMN api_scene_step_variable.name IS '变量名';
COMMENT ON COLUMN api_scene_step_variable.value IS '变量值';
COMMENT ON COLUMN api_scene_step_variable.source IS '来源：custom/interface';
COMMENT ON COLUMN api_scene_step_variable.interface_variable_id IS '来源接口变量 ID';
COMMENT ON COLUMN api_scene_step_variable.description IS '变量描述';
COMMENT ON COLUMN api_scene_step_variable.sort_order IS '排序序号';

COMMENT ON TABLE api_scenario_variable IS '场景变量表';
COMMENT ON COLUMN api_scenario_variable.scene_id IS '归属场景 ID';
COMMENT ON COLUMN api_scenario_variable.name IS '变量名';
COMMENT ON COLUMN api_scenario_variable.value IS '变量值';
COMMENT ON COLUMN api_scenario_variable.description IS '变量描述';
COMMENT ON COLUMN api_scenario_variable.sort_order IS '排序序号';

COMMENT ON TABLE api_scene_interface IS '场景关联接口表';
COMMENT ON COLUMN api_scene_interface.scene_id IS '场景 ID';
COMMENT ON COLUMN api_scene_interface.interface_id IS '接口定义 ID';
COMMENT ON COLUMN api_scene_interface.sync_mode IS '同步模式：copy/link';
COMMENT ON COLUMN api_scene_interface.last_synced_at IS '最近同步时间';

COMMENT ON TABLE api_execution_record IS '执行记录表';
COMMENT ON COLUMN api_execution_record.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_execution_record.scene_id IS '关联场景 ID';
COMMENT ON COLUMN api_execution_record.environment_id IS '使用的环境 ID';
COMMENT ON COLUMN api_execution_record.execution_mode IS '执行方式：platform/pipeline';
COMMENT ON COLUMN api_execution_record.status IS '状态：pending/running/success/failed/error/cancelled/timeout';
COMMENT ON COLUMN api_execution_record.trigger_type IS '触发方式：manual/scheduled/pipeline';
COMMENT ON COLUMN api_execution_record.report_id IS '关联报告 ID';
COMMENT ON COLUMN api_execution_record.pipeline_id IS '流水线 ID';
COMMENT ON COLUMN api_execution_record.pipeline_url IS '流水线 URL';
COMMENT ON COLUMN api_execution_record.error_message IS '失败原因';
COMMENT ON COLUMN api_execution_record.executed_at IS '触发时间';
COMMENT ON COLUMN api_execution_record.duration_ms IS '执行耗时（毫秒）';
COMMENT ON COLUMN api_execution_record.repository_id IS '关联 GitLab 仓库配置 ID（流水线执行时记录）';

COMMENT ON TABLE api_report IS '报告表';
COMMENT ON COLUMN api_report.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_report.execution_record_id IS '关联执行记录 ID';
COMMENT ON COLUMN api_report.scene_id IS '关联场景 ID';
COMMENT ON COLUMN api_report.scene_name IS '场景名称快照';
COMMENT ON COLUMN api_report.environment_name IS '环境名称快照';
COMMENT ON COLUMN api_report.execution_mode IS '执行方式：platform/pipeline';
COMMENT ON COLUMN api_report.status IS '汇总状态：success/failed/partial';
COMMENT ON COLUMN api_report.summary IS '结果汇总';
COMMENT ON COLUMN api_report.step_results IS '步骤级结果明细';
COMMENT ON COLUMN api_report.ryze_snapshot IS 'Ryze 标准 JSON 快照';
COMMENT ON COLUMN api_report.share_enabled IS '分享是否开启';
COMMENT ON COLUMN api_report.share_token IS '分享链接令牌（唯一）';
COMMENT ON COLUMN api_report.share_expires_at IS '分享链接过期时间';

COMMENT ON TABLE api_change_history IS '变更历史表';
COMMENT ON COLUMN api_change_history.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_change_history.target_type IS '变更对象类型：interface/scene';
COMMENT ON COLUMN api_change_history.target_id IS '变更对象 ID';
COMMENT ON COLUMN api_change_history.version IS '变更序号';
COMMENT ON COLUMN api_change_history.change_type IS '变更类型：create/update/import/copy';
COMMENT ON COLUMN api_change_history.content_diff IS '变更内容快照';
COMMENT ON COLUMN api_change_history.created_by IS '变更人';

-- 接口测试 — 公共组件
COMMENT ON TABLE api_component IS '接口测试公共组件表（三级作用域）';
COMMENT ON COLUMN api_component.scope IS '作用域：project/workspace/global';
COMMENT ON COLUMN api_component.workspace_id IS '归属工作空间 ID';
COMMENT ON COLUMN api_component.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_component.type IS '组件类型：preprocessor/postprocessor/validator/extractor';
COMMENT ON COLUMN api_component.name IS '组件名称';
COMMENT ON COLUMN api_component.description IS '组件描述';
COMMENT ON COLUMN api_component.config IS '组件配置内容';
COMMENT ON COLUMN api_component.enabled IS '启用状态';
COMMENT ON COLUMN api_component.updated_by IS '最后维护人';

-- 接口测试 — 函数表
COMMENT ON TABLE api_function IS '接口测试函数表（内置 + 自定义）';
COMMENT ON COLUMN api_function.scope IS '作用域：project/workspace/global';
COMMENT ON COLUMN api_function.workspace_id IS '归属工作空间 ID';
COMMENT ON COLUMN api_function.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_function.name IS '函数名';
COMMENT ON COLUMN api_function.description IS '函数描述';
COMMENT ON COLUMN api_function.params_desc IS '参数说明';
COMMENT ON COLUMN api_function.script IS 'Groovy 脚本体';
COMMENT ON COLUMN api_function.type IS '函数类型：builtin/custom';
COMMENT ON COLUMN api_function.enabled IS '启用状态';
COMMENT ON COLUMN api_function.updated_by IS '最后维护人';

-- 接口测试 — GitLab 仓库
COMMENT ON TABLE api_gitlab_repository IS 'GitLab 仓库配置表';
COMMENT ON COLUMN api_gitlab_repository.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_gitlab_repository.name IS '配置名称';
COMMENT ON COLUMN api_gitlab_repository.repo_url IS 'GitLab 仓库地址';
COMMENT ON COLUMN api_gitlab_repository.branch IS '默认分支或标签';
COMMENT ON COLUMN api_gitlab_repository.access_token_cipher IS '访问令牌（AES-256-GCM 密文）';
COMMENT ON COLUMN api_gitlab_repository.token_suffix IS '令牌末 4 位';
COMMENT ON COLUMN api_gitlab_repository.test_source_path IS '测试源码相对路径';
COMMENT ON COLUMN api_gitlab_repository.last_import_status IS '最近导入状态';
COMMENT ON COLUMN api_gitlab_repository.last_import_at IS '最近导入时间';
COMMENT ON COLUMN api_gitlab_repository.last_metadata_sync_at IS '最近元数据同步时间';
COMMENT ON COLUMN api_gitlab_repository.last_commit_sha IS '最近同步的 commit SHA';
COMMENT ON COLUMN api_gitlab_repository.annotation_filter IS '注解过滤';
COMMENT ON COLUMN api_gitlab_repository.only_with_resource_path IS '仅保留含 resourcePath 的测试类';
COMMENT ON COLUMN api_gitlab_repository.auto_sync_enabled IS '是否开启自动同步';

COMMENT ON TABLE api_gitlab_test_class_metadata IS 'GitLab 测试类元数据表';
COMMENT ON COLUMN api_gitlab_test_class_metadata.repository_id IS '关联仓库配置';
COMMENT ON COLUMN api_gitlab_test_class_metadata.full_class_name IS '测试类全限定名';
COMMENT ON COLUMN api_gitlab_test_class_metadata.class_annotations IS '类级注解配置';
COMMENT ON COLUMN api_gitlab_test_class_metadata.display_name IS '场景显示名';
COMMENT ON COLUMN api_gitlab_test_class_metadata.description IS '描述';
COMMENT ON COLUMN api_gitlab_test_class_metadata.resource_path IS 'resource path';
COMMENT ON COLUMN api_gitlab_test_class_metadata.is_executable IS '是否可执行';
COMMENT ON COLUMN api_gitlab_test_class_metadata.methods IS '测试方法清单';

COMMENT ON TABLE api_gitlab_test_scope IS 'GitLab 测试范围参数表';
COMMENT ON COLUMN api_gitlab_test_scope.repository_id IS '关联仓库配置';
COMMENT ON COLUMN api_gitlab_test_scope.variable_name IS 'CI 变量名';
COMMENT ON COLUMN api_gitlab_test_scope.scope_type IS '范围类型：class/method/tag/custom';
COMMENT ON COLUMN api_gitlab_test_scope.description IS '描述';

COMMENT ON TABLE api_gitlab_sync_history IS 'GitLab 元数据同步历史记录';
COMMENT ON COLUMN api_gitlab_sync_history.repository_id IS '关联仓库配置';
COMMENT ON COLUMN api_gitlab_sync_history.sync_at IS '同步时间';
COMMENT ON COLUMN api_gitlab_sync_history.class_count IS '本次同步的测试类数量';
COMMENT ON COLUMN api_gitlab_sync_history.method_count IS '本次同步的测试方法数量';
COMMENT ON COLUMN api_gitlab_sync_history.commit_sha IS '同步时的 commit SHA';
COMMENT ON COLUMN api_gitlab_sync_history.status IS '同步状态';
