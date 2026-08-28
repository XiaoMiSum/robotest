-- ============================================================
-- v1.9 环境 HTTP 配置字段精简：删除默认方法/超时/重定向/SSL/默认配置
--
-- 用途：HTTP 配置收敛为「名称 + 引用名 + Base URL + 默认请求头」，
--       超时/重定向等归入执行面固定默认值，不再逐配置维护；
--       同时移除「每环境唯一默认 HTTP 配置」概念（默认环境只在环境级设置）。
-- 幂等：DROP COLUMN IF EXISTS，可重复执行。
-- 说明：schema.sql 表结构与注释已同步本次变更（随既有合并提交落地），
--       本脚本针对已建库环境的增量修正，老库直接执行即可。
-- ============================================================

ALTER TABLE api_environment_http DROP COLUMN IF EXISTS default_method;
ALTER TABLE api_environment_http DROP COLUMN IF EXISTS timeout_ms;
ALTER TABLE api_environment_http DROP COLUMN IF EXISTS connect_timeout_ms;
ALTER TABLE api_environment_http DROP COLUMN IF EXISTS follow_redirects;
ALTER TABLE api_environment_http DROP COLUMN IF EXISTS verify_ssl;
ALTER TABLE api_environment_http DROP COLUMN IF EXISTS is_default;