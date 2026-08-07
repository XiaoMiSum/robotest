package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseCaseListRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.service.project.TestCaseNodeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.pojo.PageResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * query_cases：空间内用例节点标题检索（详细设计 4.1）。
 *
 * <p>keyword 必填（防止全量倾倒）；projectId 可空（可空时按空间内全部项目聚合）。</p>
 */
@Component
public class QueryCasesTool extends AbstractQueryTool {

    private static final String TOOL_NAME = "query_cases";

    @Resource
    private TestCaseNodeService testCaseNodeService;

    @Override
    public AiToolDefinition definition() {
        return new AiToolDefinition(
                TOOL_NAME,
                "检索当前工作空间内的测试用例。按标题关键词匹配，返回命中的用例标题、所属文档与优先级。",
                ToolSchema.object(List.of(
                        ToolSchema.string("keyword", "用例标题关键词，必填"),
                        ToolSchema.string("projectId", "项目 ID，可空（空时按空间内全部项目检索）")),
                        List.of("keyword")),
                true, null);
    }

    @Override
    public String execute(AiToolContext context, Map<String, Object> args) {
        String keyword = str(args, "keyword");
        if (keyword == null || keyword.isBlank()) {
            return toResultJson(Map.of("error", "keyword 必填"));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        List<Project> projects = listProjects(context.workspaceId());
        for (Project project : projects) {
            PageResult<TestCaseCaseListRespDTO> page = testCaseNodeService.getCaseList(
                    project.getId(), context.userId(), keyword, null, 1, PER_PROJECT_LIMIT);
            for (TestCaseCaseListRespDTO caseItem : page.getList()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", caseItem.getId());
                item.put("projectId", project.getId());
                item.put("projectName", project.getName());
                item.put("title", caseItem.getTitle());
                item.put("priority", caseItem.getPriority());
                item.put("documentId", caseItem.getDocumentId());
                item.put("documentName", caseItem.getDocumentName());
                item.put("routePath", "/workspace/projects/functional-testing");
                items.add(item);
            }
        }
        return toResultJson(Map.of("count", items.size(), "cases", items));
    }
}
