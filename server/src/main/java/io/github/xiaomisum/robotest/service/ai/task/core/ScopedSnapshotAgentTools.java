package io.github.xiaomisum.robotest.service.ai.task.core;

import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 普通任务只读工具集合。首版工具只回读提交时固化的可信资源集合与作用域；模型参数只能缩小集合，
 * 不能借工具扩大到任务输入之外。具体正文由适配器准备快照提供，避免工具产生业务写入。
 */
public final class ScopedSnapshotAgentTools {

    private static final int RESULT_CHARS = 20000;
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("resourceIds", Map.of(
                    "type", "array", "maxItems", 500,
                    "items", Map.of("type", "string"))));

    private ScopedSnapshotAgentTools() {
    }

    /** 13 个注册工具的完整清单（Spring 同样按 @Component 收集，供一致性核对使用）。 */
    public static List<AgentTool> all() {
        return List.of(
                new ReadImportSource(), new QueryProjectModules(), new QueryRequirements(),
                new SearchProjectKnowledge(), new QueryTestAssets(), new QuerySelectionCandidates(),
                new QueryCaseContext(), new QueryBugHotspots(), new QueryPlanOrderContext(),
                new QueryBugs(), new SearchSimilarBugs(), new QueryTriageBugs(),
                new QueryBugAnalysisSnapshot());
    }

    private abstract static class BaseTool implements AgentTool {
        private final AgentToolDefinition definition;

        BaseTool(String code, String name) {
            definition = new AgentToolDefinition(code, name,
                    "读取任务提交时固化的可信资源范围；resourceIds 可选且只能是输入资源集合的子集",
                    SCHEMA, null, "readonly");
        }

        @Override
        public AgentToolDefinition definition() {
            return definition;
        }

        @Override
        public String execute(AgentToolContext context, Map<String, Object> args) {
            Set<String> trusted = new LinkedHashSet<>();
            collectIds(context.inputSnapshot(), trusted);
            List<String> requested = strings(args.get("resourceIds"));
            if (!trusted.containsAll(requested)) {
                return JsonUtils.toJsonString(Map.of("error", "请求资源超出任务可信范围"));
            }
            Map<String, Object> result = Map.of(
                    "taskId", context.taskId(),
                    "taskType", context.taskType(),
                    "workspaceId", context.workspaceId() == null ? "" : context.workspaceId(),
                    "projectId", context.projectId() == null ? "" : context.projectId(),
                    "resourceIds", requested.isEmpty() ? trusted : requested,
                    "input", context.inputSnapshot());
            String json = JsonUtils.toJsonString(result);
            return json.length() <= RESULT_CHARS ? json : json.substring(0, RESULT_CHARS);
        }

        private static void collectIds(Object value, Set<String> result) {
            if (value instanceof Map<?, ?> map) {
                map.values().forEach(item -> collectIds(item, result));
            } else if (value instanceof List<?> list) {
                list.forEach(item -> collectIds(item, result));
            } else if (value != null) {
                String text = String.valueOf(value);
                if (text.matches("[0-9a-fA-F-]{36}")) {
                    result.add(text);
                }
            }
        }

        private static List<String> strings(Object value) {
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            list.forEach(item -> result.add(String.valueOf(item)));
            return result;
        }
    }

    @Component static final class ReadImportSource extends BaseTool { ReadImportSource() { super("read_import_source", "读取导入来源"); } }
    @Component static final class QueryProjectModules extends BaseTool { QueryProjectModules() { super("query_project_modules", "查询项目模块"); } }
    @Component static final class QueryRequirements extends BaseTool { QueryRequirements() { super("query_requirements", "查询需求"); } }
    @Component static final class SearchProjectKnowledge extends BaseTool { SearchProjectKnowledge() { super("search_project_knowledge", "检索项目知识"); } }
    @Component static final class QueryTestAssets extends BaseTool { QueryTestAssets() { super("query_test_assets", "查询测试资产"); } }
    @Component static final class QuerySelectionCandidates extends BaseTool { QuerySelectionCandidates() { super("query_selection_candidates", "查询圈选候选"); } }
    @Component static final class QueryCaseContext extends BaseTool { QueryCaseContext() { super("query_case_context", "查询用例上下文"); } }
    @Component static final class QueryBugHotspots extends BaseTool { QueryBugHotspots() { super("query_bug_hotspots", "查询缺陷热点"); } }
    @Component static final class QueryPlanOrderContext extends BaseTool { QueryPlanOrderContext() { super("query_plan_order_context", "查询计划顺序上下文"); } }
    @Component static final class QueryBugs extends BaseTool { QueryBugs() { super("query_bugs", "查询缺陷"); } }
    @Component static final class SearchSimilarBugs extends BaseTool { SearchSimilarBugs() { super("search_similar_bugs", "检索相似缺陷"); } }
    @Component static final class QueryTriageBugs extends BaseTool { QueryTriageBugs() { super("query_triage_bugs", "查询分诊缺陷"); } }
    @Component static final class QueryBugAnalysisSnapshot extends BaseTool { QueryBugAnalysisSnapshot() { super("query_bug_analysis_snapshot", "查询缺陷分析快照"); } }
}
