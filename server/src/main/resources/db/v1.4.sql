-- Robotest V1.4 数据库增量 DDL
-- PostgreSQL
-- GitLab 集成核心表（schema.sql 中定义但未进入迁移链）
-- 执行顺序：v1.sql → v1.1.sql → v1.2.sql → v1.3.sql → v1.4.sql

-- ============================================================
-- 1. GitLab 仓库配置表
-- ============================================================

CREATE TABLE IF NOT EXISTS api_gitlab_repository (
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

CREATE INDEX IF NOT EXISTS idx_glab_repository_project
    ON api_gitlab_repository(project_id) WHERE is_deleted = FALSE;

-- ============================================================
-- 2. GitLab 测试类元数据表
-- ============================================================

CREATE TABLE IF NOT EXISTS api_gitlab_test_class_metadata (
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

CREATE INDEX IF NOT EXISTS idx_gmeta_repository
    ON api_gitlab_test_class_metadata(repository_id) WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_gmeta_class_name
    ON api_gitlab_test_class_metadata(repository_id, full_class_name) WHERE is_deleted = FALSE;

-- ============================================================
-- 3. GitLab 测试范围变量定义表
-- ============================================================

CREATE TABLE IF NOT EXISTS api_gitlab_test_scope (
    id                   UUID         PRIMARY KEY,
    repository_id        UUID         NOT NULL,
    variable_name        VARCHAR(100) NOT NULL,
    scope_type           VARCHAR(20)  NOT NULL,
    description          VARCHAR(500) NULL,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gscope_repository
    ON api_gitlab_test_scope(repository_id) WHERE is_deleted = FALSE;

-- ============================================================
-- 4. GitLab 同步历史表
-- ============================================================

CREATE TABLE IF NOT EXISTS api_gitlab_sync_history (
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

CREATE INDEX IF NOT EXISTS idx_gsync_repository
    ON api_gitlab_sync_history(repository_id, sync_at DESC) WHERE is_deleted = FALSE;
