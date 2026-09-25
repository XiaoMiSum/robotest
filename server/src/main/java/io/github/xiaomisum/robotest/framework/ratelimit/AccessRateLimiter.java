package io.github.xiaomisum.robotest.framework.ratelimit;

import io.github.xiaomisum.robotest.framework.audit.ClientIpResolver;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 公共接口限流（安全规范 6.1）：登录按 IP + 账号失败计数（成功清理账号键），
 * 刷新/邀请/报告分享/初始化/密码设置按 IP 尝试计数。
 *
 * <p>实现基于 Redis 固定窗口（INCR + 首次 EXPIRE 原子执行）。Redis 不可用时失败开放
 * （放行并记 WARN），与 {@code AiRateLimiter} 口径一致，避免缓存故障阻断认证链路。</p>
 */
@Slf4j
@Component
public class AccessRateLimiter {

    /** ARGV[1] = 窗口秒数；返回递增后的计数。EXPIRE 仅在首帧设置，窗口自首帧固定。 */
    private static final String INCR_SCRIPT = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return n
            """;

    // 包内可见：测试断言限流固定走该脚本
    static final DefaultRedisScript<Long> INCR_SCRIPT_OBJ =
            new DefaultRedisScript<>(INCR_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final ClientIpResolver clientIpResolver;

    public AccessRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties,
                             ClientIpResolver clientIpResolver) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
    }

    // ========== 编排入口（控制器单行调用） ==========

    /**
     * 登录限流：预检 IP/账号失败计数 → 执行认证 → 失败入账双键、成功仅清理账号键。
     *
     * <p>成功不清理 IP 键：防止攻击者借自有有效账号的成功登录重置 IP 攻击计数。</p>
     */
    public <T> T login(String identifier, HttpServletRequest request, Supplier<T> action) {
        if (!properties.isEnabled()) {
            return action.get();
        }
        String ip = clientIpResolver.resolve(request);
        // 账号键归一化为小写，防止大小写变体绕过失败计数
        String account = identifier == null ? ""
                : identifier.trim().toLowerCase(Locale.ROOT);
        checkNotExceeded("login:ip", ip, properties.getLoginIpMaxFailures());
        checkNotExceeded("login:acct", account, properties.getLoginAccountMaxFailures());
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            // 认证失败（凭据错误/账号禁用等）均计入，对不存在的账号同样入账，避免枚举差异
            recordFailure("login:ip", ip, properties.getLoginWindowSeconds());
            recordFailure("login:acct", account, properties.getLoginWindowSeconds());
            throw e;
        }
        clear("login:acct", account);
        return result;
    }

    /** 刷新令牌：单 IP 尝试计数 */
    public void checkRefresh(HttpServletRequest request) {
        checkAttempt("refresh", ip(request),
                properties.getRefreshMaxAttempts(), properties.getRefreshWindowSeconds());
    }

    /** 邀请公开接口（verify/check-email/join 共用计数）：单 IP 尝试计数 */
    public void checkInvitation(HttpServletRequest request) {
        checkAttempt("invite", ip(request),
                properties.getInviteMaxAttempts(), properties.getInviteWindowSeconds());
    }

    /** 报告分享免登录查看：单 IP 尝试计数 */
    public void checkPublicReport(HttpServletRequest request) {
        checkAttempt("public-report", ip(request),
                properties.getPublicReportMaxAttempts(), properties.getPublicReportWindowSeconds());
    }

    /** 系统初始化 setup：单 IP 尝试计数 */
    public void checkInitSetup(HttpServletRequest request) {
        checkAttempt("init-setup", ip(request),
                properties.getInitSetupMaxAttempts(), properties.getInitSetupWindowSeconds());
    }

    /** 密码设置类（自助改密/管理员重置）：单 IP 尝试计数 */
    public void checkPassword(HttpServletRequest request) {
        checkAttempt("password", ip(request),
                properties.getPasswordMaxAttempts(), properties.getPasswordWindowSeconds());
    }

    // ========== 模式原语 ==========

    /**
     * 尝试计数模式：调用即计数，超限抛 ACCESS_RATE_LIMITED。
     * 被拒绝的请求同样计入（窗口自首帧固定，不会被持续请求延长）。
     */
    public void checkAttempt(String scope, String identity, long limit, long windowSeconds) {
        if (!properties.isEnabled()) {
            return;
        }
        Long count = incr(key(scope, identity), windowSeconds);
        if (count != null && count > limit) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ACCESS_RATE_LIMITED);
        }
    }

    /** 失败计数模式预检：只读，已达阈值即抛，不计数 */
    public void checkNotExceeded(String scope, String identity, long limit) {
        if (!properties.isEnabled()) {
            return;
        }
        Long count = get(key(scope, identity));
        if (count != null && count >= limit) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ACCESS_RATE_LIMITED);
        }
    }

    /** 失败计数模式入账（认证失败后调用） */
    public void recordFailure(String scope, String identity, long windowSeconds) {
        if (!properties.isEnabled()) {
            return;
        }
        incr(key(scope, identity), windowSeconds);
    }

    /** 失败计数模式清理（登录成功后调用） */
    public void clear(String scope, String identity) {
        if (!properties.isEnabled()) {
            return;
        }
        String key = key(scope, identity);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("[rate-limit] 清理失败计数失败，按失败开放处理 key={} err={}",
                    key, e.getMessage());
        }
    }

    // ========== Redis 访问（失败开放） ==========

    private String key(String scope, String identity) {
        return properties.getKeyPrefix() + ":" + scope + ":" + (identity == null ? "" : identity);
    }

    private String ip(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private Long incr(String key, long windowSeconds) {
        try {
            return redisTemplate.execute(INCR_SCRIPT_OBJ, List.of(key), windowSeconds);
        } catch (DataAccessException e) {
            log.warn("[rate-limit] 计数失败，按失败开放处理 key={} err={}", key, e.getMessage());
            return null;
        }
    }

    private Long get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (DataAccessException e) {
            log.warn("[rate-limit] 读取失败，按失败开放处理 key={} err={}", key, e.getMessage());
            return null;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
