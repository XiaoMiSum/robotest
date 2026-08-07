package io.github.xiaomisum.robotest.service.ai.review;


import io.github.xiaomisum.robotest.framework.common.AiFunctionType;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckBatchDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiReviewCheckItemDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAnalysisTask;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewModuleSnapshot;
import io.github.xiaomisum.robotest.model.entity.review.TestReviewNodeSnapshot;
import io.github.xiaomisum.robotest.repository.ai.AiAnalysisTaskMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewModuleSnapshotMapper;
import io.github.xiaomisum.robotest.repository.review.TestReviewNodeSnapshotMapper;
import io.github.xiaomisum.robotest.service.ai.gateway.AiGatewayService;
import io.github.xiaomisum.robotest.service.ai.support.AiOutputValidator;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewCheckTaskHandlerTest {

    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();

    @Mock
    private AiGatewayService aiGatewayService;
    @Mock
    private AiAnalysisTaskMapper aiTaskMapper;
    @Mock
    private TestReviewNodeSnapshotMapper reviewNodeSnapshotMapper;
    @Mock
    private TestReviewModuleSnapshotMapper reviewModuleSnapshotMapper;

    @InjectMocks
    private AiReviewCheckTaskHandler handler;

    private AiAnalysisTask task() {
        AiAnalysisTask task = new AiAnalysisTask();
        task.setId(UUID.randomUUID());
        task.setTargetId(REVIEW_ID);
        task.setWorkspaceId(UUID.randomUUID());
        task.setProjectId(UUID.randomUUID());
        task.setCreatedBy(UUID.randomUUID());
        return task;
    }

    private TestReviewNodeSnapshot node(UUID id, String title, String type, UUID parentId, int sort) {
        TestReviewNodeSnapshot node = new TestReviewNodeSnapshot();
        node.setId(id);
        node.setTitle(title);
        node.setType(type);
        node.setDocumentSnapshotId(DOC_ID);
        node.setParentId(parentId);
        node.setIsAssociated(true);
        node.setSortOrder(sort);
        return node;
    }

    private TestReviewModuleSnapshot docSnapshot() {
        TestReviewModuleSnapshot m = new TestReviewModuleSnapshot();
        m.setId(DOC_ID);
        m.setReviewId(REVIEW_ID);
        m.setType(Constants.ModuleType.DOCUMENT);
        m.setName("登录用例集");
        return m;
    }

    private AiReviewCheckItemDTO item(String snapshotNodeId, String dimension) {
        AiReviewCheckItemDTO item = new AiReviewCheckItemDTO();
        item.setSnapshotNodeId(snapshotNodeId);
        item.setDimension(dimension);
        item.setSuggestion("建议文案");
        return item;
    }

    @Test
    void execute_accumulatesAndFiltersHallucinations() {
        AiAnalysisTask task = task();
        UUID caseA = UUID.randomUUID();
        UUID caseB = UUID.randomUUID();
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(
                node(caseA, "验证码登录成功", Constants.NodeType.CASE, null, 1),
                node(caseB, "支付超时提示", Constants.NodeType.CASE, null, 2),
                node(UUID.randomUUID(), "点击发送验证码", Constants.NodeType.STEP, caseA, 1)));
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(docSnapshot()));
        when(aiTaskMapper.updateProgressIfRunning(any(), anyInt(), any())).thenReturn(1);

        AiReviewCheckBatchDTO out = new AiReviewCheckBatchDTO();
        out.setItems(List.of(item(caseA.toString(), "missing_precondition"),
                item(UUID.randomUUID().toString(), "vague_step")));
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REVIEW_CHECK), any(), any(), any(),
                eq(AiReviewCheckBatchDTO.class), any())).thenReturn(out);

        Map<String, Object> result = handler.execute(task);

        assertEquals(2, result.get("totalCaseCount"));
        assertEquals(2, result.get("checkedCaseCount"));
        assertEquals(0, result.get("skippedBatches"));
        List<?> items = (List<?>) result.get("items");
        assertEquals(1, items.size());
        assertEquals(caseA.toString(), ((AiReviewCheckItemDTO) items.get(0)).getSnapshotNodeId());
        // 每批完成累计写入进度（首批边界写 0%）
        verify(aiTaskMapper).updateProgressIfRunning(eq(task.getId()), eq(0), any());
    }

    @Test
    void execute_cancelledAtBatchBoundary_returnsPartialWithoutCallingLlm() {
        AiAnalysisTask task = task();
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(
                node(UUID.randomUUID(), "登录成功", Constants.NodeType.CASE, null, 1)));
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(docSnapshot()));
        // 协作式取消：条件更新影响行数为 0
        when(aiTaskMapper.updateProgressIfRunning(any(), anyInt(), any())).thenReturn(0);

        Map<String, Object> result = handler.execute(task);

        verify(aiGatewayService, never()).completeStructured(any(), any(), any(), any(), any(), any(), any());
        assertEquals(1, result.get("totalCaseCount"));
        assertEquals(0, result.get("checkedCaseCount"));
        assertEquals(0, result.get("skippedBatches"));
    }

    @Test
    void execute_batchFailureSkipsAndContinues() {
        AiAnalysisTask task = task();
        List<TestReviewNodeSnapshot> nodes = new ArrayList<>();
        for (int i = 1; i <= 31; i++) {
            nodes.add(node(UUID.randomUUID(), "用例" + i, Constants.NodeType.CASE, null, i));
        }
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(nodes);
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(docSnapshot()));
        when(aiTaskMapper.updateProgressIfRunning(any(), anyInt(), any())).thenReturn(1);
        // 第 1 批（30 个）两次尝试均失败（重试 1 次）、第 2 批（1 个）成功
        AiReviewCheckBatchDTO out = new AiReviewCheckBatchDTO();
        out.setItems(List.of(item(nodes.get(30).getId().toString(), "missing_expected")));
        when(aiGatewayService.completeStructured(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("mock llm fail"))
                .thenThrow(new RuntimeException("mock llm fail"))
                .thenReturn(out);

        Map<String, Object> result = handler.execute(task);

        assertEquals(31, result.get("totalCaseCount"));
        assertEquals(1, result.get("checkedCaseCount"));
        assertEquals(1, result.get("skippedBatches"));
        assertEquals(1, ((List<?>) result.get("items")).size());
    }

    @Test
    void execute_noCases_returnsEmptySuccess() {
        AiAnalysisTask task = task();
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of());
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of());

        Map<String, Object> result = handler.execute(task);

        assertEquals(0, result.get("totalCaseCount"));
        assertEquals(0, result.get("checkedCaseCount"));
        assertEquals(0, result.get("skippedBatches"));
        verify(aiGatewayService, never()).completeStructured(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void execute_assertsDimensionEnumInExtraAssertion() {
        AiAnalysisTask task = task();
        UUID caseA = UUID.randomUUID();
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(
                node(caseA, "登录成功", Constants.NodeType.CASE, null, 1)));
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(docSnapshot()));
        when(aiTaskMapper.updateProgressIfRunning(any(), anyInt(), any())).thenReturn(1);
        ArgumentCaptor<Consumer<AiReviewCheckBatchDTO>> assertionCaptor = ArgumentCaptor.forClass(Consumer.class);
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REVIEW_CHECK), any(), any(), any(),
                eq(AiReviewCheckBatchDTO.class), assertionCaptor.capture())).thenReturn(new AiReviewCheckBatchDTO());

        handler.execute(task);
        Consumer<AiReviewCheckBatchDTO> assertion = assertionCaptor.getValue();
        AiReviewCheckBatchDTO bad = new AiReviewCheckBatchDTO();
        bad.setItems(List.of(item(caseA.toString(), "invalid_dimension")));
        assertThrows(AiOutputValidator.OutputValidationException.class, () -> assertion.accept(bad));
    }

    @Test
    void execute_marksSimilarTitleGroupsInBusinessData() {
        AiAnalysisTask task = task();
        when(reviewNodeSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(
                node(UUID.randomUUID(), "验证码登录成功", Constants.NodeType.CASE, null, 1),
                node(UUID.randomUUID(), "验证码登录失败", Constants.NodeType.CASE, null, 2)));
        when(reviewModuleSnapshotMapper.listByReviewId(REVIEW_ID)).thenReturn(List.of(docSnapshot()));
        when(aiTaskMapper.updateProgressIfRunning(any(), anyInt(), any())).thenReturn(1);
        ArgumentCaptor<String> dataCaptor = ArgumentCaptor.forClass(String.class);
        when(aiGatewayService.completeStructured(any(), eq(AiFunctionType.REVIEW_CHECK), any(),
                dataCaptor.capture(), any(), eq(AiReviewCheckBatchDTO.class), any())).thenReturn(new AiReviewCheckBatchDTO());

        handler.execute(task);

        String data = dataCaptor.getValue();
        assertTrue(data.contains("snapshotNodeId="));
        assertTrue(data.contains("验证码登录成功"));
        assertTrue(data.contains("【同批相似用例分组】"));
    }
}
