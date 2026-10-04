package io.github.xiaomisum.robotest.service.trace;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementSummaryRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.trace.TraceEdge;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.trace.TraceEdgeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档关联需求单测：反查口径（排除 detached、按编号排序）、越权与校验（C8）。
 */
@ExtendWith(MockitoExtension.class)
class DocumentRequirementServiceImplTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID REQ_A = UUID.randomUUID();
    private static final UUID REQ_B = UUID.randomUUID();

    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private TraceEdgeMapper traceEdgeMapper;
    @Mock
    private TraceEdgeWriter traceEdgeWriter;

    @InjectMocks
    private DocumentRequirementServiceImpl service;

    // ---------- list ----------

    @Test
    void list_returnsLinkedRequirementsSortedByCodeAndExcludesDetached() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "mindmap_document", DOC_ID))
                .thenReturn(List.of(derivation(REQ_B, "confirmed"), derivation(REQ_A, "confirmed"),
                        derivation(REQ_A, "detached")));
        when(requirementMapper.listByIds(anyCollection()))
                .thenReturn(List.of(requirement(REQ_A, "REQ-001"), requirement(REQ_B, "REQ-002")));

        List<RequirementSummaryRespDTO> result = service.list(PROJECT_ID, DOC_ID);

        assertEquals(2, result.size());
        assertEquals("REQ-001", result.get(0).getCode());
        assertEquals(REQ_A, result.get(0).getId());
        assertEquals("标题-001", result.get(0).getTitle());
        assertEquals("REQ-002", result.get(1).getCode());
    }

    @Test
    void list_returnsEmptyWithoutQueryingRequirements() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        when(traceEdgeMapper.listDerivationsTo(PROJECT_ID, "mindmap_document", DOC_ID)).thenReturn(List.of());

        assertEquals(List.of(), service.list(PROJECT_ID, DOC_ID));
        verify(requirementMapper, never()).listByIds(any());
    }

    @Test
    void list_missingDocumentThrowsNotFound() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.list(PROJECT_ID, DOC_ID));
        assertEquals(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND.code(), exception.getCode());
        verify(traceEdgeMapper, never()).listDerivationsTo(any(), any(), any());
    }

    @Test
    void list_foreignDocumentThrowsNotFound() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(UUID.randomUUID()));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.list(PROJECT_ID, DOC_ID));
        assertEquals(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND.code(), exception.getCode());
    }

    // ---------- save ----------

    @Test
    void save_syncsSelectionAndReturnsSortedSummaries() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        when(requirementMapper.listByIds(anyCollection()))
                .thenReturn(List.of(requirement(REQ_B, "REQ-002"), requirement(REQ_A, "REQ-001")));

        List<RequirementSummaryRespDTO> result = service.save(PROJECT_ID, DOC_ID,
                List.of(REQ_B, REQ_A, REQ_A), USER_ID);

        // 入参去重保序交给对账，回显按编号升序
        verify(traceEdgeWriter).syncDocumentRequirementEdges(PROJECT_ID, DOC_ID, List.of(REQ_B, REQ_A), USER_ID);
        assertEquals(2, result.size());
        assertEquals("REQ-001", result.get(0).getCode());
        assertEquals("REQ-002", result.get(1).getCode());
    }

    @Test
    void save_emptySelectionClearsLinks() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));

        List<RequirementSummaryRespDTO> result = service.save(PROJECT_ID, DOC_ID, null, USER_ID);

        verify(traceEdgeWriter).syncDocumentRequirementEdges(eq(PROJECT_ID), eq(DOC_ID), eq(List.of()),
                eq(USER_ID));
        assertEquals(List.of(), result);
        verify(requirementMapper, never()).listByIds(any());
    }

    @Test
    void save_unknownRequirementThrowsAndSkipsWrite() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        when(requirementMapper.listByIds(anyCollection()))
                .thenReturn(List.of(requirement(REQ_A, "REQ-001")));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.save(PROJECT_ID, DOC_ID, List.of(REQ_A, REQ_B), USER_ID));

        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
        verify(traceEdgeWriter, never()).syncDocumentRequirementEdges(any(), any(), any(), any());
    }

    @Test
    void save_foreignRequirementThrows() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        when(requirementMapper.listByIds(anyCollection())).thenAnswer(invocation -> {
            Requirement foreign = requirement(REQ_A, "REQ-001");
            foreign.setProjectId(UUID.randomUUID());
            return List.of(foreign);
        });

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.save(PROJECT_ID, DOC_ID, List.of(REQ_A), USER_ID));

        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
        verify(traceEdgeWriter, never()).syncDocumentRequirementEdges(any(), any(), any(), any());
    }

    @Test
    void save_missingDocumentThrowsBeforeAnyWrite() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.save(PROJECT_ID, DOC_ID, List.of(REQ_A), USER_ID));

        assertEquals(ErrorCodeConstants.TEST_CASE_DOCUMENT_NOT_FOUND.code(), exception.getCode());
        verify(traceEdgeWriter, never()).syncDocumentRequirementEdges(any(), any(), any(), any());
    }

    @Test
    void save_argumentOrderPassedThroughToWriter() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document(PROJECT_ID));
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(requirement(REQ_A, "REQ-001")));

        service.save(PROJECT_ID, DOC_ID, List.of(REQ_A), USER_ID);

        verify(traceEdgeWriter).syncDocumentRequirementEdges(eq(PROJECT_ID), eq(DOC_ID), captor.capture(),
                eq(USER_ID));
        assertEquals(List.of(REQ_A), captor.getValue());
    }

    // ---------- 构造辅助 ----------

    private TestCaseDocument document(UUID projectId) {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOC_ID);
        document.setProjectId(projectId);
        document.setName("登录脑图");
        return document;
    }

    private Requirement requirement(UUID id, String code) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setProjectId(PROJECT_ID);
        requirement.setCode(code);
        requirement.setTitle("标题-" + code.substring(4));
        return requirement;
    }

    private TraceEdge derivation(UUID sourceId, String status) {
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
}
