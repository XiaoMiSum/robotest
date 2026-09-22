package io.github.xiaomisum.robotest.service.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionFacadeImplTest {

    @Mock
    private SystemPermissionChecker systemChecker;
    @Mock
    private WorkspacePermissionChecker workspaceChecker;

    private PermissionFacadeImpl facade;
    private UUID userId;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        facade = new PermissionFacadeImpl(List.of(systemChecker, workspaceChecker));
        userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
    }

    @Test
    void permissionsOf_systemRoutesToSystemChecker() {
        when(systemChecker.codes(userId, null)).thenReturn(Set.of("code_a"));

        Set<String> result = facade.permissionsOf(userId, PermissionScope.SYSTEM, null);

        assertEquals(Set.of("code_a"), result);
    }

    @Test
    void permissionsOf_workspaceRoutesToWorkspaceChecker() {
        when(workspaceChecker.codes(userId, workspaceId)).thenReturn(Set.of("code_b"));

        Set<String> result = facade.permissionsOf(userId, PermissionScope.WORKSPACE, workspaceId);

        assertEquals(Set.of("code_b"), result);
    }
}