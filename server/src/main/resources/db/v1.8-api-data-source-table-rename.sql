-- ============================================================
-- v1.8 数据源表重命名：api_data_source → api_environment_data_source
--
-- 用途：环境内数据源归属环境语义与其它环境子表（api_environment_http /
--       api_environment_variable / api_environment_processor）命名对齐，
--       统一以 api_environment_* 前缀表达「环境级配置」。
-- 幂等：仅当旧表存在且新表不存在时执行重命名，可重复执行。
-- 说明：schema.sql 表名与注释已同步本次变更（随既有合并提交落地），
--       本脚本针对已建库环境的增量修正，老库直接执行即可。
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_tables
               WHERE schemaname = 'public' AND tablename = 'api_data_source')
       AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_tables
               WHERE schemaname = 'public' AND tablename = 'api_environment_data_source') THEN
        ALTER TABLE api_data_source RENAME TO api_environment_data_source;
    END IF;
END $$;