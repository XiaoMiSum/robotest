package io.github.xiaomisum.robotest.service.ai.task.handler;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.ai.task.TaskSubmitContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 执行顺序建议处理器单测（C8）：权限与输入校验、计划归属与空计划口径、
 * 快照现序装配与产物 rank 连续化（响应升序 + 未响应补尾）。
 */
@ExtendWith(MockitoExtension.class)
class PlanOrderHandlerTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();
    private static final UUID ROOT_ID = UUID.randomUUID();
    private static final UUID CASE_A = UUID.randomUUID();
    private static final UUID CASE_B = UUID.randomUUID();
    private static final UUID CASE_C = UUID.randomUUID();

    @Mock
    private TestPlanMapper testPlanMapper;
    @Mock
    private TestPlanNodeSnapshotMapper testPlanNodeSnapshotMapper;
    @Mock
    private TaskExecutionContext context;
    @Captor
    private org.mockito.ArgumentCaptor<Map<String, String>> promptVars;

    @InjectMocks
    private PlanOrderHandler handler;

    // ---------- SPI 契约 ----------

    @Test
    void type_andDefaultPrompt_carryContextPlaceholders() {
        assertEquals("plan_order", handler.type());
        assertTrue(handler.defaultPrompt().contains("{{planContext}}"));
        assertTrue(handler.defaultPrompt().contains("{{roundCount}}"));
    }

    @Test
    void checkPermission_withoutPlanExecute_throws306() {
        LoginUser user = new LoginUser();
        user.setAuthorities(List.of(new SimpleGrantedAuthority("plan:execute")));

        handler.checkPermission(user);

        LoginUser viewer = new LoginUser();
        viewer.setAuthorities(List.of(new SimpleGrantedAuthority("plan:view")));
        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(),
                assertThrows(ServiceException.class, () -> handler.checkPermission(viewer)).getCode());
        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(),
                assertThrows(ServiceException.class, () -> handler.checkPermission(null)).getCode());
    }

    // ---------- 输入校验 ----------

    @Test
    void validateInput_missingOrMalformedPlanId_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(Map.of())).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(
                        Map.of("planId", "nope"))).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(
                        Map.of("planId", PLAN_ID.toString(), "round", "0"))).getCode());
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(
                        Map.of("planId", PLAN_ID.toString(), "round", "first"))).getCode());
    }

    @Test
    void validateInput_withoutProjectContext_throws115() {
        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(),
                assertThrows(ServiceException.class, () -> handler.validateInput(baseInput(),
                        new TaskSubmitContext(null, null, OPERATOR_ID, null))).getCode());
    }

    @Test
    void validateInput_planMissingOrForeignProject_throws303() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(null);
        assertEquals(ErrorCodeConstants.ASSISTED_PLAN_NOT_FOUND.code(),
                assertThrows(ServiceException.class,
                        () -> handler.validateInput(baseInput(), submitContext())).getCode());

        TestPlan foreign = new TestPlan();
        foreign.setId(PLAN_ID);
        foreign.setProjectId(UUID.randomUUID());
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(foreign);
        assertEquals(ErrorCodeConstants.ASSISTED_PLAN_NOT_FOUND.code(),
                assertThrows(ServiceException.class,
                        () -> handler.validateInput(baseInput(), submitContext())).getCode());
    }

    @Test
    void validateInput_noAssociatedCases_throws304() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(PROJECT_ID));
        when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(eq(PLAN_ID), anyString()))
                .thenReturn(List.of());

        assertEquals(ErrorCodeConstants.ASSISTED_PLAN_CASES_EMPTY.code(),
                assertThrows(ServiceException.class,
                        () -> handler.validateInput(baseInput(), submitContext())).getCode());
    }

    // ---------- 执行 ----------

    @Test
    void execute_sortsSnapshotTreeAndFlattensOnlyAssociatedCases() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(PROJECT_ID));
        when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(eq(PLAN_ID), anyString()))
                .thenReturn(List.of(snapshot(CASE_A, "TC-A"), snapshot(CASE_C, "TC-C")));
        // 快照全量：兄弟按 sortOrder nullsLast 排序，根排序；DFS 现序 = A → B → C
        when(testPlanNodeSnapshotMapper.listByPlanId(PLAN_ID)).thenReturn(List.of(
                snapshotAt(ROOT_ID, null, "root", 0),
                snapshotChild(CASE_B, ROOT_ID, "TC-B", 5),
                snapshotChild(CASE_A, ROOT_ID, "TC-A", 1),
                snapshotChild(CASE_C, ROOT_ID, "TC-C", 2)));
        stubExecution("{\"artifacts\":[{\"content\":{\"items\":["
                + "{\"nodeId\":\"" + CASE_C + "\",\"suggestedRank\":2,\"reason\":\"先验主流程\"},"
                + "{\"nodeId\":\"" + CASE_A + "\",\"suggestedRank\":1} ]}}]}");

        TaskResult result = handler.execute(context);

        Map<String, Object> artifact = artifacts(result).get(0);
        assertEquals("order-1", artifact.get("key"));
        assertEquals("pending", artifact.get("confirmStatus"));
        assertEquals("执行顺序建议（2 项）", artifact.get("title"));

        Map<String, Object> content = content(artifact);
        assertEquals(PLAN_ID, UUID.fromString(String.valueOf(content.get("planId"))));
        assertEquals(List.of("TC-A", "TC-C"), content.get("beforeOrder"));
        assertEquals(List.of("TC-A", "TC-C"), content.get("afterOrder"));

        List<Map<String, Object>> items = list(content.get("items"));
        assertEquals(2, items.size());
        assertEquals(CASE_A, UUID.fromString(String.valueOf(items.get(0).get("nodeId"))));
        assertEquals(1, items.get(0).get("suggestedRank"));
        assertEquals(CASE_C, UUID.fromString(String.valueOf(items.get(1).get("nodeId"))));
        assertEquals(2, items.get(1).get("suggestedRank"));
        assertEquals("先验主流程", items.get(1).get("reason"));
        assertEquals("", items.get(0).get("reason"));
    }

    @Test
    void execute_unrespondedCasesAppendedInCurrentOrder() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(PROJECT_ID));
        when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(eq(PLAN_ID), anyString())).thenReturn(List.of(
                snapshot(CASE_A, "TC-A"), snapshot(CASE_B, "TC-B"), snapshot(CASE_C, "TC-C")));
        when(testPlanNodeSnapshotMapper.listByPlanId(PLAN_ID)).thenReturn(List.of(
                snapshot(CASE_A, "TC-A"), snapshot(CASE_B, "TC-B"), snapshot(CASE_C, "TC-C")));
        // 只响应 C 且未给 rank → 响应序排最前，A / B 按现序补尾并连续编号
        stubExecution("{\"artifacts\":[{\"content\":{\"items\":["
                + "{\"nodeId\":\"" + CASE_C + "\",\"reason\":\"回归优先\"}]}}]}");

        Map<String, Object> content = content(artifacts(handler.execute(context)).get(0));
        List<Map<String, Object>> items = list(content.get("items"));

        assertEquals(List.of("TC-C", "TC-A", "TC-B"), content.get("afterOrder"));
        assertEquals(List.of("TC-A", "TC-B", "TC-C"), content.get("beforeOrder"));
        assertEquals(List.of(CASE_C, CASE_A, CASE_B), items.stream()
                .map(item -> UUID.fromString(String.valueOf(item.get("nodeId")))).toList());
        assertEquals(List.of(1, 2, 3), items.stream().map(item -> item.get("suggestedRank")).toList());
        assertEquals("回归优先", items.get(0).get("reason"));
    }

    @Test
    void execute_planContextCarriesRoundCountVariable() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(PROJECT_ID));
        when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(eq(PLAN_ID), anyString()))
                .thenReturn(List.of(snapshot(CASE_A, "TC-A")));
        when(testPlanNodeSnapshotMapper.listByPlanId(PLAN_ID)).thenReturn(List.of(snapshot(CASE_A, "TC-A")));
        when(context.getInput()).thenReturn(new LinkedHashMap<>(Map.of("planId", PLAN_ID.toString(),
                "round", "3")));
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.prompt(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.chat(any(), any())).thenReturn(new AiChatReply(
                "{\"artifacts\":[{\"content\":{\"items\":[{\"nodeId\":\"" + CASE_A + "\"}]}}]}", 5, 6));

        handler.execute(context);

        verify(context).prompt(anyString(), promptVars.capture());
        assertEquals("3", promptVars.getValue().get("roundCount"));
        assertTrue(promptVars.getValue().get("planContext").contains("TC-A"));
    }

    @Test
    void execute_modelProducesNoInPlanItem_failsWith117() {
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan(PROJECT_ID));
        when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(eq(PLAN_ID), anyString()))
                .thenReturn(List.of(snapshot(CASE_A, "TC-A")));
        when(testPlanNodeSnapshotMapper.listByPlanId(PLAN_ID)).thenReturn(List.of(snapshot(CASE_A, "TC-A")));
        when(context.getInput()).thenReturn(baseInput());
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.prompt(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.chat(any(), any())).thenReturn(new AiChatReply(
                "{\"artifacts\":[{\"content\":{\"items\":[{\"nodeId\":\"" + UUID.randomUUID() + "\"}]}}]}",
                5, 6));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> handler.execute(context));
        assertEquals(ErrorCodeConstants.AI_MODEL_CALL_FAILED.code(), exception.getCode());
    }

    // ---------- 辅助 ----------

    private void stubExecution(String replyContent) {
        when(context.getInput()).thenReturn(baseInput());
        when(context.getProjectId()).thenReturn(PROJECT_ID);
        when(context.prompt(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.chat(any(), any())).thenReturn(new AiChatReply(replyContent, 10, 20));
    }

    private Map<String, Object> baseInput() {
        return new LinkedHashMap<>(Map.of("planId", PLAN_ID.toString()));
    }

    private TaskSubmitContext submitContext() {
        return new TaskSubmitContext(PROJECT_ID, UUID.randomUUID(), OPERATOR_ID, null);
    }

    private TestPlan plan(UUID projectId) {
        TestPlan plan = new TestPlan();
        plan.setId(PLAN_ID);
        plan.setProjectId(projectId);
        plan.setName("回归计划");
        return plan;
    }

    private TestPlanNodeSnapshot snapshot(UUID id, String title) {
        TestPlanNodeSnapshot node = new TestPlanNodeSnapshot();
        node.setId(id);
        node.setPlanId(PLAN_ID);
        node.setTitle(title);
        node.setType("case");
        node.setIsAssociated(Boolean.TRUE);
        node.setPriority("P1");
        return node;
    }

    private TestPlanNodeSnapshot snapshotChild(UUID id, UUID parentId, String title, int sortOrder) {
        TestPlanNodeSnapshot node = snapshot(id, title);
        node.setParentId(parentId);
        node.setSortOrder(sortOrder);
        return node;
    }

    private TestPlanNodeSnapshot snapshotAt(UUID id, UUID parentId, String title, Integer sortOrder) {
        TestPlanNodeSnapshot node = snapshot(id, title);
        node.setParentId(parentId);
        node.setSortOrder(sortOrder);
        node.setIsAssociated(Boolean.FALSE);
        node.setType("normal");
        return node;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> artifacts(TaskResult result) {
        return (List<Map<String, Object>>) result.result().get("artifacts");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> content(Map<String, Object> artifact) {
        return (Map<String, Object>) artifact.get("content");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object raw) {
        return raw instanceof List ? (List<Map<String, Object>>) raw : new ArrayList<>();
    }
}
