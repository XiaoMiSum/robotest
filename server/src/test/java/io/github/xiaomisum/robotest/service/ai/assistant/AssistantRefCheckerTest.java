package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.entity.requirement.Requirement;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.requirement.RequirementMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseNodeMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRefCheckerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    @Mock
    private RequirementMapper requirementMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private TestCaseNodeMapper testCaseNodeMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;

    @InjectMocks
    private AssistantRefChecker checker;

    @Test
    void workspaceOf_returnsNull_whenTypeOrIdAbsent() {
        assertNull(checker.workspaceOf(null, NODE_ID));
        assertNull(checker.workspaceOf(Constants.TraceNodeType.TEST_CASE, null));
    }

    @Test
    void workspaceOf_resolvesRequirementThroughProject() {
        Requirement requirement = new Requirement();
        requirement.setProjectId(PROJECT_ID);
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirement);
        Project project = new Project();
        project.setWorkspaceId(WORKSPACE_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        assertEquals(WORKSPACE_ID, checker.workspaceOf(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID));

        verifyNoInteractions(testCaseNodeMapper, testCaseDocumentMapper);
    }

    @Test
    void workspaceOf_resolvesTestCaseThroughDocumentAndProject() {
        TestCaseNode node = new TestCaseNode();
        node.setDocumentId(DOCUMENT_ID);
        when(testCaseNodeMapper.selectById(NODE_ID)).thenReturn(node);
        TestCaseDocument document = new TestCaseDocument();
        document.setProjectId(PROJECT_ID);
        when(testCaseDocumentMapper.selectById(DOCUMENT_ID)).thenReturn(document);
        Project project = new Project();
        project.setWorkspaceId(WORKSPACE_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        assertEquals(WORKSPACE_ID, checker.workspaceOf(Constants.TraceNodeType.TEST_CASE, NODE_ID));

        verifyNoInteractions(requirementMapper);
    }

    @Test
    void workspaceOf_returnsNull_whenReferenceChainBroken() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(null);
        assertNull(checker.workspaceOf(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID));

        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(new Requirement());
        assertNull(checker.workspaceOf(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID));

        // 未覆盖的类型不臆测归属，交由调用方按“无法判定可见”处理
        assertNull(checker.workspaceOf("unknown_type", NODE_ID));
    }

    @Test
    void requireUserVisible_throwsWithoutWorkspace() {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> checker.requireUserVisible(Constants.TraceNodeType.MINDMAP_DOCUMENT, NODE_ID, USER_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID.code(), exception.getCode());
        verifyNoInteractions(workspaceUserMapper);
    }

    @Test
    void requireUserVisible_throwsWhenNotMember() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirementOfProject());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(projectOfWorkspace());
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> checker.requireUserVisible(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID, USER_ID));

        assertEquals(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID.code(), exception.getCode());
    }

    @Test
    void requireUserVisible_passesWhenMember() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirementOfProject());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(projectOfWorkspace());
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(true);

        checker.requireUserVisible(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID, USER_ID);

        verify(workspaceUserMapper).existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID);
    }

    @Test
    void requireInActiveWorkspace_passesWhenWorkspaceMatches() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirementOfProject());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(projectOfWorkspace());

        checker.requireInActiveWorkspace(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID, WORKSPACE_ID);
    }

    @Test
    void requireInActiveWorkspace_rejectsMissingOrForeign() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(null);
        ServiceException missing = assertThrows(ServiceException.class, () -> checker
                .requireInActiveWorkspace(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID, WORKSPACE_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID.code(), missing.getCode());

        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirementOfProject());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(projectOfWorkspace());
        ServiceException foreign = assertThrows(ServiceException.class, () -> checker
                .requireInActiveWorkspace(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID,
                        UUID.randomUUID()));
        assertEquals(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID.code(), foreign.getCode());
    }

    @Test
    void requireWorkspaceMember_rejectsNullWorkspaceAndNonMember() {
        ServiceException noWorkspace = assertThrows(ServiceException.class,
                () -> checker.requireWorkspaceMember(null, USER_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), noWorkspace.getCode());
        verifyNoInteractions(workspaceUserMapper);

        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(false);
        ServiceException nonMember = assertThrows(ServiceException.class,
                () -> checker.requireWorkspaceMember(WORKSPACE_ID, USER_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), nonMember.getCode());

        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(true);
        checker.requireWorkspaceMember(WORKSPACE_ID, USER_ID);
    }

    @Test
    void requireActiveWorkspaceRef_passesMatchAndRejectsForeignWithSameCode() {
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirementOfProject());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(projectOfWorkspace());

        checker.requireActiveWorkspaceRef(WORKSPACE_ID, Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID);

        ServiceException foreign = assertThrows(ServiceException.class, () -> checker
                .requireActiveWorkspaceRef(UUID.randomUUID(), Constants.TraceNodeType.REQUIREMENT,
                        REQUIREMENT_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), foreign.getCode());

        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(null);
        ServiceException missing = assertThrows(ServiceException.class, () -> checker
                .requireActiveWorkspaceRef(WORKSPACE_ID, Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), missing.getCode());
    }

    @Test
    void requireActiveWorkspaceProject_passesMatchAndRejectsMissingOrForeign() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(projectOfWorkspace());
        checker.requireActiveWorkspaceProject(WORKSPACE_ID, PROJECT_ID);

        when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);
        ServiceException missing = assertThrows(ServiceException.class,
                () -> checker.requireActiveWorkspaceProject(WORKSPACE_ID, PROJECT_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), missing.getCode());

        when(projectMapper.selectById(PROJECT_ID)).thenReturn(projectOfWorkspace());
        ServiceException foreign = assertThrows(ServiceException.class, () -> checker
                .requireActiveWorkspaceProject(UUID.randomUUID(), PROJECT_ID));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), foreign.getCode());
    }

    @Test
    void requireActiveWorkspaceCases_returnsProjectSetWhenChainInsideWorkspace() {
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of(caseNode()));
        when(testCaseDocumentMapper.listByIds(any())).thenReturn(List.of(document()));
        when(projectMapper.listByIds(any())).thenReturn(List.of(projectOfWorkspace()));

        Set<UUID> projectIds = checker.requireActiveWorkspaceCases(WORKSPACE_ID, Set.of(NODE_ID));

        assertEquals(Set.of(PROJECT_ID), projectIds);
    }

    @Test
    void requireActiveWorkspaceCases_passesEmptyInputWithoutQueries() {
        assertEquals(Set.of(), checker.requireActiveWorkspaceCases(WORKSPACE_ID, Set.of()));
        assertEquals(Set.of(), checker.requireActiveWorkspaceCases(WORKSPACE_ID, null));
        verifyNoInteractions(testCaseNodeMapper, testCaseDocumentMapper, projectMapper);
    }

    @Test
    void requireActiveWorkspaceCases_rejectsMissingRowOrForeignProject() {
        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of());
        ServiceException missingRow = assertThrows(ServiceException.class, () -> checker
                .requireActiveWorkspaceCases(WORKSPACE_ID, Set.of(NODE_ID)));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), missingRow.getCode());

        when(testCaseNodeMapper.listByIds(any())).thenReturn(List.of(caseNode()));
        when(testCaseDocumentMapper.listByIds(any())).thenReturn(List.of(document()));
        when(projectMapper.listByIds(any())).thenReturn(List.of(projectOfWorkspace()));
        ServiceException foreign = assertThrows(ServiceException.class, () -> checker
                .requireActiveWorkspaceCases(UUID.randomUUID(), Set.of(NODE_ID)));
        assertEquals(ErrorCodeConstants.ASSISTANT_CROSS_WORKSPACE.code(), foreign.getCode());
    }

    @Test
    void titleOf_resolvesPerEntityOrNullWhenUnknown() {
        Requirement requirement = requirementOfProject();
        requirement.setCode("REQ-001");
        requirement.setTitle("登录");
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(requirement);
        assertEquals("REQ-001", checker.titleOf(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID));

        Requirement noCode = requirementOfProject();
        noCode.setTitle("登录");
        when(requirementMapper.selectById(REQUIREMENT_ID)).thenReturn(noCode);
        assertEquals("登录", checker.titleOf(Constants.TraceNodeType.REQUIREMENT, REQUIREMENT_ID));

        when(testCaseNodeMapper.selectById(NODE_ID)).thenReturn(caseNode());
        assertEquals("验证码正确可登录", checker.titleOf(Constants.TraceNodeType.TEST_CASE, NODE_ID));

        assertNull(checker.titleOf("unknown_type", NODE_ID));
        assertNull(checker.titleOf(null, NODE_ID));
    }

    private Requirement requirementOfProject() {
        Requirement requirement = new Requirement();
        requirement.setProjectId(PROJECT_ID);
        return requirement;
    }

    private static TestCaseNode caseNode() {
        TestCaseNode node = new TestCaseNode();
        node.setId(NODE_ID);
        node.setDocumentId(DOCUMENT_ID);
        node.setTitle("验证码正确可登录");
        return node;
    }

    private static TestCaseDocument document() {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOCUMENT_ID);
        document.setProjectId(PROJECT_ID);
        return document;
    }

    private Project projectOfWorkspace() {
        Project project = new Project();
        project.setWorkspaceId(WORKSPACE_ID);
        return project;
    }
}
