package io.github.xiaomisum.robotest.framework.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceRoleInterceptorTest {

    private static final String SCOPED_PATH = "/api/workspace/members";
    private static final String PERMISSIONS_PATH = "/api/auth/permissions";

    @Mock
    private WorkspaceUserMapper workspaceUserMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private WorkspaceRoleInterceptor interceptor;

    private LoginUser loginUser;
    private UUID userId;
    private String workspaceId;

    @BeforeEach
    void setUp() {
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        workspaceId = "00000000-0000-0000-0000-000000000099";

        loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setUsername("testuser");
        loginUser.setName("testuser");
        loginUser.setActiveWorkspaceId(UUID.fromString(workspaceId));
        loginUser.setAuthorities(Collections.emptyList());
        loginUser.setWorkspaceAuthorities(new ArrayList<>());

        // 设置 SecurityContext
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
        SecurityContextHolder.setContext(securityContext);
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void preHandle_noLoginUser_returnsTrue() {
        // given
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    @Test
    void preHandle_noActiveWorkspace_returnsTrue() {
        // given：LoginUser 无活跃工作空间（ContextHeaderInterceptor 未注入）
        loginUser.setActiveWorkspaceId(null);
        MockHttpServletRequest request = new MockHttpServletRequest();

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
        verifyNoInteractions(workspaceUserMapper);
    }

    // ========== fail-closed：scoped 路径无成员/无角色（安全规范 §3.2） ==========

    @Test
    void preHandle_scopedPath_nonMember_throwsNoPermission() {
        MockHttpServletRequest request = request(SCOPED_PATH);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), ex.getCode());
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
        verifyNoInteractions(roleMapper);
    }

    @Test
    void preHandle_scopedPath_workspaceUserNoRole_throwsNoPermission() {
        MockHttpServletRequest request = request(SCOPED_PATH);
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(null);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), ex.getCode());
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
        verifyNoInteractions(roleMapper);
    }

    @Test
    void preHandle_scopedPath_roleNotFound_throwsNoPermission() {
        MockHttpServletRequest request = request(SCOPED_PATH);
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);
        when(roleMapper.selectById(Constants.WorkspaceRole.ADMIN_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ErrorCodeConstants.NO_PERMISSION.code(), ex.getCode());
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    // ========== 非资源路径容忍：permissions 只回显调用方自身权限 ==========

    @Test
    void preHandle_permissionsPath_nonMember_toleratesWithoutAuthorities() {
        MockHttpServletRequest request = request(PERMISSIONS_PATH);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
        verifyNoInteractions(roleMapper, projectMapper);
    }

    @Test
    void preHandle_permissionsPath_roleNotFound_toleratesWithoutAuthorities() {
        MockHttpServletRequest request = request(PERMISSIONS_PATH);
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);
        when(roleMapper.selectById(Constants.WorkspaceRole.ADMIN_ID)).thenReturn(null);

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(result);
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    // ========== fail-closed：项目上下文归属（项目越界） ==========

    @Test
    void preHandle_scopedPath_projectInWorkspace_appendsAuthorities() {
        MockHttpServletRequest request = request("/api/project/dashboard");
        loginUser.setActiveProjectId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));
        stubMemberWithAdminRole();

        Project project = new Project();
        project.setId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));
        project.setWorkspaceId(UUID.fromString(workspaceId));
        when(projectMapper.selectById(UUID.fromString("00000000-0000-0000-0000-00000000a001")))
                .thenReturn(project);

        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(result);
        assertEquals(3, loginUser.getWorkspaceAuthorities().size());
    }

    @Test
    void preHandle_scopedPath_projectNotInWorkspace_throwsNotFound() {
        MockHttpServletRequest request = request("/api/project/dashboard");
        UUID foreignProject = UUID.fromString("00000000-0000-0000-0000-00000000b002");
        loginUser.setActiveProjectId(foreignProject);
        stubMemberWithAdminRole();

        Project project = new Project();
        project.setId(foreignProject);
        project.setWorkspaceId(UUID.fromString("00000000-0000-0000-0000-000000000077"));
        when(projectMapper.selectById(foreignProject)).thenReturn(project);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ErrorCodeConstants.PROJECT_NOT_FOUND.code(), ex.getCode());
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    @Test
    void preHandle_scopedPath_projectNotFound_throwsNotFound() {
        MockHttpServletRequest request = request("/api/project/dashboard");
        UUID missingProject = UUID.fromString("00000000-0000-0000-0000-00000000b003");
        loginUser.setActiveProjectId(missingProject);
        stubMemberWithAdminRole();
        when(projectMapper.selectById(missingProject)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ErrorCodeConstants.PROJECT_NOT_FOUND.code(), ex.getCode());
        assertTrue(loginUser.getWorkspaceAuthorities().isEmpty());
    }

    // ========== 成员加载正常路径 ==========

    @Test
    void preHandle_adminRole_appendsRoleAndPermissions() {
        // given
        MockHttpServletRequest request = request(SCOPED_PATH);
        stubMemberWithAdminRole();

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
        MockHttpServletRequest request = request(SCOPED_PATH);
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(Constants.WorkspaceRole.MEMBER_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        SysRole memberRole = new SysRole();
        memberRole.setId(Constants.WorkspaceRole.MEMBER_ID);
        memberRole.setName("workspace_member");
        memberRole.setPermissions(List.of());
        when(roleMapper.selectById(Constants.WorkspaceRole.MEMBER_ID)).thenReturn(memberRole);

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
        MockHttpServletRequest request = request(SCOPED_PATH);
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        SysRole adminRole = new SysRole();
        adminRole.setId(Constants.WorkspaceRole.ADMIN_ID);
        adminRole.setName("workspace_admin");
        adminRole.setPermissions(null);
        when(roleMapper.selectById(Constants.WorkspaceRole.ADMIN_ID)).thenReturn(adminRole);

        // when
        boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        // then
        assertTrue(result);
        assertEquals(1, loginUser.getWorkspaceAuthorities().size());
        assertEquals("ROLE_workspace_admin", loginUser.getWorkspaceAuthorities().get(0).toString());
    }

    private void stubMemberWithAdminRole() {
        WorkspaceUser workspaceUser = new WorkspaceUser();
        workspaceUser.setWorkspaceRole(Constants.WorkspaceRole.ADMIN_ID);
        when(workspaceUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(workspaceUser);

        SysRole adminRole = new SysRole();
        adminRole.setId(Constants.WorkspaceRole.ADMIN_ID);
        adminRole.setName("workspace_admin");
        adminRole.setPermissions(List.of("project:create", "project:edit"));
        when(roleMapper.selectById(Constants.WorkspaceRole.ADMIN_ID)).thenReturn(adminRole);
    }
}
