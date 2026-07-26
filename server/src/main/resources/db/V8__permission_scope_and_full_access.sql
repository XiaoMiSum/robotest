-- 权限 scope + 角色 full_access 迁移

-- 1. sys_permission 加 scope 列（global / workspace）
ALTER TABLE sys_permission ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'global';
CREATE INDEX idx_permission_scope ON sys_permission (scope);

-- 2. 更新 V1 种子数据为 global scope（用户管理、工作空间管理、角色管理）
UPDATE sys_permission SET scope = 'global' WHERE code LIKE 'user%' OR code LIKE 'workspace%' OR code LIKE 'role%';

-- 3. 更新 V7 种子数据为 workspace scope（我的空间、项目、测试用例、评审、计划、缺陷）
UPDATE sys_permission SET scope = 'workspace'
WHERE module IN ('我的空间', '项目', '测试用例', '测试评审', '测试计划', '缺陷');

-- 4. sys_role 加 full_access 列
ALTER TABLE sys_role ADD COLUMN full_access BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. workspace_admin 设为 full_access，清空 JSONB（full_access 角色不需要逐条存储）
UPDATE sys_role SET full_access = TRUE, permissions = '[]'
WHERE id = 'c0000000-0000-0000-0000-000000000001';
