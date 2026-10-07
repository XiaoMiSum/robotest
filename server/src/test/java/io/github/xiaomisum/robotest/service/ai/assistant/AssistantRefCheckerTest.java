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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private Requirement requirementOfProject() {
        Requirement requirement = new Requirement();
        requirement.setProjectId(PROJECT_ID);
        return requirement;
    }

    private Project projectOfWorkspace() {
        Project project = new Project();
        project.setWorkspaceId(WORKSPACE_ID);
        return project;
    }
}
