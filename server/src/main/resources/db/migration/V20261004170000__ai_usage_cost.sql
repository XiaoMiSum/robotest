-- AI 用量成本口径（详设 02-ai-infra-overview 2.3 / 2.8，批次二 WP-4.1）：
--   1) ai_model_config 补每百万 token 单价两列（0 = 不计价）；
--   2) ai_usage_log 补 cost 成本列（写入时按调用模型当时单价计，4.3）；
--   3) 勘误：model_id 允许 NULL——向量 API 调用按 4.3 记账但无模型配置行；
--   4) 存量状态口径对齐详设 2.8（success/failed，批次一实现误写 succeeded）。
ALTER TABLE ai_model_config ADD COLUMN input_price numeric(14,6) NOT NULL DEFAULT 0;
ALTER TABLE ai_model_config ADD COLUMN output_price numeric(14,6) NOT NULL DEFAULT 0;

ALTER TABLE ai_usage_log ADD COLUMN cost numeric(14,6) NOT NULL DEFAULT 0;
ALTER TABLE ai_usage_log ALTER COLUMN model_id DROP NOT NULL;
ALTER TABLE ai_prompt_template ADD COLUMN updated_by uuid NULL;
UPDATE ai_usage_log SET status = 'success' WHERE status = 'succeeded' AND is_deleted = FALSE;
