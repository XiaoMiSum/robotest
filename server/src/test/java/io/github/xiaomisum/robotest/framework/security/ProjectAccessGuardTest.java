package io.github.xiaomisum.robotest.framework.security;

import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.tcase.TestCaseDocumentMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTest {

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private TestCaseDocumentMapper testCaseDocumentMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;

    @InjectMocks
    private ProjectAccessGuard guard;

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(PROJECT_ID);
        project.setWorkspaceId(WORKSPACE_ID);
    }

    // ========== requireProjectMember ==========

    @Test
    void requireProjectMember_member_doesNotThrow() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(true);

        guard.requireProjectMember(PROJECT_ID, USER_ID);

        verify(workspaceUserMapper).existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID);
    }

    @Test
    void requireProjectMember_projectNotFound_throws() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> guard.requireProjectMember(PROJECT_ID, USER_ID));
        verify(workspaceUserMapper, never()).existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID);
    }

    @Test
    void requireProjectMember_notWorkspaceMember_throws() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(false);

        assertThrows(ServiceException.class,
                () -> guard.requireProjectMember(PROJECT_ID, USER_ID));
    }

    // ========== requireProjectMember（工作空间上下文重载） ==========

    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000098");

    @Test
    void requireProjectMemberWithContext_member_doesNotThrow() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(true);

        guard.requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID);
    }

    @Test
    void requireProjectMemberWithContext_projectNotInWorkspace_throwsNotFound() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        assertThrows(ServiceException.class,
                () -> guard.requireProjectMember(PROJECT_ID, OTHER_WORKSPACE_ID, USER_ID));
        verify(workspaceUserMapper, never()).existsByWorkspaceIdAndUserId(OTHER_WORKSPACE_ID, USER_ID);
    }

    @Test
    void requireProjectMemberWithContext_notMember_throws() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.existsByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(false);

        assertThrows(ServiceException.class,
                () -> guard.requireProjectMember(PROJECT_ID, WORKSPACE_ID, USER_ID));
    }

    // ========== 系统身份直通（定时任务详细设计 4.3：调度触发不校验成员关系） ==========

    @Test
    void requireProjectMember_systemOperator_bypassesAllChecks() {
        guard.requireProjectMember(PROJECT_ID, ProjectAccessGuard.SYSTEM_OPERATOR_ID);
        verifyNoInteractions(projectMapper, workspaceUserMapper);
    }

    @Test
    void requireProjectMemberWithContext_systemOperator_bypassesAllChecks() {
        guard.requireProjectMember(PROJECT_ID, OTHER_WORKSPACE_ID, ProjectAccessGuard.SYSTEM_OPERATOR_ID);
        verifyNoInteractions(projectMapper, workspaceUserMapper);
    }

    @Test
    void systemOperatorId_isAllZeroUuid() {
        assertEquals(new UUID(0L, 0L), ProjectAccessGuard.SYSTEM_OPERATOR_ID);
    }

    // ========== isDocumentMember ==========

    @Test
    void isDocumentMember_nullArguments_returnsFalse() {
        assertFalse(guard.isDocumentMember(null, USER_ID.toString()));
        assertFalse(guard.isDocumentMember(DOC_ID, null));
    }

    @Test
    void isDocumentMember_moduleNotFound_returnsFalse() {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(null);

        assertFalse(guard.isDocumentMember(DOC_ID, USER_ID.toString()));
    }

    @Test
    void isDocumentMember_moduleNotDocumentType_returnsFalse() {
        // TestCaseDocument 无 type 字段（恒为文档），该场景等价于文档不存在
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(null);

        assertFalse(guard.isDocumentMember(DOC_ID, USER_ID.toString()));
    }

    @Test
    void isDocumentMember_projectNotFound_returnsFalse() {
        TestCaseDocument document = documentModule();
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);

        assertFalse(guard.isDocumentMember(DOC_ID, USER_ID.toString()));
    }

    @Test
    void isDocumentMember_malformedUserId_returnsFalse() {
        TestCaseDocument document = documentModule();
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        // 非法 UUID 的会话用户 ID 视为无权限，不得抛异常
        assertFalse(guard.isDocumentMember(DOC_ID, "not-a-uuid"));
    }

    @Test
    void isDocumentMember_member_returnsTrue() {
        TestCaseDocument document = documentModule();
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(memberRow());

        assertTrue(guard.isDocumentMember(DOC_ID, USER_ID.toString()));
    }

    @Test
    void isDocumentMember_notWorkspaceMember_returnsFalse() {
        TestCaseDocument document = documentModule();
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(null);

        assertFalse(guard.isDocumentMember(DOC_ID, USER_ID.toString()));
    }

    // ========== hasDocumentEditPermission（WS 可写帧转发/持久化前校验，SEC-004） ==========

    @Test
    void hasDocumentEditPermission_nullArguments_returnsFalse() {
        assertFalse(guard.hasDocumentEditPermission(null, USER_ID.toString()));
        assertFalse(guard.hasDocumentEditPermission(DOC_ID, null));
    }

    @Test
    void hasDocumentEditPermission_memberWithCaseEdit_returnsTrue() {
        stubDocumentMemberChain(memberRow());
        when(sysRoleMapper.selectById(ROLE_ID)).thenReturn(roleWith(List.of("case:view", "case:edit")));

        assertTrue(guard.hasDocumentEditPermission(DOC_ID, USER_ID.toString()));
    }

    @Test
    void hasDocumentEditPermission_memberWithoutCaseEdit_returnsFalse() {
        stubDocumentMemberChain(memberRow());
        when(sysRoleMapper.selectById(ROLE_ID)).thenReturn(roleWith(List.of("case:view")));

        assertFalse(guard.hasDocumentEditPermission(DOC_ID, USER_ID.toString()));
    }

    @Test
    void hasDocumentEditPermission_memberWithoutRole_returnsFalse() {
        WorkspaceUser noRole = memberRow();
        noRole.setWorkspaceRole(null);
        stubDocumentMemberChain(noRole);

        assertFalse(guard.hasDocumentEditPermission(DOC_ID, USER_ID.toString()));
        verifyNoInteractions(sysRoleMapper);
    }

    @Test
    void hasDocumentEditPermission_roleRowMissing_returnsFalse() {
        stubDocumentMemberChain(memberRow());
        when(sysRoleMapper.selectById(ROLE_ID)).thenReturn(null);

        assertFalse(guard.hasDocumentEditPermission(DOC_ID, USER_ID.toString()));
    }

    @Test
    void hasDocumentEditPermission_notWorkspaceMember_returnsFalse() {
        TestCaseDocument document = documentModule();
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(document);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(null);

        assertFalse(guard.hasDocumentEditPermission(DOC_ID, USER_ID.toString()));
        verifyNoInteractions(sysRoleMapper);
    }

    private void stubDocumentMemberChain(WorkspaceUser member) {
        when(testCaseDocumentMapper.selectById(DOC_ID)).thenReturn(documentModule());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(WORKSPACE_ID, USER_ID)).thenReturn(member);
    }

    private WorkspaceUser memberRow() {
        WorkspaceUser member = new WorkspaceUser();
        member.setUserId(USER_ID);
        member.setWorkspaceId(WORKSPACE_ID);
        member.setWorkspaceRole(ROLE_ID);
        return member;
    }

    private SysRole roleWith(List<String> permissions) {
        SysRole role = new SysRole();
        role.setId(ROLE_ID);
        role.setPermissions(permissions);
        return role;
    }

    private TestCaseDocument documentModule() {
        TestCaseDocument document = new TestCaseDocument();
        document.setId(DOC_ID);
        document.setProjectId(PROJECT_ID);
        return document;
    }
}
