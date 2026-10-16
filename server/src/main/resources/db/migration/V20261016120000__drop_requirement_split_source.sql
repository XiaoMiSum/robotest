-- =============================================================================
-- V20261016120000__drop_requirement_split_source.sql — 移除条目内 AI 拆分残留列
-- 依据：docs/04-detailed-design/06-requirement-management/02-requirement-management.md
--       （条目内 AI 拆分入口已下线，仅保留文档导入拆分）
--
-- 1. 变更目的与影响范围
--    删除 requirement_split_record.source_requirement_id 列及其索引
--    idx_requirement_split_source——该列仅承载条目内拆分（source_type = 'requirement'）
--    记录的原条目标识，入口下线后不再写入。影响：执行后旧代码不可回放于该列。
-- 2. 正向 DDL 与数据回填
--    无数据回填（用户确认不存在存量数据；如有临时环境存在 requirement 来源行，
--    执行前可按需归档相关需求条目，列数据无需迁移）。
-- 3. 回滚方案
--    ALTER TABLE requirement_split_record ADD COLUMN source_requirement_id uuid NULL;
--    CREATE INDEX idx_requirement_split_source ON requirement_split_record (source_requirement_id);
--    （列值已不可恢复，仅结构回退）
-- 4. 兼容窗口与部署顺序
--    先部署移除该列引用的服务端版本，再执行本脚本；或同窗口顺序执行。
-- 5. 大表锁影响评估
--    DROP COLUMN / DROP INDEX 为元数据级操作（PG11+ 不重写表），瞬时完成。
-- 6. 索引 / 逻辑删除 / 租户隔离检查
--    删除一个索引后单表索引 3 个 ≤ 5（C9）；隔离边界仍为 project_id。
--
-- 本地执行：docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

DROP INDEX IF EXISTS idx_requirement_split_source;
ALTER TABLE requirement_split_record DROP COLUMN IF EXISTS source_requirement_id;

COMMIT;
