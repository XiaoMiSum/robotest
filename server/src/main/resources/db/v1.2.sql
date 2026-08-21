-- Robotest V1.2 数据库增量 DDL
-- PostgreSQL
-- V1.2 项目模块与用例文档重构：project_module + test_case_document 替代 test_case_module
-- 依据：《项目模块详细设计说明书 V1.2》《文档管理详细设计说明书 V1.2》
-- 首次建库按 v1.sql → v1.1.sql → v1.2.sql 顺序执行

-- ============================================================
-- 1. 项目模块表（纯目录节点，无 type 字段）
-- ============================================================

CREATE TABLE project_module (
    id          UUID         PRIMARY KEY,
    project_id  UUID         NOT NULL,
    parent_id   UUID         NULL,
    name        VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pmod_project ON project_module(project_id);
CREATE INDEX idx_pmod_parent ON project_module(parent_id);

COMMENT ON TABLE project_module IS '项目模块表（纯目录树节点，无 type 字段，跨功能测试/接口管理/测试场景共享）';
COMMENT ON COLUMN project_module.id IS '模块唯一标识';
COMMENT ON COLUMN project_module.project_id IS '所属项目 ID，关联 ws_project.id';
COMMENT ON COLUMN project_module.parent_id IS '父级模块 ID，NULL=根节点';
COMMENT ON COLUMN project_module.name IS '模块名称（同级唯一）';
COMMENT ON COLUMN project_module.sort_order IS '同级排序序号';
COMMENT ON COLUMN project_module.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN project_module.created_at IS '创建时间';
COMMENT ON COLUMN project_module.updated_at IS '更新时间';

-- ============================================================
-- 2. 用例文档表（布局内嵌 JSON 字段）
-- ============================================================

CREATE TABLE test_case_document (
    id          UUID         PRIMARY KEY,
    project_id  UUID         NOT NULL,
    module_id   UUID         NULL,
    name        VARCHAR(100) NOT NULL,
    layout      JSONB        NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tcd_project ON test_case_document(project_id);
CREATE INDEX idx_tcd_module ON test_case_document(module_id);

COMMENT ON TABLE test_case_document IS '用例文档表（布局内嵌 JSON 字段，替代 test_case_document_layout 表）';
COMMENT ON COLUMN test_case_document.id IS '文档唯一标识';
COMMENT ON COLUMN test_case_document.project_id IS '所属项目 ID，关联 ws_project.id';
COMMENT ON COLUMN test_case_document.module_id IS '所属模块 ID，关联 project_module.id（NULL=根层级）';
COMMENT ON COLUMN test_case_document.name IS '文档名称（同模块下唯一）';
COMMENT ON COLUMN test_case_document.layout IS '布局数据 JSONB（template: right/top，offsets: {nodeId: {x,y}}）';
COMMENT ON COLUMN test_case_document.sort_order IS '同层级排序序号';
COMMENT ON COLUMN test_case_document.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN test_case_document.created_at IS '创建时间';
COMMENT ON COLUMN test_case_document.updated_at IS '更新时间';

-- ============================================================
-- 3. 存量数据迁移（必须在第 4 节删表前执行）
-- ============================================================
-- 旧 test_case_module 单表同时存目录与文档，按 type 拆分迁移；
-- 各行保持原 id 不变：目录 id 被 bug.module_id / 评审计划快照 original_module_id 引用，
-- 文档 id 被 test_case_node.document_id 引用，换 id 会导致全部引用悬空

-- 3.1 目录节点 → project_module
INSERT INTO project_module (id, project_id, parent_id, name, sort_order, is_deleted, created_at, updated_at)
SELECT id, project_id, parent_id, name, sort_order, is_deleted, created_at, updated_at
FROM test_case_module
WHERE type = 'directory';

-- 3.2 文档节点 → test_case_document（旧 parent_id 即新 module_id）；
--     布局自 test_case_document_layout 合并为内嵌 JSONB，无布局行的文档 layout 保持 NULL
INSERT INTO test_case_document (id, project_id, module_id, name, layout, sort_order, is_deleted, created_at, updated_at)
SELECT m.id, m.project_id, m.parent_id, m.name, l.layout_json, m.sort_order, m.is_deleted, m.created_at, m.updated_at
FROM test_case_module m
LEFT JOIN test_case_document_layout l ON l.document_id = m.id AND l.is_deleted = FALSE
WHERE m.type = 'document';

-- ============================================================
-- 4. 清理旧表（存量迁移完成后执行）
-- ============================================================
-- 所有依赖 test_case_module 的服务已迁移到 project_module + test_case_document，旧表可安全删除
DROP TABLE IF EXISTS test_case_module CASCADE;
-- test_case_document_layout 已被 test_case_document.layout JSONB 字段替代
DROP TABLE IF EXISTS test_case_document_layout CASCADE;

-- bug.module_id 引用目标随模块重构变更，同步更新库内注释（v1.sql 基线不回改）
COMMENT ON COLUMN bug.module_id IS '所属模块 ID，关联 project_module.id（V1.2 起缺陷按目录归属）';

-- ============================================================
-- 5. 注意事项
-- ============================================================
-- - test_case_node.document_id 仍指向文档 ID（现为 test_case_document.id）
-- - bug.module_id 仍指向模块 ID（现为 project_module.id）
