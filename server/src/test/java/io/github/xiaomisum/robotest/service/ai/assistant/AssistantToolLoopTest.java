package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantIntentBuilder;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantTool;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolContext;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolDefinition;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.AssistantToolRegistry;
import io.github.xiaomisum.robotest.service.ai.assistant.tool.ToolCategory;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具调用循环单测（详设 4.1）：轮次上限与固定文案、查询回填与进度帧、澄清校验与错误回填、
 * 变更规范化终止、清单外拒绝与参数非法降级（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantToolLoopTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WS_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private static final AssistantToolDefinition QUERY_DEF = new AssistantToolDefinition(
            "query_projects", "查询项目", Map.of("type", "object"), ToolCategory.QUERY);
    private static final AssistantToolDefinition ASK_DEF = new AssistantToolDefinition(
            "ask_user", "澄清", Map.of("type", "object"), ToolCategory.CLARIFY);
    private static final AssistantToolDefinition CREATE_DEF = new AssistantToolDefinition(
            "create_case", "创建用例", Map.of("type", "object"), ToolCategory.CHANGE);

    @Mock
    private AssistantToolRegistry registry;
    @Mock
    private AssistantIntentBuilder intentBuilder;
    @Mock
    private AssistantService assistantService;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private AssistantTool queryTool;
    @Mock
    private AssistantTool askTool;

    @InjectMocks
    private AssistantToolLoop loop;

    @Test
    void textOnlyHop_returnsAnswerWithTokens() {
        stubRegistry();
        when(modelClient.chatWithTools(any(), anyList(), anyList()))
                .thenReturn(new AiChatReply("最终回答", 10, 2));

        AssistantToolLoop.LoopOutcome outcome = loop.run(request(), modelClient);

        assertEquals("answer", outcome.kind());
        assertEquals("最终回答", outcome.answer());
        assertEquals(10, outcome.tokensIn());
        assertEquals(2, outcome.tokensOut());
        assertTrue(outcome.citations().isEmpty());
        verify(assistantService, never()).appendToolMessage(any(), anyString(), anyString());
    }

    @Test
    void blankFinalText_throwsParseFailed() {
        stubRegistry();
        when(modelClient.chatWithTools(any(), anyList(), anyList()))
                .thenReturn(new AiChatReply("   ", 1, 1));

        ServiceException error = assertThrows(ServiceException.class,
                () -> loop.run(request(), modelClient));

        assertEquals(ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code(), error.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryHop_appendsToolMessageEmitsFrameAndFeedsBack() {
        stubRegistry();
        when(queryTool.execute(any(AssistantToolContext.class), anyMap()))
                .thenReturn("{\"count\":0,\"projects\":[]}");
        when(modelClient.chatWithTools(any(), anyList(), anyList()))
                .thenReturn(new AiChatReply("先查一下", 5, 3, List.of(new AiToolCall("tc-1", "query_projects", "{}"))))
                .thenReturn(new AiChatReply("共 0 个项目。", 7, 4));

        AssistantToolLoop.LoopOutcome outcome = loop.run(request(), modelClient);

        assertEquals("answer", outcome.kind());
        verify(assistantService).appendToolMessage(CONVERSATION_ID, "tc-1", "{\"count\":0,\"projects\":[]}");
        // 回填轮次：assistant(tool_calls) + tool 结果成对入列（OpenAI 协议）
        ArgumentCaptor<List<Message>> turns = ArgumentCaptor.forClass(List.class);
        verify(modelClient, times(2)).chatWithTools(any(), anyList(), turns.capture());
        assertEquals(2, turns.getAllValues().get(1).size());
        // 调用载荷随终态回传（审计轨迹）与逐跳 token 累计
        assertEquals(1, outcome.toolCalls().size());
        assertEquals(12, outcome.tokensIn());
        assertEquals(7, outcome.tokensOut());
    }

    @Test
    void clarifyHop_validArgs_returnsClarifyOutcome() {
        stubRegistry();
        when(modelClient.chatWithTools(any(), anyList(), anyList())).thenReturn(new AiChatReply("", 3, 3,
                List.of(new AiToolCall("tc-2", "ask_user",
                        "{\"question\":\"哪个项目？\",\"options\":[\"商城\",\"门店\"]}"))));

        AssistantToolLoop.LoopOutcome outcome = loop.run(request(), modelClient);

        assertEquals("clarify", outcome.kind());
        assertEquals("哪个项目？", outcome.question());
        assertEquals(List.of("商城", "门店"), outcome.options());
        verify(assistantService, never()).appendToolMessage(any(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void clarifyHop_invalidArgs_feedsBackErrorAndContinues() {
        stubRegistry();
        when(askTool.execute(any(AssistantToolContext.class), anyMap()))
                .thenThrow(new IllegalArgumentException("question is required"));
        when(modelClient.chatWithTools(any(), anyList(), anyList()))
                .thenReturn(new AiChatReply("", 1, 1,
                        List.of(new AiToolCall("tc-3", "ask_user", "{\"options\":[\"a\"]}"))))
                .thenReturn(new AiChatReply("明白了，跳过。", 2, 2));

        AssistantToolLoop.LoopOutcome outcome = loop.run(request(), modelClient);

        assertEquals("answer", outcome.kind());
        // 澄清入参非法：以 tool 轮次回填错误供模型修正（不落库、不终止会话）
        ArgumentCaptor<List<Message>> turns = ArgumentCaptor.forClass(List.class);
        verify(modelClient, times(2)).chatWithTools(any(), anyList(), turns.capture());
        List<Message> secondTurns = turns.getAllValues().get(1);
        assertEquals(2, secondTurns.size());
        ToolResponseMessage feedback = (ToolResponseMessage) secondTurns.get(1);
        assertTrue(feedback.getResponses().get(0).responseData().contains("question is required"));
        verify(assistantService, never()).appendToolMessage(any(), anyString(), anyString());
    }

    @Test
    void changeHop_normalizesAndReturnsIntent() {
        stubRegistry();
        Map<String, Object> args = Map.of("targetTitle", "登录用例");
        when(modelClient.chatWithTools(any(), anyList(), anyList())).thenReturn(new AiChatReply("", 4, 6,
                List.of(new AiToolCall("tc-4", "create_case", "{\"targetTitle\":\"登录用例\"}"))));
        Map<String, Object> intent = Map.of("kind", "create_case");
        when(intentBuilder.normalize(eq("create_case"), any(), eq(WS_ID))).thenReturn(intent);

        AssistantToolLoop.LoopOutcome outcome = loop.run(request(), modelClient);

        assertEquals("intent", outcome.kind());
        assertEquals(intent, outcome.intent());
        verify(intentBuilder).normalize(eq("create_case"), any(), eq(WS_ID));
    }

    @Test
    void unknownTool_throwsParseFailed() {
        stubRegistry();
        when(registry.definition("bogus_tool")).thenReturn(null);
        when(modelClient.chatWithTools(any(), anyList(), anyList())).thenReturn(new AiChatReply("", 1, 1,
                List.of(new AiToolCall("tc-5", "bogus_tool", "{}"))));

        ServiceException error = assertThrows(ServiceException.class,
                () -> loop.run(request(), modelClient));

        assertEquals(ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code(), error.getCode());
    }

    @Test
    void loopCap_returnsLimitTextAsAnswer() {
        stubRegistry();
        when(queryTool.execute(any(AssistantToolContext.class), anyMap())).thenReturn("{}");
        when(modelClient.chatWithTools(any(), anyList(), anyList())).thenReturn(new AiChatReply("", 1, 1,
                List.of(new AiToolCall("tc-loop", "query_projects", "{}"))));

        AssistantToolLoop.LoopOutcome outcome = loop.run(request(), modelClient);

        assertEquals("answer", outcome.kind());
        assertEquals(AssistantToolLoop.LIMIT_TEXT, outcome.answer());
        verify(modelClient, times(AssistantToolLoop.MAX_LOOPS)).chatWithTools(any(), anyList(), anyList());
    }

    @Test
    void queryHop_badArgsJson_feedsBackErrorWithoutExecution() {
        stubRegistry();
        when(modelClient.chatWithTools(any(), anyList(), anyList()))
                .thenReturn(new AiChatReply("", 1, 1,
                        List.of(new AiToolCall("tc-6", "query_projects", "not-json"))))
                .thenReturn(new AiChatReply("换个问法。", 1, 1));

        AssistantToolLoop.LoopOutcome outcome = loop.run(request(), modelClient);

        assertEquals("answer", outcome.kind());
        verify(queryTool, never()).execute(any(), any());
        verify(assistantService).appendToolMessage(eq(CONVERSATION_ID), eq("tc-6"), contains("JSON 非法"));
    }

    private void stubRegistry() {
        lenient().when(registry.all()).thenReturn(List.of(QUERY_DEF, ASK_DEF, CREATE_DEF));
        lenient().when(registry.definition(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return switch (name) {
                case "query_projects" -> QUERY_DEF;
                case "ask_user" -> ASK_DEF;
                case "create_case" -> CREATE_DEF;
                default -> null;
            };
        });
        lenient().when(registry.executable(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return "query_projects".equals(name) ? queryTool
                    : "ask_user".equals(name) ? askTool : null;
        });
    }

    private AssistantToolLoop.LoopRequest request() {
        return new AssistantToolLoop.LoopRequest(USER_ID, WS_ID, CONVERSATION_ID,
                "system", "user", List.of(), new AiModelConfig(), List.of(), null);
    }
}
