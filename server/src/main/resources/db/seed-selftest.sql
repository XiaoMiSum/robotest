-- ============================================================
-- Robotest 自测项目种子数据（幂等，可重复执行）
-- 目标库：docker pgvector 容器中的 robotest 库
-- 被测对象：Robotest 平台自身（用户/空间/用例/评审/计划/缺陷/接口测试）
--
-- 使用：
--   docker cp server/src/main/resources/db/seed-selftest.sql pgvector:/tmp/
--   docker exec pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f /tmp/seed-selftest.sql
--
-- UUID 约定：所有种子数据 id 均以 `7e57` 开头（段1 编码实体类型），
--           因此清理段统一用 `id::text LIKE '7e57%'` 即可精确圈定本脚本写入的行。
--
--   7e570001 ws_project                 7e570002 project_module
--   7e570003 test_case_document         7e570004 test_case_node
--   7e570005 api_environment            7e570006 api_interface
--   7e570007 api_scene                  7e570008 api_component
--   7e570009 api_function               7e57000a api_mock_definition
--   7e57000b test_plan                  7e57000c test_plan_module_snapshot
--   7e57000d test_plan_node_snapshot    7e57000e test_plan_execution_record
--   7e57000f test_review                7e570010 test_review_module_snapshot
--   7e570011 test_review_node_snapshot  7e570012 test_review_record
--   7e570013 bug                        7e570014 bug_log
--   7e570015 requirement_pool_item      7e570016 ws_project_activity
--   7e570017 api_report                 7e570018 api_execution_record
--   7e570019 api_debug_record           7e57001a api_scheduled_task
--   7e57001b api_scheduled_task_execution  7e57001c api_swagger_url
--   7e57001d api_import_record            7e57001e ws_workspace
--   7e57001f sys_user                     7e570020 ws_user
--
-- 示例空间：ws_workspace「示例空间」+ sys_user「示例用户(demo)」+ ws_user 空间管理员，
--           示例项目「Robotest 平台自测」归属该空间。demo 口令与 admin 相同（复用其 password_hash）。
--           另含 10 名空间成员（7e57001f-…-0002~000b / 7e570020-…-0002~000b），
--           workspace_role 取内置「成员」，用于成员列表/权限演示。
--
-- 依赖：本脚本写入 ws_project_activity，其 DDL 已包含在同目录 schema.sql 中
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- ============================================================
-- 0. 清理旧种子（子表在前）
-- ============================================================
DELETE FROM test_review_record            WHERE id::text LIKE '7e57%';
DELETE FROM test_review_node_snapshot     WHERE id::text LIKE '7e57%';
DELETE FROM test_review_module_snapshot   WHERE id::text LIKE '7e57%';
DELETE FROM test_review                   WHERE id::text LIKE '7e57%';

DELETE FROM test_plan_execution_record    WHERE id::text LIKE '7e57%';
DELETE FROM test_plan_node_snapshot       WHERE id::text LIKE '7e57%';
DELETE FROM test_plan_module_snapshot     WHERE id::text LIKE '7e57%';
DELETE FROM test_plan                     WHERE id::text LIKE '7e57%';

DELETE FROM bug_log                       WHERE id::text LIKE '7e57%';
DELETE FROM bug_attachment                 WHERE id::text LIKE '7e57%';
DELETE FROM bug                           WHERE id::text LIKE '7e57%';

DELETE FROM ws_project_activity           WHERE id::text LIKE '7e57%';
DELETE FROM requirement_document_rel      WHERE id::text LIKE '7e57%';
DELETE FROM requirement_pool_item         WHERE id::text LIKE '7e57%';

DELETE FROM api_report                    WHERE id::text LIKE '7e57%';
DELETE FROM api_execution_record          WHERE id::text LIKE '7e57%';
DELETE FROM api_scheduled_task_execution  WHERE id::text LIKE '7e57%';
DELETE FROM api_scheduled_task            WHERE id::text LIKE '7e57%';
DELETE FROM api_import_record             WHERE id::text LIKE '7e57%';
DELETE FROM api_import_mapping            WHERE id::text LIKE '7e57%';
DELETE FROM api_debug_record              WHERE id::text LIKE '7e57%';
DELETE FROM api_mock_access_log           WHERE id::text LIKE '7e57%';
DELETE FROM api_mock_definition           WHERE id::text LIKE '7e57%';
DELETE FROM api_scene_follow              WHERE id::text LIKE '7e57%';
DELETE FROM api_scene                     WHERE id::text LIKE '7e57%';
DELETE FROM api_interface_change_log      WHERE id::text LIKE '7e57%';
DELETE FROM api_interface_follow          WHERE id::text LIKE '7e57%';
DELETE FROM api_interface                 WHERE id::text LIKE '7e57%';
DELETE FROM api_environment               WHERE id::text LIKE '7e57%';
DELETE FROM api_swagger_url               WHERE id::text LIKE '7e57%';
DELETE FROM api_function                  WHERE id::text LIKE '7e57%';
DELETE FROM api_component                 WHERE id::text LIKE '7e57%';

DELETE FROM test_case_node                WHERE id::text LIKE '7e57%';
DELETE FROM test_case_document            WHERE id::text LIKE '7e57%';
DELETE FROM project_module                WHERE id::text LIKE '7e57%';

DELETE FROM ws_project                    WHERE id::text LIKE '7e57%';

DELETE FROM ws_user                       WHERE id::text LIKE '7e57%';
DELETE FROM sys_user                      WHERE id::text LIKE '7e57%';
DELETE FROM ws_workspace                  WHERE id::text LIKE '7e57%';

-- ============================================================
-- 0.1 示例空间 / 示例用户 / 空间成员
--     workspace_role 取系统内置空间角色：c0000000-...-0001 管理员、c0000000-...-0002 成员
-- ============================================================
INSERT INTO ws_workspace (id, name, description, status, created_by, is_deleted, created_at, updated_at)
VALUES
('7e57001e-0000-4000-8000-000000000001',
 '示例空间',
 '自测/演示用示例空间，由示例用户(demo)担任空间管理员，示例项目「Robotest 平台自测」归属本空间。',
 'active',
 '7e57001f-0000-4000-8000-000000000001',
 false, now() - interval '30 days', now());

INSERT INTO sys_user (id, username, name, email, password_hash, avatar_url, status,
                      last_active_workspace_id, is_deleted, created_at, updated_at)
VALUES
('7e57001f-0000-4000-8000-000000000001',
 'demo',
 '示例用户',
 'demo@robotest.local',
 '$2a$10$htqAJ566CXGXdQeQY./b4OwiDxFD.tNa/i2XQ9Dm7EA4V3Tg66KsO',
 NULL,
 'active',
 '7e57001e-0000-4000-8000-000000000001',
 false, now() - interval '30 days', now());

-- 示例用户 = 示例空间管理员（admin / A001 不加入本空间，示例数据统一挂到 demo）
INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id,
                     joined_at, last_accessed_at, is_deleted, created_at, updated_at)
VALUES
('7e570020-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001',
 '7e57001e-0000-4000-8000-000000000001',
 'c0000000-0000-0000-0000-000000000001',
 '7e570001-0000-4000-8000-000000000001',
 now() - interval '30 days', now() - interval '1 day', false, now() - interval '30 days', now());

-- ============================================================
-- 0.2 示例空间成员（10 人，workspace_role = 内置「成员」）
--     口令复用 demo 的 password_hash；邮箱/用户名唯一索引保证不与既有账号冲突
-- ============================================================
INSERT INTO sys_user (id, username, name, email, password_hash, avatar_url, status,
                      last_active_workspace_id, is_deleted, created_at, updated_at)
SELECT v.id::uuid, v.username, v.name, v.email,
       '$2a$10$htqAJ566CXGXdQeQY./b4OwiDxFD.tNa/i2XQ9Dm7EA4V3Tg66KsO',
       NULL, 'active', NULL, false, now() - interval '20 days', now()
FROM (VALUES
  ('7e57001f-0000-4000-8000-000000000002','chensiyuan',    '陈思远','chensiyuan@robotest.local'),
  ('7e57001f-0000-4000-8000-000000000003','linjiayi',      '林嘉怡','linjiayi@robotest.local'),
  ('7e57001f-0000-4000-8000-000000000004','wanghao',       '王浩',  'wanghao@robotest.local'),
  ('7e57001f-0000-4000-8000-000000000005','zhaoyuxin',     '赵雨欣','zhaoyuxin@robotest.local'),
  ('7e57001f-0000-4000-8000-000000000006','zhouzimo',      '周子墨','zhouzimo@robotest.local'),
  ('7e57001f-0000-4000-8000-000000000007','sunwanting',    '孙婉婷','sunwanting@robotest.local'),
  ('7e57001f-0000-4000-8000-000000000008','wujunlei',      '吴俊磊','wujunlei@robotest.local'),
  ('7e57001f-0000-4000-8000-000000000009','zhengxinyao',   '郑欣瑶','zhengxinyao@robotest.local'),
  ('7e57001f-0000-4000-8000-00000000000a','huangzhiqiang', '黄志强','huangzhiqiang@robotest.local'),
  ('7e57001f-0000-4000-8000-00000000000b','hejing',        '何静',  'hejing@robotest.local')
) AS v(id, username, name, email);

INSERT INTO ws_user (id, user_id, workspace_id, workspace_role, default_project_id,
                     joined_at, last_accessed_at, is_deleted, created_at, updated_at)
SELECT ('7e570020-0000-4000-8000-' || substr(v.uid, 25))::uuid,
       v.uid::uuid,
       '7e57001e-0000-4000-8000-000000000001',
       'c0000000-0000-0000-0000-000000000002',
       NULL,
       now() - interval '20 days' + (v.n || ' days')::interval,
       now() - ((v.n + 1) * interval '1 hour'),
       false, now() - interval '20 days', now()
FROM (VALUES
  ('7e57001f-0000-4000-8000-000000000002', 0),
  ('7e57001f-0000-4000-8000-000000000003', 1),
  ('7e57001f-0000-4000-8000-000000000004', 2),
  ('7e57001f-0000-4000-8000-000000000005', 3),
  ('7e57001f-0000-4000-8000-000000000006', 4),
  ('7e57001f-0000-4000-8000-000000000007', 5),
  ('7e57001f-0000-4000-8000-000000000008', 6),
  ('7e57001f-0000-4000-8000-000000000009', 7),
  ('7e57001f-0000-4000-8000-00000000000a', 8),
  ('7e57001f-0000-4000-8000-00000000000b', 9)
) AS v(uid, n);

-- ============================================================
-- 1. 项目本体（归属示例空间）
-- ============================================================
INSERT INTO ws_project
(id, workspace_id, name, description, status, start_time, end_time, created_by,
 is_deleted, created_at, updated_at)
VALUES
('7e570001-0000-4000-8000-000000000001',
 '7e57001e-0000-4000-8000-000000000001',
 'Robotest 平台自测',
 '以 Robotest 平台自身为被测对象：覆盖系统管理、功能测试（用例/评审/计划）、缺陷管理与接口测试五大业务域，用于功能验证、演示与回归。',
 'active',
 now() - interval '30 days',
 now() + interval '60 days',
 '7e57001f-0000-4000-8000-000000000001',
 false, now() - interval '30 days', now());

-- ============================================================
-- 2. 项目模块树（纯目录节点，type 由查询派生，表内无 type 列）
-- ============================================================
INSERT INTO project_module (id, project_id, parent_id, name, sort_order, is_deleted, created_at, updated_at)
SELECT v.id::uuid, v.project_id::uuid, NULLIF(v.parent_id, '')::uuid, v.name, v.sort_order::int,
       false, now() - interval '29 days', now()
FROM (VALUES
  ('7e570002-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001','',        '系统管理',0),
  ('7e570002-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001','',        '功能测试',1),
  ('7e570002-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001','',        '接口测试',2),
  ('7e570002-0000-4000-8000-000000000004','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000001','用户与权限',0),
  ('7e570002-0000-4000-8000-000000000005','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000001','空间与项目',1),
  ('7e570002-0000-4000-8000-000000000006','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000002','用例管理',0),
  ('7e570002-0000-4000-8000-000000000007','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000002','评审与计划',1),
  ('7e570002-0000-4000-8000-000000000008','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000002','缺陷管理',2),
  ('7e570002-0000-4000-8000-000000000009','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000003','接口管理',0),
  ('7e570002-0000-4000-8000-00000000000a','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000003','场景与执行',1)
) AS v(id, project_id, parent_id, name, sort_order);

-- ============================================================
-- 3. 用例文档（layout 与服务创建口径一致：{"template":"right","offsets":{}}）
-- ============================================================
INSERT INTO test_case_document (id, project_id, module_id, name, layout, sort_order, is_deleted, created_at, updated_at)
SELECT v.id::uuid, v.project_id::uuid, v.module_id::uuid, v.name::text,
       '{"template":"right","offsets":{}}'::jsonb, 0, false,
       now() - interval '28 days', now()
FROM (VALUES
  ('7e570003-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000004','用户登录与鉴权'),
  ('7e570003-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000006','用例管理核心流程'),
  ('7e570003-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000007','评审与计划执行'),
  ('7e570003-0000-4000-8000-000000000004','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000008','缺陷生命周期'),
  ('7e570003-0000-4000-8000-000000000005','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000009','接口与场景执行')
) AS v(id, project_id, module_id, name);

-- ============================================================
-- 4. 用例脑图节点
--    结构：每份文档 = 1 个根节点(normal, parent_id NULL) + 若干 case 节点
--          case 的直接子节点只能是 precondition / step / expected
-- ============================================================
INSERT INTO test_case_node (id, document_id, parent_id, type, title, priority, sort_order,
                            version, ai_generated, is_deleted, created_at, updated_at)
SELECT v.id::uuid, v.document_id::uuid, NULLIF(v.parent_id, '')::uuid, v.type, v.title,
       NULLIF(v.priority, ''), v.sort_order::int, 1, false, false,
       now() - interval '28 days', now()
FROM (VALUES
  -- ---------- 文档1：用户登录与鉴权（19 节点） ----------
  ('7e570004-0000-4000-8000-000000000100','7e570003-0000-4000-8000-000000000001','',                                       'normal',       '用户登录与鉴权', NULL, 0),
  ('7e570004-0000-4000-8000-000000000101','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000100', 'case',         '使用正确账号密码登录成功', 'P0', 0),
  ('7e570004-0000-4000-8000-000000000102','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000101', 'precondition', '已存在启用状态的测试账号 demo', NULL, 0),
  ('7e570004-0000-4000-8000-000000000103','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000101', 'step',         '在登录页输入用户名与密码', NULL, 1),
  ('7e570004-0000-4000-8000-000000000104','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000101', 'step',         '点击登录按钮提交', NULL, 2),
  ('7e570004-0000-4000-8000-000000000105','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000101', 'expected',     '跳转至工作台，顶部显示当前用户名', NULL, 3),
  ('7e570004-0000-4000-8000-000000000106','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000101', 'expected',     '写入有效的访问与刷新令牌', NULL, 4),

  ('7e570004-0000-4000-8000-000000000107','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000100', 'case',         '密码错误时给出明确提示且不跳转', 'P1', 1),
  ('7e570004-0000-4000-8000-000000000108','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000107', 'precondition', '登录页已打开且本地无残留令牌', NULL, 0),
  ('7e570004-0000-4000-8000-000000000109','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000107', 'step',         '输入正确用户名与错误密码并提交', NULL, 1),
  ('7e570004-0000-4000-8000-00000000010a','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000107', 'step',         '观察页面提示与路由变化', NULL, 2),
  ('7e570004-0000-4000-8000-00000000010b','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000107', 'expected',     '提示「用户名或密码错误」并停留在登录页', NULL, 3),
  ('7e570004-0000-4000-8000-00000000010c','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000107', 'expected',     '本地未写入任何令牌', NULL, 4),

  ('7e570004-0000-4000-8000-00000000010d','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000100', 'case',         '被禁用账号登录被拦截', 'P1', 2),
  ('7e570004-0000-4000-8000-00000000010e','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000010d', 'precondition', '存在状态为 disabled 的测试账号', NULL, 0),
  ('7e570004-0000-4000-8000-00000000010f','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000010d', 'step',         '输入被禁用账号与正确密码提交', NULL, 1),
  ('7e570004-0000-4000-8000-000000000110','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000010d', 'step',         '观察响应提示与登录状态', NULL, 2),
  ('7e570004-0000-4000-8000-000000000111','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000010d', 'expected',     '提示账号已被禁用', NULL, 3),
  ('7e570004-0000-4000-8000-000000000112','7e570003-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000010d', 'expected',     '不建立会话，不写入令牌', NULL, 4),

  -- ---------- 文档2：用例管理核心流程（20 节点） ----------
  ('7e570004-0000-4000-8000-000000000200','7e570003-0000-4000-8000-000000000002','',                                       'normal',       '用例管理核心流程', NULL, 0),
  ('7e570004-0000-4000-8000-000000000201','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000200', 'normal',       '文档与模块', NULL, 0),

  ('7e570004-0000-4000-8000-000000000202','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000201', 'case',         '在模块下新建用例文档并自动生成根节点', 'P0', 0),
  ('7e570004-0000-4000-8000-000000000203','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000202', 'precondition', '当前项目已有「用例管理」模块', NULL, 0),
  ('7e570004-0000-4000-8000-000000000204','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000202', 'step',         '在模块节点上选择新建文档', NULL, 1),
  ('7e570004-0000-4000-8000-000000000205','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000202', 'step',         '输入文档名并确认', NULL, 2),
  ('7e570004-0000-4000-8000-000000000206','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000202', 'expected',     '文档创建成功，自动出现同名脑图根节点', NULL, 3),
  ('7e570004-0000-4000-8000-000000000207','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000202', 'expected',     '文档树刷新并高亮新建的文档', NULL, 4),

  ('7e570004-0000-4000-8000-000000000208','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000201', 'case',         '重命名文档后根节点标题同步更新', 'P1', 1),
  ('7e570004-0000-4000-8000-000000000209','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000208', 'precondition', '已存在文档及其同名根节点', NULL, 0),
  ('7e570004-0000-4000-8000-00000000020a','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000208', 'step',         '在左侧文档树上重命名该文档', NULL, 1),
  ('7e570004-0000-4000-8000-00000000020b','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000208', 'step',         '重新打开该文档查看脑图', NULL, 2),
  ('7e570004-0000-4000-8000-00000000020c','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000208', 'expected',     '根节点标题与新文档名完全一致', NULL, 3),
  ('7e570004-0000-4000-8000-00000000020d','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000208', 'expected',     '刷新页面后标题仍保持一致', NULL, 4),

  ('7e570004-0000-4000-8000-00000000020e','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-000000000200', 'case',         '删除模块时其下文档一并逻辑删除', 'P2', 1),
  ('7e570004-0000-4000-8000-00000000020f','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-00000000020e', 'precondition', '目标模块下存在 2 份文档', NULL, 0),
  ('7e570004-0000-4000-8000-000000000210','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-00000000020e', 'step',         '删除该模块并确认二次弹窗', NULL, 1),
  ('7e570004-0000-4000-8000-000000000211','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-00000000020e', 'step',         '刷新用例管理页面', NULL, 2),
  ('7e570004-0000-4000-8000-000000000212','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-00000000020e', 'expected',     '模块与其下文档均不再显示', NULL, 3),
  ('7e570004-0000-4000-8000-000000000213','7e570003-0000-4000-8000-000000000002','7e570004-0000-4000-8000-00000000020e', 'expected',     '数据库中对应行为 is_deleted = true', NULL, 4),

  -- ---------- 文档3：评审与计划执行（13 节点） ----------
  ('7e570004-0000-4000-8000-000000000300','7e570003-0000-4000-8000-000000000003','',                                       'normal',       '评审与计划执行', NULL, 0),
  ('7e570004-0000-4000-8000-000000000301','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000300', 'case',         '创建计划时固化模块与节点快照', 'P0', 0),
  ('7e570004-0000-4000-8000-000000000302','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000301', 'precondition', '用例库中已有模块与文档', NULL, 0),
  ('7e570004-0000-4000-8000-000000000303','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000301', 'step',         '选择文档并圈选用例，创建计划', NULL, 1),
  ('7e570004-0000-4000-8000-000000000304','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000301', 'step',         '打开计划详情查看快照树', NULL, 2),
  ('7e570004-0000-4000-8000-000000000305','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000301', 'expected',     '快照结构与源模块树一致且名称已固化', NULL, 3),
  ('7e570004-0000-4000-8000-000000000306','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000301', 'expected',     '后续修改源用例不影响已建计划的快照', NULL, 4),

  ('7e570004-0000-4000-8000-000000000307','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000300', 'case',         '执行记录回写快照最新结果', 'P1', 1),
  ('7e570004-0000-4000-8000-000000000308','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000307', 'precondition', '计划中已圈选若干用例', NULL, 0),
  ('7e570004-0000-4000-8000-000000000309','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000307', 'step',         '对其中一条用例标记执行结果 pass', NULL, 1),
  ('7e570004-0000-4000-8000-00000000030a','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000307', 'step',         '返回计划详情查看该用例状态', NULL, 2),
  ('7e570004-0000-4000-8000-00000000030b','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000307', 'expected',     '快照 last_result 更新为 pass 并记录执行人与时间', NULL, 3),
  ('7e570004-0000-4000-8000-00000000030c','7e570003-0000-4000-8000-000000000003','7e570004-0000-4000-8000-000000000307', 'expected',     '计划状态由 new 自动变为 in_progress', NULL, 4),

  -- ---------- 文档4：缺陷生命周期（12 节点） ----------
  ('7e570004-0000-4000-8000-000000000400','7e570003-0000-4000-8000-000000000004','',                                       'normal',       '缺陷生命周期', NULL, 0),
  ('7e570004-0000-4000-8000-000000000401','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000400', 'case',         '缺陷 active → resolved → closed 闭环', 'P0', 0),
  ('7e570004-0000-4000-8000-000000000402','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000401', 'precondition', '项目中存在一条 active 状态的缺陷', NULL, 0),
  ('7e570004-0000-4000-8000-000000000403','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000401', 'step',         '选择解决方案 fixed 并填写说明提交', NULL, 1),
  ('7e570004-0000-4000-8000-000000000404','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000401', 'step',         '回归通过后执行关闭操作', NULL, 2),
  ('7e570004-0000-4000-8000-000000000405','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000401', 'expected',     '状态依次变为 resolved 与 closed', NULL, 3),
  ('7e570004-0000-4000-8000-000000000406','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000401', 'expected',     'resolved_at、closed_by、closed_at 均被正确写入', NULL, 4),

  ('7e570004-0000-4000-8000-000000000407','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000400', 'case',         '缺陷 reopen 后清空解决信息并累加重开次数', 'P1', 1),
  ('7e570004-0000-4000-8000-000000000408','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000407', 'precondition', '存在一条 resolved 状态的缺陷', NULL, 0),
  ('7e570004-0000-4000-8000-000000000409','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000407', 'step',         '填写重开说明并提交', NULL, 1),
  ('7e570004-0000-4000-8000-00000000040a','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000407', 'expected',     '状态回到 active，reopen_count 加 1', NULL, 2),
  ('7e570004-0000-4000-8000-00000000040b','7e570003-0000-4000-8000-000000000004','7e570004-0000-4000-8000-000000000407', 'expected',     'resolution、resolved_by、resolved_at 全部清空', NULL, 3),

  -- ---------- 文档5：接口与场景执行（18 节点） ----------
  ('7e570004-0000-4000-8000-000000000500','7e570003-0000-4000-8000-000000000005','',                                       'normal',       '接口与场景执行', NULL, 0),
  ('7e570004-0000-4000-8000-000000000501','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000500', 'case',         '场景步骤按 sortOrder 升序执行', 'P0', 0),
  ('7e570004-0000-4000-8000-000000000502','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000501', 'precondition', '场景中已有 3 个 HTTP 步骤', NULL, 0),
  ('7e570004-0000-4000-8000-000000000503','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000501', 'step',         '执行该场景', NULL, 1),
  ('7e570004-0000-4000-8000-000000000504','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000501', 'step',         '查看执行报告中的步骤顺序', NULL, 2),
  ('7e570004-0000-4000-8000-000000000505','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000501', 'expected',     '步骤顺序与编辑器中的排序完全一致', NULL, 3),
  ('7e570004-0000-4000-8000-000000000506','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000501', 'expected',     '报告 step.index 从 1 连续递增', NULL, 4),

  ('7e570004-0000-4000-8000-000000000507','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000500', 'case',         '断言失败时场景状态置为 failed', 'P1', 1),
  ('7e570004-0000-4000-8000-000000000508','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000507', 'precondition', '场景中存在一个必然失败的断言', NULL, 0),
  ('7e570004-0000-4000-8000-000000000509','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000507', 'step',         '执行该场景', NULL, 1),
  ('7e570004-0000-4000-8000-00000000050a','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000507', 'step',         '打开报告查看断言明细', NULL, 2),
  ('7e570004-0000-4000-8000-00000000050b','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000507', 'expected',     '失败断言 pass=false 且标注 expected/actual', NULL, 3),
  ('7e570004-0000-4000-8000-00000000050c','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000507', 'expected',     '报告汇总 status 为 failed', NULL, 4),

  ('7e570004-0000-4000-8000-00000000050d','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-000000000500', 'case',         '提取器输出可供后续步骤引用', 'P1', 2),
  ('7e570004-0000-4000-8000-00000000050e','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-00000000050d', 'precondition', '步骤 1 已配置 json_field 提取器', NULL, 0),
  ('7e570004-0000-4000-8000-00000000050f','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-00000000050d', 'step',         '执行场景并观察步骤 2 的请求头', NULL, 1),
  ('7e570004-0000-4000-8000-000000000510','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-00000000050d', 'expected',     '步骤 2 使用了步骤 1 提取到的变量值', NULL, 2),
  ('7e570004-0000-4000-8000-000000000511','7e570003-0000-4000-8000-000000000005','7e570004-0000-4000-8000-00000000050d', 'expected',     '报告 variables 中包含该变量及其取值', NULL, 3)
) AS v(id, document_id, parent_id, type, title, priority, sort_order);

-- ============================================================
-- 5. 接口测试 — 环境
--    JSONB 聚合结构与 EnvironmentEffectiveSnapshot.normalize() 写入口径一致
-- ============================================================
INSERT INTO api_environment
(id, project_id, name, description, scope, is_default, sort_order,
 http_configs, variables, data_sources, processors, is_deleted, created_at, updated_at)
VALUES
('7e570005-0000-4000-8000-000000000001',
 '7e570001-0000-4000-8000-000000000001',
 '本地开发环境', '本机 docker 启动的 robotest 后端（58080）', 'project', true, 0,
 '[{"name":"默认配置","refName":"default","baseUrl":"http://localhost:58080",
    "headers":[{"key":"Content-Type","value":"application/json","enabled":true}],
    "isDefault":true}]',
 '[{"name":"workspaceId","value":"7e57001e-0000-4000-8000-000000000001","description":"当前工作空间 ID"},
   {"name":"demoUser","value":"demo","description":"示例用户用户名"}]',
 '[]',
 '[{"processorType":"preprocessor","name":"注入 Trace 头",
    "config":{"headers":{"X-Trace-Id":"robotest-self-test"}},
    "sortOrder":0,"enabled":true}]',
 false, now() - interval '20 days', now()),

('7e570005-0000-4000-8000-000000000002',
 '7e570001-0000-4000-8000-000000000001',
 '回归环境', '预发/回归环境地址（示例）', 'project', false, 1,
 '[{"name":"默认配置","refName":"default","baseUrl":"https://staging.robotest.example.com",
    "headers":[],"isDefault":true}]',
 '[]',
 '[{"name":"回归库","refName":"regression","driver":"org.postgresql.Driver",
    "url":"jdbc:postgresql://localhost:5432/robotest",
    "connectionProperties":{"user":"postgres"},"maxPoolSize":5,"isDefault":true}]',
 '[]',
 false, now() - interval '20 days', now());

-- ============================================================
-- 6. 接口测试 — 接口定义
--    status 口径以代码为准：enabled / disabled（ApiInterfaceServiceImpl:120）
-- ============================================================
INSERT INTO api_interface
(id, project_id, module_id, name, protocol, method, path, description,
 headers, body_type, body, query_params, rest_params, auth, status,
 created_by, change_version, response_example, reference_count,
 validators, extractors, is_deleted, created_at, updated_at)
VALUES
-- 1 登录获取双令牌
('7e570006-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000004',
 '登录获取双令牌','http','POST','/api/auth/login','账号密码登录，返回 access/refresh 双令牌',
 '[{"key":"Content-Type","value":"application/json","enabled":true}]','json',
 '{"type":"json","content":{"username":"demo","password":"Passw0rd!"}}','[]',
 '[]',NULL,'enabled',
 '7e57001f-0000-4000-8000-000000000001',1,
 '{"status":200,"headers":null,"body":{"code":0,"data":{"accessToken":"...","refreshToken":"..."}}}',0,
 '[{"id":"7e570006-0000-4000-8000-000000000a01","name":"状态码为200","enabled":true,"target":"status_code","condition":"equals","expression":"","expected":"200"},{"id":"7e570006-0000-4000-8000-000000000a02","name":"业务码为0","enabled":true,"target":"json_field","condition":"equals","expression":"$.code","expected":"0"}]',
 '[{"id":"7e570006-0000-4000-8000-000000000b01","name":"提取访问令牌","enabled":true,"source":"json_field","expression":"$.data.accessToken","variableName":"accessToken","description":""}]',
 false, now() - interval '18 days', now()),

-- 2 刷新访问令牌
('7e570006-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000004',
 '刷新访问令牌','http','POST','/api/auth/refresh','用 refresh token 换取新的 access token',
 '[{"key":"Content-Type","value":"application/json","enabled":true}]','json',
 '{"type":"json","content":{"refreshToken":"{{refreshToken}}"}}','[]',
 '[]',NULL,'enabled',
 '7e57001f-0000-4000-8000-000000000001',1,
 '{"status":200,"headers":null,"body":{"code":0,"data":{"accessToken":"..."}}}',0,
 '[{"id":"7e570006-0000-4000-8000-000000000a03","name":"状态码为200","enabled":true,"target":"status_code","condition":"equals","expression":"","expected":"200"}]',
 '[]',
 false, now() - interval '18 days', now()),

-- 3 分页查询用户
('7e570006-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000004',
 '分页查询用户','http','GET','/api/admin/users','管理员分页查询系统用户，支持关键字与状态筛选',
 '[{"key":"X-Active-Workspace","value":"{{workspaceId}}","enabled":true}]','',
 NULL,
 '[{"key":"pageNo","value":"1","enabled":true},{"key":"pageSize","value":"20","enabled":true}]',
 '[]','{"type":"bearer","token":"{{accessToken}}"}','enabled',
 '7e57001f-0000-4000-8000-000000000001',2,
 '{"status":200,"headers":null,"body":{"code":0,"data":{"list":[],"total":0}}}',0,
 '[{"id":"7e570006-0000-4000-8000-000000000a04","name":"业务码为0","enabled":true,"target":"json_field","condition":"equals","expression":"$.code","expected":"0"}]',
 '[{"id":"7e570006-0000-4000-8000-000000000b02","name":"提取用户总数","enabled":true,"source":"json_field","expression":"$.data.total","variableName":"userTotal","description":""}]',
 false, now() - interval '18 days', now()),

-- 4 项目列表查询
('7e570006-0000-4000-8000-000000000004','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000005',
 '项目列表查询','http','GET','/api/workspace/projects','查询当前工作空间下的项目列表与计数',
 '[{"key":"X-Active-Workspace","value":"{{workspaceId}}","enabled":true}]','',
 NULL,
 '[{"key":"status","value":"active","enabled":true}]',
 '[]',NULL,'enabled',
 '7e57001f-0000-4000-8000-000000000001',1,
 '{"status":200,"headers":null,"body":{"code":0,"data":[]}}',0,
 '[{"id":"7e570006-0000-4000-8000-000000000a05","name":"状态码为200","enabled":true,"target":"status_code","condition":"equals","expression":"","expected":"200"}]',
 '[]',
 false, now() - interval '18 days', now()),

-- 5 接口分页列表
('7e570006-0000-4000-8000-000000000005','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000009',
 '接口分页列表','http','GET','/api/project/interfaces','按模块与关键字分页查询接口定义',
 '[{"key":"X-Active-Workspace","value":"{{workspaceId}}","enabled":true},{"key":"X-Active-Project","value":"{{projectId}}","enabled":true}]','',
 NULL,
 '[{"key":"pageNo","value":"1","enabled":true},{"key":"pageSize","value":"20","enabled":true}]',
 '[]','{"type":"bearer","token":"{{accessToken}}"}','enabled',
 '7e57001f-0000-4000-8000-000000000001',1,
 '{"status":200,"headers":null,"body":{"code":0,"data":{"list":[],"total":0}}}',0,
 '[]','[]',
 false, now() - interval '18 days', now()),

-- 6 创建接口定义
('7e570006-0000-4000-8000-000000000006','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000009',
 '创建接口定义','http','POST','/api/project/interfaces','在指定模块下创建接口定义',
 '[{"key":"Content-Type","value":"application/json","enabled":true},{"key":"X-Active-Project","value":"{{projectId}}","enabled":true}]','json',
 '{"type":"json","content":{"name":"示例接口","method":"GET","path":"/api/example","moduleId":null}}','[]',
 '[]','{"type":"bearer","token":"{{accessToken}}"}','enabled',
 '7e57001f-0000-4000-8000-000000000001',1,
 '{"status":200,"headers":null,"body":{"code":0,"data":{"id":"..."}}}',1,
 '[{"id":"7e570006-0000-4000-8000-000000000a06","name":"业务码为0","enabled":true,"target":"json_field","condition":"equals","expression":"$.code","expected":"0"}]',
 '[{"id":"7e570006-0000-4000-8000-000000000b03","name":"提取接口 ID","enabled":true,"source":"json_field","expression":"$.data.id","variableName":"createdInterfaceId","description":""}]',
 false, now() - interval '18 days', now()),

-- 7 查询环境详情
('7e570006-0000-4000-8000-000000000007','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-00000000000a',
 '查询环境详情','http','GET','/api/project/environments/{id}','按 ID 查询接口测试环境详情（含聚合 JSONB）',
 '[{"key":"X-Active-Workspace","value":"{{workspaceId}}","enabled":true}]','',
 NULL,
 '[]',
 '[{"key":"id","value":"7e570005-0000-4000-8000-000000000001","enabled":true}]',NULL,'enabled',
 '7e57001f-0000-4000-8000-000000000001',1,
 '{"status":200,"headers":null,"body":{"code":0,"data":{"id":"...","name":"本地开发环境"}}}',0,
 '[]','[]',
 false, now() - interval '18 days', now()),

-- 8 调试执行
('7e570006-0000-4000-8000-000000000008','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-00000000000a',
 '调试执行','http','POST','/api/project/debug/execute','快速调试入口，携带请求配置直接发起一次调用',
 '[{"key":"Content-Type","value":"application/json","enabled":true}]','json',
 '{"type":"json","content":{"method":"GET","url":"/api/health","headers":[],"params":[],"body":{"type":"none","content":null}}}','[]',
 '[]',NULL,'enabled',
 '7e57001f-0000-4000-8000-000000000001',1,
 '{"status":200,"headers":{"Content-Type":"application/json"},"body":{"status":"UP"}}',0,
 '[{"id":"7e570006-0000-4000-8000-000000000a07","name":"状态码为200","enabled":true,"target":"status_code","condition":"equals","expression":"","expected":"200"}]',
 '[]',
 false, now() - interval '18 days', now());

-- ============================================================
-- 7. 接口测试 — 公共组件 / 自定义函数
--    type 取值（web/src/types/project/api-testing/apitest.ts:574）
--    config 为 JSONB：服务端写入 JsonUtils.toJsonString(map)，故存裸 JSON 对象字面量
-- ============================================================
INSERT INTO api_component (id, scope, workspace_id, project_id, type, name, description,
                           sort_order, config, enabled, updated_by, is_deleted, created_at, updated_at)
VALUES
('7e570008-0000-4000-8000-000000000001','project',NULL,'7e570001-0000-4000-8000-000000000001',
 'preprocessor','注入 Trace 头','给所有请求统一追加 X-Trace-Id，便于链路排查',
 0,'{"headers":{"X-Trace-Id":"robotest-self-test"}}',true,
 '7e57001f-0000-4000-8000-000000000001',false, now() - interval '17 days', now()),

('7e570008-0000-4000-8000-000000000002','project',NULL,'7e570001-0000-4000-8000-000000000001',
 'postprocessor','解析响应耗时','从响应头读取 server-timing 并写入变量',
 1,'{"headers":["server-timing"],"variableName":"serverTiming"}',true,
 '7e57001f-0000-4000-8000-000000000001',false, now() - interval '17 days', now()),

('7e570008-0000-4000-8000-000000000003','project',NULL,'7e570001-0000-4000-8000-000000000001',
 'validator','业务码非空断言','统一断言响应体中 $.code 字段存在且可解析',
 0,'{"target":"json_field","condition":"not_empty","expression":"$.code","expected":""}',true,
 '7e57001f-0000-4000-8000-000000000001',false, now() - interval '17 days', now()),

('7e570008-0000-4000-8000-000000000004','project',NULL,'7e570001-0000-4000-8000-000000000001',
 'extractor','提取分页 total','从分页响应里抽出 total 写入变量，供后续步骤断言',
 0,'{"source":"json_field","expression":"$.data.total","variableName":"total","description":"分页总数"}',true,
 '7e57001f-0000-4000-8000-000000000001',false, now() - interval '17 days', now());

INSERT INTO api_function (id, scope, workspace_id, project_id, name, description, params_desc,
                          script, type, enabled, updated_by, is_deleted, created_at, updated_at)
VALUES
('7e570009-0000-4000-8000-000000000001','project',NULL,'7e570001-0000-4000-8000-000000000001',
 'md5Hex','计算字符串的 MD5 十六进制摘要','value: 待摘要的原始字符串',
 'def md5Hex(String value) {\n    if (value == null) return null\n    def digest = java.security.MessageDigest.getInstance("MD5")\n    digest.update(value.getBytes("UTF-8"))\n    return digest.digest().collect { String.format("%02x", it) }.join("")\n}',
 'custom', true, '7e57001f-0000-4000-8000-000000000001', false, now() - interval '16 days', now()),

('7e570009-0000-4000-8000-000000000002','project',NULL,'7e570001-0000-4000-8000-000000000001',
 'isoNow','返回当前 UTC ISO-8601 时间串','offsetSeconds: 相对当前时刻的秒偏移',
 'def isoNow(Integer offsetSeconds = 0) {\n    def instant = java.time.Instant.now().plusSeconds(offsetSeconds == null ? 0 : offsetSeconds)\n    return java.time.format.DateTimeFormatter.ISO_INSTANT.format(instant)\n}',
 'custom', true, '7e57001f-0000-4000-8000-000000000001', false, now() - interval '16 days', now());

-- ============================================================
-- 8. 接口测试 — 场景（steps 为 JSONB 数组，字段口径见 SceneDetailAssembler.toStepDetail）
-- ============================================================
INSERT INTO api_scene
(id, project_id, module_id, name, description, environment_id, priority, status,
 variables, processors, steps, change_version, is_deleted, created_at, updated_at)
VALUES
-- 场景1：登录 → 拉取用户 → 断言（3 步）
('7e570007-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-00000000000a',
 '登录后拉取用户列表','端到端冒烟：登录换令牌 → 带令牌分页查用户 → 校验返回结构',
 '7e570005-0000-4000-8000-000000000001','P0','published',
 '[{"id":"7e570007-0000-4000-8000-000000000101","name":"tenant","value":"selftest","source":"constant","interfaceVariableId":null,"description":"租户标识","sortOrder":0}]',
 '[]',
 '[
  {"id":"7e570007-0000-4000-8000-000000001001","name":"登录获取令牌","stepType":"http","sortOrder":0,
   "enabled":true,"sourceType":"custom","sourceId":null,"sourceInterfaceId":null,"sourceInterfaceName":null,
   "requestConfig":{"method":"POST","url":"/api/auth/login","timeout":30000,
     "headers":[{"key":"Content-Type","value":"application/json","enabled":true}],
     "params":[],
     "body":{"type":"json","content":{"username":"demo","password":"Passw0rd!"}}},
   "variables":[],"processors":[],
   "validators":[{"id":"7e570007-0000-4000-8000-000000002001","name":"状态码为200","enabled":true,"target":"status_code","condition":"equals","expression":"","expected":"200"},
                 {"id":"7e570007-0000-4000-8000-000000002002","name":"业务码为0","enabled":true,"target":"json_field","condition":"equals","expression":"$.code","expected":"0"}],
   "extractors":[{"id":"7e570007-0000-4000-8000-000000003001","name":"提取访问令牌","enabled":true,"source":"json_field","expression":"$.data.accessToken","variableName":"accessToken","description":""}]},

  {"id":"7e570007-0000-4000-8000-000000001002","name":"分页查询用户","stepType":"http","sortOrder":1,
   "enabled":true,"sourceType":"link","sourceId":"7e570006-0000-4000-8000-000000000003",
   "sourceInterfaceId":"7e570006-0000-4000-8000-000000000003","sourceInterfaceName":"分页查询用户",
   "requestConfig":{"method":"GET","url":"/api/admin/users","timeout":30000,
     "headers":[{"key":"X-Active-Workspace","value":"7e57001e-0000-4000-8000-000000000001","enabled":true}],
     "params":[{"key":"pageNo","value":"1","enabled":true},{"key":"pageSize","value":"20","enabled":true}],
     "body":{"type":"none","content":null}},
   "variables":[],"processors":[],
   "validators":[{"id":"7e570007-0000-4000-8000-000000002003","name":"业务码为0","enabled":true,"target":"json_field","condition":"equals","expression":"$.code","expected":"0"}],
   "extractors":[{"id":"7e570007-0000-4000-8000-000000003002","name":"提取用户总数","enabled":true,"source":"json_field","expression":"$.data.total","variableName":"userTotal","description":""}]},

  {"id":"7e570007-0000-4000-8000-000000001003","name":"断言用户总数大于 0","stepType":"http","sortOrder":2,
   "enabled":true,"sourceType":"custom","sourceId":null,"sourceInterfaceId":null,"sourceInterfaceName":null,
   "requestConfig":{"method":"GET","url":"/api/health","timeout":10000,
     "headers":[],"params":[],"body":{"type":"none","content":null}},
   "variables":[],"processors":[],
   "validators":[{"id":"7e570007-0000-4000-8000-000000002004","name":"服务健康","enabled":true,"target":"status_code","condition":"equals","expression":"","expected":"200"}],
   "extractors":[]}
 ]',
 3, false, now() - interval '15 days', now()),

-- 场景2：接口冒烟（3 步）
('7e570007-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-00000000000a',
 '平台接口冒烟','覆盖认证、项目、接口管理三个入口的连通性检查',
 '7e570005-0000-4000-8000-000000000001','P1','published',
 '[]','[]',
 '[
  {"id":"7e570007-0000-4000-8000-000000001004","name":"健康检查","stepType":"http","sortOrder":0,
   "enabled":true,"sourceType":"custom","sourceId":null,"sourceInterfaceId":null,"sourceInterfaceName":null,
   "requestConfig":{"method":"GET","url":"/api/health","timeout":5000,"headers":[],"params":[],"body":{"type":"none","content":null}},
   "variables":[],"processors":[],
   "validators":[{"id":"7e570007-0000-4000-8000-000000002005","name":"状态码为200","enabled":true,"target":"status_code","condition":"equals","expression":"","expected":"200"}],
   "extractors":[]},

  {"id":"7e570007-0000-4000-8000-000000001005","name":"项目列表","stepType":"http","sortOrder":1,
   "enabled":true,"sourceType":"link","sourceId":"7e570006-0000-4000-8000-000000000004",
   "sourceInterfaceId":"7e570006-0000-4000-8000-000000000004","sourceInterfaceName":"项目列表查询",
   "requestConfig":{"method":"GET","url":"/api/workspace/projects","timeout":10000,
     "headers":[{"key":"X-Active-Workspace","value":"7e57001e-0000-4000-8000-000000000001","enabled":true}],
     "params":[{"key":"status","value":"active","enabled":true}],"body":{"type":"none","content":null}},
   "variables":[],"processors":[],
   "validators":[{"id":"7e570007-0000-4000-8000-000000002006","name":"业务码为0","enabled":true,"target":"json_field","condition":"equals","expression":"$.code","expected":"0"}],
   "extractors":[]},

  {"id":"7e570007-0000-4000-8000-000000001006","name":"接口列表（禁用步骤示例）","stepType":"http","sortOrder":2,
   "enabled":false,"sourceType":"link","sourceId":"7e570006-0000-4000-8000-000000000005",
   "sourceInterfaceId":"7e570006-0000-4000-8000-000000000005","sourceInterfaceName":"接口分页列表",
   "requestConfig":{"method":"GET","url":"/api/project/interfaces","timeout":10000,
     "headers":[{"key":"X-Active-Project","value":"7e570001-0000-4000-8000-000000000001","enabled":true}],
     "params":[{"key":"pageNo","value":"1","enabled":true}],"body":{"type":"none","content":null}},
   "variables":[],"processors":[],"validators":[],"extractors":[]}
 ]',
 2, false, now() - interval '14 days', now()),

-- 场景3：Mock 校验（2 步，草稿态）
('7e570007-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001','7e570002-0000-4000-8000-00000000000a',
 'Mock 响应校验','验证 Mock 命中规则与延迟配置生效',
 '7e570005-0000-4000-8000-000000000002','P2','draft',
 '[]',
 '[{"processorType":"preprocessor","name":"强制走 Mock","config":{"headers":{"X-Mock-Enabled":"true"}},"sortOrder":0,"enabled":true}]',
 '[
  {"id":"7e570007-0000-4000-8000-000000001007","name":"调用 Mock 接口","stepType":"http","sortOrder":0,
   "enabled":true,"sourceType":"custom","sourceId":null,"sourceInterfaceId":null,"sourceInterfaceName":null,
   "requestConfig":{"method":"GET","url":"http://localhost:58080/mock/robotest/health","timeout":5000,
     "headers":[{"key":"X-Mock-Enabled","value":"true","enabled":true}],"params":[],"body":{"type":"none","content":null}},
   "variables":[],"processors":[],
   "validators":[{"id":"7e570007-0000-4000-8000-000000002007","name":"命中 Mock","enabled":true,"target":"json_field","condition":"equals","expression":"$.mocked","expected":"true"}],
   "extractors":[]},

  {"id":"7e570007-0000-4000-8000-000000001008","name":"校验延迟生效","stepType":"http","sortOrder":1,
   "enabled":true,"sourceType":"custom","sourceId":null,"sourceInterfaceId":null,"sourceInterfaceName":null,
   "requestConfig":{"method":"GET","url":"http://localhost:58080/mock/robotest/slow","timeout":8000,
     "headers":[],"params":[],"body":{"type":"none","content":null}},
   "variables":[],"processors":[],
   "validators":[{"id":"7e570007-0000-4000-8000-000000002008","name":"延迟不低于 200ms","enabled":true,"target":"response_time","condition":"gte","expression":"","expected":"200"}],
   "extractors":[]}
 ]',
 1, false, now() - interval '14 days', now());

-- ============================================================
-- 9. 接口测试 — Mock / Swagger / 调试记录 / 导入记录
-- ============================================================
INSERT INTO api_mock_definition
(id, project_id, interface_id, name, description, method, path, priority, match_rules,
 enabled, follow_api, response_status, response_headers, response_body_type, response_body,
 delay_ms, hit_count, last_hit_at, is_deleted, created_at, updated_at)
VALUES
('7e57000a-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001','7e570006-0000-4000-8000-000000000001',
 '健康检查 Mock','前端联调时兜底返回，不依赖后端起服',
 'GET','/mock/robotest/health',10,'[{"type":"exact","key":"path","value":"/mock/robotest/health"}]',
 true,false,200,'{"Content-Type":"application/json","X-Mock":"true"}','json',
 '{"status":"UP","mocked":true,"build":"seed"}',
 0,42, now() - interval '2 days', false, now() - interval '13 days', now()),

('7e57000a-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',NULL,
 '慢响应 Mock','用于验证超时与延迟配置',
 'GET','/mock/robotest/slow',5,'[{"type":"prefix","key":"path","value":"/mock/robotest/slow"}]',
 true,false,200,'{"Content-Type":"application/json"}','json',
 '{"mocked":true,"note":"delayed"}',
 250,7, now() - interval '5 days', false, now() - interval '13 days', now());

INSERT INTO api_swagger_url (id, project_id, name, url, format, last_import_status, last_import_at,
                             is_deleted, created_at, updated_at)
VALUES
('7e57001c-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '平台自身 OpenAPI','http://localhost:58080/v3/api-docs','openapi',
 'success', now() - interval '3 days', false, now() - interval '12 days', now());

INSERT INTO api_debug_record
(id, project_id, user_id, name, protocol, method, url, headers, body_type, body, query_params,
 jdbc_config, processors, environment_id, timeout_ms, executed_at, duration_ms, status,
 response_status, response_headers, response_body, response_size, error_message,
 is_deleted, created_at, updated_at)
VALUES
('7e570019-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','调试健康检查','http','GET','/api/health',
 '[]','',NULL,'[]',
 NULL,'[]','7e570005-0000-4000-8000-000000000001',5000,
 now() - interval '4 days',18,'success',
 200,'{"Content-Type":"application/json"}','{"status":"UP"}',16,NULL,
 false, now() - interval '4 days', now()),

('7e570019-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','调试登录接口','http','POST','/api/auth/login',
 '[{"key":"Content-Type","value":"application/json","enabled":true}]','json',
 '{"type":"json","content":{"username":"demo","password":"Passw0rd!"}}','[]',
 NULL,'[]','7e570005-0000-4000-8000-000000000001',30000,
 now() - interval '3 days',96,'success',
 200,'{"Content-Type":"application/json"}','{"code":0,"data":{"accessToken":"***"}}',86,NULL,
 false, now() - interval '3 days', now()),

('7e570019-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','调试错误路径','http','GET','/api/not-exist',
 '[]','',NULL,'[]',
 NULL,'[]','7e570005-0000-4000-8000-000000000001',5000,
 now() - interval '3 days',12,'failed',
 404,'{"Content-Type":"application/json"}','{"code":404,"message":"Not Found"}',32,NULL,
 false, now() - interval '3 days', now());

INSERT INTO api_import_record (id, project_id, import_type, source_name, status, summary,
                               error_details, created_by, is_deleted, created_at, updated_at)
VALUES
('7e57001d-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 'openapi','平台自身 OpenAPI','success',
 '{"added":0,"updated":8,"skipped":0,"failed":0}',NULL,
 '7e57001f-0000-4000-8000-000000000001', false, now() - interval '3 days', now());

-- ============================================================
-- 10. 接口测试 — 定时任务 / 执行记录 / 报告
-- ============================================================
INSERT INTO api_scheduled_task
(id, project_id, task_type, name, description, bound_object_id, bound_object_name, execution_scope,
 module_ids, scene_ids, openapi_url, environment_id, cron_expression, enabled,
 last_execution_status, last_execution_at, created_by, is_deleted, created_at, updated_at)
VALUES
('7e57001a-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 'scene','冒烟场景每晚回归','每日凌晨 2 点跑一遍登录冒烟场景',
 '7e570007-0000-4000-8000-000000000001','登录后拉取用户列表','bound',
 NULL,'["7e570007-0000-4000-8000-000000000001"]',NULL,
 '7e570005-0000-4000-8000-000000000001','0 0 2 * * ?',true,
 'success', now() - interval '1 day',
 '7e57001f-0000-4000-8000-000000000001', false, now() - interval '11 days', now());

INSERT INTO api_scheduled_task_execution
(id, task_id, project_id, trigger_type, status, error_message, report_id, import_record_id,
 triggered_at, duration_ms, is_deleted, created_at, updated_at)
VALUES
('7e57001b-0000-4000-8000-000000000001','7e57001a-0000-4000-8000-000000000001',
 '7e570001-0000-4000-8000-000000000001','cron','success',NULL,
 '7e570017-0000-4000-8000-000000000002',NULL,
 now() - interval '1 day',1840,false, now() - interval '1 day', now());

INSERT INTO api_execution_record
(id, project_id, scene_id, environment_id, execution_mode, status, trigger_type, source,
 report_id, error_message, executed_at, duration_ms, is_deleted, created_at, updated_at)
VALUES
('7e570018-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '7e570007-0000-4000-8000-000000000001','7e570005-0000-4000-8000-000000000001',
 'platform','failed','manual','scene',
 '7e570017-0000-4000-8000-000000000001',NULL,
 now() - interval '6 days',2130,false, now() - interval '6 days', now()),

('7e570018-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',
 '7e570007-0000-4000-8000-000000000001','7e570005-0000-4000-8000-000000000001',
 'platform','success','manual','scene',
 '7e570017-0000-4000-8000-000000000002',NULL,
 now() - interval '1 day',1840,false, now() - interval '1 day', now()),

('7e570018-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001',
 '7e570007-0000-4000-8000-000000000002','7e570005-0000-4000-8000-000000000001',
 'platform','success','manual','scene',
 NULL,NULL,
 now() - interval '2 days',760,false, now() - interval '2 days', now());

INSERT INTO api_report
(id, project_id, execution_record_id, report_type, external_id, name, environment_name,
 execution_mode, source, status, summary, result, ryze_snapshot, share_token, share_expires_at,
 share_user_id, is_deleted, created_at, updated_at)
VALUES
('7e570017-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '7e570018-0000-4000-8000-000000000001','scene','7e570007-0000-4000-8000-000000000001',
 '登录后拉取用户列表 · 06-22 失败运行','本地开发环境',
 'platform','scene','failed',
 '{"totalSteps":3,"passed":2,"failed":1,"skipped":0,"durationMs":2130}',
 '{"steps":[{"index":1,"name":"登录获取令牌","pass":true,"durationMs":96},
            {"index":2,"name":"分页查询用户","pass":true,"durationMs":184},
            {"index":3,"name":"断言用户总数大于 0","pass":false,"durationMs":42,
             "assertions":[{"pass":false,"target":"status_code","expression":"","expected":"200","actual":"503"}]}]}',
 NULL,NULL,NULL,NULL,
 false, now() - interval '6 days', now()),

('7e570017-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',
 '7e570018-0000-4000-8000-000000000002','scene','7e570007-0000-4000-8000-000000000001',
 '登录后拉取用户列表 · 06-27 成功运行','本地开发环境',
 'platform','scene','success',
 '{"totalSteps":3,"passed":3,"failed":0,"skipped":0,"durationMs":1840}',
 '{"steps":[{"index":1,"name":"登录获取令牌","pass":true,"durationMs":92},
            {"index":2,"name":"分页查询用户","pass":true,"durationMs":171},
            {"index":3,"name":"断言用户总数大于 0","pass":true,"durationMs":36}]}',
 NULL,'share_7e57_0002', now() + interval '7 days',
 '7e57001f-0000-4000-8000-000000000001', false, now() - interval '1 day', now());

-- ============================================================
-- 11. 功能测试 — 测试计划
--     计划1 圈选「用户登录与鉴权」文档（19 节点），计划2 圈选「缺陷生命周期」（12 节点）
-- ============================================================
INSERT INTO test_plan
(id, project_id, name, description, status, executor_id, start_time, end_time,
 environment, snapshot_synced_at, is_deleted, created_at, updated_at)
VALUES
('7e57000b-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '用户登录回归计划','覆盖登录成功、密码错误、账号禁用三条主路径的回归执行',
 'in_progress','7e57001f-0000-4000-8000-000000000001',
 now() - interval '7 days', now() + interval '3 days',
 '本地开发环境', now() - interval '1 day', false, now() - interval '7 days', now()),

('7e57000b-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',
 '缺陷流程验证计划','验证缺陷状态机（active/resolved/closed/reopen）字段回写正确性',
 'in_progress','7e57001f-0000-4000-8000-000000000001',
 now() - interval '3 days', now() + interval '5 days',
 '本地开发环境', now() - interval '12 hours', false, now() - interval '3 days', now());

-- 计划模块快照（parent_id 指向同计划的快照行，不是原表）
INSERT INTO test_plan_module_snapshot
(id, plan_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES
('7e57000c-0000-4000-8000-000000000001','7e57000b-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000001',NULL,'系统管理','directory',0,false, now() - interval '7 days', now()),
('7e57000c-0000-4000-8000-000000000002','7e57000b-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000001','用户与权限','directory',0,false, now() - interval '7 days', now()),
('7e57000c-0000-4000-8000-000000000003','7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000002','用户登录与鉴权','document',0,false, now() - interval '7 days', now()),
('7e57000c-0000-4000-8000-000000000004','7e57000b-0000-4000-8000-000000000002','7e570002-0000-4000-8000-000000000002',NULL,'功能测试','directory',1,false, now() - interval '3 days', now()),
('7e57000c-0000-4000-8000-000000000005','7e57000b-0000-4000-8000-000000000002','7e570002-0000-4000-8000-000000000008','7e57000c-0000-4000-8000-000000000004','缺陷管理','directory',2,false, now() - interval '3 days', now()),
('7e57000c-0000-4000-8000-000000000006','7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000005','缺陷生命周期','document',0,false, now() - interval '3 days', now());

-- 计划节点快照：快照 id = '7e57000d-' + 原节点 id 的末 12 位；parent 指向同前缀快照行
INSERT INTO test_plan_node_snapshot
(id, plan_id, original_node_id, document_snapshot_id, parent_id, title, type, priority,
 is_associated, last_result, last_executor_id, last_executed_at, sort_order,
 ai_generated, is_deleted, created_at, updated_at)
SELECT ('7e57000d-0000-4000-8000-' || substr(v.orig_id, 25))::uuid,
       v.plan_id::uuid, v.orig_id::uuid, v.doc_snap::uuid,
       CASE WHEN v.parent_id = '' THEN NULL
            ELSE ('7e57000d-0000-4000-8000-' || substr(v.parent_id, 25))::uuid END,
       v.title, v.type, NULLIF(v.priority, ''), v.associated::bool, v.last_result,
       NULLIF(v.executor, '')::uuid, v.executed_at::timestamp, v.sort_order::int,
       false, false, now() - interval '7 days', now()
FROM (VALUES
  -- ---- 计划1 / 文档1 用户登录与鉴权（19 行） ----
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000100','','用户登录与鉴权','normal','','false','untested','',NULL,0),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000101','7e570004-0000-4000-8000-000000000100','使用正确账号密码登录成功','case','P0','true','pass','7e57001f-0000-4000-8000-000000000001', now() - interval '5 days',0),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000102','7e570004-0000-4000-8000-000000000101','已存在启用状态的测试账号 demo','precondition','','false','untested','',NULL,0),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000103','7e570004-0000-4000-8000-000000000101','在登录页输入用户名与密码','step','','false','untested','',NULL,1),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000104','7e570004-0000-4000-8000-000000000101','点击登录按钮提交','step','','false','untested','',NULL,2),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000105','7e570004-0000-4000-8000-000000000101','跳转至工作台，顶部显示当前用户名','expected','','false','untested','',NULL,3),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000106','7e570004-0000-4000-8000-000000000101','写入有效的访问与刷新令牌','expected','','false','untested','',NULL,4),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000107','7e570004-0000-4000-8000-000000000100','密码错误时给出明确提示且不跳转','case','P1','true','fail','7e57001f-0000-4000-8000-000000000001', now() - interval '4 days',1),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000108','7e570004-0000-4000-8000-000000000107','登录页已打开且本地无残留令牌','precondition','','false','untested','',NULL,0),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000109','7e570004-0000-4000-8000-000000000107','输入正确用户名与错误密码并提交','step','','false','untested','',NULL,1),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-00000000010a','7e570004-0000-4000-8000-000000000107','观察页面提示与路由变化','step','','false','untested','',NULL,2),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-00000000010b','7e570004-0000-4000-8000-000000000107','提示「用户名或密码错误」并停留在登录页','expected','','false','untested','',NULL,3),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-00000000010c','7e570004-0000-4000-8000-000000000107','本地未写入任何令牌','expected','','false','untested','',NULL,4),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-00000000010d','7e570004-0000-4000-8000-000000000100','被禁用账号登录被拦截','case','P1','true','block','7e57001f-0000-4000-8000-000000000001', now() - interval '3 days',2),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-00000000010e','7e570004-0000-4000-8000-00000000010d','存在状态为 disabled 的测试账号','precondition','','false','untested','',NULL,0),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-00000000010f','7e570004-0000-4000-8000-00000000010d','输入被禁用账号与正确密码提交','step','','false','untested','',NULL,1),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000110','7e570004-0000-4000-8000-00000000010d','观察响应提示与登录状态','step','','false','untested','',NULL,2),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000111','7e570004-0000-4000-8000-00000000010d','提示账号已被禁用','expected','','false','untested','',NULL,3),
  ('7e57000b-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000001','7e57000c-0000-4000-8000-000000000003',
   '7e570004-0000-4000-8000-000000000112','7e570004-0000-4000-8000-00000000010d','不建立会话，不写入令牌','expected','','false','untested','',NULL,4),

  -- ---- 计划2 / 文档4 缺陷生命周期（12 行） ----
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000400','','缺陷生命周期','normal','','false','untested','',NULL,0),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000401','7e570004-0000-4000-8000-000000000400','缺陷 active → resolved → closed 闭环','case','P0','true','pass','7e57001f-0000-4000-8000-000000000001', now() - interval '2 days',0),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000402','7e570004-0000-4000-8000-000000000401','项目中存在一条 active 状态的缺陷','precondition','','false','untested','',NULL,0),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000403','7e570004-0000-4000-8000-000000000401','选择解决方案 fixed 并填写说明提交','step','','false','untested','',NULL,1),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000404','7e570004-0000-4000-8000-000000000401','回归通过后执行关闭操作','step','','false','untested','',NULL,2),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000405','7e570004-0000-4000-8000-000000000401','状态依次变为 resolved 与 closed','expected','','false','untested','',NULL,3),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000406','7e570004-0000-4000-8000-000000000401','resolved_at、closed_by、closed_at 均被正确写入','expected','','false','untested','',NULL,4),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000407','7e570004-0000-4000-8000-000000000400','缺陷 reopen 后清空解决信息并累加重开次数','case','P1','true','fail','7e57001f-0000-4000-8000-000000000001', now() - interval '1 day',1),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000408','7e570004-0000-4000-8000-000000000407','存在一条 resolved 状态的缺陷','precondition','','false','untested','',NULL,0),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-000000000409','7e570004-0000-4000-8000-000000000407','填写重开说明并提交','step','','false','untested','',NULL,1),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-00000000040a','7e570004-0000-4000-8000-000000000407','状态回到 active，reopen_count 加 1','expected','','false','untested','',NULL,2),
  ('7e57000b-0000-4000-8000-000000000002','7e570003-0000-4000-8000-000000000004','7e57000c-0000-4000-8000-000000000006',
   '7e570004-0000-4000-8000-00000000040b','7e570004-0000-4000-8000-000000000407','resolution、resolved_by、resolved_at 全部清空','expected','','false','untested','',NULL,3)
) AS v(plan_id, doc_id, doc_snap, orig_id, parent_id, title, type, priority,
       associated, last_result, executor, executed_at, sort_order);

-- 计划执行记录（snapshot_node_id 指向快照行）
INSERT INTO test_plan_execution_record
(id, plan_id, snapshot_node_id, executor_id, result, note, executed_at, is_deleted, created_at, updated_at)
VALUES
('7e57000e-0000-4000-8000-000000000001','7e57000b-0000-4000-8000-000000000001',
 '7e57000d-0000-4000-8000-000000000101','7e57001f-0000-4000-8000-000000000001','fail',
 '登录后停留在登录页，疑似令牌写入失败', now() - interval '6 days', false, now() - interval '6 days', now()),
('7e57000e-0000-4000-8000-000000000002','7e57000b-0000-4000-8000-000000000001',
 '7e57000d-0000-4000-8000-000000000101','7e57001f-0000-4000-8000-000000000001','pass',
 '修复 token 写入后回归通过', now() - interval '5 days', false, now() - interval '5 days', now()),
('7e57000e-0000-4000-8000-000000000003','7e57000b-0000-4000-8000-000000000001',
 '7e57000d-0000-4000-8000-000000000107','7e57001f-0000-4000-8000-000000000001','fail',
 '错误提示文案与用例预期不一致', now() - interval '4 days', false, now() - interval '4 days', now()),
('7e57000e-0000-4000-8000-000000000004','7e57000b-0000-4000-8000-000000000001',
 '7e57000d-0000-4000-8000-00000000010d','7e57001f-0000-4000-8000-000000000001','block',
 '测试环境缺少 disabled 账号，待造数', now() - interval '3 days', false, now() - interval '3 days', now()),
('7e57000e-0000-4000-8000-000000000005','7e57000b-0000-4000-8000-000000000002',
 '7e57000d-0000-4000-8000-000000000401','7e57001f-0000-4000-8000-000000000001','pass',
 '状态机字段回写全部正确', now() - interval '2 days', false, now() - interval '2 days', now()),
('7e57000e-0000-4000-8000-000000000006','7e57000b-0000-4000-8000-000000000002',
 '7e57000d-0000-4000-8000-000000000407','7e57001f-0000-4000-8000-000000000001','fail',
 'reopen 后 resolution 未被清空', now() - interval '1 day', false, now() - interval '1 day', now());

-- ============================================================
-- 12. 功能测试 — 测试评审（圈选「用例管理核心流程」20 节点）
-- ============================================================
INSERT INTO test_review
(id, project_id, title, description, initiator_id, participant_ids, status, is_deleted, created_at, updated_at)
VALUES
('7e57000f-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '用例管理用例评审','评审文档「用例管理核心流程」的用例粒度、前置条件与断言完整性',
 '7e57001f-0000-4000-8000-000000000001',
 '["7e57001f-0000-4000-8000-000000000001","7e57001f-0000-4000-8000-000000000001"]',
 'in_progress', false, now() - interval '7 days', now());

INSERT INTO test_review_module_snapshot
(id, review_id, original_module_id, parent_id, name, type, sort_order, is_deleted, created_at, updated_at)
VALUES
('7e570010-0000-4000-8000-000000000001','7e57000f-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000002',NULL,'功能测试','directory',1,false, now() - interval '7 days', now()),
('7e570010-0000-4000-8000-000000000002','7e57000f-0000-4000-8000-000000000001','7e570002-0000-4000-8000-000000000006','7e570010-0000-4000-8000-000000000001','用例管理','directory',0,false, now() - interval '7 days', now()),
('7e570010-0000-4000-8000-000000000003','7e57000f-0000-4000-8000-000000000001','7e570003-0000-4000-8000-000000000002','7e570010-0000-4000-8000-000000000002','用例管理核心流程','document',0,false, now() - interval '7 days', now());

INSERT INTO test_review_node_snapshot
(id, review_id, original_node_id, document_snapshot_id, parent_id, title, type, priority,
 is_associated, last_mark, last_reviewer_id, last_reviewed_at, sort_order,
 ai_generated, is_deleted, created_at, updated_at)
SELECT ('7e570011-0000-4000-8000-' || substr(v.orig_id, 25))::uuid,
       v.review_id::uuid, v.orig_id::uuid, '7e570010-0000-4000-8000-000000000003',
       CASE WHEN v.parent_id = '' THEN NULL
            ELSE ('7e570011-0000-4000-8000-' || substr(v.parent_id, 25))::uuid END,
       v.title, v.type, NULLIF(v.priority, ''), v.associated::bool,
       NULLIF(v.last_mark, ''), NULLIF(v.reviewer, '')::uuid, v.reviewed_at::timestamp,
       v.sort_order::int, false, false, now() - interval '7 days', now()
FROM (VALUES
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000200','','用例管理核心流程','normal','','false','','',NULL,0),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000201','7e570004-0000-4000-8000-000000000200','文档与模块','normal','','false','','',NULL,0),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000202','7e570004-0000-4000-8000-000000000201','在模块下新建用例文档并自动生成根节点','case','P0','true','pass','7e57001f-0000-4000-8000-000000000001', now() - interval '2 days',0),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000203','7e570004-0000-4000-8000-000000000202','当前项目已有「用例管理」模块','precondition','','false','','',NULL,0),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000204','7e570004-0000-4000-8000-000000000202','在模块节点上选择新建文档','step','','false','','',NULL,1),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000205','7e570004-0000-4000-8000-000000000202','输入文档名并确认','step','','false','','',NULL,2),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000206','7e570004-0000-4000-8000-000000000202','文档创建成功，自动出现同名脑图根节点','expected','','false','','',NULL,3),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000207','7e570004-0000-4000-8000-000000000202','文档树刷新并高亮新建的文档','expected','','false','','',NULL,4),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000208','7e570004-0000-4000-8000-000000000201','重命名文档后根节点标题同步更新','case','P1','true','fail','7e57001f-0000-4000-8000-000000000001', now() - interval '3 days',1),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000209','7e570004-0000-4000-8000-000000000208','已存在文档及其同名根节点','precondition','','false','','',NULL,0),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000020a','7e570004-0000-4000-8000-000000000208','在左侧文档树上重命名该文档','step','','false','','',NULL,1),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000020b','7e570004-0000-4000-8000-000000000208','重新打开该文档查看脑图','step','','false','','',NULL,2),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000020c','7e570004-0000-4000-8000-000000000208','根节点标题与新文档名完全一致','expected','','false','','',NULL,3),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000020d','7e570004-0000-4000-8000-000000000208','刷新页面后标题仍保持一致','expected','','false','','',NULL,4),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000020e','7e570004-0000-4000-8000-000000000200','删除模块时其下文档一并逻辑删除','case','P2','true','','',NULL,1),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-00000000020f','7e570004-0000-4000-8000-00000000020e','目标模块下存在 2 份文档','precondition','','false','','',NULL,0),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000210','7e570004-0000-4000-8000-00000000020e','删除该模块并确认二次弹窗','step','','false','','',NULL,1),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000211','7e570004-0000-4000-8000-00000000020e','刷新用例管理页面','step','','false','','',NULL,2),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000212','7e570004-0000-4000-8000-00000000020e','模块与其下文档均不再显示','expected','','false','','',NULL,3),
  ('7e57000f-0000-4000-8000-000000000001','7e570004-0000-4000-8000-000000000213','7e570004-0000-4000-8000-00000000020e','数据库中对应行为 is_deleted = true','expected','','false','','',NULL,4)
) AS v(review_id, orig_id, parent_id, title, type, priority, associated, last_mark, reviewer, reviewed_at, sort_order);

-- 评审记录：mark 可为 pass / fail / pending，pending 落到快照时 last_mark 必须置 NULL
INSERT INTO test_review_record
(id, review_id, snapshot_node_id, reviewer_id, operation_type, mark, comment, is_deleted, created_at, updated_at)
VALUES
('7e570012-0000-4000-8000-000000000001','7e57000f-0000-4000-8000-000000000001',
 '7e570011-0000-4000-8000-000000000202','7e57001f-0000-4000-8000-000000000001','mark','pass',NULL,
 false, now() - interval '6 days', now()),
('7e570012-0000-4000-8000-000000000002','7e57000f-0000-4000-8000-000000000001',
 '7e570011-0000-4000-8000-000000000202','7e57001f-0000-4000-8000-000000000001','comment',NULL,
 '前置条件覆盖完整，建议补充「无权限用户」分支',
 false, now() - interval '6 days', now()),
('7e570012-0000-4000-8000-000000000003','7e57000f-0000-4000-8000-000000000001',
 '7e570011-0000-4000-8000-000000000202','7e57001f-0000-4000-8000-000000000001','mark','pending',NULL,
 false, now() - interval '4 days', now()),
('7e570012-0000-4000-8000-000000000004','7e57000f-0000-4000-8000-000000000001',
 '7e570011-0000-4000-8000-000000000208','7e57001f-0000-4000-8000-000000000001','mark','fail',NULL,
 false, now() - interval '3 days', now()),
('7e570012-0000-4000-8000-000000000005','7e57000f-0000-4000-8000-000000000001',
 '7e570011-0000-4000-8000-000000000208','7e57001f-0000-4000-8000-000000000001','comment',NULL,
 '缺少「刷新页面后标题仍一致」的交叉验证步骤',
 false, now() - interval '3 days', now()),
('7e570012-0000-4000-8000-000000000006','7e57000f-0000-4000-8000-000000000001',
 '7e570011-0000-4000-8000-000000000202','7e57001f-0000-4000-8000-000000000001','mark','pass',NULL,
 false, now() - interval '2 days', now()),
('7e570012-0000-4000-8000-000000000007','7e57000f-0000-4000-8000-000000000001',
 '7e570011-0000-4000-8000-000000000201','7e57001f-0000-4000-8000-000000000001','comment',NULL,
 '分组结构合理，建议保留',
 false, now() - interval '1 day', now());

-- ============================================================
-- 13. 缺陷管理（6 条缺陷，覆盖 active / resolved / rejected / closed 四态 + reopen 链路）
-- ============================================================
INSERT INTO bug
(id, project_id, title, severity, priority, status, repro_steps, reporter_id, assignee_id,
 related_case_id, related_plan_id, bug_type, module_id, keywords, due_date, confirmed,
 reopen_count, last_reopened_at, resolution, duplicate_of_bug_id, resolved_by, resolved_at,
 rejected_by, closed_by, closed_at, is_deleted, created_at, updated_at)
VALUES
('7e570013-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '用例文档重命名后脑图根节点标题未同步','serious','high','active',
 '1. 新建用例文档并进入脑图\n2. 在左侧文档树重命名该文档\n3. 刷新页面查看根节点标题',
 '7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 '7e570004-0000-4000-8000-000000000208','7e57000b-0000-4000-8000-000000000001',
 'code_error','7e570002-0000-4000-8000-000000000006','用例,重命名,脑图',
 current_date + 7, true, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 false, now() - interval '24 days', now()),

('7e570013-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',
 '登录页错误提示文案与设计稿不一致','general','medium','closed',
 '1. 打开登录页\n2. 输入错误密码提交\n3. 对比设计稿中的提示文案',
 '7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 '7e570004-0000-4000-8000-000000000107','7e57000b-0000-4000-8000-000000000001',
 'ui_improvement','7e570002-0000-4000-8000-000000000004','登录,文案',
 current_date - 10, true, 0, NULL, 'fixed', NULL,
 '7e57001f-0000-4000-8000-000000000001', now() - interval '9 days',
 NULL, '7e57001f-0000-4000-8000-000000000001', now() - interval '8 days',
 false, now() - interval '20 days', now()),

('7e570013-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001',
 '模块删除后仍可在计划中被选到','minor','low','active',
 '1. 删除某模块\n2. 打开计划的用例圈选弹窗\n3. 观察模块列表',
 '7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 NULL, '7e57000b-0000-4000-8000-000000000002',
 'other','7e570002-0000-4000-8000-000000000006','模块,计划,缓存',
 NULL, true, 1, now() - interval '5 days', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 false, now() - interval '15 days', now()),

('7e570013-0000-4000-8000-000000000004','7e570001-0000-4000-8000-000000000001',
 '刷新令牌过期后未回登录页且无任何提示','fatal','high','resolved',
 '1. 登录后把系统时间拨快 2 小时\n2. 点击任意页面\n3. 观察是否跳转与是否有提示',
 '7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 '7e570004-0000-4000-8000-00000000010d', NULL,
 'code_error','7e570002-0000-4000-8000-000000000004','令牌,刷新,鉴权',
 current_date + 3, true, 0, NULL, 'fixed', NULL,
 '7e57001f-0000-4000-8000-000000000001', now() - interval '12 hours',
 NULL, NULL, NULL,
 false, now() - interval '10 days', now()),

('7e570013-0000-4000-8000-000000000005','7e570001-0000-4000-8000-000000000001',
 '建议模块树支持批量拖拽排序','general','low','rejected',
 '1. 在用例管理模块树上尝试多选\n2. 拖拽调整顺序',
 '7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 NULL, NULL,
 'design_defect','7e570002-0000-4000-8000-000000000006','模块,交互',
 NULL, false, 0, NULL, NULL, NULL, NULL, NULL,
 '7e57001f-0000-4000-8000-000000000001', NULL, NULL,
 false, now() - interval '12 days', now()),

('7e570013-0000-4000-8000-000000000006','7e570001-0000-4000-8000-000000000001',
 '接口列表页超过 200 条时滚动明显卡顿','serious','medium','active',
 '1. 造 200+ 条接口数据\n2. 打开接口管理列表页快速滚动',
 '7e57001f-0000-4000-8000-000000000001', NULL,
 NULL, NULL,
 'performance','7e570002-0000-4000-8000-000000000009','接口列表,性能',
 current_date + 14, false, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 false, now() - interval '2 days', now());

INSERT INTO bug_log (id, bug_id, operator_id, operation_type, content, is_deleted, created_at, updated_at)
VALUES
('7e570014-0000-4000-8000-000000000001','7e570013-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001','create','创建缺陷',false, now() - interval '24 days', now()),
('7e570014-0000-4000-8000-000000000002','7e570013-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001','assign','指派处理人：示例用户',false, now() - interval '23 days', now()),
('7e570014-0000-4000-8000-000000000003','7e570013-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001','update','补充关键词：用例,重命名,脑图',false, now() - interval '22 days', now()),

('7e570014-0000-4000-8000-000000000004','7e570013-0000-4000-8000-000000000002','7e57001f-0000-4000-8000-000000000001','create','创建缺陷',false, now() - interval '20 days', now()),
('7e570014-0000-4000-8000-000000000005','7e570013-0000-4000-8000-000000000002','7e57001f-0000-4000-8000-000000000001','update','优先级调整为 medium',false, now() - interval '18 days', now()),
('7e570014-0000-4000-8000-000000000006','7e570013-0000-4000-8000-000000000002','7e57001f-0000-4000-8000-000000000001','resolve','解决，说明：已按设计稿统一文案',false, now() - interval '9 days', now()),
('7e570014-0000-4000-8000-000000000007','7e570013-0000-4000-8000-000000000002','7e57001f-0000-4000-8000-000000000001','close','关闭缺陷，说明：回归通过',false, now() - interval '8 days', now()),

('7e570014-0000-4000-8000-000000000008','7e570013-0000-4000-8000-000000000003','7e57001f-0000-4000-8000-000000000001','create','创建缺陷',false, now() - interval '15 days', now()),
('7e570014-0000-4000-8000-000000000009','7e570013-0000-4000-8000-000000000003','7e57001f-0000-4000-8000-000000000001','resolve','解决，说明：模块树查询已加 is_deleted 过滤',false, now() - interval '6 days', now()),
('7e570014-0000-4000-8000-00000000000a','7e570013-0000-4000-8000-000000000003','7e57001f-0000-4000-8000-000000000001','reopen','重开缺陷，说明：计划圈选弹窗仍能看到已删模块',false, now() - interval '5 days', now()),

('7e570014-0000-4000-8000-00000000000b','7e570013-0000-4000-8000-000000000004','7e57001f-0000-4000-8000-000000000001','create','创建缺陷',false, now() - interval '10 days', now()),
('7e570014-0000-4000-8000-00000000000c','7e570013-0000-4000-8000-000000000004','7e57001f-0000-4000-8000-000000000001','confirm','确认缺陷',false, now() - interval '10 days', now()),
('7e570014-0000-4000-8000-00000000000d','7e570013-0000-4000-8000-000000000004','7e57001f-0000-4000-8000-000000000001','assign','指派处理人：示例用户',false, now() - interval '9 days', now()),
('7e570014-0000-4000-8000-00000000000e','7e570013-0000-4000-8000-000000000004','7e57001f-0000-4000-8000-000000000001','resolve','解决，说明：401 时统一走刷新失败分支并跳登录页',false, now() - interval '12 hours', now()),

('7e570014-0000-4000-8000-00000000000f','7e570013-0000-4000-8000-000000000005','7e57001f-0000-4000-8000-000000000001','create','创建缺陷',false, now() - interval '12 days', now()),
('7e570014-0000-4000-8000-000000000010','7e570013-0000-4000-8000-000000000005','7e57001f-0000-4000-8000-000000000001','reject','拒绝缺陷，说明：与模块树单条排序的设计冲突',false, now() - interval '11 days', now()),

('7e570014-0000-4000-8000-000000000011','7e570013-0000-4000-8000-000000000006','7e57001f-0000-4000-8000-000000000001','create','创建缺陷',false, now() - interval '2 days', now());

-- ============================================================
-- 14. 需求池（active / archived 两态，见 docs/01-requirements/06-ai/03-srs-intelligent-case.md）
-- ============================================================
INSERT INTO requirement_pool_item
(id, project_id, title, content, source_url, status, ai_generated, created_by, updated_by,
 is_deleted, created_at, updated_at)
VALUES
('7e570015-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '用例脑图支持按优先级批量筛选',
 '希望在脑图视图中可以只看 P0 用例，方便回归前快速圈定高优范围。',
 NULL,'active',false,'7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 false, now() - interval '9 days', now()),

('7e570015-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',
 '缺陷列表按模块聚合统计',
 '缺陷页希望能按模块做一次聚合，直观看到哪个模块缺陷最集中。',
 NULL,'active',true,'7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 false, now() - interval '8 days', now()),

('7e570015-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001',
 '场景步骤支持条件分支',
 '复杂场景需要 if/else 分支，例如登录失败时走恢复流程。',
 'https://example.com/issues/scene-branch','active',false,
 '7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 false, now() - interval '6 days', now()),

('7e570015-0000-4000-8000-000000000004','7e570001-0000-4000-8000-000000000001',
 '报告支持导出 PDF',
 '交付场景需要把接口报告导成 PDF 归档。',
 NULL,'archived',false,
 '7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 false, now() - interval '20 days', now()),

('7e570015-0000-4000-8000-000000000005','7e570001-0000-4000-8000-000000000001',
 'Mock 支持按请求体内容路由',
 '同一个 path 希望能按 body 里的 type 字段返回不同 Mock 响应。',
 NULL,'active',true,'7e57001f-0000-4000-8000-000000000001','7e57001f-0000-4000-8000-000000000001',
 false, now() - interval '4 days', now());

-- ============================================================
-- 15. 项目动态（工作台「项目动态」数据源；resource_type / action 取值与
--     ProjectActivityServiceImpl 的 record() 调用方一致）
-- ============================================================
INSERT INTO ws_project_activity
(id, project_id, actor_id, actor_name, resource_type, resource_id, resource_name,
 action, summary, occurred_at, is_deleted, created_at, updated_at)
VALUES
('7e570016-0000-4000-8000-000000000001','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','PROJECT','7e570001-0000-4000-8000-000000000001',
 'Robotest 平台自测','PROJECT_CREATED','创建项目「Robotest 平台自测」',
 now() - interval '30 days', false, now() - interval '30 days', now()),
('7e570016-0000-4000-8000-000000000002','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_CASE_DOCUMENT','7e570003-0000-4000-8000-000000000001',
 '用户登录与鉴权','CASE_CREATED','创建用例「用户登录与鉴权」',
 now() - interval '28 days', false, now() - interval '28 days', now()),
('7e570016-0000-4000-8000-000000000003','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_CASE_DOCUMENT','7e570003-0000-4000-8000-000000000002',
 '用例管理核心流程','CASE_CREATED','创建用例「用例管理核心流程」',
 now() - interval '28 days', false, now() - interval '28 days', now()),
('7e570016-0000-4000-8000-000000000004','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','BUG','7e570013-0000-4000-8000-000000000001',
 '用例文档重命名后脑图根节点标题未同步','BUG_CREATED','提交缺陷「用例文档重命名后脑图根节点标题未同步」',
 now() - interval '24 days', false, now() - interval '24 days', now()),
('7e570016-0000-4000-8000-000000000005','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_REVIEW','7e57000f-0000-4000-8000-000000000001',
 '用例管理用例评审','REVIEW_CREATED','创建评审「用例管理用例评审」',
 now() - interval '7 days', false, now() - interval '7 days', now()),
('7e570016-0000-4000-8000-000000000006','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_PLAN','7e57000b-0000-4000-8000-000000000001',
 '用户登录回归计划','PLAN_CREATED','创建测试计划「用户登录回归计划」',
 now() - interval '7 days', false, now() - interval '7 days', now()),
('7e570016-0000-4000-8000-000000000007','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_PLAN','7e57000b-0000-4000-8000-000000000001',
 '用户登录回归计划','PLAN_STARTED','开始执行测试计划「用户登录回归计划」',
 now() - interval '7 days', false, now() - interval '7 days', now()),
('7e570016-0000-4000-8000-000000000008','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_PLAN','7e57000b-0000-4000-8000-000000000001',
 '用户登录回归计划','PLAN_CASES_UPDATED','调整测试计划「用户登录回归计划」的用例',
 now() - interval '6 days', false, now() - interval '6 days', now()),
('7e570016-0000-4000-8000-000000000009','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','BUG','7e570013-0000-4000-8000-000000000001',
 '用例文档重命名后脑图根节点标题未同步','BUG_ASSIGNED','指派缺陷「用例文档重命名后脑图根节点标题未同步」处理人',
 now() - interval '23 days', false, now() - interval '23 days', now()),
('7e570016-0000-4000-8000-00000000000a','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_REVIEW','7e57000f-0000-4000-8000-000000000001',
 '用例管理用例评审','REVIEW_STATUS_CHANGED','评审「用例管理用例评审」状态更新',
 now() - interval '3 days', false, now() - interval '3 days', now()),
('7e570016-0000-4000-8000-00000000000b','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','TEST_PLAN','7e57000b-0000-4000-8000-000000000002',
 '缺陷流程验证计划','PLAN_STARTED','开始执行测试计划「缺陷流程验证计划」',
 now() - interval '3 days', false, now() - interval '3 days', now()),
('7e570016-0000-4000-8000-00000000000c','7e570001-0000-4000-8000-000000000001',
 '7e57001f-0000-4000-8000-000000000001','示例用户','BUG','7e570013-0000-4000-8000-000000000003',
 '模块删除后仍可在计划中被选到','BUG_STATUS_CHANGED','更新缺陷「模块删除后仍可在计划中被选到」状态',
 now() - interval '5 days', false, now() - interval '5 days', now());

COMMIT;
