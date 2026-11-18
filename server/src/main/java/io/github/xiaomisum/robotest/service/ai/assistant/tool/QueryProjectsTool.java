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

/** 查询项目（详设 4.1）：仅返回头中活跃项目，供模型确认作用域内 projectId；跨项目指代不由此工具提供。 */
@Component
public class QueryProjectsTool implements AssistantTool {

    private static final int MAX = 50;

    @Resource
    private ProjectMapper projectMapper;

    @Override
    public AssistantToolDefinition definition() {
        return new AssistantToolDefinition(
                "query_projects",
                "查询当前项目（可按名称关键词过滤）以确认作用域内的 projectId，不得虚构 ID；"
                        + "助手只在当前项目内工作，无法切换到其他项目。",
                ToolSchemas.object(List.of(
                        ToolSchemas.string("keyword", "项目名称关键词，可空表示取当前项目")), null),
                ToolCategory.QUERY);
    }

    @Override
    public String execute(AssistantToolContext context, Map<String, Object> args) {
        String keyword = str(args.get("keyword")).trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> projects = new ArrayList<>();
        for (Project project : projectMapper.listByWorkspaceId(context.workspaceId())) {
            if (!context.projectId().equals(project.getId())) {
                continue;
            }
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
