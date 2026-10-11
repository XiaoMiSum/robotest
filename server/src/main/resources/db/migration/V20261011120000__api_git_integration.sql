-- GitLab 集成数据层（GitLab 集成详设 6.1，批次一 WP-1.1）：
--   1) 新增代码仓库配置表 api_git_repo（一项目一仓库，import_mode / repo_path / default_branch 创建后不可变）；
--   2) project_module 补 source_id 稳定键与 uk_pmod_source（包容器存包名、类模块存全限定名）；
--   3) api_scene 补来源 / 解析七列与 uk_scene_source（平台原生行为 platform，存量行不受影响）；
--   4) api_execution_record 补流水线批次八列与索引（流水线执行链路用，批次一仅落列）；
--   5) api_environment 补 source_id 认领封印列（环境合并用）。
-- 执行顺序：先执行本脚本，后发布应用（全部扩展列可空或带默认值，反序亦兼容，正序为惯例）。
-- 回滚方案：应用回退上一版本即可；本脚本无需回滚（新增表与可空列对旧代码无副作用）。
-- 部署顺序与锁影响：小表 DDL 秒级完成、无长事务风险；唯一索引均为部分索引，存量行 source_* 为 NULL 不参与约束。
-- 租户隔离：api_git_repo 以 project_id 归属项目，无跨项目查询路径。

-- 1. 新增表 api_git_repo（GitLab 集成详设 2.1.1）
CREATE TABLE api_git_repo (
    id                  UUID PRIMARY KEY,
    project_id          UUID NOT NULL,
    name                VARCHAR(200) NOT NULL,
    gitlab_base_url     VARCHAR(500) NOT NULL,
    repo_path           VARCHAR(500) NOT NULL,
    default_branch      VARCHAR(200) NOT NULL DEFAULT 'master',
    import_mode         VARCHAR(20) NOT NULL,
    token_cipher        VARCHAR(500) NOT NULL,
    test_source_dir     VARCHAR(500) NOT NULL DEFAULT 'src/test/java',
    resource_dir        VARCHAR(500) NOT NULL DEFAULT 'src/test/resources',
    env_file_path       VARCHAR(500) NOT NULL DEFAULT 'common/env/env.yaml',
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    last_import_at      TIMESTAMP NULL,
    last_import_commit  VARCHAR(64) NULL,
    last_import_summary JSONB NULL,
    head_commit         VARCHAR(64) NULL,
    head_checked_at     TIMESTAMP NULL,
    head_probe_status   VARCHAR(20) NULL,
    created_by          UUID NOT NULL,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_api_git_repo_project ON api_git_repo(project_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_api_git_repo_project ON api_git_repo(project_id) WHERE is_deleted = FALSE;

COMMENT ON TABLE api_git_repo IS '代码仓库配置表（一项目一仓库，GitLab 集成详设 2.1.1）';
COMMENT ON COLUMN api_git_repo.repo_path IS '项目路径（group/subgroup/repo），源侧稳定键，创建后不可改';
COMMENT ON COLUMN api_git_repo.import_mode IS '导入模式：platform（导入为平台场景）/ gitlab_ci（流水线调度），创建后不可改';
COMMENT ON COLUMN api_git_repo.token_cipher IS '访问令牌密文（AES-256-GCM，只进不出，不下发前端）';
COMMENT ON COLUMN api_git_repo.last_import_commit IS '上次导入分支 HEAD，commit 幂等闸门比对基准';
COMMENT ON COLUMN api_git_repo.last_import_summary IS '上次导入结果 {created,updated,parseFailed,errorFailed,skipped}，闸门只看 errorFailed';
COMMENT ON COLUMN api_git_repo.head_commit IS '源探查回写的默认分支最新提交，与 last_import_commit 不一致即「源有更新」';
COMMENT ON COLUMN api_git_repo.head_probe_status IS '源探查结果：ok/unreachable，unreachable 时保留上次 head_commit';

-- 2. project_module 扩展（项目模块详设 2.1）
ALTER TABLE project_module ADD COLUMN source_id VARCHAR(500) NULL;
CREATE UNIQUE INDEX uk_pmod_source ON project_module(project_id, parent_id, source_id)
    WHERE is_deleted = FALSE AND source_id IS NOT NULL;
COMMENT ON COLUMN project_module.source_id IS '源侧映射标识：包容器存完整包名、类模块存测试类全限定名（GitLab 集成详设 3.5）';

-- 3. api_scene 扩展（测试场景详设 2.1.2）
ALTER TABLE api_scene ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'platform';
ALTER TABLE api_scene ADD COLUMN repo_id UUID NULL;
ALTER TABLE api_scene ADD COLUMN source_id VARCHAR(500) NULL;
ALTER TABLE api_scene ADD COLUMN source_ref VARCHAR(500) NULL;
ALTER TABLE api_scene ADD COLUMN source_status VARCHAR(30) NULL;
ALTER TABLE api_scene ADD COLUMN parse_status VARCHAR(20) NULL;
ALTER TABLE api_scene ADD COLUMN parse_issues JSONB NULL;
CREATE UNIQUE INDEX uk_scene_source ON api_scene(repo_id, source_id)
    WHERE is_deleted = FALSE AND repo_id IS NOT NULL AND source_id IS NOT NULL;

COMMENT ON COLUMN api_scene.source_type IS '场景来源：platform（平台原生）/ gitlab（GitLab 导入）';
COMMENT ON COLUMN api_scene.repo_id IS '来源仓库（api_git_repo.id），仅 source_type = gitlab 时非空';
COMMENT ON COLUMN api_scene.source_id IS '源侧稳定键（类全限定名#方法名），导入写入，禁止平台编辑覆盖';
COMMENT ON COLUMN api_scene.source_ref IS '源侧引用（步骤模板或测试方法的相对路径 / 方法签名）';
COMMENT ON COLUMN api_scene.source_status IS '源状态：in_sync/parse_failed/repo_removed/repo_unreachable，仅展示';
COMMENT ON COLUMN api_scene.parse_status IS '解析状态：ok/failed，仅 platform 导入模式有意义';
COMMENT ON COLUMN api_scene.parse_issues IS '解析问题清单 [{type,message,ref}]';

-- 4. api_execution_record 扩展（API 测试基础设施详设 2.1.3）
ALTER TABLE api_execution_record ADD COLUMN repo_id UUID NULL;
ALTER TABLE api_execution_record ADD COLUMN pipeline_iid BIGINT NULL;
ALTER TABLE api_execution_record ADD COLUMN pipeline_url VARCHAR(500) NULL;
ALTER TABLE api_execution_record ADD COLUMN batch_id VARCHAR(64) NULL;
ALTER TABLE api_execution_record ADD COLUMN source_ref VARCHAR(500) NULL;
ALTER TABLE api_execution_record ADD COLUMN commit_sha VARCHAR(64) NULL;
ALTER TABLE api_execution_record ADD COLUMN scheduled_task_id UUID NULL;
ALTER TABLE api_execution_record ADD COLUMN summary JSONB NULL;
CREATE INDEX idx_exec_pipeline ON api_execution_record(repo_id, pipeline_iid)
    WHERE pipeline_iid IS NOT NULL;
CREATE INDEX idx_exec_batch ON api_execution_record(batch_id) WHERE batch_id IS NOT NULL;

COMMENT ON COLUMN api_execution_record.repo_id IS '来源仓库（api_git_repo.id），CI 引擎执行时非空';
COMMENT ON COLUMN api_execution_record.pipeline_iid IS 'GitLab 流水线IID（project 内唯一，artifact 下载键）';
COMMENT ON COLUMN api_execution_record.batch_id IS '外部执行批次票据，同批次内所有记录相同';
COMMENT ON COLUMN api_execution_record.summary IS '批次汇总（期望/实际用例数与差值），流水线执行用';

-- 5. api_environment 扩展（环境管理详设 2.1.1）
ALTER TABLE api_environment ADD COLUMN source_id VARCHAR(500) NULL;
COMMENT ON COLUMN api_environment.source_id IS '认领该环境的仓库路径（api_git_repo.repo_path），写入即封印';
