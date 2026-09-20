package io.github.xiaomisum.robotest.framework.config;

import io.github.xiaomisum.robotest.framework.interceptor.ContextHeaderInterceptor;
import io.github.xiaomisum.robotest.framework.interceptor.WorkspaceRoleInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private ContextHeaderInterceptor contextHeaderInterceptor;
    @Resource
    private WorkspaceRoleInterceptor workspaceRoleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // C4 单点（02 §3.1.1）：先解析上下文头注入 LoginUser，WorkspaceRoleInterceptor 不再自读头
        registry.addInterceptor(contextHeaderInterceptor)
                .addPathPatterns("/api/workspace/**", "/api/project/**", "/api/auth/permissions")
                .excludePathPatterns(
                        "/api/admin/**",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/init/**",
                        "/api/workspace/invitations/verify",
                        "/api/workspace/invitations/check-email",
                        "/api/workspace/invitations/join",
                        "/api/workspace/ai/status",
                        "/ws/**",
                        "/debug/**"
                );
        registry.addInterceptor(workspaceRoleInterceptor)
                .addPathPatterns("/api/workspace/**", "/api/project/**", "/api/auth/permissions")
                .excludePathPatterns(
                        "/api/admin/**",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/init/**",
                        "/api/workspace/invitations/verify",
                        "/api/workspace/invitations/check-email",
                        "/api/workspace/invitations/join",
                        "/api/workspace/ai/status",
                        "/ws/**",
                        "/debug/**"
                );
    }
}
