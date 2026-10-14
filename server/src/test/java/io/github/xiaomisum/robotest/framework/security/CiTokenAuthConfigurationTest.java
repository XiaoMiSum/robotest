package io.github.xiaomisum.robotest.framework.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CI 只读令牌认证装配（GitLab 集成详细设计 4.6）：过滤器只挂在 /api/ci/* 且位于安全链之前。
 * <p>过滤器行为语义见 CiTokenAuthFilterTest，端点级鉴权链见 CiBatchControllerApiTest。
 */
class CiTokenAuthConfigurationTest {

    @Test
    void registrationBindsCiUrlPatternOnlyAndStaysBeforeSecurityChain() {
        FilterRegistrationBean<CiTokenAuthFilter> registration =
                new CiTokenAuthConfiguration().ciTokenAuthFilter("ci-token-xyz");

        assertTrue(registration.getFilter() instanceof CiTokenAuthFilter);
        Collection<String> urlPatterns = registration.getUrlPatterns();
        assertEquals(1, urlPatterns.size());
        assertEquals("/api/ci/*", urlPatterns.iterator().next());
        // 安全过滤链之前短路，避免 /api/ci/** 免登录配置被链上逻辑二次判定
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 110, registration.getOrder());
    }
}
