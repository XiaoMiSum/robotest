package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 查询项目（详设 4.1）：活跃工作空间内可见项目，按名称关键词过滤；供模型补全 / 校验 projectId。 */
@Component
public class QueryProjectsTool implements AssistantTool {

    private static final int MAX = 50;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "query_projects",
                "查询当前工作空间内可见的项目清单（可按名称关键词过滤）。当需要项目 ID（projectId）"
                        + "而无法从上下文确定、或需要用户在多个项目间选择时先调用本工具，不得虚构 ID。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("keyword", "项目名称关键词，可空表示取全部")), null),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String keyword = str(args.get("keyword")).trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> projects = new ArrayList<>();
        for (Project project : projectMapper.listByWorkspaceId(context.workspaceId())) {
            if (!keyword.isEmpty()
                    && (project.getName() == null
                    || !project.getName().toLowerCase(Locale.ROOT).contains(keyword))) {
                continue;
            }
            if (projects.size() >= MAX) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", String.valueOf(project.getId()));
            item.put("name", project.getName());
            projects.add(item);
        }
        return JsonUtils.toJsonString(Map.of("count", projects.size(), "projects", projects));
    }

    private static String str(Object value) {
        return value instanceof String text ? text : "";
    }
}
