package io.github.xiaomisum.robotest.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceRoleInterceptorTest {

    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysRoleMapper roleMapper;

    @InjectMocks
    private WorkspaceRoleInterceptor interceptor;

    private LoginUser loginUser;
    private UUID userId;
    private String workspaceId;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        workspaceId = "ws-001";

        loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setUsername("testuser");
        loginUser.setName("testuser");
        loginUser.setAuthorities(Collections.emptyList());
        loginUser.setWorkspaceAuthorities(new ArrayList<>());

        // 设置 SecurityContext
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void preHandle_noLoginUser_returnsTrue() {
        // given — override the SecurityContext set in setUp
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Active-Workspace", workspaceId);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    @Test
    void preHandle_noWorkspaceHeader_returnsTrue() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    @Test
    void preHandle_userNotInWorkspace_returnsTrue() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Active-Workspace", workspaceId);

        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    @Test
    void preHandle_workspaceUserNoRole_returnsTrue() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Active-Workspace", workspaceId);

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(null);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    @Test
    void preHandle_roleNotFound_returnsTrue() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Active-Workspace", workspaceId);

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);
        when(roleMapper.selectById(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID)).thenReturn(null);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    @Test
    void preHandle_adminRole_appendsRoleAndPermissions() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Active-Workspace", workspaceId);

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        SysRole adminRole = new SysRole();
        adminRole.setId(UUID.fromString(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID));
        adminRole.setName("workspace_admin");
        adminRole.setPermissions(List.of("project:create", "project:edit"));
        when(roleMapper.selectById(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID)).thenReturn(adminRole);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertEquals(3, loginUser.getWorkspaceAuthorities().size());
        List<String> authStrings = loginUser.getWorkspaceAuthorities().stream()
                .map(Object::toString).toList();
        assertTrue(authStrings.contains("ROLE_workspace_admin"));
        assertTrue(authStrings.contains("project:create"));
        assertTrue(authStrings.contains("project:edit"));
    }

    @Test
    void preHandle_memberRole_appendsOnlyRole() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Active-Workspace", workspaceId);

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        SysRole memberRole = new SysRole();
        memberRole.setId(UUID.fromString(ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID));
        memberRole.setName("workspace_member");
        memberRole.setPermissions(List.of());
        when(roleMapper.selectById(ErrorCodeConstants.WORKSPACE_ROLE_MEMBER_ID)).thenReturn(memberRole);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertEquals(1, loginUser.getWorkspaceAuthorities().size());
        assertEquals("ROLE_workspace_member", loginUser.getWorkspaceAuthorities().get(0).toString());
    }

    @Test
    void preHandle_emptyPermissions_appendsOnlyRole() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Active-Workspace", workspaceId);

        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        SysRole adminRole = new SysRole();
        adminRole.setId(UUID.fromString(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID));
        adminRole.setName("workspace_admin");
        adminRole.setPermissions(null);
        when(roleMapper.selectById(ErrorCodeConstants.WORKSPACE_ROLE_ADMIN_ID)).thenReturn(adminRole);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertEquals(1, loginUser.getWorkspaceAuthorities().size());
        assertEquals("ROLE_workspace_admin", loginUser.getWorkspaceAuthorities().get(0).toString());
    }
}
