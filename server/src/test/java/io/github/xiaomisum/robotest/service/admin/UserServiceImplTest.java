package io.github.xiaomisum.robotest.service.admin;

import io.github.xiaomisum.robotest.model.entity.SysUser;
import io.github.xiaomisum.robotest.repository.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
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
class UserServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID userId;
    private SysUser user;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        user = new SysUser();
        user.setId(userId);
        user.setUsername("tester");
        user.setPasswordHash("old-hash");
    }

    // ========== changePassword ==========

    @Test
    void changePassword_success() {
        when(userMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("OldPass123!", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass456!")).thenReturn("new-hash");

        userService.changePassword(userId, "OldPass123!", "NewPass456!");

        // 更新载体仅携带 id + 新密码，不再回写查询实体
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(userId, captor.getValue().getId());
        assertEquals("new-hash", captor.getValue().getPasswordHash());
    }

    @Test
    void changePassword_oldPasswordWrong_throws() {
        when(userMapper.selectById(userId)).thenReturn(user);
        when(passwordEncoder.matches("WrongOld!", "old-hash")).thenReturn(false);

        assertThrows(ServiceException.class,
                () -> userService.changePassword(userId, "WrongOld!", "NewPass456!"));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePassword_userNotFound_throws() {
        when(userMapper.selectById(userId)).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> userService.changePassword(userId, "OldPass123!", "NewPass456!"));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }
}
