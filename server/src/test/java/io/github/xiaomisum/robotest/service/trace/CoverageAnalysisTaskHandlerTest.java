package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖分析任务处理器单测：范围解析 / 权限 / 无用例直判与模型输出清洗（追溯矩阵详设 4.2，C8）。
 */
@ExtendWith(MockitoExtension.class)
class CoverageAnalysisTaskHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID CASE_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TraceEdgeMapper traceEdgeMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TraceMatrixService traceMatrixService;
    @Mock
    private TaskExecutionContext context;

    @InjectMocks
    private CoverageAnalysisTaskHandler handler;

    @Test
    void type_isCoverageAnalysis() {
        assertEquals("coverage_analysis", handler.type());
    }

    @Test
    void defaultPrompt_declaresSceneVariables() {
        // 场景（AiPromptScenes）要求提示词声明两个必填变量，缺一保存即校验失败
        String prompt = handler.defaultPrompt();
        assertTrue(prompt.contains("{{requirementContext}}"));
        assertTrue(prompt.contains("{{caseContext}}"));
    }

    @Test
    void checkPermission_withoutTraceView_throwsNoPermission() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("case:view"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(user));
        assertEquals(ErrorCodeConstants.TRACE_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void checkPermission_nullLoginUser_throwsNoPermission() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.checkPermission(null));
        assertEquals(ErrorCodeConstants.TRACE_NO_PERMISSION.code(), exception.getCode());
    }

    @Test
    void checkPermission_withTraceView_passes() {
        LoginUser user = mock(LoginUser.class);
        when(user.getPermissions()).thenReturn(List.of("trace:view"));

        assertDoesNotThrow(() -> handler.checkPermission(user));
    }

    // ---------- 输入校验（提交侧） ----------

    @Test
    void validateInput_absentOrEmptyIds_passes() {
        assertDoesNotThrow(() -> handler.validateInput(null));
        assertDoesNotThrow(() -> handler.validateInput(Map.of()));
        // 空数组视同未传（缺省全量口径）
        assertDoesNotThrow(() -> handler.validateInput(Map.of("requirementIds", List.of())));
    }

    @Test
    void validateInput_malformedIds_throwsInputInvalid() {
        assertInputInvalid(() -> handler.validateInput(Map.of("requirementIds", "not-a-list")));
        assertInputInvalid(() -> handler.validateInput(Map.of("requirementIds", List.of("not-a-uuid"))));
    }

    @Test
    void validateInput_moreThanLimit_throwsInputInvalid() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            ids.add(UUID.randomUUID().toString());
        }
        assertInputInvalid(() -> handler.validateInput(Map.of("requirementIds", ids)));
    }

    @Test
    void validateInput_validIds_passes() {
        assertDoesNotThrow(() -> handler.validateInput(
                Map.of("requirementIds", List.of(UUID.randomUUID().toString()))));
    }

    @Test
    void validateInput_contextMissingProject_throwsInputInvalid() {
        assertInputInvalid(() -> handler.validateInput(Map.of(), null));
        assertInputInvalid(() -> handler.validateInput(Map.of(),
                new TaskSubmitContext(null, null, null, null)));
    }

    @Test
    void validateInput_fullScopeEmpty_throwsInputInvalid() {
        when(requirementMapper.countAnalyzableByProject(PROJECT_ID)).thenReturn(0L);

        assertInputInvalid(() -> handler.validateInput(Map.of(), submitContext()));
    }

    @Test
    void validateInput_fullScopeOverLimit_throwsInputInvalid() {
        when(requirementMapper.countAnalyzableByProject(PROJECT_ID)).thenReturn(101L);

        assertInputInvalid(() -> handler.validateInput(Map.of(), submitContext()));
    }

    @Test
    void validateInput_fullScopeWithinLimit_passes() {
        when(requirementMapper.countAnalyzableByProject(PROJECT_ID)).thenReturn(2L);
        when(requirementMapper.listAnalyzableByProject(PROJECT_ID))
                .thenReturn(List.of(requirement("confirmed"), requirement("changed")));

        assertDoesNotThrow(() -> handler.validateInput(Map.of(), submitContext()));
    }

    @Test
    void validateInput_explicitIdArchived_throwsInputInvalid() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("archived")));

        assertInputInvalid(() -> handler.validateInput(explicitIds(), submitContext()));
    }

    @Test
    void validateInput_explicitIdForeignProject_throwsInputInvalid() {
        Requirement alien = requirement("confirmed");
        alien.setProjectId(UUID.randomUUID());
        when(requirementMapper.listByIds(any())).thenReturn(List.of(alien));

        assertInputInvalid(() -> handler.validateInput(explicitIds(), submitContext()));
    }

    @Test
    void validateInput_explicitIdMissing_throwsInputInvalid() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of());

        assertInputInvalid(() -> handler.validateInput(explicitIds(), submitContext()));
    }

    @Test
    void validateInput_explicitIdsValid_passes() {
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("confirmed")));

        assertDoesNotThrow(() -> handler.validateInput(explicitIds(), submitContext()));
    }

    // ---------- 执行 ----------

    @Test
    void execute_noLinkedCases_judgesUncoveredWithoutModel() {
        stubFullScope();
        when(traceEdgeMapper.listActiveBySource(anyString(), any(Collection.class)))
                .thenReturn(List.of());
        when(traceMatrixService.applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("uncovered"), any())).thenReturn(true);

        TaskResult result = handler.execute(context);

        assertEquals(1, result.result().get("analyzedCount"));
        assertEquals(0, result.result().get("skippedCount"));
        assertEquals(1, result.result().get("uncoveredCount"));
        assertEquals(0, result.tokensIn());
        assertEquals(0, result.tokensOut());
        // 无关联用例不调模型（4.2 步骤 2）
        verify(context, never()).chat(anyString(), anyString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(traceMatrixService).applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("uncovered"), captor.capture());
        assertEquals(List.of("无关联用例"), captor.getValue().get("gaps"));
        assertEquals(List.of(), captor.getValue().get("coveredBy"));
        verify(context).report(eq(100), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_withLinkedCases_callsModelAndCleansEvidence() {
        stubExplicitScope();
        when(traceEdgeMapper.listActiveBySource(anyString(), any(Collection.class)))
                .thenReturn(List.of(derivationEdge()));
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of(caseNode()));
        when(context.prompt(anyString(), any())).thenReturn("rendered prompt");
        when(context.chat(anyString(), anyString())).thenReturn(new AiChatReply(
                "{\"coverageStatus\":\"partial\",\"matchedCaseIds\":[\"" + CASE_ID
                        + "\",\"ghost-id\"],\"gaps\":[\"缺少超时限制\"],\"reason\":\"部分覆盖\"}",
                7, 11));
        when(traceMatrixService.applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("partial"), any())).thenReturn(true);

        TaskResult result = handler.execute(context);

        assertEquals(1, result.result().get("analyzedCount"));
        assertEquals(0, result.result().get("uncoveredCount"));
        assertEquals(7, result.tokensIn());
        assertEquals(11, result.tokensOut());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(traceMatrixService).applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("partial"), captor.capture());
        Map<String, Object> evidence = captor.getValue();
        // matchedCaseIds 过滤幻觉 ID，已知用例映射为标题存 evidence.coveredBy（可读）
        assertEquals(List.of("验证码正确可登录"), evidence.get("coveredBy"));
        assertEquals(List.of("缺少超时限制"), evidence.get("gaps"));
        assertEquals("部分覆盖", evidence.get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_evidenceReasonTruncated() {
        stubExplicitScope();
        when(traceEdgeMapper.listActiveBySource(anyString(), any(Collection.class)))
                .thenReturn(List.of(derivationEdge()));
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of(caseNode()));
        when(context.prompt(anyString(), any())).thenReturn("rendered prompt");
        when(context.chat(anyString(), anyString())).thenReturn(new AiChatReply(
                "{\"coverageStatus\":\"uncovered\",\"matchedCaseIds\":[],\"gaps\":[],\"reason\":\""
                        + "a".repeat(1200) + "\"}", 1, 1));
        when(traceMatrixService.applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("uncovered"), any())).thenReturn(true);

        handler.execute(context);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(traceMatrixService).applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("uncovered"), captor.capture());
        String reason = (String) captor.getValue().get("reason");
        assertEquals(1001, reason.length());
    }

    @Test
    void execute_invalidCoverageStatus_throwsModelFailed() {
        stubExplicitScope();
        when(traceEdgeMapper.listActiveBySource(anyString(), any(Collection.class)))
                .thenReturn(List.of(derivationEdge()));
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of(caseNode()));
        when(context.prompt(anyString(), any())).thenReturn("rendered prompt");
        when(context.chat(anyString(), anyString()))
                .thenReturn(new AiChatReply("{\"coverageStatus\":\"unknown\"}", 1, 1));

        assertModelFailed(() -> handler.execute(context));
    }

    @Test
    void execute_unparsableModelOutput_throwsModelFailed() {
        stubExplicitScope();
        when(traceEdgeMapper.listActiveBySource(anyString(), any(Collection.class)))
                .thenReturn(List.of(derivationEdge()));
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of(caseNode()));
        when(context.prompt(anyString(), any())).thenReturn("rendered prompt");
        when(context.chat(anyString(), anyString()))
                .thenReturn(new AiChatReply("not-a-json", 1, 1));

        assertModelFailed(() -> handler.execute(context));
    }

    @Test
    void execute_conflictEdgeExcluded_judgesUncoveredWithoutModel() {
        stubFullScope();
        // conflict 边不计入覆盖统计（4.1），等同无关联用例
        TraceEdge conflict = derivationEdge();
        conflict.setStatus("conflict");
        when(traceEdgeMapper.listActiveBySource(anyString(), any(Collection.class)))
                .thenReturn(List.of(conflict));
        when(traceMatrixService.applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("uncovered"), any())).thenReturn(true);

        TaskResult result = handler.execute(context);

        verify(context, never()).chat(anyString(), anyString());
        assertEquals(1, result.result().get("uncoveredCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_manualRowSkipped_countsSkipped() {
        stubFullScope();
        when(traceEdgeMapper.listActiveBySource(anyString(), any(Collection.class)))
                .thenReturn(List.of());
        // 人工判定优先（3.8）：写入被跳过，结论不计入 uncovered
        when(traceMatrixService.applyAiCoverage(eq(PROJECT_ID), eq(TASK_ID), eq(REQUIREMENT_ID),
                eq("uncovered"), any())).thenReturn(false);

        TaskResult result = handler.execute(context);

        assertEquals(1, result.result().get("analyzedCount"));
        assertEquals(1, result.result().get("skippedCount"));
        assertEquals(0, result.result().get("uncoveredCount"));
    }

    @Test
    void execute_fullScopeGrowsOverLimitAtExecute_throwsInputInvalid() {
        when(context.getInput()).thenReturn(Map.of());
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        // 提交与执行之间范围漂移：执行期复核上限（详设 3.7）
        when(requirementMapper.countAnalyzableByProject(PROJECT_ID)).thenReturn(101L);

        assertInputInvalid(() -> handler.execute(context));
    }

    // ---------- 辅助 ----------

    private void stubFullScope() {
        when(context.getInput()).thenReturn(Map.of());
        stubScopeBase();
        when(requirementMapper.countAnalyzableByProject(PROJECT_ID)).thenReturn(1L);
        when(requirementMapper.listAnalyzableByProject(PROJECT_ID))
                .thenReturn(List.of(requirement("confirmed")));
    }

    private void stubExplicitScope() {
        when(context.getInput()).thenReturn(explicitIds());
        stubScopeBase();
        when(requirementMapper.listByIds(any())).thenReturn(List.of(requirement("confirmed")));
    }

    private void stubScopeBase() {
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        // 部分失败用例在写入前抛出，taskId 桩可能不被消费
        lenient().when(context.getTaskId()).thenReturn(TASK_ID);
    }

    private TaskSubmitContext submitContext() {
        return new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), UUID.randomUUID(), null);
    }

    private static Map<String, Object> explicitIds() {
        return Map.of("requirementIds", List.of(REQUIREMENT_ID.toString()));
    }

    private static Requirement requirement(String status) {
        Requirement item = new Requirement();
        item.setId(REQUIREMENT_ID);
        item.setProjectId(PROJECT_ID);
        item.setCode("REQ-001");
        item.setTitle("登录验证码");
        item.setDescription("输入验证码后方可登录");
        item.setStatus(status);
        return item;
    }

    private static TraceEdge derivationEdge() {
        TraceEdge edge = new TraceEdge();
        edge.setId(UUID.randomUUID());
        edge.setProjectId(PROJECT_ID);
        edge.setEdgeType("derivation");
        edge.setSourceType("requirement");
        edge.setSourceId(REQUIREMENT_ID);
        edge.setTargetType("test_case");
        edge.setTargetId(CASE_ID);
        edge.setStatus("ai_created");
        return edge;
    }

    private static TestCaseNode caseNode() {
        TestCaseNode node = new TestCaseNode();
        node.setId(CASE_ID);
        node.setType("case");
        node.setTitle("验证码正确可登录");
        return node;
    }

    /** 业务异常断言：范围 / 格式校验失败统一 1000018156（覆盖分析任务参数非法） */
    private void assertInputInvalid(Runnable invocation) {
        ServiceException exception =
                assertThrows(ServiceException.class, invocation::run);
        assertEquals(ErrorCodeConstants.TRACE_COVERAGE_INPUT_INVALID.code(), exception.getCode());
    }

    /** 模型输出不合法（状态取值 / 不可解析）统一 1000018117 落任务失败态 */
    private void assertModelFailed(Runnable invocation) {
        ServiceException exception =
                assertThrows(ServiceException.class, invocation::run);
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }
}
