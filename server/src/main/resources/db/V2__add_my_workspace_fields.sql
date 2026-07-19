-- 我的工作空间功能：补充字段

-- workspace_user 表添加 default_project_id 字段
ALTER TABLE workspace_user ADD COLUMN default_project_id UUID;

CREATE INDEX idx_workspace_user_default_project_id ON workspace_user (default_project_id);

-- sys_user 表添加 last_active_workspace_id 字段
ALTER TABLE sys_user ADD COLUMN last_active_workspace_id UUID;

CREATE INDEX idx_user_last_active_workspace ON sys_user (last_active_workspace_id);
