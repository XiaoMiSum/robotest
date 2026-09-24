package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspacePermissionCheckerTest {

    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysRoleMapper roleMapper;

    @InjectMocks
    private WorkspacePermissionChecker checker;

    private UUID userId;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
    }

    @Test
    void codes_withValidMembership_returnsPermissions() {
        WorkspaceUser wu = new WorkspaceUser();
        wu.setUserId(userId);
        wu.setWorkspaceId(workspaceId);
        UUID roleId = UUID.randomUUID();
        wu.setWorkspaceRole(roleId);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(wu);

        SysRole role = new SysRole();
        role.setPermissions(Set.of("bug:edit", "bug:view").stream().toList());
        when(roleMapper.selectById(roleId)).thenReturn(role);

        Set<String> codes = checker.codes(userId, workspaceId);

        assertEquals(Set.of("bug:edit", "bug:view"), codes);
    }

    @Test
    void codes_nullWorkspaceId_returnsEmpty() {
        assertTrue(checker.codes(userId, null).isEmpty());
    }

    @Test
    void codes_noMembership_returnsEmpty() {
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(null);

        assertTrue(checker.codes(userId, workspaceId).isEmpty());
    }

    @Test
    void codes_roleMissing_returnsEmpty() {
        WorkspaceUser wu = new WorkspaceUser();
        wu.setWorkspaceRole(UUID.randomUUID());
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(wu);
        when(roleMapper.selectById(wu.getWorkspaceRole())).thenReturn(null);

        assertTrue(checker.codes(userId, workspaceId).isEmpty());
    }

    @Test
    void codes_workspaceRoleNull_returnsEmpty() {
        WorkspaceUser wu = new WorkspaceUser();
        wu.setWorkspaceRole(null);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(wu);

        assertTrue(checker.codes(userId, workspaceId).isEmpty());
    }

    @Test
    void codes_nullPermissionsInRole_returnsEmpty() {
        WorkspaceUser wu = new WorkspaceUser();
        UUID roleId = UUID.randomUUID();
        wu.setWorkspaceRole(roleId);
        when(workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(wu);

        SysRole role = new SysRole();
        role.setPermissions(null);
        when(roleMapper.selectById(roleId)).thenReturn(role);

        assertTrue(checker.codes(userId, workspaceId).isEmpty());
    }
}