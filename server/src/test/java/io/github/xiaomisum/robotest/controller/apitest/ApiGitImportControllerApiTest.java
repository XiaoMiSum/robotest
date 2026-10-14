package io.github.xiaomisum.robotest.controller.apitest;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportPreviewRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.ApiGitImportStatusRespDTO;
import io.github.xiaomisum.robotest.service.apitest.ApiGitImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 集成测试（GitLab 集成详细设计 3.2）：扫描预览 / 执行导入 / 进度查询的路由与信封契约。
 *
 * <p>与 ApiGitRepoControllerApiTest 同口径：上下文取自预置 activeWorkspaceId / activeProjectId
 * 的 LoginUser principal（C4，头解析由 ContextHeaderInterceptorTest 覆盖），
 * {@code @PreAuthorize} 授权语义不在独立 MockMvc 中生效。
 */
class ApiGitImportControllerApiTest {

    private static final UUID PROJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REPO_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private MockMvc mockMvc;
    private ApiGitImportService apiGitImportService;

    @BeforeEach
    void setUp() {
        apiGitImportService = mock(ApiGitImportService.class);
        ApiGitImportController controller = new ApiGitImportController();
        ReflectionTestUtils.setField(controller, "apiGitImportService", apiGitImportService);

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

    // ========== 扫描预览 ==========

    @Test
    void preview_success_returnsScanSummaryEnvelope() throws Exception {
        ApiGitImportPreviewRespDTO resp = new ApiGitImportPreviewRespDTO();
        resp.setRepoId(REPO_ID);
        resp.setBranch("master");
        resp.setHeadCommit("a1b2c3d4e5f6");
        resp.setClasses(3);
        resp.setEffectiveCases(5);
        ApiGitImportPreviewRespDTO.ScenesDTO scenes = new ApiGitImportPreviewRespDTO.ScenesDTO();
        scenes.setCreate(4);
        scenes.setUpdate(1);
        scenes.setParseFailed(0);
        resp.setScenes(scenes);
        when(apiGitImportService.preview(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/project/git-repos/" + REPO_ID + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.repoId").value(REPO_ID.toString()))
                .andExpect(jsonPath("$.data.branch").value("master"))
                .andExpect(jsonPath("$.data.headCommit").value("a1b2c3d4e5f6"))
                .andExpect(jsonPath("$.data.classes").value(3))
                .andExpect(jsonPath("$.data.effectiveCases").value(5))
                .andExpect(jsonPath("$.data.scenes.create").value(4));

        verify(apiGitImportService).preview(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID));
    }

    @Test
    void preview_repoNotFound_mapsTo1000017701Envelope() throws Exception {
        when(apiGitImportService.preview(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID)))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND));

        mockMvc.perform(post("/api/project/git-repos/" + REPO_ID + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_REPO_NOT_FOUND.msg()));
    }

    // ========== 执行导入 ==========

    @Test
    void imports_success_returnsAcceptedStatusEnvelope() throws Exception {
        when(apiGitImportService.imports(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID)))
                .thenReturn(statusFixture("fetch", 0, null));

        mockMvc.perform(post("/api/project/git-repos/" + REPO_ID + "/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.stage").value("fetch"))
                .andExpect(jsonPath("$.data.processed").value(0));

        verify(apiGitImportService).imports(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID));
    }

    @Test
    void imports_inProgress_mapsTo1000017708Envelope() throws Exception {
        when(apiGitImportService.imports(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID)))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_IMPORT_IN_PROGRESS));

        mockMvc.perform(post("/api/project/git-repos/" + REPO_ID + "/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_IMPORT_IN_PROGRESS.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_IMPORT_IN_PROGRESS.msg()));
    }

    @Test
    void imports_commitUpToDate_mapsTo1000017709Envelope() throws Exception {
        when(apiGitImportService.imports(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID)))
                .thenThrow(ServiceExceptionUtil.get(ErrorCodeConstants.API_GIT_IMPORT_COMMIT_UP_TO_DATE));

        mockMvc.perform(post("/api/project/git-repos/" + REPO_ID + "/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodeConstants.API_GIT_IMPORT_COMMIT_UP_TO_DATE.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCodeConstants.API_GIT_IMPORT_COMMIT_UP_TO_DATE.msg()));
    }

    // ========== 导入进度 ==========

    @Test
    void status_success_returnsProgressEnvelope() throws Exception {
        when(apiGitImportService.status(eq(PROJECT_ID), eq(WORKSPACE_ID), eq(USER_ID), eq(REPO_ID)))
                .thenReturn(statusFixture("scan", 12, 40));

        mockMvc.perform(get("/api/project/git-repos/" + REPO_ID + "/imports/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.stage").value("scan"))
                .andExpect(jsonPath("$.data.processed").value(12))
                .andExpect(jsonPath("$.data.total").value(40));
    }

    // ========== 路由 / 参数边界 ==========

    @Test
    void preview_nonUuidRepoId_rejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/project/git-repos/not-a-uuid/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()));
    }

    @Test
    void imports_getMethodNotAllowed() throws Exception {
        // imports 仅接受 POST：方法不匹配由全局 advice 回 405 信封，锁路由契约
        mockMvc.perform(get("/api/project/git-repos/" + REPO_ID + "/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.METHOD_NOT_ALLOWED.code()));
    }

    private ApiGitImportStatusRespDTO statusFixture(String stage, Integer processed, Integer total) {
        ApiGitImportStatusRespDTO dto = new ApiGitImportStatusRespDTO();
        dto.setStage(stage);
        dto.setProcessed(processed);
        dto.setTotal(total);
        return dto;
    }
}
