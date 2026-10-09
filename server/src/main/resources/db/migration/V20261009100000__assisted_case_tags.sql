-- AI 辅助功能用例标签落库（辅助功能详设 2，批次三 WP-7.3）：
--   1) test_case_node 补 tags jsonb 列（数组），承接用例补全建议的 fields.tags 采纳写入；
--   2) 标签仅落库供对照面板与后续检索使用，不进脑图渲染、不进向量索引。
ALTER TABLE test_case_node ADD COLUMN tags jsonb NULL;
COMMENT ON COLUMN test_case_node.tags IS '用例标签（jsonb 数组），AI 补全建议采纳落库';
