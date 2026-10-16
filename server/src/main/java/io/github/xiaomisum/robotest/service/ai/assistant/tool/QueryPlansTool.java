package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.service.ai.assistant.AssistantRefChecker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 查询计划清单（详设 4.1）：按项目取近期计划并可按名称过滤，供调整类动作引用 planId。 */
@Component
public class QueryPlansTool implements AssistantTool {

    private static final int PER_PROJECT = 20;
    private static final int MAX = 50;

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private AssistantRefChecker refChecker;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "query_plans",
                "查询测试计划清单（planId / 名称 / 状态 / 所属项目，可按项目或名称关键词过滤）。"
                        + "调整计划、查看执行进度需要 planId 时调用，ID 只能取本工具返回值。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("projectId", "限定项目，可空"),
                        ToolSchemas.string("keyword", "计划名称关键词，可空")), null),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String keyword = str(args.get("keyword")).trim().toLowerCase(Locale.ROOT);
        UUID projectId = uuid(args.get("projectId"));
        if (projectId != null) {
            refChecker.requireActiveWorkspaceProject(context.workspaceId(), projectId);
        }
        List<Map<String, Object>> plans = new ArrayList<>();
        for (Project project : projectMapper.listByWorkspaceId(context.workspaceId())) {
            if (projectId != null && !project.getId().equals(projectId)) {
                continue;
            }
            for (TestPlan plan : testPlanMapper.findRecentPlans(project.getId(), PER_PROJECT)) {
                if (plans.size() >= MAX) {
                    return result(plans);
                }
                String name = plan.getName() == null ? "" : plan.getName();
                if (!keyword.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(keyword)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("planId", String.valueOf(plan.getId()));
                item.put("name", name);
                item.put("status", plan.getStatus());
                item.put("projectId", String.valueOf(project.getId()));
                item.put("projectName", project.getName());
                plans.add(item);
            }
        }
        return result(plans);
    }

    private static String result(List<Map<String, Object>> plans) {
        return JsonUtils.toJsonString(Map.of("count", plans.size(), "plans", plans));
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
