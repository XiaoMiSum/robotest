-- 更新 workspace 角色名称和权限

-- 1. 更新 workspace_member 角色名称和权限
UPDATE sys_role
SET name = '空间成员',
    description = '空间成员 — 除删除/归档项目、管理成员、编辑空间信息外的其他权限',
    permissions = '["ws-info:view","ws-member:view","ws-invitation:view","ws-invitation:manage","project:view","project:create","project:edit","project:set-default","case:view","case:edit","review:view","review:create","review:edit","review:complete","plan:view","plan:create","plan:execute","plan:close","bug:view","bug:create","bug:edit"]'
WHERE id = 'c0000000-0000-0000-0000-000000000002';

-- 2. 更新 workspace_admin 角色名称
UPDATE sys_role
SET name = '空间管理员',
    description = '空间管理员 — 拥有工作空间内全部业务权限'
WHERE id = 'c0000000-0000-0000-0000-000000000001';
