package io.github.xiaomisum.robotest.framework.ratelimit;

import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import xyz.migoo.framework.common.exception.ServiceException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 限流键格式、窗口/阈值传参与失败路径（安全规范 6.1：限流键、窗口、阈值和失败测试可验证）。
 */
@ExtendWith(MockitoExtension.class)
class AccessRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RateLimitProperties properties;
    private AccessRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        limiter = new AccessRateLimiter(redisTemplate, properties, new ClientIpResolver());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        return request;
    }

    private MockHttpServletRequest requestBehindProxy(String forwardedFor) {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    // ========== 尝试计数 ==========

    @Test
    void checkAttempt_underLimit_passes_andUsesConfiguredKeyWindow() {
        // 限流键 = {prefix}:{scope}:{identity}；窗口作为脚本参数传入
        when(redisTemplate.execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:refresh:203.0.113.9")), eq(60L))).thenReturn(30L);

        assertDoesNotThrow(() -> limiter.checkRefresh(requestBehindProxy("203.0.113.9, 10.1.1.1")));

        verify(redisTemplate).execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:refresh:203.0.113.9")), eq(60L));
    }

    @Test
    void checkAttempt_countOverLimit_throwsAccessRateLimited() {
        when(redisTemplate.execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                anyList(), eq(60L))).thenReturn(6L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> limiter.checkAttempt("refresh", "10.0.0.9", 5, 60));

        assertEquals(ErrorCodeConstants.ACCESS_RATE_LIMITED.code(), ex.getCode());
    }

    @Test
    void checkAttempt_redisFailure_failsOpen() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(60L)))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        assertDoesNotThrow(() -> limiter.checkAttempt("refresh", "10.0.0.9", 1, 60));
    }

    @Test
    void checkAttempt_disabled_skipsRedis() {
        properties.setEnabled(false);

        assertDoesNotThrow(() -> limiter.checkRefresh(request()));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void checkAttempt_customKeyPrefix_propagatesToKey() {
        properties.setKeyPrefix("myrate");
        when(redisTemplate.execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("myrate:invite:10.0.0.9")), eq(60L))).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.checkInvitation(request()));

        verify(redisTemplate).execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("myrate:invite:10.0.0.9")), eq(60L));
    }

    // ========== 登录：失败计数 + 成功清理 ==========

    private void stubLoginCounts(String ipCount, String accountCount) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rl:login:ip:10.0.0.9")).thenReturn(ipCount);
        when(valueOps.get("rl:login:acct:bob")).thenReturn(accountCount);
    }

    @Test
    void login_success_clearsAccountKeyOnly_andReturnsActionResult() {
        stubLoginCounts(null, null);

        String result = limiter.login(" Bob ", request(), () -> "token");

        assertEquals("token", result);
        // 账号键归一化为小写并清理；IP 键保留至窗口过期（防止借成功登录重置 IP 攻击计数）
        verify(redisTemplate).delete("rl:login:acct:bob");
        verify(redisTemplate, never()).delete("rl:login:ip:10.0.0.9");
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void login_accountFailuresAtThreshold_blocksBeforeAuthentication() {
        stubLoginCounts(null, "5");
        AtomicBoolean authenticated = new AtomicBoolean(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> limiter.login("bob", request(), () -> {
                    authenticated.set(true);
                    return "token";
                }));

        assertEquals(ErrorCodeConstants.ACCESS_RATE_LIMITED.code(), ex.getCode());
        assertFalse(authenticated.get(), "达到阈值后不应再发起认证");
    }

    @Test
    void login_ipFailuresAtThreshold_blocksBeforeAuthentication() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rl:login:ip:10.0.0.9")).thenReturn("10");
        AtomicBoolean authenticated = new AtomicBoolean(false);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> limiter.login("bob", request(), () -> {
                    authenticated.set(true);
                    return "token";
                }));

        assertEquals(ErrorCodeConstants.ACCESS_RATE_LIMITED.code(), ex.getCode());
        assertFalse(authenticated.get());
        verify(valueOps, never()).get("rl:login:acct:bob");
    }

    @Test
    void login_authFailure_recordsIpAndAccountFailures_andRethrows() {
        stubLoginCounts(null, null);
        when(redisTemplate.execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                anyList(), eq(300L))).thenReturn(1L);

        RuntimeException failure = new RuntimeException("bad credentials");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> limiter.login("bob", request(), () -> {
                    throw failure;
                }));

        assertSame(failure, ex, "认证异常必须原样抛出，不改写响应");
        // 双键入账：IP 键 + 账号键，窗口为 loginWindowSeconds
        verify(redisTemplate).execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:login:ip:10.0.0.9")), eq(300L));
        verify(redisTemplate).execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:login:acct:bob")), eq(300L));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void login_recordFailureRedisDown_failsOpenAndStillRethrowsOriginal() {
        stubLoginCounts(null, null);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(300L)))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        RuntimeException failure = new RuntimeException("bad credentials");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> limiter.login("bob", request(), () -> {
                    throw failure;
                }));

        assertSame(failure, ex);
    }

    @Test
    void login_clearRedisDown_failsOpenAndKeepsResult() {
        stubLoginCounts(null, null);
        when(redisTemplate.delete("rl:login:acct:bob"))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        assertEquals("token", limiter.login("bob", request(), () -> "token"));
    }

    @Test
    void login_readRedisDown_failsOpenAndStillAuthenticates() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new DataAccessResourceFailureException("redis down"));

        assertEquals("token", limiter.login("bob", request(), () -> "token"));
    }

    @Test
    void login_disabled_bypassesRedisEntirely() {
        properties.setEnabled(false);

        assertEquals("token", limiter.login("bob", request(), () -> "token"));

        verifyNoInteractions(redisTemplate);
    }

    // ========== 其余编排入口的键格式 ==========

    @Test
    void checkPublicReport_usesConfiguredWindow() {
        when(redisTemplate.execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:public-report:10.0.0.9")), eq(60L))).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.checkPublicReport(request()));

        verify(redisTemplate).execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:public-report:10.0.0.9")), eq(60L));
    }

    @Test
    void checkInitSetup_andPassword_useTheirOwnScopesAndWindows() {
        when(redisTemplate.execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                anyList(), any())).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.checkInitSetup(request()));
        assertDoesNotThrow(() -> limiter.checkPassword(request()));

        verify(redisTemplate).execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:init-setup:10.0.0.9")), eq(600L));
        verify(redisTemplate).execute(eq(AccessRateLimiter.INCR_SCRIPT_OBJ),
                eq(List.of("rl:password:10.0.0.9")), eq(300L));
    }
}
