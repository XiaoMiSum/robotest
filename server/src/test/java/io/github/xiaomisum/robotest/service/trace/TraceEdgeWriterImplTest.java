package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 快照圈选对账单测（追溯矩阵详设 4.1）：新增 / 保留 / 移除三类边行为（C8）。
 */
@ExtendWith(MockitoExtension.class)
class TraceEdgeWriterImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OPERATOR_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID CASE_A = UUID.randomUUID();
    private static final UUID CASE_B = UUID.randomUUID();

    @Mock
    private TraceEdgeMapper traceEdgeMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;

    @InjectMocks
    private TraceEdgeWriterImpl writer;

    @Test
    void sync_insertsNewlySelectedAndKeepsRetained() {
        TraceEdge retained = snapshotEdge(CASE_A);
        when(traceEdgeMapper.listCaseSnapshots(PROJECT_ID, "test_review", REVIEW_ID))
                .thenReturn(List.of(retained));
        when(testCaseNodeMapper.listByIds(Set.of(CASE_A, CASE_B)))
                .thenReturn(List.of(caseNode(CASE_A, 3), caseNode(CASE_B, 1)));
        when(traceEdgeMapper.insert(any(TraceEdge.class))).thenReturn(1);

        writer.syncCaseSnapshotEdges(PROJECT_ID, "test_review", REVIEW_ID, Set.of(CASE_A, CASE_B), OPERATOR_ID);

        verify(traceEdgeMapper, never()).deleteById(retained.getId());
        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).insert(captor.capture());
        TraceEdge created = captor.getValue();
        assertCreatedEdge(created);
        // 版本锚定在圈选用例当前版本（target_version = v{version}）
        assertEquals("v1", created.getTargetVersion());
        assertEquals(OPERATOR_ID, created.getConfirmedBy());
    }

    @Test
    void sync_removesEdgesBeyondCurrentSelection() {
        TraceEdge deselected = snapshotEdge(CASE_A);
        when(traceEdgeMapper.listCaseSnapshots(PROJECT_ID, "test_review", REVIEW_ID))
                .thenReturn(List.of(deselected));

        writer.syncCaseSnapshotEdges(PROJECT_ID, "test_review", REVIEW_ID, Set.of(), OPERATOR_ID);

        verify(traceEdgeMapper).deleteById(deselected.getId());
        verify(testCaseNodeMapper, never()).listByIds(any());
        verify(traceEdgeMapper, never()).insert(any(TraceEdge.class));
    }

    @Test
    void sync_skipsSelectionReferencingMissingCase() {
        when(traceEdgeMapper.listCaseSnapshots(PROJECT_ID, "test_plan", REVIEW_ID)).thenReturn(List.of());
        when(testCaseNodeMapper.listByIds(Set.of(CASE_A))).thenReturn(List.of());

        writer.syncCaseSnapshotEdges(PROJECT_ID, "test_plan", REVIEW_ID, Set.of(CASE_A), OPERATOR_ID);

        verify(traceEdgeMapper, never()).insert(any(TraceEdge.class));
    }

    @Test
    void sync_concurrentInsertIsSwallowed() {
        when(traceEdgeMapper.listCaseSnapshots(PROJECT_ID, "test_review", REVIEW_ID)).thenReturn(List.of());
        when(testCaseNodeMapper.listByIds(Set.of(CASE_A))).thenReturn(List.of(caseNode(CASE_A, 2)));
        when(traceEdgeMapper.insert(any(TraceEdge.class))).thenThrow(new DuplicateKeyException("uk"));

        assertDoesNotThrow(() -> writer.syncCaseSnapshotEdges(PROJECT_ID, "test_review", REVIEW_ID,
                Set.of(CASE_A), OPERATOR_ID));
    }

    @Test
    void remove_deletesEverySnapshotEdge() {
        TraceEdge first = snapshotEdge(CASE_A);
        TraceEdge second = snapshotEdge(CASE_B);
        when(traceEdgeMapper.listCaseSnapshots(PROJECT_ID, "test_review", REVIEW_ID))
                .thenReturn(List.of(first, second));

        writer.removeCaseSnapshotEdges(PROJECT_ID, "test_review", REVIEW_ID);

        verify(traceEdgeMapper, times(1)).deleteById(first.getId());
        verify(traceEdgeMapper, times(1)).deleteById(second.getId());
    }

    // ---------- 构造辅助 ----------

    private void assertCreatedEdge(TraceEdge created) {
        assertEquals("case_snapshot", created.getEdgeType());
        assertEquals("test_case", created.getSourceType());
        assertEquals("confirmed", created.getStatus());
        assertEquals("manual", created.getEstablishedBy());
        assertEquals("test_review", created.getTargetType());
        assertEquals(REVIEW_ID, created.getTargetId());
        assertEquals(PROJECT_ID, created.getProjectId());
    }

    private TraceEdge snapshotEdge(UUID caseId) {
        TraceEdge edge = new TraceEdge();
        edge.setId(UUID.randomUUID());
        edge.setProjectId(PROJECT_ID);
        edge.setSourceType("test_case");
        edge.setSourceId(caseId);
        edge.setTargetType("test_review");
        edge.setTargetId(REVIEW_ID);
        return edge;
    }

    private TestCaseNode caseNode(UUID id, int version) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setVersion(version);
        node.setTitle("TC-" + id);
        return node;
    }
}
