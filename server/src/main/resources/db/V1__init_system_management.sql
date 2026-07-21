-- Robotest 系统管理模块 DDL
-- PostgreSQL

-- 用户表
CREATE TABLE sys_user (
    id           UUID         PRIMARY KEY,
    username     VARCHAR(30)  NOT NULL,
    email        VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url   VARCHAR(500),
    status       VARCHAR(20)  NOT NULL DEFAULT 'active',
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_username ON sys_user (username);
CREATE INDEX idx_status ON sys_user (status);

-- 角色表
CREATE TABLE sys_role (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL,
    description  VARCHAR(200),
    type         VARCHAR(20)  NOT NULL,
    is_system    BOOLEAN      NOT NULL DEFAULT FALSE,
    permissions  JSONB        NOT NULL DEFAULT '[]',
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_role_name ON sys_role (name);
CREATE INDEX idx_role_type ON sys_role (type);

-- 用户-角色关联表
CREATE TABLE sys_user_role (
    id          UUID      PRIMARY KEY,
    user_id     UUID      NOT NULL,
    role_id     UUID      NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_user_role ON sys_user_role (user_id, role_id);
CREATE INDEX idx_user_role_role_id ON sys_user_role (role_id);

-- 工作空间表
CREATE TABLE workspace (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_workspace_name ON workspace (name);

-- 用户-工作空间关联表
CREATE TABLE workspace_user (
    id             UUID      PRIMARY KEY,
    user_id        UUID      NOT NULL,
    workspace_id   UUID      NOT NULL,
    workspace_role VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted     BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_user_workspace ON workspace_user (user_id, workspace_id);
CREATE INDEX idx_workspace_user_workspace_id ON workspace_user (workspace_id);

-- 权限点表
CREATE TABLE sys_permission (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    parent_code VARCHAR(100),
    module      VARCHAR(50)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_permission_code ON sys_permission (code);
CREATE INDEX idx_permission_parent_code ON sys_permission (parent_code);
CREATE INDEX idx_permission_module ON sys_permission (module);

-- 初始权限数据
INSERT INTO sys_permission (id, code, name, parent_code, module, sort_order, created_at, updated_at, is_deleted) VALUES
('a0000000-0000-0000-0000-000000000001', 'user', '用户管理', NULL, '用户管理', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000002', 'user:view', '查看用户', 'user', '用户管理', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000003', 'user:create', '创建用户', 'user', '用户管理', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000004', 'user:edit', '编辑用户', 'user', '用户管理', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000005', 'user:disable', '禁用/启用用户', 'user', '用户管理', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000006', 'user:reset-password', '重置密码', 'user', '用户管理', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000007', 'workspace', '工作空间管理', NULL, '工作空间管理', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000008', 'workspace:view', '查看工作空间', 'workspace', '工作空间管理', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000009', 'workspace:create', '创建工作空间', 'workspace', '工作空间管理', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000010', 'workspace:edit', '编辑工作空间', 'workspace', '工作空间管理', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000011', 'workspace:delete', '解散工作空间', 'workspace', '工作空间管理', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000012', 'workspace:manage-members', '管理成员', 'workspace', '工作空间管理', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000013', 'role', '角色管理', NULL, '角色管理', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000014', 'role:view', '查看角色', 'role', '角色管理', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000015', 'role:create', '创建角色', 'role', '角色管理', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000016', 'role:edit', '编辑角色', 'role', '角色管理', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE),
('a0000000-0000-0000-0000-000000000017', 'role:delete', '删除角色', 'role', '角色管理', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);

-- 预置角色：系统管理员（拥有所有admin权限）
INSERT INTO sys_role (id, name, description, type, is_system, permissions, created_at, updated_at, is_deleted) VALUES
('b0000000-0000-0000-0000-000000000001', '系统管理员', '拥有系统管理所有权限', 'system', TRUE,
 '["user","user:view","user:create","user:edit","user:disable","user:reset-password","workspace","workspace:view","workspace:create","workspace:edit","workspace:delete","workspace:manage-members","role","role:view","role:create","role:edit","role:delete"]',
 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE);
