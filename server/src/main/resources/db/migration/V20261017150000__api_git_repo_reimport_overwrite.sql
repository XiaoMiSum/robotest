-- 仓库配置补列：重复导入覆盖策略 reimport_overwrite（GitLab 集成详设 2.1.1 / 3.5）：
--   1) api_git_repo 补 reimport_overwrite 布尔列（默认 FALSE 不覆盖），仅 platform 导入模式生效；
--   2) 该列曾被原地写入 V20261011120000（已执行版本不可变更），对存量库以本脚本幂等补列；
--   3) IF NOT EXISTS 保证已手工补列的环境安全跳过，可重复执行。
ALTER TABLE api_git_repo
    ADD COLUMN IF NOT EXISTS reimport_overwrite BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN api_git_repo.reimport_overwrite IS '重复导入是否以源侧内容覆盖平台侧场景内容（默认不覆盖，仅 platform 模式生效）';
