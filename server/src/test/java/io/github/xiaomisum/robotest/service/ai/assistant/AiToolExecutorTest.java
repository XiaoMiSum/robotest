package io.github.xiaomisum.robotest.service.ai.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiToolExecutorTest {

    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private AiTool tool;

    @InjectMocks
    private AiToolExecutor executor;

    private final AiToolContext context =
            new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);

    @Test
    void execute_returnsRegisteredToolResult() {
        when(toolRegistry.get("query_bugs")).thenReturn(tool);
        when(tool.execute(context, Map.of())).thenReturn("{\"rows\":[]}");

        String result = executor.execute(context, "query_bugs", Map.of());

        assertEquals("{\"rows\":[]}", result);
        verify(tool).execute(context, Map.of());
    }

    @Test
    void execute_unknownToolReturnsErrorJson() {
        when(toolRegistry.get("nope_tool")).thenReturn(null);

        String result = executor.execute(context, "nope_tool", Map.of());

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("未知工具: nope_tool"));
        verify(tool, never()).execute(any(), any());
    }

    @Test
    void execute_serviceExceptionConvertedToErrorJson() {
        when(toolRegistry.get("query_bugs")).thenReturn(tool);
        when(tool.execute(any(), any())).thenThrow(new ServiceException(1000003005, "会话不存在"));

        String result = executor.execute(context, "query_bugs", Map.of());

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("会话不存在"));
    }

    @Test
    void execute_genericExceptionConvertedToErrorJson() {
        when(toolRegistry.get("query_bugs")).thenReturn(tool);
        when(tool.execute(any(), any())).thenThrow(new IllegalStateException("boom"));

        String result = executor.execute(context, "query_bugs", Map.of());

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("工具执行异常: boom"));
    }
}
