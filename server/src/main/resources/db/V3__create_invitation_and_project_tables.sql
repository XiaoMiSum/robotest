-- 空间管理模块：邀请链接表和项目表

-- 邀请链接表
CREATE TABLE workspace_invitation (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL,
    expires_at TIMESTAMP NULL,
    max_uses INT NULL,
    use_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_invitation_token ON workspace_invitation (token);
CREATE INDEX idx_invitation_workspace_id ON workspace_invitation (workspace_id);

-- 项目表
CREATE TABLE project (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_project_workspace_name ON project (workspace_id, name);
CREATE INDEX idx_project_workspace_id ON project (workspace_id);
CREATE INDEX idx_project_status ON project (status);
