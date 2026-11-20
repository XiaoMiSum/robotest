package io.github.xiaomisum.robotest.service.domain.tcasedoc;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.response.requirement.RequirementRefRespDTO;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseRequirementServiceImplTest {

    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private RequirementMapper requirementMapper;

    @InjectMocks
    private TestCaseRequirementServiceImpl service;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
    }

    @Test
    void listRequirementRefs_keepsOrderAndSkipsMissingOrCrossProject() {
        UUID reqA = UUID.randomUUID();
        UUID reqB = UUID.randomUUID();
        UUID reqMissing = UUID.randomUUID();
        Requirement foreign = requirement(UUID.randomUUID(), "REQ-X");
        foreign.setProjectId(UUID.randomUUID());
        when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(
                requirement(reqB, "REQ-10"), requirement(reqA, "REQ-9"), foreign));

        List<RequirementRefRespDTO> result = service.listRequirementRefs(projectId,
                List.of(reqA, reqMissing, reqB, foreign.getId(), reqA));

        assertEquals(List.of(reqA, reqB), result.stream().map(RequirementRefRespDTO::getId).toList());
        assertEquals(List.of("REQ-9", "REQ-10"), result.stream().map(RequirementRefRespDTO::getCode).toList());
    }

    @Test
    void listRequirementRefs_emptyIds_skipsQuery() {
        assertTrue(service.listRequirementRefs(projectId, List.of()).isEmpty());
        verify(requirementMapper, never()).listByIds(anyCollection());
    }

    @Test
    void resolvePlannedCases_groupsCaseNodesByDocument() {
        UUID requirementId = UUID.randomUUID();
        when(requirementMapper.listByIds(anyCollection()))
                .thenReturn(List.of(requirement(requirementId, "REQ-1")));
        UUID docA = UUID.randomUUID();
        UUID docB = UUID.randomUUID();
        UUID caseA1 = UUID.randomUUID();
        UUID caseA2 = UUID.randomUUID();
        UUID caseB = UUID.randomUUID();
        when(testCaseNodeMapper.listCasesByRequirementIds(List.of(requirementId))).thenReturn(List.of(
                node(caseA1, docA), node(caseA2, docA), node(caseB, docB), node(UUID.randomUUID(), null)));

        Map<UUID, Set<UUID>> result = service.resolvePlannedCases(projectId, List.of(requirementId));

        assertEquals(Set.of(caseA1, caseA2), result.get(docA));
        assertEquals(Set.of(caseB), result.get(docB));
        assertEquals(2, result.size());
    }

    @Test
    void resolvePlannedCases_emptyIds_skipsQueries() {
        assertTrue(service.resolvePlannedCases(projectId, List.of()).isEmpty());
        verify(requirementMapper, never()).listByIds(anyCollection());
        verify(testCaseNodeMapper, never()).listCasesByRequirementIds(anyCollection());
    }

    @Test
    void resolvePlannedCases_crossProjectRequirement_throws() {
        UUID requirementId = UUID.randomUUID();
        Requirement foreign = requirement(requirementId, "REQ-1");
        foreign.setProjectId(UUID.randomUUID());
        when(requirementMapper.listByIds(anyCollection())).thenReturn(List.of(foreign));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.resolvePlannedCases(projectId, List.of(requirementId)));

        assertEquals(ErrorCodeConstants.REQUIREMENT_NOT_FOUND.code(), exception.getCode());
        verify(testCaseNodeMapper, never()).listCasesByRequirementIds(anyCollection());
    }

    private Requirement requirement(UUID id, String code) {
        Requirement requirement = new Requirement();
        requirement.setId(id);
        requirement.setCode(code);
        requirement.setTitle("需求 " + code);
        requirement.setProjectId(projectId);
        return requirement;
    }

    private static TestCaseNode node(UUID id, UUID docId) {
        TestCaseNode node = new TestCaseNode();
        node.setId(id);
        node.setDocumentId(docId);
        return node;
    }
}
