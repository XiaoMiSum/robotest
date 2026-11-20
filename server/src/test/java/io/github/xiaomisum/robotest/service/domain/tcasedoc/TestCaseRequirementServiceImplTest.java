package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseRequirement;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseRequirementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseRequirementServiceImplTest {

    @Mock
    private TestCaseRequirementMapper caseRequirementMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private ProjectAccessGuard projectAccessGuard;

    @InjectMocks
    private TestCaseRequirementServiceImpl service;

    private UUID projectId;
    private UUID userId;
    private UUID caseId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        documentId = UUID.randomUUID();
    }

    @Test
    void saveRequirements_updateExistingLink() {
        stubCaseNode(Constants.NodeType.CASE);
        UUID requirementId = UUID.randomUUID();
        stubRequirement(requirementId);
        when(caseRequirementMapper.updateRequirementByCaseNode(projectId, caseId, requirementId)).thenReturn(1);

        List<RequirementRefRespDTO> result = service.saveRequirements(projectId, caseId, requirementId, userId);

        assertEquals(List.of("REQ-1"), result.stream().map(RequirementRefRespDTO::getCode).toList());
        verify(caseRequirementMapper).updateRequirementByCaseNode(projectId, caseId, requirementId);
        verify(caseRequirementMapper, never()).insert(any(TestCaseRequirement.class));
        verify(caseRequirementMapper, never()).deleteByCaseNodeId(any(), any());
    }

    @Test
    void saveRequirements_insertWhenNoExistingLink() {
        stubCaseNode(Constants.NodeType.CASE);
        UUID requirementId = UUID.randomUUID();
        stubRequirement(requirementId);
        when(caseRequirementMapper.updateRequirementByCaseNode(projectId, caseId, requirementId)).thenReturn(0);

        List<RequirementRefRespDTO> result = service.saveRequirements(projectId, caseId, requirementId, userId);

        assertEquals(List.of("REQ-1"), result.stream().map(RequirementRefRespDTO::getCode).toList());
        ArgumentCaptor<TestCaseRequirement> captor = ArgumentCaptor.forClass(TestCaseRequirement.class);
        verify(caseRequirementMapper).insert(captor.capture());
        assertEquals(projectId, captor.getValue().getProjectId());
        assertEquals(requirementId, captor.getValue().getRequirementId());
        assertEquals(caseId, captor.getValue().getCaseNodeId());
        assertEquals(documentId, captor.getValue().getDocumentId());
    }

    @Test
    void saveRequirements_nullClearsLink() {
        stubCaseNode(Constants.NodeType.CASE);

        List<RequirementRefRespDTO> result = service.saveRequirements(projectId, caseId, null, userId);

        assertTrue(result.isEmpty());
        verify(caseRequirementMapper).deleteByCaseNodeId(projectId, caseId);
        verify(caseRequirementMapper, never()).updateRequirementByCaseNode(any(), any(), any());
        verify(caseRequirementMapper, never()).insert(any(TestCaseRequirement.class));
        verifyNoInteractions(requirementMapper);
    }

    @Test
    void saveRequirements_idempotentWhenSameRequirement() {
        stubCaseNode(Constants.NodeType.CASE);
        UUID requirementId = UUID.randomUUID();
        stubRequirement(requirementId);
        when(caseRequirementMapper.updateRequirementByCaseNode(projectId, caseId, requirementId)).thenReturn(1);

        service.saveRequirements(projectId, caseId, requirementId, userId);
        service.saveRequirements(projectId, caseId, requirementId, userId);

        verify(caseRequirementMapper, times(2)).updateRequirementByCaseNode(projectId, caseId, requirementId);
        verify(caseRequirementMapper, never()).insert(any(TestCaseRequirement.class));
    }

    @Test
    void saveRequirements_nonCaseNode_throws() {
        stubCaseNode(Constants.NodeType.STEP);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveRequirements(projectId, caseId, UUID.randomUUID(), userId));

        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), exception.getCode());
        verify(caseRequirementMapper, never()).insert(any(TestCaseRequirement.class));
    }

    @Test
    void saveRequirements_crossProjectRequirement_throws() {
        stubCaseNode(Constants.NodeType.CASE);
        UUID requirementId = UUID.randomUUID();
        Requirement otherProject = requirement(requirementId, "REQ-1");
        otherProject.setProjectId(UUID.randomUUID());
        when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(otherProject));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveRequirements(projectId, caseId, requirementId, userId));

        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
        verify(caseRequirementMapper, never()).updateRequirementByCaseNode(any(), any(), any());
        verify(caseRequirementMapper, never()).insert(any(TestCaseRequirement.class));
    }

    @Test
    void resolvePlannedCases_keepsOnlyExistingCaseNodes() {
        UUID requirementId = UUID.randomUUID();
        when(requirementMapper.listByIds(anyCollection()))
                .thenReturn(List.of(requirement(requirementId, "REQ-1")));
        UUID caseNode = UUID.randomUUID();
        UUID stepNode = UUID.randomUUID();
        UUID removedNode = UUID.randomUUID();
        when(caseRequirementMapper.listByRequirementIds(projectId, List.of(requirementId))).thenReturn(List.of(
                link(requirementId, caseNode, documentId),
                link(requirementId, stepNode, documentId),
                link(requirementId, removedNode, documentId)));
        when(testCaseNodeMapper.listByIds(anyCollection())).thenReturn(List.of(
                node(caseNode, documentId, Constants.NodeType.CASE),
                node(stepNode, documentId, Constants.NodeType.STEP)));

        Map<UUID, Set<UUID>> result = service.resolvePlannedCases(projectId, List.of(requirementId));

        assertEquals(Set.of(caseNode), result.get(documentId));
    }

    private void stubCaseNode(String type) {
        TestCaseNode node = node(caseId, documentId, type);
        when(testCaseNodeMapper.selectById(caseId)).thenReturn(node);
        TestCaseDocument document = new TestCaseDocument();
        document.setId(documentId);
        document.setProjectId(projectId);
        when(testCaseDocumentMapper.selectById(documentId)).thenReturn(document);
    }

    private void stubRequirement(UUID requirementId) {
        when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(requirement(requirementId, "REQ-1")));
    }

    private static TestCaseNode node(UUID id, UUID docId, String type) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setDocumentId(docId);
        node.setType(type);
        return node;
    }

    private Requirement requirement(UUID id, String code) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setCode(code);
        requirement.setTitle("需求 " + code);
        requirement.setProjectId(projectId);
        return requirement;
    }

    private TestCaseRequirement link(UUID requirementId, UUID caseNodeId, UUID docId) {
        TestCaseRequirement link = new TestCaseRequirement();
        link.setProjectId(projectId);
        link.setRequirementId(requirementId);
        link.setCaseNodeId(caseNodeId);
        link.setDocumentId(docId);
        return link;
    }
}
