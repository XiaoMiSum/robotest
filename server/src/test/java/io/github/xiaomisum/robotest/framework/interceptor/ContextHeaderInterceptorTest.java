package io.github.xiaomisum.robotest.framework.interceptor;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C4 上下文头唯一解析点特征测试（02 §4 步骤 1、§5 验收：上下文解析覆盖全部入口 Controller）。
 *
 * <p>冻结行为：业务路径（/api/workspaces/active、/api/workspace/**、/api/project/**）缺失/非法上下文头即抛 4xx 业务异常；
 * /api/workspaces 列表路径不强制空间头；豁免路径与匿名请求不拦截；合法头注入 LoginUser.activeWorkspaceId/activeProjectId。</p>
 */
class ContextHeaderInterceptorTest {

    private ContextHeaderInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        interceptor = new ContextHeaderInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        loginUser = new LoginUser();
        loginUser.setId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
        Authentication auth = new UsernamePasswordAuthenticationToken(loginUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubPath(String path) {
        when(request.getRequestURI()).thenReturn(path);
    }

    // ========== 正常解析注入 ==========

    @Test
    void projectPath_parsesAndInjectsBothHeaders() {
        stubPath("/api/project/environments");
        UUID wsId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(request.getHeader("X-Active-Workspace")).thenReturn(wsId.toString());
        when(request.getHeader("X-Active-Project")).thenReturn(projectId.toString());

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(wsId, loginUser.getActiveWorkspaceId());
        assertEquals(projectId, loginUser.getActiveProjectId());
    }

    @Test
    void workspacePath_parsesSingleHeader() {
        stubPath("/api/workspace/members");
        UUID wsId = UUID.randomUUID();
        when(request.getHeader("X-Active-Workspace")).thenReturn(wsId.toString());

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(wsId, loginUser.getActiveWorkspaceId());
    }

    // ========== 严格拒绝：缺失头 ==========

    @Test
    void projectPath_missingWorkspaceHeader_rejects() {
        stubPath("/api/project/bugs");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);
        when(request.getHeader("X-Active-Project")).thenReturn(UUID.randomUUID().toString());

        ServiceException e = seeResultThrow();
        assertEquals(1000002008, e.getCode());
    }

    @Test
    void projectPath_bothHeadersRequired() {
        stubPath("/api/project/bugs");
        when(request.getHeader("X-Active-Workspace")).thenReturn(UUID.randomUUID().toString());
        when(request.getHeader("X-Active-Project")).thenReturn(null);

        ServiceException e = seeResultThrow();
        assertEquals(1000002008, e.getCode());
    }

    @Test
    void workspacePath_missingWorkspaceHeader_rejects() {
        stubPath("/api/workspace/members");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);

        ServiceException e = seeResultThrow();
        assertEquals(1000002008, e.getCode());
    }

    // ========== 严格拒绝：非法格式 ==========

    @Test
    void illegalHeaderValue_rejects() {
        stubPath("/api/project/bugs");
        when(request.getHeader("X-Active-Workspace")).thenReturn("not-a-uuid");
        when(request.getHeader("X-Active-Project")).thenReturn(UUID.randomUUID().toString());

        ServiceException e = seeResultThrow();
        assertEquals(1000002009, e.getCode());
    }

    // ========== 我的空间路径 ==========

    @Test
    void workspaceSelectionPath_withoutHeader_allowed() {
        stubPath("/api/workspaces");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertNull(loginUser.getActiveWorkspaceId());
    }

    @Test
    void workspaceSelectionPath_withHeader_isOptionalButParsed() {
        stubPath("/api/workspaces");
        UUID wsId = UUID.randomUUID();
        when(request.getHeader("X-Active-Workspace")).thenReturn(wsId.toString());

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(wsId, loginUser.getActiveWorkspaceId());
    }

    @Test
    void activeWorkspacePath_missingWorkspaceHeader_rejects() {
        stubPath("/api/workspaces/active");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);

        ServiceException e = seeResultThrow();
        assertEquals(1000002008, e.getCode());
    }

    @Test
    void activeWorkspacePath_parsesWorkspaceHeader() {
        stubPath("/api/workspaces/active");
        UUID wsId = UUID.randomUUID();
        when(request.getHeader("X-Active-Workspace")).thenReturn(wsId.toString());

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(wsId, loginUser.getActiveWorkspaceId());
    }

    @Test
    void activeWorkspacePath_doesNotRequireProjectHeader() {
        stubPath("/api/workspaces/active");
        UUID wsId = UUID.randomUUID();
        when(request.getHeader("X-Active-Workspace")).thenReturn(wsId.toString());
        when(request.getHeader("X-Active-Project")).thenReturn(null);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(wsId, loginUser.getActiveWorkspaceId());
        assertNull(loginUser.getActiveProjectId());
    }

    @Test
    void activeWorkspacePath_ignoresProjectHeader() {
        stubPath("/api/workspaces/active");
        UUID wsId = UUID.randomUUID();
        when(request.getHeader("X-Active-Workspace")).thenReturn(wsId.toString());
        when(request.getHeader("X-Active-Project")).thenReturn("not-a-uuid");

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
        assertEquals(wsId, loginUser.getActiveWorkspaceId());
    }

    @Test
    void activeWorkspacePath_invalidWorkspaceHeader_rejects() {
        stubPath("/api/workspaces/active");
        when(request.getHeader("X-Active-Workspace")).thenReturn("not-a-uuid");

        ServiceException e = seeResultThrow();
        assertEquals(1000002009, e.getCode());
    }

    // ========== 豁免路径 ==========

    @Test
    void permissionsPath_withoutHeaders_allowed() {
        stubPath("/api/auth/permissions");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);
        when(request.getHeader("X-Active-Project")).thenReturn(null);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
    }

    @Test
    void aiStatusPath_withoutHeaders_allowed() {
        stubPath("/api/workspace/ai/status");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
    }

    @Test
    void invitationVerifyPath_withoutHeaders_allowed() {
        stubPath("/api/workspace/invitations/verify");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
    }

    // ========== 匿名请求 ==========

    @Test
    void anonymousRequest_notIntercepted() {
        SecurityContextHolder.clearContext();
        stubPath("/api/project/bugs");

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
    }

    // ========== 非上下文路径 ==========

    @Test
    void nonContextPath_alwaysAllowed() {
        stubPath("/api/workspaces");
        when(request.getHeader("X-Active-Workspace")).thenReturn(null);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertTrue(proceed);
    }

    private ServiceException seeResultThrow() {
        try {
            interceptor.preHandle(request, response, new Object());
        } catch (ServiceException e) {
            return e;
        }
        throw new AssertionError("预期抛出 ServiceException，实际未抛出");
    }
}