-- ============================================================
-- Robotest AI 能力验收种子数据（幂等，可重复执行）
-- 目标库：docker pgvector 容器中的 robotest 库
--
-- 使用：
--   docker cp server/src/main/resources/db/seed-ai.sql pgvector:/tmp/
--   docker exec pgvector psql -U postgres -d robotest -v ON_ERROR_STOP=1 -f /tmp/seed-ai.sql
--
-- 内容（详设 docs/04-detailed-design/07-ai-capability/02-ai-infra-overview.md）：
--   1. ai_config 单例行：强制回置验收状态（enabled=true、默认模型指向本种子模型、超时 600s、自动重试 2 次）
--   2. ai_model_config：OpenAI 兼容默认模型（ON CONFLICT DO NOTHING，不覆盖已手工调整的行）
--
-- UUID 约定：本脚本 id 均以 `a1f0` 开头（区别于 seed-selftest 的 7e57 段），
--           清理段用 `id::text LIKE 'a1f0%'` 精确圈定。
--
--   a1f00001 ai_config    a1f00002 ai_model_config
--
-- API 密钥说明（详设 4.1）：
--   api_key_encrypted 为 AES-256-GCM 密文（SecretCryptoUtil，密钥 = dev ENV_SECRET_KEY），
--   默认值是占位密钥 sk-replace-with-your-key 的密文。占位密钥可完成入口显隐、任务
--   提交 / 轮询 / 取消 / 重试与产物确认验收；真实模型调用将失败并回执 1000018117。
--   接入真实 LLM 前，先在本地生成密文并更新一行（密钥与 base_url 按实际供给替换）：
--     byte[] key = SecretCryptoUtil.parseKey("<ENV_SECRET_KEY>");
--     String cipher = SecretCryptoUtil.encrypt(key, "<真实 API Key>");
--     UPDATE ai_model_config SET base_url = '<兼容端点>/v1', model_name = '<模型名>',
--            api_key_encrypted = '<密文>' WHERE id = 'a1f00002-0000-4000-8000-000000000001';
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- 模型行（先于配置，保证 default_model_id 引用存在）
INSERT INTO ai_model_config (id, name, provider, base_url, api_key_encrypted, model_name,
                             capabilities, priority, enabled, created_at, updated_at, is_deleted)
VALUES ('a1f00002-0000-4000-8000-000000000001',
        'OpenAI 兼容（占位）',
        'openai-compatible',
        'https://api.openai.com/v1',
        'B1doDlUwZLXI8DQqTGHydP3MFrEm5VzgkV0Ho4GEZHXgQOHhEtiz2qASIwkNaVTLakCYTg==',
        'gpt-4o-mini',
        '["chat"]'::jsonb,
        100,
        TRUE,
        now(), now(), FALSE)
ON CONFLICT (id) DO NOTHING;

-- 单例行：强制回置验收状态（存在则更新，缺失则补插）
UPDATE ai_config
SET enabled           = TRUE,
    default_model_id  = 'a1f00002-0000-4000-8000-000000000001',
    task_timeout_seconds = 600,
    task_max_retries  = 2,
    updated_at        = now()
WHERE is_deleted = FALSE;

INSERT INTO ai_config (id, enabled, default_model_id, task_timeout_seconds, task_max_retries,
                       created_at, updated_at, is_deleted)
SELECT 'a1f00001-0000-4000-8000-000000000001',
       TRUE,
       'a1f00002-0000-4000-8000-000000000001',
       600,
       2,
       now(), now(), FALSE
WHERE NOT EXISTS (SELECT 1 FROM ai_config WHERE is_deleted = FALSE);

COMMIT;
