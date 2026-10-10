package io.github.xiaomisum.robotest.service.ai.task.adopt;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.ai.AiTask;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanOrderAdopterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @Mock
    private AssistAdoptSupport support;
    @Mock
    private TestPlanMapper testPlanMapper;
    @Mock
    private TestPlanNodeSnapshotMapper testPlanNodeSnapshotMapper;

    @InjectMocks
    private PlanOrderAdopter adopter;

    @Test
    void type_isPlanOrder() {
        assertEquals("plan_order", adopter.type());
    }

    @Test
    void adopt_rejected_returnsNullAndSkipsEverything() {
        assertNull(adopter.adopt(context(Constants.AiArtifactAction.REJECTED, null, PLAN_ID, 1,
                List.of(item(nodeId(3), 1)), List.of(), List.of())));

        verify(support, never()).requirePermission(any(), anyString());
        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(any(), any());
    }

    @Test
    void adopt_withoutPlanExecutePermission_failsBeforeAnyQuery() {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTED_NO_PERMISSION))
                .when(support).requirePermission(any(), eq("plan:execute"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 1,
                        List.of(), List.of(), List.of())));

        assertEquals(ErrorCodeConstants.ASSISTED_NO_PERMISSION.code(), exception.getCode());
        verify(testPlanMapper, never()).selectById(any());
    }

    @Test
    void adopt_planNotFoundOrOtherProject_throwsPlanNotFound() {
        TestPlan other = new TestPlan();
        other.setId(PLAN_ID);
        other.setProjectId(OTHER_PROJECT_ID);
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(other);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 1,
                        List.of(), List.of(), List.of())));

        assertEquals(ErrorCodeConstants.ASSISTED_PLAN_NOT_FOUND.code(), exception.getCode());
    }

    @Test
    void adopt_planWithoutAssociatedCases_throwsPlanCasesEmpty() {
        stubPlan();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 1,
                        List.of(), List.of(), List.of())));

        assertEquals(ErrorCodeConstants.ASSISTED_PLAN_CASES_EMPTY.code(), exception.getCode());
        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(any(), any());
    }

    @Test
    void adopt_missingPlanIdInTargetAndInput_throwsInputInvalid() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, null, 1,
                        List.of(), List.of(), List.of())));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void adopt_nonPositiveRound_throwsInputInvalid() {
        stubPlan();

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 0,
                        List.of(), List.of(), List.of())));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
    }

    @Test
    void adopt_itemOutsidePlanCases_throwsInputInvalid() {
        stubPlan();
        List<TestPlanNodeSnapshot> associated = List.of(snapshot(nodeId(1), null, 0, true));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 1,
                        List.of(item(UUID.randomUUID(), 1)), associated, associated)));

        assertEquals(ErrorCodeConstants.AI_TASK_INPUT_INVALID.code(), exception.getCode());
        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(any(), any());
    }

    @Test
    void adopt_reorderedCasesReassignSortOrderAndLeaveRootUntouched() {
        stubPlan();
        TestPlanNodeSnapshot root = snapshot(null, null, 0, false);
        TestPlanNodeSnapshot first = snapshot(nodeId(1), root.getId(), 0, true);
        TestPlanNodeSnapshot second = snapshot(nodeId(2), root.getId(), 1, true);
        List<TestPlanNodeSnapshot> all = List.of(root, first, second);
        // 建议顺序：第二个用例排到首位，原首位次之
        List<Map<String, Object>> items = List.of(item(second.getId(), 1), item(first.getId(), 2));

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 1, items, all,
                List.of(first, second)));

        // 根节点不参与重排；关联用例在原槽位上按建议名次对调
        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(eq(root.getId()), any());
        verify(testPlanNodeSnapshotMapper).updateSortOrder(eq(second.getId()), eq(0));
        verify(testPlanNodeSnapshotMapper).updateSortOrder(eq(first.getId()), eq(1));
        verify(support).record(any(), eq("TEST_PLAN"), eq(PLAN_ID), anyString(),
                eq("PLAN_UPDATED"), anyString());
    }

    @Test
    void adopt_keepsStructureSiblingPositionWhileReorderingCases() {
        stubPlan();
        TestPlanNodeSnapshot root = snapshot(null, null, 0, false);
        TestPlanNodeSnapshot folder = snapshot(nodeId(10), root.getId(), 0, false);
        TestPlanNodeSnapshot first = snapshot(nodeId(1), root.getId(), 1, true);
        TestPlanNodeSnapshot second = snapshot(nodeId(2), root.getId(), 2, true);
        TestPlanNodeSnapshot nested = snapshot(nodeId(3), folder.getId(), 0, true);
        List<TestPlanNodeSnapshot> all = List.of(root, folder, first, second, nested);
        List<Map<String, Object>> items = List.of(item(second.getId(), 1), item(first.getId(), 2),
                item(nested.getId(), 3));

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 1, items, all,
                List.of(first, second, nested)));

        // 结构容器原位不动，其下用例不受同级重排影响；两个关联用例重排到容器之后的槽位
        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(eq(folder.getId()), any());
        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(eq(nested.getId()), any());
        verify(testPlanNodeSnapshotMapper).updateSortOrder(eq(second.getId()), eq(1));
        verify(testPlanNodeSnapshotMapper).updateSortOrder(eq(first.getId()), eq(2));
    }

    @Test
    void adopt_editedContentOverridesArtifactItems() {
        stubPlan();
        TestPlanNodeSnapshot root = snapshot(null, null, 0, false);
        TestPlanNodeSnapshot first = snapshot(nodeId(1), root.getId(), 0, true);
        TestPlanNodeSnapshot second = snapshot(nodeId(2), root.getId(), 1, true);
        List<TestPlanNodeSnapshot> all = List.of(root, first, second);
        Map<String, Object> dragged = Map.of("items", List.of(item(first.getId(), 1), item(second.getId(), 2)));

        adopter.adopt(context(Constants.AiArtifactAction.ADOPTED_EDITED, dragged, PLAN_ID, 1,
                List.of(item(second.getId(), 1), item(first.getId(), 2)), all, List.of(first, second)));

        // 人工拖拽后的顺序优先于产物建议：首个用例回到首位
        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(any(), any());
        verify(support, never()).record(any(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void adopt_unchangedOrderSkipsWriteAndActivity() {
        stubPlan();
        TestPlanNodeSnapshot root = snapshot(null, null, 0, false);
        TestPlanNodeSnapshot first = snapshot(nodeId(1), root.getId(), 0, true);
        TestPlanNodeSnapshot second = snapshot(nodeId(2), root.getId(), 1, true);
        List<TestPlanNodeSnapshot> all = List.of(root, first, second);

        AdoptOutcome outcome = adopter.adopt(context(Constants.AiArtifactAction.ADOPTED, null, PLAN_ID, 2,
                List.of(item(first.getId(), 1), item(second.getId(), 2)), all, List.of(first, second)));

        verify(testPlanNodeSnapshotMapper, never()).updateSortOrder(any(), any());
        verify(support, never()).record(any(), anyString(), any(), anyString(), anyString(), anyString());
        assertEquals(PLAN_ID.toString(), outcome.adoptedRef().get("planId"));
        assertEquals(List.of(first.getId().toString(), second.getId().toString()),
                outcome.adoptedRef().get("appliedOrder"));
        // 承接不产生新实体，仅回执计划 ID 供前端回跳
        assertNull(outcome.createdId());
    }

    // ---------- fixtures ----------

    private void stubPlan() {
        TestPlan plan = new TestPlan();
        plan.setId(PLAN_ID);
        plan.setProjectId(PROJECT_ID);
        plan.setName("回归计划");
        lenient().when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
    }

    private static UUID nodeId(int index) {
        return new UUID(0L, index);
    }

    private static Map<String, Object> item(UUID nodeId, int rank) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("nodeId", nodeId.toString());
        item.put("suggestedRank", rank);
        return item;
    }

    private TestPlanNodeSnapshot snapshot(UUID id, UUID parentId, int sortOrder, boolean associated) {
        TestPlanNodeSnapshot node = new TestPlanNodeSnapshot();
        node.setId(id == null ? UUID.randomUUID() : id);
        node.setPlanId(PLAN_ID);
        node.setParentId(parentId);
        node.setType(Constants.NodeType.CASE);
        node.setTitle("用例-" + node.getId());
        node.setSortOrder(sortOrder);
        node.setIsAssociated(associated);
        return node;
    }

    private AdoptContext context(String action, Map<String, Object> edited, UUID targetPlanId, Integer round,
            List<Map<String, Object>> items, List<TestPlanNodeSnapshot> all,
            List<TestPlanNodeSnapshot> associated) {
        LoginUser loginUser = mock(LoginUser.class);
        lenient().when(loginUser.getPermissions()).thenReturn(List.of("plan:execute"));
        AiTask task = new AiTask();
        task.setType("plan_order");
        lenient().when(testPlanNodeSnapshotMapper.listByPlanId(PLAN_ID)).thenReturn(new ArrayList<>(all));
        lenient().when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(PLAN_ID, Constants.NodeType.CASE))
                .thenReturn(new ArrayList<>(associated));

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("planId", PLAN_ID.toString());
        content.put("items", items);
        Map<String, Object> artifact = Map.of("key", "order-1",
                "kind", Constants.AiArtifactKind.ORDER_SUGGESTION, "content", content);
        return new AdoptContext(task, artifact, action, edited, null, null, null, null, null,
                null, null, targetPlanId, round, PROJECT_ID, OPERATOR_ID, loginUser);
    }
}
