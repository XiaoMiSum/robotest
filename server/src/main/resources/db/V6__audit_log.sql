-- V6__audit_log.sql
-- 审计日志表，记录关键写操作
CREATE TABLE IF NOT EXISTS audit_log (
    id            BIGINT       PRIMARY KEY,
    operator_id   VARCHAR(64)  NOT NULL,
    operator_name VARCHAR(64)  NOT NULL DEFAULT '',
    operation     VARCHAR(32)  NOT NULL,
    entity_type   VARCHAR(64)  NOT NULL,
    entity_id     VARCHAR(64)  NOT NULL DEFAULT '',
    changes       JSONB        NOT NULL DEFAULT '{}',
    request_ip    VARCHAR(64)  NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_log_operator ON audit_log (operator_id);
CREATE INDEX idx_audit_log_entity   ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_created  ON audit_log (created_at);
