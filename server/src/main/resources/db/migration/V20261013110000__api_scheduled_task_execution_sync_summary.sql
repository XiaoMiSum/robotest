-- 背景：「GitLab 同步」任务（WP-8.2）的执行记录须承载本次同步结果摘要
--       （新增 / 更新 / 解析失败 / 真实错误数、提交号），任务管理详设 1.7 响应
--       字段 syncSummary 依赖该列；接口同步任务的 importSummary 为冗余导入记录，
--       GitLab 同步不产生导入记录，需独立列落库。
-- 变更：为 api_scheduled_task_execution 新增 sync_summary JSONB 列，仅
--       task_type = gitlab_sync 的执行记录有值，其余类型恒 NULL。
-- 回滚：ALTER TABLE api_scheduled_task_execution DROP COLUMN IF EXISTS sync_summary;
-- 影响：ADD COLUMN 可空列不重写表、不持排他锁（PG 11+）；无新索引
--       （该表按 task_id / project_id+triggered_at 查询的既有索引不变）。
ALTER TABLE api_scheduled_task_execution
    ADD COLUMN IF NOT EXISTS sync_summary JSONB NULL;

COMMENT ON COLUMN api_scheduled_task_execution.sync_summary IS
    '同步结果摘要（仅 GitLab 同步任务）：created/updated/parseFailed/errorFailed/commit';
