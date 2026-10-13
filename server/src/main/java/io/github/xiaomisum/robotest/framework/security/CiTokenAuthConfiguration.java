package io.github.xiaomisum.robotest.framework.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * CI 侧只读令牌认证装配（GitLab 集成详细设计 4.6）。
 * <p>注册于安全过滤链之前，对 /api/ci/* 短路校验令牌；URL 免登录配置见
 * application.yaml 的 migoo.security.permit-all-urls（/api/ci/**）。
 */
@Configuration
public class CiTokenAuthConfiguration {

    @Bean
    public FilterRegistrationBean<CiTokenAuthFilter> ciTokenAuthFilter(
            @Value("${robotest.gitlab.ci-token:}") String ciToken) {
        FilterRegistrationBean<CiTokenAuthFilter> registration =
                new FilterRegistrationBean<>(new CiTokenAuthFilter(ciToken));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 110);
        registration.addUrlPatterns("/api/ci/*");
        registration.setName("ciTokenAuthFilter");
        return registration;
    }
}
