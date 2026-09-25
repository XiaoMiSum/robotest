package io.github.xiaomisum.robotest.framework.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 公共接口限流参数（安全规范 6.1）：限流键前缀、窗口、阈值均可配置。
 *
 * <p>限流键格式 {@code {keyPrefix}:{scope}:{identity}}，scope 为接口域、identity 为 IP
 * 或账号标识；实现基于 Redis，连接参数走 {@code spring.data.redis}。</p>
 */
@Data
@ConfigurationProperties(prefix = "robotest.security.rate-limit")
public class RateLimitProperties {

    /** 总开关：关闭后全部放行（仅运维降级用） */
    private boolean enabled = true;
    /** 限流键前缀 */
    private String keyPrefix = "rl";

    /** 登录失败计数窗口（秒） */
    private long loginWindowSeconds = 300;
    /** 登录：单 IP 失败阈值 */
    private long loginIpMaxFailures = 10;
    /** 登录：单账号失败阈值（成功登录后清理） */
    private long loginAccountMaxFailures = 5;

    /** 刷新令牌尝试窗口（秒） */
    private long refreshWindowSeconds = 60;
    /** 刷新：单 IP 尝试阈值 */
    private long refreshMaxAttempts = 30;

    /** 邀请公开接口尝试窗口（秒） */
    private long inviteWindowSeconds = 60;
    /** 邀请公开接口：单 IP 尝试阈值（verify/check-email/join 共用计数） */
    private long inviteMaxAttempts = 20;

    /** 报告分享免登录查看窗口（秒） */
    private long publicReportWindowSeconds = 60;
    /** 报告分享免登录查看：单 IP 尝试阈值 */
    private long publicReportMaxAttempts = 60;

    /** 系统初始化 setup 尝试窗口（秒） */
    private long initSetupWindowSeconds = 600;
    /** 系统初始化 setup：单 IP 尝试阈值 */
    private long initSetupMaxAttempts = 5;

    /** 密码设置类（自助改密/管理员重置）窗口（秒） */
    private long passwordWindowSeconds = 300;
    /** 密码设置类：单 IP 尝试阈值 */
    private long passwordMaxAttempts = 10;
}
