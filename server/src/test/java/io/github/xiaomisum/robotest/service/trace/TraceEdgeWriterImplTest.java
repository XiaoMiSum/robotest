package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID REQ_A = UUID.randomUUID();
    private static final UUID REQ_B = UUID.randomUUID();

    @Mock
    private TraceEdgeMapper traceEdgeMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private RequirementMapper requirementMapper;

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

    // ---------- 文档关联需求对账（requirement → mindmap_document 反向承载） ----------

    @Test
    void syncDocument_insertsNewLinkAsManualDerivation() {
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "mindmap_document", DOC_ID)).thenReturn(List.of());
        when(requirementMapper.listByIds(Set.of(REQ_A))).thenReturn(List.of(requirement(REQ_A, "REQ-001")));
        when(traceEdgeMapper.insert(any(TraceEdge.class))).thenReturn(1);

        writer.syncDocumentRequirementEdges(PROJECT_ID, DOC_ID, Set.of(REQ_A), OPERATOR_ID);

        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).insert(captor.capture());
        TraceEdge created = captor.getValue();
        assertEquals("derivation", created.getEdgeType());
        assertEquals("requirement", created.getSourceType());
        assertEquals(REQ_A, created.getSourceId());
        assertEquals("mindmap_document", created.getTargetType());
        assertEquals(DOC_ID, created.getTargetId());
        assertEquals(PROJECT_ID, created.getProjectId());
        assertEquals("confirmed", created.getStatus());
        assertEquals("manual", created.getEstablishedBy());
        assertEquals(OPERATOR_ID, created.getConfirmedBy());
        // 文档无版本概念，版本字段不参与比对
        assertNull(created.getTargetVersion());
    }

    @Test
    void syncDocument_removesDeselectedAndKeepsRetained() {
        TraceEdge retained = derivationEdge(REQ_A, "confirmed");
        TraceEdge deselected = derivationEdge(REQ_B, "confirmed");
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "mindmap_document", DOC_ID))
                .thenReturn(List.of(retained, deselected));
        when(requirementMapper.listByIds(Set.of(REQ_A))).thenReturn(List.of(requirement(REQ_A, "REQ-001")));

        writer.syncDocumentRequirementEdges(PROJECT_ID, DOC_ID, Set.of(REQ_A), OPERATOR_ID);

        verify(traceEdgeMapper).deleteById(deselected.getId());
        verify(traceEdgeMapper, never()).deleteById(retained.getId());
        verify(traceEdgeMapper, never()).insert(any(TraceEdge.class));
        verify(traceEdgeMapper, never()).updateById(any(TraceEdge.class));
    }

    @Test
    void syncDocument_restoresDetachedLinkInsteadOfInserting() {
        TraceEdge detached = derivationEdge(REQ_A, "detached");
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "mindmap_document", DOC_ID))
                .thenReturn(List.of(detached));
        when(requirementMapper.listByIds(Set.of(REQ_A))).thenReturn(List.of(requirement(REQ_A, "REQ-001")));

        writer.syncDocumentRequirementEdges(PROJECT_ID, DOC_ID, Set.of(REQ_A), OPERATOR_ID);

        // detached 行占用 uk_trace_edge_pair，重勾选只能恢复既有行
        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).updateById(captor.capture());
        assertEquals(detached.getId(), captor.getValue().getId());
        assertEquals("confirmed", captor.getValue().getStatus());
        assertEquals(OPERATOR_ID, captor.getValue().getConfirmedBy());
        verify(traceEdgeMapper, never()).insert(any(TraceEdge.class));
        verify(traceEdgeMapper, never()).deleteById(any());
    }

    @Test
    void syncDocument_clearAllDeletesEveryLink() {
        TraceEdge confirmed = derivationEdge(REQ_A, "confirmed");
        TraceEdge detached = derivationEdge(REQ_B, "detached");
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "mindmap_document", DOC_ID))
                .thenReturn(List.of(confirmed, detached));

        writer.syncDocumentRequirementEdges(PROJECT_ID, DOC_ID, List.of(), OPERATOR_ID);

        verify(traceEdgeMapper).deleteById(confirmed.getId());
        verify(traceEdgeMapper).deleteById(detached.getId());
        verify(requirementMapper, never()).listByIds(any());
        verify(traceEdgeMapper, never()).insert(any(TraceEdge.class));
    }

    // ---------- AI 派生批量建边（生成链采纳 3.5） ----------

    @Test
    void writeAi_insertsAiCreatedDerivationsWithVersion() {
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "test_case", DOC_ID)).thenReturn(List.of());
        when(traceEdgeMapper.insert(any(TraceEdge.class))).thenReturn(1);

        writer.writeAiDerivationEdges(PROJECT_ID, List.of(REQ_A, REQ_B), "test_case", DOC_ID, "v1");

        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper, times(2)).insert(captor.capture());
        TraceEdge created = captor.getAllValues().get(0);
        assertEquals("derivation", created.getEdgeType());
        assertEquals("requirement", created.getSourceType());
        assertEquals(REQ_A, created.getSourceId());
        assertEquals("test_case", created.getTargetType());
        assertEquals(DOC_ID, created.getTargetId());
        assertEquals("v1", created.getTargetVersion());
        assertEquals("ai_created", created.getStatus());
        assertEquals("ai", created.getEstablishedBy());
        assertNull(created.getConfirmedBy());
        assertEquals(REQ_B, captor.getAllValues().get(1).getSourceId());
    }

    @Test
    void writeAi_skipsExistingPairsIncludingDetached() {
        TraceEdge existing = derivationEdge(REQ_A, "detached");
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "module", DOC_ID)).thenReturn(List.of(existing));

        writer.writeAiDerivationEdges(PROJECT_ID, List.of(REQ_A, REQ_B), "module", DOC_ID, null);

        ArgumentCaptor<TraceEdge> captor = ArgumentCaptor.forClass(TraceEdge.class);
        verify(traceEdgeMapper).insert(captor.capture());
        // detached 不得由 AI 重建，只补缺对；无版本列的实体 target_version 为 null
        assertEquals(REQ_B, captor.getValue().getSourceId());
        assertNull(captor.getValue().getTargetVersion());
    }

    @Test
    void writeAi_emptySourcesWriteNothing_andDuplicateSwallowed() {
        writer.writeAiDerivationEdges(PROJECT_ID, List.of(), "module", DOC_ID, null);
        writer.writeAiDerivationEdges(PROJECT_ID, null, "module", DOC_ID, null);
        verify(traceEdgeMapper, never()).insert(any(TraceEdge.class));

        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "test_case", DOC_ID)).thenReturn(List.of());
        when(traceEdgeMapper.insert(any(TraceEdge.class))).thenThrow(new DuplicateKeyException("uk_trace_edge_pair"));
        assertDoesNotThrow(() -> writer.writeAiDerivationEdges(PROJECT_ID, List.of(REQ_A), "test_case", DOC_ID, "v1"));
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

    private TraceEdge derivationEdge(UUID sourceId, String status) {
        TraceEdge edge = new TraceEdge();
        edge.setId(UUID.randomUUID());
        edge.setProjectId(PROJECT_ID);
        edge.setEdgeType("derivation");
        edge.setSourceType("requirement");
        edge.setSourceId(sourceId);
        edge.setTargetType("mindmap_document");
        edge.setTargetId(DOC_ID);
        edge.setStatus(status);
        return edge;
    }

    private Requirement requirement(UUID id, String code) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setProjectId(PROJECT_ID);
        requirement.setCode(code);
        requirement.setTitle("标题-" + code);
        return requirement;
    }
}
