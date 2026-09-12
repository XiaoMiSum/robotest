package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPermissionCheckerTest {

    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;

    @InjectMocks
    private SystemPermissionChecker checker;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void codes_multipleRoles_returnsDistinctPermissions() {
        SysUserRole ur1 = new SysUserRole();
        ur1.setRoleId(UUID.randomUUID());
        SysUserRole ur2 = new SysUserRole();
        ur2.setRoleId(UUID.randomUUID());

        when(userRoleMapper.listByUserId(userId)).thenReturn(List.of(ur1, ur2));

        SysRole r1 = new SysRole();
        r1.setPermissions(List.of("user:view", "user:edit"));
        SysRole r2 = new SysRole();
        r2.setPermissions(List.of("user:edit", "role:view"));
        when(roleMapper.listByIds(List.of(ur1.getRoleId(), ur2.getRoleId())))
                .thenReturn(List.of(r1, r2));

        Set<String> codes = checker.codes(userId, null);

        assertEquals(Set.of("user:view", "user:edit", "role:view"), codes);
    }

    @Test
    void codes_nullPermissionsIgnored() {
        SysUserRole ur = new SysUserRole();
        ur.setRoleId(UUID.randomUUID());
        when(userRoleMapper.listByUserId(userId)).thenReturn(List.of(ur));

        SysRole role = new SysRole();
        role.setPermissions(null);
        when(roleMapper.listByIds(List.of(ur.getRoleId()))).thenReturn(List.of(role));

        Set<String> codes = checker.codes(userId, null);

        assertTrue(codes.isEmpty());
    }

    @Test
    void codes_noRoles_returnsEmpty() {
        when(userRoleMapper.listByUserId(userId)).thenReturn(List.of());

        Set<String> codes = checker.codes(userId, null);

        assertTrue(codes.isEmpty());
    }
}