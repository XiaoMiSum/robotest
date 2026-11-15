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
    void saveRequirements_replacesDiff() {
        stubCaseNode(Constants.NodeType.CASE);
        UUID keep = UUID.randomUUID();
        UUID added = UUID.randomUUID();
        UUID removed = UUID.randomUUID();
        UUID removedLinkId = UUID.randomUUID();
        when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(
                requirement(keep, "REQ-2"), requirement(added, "REQ-10")));
        when(caseRequirementMapper.listByCaseNodeId(projectId, caseId)).thenReturn(List.of(
                link(UUID.randomUUID(), keep), link(removedLinkId, removed)));

        List<RequirementRefRespDTO> result = service.saveRequirements(
                projectId, caseId, List.of(keep, added), userId);

        assertEquals(List.of("REQ-2", "REQ-10"), result.stream().map(RequirementRefRespDTO::getCode).toList());
        verify(caseRequirementMapper).deleteById(removedLinkId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TestCaseRequirement>> captor = ArgumentCaptor.forClass(List.class);
        verify(caseRequirementMapper).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(added, captor.getValue().get(0).getRequirementId());
        assertEquals(caseId, captor.getValue().get(0).getCaseNodeId());
        assertEquals(documentId, captor.getValue().get(0).getDocumentId());
    }

    @Test
    void saveRequirements_nonCaseNode_throws() {
        stubCaseNode(Constants.NodeType.STEP);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveRequirements(projectId, caseId, List.of(UUID.randomUUID()), userId));

        assertEquals(ErrorCodeConstants.TEST_CASE_NODE_NOT_FOUND.code(), exception.getCode());
        verify(caseRequirementMapper, never()).insertBatch(any());
    }

    @Test
    void saveRequirements_crossProjectRequirement_throws() {
        stubCaseNode(Constants.NodeType.CASE);
        UUID requirementId = UUID.randomUUID();
        Requirement otherProject = requirement(requirementId, "REQ-1");
        otherProject.setProjectId(UUID.randomUUID());
        when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(otherProject));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveRequirements(projectId, caseId, List.of(requirementId), userId));

        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
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
                link(UUID.randomUUID(), requirementId, caseNode, documentId),
                link(UUID.randomUUID(), requirementId, stepNode, documentId),
                link(UUID.randomUUID(), requirementId, removedNode, documentId)));
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

    private TestCaseRequirement link(UUID id, UUID requirementId) {
        return link(id, requirementId, caseId, documentId);
    }

    private TestCaseRequirement link(UUID id, UUID requirementId, UUID caseNodeId, UUID docId) {
        TestCaseRequirement link = new TestCaseRequirement();
        link.setId(id);
        link.setProjectId(projectId);
        link.setRequirementId(requirementId);
        link.setCaseNodeId(caseNodeId);
        link.setDocumentId(docId);
        return link;
    }
}
