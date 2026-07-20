-- workspace 角色 RBAC 迁移
-- 将 workspace_user.workspace_role 从字符串枚举迁移为引用 sys_role.id（UUID）

-- 1. 预置 workspace 角色
INSERT INTO sys_role (id, name, description, type, is_system, permissions, created_at, updated_at, is_deleted) VALUES
('c0000000-0000-0000-0000-000000000001', 'workspace_admin',  '空间管理员 — 拥有工作空间内全部业务权限', 'workspace', TRUE,
 '["workspace:edit","workspace:manage-members","project:create","project:edit","project:archive","project:delete"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('c0000000-0000-0000-0000-000000000002', 'workspace_member', '普通成员 — 默认工作空间成员角色', 'workspace', TRUE,
 '[]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- 2. 迁移现有数据（member → workspace_member, admin → workspace_admin）
DO $$
BEGIN
  UPDATE workspace_user
  SET workspace_role = 'c0000000-0000-0000-0000-000000000002'
  WHERE workspace_role = 'member';

  UPDATE workspace_user
  SET workspace_role = 'c0000000-0000-0000-0000-000000000001'
  WHERE workspace_role = 'admin';
END $$;

-- 3. 修改列类型为 UUID（数据已迁移为 UUID 字符串，可直接转换）
ALTER TABLE workspace_user
  ALTER COLUMN workspace_role TYPE UUID USING workspace_role::UUID;

-- 4. 设置默认值为 workspace_member 角色 ID
ALTER TABLE workspace_user
  ALTER COLUMN workspace_role SET DEFAULT 'c0000000-0000-0000-0000-000000000002'::UUID;
