package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具注册表单测（详设 4.1）：16 个固化定义（执行体 + 变更 definition-only）、
 * 类别归属与清单外拒绝口径（C8）。
 */
class AssistantToolRegistryTest {

    private final AssistantToolRegistry registry = new AssistantToolRegistry(List.of(
            new QueryProjectsTool(), new QueryDocTreeTool(), new QueryCasesTool(),
            new QueryReviewsTool(), new QueryPlansTool(), new QueryProgressTool(),
            new AskUserTool()));

    @Test
    void registers_all_definitions_and_excludes_unknown() {
        // 7 个执行体（6 查询 + 澄清）+ 8 个变更 definition = 15；search_knowledge 随 S5 增至 16
        assertEquals(15, registry.all().size());
        assertNull(registry.definition("not_exist_tool"));
        assertNull(registry.definition(null));
        assertNull(registry.executable("not_exist_tool"));
    }

    @Test
    void executable_tools_carry_query_or_clarify_category() {
        for (String name : List.of("query_projects", "query_doc_tree", "query_cases",
                "query_reviews", "query_plans", "query_progress")) {
            AssistantToolDefinition definition = registry.definition(name);
            assertNotNull(definition);
            assertEquals(ToolCategory.QUERY, definition.category());
            assertNotNull(definition.paramsSchema());
            assertNotNull(registry.executable(name));
        }
        assertEquals(ToolCategory.CLARIFY, registry.definition("ask_user").category());
        assertNotNull(registry.executable("ask_user"));
    }

    @Test
    void change_kinds_are_definition_only_with_eight_kinds() {
        assertEquals(8, AssistantToolRegistry.CHANGE_KINDS.size());
        assertTrue(AssistantToolRegistry.CHANGE_KINDS.stream()
                .noneMatch(kind -> kind.startsWith("view_")));
        for (String kind : AssistantToolRegistry.CHANGE_KINDS) {
            AssistantToolDefinition definition = registry.definition(kind);
            assertNotNull(definition);
            assertEquals(ToolCategory.CHANGE, definition.category());
            // 变更类无执行体：调用即拦截规范化（详设 4.1 零落库）
            assertNull(registry.executable(kind));
        }
    }
}
