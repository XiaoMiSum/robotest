-- Robotest 数据库初始化 DDL
-- PostgreSQL
-- 合并自 V1~V15，反映建库时的最终表结构
-- 注意：本文件用于首次初始化空库，不要在生产已迁移的库上运行

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
    status                  VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active / disabled
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
    full_access  BOOLEAN      NOT NULL DEFAULT FALSE,
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
CREATE TABLE audit_log (
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

CREATE INDEX idx_audit_log_operator ON audit_log (operator_id);
CREATE INDEX idx_audit_log_entity   ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_created  ON audit_log (created_at);

-- ============================================================
-- 2. 工作空间
-- ============================================================

-- 工作空间表
CREATE TABLE workspace (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',   -- active / dissolved
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_workspace_name ON workspace (name) WHERE is_deleted = false;
CREATE INDEX idx_workspace_created ON workspace (created_at DESC);

-- 用户-工作空间关联表
CREATE TABLE workspace_user (
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

CREATE UNIQUE INDEX uk_user_workspace ON workspace_user (user_id, workspace_id) WHERE is_deleted = false;
CREATE INDEX idx_workspace_user_workspace_id ON workspace_user (workspace_id);
CREATE INDEX idx_workspace_user_ws_role ON workspace_user (workspace_id, workspace_role);
CREATE INDEX idx_workspace_user_default_project_id ON workspace_user (default_project_id);

-- 邀请链接表
CREATE TABLE workspace_invitation (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL,
    expires_at TIMESTAMP NULL,
    max_uses INT NULL,
    use_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',   -- active / revoked
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_invitation_token ON workspace_invitation (token) WHERE is_deleted = false;
CREATE INDEX idx_invitation_ws_created ON workspace_invitation (workspace_id, created_at DESC);

-- 项目表
CREATE TABLE project (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',   -- active / archived
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    created_by UUID NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_project_workspace_name ON project (workspace_id, name) WHERE is_deleted = false;
CREATE INDEX idx_project_ws_created ON project (workspace_id, created_at DESC);
CREATE INDEX idx_project_status ON project (status);

-- ============================================================
-- 3. 功能测试 — 测试用例
-- ============================================================

-- 测试用例模块表（目录/文档树节点）
CREATE TABLE test_case_module (
    id          UUID         PRIMARY KEY,
    project_id  UUID         NOT NULL,
    parent_id   UUID         NULL,
    type        VARCHAR(20)  NOT NULL, -- directory / document
    name        VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_case_node_document_id ON test_case_node (document_id);
CREATE INDEX idx_test_case_node_parent_id ON test_case_node (parent_id);

-- 文档布局表
CREATE TABLE test_case_document_layout (
    id           UUID      PRIMARY KEY,
    document_id  UUID      NOT NULL,
    layout_json  JSONB     NOT NULL,
    is_deleted   BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_test_case_document_layout_doc ON test_case_document_layout (document_id) WHERE is_deleted = false;

-- ============================================================
-- 4. 功能测试 — 测试计划
-- ============================================================

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
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
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
    last_result            VARCHAR(20)  DEFAULT 'untested', -- pass / fail / block / untested
    last_executor_id       UUID         NULL,
    last_executed_at       TIMESTAMP    NULL,
    sort_order             INT          NOT NULL DEFAULT 0,
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
    result            VARCHAR(20) NOT NULL, -- pass / fail / block / untested
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
    status          VARCHAR(20)  NOT NULL DEFAULT 'new',          -- new / in_progress / completed
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
    last_mark              VARCHAR(10)  NULL,     -- pass / fail
    last_reviewer_id       UUID         NULL,
    last_reviewed_at       TIMESTAMP    NULL,
    sort_order             INT          NOT NULL DEFAULT 0,
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
    operation_type    VARCHAR(20)  NOT NULL, -- mark / comment
    mark              VARCHAR(10)  NULL,     -- pass / fail
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

-- 缺陷表（三态模型：active / resolved / closed + rejected）
CREATE TABLE bug (
    id                UUID         PRIMARY KEY,
    project_id        UUID         NOT NULL,
    title             VARCHAR(300) NOT NULL,
    severity          VARCHAR(20)  NOT NULL,             -- fatal / serious / general / minor
    priority          VARCHAR(20)  NOT NULL,             -- high / medium / low
    status            VARCHAR(20)  NOT NULL DEFAULT 'active', -- active / resolved / rejected / closed
    repro_steps       TEXT         NULL,                 -- 重现步骤（Markdown）
    reporter_id       UUID         NOT NULL,
    assignee_id       UUID         NULL,
    related_case_id   UUID         NULL,
    related_plan_id   UUID         NULL,
    bug_type          VARCHAR(30)  NOT NULL DEFAULT 'code_error', -- code_error / ui_improvement / design_defect / configuration / installation / security / performance / standard_spec / other
    module_id         UUID         NULL,
    keywords          VARCHAR(255) NULL,
    due_date          DATE         NULL,
    confirmed         BOOLEAN      NOT NULL DEFAULT FALSE,
    reopen_count      INT          NOT NULL DEFAULT 0,
    last_reopened_at  TIMESTAMP    NULL,
    resolution        VARCHAR(30)  NULL,                 -- fixed / by_design / duplicate / external / cannot_reproduce / deferred / wont_fix
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
-- 7. 表与列注释（PostgreSQL COMMENT ON，数据库客户端可见）
-- ============================================================

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.id IS '用户唯一标识';
COMMENT ON COLUMN sys_user.username IS '登录用户名，唯一';
COMMENT ON COLUMN sys_user.name IS '用户显示名称';
COMMENT ON COLUMN sys_user.email IS '邮箱地址，用于登录和通知';
COMMENT ON COLUMN sys_user.password_hash IS '密码 BCrypt 哈希值';
COMMENT ON COLUMN sys_user.avatar_url IS '头像 URL';
COMMENT ON COLUMN sys_user.status IS '用户状态：active=正常, disabled=禁用';
COMMENT ON COLUMN sys_user.last_active_workspace_id IS '上次登录默认进入的工作空间 ID';
COMMENT ON COLUMN sys_user.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN sys_user.created_at IS '创建时间';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间';

COMMENT ON TABLE sys_role IS '角色表（系统级 + 空间级）';
COMMENT ON COLUMN sys_role.id IS '角色唯一标识';
COMMENT ON COLUMN sys_role.name IS '角色名称，唯一';
COMMENT ON COLUMN sys_role.description IS '角色描述';
COMMENT ON COLUMN sys_role.type IS '角色类型：system=系统级, workspace=空间级';
COMMENT ON COLUMN sys_role.is_system IS '是否系统预置角色（不可删除）';
COMMENT ON COLUMN sys_role.permissions IS '权限点代码列表（JSON 数组）';
COMMENT ON COLUMN sys_role.full_access IS '是否拥有全部权限（跳过 permissions 校验）';
COMMENT ON COLUMN sys_role.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN sys_role.created_at IS '创建时间';
COMMENT ON COLUMN sys_role.updated_at IS '更新时间';

COMMENT ON TABLE sys_user_role IS '用户-角色关联表';
COMMENT ON COLUMN sys_user_role.id IS '关联唯一标识';
COMMENT ON COLUMN sys_user_role.user_id IS '用户 ID，关联 sys_user.id';
COMMENT ON COLUMN sys_user_role.role_id IS '角色 ID，关联 sys_role.id';
COMMENT ON COLUMN sys_user_role.assigned_at IS '角色分配时间';
COMMENT ON COLUMN sys_user_role.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN sys_user_role.created_at IS '创建时间';
COMMENT ON COLUMN sys_user_role.updated_at IS '更新时间';

COMMENT ON TABLE sys_permission IS '权限点表（树形结构，通过 parent_code 组织层级）';
COMMENT ON COLUMN sys_permission.id IS '权限点唯一标识';
COMMENT ON COLUMN sys_permission.code IS '权限点代码，如 user:view';
COMMENT ON COLUMN sys_permission.name IS '权限点显示名称';
COMMENT ON COLUMN sys_permission.parent_code IS '父级权限点代码，NULL=根节点';
COMMENT ON COLUMN sys_permission.module IS '所属模块';
COMMENT ON COLUMN sys_permission.scope IS '作用域：global=全局, workspace=空间级';
COMMENT ON COLUMN sys_permission.sort_order IS '同级排序序号';
COMMENT ON COLUMN sys_permission.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN sys_permission.created_at IS '创建时间';
COMMENT ON COLUMN sys_permission.updated_at IS '更新时间';

COMMENT ON TABLE audit_log IS '审计日志表（记录关键操作的字段级变更）';
COMMENT ON COLUMN audit_log.id IS '日志唯一标识（雪花算法）';
COMMENT ON COLUMN audit_log.operator_id IS '操作人 ID';
COMMENT ON COLUMN audit_log.operator_name IS '操作人名称（冗余，便于快速展示）';
COMMENT ON COLUMN audit_log.operation IS '操作类型';
COMMENT ON COLUMN audit_log.entity_type IS '操作实体类型（如 Bug/TestPlan）';
COMMENT ON COLUMN audit_log.entity_id IS '操作实体 ID';
COMMENT ON COLUMN audit_log.changes IS '字段变更详情（JSON，记录旧值/新值）';
COMMENT ON COLUMN audit_log.request_ip IS '请求 IP 地址';
COMMENT ON COLUMN audit_log.created_at IS '创建时间';
COMMENT ON COLUMN audit_log.updated_at IS '更新时间';
COMMENT ON COLUMN audit_log.is_deleted IS '逻辑删除标志';

COMMENT ON TABLE workspace IS '工作空间表（多租户隔离单元）';
COMMENT ON COLUMN workspace.id IS '工作空间唯一标识';
COMMENT ON COLUMN workspace.name IS '工作空间名称，唯一';
COMMENT ON COLUMN workspace.description IS '工作空间描述';
COMMENT ON COLUMN workspace.status IS '工作空间状态：active=正常, dissolved=已解散';
COMMENT ON COLUMN workspace.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN workspace.created_at IS '创建时间';
COMMENT ON COLUMN workspace.updated_at IS '更新时间';

COMMENT ON TABLE workspace_user IS '用户-工作空间关联表';
COMMENT ON COLUMN workspace_user.id IS '关联唯一标识';
COMMENT ON COLUMN workspace_user.user_id IS '用户 ID，关联 sys_user.id';
COMMENT ON COLUMN workspace_user.workspace_id IS '工作空间 ID，关联 workspace.id';
COMMENT ON COLUMN workspace_user.workspace_role IS '空间角色 ID，关联 sys_role.id（预置角色 UUID）';
COMMENT ON COLUMN workspace_user.default_project_id IS '空间内默认项目 ID，关联 project.id';
COMMENT ON COLUMN workspace_user.joined_at IS '加入时间';
COMMENT ON COLUMN workspace_user.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN workspace_user.created_at IS '创建时间';
COMMENT ON COLUMN workspace_user.updated_at IS '更新时间';

COMMENT ON TABLE workspace_invitation IS '邀请链接表';
COMMENT ON COLUMN workspace_invitation.id IS '邀请唯一标识';
COMMENT ON COLUMN workspace_invitation.workspace_id IS '所属工作空间 ID，关联 workspace.id';
COMMENT ON COLUMN workspace_invitation.token IS '邀请令牌，唯一';
COMMENT ON COLUMN workspace_invitation.created_by IS '创建人 ID，关联 sys_user.id';
COMMENT ON COLUMN workspace_invitation.expires_at IS '过期时间，NULL=永不过期';
COMMENT ON COLUMN workspace_invitation.max_uses IS '最大使用次数，NULL=不限';
COMMENT ON COLUMN workspace_invitation.use_count IS '已使用次数';
COMMENT ON COLUMN workspace_invitation.status IS '邀请状态：active=有效, revoked=已撤销';
COMMENT ON COLUMN workspace_invitation.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN workspace_invitation.created_at IS '创建时间';
COMMENT ON COLUMN workspace_invitation.updated_at IS '更新时间';

COMMENT ON TABLE project IS '项目表（工作空间内的业务项目）';
COMMENT ON COLUMN project.id IS '项目唯一标识';
COMMENT ON COLUMN project.workspace_id IS '所属工作空间 ID，关联 workspace.id';
COMMENT ON COLUMN project.name IS '项目名称（同一工作空间内唯一）';
COMMENT ON COLUMN project.description IS '项目描述';
COMMENT ON COLUMN project.status IS '项目状态：active=正常, archived=已归档';
COMMENT ON COLUMN project.start_time IS '项目开始时间';
COMMENT ON COLUMN project.end_time IS '项目结束时间';
COMMENT ON COLUMN project.created_by IS '创建人 ID，关联 sys_user.id';
COMMENT ON COLUMN project.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN project.created_at IS '创建时间';
COMMENT ON COLUMN project.updated_at IS '更新时间';

COMMENT ON TABLE test_case_module IS '测试用例模块表（目录/文档两级树形结构）';
COMMENT ON COLUMN test_case_module.id IS '模块唯一标识';
COMMENT ON COLUMN test_case_module.project_id IS '所属项目 ID，关联 project.id';
COMMENT ON COLUMN test_case_module.parent_id IS '父级模块 ID，NULL=根节点';
COMMENT ON COLUMN test_case_module.type IS '模块类型：directory=目录, document=文档';
COMMENT ON COLUMN test_case_module.name IS '模块名称';
COMMENT ON COLUMN test_case_module.sort_order IS '同一层级排序序号';
COMMENT ON COLUMN test_case_module.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_case_module.created_at IS '创建时间';
COMMENT ON COLUMN test_case_module.updated_at IS '更新时间';

COMMENT ON TABLE test_case_node IS '测试用例节点表（脑图节点，支持树形嵌套）';
COMMENT ON COLUMN test_case_node.id IS '节点唯一标识';
COMMENT ON COLUMN test_case_node.document_id IS '所属文档 ID，关联 test_case_module.id（type=document 的模块）';
COMMENT ON COLUMN test_case_node.parent_id IS '父级节点 ID，NULL=根节点';
COMMENT ON COLUMN test_case_node.type IS '节点类型：case=用例, normal=普通, precondition=前置条件, step=步骤, expected=预期结果';
COMMENT ON COLUMN test_case_node.title IS '节点标题';
COMMENT ON COLUMN test_case_node.priority IS '用例优先级：P0/P1/P2/P3，仅 case 节点有值';
COMMENT ON COLUMN test_case_node.sort_order IS '排序序号';
COMMENT ON COLUMN test_case_node.version IS '乐观锁版本号，用于并发冲突检测';
COMMENT ON COLUMN test_case_node.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_case_node.created_at IS '创建时间';
COMMENT ON COLUMN test_case_node.updated_at IS '更新时间';

COMMENT ON TABLE test_case_document_layout IS '文档布局表（节点自由拖拽偏移）';
COMMENT ON COLUMN test_case_document_layout.id IS '布局唯一标识';
COMMENT ON COLUMN test_case_document_layout.document_id IS '所属文档 ID，关联 test_case_module.id';
COMMENT ON COLUMN test_case_document_layout.layout_json IS '布局数据（JSON，记录各节点偏移坐标）';
COMMENT ON COLUMN test_case_document_layout.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_case_document_layout.created_at IS '创建时间';
COMMENT ON COLUMN test_case_document_layout.updated_at IS '更新时间';

COMMENT ON TABLE test_plan IS '测试计划表（用例执行计划）';
COMMENT ON COLUMN test_plan.id IS '计划唯一标识';
COMMENT ON COLUMN test_plan.project_id IS '所属项目 ID，关联 project.id';
COMMENT ON COLUMN test_plan.name IS '计划名称';
COMMENT ON COLUMN test_plan.description IS '计划描述';
COMMENT ON COLUMN test_plan.status IS '计划状态：new=新建, in_progress=进行中, completed=已完成, closed=已关闭';
COMMENT ON COLUMN test_plan.executor_id IS '执行人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_plan.start_time IS '计划开始时间';
COMMENT ON COLUMN test_plan.end_time IS '计划结束时间';
COMMENT ON COLUMN test_plan.environment IS '测试环境描述';
COMMENT ON COLUMN test_plan.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_plan.created_at IS '创建时间';
COMMENT ON COLUMN test_plan.updated_at IS '更新时间';

COMMENT ON TABLE test_plan_module_snapshot IS '计划模块快照表（创建计划时固化模块结构）';
COMMENT ON COLUMN test_plan_module_snapshot.id IS '快照唯一标识';
COMMENT ON COLUMN test_plan_module_snapshot.plan_id IS '所属计划 ID，关联 test_plan.id';
COMMENT ON COLUMN test_plan_module_snapshot.original_module_id IS '原始模块 ID（用于差量同步定位变更）';
COMMENT ON COLUMN test_plan_module_snapshot.parent_id IS '快照父级模块 ID';
COMMENT ON COLUMN test_plan_module_snapshot.name IS '模块名称（固化原值，不随源数据变化）';
COMMENT ON COLUMN test_plan_module_snapshot.type IS '模块类型：directory=目录, document=文档';
COMMENT ON COLUMN test_plan_module_snapshot.sort_order IS '排序序号';
COMMENT ON COLUMN test_plan_module_snapshot.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_plan_module_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN test_plan_module_snapshot.updated_at IS '更新时间';

COMMENT ON TABLE test_plan_node_snapshot IS '计划节点快照表（创建计划时固化节点内容）';
COMMENT ON COLUMN test_plan_node_snapshot.id IS '快照节点唯一标识';
COMMENT ON COLUMN test_plan_node_snapshot.plan_id IS '所属计划 ID，关联 test_plan.id';
COMMENT ON COLUMN test_plan_node_snapshot.original_node_id IS '原始节点 ID（用于差量同步定位变更）';
COMMENT ON COLUMN test_plan_node_snapshot.document_snapshot_id IS '所属文档快照 ID，关联 test_plan_module_snapshot.id';
COMMENT ON COLUMN test_plan_node_snapshot.parent_id IS '快照父级节点 ID';
COMMENT ON COLUMN test_plan_node_snapshot.title IS '节点标题（固化原值）';
COMMENT ON COLUMN test_plan_node_snapshot.type IS '节点类型：case/normal/precondition/step/expected（同 test_case_node.type）';
COMMENT ON COLUMN test_plan_node_snapshot.priority IS '用例优先级（固化原值）';
COMMENT ON COLUMN test_plan_node_snapshot.is_associated IS '是否被选入计划';
COMMENT ON COLUMN test_plan_node_snapshot.last_result IS '最新执行结果：pass=通过, fail=失败, block=阻塞, untested=未执行';
COMMENT ON COLUMN test_plan_node_snapshot.last_executor_id IS '最近一次执行人 ID';
COMMENT ON COLUMN test_plan_node_snapshot.last_executed_at IS '最近一次执行时间';
COMMENT ON COLUMN test_plan_node_snapshot.sort_order IS '排序序号';
COMMENT ON COLUMN test_plan_node_snapshot.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_plan_node_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN test_plan_node_snapshot.updated_at IS '更新时间';

COMMENT ON TABLE test_plan_execution_record IS '计划执行记录表（每次执行的完整历史）';
COMMENT ON COLUMN test_plan_execution_record.id IS '执行记录唯一标识';
COMMENT ON COLUMN test_plan_execution_record.plan_id IS '所属计划 ID，关联 test_plan.id';
COMMENT ON COLUMN test_plan_execution_record.snapshot_node_id IS '快照节点 ID，关联 test_plan_node_snapshot.id';
COMMENT ON COLUMN test_plan_execution_record.executor_id IS '执行人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_plan_execution_record.result IS '执行结果：pass=通过, fail=失败, block=阻塞, untested=未执行（预留）';
COMMENT ON COLUMN test_plan_execution_record.note IS '执行备注';
COMMENT ON COLUMN test_plan_execution_record.executed_at IS '执行时间';
COMMENT ON COLUMN test_plan_execution_record.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_plan_execution_record.created_at IS '创建时间';
COMMENT ON COLUMN test_plan_execution_record.updated_at IS '更新时间';

COMMENT ON TABLE test_review IS '测试评审表（用例评审流程）';
COMMENT ON COLUMN test_review.id IS '评审唯一标识';
COMMENT ON COLUMN test_review.project_id IS '所属项目 ID，关联 project.id';
COMMENT ON COLUMN test_review.title IS '评审标题';
COMMENT ON COLUMN test_review.description IS '评审描述';
COMMENT ON COLUMN test_review.initiator_id IS '发起人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_review.participant_ids IS '参与者 ID 列表（JSON 数组）';
COMMENT ON COLUMN test_review.status IS '评审状态：new=待评审, in_progress=评审中, completed=已完成';
COMMENT ON COLUMN test_review.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_review.created_at IS '创建时间';
COMMENT ON COLUMN test_review.updated_at IS '更新时间';

COMMENT ON TABLE test_review_module_snapshot IS '评审模块快照表（创建评审时固化模块结构）';
COMMENT ON COLUMN test_review_module_snapshot.id IS '快照唯一标识';
COMMENT ON COLUMN test_review_module_snapshot.review_id IS '所属评审 ID，关联 test_review.id';
COMMENT ON COLUMN test_review_module_snapshot.original_module_id IS '原始模块 ID（用于差量同步定位变更）';
COMMENT ON COLUMN test_review_module_snapshot.parent_id IS '快照父级模块 ID';
COMMENT ON COLUMN test_review_module_snapshot.name IS '模块名称（固化原值）';
COMMENT ON COLUMN test_review_module_snapshot.type IS '模块类型：directory=目录, document=文档';
COMMENT ON COLUMN test_review_module_snapshot.sort_order IS '排序序号';
COMMENT ON COLUMN test_review_module_snapshot.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_review_module_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN test_review_module_snapshot.updated_at IS '更新时间';

COMMENT ON TABLE test_review_node_snapshot IS '评审节点快照表（创建评审时固化节点内容）';
COMMENT ON COLUMN test_review_node_snapshot.id IS '快照节点唯一标识';
COMMENT ON COLUMN test_review_node_snapshot.review_id IS '所属评审 ID，关联 test_review.id';
COMMENT ON COLUMN test_review_node_snapshot.original_node_id IS '原始节点 ID（用于差量同步定位变更）';
COMMENT ON COLUMN test_review_node_snapshot.document_snapshot_id IS '所属文档快照 ID，关联 test_review_module_snapshot.id';
COMMENT ON COLUMN test_review_node_snapshot.parent_id IS '快照父级节点 ID';
COMMENT ON COLUMN test_review_node_snapshot.title IS '节点标题（固化原值）';
COMMENT ON COLUMN test_review_node_snapshot.type IS '节点类型：case/normal/precondition/step/expected（同 test_case_node.type）';
COMMENT ON COLUMN test_review_node_snapshot.priority IS '用例优先级（固化原值）';
COMMENT ON COLUMN test_review_node_snapshot.is_associated IS '是否被选入评审';
COMMENT ON COLUMN test_review_node_snapshot.last_mark IS '最新评审标记：pass=通过, fail=不通过, NULL=待评审';
COMMENT ON COLUMN test_review_node_snapshot.last_reviewer_id IS '最近一次评审人 ID';
COMMENT ON COLUMN test_review_node_snapshot.last_reviewed_at IS '最近一次评审时间';
COMMENT ON COLUMN test_review_node_snapshot.sort_order IS '排序序号';
COMMENT ON COLUMN test_review_node_snapshot.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_review_node_snapshot.created_at IS '创建时间';
COMMENT ON COLUMN test_review_node_snapshot.updated_at IS '更新时间';

COMMENT ON TABLE test_review_record IS '评审记录表（每次评审操作的完整历史）';
COMMENT ON COLUMN test_review_record.id IS '评审记录唯一标识';
COMMENT ON COLUMN test_review_record.review_id IS '所属评审 ID，关联 test_review.id';
COMMENT ON COLUMN test_review_record.snapshot_node_id IS '快照节点 ID，关联 test_review_node_snapshot.id';
COMMENT ON COLUMN test_review_record.reviewer_id IS '评审人 ID，关联 sys_user.id';
COMMENT ON COLUMN test_review_record.operation_type IS '操作类型：mark=标记, comment=评论';
COMMENT ON COLUMN test_review_record.mark IS '评审标记：pass=通过, fail=不通过, NULL=仅评论无标记';
COMMENT ON COLUMN test_review_record.comment IS '评论内容';
COMMENT ON COLUMN test_review_record.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_review_record.created_at IS '创建时间';
COMMENT ON COLUMN test_review_record.updated_at IS '更新时间';

COMMENT ON TABLE bug IS '缺陷表（三态模型：active ↔ resolved/closed，可重开）';
COMMENT ON COLUMN bug.id IS '缺陷唯一标识';
COMMENT ON COLUMN bug.project_id IS '所属项目 ID，关联 project.id';
COMMENT ON COLUMN bug.title IS '缺陷标题';
COMMENT ON COLUMN bug.severity IS '严重等级：fatal=致命, serious=严重, general=一般, minor=轻微';
COMMENT ON COLUMN bug.priority IS '优先级：high=高, medium=中, low=低';
COMMENT ON COLUMN bug.status IS '缺陷状态：active=激活, resolved=已解决, rejected=已拒绝, closed=已关闭';
COMMENT ON COLUMN bug.repro_steps IS '重现步骤（Markdown 格式）';
COMMENT ON COLUMN bug.reporter_id IS '报告人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug.assignee_id IS '处理人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug.related_case_id IS '关联用例节点 ID';
COMMENT ON COLUMN bug.related_plan_id IS '关联计划 ID';
COMMENT ON COLUMN bug.bug_type IS '缺陷类型：code_error/ui_improvement/design_defect/configuration/installation/security/performance/standard_spec/other';
COMMENT ON COLUMN bug.module_id IS '所属模块 ID，关联 test_case_module.id';
COMMENT ON COLUMN bug.keywords IS '搜索关键词（逗号分隔）';
COMMENT ON COLUMN bug.due_date IS '期望解决日期';
COMMENT ON COLUMN bug.confirmed IS '是否已确认（开发确认是缺陷）';
COMMENT ON COLUMN bug.reopen_count IS '重开次数';
COMMENT ON COLUMN bug.last_reopened_at IS '最近一次重开时间';
COMMENT ON COLUMN bug.resolution IS '解决方案：fixed=已修复, by_design=设计如此, duplicate=重复提交, external=外部原因, cannot_reproduce=无法重现, deferred=延期处理, wont_fix=不予修复';
COMMENT ON COLUMN bug.duplicate_of_bug_id IS '重复的原始缺陷 ID，关联 bug.id';
COMMENT ON COLUMN bug.resolved_by IS '解决人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug.resolved_at IS '解决时间';
COMMENT ON COLUMN bug.rejected_by IS '拒绝人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug.closed_by IS '关闭人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug.closed_at IS '关闭时间';
COMMENT ON COLUMN bug.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN bug.created_at IS '创建时间';
COMMENT ON COLUMN bug.updated_at IS '更新时间';

COMMENT ON TABLE bug_log IS '缺陷操作日志表';
COMMENT ON COLUMN bug_log.id IS '日志唯一标识';
COMMENT ON COLUMN bug_log.bug_id IS '缺陷 ID，关联 bug.id';
COMMENT ON COLUMN bug_log.operator_id IS '操作人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug_log.operation_type IS '操作类型（create/assign/resolve/reject/close/reopen 等）';
COMMENT ON COLUMN bug_log.content IS '操作内容详情';
COMMENT ON COLUMN bug_log.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN bug_log.created_at IS '创建时间';
COMMENT ON COLUMN bug_log.updated_at IS '更新时间';

COMMENT ON TABLE bug_attachment IS '缺陷附件表';
COMMENT ON COLUMN bug_attachment.id IS '附件唯一标识';
COMMENT ON COLUMN bug_attachment.bug_id IS '缺陷 ID，关联 bug.id';
COMMENT ON COLUMN bug_attachment.file_name IS '文件名';
COMMENT ON COLUMN bug_attachment.storage_path IS '存储路径';
COMMENT ON COLUMN bug_attachment.file_size IS '文件大小（字节）';
COMMENT ON COLUMN bug_attachment.content_type IS 'MIME 类型';
COMMENT ON COLUMN bug_attachment.uploader_id IS '上传人 ID，关联 sys_user.id';
COMMENT ON COLUMN bug_attachment.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN bug_attachment.created_at IS '创建时间';
COMMENT ON COLUMN bug_attachment.updated_at IS '更新时间';

-- ============================================================
-- 8. 种子数据
-- ============================================================

-- 7.1 权限点（系统管理模块）
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

-- 7.2 权限点（业务模块 — 工作空间/项目/测试用例/评审/计划/缺陷）
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
('c0000000-0000-0000-0000-000000000010', 'project',            '项目',                NULL,           '项目',    'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000011', 'project:view',       '查看项目',            'project',      '项目',    'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000012', 'project:create',     '创建项目',            'project',      '项目',    'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000013', 'project:edit',       '编辑项目',            'project',      '项目',    'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000014', 'project:delete',     '删除项目',            'project',      '项目',    'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000015', 'project:archive',    '归档/启封项目',       'project',      '项目',    'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000016', 'project:set-default','设为默认项目',        'project',      '项目',    'workspace', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000017', 'case',               '测试用例',            NULL,           '测试用例', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000018', 'case:view',          '查看用例',            'case',         '测试用例', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000019', 'case:edit',          '编辑用例',            'case',         '测试用例', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000020', 'review',             '测试评审',            NULL,           '测试评审', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000021', 'review:view',        '查看评审',            'review',       '测试评审', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000022', 'review:create',      '发起评审',            'review',       '测试评审', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000023', 'review:edit',        '评审操作',            'review',       '测试评审', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000024', 'review:complete',    '完成评审',            'review',       '测试评审', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000025', 'plan',               '测试计划',            NULL,           '测试计划', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000026', 'plan:view',          '查看计划',            'plan',         '测试计划', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000027', 'plan:create',        '创建计划',            'plan',         '测试计划', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000028', 'plan:execute',       '执行计划',            'plan',         '测试计划', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000029', 'plan:close',         '关闭计划',            'plan',         '测试计划', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000030', 'bug',                '缺陷',                NULL,           '缺陷',    'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000031', 'bug:view',           '查看缺陷',            'bug',          '缺陷',    'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000032', 'bug:create',         '提交缺陷',            'bug',          '缺陷',    'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000033', 'bug:edit',           '编辑缺陷',            'bug',          '缺陷',    'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- 7.3 预置角色
INSERT INTO sys_role (id, name, description, type, is_system, permissions, full_access, created_at, updated_at, is_deleted) VALUES
-- 系统管理员：拥有系统管理所有权限
('b0000000-0000-0000-0000-000000000001', '系统管理员',
 '拥有系统管理所有权限', 'system', TRUE,
 '["user","user:view","user:create","user:edit","user:disable","user:reset-password","workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","role","role:view","role:create","role:edit","role:delete"]',
 FALSE,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 空间管理系统角色：拥有工作空间管理所有权限（跨空间管理）
('b0000000-0000-0000-0000-000000000002', '空间管理员',
 '拥有工作空间管理所有权限，可创建/删除/管理所有工作空间', 'system', TRUE,
 '["workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","ws-info","ws-info:view","ws-info:edit","ws-member","ws-member:view","ws-member:manage","ws-invitation","ws-invitation:view","ws-invitation:manage"]',
 FALSE,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- workspace 管理员：空间内全部业务权限（full_access）
('c0000000-0000-0000-0000-000000000001', '管理员',
 '空间管理员 — 拥有工作空间内全部业务权限', 'workspace', TRUE,
 '[]',
 TRUE,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- workspace 普通成员：默认角色
('c0000000-0000-0000-0000-000000000002', '成员',
 '空间成员 — 除删除/归档项目、管理成员、编辑空间信息外的其他权限', 'workspace', TRUE,
 '["ws-info:view","ws-member:view","ws-invitation:view","ws-invitation:manage","project:view","project:create","project:edit","project:set-default","case:view","case:edit","review:view","review:create","review:edit","review:complete","plan:view","plan:create","plan:execute","plan:close","bug:view","bug:create","bug:edit"]',
 FALSE,
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);
