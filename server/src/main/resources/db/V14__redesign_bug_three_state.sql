-- 禅道式三态模型重设计：状态机 6 态 → 3 态（active/resolved/closed），
-- 引入解决方案/确认/重开机制，description 更名为 repro_steps（Markdown 存储）

-- 1. 新增列
ALTER TABLE bug ADD COLUMN bug_type            VARCHAR(30) NOT NULL DEFAULT 'code_error';
ALTER TABLE bug ADD COLUMN module_id           UUID        NULL;
ALTER TABLE bug ADD COLUMN keywords            VARCHAR(255) NULL;
ALTER TABLE bug ADD COLUMN due_date            DATE        NULL;
ALTER TABLE bug ADD COLUMN confirmed           BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE bug ADD COLUMN reopen_count        INT         NOT NULL DEFAULT 0;
ALTER TABLE bug ADD COLUMN last_reopened_at    TIMESTAMP   NULL;
ALTER TABLE bug ADD COLUMN resolution          VARCHAR(30) NULL;
ALTER TABLE bug ADD COLUMN duplicate_of_bug_id UUID        NULL;
ALTER TABLE bug ADD COLUMN resolved_by         UUID        NULL;
ALTER TABLE bug ADD COLUMN resolved_at         TIMESTAMP   NULL;
ALTER TABLE bug ADD COLUMN closed_by           UUID        NULL;
ALTER TABLE bug ADD COLUMN closed_at           TIMESTAMP   NULL;

-- 2. description 更名为 repro_steps（重现步骤，Markdown 原文）
ALTER TABLE bug RENAME COLUMN description TO repro_steps;

-- 3. 存量数据迁移（顺序敏感：先按旧状态补 confirmed，再映射状态）
UPDATE bug SET confirmed = TRUE WHERE status <> 'new';

UPDATE bug SET status = 'active' WHERE status IN ('new', 'assigned', 'fixing');

UPDATE bug SET status = 'resolved', resolution = 'fixed', resolved_at = updated_at
 WHERE status IN ('fixed', 'verified');

UPDATE bug SET resolution = 'fixed', resolved_at = updated_at, closed_at = updated_at
 WHERE status = 'closed';

-- 4. 状态默认值改为 active
ALTER TABLE bug ALTER COLUMN status SET DEFAULT 'active';

CREATE INDEX idx_bug_module_id ON bug (module_id);
