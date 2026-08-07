package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.plan.TestPlanListRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.service.project.TestPlanService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * query_plans：空间内测试计划列表查询（详细设计 4.1）。
 *
 * <p>跨项目聚合逐项目调用 getPlanPage 并合并；status 为空时覆盖全部计划状态。</p>
 */
@Component
public class QueryPlansTool extends AbstractQueryTool {

    private static final String TOOL_NAME = "query_plans";

    @Resource
    private TestPlanService testPlanService;

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "查询当前工作空间内的测试计划列表。可按计划状态过滤；支持统计各计划用例执行情况。",
                ToolSchema.object(List.of(
                        ToolSchema.string("status", "计划状态：new(未开始)/in_progress(进行中)/completed(已完成)/closed(已关闭)，可空",
                                List.of(Constants.Status.NEW, Constants.Status.IN_PROGRESS,
                                        Constants.Status.COMPLETED, Constants.Status.CLOSED)),
                        ToolSchema.string("keyword", "计划名称关键词，可空")),
                        List.of()),
                true, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        String status = str(args, "status");
        String keyword = str(args, "keyword");

        List<Map<String, Object>> items = new ArrayList<>();
        for (Project project : listProjects(context.workspaceId())) {
            PageResult<TestPlanListRespDTO> page = testPlanService.getPlanPage(
                    project.getId(), context.userId(), status, keyword, 1, PER_PROJECT_LIMIT);
            for (TestPlanListRespDTO plan : page.getList()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", plan.getId());
                item.put("projectId", project.getId());
                item.put("projectName", project.getName());
                item.put("name", plan.getName());
                item.put("status", plan.getStatus());
                item.put("executor", plan.getExecutor() != null ? plan.getExecutor().getName() : null);
                item.put("environment", plan.getEnvironment());
                item.put("progressPercent", plan.getProgressPercent());
                item.put("passRate", plan.getPassRate());
                item.put("routePath", "/workspace/projects/plans/" + plan.getId());
                items.add(item);
            }
        }
        return toResultJson(Map.of("count", items.size(), "plans", items));
    }
}
