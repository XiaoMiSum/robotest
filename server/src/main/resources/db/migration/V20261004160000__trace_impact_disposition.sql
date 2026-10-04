-- =============================================================================
-- V20261004160000__trace_impact_disposition.sql — trace_edge 影响处置字段组补列
-- 依据：docs/04-detailed-design/05-trace-matrix.md（2.2、3.10、4.3）
--
-- 1. 变更目的与影响范围
--    为 trace_edge 追加影响处置字段组 disposition / reason / disposed_by 三列，
--    承接 impact_analysis 任务写入的处置标记与人工处置记录（3.10 受影响项、4.3 处置闭环）。
--    影响：仅新增可空列，不修改或删除既有列与索引，对存量行与既有接口零影响。
-- 2. 正向 DDL 与数据回填
--    三条 ALTER TABLE ADD COLUMN（可空、无默认值），无业务数据回填。
-- 3. 回滚方案
--    ALTER TABLE trace_edge DROP COLUMN disposition;
--    ALTER TABLE trace_edge DROP COLUMN reason;
--    ALTER TABLE trace_edge DROP COLUMN disposed_by;
--    处置标记随列删除一并丢弃；回滚前需确认无待处置的受影响项依赖该记录。
-- 4. 兼容窗口与部署顺序
--    仅在缺少新列的存量库执行一次；先执行本脚本，再部署引用新列的服务端版本。
--    全新建库不执行本文件，直接使用 server/src/main/resources/db/schema.sql 全量基线。
-- 5. 大表锁影响评估
--    PostgreSQL 11+ 对可空无默认值的 ADD COLUMN 只做元数据变更，不重写表、不长时间持锁。
-- 6. 索引 / 逻辑删除 / 租户隔离检查
--    未新增索引（处置过滤与列表分页走既有 idx_trace_edge_project，C9 索引数不变）；
--    三列均可空，公共四字段与 project_id 隔离边界不变（C5）。
--
-- 本地执行：docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

ALTER TABLE trace_edge ADD COLUMN disposition varchar(20) NULL;
ALTER TABLE trace_edge ADD COLUMN reason varchar(500) NULL;
ALTER TABLE trace_edge ADD COLUMN disposed_by uuid NULL;

COMMIT;
