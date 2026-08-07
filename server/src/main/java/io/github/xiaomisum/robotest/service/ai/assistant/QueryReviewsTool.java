package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.review.TestReviewListRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.service.project.TestReviewService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * query_reviews：空间内评审列表查询（详细设计 4.1）。
 *
 * <p>跨项目聚合逐项目调用 getReviewPage 并合并；status 为空时覆盖全部评审状态。</p>
 */
@Component
public class QueryReviewsTool extends AbstractQueryTool {

    private static final String TOOL_NAME = "query_reviews";

    @Resource
    private TestReviewService testReviewService;

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "查询当前工作空间内的测试评审列表。可按评审状态过滤；支持统计各评审的用例通过情况。",
                ToolSchema.object(List.of(
                        ToolSchema.string("status", "评审状态：new(进行中)/completed(已完成)，可空",
                                List.of(Constants.Status.NEW, Constants.Status.COMPLETED)),
                        ToolSchema.string("keyword", "评审标题关键词，可空")),
                        List.of()),
                true, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        String status = str(args, "status");
        String keyword = str(args, "keyword");

        List<Map<String, Object>> items = new ArrayList<>();
        for (Project project : listProjects(context.workspaceId())) {
            PageResult<TestReviewListRespDTO> page = testReviewService.getReviewPage(
                    project.getId(), context.userId(), status, keyword, 1, PER_PROJECT_LIMIT);
            for (TestReviewListRespDTO review : page.getList()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", review.getId());
                item.put("projectId", project.getId());
                item.put("projectName", project.getName());
                item.put("title", review.getTitle());
                item.put("status", review.getStatus());
                item.put("initiator", review.getInitiator() != null ? review.getInitiator().getName() : null);
                item.put("participantCount", review.getParticipantCount());
                item.put("progressPercent", review.getProgressPercent());
                item.put("passRate", review.getPassRate());
                item.put("routePath", "/workspace/projects/reviews/" + review.getId());
                items.add(item);
            }
        }
        return toResultJson(Map.of("count", items.size(), "reviews", items));
    }
}
