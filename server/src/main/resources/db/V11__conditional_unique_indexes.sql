-- 将所有唯一索引改为条件唯一索引，仅对未删除的行生效
-- 支持逻辑删除后重建同名数据

-- 1. sys_user.username
DROP INDEX IF EXISTS uk_username;
CREATE UNIQUE INDEX uk_username ON sys_user (username) WHERE is_deleted = false;

-- 2. sys_role.name
DROP INDEX IF EXISTS uk_role_name;
CREATE UNIQUE INDEX uk_role_name ON sys_role (name) WHERE is_deleted = false;

-- 3. sys_user_role (user_id, role_id)
DROP INDEX IF EXISTS uk_user_role;
CREATE UNIQUE INDEX uk_user_role ON sys_user_role (user_id, role_id) WHERE is_deleted = false;

-- 4. workspace.name
DROP INDEX IF EXISTS uk_workspace_name;
CREATE UNIQUE INDEX uk_workspace_name ON workspace (name) WHERE is_deleted = false;

-- 5. workspace_user (user_id, workspace_id)
DROP INDEX IF EXISTS uk_user_workspace;
CREATE UNIQUE INDEX uk_user_workspace ON workspace_user (user_id, workspace_id) WHERE is_deleted = false;

-- 6. sys_permission.code
DROP INDEX IF EXISTS uk_permission_code;
CREATE UNIQUE INDEX uk_permission_code ON sys_permission (code) WHERE is_deleted = false;

-- 7. workspace_invitation.token
DROP INDEX IF EXISTS uk_invitation_token;
CREATE UNIQUE INDEX uk_invitation_token ON workspace_invitation (token) WHERE is_deleted = false;

-- 8. project (workspace_id, name)
DROP INDEX IF EXISTS uk_project_workspace_name;
CREATE UNIQUE INDEX uk_project_workspace_name ON project (workspace_id, name) WHERE is_deleted = false;

-- 9. test_case_document_layout.document_id
DROP INDEX IF EXISTS uk_test_case_document_layout_doc;
CREATE UNIQUE INDEX uk_test_case_document_layout_doc ON test_case_document_layout (document_id) WHERE is_deleted = false;
