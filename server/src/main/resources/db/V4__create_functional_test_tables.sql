-- 功能测试模块 DDL

-- ==================== 测试用例管理 ====================

-- 测试用例模块表（目录/文档树节点）
CREATE TABLE test_case_module (
    id          UUID         PRIMARY KEY,
    project_id  UUID         NOT NULL,
    parent_id   UUID         NULL,
    type        VARCHAR(20)  NOT NULL, -- directory / document
    name        VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_test_case_module_project_id ON test_case_module (project_id);
CREATE INDEX idx_test_case_module_parent_id ON test_case_module (parent_id);

-- 测试用例节点表（脑图节点）
CREATE TABLE test_case_node (
    id           UUID         PRIMARY KEY,
    document_id  UUID         NOT NULL,
    parent_id    UUID         NULL,
    type         VARCHAR(20)  NOT NULL DEFAULT 'normal', -- case / normal / precondition / step / expected
    title        VARCHAR(200) NOT NULL,
    priority     VARCHAR(2)   NULL,     -- P0 / P1 / P2 / P3（仅 case 节点）
    sort_order   INT          NOT NULL DEFAULT 0,
    version      INT          NOT NULL DEFAULT 1,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_test_case_node_document_id ON test_case_node (document_id);
CREATE INDEX idx_test_case_node_parent_id ON test_case_node (parent_id);

-- 文档布局表
CREATE TABLE test_case_document_layout (
    id           UUID      PRIMARY KEY,
    document_id  UUID      NOT NULL,
    layout_json  JSONB     NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_test_case_document_layout_doc ON test_case_document_layout (document_id);

-- ==================== 测试计划 ====================

-- 测试计划表
CREATE TABLE test_plan (
    id           UUID         PRIMARY KEY,
    project_id   UUID         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  TEXT         NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'new', -- new / in_progress / completed / closed
    executor_id  UUID         NULL,
    start_time   TIMESTAMP    NULL,
    end_time     TIMESTAMP    NULL,
    environment  VARCHAR(200) NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_test_plan_project_id ON test_plan (project_id);
CREATE INDEX idx_test_plan_status ON test_plan (status);

-- 计划模块快照表
CREATE TABLE test_plan_module_snapshot (
    id                  UUID         PRIMARY KEY,
    plan_id             UUID         NOT NULL,
    original_module_id  UUID         NULL,
    parent_id           UUID         NULL,
    name                VARCHAR(100) NOT NULL,
    type                VARCHAR(20)  NOT NULL, -- directory / document
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
    type                   VARCHAR(20)  NOT NULL, -- case / normal / precondition / step / expected
    priority               VARCHAR(2)   NULL,
    is_associated          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    last_result            VARCHAR(20)  DEFAULT 'untested', -- pass / fail / block / untested
    last_executor_id       UUID         NULL,
    last_executed_at       TIMESTAMP    NULL,
    sort_order             INT          NOT NULL DEFAULT 0,
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
    result            VARCHAR(20) NOT NULL, -- pass / fail / block / untested
    note              TEXT      NULL,
    executed_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_test_plan_execution_record_plan_id ON test_plan_execution_record (plan_id);
CREATE INDEX idx_test_plan_execution_record_snapshot_node_id ON test_plan_execution_record (snapshot_node_id);

-- ==================== 测试评审 ====================

-- 测试评审表
CREATE TABLE test_review (
    id              UUID         PRIMARY KEY,
    project_id      UUID         NOT NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    initiator_id    UUID         NOT NULL,
    participant_ids JSONB        NOT NULL DEFAULT '[]',
    status          VARCHAR(20)  NOT NULL DEFAULT 'in_progress', -- in_progress / completed
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_test_review_project_id ON test_review (project_id);
CREATE INDEX idx_test_review_status ON test_review (status);

-- 评审模块快照表
CREATE TABLE test_review_module_snapshot (
    id                  UUID         PRIMARY KEY,
    review_id           UUID         NOT NULL,
    original_module_id  UUID         NULL,
    parent_id           UUID         NULL,
    name                VARCHAR(100) NOT NULL,
    type                VARCHAR(20)  NOT NULL, -- directory / document
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
    type                   VARCHAR(20)  NOT NULL, -- case / normal / precondition / step / expected
    priority               VARCHAR(2)   NULL,
    is_associated          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    last_mark              VARCHAR(10)  NULL,     -- pass / fail
    last_reviewer_id       UUID         NULL,
    last_reviewed_at       TIMESTAMP    NULL,
    sort_order             INT          NOT NULL DEFAULT 0,
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
    operation_type    VARCHAR(20)  NOT NULL, -- mark / comment
    mark              VARCHAR(10)  NULL,     -- pass / fail
    comment           TEXT         NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_test_review_record_review_id ON test_review_record (review_id);
CREATE INDEX idx_test_review_record_snapshot_node_id ON test_review_record (snapshot_node_id);

-- ==================== 缺陷管理 ====================

-- 缺陷表
CREATE TABLE bug (
    id               UUID         PRIMARY KEY,
    project_id       UUID         NOT NULL,
    title            VARCHAR(300) NOT NULL,
    severity         VARCHAR(20)  NOT NULL, -- fatal / serious / general / minor
    priority         VARCHAR(20)  NOT NULL, -- high / medium / low
    status           VARCHAR(20)  NOT NULL DEFAULT 'new', -- new / assigned / fixing / fixed / verified / closed
    description      TEXT         NULL,
    reporter_id      UUID         NOT NULL,
    assignee_id      UUID         NULL,
    related_case_id  UUID         NULL,
    related_plan_id  UUID         NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_bug_project_id ON bug (project_id);
CREATE INDEX idx_bug_status ON bug (status);
CREATE INDEX idx_bug_assignee_id ON bug (assignee_id);

-- 缺陷日志表
CREATE TABLE bug_log (
    id             UUID         PRIMARY KEY,
    bug_id         UUID         NOT NULL,
    operator_id    UUID         NOT NULL,
    operation_type VARCHAR(50)  NOT NULL,
    content        TEXT         NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_bug_log_bug_id ON bug_log (bug_id);
