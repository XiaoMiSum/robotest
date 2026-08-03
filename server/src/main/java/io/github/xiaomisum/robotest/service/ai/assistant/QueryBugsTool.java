package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugListRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.service.project.BugService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * query_bugs：空间内缺陷列表/计数查询（详细设计 4.1）。
 *
 * <p>countOnly 时只返回计数（SQL 精确 total），不返回明细；assigneeIsMe 以当前用户过滤处理人。</p>
 */
@Component
public class QueryBugsTool extends AbstractQueryTool {

    private static final String TOOL_NAME = "query_bugs";

    @Resource
    private BugService bugService;

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "查询当前工作空间内的缺陷列表或计数。可按状态、严重等级、处理人是否本人、关键词过滤；countOnly 时仅返回数量。",
                ToolSchema.object(List.of(
                        ToolSchema.string("status", "缺陷状态：active(激活)/resolved(已解决)/rejected(已拒绝)/closed(已关闭)，可空",
                                List.of(Constants.BugStatus.ACTIVE, Constants.BugStatus.RESOLVED,
                                        Constants.BugStatus.REJECTED, Constants.BugStatus.CLOSED)),
                        ToolSchema.string("severity", "严重等级：fatal/serious/general/minor，可空",
                                List.of(Constants.BugSeverity.FATAL, Constants.BugSeverity.SERIOUS,
                                        Constants.BugSeverity.GENERAL, Constants.BugSeverity.MINOR)),
                        ToolSchema.bool("assigneeIsMe", "仅查询指派给当前用户的缺陷，可空"),
                        ToolSchema.string("keyword", "标题关键词，可空"),
                        ToolSchema.bool("countOnly", "仅返回数量，可空")),
                        List.of()),
                true, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        String status = str(args, "status");
        String severity = str(args, "severity");
        String keyword = str(args, "keyword");
        boolean countOnly = Boolean.TRUE.equals(bool(args, "countOnly"));
        boolean assigneeIsMe = Boolean.TRUE.equals(bool(args, "assigneeIsMe"));
        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;

        for (Project project : listProjects(context.workspaceId())) {
            PageResult<BugListRespDTO> page = bugService.getBugPage(
                    project.getId(), status, severity, null, null,
                    assigneeIsMe ? context.userId() : null,
                    null, null, null, keyword, 1, PER_PROJECT_LIMIT);
            total += page.getTotal();
            if (countOnly) {
                continue;
            }
            for (BugListRespDTO bug : page.getList()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", bug.getId());
                item.put("projectId", project.getId());
                item.put("projectName", project.getName());
                item.put("title", bug.getTitle());
                item.put("status", bug.getStatus());
                item.put("severity", bug.getSeverity());
                item.put("priority", bug.getPriority());
                item.put("assignee", bug.getAssignee() != null ? bug.getAssignee().getName() : null);
                item.put("routePath", "/workspace/projects/bugs/" + bug.getId());
                items.add(item);
            }
        }
        if (countOnly) {
            return toResultJson(Map.of("count", total));
        }
        return toResultJson(Map.of("count", items.size(), "bugs", items));
    }
}
