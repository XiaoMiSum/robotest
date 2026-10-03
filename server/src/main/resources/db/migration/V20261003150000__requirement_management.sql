-- =============================================================================
-- V20261003150000__requirement_management.sql — 需求管理数据层迁移
-- 依据：docs/04-detailed-design/06-requirement-management/02-requirement-management.md（2.2–2.4、6.1）
--
-- 1. 变更目的与影响范围
--    为需求管理模块建立 requirement / requirement_change_log / requirement_split_record 三表及索引；
--    权限点由「需求池」两点升级为 requirement:view / create / edit / confirm 四点（scope = workspace）；
--    删除被新模型取代的需求池存量表 requirement_pool_item / requirement_document_rel。
--    影响：执行后至需求管理接口对齐（P2 WP-2.1/2.2）交付前，引用旧表的存量接口不可用
--    （feature 分支内部已知断窗，用户已确认删除旧表）。
-- 2. 正向 DDL 与数据回填
--    本文件仅含 DDL 与种子变更，无业务数据回填；旧表数据不迁移，执行前按 runbook §9 备份。
-- 3. 回滚方案
--    从执行前备份恢复 requirement_pool_item / requirement_document_rel 两表数据、被改的两条权限行
--    与两条角色行；新三表可直接 DROP TABLE requirement_split_record / requirement_change_log / requirement
--    （连同其索引）回退。
-- 4. 兼容窗口与部署顺序
--    仅在缺少新表的存量库执行一次；先执行本脚本，再部署引用新表的服务端版本。
--    全新建库不执行本文件，直接使用 server/src/main/resources/db/schema.sql 全量基线。
-- 5. 大表锁影响评估
--    全部为新建表，无锁；权限 / 角色为小表行锁；DROP 旧表为元数据锁（数据已备份，瞬时完成）。
-- 6. 索引 / 逻辑删除 / 租户隔离检查
--    每表含 id / created_at / updated_at / is_deleted（C5）；单表索引 4 / 1 / 3 均 ≤ 5（C9）；
--    隔离边界为 project_id，查询强制过滤。
--
-- 本地执行：docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

-- ---------- 1. 新表（DDL 见详设 2.2–2.4） ----------

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

-- ---------- 2. 权限点升级（详设 6.1） ----------

UPDATE sys_permission
SET name = '需求管理', module = '需求管理', top_module = '需求管理', updated_at = CURRENT_TIMESTAMP
WHERE id = 'c0000000-0000-0000-0000-000000000034' AND code = 'requirement';

UPDATE sys_permission
SET name = '查看需求', module = '需求管理', top_module = '需求管理', updated_at = CURRENT_TIMESTAMP
WHERE id = 'c0000000-0000-0000-0000-000000000035' AND code = 'requirement:view';

UPDATE sys_permission
SET name = '编辑需求', module = '需求管理', top_module = '需求管理', sort_order = 3, updated_at = CURRENT_TIMESTAMP
WHERE id = 'c0000000-0000-0000-0000-000000000036' AND code = 'requirement:edit';

INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted)
VALUES
('c0000000-0000-0000-0000-000000000074', 'requirement:create', '新建与导入需求', 'requirement', '需求管理', '需求管理', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000075', 'requirement:confirm', '确认与归档需求', 'requirement', '需求管理', '需求管理', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ---------- 3. 预置角色授权（补 requirement:create / requirement:confirm） ----------

UPDATE sys_role
SET permissions = permissions || COALESCE((
        SELECT jsonb_agg(e)
        FROM jsonb_array_elements_text('["requirement:create","requirement:confirm"]'::jsonb) AS e
        WHERE NOT (sys_role.permissions @> jsonb_build_array(e))
    ), '[]'::jsonb),
    updated_at = CURRENT_TIMESTAMP
WHERE id IN ('c0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002');

-- ---------- 4. 删除被取代的需求池存量表 ----------

DROP TABLE IF EXISTS requirement_document_rel;
DROP TABLE IF EXISTS requirement_pool_item;

COMMIT;
