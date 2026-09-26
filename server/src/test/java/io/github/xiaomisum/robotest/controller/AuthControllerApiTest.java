package io.github.xiaomisum.robotest.controller;

import io.github.xiaomisum.robotest.framework.ratelimit.AccessRateLimiter;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.service.admin.UserService;
import io.github.xiaomisum.robotest.service.admin.audit.LoginAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.migoo.framework.apilog.core.ApiErrorLogFrameworkService;
import xyz.migoo.framework.common.exception.GlobalErrorCodeConstants;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher;
import xyz.migoo.framework.web.config.ExceptionHandlingConfiguration;
import xyz.migoo.framework.web.core.handler.GlobalExceptionHandler;
import xyz.migoo.framework.web.i18n.I18NMessage;

import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 集成测试（QA-009）：登录与刷新链路的 HTTP 契约。
 *
 * <p>独立 MockMvc 装配真实 Controller 与 migoo 全局异常 advice：错误信封遵循
 * DEC-002 方案 A（HTTP 200 兼容模式，错误由 {@code Result.code} 表达）。
 * 认证、限流、审计为 Mockito 边界，不依赖数据库与 Redis；未登录 401 由安全过滤链
 * 及拦截器/守卫测试覆盖，不在此层重复。
 */
class AuthControllerApiTest {

    private MockMvc mockMvc;

    private AuthUserDetailsFetcher<LoginUser> authUserDetailsFetcher;
    private AccessRateLimiter accessRateLimiter;
    private LoginAuditService loginAuditService;
    private UserService userService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authUserDetailsFetcher = mock(AuthUserDetailsFetcher.class);
        accessRateLimiter = mock(AccessRateLimiter.class);
        loginAuditService = mock(LoginAuditService.class);
        userService = mock(UserService.class);

        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authUserDetailsFetcher", authUserDetailsFetcher);
        ReflectionTestUtils.setField(controller, "accessRateLimiter", accessRateLimiter);
        ReflectionTestUtils.setField(controller, "loginAuditService", loginAuditService);
        ReflectionTestUtils.setField(controller, "userService", userService);

        GlobalExceptionHandler advice = new ExceptionHandlingConfiguration().globalExceptionHandler(
                "robotest",
                mock(ApiErrorLogFrameworkService.class),
                new I18NMessage(new StaticMessageSource()));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .build();
    }

    private AuthUserDetailsFetcher.LoginResult<LoginUser> loginFixture() {
        LoginUser user = new LoginUser();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setUsername("admin");
        return new AuthUserDetailsFetcher.LoginResult<LoginUser>()
                .setAccessToken("access-token")
                .setRefreshToken("refresh-token")
                .setUser(user);
    }

    @Test
    void login_success_returnsEnvelopeAndAudits() throws Exception {
        // 限流器直通执行认证 Supplier，保证 HTTP 层真正驱动 fetcher（与生产链路一致）
        when(accessRateLimiter.login(anyString(), any(), any())).thenAnswer(invocation ->
                ((Supplier<AuthUserDetailsFetcher.LoginResult<LoginUser>>) invocation.getArgument(2)).get());
        when(authUserDetailsFetcher.authenticate("admin", "secret#123")).thenReturn(loginFixture());

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"identifier\":\"admin\",\"password\":\"secret#123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.username").value("admin"));

        verify(authUserDetailsFetcher).authenticate("admin", "secret#123");
        verify(accessRateLimiter).login(eq("admin"), any(), any());
        verify(loginAuditService).recordLogin(any(UUID.class), eq("admin"), any());
    }

    @Test
    void login_blankCredentials_rejectedBeforeAuthenticator() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.BAD_REQUEST.code()));

        verify(authUserDetailsFetcher, never()).authenticate(any(), any());
        verifyNoAudit();
    }

    @Test
    void login_malformedJson_returnsCompatibilityErrorEnvelope() throws Exception {
        // 畸形 JSON 无 advice 专用分支，落兜底（DEC-002 方案A：HTTP 200，错误由 Result.code 表达）
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("not-a-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.code()));

        verify(authUserDetailsFetcher, never()).authenticate(any(), any());
    }

    @Test
    void refresh_missingHeader_neverReachesAuthenticator() throws Exception {
        // 缺请求头同样落 advice 兜底（DEC-002 方案A），关键是不触达认证器
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.code()));

        verify(authUserDetailsFetcher, never()).refreshToken(any());
    }

    @Test
    void refresh_success_returnsNewTokensThroughLimiter() throws Exception {
        when(authUserDetailsFetcher.refreshToken("old-refresh")).thenReturn(loginFixture());

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Refresh-Token", "old-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(GlobalErrorCodeConstants.SUCCESS.code()))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));

        verify(accessRateLimiter).checkRefresh(any());
        verify(authUserDetailsFetcher).refreshToken("old-refresh");
    }

    private void verifyNoAudit() {
        verify(loginAuditService, never()).recordLogin(any(), any(), any());
    }
}
