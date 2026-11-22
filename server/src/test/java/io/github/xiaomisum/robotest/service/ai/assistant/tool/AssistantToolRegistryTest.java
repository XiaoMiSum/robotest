package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 工具注册表单测（详设 4.1）：8 个固化定义（7 查询/检索 + 1 澄清）、
 * 类别归属与清单外拒绝口径（C8）。
 */
class AssistantToolRegistryTest {

    private final AssistantToolRegistry registry = new AssistantToolRegistry(List.of(
            new QueryProjectsTool(), new QueryDocTreeTool(), new QueryCasesTool(),
            new QueryReviewsTool(), new QueryPlansTool(), new QueryProgressTool(),
            new SearchKnowledgeTool(), new AskUserTool()));

    @Test
    void registers_all_definitions_and_excludes_unknown() {
        // 7 查询（含知识检索）+ 澄清 = 8 执行体（详设 4.1 固化清单）
        assertEquals(8, registry.all().size());
        assertNull(registry.definition("not_exist_tool"));
        assertNull(registry.definition(null));
        assertNull(registry.executable("not_exist_tool"));
    }

    @Test
    void executable_tools_carry_query_or_clarify_category() {
        for (String name : List.of("query_projects", "query_doc_tree", "query_cases",
                "query_reviews", "query_plans", "query_progress", "search_knowledge")) {
            AssistantToolDefinition definition = registry.definition(name);
            assertNotNull(definition);
            assertEquals(ToolCategory.QUERY, definition.category());
            assertNotNull(definition.paramsSchema());
            assertNotNull(registry.executable(name));
        }
        assertEquals(ToolCategory.CLARIFY, registry.definition("ask_user").category());
        assertNotNull(registry.executable("ask_user"));
    }
}
