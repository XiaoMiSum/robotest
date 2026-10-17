package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import io.github.xiaomisum.robotest.service.domain.plan.TestPlanService;
import io.github.xiaomisum.robotest.service.domain.review.TestReviewService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 查询进度（详设 4.1）：评审 / 计划的执行进度统计直答数据源——进度类问题归只读问答
 * （需求 1.5），不再产生 intent kind。
 */
@Component
public class QueryProgressTool implements AssistantTool {

    @Resource
    private TestPlanService testPlanService;
    @Resource
    private TestReviewService testReviewService;
    @Resource
    private AssistantRefChecker refChecker;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "query_progress",
                "查询既有测试计划的执行进度或测试评审的评审进度统计（按对象 ID 直取）。"
                        + "用户询问「进度如何」类问题时调用，ID 只能取 query_plans / query_reviews 返回值。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("targetType", "目标类型", List.of(
                                Constants.EntityType.TEST_PLAN, Constants.EntityType.TEST_REVIEW)),
                        ToolSchemas.string("targetId", "目标对象 ID")), List.of("targetType", "targetId")),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String targetType = String.valueOf(args.get("targetType"));
        UUID targetId = uuid(args.get("targetId"));
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        refChecker.requireActiveWorkspaceRef(context.workspaceId(), targetType, targetId);
        UUID projectId = refChecker.projectIdOf(targetType, targetId);
        Object progress;
        if (Constants.EntityType.TEST_PLAN.equals(targetType)) {
            progress = testPlanService.getPlanProgress(projectId, targetId, context.userId());
        } else if (Constants.EntityType.TEST_REVIEW.equals(targetType)) {
            progress = testReviewService.getReviewProgress(projectId, targetId, context.userId());
        } else {
            throw new IllegalArgumentException("unsupported targetType: " + targetType);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetType", targetType);
        result.put("targetId", targetId.toString());
        result.put("progress", progress);
        return JsonUtils.toJsonString(result);
    }

    private static UUID uuid(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
