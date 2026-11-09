package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiSettingsReader;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * assistant_parse 类型适配器单测（总册 3.6.1 / 分册 04 §4.1）：登记元数据（类型、预算、
 * 提示词正文、策略分派）、输入归属校验与 vision 能力派生（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantParseTypeAdapterTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();

    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private AiAssistantConversationMapper conversationMapper;
    @Mock
    private AssistantPromptSupport promptSupport;
    @Mock
    private AssistantExecutionStrategy executionStrategy;
    @Mock
    private TaskExecutionStrategy agentStrategy;

    @InjectMocks
    private AssistantParseTypeAdapter adapter;

    @Test
    void type_budgetAndPromptDeclareAssistantScene() {
        assertEquals(Constants.AiTaskType.ASSISTANT_PARSE, adapter.type());

        Map<String, Object> budget = adapter.defaultBudget();
        assertEquals(5, budget.get("maxSteps"));
        assertEquals(600, budget.get("maxDurationSeconds"));
        assertEquals(200000, budget.get("maxTokens"));
        assertEquals(5, budget.get("maxCost"));

        String prompt = adapter.systemPrompt();
        assertTrue(prompt.contains("{{history}}"));
        assertTrue(prompt.contains("{{context}}"));
        assertTrue(prompt.contains("ask_user"));
        // 助手走自有 AssistantToolRegistry，不签发 Agent 工具白名单，也不产生通用提案
        assertTrue(adapter.toolCodes().isEmpty());
        assertTrue(adapter.buildProposals("{}", Map.of()).isEmpty());
    }

    @Test
    void executionStrategy_dispatchesToAssistantStrategyNotAgent() {
        assertSame(executionStrategy, adapter.executionStrategy(agentStrategy));
    }

    @Test
    void validateInput_rejectsMissingOrMalformedMessageId() {
        assertInputInvalid(() -> adapter.validateInput(null));
        assertInputInvalid(() -> adapter.validateInput(Map.of("messageId", "not-a-uuid")));
    }

    @Test
    void validateInput_context_rejectsForeignOwnership() {
        assertInputInvalid(() -> adapter.validateInput(input(),
                new TaskSubmitContext(null, WORKSPACE_ID, null, null)));
        assertInputInvalid(() -> adapter.validateInput(input(),
                new TaskSubmitContext(null, WORKSPACE_ID, UUID.randomUUID(), null)));
    }

    @Test
    void validateInput_context_acceptsOwnAssistantMessage() {
        when(messageMapper.selectById(MESSAGE_ID)).thenReturn(assistantMessage());
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation());

        adapter.validateInput(input(), new TaskSubmitContext(null, WORKSPACE_ID, USER_ID, null));
    }

    @Test
    void requiredCapability_derivesVisionFromTriggerImageOnly() {
        lenient().when(messageMapper.selectById(MESSAGE_ID)).thenReturn(assistantMessage());

        AiAssistantMessage trigger = trigger();
        trigger.setImages(List.of(Map.of("fileId", FILE_ID.toString(), "name", "a.png")));
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(List.of(trigger));
        assertEquals(AiSettingsReader.CAPABILITY_VISION, adapter.requiredCapability(input()));

        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(List.of(trigger()));
        assertNull(adapter.requiredCapability(input()));
    }

    @Test
    void requiredCapability_invalidInput_returnsNullWithoutThrowing() {
        assertNull(adapter.requiredCapability(Map.of("messageId", "not-a-uuid")));
        assertNull(adapter.requiredCapability(null));
    }

    // ---------- 辅助 ----------

    private static Map<String, Object> input() {
        return Map.of("messageId", MESSAGE_ID.toString());
    }

    private static AiAssistantMessage assistantMessage() {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setId(MESSAGE_ID);
        message.setConversationId(CONVERSATION_ID);
        message.setRole(Constants.AiAssistantMessageRole.ASSISTANT);
        message.setStatus(Constants.AiAssistantMessageStatus.STREAMING);
        return message;
    }

    private static AiAssistantConversation conversation() {
        AiAssistantConversation conversation = new AiAssistantConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        conversation.setWorkspaceId(WORKSPACE_ID);
        return conversation;
    }

    private static AiAssistantMessage trigger() {
        AiAssistantMessage message = new AiAssistantMessage();
        message.setId(UUID.randomUUID());
        message.setConversationId(CONVERSATION_ID);
        message.setRole(Constants.AiAssistantMessageRole.USER);
        message.setContent("帮我建一个登录用例");
        return message;
    }

    private static void assertInputInvalid(Runnable invocation) {
        ServiceException error = assertThrows(ServiceException.class, invocation::run);
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), error.getCode());
    }
}
