package io.github.xiaomisum.robotest.framework.config;

import io.github.xiaomisum.robotest.framework.interceptor.ContextHeaderInterceptor;
import io.github.xiaomisum.robotest.framework.interceptor.WorkspaceRoleInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private ContextHeaderInterceptor contextHeaderInterceptor;
    @Resource
    private WorkspaceRoleInterceptor workspaceRoleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // OpenAPI 端点：先注册，防止被静态资源处理器拦截
        registry.addResourceHandler("/v3/api-docs/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springdoc-openapi-ui/")
                .resourceChain(false);

        // 静态资源：排除 OpenAPI 路径
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected org.springframework.core.io.Resource getResource(
                            String resourcePath, org.springframework.core.io.Resource location) throws IOException {
                        if (resourcePath.startsWith("v3/api-docs")) {
                            return null;
                        }
                        return super.getResource(resourcePath, location);
                    }
                });
    }
}
