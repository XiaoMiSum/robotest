-- ============================================================
-- Robotest 数据库初始化 DDL（全量合并版）
-- PostgreSQL 14+
-- 用途：首次建库时一次性执行，不要在已迁移的库上运行
-- ============================================================

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
                                top_module  VARCHAR(50)  NOT NULL DEFAULT '',
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
                              created_by  UUID,
                              is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
                              created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ws_workspace_name ON ws_workspace (name) WHERE is_deleted = false;
CREATE INDEX idx_ws_workspace_created ON ws_workspace (created_at DESC);
CREATE INDEX idx_ws_workspace_created_by ON ws_workspace (created_by);

-- 用户-工作空间关联表
CREATE TABLE ws_user (
                         id                UUID      PRIMARY KEY,
                         user_id           UUID      NOT NULL,
                         workspace_id      UUID      NOT NULL,
                         workspace_role    UUID      NOT NULL DEFAULT 'c0000000-0000-0000-0000-000000000002',
                         default_project_id UUID,
                         joined_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         last_accessed_at  TIMESTAMP NULL,
                         is_deleted        BOOLEAN   NOT NULL DEFAULT FALSE,
                         created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ws_user_user_workspace ON ws_user (user_id, workspace_id) WHERE is_deleted = false;
CREATE INDEX idx_ws_user_workspace_id ON ws_user (workspace_id);
CREATE INDEX idx_ws_user_ws_role ON ws_user (workspace_id, workspace_role);
CREATE INDEX idx_ws_user_default_project_id ON ws_user (default_project_id);
CREATE INDEX idx_ws_user_user_last_accessed ON ws_user (user_id, last_accessed_at DESC) WHERE is_deleted = FALSE;

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

-- 项目动态表
CREATE TABLE ws_project_activity (
                                id UUID PRIMARY KEY,
                                project_id UUID NOT NULL,
                                actor_id UUID NOT NULL,
                                actor_name VARCHAR(100) NOT NULL,
                                resource_type VARCHAR(32) NOT NULL,
                                resource_id UUID NOT NULL,
                                resource_name VARCHAR(200) NOT NULL,
                                action VARCHAR(32) NOT NULL,
                                summary VARCHAR(500) NOT NULL,
                                occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_activity_project_occurred
    ON ws_project_activity (project_id, occurred_at DESC, id DESC)
    WHERE is_deleted = FALSE;

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
CREATE INDEX idx_test_case_node_document_type ON test_case_node (document_id, type) WHERE is_deleted = FALSE;

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
                                id                UUID         PRIMARY KEY,
                                bug_id            UUID         NOT NULL,
                                file_name         VARCHAR(255) NOT NULL,
                                storage_path      VARCHAR(500) NULL,
                                file_size         BIGINT       NOT NULL,
                                content_type      VARCHAR(100) NULL,
                                uploader_id       UUID         NOT NULL,
                                file_resource_id  UUID         NULL,
                                is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bug_attachment_bug_id ON bug_attachment (bug_id);
CREATE INDEX idx_bug_attachment_file_resource ON bug_attachment (file_resource_id);

COMMENT ON COLUMN bug_attachment.storage_path IS '历史列（本地磁盘相对路径）：回填前兼容读取用，新行不再写入（文件管理详设 2.2）';
COMMENT ON COLUMN bug_attachment.file_resource_id IS '关联 file_resource.id（文件管理详设 2.2）；存量行由应用启动回填，NULL 期间兼容本地 storage_path';

-- ============================================================
-- 7. 需求管理
-- ============================================================

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

-- ============================================================
-- 8. 项目模块与用例文档（V1.2 重构）
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
-- 9. 接口测试 — 环境管理
-- 环境聚合：HTTP 配置/变量/数据源/处理器均以 JSONB 存储在主表（替代原四张子表）
-- ============================================================

CREATE TABLE api_environment (
                                 id           UUID         PRIMARY KEY,
                                 project_id   UUID         NOT NULL,
                                 name         VARCHAR(100) NOT NULL,
                                 description  VARCHAR(500) NULL,
                                 scope        VARCHAR(10)  NOT NULL DEFAULT 'project',
                                 is_default   BOOLEAN       NOT NULL DEFAULT FALSE,
                                 sort_order   INT          NOT NULL DEFAULT 0,
                                 http_configs JSONB        NOT NULL DEFAULT '[]',
                                 variables    JSONB        NOT NULL DEFAULT '[]',
                                 data_sources JSONB        NOT NULL DEFAULT '[]',
                                 processors   JSONB        NOT NULL DEFAULT '[]',
                                 is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
                                 created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_env_project ON api_environment(project_id);

-- ============================================================
-- 10. 接口测试 — 接口管理
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
                                 validators      JSONB        NOT NULL DEFAULT '[]',
                                 extractors      JSONB        NOT NULL DEFAULT '[]',
                                 is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                               created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_intf_project ON api_interface(project_id);
CREATE INDEX idx_intf_module ON api_interface(module_id);

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
                                          id             UUID         PRIMARY KEY,
                                          interface_id   UUID         NOT NULL,
                                          change_version INT          NOT NULL,
                                          action         VARCHAR(20)  NOT NULL,
                                          summary        VARCHAR(500) NULL,
                                          operator_id    UUID         NULL,
                                          is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
                                          created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ichangelog_interface ON api_interface_change_log(interface_id, change_version);

CREATE TABLE api_import_record (
                                   id              UUID         PRIMARY KEY,
                                   project_id      UUID         NOT NULL,
                                   import_type     VARCHAR(20)  NOT NULL,
                                   source_name     VARCHAR(200) NOT NULL,
status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
                                    summary         JSONB        NULL,
                                    error_details   JSONB        NULL,
                                    created_by      UUID         NOT NULL,
                                    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
                                    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_irecord_project ON api_import_record(project_id, created_at DESC);

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
-- 11. 接口测试 — Swagger URL 配置
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
-- 12. 接口测试 — 定时任务
-- ============================================================

CREATE TABLE api_scheduled_task (
                                    id                      UUID          PRIMARY KEY,
                                    project_id              UUID          NOT NULL,
                                    task_type               VARCHAR(30)   NOT NULL,
                                    name                    VARCHAR(200)  NOT NULL,
                                    description             VARCHAR(500)  NULL,
                                    bound_object_id         UUID          NULL,
                                    bound_object_name       VARCHAR(200)  NULL,
                                    execution_scope         VARCHAR(20)   NULL,
                                    module_ids              JSON          NULL,
                                    scene_ids               JSON          NULL,
                                    openapi_url             VARCHAR(2000) NULL,
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
-- 13. 接口测试 — Mock 服务
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
-- 14. 接口测试 — 测试场景与执行
-- ============================================================

CREATE TABLE api_scene (
                           id             UUID          PRIMARY KEY,
                           project_id     UUID          NOT NULL,
                           module_id      UUID          NULL,
                           name           VARCHAR(200)  NOT NULL,
                           description    TEXT          NULL,
 environment_id UUID          NULL,
                             priority       VARCHAR(2)    NULL,
                             status         VARCHAR(10)   NOT NULL DEFAULT 'draft',
                             variables      JSONB         NOT NULL DEFAULT '[]',  -- 场景变量唯一权威源 [{name, value, description}]
  processors     JSONB         NOT NULL DEFAULT '[]',
  steps          JSONB         NOT NULL DEFAULT '[]',
                             change_version INT           NOT NULL DEFAULT 1,
                            is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
                            created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scene_project ON api_scene(project_id);
CREATE INDEX idx_scene_module ON api_scene(module_id);

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
                                      source         VARCHAR(20)   NOT NULL DEFAULT 'scene',
                                      report_id      UUID          NULL,
                                      error_message  VARCHAR(2000) NULL,
                                       executed_at    TIMESTAMP     NOT NULL,
                                       duration_ms    INT           NULL,
                                       is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
                                      created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exec_scene_id ON api_execution_record(scene_id, executed_at DESC);
CREATE INDEX idx_exec_project_executed ON api_execution_record(project_id, executed_at DESC);
CREATE INDEX idx_exec_status ON api_execution_record(status);

CREATE TABLE api_report (
                            id                 UUID          PRIMARY KEY,
                            project_id         UUID          NOT NULL,
                            execution_record_id UUID         NULL,
                            report_type        VARCHAR(20)   NOT NULL,
                            external_id        UUID          NULL,
                            name               VARCHAR(200)  NOT NULL,
                            environment_name   VARCHAR(100)  NULL,
                            execution_mode     VARCHAR(20)   NOT NULL DEFAULT 'platform',
                            source             VARCHAR(20)   NOT NULL DEFAULT 'scene',
                            status             VARCHAR(20)   NOT NULL,
                            summary            JSONB         NOT NULL,
                            result             JSONB         NOT NULL,
                            ryze_snapshot      JSONB         NULL,
                            share_token        VARCHAR(64)   NULL,
                            share_expires_at   TIMESTAMP     NULL,
                            share_user_id      UUID          NULL,
                            is_deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
                            created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_report_type_external ON api_report(report_type, external_id);
CREATE INDEX idx_report_project_created ON api_report(project_id, created_at DESC);
CREATE UNIQUE INDEX uk_report_share_token ON api_report(share_token) WHERE share_token IS NOT NULL;
CREATE INDEX idx_report_share_user ON api_report(share_user_id) WHERE share_user_id IS NOT NULL;

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
-- 15. 接口测试 — 公共组件
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
-- 16. 接口测试 — 函数表（内置 + 自定义）
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
-- 17. 追溯矩阵
-- ============================================================

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
    disposition    varchar(20) NULL,
    reason         varchar(500) NULL,
    disposed_by    uuid NULL,
    created_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted     boolean NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_trace_edge_pair ON trace_edge (source_type, source_id, target_type, target_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_trace_edge_project ON trace_edge (project_id);
CREATE INDEX idx_trace_edge_source ON trace_edge (source_type, source_id);
CREATE INDEX idx_trace_edge_target ON trace_edge (target_type, target_id);

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

-- ============================================================
-- 18. AI 能力
-- ============================================================

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
    updated_by uuid NULL,
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

-- ============================================================
-- 19. 种子数据（权限点、角色）
-- ============================================================

-- ------------------------------------------------------------
-- 19.1 权限点（系统管理模块）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000001', 'user',                '用户管理',       NULL,  '用户管理',     '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000002', 'user:view',           '查看用户',       'user', '用户管理',     '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000003', 'user:create',         '创建用户',       'user', '用户管理',     '系统管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000004', 'user:edit',           '编辑用户',       'user', '用户管理',     '系统管理', 'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000005', 'user:disable',        '禁用/启用用户',   'user', '用户管理',     '系统管理', 'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000006', 'user:reset-password', '重置密码',       'user', '用户管理',     '系统管理', 'global', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000007', 'workspace',            '空间管理',       NULL,  '工作空间管理',  '系统管理', 'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000008', 'workspace:view',       '查看工作空间',    'workspace', '工作空间管理', '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000009', 'workspace:create',     '创建工作空间',    'workspace', '工作空间管理', '系统管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000010', 'workspace:edit',       '编辑工作空间',    'workspace', '工作空间管理', '系统管理', 'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000011', 'workspace:delete',     '解散工作空间',    'workspace', '工作空间管理', '系统管理', 'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000012', 'workspace:manage-members', '管理成员',   'workspace', '工作空间管理', '系统管理', 'global', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000013', 'role',                '角色管理',       NULL,  '角色管理',     '系统管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000014', 'role:view',           '查看角色',       'role', '角色管理',     '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000015', 'role:create',         '创建角色',       'role', '角色管理',     '系统管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000016', 'role:edit',           '编辑角色',       'role', '角色管理',     '系统管理', 'global', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000017', 'role:delete',         '删除角色',       'role', '角色管理',     '系统管理', 'global', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.2 权限点（审计日志模块，全局系统管理，审计查询详细设计 2.1）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000021', 'audit',      '审计日志',    NULL, '审计日志', '系统管理', 'global', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000022', 'audit:view', '查看审计日志', 'audit', '审计日志', '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.3 权限点（业务模块 — 工作空间/项目/测试用例/评审/计划/缺陷）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000001', 'ws-info',            '空间信息',     NULL,           '我的空间', '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000002', 'ws-info:view',       '查看空间信息',  'ws-info',      '我的空间', '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000003', 'ws-info:edit',       '编辑空间信息',  'ws-info',      '我的空间', '我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000004', 'ws-member',          '成员管理',     NULL,           '我的空间', '我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000005', 'ws-member:view',     '查看成员',     'ws-member',    '我的空间', '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000006', 'ws-member:manage',   '管理成员',     'ws-member',    '我的空间', '我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000007', 'ws-invitation',      '邀请链接',     NULL,           '我的空间', '我的空间', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000008', 'ws-invitation:view', '查看邀请链接',  'ws-invitation','我的空间', '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000009', 'ws-invitation:manage','管理邀请链接', 'ws-invitation','我的空间', '我的空间', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000010', 'project',            '项目列表',     NULL,           '项目',    '我的空间', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000011', 'project:view',       '查看项目',     'project',      '项目',    '我的空间', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000017', 'case',               '测试用例',     NULL,           '测试用例', '功能测试', 'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000018', 'case:view',          '查看用例',     'case',         '测试用例', '功能测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000019', 'case:edit',          '编辑用例',     'case',         '测试用例', '功能测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000020', 'review',             '测试评审',     NULL,           '测试评审', '功能测试', 'workspace', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000021', 'review:view',        '查看评审',     'review',       '测试评审', '功能测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000022', 'review:create',      '发起评审',     'review',       '测试评审', '功能测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000023', 'review:edit',        '评审操作',     'review',       '测试评审', '功能测试', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000024', 'review:complete',    '完成评审',     'review',       '测试评审', '功能测试', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000025', 'plan',               '测试计划',     NULL,           '测试计划', '功能测试', 'workspace', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000026', 'plan:view',          '查看计划',     'plan',         '测试计划', '功能测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000027', 'plan:create',        '创建计划',     'plan',         '测试计划', '功能测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000028', 'plan:execute',       '执行计划',     'plan',         '测试计划', '功能测试', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000029', 'plan:close',         '关闭计划',     'plan',         '测试计划', '功能测试', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000030', 'bug',                '缺陷',        NULL,           '缺陷',    '缺陷管理', 'workspace', 18, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000031', 'bug:view',           '查看缺陷',     'bug',          '缺陷',    '缺陷管理', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.4 权限点（需求管理）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000034', 'requirement',         '需求管理',     NULL,          '需求管理', '需求管理', 'workspace', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000035', 'requirement:view',    '查看需求',     'requirement', '需求管理', '需求管理', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000074', 'requirement:create',  '新建与导入需求', 'requirement', '需求管理', '需求管理', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000036', 'requirement:edit',    '编辑需求',     'requirement', '需求管理', '需求管理', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000075', 'requirement:confirm', '确认与归档需求', 'requirement', '需求管理', '需求管理', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.5 权限点（接口测试模块）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
-- 测试场景
('c0000000-0000-0000-0000-000000000040', 'api-scene',          '测试场景',        NULL,            '接口测试·测试场景',  '接口测试', 'workspace', 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000041', 'api-scene:view',     '查看场景',        'api-scene',     '接口测试·测试场景',  '接口测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000042', 'api-scene:edit',     '编辑场景',        'api-scene',     '接口测试·测试场景',  '接口测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000043', 'api-scene:import',   '导入场景',        'api-scene',     '接口测试·测试场景',  '接口测试', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000045', 'api-scene:execute',  '执行场景',        'api-scene',     '接口测试·测试场景',  '接口测试', 'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 接口管理
('c0000000-0000-0000-0000-000000000046', 'api-interface',         '接口管理',    NULL,            '接口测试·接口管理',  '接口测试', 'workspace', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000047', 'api-interface:view',    '查看接口',    'api-interface',  '接口测试·接口管理',  '接口测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000048', 'api-interface:edit',    '编辑接口',    'api-interface',  '接口测试·接口管理',  '接口测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000049', 'api-interface:delete', '删除接口',    'api-interface',  '接口测试·接口管理',  '接口测试', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 公共组件
('c0000000-0000-0000-0000-000000000050', 'api-component',         '公共组件',    NULL,            '接口测试·公共组件',  '接口测试', 'workspace', 17, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000051', 'api-component:view',    '查看组件',    'api-component',  '接口测试·公共组件',  '接口测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000052', 'api-component:edit',    '编辑组件',    'api-component',  '接口测试·公共组件',  '接口测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000070', 'api-component:edit-space', '编辑空间级组件', 'api-component', '接口测试·公共组件', '接口测试', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000071', 'api-component:edit-global', '编辑全局组件', 'api-component', '接口测试·公共组件', '接口测试', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 快速调试
('c0000000-0000-0000-0000-000000000053', 'api-debug',         '快速调试',    NULL,            '接口测试·快速调试',  '接口测试', 'workspace', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000054', 'api-debug:view',    '查看调试记录', 'api-debug',     '接口测试·快速调试',  '接口测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 定时任务
('c0000000-0000-0000-0000-000000000055', 'api-timer',         '定时任务',    NULL,            '接口测试·定时任务',  '接口测试', 'workspace', 14, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000056', 'api-timer:view',    '查看定时任务', 'api-timer',     '接口测试·定时任务',  '接口测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000057', 'api-timer:edit',    '编辑定时任务', 'api-timer',     '接口测试·定时任务',  '接口测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- Mock 服务
('c0000000-0000-0000-0000-000000000058', 'api-mock',          'Mock 服务',    NULL,            '接口测试·Mock服务',  '接口测试', 'workspace', 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000059', 'api-mock:view',     '查看 Mock',    'api-mock',      '接口测试·Mock服务',  '接口测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000060', 'api-mock:edit',     '编辑 Mock',    'api-mock',      '接口测试·Mock服务',  '接口测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 测试报告
('c0000000-0000-0000-0000-000000000061', 'api-report',        '测试报告',    NULL,            '接口测试·测试报告',  '接口测试', 'workspace', 13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000062', 'api-report:view',   '查看报告',    'api-report',    '接口测试·测试报告',  '接口测试', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000063', 'api-report:delete', '删除报告',    'api-report',    '接口测试·测试报告',  '接口测试', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.5.1 权限点（接口测试·环境管理 / 函数管理，项目设置分组）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000064', 'api-env',            '环境管理',       NULL,          '接口测试·环境管理', '接口测试', 'workspace', 15, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000065', 'api-env:view',       '查看环境',       'api-env',     '接口测试·环境管理', '接口测试', 'workspace', 1,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000066', 'api-env:edit',       '编辑环境',       'api-env',     '接口测试·环境管理', '接口测试', 'workspace', 2,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000067', 'api-func',           '函数管理',       NULL,          '接口测试·函数管理', '接口测试', 'workspace', 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000068', 'api-func:view',      '查看函数',       'api-func',    '接口测试·函数管理', '接口测试', 'workspace', 1,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000069', 'api-func:edit',      '编辑函数',       'api-func',    '接口测试·函数管理', '接口测试', 'workspace', 2,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000072', 'api-func:edit-space', '编辑空间级函数', 'api-func',    '接口测试·函数管理', '接口测试', 'workspace', 3,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000073', 'api-func:edit-global', '编辑全局函数', 'api-func',    '接口测试·函数管理', '接口测试', 'workspace', 4,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.6 权限点（追溯矩阵）
-- ------------------------------------------------------------

INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000076', 'trace',      '追溯矩阵',     NULL,    '追溯矩阵', '追溯矩阵', 'workspace', 19, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000077', 'trace:view', '查看追溯矩阵', 'trace', '追溯矩阵', '追溯矩阵', 'workspace', 1,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000078', 'trace:edit', '编辑追溯',     'trace', '追溯矩阵', '追溯矩阵', 'workspace', 2,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.7 权限点（AI 能力）
-- ------------------------------------------------------------

INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000023', 'ai:admin',   'AI 配置与用量管理',  'ai', 'AI 能力', 'AI 能力', 'global',    1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000079', 'ai',         'AI 能力',           NULL, 'AI 能力', 'AI 能力', 'workspace', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000080', 'ai:task',    '发起与管理 AI 任务', 'ai',  'AI 能力', 'AI 能力', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000081', 'ai:confirm', 'AI 产物确认',        'ai',  'AI 能力', 'AI 能力', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.8 权限点（文件管理）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000031', 'file',        '文件管理',   NULL,   '文件管理', '系统管理', 'global', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000032', 'file:view',   '查看文件',   'file', '文件管理', '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000033', 'file:delete', '删除文件',   'file', '文件管理', '系统管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ------------------------------------------------------------
-- 19.9 预置角色（含全部版本权限合并）
-- ------------------------------------------------------------
INSERT INTO sys_role (id, name, description, type, is_system, permissions, created_at, updated_at, is_deleted) VALUES
-- 系统管理员：拥有系统管理所有权限
('b0000000-0000-0000-0000-000000000001', '系统管理员',
 '拥有系统管理所有权限', 'system', TRUE,
 '["user","user:view","user:create","user:edit","user:disable","user:reset-password","workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","role","role:view","role:create","role:edit","role:delete","ai","ai:admin","file","file:view","file:delete"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 空间管理系统角色：拥有工作空间管理所有权限（跨空间管理）
('b0000000-0000-0000-0000-000000000002', '空间管理员',
 '拥有工作空间管理所有权限，可创建/删除/管理所有工作空间', 'system', TRUE,
 '["workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","ws-info","ws-info:view","ws-info:edit","ws-member","ws-member:view","ws-member:manage","ws-invitation","ws-invitation:view","ws-invitation:manage"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- workspace 管理员：空间内全部业务权限（显式授权全部空间权限码）
('c0000000-0000-0000-0000-000000000001', '管理员',
 '空间管理员 — 拥有工作空间内全部业务权限', 'workspace', TRUE,
 '["ws-info","ws-info:view","ws-info:edit","ws-member","ws-member:view","ws-member:manage","ws-invitation","ws-invitation:view","ws-invitation:manage","project","project:view","case","case:view","case:edit","review","review:view","review:create","review:edit","review:complete","plan","plan:view","plan:create","plan:execute","plan:close","bug","bug:view","requirement","requirement:view","requirement:create","requirement:edit","requirement:confirm","trace","trace:view","trace:edit","api-scene","api-scene:view","api-scene:edit","api-scene:import","api-scene:execute","api-interface","api-interface:view","api-interface:edit","api-interface:delete","api-component","api-component:view","api-component:edit","api-component:edit-space","api-component:edit-global","api-env","api-env:view","api-env:edit","api-func","api-func:view","api-func:edit","api-func:edit-space","api-func:edit-global","api-debug","api-debug:view","api-timer","api-timer:view","api-timer:edit","api-mock","api-mock:view","api-mock:edit","api-report","api-report:view","api-report:delete","ai","ai:task","ai:confirm"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- workspace 普通成员：默认角色
('c0000000-0000-0000-0000-000000000002', '成员',
 '空间成员 — 除删除/归档项目、管理成员、编辑空间信息外的其他权限', 'workspace', TRUE,
 '["ws-info:view","ws-member:view","ws-invitation:view","ws-invitation:manage","project:view","case:view","case:edit","review:view","review:create","review:edit","review:complete","plan:view","plan:create","plan:execute","plan:close","bug:view","requirement:view","requirement:create","requirement:edit","requirement:confirm","trace","trace:view","trace:edit","api-scene","api-scene:view","api-scene:edit","api-scene:import","api-scene:execute","api-interface","api-interface:view","api-interface:edit","api-component","api-component:view","api-component:edit","api-env","api-env:view","api-env:edit","api-func","api-func:view","api-func:edit","api-debug","api-debug:view","api-timer","api-timer:view","api-timer:edit","api-mock","api-mock:view","api-mock:edit","api-report","api-report:view","ai","ai:task","ai:confirm"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

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
COMMENT ON COLUMN sys_user.status IS '用户状态：active=正常, disabled=禁用, locked=锁定';
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
COMMENT ON COLUMN sys_permission.top_module IS '所属一级模块：系统管理/我的空间/功能测试/接口测试/缺陷管理';
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
COMMENT ON COLUMN ws_workspace.created_by IS '创建人 user id（逻辑外键 → sys_user.id，无物理外键，C5）';

COMMENT ON TABLE ws_user IS '用户-工作空间关联表';
COMMENT ON COLUMN ws_user.id IS '关联唯一标识';
COMMENT ON COLUMN ws_user.user_id IS '用户 ID，关联 sys_user.id';
COMMENT ON COLUMN ws_user.workspace_id IS '工作空间 ID，关联 ws_workspace.id';
COMMENT ON COLUMN ws_user.workspace_role IS '空间角色 ID，关联 sys_role.id（预置角色 UUID）';
COMMENT ON COLUMN ws_user.default_project_id IS '空间内默认项目 ID，关联 ws_project.id';
COMMENT ON COLUMN ws_user.joined_at IS '加入时间';
COMMENT ON COLUMN ws_user.last_accessed_at IS '最近一次成功进入工作空间的时间，NULL=未进入过';

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

COMMENT ON TABLE ws_project_activity IS '项目动态表（项目语义操作时间线，供工作台「最近动态」展示）';
COMMENT ON COLUMN ws_project_activity.id IS '动态唯一标识';
COMMENT ON COLUMN ws_project_activity.project_id IS '所属项目 ID，关联 ws_project.id';
COMMENT ON COLUMN ws_project_activity.actor_id IS '操作人 ID，关联 sys_user.id';
COMMENT ON COLUMN ws_project_activity.actor_name IS '操作人名称快照（用户删除后仍可展示历史操作人，系统任务记「系统」）';
COMMENT ON COLUMN ws_project_activity.resource_type IS '资源类型：PROJECT/TEST_CASE_DOCUMENT/TEST_REVIEW/TEST_PLAN/BUG';
COMMENT ON COLUMN ws_project_activity.resource_id IS '资源 ID（配合 resource_type 跳转详情页）';
COMMENT ON COLUMN ws_project_activity.resource_name IS '资源名称快照（资源删除后动态仍可展示）';
COMMENT ON COLUMN ws_project_activity.action IS '动作编码：PROJECT_CREATED/PROJECT_UPDATED/PROJECT_ARCHIVED/PROJECT_UNARCHIVED/PROJECT_DELETED/CASE_CREATED/CASE_UPDATED/CASE_DELETED/REVIEW_* /PLAN_* /BUG_*';
COMMENT ON COLUMN ws_project_activity.summary IS '动态摘要（后端业务服务生成的语义描述）';
COMMENT ON COLUMN ws_project_activity.occurred_at IS '业务发生时间（工作台按 occurred_at DESC, id DESC 取最近动态）';

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
COMMENT ON COLUMN test_plan.status IS '计划状态：new=新建, in_progress=进行中, completed=已完成, blocked=已阻塞, closed=已关闭';
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
COMMENT ON COLUMN test_review.status IS '评审状态：new=待评审, in_progress=进行中, completed=已通过, rejected=已驳回';

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

-- 需求管理
COMMENT ON TABLE requirement IS '需求条目表（项目内需求主表）';
COMMENT ON COLUMN requirement.id IS '需求 ID';
COMMENT ON COLUMN requirement.project_id IS '所属项目 ID（隔离边界）';
COMMENT ON COLUMN requirement.module_id IS '归属模块 ID（逻辑外键）';
COMMENT ON COLUMN requirement.system_version IS '被测业务系统版本（按属性变更处理）';
COMMENT ON COLUMN requirement.code IS '需求编号（项目内唯一）';
COMMENT ON COLUMN requirement.title IS '需求标题';
COMMENT ON COLUMN requirement.description IS '需求描述正文（Markdown）';
COMMENT ON COLUMN requirement.status IS '状态机：draft/confirmed/changed/archived';
COMMENT ON COLUMN requirement.priority IS '优先级：high/medium/low';
COMMENT ON COLUMN requirement.owner_id IS '负责人 ID';
COMMENT ON COLUMN requirement.tags IS '标签集合（jsonb 数组）';
COMMENT ON COLUMN requirement.source IS '来源：manual/import';
COMMENT ON COLUMN requirement.source_file_id IS '导入来源附件 ID';
COMMENT ON COLUMN requirement.confirmed_at IS '最近一次进入已确认状态的时间';

COMMENT ON TABLE requirement_change_log IS '需求变更记录表（时间线）';
COMMENT ON COLUMN requirement_change_log.id IS '记录 ID';
COMMENT ON COLUMN requirement_change_log.requirement_id IS '所属需求 ID（逻辑外键）';
COMMENT ON COLUMN requirement_change_log.operator_id IS '操作人 ID';
COMMENT ON COLUMN requirement_change_log.change_type IS '变更类型：title/description/module/status/attribute';
COMMENT ON COLUMN requirement_change_log.before_summary IS '变更前字段级摘要';
COMMENT ON COLUMN requirement_change_log.after_summary IS '变更后字段级摘要';

COMMENT ON TABLE requirement_split_record IS '需求拆解记录表';
COMMENT ON COLUMN requirement_split_record.id IS '记录 ID';
COMMENT ON COLUMN requirement_split_record.project_id IS '归属项目 ID（隔离边界）';
COMMENT ON COLUMN requirement_split_record.source_type IS '拆解来源：document/requirement';
COMMENT ON COLUMN requirement_split_record.source_file_id IS '导入原始文档附件 ID';
COMMENT ON COLUMN requirement_split_record.source_requirement_id IS '原条目标识 ID';
COMMENT ON COLUMN requirement_split_record.ai_task_id IS '关联 AI 任务 ID（逻辑外键 → ai_task）';
COMMENT ON COLUMN requirement_split_record.status IS '状态：pending/adopted/rejected';
COMMENT ON COLUMN requirement_split_record.adopt_result IS '采纳结果（jsonb）';

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
COMMENT ON COLUMN api_interface.validators IS '响应验证器(仅定义存储)';
COMMENT ON COLUMN api_interface.extractors IS '响应提取器(仅定义存储)';

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
COMMENT ON COLUMN api_interface_change_log.change_version IS '该次保存后的版本号';
COMMENT ON COLUMN api_interface_change_log.action IS '变更动作：create/update/copy/import/status';
COMMENT ON COLUMN api_interface_change_log.summary IS '变更摘要';
COMMENT ON COLUMN api_interface_change_log.operator_id IS '操作人';

COMMENT ON TABLE api_import_record IS '导入记录表';
COMMENT ON COLUMN api_import_record.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_import_record.import_type IS '导入类型：url_swagger/curl';
COMMENT ON COLUMN api_import_record.source_name IS '导入源名称';
COMMENT ON COLUMN api_import_record.status IS '导入状态';
COMMENT ON COLUMN api_import_record.summary IS '导入汇总：{created, updated, failed, skipped}';
COMMENT ON COLUMN api_import_record.error_details IS '错误详情';
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
COMMENT ON COLUMN api_scene.priority IS '优先级：P0/P1/P2/P3，NULL 表示未设置';
COMMENT ON COLUMN api_scene.variables IS '场景变量唯一权威源 JSONB：[{name, value, description}]，随场景整体读写';
COMMENT ON COLUMN api_scene.processors IS '场景级处理器列表（元素含 type 区分 pre/post）';
COMMENT ON COLUMN api_scene.steps IS '步骤聚合 JSONB：结构与前端步骤对象一致，每步含 variables 数组（合并自原 api_scene_step_variable）';
COMMENT ON COLUMN api_scene.change_version IS '变更版本号（乐观锁）';

COMMENT ON TABLE api_scene_follow IS '场景关注表';
COMMENT ON COLUMN api_scene_follow.scene_id IS '关联场景 ID';
COMMENT ON COLUMN api_scene_follow.user_id IS '关注用户 ID';

COMMENT ON TABLE api_execution_record IS '执行记录表';
COMMENT ON COLUMN api_execution_record.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_execution_record.scene_id IS '关联场景 ID';
COMMENT ON COLUMN api_execution_record.environment_id IS '使用的环境 ID';
COMMENT ON COLUMN api_execution_record.execution_mode IS '执行方式：platform';
COMMENT ON COLUMN api_execution_record.status IS '状态：pending/running/success/failed/error/cancelled/timeout';
COMMENT ON COLUMN api_execution_record.trigger_type IS '触发方式：manual/scheduled';
COMMENT ON COLUMN api_execution_record.source IS '报告来源：scene（场景页运行，报告不进列表）/schedule（定时任务含立即执行）';
COMMENT ON COLUMN api_execution_record.report_id IS '关联报告 ID';
COMMENT ON COLUMN api_execution_record.error_message IS '失败原因';
COMMENT ON COLUMN api_execution_record.executed_at IS '触发时间';
COMMENT ON COLUMN api_execution_record.duration_ms IS '执行耗时（毫秒）';

COMMENT ON TABLE api_report IS '报告表';
COMMENT ON COLUMN api_report.project_id IS '归属项目 ID';
COMMENT ON COLUMN api_report.execution_record_id IS '关联执行记录 ID';
COMMENT ON COLUMN api_report.report_type IS '报告类型：scene（场景报告）/suite（套件报告）';
COMMENT ON COLUMN api_report.external_id IS '外部对象 ID：场景报告=场景 ID，套件报告=任务 ID';
COMMENT ON COLUMN api_report.name IS '报告名称（场景/任务名 + 执行时间戳）';
COMMENT ON COLUMN api_report.environment_name IS '环境名称快照';
COMMENT ON COLUMN api_report.execution_mode IS '执行方式：platform';
COMMENT ON COLUMN api_report.source IS '报告来源：scene（场景页运行，不进列表）/schedule（定时任务含立即执行）';
COMMENT ON COLUMN api_report.status IS '汇总状态：success/failed/partial';
COMMENT ON COLUMN api_report.summary IS '结果汇总';
COMMENT ON COLUMN api_report.result IS '报告数据集（场景数据集或套件数据集）';
COMMENT ON COLUMN api_report.ryze_snapshot IS 'Ryze 标准 JSON 快照';
COMMENT ON COLUMN api_report.share_token IS '分享链接令牌（唯一）';
COMMENT ON COLUMN api_report.share_expires_at IS '分享链接过期时间';
COMMENT ON COLUMN api_report.share_user_id IS '分享者（最后一次生成分享链接的用户）';

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

-- 追溯矩阵
COMMENT ON TABLE trace_edge IS '追溯边表（节点间关联的结构事实）';
COMMENT ON COLUMN trace_edge.id IS '边 ID';
COMMENT ON COLUMN trace_edge.project_id IS '所属项目（隔离边界），矩阵/链路/影响查询强制过滤';
COMMENT ON COLUMN trace_edge.edge_type IS '边类型：derivation 派生边 / case_snapshot 快照引用边（用例 ⇢ 评审 / 计划）';
COMMENT ON COLUMN trace_edge.source_type IS '源节点类型：requirement/module/mindmap_document/test_case';
COMMENT ON COLUMN trace_edge.source_id IS '源节点 ID（逻辑外键，与 source_type 组合定位）';
COMMENT ON COLUMN trace_edge.target_type IS '目标节点类型：module/mindmap_document/test_case/test_review/test_plan';
COMMENT ON COLUMN trace_edge.target_id IS '目标节点 ID（逻辑外键）';
COMMENT ON COLUMN trace_edge.target_version IS '引用时目标内容的版本标识，与当前版本不一致则边转 stale';
COMMENT ON COLUMN trace_edge.status IS '边状态：ai_created/confirmed/conflict/stale/detached';
COMMENT ON COLUMN trace_edge.established_by IS '建立方式：ai/manual';
COMMENT ON COLUMN trace_edge.confirmed_by IS '最近一次人工确认/修正的操作人';
COMMENT ON COLUMN trace_edge.confirmed_at IS '最近一次人工确认/修正时间';
COMMENT ON COLUMN trace_edge.disposition IS '影响处置标记：pending/regenerate/re_review/no_impact，未纳入影响分析为空';
COMMENT ON COLUMN trace_edge.reason IS '处置理由，no_impact 必填';
COMMENT ON COLUMN trace_edge.disposed_by IS '处置操作人';

COMMENT ON TABLE trace_coverage_result IS '覆盖结论表（需求 × 用例集合的覆盖质量结论）';
COMMENT ON COLUMN trace_coverage_result.id IS '结论 ID';
COMMENT ON COLUMN trace_coverage_result.project_id IS '所属项目（隔离边界）';
COMMENT ON COLUMN trace_coverage_result.requirement_id IS '需求条目 ID（逻辑外键），每需求至多一条结论';
COMMENT ON COLUMN trace_coverage_result.coverage_status IS '覆盖结论：covered/partial/uncovered';
COMMENT ON COLUMN trace_coverage_result.evidence IS '判定依据：命中的用例集合、缺口说明、AI 理由摘要';
COMMENT ON COLUMN trace_coverage_result.analyzed_task_id IS '来源覆盖分析任务 ID（逻辑外键 → ai_task）';
COMMENT ON COLUMN trace_coverage_result.ai_analyzed_at IS 'AI 分析时间';
COMMENT ON COLUMN trace_coverage_result.reviewed_by IS '人工复核修正人；非空即人工判定优先';
COMMENT ON COLUMN trace_coverage_result.reviewed_note IS '人工修正说明';
COMMENT ON COLUMN trace_coverage_result.reviewed_at IS '人工修正时间';

-- AI 能力
COMMENT ON TABLE ai_config IS 'AI 全局配置表（单例行）';
COMMENT ON COLUMN ai_config.enabled IS 'AI 总开关';
COMMENT ON COLUMN ai_config.default_model_id IS '默认模型（逻辑外键 → ai_model_config），各能力域可覆盖';
COMMENT ON COLUMN ai_config.task_timeout_seconds IS '任务超时秒数，超时由清扫器置失败';
COMMENT ON COLUMN ai_config.task_max_retries IS '自动重试上限（调用失败短重试，与用户手动重试独立）';

COMMENT ON TABLE ai_model_config IS '模型配置表';
COMMENT ON COLUMN ai_model_config.name IS '配置名称（展示用），唯一';
COMMENT ON COLUMN ai_model_config.provider IS '供应商类别：openai/anthropic/azure/gemini/ollama/custom';
COMMENT ON COLUMN ai_model_config.base_url IS '模型端点，可为云端 API 或私有化部署';
COMMENT ON COLUMN ai_model_config.api_key_encrypted IS '密钥密文（AES 加密；接口永不回显，仅支持替换）';
COMMENT ON COLUMN ai_model_config.model_name IS '实际调用的模型标识';
COMMENT ON COLUMN ai_model_config.capabilities IS '能力标签：chat/vision/embedding';
COMMENT ON COLUMN ai_model_config.priority IS '兜底顺序（默认模型失效时按 priority 升序尝试）';
COMMENT ON COLUMN ai_model_config.enabled IS '启停开关';
COMMENT ON COLUMN ai_model_config.last_test_at IS '最近连通性测试时间';
COMMENT ON COLUMN ai_model_config.last_test_result IS '最近连通性测试结果 { success, latencyMs, msg }';

COMMENT ON TABLE ai_embedding_config IS '向量 API 配置表（单例行）';
COMMENT ON COLUMN ai_embedding_config.provider IS '供应商类别，取值同 ai_model_config.provider';
COMMENT ON COLUMN ai_embedding_config.base_url IS '向量端点（独立于生成模型，可来自不同供应商）';
COMMENT ON COLUMN ai_embedding_config.api_key_encrypted IS '密钥密文（永不回显，仅支持替换）';
COMMENT ON COLUMN ai_embedding_config.embedding_model IS '嵌入模型标识';
COMMENT ON COLUMN ai_embedding_config.dimensions IS '向量维度（决定 ai_vector_index.embedding 列维度）';
COMMENT ON COLUMN ai_embedding_config.operator IS '距离算子：cosine/l2/inner_product';
COMMENT ON COLUMN ai_embedding_config.index_type IS '索引类型：hnsw/ivfflat';
COMMENT ON COLUMN ai_embedding_config.enabled IS '启停；未启用则 RAG 与相似检测不可用';
COMMENT ON COLUMN ai_embedding_config.versions IS '历史版本记录 [{ version, embeddingModel, dimensions, operator, retiredAt }]';
COMMENT ON COLUMN ai_embedding_config.last_test_at IS '最近连通性测试时间';
COMMENT ON COLUMN ai_embedding_config.last_test_result IS '最近连通性测试结果';

COMMENT ON TABLE ai_prompt_template IS '场景提示词表';
COMMENT ON COLUMN ai_prompt_template.scene IS '场景编码（如 requirement_split），与任务 type 对应';
COMMENT ON COLUMN ai_prompt_template.name IS '场景名称（展示用）';
COMMENT ON COLUMN ai_prompt_template.content IS '模板正文，支持 {{variable}} 占位';
COMMENT ON COLUMN ai_prompt_template.variables IS '可用变量清单 [{ name, desc, required }]';
COMMENT ON COLUMN ai_prompt_template.source IS '来源：default 内置默认 / custom 自定义覆盖';
COMMENT ON COLUMN ai_prompt_template.version IS '自定义版本号，重置后归 1';

COMMENT ON TABLE ai_task IS 'AI 任务表（统一任务收口）';
COMMENT ON COLUMN ai_task.type IS '任务类型（全量枚举见 AI 助手与任务中心详设 3.6.1）';
COMMENT ON COLUMN ai_task.status IS '任务状态：pending/running/succeeded/failed/cancelled';
COMMENT ON COLUMN ai_task.progress IS '进度 0–100';
COMMENT ON COLUMN ai_task.phase IS '当前阶段（进度页阶段展示用）';
COMMENT ON COLUMN ai_task.project_id IS '项目内任务的隔离归属（NULL = 不限项目的个人任务）';
COMMENT ON COLUMN ai_task.workspace_id IS '执行作用域（助手类任务经 X-Active-Workspace 头写入），RAG 限权过滤依据';
COMMENT ON COLUMN ai_task.submitted_by IS '发起人：我的任务、完成通知与重试的归属';
COMMENT ON COLUMN ai_task.prompt_scene IS '实际使用的提示词场景，用量按场景归因';
COMMENT ON COLUMN ai_task.model_id IS '实际调用的模型，失败重试复用同模型';
COMMENT ON COLUMN ai_task.input IS '任务输入：源引用 + 参数（存引用不复制全文）';
COMMENT ON COLUMN ai_task.result IS '产物明细（单一事实源），确认/驳回只指回这里';
COMMENT ON COLUMN ai_task.tokens_in IS '任务级入向 token 汇总';
COMMENT ON COLUMN ai_task.tokens_out IS '任务级出向 token 汇总';
COMMENT ON COLUMN ai_task.error_code IS '失败业务错误码（10 位）';
COMMENT ON COLUMN ai_task.error_msg IS '失败原因摘要';
COMMENT ON COLUMN ai_task.retry_of_task_id IS '手动重试时指向原任务（重试保留原任务记录）';

COMMENT ON TABLE ai_artifact_confirm IS '产物确认记录表';
COMMENT ON COLUMN ai_artifact_confirm.project_id IS '落库目标项目（确认列表过滤），经请求头上报';
COMMENT ON COLUMN ai_artifact_confirm.task_id IS '所属任务';
COMMENT ON COLUMN ai_artifact_confirm.artifact_key IS '产物在 result 中的定位键';
COMMENT ON COLUMN ai_artifact_confirm.action IS '确认动作：adopted/adopted_edited/rejected';
COMMENT ON COLUMN ai_artifact_confirm.operator_id IS '确认操作人（审计：AI 产物必经人工）';
COMMENT ON COLUMN ai_artifact_confirm.adopted_ref IS '采纳落库后的目标实体引用，反查产物来源';
COMMENT ON COLUMN ai_artifact_confirm.note IS '驳回 / 编辑原因';

COMMENT ON TABLE ai_usage_log IS '用量明细表';
COMMENT ON COLUMN ai_usage_log.project_id IS '项目内调用的统计维度；管理端测试等全局调用为 NULL';
COMMENT ON COLUMN ai_usage_log.task_id IS '关联任务（统计 → 单次调用 → 任务详情的下钻链）；助手交互调用为 NULL';
COMMENT ON COLUMN ai_usage_log.user_id IS '调用者';
COMMENT ON COLUMN ai_usage_log.model_id IS '调用的模型，按模型分组统计维度';
COMMENT ON COLUMN ai_usage_log.prompt_scene IS '场景归因，按场景统计维度';
COMMENT ON COLUMN ai_usage_log.call_type IS '调用类型：chat/embedding（向量重建同样计用量）';
COMMENT ON COLUMN ai_usage_log.prompt_tokens IS '提示 token 消耗';
COMMENT ON COLUMN ai_usage_log.completion_tokens IS '补全 token 消耗';
COMMENT ON COLUMN ai_usage_log.total_tokens IS '总 token 消耗';
COMMENT ON COLUMN ai_usage_log.latency_ms IS '单次调用耗时';
COMMENT ON COLUMN ai_usage_log.status IS '调用结果：success/failed';
COMMENT ON COLUMN ai_usage_log.error_code IS '失败归因错误码';

COMMENT ON TABLE ai_assistant_conversation IS '助手会话表（按登录用户归属）';
COMMENT ON COLUMN ai_assistant_conversation.title IS '会话标题（首问自动生成，可重命名）';
COMMENT ON COLUMN ai_assistant_conversation.user_id IS '归属人 = 唯一隔离维度，仅本人可见可操作';
COMMENT ON COLUMN ai_assistant_conversation.status IS '会话状态：active/archived';
COMMENT ON COLUMN ai_assistant_conversation.context_snapshot IS '会话创建时的活跃上下文实体引用（不参与权限判定）';

COMMENT ON TABLE ai_assistant_message IS '助手消息表';
COMMENT ON COLUMN ai_assistant_message.conversation_id IS '所属会话（逻辑外键）';
COMMENT ON COLUMN ai_assistant_message.role IS '消息角色：user/assistant/system';
COMMENT ON COLUMN ai_assistant_message.content IS '消息正文（Markdown）；流式结束后落盘，断线重连续读';
COMMENT ON COLUMN ai_assistant_message.attachments IS '用户选中的上下文实体引用集合';
COMMENT ON COLUMN ai_assistant_message.intent IS '意图解析结构化预览：动作、目标、字段级变更、影响数量';
COMMENT ON COLUMN ai_assistant_message.citations IS '来源引用集合（只读问答的可跳转引用）';
COMMENT ON COLUMN ai_assistant_message.execution IS '执行回执：previewed → executed/rejected、逐项结果与执行人';
COMMENT ON COLUMN ai_assistant_message.status IS '消息状态：streaming/done/interrupted/error';

COMMENT ON TABLE ai_vector_index IS '向量索引表（pgvector）';
COMMENT ON COLUMN ai_vector_index.project_id IS '业务归属之一：先按项目过滤再做向量检索，禁止全库比对';
COMMENT ON COLUMN ai_vector_index.entity_type IS '业务归属之二：requirement/test_case/mindmap_node/bug/review_comment';
COMMENT ON COLUMN ai_vector_index.entity_id IS '业务实体 ID（逻辑外键，重建时定位源）';
COMMENT ON COLUMN ai_vector_index.chunk_index IS '分块序号（同实体多块向量）';
COMMENT ON COLUMN ai_vector_index.content IS '嵌入原文分块（命中后直接回显上下文）';
COMMENT ON COLUMN ai_vector_index.embedding IS '向量本体，维度取 ai_embedding_config.dimensions（初始 1536）';
COMMENT ON COLUMN ai_vector_index.embedding_version IS '生成时的「模型 + 维度 + 算子」版本标识';
COMMENT ON COLUMN ai_vector_index.indexed_at IS '最近重建时间';

-- ============================================================
-- 20. 文件管理（泛化附件资源，文件管理详设 2.1）
-- ============================================================
CREATE TABLE file_resource (
    id            UUID         PRIMARY KEY,
    object_key    VARCHAR(500) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NULL,
    file_size     BIGINT       NOT NULL,
    uploader_id   UUID         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_file_resource_uploader ON file_resource (uploader_id);
CREATE INDEX idx_file_resource_created ON file_resource (created_at);

COMMENT ON TABLE file_resource IS '泛化附件资源：不挂工作空间/项目，使用方以 ID 或访问 URL 关联（文件管理详设 2.1）';
COMMENT ON COLUMN file_resource.object_key IS 'MinIO 对象键（服务端生成 objects/{uuid}{ext}，不含用户可控路径）';
COMMENT ON COLUMN file_resource.uploader_id IS '上传者（sys_user.id，逻辑外键）';
