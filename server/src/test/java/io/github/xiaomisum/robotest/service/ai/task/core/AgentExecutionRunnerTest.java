package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.UserDetailsBridgeImpl;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTool;
import io.github.xiaomisum.robotest.repository.ai.AiTaskToolMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentExecutionRunnerTest {

    private static final String TYPE = "test_design_generation";

    @Mock
    private AgentToolRegistry toolRegistry;
    @Mock
    private AiTaskToolMapper taskToolMapper;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private UserDetailsBridgeImpl userDetailsBridge;
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
        lenient().doReturn(new LoginUser()).when(userDetailsBridge).loadByUserId(anyString());
    }

    @Test
    void execute_finalOutput_returnsAdapterResult() {
        adapter.drafts = List.of(new ProposalDraft("p1", "kind", null, "title", Map.of()));
        adapter.resultMeta = Map.of("source", "agent");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList()))
                .thenReturn(reply("{\"ok\":true}", List.of()));

        TaskExecutionStrategy.ExecutionResult result = runner.execute(context, control);

        assertEquals(adapter.drafts, result.proposals());
        assertEquals(adapter.resultMeta, result.resultMeta());
        verify(control).beforeCall();
        verify(control).afterModelCall(7, 3, new BigDecimal("0.000000"));
    }

    @Test
    void execute_toolLoop_checksAndRecordsAroundEveryCall() {
        givenWhitelistedTool("query_x", null);
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList())).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "{}"))),
                reply("最终产出", List.of()));

        runner.execute(context, control);

        verify(modelClient, times(2)).chatWithToolDeclarations(any(), anyList(), anyList());
        verify(control, times(2)).beforeCall();
        verify(control, times(2)).afterModelCall(7, 3, new BigDecimal("0.000000"));
        verify(control).beforeToolCall();
        verify(control).afterToolCall();
        verify(control).recordPlan(Map.of("summary", "计划"));
        verify(control).recordTrace(eq("tool_call"), contains("query_x"), any());
        verify(control).recordTrace(eq("tool_result"), contains("query_x"), any());
    }

    @Test
    void execute_invalidToolArguments_feedsBackAndContinues() {
        givenWhitelistedTool("query_x", null);
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList())).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "not-json"))),
                reply("最终产出", List.of()));

        runner.execute(context, control);

        verify(control).recordTrace(eq("tool_result"), contains("query_x"),
                org.mockito.ArgumentMatchers.argThat(detail ->
                        String.valueOf(detail.get("result")).contains("1000018115")));
    }

    @Test
    void execute_grantedPermission_executesTool() throws Exception {
        givenWhitelistedTool("query_x", "case:edit");
        AgentTool tool = org.mockito.Mockito.mock(AgentTool.class);
        when(toolRegistry.get("query_x")).thenReturn(tool);
        when(tool.execute(any(), any())).thenReturn("{\"ok\":true}");
        LoginUser granted = new LoginUser();
        granted.setAuthorities(List.of(new SimpleGrantedAuthority("case:edit")));
        doReturn(granted).when(userDetailsBridge).loadByUserId(anyString());
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList())).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "{}"))),
                reply("最终产出", List.of()));

        runner.execute(context, control);

        verify(tool).execute(any(), eq(Map.of()));
    }

    @Test
    void execute_blankFinalOutput_throwsModelFailure() {
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList()))
                .thenReturn(reply("   ", List.of()));

        var exception = assertThrows(xyz.migoo.framework.common.exception.ServiceException.class,
                () -> runner.execute(context, control));

        assertEquals(1000018117, exception.getCode());
    }

    @Test
    void execute_controlStopsBeforeModel_doesNotCallModel() {
        org.mockito.Mockito.doThrow(new ExecutionControl.InactiveExecutionException())
                .when(control).beforeCall();

        assertThrows(ExecutionControl.InactiveExecutionException.class,
                () -> runner.execute(context, control));

        verify(modelClient, never()).chatWithToolDeclarations(any(), anyList(), anyList());
    }

    private void givenWhitelistedTool(String toolCode, String permissionCode) {
        adapter.toolCodes = List.of(toolCode);
        AiTaskTool row = new AiTaskTool();
        row.setToolCode(toolCode);
        row.setTaskTypes(List.of(TYPE));
        row.setEnabled(true);
        when(taskToolMapper.selectList(any())).thenReturn(List.of(row));
        when(toolRegistry.definition(toolCode)).thenReturn(new AgentToolDefinition(
                toolCode, toolCode, "查询类工具", Map.of(), permissionCode, "readonly"));
    }

    private static AiChatReply reply(String content, List<AiToolCall> toolCalls) {
        return new AiChatReply(content, 7, 3, toolCalls);
    }

    private static final class StubAdapter implements TaskTypeAdapter {
        private List<String> toolCodes = List.of();
        private List<ProposalDraft> drafts = List.of();
        private Map<String, Object> resultMeta;

        @Override
        public String type() {
            return TYPE;
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
        public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
            return drafts;
        }

        @Override
        public Map<String, Object> buildResultMeta(String finalOutput, Map<String, Object> input) {
            return resultMeta;
        }
    }
}
