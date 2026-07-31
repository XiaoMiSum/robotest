package io.github.xiaomisum.robotest.framework.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 合并部署（方案B）SPA 静态资源托管配置。
 * <p>
 * 仅当 classpath 存在 static/index.html（即 scripts/deploy-merged.sh 已复制前端构建产物）时生效，
 * 分离部署与本地开发时该类不注册，行为零影响。
 * <p>
 * 不使用 addViewControllers + 路径正则的方案：PathPattern 的路径变量只能匹配单段路径，
 * 形如 /workspace/xxx/project/yyy 的多级前端路由会 404；
 * 改用 PathResourceResolver 兜底，物理资源不存在时统一回退 index.html，交由前端路由接管。
 */
@Configuration
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        // /api、/ws 未被 Controller/WebSocket 匹配时应返回 404，不能回退到 index.html
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")) {
                            return null;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
