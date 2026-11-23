package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTool;
import io.github.xiaomisum.robotest.repository.ai.AiTaskToolMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiChatRequest;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiPromptService;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.github.xiaomisum.robotest.service.ai.task.type.AgentExecutionPreparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentExecutionRunnerTest {

    private static final String TYPE = "requirement_import";

    @Mock
    private AgentToolRegistry toolRegistry;
    @Mock
    private AiTaskToolMapper taskToolMapper;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private AiPromptService promptService;
    @Mock
    private AgentToolArgumentValidator argumentValidator;
    @Mock
    private ExecutionControl control;

    @InjectMocks
    private AgentExecutionRunner runner;

    private AiTask task;
    private AiTaskExecution execution;
    private AiModelConfig model;
    private StubAdapter adapter;
    private TaskExecutionStrategy.ExecutionContext context;

    @BeforeEach
    void setUp() {
        task = new AiTask();
        task.setId(UUID.randomUUID());
        task.setType(TYPE);
        task.setSubmittedBy(UUID.randomUUID());
        task.setInput(Map.of("requirementIds", List.of(UUID.randomUUID().toString())));
        execution = new AiTaskExecution();
        execution.setId(UUID.randomUUID());
        execution.setTaskId(task.getId());
        model = new AiModelConfig();
        model.setId(UUID.randomUUID());
        adapter = new StubAdapter();
        context = new TaskExecutionStrategy.ExecutionContext(task, execution, model,
                UUID.randomUUID(), UUID.randomUUID(), null, adapter);

        lenient().when(control.maxSteps(40)).thenReturn(10);
        lenient().when(promptService.render(anyString(), anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void execute_finalOutput_returnsAdapterResult() {
        adapter.drafts = List.of(new ProposalDraft("p1", "kind", null, "title", Map.of()));
        adapter.resultMeta = Map.of("source", "agent");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false)))
                .thenReturn(reply("{\"ok\":true}", List.of()));

        TaskExecutionStrategy.ExecutionResult result = runner.execute(context, control);

        assertEquals(adapter.drafts, result.proposals());
        assertEquals(adapter.resultMeta, result.resultMeta());
        verify(control, times(2)).beforeCall();
        verify(control).afterModelCall(7, 3, new BigDecimal("0.000000"));
    }

    @Test
    void execute_toolLoop_checksAndRecordsAroundEveryCall() {
        givenWhitelistedTool("query_x");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false))).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "{}"))),
                reply("最终产出", List.of()));

        runner.execute(context, control);

        verify(modelClient, times(2)).chatWithToolDeclarations(any(), anyList(), anyList(), eq(false));
        verify(control, times(3)).beforeCall();
        verify(control, times(2)).afterModelCall(7, 3, new BigDecimal("0.000000"));
        verify(control).beforeToolCall();
        verify(control).afterToolCall();
        verify(control).recordPlan(Map.of("summary", "计划"));
        verify(control).recordTrace(eq("tool_call"), contains("query_x"), any());
        verify(control).recordTrace(eq("tool_result"), contains("query_x"), any());
    }

    @Test
    void execute_invalidToolArguments_feedsBackAndContinues() {
        givenWhitelistedTool("query_x");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false))).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "not-json"))),
                reply("最终产出", List.of()));

        runner.execute(context, control);

        verify(control).recordTrace(eq("tool_result"), contains("query_x"),
                org.mockito.ArgumentMatchers.argThat(detail ->
                        String.valueOf(detail.get("result")).contains("1000018115")));
    }

    @Test
    void execute_schemaInvalidToolArguments_doesNotExecuteTool() {
        givenWhitelistedTool("query_x");
        AgentTool tool = org.mockito.Mockito.mock(AgentTool.class);
        when(argumentValidator.validate(any(), any())).thenReturn("缺少必填参数: id");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false))).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "{}"))),
                reply("最终产出", List.of()));

        runner.execute(context, control);

        verify(tool, never()).execute(any(), any());
    }

    @Test
    void execute_customPromptAndPreparedMedia_reachActualRequest() {
        AiChatMedia media = new AiChatMedia("image/png", new byte[]{1, 2});
        adapter.preparation = new AgentExecutionPreparation(Map.of("name", "demo"), "trusted", List.of(media),
                output -> adapter.drafts, output -> adapter.resultMeta);
        when(promptService.render(TYPE, "系统提示词", Map.of("name", "demo"))).thenReturn("自定义提示词");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false)))
                .thenReturn(reply("最终产出", List.of()));

        runner.execute(context, control);

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(modelClient).chatWithToolDeclarations(captor.capture(), anyList(), anyList(), eq(false));
        assertEquals("自定义提示词", captor.getValue().systemPrompt());
        assertEquals(1, captor.getValue().media().size());
        assertTrue(captor.getValue().userPrompt().contains("trusted"));
    }

    @Test
    void execute_whitelistedTool_executesTool() throws Exception {
        givenWhitelistedTool("query_x");
        AgentTool tool = org.mockito.Mockito.mock(AgentTool.class);
        when(toolRegistry.get("query_x")).thenReturn(tool);
        when(tool.execute(any(), any())).thenReturn("{\"ok\":true}");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false))).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "{}"))),
                reply("最终产出", List.of()));

        runner.execute(context, control);

        verify(tool).execute(any(), eq(Map.of()));
    }

    @Test
    void execute_blankFinalOutput_throwsModelFailure() {
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false)))
                .thenReturn(reply("   ", List.of()));

        var exception = assertThrows(xyz.migoo.framework.common.exception.ServiceException.class,
                () -> runner.execute(context, control));

        assertEquals(1000018117, exception.getCode());
        verify(modelClient, times(1)).chatWithToolDeclarations(any(), anyList(), anyList(), eq(false));
    }

    @Test
    void execute_outputRepair_retriesOnceAndSucceeds() {
        adapter.supportsRepair = true;
        java.util.concurrent.atomic.AtomicInteger parses = new java.util.concurrent.atomic.AtomicInteger();
        adapter.preparation = new AgentExecutionPreparation(Map.of("name", "demo"), "trusted", List.of(),
                output -> {
                    if (parses.getAndIncrement() == 0) {
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(),
                                "missing_source_refs=1（示例取值 REQ-020）");
                    }
                    return List.of(new ProposalDraft("p1", "kind", null, "title", Map.of()));
                }, output -> Map.of());
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false)))
                .thenReturn(reply("坏输出", List.of()), reply("好输出", List.of()));

        TaskExecutionStrategy.ExecutionResult result = runner.execute(context, control);

        assertEquals(1, result.proposals().size());
        assertEquals(2, parses.get());
        verify(modelClient, times(2)).chatWithToolDeclarations(any(), anyList(), anyList(), eq(false));
        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(modelClient, atLeastOnce()).chatWithToolDeclarations(captor.capture(), anyList(), anyList(), eq(false));
        assertTrue(captor.getAllValues().get(1).userPrompt().contains("输出修正要求"));
        assertTrue(captor.getAllValues().get(1).userPrompt().contains("REQ-020"));
        assertEquals("系统提示词", captor.getAllValues().get(1).systemPrompt());
        verify(control).recordTrace(eq(Constants.AiTraceEventType.AI_DECISION), contains("自愈重试"), any());
    }

    @Test
    void execute_outputRepairStillInvalid_keepsFirstError() {
        adapter.supportsRepair = true;
        adapter.preparation = new AgentExecutionPreparation(Map.of(), "trusted", List.of(),
                output -> {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), "全量丢弃");
                }, output -> Map.of());
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList(), eq(false)))
                .thenReturn(reply("坏输出", List.of()), reply("仍坏输出", List.of()));

        ServiceException error = assertThrows(ServiceException.class, () -> runner.execute(context, control));

        assertTrue(error.getMessage().contains("全量丢弃"));
        verify(modelClient, times(2)).chatWithToolDeclarations(any(), anyList(), anyList(), eq(false));
        verify(control).recordTrace(eq(Constants.AiTraceEventType.AI_DECISION), eq("模型输出解析失败"), any());
    }

    @Test
    void execute_controlStopsBeforeModel_doesNotCallModel() {
        org.mockito.Mockito.doThrow(new ExecutionControl.InactiveExecutionException())
                .when(control).beforeCall();

        assertThrows(ExecutionControl.InactiveExecutionException.class,
                () -> runner.execute(context, control));

        verify(modelClient, never()).chatWithToolDeclarations(any(), anyList(), anyList(), eq(false));
    }

    private void givenWhitelistedTool(String toolCode) {
        adapter.toolCodes = List.of(toolCode);
        AiTaskTool row = new AiTaskTool();
        row.setToolCode(toolCode);
        row.setTaskTypes(List.of(TYPE));
        row.setEnabled(true);
        when(taskToolMapper.selectList(any())).thenReturn(List.of(row));
        when(toolRegistry.definition(toolCode)).thenReturn(new AgentToolDefinition(
                toolCode, toolCode, "查询类工具", Map.of(), "readonly"));
    }

    private static AiChatReply reply(String content, List<AiToolCall> toolCalls) {
        return new AiChatReply(content, 7, 3, toolCalls);
    }

    private static final class StubAdapter implements TaskTypeAdapter {
        private List<String> toolCodes = List.of();
        private List<ProposalDraft> drafts = List.of();
        private Map<String, Object> resultMeta;
        private AgentExecutionPreparation preparation;
        private boolean supportsRepair;

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public boolean supportsOutputRepair() {
            return supportsRepair;
        }

        @Override
        public void validateInput(Map<String, Object> input) {
        }

        @Override
        public List<String> toolCodes() {
            return toolCodes;
        }

        @Override
        public Map<String, Object> defaultBudget() {
            return Map.of();
        }

        @Override
        public String systemPrompt() {
            return "系统提示词";
        }

        @Override
        public AgentExecutionPreparation prepare(PreparationContext context) {
            return preparation == null ? TaskTypeAdapter.super.prepare(context) : preparation;
        }

        @Override
        public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
            return drafts;
        }

        @Override
        public Map<String, Object> buildResultMeta(String finalOutput, Map<String, Object> input) {
            return resultMeta;
        }
    }
}
