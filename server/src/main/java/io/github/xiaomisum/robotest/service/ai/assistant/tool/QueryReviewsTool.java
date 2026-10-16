package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.model.entity.review.TestReview;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.review.TestReviewMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 查询评审清单（详设 4.1）：按项目取近期评审并可按名称过滤，供调整类动作引用 reviewId。 */
@Component
public class QueryReviewsTool implements AssistantTool {

    private static final int PER_PROJECT = 20;
    private static final int MAX = 50;

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TestReviewMapper testReviewMapper;
    @Resource
    private AssistantRefChecker refChecker;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "query_reviews",
                "查询评审清单（reviewId / 名称 / 状态 / 所属项目，可按项目或名称关键词过滤）。"
                        + "调整评审、查看评审进度需要 reviewId 时调用，ID 只能取本工具返回值。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("projectId", "限定项目，可空"),
                        ToolSchemas.string("keyword", "评审名称关键词，可空")), null),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String keyword = str(args.get("keyword")).trim().toLowerCase(Locale.ROOT);
        UUID projectId = uuid(args.get("projectId"));
        if (projectId != null) {
            refChecker.requireActiveWorkspaceProject(context.workspaceId(), projectId);
        }
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (Project project : projectMapper.listByWorkspaceId(context.workspaceId())) {
            if (projectId != null && !project.getId().equals(projectId)) {
                continue;
            }
            for (TestReview review : testReviewMapper.findRecentReviews(project.getId(), PER_PROJECT)) {
                if (reviews.size() >= MAX) {
                    return result(reviews);
                }
                String title = review.getTitle() == null ? "" : review.getTitle();
                if (!keyword.isEmpty() && !title.toLowerCase(Locale.ROOT).contains(keyword)) {
                    continue;
                }
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("reviewId", String.valueOf(review.getId()));
                item.put("title", title);
                item.put("status", review.getStatus());
                item.put("projectId", String.valueOf(project.getId()));
                item.put("projectName", project.getName());
                reviews.add(item);
            }
        }
        return result(reviews);
    }

    private static String result(List<Map<String, Object>> reviews) {
        return JsonUtils.toJsonString(Map.of("count", reviews.size(), "reviews", reviews));
    }

    private static String str(Object value) {
        return value instanceof String text ? text : "";
    }

    private static UUID uuid(Object value) {
        String text = str(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
