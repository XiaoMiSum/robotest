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
-- 5. 项目设置表（安全策略与应用设置，「域 + 键」统一存储）
-- ============================================================

CREATE TABLE project_setting (
    id            UUID         PRIMARY KEY,
    project_id    UUID         NOT NULL,
    domain        VARCHAR(20)  NOT NULL,
    setting_key   VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    updated_by    UUID         NOT NULL,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_project_setting
    ON project_setting(project_id, domain, setting_key) WHERE is_deleted = FALSE;

COMMENT ON TABLE project_setting IS '项目设置表（项目级设置项「域+键」统一存储，未落库键读取时返回注册表默认值）';
COMMENT ON COLUMN project_setting.id IS '设置项唯一标识';
COMMENT ON COLUMN project_setting.project_id IS '所属项目 ID，关联 ws_project.id（逻辑关联，无物理外键）';
COMMENT ON COLUMN project_setting.domain IS '业务域归属：common / api_test / func_test';
COMMENT ON COLUMN project_setting.setting_key IS '设置项标识（如 report.share.enabled），白名单见代码注册表';
COMMENT ON COLUMN project_setting.setting_value IS '设置值（字符串化存储，语义由注册表定义）';
COMMENT ON COLUMN project_setting.updated_by IS '最后维护人，关联 sys_user.id';
COMMENT ON COLUMN project_setting.is_deleted IS '逻辑删除标志';
COMMENT ON COLUMN project_setting.created_at IS '创建时间';
COMMENT ON COLUMN project_setting.updated_at IS '更新时间';

-- ============================================================
-- 6. 接口测试——环境管理（SRS 3.7.1，详细设计《环境管理详细设计说明书》2.1）
-- ============================================================

CREATE TABLE api_environment (
    id          UUID         PRIMARY KEY,
    project_id  UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    scope       VARCHAR(10)  NOT NULL DEFAULT 'project',
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order  INT          NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_env_project ON api_environment(project_id);

COMMENT ON TABLE api_environment IS '接口测试环境表（项目级环境配置集合，配置项与 Ryze 配置元件对齐）';
COMMENT ON COLUMN api_environment.project_id IS '归属项目 ID，关联 ws_project.id（逻辑关联，无物理外键）';
COMMENT ON COLUMN api_environment.scope IS '环境归属范围：project（项目级）/ global（全局级，V1.2 预留扩展）';
COMMENT ON COLUMN api_environment.is_default IS '是否默认环境（项目内唯一）：场景执行未指定环境时使用默认环境';
COMMENT ON COLUMN api_environment.sort_order IS '排序序号，列表按升序展示（默认环境置顶）';

CREATE TABLE api_environment_http (
    id                 UUID           PRIMARY KEY,
    environment_id     UUID           NOT NULL,
    name               VARCHAR(100)   NOT NULL,
    ref_name           VARCHAR(100)   NOT NULL,
    base_url           VARCHAR(2000)  NOT NULL,
    default_method     VARCHAR(10)    NULL,
    default_headers    JSONB          NOT NULL DEFAULT '[]',
    timeout_ms         INT            NOT NULL DEFAULT 30000,
    connect_timeout_ms INT            NOT NULL DEFAULT 10000,
    follow_redirects   BOOLEAN        NOT NULL DEFAULT TRUE,
    verify_ssl         BOOLEAN        NOT NULL DEFAULT TRUE,
    is_default         BOOLEAN        NOT NULL DEFAULT FALSE,
    is_deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ehttp_env ON api_environment_http(environment_id);

COMMENT ON TABLE api_environment_http IS '环境 HTTP 配置表（每环境支持多个目标系统配置，有且仅有一个默认）';
COMMENT ON COLUMN api_environment_http.environment_id IS '归属环境 ID，关联 api_environment.id（逻辑关联，无物理外键）';
COMMENT ON COLUMN api_environment_http.ref_name IS '引用名称（对应 Ryze ref_name，用于步骤中引用该配置）';
COMMENT ON COLUMN api_environment_http.default_headers IS '默认请求头 [{key, value, enabled}]';
COMMENT ON COLUMN api_environment_http.is_default IS '是否为该环境的默认 HTTP 配置';

CREATE TABLE api_environment_variable (
    id             UUID         PRIMARY KEY,
    environment_id UUID         NOT NULL,
    name           VARCHAR(100) NOT NULL,
    value          TEXT         NULL,
    description    VARCHAR(500) NULL,
    type           VARCHAR(10)  NOT NULL DEFAULT 'text',
    source_step_id UUID         NULL,
    source_report_id UUID       NULL,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evar_env ON api_environment_variable(environment_id);

COMMENT ON TABLE api_environment_variable IS '环境变量表（变量解析优先级：内置函数 < 环境变量 < 场景变量 < 步骤级变量 < 提取器变量 < 运行时覆盖）';
COMMENT ON COLUMN api_environment_variable.name IS '变量名，仅字母/数字/下划线，同环境内唯一';
COMMENT ON COLUMN api_environment_variable.value IS '变量值；type=sensitive 时存 AES-256-GCM 密文，不输出明文至前端';
COMMENT ON COLUMN api_environment_variable.type IS '变量类型：text / number / sensitive';
COMMENT ON COLUMN api_environment_variable.source_step_id IS '来源步骤 ID：从执行结果添加变量时记录溯源（3.3.2），普通录入为空';
COMMENT ON COLUMN api_environment_variable.source_report_id IS '来源报告 ID：从执行结果添加变量时记录溯源（3.3.2），普通录入为空';

CREATE TABLE api_data_source (
    id                    UUID         PRIMARY KEY,
    environment_id        UUID         NOT NULL,
    name                  VARCHAR(100) NOT NULL,
    ref_name              VARCHAR(100) NOT NULL,
    driver                VARCHAR(100) NOT NULL,
    url                   VARCHAR(500) NOT NULL,
    connection_properties JSONB        NOT NULL DEFAULT '{}',
    max_pool_size         INT          NOT NULL DEFAULT 5,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ds_env ON api_data_source(environment_id);

COMMENT ON TABLE api_data_source IS '环境数据源表（JDBC 连接信息，用户名密码直接写入 URL）';
COMMENT ON COLUMN api_data_source.ref_name IS '引用名称（对应 Ryze ref_name，用于步骤中引用该数据源）';
COMMENT ON COLUMN api_data_source.url IS 'JDBC 连接 URL（凭据内嵌，不在表单独立存储）';
COMMENT ON COLUMN api_data_source.connection_properties IS '附加连接参数 JSON 对象';
COMMENT ON COLUMN api_data_source.max_pool_size IS 'HikariCP 连接池最大连接数';

CREATE TABLE api_environment_processor (
    id             UUID         PRIMARY KEY,
    environment_id UUID         NOT NULL,
    processor_type VARCHAR(20)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    config         JSONB        NOT NULL,
    sort_order     INT          NOT NULL DEFAULT 0,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eproc_env ON api_environment_processor(environment_id);

COMMENT ON TABLE api_environment_processor IS '环境全局处理器表（前置/后置，作用于该环境下所有请求）';
COMMENT ON COLUMN api_environment_processor.processor_type IS '处理器类型：preprocessor / postprocessor';
COMMENT ON COLUMN api_environment_processor.config IS '处理器配置 JSON（与 Ryze 处理器元件结构一致）';

-- ============================================================
-- 6.8 快速调试——调试记录表
-- ============================================================
CREATE TABLE api_debug_record (
    id               UUID          PRIMARY KEY,
    project_id       UUID          NOT NULL,
    user_id          UUID          NOT NULL,
    name             VARCHAR(200)  NULL,
    protocol         VARCHAR(20)   NOT NULL,
    method           VARCHAR(10)   NULL,
    url              VARCHAR(2000) NULL,
    headers          JSONB         NOT NULL DEFAULT '[]',
    body_type        VARCHAR(20)   NULL,
    body             JSONB         NULL,
    query_params     JSONB         NOT NULL DEFAULT '[]',
    jdbc_config      JSONB         NULL,
    processors       JSONB         NOT NULL DEFAULT '[]',
    environment_id   UUID          NULL,
    timeout_ms       INT           NULL,
    executed_at      TIMESTAMP     NOT NULL,
    duration_ms      INT           NULL,
    status           VARCHAR(20)   NOT NULL,
    response_status  INT           NULL,
    response_headers JSONB         NULL,
    response_body    TEXT          NULL,
    response_size    INT           NULL,
    error_message    VARCHAR(2000) NULL,
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_debug_project_user ON api_debug_record(project_id, user_id);
CREATE INDEX idx_debug_executed_at ON api_debug_record(executed_at);

COMMENT ON TABLE api_debug_record IS '快速调试记录表（服务端执行后自动保存的请求快照与响应，每用户保留最近 200 条）';
COMMENT ON COLUMN api_debug_record.body_type IS '请求体类型：none / json / form / raw / binary';
COMMENT ON COLUMN api_debug_record.body IS '请求体内容 JSON（结构随 body_type）';
COMMENT ON COLUMN api_debug_record.environment_id IS '执行引用的环境 ID（相对 URL 拼接与变量来源），可空';
COMMENT ON COLUMN api_debug_record.status IS '执行结果：success / failed / error';
COMMENT ON COLUMN api_debug_record.response_body IS '响应体（截断存储，最大 1MB）';
COMMENT ON COLUMN api_debug_record.response_size IS '响应体字节数';

-- ============================================================
-- 7. 注意事项
-- ============================================================
-- - test_case_node.document_id 仍指向文档 ID（现为 test_case_document.id）
-- - bug.module_id 仍指向模块 ID（现为 project_module.id）
