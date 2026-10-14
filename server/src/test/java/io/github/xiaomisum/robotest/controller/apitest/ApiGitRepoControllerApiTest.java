package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.apitest.ApiGitRepoCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoConnectionTestRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitRepoRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiGitRepoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.migoo.framework.apilog.core.ApiErrorLogFrameworkService;
import xyz.migoo.framework.common.exception.GlobalErrorCodeConstants;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.web.core.handler.GlobalExceptionHandler;
import xyz.migoo.framework.web.i18n.I18NMessage;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 集成测试（GitLab 集成详细设计 3.1）：仓库配置的路由 / 方法、@Valid 参数校验
 * 与业务错误信封（DEC-002 方案 A：HTTP 200，错误由 {@code Result.code} 表达）。
 *
 * <p>独立 MockMvc 只锁定 HTTP 契约：上下文（workspace / project）来自
 * {@code @AuthenticationPrincipal LoginUser}——由 ContextHeaderInterceptor 从
 * X-Active-Workspace / X-Active-Project 头解析注入（C4，拦截器语义另有
 * ContextHeaderInterceptorTest 覆盖），本测试经 SecurityContextHolder 预置带上下文的登录态
 * 并注册 AuthenticationPrincipalArgumentResolver，校验 Service 收到的上下文参数
 * 即锁定「上下文只经请求头、不进 URL / body」的口径；
 * {@code @PreAuthorize} 授权语义由安全链与守卫测试覆盖，独立装配不生效。
 */
class ApiGitRepoControllerApiTest {

    private static final UUID PROJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REPO_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private MockMvc mockMvc;
    private ApiGitRepoService apiGitRepoService;

    @BeforeEach
    void setUp() {
        apiGitRepoService = mock(ApiGitRepoService.class);
        ApiGitRepoController controller = new ApiGitRepoController();
        ReflectionTestUtils.setField(controller, "apiGitRepoService", apiGitRepoService);

        GlobalExceptionHandler advice = new GlobalExceptionHandler(
                "robotest",
                mock(ApiErrorLogFrameworkService.class),
                new I18NMessage(new StaticMessageSource()));

        // 独立 MockMvc 无安全过滤链：@AuthenticationPrincipal 需注册解析器并经
        // SecurityContextHolder 直供登录态，等价于拦截器从 X-Active-* 头解析后的注入结果（C4）
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        bindLoginUser();
    }

    @AfterEach
    void tearDown() {
        // SecurityContextHolder 为线程级上下文，用例结束清理避免跨用例泄漏
        SecurityContextHolder.clearContext();
    }

    private void bindLoginUser() {
        LoginUser user = new LoginUser();
        user.setId(USER_ID);
        user.setUsername("qa-admin");
        user.setActiveWorkspaceId(WORKSPACE_ID);
        user.setActiveProjectId(PROJECT_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private ApiGitRepoRespDTO repoFixture() {
        ApiGitRepoRespDTO dto = new ApiGitRepoRespDTO();
        dto.setId(REPO_ID.toString());
        dto.setProjectId(PROJECT_ID.toString());
        dto.setName("order-api-regression");
        dto.setGitlabBaseUrl("https://gitlab.example.com");
        dto.setRepoPath("qa/order-api-tests");
        dto.setDefaultBranch("master");
        dto.setImportMode("gitlab_ci");
        dto.setEnabled(true);
        dto.setHasUpdates(true);
        return dto;
    }

    // ========== 查询 ==========

    @Test
    void get_success_returnsEnvelopeAndPassesHeaderContext() throws Exception {
        when(apiGitRepoService.getRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID)))
                .thenReturn(repoFixture());

        mockMvc.perform(get("/api/project/git-repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.repoPath").value("qa/order-api-tests"))
                .andExpect(jsonPath("$.data.importMode").value("gitlab_ci"))
                .andExpect(jsonPath("$.data.hasUpdates").value(true));

        verify(apiGitRepoService).getRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID));
    }

    // ========== 创建 ==========

    @Test
    void create_success_forwardsBodyAndReturnsEnvelope() throws Exception {
        when(apiGitRepoService.createRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), any()))
                .thenReturn(repoFixture());

        mockMvc.perform(post("/api/project/git-repos")
                        
                        .contentType("application/json")
                        .content("{\"name\":\"order-api-regression\",\"gitlabBaseUrl\":\"https://gitlab.example.com\","
                                + "\"repoPath\":\"qa/order-api-tests\",\"defaultBranch\":\"master\","
                                + "\"importMode\":\"gitlab_ci\",\"token\":\"glpat-xxxx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.id").value(REPO_ID.toString()));

        // 只写不读的 token 不回显：响应信封永不含令牌（详设 3.1）
        ArgumentCaptor<ApiGitRepoCreateReqDTO> captor = ArgumentCaptor.forClass(ApiGitRepoCreateReqDTO.class);
        verify(apiGitRepoService).createRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), captor.capture());
        assertEquals("order-api-regression", captor.getValue().getName());
        assertEquals("gitlab_ci", captor.getValue().getImportMode());
    }

    @Test
    void create_blankName_rejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/project/git-repos")
                        
                        .contentType("application/json")
                        .content("{\"gitlabBaseUrl\":\"https://gitlab.example.com\",\"importMode\":\"gitlab_ci\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("名称不能为空")));

        verify(apiGitRepoService, never()).createRepo(any(), any(), any(), any());
    }

    @Test
    void create_nonHttpBaseUrl_rejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/project/git-repos")
                        
                        .contentType("application/json")
                        .content("{\"name\":\"repo\",\"gitlabBaseUrl\":\"ftp://gitlab.example.com\","
                                + "\"importMode\":\"gitlab_ci\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("GitLab 地址")));

        verify(apiGitRepoService, never()).createRepo(any(), any(), any(), any());
    }

    @Test
    void create_pathConflict_mapsTo1000017703Envelope() throws Exception {
        when(apiGitRepoService.createRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_PATH_CONFLICT));

        mockMvc.perform(post("/api/project/git-repos")
                        
                        .contentType("application/json")
                        .content("{\"name\":\"repo\",\"gitlabBaseUrl\":\"https://gitlab.example.com\","
                                + "\"importMode\":\"gitlab_ci\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_REPO_PATH_CONFLICT.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_REPO_PATH_CONFLICT.msg()));
    }

    // ========== 更新 ==========

    @Test
    void update_success_returnsTrueEnvelope() throws Exception {
        mockMvc.perform(put("/api/project/git-repos/" + REPO_ID)
                        
                        .contentType("application/json")
                        .content("{\"name\":\"renamed-repo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data").value(true));

        // 部分更新（C11）：请求体只有 name，Service 只应收到该变更字段
        verify(apiGitRepoService).updateRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID),
                argThat(dto -> "renamed-repo".equals(dto.getName()) && dto.getRejectedFields().isEmpty()));
    }

    @Test
    void update_lockedFieldForwardedThroughAnySetter() throws Exception {
        // repoPath 不在 DTO 声明字段中，经 @JsonAnySetter 落入 rejectedFields 交 Service 拒绝（1000017711）
        mockMvc.perform(put("/api/project/git-repos/" + REPO_ID)
                        
                        .contentType("application/json")
                        .content("{\"repoPath\":\"qa/other-tests\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()));

        verify(apiGitRepoService).updateRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID),
                argThat(dto -> dto.getRejectedFields().containsKey("repoPath")));
    }

    @Test
    void update_lockedField_mapsTo1000017711Envelope() throws Exception {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_IMMUTABLE_FIELD))
                .when(apiGitRepoService)
                .updateRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID), any());

        mockMvc.perform(put("/api/project/git-repos/" + REPO_ID)
                        
                        .contentType("application/json")
                        .content("{\"importMode\":\"platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_REPO_IMMUTABLE_FIELD.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_REPO_IMMUTABLE_FIELD.msg()));
    }

    @Test
    void update_nonUuidRepoId_rejectedBeforeService() throws Exception {
        mockMvc.perform(put("/api/project/git-repos/not-a-uuid")
                        
                        .contentType("application/json")
                        .content("{\"name\":\"renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()));

        verify(apiGitRepoService, never()).updateRepo(any(), any(), any(), any(), any());
    }

    // ========== 删除 ==========

    @Test
    void delete_success_returnsTrueEnvelope() throws Exception {
        mockMvc.perform(delete("/api/project/git-repos/" + REPO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data").value(true));

        verify(apiGitRepoService).deleteRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID));
    }

    @Test
    void delete_repoNotFound_mapsTo1000017701Envelope() throws Exception {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND))
                .when(apiGitRepoService).deleteRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID));

        mockMvc.perform(delete("/api/project/git-repos/" + REPO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.msg()));
    }

    @Test
    void delete_boundBySyncTask_mapsTo1000017712Envelope() throws Exception {
        doThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_TASK_BOUND))
                .when(apiGitRepoService).deleteRepo(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID));

        mockMvc.perform(delete("/api/project/git-repos/" + REPO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_REPO_TASK_BOUND.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_REPO_TASK_BOUND.msg()));
    }

    // ========== 连通性测试 ==========

    @Test
    void testConnection_success_returnsProbeEnvelope() throws Exception {
        when(apiGitRepoService.testConnection(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), any()))
                .thenReturn(new ApiGitRepoConnectionTestRespDTO(
                        true, true, true, "17.5.1", "master"));

        mockMvc.perform(post("/api/project/git-repos/connections/test")
                        
                        .contentType("application/json")
                        .content("{\"gitlabBaseUrl\":\"https://gitlab.example.com\","
                                + "\"repoPath\":\"qa/order-api-tests\",\"defaultBranch\":\"master\","
                                + "\"token\":\"glpat-xxxx\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.reachable").value(true))
                .andExpect(jsonPath("$.data.tokenValid").value(true))
                .andExpect(jsonPath("$.data.branchExists").value(true))
                .andExpect(jsonPath("$.data.version").value("17.5.1"))
                .andExpect(jsonPath("$.data.resolvedBranch").value("master"));
    }

    @Test
    void testConnection_connectFailed_mapsTo1000017702Envelope() throws Exception {
        when(apiGitRepoService.testConnection(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), any()))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED));

        mockMvc.perform(post("/api/project/git-repos/connections/test")
                        
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_REPO_CONNECT_FAILED.msg()));
    }

    @Test
    void testConnection_invalidBaseUrl_rejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/project/git-repos/connections/test")
                        
                        .contentType("application/json")
                        .content("{\"gitlabBaseUrl\":\"gitlab.example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()));

        verify(apiGitRepoService, never()).testConnection(any(), any(), any(), any());
    }
}
