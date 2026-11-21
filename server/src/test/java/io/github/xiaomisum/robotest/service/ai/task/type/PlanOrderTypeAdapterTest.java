package io.github.xiaomisum.robotest.service.ai.task.type;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlan;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanExecutionRecord;
import io.github.xiaomisum.robotest.model.entity.plan.TestPlanNodeSnapshot;
import io.github.xiaomisum.robotest.repository.plan.TestPlanExecutionRecordMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanMapper;
import io.github.xiaomisum.robotest.repository.plan.TestPlanNodeSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanOrderTypeAdapterTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    @Mock
    private TestPlanMapper testPlanMapper;
    @Mock
    private TestPlanNodeSnapshotMapper testPlanNodeSnapshotMapper;
    @Mock
    private TestPlanExecutionRecordMapper testPlanExecutionRecordMapper;

    @InjectMocks
    private PlanOrderTypeAdapter adapter;

    @Test
    void defaultPrompt_declaresRealEvidenceDimensions() {
        String prompt = adapter.defaultPrompt();
        assertTrue(prompt.contains("历史失败率"));
        assertTrue(prompt.contains("前置 / 步骤 / 预期文本"));
        assertTrue(prompt.contains("依赖线索"));
    }

    @Test
    void prepare_planContextIncludesPathChildrenAndFailureRate() {
        TestPlan plan = new TestPlan();
        plan.setId(PLAN_ID);
        plan.setProjectId(PROJECT_ID);
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan);

        UUID rootId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID preId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        UUID expectedId = UUID.randomUUID();

        TestPlanNodeSnapshot root = snapshot(rootId, null, "normal", "登录模块", 0, false, null);
        TestPlanNodeSnapshot caseNode = snapshot(caseId, rootId, Constants.NodeType.CASE, "获取验证码", 0, true,
                Constants.ExecutionResult.FAIL);
        TestPlanNodeSnapshot pre = snapshot(preId, caseId, Constants.NodeType.PRECONDITION, "用户已到达登录页", 0, false, null);
        TestPlanNodeSnapshot step = snapshot(stepId, caseId, Constants.NodeType.STEP, "输入手机号", 0, false, null);
        TestPlanNodeSnapshot expected = snapshot(expectedId, caseId, Constants.NodeType.EXPECTED, "提示已发送", 0, false, null);

        when(testPlanNodeSnapshotMapper.listByPlanId(PLAN_ID))
                .thenReturn(List.of(root, caseNode, pre, step, expected));
        when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(PLAN_ID, Constants.NodeType.CASE))
                .thenReturn(List.of(caseNode));
        when(testPlanExecutionRecordMapper.listByPlanId(PLAN_ID))
                .thenReturn(List.of(
                        record(caseId, Constants.ExecutionResult.FAIL),
                        record(caseId, Constants.ExecutionResult.FAIL),
                        record(caseId, Constants.ExecutionResult.PASS),
                        record(caseId, Constants.ExecutionResult.BLOCK)));

        AgentExecutionPreparation preparation = adapter.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), null, PROJECT_ID,
                Map.of("planId", PLAN_ID.toString()), null));

        String context = preparation.promptVariables().get("planContext");
        assertTrue(context.contains("历史失败率"), "应声明失败率列：\n" + context);
        assertTrue(context.contains("登录模块 › 获取验证码"), "应包含祖先路径：\n" + context);
        assertTrue(context.contains("用户已到达登录页"), "应包含前置条件：\n" + context);
        assertTrue(context.contains("输入手机号"), "应包含步骤：\n" + context);
        assertTrue(context.contains("提示已发送"), "应包含预期：\n" + context);
        assertTrue(context.contains("50% (2/4)"), "失败率应为 fail/pass/block = 2/4：\n" + context);
        assertEquals(context, preparation.inputSnapshot(), "inputSnapshot 与 planContext 应一致（工具回显同一快照）");
    }

    @Test
    void prepare_noExecutionRecords_leavesFailureRateBlank() {
        TestPlan plan = new TestPlan();
        plan.setId(PLAN_ID);
        plan.setProjectId(PROJECT_ID);
        when(testPlanMapper.selectById(PLAN_ID)).thenReturn(plan);

        UUID caseId = UUID.randomUUID();
        TestPlanNodeSnapshot caseNode = snapshot(caseId, null, Constants.NodeType.CASE, "独立用例", 0, true, null);

        when(testPlanNodeSnapshotMapper.listByPlanId(PLAN_ID)).thenReturn(List.of(caseNode));
        when(testPlanNodeSnapshotMapper.listAssociatedByPlanId(PLAN_ID, Constants.NodeType.CASE))
                .thenReturn(List.of(caseNode));
        when(testPlanExecutionRecordMapper.listByPlanId(PLAN_ID)).thenReturn(List.of());

        AgentExecutionPreparation preparation = adapter.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), null, PROJECT_ID,
                Map.of("planId", PLAN_ID.toString()), null));

        String context = preparation.promptVariables().get("planContext");
        String caseLine = java.util.Arrays.stream(context.split("\n"))
                .filter(line -> line.contains(caseId.toString()))
                .findFirst().orElse("");
        assertTrue(caseLine.contains(" |  | "), "无执行记录时失败率列应留空：\n" + caseLine);
        assertFalse(caseLine.contains("%"), "无执行记录不应臆造失败率：\n" + caseLine);
    }

    private static TestPlanNodeSnapshot snapshot(UUID id, UUID parentId, String type, String title,
            int sortOrder, boolean associated, String lastResult) {
        TestPlanNodeSnapshot node = new TestPlanNodeSnapshot();
        node.setId(id);
        node.setPlanId(PLAN_ID);
        node.setParentId(parentId);
        node.setType(type);
        node.setTitle(title);
        node.setSortOrder(sortOrder);
        node.setIsAssociated(associated);
        node.setLastResult(lastResult);
        node.setPriority("P0");
        return node;
    }

    private static TestPlanExecutionRecord record(UUID nodeId, String result) {
        TestPlanExecutionRecord record = new TestPlanExecutionRecord();
        record.setPlanId(PLAN_ID);
        record.setSnapshotNodeId(nodeId);
        record.setResult(result);
        return record;
    }
}
