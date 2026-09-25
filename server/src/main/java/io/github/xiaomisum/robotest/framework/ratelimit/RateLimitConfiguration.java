package io.github.xiaomisum.robotest.framework.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 限流参数装配（安全规范 6.1）；Redis 连接走 spring.data.redis 配置。
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {
}
