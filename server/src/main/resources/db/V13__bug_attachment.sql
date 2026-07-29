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
