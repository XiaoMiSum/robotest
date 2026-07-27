package io.github.xiaomisum.robotest.framework.config;

import io.github.xiaomisum.robotest.framework.interceptor.WorkspaceRoleInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private WorkspaceRoleInterceptor workspaceRoleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(workspaceRoleInterceptor)
                .addPathPatterns("/api/workspace/**", "/api/project/**", "/api/auth/permissions")
                .excludePathPatterns(
                        "/api/admin/**",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/workspace/invitations/verify",
                        "/api/workspace/invitations/join",
                        "/ws/**",
                        "/debug/**"
                );
    }
}
