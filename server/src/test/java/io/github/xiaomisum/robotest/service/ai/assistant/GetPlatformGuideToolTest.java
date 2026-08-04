package io.github.xiaomisum.robotest.service.ai.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPlatformGuideToolTest {

    @Mock
    private GuideKnowledgeBase guideKnowledgeBase;

    @InjectMocks
    private GetPlatformGuideTool tool;

    private final AiToolContext context =
            new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);

    @Test
    void definition_returnsNameReadOnlyAndTopicSchema() {
        AiToolDefinition def = tool.definition();

        assertEquals("get_platform_guide", def.name());
        assertTrue(def.readOnly());
        Map<String, Object> properties = (Map<String, Object>) def.paramsSchema().get("properties");
        assertTrue(properties.containsKey("topic"));
        assertEquals("string", ((Map<?, ?>) properties.get("topic")).get("type"));
    }

    @Test
    void execute_returnsFragmentsWhenMatchesFound() {
        GuideKnowledgeBase.GuideFragment f1 = new GuideKnowledgeBase.GuideFragment(
                List.of("评审"), "/reviews", List.of(), "评审流程说明");
        GuideKnowledgeBase.GuideFragment f2 = new GuideKnowledgeBase.GuideFragment(
                List.of("计划"), "/plans", List.of(), "计划创建说明");
        when(guideKnowledgeBase.search(context.userId(), context.workspaceId(), "如何发起评审"))
                .thenReturn(List.of(f1, f2));

        String result = tool.execute(context, Map.of("topic", "如何发起评审"));

        assertTrue(result.contains("\"count\":2"));
        assertTrue(result.contains("评审流程说明"));
        assertTrue(result.contains("/reviews"));
    }

    @Test
    void execute_returnsEmptyWhenTopicBlank() {
        String result = tool.execute(context, Map.of("topic", "   "));

        assertEquals("{}", result);
        verify(guideKnowledgeBase, never()).search(any(), any(), any());
    }

    @Test
    void execute_returnsEmptyWhenTopicMissing() {
        String result = tool.execute(context, Map.of());

        assertEquals("{}", result);
        verify(guideKnowledgeBase, never()).search(any(), any(), any());
    }

    @Test
    void execute_returnsEmptyWhenTopicIsNotString() {
        String result = tool.execute(context, Map.of("topic", 42));

        assertEquals("{}", result);
        verify(guideKnowledgeBase, never()).search(any(), any(), any());
    }

    @Test
    void execute_trimsTopicBeforeSearch() {
        when(guideKnowledgeBase.search(context.userId(), context.workspaceId(), "评审"))
                .thenReturn(List.of());

        String result = tool.execute(context, Map.of("topic", "  评审  "));

        assertEquals("{}", result);
        verify(guideKnowledgeBase).search(context.userId(), context.workspaceId(), "评审");
    }

    @Test
    void execute_returnsEmptyWhenNoMatches() {
        when(guideKnowledgeBase.search(any(), any(), any())).thenReturn(List.of());

        String result = tool.execute(context, Map.of("topic", "不存在的主题"));

        assertEquals("{}", result);
    }
}
