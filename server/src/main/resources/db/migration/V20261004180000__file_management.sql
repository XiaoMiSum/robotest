-- 文件管理模块（文件管理详设 2.1–2.3，批次二 WP-2.0）：
--   1) 新增泛化附件资源表 file_resource（不挂工作空间 / 项目，使用方以 ID 或访问 URL 关联）；
--   2) bug_attachment 补 file_resource_id 逻辑外键与索引，storage_path 放宽可空（历史列不再写入新行）；
--   3) 系统管理新增文件管理权限点，并为系统管理员角色追加授权。
-- 执行顺序：先执行本脚本，后发布应用（新列可空，反序会导致新代码读列失败）。
-- 回滚方案：应用回退上一版本即可；本脚本无需回滚（新增表与可空列对旧代码无副作用）。
-- 部署顺序与锁影响：小表 DDL 秒级完成、无长事务风险；存量本地附件由应用启动幂等回填
-- （FileResourceBackfillRunner，MinIO 不可用时跳过、下次启动重试），回填期本地文件不删除。
-- 租户隔离：泛化资源本体无租户字段（设计取舍，详设 5.1），管理页为 global 权限点。

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

ALTER TABLE bug_attachment ADD COLUMN file_resource_id UUID NULL;
ALTER TABLE bug_attachment ALTER COLUMN storage_path DROP NOT NULL;
CREATE INDEX idx_bug_attachment_file_resource ON bug_attachment (file_resource_id);

COMMENT ON COLUMN bug_attachment.storage_path IS '历史列（本地磁盘相对路径）：回填前兼容读取用，新行不再写入（文件管理详设 2.2）';
COMMENT ON COLUMN bug_attachment.file_resource_id IS '关联 file_resource.id（文件管理详设 2.2）；存量行由应用启动回填，NULL 期间兼容本地 storage_path';

INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000031', 'file',        '文件管理',   NULL,   '文件管理', '系统管理', 'global', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000032', 'file:view',   '查看文件',   'file', '文件管理', '系统管理', 'global', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000033', 'file:delete', '删除文件',   'file', '文件管理', '系统管理', 'global', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

UPDATE sys_role
SET permissions = permissions || '["file","file:view","file:delete"]'::jsonb, updated_at = CURRENT_TIMESTAMP
WHERE id = 'b0000000-0000-0000-0000-000000000001' AND NOT permissions @> '["file"]'::jsonb;
