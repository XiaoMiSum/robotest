-- ============================================================
-- RoboTest 演示数据种子（本地开发用，禁止用于生产）
--
-- 内容：1) docs/01-requirements 全量需求文档导入（目录/索引文件除外，33 篇）
--       2) 演示数据：空间成员 / 项目模块树 / 脑图用例 / 接口环境与接口 / 缺陷 /
--          项目动态 / 测试评审（含快照）/ 测试计划（含快照与执行记录）
--
-- 目标库：本地开发库（dev profile 默认 jdbc:postgresql://localhost:5432/robotest）
-- 前置条件：目标库已存在工作空间 01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7（示例空间）与项目 01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a（Robotest），
--           否则脚本开头的守卫会直接报错终止。
--
-- 执行方式：
--   docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 < scripts/db/seed-demo.sql
--
-- 可重复执行：所有种子行主键使用固定 UUID 前缀 5eed，重跑时先清理同前缀旧行。
-- ============================================================

BEGIN;

-- 守卫：目标工作空间/项目必须已存在
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ws_workspace WHERE id = '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7' AND is_deleted = FALSE)
        OR NOT EXISTS (SELECT 1 FROM ws_project WHERE id = '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a' AND is_deleted = FALSE) THEN
        RAISE EXCEPTION '目标工作空间或项目不存在：请先在本地初始化示例空间与 Robotest 项目'
            USING HINT = '本种子脚本依赖既有 ws_workspace/ws_project 固定 ID';
    END IF;
END $$;

-- 清理旧种子行（按 5eed 前缀，子表在前）
DELETE FROM ft_plan_execution_record WHERE id::text LIKE '5eed%';
DELETE FROM ft_plan_node_snapshot WHERE id::text LIKE '5eed%';
DELETE FROM ft_plan_module_snapshot WHERE id::text LIKE '5eed%';
DELETE FROM ft_plan WHERE id::text LIKE '5eed%';
DELETE FROM ft_review_record WHERE id::text LIKE '5eed%';
DELETE FROM ft_review_node_snapshot WHERE id::text LIKE '5eed%';
DELETE FROM ft_review_module_snapshot WHERE id::text LIKE '5eed%';
DELETE FROM ft_review WHERE id::text LIKE '5eed%';
DELETE FROM ws_project_activity WHERE id::text LIKE '5eed%';
DELETE FROM bug_log WHERE id::text LIKE '5eed%';
DELETE FROM bug WHERE id::text LIKE '5eed%';
DELETE FROM api_interface WHERE id::text LIKE '5eed%';
DELETE FROM api_environment WHERE id::text LIKE '5eed%';
DELETE FROM ft_case_node WHERE id::text LIKE '5eed%';
DELETE FROM ft_case_document WHERE id::text LIKE '5eed%';
DELETE FROM requirement_change_log WHERE id::text LIKE '5eed%';
DELETE FROM requirement WHERE id::text LIKE '5eed%';
DELETE FROM project_module WHERE id::text LIKE '5eed%';
DELETE FROM ws_user WHERE id::text LIKE '5eed%';

-- ============================================================
-- 1. 空间成员：10 个演示账号加入示例空间（2 管理员 + 8 成员）
-- ============================================================
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0001-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000002', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000001', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0002-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000001', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0003-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0004-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000005', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0005-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000006', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0006-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000007', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0007-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000008', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0008-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000009', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0009-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-00000000000a', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id, joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES ('5eed000a-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-00000000000b', '01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7', 'c0000000-0000-0000-0000-000000000002', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', CURRENT_TIMESTAMP - INTERVAL '20 days', NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');

-- ============================================================
-- 2. 项目模块树（需求 / 用例 / 接口共用目录树）
-- ============================================================
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed000b-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', NULL, '平台通用', NULL, 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed000c-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', NULL, '系统管理', NULL, 1, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed000d-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000c-0000-4000-8000-000000000000', '数据概览', NULL, 2, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed000e-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000c-0000-4000-8000-000000000000', '用户管理', NULL, 3, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed000f-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000c-0000-4000-8000-000000000000', '空间管理页', NULL, 4, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0010-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000c-0000-4000-8000-000000000000', '角色权限', NULL, 5, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0011-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000c-0000-4000-8000-000000000000', '审计日志', NULL, 6, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0012-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000c-0000-4000-8000-000000000000', '系统初始化', NULL, 7, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0013-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', NULL, '空间管理', NULL, 8, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0014-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0013-0000-4000-8000-000000000000', '我的工作空间', NULL, 9, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0015-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0013-0000-4000-8000-000000000000', '空间信息', NULL, 10, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0016-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0013-0000-4000-8000-000000000000', '成员管理', NULL, 11, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0017-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0013-0000-4000-8000-000000000000', '项目管理', NULL, 12, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0018-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0013-0000-4000-8000-000000000000', '邀请加入', NULL, 13, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0019-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', NULL, '功能测试', NULL, 14, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed001a-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0019-0000-4000-8000-000000000000', '需求管理', NULL, 15, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed001b-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0019-0000-4000-8000-000000000000', '用例脑图', NULL, 16, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed001c-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0019-0000-4000-8000-000000000000', '测试评审', NULL, 17, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed001d-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0019-0000-4000-8000-000000000000', '测试计划', NULL, 18, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed001e-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', NULL, '缺陷管理', NULL, 19, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed001f-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', NULL, '接口测试', NULL, 20, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0020-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', '快速调试', NULL, 21, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0021-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', '接口管理', NULL, 22, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0022-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', 'Mock 服务', NULL, 23, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0023-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', '测试场景', NULL, 24, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0024-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', '测试报告', NULL, 25, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0025-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', '定时任务', NULL, 26, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0026-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', '公共约定', NULL, 27, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0027-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', '项目设置', NULL, 28, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0028-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', 'GitLab 集成', NULL, 29, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0029-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', NULL, 'AI 能力', NULL, 30, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed002a-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0029-0000-4000-8000-000000000000', 'AI 生成', NULL, 31, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed002b-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0029-0000-4000-8000-000000000000', '智能助手', NULL, 32, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed002c-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0029-0000-4000-8000-000000000000', '缺陷分析', NULL, 33, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');
INSERT INTO project_module (id, project_id, parent_id, name, source_id, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed002d-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0029-0000-4000-8000-000000000000', 'AI 辅助功能', NULL, 34, FALSE, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days');

-- ============================================================
-- 3. 需求条目：docs/01-requirements 下 33 篇需求文档全量导入（不含 01-readme 等目录文件）
--    标题取文档一级标题（去「软件测试平台——」前缀），描述为文档全文 Markdown
-- ============================================================
INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed002e-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000b-0000-4000-8000-000000000000', 'V1.0', 'REQ-001', '需求规格说明书总览',
        '# 软件测试平台——需求规格说明书总览

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档定义软件测试平台的功能需求与非功能需求，面向项目经理、业务分析人员、测试工程师及开发团队，确保各方对平台的行为、规则和边界有一致理解。

### 1.2 范围

平台包含两类用户角色：**系统管理员**和**业务用户**。  

- 系统管理员拥有全局管理权限，可管理用户、工作空间和角色。  
- 业务用户可同时归属多个工作空间，通过切换活跃工作空间在不同工作空间间工作，所有业务操作限定在当前活跃工作空间内。

测试用例以 **模块树（目录/文档）** 组织，每个文档包含一个脑图形式的测试用例集合，支持多人实时协作编辑。  
测试评审和测试计划基于用例的结构化快照进行，支持完整的历史回溯和多次执行/评审记录。

业务需求以**需求条目**在平台内登记，需求条目是测试追溯链的起点。AI 能力作为平台级底座贯穿需求生成、智能助手、缺陷分析与辅助功能，详见 `docs/01-requirements/07-ai-capability/02-srs-ai-capability.md`。

### 1.3 定义与缩写

| 术语         | 定义                                         |
| ---------- | ------------------------------------------ |
| 活跃工作空间     | 业务用户登录后选择的工作空间，所有业务操作默认在该工作空间上下文中执行        |
| 模块         | 项目内测试用例的组织结构，分为**目录**和**文档**两种节点           |
| 目录         | 仅用于分类的模块节点，不包含测试用例                         |
| 文档         | 关联一个脑图的模块节点，包含若干测试用例                       |
| 脑图文档       | 以思维导图形式组织的测试用例集合，由多个节点组成                   |
| 测试用例（用例节点） | 脑图中被标记为“用例”的节点，具有步骤、预期结果等属性                |
| 快照         | 评审或计划创建时对模块树和文档节点的完整复制，用于固化当时的状态           |
| 关联节点       | 用户在创建评审或计划时显式选择的用例节点                       |
| 关联节点树      | 从快照中裁剪出的最小树形结构，包含所有关联节点、它们的祖先模块/文档节点以及后代节点 |
| 同步         | 手动将评审或计划的快照更新为当前最新模块和用例内容的操作               |
| 需求条目     | 项目内一条业务需求的登记单位，由大需求文档按模块拆分导入或手工创建，是测试追溯链的起点 |

---


## 2. 总体描述

### 2.1 用户角色与特征

| 角色    | 职责概述              | 权限边界                         |
| ----- | ----------------- | ---------------------------- |
| 系统管理员 | 平台全局维护            | 拥有系统权限，可访问系统管理功能；可同时归属工作空间 |
| 业务用户  | 所属工作空间内的测试活动管理与执行 | 拥有工作空间权限，所有业务操作限于当前活跃工作空间 |

- 一名用户可同时拥有多个系统角色和多个工作空间角色。
- 系统管理员由系统初始化时预设，不可被删除。
- 业务用户至少归属一个工作空间才能使用业务功能。
- 权限模型采用 RBAC，权限码按作用域分为 `global`（系统级）和 `workspace`（空间级）两类。

### 2.2 假设与依赖

- 平台账号由系统管理员统一创建，不存在自主注册功能。
- 平台具备基本的日志与审计能力（本文档不展开）。
- 脑图编辑器的前端技术选型及实时协作实现方案不影响业务需求的定义。

---


## 3. 非功能需求

### 3.1 性能需求

- 常规页面首屏加载时间不超过2秒。  
- 工作空间切换后数据刷新应在500毫秒内完成。  
- 脑图文档（含200个以上节点）打开时间不超过1秒。  
- 快照生成操作应在500毫秒内完成（对于200节点规模的文档）。  
- 系统应支持至少100名业务用户同时在线操作。

### 3.2 安全性需求

- 用户身份认证应安全可靠，会话超时后需重新登录。  
- 业务操作必须验证用户所属的活跃工作空间，严格隔离不同工作空间的数据。  
- 登录失败次数过多时应临时锁定账户。  
- 应防范常见的Web安全攻击（如跨站脚本、跨站请求伪造）。  
- 实时协作通道也应进行身份验证和工作空间上下文校验。

### 3.3 可用性需求

- 脑图编辑器应提供常用键盘快捷键（如Tab/Enter创建子节点，Delete删除节点）。  
- 工作空间切换器应固定在界面明显位置，当前活跃工作空间状态清晰可见。  
- 在快照展示页面，应明确标识“快照版本”和生成时间。  
- 同步操作应提供二次确认，防止误操作。  
- 执行历史和评审历史应支持按时间排序查看。

### 3.4 可靠性需求

- 系统应保证7×24小时可用，计划内维护时间每月不超过2小时。  
- 数据应每日自动备份，支持恢复，数据丢失不超过24小时。

---


## 4. 数据需求

### 4.1 核心数据实体及其关系

- **用户**：存储账号信息、角色及状态，与工作空间为多对多关系。  
- **工作空间**：存储工作空间基本信息，是业务资源隔离的基本单位。  
- **项目**：归属于某个工作空间，包含若干模块和缺陷。  
- **测试用例模块**：形成树形结构，节点分为目录和文档。  
- **脑图节点**：归属于某个文档，形成树形结构，节点可标记为用例节点，用例节点包含测试属性。  
- **测试计划**：归属于项目，包含计划信息，与快照关联。  
- **计划快照**：计划创建时生成的模块和节点副本，与执行记录关联。  
- **测试评审**：归属于项目，包含发起人、参与者列表，与快照关联。  
- **评审快照**：评审创建时生成的模块和节点副本，与评审记录关联。  
- **缺陷**：归属于项目，可关联原始用例和测试计划，具有状态流转和操作日志。
- **需求条目**：归属于项目并按模块归属，是追溯链的起点，具有独立状态与变更记录。
- **AI 任务**：记录其执行与产出提案——执行过程全程留痕、可查看与中止，同一任务可再次执行；产出为待采纳提案，不直接成为业务数据，经人工采纳后才落库。

### 4.2 数据隔离与生命周期

所有业务数据（项目、用例、计划、评审、缺陷）均通过工作空间归属进行隔离，确保业务用户只能访问其当前活跃工作空间内的数据。

---


## 5. 附录

### 5.1 附录 A：角色权限矩阵

| 模块       | 功能点                    | 系统权限用户 | 工作空间权限用户 |
| -------- | ---------------------- | ------ | -------- |
| 用户管理     | 创建、编辑、禁用账号             | ✅      | ❌        |
| 全局工作空间管理 | 创建、归档/重新启用工作空间；添加、移除任意成员    | ✅      | ❌        |
| 角色管理     | 定义角色、配置权限、分配角色         | ✅      | ❌        |
| 我的工作空间   | 查看归属工作空间、切换活跃工作空间、编辑信息 | ❌      | ✅        |
| 工作空间成员管理 | 查看成员、邀请、移除（当前工作空间）     | ❌      | ✅        |
| 项目管理     | 创建、编辑、归档项目             | ❌      | ✅        |
| 模块管理     | 管理目录/文档树               | ❌      | ✅        |
| 脑图编辑     | 实时协作编辑脑图，标记用例节点        | ❌      | ✅        |
| 测试评审     | 发起评审、同步                | ❌      | ✅        |
|          | 提交标记和评论                | ❌      | ✅（参与者）   |
| 测试计划     | 创建、编辑、关闭计划、同步          | ❌      | ✅（负责人）   |
|          | 提交执行记录                 | ❌      | ✅（执行人）   |
| 缺陷管理     | 创建、指派、状态流转、统计查看        | ❌      | ✅        |

### 5.2 附录 B：登录跳转逻辑

用户在登录前，系统首先检查初始化状态：

1. 若系统未初始化（无任何用户）→ 自动跳转至 `/init` 初始化页面
2. 若系统已初始化 → 正常展示登录表单

用户登录成功后，系统根据以下条件决定跳转目标：

1. 若用户拥有工作空间 → 跳转至 `/workspaces`（我的空间页面）
2. 若用户无工作空间：
   - 拥有任意系统权限 → 跳转至 `/admin/dashboard`（系统管理数据概览）
   - 无系统权限 → 跳转至 `/workspaces`（我的空间页面）

### 5.3 附录 C：核心页面规划

**系统管理员端**  

- 数据概览  
- 用户管理  
- 空间管理  
- 角色管理  

**业务用户端**  

- 工作台（重定向至活跃工作空间项目列表）  
- 我的空间（工作空间检索、筛选与切换）
- 工作空间成员管理（当前工作空间）  
- 项目管理（当前工作空间）  
- 项目工作区（用例管理、测试计划、测试评审、缺陷管理）

路由与访问控制上，系统管理员无法访问业务页面；业务用户访问业务页面时需确保当前活跃工作空间与页面上下文一致。

---

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `02-srs-overview.md` | 前言、1. 引言、2. 总体描述、4. 非功能需求、5. 数据需求、6. 附录 |
| 系统管理模块 | `01-system-management/02-srs-system-management.md`（总览）及页面分册 `01-system-management/03-srs-system-management-dashboard.md`、`01-system-management/04-srs-system-management-user.md`、`01-system-management/05-srs-system-management-workspace.md`、`01-system-management/06-srs-system-management-role.md`、`01-system-management/07-srs-system-management-audit.md`、`01-system-management/08-srs-system-management-init.md` | 3.1 系统管理模块 |
| 业务功能模块 | `03-function-testing/02-srs-business-features.md`（功能测试）及 `03-function-testing/03-srs-requirement-management.md`（需求管理，功能测试子模块） | 3.2 业务功能模块（功能测试） |
| 空间管理模块 | `02-space-management/02-srs-space-management.md`（总览）及页面分册 `02-space-management/03-srs-space-management-my-workspace.md`、`02-space-management/04-srs-space-management-info.md`、`02-space-management/05-srs-space-management-member.md`、`02-space-management/06-srs-space-management-project.md`、`02-space-management/07-srs-space-management-invite-join.md` | 3.2 业务功能模块（空间管理） |
| 缺陷管理模块 | `04-bug-management/02-srs-bug-management.md` | 3.2 业务功能模块（缺陷管理） |
| AI 能力模块 | `07-ai-capability/02-srs-ai-capability.md`（总册）及分册 `07-ai-capability/03-srs-ai-generation.md`、`07-ai-capability/04-srs-ai-assistant.md`、`07-ai-capability/05-srs-ai-defect-analysis.md`、`07-ai-capability/06-srs-ai-assisted-features.md` | 3.2 业务功能模块（AI 能力） |

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 初始版本 |
| V1.0 | 2026-10-16 | 对齐 AI Task 双平面口径：数据需求中 AI 任务改为记录执行与产出提案，提案为待采纳态、经人工采纳后才落库 |
| V1.0 | 2026-10-16 | 追溯矩阵需求并入需求管理模块：分册对照表同步，范围、术语与数据需求中的追溯条目改写 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：范围与术语表移除追溯矩阵引用，数据需求删除对应关系实体，分册对照表移除追溯矩阵分册 |
| V1.0 | 2026-10-18 | 需求管理并入功能测试模块：分册对照表需求管理行并入业务功能模块（功能测试）行，分册迁入 `03-function-testing/` |', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000001', '["平台","总览"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '33 days', CURRENT_TIMESTAMP - INTERVAL '33 days', CURRENT_TIMESTAMP - INTERVAL '33 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed002f-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000b-0000-4000-8000-000000000000', 'V1.0', 'REQ-002', '通用需求',
        '# 软件测试平台——通用需求

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档定义平台级通用需求——**登录、令牌刷新、限流**的行为、规则与边界，面向项目经理、业务分析人员、测试工程师及开发团队，作为各业务模块共同依赖的认证与防滥用基线。

### 1.2 范围

覆盖以下三类通用能力：

1. **登录** — 系统管理员与业务用户的身份认证入口，含登录前的初始化引导衔接、登录成功跳转与登录失败处置；
2. **令牌刷新** — 会话存续期内访问令牌的自动续期、续期失败回落，以及会话终止与安全事件导致的令牌失效；
3. **限流** — 登录、令牌刷新及各类公开接口的请求频率限制与登录失败锁定。

不包含：具体页面的布局与交互细节（见交互设计文档）、接口路径与报文结构（见详细设计文档）、限流与令牌撤销的技术实现口径（见 `docs/00-spec/40-security/01-security.md`）。

### 1.3 与既有文档的关系

- 平台总体需求与角色模型见 `docs/01-requirements/02-srs-overview.md`，本文档为通用能力的独立需求分册，两者为并列关系；总览 3.2 安全性需求、5.2 登录跳转逻辑与本文档一致，冲突之处以本文档为准。
- 系统初始化引导见 `docs/01-requirements/01-system-management/08-srs-system-management-init.md`；用户状态（启用/禁用/锁定）与安全事件导致的会话失效见 `docs/01-requirements/01-system-management/04-srs-system-management-user.md`；登录与操作审计见 `docs/01-requirements/01-system-management/07-srs-system-management-audit.md`。本文档不重复描述上述分册已定义的内容。
- 各业务模块的公开入口（邀请链接、报告分享、Mock 访问等）的限流要求以本文档 3.3 的统一口径为准，模块文档不另行定义。

### 1.4 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| 会话 | 用户一次登录成功后到退出或失效前的身份存续状态，由访问令牌与刷新令牌共同维持 |
| 访问令牌 | 会话中用于证明调用方身份的短期凭证，过期后通过刷新令牌续期 |
| 刷新令牌 | 会话中用于续期访问令牌的长期凭证，不直接用于业务调用 |
| 令牌撤销 | 使已签发令牌提前失效的处置；撤销后该令牌的后续使用一律拒绝 |
| 令牌刷新 | 使用刷新令牌获取新访问令牌的过程，用户无感知 |
| 限流 | 对单位时间内请求数超过阈值的调用予以拒绝的防护机制 |
| 登录失败锁定 | 同一账号连续登录失败达阈值后，在锁定期内拒绝该账号登录的处置 |
| 公开接口 | 无需登录即可访问的接口（登录、初始化、邀请校验、报告分享查看等） |
| 用户故事 | 以用户视角描述的capability单元，编号格式 `US-<域>-NNN`，本文档使用 `COM`（通用）域 |

## 2. 总体描述

**适用对象**：登录与令牌刷新对全部用户（系统管理员、业务用户）生效；限流对全部公开接口及认证接口生效，匿名调用方同样受限流约束。

**总体行为**：

- 用户先经初始化引导（未初始化时）再进入登录；登录成功建立会话，此后业务请求在会话内进行。
- 会话存续期内访问令牌自动续期，用户无需重复登录；续期失败或会话被终止时回落到未登录态。
- 认证类与公开类接口统一受限流保护，超限被拒绝并给出可理解的提示，正常操作频率下不受影响。

**假设与依赖**：

- 账号由管理员统一创建，不存在自主注册（沿用总览 2.2）。
- 客户端来源地址的识别依赖受控网关正确传递转发头。

## 3. 功能需求

### 3.1 登录（US-COM-001）

**功能描述**：以账号与密码完成身份认证，登录前自动衔接系统初始化检测，登录成功后建立会话并按规则跳转；登录失败给出统一提示并对暴力破解予以锁定。  
**功能点**：

- 登录表单采集登录名与密码两项，提交期间按钮进入加载态、禁止重复提交，支持回车提交。
- 进入登录页时自动检测系统初始化状态：未初始化跳转初始化页，已初始化正常展示登录表单（规则见 `docs/01-requirements/01-system-management/08-srs-system-management-init.md`）。
- 登录成功：建立会话（签发访问令牌与刷新令牌），按跳转规则进入目标页面（见 1.3 所引总览 5.2）。
- 登录失败：返回统一失败提示，不区分账号不存在、密码错误、账号被禁用或锁定。
- 连续失败达阈值：临时锁定该账号，锁定期内登录被拒绝，并提示锁定原因与可重试时间。
- 成功登录后清零该账号的连续失败计数。

**业务规则**：

- 平台不提供自主注册入口，账号由管理员创建。
- 禁用与锁定状态均阻止登录；提示文案不区分具体状态，避免泄露账号存在性。
- 锁定命中与限流超限分别给出明确提示（锁定提示需说明是失败次数过多所致，限流超限提示稍后重试），两者互不混淆。
- 登录行为（成功、失败、锁定）计入审计记录，口径见 `docs/01-requirements/01-system-management/07-srs-system-management-audit.md`。
- 初始化引导期间的检测异常不阻断登录页展示（沿用初始化分册规则）。

### 3.2 令牌刷新（US-COM-002）

**功能描述**：会话存续期内自动续期访问令牌，使用户持续操作无需重新登录；续期失败或会话终止时回落到未登录态并引导重新登录。  
**功能点**：

- 会话由短期访问令牌与长期刷新令牌维持；访问令牌到期前后使用刷新令牌自动换取新访问令牌，全过程用户无感知，不跳转、不弹出重新登录。
- 刷新成功：被中断的请求继续完成，用户操作不丢失、不重复提交。
- 刷新失败（刷新令牌过期、无效或已被撤销）：进入未登录态，跳转登录页并提示会话已过期需重新登录。
- 并发刷新：同一会话内多个请求同时需要刷新时只执行一次刷新，其余请求等待并复用同一结果。
- 退出登录：服务端撤销当前会话的访问令牌与刷新令牌，同时清理本地会话；撤销后该会话的两个令牌均不可再使用。
- 安全事件（账号被禁用/锁定、密码被重置或修改）：该账号既有会话的全部令牌失效，后续刷新一律失败，强制重新登录；用户重新登录后不受既往事件影响（行为与 `docs/01-requirements/01-system-management/04-srs-system-management-user.md` 一致）。

**业务规则**：

- 刷新使用刷新令牌完成，任何环节均不要求用户重复输入账号密码。
- 刷新与登录同受 3.3 限流约束。
- 令牌不得出现在 URL、查询参数、日志与错误提示中。
- 长期未活动导致会话超时后需重新登录（沿用总览 3.2）。
- 角色或权限变更不要求重新登录：已登录会话按变更后的权限即时生效（沿用总览角色管理分册口径）。

### 3.3 限流（US-COM-003）

**功能描述**：对登录、令牌刷新及各类公开接口实施请求频率限制，防止口令暴力破解与接口滥用，保障认证链路与平台整体可用。  
**功能点**：

- 限流范围：登录、令牌刷新、系统初始化设置、邀请链接校验与加入、密码设置（自助修改与管理员重置）、报告分享免登录查看、Mock 访问等认证入口与公开入口。
- 限流维度：登录按来源 IP 与账号标识双维度限制；其余接口按来源 IP 单维度限制；各接口计数相互独立。
- 超限表现：返回限流错误（HTTP 429），提示「请求过于频繁，请稍后再试」，不区分触发维度、不泄露账号是否存在。
- 登录失败锁定：同一账号连续登录失败达阈值后进入锁定，锁定期内登录被拒绝并返回锁定错误（HTTP 423），提示为失败次数过多所致。
- 阈值与窗口集中声明、可配置、可审计，默认取值见 `docs/00-spec/40-security/01-security.md` 6.1 各场景表。
- 多实例部署下，限流计数与登录失败计数全局共享、判定一致。
- 限流机制自身异常（如计数存储不可用）时不阻断正常登录与业务访问，记录告警日志。

**业务规则**：

- 失败响应与超限响应均不泄露账号是否存在；认证失败（含账号不存在）计入账号失败维度，避免通过差异推断账号。
- 成功登录清零该账号连续失败计数；来源 IP 维度在窗口内不清零，窗口滚动重置。
- 限流超限返回全局错误码 429、账号锁定返回 423，不占用业务错误码号段。
- 正常操作频率不触发限流；阈值调整不得低于正常使用速率的合理倍数。
- 被限流的请求不执行、不落业务数据，重试需由调用方在窗口结束后发起。

## 4. 非功能需求

### 4.1 安全性需求

- 令牌与密码不得出现在 URL、查询参数、日志、审计详情与错误响应中。
- 登录失败、锁定与限流超限的提示一律不泄露账号存在性。
- 密码在交互过程中不回显，错误提示不回显输入的凭据内容。

### 4.2 性能与可用性需求

- 令牌自动刷新对用户操作无感知，不引起页面刷新、状态重置或数据丢失。
- 刷新期间的请求等待时间应在可接受范围内，不出现长时间无响应。
- 限流阈值按正常使用速率的合理倍数设定，正常业务操作不被误伤。

### 4.3 可靠性需求

- 限流或计数组件故障不得导致正常用户无法登录或业务中断（可用性优先，异常留痕）。
- 令牌撤销与刷新失败的判定在故障场景下不产生「已失效会话仍可继续使用」的不一致（判定存疑时按未登录处理）。

## 5. 数据需求

### 5.1 核心数据实体

- **会话（令牌）状态**：所属用户、签发时间、过期时间、撤销状态与撤销原因（登出 / 安全事件）。
- **登录失败与锁定计数**：账号标识、连续失败计数、锁定截止时间、计数窗口。
- **限流计数**：接口标识、维度键（来源 IP / 账号标识）、窗口、计数。
- **登录审计记录**：操作人、时间、来源地址、结果（成功 / 失败 / 锁定）、请求标识；实体定义与字段口径见审计分册，本分册不重复定义。

### 5.2 数据生命周期

- 限流计数与登录失败计数为窗口型临时数据，超窗自动失效，不作为业务数据长期保留。
- 会话（令牌）状态随撤销与过期终结，无需长期保留已终结会话的完整凭证信息。
- 登录审计记录随审计体系统一保留，遵循审计分册的保留策略。

## 6. 附录

### 附录 A：用户故事与章节映射

| 用户故事 | 章节 |
| --- | --- |
| US-COM-001 登录 | 3.1 |
| US-COM-002 令牌刷新 | 3.2 |
| US-COM-003 限流 | 3.3 |

---

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 创建通用需求分册（登录、令牌刷新、限流） |', 'archived', 'high', '7e57001f-0000-4000-8000-000000000002', '["通用","登录","安全"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '32 days', CURRENT_TIMESTAMP - INTERVAL '32 days', CURRENT_TIMESTAMP - INTERVAL '32 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0030-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000c-0000-4000-8000-000000000000', 'V1.0', 'REQ-003', '系统管理模块',
        '# 软件测试平台——系统管理模块

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 系统管理模块

**功能描述**：系统管理模块汇聚平台级管理能力，供拥有系统权限的用户维护账号、工作空间、角色与平台配置；页面级需求按页拆分为独立分册，本总览仅维护模块入口与页面清单。  
**功能点**：

- 入口可见性：拥有任意系统权限的用户可见系统管理功能入口，无系统权限的用户不显示系统管理入口。
- 页面拆分：各页面的功能点与业务规则按页沉淀在对应分册，不在此重复描述。

**页面清单**：

| 分册 | 页面 | 说明 |
| ---- | ---- | ---- |
| [03 数据概览](03-srs-system-management-dashboard.md) | 数据概览页 | 管理端默认落地页 |
| [04 用户管理](04-srs-system-management-user.md) | 用户列表页、用户表单页 | 平台账号的生命周期维护 |
| [05 空间管理](05-srs-system-management-workspace.md) | 空间列表页、空间详情页 | 全局工作空间与成员构成 |
| [06 角色管理](06-srs-system-management-role.md) | 角色管理页 | 角色定义、权限点与关联用户 |
| [07 审计查询](07-srs-system-management-audit.md) | 审计查询页 | 页面尚未实现，先行需求 |
| [08 系统初始化](08-srs-system-management-init.md) | 系统初始化页 | 登录域引导页，未登录可达 |

**业务规则**：

- 具体菜单项按对应查看权限过滤，无权限的菜单项不展示。
- 页面清单与管理端实际页面一一对应，新增页面时同步更新本表与所在目录索引。

## 2. 管理端导航

**功能描述**：系统管理功能的统一导航入口，按权限动态呈现。  
**功能点**：

- 导航按功能域分组呈现：概览（数据概览）、组织与权限（用户管理 / 角色管理 / 空间管理）。
- 从导航切换页面时地址栏路径随页面变化，可直接通过地址访问。

**业务规则**：

- 数据概览为管理端默认落地页。
- 系统初始化页不进入管理端导航，由登录页检测系统状态后引导进入。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000003', '["系统管理"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '31 days', CURRENT_TIMESTAMP - INTERVAL '31 days', CURRENT_TIMESTAMP - INTERVAL '31 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0031-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000d-0000-4000-8000-000000000000', 'V1.0', 'REQ-004', '数据概览页',
        '# 软件测试平台——数据概览页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 数据概览

**功能描述**：管理端的默认落地页，以全平台聚合口径汇总平台关键运营指标、趋势图表与最新空间，帮助管理员快速掌握平台整体运行状态；公共约定见总览分册。  
**功能点**：

- 页面由页面头部、四张关键指标卡、两张图表、最近创建的空间表格构成。
- 页面头部展示标题、副标题（平台整体运行状态及数据截至时间）与「刷新」操作。
- 进入页面自动加载；「刷新」重新获取指标、图表与空间表格第一页数据。

**业务规则**：

- 指标与图表均为全平台聚合口径，不按工作空间或项目拆分，无需选择时间范围。
- 数据概览不提供报表导出功能。
- 关键指标卡为纯展示，不可点击、无跳转。
- 加载过程中展示页面级加载遮罩。
- 指标图表与空间表格独立加载、互不影响；任一区块加载失败时在对应区块展示错误提示并提供「重试」入口，已加载成功的区块保留展示。

### 1.1 关键指标卡

**功能描述**：以四张卡片呈现平台核心指标，主值放大展示，脚注展示辅助信息。  
**功能点**：

- 用户总数（单位：人）：脚注展示较上周新增数与启用用户数。
- 工作空间（单位：个）：脚注展示活跃数与归档数。
- 项目总数（单位：个）：脚注展示近 7 日新增数。
- 今日活跃（单位：人次）：无脚注，统计当日登录人次。

**业务规则**：

- 脚注中的正向增量以强调色呈现，增量为 0 时显示「+0」并使用中性样式。
- 数据未就绪或为空时主值显示 0。

### 1.2 图表

**功能描述**：以趋势图与占比图分别呈现平台活跃变化与用户状态构成。  
**功能点**：

- 近 14 日活跃用户趋势图：面积折线展示连续 14 个自然日（含当日）的每日活跃用户数，末点高亮并标注当日数值，横轴标注首日、中间日、末日；口径为按日去重的登录用户数。
- 用户状态分布图：环形图分为启用、停用、锁定三段，配图例展示各状态人数，环心展示用户总数及「全部用户」说明。

**业务规则**：

- 趋势图无记录的日期按 0 计；完全无数据时折线贴合基线，末点标注 0。
- 状态分布三段人数之和等于用户总数；用户总数为 0 时仅展示底环，环心显示 0，图例各状态为 0。

### 1.3 最近创建的空间

**功能描述**：以表格展示平台最近创建的工作空间，便于管理员快速了解并进入空间管理。  
**功能点**：

- 表格列：空间名称、创建人、成员、项目、状态、创建时间；按创建时间倒序排列，每页 8 条，页脚展示空间总数与分页。
- 卡片标题右侧提供「查看全部」入口，跳转至空间管理列表。
- 状态列展示「活跃」或「归档」；创建人缺失时展示「—」。

**业务规则**：

- 无工作空间时展示空态「暂无工作空间」。
- 表格行不可点击，仅「查看全部」提供跳转。', 'confirmed', 'low', '7e57001f-0000-4000-8000-00000000000b', '["系统管理","数据概览"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0032-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000e-0000-4000-8000-000000000000', 'V1.0', 'REQ-005', '用户管理页',
        '# 软件测试平台——用户管理页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中  

---

## 1. 用户管理

**功能描述**：管理平台全部账号的查询、创建、状态流转与系统角色分配，公共约定见总览分册。  
**功能点**：

- 页面由页面头部、筛选栏、用户表格与页脚构成，进入页面自动加载；页面头部展示标题、副标题与「新建用户」入口，页脚展示账号总数与分页（每页可选 10、20、50、100 条，默认 20 条）。
- 筛选条件包含用户名/邮箱关键字、状态与系统角色，提供「查询」与「重置」，关键字回车可直接查询，「重置」清空条件并回到第一页。

**业务规则**：

- 用户状态为启用、禁用、锁定三态，仅启用状态允许登录，禁用与锁定均阻断登录，历史数据保留。
- 用户的工作空间归属由全局工作空间管理维护，本模块不涉及工作空间归属设置。
- 本模块不提供删除账号功能。

### 1.1 用户列表

**功能描述**：以表格呈现平台账号及其状态与系统角色，支撑检索与进入后续操作。  
**功能点**：

- 表格列：选择框、用户、邮箱、状态、系统角色、创建时间、操作。
- 用户列展示用户名与姓名，用户名以链接呈现，点击进入该用户编辑页。
- 状态列以启用/禁用/锁定文案呈现；系统角色多枚并列展示，无角色时显示占位符。

**业务规则**：

- 无数据时展示空态，加载过程中展示加载态。

### 1.2 行操作

**功能描述**：对单个账号执行编辑、状态流转与密码重置。  
**功能点**：

- 每行提供五项操作：编辑、启用、禁用、锁定、重置密码。
- 编辑进入该账号的编辑表单；启用仅在状态非启用时展示，禁用仅在状态非禁用时展示，锁定仅在状态非锁定时展示。
- 重置密码弹出对话框，展示目标用户名与密码长度要求，输入新密码后确认提交。

**业务规则**：

- 启用、禁用、锁定均需二次确认，确认提示包含目标用户名，完成后刷新列表。
- 三态可相互流转，状态变更不影响该账号的历史数据。
- 重置密码的新密码须为 8~64 字符，为空或长度不符时不提交并给出提示。
- 禁用、锁定、重置密码成功后，该账号此前已登录的会话**立即失效**（被强制退出，需重新登录）；启用操作不终止已有会话。

### 1.3 批量操作

**功能描述**：对勾选的多个账号批量变更启用状态。  
**功能点**：

- 勾选行后页脚出现「已选 N 项」及「批量启用」「批量禁用」入口，取消全部勾选后隐藏。

**业务规则**：

- 仅提供批量启用与批量禁用，不提供批量锁定或批量解锁；未勾选任何行时不展示批量操作入口。
- 批量操作需二次确认，确认提示展示选中数量，完成后刷新列表。
- 批量禁用成功后，受影响账号的已登录会话同样立即失效。

### 1.4 用户表单（新建与编辑）

**功能描述**：新建与编辑账号共用同一表单页，按模式调整可编辑范围。  
**功能点**：

- 表单字段：用户名、姓名、邮箱、密码、系统角色（复选，可不勾选）；页面提供「取消」与「保存」。
- 新建态：用户名可编辑，密码必填并展示密码强度提示。
- 编辑态：用户名只读不可修改，密码区为「修改密码」入口，通过弹窗设置新密码。

**业务规则**：

- 用户名必填，3~30 字符，仅允许字母、数字、下划线与连字符，且全局唯一；编辑时不可修改。
- 姓名必填，不超过 50 字符；邮箱必填、格式合法且全局唯一。
- 密码 8~64 字符；新建账号保存成功后默认为启用状态。
- 保存成功后返回用户列表页，保存失败时提示原因并停留在表单页。', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000004', '["系统管理","用户"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '29 days', CURRENT_TIMESTAMP - INTERVAL '29 days', CURRENT_TIMESTAMP - INTERVAL '29 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0033-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000f-0000-4000-8000-000000000000', 'V1.0', 'REQ-006', '空间管理页',
        '# 软件测试平台——空间管理页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 空间管理

**功能描述**：管理平台内所有工作空间的列表浏览、新建、详情查看、信息与成员维护，以及归档与重新启用。  
**功能点**：

- 页面包含空间列表、新建空间对话框与空间详情（基本信息、统计、成员列表）；归档与重新启用的入口位于详情页头部，列表页不提供状态变更操作。

**业务规则**：

- 空间名称全局唯一，长度 2~50 字符，描述最多 200 字符；创建空间时必须指定一名空间管理员（单选、必填），创建后该用户即以管理员身份加入。
- 空间状态仅「活跃」「归档」两态；归档空间只读，归档期间成员归属保留，重新启用后无需重新添加。

### 1.1 空间列表

**功能描述**：以表格呈现平台全部工作空间及其规模，提供状态筛选、关键字搜索与新建入口。  
**功能点**：

- 状态筛选以「活跃 / 归档」分段切换，默认选中活跃；关键字搜索按空间名称匹配，回车或「查询」触发，「重置」恢复默认条件。
- 表格列：空间名称、描述、状态、成员数、项目数、创建时间、操作；固定提示按创建时间倒序，默认每页 20 条。
- 操作列提供「查看详情」，点击行或空间名称同样进入详情页；页头提供「新建空间」按钮。

**业务规则**：

- 无新建空间权限时不显示「新建空间」按钮；状态列以「活跃」或「归档」标识，描述为空时展示占位符；无匹配数据时展示空态，加载失败可重新加载。

### 1.2 新建空间

**功能描述**：通过对话框创建一个新的工作空间，并同时指定首名空间管理员。  
**功能点**：

- 字段：名称（必填，2~50 字符，实时字数统计）、管理员（必填，单选，可输入姓名搜索，仅可选启用用户，默认选中当前登录用户）、描述（可选，最多 200 字符）；「取消」关闭对话框，「确定」先校验必填与长度再提交，创建成功后关闭对话框并进入该空间详情页。

**业务规则**：

- 名称与已有空间重名时创建失败并提示，对话框保留已填内容供修改；提交过程中禁止重复提交。

### 1.3 空间详情

**功能描述**：查看并维护单个工作空间的基本信息、规模统计与成员构成。  
**功能点**：

- 面包屑展示返回空间管理列表的路径与当前空间名称；页头展示空间名称、状态标识、空间 ID 与创建时间，并按状态只展示「归档空间」或「重新启用」之一；加载失败展示错误提示。

**业务规则**：

- 空间不存在或无权限访问时给出明确错误提示，并提供返回列表的途径。

#### 1.3.1 基本信息与统计

**功能描述**：以表单呈现并维护空间基本信息，同时呈现成员数与项目数。  
**功能点**：

- 表单字段：空间名称（2~50 字符）、创建人（只读展示）、描述（最多 200 字符）；统计以徽标展示成员数与项目数并随之刷新；提供「保存修改」与「重置」，重置恢复为最近一次保存的值。

**业务规则**：

- 创建人不可编辑，修改名称仍须满足全局唯一与长度限制；归档态表单整体只读，隐藏「保存修改」与「重置」。

#### 1.3.2 成员列表

**功能描述**：列出空间成员并支持添加成员、调整空间角色与移除成员。  
**功能点**：

- 表格列：用户（头像与用户名）、空间角色、加入时间、操作（改角色、移除）；卡片头部展示成员总数与「添加用户」，默认每页 20 条。
- 添加用户按用户名或邮箱搜索后多选，逐人指定空间角色（默认预选首个角色），至少选择一名方可提交；改角色为行内选择、即时生效，移除需二次确认并同步刷新统计。

**业务规则**：

- 仅启用状态的用户可被添加，已是本空间成员的用户自动跳过并提示；角色调整失败时还原展示并刷新列表，且不可将最后一名空间管理员改为其他角色。
- 归档态隐藏「添加用户」与操作列，成员列表只读。

### 1.4 归档与重新启用

**功能描述**：在详情页头部对空间执行归档或重新启用，实现活跃与归档两态切换。  
**功能点**：

- 归档仅活跃空间可执行，需手动输入当前空间名称确认，完成后返回列表页；重新启用仅归档空间展示，点击后立即生效并刷新当前页为活跃态。

**业务规则**：

- 归档不要求空间下没有项目，也不解除成员归属，归档后整页只读、仅保留「重新启用」；重新启用无需二次确认，基本信息与成员关系原样生效。', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000005', '["系统管理","工作空间"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days', CURRENT_TIMESTAMP - INTERVAL '28 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0034-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0010-0000-4000-8000-000000000000', 'V1.0', 'REQ-007', '角色管理页',
        '# 软件测试平台——角色管理页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 角色管理

**功能描述**：维护平台角色及其权限配置与关联用户；页面左右分栏，左侧以角色树浏览与维护角色，右侧以标签页展示所选角色的权限点与关联用户。  
**功能点**：

- 页面头部展示标题「角色管理」及副标题（说明本页用于系统角色的权限点配置与关联用户）。
- 主体左右分栏：左侧为角色列表，右侧为详情区；详情区含「权限点」「关联用户」两个标签页。
- 未选择角色时，两个标签页均展示空态提示，引导先在左侧选择角色。
- 单击具体角色后默认展示「权限点」标签页，切换标签页仅切换右侧内容，不改变选中角色。
- 角色列表、权限配置、关联用户任一加载失败时，给出明确错误提示。

**业务规则**：

- 角色名称全局唯一。
- 具备全量权限的角色，其权限配置面板整体锁定，不可手动编辑。
- 权限变更后，已登录用户的下次请求即可生效。
- 权限作用域分为系统级与空间级两类，系统角色配置系统级权限点，工作空间角色配置空间级权限点。
- 工作空间角色授予用户，既可在本页关联，也可在空间成员管理中设置。
- 预置的「系统管理员」角色不可删除，其权限默认全有且不可更改。

### 1.1 角色列表与角色维护

**功能描述**：以树形列表展示平台全部角色并按类型分组，同时提供角色的新增、重命名与删除入口。  
**功能点**：

- 角色分为「系统角色」与「工作空间角色」两个分组，两个分组始终展示；卡片标题展示角色总数（分组节点不计入）。
- 具体角色节点展示角色名称与已关联用户数，预置角色附「预置」标识。
- 分组节点上提供「新增」操作，在对应分组下创建角色；新增与重命名均为弹出输入框录入角色名称，提交成功后刷新列表并选中新角色。
- 当前选中的具体角色节点上提供「重命名」「删除」操作；未选中的角色不展示操作按钮。
- 单击分组节点仅切换选中态，不加载详情；删除当前正在查看的角色后，右侧详情随之清空。

**业务规则**：

- 预置角色不提供重命名与删除操作，删除前需二次确认。
- 新增、重命名时角色名称不能为空。

### 1.2 权限点

**功能描述**：以表格按模块分组展示所选角色的权限点，供管理员勾选并保存。  
**功能点**：

- 权限点按一级模块、二级模块两级分组展示，同一一级模块的多行合并展示模块名。
- 每个权限点以复选框呈现；表头提供全选复选框，支持一键全选或全部取消。
- 表头提供「撤销修改」与「保存权限」操作，勾选内容与已保存内容一致时两按钮禁用，否则可用。
- 保存成功后提示成功，并以保存结果刷新勾选状态；「撤销修改」恢复到上次保存的勾选状态。

**业务规则**：

- 系统预置角色的权限点全部锁定、不可勾选，且不显示「撤销修改」「保存权限」操作，全选复选框同样禁用。

### 1.3 关联用户

**功能描述**：展示拥有该角色的用户，支持搜索添加与移除。  
**功能点**：

- 系统角色：表格展示用户名、姓名、邮箱、状态、授权时间，行内提供「移除」。
- 工作空间角色：表格按用户聚合展示用户名、姓名、归属空间（可多个）、授权时间，行内提供「移除」。
- 「添加用户」打开选择弹窗，按姓名远程搜索候选用户（仅活跃用户），支持多选，已关联用户不出现在候选中。
- 工作空间角色添加时还须同时选择至少一个空间，未选择空间不允许提交。
- 工作空间角色移除时，该用户仅在一个空间拥有此角色则确认后直接移除；在多个空间拥有时弹窗勾选要移除的空间（可多选）后移除。
- 列表支持分页与每页条数切换，页脚展示总条数。

**业务规则**：

- 移除角色关联前需二次确认；未选择用户或未选择空间不允许提交。
- 当前页无数据且存在后续页时自动回退到末页，避免空页停留。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000003', '["系统管理","角色权限"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '27 days', CURRENT_TIMESTAMP - INTERVAL '27 days', CURRENT_TIMESTAMP - INTERVAL '27 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0035-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0011-0000-4000-8000-000000000000', 'V1.0', 'REQ-008', '审计查询页',
        '# 软件测试平台——审计查询页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中  

---

## 1. 审计查询

**功能描述**：面向平台管理员的全平台审计查询页面，以条件检索、分页列表与统计概览呈现平台关键操作与登录行为，便于追溯操作来源与变更内容；公共约定见总览分册。  
> **先行需求说明**：本页面当前尚未实现，本分册为按已确认的详细设计提炼的先行需求，用于约束页面的实现范围与验收口径。  
**功能点**：

- 页面由查询条件区、统计概览、结果列表构成，进入页面自动加载首屏数据。
- 支持「查询」与「重置」：查询按当前条件刷新统计概览与结果列表，重置清空条件后重新查询。
- 审计数据为全平台级口径，不按工作空间或项目拆分。

**业务规则**：

- 需具备审计查看权限方可访问本页面，无该权限的用户不显示入口。
- 各查询条件均可不选，未选择的条件不参与筛选。
- 加载过程中展示加载状态；加载失败时在对应区块展示错误提示并提供「重试」入口。

### 1.1 查询条件

**功能描述**：按时间范围、操作类型、操作人与对象类型组合筛选审计记录。  
**功能点**：

- 时间范围：起始日期与结束日期，按自然日闭区间筛选记录时间。
- 操作类型：区分登录与业务操作，可选登录、新增、修改、删除。
- 操作人：按操作人名称模糊匹配。
- 对象类型：按被操作对象的类型精确筛选。

**业务规则**：

- 各条件可任意组合，条件之间为「且」的关系。
- 条件修改后需点击「查询」才生效，「重置」将全部条件恢复为空。

### 1.2 结果列表

**功能描述**：以分页表格逐条展示命中的审计记录，呈现操作时间、操作人、变更内容概要与来源。  
**功能点**：

- 表格列：记录时间、操作人、操作类型、操作对象、变更摘要、请求来源。
- 操作对象展示被操作对象的类型与标识；登录记录的操作对象为发起登录的用户。
- 变更摘要展示该条记录的关键变更内容；登录记录无变更内容时展示占位符。
- 请求来源展示操作发生的来源地址，登录记录展示登录来源。
- 按记录时间倒序排列，默认每页 20 条，页脚展示命中总数与分页控件。

**业务规则**：

- 无匹配记录时展示空态提示。
- 列表仅提供浏览能力，不提供编辑或删除审计记录的操作。
- 审计记录中的敏感信息以脱敏形式展示，不以明文呈现。

### 1.3 统计概览

**功能描述**：按天聚合展示所选范围内的操作次数趋势，辅助判断操作量变化。  
**功能点**：

- 统计维度：按所选对象类型，自统计起始日期起逐日统计操作次数。
- 以日期为横轴、操作次数为纵轴呈现每日次数，日期按先后顺序排列。

**业务规则**：

- 统计概览与结果列表随同一次查询刷新，统计范围由所选对象类型与起始日期决定。
- 统计范围内无记录时统计概览展示空态。', 'confirmed', 'low', '7e57001f-0000-4000-8000-000000000009', '["系统管理","审计"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '26 days', CURRENT_TIMESTAMP - INTERVAL '26 days', CURRENT_TIMESTAMP - INTERVAL '26 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0036-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0012-0000-4000-8000-000000000000', 'V1.0', 'REQ-009', '系统初始化页',
        '# 软件测试平台——系统初始化页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 系统初始化

**功能描述**：平台首次部署完成后，系统必须经过初始化才能使用；初始化由安装者在登录页自动引导完成，用于设置管理员密码并创建系统管理员账号。  
**功能点**：

- 初始化检测：访问登录页时自动检测系统是否已初始化，未初始化则自动跳转至初始化页。
- 初始化表单：登录名、管理员密码、确认密码三个输入项，登录名固定预填 `admin` 且不可编辑。
- 完成初始化：提交后创建管理员账号并授予系统管理员角色，完成后跳转至登录页。

**业务规则**：

- 初始化入口为公开入口，无需登录即可访问。
- 登录页常驻「首次部署？系统将自动引导完成初始化」提示文案；检测异常（如网络失败）不阻断页面，登录页照常展示、初始化页照常展示表单。

### 1.1 初始化检测

**功能描述**：在登录页与初始化页进入时分别自动检测初始化状态，保证入口正确。  
**功能点**：

- 访问登录页时自动检测系统是否已初始化：未初始化时自动跳转至初始化页，已初始化时正常展示登录表单。
- 访问初始化页时同样先检测状态：系统已初始化则自动跳转回登录页；检测过程中初始化页展示「检查系统状态...」加载态，检测完成后才展示表单。

**业务规则**：

- 初始化完成后再次访问初始化页不会展示初始化表单，只能通过登录页进入系统。

### 1.2 初始化表单

**功能描述**：以表单采集管理员登录名与密码，登录名使用系统预设值，不提供注册其他账号的入口。  
**功能点**：

- 登录名：固定预填 `admin`，只读不可编辑。
- 管理员密码：必填，长度 8~64 字符；输入过程中实时展示达标提示（长度、包含字母、包含数字与符号）。
- 确认密码：需与管理员密码完全一致；提交操作为「完成初始化」按钮，提交中按钮进入加载态。

**业务规则**：

- 密码长度不足或超出 8~64 字符、两次输入不一致时均提示并阻止提交；长度为强制校验项，包含字母、包含数字与符号为建议性提示，不阻止提交。

### 1.3 初始化完成

**功能描述**：提交成功后创建系统管理员账号并引导安装者登录。  
**功能点**：

- 提交成功后创建登录名为 `admin`、显示名称为「系统管理员」的账号，并授予系统管理员角色。
- 成功后提示「系统初始化成功，请登录」并跳转至登录页，安装者使用 admin 账号及已设置的密码登录；页面常驻「初始化完成后将跳转至登录页」说明文案。

**业务规则**：

- 防重复初始化：系统已初始化时再次提交返回「系统已初始化，请直接登录」错误提示，不产生新账号。
- 系统管理员角色为预置角色，拥有全部系统权限，不可删除。
- 初始化完成后登录页恢复正常登录，不再出现初始化引导；使用顺序上可先进入系统管理创建普通用户，也可先创建工作空间后再创建业务用户。', 'draft', 'medium', '7e57001f-0000-4000-8000-000000000006', '["系统管理","初始化"]'::jsonb,
        'import', NULL, null, CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '25 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0037-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0013-0000-4000-8000-000000000000', 'V1.0', 'REQ-010', '空间管理总览',
        '# 软件测试平台——空间管理总览

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 空间管理模块

**功能描述**：空间管理是业务功能模块下的空间域，仅业务用户可访问，用于管理归属的工作空间及其成员与项目；页面级需求按页拆分为独立分册，本总览仅维护模块定位、活跃工作空间机制与页面清单。  
**功能点**：

- 访问范围：业务用户登录后进入空间域；平台级的全局空间管理不属于本模块。
- 上下文边界：空间域内的查看与操作均作用于当前活跃工作空间，不跨空间生效。
- 页面拆分：各页面的功能点与业务规则按页沉淀在对应分册，本总览不重复描述。

**页面清单**：

| 分册 | 页面 | 说明 |
| ---- | ---- | ---- |
| [03 我的空间](03-srs-space-management-my-workspace.md) | 我的空间 | 归属空间的检索、筛选与创建，以及活跃工作空间的切换入口 |
| [04 空间信息](04-srs-space-management-info.md) | 空间信息 | 当前活跃空间基本信息的查看与编辑 |
| [05 成员管理](05-srs-space-management-member.md) | 成员管理 | 当前活跃空间成员构成、成员角色与邀请的维护 |
| [06 项目列表](06-srs-space-management-project.md) | 项目列表 | 当前活跃空间内项目的新建、编辑与归档入口 |
| [07 加入空间](07-srs-space-management-invite-join.md) | 加入空间 | 凭邀请链接加入空间的公开页面，未登录也可访问 |

**业务规则**：

- 各页面的功能点与业务规则见对应分册；模块级公共约定见总览分册 [`../02-srs-overview.md`](../02-srs-overview.md)。
- 页面清单与业务端实际页面一一对应，新增页面时同步更新本表与所在目录索引。
- 加入空间为公开页面，不进入业务端导航。

## 2. 活跃工作空间机制

**功能描述**：业务用户可同时归属多个工作空间，通过唯一的活跃工作空间确定当前业务上下文。  
**功能点**：

- 归属一个或多个工作空间时，均需在我的空间页手动选择进入，系统不自动激活，也不隐藏该页入口。
- 界面提供工作空间切换入口，切换完成后所有业务数据立即刷新为新工作空间的上下文。
- 当前活跃工作空间的名称在顶栏常驻展示，活跃状态清晰可见。

**业务规则**：

- 用户从当前活跃工作空间被移除时不自动切换：继续访问该空间的业务功能将被拒绝并提示，需在我的空间页手动切换到其他空间；无剩余归属空间时业务功能不可用。
- 已归档空间不可切换为活跃空间，也不可进入其业务功能。
- 空间切换入口不因仅归属一个工作空间而隐藏。
- 切换后页面按新的空间上下文重新加载，不残留上一个空间的数据。

## 3. 菜单与权限

**功能描述**：空间域的导航入口按当前上下文与用户权限动态呈现。  
**功能点**：

- 处于空间上下文时，顶栏动态菜单按固定顺序呈现空间信息、成员管理、项目列表；进入项目后，动态菜单切换为项目内的功能入口。
- 顶栏另提供「我的空间」入口用于返回空间列表，以及「空间管理」入口用于进入当前空间的信息页。
- 空间域各页面可直接通过地址访问，地址栏路径随页面变化。

**业务规则**：

- 各菜单项按对应查看权限过滤，无权限的菜单项不展示；「空间管理」入口需具备空间域查看权限。
- 未选择活跃工作空间时，不展示空间域菜单，并提示选择一个工作空间。
- 我的空间与加入空间不进入顶栏动态菜单。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-09-26 | 初始版本 |
| V1.0 | 2026-10-01 | 活跃工作空间机制与「我的空间」页面分册对齐：取消单空间自动激活与切换入口隐藏，被移除后不再自动切换 |', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000002', '["空间管理"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '24 days', CURRENT_TIMESTAMP - INTERVAL '24 days', CURRENT_TIMESTAMP - INTERVAL '24 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0038-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0014-0000-4000-8000-000000000000', 'V1.0', 'REQ-011', '我的空间页',
        '# 软件测试平台——我的空间页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 我的空间页

**功能描述**：业务用户登录后的落地页，用于查看、检索和切换自己归属的工作空间，具备创建空间权限的用户可在此发起创建；公共约定见总览分册 `../02-srs-overview.md`。  
**功能点**：

- 页头展示页面标题与引导语；「新建空间」按钮仅对具备创建空间权限的用户展示。
- 工具栏包含范围分段、名称搜索与「按最近访问排序」提示。
- 卡片网格展示排序后的空间，默认每页 12 条，可选 12/24/48 条，总数超过每页条数时提供分页；首次加载中、加载失败或列表为空时不展示分页。
- 首次加载以骨架卡片占位；已加载后的重新查询仅在列表上方提示刷新，不清空既有内容。

**业务规则**：

- 名称关键词与范围分段可组合使用，任一条件变化后列表回到第一页，首次加载完成前分段与搜索不可操作。
- 有筛选条件且无匹配结果时展示「未找到匹配的工作空间」与「清除筛选」；无任何归属空间时展示「暂无归属的工作空间，请联系管理员」，具备创建权限时附「新建空间」入口。
- 加载失败展示错误说明与「重试」：首次失败占满内容区，已有数据时以错误条提示并保留已成功数据；重试沿用当前关键词与范围。

### 1.1 排序、搜索与范围分段

**功能描述**：确定卡片顺序，并按名称与范围定位目标空间。  
**功能点**：

- 按当前用户最近一次成功进入各空间的时间倒序排列；无访问记录的空间排在其后，内部依次按加入时间倒序、创建时间倒序、空间标识升序兜底，保证顺序稳定。
- 范围分段为「全部」「我管理的」「已归档」三项，每项展示该范围的真实数量。
- 名称搜索对空间名称做不区分大小写的模糊匹配：输入即检索，回车立即检索，清空即恢复。

**业务规则**：

- 「全部」包含活跃与已归档空间；「我管理的」仅包含当前用户担任管理员的活跃空间；「已归档」仅包含已归档空间。
- 三个范围计数基于同一名称关键词实时刷新；分页总数仅统计当前选中范围与关键词下的结果。

### 1.2 空间卡片

**功能描述**：以卡片网格呈现每个空间的信息与状态。  
**功能点**：

- 展示空间名称、描述（无描述时显示「暂无描述」）、当前用户在该空间的真实角色名称、成员数、项目数、用例数；统计数字按千分位展示。
- 当前活跃空间标注「当前空间」；已归档空间标注「已归档」「只读」。
- 成员数、项目数、用例数为纯展示项，不提供单独跳转入口。

**业务规则**：

- 角色标签必须展示角色真实名称（如「管理员」「成员」），不得以「我管理」等行为描述替代。
- 已归档空间保留成员关系与历史数据，仅展示信息与统计，卡片不可点击、不可进入业务功能。

### 1.3 切换活跃工作空间

**功能描述**：点击卡片把目标空间设为活跃空间并进入其业务功能。  
**功能点**：

- 点击任一非归档卡片即切换为该空间，并记录本次访问时间供排序使用；切换期间禁止重复点击，成功后立即跳转。
- 该空间存在当前用户的默认项目时直接进入该项目工作台，否则进入该空间的项目列表。
- 已归档空间点击不产生任何跳转。

**业务规则**：

- 切换失败时停留在本页并提示原因，活跃空间保持不变。
- 无论归属一个还是多个空间，均需在本页手动选择进入，系统不自动激活，也不隐藏本页入口。
- 活跃空间的成员关系被移除后不自动切换：继续访问该空间的业务功能将被拒绝并提示，需在本页手动切换到其他空间。

### 1.4 创建工作空间

**功能描述**：由具备创建空间权限的用户在本页发起创建。  
**功能点**：

- 入口为页头「新建空间」按钮、列表末尾「新建工作空间」卡片、无归属空间时的空态按钮，三者均按创建权限展示。
- 对话框字段：名称（必填，2–50 字符）、管理员（必填、单选，默认当前登录用户，可按姓名搜索启用中的用户，可清空后重新选择）、描述（可选，最多 200 字符）。
- 提交过程中禁用提交与取消，成功后提示创建成功。

**业务规则**：

- 任一必填项缺失或超出长度限制时阻止提交并就地提示。
- 创建成功后清空名称关键词、范围回到「全部」并刷新列表，确保新空间立即可见。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000007', '["空间管理","我的空间"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '23 days', CURRENT_TIMESTAMP - INTERVAL '23 days', CURRENT_TIMESTAMP - INTERVAL '23 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0039-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0015-0000-4000-8000-000000000000', 'V1.0', 'REQ-012', '空间信息页',
        '# 软件测试平台——空间信息页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 空间信息

**功能描述**：当前活跃工作空间的信息维护页，集中展示空间基础资料与运行统计，并为具备编辑能力的用户提供名称、描述的维护能力；菜单项按「空间信息查看权限」过滤，公共约定见总览分册（`../02-srs-overview.md`）。  
**功能点**：

- 页面由页头、工作空间统计卡与「基础信息」区构成，分基础信息展示与编辑、工作空间统计与快捷入口、状态与权限三部分。
- 进入页面自动加载当前空间信息；地址中指定其他空间时按该空间加载，并将其切换为当前活跃空间。

**业务规则**：

- 本页只承载空间信息的查看与基础资料维护，不提供归档、删除、退出空间等操作，相关能力在其他页面办理。

### 1.1 基础信息展示与编辑

**功能描述**：展示空间标识与来源信息，并按编辑能力提供名称、描述的维护。  
**功能点**：

- 页头展示标题「空间信息」与副标题「空间基础资料与运行统计」。
- 只读信息展示：空间 ID、创建人、创建时间；创建人或空间 ID 缺失时以「—」占位。
- 名称与描述可编辑：名称必填且限 2~50 字符，描述最长 500 字符、可留空，输入时展示实时字数统计。
- 具备空间信息编辑能力时，名称与描述可编辑并展示「保存修改」操作；否则两者只读且不展示保存入口。
- 「保存修改」提交去除首尾空白后的名称与描述，成功后提示「已保存」，并同步刷新页面信息与顶部活跃空间名称。

**业务规则**：

- 名称在平台内全局唯一；与其他空间重名时保存失败并提示名称已存在。
- 校验不通过时不发起保存，由字段就地给出提示：名称为空提示「请输入空间名称」，长度不足或超出提示「名称长度需在 2-50 字符之间」。
- 本页不因空间归档而额外禁用编辑，名称与描述是否可改仅取决于是否具备空间信息编辑能力。
- 空间 ID、创建人与创建时间不参与编辑，仅随空间信息一同加载展示。

### 1.2 工作空间统计与快捷入口

**功能描述**：以统计卡呈现当前空间规模，并作为进入成员与项目页面的快捷入口。  
**功能点**：

- 统计卡展示成员数（单位：人）与项目数（单位：个）。
- 成员卡点击进入当前空间的成员管理页；项目卡点击进入当前空间的项目列表页。

**业务规则**：

- 数据未就绪或为空时统计值显示 0。
- 统计值随空间信息一同加载，切换活跃空间后随之更新。

### 1.3 状态与权限

**功能描述**：覆盖加载、失败与权限不足等状态分支，保证页面边界清晰可用。  
**功能点**：

- 加载过程中展示页面级加载遮罩，已加载内容在重新加载期间不清空。
- 缺少空间信息查看权限时不展示菜单入口。
- 加载或保存失败时在页面给出对应提示。

**业务规则**：

- 空间不存在或当前用户不属于该空间时提示「加载空间信息失败」；保存失败时提示「保存失败」。
- 通过地址直达时页面仍可打开，可编辑性仍按是否具备空间信息编辑能力判定。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000008', '["空间管理","空间信息"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '22 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed003a-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0016-0000-4000-8000-000000000000', 'V1.0', 'REQ-013', '成员管理页',
        '# 软件测试平台——成员管理页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 成员管理

**功能描述**：管理当前活跃工作空间的成员构成，涵盖成员查看与筛选、成员关系调整、直接邀请平台业务用户，以及用于扩招的邀请链接管理；菜单入口按「成员管理查看权限」过滤，公共约定见[总览](../02-srs-overview.md)。  
**功能点**：

- 页面并排展示「成员列表」「邀请链接」两张内容卡片（等高对齐，表头与表尾在同一水平线）；「邀请链接」卡片仅对具备邀请管理权限的用户展示。
- 页头提供「邀请成员」（需成员管理权限）与「生成链接」「复制邀请链接」（需邀请管理权限）操作。
- 进入页面自动加载成员列表；具备邀请管理权限时同时加载邀请链接列表。

**业务规则**：

- 所有数据与操作仅作用于当前活跃工作空间，不影响用户在其他工作空间的归属。
- 空间内必须至少保留一位管理员：降级最后一名管理员、移除最后一名管理员（含其自行退出）均被拒绝并提示。

### 1.1 成员列表

**功能描述**：以表格呈现当前活跃工作空间的成员构成，并提供姓名/邮箱搜索与角色筛选。  
**功能点**：

- 表格列：用户（头像、姓名或用户名、邮箱）、角色、加入时间、操作；管理员角色标签使用强调样式。
- 面板标题旁展示成员总数；底部分页每页 20 条，并展示「共 N 人 · 每页 20 条」说明。
- 姓名/邮箱搜索：关键词输入停顿后自动检索，清空关键词立即恢复全量。
- 角色筛选：按本空间可分配角色（不含分组角色）筛选，可清除恢复「全部角色」。
- 无结果时展示空态「暂无符合条件的成员」；加载中展示加载态，失败时给出错误提示。

**业务规则**：

- 搜索与角色筛选可组合使用；任一筛选条件变化后回到第一页，翻页时保留当前筛选条件。
- 邮箱为敏感信息：仅空间管理员可见完整邮箱，普通成员一律看到脱敏后的邮箱（含自己所在行）。

### 1.2 成员操作

**功能描述**：调整成员角色、移除其他成员，或由成员自行退出当前空间。  
**功能点**：

- 改角色：具备成员管理权限的用户可对任意成员行内修改角色，选中即提交，成功后就地更新并提示；失败时给出错误提示并刷新为列表最新数据。
- 角色选项与角色筛选一致，仅列出本空间可分配角色。
- 移除成员：具备成员管理权限的用户可移除其他成员，需二次确认后执行。
- 自行退出：任一成员（含无成员管理权限的普通成员）在自己的行上看到「退出」入口，二次确认提示退出后将无法访问此空间的资源；退出成功后离开当前空间并返回工作空间列表。
- 无成员管理权限的用户对他人的行不展示任何操作。

**业务规则**：

- 行操作按对象区分：操作自己显示「退出」，操作他人显示「移除」；两种操作均要求至少保留一位管理员。
- 具备成员管理权限的用户对自己同样可改角色，受「至少保留一位管理员」约束。

### 1.3 直接邀请成员

**功能描述**：从平台业务用户中搜索并批量邀请其加入当前活跃工作空间。  
**功能点**：

- 弹窗提供多选搜索框，可按姓名、用户名或邮箱搜索平台内启用状态的业务用户；弹窗提示新成员将以空间成员角色加入。
- 未选择任何用户时提示「请至少选择一个用户」，不允许提交。
- 提交后按汇总结果提示：成功添加的人数，以及因已是本空间成员而被跳过的人数。

**业务规则**：

- 邀请候选列表不预先排除已有成员，「已是本空间成员」在提交结果中以跳过人数提示，不重复添加、不视为失败。
- 仅具备成员管理权限的用户可打开该弹窗并提交邀请。

### 1.4 邀请链接

**功能描述**：生成、复制与失效邀请链接，通过链接扩招成员，并跟踪其使用情况。  
**功能点**：

- 生成链接：可选设置过期时间（留空表示永不过期）与最大使用次数（1~10000，留空表示不限）；创建成功后弹窗展示完整链接并提供复制。
- 列表列：邀请链接（脱敏凭据）、使用次数、过期时间、状态、创建时间、操作；分页每页 20 条。
- 状态取值：有效、已达上限、已过期、已失效；未设置过期时间的展示「永不过期」。
- 使用次数展示为「已用次数 / 最大次数」，未设置上限时最大次数显示「不限」。
- 复制：将完整链接与邀请说明文案一并写入剪贴板；页头「复制邀请链接」快捷复制最近一条有效链接，无有效链接时提示「暂无可复制的有效邀请链接」。
- 失效：需二次确认，失效后该链接不可再使用。

**业务规则**：

- 列表仅展示脱敏凭据；完整凭据仅在创建成功弹窗或用户明确执行复制时向具备邀请管理权限的用户展示。
- 仅「有效」与「已达上限」的链接可复制、可失效；已过期或已失效的链接不可再次复制，也不提供失效操作。
- 通过链接加入的成员按默认角色进入；加入流程见 [07 加入空间](07-srs-space-management-invite-join.md)。', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000003', '["空间管理","成员"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '21 days', CURRENT_TIMESTAMP - INTERVAL '21 days', CURRENT_TIMESTAMP - INTERVAL '21 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed003b-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0017-0000-4000-8000-000000000000', 'V1.0', 'REQ-014', '项目列表页',
        '# 软件测试平台——项目列表页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 项目列表页

**功能描述**：管理当前活跃工作空间下的项目，提供项目浏览、搜索、创建、编辑、设为默认、归档与删除能力；菜单项按「项目查看权限」过滤，公共约定见总览分册（`../02-srs-overview.md`）。  
**功能点**：

- 页面由页头、状态与搜索筛选条、项目卡片网格以及新建/编辑对话框构成；页头展示标题「项目列表」与当前空间项目说明。
- 进入页面自动加载项目列表与状态数量；切换活跃工作空间后进入该空间的项目列表，两者均按新空间重新加载。
- 具备项目创建能力时展示「新建项目」入口，否则不展示；空间内无任何项目时展示创建引导与「创建第一个项目」。
- 加载过程中展示骨架占位，刷新期间提示「正在刷新项目列表…」且不清空已加载内容。

**业务规则**：

- 所有数据与操作仅作用于当前活跃工作空间；缺少项目查看权限时不展示菜单入口。
- 空间无项目的判定口径：无关键字、处于活跃分段，且活跃与已归档数量之和为 0。
- 当前状态或关键字无匹配结果时展示筛选空态并提供「清除筛选」，恢复到活跃分段且无关键字。
- 加载失败时展示错误提示与「重试」，重试保留当前状态分段与关键字重新加载。

### 1.1 项目列表展示与筛选

**功能描述**：以卡片网格呈现当前空间的项目及其状态与关键属性，并支持按名称或描述检索。  
**功能点**：

- 卡片展示状态、默认项目标识、名称、描述（无描述展示占位文案）、起止日期、创建人与创建时间。
- 操作按状态与能力呈现：活跃卡片提供「进入」「设为默认」，具备编辑能力时另有「编辑」，空间管理员另有「归档」；已归档卡片仅提供「启封」「删除」（限空间管理员）与「只读」标识。
- 状态分段为「活跃」「已归档」并标注真实数量，默认选中活跃；每页 20 条，滚动至底部自动加载后续项目，加载完提示「没有更多项目」。
- 搜索框按项目名称或描述包含匹配，输入后自动检索、回车立即检索、清空恢复全部结果；结果固定按最近更新时间倒序，筛选条常驻提示「按最近更新排序」。

**业务规则**：

- 状态数量由独立统计提供：应用当前关键字、忽略当前状态，不得由当前列表数据推导。
- 仅活跃项目可进入；进入项目时校验该项目属于当前活跃工作空间，不属于则拒绝并提示项目不存在。
- 已归档项目不可进入、不可编辑。

### 1.2 创建与编辑项目

**功能描述**：在当前空间下新建项目，并维护已有项目的名称、描述与起止时间。  
**功能点**：

- 字段：名称（必填，最长 100 字符）、描述、开始时间、结束时间（后三者可选）；「取消」关闭对话框，「创建」「保存」校验后提交，成功提示「项目已创建」或「已保存」并刷新列表。
- 项目自动归属当前活跃空间，创建后状态为「活跃」；编辑对话框复用创建字段，标题为「编辑项目」。
- 仅空间管理员或项目创建人可见「编辑」入口。

**业务规则**：

- 名称在当前工作空间内唯一，重名创建或保存失败并提示，对话框保留已填内容供修改。
- 结束时间早于开始时间时创建失败并提示；提交过程中禁止重复提交。
- 编辑后所属工作空间不可变更；已归档项目不可编辑。
- 保存仅更新本次填写的字段，清空描述或起止时间不会清除原值。

### 1.3 设为默认项目

**功能描述**：将活跃项目标记为当前用户的个人默认项目。  
**功能点**：

- 活跃卡片展示「设为默认」，已是默认项目的不再展示；成功后提示已设为默认并刷新列表。

**业务规则**：

- 同一工作空间内每位成员至多一个个人默认项目，重新设置即替换原设置。
- 该操作仅维护个人偏好，不进入项目，也不改变项目数据。

### 1.4 归档、启封与删除

**功能描述**：将活跃项目归档为只读、将已归档项目恢复为活跃，或从当前空间移除不再需要的项目。  
**功能点**：

- 「归档」「启封」「删除」仅空间管理员可见，且「删除」只出现在已归档卡片上；执行前弹出确认，确认后执行并刷新列表，取消则不执行。

**业务规则**：

- 归档前项目下须无未开始或进行中的测试计划，存在时归档失败并提示。
- 归档成功后清除工作空间成员对该项目的个人默认项目设置，启封不涉及该清理；启封后项目恢复活跃。
- 删除前提示删除后不可恢复，成功后提示「已删除」，列表与状态数量随即刷新，已删除项目不再展示。
- 删除时同样清除工作空间成员对该项目的个人默认项目设置。', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000004', '["空间管理","项目"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed003c-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0018-0000-4000-8000-000000000000', 'V1.0', 'REQ-015', '加入空间页',
        '# 软件测试平台——加入空间页

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---
## 1. 加入空间

**功能描述**：通过邀请链接加入工作空间的公开入口，无需登录即可打开；页面先校验链接凭据，再引导完成邮箱确认、密码验证或注册，成功后直接登录进入该空间；公共约定见[总览分册](../02-srs-overview.md)。
**功能点**：

- 页面打开即自动校验链接凭据，通过后按「输入邮箱 → 验证密码或创建账号」逐步展开，全流程无需预先登录。
- 凭据取自邀请方分享的链接中携带的令牌；加载、错误与失败状态均在页面内呈现，并提供可继续操作的入口。

**业务规则**：

- 链接凭据绑定目标空间：设置有效期的到期后失效；设置使用次数上限的，每成功加入一次计一次、达到上限后失效；被撤销后立即失效；两者均未设置的长期有效且可重复使用。
- 加入与登录一步完成，成功后无需再输入一次密码；每步提交期间按钮进入加载态并禁用，防止重复提交。

### 1.1 链接校验与状态展示

**功能描述**：进入页面即校验链接凭据，并将结果以加载态或错误态呈现。
**功能点**：

- 校验中展示「正在验证邀请链接...」；有效时展示空间名称（「您将被加入「××」工作空间」）并进入邮箱输入步骤。
- 无效时整卡切换为「邀请链接无效」，展示具体原因并提供「返回登录」按钮。

**业务规则**：

- 链接缺少令牌参数时提示「邀请链接无效：缺少令牌参数」；校验返回无效时统一提示「邀请链接已失效或不存在」。
- 校验请求本身失败（网络异常、触发限流）时进入错误态并展示该失败原因。

### 1.2 邮箱确认与账号分支

**功能描述**：确认将加入的空间，并通过邮箱区分已有账号与新用户。
**功能点**：

- 展示空间名称提示、邮箱输入框与「下一步」按钮；提交后按邮箱是否已有账号进入密码验证步骤或创建账号步骤。
- 查询失败时展示错误信息并停留在当前步骤，已填内容不丢失。

**业务规则**：

- 未输入邮箱时提示「请输入邮箱」，不发起查询；邮箱格式不合法时提示「邮箱格式不正确」。

### 1.3 账号验证与注册加入

**功能描述**：已有账号通过密码验证直接加入并登录；未检测到账号时一次提交完成注册并加入。
**功能点**：

- 已有账号：只读邮箱、密码输入框（含实时强度提示）与「加入并登录」；新用户：只读邮箱、姓名输入框（最多 50 字符）、密码输入框与「创建并加入」。
- 两分支均提供「← 返回」回到邮箱步骤；密码在提交时校验，不逐字符拦截。

**业务规则**：

- 密码须为 8~64 个字符：未输入提示「请输入密码」，长度不符提示「密码长度为 8-64 个字符」，与平台其他设置密码处口径一致。
- 姓名必填，去除首尾空白后为空时提示「请输入姓名」；密码与已有账号不匹配时提示「密码错误，请重新输入」并停留在当前步骤。

### 1.4 加入成功与失败提示

**功能描述**：加入成功即完成登录并落到被邀请的空间，失败时给出可理解的原因。
**功能点**：

- 成功后以该空间为当前空间并进入其项目列表页；已有账号提示「已成功加入工作空间」，新账号提示「欢迎加入！已自动创建账号并登录。」。
- 各步骤失败以页面消息展示原因并停留在当前步骤，保留已填内容。

**业务规则**：

- 该邮箱已是目标空间成员时重复加入不报错，按已加入状态直接登录进入。
- 典型失败原因包括凭据不存在或已失效、已过期、已被撤销、已达最大使用次数、密码错误、邮箱格式不正确、请求过于频繁；提交失败不创建账号、不进入空间，无效链接的错误态始终提供「返回登录」入口。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-00000000000a', '["空间管理","邀请"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '19 days', CURRENT_TIMESTAMP - INTERVAL '19 days', CURRENT_TIMESTAMP - INTERVAL '19 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed003d-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0019-0000-4000-8000-000000000000', 'V1.0', 'REQ-016', '业务功能模块',
        '# 软件测试平台——业务功能模块

**文档版本**：V1.0  
**日期**：2026-09-26  
**状态**：起草中

---

## 1. 业务功能模块（仅业务用户可访问）


业务功能模块下的空间管理需求（我的空间、空间信息、成员管理、项目列表、加入空间）已拆分至独立目录，见 [`../02-space-management/01-readme.md`](../02-space-management/01-readme.md)；活跃工作空间的机制与约定亦见该目录总览分册。

### 1.1 功能测试（项目工作区）

进入项目后，所有功能测试活动均在当前选定的项目内进行，当前活跃工作空间上下文不变。

#### 1.1.1 测试用例管理

**模块管理**  

- 项目下可创建无限层级的模块树，节点类型分为**目录**（可包含子目录和文档）和**文档**（不可包含子模块，仅关联一个脑图）。  
- 支持新建、重命名、删除、拖拽排序。删除目录时需目录为空；删除文档将同时删除其内部所有用例数据。  

**脑图实时协作编辑**  

- 打开文档后进入脑图编辑器，支持同时有多名用户编辑同一文档，编辑操作实时同步给其他用户。  
- 脑图由任意节点构成，用户可自由创建、移动、删除节点，并可编辑节点文本、调整层级关系。  
- 节点可被标记为**用例节点**，用例节点额外拥有优先级、前置条件、测试步骤、预期结果、标签等属性。  
- 编辑过程中系统应保证数据一致性，多人操作冲突时以合理策略合并。  

**关联与引用**  

- 在测试计划或测试评审中，用户可以从模块树中选取具体的用例节点进行关联。  
- 关联完成后，系统生成该时刻的模块和文档快照，以保证后续历史查看不受用例修改影响。

#### 1.1.2 测试评审管理

**概述**  
测试评审仅针对测试用例，以脑图快照为基础，支持多位参与者对节点进行标记和评论，同一节点可被多次评审，形成评审记录。

**发起评审**  

- 用户从模块树中选择需评审的用例节点（可跨文档多选），指定发起人（默认自己）和若干参与者（必选至少一人），填写标题和描述。  
- 系统根据所选节点，自动生成当时的模块快照和文档节点快照，评审状态初始为“待评审”，首次提交标记后自动转入“进行中”。

**评审详情展示**  

- 评审详情以**关联节点树**的形式呈现：仅展示与关联节点相关的模块路径和脑图子树，高亮被标记为关联的用例节点，并显示各节点的最新评审标记状态。  
- 涉及多个文档时，详情页左侧提供快照文档树，点击文档切换对应的快照脑图。  
- 评审列表支持按标题关键字模糊查询与状态筛选（待评审 / 进行中 / 已通过 / 已驳回），并展示关联用例数、进度、通过率与参与者。

**评审操作**  

- 参与者可点击树上的任意节点，进行**标记**（仅用例节点可标记“通过”、“不通过”或重置回“待评审”）或**添加评论**。标记结果回显在节点上。  
- 每次提交生成一条评审记录，系统同步更新该节点的最后标记状态（用于树形视图的快速展示）。  
- 所有参与者的操作实时保存，其他参与者可即时看到最新状态。  

**调整规划用例**  

- 评审未结束（待评审 / 进行中）时，可重新选择关联的用例：支持新增文档、移除文档、调整文档内的用例集合。  
- 系统按差量更新快照：新增文档生成快照，移除文档删除快照（已产生的标记/评论记录保留作审计），保留文档补入快照后新建的节点并重刷关联状态（取消关联的节点保留已有标记，仅不再计入统计）。

**评审记录查看**  

- 对于任意节点，可查看其完整的评审历史，包含历次标记变更和评论。  

**完成评审**  

- 发起人可手动关闭评审，状态变为“已通过”，此后不可再新增或修改评审记录，快照冻结。  
- 仅当全部关联用例评审通过（无待评审、无不通过）时才允许完成评审。

**驳回评审**  

- 评审未完成（待评审 / 进行中）时，发起人可驳回评审，状态变为“已驳回”，此后不可再新增或修改评审记录，快照冻结。  
- 已驳回的评审可由发起人重新发起，状态回到“进行中”，既有标记与评论保留，参与者可继续评审。  
- 评审状态在列表与详情页统一展示为：待评审 / 进行中 / 已通过 / 已驳回。  

**同步最新用例**  

- 在“评审中”状态下，发起人可执行同步操作。系统根据关联的原始节点ID，用当前最新的模块结构和用例内容更新快照。若某原始节点已被删除，则在快照中标记为“已删除”，其已有评审记录仅可查看。  
- 同步后，保留所有已产生的评审记录。  

**业务规则**：  

- 参与者必须是当前活跃工作空间成员。  
- 发起人不能同时作为参与者。  
- 评审进入终态（已通过 / 已驳回）后，任何人均只能查看，不能编辑；已驳回的评审可由发起人重新发起。

#### 1.1.3 测试计划管理

**概述**  
测试计划关联测试用例并管理执行过程，基于快照进行，支持多次执行记录，以追踪执行历史。

**创建计划与关联用例**  

- 用户创建计划，输入名称、起止时间、负责人、执行环境、描述等信息。  
- 从模块树中选择需执行的用例节点，系统生成当时的模块和文档快照，并为每个关联的用例节点初始化执行状态为“未执行”。

**计划详情展示**  

- 计划详情以**关联节点树**形式展示，高亮关联的用例节点，并根据最后一次执行结果显示状态图标（通过/失败/阻塞/待执行）。  
- 涉及多个文档时，详情页左侧提供快照文档树，点击文档切换对应的快照脑图。  
- 计划列表支持按名称关键字模糊查询与状态筛选。  

**执行跟踪**  

- 负责人或指定的执行人可点击树上的关联用例节点，标记执行结果（通过/失败/阻塞/待执行）并填写备注，结果回显在节点上。  
- 每次提交生成一条执行记录，系统同步更新该节点的最后执行结果。  
- 同一节点可被多次执行，所有历史记录可追溯。  

**调整规划用例**  

- 计划未结束（新建/进行中）时，可重新选择关联的用例，差量更新规则与测试评审一致；新补入快照的用例初始状态为“待执行”。

**执行历史**  

- 对任一节点可查看其完整的执行记录列表，包含每次结果、执行人和备注。  

**计划状态流转**  

- 计划状态按“新建 → 进行中 → 已完成 → 已关闭”流转。  
- 新建或进行中的计划可由负责人阻塞为“已阻塞”；已阻塞的计划仅可由负责人恢复为“进行中”，或直接删除。  
- 阻塞期间计划暂停执行：不可标记执行结果、调整规划用例、同步最新用例或完成。  
- 关闭计划时，系统检查是否有未执行用例，若有则提示。关闭后不可再修改执行结果。  

**同步最新用例**  

- 在“进行中”状态下，负责人可同步快照至最新版本，保留所有已产生的执行记录。已删除的原始节点将在快照中标记，其结果只读。  

**业务规则**：  

- 计划负责人和实际执行人必须是当前工作空间成员。  
- 只有被关联的用例节点才可以被标记执行结果。  
- 阻塞、恢复、完成与删除计划仅限计划负责人操作。', 'archived', 'medium', '7e57001f-0000-4000-8000-000000000006', '["功能测试"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed003e-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001a-0000-4000-8000-000000000000', 'V1.1', 'REQ-017', '需求管理',
        '# 软件测试平台——需求管理

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. 需求管理

### 1.1 总体描述

需求管理用于在项目内登记与维护业务需求，是测试追溯链的起点：模块、脑图用例文档与测试用例由需求派生，测试评审与测试计划经用例快照回溯至需求。需求管理与 AI 能力配合使用，本分册只定义需求本身的管理行为。

**需求拆解**：需求以单层**需求条目**管理，不设下级拆分单元。导入大需求文档时，由 **AI 判定模块边界，将文档按模块拆分为多条需求**（每条需求归属一个模块）。拆解建议为待采纳态，见 1.4。

**层级结构**：需求条目归属模块。需求条目是追溯的最小单位，下游派生均以需求为粒度。

**状态流转**：需求条目在**草稿**、**已确认**、**已变更**之间流转，另有**已归档**：

- 草稿 → 已确认（人工确认）；
- 已确认 → 已变更（修改标题、描述或所属模块）；
- 已变更 → 已确认（重新确认）；
- 非归档条目 → 已归档（二次确认后归档，条目只读）。

**编号规则**：需求编号为 `REQ-<项目内序号>`，项目内唯一。

**权限**：拥有需求管理权限的用户在功能测试侧栏可见「需求列表」子项；需求数据限定在当前项目内，操作人须为当前工作空间成员。

**定义与缩写**：

| 术语 | 定义 |
| ---- | ---- |
| 需求条目 | 项目内一条业务需求的登记单位，归属一个模块，是测试追溯链的起点 |
| 需求拆解 | 将一份大需求文档或一条粗粒度需求，按模块拆分为多条需求条目的操作；由 AI 判定拆分边界，人工采纳生效 |
| 被测业务系统 | 平台中的一个项目即一个被测业务系统 |
| 版本 | 需求所归属的被测业务系统版本（如 `V2.3`），文本格式、允许为空 |

### 1.2 需求列表（US-RM-001）

**功能描述**：以分页表格展示当前项目的需求条目，提供筛选与关键词搜索，并可直接发起新建与导入。  
**功能点**：

- 表格列依次为：编号、标题、所属模块、版本、状态、负责人、更新时间、操作。
- 更多筛选：状态、所属模块、负责人、版本四项，入口角标显示已设置条件数量，提供「查询」与「重置」。
- 关键词搜索：支持按编号（前缀或后缀）或标题（包含）匹配；回车、失焦、清空立即查询，输入停止约 1 秒后自动查询。
- 分页：默认每页 20 条，可切换 20 / 50 / 100；查询条件变化时回到第一页，翻页保留当前条件。
- 行操作：「详情」（常显）；「确认」（草稿或已变更时可用）；「更多」菜单含「编辑」（非归档态）、「归档」（非归档态）、「取消归档」（归档态），操作列内容右对齐。
- 页头提供「新建需求」与「导入需求」入口。

**业务规则**：

- 重置清空全部筛选条件与关键词后重新查询。
- 归档需二次确认；归档后条目只读，不再作为 AI 生成的有效输入。
- 平台不提供需求删除功能，仅支持归档，以保留需求的历史与变更记录。

### 1.3 需求详情与编辑（US-RM-002）

**功能描述**：查看需求条目的全量信息，维护其属性与内容，执行确认与归档操作。  
**功能点**：

- 顶栏展示编号、标题、状态与操作按钮（确认、归档）；非归档态标题可直接编辑。
- 属性栏展示所属模块、版本、负责人、优先级、标签、描述（支持 Markdown）、来源；非归档态均可编辑并保存。
- 内容区展示需求描述全文；来源为导入时，展示来源附件与原文定位，可回看原文。
- 变更记录：以时间线展示历次状态变更与内容修改，包含操作人、时间与变更摘要。

**业务规则**：

- 保存仅提交实际发生变化的字段，未修改的字段保持原值。
- 修改标题、描述或所属模块触发「已确认 → 已变更」状态流转；仅修改负责人、版本、标签、优先级等属性不触发。
- 非归档态可编辑，归档后整体只读，仅保留「取消归档」操作。

### 1.4 需求导入与拆解（US-RM-003）

**功能描述**：将外部大需求文档导入平台，并由 AI 按模块拆分为多条需求。  
**功能点**：

- 支持上传 Markdown、Word 文档与图片附件（多模态输入），单个文件不超过 20MB。
- 导入提交即启动执行并跳转任务详情，可查看执行过程（当前步骤、轨迹）、待采纳与已终止（含终止原因）。
- AI 判定模块边界，产出**多条需求条目建议**：每条含标题、描述、所属模块与来源原文定位；整体为**待采纳态**，操作人可逐条采纳、修改后采纳、批量采纳或驳回。
- 导入解析时 AI 从文档识别被测业务系统版本，作为**文档级建议**随拆分建议一并展示；采纳面板可修改或清空，采纳后写入所选条目，识别不到时留空待手工补录。
- 采纳时可调整归属项目、模块与版本；采纳即内容确认，条目按编号规则生成编号并直接进入已确认状态。
- 原始文件作为来源附件保留，可在条目详情中回看与下载，拆分结果可定位到原文位置。

**业务规则**：

- 解析与拆分结果不直接生效，未采纳不落库。
- 解析失败时执行终止并给出原因，原始文件保留可再次执行。
- 手工创建的需求初始状态为草稿，须经 1.3 的确认流程进入已确认。

### 1.5 数据需求

**功能描述**：定义需求管理涉及的数据实体及其关系（实体级，不含表结构）。  
**功能点**：

- **需求条目**：编号、标题、描述、所属模块、版本、状态、负责人、优先级、标签、来源（手工 / 导入）、来源附件、所属项目。
- **需求变更记录**：需求标识、操作人、操作类型、变更前后摘要、时间。
- **拆解记录**：来源文档标识、拆分建议与采纳结果、操作人、时间。

**业务规则**：

- 所有实体通过所属项目归属到工作空间，遵循既有的数据隔离约定。
- 需求条目不物理删除，归档为终态入口。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 初始版本 |
| V1.0 | 2026-10-15 | 「需求管理」菜单下设需求列表与追溯矩阵两个子入口，可见条件扩展为需求管理权限或追溯查看权限任一 |
| V1.0 | 2026-10-15 | 列表行操作调整：「归档」收进「更多」菜单，操作列右对齐 |
| V1.0 | 2026-10-15 | 下线条目内「AI 拆分」入口：拆解仅由文档导入单一入口承载，1.1 / 1.3 / 1.4 与数据需求同步收敛 |
| V1.0 | 2026-10-16 | 拆解建议「待确认态」统一为「待采纳态」，对齐 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`） |
| V1.0 | 2026-10-16 | 对齐 AI Task 双平面口径：导入提交即启动执行并跳转任务详情（过程 / 待采纳 / 已终止），解析失败改执行终止可再次执行，拆解术语与面板改「采纳」 |
| V1.0 | 2026-10-16 | 追溯矩阵需求并入本模块：追溯相关能力定义指向追溯矩阵分册，「追溯边」「孤儿检测」改「对应关系」「无来源用例检测」，人工修正动作定名「更正来源 / 清除关联 / 恢复」 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：删除 US-RM-004 追溯视图入口与 US-RM-005 需求变更与影响标记（原 1.7 数据需求顺延为 1.5），权限恢复为仅需求管理权限、菜单只设需求列表子入口，列表移除覆盖状态列，术语与数据需求中的追溯相关条目移除 |
| V1.0 | 2026-10-18 | 需求管理并入功能测试模块：1.1 权限口径改为功能测试侧栏「需求列表」子项，分册迁入功能测试模块目录 |', 'changed', 'high', '7e57001f-0000-4000-8000-000000000002', '["功能测试","需求管理"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '17 days', CURRENT_TIMESTAMP - INTERVAL '17 days', CURRENT_TIMESTAMP - INTERVAL '17 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed003f-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001e-0000-4000-8000-000000000000', 'V1.0', 'REQ-018', '缺陷管理',
        '# 软件测试平台——缺陷管理

**文档版本**：V1.0  
**日期**：2026-10-10  
**状态**：起草中

---

## 1. 缺陷管理

### 1.1 总体描述

缺陷管理用于登记项目测试过程中发现的缺陷，并跟踪其从受理到关闭的完整处理过程。本分册与《业务功能模块》分册 `docs/01-requirements/03-function-testing/02-srs-business-features.md` 并列，缺陷管理的功能需求以本分册为准。

**缺陷生命周期**：缺陷在四个状态间流转——**激活**、**已修复**、**已拒绝**、**已关闭**：

- 激活 → 已修复 / 已拒绝；
- 已修复 → 已关闭 / 激活；
- 已拒绝 → 已关闭 / 激活；
- 已关闭 → 激活。

**视图**：列表视图（表格 + 筛选）与看板视图（按状态分列），页内可随时切换，两视图共用同一套筛选条件。

**权限**：拥有缺陷查看权限的用户可见「缺陷管理」菜单并进入本模块；缺陷数据限定在当前项目内，处理人须为当前工作空间成员。

**枚举取值**：

- 严重等级：致命 / 严重 / 一般 / 轻微；
- 优先级：高 / 中 / 低；
- 缺陷类型：代码错误、界面优化、设计缺陷、配置相关、安装部署、安全相关、性能问题、标准规范、其他；
- 解决方案：已解决、设计如此、重复缺陷、外部原因、无法重现、延期处理、不予解决。

### 1.2 缺陷列表

**功能描述**：以分页表格展示当前项目缺陷，提供快捷过滤、组合筛选与关键词搜索，并可直接对单条缺陷发起处理操作。  
**功能点**：

- 表格列依次为：ID（短标识）、标题（超长截断、悬停完整展示）、类型、严重等级、优先级、状态、创建人、处理人、解决人、解决时间、解决方案、关闭时间、创建时间、操作；已确认缺陷在状态列附加「已确认」标记。
- 快捷过滤（单选，切换即查询）：全部、未修复的、由我创建、指派给我、由我修复、由我关闭。
- 更多筛选：状态、类型、严重等级、优先级四项，入口角标显示已设置条件数量，提供「查询」与「重置」。
- 关键词搜索：支持按 ID（前缀或后缀）或标题（包含）匹配；回车、失焦、清空立即查询，输入停止约 1 秒后自动查询。
- 分页：默认每页 20 条，可切换 20 / 50 / 100；查询条件变化时回到第一页，翻页保留当前条件。
- 行操作：「详情」（常显）；「解决」「拒绝」（激活态）；「关闭」（已修复 / 已拒绝）；「激活」（已关闭）；「更多」菜单含「确认」（激活且未确认）、「激活」（已修复 / 已拒绝）、「指派」（非已关闭）、「复制」（携带本缺陷跳转新建表单）。
- 页头提供「提交缺陷」入口与视图切换（列表 / 看板）。

**业务规则**：

- 快捷过滤「未修复的」（仅激活状态）与手动选择的状态条件同时存在时，以快捷过滤为准。
- 重置清空全部筛选条件、快捷过滤与关键词后重新查询。
- 指派操作必须选择处理人，处理人须为当前工作空间成员。
- 平台不提供缺陷删除功能。

### 1.3 看板视图

**功能描述**：按状态分列展示缺陷卡片，通过拖拽直接完成状态流转。  
**功能点**：

- 看板固定为三列：**激活**、**已修复**、**已关闭**，列头展示该列缺陷总数；**已拒绝**缺陷不在看板展示，仅在列表视图可见。
- 卡片展示标题、严重等级与处理人（有处理人时），点击进入详情。
- 拖拽时合法目标列高亮、非法目标列置灰且不可放置；拖向「已修复」先打开解决对话框（必填解决方案与备注），其余合法目标需填写变更说明。
- 列表的筛选条件与快捷过滤在看板下同样生效；状态条件与列状态不一致时该列不展示数据。

**业务规则**：

- 看板内可完成的流转：激活 → 已修复、已修复 → 已关闭、已修复 → 激活、已关闭 → 激活。
- 涉及已拒绝状态的流转（激活 → 拒绝、已拒绝 → 关闭 / 激活）只能在列表视图或详情页进行。

### 1.4 状态流转与处理操作

**功能描述**：按 1.1 的状态机推进缺陷处理，记录每次变更的说明与操作历史。  
**功能点**：

- **解决**：选择解决方案并填写备注；解决方案为「重复缺陷」时必须指定原始缺陷，且不能选择缺陷自身。
- **拒绝**：填写说明。
- **关闭**：由已修复或已拒绝进入，填写说明。
- **激活（重开）**：由已修复、已拒绝或已关闭进入，填写说明；缺陷累计重开次数，并在详情页展示「已激活 N 次」。
- **确认**：仅激活且未确认的缺陷可确认，经二次确认后标记为有效待处理。
- **指派**：重新选择处理人；已关闭缺陷不可指派。
- 每次状态变更与信息更新均记录操作人、操作类型与说明，形成操作记录（见 1.6）。

**业务规则**：

- 状态流转必须符合 1.1 的转换关系，非法流转被拒绝并给出提示。
- 关闭、拒绝与重开必须填写说明，未填写不予受理。
- 解决必须选择解决方案并填写备注，未填写不予受理；选择「重复缺陷」时必须指定存在的原始缺陷。
- 解决或拒绝后，处理人回到创建人；激活后，处理人指向原解决人（无则原拒绝人）。

### 1.5 提交缺陷

**功能描述**：在项目内登记新缺陷并指派处理人。  
**功能点**：

- 表单字段：
  - 标题：必填，最多 300 字；
  - 重现步骤：选填，所见即所得编辑（编辑区即最终渲染效果，无需查看或编辑源码），支持 Markdown 常用语法；可在正文直接粘贴或经编辑器插入图片，图片随正文一并保存；
  - 附件：选填，可多选，单个文件不超过 10MB，创建成功后上传；
  - 缺陷类型：必填，默认「代码错误」；
  - 所属模块：选填，从项目模块树中选择；
  - 严重等级：必填，默认「一般」；
  - 优先级：必填，默认「中」；
  - 截止日期：选填；
  - 关键词：选填，多个关键词以空格分隔，最多 255 字；
  - 指派给：必填，从当前工作空间成员中选择；
  - 关联用例：选填，单选，再次选择将替换已选用例；
  - 关联计划：选填，仅可选择进行中的计划。
- 校验通过后创建缺陷并返回缺陷列表；创建失败时保留已填内容并提示。
- 超过 10MB 的附件在选择时即被忽略并提示，可手动移除已选附件。
- 正文图片受同一上限约束：单张超过 10MB 或类型不在附件允许范围内时提示并忽略，正文其余内容保留。
- 「复制」入口以源缺陷回填本表单。

**业务规则**：

- 必填项未通过校验时不提交。
- 处理人必选且须为当前工作空间成员。
- 复制回填标题、类型、所属模块、严重等级、优先级、截止日期、关键词、重现步骤与关联用例；不带处理人与状态信息；关联计划仅在仍为进行中计划时保留。
- 取消提交返回缺陷列表。

### 1.6 缺陷详情

**功能描述**：查看缺陷全量信息、编辑属性与内容、管理附件并执行全部处理操作。  
**功能点**：

- 顶栏展示短 ID、标题、状态与已激活次数；非关闭态标题可直接编辑（最多 300 字），并提供「提交缺陷」入口。
- 重现步骤：非关闭态可编辑（所见即所得，编辑区即渲染效果；可粘贴或插入图片，口径同 1.5），关闭态只读渲染；正文内嵌图片在查看与只读态下正常渲染；无内容时展示空态。
- 附件：展示文件名、大小、上传人与上传时间；非关闭态可上传（单个不超过 10MB）与删除（需二次确认），任何状态均可下载；无附件时展示空态。
- 操作记录：以时间线展示每条记录的操作人、操作与说明。
- 属性栏展示缺陷类型、所属模块、严重等级、优先级、截止日期、关键词、处理人、关联用例与关联计划；非关闭态均可编辑并保存，关闭态全部只读。
- 关联用例：悬停预览用例标题、优先级、所属文档及前置条件、步骤、预期内容，可打开所在文档；支持选择、更换与清除，清除经保存生效。
- 修改关联计划时可从全部计划中选择。
- 底栏操作按状态显隐：确认（激活且未确认）、解决、拒绝、关闭、激活、保存。

**业务规则**：

- 已关闭缺陷整体只读：不可编辑、不可保存、不可上传或删除附件，仅保留「激活」操作。
- 保存仅提交实际发生变化的字段，未修改的字段保持原值；处理人的变更经指派动作完成。
- 附件与正文图片的单个文件大小上限均为 10MB，超出不予上传。
- 状态流转操作的前置条件与必填项见 1.4。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 初始版本 |
| V1.0 | 2026-10-10 | 缺陷正文支持贴入图片：提交与详情可粘贴/插入图片（单张 10MB、类型同附件），详情查看与关闭态只读渲染正文图片 |
| V1.0 | 2026-10-10 | 重现步骤改为所见即所得编辑（编辑区即渲染效果），不再要求查看或编辑 Markdown 源码 |', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000006', '["缺陷管理"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '16 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0040-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001f-0000-4000-8000-000000000000', 'V1.2', 'REQ-019', '接口测试需求规格说明书总览',
        '# 软件测试平台——接口测试需求规格说明书总览

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

本文档定义软件测试平台**接口测试**业务域的功能需求与非功能需求，面向项目经理、业务分析人员、测试工程师及开发团队，确保各方对接口测试模块的行为、规则和边界有一致理解。

结合本平台既有的空间管理、功能测试、缺陷管理等能力域进行裁剪与适配；凡与本文档冲突之处，以本文档为准。

### 1.2 范围

本文档覆盖接口测试业务域的完整链路：

1. **快速调试** — 面向开发与联调阶段的单请求快速验证，支持**服务端执行**与**本地执行**两种模式（支持导入 cURL）；
2. **接口管理** — 接口资产的集中管理（新建、导入、引用关系、变更历史）；
3. **Mock 服务** — 模拟接口返回，解除联调依赖；
4. **测试场景** — 多步骤编排的场景级自动化（步骤即取样器，支持 http/jdbc）；
5. **接口测试报告** — 场景执行结果的查看、分享与删除；
6. **定时任务** — 接口导入与场景执行的统一周期调度（Cron 表达式、执行记录与失败查看），任务归属项目；
7. **项目设置** — 平台级统一的项目设置入口，本域在其下定义两个配置模块：**环境管理**（项目级环境配置：默认配置、数据源、全局变量、全局前置/后置处理器，配置项与 Ryze 对应一致）、**公共组件**（项目级可复用的前置/后置处理器、验证器、提取器资产，快速调试与测试场景中可复制引入使用）；入口平台级统一，配置项按业务域归属标注，避免跨业务域配置相互干扰（见 3.7）；
8. **公共能力** — 请求参数模型、前置/后置处理器、验证器、内置函数、执行引擎。

不包含（首期范围）：非 HTTP/JDBC 协议支持（首期仅 HTTP/HTTPS 与 JDBC 取样器，数据模型与执行引擎按多协议扩展预留）、Mock 服务独立部署形态。

### 1.3 与既有文档的关系

- 平台总体需求（系统管理、空间管理、功能测试、缺陷管理）见 `docs/01-requirements/01-readme.md`（V1.0），本文档为接口测试业务域的独立需求基线，两者为并列关系。
- 术语、角色模型（系统管理员 / 业务用户、RBAC 权限、`global` / `workspace` / `project` 作用域）、数据隔离原则等沿用 `docs/01-requirements/01-readme.md` V1.0 定义，本文档不重复描述。
- 本文档新增定义见 1.4 定义与缩写。
- 本文档版本随平台文档基线统一编排（见文档头），不再独立演进版本线。

### 1.4 定义与缩写

| 术语              | 定义                                                                                                                                   |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 接口定义            | 被测接口的规范化描述（协议、方法、路径、请求参数、响应示例等），作为测试场景与 Mock 服务的资产底座                                                                                 |
| 接口场景            | 多个接口定义与请求（http/jdbc 取样器）按顺序编排而成的步骤序列，用于场景级自动化回归                                                                                      |
| 项目设置            | 平台级统一的项目配置入口（设置框架各业务域共享），配置项按业务域归属显式标注（通用 / 接口测试 / 功能测试），界面按当前业务域过滤展示；本域在其下定义环境管理、公共组件                                                       |
| 公共组件            | 项目级维护的可复用组件（前置/后置处理器、验证器、提取器），快速调试与测试场景中可复制引入；引入为仅复制，得到独立副本，与源资产无关联                                                             |
| 场景模块（项目统一）   | 项目级统一模块树（project_module），测试场景、接口定义、功能用例文档共享此树进行组织，支持多级嵌套；接口管理与测试场景页面左侧复用同一棵树，右侧按资产类型筛选 |
| 接口场景            | 多个接口定义与请求（http/jdbc 取样器）按顺序编排而成的步骤序列，用于场景级自动化回归                                                                                      |
| Mock            | 按配置规则模拟接口响应，供依赖方在真实服务不可用时联调                                                                                                          |
| 环境              | 项目级配置集合，融合 Ryze 默认配置（http 默认配置）、数据源、全局变量与全局前置/后置处理器，配置项与 Ryze 对应一致；支持配置多个环境                                                          |
| 环境变量            | 环境级全局变量，与 Ryze 全局变量配置对应一致，在环境内定义、可通过 `${变量名}` 引用的命名值，作用于请求参数、前置/后置处理器等场景                                                             |
| 前置处理器           | 在测试组件（取样器、测试集合）执行前运行的 Ryze 组件，用于数据准备、环境初始化、认证获取；结构含类型、配置、启用、排序等要素（与 Ryze 对应一致），可携带提取器，提取器可手动配置或从公共组件复制引入（引入为独立副本，与源资产无关联）                                                |
| 后置处理器           | 在测试组件执行后运行的 Ryze 组件，用于数据清理、状态恢复、结果验证；结构同前置处理器，执行顺序相反                                                                                 |
| 提取器（extractors） | 从处理器/取样器响应中提取值并存储为变量供后续步骤使用的 Ryze 组件，类型支持 JSONPath、正则、响应头、整响应等提取方式（类型标识以 Ryze 为准）                                                    |
| 验证器（validators） | 对取样器响应（状态码、响应头、响应体、响应时间等）进行校验的 Ryze 验证器组件，类型支持 JSON、HTTP、结果三类校验（类型标识以 Ryze 为准），验证失败则该步骤判定失败                                           |
| 全局前置/后置         | 在环境或场景级配置、对所有请求生效的前置/后置处理器                                                                                                           |
| 内置函数            | Ryze 框架内置的数据生成与处理函数（如 `random_string`、`faker`、`timestamp`、`digest`、`uuid` 等），以 `${函数名(参数)}` 语法调用；平台提供快捷使用帮助，实际执行以 Ryze 框架为准          |
| Ryze            | 平台直接引入的测试执行框架，以 JSON/YAML 描述测试场景，测试用例与代码分离；平台执行引擎基于其构建，执行时将平台中保存的接口测试数据解析为 Ryze 标准 JSON 后运行                                          |
| Ryze 标准格式       | 执行引擎可识别的标准 JSON 结构，与 Ryze 框架对应一致（测试集合与步骤的字段结构详见详细设计）；平台场景/调试请求在执行时解析为该格式                                                             |
| 取样器（Sampler）    | Ryze 中执行具体测试逻辑的最小单元（对应平台的一次请求步骤），含步骤配置、提取器、验证器                                                                                       |
| 资源池             | 平台侧执行引擎（基于 Ryze 构建）的运行单元，承载服务端执行的任务；支持并发执行                                                                                           |
| 复制与链接引用         | 场景导入系统请求的两种方式：复制产生独立副本，修改互不影响；链接引用跟随源数据变化（参数值及启用状态可独立调整，见 3.4）                                                                       |
| 变更历史            | 对象（接口/场景）每次保存变更产生的记录，含变更序号                                                                                                           |
| 执行历史            | 对象（场景）每次执行产生的记录，与报告关联                                                                                                                |
| cURL            | 命令行 HTTP 请求工具格式，支持从 Chrome/Charles/Fiddler 等抓包工具导出                                                                                   |

---


## 2. 总体描述

### 2.1 用户角色与特征

沿用 V1.0 角色模型，补充约定：

- 接口测试业务域面向**业务用户**开放，所有操作严格限定在当前活跃工作空间与当前项目上下文内。
- 权限粒度：接口管理、测试场景、报告、定时任务及项目设置（环境管理、公共组件）的查看/编辑/删除/执行权限由项目级角色控制（见附录 B）。

### 2.2 假设与依赖

- 被测接口以 HTTP/HTTPS 协议暴露，平台通过标准 HTTP 客户端发起请求，不依赖特定被测试系统框架。
- 平台直接引入 Ryze 测试框架作为执行引擎依赖；调试、场景执行时，平台将自有场景模型解析为 Ryze 标准 JSON 并在服务端执行（转换由平台内部完成，对用户无感）。
- 服务端执行由平台执行引擎（基于 Ryze 构建的资源池）承载，支持并发执行；调试亦支持本地执行模式（由用户本机代理发起）。
- 数据库前置/后置处理器（JDBC）依赖环境管理中配置的数据源；数据源驱动由平台内置常见数据库驱动（MySQL、PostgreSQL、Oracle、SQLServer 等）支持。
- 场景、报告的存储遵循平台数据库规范，接口测试执行历史可能数据量大，需在详细设计中明确清理策略（默认保留90天）。

### 2.3 实施分期

按价值/成本比排定优先级，供迭代规划参考（非承诺性约束）：

| 梯队  | 需求范围                                                                                                    | 说明                           |
| --- | ------------------------------------------------------------------------------------------------------- | ---------------------------- |
| 一   | 快速调试（含服务端执行与本地执行）、请求参数模型、验证器、环境管理（默认配置/数据源/全局变量/全局前后置处理器）、服务端执行（Ryze 执行引擎接入）与并发调度基础                 | 基础链路，打通「调试 → 执行 → 结果」，支持本地调试 |
| 二   | 接口管理（含 cURL/Swagger URL 导入）、报告                                                                             | 资产化管理，支持接口回归                 |
| 三   | 测试场景（步骤编排、场景模块、http/jdbc 取样器）、前置/后置处理器、提取器（含调试请求提取变量）、内置函数、公共组件（项目级处理器/验证器/提取器资产库，供快速调试与测试场景复制引入） | 场景级自动化                       |
| 四   | Mock 服务、定时导入、场景定时任务                                      | 联调提效与企业级能力                   |
| 后续  | 非 HTTP/JDBC 协议（基于 Ryze 多协议能力扩展）                                                   | 多协议支持，架构预留，按需评估    |

---


## 3. 非功能需求

### 3.1 性能需求

- 单请求服务端执行的端到端延迟不超过请求本身网络往返 + 2 秒（受被测系统影响，为目标值而非硬性门槛）。
- 平台内执行支持并发（默认 5），并发执行下，单任务执行性能不因并发显著下降（目标值下降不超过 20%）。
- 百级步骤场景从触发到执行完毕（含步骤间参数传递）的总耗时目标值不超过请求总耗时 + 30 秒计算开销；场景编排保存响应时间 < 2 秒。
- 报告列表、接口列表等查询接口在万级数据量下分页响应时间目标值 2 秒内。
- 请求体大小上限与上传文件大小上限为系统配置项（默认 10MB），超限明确提示。

### 3.2 安全性需求

- 接口测试涉及的用户名密码（Basic/Digest Auth）、数据源密码等加密存储，不以下发明文至前端（脱敏展示）。环境变量值明文存储与展示（无敏感值概念）。
- 执行请求的目标地址不受平台出网限制策略约束之外的其他限制；Mock 访问需平台侧限流与审计（单路径 QPS 上限可配置，超限返回 429）。
- 跨项目数据隔离：接口定义、场景、环境、报告、公共组件严格按项目隔离，用户仅能访问其有权限的项目数据。
- 报告分享面向具备报告查看/分享权限的成员开放；生成分享链接时选择有效期，链接在有效期内免登录可访问、过期即失效，分享内容遵循项目数据安全策略。
- 导入功能需防范恶意内容与 SSRF（Swagger URL 导入的目标地址白名单或校验策略在详细设计中确定）。

### 3.3 可用性需求

- 快速调试、场景执行均提供明确的加载态与结果反馈；执行失败给出可定位的错误信息（连接失败/超时/验证器失败明细）。
- 场景编辑支持步骤级启用/禁用与失败定位，批量调试可直接看到每个步骤的独立结果。
- 变更历史与执行历史为只读追溯信息，展示清晰、可区分来源（手动/导入/定时/同步）。

### 3.4 可靠性需求

- 服务端执行引擎故障不影响平台其他功能（场景/报告查询正常），执行任务失败可重试（手动或配置自动重试次数，默认不自动重试）。
- 定时导入、场景定时任务失败时记录失败原因并支持查看最近一次执行状态。
- 报告与执行历史数据量大时按系统配置的清理策略自动清理（默认保留 90 天），清理行为记录日志，清理后历史详情明确展示「执行结果被清理」。

### 3.5 兼容性需求

- 支持主流的 Swagger/OpenAPI 2.0/3.0（URL 拉取）与 cURL 命令导入。
- 变量引用语法（`${变量名}`）与内置函数与 Ryze 框架保持一致；外部工具存量资产迁移友好性由后续迭代评估。
- 平台场景模型与 Ryze 标准格式的转换错误可定位，平台自身存储不依赖 Ryze 文档格式。

---


## 4. 数据需求

### 4.1 新增数据实体

- **接口定义**：协议、方法、路径、模块归属、请求参数（请求头/请求体/Query/REST/认证）、响应示例、变更历史。
- **接口场景**：场景元数据（名称、模块归属、参数配置）、步骤树（请求步骤及导入步骤）、前置/后置处理器、验证器、变更历史。
- **场景步骤**：步骤类型（http 取样器：系统请求/自定义请求；jdbc 取样器：数据库请求）、排序、启用状态、复制/链接引用关系标识（含源ID、引用模式）。
- **Mock 定义**：关联接口定义、匹配规则、响应定义（含延迟配置）、启停状态、命中次数统计。
- **环境**：环境名称、默认配置（http 默认配置，与 Ryze 对应一致）、数据源、全局变量、全局前置/后置处理器。
- **调试记录**：用户、请求快照、执行时间、执行模式（服务端/本地），本地执行记录仅存本地缓存。
- **报告**：关联执行对象自身（场景或任务）、报告类型（场景 `scene` / 套件 `suite`）、执行方式、结果汇总、结果明细（场景报告为单场景步骤明细；套件报告为「场景 → 步骤」两级明细）、分享链接与有效期。
- **执行历史**：关联执行对象（场景）、执行时间、执行方式（平台内）、结果、报告关联。套件执行的执行历史逐场景记录，共享套件报告关联。
- **公共组件**：类型（前置处理器/后置处理器/验证器/提取器）、名称、内容配置（对应组件的配置要素，以 Ryze 为准）、启用状态、维护人、维护时间。
- **变更历史**：关联对象（接口/场景）、变更序号、变更内容、变更人、变更时间。
- **定时任务**：任务类型（接口导入/场景执行）、绑定对象（Swagger 文档地址/测试场景）、Cron 表达式、启用状态、执行记录（触发时间、触发方式、结果、失败原因、关联报告或导入结果）、创建者。

所有新增表遵循平台数据库规范：必含 `id`、`created_at`、`updated_at`、`is_deleted`（逻辑删除标记），禁止物理外键；索引设计遵循平台索引规范，高频查询条件（接口路径+方法、报告时间范围、对象引用关系等）需建索引；报告与执行历史等大表按月分区，清理策略默认保留 90 天（可配置）。

> 存储形态说明：平台以自有字段模型存储全部接口测试数据（上表各实体），**不持久化 Ryze 文档**；Ryze 标准 JSON 仅在执行时由平台实时解析生成，随执行快照保留以便结果回溯与转换问题定位。

### 4.2 数据隔离与生命周期

- 接口定义、接口场景、Mock、环境、报告、公共组件、定时任务均归属项目，按项目隔离，与平台既有数据隔离原则一致。
- 逻辑删除贯穿全部实体；删除接口定义时其下关联的 Mock 的引用关系同步失效。
- 报告与执行历史保留期限默认为 90 天（系统配置项），超期由系统清理任务清理；清理后执行历史保留元数据、详情置为「执行结果被清理」。
- 分享链接过期即失效，不长期保留可访问的匿名报告。

---


## 5. 附录

### 附录 A：用户故事与需求章节映射

| 用户故事               | 章节   |
| ------------------ | ---- |
| US-API-001 快速调试    | 3.1  |
| US-API-002 接口管理    | 3.2  |
| US-API-003 Mock 服务 | 3.3  |
| US-API-004 测试场景    | 3.4  |
| US-API-005 环境管理    | 3.7.1 |
| US-API-006 接口测试报告  | 3.5  |
| US-API-007 定时任务    | 3.6  |
| US-API-008 公共组件    | 3.7.2 |

### 附录 B：角色权限矩阵

| 模块      | 功能点                               | 系统权限用户    | 工作空间权限用户（项目级）      |
| ------- | --------------------------------- | --------- | ------------------ |
| 快速调试    | 新建/执行/保存调试请求                      | ❌         | ✅                  |
| 接口管理    | 查看                                | ❌         | ✅（项目成员）            |
|         | 新建/编辑/复制/导入                       | ❌         | ✅（项目维护者）           |
|         | 删除                                | ❌         | ✅（项目维护者）           |
| Mock 服务 | 查看/访问 Mock 地址                     | ❌         | ✅（项目成员；外部依赖方经分享访问） |
|         | 创建/编辑/启停/删除                       | ❌         | ✅（项目维护者）           |
| 测试场景    | 查看/执行                             | ❌         | ✅（项目成员）            |
|         | 创建/编辑/删除/定时任务                     | ❌         | ✅（项目维护者）           |
| 环境管理    | 查看/使用                             | ❌         | ✅（项目成员）            |
|         | 创建/编辑/删除/导入导出                     | ❌         | ✅（项目维护者）           |
| 报告      | 查看/分享                            | ❌         | ✅（执行者本人或项目维护者）     |
|         | 删除                                | ❌         | ✅（项目维护者）           |
| 公共组件    | 查看/复制引入                           | ❌         | ✅（项目成员）            |
|         | 新建/编辑/删除/启停                       | ❌         | ✅（项目维护者）           |
| 定时任务    | 查看/查看执行记录                         | ❌         | ✅（项目成员）            |
|         | 新建/编辑/启停/删除/立即执行                  | ❌         | ✅（项目维护者）           |

> 说明：项目角色模型（成员/维护者）的具体命名与继承关系沿用平台既有项目权限体系，若 V1.0 已定义项目级角色则以 V1.0 为准；项目级角色的细化在概要/详细设计阶段与平台权限模型对齐后确定。

## 分册-章节对照表

| 分册 | 文件 | 覆盖章节 |
|---|---|---|
| 总览 | `02-api-srs-overview.md` | 前言、1. 引言、2. 总体描述、4. 非功能需求、5. 数据需求、6. 附录 |
| 快速调试 | `03-api-srs-quick-debug.md` | 3.1 快速调试 |
| 接口管理 | `04-api-srs-interface-management.md` | 3.2 接口管理 |
| Mock 服务 | `05-api-srs-mock-service.md` | 3.3 Mock 服务 |
| 测试场景 | `06-api-srs-test-scenario.md` | 3.4 测试场景 |
| 接口测试报告 | `07-api-srs-test-report.md` | 3.5 接口测试报告 |
| 定时任务 | `08-api-srs-scheduled-task.md` | 3.6 定时任务 |
| 项目设置 | `10-api-srs-project-settings.md` | 3.7 项目设置 |
| 公共需求 | `09-api-srs-common.md` | 3.8 请求参数与认证、3.9 前置与后置处理器、3.10 验证器、3.11 内置函数与变量引用、3.12 执行引擎 |', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000005', '["接口测试","总览"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '15 days', CURRENT_TIMESTAMP - INTERVAL '15 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0041-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0020-0000-4000-8000-000000000000', 'V1.2', 'REQ-020', '快速调试',
        '# 软件测试平台——快速调试

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 快速调试（US-API-001）

**功能描述**：提供面向开发联调阶段的多标签调试能力，支持同时打开多个调试会话（标签页），快速构造并执行 HTTP 请求，查看响应结果，验证通过后可保存为接口定义或调试记录。支持**服务端执行**与**本地执行**两种模式。

**功能点**：

- 多标签调试：支持同时打开多个调试标签页，每个标签独立维护请求/响应状态；标签显示请求方法图标 + 简短名称，支持新建、切换、关闭、重命名；最多同时打开 10 个标签。
- 新建调试请求：选择协议（HTTP/HTTPS）、请求方法（GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD/CONNECT）、输入完整 URL；请求编辑器对齐 Postman（Params/Auth/Headers/Body 页签），请求头、请求体、认证基于公共能力配置。
- 执行模式切换：
  - **服务端执行（默认）**：由平台执行引擎在服务端发起请求，适用于验证服务端网络策略与接口可达性，执行结果可保存为调试记录；
  - **本地执行**：通过平台提供的本地代理客户端（或浏览器插件）从用户本机发起请求，适用于调试本地环境、绕过服务端网络限制或验证本地代码变更。本地执行结果仅展示，不持久化为平台报告或调试记录（记录仅保存在浏览器本地缓存），不可保存为接口资产。（该模式依赖本地代理客户端，暂缓交付。）
- 导入 cURL：粘贴抓包工具（Chrome/Charles/Fiddler）导出的 cURL 命令，自动解析为请求（方法、URL、请求头、请求体），填充当前激活标签。
- 响应查看：展示状态码、响应头、响应体与响应耗时；响应区对齐 Postman——[Body]/[Headers]/[Cookies] 页签，Body 内 Pretty/Raw/Preview 三种视图，响应体支持按格式（JSON/XML/HTML/纯文本）展示，JSON 语法高亮与折叠，支持响应体搜索（`Ctrl+F`）。
- 保存：服务端执行的调试请求可通过 URL 栏 [保存] 按钮保存为接口定义，保存时填写名称并归属到接口管理模块（新建接口或归属已有接口定义）。
- 自动保存历史记录：每次服务端执行完成后，自动将请求快照与响应结果保存为历史记录，无需用户手动操作；历史记录按时间倒序排列，保留最近 200 条（超出自动淘汰最旧记录）。
- 从历史记录恢复：在历史记录视图中点击条目，自动创建新调试标签并回填该次请求的方法、URL、请求头、请求体、认证配置，同时在新标签响应区展示该次响应结果；支持快捷键 `Ctrl+Shift+H` 快速切换到历史记录标签。
- 调试记录管理：页面级固定标签「历史记录」，不可关闭，位于标签栏末尾；点击标签切换为全宽历史记录视图，支持搜索、重命名、删除（仅服务端执行的持久化记录）。

**业务规则**：

- 未保存的调试请求执行后仅展示结果，不产生持久化数据；保存后即成为接口定义资产。
- 自动保存仅记录服务端执行结果；本地执行不自动保存（仅浏览器本地缓存）。
- 历史记录保留最近 200 条，超出后自动淘汰最旧记录；用户可手动删除单条记录。
- 关闭有未保存修改的标签时弹出二次确认；最后一个标签关闭后自动创建空白标签。
- 标签首次执行后自动以「方法 + URL 路径末段」命名；用户可双击标签重命名。
- 导入 cURL 仅解析请求描述，不执行 cURL 命令本身，也不执行其中的任何脚本或文件引用。
- 调试请求支持变量引用（`${变量名}`）与内置函数，变量来源为环境变量。
- 请求体仅支持一种类型（none/x-www-form-urlencoded/raw），切换类型时保留已填内容供切换回时恢复。
- 调试执行不产生报告，仅展示本次执行结果；需要留痕时使用测试场景执行。
- 本地执行模式不消耗平台执行引擎并发资源，不记录审计日志（仅记录操作行为，不记录请求明细）。', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000005', '["接口测试","快速调试"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0042-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', 'V1.2', 'REQ-021', '接口管理',
        '# 软件测试平台——接口管理

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 接口管理（US-API-002）

**功能描述**：提供接口资产的集中管理，支持新建、导入、维护与查看引用关系，是测试场景与 Mock 服务的数据底座。

**功能点**：

- 新建接口：填写接口名称、所属模块、协议、请求方法、路径（支持路径参数 `${id}` 占位），并可编辑请求参数、响应示例。
- 导入接口：
  - **cURL 导入**：粘贴抓包工具（Chrome/Charles/Fiddler）导出的 cURL 命令（支持单条或多条），解析为接口定义（方法、URL、请求头、请求体）；
  - **Swagger URL 导入**：输入 Swagger/OpenAPI 文档地址，拉取并解析为接口定义；
  - **定时导入**：配置定时任务周期拉取 Swagger URL 并增量更新接口定义（统一任务管理见 3.6）。
- 接口列表：按模块树组织，支持筛选条件（ID、接口名称、所属模块、协议）与视图切换（我关注的 / 我创建的 / 全部）。
- 接口预览（API 预览）：详情页展示接口定义信息、**引用关系**（被哪些场景/Mock 引用）、**变更历史**。
- 调试入口：从接口定义一键进入调试（携带该接口的请求参数）。
- 列表操作：编辑、复制、删除、关注/取消关注。

**业务规则**：

- 接口名称在所属模块内唯一；模块支持多级目录组织。
- 接口定义被场景或 Mock 引用时，禁止直接删除，需先解除引用/关联（平台提示引用方）。
- 复制以源接口为模板打开新建态编辑器并回填全部接口定义（名称默认「原名称（副本）」，可修改），保存后产生独立副本，与原接口无关联，源接口后续修改不影响副本。
- 变更历史记录每次保存产生的变更记录并生成变更序号，详情可查看。
- cURL 导入需校验格式合法性；无法解析的条目跳过并给出失败清单，不阻断整体导入。
- 定时导入的更新采用「接口路径+方法」匹配策略：已存在则覆盖更新，不存在则新增；被手动修改过的接口定义不被定时导入覆盖（导入策略为系统配置项）。', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000008', '["接口测试","接口管理"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '13 days', CURRENT_TIMESTAMP - INTERVAL '13 days', CURRENT_TIMESTAMP - INTERVAL '13 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0043-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0022-0000-4000-8000-000000000000', 'V1.2', 'REQ-022', 'Mock 服务',
        '# 软件测试平台——Mock 服务

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. Mock 服务（US-API-003）

**功能描述**：为接口定义创建 Mock 规则，按配置模拟响应，使依赖方在真实服务不可用时正常联调。

**功能点**：

- 创建 Mock：从接口定义创建（继承路径与方法）或独立创建；Mock 归属接口管理模块。
- Mock 规则：配置匹配条件（请求方法、路径、请求头、请求参数）与响应定义（状态码、响应头、响应体，支持变量与内置函数）。支持配置响应延迟时间（模拟慢响应）。
- 跟随 API：开启后 Mock 响应跟随接口定义中配置的响应示例（Mock 自身未配置响应时生效）。
- Mock 调试：在 Mock 配置页直接执行一次请求验证匹配与响应是否符合预期。
- 复制 Mock 地址：一键复制 Mock 的访问地址，供依赖方配置。
- Mock 列表：按接口模块组织，支持启停、编辑、删除。提供命中次数统计与最近命中时间查看。

**业务规则**：

- Mock 地址与真实接口地址同构（同路径、同方法），仅通过平台提供的 Mock 域名/端口区分。
- Mock 匹配按「请求方法 + 路径 + 匹配条件」顺序取第一条命中规则；无命中时返回 404。
- Mock 的响应内容支持变量（环境变量、内置函数）动态生成。
- Mock 启停状态即时生效，不重启服务。
- Mock 访问不要求请求方具备平台登录态（面向依赖方联调），但需防滥用（平台侧限流，如单 Mock 路径 QPS 上限可配置），访问量计入审计日志。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-00000000000a', '["接口测试","Mock"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0044-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0023-0000-4000-8000-000000000000', 'V1.2', 'REQ-023', '测试场景',
        '# 软件测试平台——测试场景

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 测试场景（US-API-004）

**功能描述**：提供场景级自动化测试能力，覆盖**平台场景数据**：平台内创建的可执行场景，将接口、请求按顺序编排为步骤序列（步骤即取样器，支持 http/jdbc 两种类型），支持参数传递，一键执行并生成场景报告，由平台执行引擎在服务端执行。

**功能点**：

**平台场景数据**：

- 场景创建：定义场景名称、所属模块（模块为项目级统一模块树 project_module，与接口定义共享同一棵树）与优先级（P0/P1/P2/P3，缺省 P2）。
- **参数**：场景级定义常量、列表参数。
- **前/后置**：场景级前置/后置处理器，基于公共能力，在场景编辑器中以独立标签页分别配置前置处理器与后置处理器。
- **公共组件引入**：场景级前置/后置处理器、步骤级处理器/验证器/提取器均可从公共组件库（平台级）复制引入。
- **场景步骤（步骤即取样器）**：
  - **http 取样器**：支持导入系统请求（从接口定义、其他场景复制或链接引用）、自定义请求（创建不落库的 HTTP 请求步骤，可引用环境自动填充域名或填写完整 URL，支持粘贴 cURL）与验证器；
  - **jdbc 取样器**：创建数据库请求步骤，选择环境中的数据源并编写 SQL 语句，可携带提取器/验证器；用于数据准备、数据校验或生成后续步骤参数。
- **平台内执行**：由平台执行引擎基于 Ryze 在服务端并发执行；执行后生成场景报告。
- **场景控制**：单步骤操作（启用/禁用、执行、复制、删除、在之前/之后插入步骤）、批量操作（批量启用、批量禁用、批量调试、批量删除、批量移动）与场景级操作（关注、复制链接、查看报告、编辑、执行、复制、创建定时任务、删除）。
- **执行历史与变更历史**：场景保存后自动提供两个标签页（变更历史同 3.2 语义，执行历史见 3.5）。

**业务规则**：

**平台场景数据**：

- **参数优先级**：执行期变量（处理器/提取器产生）> 场景参数 > 环境参数；同名变量时按该优先级取值。执行报告中对每个步骤使用的变量标注来源层级（环境/场景/提取器），便于调试。
- **公共组件引入（仅复制）**：引入的处理器/验证器/提取器为独立副本，与公共组件无关联。
- **场景执行失败语义**：任一步骤失败，后续步骤不再执行并在报告中标记未执行。
- **执行历史保留**：默认保留全部记录；手动删除报告不影响执行历史查看详情；系统按清理策略（默认90天）清理报告后，执行历史详情显示「执行结果被清理」。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000008', '["接口测试","场景"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '11 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0045-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0024-0000-4000-8000-000000000000', 'V1.2', 'REQ-024', '接口测试报告',
        '# 软件测试平台——接口测试报告

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 接口测试报告（US-API-006）

**功能描述**：集中查看场景执行结果，支持详情查看、分享与删除；报告来源为平台内执行。报告按粒度分为**场景报告**（单个场景）与**套件报告**（定时任务含立即执行聚合的多个场景，按「场景 → 步骤」两级展示）。

**功能点**：

- 报告列表：分页展示报告（可筛选执行方式、时间范围）。列表只展示定时任务（含调度页"立即执行"）产生的报告；场景页 [运行] 按钮直接产生的报告不进列表，可在对应场景的执行记录中通过弹窗查看。套件报告在列表中以一行展示，报告名称为「任务名 + 执行时间戳」。
- 查看报告：点击报告名称预览报告详情。
  - **场景报告**：展示该场景的步骤级明细，点击步骤可查看该步骤实际请求与响应内容；
  - **套件报告**：先展示套件内**场景列表**（含各场景状态/通过率/耗时），点击场景展开其**步骤明细**（两级：套件 → 场景 → 步骤），步骤点击可查看请求/响应内容。
- 查看报告（执行记录入口）：场景执行记录中的 [报告] 按钮在弹窗中展示报告明细，适用于包括场景页 [运行] 产生的报告。
- 分享报告：具备报告查看/分享权限的成员即可生成分享链接（不设全局开关）；生成时选择链接有效期（默认 7 天），链接在有效期内免登录可访问，过期即失效。**已有未过期分享时，分享弹窗直接展示现有记录（链接、有效期、分享者）并提供复制**，仅无分享或已过期时走生成流程（可显式重新生成覆盖）。复制的分享链接为带描述文本，包含测试报告名称、分享链接、链接有效期、分享者。套件报告分享后按「场景 → 步骤」两级展示。
- 删除报告：单个删除或勾选批量删除。删除套件报告不影响被圈选场景与产生它的执行记录。

**业务规则**：

- 报告类型区分：`scene`（场景页 [运行] 或执行记录生成，仅关联单一场景）与 `suite`（定时任务含立即执行聚合生成，关联一次任务下多个场景）。套件报告内嵌的场景明细复用场景报告数据模型，前端一套渲染规则。
- 删除报告后不可恢复；报告被系统清理后，执行历史详情显示「执行结果被清理」。
- 分享链接在有效期内可匿名访问（不要求平台登录态），有效期过期后链接失效。
- 报告已有未过期分享时，分享弹窗复用展示现有分享记录（含分享者），复制内容附带报告名称、链接、有效期与分享者描述；重新生成分享将覆盖旧链接与分享者。
- 报告内容包含敏感信息（请求头 Token、响应体业务数据）时，分享遵循项目的数据安全策略。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-00000000000b', '["接口测试","报告"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '10 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0046-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0025-0000-4000-8000-000000000000', 'V1.2', 'REQ-025', '定时任务',
        '# 软件测试平台——定时任务

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 定时任务（US-API-007）

**功能描述**：任务级定时调度，将「测试计划」「接口同步」「GitLab 同步」三类定时任务统一管理：按 Cron 表达式周期触发，记录每次触发结果，支持启停与立即执行；任务归属项目，按项目隔离。

**功能点**：

- **任务类型**：
  - **测试计划任务**：按执行范围批量执行接口测试场景，执行方式：全部执行、指定模块（多选）、指定场景（多选）；执行时基于实时数据圈选场景，需指定目标环境；触发执行生成接口测试报告；
  - **接口同步任务**：直接指定 OpenAPI/Swagger JSON 文件的 URL 地址，周期拉取并增量更新接口定义（导入策略同 3.2 定时导入）。
  - **GitLab 同步任务**：以定时任务形态承载 GitLab 仓库的源变更检测，任务不携带执行范围、目标环境与文档 URL 字段。该类型的功能描述与业务规则见《GitLab 集成需求规格说明书》（`docs/01-requirements/05-api-testing/11-api-srs-gitlab-integration.md`）。
- **调度配置**：Cron 表达式（支持常用预设：每 5 分钟 / 每小时 / 每天 / 每周 + 自定义），保存时校验表达式合法性并预览下次执行时间。
- **任务管理**：统一任务列表（任务名称、类型、执行范围、调度表达式、启用状态、上次执行时间与结果、下次执行时间），支持新建、编辑、启停、立即执行、删除、查看执行记录。
- **执行记录**：每次触发（定时或手动）产生一条执行记录：触发时间、触发方式（定时/手动）、执行结果（成功/失败/超时）、失败原因、关联报告（测试计划任务）、导入结果（接口同步任务：新增/更新/失败数）或同步结果计数（GitLab 同步任务：新增/更新/解析失败，无变更时记为跳过）。GitLab 同步任务不产生测试报告。
- **结果查看**：任务列表展示最近一次执行状态；执行失败记录失败原因，可通过「立即执行」手动补跑。
- **套件报告**：测试计划任务每次触发按其执行范围圈选多个场景，执行完成后**聚合生成一份套件报告**（报告类型 `suite`），报告名称为「任务名 + 执行时间戳」，明细按「场景 → 步骤」两级展示；执行记录关联该套件报告。

**业务规则**：

- Cron 表达式校验不通过不可保存；调度精度为分钟级，触发时间按服务器时区计算。
- 同一任务上一次执行未结束时，本次触发跳过（不重复触发、不累计）。
- 定时触发的场景执行进入平台执行引擎并发队列，与手动执行统一排队。
- **测试计划任务执行方式**：
  - **全部**：执行目标环境下该项目所有已发布且可执行的接口测试场景；
  - **指定模块（多选）**：仅执行选中的模块及其下的场景；
  - **指定场景（多选）**：仅执行选中的具体场景；
  - 执行时基于实时数据（非快照），圈选场景时校验场景为已发布（非草稿）的可执行场景且归属当前项目；草稿状态场景无论何种执行方式均跳过执行并留痕提示；需指定目标环境。
- **删除保护**：被定时任务选中的场景或模块禁止直接删除，需先删除任务或移除选中。
- 任务以创建者身份与权限执行；创建者退出项目后任务停用。
- 停用后不再触发；删除任务不影响已产生的执行记录与报告。
- 接口同步任务的导入策略同 3.2 定时导入（接口路径+方法匹配，手动修改过的接口不被覆盖）。
- 执行失败不自动重试，可通过「立即执行」手动补跑。
- 失败通知渠道沿用平台既有消息能力（预留）；平台无消息中心时仅记录失败状态不通知，通知能力在详细设计中确定。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-11 | 任务类型登记「GitLab 同步」取值并交叉引用 GitLab 集成需求分册；执行记录字段补充同步结果计数分支，合并重复的「业务规则」标题块 |', 'confirmed', 'low', '7e57001f-0000-4000-8000-00000000000a', '["接口测试","定时任务"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0047-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0026-0000-4000-8000-000000000000', 'V1.2', 'REQ-026', '公共需求',
        '# 软件测试平台——公共需求

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 请求参数与认证（公共需求）

**功能描述**：定义快速调试、接口管理、测试场景自定义请求共用的请求参数模型与认证方式。

**功能点**：

- **请求头**：名称下拉选择（常用头自动补全）+ 值输入，支持单条/批量添加；值支持变量与内置函数。
- **请求体**，类型互斥，支持：
  - `none`：无请求体；
  - `x-www-form-urlencoded`：键值对，支持 URL 编码开关；
  - `raw`：原始文本，可自定义 Content-Type，子类型选择器；
- **Query 参数**：URL 查询参数键值对，支持变量与内置函数。
- **认证**：支持 No Auth（无认证）、Bearer Token（Token）、API Key（键名/值）、Basic Auth（用户名/密码）、Digest Auth（用户名/密码，暂不开放）。

**业务规则**：

- 请求体类型切换时保留各类型已填写内容，切回后恢复，不因切换丢失（同一请求下）。
- Query 参数与 URL 中已带查询串同时存在时，执行时合并去重（以参数区为准）。
- 认证信息仅作用于当前请求配置，不随请求保存为明文（存储加密）。
- Bearer Token、API Key 由前端在提交执行时换算为请求头（`Authorization: Bearer <token>`、自定义请求头），不新增独立存储字段。
- 路径参数与路径占位符一一对应：未提供值时按字面 `{id}` 发送并在校验提示中告知。
- 跟随重定向关闭时，重定向响应按实际响应展示（状态码 3xx），不自动跳转。
- 执行时该请求模型解析为 Ryze 标准格式，映射规则在详细设计中定义。

## 2. 前置与后置处理器（公共需求）

**功能描述**：前置/后置处理器以 Ryze 框架为准，在测试组件（取样器、测试集合）执行前/后运行，用于数据准备、环境初始化、认证获取、数据清理、结果验证等预处理/后处理逻辑；支持 http 与 jdbc 两种处理器类型（同取样器）。

**功能点**：

- **处理器结构**：与 Ryze 一致（类型、配置、启用、排序等要素），首期支持 `http` 与 `jdbc` 两种处理器类型，其余协议随多协议扩展预留；具体字段结构详见详细设计。
- **配置位置**：可配置于环境级（项目级）、场景级、步骤级（取样器级），下级继承上级配置并可覆盖。
- **提取器**：处理器可携带提取器，从处理器响应中提取变量供后续步骤使用（如前置登录获取 token）。
- **前置处理器**：执行于测试组件之前，用于数据准备、环境初始化、认证获取、资源预分配。
- **后置处理器**：执行于测试组件之后，用于数据清理、状态恢复、结果验证、通知发送；可访问执行结果与测试过程中生成的变量。
- **公共组件**：项目级可复用处理器/提取器资产的集中管理与复制引入统一由项目设置-公共组件提供（见 3.7.2）。

**业务规则**：

- 执行顺序：前置处理器按「环境级 → 场景级 → 步骤级」依次执行；后置处理器按相反顺序（「步骤级 → 场景级 → 环境级」）执行。
- 任一前置处理器失败则其所属组件不执行并标记失败；后置处理器失败不改变组件本身成败，但记录失败信息。
- 处理器失败与否对执行结果的影响遵循 Ryze 框架语义；多处理器按配置顺序执行。
- 提取器类型以 Ryze 为准，支持 JSONPath、正则、响应头、整响应等提取方式；提取的变量遵循 Ryze 变量作用域。
- JDBC 处理器依赖环境管理中配置的数据源（见 3.7.1），执行前校验数据源连接，连接失败明确提示数据源不可用。
- 公共组件的共享范围、维护权限与「仅复制」引入语义见 3.7.2。

## 3. 验证器（公共需求）

**功能描述**：验证器以 Ryze 验证器为准，对请求响应进行校验，验证失败则该步骤判定失败。验证器在取样器或处理器执行完成后自动运行，从响应中获取实际值并与预期值比较。

**功能点**：

- **验证器类型**，以 Ryze 为准，支持：
  - JSON 验证器：通过 JSONPath 校验 JSON 响应内容；
  - HTTP 验证器：校验 HTTP 状态码、响应头、响应体；
  - 结果验证器：校验取样器响应结果，如响应时间。
- **验证字段**：指定被校验的响应部位（状态码、响应头、响应体、JSONPath 表达式、响应时间等）。
- **期望值**：期望值，支持单个值或多个值（部分验证规则下生效）。
- **验证规则**，以 Ryze 为准，支持：相等、任意一个相等、不相等、大小比较（大于/小于/大于等于/小于等于）、包含、包含任意一个、不包含、正则匹配、非空、为空、对象匹配；规则与字段的具体标识映射详见详细设计。
- **配置位置**：可配置于取样器级、后置处理器中。
- **扩展**：内置验证器无法满足时，支持基于 Ryze 扩展机制的自定义验证器（预留）。
- **全局验证器资产**：验证器类公共组件（json/http_assertion/result_assertion，结构同本节）的集中管理与复制引入统一由项目设置-公共组件提供（见 3.7.2）。

**业务规则**：

- 多个验证器为「且」关系，任一失败则该步骤失败并记录失败验证器明细。
- 验证器表达式解析失败按验证失败处理，并在报告中给出解析错误提示。
- 验证器字段、期望值支持变量引用与函数，在验证前求值。
- 验证规则与多值期望的兼容组合遵循 Ryze 框架语义。
- 全局验证器资产的共享与引入语义见 3.7.2。

## 4. 内置函数与变量引用（公共需求）

**功能描述**：内置函数与变量引用以 Ryze 框架为准，平台提供**快捷使用帮助**功能辅助用户插入与查询，实际执行时直接以 Ryze 框架解析求值。

**功能点**：

- **内置函数**：以 Ryze 内置函数为准，调用语法 `${函数名(参数1, 参数2, ...)}`，覆盖：
  - 安全加密：`digest`（信息摘要，支持 md5/sha1/sha256 等、盐值与大小写参数）、`google2fa`（谷歌验证码）；
  - 数据生成：`random`（随机数）、`random_string`（随机字符串，长度/字符集/大小写可配）、`faker`（JavaFaker 模拟数据，如 `name.fullName`、`internet.emailAddress`、`phoneNumber.cellPhone`，区域可配）、`uuid`；
  - 日期时间：`timestamp`（当前时间戳或格式化日期）、`time_shift`（时间偏移，ISO-8601 偏移量）；
  - 数据处理：`json`（键值对构造 JSON）、`json_read`（JSONPath 读取）、`url_encode`/`url_decode`、`base64_encode`/`base64_decode`、`property`（系统属性）。
- **快捷使用帮助**：平台提供函数浏览与插入辅助（如函数列表、参数说明、使用示例、一键插入到输入位置），帮助用户快速选用 Ryze 函数；帮助内容与 Ryze 框架函数能力保持一致。
- **变量引用**：统一 `${变量名}` 语法，变量定义于环境（项目）级、场景级与步骤级（执行期由提取器/前置处理器产生的变量），子级继承父级变量并可覆盖；支持简单变量、对象变量（`${config.port}`）、数组变量。
- **配置位置**：函数与变量在请求参数、请求头、前置/后置处理器、验证器、Mock 响应中均可用。

**业务规则**：

- 函数与变量在请求发送前求值（先执行函数生成变量值，再进行变量替换）；引用不存在的变量时保留字面量并在执行日志中给出警告（不阻断执行）。
- 函数参数为字符串时的写法遵循 Ryze 模板语法（详见详细设计）。
- 函数库为 Ryze 框架内置能力，实际执行以 Ryze 框架为准；平台自身不维护独立函数实现。
- 内置函数无法满足时，支持基于 Ryze 扩展机制的自定义函数（预留）。

## 5. 执行引擎（公共需求）

**功能描述**：统一承载调试、场景的请求执行，由平台直接引入的 Ryze 测试框架在服务端执行，执行时将平台中保存的接口测试数据解析为 Ryze 标准 JSON 后运行。快速调试额外支持**本地执行**模式。

**功能点**：

- **执行模式**：
  - **平台内执行**：调试、场景均由平台执行引擎（基于 Ryze 构建的资源池）在服务端执行，不依赖用户本机任何服务。**支持多任务并发执行**：系统管理员可设置全局最大并发执行数（默认值 5），同一项目下共享该并发配额；调试请求与场景执行任务统一纳入并发队列管理。
- **格式转换**（平台内执行）：执行时平台将调试请求、测试场景的平台自有模型解析为 Ryze 标准格式，转换由平台内部完成、对用户无感，不要求用户感知 Ryze 语法。
- **执行结果**：平台内执行结果统一进入报告与执行历史体系；本地调试结果仅展示，不持久化。

**业务规则**：

- **并发控制**：平台内执行支持并发，最大并发数由系统配置项（`api.execution.max-concurrent`）控制，默认 5。超出并发数的任务排队等待，队列长度可配置（默认 100），超出队列长度时拒绝执行并提示。
- 执行超时按请求级「响应超时」配置控制，超时任务标记为超时失败并记录。
- 执行任务量纳入平台审计日志（用户、对象、执行时间、结果；本地执行仅记录操作行为）。
- 格式转换失败（平台模型存在 Ryze 无法表达的配置）时执行失败并给出可定位的转换错误信息，不产生部分执行结果。
- 首期启用 Ryze 的 HTTP 与 JDBC 取样器能力；Ryze 支持的其他协议（Redis/WebSocket/Dubbo/Kafka 等）作为多协议扩展预留。
- 扩展约束：自定义验证器、自定义提取器、自定义函数基于 Ryze 扩展机制提供（预留），平台不自行实现与 Ryze 语义不一致的平行能力。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000005', '["接口测试","公共约定"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '8 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0048-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0027-0000-4000-8000-000000000000', 'V1.2', 'REQ-027', '项目设置',
        '# 软件测试平台——项目设置

**文档版本**：V1.0
**日期**：2026-09-23
**状态**：起草中

---

## 1. 项目设置（平台级模块）

**功能描述**：平台级统一的项目设置入口，聚合项目级配置与资产类功能。设置框架由平台层统一建设，各业务域共享同一入口与权限模型；每个配置项显式标注业务域归属（`common` 通用 / `api_test` 接口测试 / `func_test` 功能测试），界面按当前业务域过滤展示，跨域配置相互独立、互不干扰。

**业务规则**（项目设置通用规则）：

- 项目设置入口在各业务域内呈现一致框架，仅展示 `common` 与当前业务域归属的配置项；其他业务域专属配置项不可见、不可改。
- 新业务域接入时在本模块下扩展其域归属配置项，不重复建设独立设置入口。

本域在项目设置下定义以下配置模块：

### 1.1 环境管理（US-API-005）

**功能描述**：项目级维护环境配置，融合 Ryze 框架的默认配置（http 默认配置）、数据源、全局变量与全局前置/后置处理器，配置项与 Ryze 对应一致；支持配置多个环境，使场景可在不同环境（如测试/预发/生产）间切换执行。

**功能点**：

- **环境配置**：维护环境列表，支持新建、复制、编辑、删除、导入、导出；支持配置多个环境。
- **默认配置**：环境级 http 默认配置，配置项与 Ryze 对应一致，执行时合并进 Ryze 标准 JSON，作为请求步骤的默认值。
- **数据源**：配置数据源（数据源名称、引用名称（ref_name）、驱动、连接 URL 含用户名密码、最大连接数），供前置/后置 JDBC 处理器使用；支持连接测试。支持 MySQL、PostgreSQL、Oracle、SQLServer、Redis。
- **全局变量**：环境级变量，单个/批量添加、编辑、删除；在请求参数、前置/后置处理器中通过 `${变量名}` 引用，与 Ryze 全局变量配置对应一致。
- **前置/后置**：环境级全局前置/后置处理器，基于公共能力，对环境中所有请求生效。

**业务规则**：

- 环境归属项目，仅当前项目内可见可用；支持配置多个环境，场景执行时选择目标环境。
- 环境配置项与 Ryze 框架对应一致，平台不提供 Ryze 之外的平行配置项；具体字段映射在详细设计中确定。
- 请求步骤引用环境后，URL 中不填域名仅填接口路径（域名来自环境默认配置）。
- 环境导入支持「覆盖」开关：开启时重名环境覆盖，关闭时重名环境跳过（不新增）。
- 环境删除前校验引用：被接口场景引用的环境**禁止删除**（强制保护），需先解除引用。
- 数据源密码等敏感配置加密存储，导出环境时敏感信息脱敏或需授权导出。
- 全局前置/后置与请求自身的同名前置/后置处理器同时存在时均执行（执行顺序遵循 Ryze 处理器层级语义）。
- 环境变量与场景参数、执行期变量同名时按 3.4 参数优先级取值。

### 1.2 公共组件（US-API-008）

**功能描述**：项目级维护可复用的组件资产库（前置/后置处理器、验证器、提取器），供快速调试与测试场景以「仅复制」方式引入使用；归属项目设置，按项目隔离。

**功能点**：

- **资产类型**：前置处理器、后置处理器、验证器、提取器四类；各类资产的结构与对应公共能力一致（处理器与提取器见 3.9，验证器见 3.10）。
- **资产管理**：新建、编辑、复制、删除、启用/停用；处理器/提取器类资产结构同 3.9（http/jdbc 处理器，json/regex/http_header/result 提取器），验证器类资产结构同 3.10（json/http_assertion/result_assertion）。
- **引入使用**：快速调试、测试场景（场景级/步骤级）可从资产库选择复制引入（引入选择器位于快速调试面板与测试场景的处理器/验证器/提取器配置处）。

**业务规则**：

- 公共组件为项目级共享：维护权限为项目维护者，项目成员可浏览与复制引入。
- 引入为仅复制：引入得到独立副本，与源资产无关联；源资产修改/删除不影响已引入副本。
- 复制资产以源资产为模板打开新建面板并回填全部配置（名称默认追加「 (副本)」，可修改），保存后产生同一作用域下的独立副本。
- 公共组件停用后不可再引入，已引入副本不受影响。', 'confirmed', 'medium', '7e57001f-0000-4000-8000-00000000000a', '["接口测试","项目设置"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP - INTERVAL '7 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed0049-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0028-0000-4000-8000-000000000000', 'V1.2', 'REQ-028', 'GitLab 集成需求规格说明书',
        '# 软件测试平台——GitLab 集成需求规格说明书

**文档版本**：V1.0
**日期**：2026-10-11
**状态**：起草中

---

## 1. 引言

### 1.1 编写目的

定义接口测试业务域与 GitLab 代码仓库集成的需求边界：如何将托管在 GitLab 上的 Maven 接口自动化工程导入为平台测试场景，以及导入后如何以双引擎方式执行与归集结果。本文是该能力的唯一需求来源，供后续概要设计、详细设计与验收对照使用。

### 1.2 范围

覆盖：

1. **仓库集成配置**：项目级 GitLab 仓库配置的创建、维护与连通性校验；
2. **工程导入与解析**（F1）：测试类扫描、用例解析、模块归属、环境合并、增量导入、源变更定时同步与改名迁移；
3. **双引擎执行**：场景按来源与导入模式路由到平台执行引擎或 GitLab 流水线引擎，含单场景执行与定时任务混合执行；
4. **结果归集**：流水线状态与用例级结果回收、双引擎套件报告聚合。

不覆盖：Mock 服务、接口定义导入（cURL / Swagger）、快速调试等既有能力不因本功能变更。

### 1.3 与基线的关系

- 《接口测试总览》《测试场景》《定时任务》《环境管理》等既有分册描述的平台原生能力保持不变，本分册只新增「导入场景」与「流水线引擎」两条并行路径；
- 既有 cURL / Swagger 导入链路的记录与映射机制不被本功能复用，二者互不干扰；
- 测试场景的字段模型、报告结构、任务执行范围等以既有分册为准，本分册只描述其中因导入而新增的语义。

### 1.4 定义与缩写

| 术语 | 定义 |
| ---- | ---- |
| 双引擎 | 同一平台内并存的两种场景执行方式：平台执行引擎（Ryze）与 GitLab 流水线引擎 |
| 导入模式 | 仓库创建时二选一的导入方式，决定该仓库下场景的导入深度与可选执行引擎 |
| `platform` 模式 | 导入为平台场景：完整解析步骤与用例、合并环境声明，场景可由平台引擎执行 |
| `gitlab_ci` 模式 | 流水线调度载体：只建圈选载体不解析步骤，场景仅由流水线引擎执行 |
| 测试类 | 位于测试源码目录下的 Java 类，其方法同时标注 `@Test` 与 `@RyzeTest` 才计为有效用例 |
| 步骤模板 | 测试类引用的 YAML 文件，描述请求、断言与提取等编排内容 |
| 外部执行批次 | 一次 CI 引擎触发在平台侧的执行载体，一条流水线对应一个批次 |
| 幂等闸门 | 导入前对目标分支提交的比对，用于跳过无实质变更的重复导入 |
| 写一次封印 | 环境配置首次导入后即由平台接管，仓库侧后续变更不再回同步 |
| 源探查 | 平台内置服务按固定周期检查仓库默认分支最新提交，与上次导入提交比对后在仓库上标记「源有更新」；只读，不把变更落地到场景 |

## 2. 总体描述

### 2.1 业务场景

接口自动化工程以 Maven + TestNG + Ryze 形式托管在 GitLab，用例代码与步骤 YAML 由测试开发维护在仓库中。平台侧需要把这类工程**纳入统一的场景管理、定时调度与报告体系**，同时不强迫团队迁移既有代码组织方式。

### 2.2 核心概念模型

```mermaid
flowchart LR
    R[GitLab 仓库] -->|一项目一仓库| C[仓库集成配置]
    C -->|创建时二选一| M{导入模式}
    M -- platform --> P[平台场景<br/>完整解析 + 环境合并]
    M -- gitlab_ci --> L[流水线载体<br/>仅圈选, 不解析步骤]
    P --> E1[平台执行引擎]
    L --> E2[GitLab 流水线引擎]
    E1 --> S[统一报告与定时任务]
    E2 --> S
```

### 2.3 关键约束

| 编号 | 约束 |
| ---- | ---- |
| K1 | 一个项目只允许绑定一个仓库，多仓库不支持 |
| K2 | 导入模式、项目路径、默认分支创建后不可修改，变更绑定仓库需删除后重建，也不提供「强制重导入」绕过 |
| K3 | 导入不复用既有接口导入的记录与映射机制 |
| K4 | 解析失败不创建场景，只在导入结果中列出明细 |
| K5 | CI 引擎场景在仓库不可达时无降级路径，跳过并留痕 |
| K6 | 环境首次导入后由平台接管，仓库侧变更不再回同步 |

## 3. 功能需求

### 3.1 仓库集成配置（F1.1）

**功能描述**

在项目设置中维护本项目与 GitLab 仓库的绑定关系与接入参数，作为导入与执行的前置条件。

**功能点**

1. 查看当前项目的仓库配置（一项目一仓库，页面仅呈现单个配置，不设列表）；
2. 创建仓库配置：展示名、GitLab 实例地址、项目路径、默认分支、导入模式、访问令牌、测试源码目录、资源目录、环境声明文件路径、重复导入是否覆盖平台侧内容的策略（默认不覆盖）；
3. 修改配置：展示名、GitLab 实例地址、访问令牌、测试源码目录、资源目录、环境声明文件路径、重复导入覆盖策略可改；导入模式、项目路径、默认分支创建后锁定，只读呈现；令牌只写不读；
4. 删除配置：逻辑删除，其下已导入场景保留但标记为来源已移除；变更绑定仓库或导入模式须先删除再重建；
5. 连通性测试：校验实例可达、令牌有效且具备所需权限、默认分支存在。

**业务规则**

| 编号 | 规则 |
| ---- | ---- |
| BR-1.1 | 项目与仓库一对一，重复绑定同一项目路径返回冲突错误 |
| BR-1.2 | 导入模式取值二选一（`platform` / `gitlab_ci`）；导入模式、项目路径、默认分支创建后任何入口均不接受修改，仅接受删除后重建 |
| BR-1.3 | 访问令牌加密存储，任何查询接口不回显明文 |
| BR-1.4 | 删除配置后，其下导入场景仍可查看与编辑，但不可再由流水线引擎执行；存在启用中的 GitLab 同步任务时禁止删除配置 |
| BR-1.5 | 连通性测试失败时给出可区分的原因：实例不可达 / 令牌无效 / 分支不存在 |

### 3.2 工程导入与解析（F1.2）

**功能描述**

将仓库中的测试工程扫描为平台可识别的测试模块与测试场景，支持预览后确认导入与后续增量同步。

**功能点**

1. 扫描预览：拉取默认分支，扫描测试类，给出将新建 / 更新 / 解析失败的清单与汇总，不落库；
2. 执行导入：按预览清单创建或更新模块与场景，回写导入时间、提交号与汇总；
3. 导入进度：展示阶段与计数；
4. 解析失败清单：按类、方法、原因列出，可导出；
5. 增量再导入：仓库有新提交时重复导入只做增量合并；是否以源侧内容覆盖平台侧场景内容由仓库配置的重复导入覆盖策略决定，默认不覆盖；
6. 导入场景的展示与编辑：`platform` 导入场景与平台原生场景使用同一列表与编辑器，附加来源标识与源状态徽标；`gitlab_ci` 场景在平台侧为只读圈选载体，进入场景编排页并以只读形态呈现，展示该场景对应的单个用例信息与执行入口，不提供任何编辑能力。场景与测试方法一一对应（场景标识即「类全限定名#方法名」），不存在一个场景挂多个用例。

**业务规则**

| 编号 | 规则 |
| ---- | ---- |
| BR-1.6 | 有效用例的判定为方法**同时**标注 `@Test` 与 `@RyzeTest`；位于块注释内的一律不计 |
| BR-1.7 | 导入的模块树固定为两层：第一层为包容器（每包一个，名称取完整包名，不逐级展开包层级），第二层为类模块（每测试类一个，名称取类简单名，归属其所属包容器） |
| BR-1.8 | 同一测试类重复导入只更新既有模块；包容器按完整包名认领，类模块优先按全限定名认领，未命中时按「同包容器 + 同简单名」认领并回填全限定名，不新建重复节点 |
| BR-1.9 | `platform` 模式必须解析步骤模板与 YAML 并结构化为平台步骤；引用缺失或 YAML 不合法的**不创建场景**，计入解析失败明细 |
| BR-1.10 | `gitlab_ci` 模式不读取步骤模板与 YAML，步骤结构恒为空，用例标识与名称仅取自 `@Test` 的 `description` 属性 |
| BR-1.11 | 导入幂等：同场景重复导入不产生重复数据，稳定按归属标识合并 |
| BR-1.12 | 解析失败的场景不创建；已创建场景在后续导入中因源码解析失败而保留原状，其状态标记为「源码已退化」 |
| BR-1.13 | 目标分支提交与上次导入一致、且上次无遗留真实错误时，导入被跳过并给出提示；预览阶段同样执行该判断 |
| BR-1.14 | 导入不加全局锁，同一仓库并发导入请求由后续请求读到最新结果，不做排队 |
| BR-1.15 | 导入全程失败不留半成品：任一阶段异常回滚本次写入，只保留错误记录 |

### 3.3 环境与数据源合并（F1.3）

**功能描述**

`platform` 模式导入时，将仓库声明的环境与数据源配置并入本项目环境，供导入场景执行时使用。

**功能点**

1. 读取仓库环境声明文件，提取 HTTP 配置与数据源条目；
2. 并入本项目默认环境；若项目尚无默认环境则新建一个作为默认环境；
3. 场景在自身未指定环境时，回填为该默认环境。

**业务规则**

| 编号 | 规则 |
| ---- | ---- |
| BR-1.16 | 项目已有默认环境时**合并写入**：按引用名覆盖同类条目，保留用户自行添加的其他条目 |
| BR-1.17 | 项目无默认环境时新建，默认环境名称取仓库展示名 |
| BR-1.18 | 首次写入后由平台接管该环境，仓库侧后续环境变更不再回同步，也不提供差异对比与强制覆盖 |
| BR-1.19 | 场景仅在自身环境为空时回填，不覆盖用户已选择的环境 |
| BR-1.20 | `gitlab_ci` 模式不读取、不合并任何环境配置 |

### 3.4 双引擎执行（F1.4）

**功能描述**

场景可由平台执行引擎或 GitLab 流水线引擎执行，路由依据场景来源与所属仓库的导入模式自动判定，用户不直接选择引擎。

**功能点**

1. 单场景执行：按引擎自动路由，入口为场景列表行内 [执行] 与编辑页顶部 [运行]，页面动作文案统一为「运行」；
2. CI 执行：向 GitLab 触发流水线并传入批次票据，由 CI 脚本凭票据向平台换取本批场景清单；一次任务恒定触发一条流水线；批次为服务端内部执行载体，用户不可见；
3. 执行状态查询：执行记录在平台组与流水线组全部到达终态前保持进行中，完成后写入最终结果与报告。

**业务规则**

| 编号 | 规则 |
| ---- | ---- |
| BR-1.21 | 平台原生场景恒由平台引擎执行；`platform` 模式导入场景同样仅由平台引擎执行，不提供切换到流水线引擎的入口 |
| BR-1.22 | `gitlab_ci` 模式导入场景恒由流水线引擎执行，不提供引擎切换入口，也不提供三选一下拉 |
| BR-1.23 | 一次任务恒定只触发一条流水线，不拆批、不按仓库分组；场景清单在触发流水线前冻结，执行期间平台侧的场景增删改不影响正在跑的批次 |
| BR-1.24 | 流水线用例结果按测试类全限定名与方法名匹配场景，由 CI 侧将结果制品主动推送至平台回传，回写为异步操作，不阻塞触发请求；批次一经置终态，后续写入（重复回报、轮询补拉）一律忽略，不丢结果也不被覆盖 |
| BR-1.25 | 仓库不可达时跳过该场景并在执行记录与报告中留痕，不提供手工补录、不自动改用平台引擎、不提供强制重导入 |
| BR-1.26 | 仓库被禁用时其下流水线场景不可执行，给出明确提示 |
| BR-1.27 | 结果制品推送经重试后仍失败时，流水线作业显式失败并尽力回报完成状态，禁止静默通过；平台未收到推送时按降级口径整批失败，CI 侧的 GitLab artifact 上传仅作人工排查副本、不承担回传职责 |

### 3.5 定时任务混合执行（F1.5）

**功能描述**

定时任务按既有执行范围圈选场景后，自动拆分为平台组与流水线组分别执行并统一判定完成。

**功能点**

1. 触发时按引擎分组，两组并行推进；
2. 任务执行记录同时覆盖平台组与流水线组，双侧全部到达终态前保持进行中；
3. 全部结束后写入套件报告，报告内逐场景标注执行引擎。

**业务规则**

| 编号 | 规则 |
| ---- | ---- |
| BR-1.27 | 先按任务范围取全集，再按引擎分组；「未配置步骤」不是跳过流水线组的理由 |
| BR-1.28 | 任务完成判据为：平台组全部到达终态**且**批次内所有流水线到达终态 |
| BR-1.29 | 服务重启后，未结束的流水线组由系统重新接管继续轮询，不因进程退出而滞留为进行中 |
| BR-1.30 | 任务绑定的执行环境仍然权威，导入场景自身的环境不参与套件装配 |

### 3.6 结果归集与报告（F1.6）

**功能描述**

两种引擎的执行结果归集到同一套报告体系，支持混合套件报告与单场景报告。

**功能点**

1. 套件报告首写后可追加更新，平台组先写、流水线组后追加；
2. 报告内每个用例标注其执行引擎；
3. CI 引擎单场景执行同样产生场景级报告；
4. 用例标识与名称口径与平台执行一致。

**业务规则**

| 编号 | 规则 |
| ---- | ---- |
| BR-1.31 | 报告的执行方式字段维持平台常量，不新增取值；引擎信息按场景记录在结果数据集中 |
| BR-1.32 | 同一份套件报告允许两种引擎的用例并存 |
| BR-1.33 | 用例名称优先取测试方法的描述属性，缺失时回退到方法名 |
| BR-1.34 | 流水线整体失败但有用例级报告时以用例结果为准；完全无报告时批次内全部记为失败并附流水线链接 |

### 3.7 源变更同步（F1.7）

**功能描述**

平台侧无需人工反复点击导入：平台内置探查服务按固定周期发现仓库变更并在仓库上标记「源有更新」，变更的落地由用户自行创建的定时任务按约定周期同步为平台场景，并在源工程发生改名类重构时迁移既有数据而非重建。

**功能点**

1. 新建定时任务时可选择「GitLab 同步」类型，绑定本项目仓库，按既有 Cron 配置方式设定周期；
2. 到点自动执行一次同步，也可在任务列表立即执行；
3. 同步前判定目标分支提交与上次导入是否一致，一致则跳过并记录「无变更」；
4. 同步结果按新增 / 更新 / 解析失败 / 真实错误计数记录；
5. 同步任务的启停、执行历史与失败提示与其他任务类型一致；
6. 平台内置探查服务按固定周期检查各启用仓库默认分支的最新提交，与上次导入提交不一致时在仓库上标记「源有更新」，导入或同步成功后标记自动消失；
7. 探查失败（如仓库不可达、令牌失效）时仓库标记为「探查失败」并保留上次探查结果；禁用状态的仓库不探查。

**业务规则**

| 编号 | 规则 |
| ---- | ---- |
| BR-1.35 | 源变更的发现由平台内置探查服务承担（仓库级标记、固定周期、不可配置）；变更落地到场景仅由 GitLab 同步任务与手动导入承担，用户的周期配置只作用于落地环节；不提供用户手动探查入口 |
| BR-1.36 | 同步任务与手动导入共用同一套解析、稳定键与幂等规则，两者并发时不互相阻塞，结果以最后写入为准 |
| BR-1.37 | 同步任务不产生测试报告，其执行记录只承载同步结果摘要 |
| BR-1.38 | 导入与同步时均可探查文件级改名：以导入时留存的提交号与目标分支当前提交做变更比对，识别被改名的测试类文件 |
| BR-1.39 | 改名识别结果仅在通过内容校验后才采信（新旧文件的方法集合或步骤模板引用集合重合度达标），校验不通过按删除旧类、新增新类处理；禁止仅凭相似度提示直接迁移，避免把一类的数据错挂到另一类 |
| BR-1.40 | 采信改名后迁移模块与场景的源侧归属标识，平台侧已有的场景内容、模块归属与关联关系随之保留，不产生重复节点与陈旧条目 |
| BR-1.41 | 改类名与改包名均使全限定名变化，一律走改名探查与内容校验；模块认领规则只承担无归属标识模块的首次认领与回填，不承担改名识别 |
| BR-1.42 | 无法探查到的改名（如非公开测试类）按删除旧类、新增新类处理，旧条目标记为来源已移除，不自动清理 |
| BR-1.43 | `gitlab_ci` 场景在平台侧完全只读：进入同一场景编排页但以只读形态呈现，不提供步骤编排、场景变量与处理器编辑，仅展示用例信息与执行入口；场景与测试方法一一对应，用例信息即该场景自身对应的方法（方法名 / `@Test` 的 description / 源文件），恒为单条；用例与步骤的维护一律在仓库侧完成，平台侧不开放任何写入口 |
| BR-1.44 | `platform` 导入场景复用平台原生场景的列表与编辑器，附加来源标识与源状态徽标；源侧标识只读，源引用可跳转仓库侧文件；平台侧内容修改在再次导入时按归属合并：重复导入覆盖策略关闭（默认）时不被覆盖，开启时由源侧内容覆盖；两种情况下均不回写仓库 |
| BR-1.45 | 探查只读：不触发导入、不写场景数据；「源有更新」为仓库级派生标记（探查到的最新提交与上次导入提交不一致即成立），不逐场景标注；探查失败不影响场景的查看与执行，也不自动重试 |

## 4. 非功能需求

| 编号 | 需求 |
| ---- | ---- |
| N1 | 导入为异步操作，单次导入千级用例不阻塞界面；预览与导入均给出阶段进度 |
| N2 | 导入与执行接口对令牌等凭据一律不回显 |
| N3 | 仓库地址、令牌、场景标识等用户可控内容不得拼接进命令行或脚本执行 |
| N4 | 导入失败可重入：修复问题后重复执行导入结果一致 |
| N5 | 双引擎混合场景下报告聚合延迟可接受（不早于最慢一侧结束），且不产生空洞报告 |

## 5. 数据需求

本功能新增**一张**数据实体（代码仓库配置），并对四个既有实体扩展导入与执行相关的描述性属性，其余实体不变更：

| 实体 | 变更 |
| ---- | ---- |
| 代码仓库配置 | **新增**：绑定项目、仓库标识、导入模式、导入状态摘要等 |
| 项目模块 | 扩展：源侧归属标识，用于导入场景的模块认领与去重 |
| 测试场景 | 扩展：来源类型、所属仓库、源侧标识与引用、源状态、解析状态与问题清单 |
| 执行记录 | 扩展：仓库、流水线编号与链接、批次票据、源侧引用、提交号、所属任务、用例汇总 |
| 环境 | 扩展：认领该环境的仓库标识，用于写一次封印判定 |
| 定时任务 | 扩展：任务类型新增「GitLab 同步」取值，其余执行范围与调度配置复用既有模型 |

字段级定义、索引与迁移脚本见《GitLab 集成详细设计说明书》（`docs/04-detailed-design/05-api-testing/32-gitlab-integration-overview.md` 2、6.1）。

## 6. 附录：接入前置

接入方需满足以下前置条件，缺一将导致对应能力不可用：

| 前置项 | 适用范围 | 不满足的后果 |
| ---- | ---- | ---- |
| 测试工程为 Maven + TestNG + Ryze 结构 | 全部 | 扫描不到有效用例，导入结果为空 |
| 测试方法同时标注 `@Test` 与 `@RyzeTest` | 全部 | 该方法不计为用例 |
| 提供具备所需权限的访问令牌 | 全部 | 连通性测试失败，无法导入与执行 |
| 仓库根目录提供 CI 配置文件并按平台约定改造 | 仅 `gitlab_ci` 模式 | 流水线触发失败或无法回收用例结果 |
| CI 执行器可出站访问平台服务（取批次清单、回报执行完成） | 仅 `gitlab_ci` 模式 | 清单取不到，流水线跑空或结果无法归集 |
| Ryze 版本支持按「测试类全限定名#方法名」导出结果信封 | 仅 `gitlab_ci` 模式 | 结果无类方法标识，平台无法反查场景，批次整体按失败处理 |
| 步骤模板与 YAML 存放于约定资源目录 | 仅 `platform` 模式 | 对应用例进入解析失败明细，不创建场景 |
| 环境声明文件存在于约定路径 | 仅 `platform` 模式 | 环境不合并，导入场景需手工指定环境 |

CI 配置改造约定（变量名、结果上报路径与阶段划分）在实施阶段于仓库侧约定文件中固化，本文不展开。其中与平台解析强相关的三项由平台单方确定，接入方不得变更：

| 约定项 | 取值 |
| ---- | ---- |
| 结果输出目录 | `./__Ryze_Native_Result__` |
| 结果文件命名 | `{测试类全限定名}#{方法名}.json` |
| 结果文件结构 | 信封 `{ testClass, testMethod, result }`，`result` 为 Ryze 原生结果树 |

降级口径：未回收到任何可用结果信封（未收到结果制品推送、输出目录为空或全部解析失败）时，整批按失败处理并附流水线链接；期望与实际用例数的差值计为解析失败（如 YAML 缺失），不计为执行失败。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-11 | 初始版本：GitLab 集成需求 |
| V1.0 | 2026-10-11 | 模块树改为固定两层（包容器 + 类模块），新增 F1.7 源变更同步与改名迁移规则，明确平台模式场景不路由到流水线 |
| V1.0 | 2026-10-11 | 执行链路改为批次票据 + 冻结清单 + artifact 结果回传，取消拆批；接入前置补充结果文件契约与 Ryze 版本要求 |
| V1.0 | 2026-10-11 | F1.1 功能点 1 明确页面不设配置列表，取消「列表退化为单卡片」表述 |
| V1.0 | 2026-10-11 | 删除 F1.4 批量执行功能点与 N4 圈选上限：平台不存在场景列表批量执行，单场景执行入口为场景编辑页顶部 [运行]；执行状态口径由「进度合并展示」改为「终态前保持进行中」，批次明确为服务端内部载体 |
| V1.0 | 2026-10-11 | F1.2 新增功能点 6 与 BR-1.43 / BR-1.44：明确导入场景的展示与编辑口径，`gitlab_ci` 场景平台侧完全只读并使用独立详情视图 |
| V1.0 | 2026-10-11 | F1.4 功能点 1 执行入口统一为双入口：场景列表行内 [执行] 与编辑页顶部 [运行] |
| V1.0 | 2026-10-11 | `gitlab_ci` 场景展示口径修正：进入同一场景编排页以只读形态呈现，不新增独立视图与路由 |
| V1.0 | 2026-10-11 | 收紧配置修改口径：项目路径、默认分支与导入模式一并创建后锁定，变更绑定仓库或导入模式须删除后重建 |
| V1.0 | 2026-10-11 | `gitlab_ci` 场景只读口径修正为单个用例信息：场景与测试方法一一对应（场景标识即「类全限定名#方法名」），废止「用例清单」多用例表述 |
| V1.0 | 2026-10-11 | F1.7 新增源探查服务（功能点 6/7、BR-1.45，BR-1.35 改写）：仓库级「源有更新」标记由平台内置探查承担，场景级「源已变更」废止 |
| V1.0 | 2026-10-12 | 仓库配置新增重复导入覆盖策略（默认不覆盖）：F1.1 功能点 2/3 登记该配置项，F1.2 功能点 5 与 BR-1.44 改为按策略控制内容覆盖行为 |
| V1.0 | 2026-10-12 | BR-1.38 改名探查入口由「同步时」扩大为「导入与同步时」：手动导入与同步任务共用同一改名迁移判定 |
| V1.0 | 2026-10-13 | 附录降级口径删除「先取测试报告汇总」档：未回收到任何可用结果信封时直接整批按失败并附流水线链接（汇总数字无法映射场景、写不出步骤明细） |
| V1.0 | 2026-10-17 | F1.3 单场景执行口径修订：页面动作文案统一为「运行」，不再随引擎变化（路由仍按引擎派生） |
| V1.0 | 2026-10-18 | 结果回传改为 CI 主动推送制品至平台：BR-1.24 改写、新增 BR-1.27（推送重试失败显式失败并尽力回报，GitLab artifact 保留仅作人工排查副本）；降级口径措辞同步「未收到推送」 |', 'draft', 'low', '7e57001f-0000-4000-8000-000000000008', '["接口测试","GitLab"]'::jsonb,
        'import', NULL, null, CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '6 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed004a-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0029-0000-4000-8000-000000000000', 'V1.1', 'REQ-029', 'AI 能力',
        '# 软件测试平台——AI 能力

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. AI 能力

### 1.1 总体描述

AI 能力是平台级基础能力，四大能力域构建其上：

| 能力域 | 分册 | 一句话定位 |
| ---- | ---- | ---- |
| AI+需求 生成链 | `docs/01-requirements/07-ai-capability/03-srs-ai-generation.md` | AI 将已确认的需求条目生成模块、脑图用例文档与测试用例，并推荐评审/计划圈选 |
| 智能助手 | `docs/01-requirements/07-ai-capability/04-srs-ai-assistant.md` | 一句话下达任务，经确认后将数据落库（测试用例、测试评审、测试计划） |
| 缺陷分析 | `docs/01-requirements/07-ai-capability/05-srs-ai-defect-analysis.md` | 缺陷趋势与质量度量、自动分类分诊、重复缺陷检测 |
| 辅助功能 | `docs/01-requirements/07-ai-capability/06-srs-ai-assisted-features.md` | 用例补全、用例级别推荐、测试用例执行顺序推荐 |

AI 生成链以需求管理（功能测试子模块）的需求条目为输入，需求条目的管理行为见 `docs/01-requirements/03-function-testing/03-srs-requirement-management.md`；测试评审与测试计划的快照机制沿用既有口径，见 `docs/01-requirements/03-function-testing/02-srs-business-features.md`。

**两条全局原则**：

- **AI 自主编排执行**：耗时的 AI 操作一律经 AI Task 框架执行——由 AI 自主规划步骤、在授权工具范围内调用能力并全程留痕；提交即返回，执行过程（计划、当前步骤、轨迹）实时可见，可随时中止；执行受预算护栏约束，超限自动终止，不阻塞用户当前操作。
- **提案人工采纳**：AI 产出的模块、脑图、用例、圈选建议、分类建议等一律为**提案**，不直接落为正式数据；人工逐项或批量审核采纳（含修改后采纳）后才生效。采纳后 AI 不覆盖人工修改过的内容。

**定义与缩写**：

| 术语 | 定义 |
| ---- | ---- |
| 模型配置 | 对单个大模型接入的登记（地址、密钥、能力声明、启用状态），平台可配置多个，供给方式不锁定，可为云端 API 或私有化部署端点 |
| 向量 API | 将文本转化为向量表示的独立接入配置，可与模型配置来自不同供应商 |
| 提示词 | 针对特定业务场景编排的模型指令模板，可按场景自定义覆盖默认值 |
| AI 任务 | 一次面向明确目标的 AI 工作请求，由平台统一受理并交由 AI 自主编排执行，具有类型、状态与产出提案 |
| 执行轨迹 | 执行过程中 AI 的步骤规划、决策与工具调用的全过程留痕，用户可回看，只增不改 |
| 预算护栏 | 单次执行的资源上限（步数、时长、token、成本），超限自动终止执行 |
| 提案 | AI 执行产出的结构化结果（附证据引用），处于待采纳态，经人工采纳后落库 |
| 待采纳态 | AI 提案未生效的状态，可逐项采纳、修改后采纳、批量采纳或驳回 |
| 向量化 | 将文本等内容转化为向量表示，供相似检索使用 |
| RAG | 检索增强生成：生成前先检索平台内相关业务数据，并在产出中给出来源引用 |
| 多模态 | AI 输入不仅限于文本，还包括图片、文档附件等 |

### 1.2 AI 配置与使用分析（US-AI-001）

**功能描述**：系统管理端提供 AI 能力的全局开关、模型与向量 API 配置、业务场景提示词维护与使用情况分析。  
**功能点**：

- **总开关**：全局开启 / 关闭 AI 功能；关闭后全平台 AI 功能入口不可用并提示「AI 功能已关闭」，已生成的数据与确认记录保留可查。
- **多模型配置**：可配置多个模型条目，每条含名称、能力声明（文本生成 / 多模态解析）、接入地址、密钥与启用状态；可指定默认模型，各能力域（生成链、助手、缺陷分析、辅助功能）可分别覆盖默认模型；每条模型提供连通性测试与启用 / 停用。
- **向量 API 配置**：向量化 API 独立配置（接入地址、密钥、模型标识），可与模型配置来自不同供应商；提供连通性测试；未配置时依赖检索的能力（RAG、相似检测）不可用。
- **业务场景提示词配置**：按业务场景维护提示词模板（测试用例生成、需求拆解、助手任务执行、缺陷分类分诊、缺陷重复检测、用例补全等），支持查看默认提示词、自定义覆盖与恢复默认；场景随能力域扩展，未自定义的场景使用默认提示词。
- **使用情况分析**：按时间范围、模型、能力域统计调用次数、成功 / 失败分布与失败原因、平均响应时延、token 消耗（模型服务上报时）；提供趋势图与异常提示（如失败率突增）；可下钻查看对应的 AI 任务列表。

**业务规则**：

- 配置与使用分析仅系统管理端可见可用，业务用户不可见；全部配置操作记审计。
- 关闭总开关时，进行中的 AI 任务立即终止（原因：AI 功能已关闭），已采纳产出不受影响。
- 密钥保存后不可回显，仅支持替换。
- 未配置任何启用状态的模型或向量 API 时，对应 AI 能力入口可见但不可用，并引导至配置页。
- 提示词自定义仅影响其对应场景的后续 AI 调用，不改变已采纳的产出。
- 模型调用失败（不可用、超时、限流）时，执行终止并给出原因，可再次执行；平台非 AI 功能不受影响。

### 1.3 知识与检索能力（US-AI-002）

**功能描述**：AI 的生成与问答基于平台内业务数据的向量化检索（RAG），并支持多模态输入。  
**功能点**：

- 平台内指定范围的文本类业务数据（需求条目、测试用例、缺陷、评审记录等）可被向量化索引，供相似检索与生成引用。
- AI 产出（提案、回答、建议）必须携带来源引用，引用指向具体业务对象，可点击跳转定位。
- 多模态输入：支持将用户上传的图片与文档附件作为 AI 输入解析（如需求导入的截图、附件中的需求文本）。
- 检索范围严格限定：仅检索当前用户有权限访问的范围（当前工作空间及其项目），无权限数据不得进入 AI 上下文。

**业务规则**：

- 向量索引随业务数据变更同步更新；已归档、逻辑删除的数据不再被检索。
- 检索能力不可用时，依赖检索的 AI 功能提示不可用，不降级为无引用的生成。
- 来源引用缺失的 AI 产出视为不合格产出，不得进入待采纳态供采纳。

### 1.5 AI 任务通用规则（US-AI-004）

**功能描述**：所有能力域的 AI 操作遵循统一的任务执行、提案采纳与审计口径。  
**功能点**：

- AI 任务状态：执行中（可挂起等待人工批准）→ 待采纳 → 已结束；中止、预算耗尽或执行出错时为已终止。任务列表展示类型、输入来源、执行摘要与提案摘要，完成后通知发起人。
- 执行过程可见：AI 在执行开始给出的步骤计划随任务展示；执行中的决策与工具调用可在轨迹中逐条回看；执行实时可见、可中止，中止后可再次执行（保留原执行记录）。
- 预算护栏：单次执行的步数、时长、token 与成本受上限约束，超限自动终止并给出原因；发起时可在类型默认上限内调低预算。
- 提案采纳操作：单条采纳、批量采纳、修改后采纳、驳回；驳回可附反馈再次执行（局部重新生成）。
- 人工编辑保护：已被人工编辑过的产出，AI 仅在用户显式发起重新生成并二次采纳后才可覆盖。
- 审计：每次任务发起、中止、再次执行、采纳、修改、驳回均记录操作人、时间与内容摘要，可在任务详情回看；执行轨迹只增不改。
- 权限：AI 任务的发起与采纳入口由前端按既有界面权限显隐，后端任务端点不做权限校验（仅保留任务归属可见性，工作空间 / 项目上下文经请求头传递）；AI 只能调用任务授权范围内的能力，不绕过既有业务校验。

**业务规则**：

- 同一批输入存在进行中的同类任务时，重复发起需提示。
- 执行中止、执行错误或预算耗尽不产生任何落库数据与提案；已采纳的产出不受后续执行失败影响。
- AI 产出在采纳前不落库；写入业务数据的唯一入口是采纳动作。

### 1.6 非功能需求

- 交互式 AI 建议（补全、级别推荐、重复检测等）应在 10 秒内给出首屏反馈；未及时响应时展示进行中状态与中止入口。
- AI 任务的执行过程（计划、当前步骤、轨迹）实时可见且可中止；完成后通知发起人。
- AI 产出的来源引用 100% 可跳转定位。
- 使用云端模型时，出站内容仅限任务必需的上下文，由系统管理员决定是否启用云端接入；密钥不落日志。
- 配置的模型或向量 API 不可用时，平台核心测试功能不受影响，AI 入口降级提示。
- AI 上下文严格限于当前用户有权限的数据（见 1.3），跨工作空间数据不得进入任何 AI 任务。

### 1.7 数据需求

**功能描述**：定义 AI 能力涉及的数据实体及其关系（实体级，不含表结构）。  
**功能点**：

- **AI 任务**：任务类型、输入来源（如需求条目集）、生命周期状态、发起人、终止原因、产出提案清单。
- **执行与轨迹**：执行的预算上限与实际消耗（步数 / 时长 / token / 成本）、状态与终止原因；轨迹事件的类型、摘要与时序（只增不改）。
- **提案**：结构化内容、证据引用（来源对象与原文引语）、确认状态（待采纳 / 只读）。
- **采纳记录**：所属任务、提案标识、采纳方式（单条 / 批量 / 修改后 / 驳回）、操作人、时间、落库结果引用。
- **AI 配置**：总开关状态、模型配置（接入信息、能力声明、默认标记、启用状态，可多条）、向量 API 配置、业务场景提示词（默认值与自定义值）。
- **AI 使用统计**：按时间、模型、能力域聚合的调用量、成功 / 失败与失败原因、响应时延、token 消耗。
- **向量索引条目**：来源业务对象标识、所属工作空间、索引状态。
- **AI 助手会话与消息**：定义见智能助手分册。

**业务规则**：

- 所有实体通过所属项目或工作空间归属进行隔离，遵循既有数据隔离约定。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-16 | 补建修改记录（首次修改）；1.2 业务场景提示词清单「助手意图解析」改为「助手任务执行」（智能助手执行机制切换为对话式任务执行，见 `docs/01-requirements/07-ai-capability/04-srs-ai-assistant.md`） |
| V1.0 | 2026-10-16 | 任务口径按 AI Task 双平面重写（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`）：全局原则改「AI 自主编排执行 + 提案人工采纳」，1.5 状态机改「执行中（可挂起）→ 待采纳 → 已结束 / 已终止」并补轨迹、预算护栏与再次执行，1.6 非功能同步，1.7 数据需求改任务 / 执行与轨迹 / 提案 / 采纳记录 |
| V1.0 | 2026-10-16 | 对齐 AI Task 双平面口径：模型调用失败改「执行终止、可再次执行」，统一任务框架改 AI Task 框架，结果摘要改提案摘要，提案确认 / 权限确认改采纳，终止口径「失败」改「执行错误」 |
| V1.0 | 2026-10-16 | 追溯矩阵需求并入需求管理模块：1.1 去除追溯链表述、1.2 提示词清单去除「覆盖分析」、1.4 改为指引，定义与缩写及数据需求中的追溯条目移除 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：删除 1.4 追溯矩阵指引；后续章节号未重排（1.5 起沿用原编号），以保持各分册与交互设计中「总册 1.5」等交叉引用有效 |
| V1.0 | 2026-10-19 | 权限口径调整：1.5 权限条目改为「前端按既有界面权限显隐入口，后端任务端点不做权限校验（仅保留任务归属可见性）」，业务校验与能力白名单约束不变 |', 'confirmed', 'medium', '7e57001f-0000-4000-8000-000000000009', '["AI 能力"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed004b-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed002a-0000-4000-8000-000000000000', 'V1.1', 'REQ-030', 'AI+需求 生成链',
        '# 软件测试平台——AI+需求 生成链

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. AI+需求 生成链

### 1.1 总体描述

生成链将 AI 贯穿需求到测试评审 / 测试计划的完整链路：

- `AI + 需求 = 模块 + 脑图用例文档 + 测试用例`：一次生成产出三层派生物；
- `AI + 需求 + 模块 + 脑图用例文档 + 测试用例 = 测试评审 或 测试计划`：基于全链路上下文推荐评审 / 计划的圈选范围。

输入为功能测试模块需求子模块中**已确认的需求**（见 `docs/01-requirements/03-function-testing/03-srs-requirement-management.md`）。生成由 AI 自主编排执行，产出为提案，任务与提案采纳的通用口径见总册 1.5（`docs/01-requirements/07-ai-capability/02-srs-ai-capability.md`），本分册不重复定义。

### 1.2 发起生成（US-AI-005）

**功能描述**：选择已确认的需求，发起测试设计生成任务。  
**功能点**：

- 入口一：需求列表 / 需求详情中多选需求 → 「AI 生成用例」。
- 入口二：在需求列表按模块批量选中需求，一键发起。
- 发起时可配置：目标项目与模块落位（新建顶级目录 / 挂到既有模块节点）、生成范围（所选需求）、用例粒度偏好（精简 / 标准 / 详细）。
- 提交后立即创建任务并启动执行、跳转任务详情页，不阻塞当前操作。

**业务规则**：

- 仅已确认需求可作为输入；草稿、已变更、已归档需求不可选，入口对不可选需求置灰并说明原因。
- 任务执行期间输入需求被变更时，任务按发起时的需求快照继续执行，提案采纳时提示「来源需求已变更」，由人工决定采纳或驳回。

### 1.3 执行过程与进度（US-AI-006）

**功能描述**：展示生成任务的执行过程与结果。  
**功能点**：

- 任务详情页展示 AI 规划的执行步骤与当前进度（典型步骤序列：解析需求 → 生成模块结构 → 生成脑图文档 → 标记用例节点 → 填充用例属性，序列由 AI 自主规划，仅供展示参考）。
- 完整执行过程可在轨迹中逐步回看；展示已产出提案数量与终止原因摘要。
- 支持中止进行中的任务；中止后可再次执行，保留原执行记录与轨迹。

**业务规则**：

- 中止、执行错误或预算耗尽的任务不产生任何落库数据与提案。
- 执行完成任务的提案进入待采纳态等待审核（见 1.4）。

### 1.4 提案审核与采纳（US-AI-007）

**功能描述**：审核三层生成提案，采纳后落库。  
**功能点**：

- 提案以三层树预览：模块（建议）→ 脑图文档（建议）→ 脑图节点（其中标记为用例的节点附用例属性建议：优先级、前置条件、测试步骤、预期结果、标签）。
- 审核操作：单条采纳、整树采纳、编辑后采纳、驳回；驳回可附反馈重新生成该子树。
- 采纳时指定落库的目标项目与模块位置；采纳后落库为正式模块、脑图文档与测试用例。
- 每个标记为用例的脑图节点对应生成一条测试用例实体，可回溯到脑图节点路径。

**业务规则**：

- 未采纳的提案不落库，驳回提案保留记录供审计。
- 采纳以新增方式落库，不覆盖既有模块与用例；与既有数据疑似重复时提示人工处理。
- 采纳后 AI 不再自动覆盖；人工编辑过的产出仅在显式重新生成并二次采纳后可被覆盖（总册 1.5）。

### 1.5 评审与计划圈选建议（US-AI-008）

**功能描述**：基于全链路上下文，推荐测试评审与测试计划的圈选范围。  
**功能点**：

- 对已确认的测试用例，AI 结合需求、模块归属与用例内容，推荐两类圈选：
  - **评审圈选**：建议纳入本次评审的用例子集，附建议理由（如新增需求覆盖、高风险模块）；
  - **计划圈选**：建议纳入计划执行的用例子集，可按轮次给出建议分组。
- 建议为待采纳态：可采纳、调整后采纳（增删用例）、驳回。
- 采纳后沿用既有评审 / 计划创建流程生成快照；评审意见与计划项记录引用时点的用例版本。
- 执行顺序建议由辅助功能分册定义（`docs/01-requirements/07-ai-capability/06-srs-ai-assisted-features.md`），此处不重复。

**业务规则**：

- 圈选建议不自动创建评审或计划，采纳后走既有创建流程与快照语义，AI 不改变既有快照机制。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 初始版本 |
| V1.0 | 2026-10-15 | 发起入口按钮更名「AI 生成用例」 |
| V1.0 | 2026-10-16 | 同步 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`）：1.3 固定六阶段改「AI 规划步骤 + 轨迹回看 + 中止 / 再次执行」，产物改称提案、待确认态改待采纳态，1.4 / 1.5 措辞对齐总册 1.5 |
| V1.0 | 2026-10-16 | 对齐 AI Task 双平面口径：1.4 标题及正文「确认」改「采纳」，终止口径改「中止、执行错误或预算耗尽」，圈选建议采纳后走既有创建流程 |
| V1.0 | 2026-10-16 | 追溯矩阵需求并入需求管理模块：1.6 矩阵视图与影响处置（US-AI-009）移出本分册，追溯术语改「来源对应 / 验证对应」并对齐追溯矩阵分册 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：移除生成采纳建立来源对应、圈选产出挂验证对应及其「待重新确认」标记等描述，采纳仅落库模块、脑图文档与测试用例，不建立任何对应关系 |', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000009', '["AI 能力","生成"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '4 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed004c-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed002b-0000-4000-8000-000000000000', 'V1.1', 'REQ-031', '智能助手',
        '# 软件测试平台——智能助手

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. 智能助手

### 1.1 总体描述

智能助手提供全局对话入口，用户用一句话下达任务，助手解析意图、展示待执行变更，经确认后将数据落库。落库对象为**测试用例、测试评审、测试计划**三类业务数据，并支持只读查询问答。

核心交互链路：**一句话 → 对话式任务执行（查询补全与澄清追问）→ 变更预览 → 人工确认 → 落库 → 回执**。确认机制、任务与审计口径见总册 1.5（`docs/01-requirements/07-ai-capability/02-srs-ai-capability.md`）；助手不绕过任何权限与业务校验，落库规则与界面操作完全一致。

**权限**：助手入口对业务用户可见；执行范围限于当前活跃工作空间及其项目，操作人即当前登录用户。会话仅创建人可见可操作（含历史与执行记录），管理员亦不跨人查看。

### 1.2 助手会话（US-AI-010）

**功能描述**：管理助手对话会话与上下文。  
**功能点**：

- 全局常驻入口（侧边栏 / 悬浮球），任意业务页面可唤起；未进入工作空间的页面（如「我的空间」列表页）不显示入口。
- 会话列表：仅展示当前活跃工作空间内的历史会话，按时间倒序，支持新建会话、继续既有会话、重命名与删除。
- 上下文自动携带：当前活跃工作空间、当前项目与当前页面（如正在查看的脑图文档、计划），助手可据此解析省略的指代；**助手内确定或选择的项目仅在本会话内生效，不改变全局项目选择**。
- 多轮对话：信息不足或存在歧义时助手**主动追问澄清**并给出 2~5 个快捷选项，不臆断执行；用户可补充约束（如"只生成登录模块的用例"）。
- 查询补全：助手执行任务前可先查询平台数据（项目、模块与用例文档树、计划、评审、用例清单与进度等）自行补全省略的指代与参数，仅在系统中无据可依时才询问用户。

**业务规则**：

- 会话归属于其创建时所在的工作空间：仅在归属空间的会话列表中可见，发送与执行限于归属空间；不在归属空间操作该会话时拒绝并提示切换。
- 创建会话须处于工作空间上下文。
- 切换活跃工作空间或项目后，新任务的执行范围以切换后的上下文为准；跨工作空间的指代被拒绝并提示。
- 删除会话不影响已确认落库的数据。

### 1.3 意图解析与变更预览（US-AI-011）

**功能描述**：将自然语言任务解析为结构化变更，并在执行前展示预览。  
**功能点**：

- 支持的任务类型（首批）：
  - 测试用例：新建用例、修改用例属性（优先级、预期等）、补全用例、批量标记（设置优先级 / 类型标记）；
  - 测试评审：创建评审并圈选用例、调整圈选；
  - 测试计划：创建计划并圈选用例、调整圈选。
- 预览以结构化形式展示：目标对象、变更类型、字段级变更内容（新增展示完整字段，修改展示原值 → 新值），以及将创建的记录数量。
- 执行过程中信息不足或存在多种理解时，不生成预览，由助手反问澄清并给出快捷选项。
- 预览有时效，超时后需重新解析确认。

**业务规则**：

- 预览阶段不产生任何落库数据。
- 执行结果必须落到具体业务对象与字段，无明确对象的模糊指令不予执行。

### 1.4 确认与落库（US-AI-012）

**功能描述**：人工确认预览后执行落库，并返回执行回执。  
**功能点**：

- 用户对预览执行「确认执行」或「取消」；可返回上一步修改指令后重新预览。
- 确认后按与界面操作相同的权限校验与业务校验执行落库（如圈选的用例必须存在、计划起止时间合法、参与者须为工作空间成员）。
- 执行回执：逐项展示成功与失败结果，成功项附对象链接可直接跳转，失败项附原因。
- 部分失败时，成功项保留，失败项不落库，可在回执中单项重试。
- 每次落库记录审计（见总册 1.5），会话内可回看历史执行记录。

**业务规则**：

- 未确认不落库；取消不产生任何数据。
- 确认即按预览声明的目标项目执行：**无须选中或切换到该项目**；助手内确定或选择的项目仅在本会话生效，不改变全局项目选择。
- 校验规则与错误提示与界面操作一致，助手不放宽任何必填、合法性与权限约束。
- 助手创建的评审与计划同样生成快照，快照语义沿用既有机制。

### 1.5 只读问答（US-AI-013）

**功能描述**：回答查询与统计类问题，不产生数据变更。  
**功能点**：

- 支持查询当前工作空间内的数据，如"当前计划执行进度""本周新增多少缺陷""某模块的用例数量"；进度查看类问题（评审进度、计划执行进度）直接作答并附对象跳转链接，不进入变更预览与确认流程。
- 回答附来源引用，可点击跳转到对应对象（总册 1.3）；来源分平台数据查询结果与知识检索（含平台使用指引）两类。
- 数据超出权限范围时明确拒绝并说明。

**业务规则**：

- 只读问答不进入变更预览流程，直接返回答案。
- 答案基于平台数据查询或知识检索结果，检索不可用时提示不可用，不编造数据。

### 1.6 消息附图与视觉理解（US-AI-014）

**功能描述**：用户发送消息时可附带图片，由具备视觉能力的模型理解后参与本轮问答与任务执行。  
**功能点**：

- 输入区支持选择图片随消息发送：单条消息 ≤3 张，单张 ≤10MB，支持 png / jpg / jpeg / gif / webp / bmp。
- 消息回显附图缩略图，历史会话可回看。
- 附图在本轮对话中被模型理解，可参与只读问答、澄清追问与变更预览（如据截图提出补全用例的变更）。
- 无可用视觉能力模型时发送即明确失败并提示，不静默忽略图片。

**业务规则**：

- 图片仅在发送轮次内被理解，历史消息图片不随上下文回传；跨轮追问需重新附图或以文字承接。
- 图片类型或大小超限即时拒绝并提示；图片与 1.2 的实体附件互为独立校验、互不影响。
- 图片仅存文件引用（落库不存字节），权限与会话归属口径同 1.2。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 初始版本 |
| V1.0 | 2026-10-07 | 用例属性口径对齐数据模型：移除「标签」，批量标记明确为设置优先级 / 类型标记 |
| V1.0 | 2026-10-16 | 会话增加空间归属：列表仅展示当前活跃空间的会话，创建与发送须处于归属空间，入口在未进入工作空间的页面隐藏 |
| V1.0 | 2026-10-16 | 执行机制改为对话式任务执行（工具调用）：1.1 链路改写，1.2 补主动追问快捷选项与查询补全，1.3 移出进度查看类，1.5 归入进度查看与双通道回答来源 |
| V1.0 | 2026-10-16 | 新增 1.6 消息附图与视觉理解（US-AI-014）：≤3 张 / 单张 ≤10MB、仅本轮理解不回传历史图片、无可用视觉模型发送即失败 |
| V1.0 | 2026-10-16 | 项目选择会话私有化：1.2 补"助手内项目选择不改变全局选择"，1.4 补"确认即按预览目标项目执行、无须选中或切换" |', 'confirmed', 'high', '7e57001f-0000-4000-8000-000000000007', '["AI 能力","助手"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed004d-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed002c-0000-4000-8000-000000000000', 'V1.1', 'REQ-032', '缺陷分析',
        '# 软件测试平台——缺陷分析

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. 缺陷分析

### 1.1 总体描述

缺陷分析面向缺陷管理模块（`docs/01-requirements/04-bug-management/02-srs-bug-management.md`）提供三类 AI 能力：**趋势与质量度量**、**自动分类与分诊**、**重复缺陷检测**。缺陷实体与既有状态流转、解决方案机制沿用缺陷管理分册定义，本分册只定义 AI 新增的分析行为。

**权限**：拥有缺陷查看权限的用户可见分析入口；统计与建议范围限于当前项目，跨工作空间数据不进入任何分析任务。

### 1.2 趋势与质量度量（US-AI-014）

**功能描述**：以图表与 AI 摘要展示缺陷趋势和质量度量，支持下钻定位。  
**功能点**：

- **趋势视图**：按时间轴展示新增数、关闭数、存量（激活）数，可按模块、严重等级、缺陷类型分组对比。
- **质量度量**：
  - 修复时长（激活 → 已修复的平均与分位时长）；
  - 重开率（已激活次数 ≥ 1 的缺陷占比）；
  - 重复缺陷占比（解决方案为「重复缺陷」的占比）；
  - 严重等级分布、模块分布、类型分布。
- **AI 摘要**：自动生成本期趋势解读与异常提示（如重开率环比突增、某模块缺陷集中），摘要附数据来源引用。
- 图表与摘要支持时间范围筛选；点击数据点 / 数据项下钻到对应缺陷列表，复用缺陷列表既有筛选。

**业务规则**：

- 每张图表标注统计口径与时间范围，口径与缺陷管理分册的状态定义一致。
- AI 摘要为只读建议，不写入任何数据；数据实时计算，不缓存过期结论。

### 1.3 自动分类与分诊（US-AI-015）

**功能描述**：为新建缺陷提供实时填写建议与分诊建议，并支持存量缺陷批量分类。  
**功能点**：

- **新建时实时建议**：基于用户输入的标题（及已填写的重现步骤、所属模块），实时给出优化后标题、重现步骤（含实际结果与期望结果）、缺陷类型、严重等级、优先级、所属模块、关联用例、关键词的建议值；建议附理由与来源引用。
- 表单中建议值以标记呈现，用户可一键采纳或修改后提交；用户填写值与建议不一致时以用户值为准。
- 建议请求期间不阻塞表单填写；请求失败或功能不可用时给出可重试提示，不影响缺陷提交。
- **存量批量分类**：对未分类或分类存疑的历史缺陷发起后台批量分析任务，产出建议清单为待采纳态，逐项或批量采纳后更新缺陷字段。
- **分诊队列**：对激活未指派的缺陷，AI 给出处理优先顺序建议（结合严重等级、存放时长、模块热点），展示为排序建议列表，人工可调整。

**业务规则**：

- AI 不自动改写已提交缺陷的任何字段；批量分析为待采纳态，采纳前不生效。
- 采纳与驳回记录审计（总册 1.5）。

### 1.4 重复缺陷检测（US-AI-016）

**功能描述**：在缺陷录入与存量数据中检测疑似重复缺陷，辅助归并。  
**功能点**：

- **录入时检测**：标题与重现步骤填写后，实时检索同项目内的相似存量缺陷，按相似程度排序展示（短 ID、标题、状态、相似依据），可点击跳转查看。
- 用户确认为重复时，引导按既有「重复缺陷」解决方案提交（须指定原始缺陷，规则沿用缺陷管理分册）。
- **存量批量检测**：发起后台任务扫描存量缺陷，产出疑似重复分组为待采纳态；人工采纳分组后，按既有重复缺陷机制逐条处理。

**业务规则**：

- 检测结果仅作建议，不自动合并、不自动修改任何缺陷状态。
- 相似度仅作排序依据，具体阈值不对外承诺；漏检与误检由人工复核兜底。
- 检索范围限于当前项目，且遵循 AI 数据限权（总册 1.3）。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 初始版本 |
| V1.0 | 2026-10-16 | 补建修改记录（首次修改）；待确认态改待采纳态，对齐总册 1.5 的 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`） |
| V1.0 | 2026-10-16 | 对齐 AI Task 双平面口径：存量重复缺陷分组由「人工确认」改为「人工采纳」 |
| V1.0 | 2026-10-20 | 新建时建议实时化：新增优化后标题、重现步骤（含实际结果与期望结果）、关联用例建议，移除指派候选（保留关键词）；补「请求不阻塞录入、失败可重试」规则 |', 'changed', 'medium', '7e57001f-0000-4000-8000-000000000009', '["AI 能力","缺陷分析"]'::jsonb,
        'import', NULL, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days', FALSE);

INSERT INTO requirement (id, project_id, module_id, system_version, code, title, description, status, priority, owner_id, tags, source, source_file_id, confirmed_at, created_at, updated_at, is_deleted)
VALUES ('5eed004e-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed002d-0000-4000-8000-000000000000', 'V1.1', 'REQ-033', 'AI 辅助功能',
        '# 软件测试平台——AI 辅助功能

**文档版本**：V1.0  
**日期**：2026-10-01  
**状态**：起草中

---

## 1. AI 辅助功能

### 1.1 总体描述

辅助功能面向脑图用例编辑与测试计划执行，提供三项轻量 AI 能力：**用例补全**、**用例级别推荐**、**测试用例执行顺序推荐**。三项能力均为交互式建议，遵循总册 1.5 的采纳口径（`docs/01-requirements/07-ai-capability/02-srs-ai-capability.md`）：建议不直接生效，经人工采纳后落库，人工编辑过的内容 AI 不自动覆盖。

**权限**：拥有脑图编辑权限的用户可使用用例补全与级别推荐；拥有计划执行权限的用户可使用执行顺序推荐，范围限于当前项目。

### 1.2 用例补全（US-AI-017）

**功能描述**：对选中的用例节点补全用例属性与场景建议。  
**功能点**：

- 在脑图编辑器中选中一个或多个用例节点 → 「AI 补全」，基于节点文本、所在文档层级与上游需求上下文生成补全建议。
- 补全范围：前置条件、测试步骤、预期结果、标签，以及边界值 / 异常场景的补充节点建议。
- 建议以对照形式展示（现有内容 vs AI 建议），逐条采纳、批量采纳或驳回；补充节点建议需采纳后才写入脑图。
- 生成经任务框架执行，超过交互等待时间时展示任务详情入口（总册 1.5）。

**业务规则**：

- 已有人工填写的字段默认不被覆盖，AI 只提供替换建议，须人工显式采纳。
- 空缺字段的补全同样须采纳后生效。
- 采纳结果作为用例修改记录留痕。

### 1.3 用例级别推荐（US-AI-018）

**功能描述**：为用例节点推荐优先级并给出理由。  
**功能点**：

- 对单个或批量用例节点推荐优先级（高 / 中 / 低），理由引用上游需求的重要程度与该模块的历史缺陷热点。
- 批量推荐以后台任务执行，产出建议清单为待采纳态，逐条或批量采纳后更新用例优先级。
- 展示：建议级别、现有级别、理由与来源引用；现有与建议一致的项折叠展示。

**业务规则**：

- 采纳前不修改任何用例；已有人工设定的优先级不自动改变，采纳时明确覆盖。
- 推荐仅基于平台内数据（需求、用例、缺陷），不引入外部数据。

### 1.4 执行顺序推荐（US-AI-019）

**功能描述**：为测试计划的关联用例推荐执行顺序。  
**功能点**：

- 对计划详情关联节点树中的用例，AI 给出执行顺序建议，依据：用例间依赖线索（前置条件与步骤引用）、历史失败率高的用例前置、风险与优先级。
- 以排序建议列表 / 拖拽序号形式展示，可预览建议调整前后的对比。
- 采纳后更新计划内用例的执行排序；人工可继续手动调整，最终以人工顺序为准。

**业务规则**：

- 执行顺序推荐不改变计划的关联用例集合与快照内容，仅调整展示与执行次序。
- 建议采纳前不生效；推荐为辅助参考，阻塞关系仍以用例前置条件的人工维护为准。

## 修改记录

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| V1.0 | 2026-10-01 | 初始版本 |
| V1.0 | 2026-10-16 | 补建修改记录（首次修改）；待确认态改待采纳态、执行改经任务框架，对齐总册 1.5 的 AI Task 双平面口径（`docs/02-high-level-design/07-ai-capability/03-hld-ai-task.md`） |
| V1.0 | 2026-10-16 | 对齐 AI Task 双平面口径：总册 1.5 引用改「采纳口径」，补全节点建议改「采纳后写入」、空缺字段改「采纳后生效」，超时展示改「任务详情入口」 |
| V1.0 | 2026-10-16 | 「追溯边」改「对应关系」，对齐追溯矩阵分册口径 |
| V1.0 | 2026-10-17 | 追溯矩阵需求整体取消：用例补全业务规则移除「来源需求的对应关系不变」表述 |', 'draft', 'low', '7e57001f-0000-4000-8000-000000000009', '["AI 能力","辅助功能"]'::jsonb,
        'import', NULL, null, CURRENT_TIMESTAMP - INTERVAL '1 days', CURRENT_TIMESTAMP - INTERVAL '1 days', FALSE);

-- 需求变更记录：内容修改（changed）、归档（archived）与人工确认（confirmed）
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed004f-0000-4000-8000-000000000000', '5eed003e-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000002', 'title', '{"title":"需求管理"}'::jsonb, '{"title":"需求管理（并入功能测试）"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0050-0000-4000-8000-000000000000', '5eed003e-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000002', 'description', '{"description":"…（V1.0 描述）"}'::jsonb, '{"description":"…（V1.1：拆解收敛为文档导入单一入口）"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0051-0000-4000-8000-000000000000', '5eed004d-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000009', 'module', '{"moduleId":"5eed0029-0000-4000-8000-000000000000"}'::jsonb, '{"moduleId":"5eed002c-0000-4000-8000-000000000000"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP - INTERVAL '4 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0052-0000-4000-8000-000000000000', '5eed002f-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000001', 'status', '{"status":"confirmed"}'::jsonb, '{"status":"archived"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP - INTERVAL '8 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0053-0000-4000-8000-000000000000', '5eed003d-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000006', 'status', '{"status":"confirmed"}'::jsonb, '{"status":"archived"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '6 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0054-0000-4000-8000-000000000000', '5eed002e-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000001', 'status', '{"status":"draft"}'::jsonb, '{"status":"confirmed"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '25 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0055-0000-4000-8000-000000000000', '5eed0032-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'status', '{"status":"draft"}'::jsonb, '{"status":"confirmed"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '22 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0056-0000-4000-8000-000000000000', '5eed0041-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000005', 'status', '{"status":"draft"}'::jsonb, '{"status":"confirmed"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0057-0000-4000-8000-000000000000', '5eed004b-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000009', 'status', '{"status":"draft"}'::jsonb, '{"status":"confirmed"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '9 days', CURRENT_TIMESTAMP - INTERVAL '9 days', FALSE);
INSERT INTO requirement_change_log (id, requirement_id, operator_id, change_type, before_summary, after_summary, created_at, updated_at, is_deleted)
VALUES ('5eed0058-0000-4000-8000-000000000000', '5eed0042-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000008', 'attribute', '{"priority":"medium"}'::jsonb, '{"priority":"high"}'::jsonb, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '10 days', FALSE);

-- ============================================================
-- 4. 脑图用例文档与节点（module / case / precondition / step / expected）
-- ============================================================
INSERT INTO ft_case_document (id, project_id, module_id, name, layout, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0059-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed000e-0000-4000-8000-000000000000', '用户登录', NULL, 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ft_case_document (id, project_id, module_id, name, layout, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed005a-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed001a-0000-4000-8000-000000000000', '需求导入与拆解', NULL, 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');
INSERT INTO ft_case_document (id, project_id, module_id, name, layout, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed005b-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0020-0000-4000-8000-000000000000', '接口调试', NULL, 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '20 days');

INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed005c-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', NULL, 'normal', '用户登录', NULL, NULL, 0, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed005d-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005c-0000-4000-8000-000000000000', 'normal', '账号密码登录', NULL, NULL, 1, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed005e-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005d-0000-4000-8000-000000000000', 'case', '正确账号密码登录成功', 'P1', NULL, 2, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed005f-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', 'precondition', '已在系统初始化中创建账号 demo，浏览器无有效会话', NULL, NULL, 3, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0060-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', 'step', '打开登录页 /login', NULL, NULL, 4, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0061-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', 'step', '输入 demo 与正确密码后点击「登录」', NULL, NULL, 5, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0062-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', 'step', '观察跳转与会话写入', NULL, NULL, 6, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0063-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', 'expected', '登录成功并跳转工作台首页', NULL, NULL, 7, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0064-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', 'expected', '写入有效访问令牌，接口鉴权通过', NULL, NULL, 8, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0065-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005d-0000-4000-8000-000000000000', 'case', '密码错误给出明确提示', 'P2', NULL, 9, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0066-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0065-0000-4000-8000-000000000000', 'precondition', '账号 demo 存在且密码已知错误', NULL, NULL, 10, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0067-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0065-0000-4000-8000-000000000000', 'step', '打开登录页 /login', NULL, NULL, 11, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0068-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0065-0000-4000-8000-000000000000', 'step', '输入错误密码提交', NULL, NULL, 12, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0069-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0065-0000-4000-8000-000000000000', 'expected', '页面提示「用户名或密码错误」，不暴露账号是否存在', NULL, NULL, 13, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed006a-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005d-0000-4000-8000-000000000000', 'case', '连续失败触发限流锁定', 'P0', NULL, 14, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed006b-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed006a-0000-4000-8000-000000000000', 'precondition', '账号 demo 存在，未处于锁定状态', NULL, NULL, 15, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed006c-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed006a-0000-4000-8000-000000000000', 'step', '连续 5 次输入错误密码提交', NULL, NULL, 16, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed006d-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed006a-0000-4000-8000-000000000000', 'step', '第 6 次输入正确密码提交', NULL, NULL, 17, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed006e-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed006a-0000-4000-8000-000000000000', 'expected', '第 6 次被限流拒绝并提示稍后重试', NULL, NULL, 18, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed006f-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed006a-0000-4000-8000-000000000000', 'expected', '锁定解除后可正常登录', NULL, NULL, 19, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0070-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005c-0000-4000-8000-000000000000', 'normal', '令牌刷新', NULL, NULL, 20, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0071-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0070-0000-4000-8000-000000000000', 'case', '访问令牌过期自动刷新', 'P2', NULL, 21, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0072-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', 'precondition', '用户已登录且刷新令牌有效', NULL, NULL, 22, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0073-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', 'step', '等待访问令牌过期', NULL, NULL, 23, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0074-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', 'step', '发起任意业务请求', NULL, NULL, 24, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0075-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', 'step', '观察 401 后的重放行为', NULL, NULL, 25, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0076-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', 'expected', '拦截器以刷新令牌换取新访问令牌', NULL, NULL, 26, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0077-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', 'expected', '原请求自动重放且业务无感', NULL, NULL, 27, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0078-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed005c-0000-4000-8000-000000000000', 'normal', '单点登出', NULL, NULL, 28, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0079-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0078-0000-4000-8000-000000000000', 'case', '登出后旧令牌立即失效', 'P2', NULL, 29, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed007a-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0079-0000-4000-8000-000000000000', 'precondition', '用户已登录，持有有效访问令牌', NULL, NULL, 30, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed007b-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0079-0000-4000-8000-000000000000', 'step', '点击「退出登录」', NULL, NULL, 31, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed007c-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0079-0000-4000-8000-000000000000', 'step', '携带旧访问令牌请求业务接口', NULL, NULL, 32, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed007d-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0079-0000-4000-8000-000000000000', 'expected', '退出成功且旧令牌访问返回 401', NULL, NULL, 33, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed007e-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', NULL, 'normal', '需求导入与拆解', NULL, NULL, 0, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed007f-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed007e-0000-4000-8000-000000000000', 'normal', '文档导入', NULL, NULL, 1, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0080-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed007f-0000-4000-8000-000000000000', 'case', '上传 Markdown 文档导入成功', 'P1', NULL, 2, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0081-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0080-0000-4000-8000-000000000000', 'precondition', '操作人拥有需求管理权限，准备一份 5MB 内的 Markdown 需求文档', NULL, NULL, 3, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0082-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0080-0000-4000-8000-000000000000', 'step', '在需求列表点击「导入需求」', NULL, NULL, 4, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0083-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0080-0000-4000-8000-000000000000', 'step', '上传 Markdown 文件并提交', NULL, NULL, 5, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0084-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0080-0000-4000-8000-000000000000', 'expected', '导入提交即创建 AI 任务并跳转任务详情', NULL, NULL, 6, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0085-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0080-0000-4000-8000-000000000000', 'expected', '原始文件作为来源附件保留', NULL, NULL, 7, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0086-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed007f-0000-4000-8000-000000000000', 'case', '超过 20MB 拒绝导入', 'P1', NULL, 8, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0087-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0086-0000-4000-8000-000000000000', 'precondition', '准备一份 25MB 的 Word 文档', NULL, NULL, 9, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0088-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0086-0000-4000-8000-000000000000', 'step', '在导入面板选择该文件并提交', NULL, NULL, 10, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0089-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0086-0000-4000-8000-000000000000', 'expected', '前端校验直接拒绝并提示单文件不超过 20MB', NULL, NULL, 11, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed008a-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed007e-0000-4000-8000-000000000000', 'normal', 'AI 拆解与采纳', NULL, NULL, 12, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed008b-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008a-0000-4000-8000-000000000000', 'case', '拆解建议按模块产出', 'P1', NULL, 13, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed008c-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008b-0000-4000-8000-000000000000', 'precondition', '文档导入任务已执行完成', NULL, NULL, 14, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed008d-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008b-0000-4000-8000-000000000000', 'step', '打开任务详情查看拆解建议', NULL, NULL, 15, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed008e-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008b-0000-4000-8000-000000000000', 'step', '检查建议条目的标题/描述/模块/原文定位', NULL, NULL, 16, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed008f-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008b-0000-4000-8000-000000000000', 'expected', '每条建议归属唯一模块且可定位原文', NULL, NULL, 17, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0090-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008b-0000-4000-8000-000000000000', 'expected', '整体为待采纳态，不直接落库', NULL, NULL, 18, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0091-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008a-0000-4000-8000-000000000000', 'case', '采纳后按规则生成编号', 'P0', NULL, 19, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0092-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0091-0000-4000-8000-000000000000', 'precondition', '存在待采纳的拆解建议', NULL, NULL, 20, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0093-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0091-0000-4000-8000-000000000000', 'step', '点击「批量采纳」', NULL, NULL, 21, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0094-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0091-0000-4000-8000-000000000000', 'step', '确认归属项目、模块与版本', NULL, NULL, 22, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0095-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0091-0000-4000-8000-000000000000', 'expected', '条目按 REQ-<项目内序号> 生成编号', NULL, NULL, 23, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0096-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0091-0000-4000-8000-000000000000', 'expected', '状态直接进入已确认并写入确认时间', NULL, NULL, 24, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0097-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed008a-0000-4000-8000-000000000000', 'case', '驳回建议不落库', 'P1', NULL, 25, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0098-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0097-0000-4000-8000-000000000000', 'precondition', '存在待采纳的拆解建议', NULL, NULL, 26, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0099-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0097-0000-4000-8000-000000000000', 'step', '逐条驳回全部建议', NULL, NULL, 27, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed009a-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0097-0000-4000-8000-000000000000', 'step', '刷新需求列表', NULL, NULL, 28, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed009b-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0097-0000-4000-8000-000000000000', 'expected', '驳回条目不产生需求记录', NULL, NULL, 29, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed009c-0000-4000-8000-000000000000', '5eed005a-0000-4000-8000-000000000000', '5eed0097-0000-4000-8000-000000000000', 'expected', '任务详情保留已终止原因', NULL, NULL, 30, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed009d-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', NULL, 'normal', '接口调试', NULL, NULL, 0, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed009e-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009d-0000-4000-8000-000000000000', 'normal', '快速调试', NULL, NULL, 1, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed009f-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009e-0000-4000-8000-000000000000', 'case', 'GET 请求调试返回响应', 'P1', NULL, 2, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a0-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009f-0000-4000-8000-000000000000', 'precondition', '存在可用的开发环境（本地后端 58080）', NULL, NULL, 3, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a1-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009f-0000-4000-8000-000000000000', 'step', '打开快速调试页，选择 GET 方法', NULL, NULL, 4, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a2-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009f-0000-4000-8000-000000000000', 'step', '输入 /api/health 并选择开发环境', NULL, NULL, 5, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a3-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009f-0000-4000-8000-000000000000', 'step', '点击「发送」', NULL, NULL, 6, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a4-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009f-0000-4000-8000-000000000000', 'expected', '展示响应状态码、耗时与响应体', NULL, NULL, 7, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a5-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009f-0000-4000-8000-000000000000', 'expected', '调试记录写入历史并可恢复', NULL, NULL, 8, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a6-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009e-0000-4000-8000-000000000000', 'case', 'JSON Body 提交调试', 'P2', NULL, 9, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a7-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00a6-0000-4000-8000-000000000000', 'precondition', '选择 POST 方法与 JSON 请求体类型', NULL, NULL, 10, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a8-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00a6-0000-4000-8000-000000000000', 'step', '填写 /api/auth/login 的 JSON Body', NULL, NULL, 11, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00a9-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00a6-0000-4000-8000-000000000000', 'step', '发送请求', NULL, NULL, 12, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00aa-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00a6-0000-4000-8000-000000000000', 'expected', '请求头自动携带 Content-Type: application/json', NULL, NULL, 13, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ab-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00a6-0000-4000-8000-000000000000', 'expected', '响应示例可保存到接口', NULL, NULL, 14, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ac-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed009d-0000-4000-8000-000000000000', 'normal', '保存为接口', NULL, NULL, 15, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ad-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00ac-0000-4000-8000-000000000000', 'case', 'create 模式新建接口', 'P1', NULL, 16, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ae-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00ad-0000-4000-8000-000000000000', 'precondition', '调试请求已完成且未归属任何接口', NULL, NULL, 17, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00af-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00ad-0000-4000-8000-000000000000', 'step', '点击「保存为接口」，选择 create', NULL, NULL, 18, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b0-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00ad-0000-4000-8000-000000000000', 'step', '填写名称与归属模块后保存', NULL, NULL, 19, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b1-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00ad-0000-4000-8000-000000000000', 'expected', '接口管理出现新接口且版本为 1', NULL, NULL, 20, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b2-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00ad-0000-4000-8000-000000000000', 'expected', '请求快照与响应示例完整保留', NULL, NULL, 21, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b3-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00ac-0000-4000-8000-000000000000', 'case', 'attach 模式覆盖已有接口', 'P2', NULL, 22, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b4-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00b3-0000-4000-8000-000000000000', 'precondition', '调试请求归属已有接口', NULL, NULL, 23, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b5-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00b3-0000-4000-8000-000000000000', 'step', '点击「保存为接口」，选择 attach', NULL, NULL, 24, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b6-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00b3-0000-4000-8000-000000000000', 'step', '携带乐观锁版本提交', NULL, NULL, 25, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ft_case_node (id, document_id, parent_id, type, title, priority, tags, sort_order, version, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00b7-0000-4000-8000-000000000000', '5eed005b-0000-4000-8000-000000000000', '5eed00b3-0000-4000-8000-000000000000', 'expected', '接口请求定义被覆盖且 change_version 递增', NULL, NULL, 26, 1, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');

-- ============================================================
-- 5. 接口测试环境（聚合 JSONB）
-- ============================================================
INSERT INTO api_environment (id, project_id, name, description, scope, is_default, sort_order, http_configs, variables, data_sources, processors, source_id, is_deleted, created_at, updated_at)
VALUES ('5eed00b8-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '开发环境', '本地开发后端（dev profile，端口 58080）', 'project', TRUE, 0,
        '[{"name":"本地后端","baseUrl":"http://localhost:58080","headers":[],"isDefault":true}]'::jsonb,
        '[{"name":"baseUrl","value":"http://localhost:58080","description":"本地后端地址"},{"name":"workspaceId","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","description":"示例空间（上下文头 X-Active-Workspace）"},{"name":"projectId","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","description":"Robotest 项目（上下文头 X-Active-Project）"}]'::jsonb,
        '[]'::jsonb, '[]'::jsonb, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '16 days');
INSERT INTO api_environment (id, project_id, name, description, scope, is_default, sort_order, http_configs, variables, data_sources, processors, source_id, is_deleted, created_at, updated_at)
VALUES ('5eed00b9-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '测试环境', '联调测试环境（占位地址，按实际部署替换）', 'project', FALSE, 1,
        '[{"name":"测试后端","baseUrl":"http://test.robotest.local:58080","headers":[],"isDefault":true}]'::jsonb,
        '[{"name":"baseUrl","value":"http://test.robotest.local:58080","description":"测试后端地址"}]'::jsonb,
        '[]'::jsonb, '[]'::jsonb, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '16 days', CURRENT_TIMESTAMP - INTERVAL '16 days');

-- ============================================================
-- 6. 接口管理：对齐平台真实 API 路径的示例接口
-- ============================================================
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00ba-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '用户登录', 'http', 'POST', '/api/auth/login', '账号密码登录，签发 JWT 双令牌（访问令牌 + 刷新令牌）',
        '[]'::jsonb,
        'json', '{"username":"demo","password":"******"}'::jsonb,
        '[]'::jsonb, '[]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-000000000002', 1, '{"status":200,"body":{"code":0,"msg":"success","data":{"accessToken":"eyJhbGciOi…","refreshToken":"eyJhbGciOi…","expiresIn":7200}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00bb-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '需求分页查询', 'http', 'GET', '/api/project/requirements', '按项目分页查询需求条目，支持状态/模块/负责人/版本筛选与关键词搜索',
        '[{"key":"X-Active-Workspace","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","enabled":true},{"key":"X-Active-Project","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","enabled":true}]'::jsonb,
        NULL, NULL,
        '[{"key":"pageNo","value":"1","enabled":true},{"key":"pageSize","value":"20","enabled":true},{"key":"status","value":"confirmed","enabled":true},{"key":"keyword","value":"REQ-","enabled":true}]'::jsonb, '[]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-000000000002', 1, '{"status":200,"body":{"code":0,"data":{"list":[{"code":"REQ-001","title":"需求规格说明书总览","status":"confirmed"}],"total":33}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00bc-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '新建需求', 'http', 'POST', '/api/project/requirements', '手工创建需求条目，初始状态为草稿，编号由分配器生成',
        '[{"key":"X-Active-Workspace","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","enabled":true},{"key":"X-Active-Project","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","enabled":true}]'::jsonb,
        'json', '{"title":"示例需求：登录页改版","description":"支持记住我","priority":"medium","tags":["示例"]}'::jsonb,
        '[]'::jsonb, '[]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-000000000003', 1, '{"status":200,"body":{"code":0,"data":{"id":"5eed9001-0000-4000-8000-000000000000","code":"REQ-034","status":"draft"}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00bd-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '需求确认', 'http', 'POST', '/api/project/requirements/{id}/confirm', '草稿/已变更条目人工确认后进入已确认',
        '[{"key":"X-Active-Workspace","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","enabled":true},{"key":"X-Active-Project","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","enabled":true}]'::jsonb,
        NULL, NULL,
        '[]'::jsonb, '[{"key":"id","value":"需求 ID","enabled":true}]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-000000000003', 1, '{"status":200,"body":{"code":0,"data":{"status":"confirmed"}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00be-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '用例文档节点树查询', 'http', 'GET', '/api/project/documents/{docId}/nodes', '按文档查询脑图节点树（含布局）',
        '[{"key":"X-Active-Workspace","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","enabled":true},{"key":"X-Active-Project","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","enabled":true}]'::jsonb,
        NULL, NULL,
        '[]'::jsonb, '[{"key":"docId","value":"用例文档 ID","enabled":true}]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-000000000004', 1, '{"status":200,"body":{"code":0,"data":{"node":{"id":"…","title":"用户登录","type":"normal","children":[]},"layout":null}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00bf-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '创建缺陷', 'http', 'POST', '/api/project/bugs', '登记缺陷，需指定严重等级、优先级、类型与处理人',
        '[{"key":"X-Active-Workspace","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","enabled":true},{"key":"X-Active-Project","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","enabled":true}]'::jsonb,
        'json', '{"title":"示例缺陷：列表分页异常","severity":"general","priority":"medium","bugType":"code_error","assigneeId":"7e57001f-0000-4000-8000-000000000004"}'::jsonb,
        '[]'::jsonb, '[]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-000000000006', 1, '{"status":200,"body":{"code":0,"data":{"id":"5eed9002-0000-4000-8000-000000000000","status":"active"}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00c0-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '解析导入接口定义', 'http', 'POST', '/api/project/interfaces/import/parsed', '提交已解析的 OpenAPI/Swagger 定义完成导入',
        '[{"key":"X-Active-Workspace","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","enabled":true},{"key":"X-Active-Project","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","enabled":true}]'::jsonb,
        'json', '{"sourceType":"swagger","items":[]}'::jsonb,
        '[]'::jsonb, '[]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-000000000008', 1, '{"status":200,"body":{"code":0,"data":{"createdCount":3,"overwrittenCount":1,"skippedCount":0}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');
INSERT INTO api_interface (id, project_id, module_id, name, protocol, method, path, description, headers, body_type, body, query_params, rest_params, auth, status, created_by, change_version, response_example, reference_count, validators, extractors, is_deleted, created_at, updated_at)
VALUES ('5eed00c1-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '5eed0021-0000-4000-8000-000000000000', '环境列表查询', 'http', 'GET', '/api/project/environments', '查询项目下的接口测试环境列表',
        '[{"key":"X-Active-Workspace","value":"01a1521c-b7b0-79ac-b7b8-ffc771c4a7e7","enabled":true},{"key":"X-Active-Project","value":"01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a","enabled":true}]'::jsonb,
        NULL, NULL,
        '[]'::jsonb, '[]'::jsonb,
        NULL, 'enabled', '7e57001f-0000-4000-8000-00000000000a', 1, '{"status":200,"body":{"code":0,"data":{"list":[{"name":"开发环境","isDefault":true},{"name":"测试环境","isDefault":false}]}}}'::jsonb, 0, '[]'::jsonb, '[]'::jsonb, FALSE, CURRENT_TIMESTAMP - INTERVAL '14 days', CURRENT_TIMESTAMP - INTERVAL '14 days');

-- ============================================================
-- 7. 缺陷与处理日志
-- ============================================================
INSERT INTO bug (id, project_id, title, severity, priority, status, repro_steps, reporter_id, assignee_id, related_case_id, related_plan_id, bug_type, module_id, keywords, due_date, confirmed, reopen_count, last_reopened_at, resolution, duplicate_of_bug_id, resolved_by, resolved_at, rejected_by, closed_by, closed_at, is_deleted, created_at, updated_at)
VALUES ('5eed00c2-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '登录页在 Chrome 142 下输入框自动填充失效', 'fatal', 'high', 'active', '1. 使用 Chrome 142 打开 /login
2. 输入一次账号密码并成功登录后退出
3. 再次打开 /login

**预期**：浏览器自动填充上次账号
**实际**：输入框 autocomplete 被禁用，无法自动填充',
        '7e57001f-0000-4000-8000-000000000004', '7e57001f-0000-4000-8000-000000000004', NULL, NULL, 'code_error', '5eed000e-0000-4000-8000-000000000000', 'code_error', NULL, true, 0, NULL,
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '8 hours');
INSERT INTO bug (id, project_id, title, severity, priority, status, repro_steps, reporter_id, assignee_id, related_case_id, related_plan_id, bug_type, module_id, keywords, due_date, confirmed, reopen_count, last_reopened_at, resolution, duplicate_of_bug_id, resolved_by, resolved_at, rejected_by, closed_by, closed_at, is_deleted, created_at, updated_at)
VALUES ('5eed00c3-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '需求列表分页切换后筛选条件丢失', 'serious', 'high', 'resolved', '1. 进入需求列表，筛选状态=已确认
2. 翻到第 2 页
3. 观察筛选条件

**预期**：翻页保留当前条件
**实际**：筛选条件被清空',
        '7e57001f-0000-4000-8000-000000000005', '7e57001f-0000-4000-8000-000000000002', NULL, NULL, 'code_error', '5eed001a-0000-4000-8000-000000000000', 'code_error', NULL, true, 0, NULL,
        'fixed', NULL, '7e57001f-0000-4000-8000-000000000002', CURRENT_TIMESTAMP - INTERVAL '20 hours', NULL, NULL, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '8 hours');
INSERT INTO bug (id, project_id, title, severity, priority, status, repro_steps, reporter_id, assignee_id, related_case_id, related_plan_id, bug_type, module_id, keywords, due_date, confirmed, reopen_count, last_reopened_at, resolution, duplicate_of_bug_id, resolved_by, resolved_at, rejected_by, closed_by, closed_at, is_deleted, created_at, updated_at)
VALUES ('5eed00c4-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '缺陷列表首屏加载超过 3 秒', 'general', 'medium', 'closed', '1. 造 500 条缺陷数据
2. 打开缺陷列表页

**预期**：首屏 1 秒内可见
**实际**：加载耗时 3.4 秒',
        '7e57001f-0000-4000-8000-000000000003', '7e57001f-0000-4000-8000-000000000006', NULL, NULL, 'performance', '5eed001e-0000-4000-8000-000000000000', 'performance', NULL, true, 0, NULL,
        'fixed', NULL, '7e57001f-0000-4000-8000-000000000006', CURRENT_TIMESTAMP - INTERVAL '20 hours', NULL, '7e57001f-0000-4000-8000-000000000006', CURRENT_TIMESTAMP - INTERVAL '8 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '8 hours');
INSERT INTO bug (id, project_id, title, severity, priority, status, repro_steps, reporter_id, assignee_id, related_case_id, related_plan_id, bug_type, module_id, keywords, due_date, confirmed, reopen_count, last_reopened_at, resolution, duplicate_of_bug_id, resolved_by, resolved_at, rejected_by, closed_by, closed_at, is_deleted, created_at, updated_at)
VALUES ('5eed00c5-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '成员管理页长昵称溢出未裁剪', 'minor', 'low', 'active', '1. 将成员昵称改为 30 个字符
2. 打开成员管理页

**实际**：昵称溢出表格列，挤压操作列',
        '7e57001f-0000-4000-8000-000000000007', '7e57001f-0000-4000-8000-000000000005', NULL, NULL, 'ui_improvement', '5eed0016-0000-4000-8000-000000000000', 'ui_improvement', NULL, false, 0, NULL,
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '8 hours');
INSERT INTO bug (id, project_id, title, severity, priority, status, repro_steps, reporter_id, assignee_id, related_case_id, related_plan_id, bug_type, module_id, keywords, due_date, confirmed, reopen_count, last_reopened_at, resolution, duplicate_of_bug_id, resolved_by, resolved_at, rejected_by, closed_by, closed_at, is_deleted, created_at, updated_at)
VALUES ('5eed00c6-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '接口环境变量明文回显未脱敏', 'serious', 'high', 'closed', '1. 在环境变量中配置 token 类敏感值
2. 重新打开环境详情

**实际**：敏感变量明文回显，未按 hasValue 口径处理',
        '7e57001f-0000-4000-8000-000000000009', '7e57001f-0000-4000-8000-00000000000a', NULL, NULL, 'security', '5eed0027-0000-4000-8000-000000000000', 'security', NULL, true, 0, NULL,
        'fixed', NULL, '7e57001f-0000-4000-8000-00000000000a', CURRENT_TIMESTAMP - INTERVAL '20 hours', NULL, '7e57001f-0000-4000-8000-00000000000a', CURRENT_TIMESTAMP - INTERVAL '8 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '8 hours');
INSERT INTO bug (id, project_id, title, severity, priority, status, repro_steps, reporter_id, assignee_id, related_case_id, related_plan_id, bug_type, module_id, keywords, due_date, confirmed, reopen_count, last_reopened_at, resolution, duplicate_of_bug_id, resolved_by, resolved_at, rejected_by, closed_by, closed_at, is_deleted, created_at, updated_at)
VALUES ('5eed00c7-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '评审通过后仍希望支持继续标记', 'general', 'low', 'rejected', '1. 完成一次评审（状态=已通过）
2. 尝试继续标记

**实际**：按当前设计评审完成后只读，属预期行为',
        '7e57001f-0000-4000-8000-000000000004', '7e57001f-0000-4000-8000-000000000002', NULL, NULL, 'design_defect', '5eed001c-0000-4000-8000-000000000000', 'design_defect', NULL, false, 0, NULL,
        'by_design', NULL, NULL, NULL, '7e57001f-0000-4000-8000-000000000002', NULL, NULL, FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '8 hours');

-- 缺陷处理日志
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00c8-0000-4000-8000-000000000000', '5eed00c2-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'create', '创建缺陷并指派给处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00c9-0000-4000-8000-000000000000', '5eed00c2-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'assign', '指派处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00ca-0000-4000-8000-000000000000', '5eed00c2-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'confirm', '确认受理', FALSE, CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '11 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00cb-0000-4000-8000-000000000000', '5eed00c3-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000005', 'create', '创建缺陷并指派给处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00cc-0000-4000-8000-000000000000', '5eed00c3-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000005', 'assign', '指派处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00cd-0000-4000-8000-000000000000', '5eed00c3-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000002', 'confirm', '确认受理', FALSE, CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '11 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00ce-0000-4000-8000-000000000000', '5eed00c3-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000002', 'resolve', '已修复：需求列表分页切换后筛选条件丢失', FALSE, CURRENT_TIMESTAMP - INTERVAL '20 hours', CURRENT_TIMESTAMP - INTERVAL '20 hours');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00cf-0000-4000-8000-000000000000', '5eed00c4-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', 'create', '创建缺陷并指派给处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d0-0000-4000-8000-000000000000', '5eed00c4-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', 'assign', '指派处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d1-0000-4000-8000-000000000000', '5eed00c4-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000006', 'confirm', '确认受理', FALSE, CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '11 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d2-0000-4000-8000-000000000000', '5eed00c4-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000006', 'resolve', '已修复：缺陷列表首屏加载超过 3 秒', FALSE, CURRENT_TIMESTAMP - INTERVAL '20 hours', CURRENT_TIMESTAMP - INTERVAL '20 hours');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d3-0000-4000-8000-000000000000', '5eed00c4-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000006', 'close', '验证通过，关闭缺陷', FALSE, CURRENT_TIMESTAMP - INTERVAL '8 hours', CURRENT_TIMESTAMP - INTERVAL '8 hours');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d4-0000-4000-8000-000000000000', '5eed00c5-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000007', 'create', '创建缺陷并指派给处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d5-0000-4000-8000-000000000000', '5eed00c5-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000007', 'assign', '指派处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d6-0000-4000-8000-000000000000', '5eed00c6-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000009', 'create', '创建缺陷并指派给处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d7-0000-4000-8000-000000000000', '5eed00c6-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000009', 'assign', '指派处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d8-0000-4000-8000-000000000000', '5eed00c6-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-00000000000a', 'confirm', '确认受理', FALSE, CURRENT_TIMESTAMP - INTERVAL '11 days', CURRENT_TIMESTAMP - INTERVAL '11 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00d9-0000-4000-8000-000000000000', '5eed00c6-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-00000000000a', 'resolve', '已修复：接口环境变量明文回显未脱敏', FALSE, CURRENT_TIMESTAMP - INTERVAL '20 hours', CURRENT_TIMESTAMP - INTERVAL '20 hours');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00da-0000-4000-8000-000000000000', '5eed00c6-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-00000000000a', 'close', '验证通过，关闭缺陷', FALSE, CURRENT_TIMESTAMP - INTERVAL '8 hours', CURRENT_TIMESTAMP - INTERVAL '8 hours');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00db-0000-4000-8000-000000000000', '5eed00c7-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'create', '创建缺陷并指派给处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00dc-0000-4000-8000-000000000000', '5eed00c7-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'assign', '指派处理人', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES ('5eed00dd-0000-4000-8000-000000000000', '5eed00c7-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000002', 'reject', '按设计预期，驳回缺陷', FALSE, CURRENT_TIMESTAMP - INTERVAL '10 hours', CURRENT_TIMESTAMP - INTERVAL '10 hours');

-- ============================================================
-- 8. 项目动态
-- ============================================================
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e0-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000001', '示例用户', 'requirement', '5eed002e-0000-4000-8000-000000000000', '需求规格说明书总览', 'import', '导入需求文档《需求规格说明书总览》，采纳为 REQ-001', CURRENT_TIMESTAMP - INTERVAL '25 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '25 days');
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e1-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000003', '林嘉怡', 'requirement', '5eed0032-0000-4000-8000-000000000000', '用户管理页', 'confirm', '确认需求 REQ-005（用户管理页）', CURRENT_TIMESTAMP - INTERVAL '22 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '22 days', CURRENT_TIMESTAMP - INTERVAL '22 days');
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e2-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000004', '王浩', 'test_case', '5eed0059-0000-4000-8000-000000000000', '用户登录', 'create', '创建用例文档《用户登录》，新增 5 条用例', CURRENT_TIMESTAMP - INTERVAL '18 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '18 days', CURRENT_TIMESTAMP - INTERVAL '18 days');
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e3-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000006', '周子墨', 'bug', '5eed00c2-0000-4000-8000-000000000000', '登录页在 Chrome 142 下输入框自动填充失效', 'create', '登记缺陷（致命 / 代码错误）并指派王浩', CURRENT_TIMESTAMP - INTERVAL '12 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '12 days', CURRENT_TIMESTAMP - INTERVAL '12 days');
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e4-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000005', '赵雨欣', 'api_interface', '5eed00bb-0000-4000-8000-000000000000', '需求分页查询', 'create', '保存调试请求为接口《需求分页查询》', CURRENT_TIMESTAMP - INTERVAL '10 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '10 days', CURRENT_TIMESTAMP - INTERVAL '10 days');
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e5-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000002', '陈思远', 'requirement', '5eed003e-0000-4000-8000-000000000000', '需求管理（并入功能测试）', 'update', '修改需求 REQ-017 标题与描述，状态变更为已变更', CURRENT_TIMESTAMP - INTERVAL '3 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days');
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e6-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000003', '林嘉怡', 'test_review', '5eed00de-0000-4000-8000-000000000000', '用户登录用例评审', 'create', '发起评审《用户登录用例评审》，关联 5 条用例', CURRENT_TIMESTAMP - INTERVAL '2 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ws_project_activity (id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name, action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES ('5eed00e7-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '7e57001f-0000-4000-8000-000000000004', '王浩', 'test_plan', '5eed00df-0000-4000-8000-000000000000', 'V1.0 登录回归测试计划', 'execute', '执行测试计划：通过 3 条、失败 1 条、未测 1 条', CURRENT_TIMESTAMP - INTERVAL '1 days', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 days', CURRENT_TIMESTAMP - INTERVAL '1 days');

-- ============================================================
-- 9. 测试评审（已通过）与测试计划（进行中）——快照基于《用户登录》用例文档
--    快照口径：模块快照复制文档所属模块祖先链（directory）+ 文档节点（document），
--    节点快照 document_snapshot_id 指向文档对应的模块快照行 ID
-- ============================================================
-- 9.1 测试评审：用户登录用例评审（已通过）
INSERT INTO ft_review (id, project_id, title, description, initiator_id, participant_ids, status, is_deleted, created_at, updated_at)
VALUES ('5eed00de-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', '用户登录用例评审', '覆盖账号密码登录、令牌刷新与单点登出的用例评审', '7e57001f-0000-4000-8000-000000000003',
        '["7e57001f-0000-4000-8000-000000000003","7e57001f-0000-4000-8000-000000000002","7e57001f-0000-4000-8000-000000000004","7e57001f-0000-4000-8000-000000000005"]'::jsonb, 'completed', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 days');
INSERT INTO ft_review_module_snapshot (id, review_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed00e8-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed000c-0000-4000-8000-000000000000', NULL, '系统管理', 'directory', 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_module_snapshot (id, review_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed00e9-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed000e-0000-4000-8000-000000000000', '5eed00e8-0000-4000-8000-000000000000', '用户管理', 'directory', 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_module_snapshot (id, review_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed00ea-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed00e9-0000-4000-8000-000000000000', '用户登录', 'document', 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00eb-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed005c-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', NULL, '用户登录', 'normal', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ec-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed005d-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00eb-0000-4000-8000-000000000000', '账号密码登录', 'normal', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ed-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ec-0000-4000-8000-000000000000', '正确账号密码登录成功', 'case', 'P1', true, 'pass', '7e57001f-0000-4000-8000-000000000003', CURRENT_TIMESTAMP - INTERVAL '25 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ee-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed005f-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '已在系统初始化中创建账号 demo，浏览器无有效会话', 'precondition', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ef-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0060-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '打开登录页 /login', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f0-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0061-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '输入 demo 与正确密码后点击「登录」', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f1-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0062-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '观察跳转与会话写入', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f2-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0063-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '登录成功并跳转工作台首页', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f3-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0064-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '写入有效访问令牌，接口鉴权通过', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f4-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0065-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ec-0000-4000-8000-000000000000', '密码错误给出明确提示', 'case', 'P2', true, 'pass', '7e57001f-0000-4000-8000-000000000003', CURRENT_TIMESTAMP - INTERVAL '25 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f5-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0066-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f4-0000-4000-8000-000000000000', '账号 demo 存在且密码已知错误', 'precondition', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f6-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0067-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f4-0000-4000-8000-000000000000', '打开登录页 /login', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f7-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0068-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f4-0000-4000-8000-000000000000', '输入错误密码提交', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f8-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0069-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f4-0000-4000-8000-000000000000', '页面提示「用户名或密码错误」，不暴露账号是否存在', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00f9-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed006a-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ec-0000-4000-8000-000000000000', '连续失败触发限流锁定', 'case', 'P0', true, 'fail', '7e57001f-0000-4000-8000-000000000003', CURRENT_TIMESTAMP - INTERVAL '25 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00fa-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed006b-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f9-0000-4000-8000-000000000000', '账号 demo 存在，未处于锁定状态', 'precondition', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00fb-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed006c-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f9-0000-4000-8000-000000000000', '连续 5 次输入错误密码提交', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00fc-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed006d-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f9-0000-4000-8000-000000000000', '第 6 次输入正确密码提交', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00fd-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed006e-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f9-0000-4000-8000-000000000000', '第 6 次被限流拒绝并提示稍后重试', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00fe-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed006f-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00f9-0000-4000-8000-000000000000', '锁定解除后可正常登录', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed00ff-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0070-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00eb-0000-4000-8000-000000000000', '令牌刷新', 'normal', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0100-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00ff-0000-4000-8000-000000000000', '访问令牌过期自动刷新', 'case', 'P2', true, 'pass', '7e57001f-0000-4000-8000-000000000003', CURRENT_TIMESTAMP - INTERVAL '25 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0101-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0072-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0100-0000-4000-8000-000000000000', '用户已登录且刷新令牌有效', 'precondition', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0102-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0073-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0100-0000-4000-8000-000000000000', '等待访问令牌过期', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0103-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0074-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0100-0000-4000-8000-000000000000', '发起任意业务请求', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0104-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0075-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0100-0000-4000-8000-000000000000', '观察 401 后的重放行为', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0105-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0076-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0100-0000-4000-8000-000000000000', '拦截器以刷新令牌换取新访问令牌', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0106-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0077-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0100-0000-4000-8000-000000000000', '原请求自动重放且业务无感', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0107-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0078-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed00eb-0000-4000-8000-000000000000', '单点登出', 'normal', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0108-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0079-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0107-0000-4000-8000-000000000000', '登出后旧令牌立即失效', 'case', 'P2', true, 'pass', '7e57001f-0000-4000-8000-000000000003', CURRENT_TIMESTAMP - INTERVAL '25 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0109-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed007a-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0108-0000-4000-8000-000000000000', '用户已登录，持有有效访问令牌', 'precondition', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed010a-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed007b-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0108-0000-4000-8000-000000000000', '点击「退出登录」', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed010b-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed007c-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0108-0000-4000-8000-000000000000', '携带旧访问令牌请求业务接口', 'step', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_node_snapshot (id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed010c-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed007d-0000-4000-8000-000000000000', '5eed00ea-0000-4000-8000-000000000000', '5eed0108-0000-4000-8000-000000000000', '退出成功且旧令牌访问返回 401', 'expected', NULL, false, NULL, NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_review_record (id, review_id, snapshot_node_id, reviewer_id, operation_type, mark, comment, is_deleted, created_at, updated_at)
VALUES ('5eed010d-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', 'mark', 'pass', '通过', FALSE, CURRENT_TIMESTAMP - INTERVAL '25 hours', CURRENT_TIMESTAMP - INTERVAL '25 hours');
INSERT INTO ft_review_record (id, review_id, snapshot_node_id, reviewer_id, operation_type, mark, comment, is_deleted, created_at, updated_at)
VALUES ('5eed010e-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed00f4-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', 'mark', 'pass', '通过', FALSE, CURRENT_TIMESTAMP - INTERVAL '25 hours', CURRENT_TIMESTAMP - INTERVAL '25 hours');
INSERT INTO ft_review_record (id, review_id, snapshot_node_id, reviewer_id, operation_type, mark, comment, is_deleted, created_at, updated_at)
VALUES ('5eed010f-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed00f9-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', 'mark', 'fail', '限流次数与详设不一致，需按 03-srs-common 限流口径修正后复审', FALSE, CURRENT_TIMESTAMP - INTERVAL '25 hours', CURRENT_TIMESTAMP - INTERVAL '25 hours');
INSERT INTO ft_review_record (id, review_id, snapshot_node_id, reviewer_id, operation_type, mark, comment, is_deleted, created_at, updated_at)
VALUES ('5eed0110-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0100-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', 'mark', 'pass', '通过', FALSE, CURRENT_TIMESTAMP - INTERVAL '25 hours', CURRENT_TIMESTAMP - INTERVAL '25 hours');
INSERT INTO ft_review_record (id, review_id, snapshot_node_id, reviewer_id, operation_type, mark, comment, is_deleted, created_at, updated_at)
VALUES ('5eed0111-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed0108-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000003', 'mark', 'pass', '通过', FALSE, CURRENT_TIMESTAMP - INTERVAL '25 hours', CURRENT_TIMESTAMP - INTERVAL '25 hours');
INSERT INTO ft_review_record (id, review_id, snapshot_node_id, reviewer_id, operation_type, mark, comment, is_deleted, created_at, updated_at)
VALUES ('5eed0112-0000-4000-8000-000000000000', '5eed00de-0000-4000-8000-000000000000', '5eed00ed-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000002', 'comment', NULL, '建议补充记住我场景的负向用例，下个迭代跟进', FALSE, CURRENT_TIMESTAMP - INTERVAL '24 hours', CURRENT_TIMESTAMP - INTERVAL '24 hours');

-- 9.2 测试计划：V1.0 登录回归测试计划（进行中）
INSERT INTO ft_plan (id, project_id, name, description, status, executor_id, start_time, end_time, environment, snapshot_synced_at, is_deleted, created_at, updated_at)
VALUES ('5eed00df-0000-4000-8000-000000000000', '01a1523d-d4c9-7dbe-8f6f-8ff34bffac0a', 'V1.0 登录回归测试计划', 'V1.0 发布前登录链路回归：覆盖登录、令牌刷新与登出', 'in_progress', '7e57001f-0000-4000-8000-000000000004',
        CURRENT_TIMESTAMP - INTERVAL '1 days', CURRENT_TIMESTAMP - INTERVAL '-2 days', '测试环境', CURRENT_TIMESTAMP - INTERVAL '2 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '1 days', CURRENT_TIMESTAMP - INTERVAL '2 hours');
INSERT INTO ft_plan_module_snapshot (id, plan_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0113-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed000c-0000-4000-8000-000000000000', NULL, '系统管理', 'directory', 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_module_snapshot (id, plan_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0114-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed000e-0000-4000-8000-000000000000', '5eed0113-0000-4000-8000-000000000000', '用户管理', 'directory', 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_module_snapshot (id, plan_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES ('5eed0115-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0059-0000-4000-8000-000000000000', '5eed0114-0000-4000-8000-000000000000', '用户登录', 'document', 0, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0116-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed005c-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', NULL, '用户登录', 'normal', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0117-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed005d-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0116-0000-4000-8000-000000000000', '账号密码登录', 'normal', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0118-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed005e-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0117-0000-4000-8000-000000000000', '正确账号密码登录成功', 'case', 'P1', true, 'pass', '7e57001f-0000-4000-8000-000000000004', CURRENT_TIMESTAMP - INTERVAL '1 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0119-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed005f-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0118-0000-4000-8000-000000000000', '已在系统初始化中创建账号 demo，浏览器无有效会话', 'precondition', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed011a-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0060-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0118-0000-4000-8000-000000000000', '打开登录页 /login', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed011b-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0061-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0118-0000-4000-8000-000000000000', '输入 demo 与正确密码后点击「登录」', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed011c-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0062-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0118-0000-4000-8000-000000000000', '观察跳转与会话写入', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed011d-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0063-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0118-0000-4000-8000-000000000000', '登录成功并跳转工作台首页', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed011e-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0064-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0118-0000-4000-8000-000000000000', '写入有效访问令牌，接口鉴权通过', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed011f-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0065-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0117-0000-4000-8000-000000000000', '密码错误给出明确提示', 'case', 'P2', true, 'pass', '7e57001f-0000-4000-8000-000000000004', CURRENT_TIMESTAMP - INTERVAL '1 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0120-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0066-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed011f-0000-4000-8000-000000000000', '账号 demo 存在且密码已知错误', 'precondition', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0121-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0067-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed011f-0000-4000-8000-000000000000', '打开登录页 /login', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0122-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0068-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed011f-0000-4000-8000-000000000000', '输入错误密码提交', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0123-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0069-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed011f-0000-4000-8000-000000000000', '页面提示「用户名或密码错误」，不暴露账号是否存在', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0124-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed006a-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0117-0000-4000-8000-000000000000', '连续失败触发限流锁定', 'case', 'P0', true, 'fail', '7e57001f-0000-4000-8000-000000000004', CURRENT_TIMESTAMP - INTERVAL '1 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0125-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed006b-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0124-0000-4000-8000-000000000000', '账号 demo 存在，未处于锁定状态', 'precondition', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0126-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed006c-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0124-0000-4000-8000-000000000000', '连续 5 次输入错误密码提交', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0127-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed006d-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0124-0000-4000-8000-000000000000', '第 6 次输入正确密码提交', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0128-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed006e-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0124-0000-4000-8000-000000000000', '第 6 次被限流拒绝并提示稍后重试', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0129-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed006f-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0124-0000-4000-8000-000000000000', '锁定解除后可正常登录', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed012a-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0070-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0116-0000-4000-8000-000000000000', '令牌刷新', 'normal', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed012b-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0071-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed012a-0000-4000-8000-000000000000', '访问令牌过期自动刷新', 'case', 'P2', true, 'pass', '7e57001f-0000-4000-8000-000000000004', CURRENT_TIMESTAMP - INTERVAL '1 hours', 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed012c-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0072-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed012b-0000-4000-8000-000000000000', '用户已登录且刷新令牌有效', 'precondition', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed012d-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0073-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed012b-0000-4000-8000-000000000000', '等待访问令牌过期', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed012e-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0074-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed012b-0000-4000-8000-000000000000', '发起任意业务请求', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed012f-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0075-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed012b-0000-4000-8000-000000000000', '观察 401 后的重放行为', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0130-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0076-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed012b-0000-4000-8000-000000000000', '拦截器以刷新令牌换取新访问令牌', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0131-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0077-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed012b-0000-4000-8000-000000000000', '原请求自动重放且业务无感', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0132-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0078-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0116-0000-4000-8000-000000000000', '单点登出', 'normal', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0133-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0079-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0132-0000-4000-8000-000000000000', '登出后旧令牌立即失效', 'case', 'P2', true, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0134-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed007a-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0133-0000-4000-8000-000000000000', '用户已登录，持有有效访问令牌', 'precondition', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0135-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed007b-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0133-0000-4000-8000-000000000000', '点击「退出登录」', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0136-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed007c-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0133-0000-4000-8000-000000000000', '携带旧访问令牌请求业务接口', 'step', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_node_snapshot (id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority, is_associated, last_result, last_executor_id, last_executed_at, sort_order, ai_generated, is_deleted, created_at, updated_at)
VALUES ('5eed0137-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed007d-0000-4000-8000-000000000000', '5eed0115-0000-4000-8000-000000000000', '5eed0133-0000-4000-8000-000000000000', '退出成功且旧令牌访问返回 401', 'expected', NULL, false, 'untested', NULL, NULL, 0, FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '2 days');
INSERT INTO ft_plan_execution_record (id, plan_id, snapshot_node_id, executor_id, result, note, executed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0138-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0118-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'pass', '执行通过', CURRENT_TIMESTAMP - INTERVAL '2 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours');
INSERT INTO ft_plan_execution_record (id, plan_id, snapshot_node_id, executor_id, result, note, executed_at, is_deleted, created_at, updated_at)
VALUES ('5eed0139-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed011f-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'pass', '执行通过', CURRENT_TIMESTAMP - INTERVAL '2 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours');
INSERT INTO ft_plan_execution_record (id, plan_id, snapshot_node_id, executor_id, result, note, executed_at, is_deleted, created_at, updated_at)
VALUES ('5eed013a-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed0124-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'fail', '关联缺陷：锁定次数与需求不一致，已提缺陷跟踪', CURRENT_TIMESTAMP - INTERVAL '2 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours');
INSERT INTO ft_plan_execution_record (id, plan_id, snapshot_node_id, executor_id, result, note, executed_at, is_deleted, created_at, updated_at)
VALUES ('5eed013b-0000-4000-8000-000000000000', '5eed00df-0000-4000-8000-000000000000', '5eed012b-0000-4000-8000-000000000000', '7e57001f-0000-4000-8000-000000000004', 'pass', '执行通过', CURRENT_TIMESTAMP - INTERVAL '2 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours');

COMMIT;
