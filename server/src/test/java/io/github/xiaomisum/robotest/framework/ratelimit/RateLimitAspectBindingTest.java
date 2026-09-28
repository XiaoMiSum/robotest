package io.github.xiaomisum.robotest.framework.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.web.core.annotation.RateLimit;
import xyz.migoo.framework.web.core.ratelimit.DefaultRateLimiter;
import xyz.migoo.framework.web.core.ratelimit.RateLimitAspect;
import xyz.migoo.framework.web.core.store.InMemoryStateStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 框架 {@link RateLimitAspect} 走真实 Spring AOP 代理的回归用例（安全规范 6.1）
 *
 * <p>为何保留：框架 1.4.0 曾因复合切点（{@code @annotation || @within}）在 Spring 7 下
 * 注解参数绑定为空，导致所有 @RateLimit 接口运行期 NPE → 500。此用例守住依赖升级后的
 * 绑定行为，防止回归。</p>
 */
class RateLimitAspectBindingTest {

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {

        @Bean
        RateLimitAspect rateLimitAspect() {
            return new RateLimitAspect(new DefaultRateLimiter(new InMemoryStateStore()));
        }

        @Bean
        Sample sample() {
            return new Sample();
        }
    }

    static class Sample {

        @RateLimit(limit = 1, window = 60)
        public String call() {
            return "ok";
        }
    }

    @Test
    void methodAnnotationBindsThroughProxyAndEnforcesLimit() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            Sample proxy = ctx.getBean(Sample.class);

            assertThat(proxy.call()).isEqualTo("ok");
            assertThatThrownBy(proxy::call)
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("code", 429);
        }
    }
}
