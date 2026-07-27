-- 新增系统角色：空间管理员
-- 拥有工作空间管理下的所有权限（系统级，跨空间管理）

INSERT INTO sys_role (id, name, description, type, is_system, permissions, created_at, updated_at, is_deleted) VALUES
('b0000000-0000-0000-0000-000000000002', '空间管理员', '拥有工作空间管理所有权限，可创建/删除/管理所有工作空间', 'system', TRUE,
 '["workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","ws-info","ws-info:view","ws-info:edit","ws-member","ws-member:view","ws-member:manage","ws-invitation","ws-invitation:view","ws-invitation:manage"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);
