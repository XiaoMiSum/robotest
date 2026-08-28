-- ============================================================
-- v1.7 权限点补充：接口测试 · 环境管理 / 函数管理 / 公共组件 / GitLab 配置 / 应用设置
--
-- 用途：补全运行中数据库（Docker PG）缺失的五个模块权限点，并同步预置角色数组。
-- 幂等：已存在的权限点跳过；角色数组仅在缺失对应权限码时追加，不会重复。
-- 说明：schema.sql 21.5/21.6 种子已同步本次变更（随既有合并提交落地），
--       本脚本针对已建库环境的增量修正，老库直接执行即可。
-- ============================================================

-- ------------------------------------------------------------
-- 1) 权限点：接口测试 · 环境管理（api-env）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000064', 'api-env', '环境管理', NULL, '接口测试·环境管理', 'workspace', 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-env' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000065', 'api-env:view', '查看环境', 'api-env', '接口测试·环境管理', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-env:view' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000066', 'api-env:edit', '编辑环境', 'api-env', '接口测试·环境管理', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-env:edit' AND is_deleted = false);

-- ------------------------------------------------------------
-- 2) 权限点：接口测试 · 函数管理（api-func）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000067', 'api-func', '函数管理', NULL, '接口测试·函数管理', 'workspace', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-func' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000068', 'api-func:view', '查看函数', 'api-func', '接口测试·函数管理', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-func:view' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000069', 'api-func:edit', '编辑函数', 'api-func', '接口测试·函数管理', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-func:edit' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000072', 'api-func:edit-space', '编辑空间级函数', 'api-func', '接口测试·函数管理', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-func:edit-space' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000073', 'api-func:edit-global', '编辑全局函数', 'api-func', '接口测试·函数管理', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-func:edit-global' AND is_deleted = false);

-- ------------------------------------------------------------
-- 3) 权限点：接口测试 · 应用设置（api-setting）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000074', 'api-setting', '应用设置', NULL, '接口测试·应用设置', 'workspace', 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-setting' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000075', 'api-setting:view', '查看应用设置', 'api-setting', '接口测试·应用设置', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-setting:view' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000076', 'api-setting:edit', '编辑应用设置', 'api-setting', '接口测试·应用设置', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-setting:edit' AND is_deleted = false);

-- ------------------------------------------------------------
-- 4) 权限点：接口测试 · 公共组件（api-component，老库可能缺失）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000050', 'api-component', '公共组件', NULL, '接口测试·公共组件', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-component' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000051', 'api-component:view', '查看组件', 'api-component', '接口测试·公共组件', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-component:view' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000052', 'api-component:edit', '编辑组件', 'api-component', '接口测试·公共组件', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-component:edit' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000070', 'api-component:edit-space', '编辑空间级组件', 'api-component', '接口测试·公共组件', 'workspace', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-component:edit-space' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000071', 'api-component:edit-global', '编辑全局组件', 'api-component', '接口测试·公共组件', 'workspace', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-component:edit-global' AND is_deleted = false);

-- ------------------------------------------------------------
-- 5) 权限点：接口测试 · GitLab 配置（api-gitlab，老库可能缺失）
-- ------------------------------------------------------------
INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000037', 'api-gitlab', 'GitLab 仓库', NULL, '接口测试·GitLab', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-gitlab' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000038', 'api-gitlab:view', '查看 GitLab 仓库', 'api-gitlab', '接口测试·GitLab', 'workspace', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-gitlab:view' AND is_deleted = false);

INSERT INTO sys_permission (id, code, name, parent_code, module, scope, sort_order, created_at, updated_at, is_deleted)
SELECT 'c0000000-0000-0000-0000-000000000039', 'api-gitlab:edit', '编辑 GitLab 仓库', 'api-gitlab', '接口测试·GitLab', 'workspace', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'api-gitlab:edit' AND is_deleted = false);

-- ------------------------------------------------------------
-- 6) 预置角色同步：空间管理员（拥有全部业务权限）
-- ------------------------------------------------------------
UPDATE sys_role
SET permissions = permissions || '["api-env","api-env:view","api-env:edit"]'::jsonb
WHERE id = 'c0000000-0000-0000-0000-000000000001' AND is_deleted = false
  AND NOT (permissions ?| ARRAY['api-env:view', 'api-env:edit']);

UPDATE sys_role
SET permissions = permissions || '["api-func","api-func:view","api-func:edit","api-func:edit-space","api-func:edit-global"]'::jsonb
WHERE id = 'c0000000-0000-0000-0000-000000000001' AND is_deleted = false
  AND NOT (permissions ?| ARRAY['api-func:view', 'api-func:edit']);

UPDATE sys_role
SET permissions = permissions || '["api-setting","api-setting:view","api-setting:edit"]'::jsonb
WHERE id = 'c0000000-0000-0000-0000-000000000001' AND is_deleted = false
  AND NOT (permissions ?| ARRAY['api-setting:view', 'api-setting:edit']);

-- ------------------------------------------------------------
-- 7) 预置角色同步：空间成员（默认角色，与环境/函数/组件一致的宽容模式）
-- ------------------------------------------------------------
UPDATE sys_role
SET permissions = permissions || '["api-env","api-env:view","api-env:edit"]'::jsonb
WHERE id = 'c0000000-0000-0000-0000-000000000002' AND is_deleted = false
  AND NOT (permissions ?| ARRAY['api-env:view', 'api-env:edit']);

UPDATE sys_role
SET permissions = permissions || '["api-func","api-func:view","api-func:edit"]'::jsonb
WHERE id = 'c0000000-0000-0000-0000-000000000002' AND is_deleted = false
  AND NOT (permissions ?| ARRAY['api-func:view', 'api-func:edit']);

UPDATE sys_role
SET permissions = permissions || '["api-setting","api-setting:view","api-setting:edit"]'::jsonb
WHERE id = 'c0000000-0000-0000-0000-000000000002' AND is_deleted = false
  AND NOT (permissions ?| ARRAY['api-setting:view', 'api-setting:edit']);