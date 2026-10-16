-- =============================================================================
-- V20261016140000__ai_assistant_message_images.sql — 助手消息附图
-- 依据：docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md（2.9）
--       docs/04-detailed-design/07-ai-capability/04-ai-assistant.md（2、3.5）
--
-- 1. 变更目的与影响范围
--    ai_assistant_message 增加 images（jsonb NULL）：用户消息的附图文件引用 [{fileId, name}]，
--    仅存引用不存字节；附图非空要求视觉能力模型（vision），解析时读回字节随本轮模型调用携带。
--    影响：新列可空，旧代码读写不感知，不阻塞部署顺序。
-- 2. 正向 DDL 与数据回填
--    无数据回填：存量行为空（NULL 语义等同无附图）；列可空，无需清理既有数据。
-- 3. 回滚方案
--    ALTER TABLE ai_assistant_message DROP COLUMN images;
--    （列值为文件引用标识，删除即回退；图片自身在文件模块，不受影响）
-- 4. 兼容窗口与部署顺序
--    先执行本脚本，再部署写入 images 的服务端版本；回滚时先回退服务端版本。
-- 5. 大表锁影响评估
--    ADD COLUMN（无默认值）为元数据级操作（PG11+ 不重写表），瞬时完成。
-- 6. 索引 / 逻辑删除 / 租户隔离检查
--    单表索引 1 个 ≤ 5（C9），不新增索引（附图随消息行读写，无独立查询路径）；
--    id/created_at/updated_at/is_deleted 齐备（C5）；fileId 为逻辑外键指向平台级文件资源。
--
-- 本地执行：docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

ALTER TABLE ai_assistant_message ADD COLUMN images jsonb NULL;

COMMENT ON COLUMN ai_assistant_message.images
    IS '用户随消息上传的图片文件引用集合 [{fileId,name}]（≤3 张，仅存引用不存字节）';

COMMIT;
