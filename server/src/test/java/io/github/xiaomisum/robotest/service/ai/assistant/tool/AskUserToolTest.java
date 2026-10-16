package io.github.xiaomisum.robotest.service.ai.assistant.tool;

import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 澄清工具入参校验单测（详设 4.1 ask_user：question 必填、options ≤5 条且清洗）（C8）。 */
class AskUserToolTest {

    private final AskUserTool tool = new AskUserTool();

    @Test
    void valid_question_returns_echo_json() {
        String result = tool.execute(context(), Map.of(
                "question", "请问在哪个项目下创建？",
                "options", List.of("当前项目：商城", "会话项目：门店")));
        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals("请问在哪个项目下创建？", parsed.get("question"));
        assertEquals(2, ((List<?>) parsed.get("options")).size());
    }

    @Test
    void missing_question_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(context(), Map.of("options", List.of("a"))));
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(context(), Map.of("question", "   ")));
    }

    @Test
    void more_than_five_options_rejected_and_blank_skipped() {
        assertThrows(IllegalArgumentException.class, () -> tool.execute(context(), Map.of(
                "question", "q",
                "options", List.of("1", "2", "3", "4", "5", "6"))));
        String result = tool.execute(context(), Map.of(
                "question", "q",
                "options", List.of("a", "", "  ")));
        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        assertEquals(List.of("a"), parsed.get("options"));
    }

    @Test
    void overlong_option_truncated_to_200() {
        String result = tool.execute(context(), Map.of(
                "question", "q",
                "options", List.of("x".repeat(260))));
        Map<String, Object> parsed = JsonUtils.parseObject(result, Map.class);
        String option = (String) ((List<?>) parsed.get("options")).get(0);
        assertEquals(200, option.length());
        assertTrue(option.startsWith("xxx"));
    }

    private static AssistantToolContext context() {
        return new AssistantToolContext(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), List.of());
    }
}
