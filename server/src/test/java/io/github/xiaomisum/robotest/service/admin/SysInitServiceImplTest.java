package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.admin.InitSetupReqDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysUserRole;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.admin.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysInitServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SysInitServiceImpl sysInitService;

    @Test
    void setup_createsAdminAndRole() {
        when(userMapper.selectCount(null)).thenReturn(0L);
        when(passwordEncoder.encode("SecurePass1!")).thenReturn("hashed");

        sysInitService.setup(new InitSetupReqDTO() {{ setPassword("SecurePass1!"); }});

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(userCaptor.capture());
        SysUser admin = userCaptor.getValue();
        assertEquals("admin", admin.getUsername());
        assertEquals(Constants.Status.ACTIVE, admin.getStatus());
        assertEquals("hashed", admin.getPasswordHash());

        ArgumentCaptor<SysUserRole> roleCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleMapper).insert(roleCaptor.capture());
        assertEquals(UUID.fromString("b0000000-0000-0000-0000-000000000001"), roleCaptor.getValue().getRoleId());
    }

    @Test
    void setup_alreadyInitialized_throws() {
        when(userMapper.selectCount(null)).thenReturn(1L);

        assertThrows(ServiceException.class,
                () -> sysInitService.setup(new InitSetupReqDTO() {{ setPassword("SecurePass1!"); }}));

        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void isInitialized_trueWhenAdminExists() {
        when(userMapper.selectCount(null)).thenReturn(3L);

        assertTrue(sysInitService.isInitialized());
    }

    @Test
    void isInitialized_falseWhenEmpty() {
        when(userMapper.selectCount(null)).thenReturn(0L);

        assertFalse(sysInitService.isInitialized());
    }
}