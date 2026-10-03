-- =============================================================================
-- V20261003150100__trace_matrix.sql — 追溯矩阵数据层迁移
-- 依据：docs/04-detailed-design/05-trace-matrix.md（2.2、2.3、6.1）
--
-- 1. 变更目的与影响范围
--    为追溯矩阵建立 trace_edge（追溯边）与 trace_coverage_result（覆盖结论）两表及索引；
--    新增权限点 trace:view / trace:edit（scope = workspace）并授权至两个 workspace 预置角色。
--    影响：纯新增，不修改或删除既有对象，对存量接口无破坏。
-- 2. 正向 DDL 与数据回填
--    本文件仅含 DDL 与种子变更，无业务数据回填。
-- 3. 回滚方案
--    DROP TABLE trace_coverage_result / trace_edge（连同其索引）；删除权限行
--    id = c0000000-0000-0000-0000-000000000076 / 0077 / 0078；
--    角色 permissions 中剔除 "trace" / "trace:view" / "trace:edit" 三码。
-- 4. 兼容窗口与部署顺序
--    仅在缺少新表的存量库执行一次；先执行本脚本，再部署引用新表的服务端版本。
--    全新建库不执行本文件，直接使用 server/src/main/resources/db/schema.sql 全量基线。
-- 5. 大表锁影响评估
--    全部为新建表，无锁；权限 / 角色种子为小表行锁，瞬时完成。
-- 6. 索引 / 逻辑删除 / 租户隔离检查
--    每表含 id / created_at / updated_at / is_deleted（C5）；单表索引 4 / 1 均 ≤ 5（C9）；
--    隔离边界为 project_id，矩阵 / 链路 / 覆盖查询强制过滤。
--
-- 本地执行：docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

-- ---------- 1. 新表（DDL 见详设 2.2 / 2.3） ----------

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

-- ---------- 2. 权限点（详设 6.1） ----------

INSERT INTO sys_permission (id, code, name, parent_code, module, top_module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000076', 'trace',      '追溯矩阵',     NULL,    '追溯矩阵', '追溯矩阵', 'workspace', 19, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000077', 'trace:view', '查看追溯矩阵', 'trace', '追溯矩阵', '追溯矩阵', 'workspace', 1,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000078', 'trace:edit', '编辑追溯',     'trace', '追溯矩阵', '追溯矩阵', 'workspace', 2,  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ---------- 3. 预置角色授权（补 trace / trace:view / trace:edit） ----------

UPDATE sys_role
SET permissions = permissions || COALESCE((
        SELECT jsonb_agg(e)
        FROM jsonb_array_elements_text('["trace","trace:view","trace:edit"]'::jsonb) AS e
        WHERE NOT (sys_role.permissions @> jsonb_build_array(e))
    ), '[]'::jsonb),
    updated_at = CURRENT_TIMESTAMP
WHERE id IN ('c0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002');

COMMIT;
