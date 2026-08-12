package io.github.xiaomisum.robotest.framework.common;

import java.util.Arrays;

/**
 * AI 功能类型枚举 —— 贯穿智能体模板、调用审计、限流类别的统一枚举。
 *
 * <p>见《AI 基础设施详细设计说明书》2.3：bug_dedup / embedding_index 仅调用 Embedding 接口，
 * 无提示词模板；embedding_index 为系统内部调用，不限流。</p>
 */
public enum AiFunctionType {

    CASE_GENERATION("case_generation", "用例子树生成", RateLimitCategory.GENERATION, true),
    STEP_COMPLETION("step_completion", "用例步骤补全", RateLimitCategory.GENERATION, true),
    TEXT_IMPORT("text_import", "外部文本导入解析", RateLimitCategory.GENERATION, true),
    REVIEW_SUMMARY("review_summary", "评审摘要生成", RateLimitCategory.GENERATION, true),
    ASSISTANT_CHAT("assistant_chat", "全局助手对话", RateLimitCategory.ASSISTANT, true),
    PRIORITY_RECOMMENDATION("priority_recommendation", "优先级推荐", RateLimitCategory.SUGGESTION, true),
    BUG_FORM_SUGGESTION("bug_form_suggestion", "缺陷标题优化与等级建议", RateLimitCategory.SUGGESTION, true),
    DSL_TRANSLATION("dsl_translation", "脑图指令翻译", RateLimitCategory.SUGGESTION, true),
    PLAN_ORDER_REASON("plan_order_reason", "执行顺序推荐理由", RateLimitCategory.SUGGESTION, true),
    MISSING_POINT_ANALYSIS("missing_point_analysis", "遗漏测试点分析", RateLimitCategory.RETRIEVAL, true),
    KEYWORD_EXTRACTION("keyword_extraction", "需求关键词抽取", RateLimitCategory.RETRIEVAL, true),
    CASE_PLAN_RECOMMENDATION("case_plan_recommendation", "用例规划推荐", RateLimitCategory.RETRIEVAL, true),
    BUG_DEDUP("bug_dedup", "缺陷语义查重", RateLimitCategory.RETRIEVAL, false),
    REVIEW_CHECK("review_check", "评审完整性检查", RateLimitCategory.TASK, true),
    BUG_CLUSTERING("bug_clustering", "缺陷聚类归纳", RateLimitCategory.TASK, true),
    EMBEDDING_INDEX("embedding_index", "向量写入与重建", null, false);

    private final String code;
    private final String label;
    private final RateLimitCategory rateLimitCategory;
    private final boolean hasTemplate;

    AiFunctionType(String code, String label, RateLimitCategory rateLimitCategory, boolean hasTemplate) {
        this.code = code;
        this.label = label;
        this.rateLimitCategory = rateLimitCategory;
        this.hasTemplate = hasTemplate;
    }

    public static AiFunctionType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 限流类别，为空表示不限流（系统内部调用）。
     */
    public RateLimitCategory getRateLimitCategory() {
        return rateLimitCategory;
    }

    public boolean hasTemplate() {
        return hasTemplate;
    }

    /**
     * 限流类别 —— 阈值取 ai_config.settings 的 rateLimit.* 键。
     */
    public enum RateLimitCategory {

        GENERATION("rateLimit.generation", 20),
        SUGGESTION("rateLimit.suggestion", 60),
        RETRIEVAL("rateLimit.retrieval", 120),
        TASK("rateLimit.task", 10),
        ASSISTANT("rateLimit.assistant", 60);

        private final String settingsKey;
        private final int defaultLimit;

        RateLimitCategory(String settingsKey, int defaultLimit) {
            this.settingsKey = settingsKey;
            this.defaultLimit = defaultLimit;
        }

        public String getSettingsKey() {
            return settingsKey;
        }

        public int getDefaultLimit() {
            return defaultLimit;
        }
    }
}
