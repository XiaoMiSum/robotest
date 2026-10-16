-- =============================================================================
-- V20261016130000__ai_assistant_conversation_workspace.sql — 助手会话归属空间
-- 依据：docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md（2.9）
--       docs/04-detailed-design/07-ai-capability/04-ai-assistant.md（2、3.1~3.3、4.4）
--
-- 1. 变更目的与影响范围
--    ai_assistant_conversation 增加 workspace_id（NOT NULL，创建时取 X-Active-Workspace）：
--    会话列表按当前活跃空间过滤，发送侧校验归属一致（1000018263）；隔离与可见性仍只看 user_id。
--    索引由 (user_id, created_at) 改为 (user_id, workspace_id, created_at) 以覆盖列表过滤 + 排序。
--    影响：执行后旧代码（不写该列的 INSERT）将失败，需与服务端同窗口部署。
-- 2. 正向 DDL 与数据回填
--    无数据回填（用户确认不存在存量数据）；如临时环境存在会话行，执行前需自行清理，
--    否则 NOT NULL 约束将拒绝执行。
-- 3. 回滚方案
--    DROP INDEX idx_ai_assistant_conversation_user_workspace;
--    CREATE INDEX idx_ai_assistant_conversation_user ON ai_assistant_conversation (user_id, created_at);
--    ALTER TABLE ai_assistant_conversation DROP COLUMN workspace_id;
--    （列值为归属空间标识，删除即回退，不影响业务数据）
-- 4. 兼容窗口与部署顺序
--    先执行本脚本，再部署写入 workspace_id 的服务端版本；回滚时先回退服务端版本。
-- 5. 大表锁影响评估
--    ADD COLUMN（无默认值）与 DROP/CREATE INDEX 均为元数据级操作（PG11+ 不重写表），瞬时完成。
-- 6. 索引 / 逻辑删除 / 租户隔离检查
--    单表索引 1 个 ≤ 5（C9）；id/created_at/updated_at/is_deleted 齐备（C5）；
--    workspace_id 为逻辑外键，隔离展示用，权限判定仍只看 user_id（详设 4.4）。
--
-- 本地执行：docker exec -i pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f - < 本文件
-- =============================================================================

BEGIN;

ALTER TABLE ai_assistant_conversation ADD COLUMN workspace_id uuid NOT NULL;

DROP INDEX IF EXISTS idx_ai_assistant_conversation_user;
CREATE INDEX idx_ai_assistant_conversation_user_workspace
    ON ai_assistant_conversation (user_id, workspace_id, created_at);

COMMENT ON COLUMN ai_assistant_conversation.workspace_id
    IS '归属工作空间（创建时固化）：列表按当前活跃空间过滤，不参与权限判定';

COMMIT;
