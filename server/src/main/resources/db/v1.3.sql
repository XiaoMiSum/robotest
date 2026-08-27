-- Robotest V1.3 数据库增量 DDL
-- PostgreSQL
-- V1.3 接口测试权限码注册（GitLab 集成、测试场景等模块权限码在 sys_permission 中缺失）
-- 依据：GitLabRepoController / ApiSceneController 等控制器 @PreAuthorize 引用的权限码
-- 首次建库按 v1.sql → v1.1.sql → v1.2.sql → v1.3.sql 顺序执行

-- ============================================================
-- 1. 接口测试权限点（workspace 作用域，接需求池 c…0036 之后）
-- ============================================================

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted) VALUES
-- GitLab 仓库配置
('c0000000-0000-0000-0000-000000000037', 'api-gitlab',         'GitLab 仓库',    NULL,            '接口测试·GitLab',   'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000038', 'api-gitlab:view',    '查看 GitLab 仓库', 'api-gitlab',    '接口测试·GitLab',   'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000039', 'api-gitlab:edit',    '编辑 GitLab 仓库', 'api-gitlab',    '接口测试·GitLab',   'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 测试场景
('c0000000-0000-0000-0000-000000000040', 'api-scene',          '测试场景',        NULL,            '接口测试·测试场景',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000041', 'api-scene:view',     '查看场景',        'api-scene',     '接口测试·测试场景',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000042', 'api-scene:edit',     '编辑场景',        'api-scene',     '接口测试·测试场景',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000043', 'api-scene:import',   '导入场景',        'api-scene',     '接口测试·测试场景',  'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000044', 'api-scene:pipeline', '流水线执行',       'api-scene',     '接口测试·测试场景',  'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000045', 'api-scene:execute',  '执行场景',        'api-scene',     '接口测试·测试场景',  'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 接口管理
('c0000000-0000-0000-0000-000000000046', 'api-interface',         '接口管理',    NULL,            '接口测试·接口管理',  'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000047', 'api-interface:view',    '查看接口',    'api-interface',  '接口测试·接口管理',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000048', 'api-interface:edit',    '编辑接口',    'api-interface',  '接口测试·接口管理',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000049', 'api-interface:delete', '删除接口',    'api-interface',  '接口测试·接口管理',  'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 公共组件
('c0000000-0000-0000-0000-000000000050', 'api-component',         '公共组件',    NULL,            '接口测试·公共组件',  'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000051', 'api-component:view',    '查看组件',    'api-component',  '接口测试·公共组件',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000052', 'api-component:edit',    '编辑组件',    'api-component',  '接口测试·公共组件',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000070', 'api-component:edit-space', '编辑空间级组件', 'api-component', '接口测试·公共组件', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000071', 'api-component:edit-global', '编辑全局组件', 'api-component', '接口测试·公共组件', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 快速调试
('c0000000-0000-0000-0000-000000000053', 'api-debug',         '快速调试',    NULL,            '接口测试·快速调试',  'workspace', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000054', 'api-debug:view',    '查看调试记录', 'api-debug',     '接口测试·快速调试',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 定时任务
('c0000000-0000-0000-0000-000000000055', 'api-timer',         '定时任务',    NULL,            '接口测试·定时任务',  'workspace', 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000056', 'api-timer:view',    '查看定时任务', 'api-timer',     '接口测试·定时任务',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000057', 'api-timer:edit',    '编辑定时任务', 'api-timer',     '接口测试·定时任务',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- Mock 服务
('c0000000-0000-0000-0000-000000000058', 'api-mock',          'Mock 服务',    NULL,            '接口测试·Mock服务',  'workspace', 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000059', 'api-mock:view',     '查看 Mock',    'api-mock',      '接口测试·Mock服务',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000060', 'api-mock:edit',     '编辑 Mock',    'api-mock',      '接口测试·Mock服务',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
-- 测试报告
('c0000000-0000-0000-0000-000000000061', 'api-report',        '测试报告',    NULL,            '接口测试·测试报告',  'workspace', 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000062', 'api-report:view',   '查看报告',    'api-report',    '接口测试·测试报告',  'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000063', 'api-report:delete', '删除报告',    'api-report',    '接口测试·测试报告',  'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- ============================================================
-- 2. 角色权限回补（管理员 + 成员）
-- ============================================================

-- 管理员角色：接口测试全部权限（含管理级删除/流水线）
UPDATE sys_role
SET permissions = permissions ||
    '["api-gitlab","api-gitlab:view","api-gitlab:edit","api-scene","api-scene:view","api-scene:edit","api-scene:import","api-scene:pipeline","api-scene:execute","api-interface","api-interface:view","api-interface:edit","api-interface:delete","api-component","api-component:view","api-component:edit","api-component:edit-space","api-component:edit-global","api-debug","api-debug:view","api-timer","api-timer:view","api-timer:edit","api-mock","api-mock:view","api-mock:edit","api-report","api-report:view","api-report:delete"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'c0000000-0000-0000-0000-000000000001'
  AND NOT permissions @> '["api-gitlab:view"]'::jsonb;

-- 成员角色：接口测试只读 + 执行（无删除/流水线权限）
UPDATE sys_role
SET permissions = permissions ||
    '["api-gitlab","api-gitlab:view","api-gitlab:edit","api-scene","api-scene:view","api-scene:edit","api-scene:import","api-scene:execute","api-interface","api-interface:view","api-interface:edit","api-component","api-component:view","api-component:edit","api-debug","api-debug:view","api-timer","api-timer:view","api-timer:edit","api-mock","api-mock:view","api-mock:edit","api-report","api-report:view"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'c0000000-0000-0000-0000-000000000002'
  AND NOT permissions @> '["api-scene:view"]'::jsonb;

-- ============================================================
-- 3. api_execution_record 新增 repository_id 列（流水线执行记录关联仓库）
-- ============================================================

ALTER TABLE api_execution_record
    ADD COLUMN IF NOT EXISTS repository_id UUID;

CREATE INDEX IF NOT EXISTS idx_api_execution_record_repository_id
    ON api_execution_record (repository_id)
    WHERE repository_id IS NOT NULL AND is_deleted = FALSE;
