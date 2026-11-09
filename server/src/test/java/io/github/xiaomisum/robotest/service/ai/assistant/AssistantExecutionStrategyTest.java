package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.task.core.ExecutionControl;
import io.github.xiaomisum.robotest.service.ai.task.core.TaskExecutionStrategy;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.mybatis.core.handler.UUIDTypeHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 助手执行策略单测（总册 4.2 助手与向量策略口径 / 分册 04 §4.1）：终态三路落盘与事件、
 * 共享控制逐跳介入、用户中止 / 预算耗尽置 interrupted（非模型错误不置 error）、
 * 模型或系统错误先置 error 再抛出、终态竞争失败不写消息终态（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AssistantExecutionStrategyTest {

    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final int PARSE_FAILED = ErrorCodeConstants.ASSISTANT_PARSE_FAILED.code();

    @Mock
    private AiAssistantMessageMapper messageMapper;
    @Mock
    private AiAssistantConversationMapper conversationMapper;
    @Mock
    private AssistantStreamBridge streamBridge;
    @Mock
    private AssistantPromptSupport promptSupport;
    @Mock
    private AssistantToolMessageWriter toolMessageWriter;
    @Mock
    private AssistantToolLoop toolLoop;
    @Mock
    private FileResourceService fileResourceService;
    @Mock
    private AiPromptService promptService;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private ExecutionControl control;
    @Mock
    private TaskTypeAdapter adapter;
    @Mock
    private AssistantStreamBridge.StreamChannel channel;

    @InjectMocks
    private AssistantExecutionStrategy strategy;

    /** 纯单测环境无 MyBatis 初始化：interrupted 条件写的 wrapper 依赖启动期注册的 TableInfo 缓存 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        if (TableInfoHelper.getTableInfo(AiAssistantMessage.class) == null) {
            TableInfoHelper.initTableInfo(assistant, AiAssistantMessage.class);
        }
    }

    private AiTask task;
    private TaskExecutionStrategy.ExecutionContext context;

    @BeforeEach
    void setUp() {
        task = new AiTask();
        task.setId(UUID.randomUUID());
        task.setType(Constants.AiTaskType.ASSISTANT_PARSE);
        task.setSubmittedBy(USER_ID);
        task.setInput(Map.of("messageId", MESSAGE_ID.toString()));
        AiTaskExecution execution = new AiTaskExecution();
        execution.setId(UUID.randomUUID());
        execution.setTaskId(task.getId());
        context = new TaskExecutionStrategy.ExecutionContext(task, execution, new AiModelConfig(),
                WORKSPACE_ID, PROJECT_ID, null, adapter);

        lenient().when(streamBridge.get(MESSAGE_ID)).thenReturn(channel);
        lenient().when(messageMapper.selectById(MESSAGE_ID)).thenReturn(assistantMessage());
        lenient().when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conversation());
        lenient().when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(history());
        lenient().when(promptSupport.contextText(any(), any())).thenReturn("会话项目状态与页面上下文");
        lenient().when(adapter.systemPrompt()).thenReturn("内置默认 {{history}} {{context}}");
        lenient().when(promptService.render(anyString(), anyString(), anyMap())).thenReturn("rendered");
    }

    // ---------- 终态三路（先落盘再发事件） ----------

    @Test
    void execute_clarify_persistsQuestionWithOptionsAndReturnsMeta() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.clarify("请问在哪个项目下创建？",
                        List.of("当前项目：商城", "会话：门店"),
                        List.of(Map.of("id", "tc-1", "name", "ask_user", "arguments", "{}")), 12, 3));

        TaskExecutionStrategy.ExecutionResult result = strategy.execute(context, control);

        assertEquals("clarify", result.resultMeta().get("kind"));
        assertTrue(result.proposals().isEmpty(), "助手澄清不产生通用提案");
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.DONE, captor.getValue().getStatus());
        assertTrue(captor.getValue().getContent().contains("请问在哪个项目下创建？"));
        assertTrue(captor.getValue().getContent().contains("- 当前项目：商城"));
        verify(channel).send(eq("clarify"), anyMap());
        verify(channel).send(eq("done"), anyMap());
        verify(channel).close();
        verify(control).ensureFinishing();
    }

    @Test
    void execute_intent_persistsPreviewAndRemembersProject() {
        Map<String, Object> intent = intent();
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.intent(intent, List.of(), 30, 9));

        TaskExecutionStrategy.ExecutionResult result = strategy.execute(context, control);

        assertEquals("intent", result.resultMeta().get("kind"));
        List<?> artifacts = (List<?>) result.resultMeta().get("artifacts");
        assertEquals(1, artifacts.size());
        assertEquals("intent-preview", ((Map<?, ?>) artifacts.get(0)).get("key"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(intent, captor.getValue().getIntent());
        ArgumentCaptor<AiAssistantConversation> conversationCaptor =
                ArgumentCaptor.forClass(AiAssistantConversation.class);
        verify(conversationMapper).updateById(conversationCaptor.capture());
        assertEquals(PROJECT_ID.toString(),
                conversationCaptor.getValue().getContextSnapshot().get("projectId"));
        verify(channel).send(eq("preview"), anyMap());
        verify(channel).send(eq("done"), anyMap());
    }

    @Test
    void execute_answer_replaysDeltaWithCitations() {
        List<Map<String, Object>> citations = List.of(Map.of(
                "type", "requirement", "id", UUID.randomUUID().toString(), "title", "REQ-1", "quote", "…"));
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.answer("执行进度 80%。", citations, List.of(), 40, 12));

        TaskExecutionStrategy.ExecutionResult result = strategy.execute(context, control);

        assertEquals("answer", result.resultMeta().get("kind"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals("执行进度 80%。", captor.getValue().getContent());
        assertEquals(1, captor.getValue().getCitations().size());
        verify(channel).send(eq("citations"), any());
        verify(channel).send(eq("delta"), anyMap());
        verify(channel).send(eq("done"), anyMap());
    }

    /** 5 跳上限用尽的固定文案走正常 answer 终态（分册 04 §4.1：正常 done 非错误码） */
    @Test
    void execute_loopLimitOutcome_completesAsDone() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.answer(AssistantToolLoop.LIMIT_TEXT, List.of(), List.of(), 5, 5));

        TaskExecutionStrategy.ExecutionResult result = strategy.execute(context, control);

        assertEquals("answer", result.resultMeta().get("kind"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.DONE, captor.getValue().getStatus());
        verify(control).ensureFinishing();
    }

    // ---------- 共享执行控制逐跳介入 ----------

    @Test
    void execute_bindsHopObserverToSharedControl() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.answer("ok", List.of(), List.of(), 1, 1));

        strategy.execute(context, control);

        ArgumentCaptor<AssistantToolLoop.HopObserver> observer =
                ArgumentCaptor.forClass(AssistantToolLoop.HopObserver.class);
        verify(toolLoop).run(any(), eq(modelClient), observer.capture());
        observer.getValue().beforeHop();
        observer.getValue().afterHop(7, 3);
        verify(control).beforeCall();
        verify(control).afterModelCall(eq(7L), eq(3L), any());
    }

    // ---------- 中止 / 预算 vs 模型错误的消息终态 ----------

    /** 用户中止（终态竞争失败）：消息置 interrupted，不写 done、不吞异常（任务终态由中止方收尾） */
    @Test
    void execute_inactiveDuringLoop_marksInterruptedAndRethrows() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenAnswer(invocation -> {
            AssistantToolLoop.HopObserver observer = invocation.getArgument(2);
            observer.beforeHop();
            return null;
        });
        org.mockito.Mockito.doThrow(new ExecutionControl.InactiveExecutionException())
                .when(control).beforeCall();

        assertThrows(ExecutionControl.InactiveExecutionException.class,
                () -> strategy.execute(context, control));

        verify(messageMapper).update(isNull(), any());
        verify(messageMapper, never()).updateById(any(AiAssistantMessage.class));
        verify(channel, never()).send(anyString(), any());
    }

    /** 预算先到（共享护栏）：同样置 interrupted 后上抛，由分派器按 1000018127 收口 aborted */
    @Test
    void execute_budgetExhaustedAtHopGate_marksInterruptedAndRethrows() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenAnswer(invocation -> {
            AssistantToolLoop.HopObserver observer = invocation.getArgument(2);
            observer.beforeHop();
            return null;
        });
        org.mockito.Mockito.doThrow(new ExecutionControl.BudgetExceededException())
                .when(control).beforeCall();

        assertThrows(ExecutionControl.BudgetExceededException.class,
                () -> strategy.execute(context, control));

        verify(messageMapper).update(isNull(), any());
        verify(messageMapper, never()).updateById(any(AiAssistantMessage.class));
    }

    /** 终态竞争：循环完成但收尾闸门发现执行已中止 → 不写 done（消息终态不变） */
    @Test
    void execute_inactiveAtFinishingGate_doesNotPersistDone() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.answer("迟到的完成", List.of(), List.of(), 1, 1));
        org.mockito.Mockito.doThrow(new ExecutionControl.InactiveExecutionException())
                .when(control).ensureFinishing();

        assertThrows(ExecutionControl.InactiveExecutionException.class,
                () -> strategy.execute(context, control));

        verify(messageMapper, never()).updateById(any(AiAssistantMessage.class));
        verify(messageMapper).update(isNull(), any());
    }

    @Test
    void execute_loopParseFailed_failsMessageAndEmitsError() {
        when(toolLoop.run(any(), eq(modelClient), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_PARSE_FAILED));

        ServiceException error = assertThrows(ServiceException.class,
                () -> strategy.execute(context, control));

        assertEquals(PARSE_FAILED, error.getCode());
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, captor.getValue().getStatus());
        verify(channel).send(eq("error"), anyMap());
        verify(channel).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_loopRuntimeException_mapsTo18117() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> strategy.execute(context, control));

        ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
        verify(channel).send(eq("error"), data.capture());
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                ((Map<?, ?>) data.getValue()).get("code"));
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, captor.getValue().getStatus());
    }

    /** 助手不豁免 chat 模型：缺模型按系统错误置 error 并透出 1000018118 */
    @Test
    void execute_missingChatModel_failsWithErrorAndCode118() {
        context = new TaskExecutionStrategy.ExecutionContext(task, context.execution(), null,
                WORKSPACE_ID, PROJECT_ID, null, adapter);

        ServiceException error = assertThrows(ServiceException.class,
                () -> strategy.execute(context, control));

        assertEquals(ErrorCodeConstants.AI_MODEL_NOT_CONFIGURED.code(), error.getCode());
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, captor.getValue().getStatus());
        verify(toolLoop, never()).run(any(), any(), any());
    }

    @Test
    void execute_missingConversation_closesChannelAndReturnsMissing() {
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(null);

        TaskExecutionStrategy.ExecutionResult result = strategy.execute(context, control);

        assertEquals("missing", result.resultMeta().get("kind"));
        verify(channel).close();
        verify(toolLoop, never()).run(any(), any(), any());
    }

    @Test
    void execute_historyWithoutUserMessage_throwsInputInvalid() {
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID))
                .thenReturn(List.of(assistantMessage()));

        ServiceException error = assertThrows(ServiceException.class,
                () -> strategy.execute(context, control));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), error.getCode());
        ArgumentCaptor<AiAssistantMessage> captor = ArgumentCaptor.forClass(AiAssistantMessage.class);
        verify(messageMapper).updateById(captor.capture());
        assertEquals(Constants.AiAssistantMessageStatus.ERROR, captor.getValue().getStatus());
        verify(toolLoop, never()).run(any(), any(), any());
    }

    @Test
    void execute_compensatesDanglingToolCallsOfUnfinishedMessages() {
        AiAssistantMessage dangling = assistantMessage();
        dangling.setStatus(Constants.AiAssistantMessageStatus.STREAMING);
        dangling.setToolCalls(List.of(Map.of("id", "tc-9", "name", "query_projects")));
        AiAssistantMessage done = assistantMessage();
        done.setId(UUID.randomUUID());
        done.setStatus(Constants.AiAssistantMessageStatus.DONE);
        done.setToolCalls(List.of(Map.of("id", "tc-done", "name", "create_case")));
        when(promptSupport.history(CONVERSATION_ID, MESSAGE_ID)).thenReturn(
                List.of(trigger(), dangling, done));
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.answer("ok", List.of(), List.of(), 1, 1));

        strategy.execute(context, control);

        verify(toolMessageWriter).appendToolMessage(eq(CONVERSATION_ID), eq("tc-9"),
                contains("操作已超时未执行"));
        verify(toolMessageWriter, never()).appendToolMessage(any(), eq("tc-done"), anyString());
    }

    /** 循环入参透传（workspace / 会话 / 提示词渲染 / 通道），共享成本按执行模型计 */
    @Test
    void execute_loopRequestCarriesVerifiedContext() {
        when(toolLoop.run(any(), eq(modelClient), any())).thenReturn(
                AssistantToolLoop.LoopOutcome.clarify("q", List.of(), List.of(), 0, 0));

        strategy.execute(context, control);

        ArgumentCaptor<AssistantToolLoop.LoopRequest> captor =
                ArgumentCaptor.forClass(AssistantToolLoop.LoopRequest.class);
        verify(toolLoop).run(captor.capture(), eq(modelClient), any());
        AssistantToolLoop.LoopRequest request = captor.getValue();
        assertEquals(USER_ID, request.userId());
        assertEquals(WORKSPACE_ID, request.workspaceId());
        assertEquals(CONVERSATION_ID, request.conversationId());
        assertEquals(channel, request.channel());
        assertTrue(request.systemPrompt().contains("智能助手"));
        assertEquals("rendered", request.userPrompt());
        verify(promptService).render(eq(Constants.AiTaskType.ASSISTANT_PARSE),
                contains("{{history}}"), anyMap());
    }

    // ---------- 辅助 ----------

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

    private static List<AiAssistantMessage> history() {
        return List.of(trigger());
    }

    private static Map<String, Object> intent() {
        Map<String, Object> scope = new java.util.LinkedHashMap<>();
        scope.put("workspaceId", WORKSPACE_ID.toString());
        scope.put("projectId", PROJECT_ID.toString());
        scope.put("projectName", "商城项目");
        Map<String, Object> intent = new java.util.LinkedHashMap<>();
        intent.put("kind", "create_case");
        intent.put("targetTitle", "登录用例");
        intent.put("scope", scope);
        return intent;
    }
}
