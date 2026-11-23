package io.github.xiaomisum.robotest.service.ai.task;

import io.github.xiaomisum.robotest.framework.common.Constants;

import java.util.Map;

/**
 * 任务标题生成器（详设 3.6.1）：提交创建时按「类型描述 - 来源标题」拼装，执行不改写。
 * 类型描述与前端任务中心的类型中文名保持一致，来源由各类型适配器提供。
 */
public final class AiTaskTitleResolver {

    private static final String SEPARATOR = " - ";
    private static final int TITLE_MAX = 200;

    private static final Map<String, String> TYPE_LABELS = Map.of(
            Constants.AiTaskType.REQUIREMENT_IMPORT, "需求导入",
            Constants.AiTaskType.CASE_OPTIMIZE, "用例优化",
            Constants.AiTaskType.MINDMAP_CASE_GENERATION, "脑图用例生成",
            Constants.AiTaskType.PLAN_ORDER, "计划排序",
            Constants.AiTaskType.REVIEW_CASE_REVIEW, "AI 评审",
            Constants.AiTaskType.BUG_TRIAGE, "缺陷分诊",
            Constants.AiTaskType.ASSISTANT_PARSE, "助手解析",
            Constants.AiTaskType.VECTOR_REINDEX, "向量索引重建");

    private AiTaskTitleResolver() {
    }

    public static String resolve(String type, String sourceTitle) {
        String label = TYPE_LABELS.getOrDefault(type, type == null ? "" : type);
        String source = sourceTitle == null ? "" : sourceTitle.trim();
        String title = source.isEmpty() ? label : label + SEPARATOR + source;
        return fit(title, TITLE_MAX);
    }

    private static String fit(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }
}
