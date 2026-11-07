package io.github.xiaomisum.robotest.service.ai.task.core;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.framework.security.UserDetailsBridgeImpl;
import io.github.xiaomisum.robotest.model.entity.ai.AiModelConfig;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskExecution;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskProposal;
import io.github.xiaomisum.robotest.model.entity.ai.AiTaskTool;
import io.github.xiaomisum.robotest.repository.ai.AiTaskMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskProposalMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskExecutionMapper;
import io.github.xiaomisum.robotest.repository.ai.AiTaskToolMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.config.AiModelClient;
import io.github.xiaomisum.robotest.service.ai.config.AiToolCall;
import io.github.xiaomisum.robotest.service.ai.task.type.ProposalDraft;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Captor;
import org.springframework.dao.DuplicateKeyException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 执行核单测（总册 4.2）：计划入轨迹、白名单 / 参数 / 权限拒绝回喂、预算闸门、
 * 双状态机终态（proposals_ready / closed / aborted 与 114 / 117 / 127 终止码）（C8）。
 */
@ExtendWith(MockitoExtension.class)
class AgentExecutionRunnerTest {

    private static final String TYPE = "test_design_generation";
    private static final int CODE_TYPE_UNSUPPORTED = 1000018114;
    private static final int CODE_MODEL_FAILED = 1000018117;
    private static final int CODE_DUPLICATE = 1000018113;
    private static final int CODE_BUDGET_EXHAUSTED = 1000018127;

    @Mock
    private TraceRecorder traceRecorder;
    @Mock
    private BudgetGuard budgetGuard;
    @Mock
    private AgentToolRegistry toolRegistry;
    @Mock
    private TaskTypeAdapterRegistry adapterRegistry;
    @Mock
    private AiTaskExecutionMapper executionMapper;
    @Mock
    private ExecutionLifecycle executionLifecycle;
    @Mock
    private AiTaskProposalMapper proposalMapper;
    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiTaskToolMapper taskToolMapper;
    @Mock
    private AiModelClient modelClient;
    @Mock
    private UserDetailsBridgeImpl userDetailsBridge;

    @Captor
    private ArgumentCaptor<Map<String, Object>> planCaptor;

    @InjectMocks
    private AgentExecutionRunner runner;

    private AiTask task;
    private AiTaskExecution execution;
    private StubAdapter adapter;
    private AgentExecutionRunner.RunContext ctx;

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
        execution.setStatus("running");
        execution.setBudget(Map.of("maxSteps", 10, "maxDurationSeconds", 600,
                "maxTokens", 200000, "maxCost", 5));

        adapter = new StubAdapter(TYPE, List.of(), List.of(
                new ProposalDraft("module:0", "module_suggestion", null, "新增登录模块",
                        Map.of("title", "登录")),
                new ProposalDraft("case:0", "test_case_suggestion", null, "登录用例建议",
                        Map.of("title", "登录用例"))));
        // 成本逐跳按模型单价累计（4.3）：单价未配置恒 0，但模型行必须存在
        AiModelConfig priced = new AiModelConfig();
        priced.setId(UUID.randomUUID());
        ctx = new AgentExecutionRunner.RunContext(task, execution, priced,
                UUID.randomUUID(), UUID.randomUUID(), null);

        lenient().when(adapterRegistry.get(TYPE)).thenReturn(adapter);
        // 执行核条件写：succeed / heartbeat 返回 false 表示执行已被外部终止，单测默认未终止
        lenient().when(executionMapper.heartbeat(any(), anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(true);
        lenient().when(executionMapper.markPlan(any(), any())).thenReturn(true);
        lenient().when(traceRecorder.recordIfActive(any(), anyString(), anyString(), any()))
                .thenReturn(true);
        lenient().when(executionLifecycle.succeed(any(), any(), anyList(), any(), any()))
                .thenReturn(true);
        lenient().when(executionLifecycle.terminate(any(), any(), anyInt(), anyString(), any()))
                .thenReturn(true);
        lenient().doReturn(new LoginUser()).when(userDetailsBridge).loadByUserId(anyString());
    }

    @Test
    void run_finalOutputWithProposals_marksTaskProposalsReady() {
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList()))
                .thenReturn(reply("{\"ok\":true}", List.of()));

        runner.run(ctx);

        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()),
                argThat(drafts -> drafts.size() == 2), any(), any());
        verify(executionLifecycle, never()).terminate(any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void run_readOnlyOutput_closesTaskWithoutProposals() {
        adapter.drafts = List.of();
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList()))
                .thenReturn(reply("排序建议正文", List.of()));

        runner.run(ctx);

        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()),
                argThat(List::isEmpty), any(), any());
    }

    @Test
    void run_whitelistReject_feedsBackAndContinues() {
        adapter.drafts = List.of();
        givenWhitelistedTool("query_x", null);
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList())).thenReturn(
                reply("{\"steps\":[\"查需求\",\"出用例\"]}",
                        List.of(new AiToolCall("call-1", "hack_tool", "{}"))),
                reply("最终产出", List.of()));

        runner.run(ctx);

        verify(traceRecorder).recordIfActive(eq(execution.getId()), eq("plan"), eq("执行计划"),
                planCaptor.capture());
        assertTrue(planCaptor.getValue().containsKey("steps"));
        verify(executionMapper).markPlan(eq(execution.getId()), any());
        verify(traceRecorder).recordIfActive(eq(execution.getId()), eq("tool_result"),
                contains("hack_tool"), detailContaining("不在白名单"));
        verify(executionMapper, atLeastOnce()).heartbeat(eq(execution.getId()), anyInt(), anyInt(), anyInt(),
                any(BigDecimal.class));
        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()), anyList(), any(), any());
        verify(modelClient, times(2)).chatWithToolDeclarations(any(), anyList(), anyList());
    }

    @Test
    void run_invalidToolArguments_feedBackErrorWithoutAbort() {
        adapter.drafts = List.of();
        givenWhitelistedTool("query_x", null);
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList())).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "not-json"))),
                reply("最终产出", List.of()));

        runner.run(ctx);

        verify(traceRecorder).recordIfActive(eq(execution.getId()), eq("tool_result"),
                contains("query_x"), detailContaining("1000018115"));
        verify(executionLifecycle, never()).terminate(any(), any(), anyInt(), anyString(), any());
        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()), anyList(), any(), any());
    }

    @Test
    void run_missingPermission_feedBackDenied() {
        adapter.drafts = List.of();
        givenWhitelistedTool("query_x", "case:edit");
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList())).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "{}"))),
                reply("最终产出", List.of()));

        runner.run(ctx);

        verify(traceRecorder).recordIfActive(eq(execution.getId()), eq("tool_result"),
                contains("query_x"), detailContaining("权限不足"));
        verify(toolRegistry, never()).get(anyString());
        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()), anyList(), any(), any());
    }

    @Test
    void run_grantedPermission_executesToolAndFeedsResult() {
        adapter.drafts = List.of();
        givenWhitelistedTool("query_x", "case:edit");
        AgentTool tool = new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return toolRegistry.definition("query_x");
            }

            @Override
            public String execute(AgentToolContext context, Map<String, Object> args) {
                return "{\"ok\":true}";
            }
        };
        when(toolRegistry.get("query_x")).thenReturn(tool);
        LoginUser granted = new LoginUser();
        granted.setAuthorities(List.of(new org.springframework.security.core.authority
                .SimpleGrantedAuthority("case:edit")));
        doReturn(granted).when(userDetailsBridge).loadByUserId(anyString());
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList())).thenReturn(
                reply("计划", List.of(new AiToolCall("call-1", "query_x", "{}"))),
                reply("最终产出", List.of()));

        runner.run(ctx);

        verify(traceRecorder).recordIfActive(eq(execution.getId()), eq("tool_result"),
                contains("query_x"), detailContaining("\"ok\":true"));
        verify(executionLifecycle).succeed(eq(task.getId()), eq(execution.getId()), anyList(), any(), any());
    }

    @Test
    void run_budgetExceededBeforeFirstHop_abortsExecutionAndTask() {
        when(budgetGuard.exceeded(any(), any())).thenReturn(true);

        runner.run(ctx);

        verify(modelClient, never()).chatWithToolDeclarations(any(), anyList(), anyList());
        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(CODE_BUDGET_EXHAUSTED), contains("预算耗尽"), any());
    }

    @Test
    void run_modelFailure_abortsWith117() {
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.AI_MODEL_CALL_FAILED));

        runner.run(ctx);

        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(CODE_MODEL_FAILED), anyString(), any());
    }

    @Test
    void run_unknownType_abortsWith114() {
        when(adapterRegistry.get(TYPE)).thenReturn(null);

        runner.run(ctx);

        verify(modelClient, never()).chatWithToolDeclarations(any(), anyList(), anyList());
        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(CODE_TYPE_UNSUPPORTED), anyString(), any());
    }

    @Test
    void run_blankFinalOutput_abortsWith117() {
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList()))
                .thenReturn(reply("   ", List.of()));

        runner.run(ctx);

        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(CODE_MODEL_FAILED), contains("模型无有效输出"), any());
    }

    @Test
    void run_duplicateProposalKey_abortsWith113() {
        when(executionLifecycle.succeed(any(), any(), anyList(), any(), any()))
                .thenThrow(new DuplicateKeyException("uk_ai_task_proposal"));
        when(modelClient.chatWithToolDeclarations(any(), anyList(), anyList()))
                .thenReturn(reply("{\"ok\":true}", List.of()));

        runner.run(ctx);

        verify(executionLifecycle).terminate(eq(task.getId()), eq(execution.getId()),
                eq(CODE_DUPLICATE), anyString(), any());
    }

    // ---------- 装配辅助 ----------

    /** 白名单签发三件套：适配器声明 ∩ ai_task_tool 启用行 ∩ 代码注册表定义 */
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

    /** TOOL_RESULT 明细含指定片段的匹配器（result 字段为回喂模型的 JSON 文本） */
    private static Map<String, Object> detailContaining(String fragment) {
        return argThat(detail -> detail != null
                && String.valueOf(detail.get("result")).contains(fragment));
    }

    private static final class StubAdapter implements TaskTypeAdapter {

        private final String type;
        private List<String> toolCodes;
        private List<ProposalDraft> drafts;

        private StubAdapter(String type, List<String> toolCodes, List<ProposalDraft> drafts) {
            this.type = type;
            this.toolCodes = toolCodes;
            this.drafts = drafts;
        }

        @Override
        public String type() {
            return type;
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
            return Map.of("maxSteps", 40, "maxDurationSeconds", 600,
                    "maxTokens", 200000, "maxCost", 5);
        }

        @Override
        public String systemPrompt() {
            return "系统提示词";
        }

        @Override
        public List<ProposalDraft> buildProposals(String finalOutput, Map<String, Object> input) {
            return drafts;
        }
    }
}
