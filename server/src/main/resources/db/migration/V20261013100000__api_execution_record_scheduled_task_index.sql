-- 背景：测试计划任务完成判据（WP-8.1）需按批次扫描最近执行的执行记录
--       （batch_id IN (...) AND created_at >= 任务上次执行时间），执行记录按任务维度
--       （scheduled_task_id）过滤时缺少可用索引，易退化为大范围扫描。
-- 变更：为 api_execution_record 新增 partial 索引 idx_exec_scheduled_task_id，
--       仅覆盖任务批量扫描路径的行（scheduled_task_id 与 batch_id 均非空）。
-- 回滚：DROP INDEX IF EXISTS idx_exec_scheduled_task_id;
-- 影响：IF NOT EXISTS 在线创建；建索引期间持 SHARE 锁（阻塞写不阻塞读），
--       建议低峰期执行。该表现有索引 5 个，本变更后为 6 个（关联字段强制索引，
--       经评审确认突破"单表原则上不超过 5 个"的原则上限）。
CREATE INDEX IF NOT EXISTS idx_exec_scheduled_task_id
    ON api_execution_record (scheduled_task_id)
    WHERE scheduled_task_id IS NOT NULL AND batch_id IS NOT NULL;
