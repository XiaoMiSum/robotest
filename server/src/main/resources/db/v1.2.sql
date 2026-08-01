-- Robotest V1.2 数据库增量 DDL
-- PostgreSQL
-- V1.2 修复 audit_log 主键类型（BIGINT → UUID）：应用全局 id-type=assign_uuid，
--     此前审计写入 UUID 进 bigint 列必报错，导致含 @AuditOperation 的事务被静默回滚
-- 依据：《AI 基础设施详细设计说明书 V1.1》框架审计；v1.sql 建表修正见 v1.sql L82

-- audit_log 当前无历史数据（此前全部写入随事务回滚），直接改列类型，无需数据转换
ALTER TABLE audit_log ALTER COLUMN id TYPE uuid USING id::text::uuid;
