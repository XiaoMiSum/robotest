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
-- 10. 接口测试 — 环境管理
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
                                      name               VARCHAR(100)   NOT NULL,
                                      ref_name           VARCHAR(100)   NULL,
                                      base_url           VARCHAR(2000)  NOT NULL,
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
                                          sort_order      INT           NOT NULL DEFAULT 0,
                                          is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
                                          created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evar_env ON api_environment_variable(environment_id, sort_order);

CREATE TABLE api_environment_data_source (
                                 id                   UUID          PRIMARY KEY,
                                 environment_id       UUID          NOT NULL,
                                 name                 VARCHAR(100)  NOT NULL,
                                 ref_name             VARCHAR(100)  NULL,
                                 driver               VARCHAR(200)  NULL,
                                 url                  VARCHAR(2000) NOT NULL,
                                 connection_properties JSONB         NULL,
                                 max_pool_size        INT           NOT NULL DEFAULT 5,
                                 is_deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
                                 created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ds_env ON api_environment_data_source(environment_id);

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
-- 11. 接口测试 — 接口管理
-- ============================================================

CREATE TABLE api_interface (
                               id              UUID         PRIMARY KEY,
                               project_id      UUID         NOT NULL,
                               module_id       UUID         NULL,
                               name            VARCHAR(200) NOT NULL,
                               protocol        VARCHAR(20)  NOT NULL DEFAULT 'http',
                               method          VARCHAR(10)  NOT NULL,
                               path            VARCHAR(2000) NOT NULL,
                               description     TEXT         NULL,
                               headers         JSONB        NOT NULL DEFAULT '[]',
                               body_type       VARCHAR(20)  NULL,
                               body            JSONB        NULL,
                               query_params    JSONB        NOT NULL DEFAULT '[]',
                               rest_params     JSONB        NOT NULL DEFAULT '[]',
                               auth            JSONB        NULL,
                               status          VARCHAR(20)  NOT NULL DEFAULT 'draft',
                               created_by      UUID         NOT NULL,
                               change_version  INT          NOT NULL DEFAULT 1,
                               response_example JSONB       NULL,
                               reference_count INT          NOT NULL DEFAULT 0,
                               is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                               created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                                    id              UUID         PRIMARY KEY,
                                    project_id      UUID         NOT NULL,
                                    import_record_id UUID        NULL,
                                    source_type     VARCHAR(20)  NOT NULL,
                                    source_id       VARCHAR(200) NOT NULL,
                                    source_name     VARCHAR(200) NULL,
                                    target_type     VARCHAR(20)  NULL,
                                    target_id       UUID         NULL,
                                    action          VARCHAR(20)  NULL,
                                    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                                    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                                   import_type     VARCHAR(20)  NOT NULL,
                                   source_name     VARCHAR(200) NOT NULL,
                                   status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
                                   summary         JSONB        NULL,
                                   error_details   JSONB        NULL,
                                   repository_id   UUID         NULL,
                                   created_by      UUID         NOT NULL,
                                   is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                                   created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_irecord_project ON api_import_record(project_id, created_at DESC);
CREATE INDEX idx_irecord_repository ON api_import_record(repository_id) WHERE repository_id IS NOT NULL AND is_deleted = FALSE;

CREATE TABLE api_debug_record (
                                   id              UUID         PRIMARY KEY,
                                   project_id      UUID         NOT NULL,
                                   user_id         UUID         NOT NULL,
                                   name            VARCHAR(200) NULL,
                                   protocol        VARCHAR(20)  NOT NULL DEFAULT 'http',
                                   method          VARCHAR(10)  NOT NULL,
                                   url             VARCHAR(2000) NOT NULL,
                                   headers         JSONB        NOT NULL DEFAULT '[]',
                                   body_type       VARCHAR(20)  NULL,
                                   body            JSONB        NULL,
                                   query_params    JSONB        NOT NULL DEFAULT '[]',
                                   jdbc_config     JSONB        NULL,
                                   processors      JSONB        NOT NULL DEFAULT '[]',
                                   environment_id  UUID         NULL,
                                   timeout_ms      INT          NULL,
                                   executed_at     TIMESTAMP    NULL,
                                   duration_ms     INT          NULL,
                                   status          VARCHAR(20)  NULL,
                                   response_status INT          NULL,
                                   response_headers JSONB       NULL,
                                   response_body   TEXT         NULL,
                                   response_size   INT          NULL,
                                   error_message   TEXT         NULL,
                                   is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                                   created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_drec_project_user ON api_debug_record(project_id, user_id);

-- ============================================================
-- 12. 接口测试 — Swagger URL 配置
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
-- 13. 接口测试 — 定时任务
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
-- 14. 接口测试 — Mock 服务
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
-- 15. 接口测试 — 测试场景与执行
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

CREATE TABLE api_scene_follow (
    id            UUID      PRIMARY KEY,
    scene_id      UUID      NOT NULL,
    user_id       UUID      NOT NULL,
    is_deleted    BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_scene_follow ON api_scene_follow(scene_id, user_id) WHERE is_deleted = false;
CREATE INDEX idx_sfollow_scene ON api_scene_follow(scene_id);
CREATE INDEX idx_sfollow_user ON api_scene_follow(user_id);

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
-- 16. 接口测试 — 公共组件
-- ============================================================

CREATE TABLE api_component (
                                  id           UUID         PRIMARY KEY,
                                  scope        VARCHAR(10)  NOT NULL DEFAULT 'project',
                                  workspace_id UUID         NULL,
                                  project_id   UUID         NULL,
                                  type         VARCHAR(30)  NOT NULL,
                                  name         VARCHAR(100) NOT NULL,
                                  description  VARCHAR(500) NULL,
                                  sort_order   INT          NOT NULL DEFAULT 0,
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
-- 17. 接口测试 — 函数表（内置 + 自定义）
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
-- 18. 接口测试 — GitLab 仓库配置
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
-- 21. 种子数据（权限点、角色、提示词模板）
-- ============================================================

-- ------------------------------------------------------------
-- 21.1 权限点（系统管理模块）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000001', 'user',                '用户管理',          NULL,  '用户管理',     'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000002', 'user:view',           '查看用户',          'user', '用户管理',     'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000003', 'user:create',         '创建用户',          'user', '用户管理',     'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000004', 'user:edit',           '编辑用户',          'user', '用户管理',     'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000005', 'user:disable',        '禁用/启用用户',      'user', '用户管理',     'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000006', 'user:reset-password', '重置密码',          'user', '用户管理',     'global', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000007', 'workspace',            '工作空间管理',       NULL,  '工作空间管理',  'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000008', 'workspace:view',       '查看工作空间',       'workspace', '工作空间管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000009', 'workspace:create',     '创建工作空间',       'workspace', '工作空间管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000010', 'workspace:edit',       '编辑工作空间',       'workspace', '工作空间管理', 'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000011', 'workspace:delete',     '解散工作空间',       'workspace', '工作空间管理', 'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000012', 'workspace:manage-members', '管理成员',     'workspace', '工作空间管理', 'global', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000013', 'role',                '角色管理',          NULL,  '角色管理',     'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000014', 'role:view',           '查看角色',          'role', '角色管理',     'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000015', 'role:create',         '创建角色',          'role', '角色管理',     'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000016', 'role:edit',           '编辑角色',          'role', '角色管理',     'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000017', 'role:delete',         '删除角色',          'role', '角色管理',     'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 21.2 权限点（AI 管理模块）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000018', 'ai',      'AI 管理',           NULL, 'AI 管理', 'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000019', 'ai:view', '查看 AI 配置与智能体', 'ai', 'AI 管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000020', 'ai:edit', '编辑 AI 配置与智能体', 'ai', 'AI 管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 21.3 权限点（业务模块 — 工作空间/项目/测试用例/评审/计划/缺陷）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000001', 'ws-info',            '空间信息',           NULL,           '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000002', 'ws-info:view',       '查看空间信息',        'ws-info',      '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000003', 'ws-info:edit',       '编辑空间信息',        'ws-info',      '我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000004', 'ws-member',          '成员管理',            NULL,           '我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000005', 'ws-member:view',     '查看成员',            'ws-member',    '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000006', 'ws-member:manage',   '管理成员',            'ws-member',    '我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000007', 'ws-invitation',      '邀请链接',            NULL,           '我的空间', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000008', 'ws-invitation:view', '查看邀请链接',         'ws-invitation','我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000009', 'ws-invitation:manage','管理邀请链接',        'ws-invitation','我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000010', 'project',            '项目',                NULL,           '项目',    'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000011', 'project:view',       '查看项目',            'project',      '项目',    'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000017', 'case',               '测试用例',            NULL,           '测试用例', 'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000018', 'case:view',          '查看用例',            'case',         '测试用例', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000019', 'case:edit',          '编辑用例',            'case',         '测试用例', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000020', 'review',             '测试评审',            NULL,           '测试评审', 'workspace', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000021', 'review:view',        '查看评审',            'review',       '测试评审', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000022', 'review:create',      '发起评审',            'review',       '测试评审', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000023', 'review:edit',        '评审操作',            'review',       '测试评审', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000024', 'review:complete',    '完成评审',            'review',       '测试评审', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000025', 'plan',               '测试计划',            NULL,           '测试计划', 'workspace', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000026', 'plan:view',          '查看计划',            'plan',         '测试计划', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000027', 'plan:create',        '创建计划',            'plan',         '测试计划', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000028', 'plan:execute',       '执行计划',            'plan',         '测试计划', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000029', 'plan:close',         '关闭计划',            'plan',         '测试计划', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000030', 'bug',                '缺陷',                NULL,           '缺陷',    'workspace', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000031', 'bug:view',           '查看缺陷',            'bug',          '缺陷',    'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 21.4 权限点（需求池）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000034', 'requirement',      '需求池',   NULL,          '需求池', 'workspace', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000035', 'requirement:view', '查看需求池', 'requirement', '需求池', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000036', 'requirement:edit', '编辑需求池', 'requirement', '需求池', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 21.5 权限点（接口测试模块）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
-- GitLab 仓库配置
('c0000000-0000-0000-0000-000000000037', 'api-gitlab',         'GitLab 仓库',    NULL,            '接口测试·GitLab',   'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000038', 'api-gitlab:view',    '查看 GitLab 仓库', 'api-gitlab',    '接口测试·GitLab',   'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000039', 'api-gitlab:edit',    '编辑 GitLab 仓库', 'api-gitlab',    '接口测试·GitLab',   'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 测试场景
('c0000000-0000-0000-0000-000000000040', 'api-scene',          '测试场景',        NULL,            '接口测试·测试场景',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000041', 'api-scene:view',     '查看场景',        'api-scene',     '接口测试·测试场景',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000042', 'api-scene:edit',     '编辑场景',        'api-scene',     '接口测试·测试场景',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000043', 'api-scene:import',   '导入场景',        'api-scene',     '接口测试·测试场景',  'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000044', 'api-scene:pipeline', '流水线执行',       'api-scene',     '接口测试·测试场景',  'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000045', 'api-scene:execute',  '执行场景',        'api-scene',     '接口测试·测试场景',  'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 接口管理
('c0000000-0000-0000-0000-000000000046', 'api-interface',         '接口管理',    NULL,            '接口测试·接口管理',  'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000047', 'api-interface:view',    '查看接口',    'api-interface',  '接口测试·接口管理',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000048', 'api-interface:edit',    '编辑接口',    'api-interface',  '接口测试·接口管理',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000049', 'api-interface:delete', '删除接口',    'api-interface',  '接口测试·接口管理',  'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 公共组件
('c0000000-0000-0000-0000-000000000050', 'api-component',         '公共组件',    NULL,            '接口测试·公共组件',  'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000051', 'api-component:view',    '查看组件',    'api-component',  '接口测试·公共组件',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000052', 'api-component:edit',    '编辑组件',    'api-component',  '接口测试·公共组件',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000070', 'api-component:edit-space', '编辑空间级组件', 'api-component', '接口测试·公共组件', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000071', 'api-component:edit-global', '编辑全局组件', 'api-component', '接口测试·公共组件', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 快速调试
('c0000000-0000-0000-0000-000000000053', 'api-debug',         '快速调试',    NULL,            '接口测试·快速调试',  'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000054', 'api-debug:view',    '查看调试记录', 'api-debug',     '接口测试·快速调试',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 定时任务
('c0000000-0000-0000-0000-000000000055', 'api-timer',         '定时任务',    NULL,            '接口测试·定时任务',  'workspace', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000056', 'api-timer:view',    '查看定时任务', 'api-timer',     '接口测试·定时任务',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000057', 'api-timer:edit',    '编辑定时任务', 'api-timer',     '接口测试·定时任务',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- Mock 服务
('c0000000-0000-0000-0000-000000000058', 'api-mock',          'Mock 服务',    NULL,            '接口测试·Mock服务',  'workspace', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000059', 'api-mock:view',     '查看 Mock',    'api-mock',      '接口测试·Mock服务',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000060', 'api-mock:edit',     '编辑 Mock',    'api-mock',      '接口测试·Mock服务',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 测试报告
('c0000000-0000-0000-0000-000000000061', 'api-report',        '测试报告',    NULL,            '接口测试·测试报告',  'workspace', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000062', 'api-report:view',   '查看报告',    'api-report',    '接口测试·测试报告',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000063', 'api-report:delete', '删除报告',    'api-report',    '接口测试·测试报告',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 21.5.1 权限点（接口测试·环境管理 / 函数管理，项目设置分组）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000064', 'api-env',            '环境管理',       NULL,          '接口测试·环境管理', 'workspace', 9,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000065', 'api-env:view',       '查看环境',       'api-env',     '接口测试·环境管理', 'workspace', 1,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000066', 'api-env:edit',       '编辑环境',       'api-env',     '接口测试·环境管理', 'workspace', 2,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000067', 'api-func',           '函数管理',       NULL,          '接口测试·函数管理', 'workspace', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000068', 'api-func:view',      '查看函数',       'api-func',    '接口测试·函数管理', 'workspace', 1,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000069', 'api-func:edit',      '编辑函数',       'api-func',    '接口测试·函数管理', 'workspace', 2,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000072', 'api-func:edit-space', '编辑空间级函数', 'api-func',    '接口测试·函数管理', 'workspace', 3,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000073', 'api-func:edit-global', '编辑全局函数', 'api-func',    '接口测试·函数管理', 'workspace', 4,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 21.6 预置角色（含全部版本权限合并）
-- ------------------------------------------------------------
INSERT INTO sys_role (id, name, description, type, is_system, permissions, created_at, updated_at, is_deleted) VALUES
-- 系统管理员：拥有系统管理 + AI 管理所有权限
('b0000000-0000-0000-0000-000000000001', '系统管理员',
 '拥有系统管理所有权限', 'system', TRUE,
 '["user","user:view","user:create","user:edit","user:disable","user:reset-password","workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","role","role:view","role:create","role:edit","role:delete","ai","ai:view","ai:edit"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 空间管理系统角色：拥有工作空间管理所有权限（跨空间管理）
('b0000000-0000-0000-0000-000000000002', '空间管理员',
 '拥有工作空间管理所有权限，可创建/删除/管理所有工作空间', 'system', TRUE,
 '["workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","ws-info","ws-info:view","ws-info:edit","ws-member","ws-member:view","ws-member:manage","ws-invitation","ws-invitation:view","ws-invitation:manage"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- workspace 管理员：空间内全部业务权限（显式授权全部空间权限码）
('c0000000-0000-0000-0000-000000000001', '管理员',
 '空间管理员 — 拥有工作空间内全部业务权限', 'workspace', TRUE,
 '["ws-info","ws-info:view","ws-info:edit","ws-member","ws-member:view","ws-member:manage","ws-invitation","ws-invitation:view","ws-invitation:manage","project","project:view","case","case:view","case:edit","review","review:view","review:create","review:edit","review:complete","plan","plan:view","plan:create","plan:execute","plan:close","bug","bug:view","requirement","requirement:view","requirement:edit","api-gitlab","api-gitlab:view","api-gitlab:edit","api-scene","api-scene:view","api-scene:edit","api-scene:import","api-scene:pipeline","api-scene:execute","api-interface","api-interface:view","api-interface:edit","api-interface:delete","api-component","api-component:view","api-component:edit","api-component:edit-space","api-component:edit-global","api-env","api-env:view","api-env:edit","api-func","api-func:view","api-func:edit","api-func:edit-space","api-func:edit-global","api-debug","api-debug:view","api-timer","api-timer:view","api-timer:edit","api-mock","api-mock:view","api-mock:edit","api-report","api-report:view","api-report:delete"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- workspace 普通成员：默认角色
('c0000000-0000-0000-0000-000000000002', '成员',
 '空间成员 — 除删除/归档项目、管理成员、编辑空间信息外的其他权限', 'workspace', TRUE,
 '["ws-info:view","ws-member:view","ws-invitation:view","ws-invitation:manage","project:view","case:view","case:edit","review:view","review:create","review:edit","review:complete","plan:view","plan:create","plan:execute","plan:close","bug:view","requirement:view","requirement:edit","api-gitlab","api-gitlab:view","api-gitlab:edit","api-scene","api-scene:view","api-scene:edit","api-scene:import","api-scene:execute","api-interface","api-interface:view","api-interface:edit","api-component","api-component:view","api-component:edit","api-env","api-env:view","api-env:edit","api-func","api-func:view","api-func:edit","api-debug","api-debug:view","api-timer","api-timer:view","api-timer:edit","api-mock","api-mock:view","api-mock:edit","api-report","api-report:view"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 21.7 智能体提示词模板种子数据
-- ------------------------------------------------------------
INSERT INTO ai_prompt_template (id, function_type, role_instruction, format_constraint, format_editable, updated_by, is_deleted, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000001', 'case_generation', '你是一名资深软件测试工程师，擅长根据需求描述设计结构化的功能测试用例。请基于给定的需求内容，生成覆盖正常流程、异常分支与边界条件的测试用例子树。用例标题应简洁明确，前置条件、步骤与预期结果应具体可执行。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "nodes": [
    {
      "type": "case",
      "title": "用例标题（一句话描述业务场景）",
      "priority": "P1",
      "children": [
        {"type": "precondition", "title": "前置条件描述"},
        {"type": "step", "title": "操作步骤描述"},
        {"type": "expected", "title": "预期结果描述"}
      ]
    }
  ]
}

字段约束：
- 顶层必须是 nodes 数组，每个元素为一个用例节点
- type 仅允许 case/precondition/step/expected
- case 节点必须带 priority，仅允许 P0/P1/P2/P3
- case 的直接子节点只能是 precondition/step/expected
- title 必填，不超过 200 字符', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000002', 'step_completion', '你是一名资深软件测试工程师，擅长补全测试用例的执行步骤与预期结果。请基于给定的用例标题与已有子节点，补全缺失的前置条件、步骤或预期结果，内容应具体、可执行、与用例主题一致。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "nodes": [
    {"type": "step", "title": "操作步骤描述"},
    {"type": "expected", "title": "预期结果描述"}
  ]
}

字段约束：
- 顶层必须是 nodes 数组，元素仅允许 step/expected 类型
- step/expected 节点不得有子节点，不得带 priority
- 仅补全缺失部分，不重复输出已有内容
- title 必填，不超过 200 字符', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000003', 'text_import', '你是一名软件测试文档解析助手，擅长将外部文本（需求文档、用例清单等）解析为结构化的测试用例树。请识别文本中的模块层级、用例标题及其前置条件、步骤、预期结果。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "nodes": [
    {
      "type": "normal",
      "title": "模块分组标题",
      "children": [
        {
          "type": "case",
          "title": "用例标题",
          "children": [
            {"type": "precondition", "title": "前置条件描述"},
            {"type": "step", "title": "操作步骤描述"},
            {"type": "expected", "title": "预期结果描述"}
          ]
        }
      ]
    }
  ]
}

字段约束：
- 顶层必须是 nodes 数组
- type 仅允许 normal/case/precondition/step/expected
- normal 可嵌套 normal/case；case 的直接子节点只能是 precondition/step/expected
- title 必填，不超过 200 字符
- 无法识别为用例结构的内容归入 normal 节点，不得虚构原文没有的用例；完全无法解析出用例结构时输出空 nodes 数组', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000004', 'review_summary', '你是一名测试评审总结助手。请基于给定的评审统计数据与未通过用例采样，输出一份简明的评审总结，包含主要问题归纳、改进建议与风险提示三个章节，语言精炼、面向测试负责人。', '输出为 Markdown 文本，章节结构依次为：主要问题归纳、改进建议、风险提示。总篇幅控制在 2000 字以内，不输出统计数据原文，不虚构统计中不存在的数字。', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000005', 'assistant_chat', '你是软件测试平台的智能助手，帮助用户查询平台数据、解答测试相关问题，并可在用户确认后执行受支持的写操作。回答应简洁准确，不确定时明确说明，不编造平台数据。', '普通回答使用简体中文纯文本或轻量 Markdown；需要调用工具时严格按照工具调用协议输出，不得在工具调用外虚构工具结果。当问题超出平台使用指引与知识库范围（get_platform_guide 无命中）时，明确告知用户无法回答或超出使用指引范围，不得编造指引内容。', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000006', 'priority_recommendation', '你是一名测试用例优先级评估助手。请基于用例标题、所属模块与需求上下文，推荐用例优先级（P0-P3），并保持同类用例判定标准一致。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "priority": "P1"
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 priority 字段
- priority 取值仅允许 P0/P1/P2/P3', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000007', 'bug_form_suggestion', '你是一名缺陷管理助手。请基于用户填写的缺陷描述，优化缺陷标题（简洁、含关键现象与场景），并建议严重等级与类型。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "optimizedTitle": "优化后的缺陷标题（简洁、含关键现象与场景）",
  "severity": "serious",
  "priority": "high",
  "reason": "建议依据的一句话说明"
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 optimizedTitle/severity/priority/reason 四个字段
- optimizedTitle 必填，不超过 100 字符
- severity 仅允许 fatal/serious/general/minor
- priority 仅允许 high/medium/low
- reason 必填，一句话说明建议依据', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000008', 'dsl_translation', '你是一名脑图操作指令翻译助手。请将用户的自然语言编辑意图翻译为平台脑图 DSL 指令序列，仅使用受支持的指令集，不执行超出用户意图的操作。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "commands": [
    {
      "selector": {"types": ["case"], "keyword": "登录"},
      "action": {"type": "mark_priority", "params": {"priority": "P1"}}
    }
  ],
  "ambiguous": false,
  "clarification": null
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 commands/ambiguous/clarification 字段
- commands 数组按序执行，上限 10 条；翻译意图不明确时 ambiguous 置 true、clarification 说明原因、commands 为空数组
- selector 各条件为 AND 关系，可选字段：types（节点类型）/priorities（仅对 case 生效）/keyword/subtreeRootTitle（限定子树范围）/aiGenerated
- action.type 仅允许 mark_type/mark_priority/highlight/move/add_child
- mark_type 的 params.nodeType 仅允许 normal/case/precondition/step/expected；mark_priority 的 params.priority 仅允许 P0/P1/P2/P3；move 的 params.targetParentTitle 必须为输入上下文中的节点标题或 @selected
- 指令必须属于注册的 DSL 指令集，selector.subtreeRootTitle 与 move 目标引用的节点必须来自输入上下文', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000009', 'plan_order_reason', '你是一名测试计划执行顺序解释助手。请基于给定用例的评分因子（历史关联缺陷数、优先级权重、模块缺陷密度），用一句话说明推荐优先执行该用例的理由。', '输出为一句简体中文说明，不超过 120 字符，仅陈述因子事实与结论，不输出评分公式与原始数值以外的推断。', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000010', 'missing_point_analysis', '你是一名测试覆盖度分析助手。请对比需求描述与现有用例清单，找出需求已提及但用例未覆盖的测试点，说明遗漏原因并给出建议归属模块。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "points": [
    {
      "title": "建议新增的用例标题",
      "description": "遗漏原因说明",
      "suggestedModulePath": "建议归属模块路径",
      "relatedCaseTitles": ["关联的候选用例标题"]
    }
  ]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 points 数组字段
- points 数组，遗漏点不超过 30 条
- title 必填，不超过 200 字符
- description 必填，说明遗漏原因
- suggestedModulePath 必须为输入中出现过的模块路径或空字符串
- relatedCaseTitles 只允许引用输入候选用例的标题，无关联时为空数组', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000011', 'keyword_extraction', '你是一名测试需求关键词抽取助手。请从给定需求文本中抽取用于检索测试用例库的关键词，关键词应为需求中出现过的核心业务词或短语，避免空泛词汇。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "keywords": ["登录", "验证码"]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 keywords 数组字段
- 关键词数量不超过 10 个，每个关键词不超过 20 字符
- 必须为输入需求文本中出现过的词或短语', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000012', 'case_plan_recommendation', '你是一名测试用例规划推荐助手。请基于需求描述与候选用例清单，为每条推荐用例生成一句话理由，说明其应纳入当前评审或测试计划用例清单的原因。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "reasons": ["该用例覆盖登录失败主流程，应纳入本次评审或测试计划"]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 reasons 数组字段
- reasons 数组长度与输入用例标题清单一一对应、完全一致
- 每条理由不超过 120 字符
- 无法给出理由的用例可用空字符串占位', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000013', 'review_check', '你是一名测试用例评审检查助手。请检查给定批次用例的完整性：缺少前置条件、步骤描述笼统、缺少预期结果、相似用例优先级冲突，并给出具体改进建议。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "items": [
    {
      "snapshotNodeId": "本批输入中的用例快照节点 ID",
      "dimension": "missing_precondition",
      "suggestion": "具体改进建议"
    }
  ]
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 items 数组字段
- items 数组，每处问题一条建议，无问题的用例不输出
- snapshotNodeId 必须来自本批输入，不得虚构
- dimension 仅允许 missing_precondition/vague_step/missing_expected/priority_conflict
- suggestion 必填，给出具体改进建议', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000014', 'bug_clustering', '你是一名缺陷归纳分析助手。请为给定的缺陷簇归纳简短的主题标签，概括该簇缺陷的共性问题。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "label": "登录态失效问题",
  "rootCause": "会话超时导致登录态失效，疑似为服务端会话校验过期"
}

字段约束：
- 顶层必须为 JSON 对象，仅包含 label/rootCause 两个字段
- label 必填，不超过 30 字符的主题名称
- rootCause 必填，一句话根因推断；证据不足时使用「疑似」措辞', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('d0000000-0000-0000-0000-000000000015', 'requirement_split', '你是一名测试需求拆分助手。请将整份需求文档按模块/功能拆分为细粒度需求条目：一个需求点 = 一个可测试功能行为（如「用户管理」拆为新增/编辑/删除/查询用户四条），模块仅作归属分组。条目内容需保留原始描述中的关键约束，不得虚构原文没有的功能。', '输出必须为合法 JSON 对象，不得包含 JSON 之外的任何文字。JSON 结构必须严格遵循如下示例（字段名、类型、层级完全一致）：
{
  "modules": [
    {
      "module": "模块名",
      "items": [
        {"title": "需求点标题", "content": "需求点内容（Markdown）"}
      ]
    }
  ]
}

字段约束：
- 顶层必须是 modules 数组，非空且不超过 50 个模块
- module 必填，不超过 100 字符
- 每模块 items 非空且不超过 50 条
- title 必填，不超过 200 字符
- content 必填，为 Markdown 格式的需求点描述', FALSE, '00000000-0000-0000-0000-000000000000', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (function_type) WHERE is_deleted = false DO NOTHING;

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

-- 接口测试 — 环境管理
COMMENT ON TABLE api_environment IS '接口测试环境表';
COMMENT ON COLUMN api_environment.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_environment.name IS '环境名称';
COMMENT ON COLUMN api_environment.description IS '环境描述';
COMMENT ON COLUMN api_environment.scope IS '归属范围：project/global';
COMMENT ON COLUMN api_environment.is_default IS '是否默认环境';
COMMENT ON COLUMN api_environment.sort_order IS '排序序号';

COMMENT ON TABLE api_environment_http IS '环境 HTTP 配置';
COMMENT ON COLUMN api_environment_http.environment_id IS '关联环境 ID';
COMMENT ON COLUMN api_environment_http.name IS '配置名称';
COMMENT ON COLUMN api_environment_http.ref_name IS '引用名称（步骤中引用该配置）';
COMMENT ON COLUMN api_environment_http.base_url IS '基础 URL';
COMMENT ON COLUMN api_environment_http.default_headers IS '默认请求头';

COMMENT ON TABLE api_environment_variable IS '环境变量表';
COMMENT ON COLUMN api_environment_variable.environment_id IS '关联环境 ID';
COMMENT ON COLUMN api_environment_variable.name IS '变量名';
COMMENT ON COLUMN api_environment_variable.value IS '变量值';
COMMENT ON COLUMN api_environment_variable.description IS '变量描述';
COMMENT ON COLUMN api_environment_variable.sort_order IS '排序序号';

COMMENT ON TABLE api_environment_data_source IS '数据源配置表';
COMMENT ON COLUMN api_environment_data_source.environment_id IS '关联环境 ID';
COMMENT ON COLUMN api_environment_data_source.name IS '数据源名称';
COMMENT ON COLUMN api_environment_data_source.ref_name IS '引用名称（步骤中引用该数据源）';
COMMENT ON COLUMN api_environment_data_source.driver IS 'JDBC 驱动类名';
COMMENT ON COLUMN api_environment_data_source.url IS 'JDBC 连接 URL';
COMMENT ON COLUMN api_environment_data_source.connection_properties IS '连接属性';
COMMENT ON COLUMN api_environment_data_source.max_pool_size IS '连接池大小';

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
COMMENT ON COLUMN api_interface.protocol IS '协议：http/jdbc';
COMMENT ON COLUMN api_interface.method IS 'HTTP 方法';
COMMENT ON COLUMN api_interface.path IS '请求路径';
COMMENT ON COLUMN api_interface.description IS '接口描述';
COMMENT ON COLUMN api_interface.headers IS '请求头';
COMMENT ON COLUMN api_interface.body_type IS '请求体类型';
COMMENT ON COLUMN api_interface.body IS '请求体';
COMMENT ON COLUMN api_interface.query_params IS 'Query 参数';
COMMENT ON COLUMN api_interface.rest_params IS 'REST 参数';
COMMENT ON COLUMN api_interface.auth IS '认证配置';
COMMENT ON COLUMN api_interface.status IS '状态：draft/published';
COMMENT ON COLUMN api_interface.created_by IS '创建人';
COMMENT ON COLUMN api_interface.change_version IS '乐观锁版本号';
COMMENT ON COLUMN api_interface.response_example IS '响应示例';
COMMENT ON COLUMN api_interface.reference_count IS '引用计数';

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
COMMENT ON COLUMN api_import_mapping.import_record_id IS '关联导入记录 ID';
COMMENT ON COLUMN api_import_mapping.source_type IS '导入源类型';
COMMENT ON COLUMN api_import_mapping.source_id IS '导入源 ID';
COMMENT ON COLUMN api_import_mapping.source_name IS '导入源名称';
COMMENT ON COLUMN api_import_mapping.target_type IS '目标类型：interface/scene';
COMMENT ON COLUMN api_import_mapping.target_id IS '目标 ID';
COMMENT ON COLUMN api_import_mapping.action IS '操作：created/updated/skipped';

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
COMMENT ON COLUMN api_import_record.import_type IS '导入类型：file_swagger/file_postman/file_har/file_jmeter/url_swagger';
COMMENT ON COLUMN api_import_record.source_name IS '导入源名称';
COMMENT ON COLUMN api_import_record.status IS '导入状态';
COMMENT ON COLUMN api_import_record.summary IS '导入汇总：{created, updated, failed, skipped}';
COMMENT ON COLUMN api_import_record.error_details IS '错误详情';
COMMENT ON COLUMN api_import_record.repository_id IS '关联仓库 ID';
COMMENT ON COLUMN api_import_record.created_by IS '导入人';

COMMENT ON TABLE api_debug_record IS '调试历史记录表';
COMMENT ON COLUMN api_debug_record.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_debug_record.user_id IS '操作用户 ID';
COMMENT ON COLUMN api_debug_record.name IS '记录名称';
COMMENT ON COLUMN api_debug_record.protocol IS '协议：http/jdbc';
COMMENT ON COLUMN api_debug_record.method IS 'HTTP 方法';
COMMENT ON COLUMN api_debug_record.url IS '请求 URL';
COMMENT ON COLUMN api_debug_record.headers IS '请求头';
COMMENT ON COLUMN api_debug_record.body_type IS '请求体类型';
COMMENT ON COLUMN api_debug_record.body IS '请求体';
COMMENT ON COLUMN api_debug_record.query_params IS 'Query 参数';
COMMENT ON COLUMN api_debug_record.jdbc_config IS 'JDBC 配置';
COMMENT ON COLUMN api_debug_record.processors IS '前置/后置处理器';
COMMENT ON COLUMN api_debug_record.environment_id IS '使用的环境 ID';
COMMENT ON COLUMN api_debug_record.timeout_ms IS '超时时间（毫秒）';
COMMENT ON COLUMN api_debug_record.executed_at IS '执行时间';
COMMENT ON COLUMN api_debug_record.duration_ms IS '执行耗时（毫秒）';
COMMENT ON COLUMN api_debug_record.status IS '执行结果：success/failed/error';
COMMENT ON COLUMN api_debug_record.response_status IS '响应状态码';
COMMENT ON COLUMN api_debug_record.response_headers IS '响应头';
COMMENT ON COLUMN api_debug_record.response_body IS '响应体（截断存储）';
COMMENT ON COLUMN api_debug_record.response_size IS '响应体大小（字节）';
COMMENT ON COLUMN api_debug_record.error_message IS '错误信息';

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

COMMENT ON TABLE api_scene_follow IS '场景关注表';
COMMENT ON COLUMN api_scene_follow.scene_id IS '关联场景 ID';
COMMENT ON COLUMN api_scene_follow.user_id IS '关注用户 ID';

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
