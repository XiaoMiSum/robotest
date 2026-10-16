-- ============================================================
-- Robotest 追溯矩阵演示种子数据（幂等，可重复执行）
-- 目标库：docker pgvector 容器中的 robotest 库
-- 前置：依赖 seed-selftest.sql 的示例项目与其中模块/用例/评审/计划节点（按固定 UUID 引用）
-- 被演示对象：需求 REQ-001（业务功能模块，01a13e4b-…-f935）的追溯矩阵
--
-- 使用：
--   docker cp server/src/main/resources/db/seed-trace.sql pgvector:/tmp/
--   docker exec pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f /tmp/seed-trace.sql
--
-- UUID 约定：本脚本 id 均以 `7ac3` 开头（区别于 seed-selftest 的 7e57 段、seed-ai 的 a1f0 段），
--           清理段用 `id::text LIKE '7ac3%'` 精确圈定。
--
--   7ac30001 trace_edge   7ac30002 trace_coverage_result
--
-- 数据设计（详设 docs/04-detailed-design/05-trace-matrix.md 2.4 / 4.3）：
--   覆盖边状态机全分支（ai_created / confirmed / conflict / stale / detached）、
--   影响处置字段组全取值（pending / regenerate / re_review / no_impact）、
--   覆盖结论 partial（reviewed_* 留空，供人工修正优先后端验收）。
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- ============================================================
-- 0. 清理旧种子
-- ============================================================
DELETE FROM trace_coverage_result WHERE id::text LIKE '7ac3%';
DELETE FROM trace_edge             WHERE id::text LIKE '7ac3%';

-- ============================================================
-- 1. 追溯边（详设 2.2）
--    派生边：需求 REQ-001 → 模块 / 用例文档 / 测试用例
--    状态机与处置标记按行注释区分，用于矩阵高亮与受影响项列表验收
-- ============================================================
INSERT INTO trace_edge
(id, project_id, edge_type, source_type, source_id, target_type, target_id, target_version,
 status, established_by, confirmed_by, confirmed_at, disposition, reason, disposed_by,
 created_at, updated_at, is_deleted)
VALUES
-- —— 派生边：需求 → 模块 ——
('7ac30001-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','module','7e570002-0000-4000-8000-000000000002',
 NULL,'confirmed','manual','7e57001f-0000-4000-8000-000000000001',now() - interval '6 days',
 NULL,NULL,NULL, now() - interval '6 days', now(), false),

('7ac30001-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','module','7e570002-0000-4000-8000-000000000001',
 NULL,'ai_created','ai',NULL,NULL,
 NULL,NULL,NULL, now() - interval '5 days', now(), false),

-- —— 派生边：需求 → 用例文档 ——
('7ac30001-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','mindmap_document','7e570003-0000-4000-8000-000000000001',
 NULL,'confirmed','ai','7e57001f-0000-4000-8000-000000000001',now() - interval '5 days',
 NULL,NULL,NULL, now() - interval '5 days', now(), false),

-- —— 派生边：需求 → 测试用例 ——
-- confirmed：人工确认有效（target_version 与用例当前版本 v1 一致）
('7ac30001-0000-4000-8000-000000000004','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','test_case','7e570004-0000-4000-8000-000000000101',
 'v1','confirmed','ai','7e57001f-0000-4000-8000-000000000001',now() - interval '5 days',
 'no_impact','需求描述变更仅涉及文案措辞，不影响该用例断言','7e57001f-0000-4000-8000-000000000001',
 now() - interval '5 days', now() - interval '1 day', false),

-- ai_created + disposition=pending：影响分析已标记待处置
('7ac30001-0000-4000-8000-000000000005','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','test_case','7e570004-0000-4000-8000-000000000107',
 'v1','ai_created','ai',NULL,NULL,
 'pending',NULL,NULL, now() - interval '5 days', now() - interval '1 day', false),

-- conflict + disposition=regenerate：AI 与人工判定不一致，待重新生成建议
('7ac30001-0000-4000-8000-000000000006','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','test_case','7e570004-0000-4000-8000-00000000010d',
 'v1','conflict','ai',NULL,NULL,
 'regenerate','AI 圈选与人工评审结论不一致，需重新生成建议','7e57001f-0000-4000-8000-000000000001',
 now() - interval '5 days', now() - interval '1 day', false),

-- stale + disposition=re_review：target_version=v0 与用例当前版本 v1 不一致，矩阵高亮待重新确认
('7ac30001-0000-4000-8000-000000000007','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','test_case','7e570004-0000-4000-8000-000000000401',
 'v0','stale','ai',NULL,NULL,
 're_review','上游需求状态变更，用例需重新评审','7e57001f-0000-4000-8000-000000000001',
 now() - interval '5 days', now() - interval '1 day', false),

-- detached：人工断开留痕，不计入矩阵与统计，AI 不得自动重建
('7ac30001-0000-4000-8000-000000000008','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','test_case','7e570004-0000-4000-8000-000000000407',
 'v1','detached','ai','7e57001f-0000-4000-8000-000000000001',now() - interval '2 days',
 NULL,'AI 误建，该用例不覆盖 REQ-001',NULL,
 now() - interval '5 days', now() - interval '2 days', false),

('7ac30001-0000-4000-8000-000000000009','7e570001-0000-4000-8000-000000000001','derivation',
 'requirement','01a13e4b-643a-7b5d-b8b4-6a774825f935','test_case','7e570004-0000-4000-8000-000000000301',
 'v1','ai_created','ai',NULL,NULL,
 NULL,NULL,NULL, now() - interval '5 days', now(), false),

-- —— 快照引用边：测试用例 ⇢ 评审 / 计划（target_version 记录圈选用例时的版本，详设 4.1）——
('7ac30001-0000-4000-8000-00000000000a','7e570001-0000-4000-8000-000000000001','case_snapshot',
 'test_case','7e570004-0000-4000-8000-000000000101','test_plan','7e57000b-0000-4000-8000-000000000001',
 'v1','confirmed','ai','7e57001f-0000-4000-8000-000000000001',now() - interval '4 days',
 NULL,NULL,NULL, now() - interval '4 days', now(), false),

('7ac30001-0000-4000-8000-00000000000b','7e570001-0000-4000-8000-000000000001','case_snapshot',
 'test_case','7e570004-0000-4000-8000-000000000107','test_review','7e57000f-0000-4000-8000-000000000001',
 'v1','confirmed','ai','7e57001f-0000-4000-8000-000000000001',now() - interval '4 days',
 NULL,NULL,NULL, now() - interval '4 days', now(), false),

('7ac30001-0000-4000-8000-00000000000c','7e570001-0000-4000-8000-000000000001','case_snapshot',
 'test_case','7e570004-0000-4000-8000-00000000010d','test_review','7e57000f-0000-4000-8000-000000000001',
 'v1','confirmed','manual','7e57001f-0000-4000-8000-000000000001',now() - interval '3 days',
 NULL,NULL,NULL, now() - interval '3 days', now(), false),

-- stale：快照时用例版本 v0，用例已更新至 v1，待重新同步
('7ac30001-0000-4000-8000-00000000000d','7e570001-0000-4000-8000-000000000001','case_snapshot',
 'test_case','7e570004-0000-4000-8000-000000000401','test_plan','7e57000b-0000-4000-8000-000000000002',
 'v0','stale','ai',NULL,NULL,
 NULL,NULL,NULL, now() - interval '4 days', now(), false),

('7ac30001-0000-4000-8000-00000000000e','7e570001-0000-4000-8000-000000000001','case_snapshot',
 'test_case','7e570004-0000-4000-8000-000000000301','test_plan','7e57000b-0000-4000-8000-000000000001',
 'v1','ai_created','ai',NULL,NULL,
 NULL,NULL,NULL, now() - interval '4 days', now(), false);

-- ============================================================
-- 2. 覆盖结论（详设 2.3）：REQ-001 部分覆盖
--    reviewed_* 留空 —— AI 判定可被人工修正覆盖；evidence 结构见详设 3.7
-- ============================================================
INSERT INTO trace_coverage_result
(id, project_id, requirement_id, coverage_status, evidence, analyzed_task_id, ai_analyzed_at,
 reviewed_by, reviewed_note, reviewed_at, created_at, updated_at, is_deleted)
VALUES
('7ac30002-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '01a13e4b-643a-7b5d-b8b4-6a774825f935','partial',
 '{"coveredBy":["7e570004-0000-4000-8000-000000000101","7e570004-0000-4000-8000-000000000107",
                "7e570004-0000-4000-8000-00000000010d","7e570004-0000-4000-8000-000000000401"],
  "gaps":["缺少验证码 60 秒重发限制用例","缺少并发登录互斥校验用例"],
  "aiReason":"主流程与禁用拦截已覆盖，异常分支与并发场景缺失"}'::jsonb,
 NULL, now() - interval '2 hours',
 NULL, NULL, NULL, now() - interval '2 hours', now(), false);

COMMIT;

-- ============================================================
-- 3. 验证（只读回显，便于人工核对）
-- ============================================================
SELECT id::text, edge_type, source_type, target_type, target_version, status, disposition
FROM trace_edge WHERE id::text LIKE '7ac3%' ORDER BY id;

SELECT requirement_id::text, coverage_status, evidence FROM trace_coverage_result
WHERE id::text LIKE '7ac3%';
