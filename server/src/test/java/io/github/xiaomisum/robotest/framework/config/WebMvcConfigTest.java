package io.github.xiaomisum.robotest.framework.config;

import io.github.xiaomisum.robotest.framework.interceptor.ContextHeaderInterceptor;
import io.github.xiaomisum.robotest.framework.interceptor.WorkspaceRoleInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 拦截器注册路径回归（C4 上下文头单点解析）。
 *
 * <p>why：AI 端点历史上未登记进 ContextHeaderInterceptor，导致 LoginUser 上的活动
 * 工作空间 / 项目恒为 null（生成发起误回 1000018205、助手回 1000002008、任务列表项目过滤失效），
 * 故锁定「解析覆盖 /api/ai/**」与「AI 路径不进角色 fail-closed 域」两条注册契约。</p>
 */
@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    @Mock
    private ContextHeaderInterceptor contextHeaderInterceptor;

    @Mock
    private WorkspaceRoleInterceptor workspaceRoleInterceptor;

    @Mock
    private InterceptorRegistry registry;

    @Mock
    private InterceptorRegistration contextRegistration;

    @Mock
    private InterceptorRegistration roleRegistration;

    @InjectMocks
    private WebMvcConfig config;

    @Test
    void addInterceptors_contextHeaderCoversAiPaths() {
        stubRegistrations();

        config.addInterceptors(registry);

        List<String> contextPaths = capturePaths(contextRegistration);
        assertTrue(contextPaths.contains("/api/ai/**"),
                "上下文头解析必须覆盖 /api/ai/**，实际注册：" + contextPaths);
        List<String> rolePaths = capturePaths(roleRegistration);
        assertTrue(rolePaths.stream().noneMatch(path -> path.startsWith("/api/ai")),
                "AI 端点不做工作空间角色 fail-closed 拒绝，实际注册：" + rolePaths);
    }

    private void stubRegistrations() {
        when(registry.addInterceptor(contextHeaderInterceptor)).thenReturn(contextRegistration);
        when(registry.addInterceptor(workspaceRoleInterceptor)).thenReturn(roleRegistration);
        when(contextRegistration.addPathPatterns(any(String[].class))).thenReturn(contextRegistration);
        when(contextRegistration.excludePathPatterns(any(String[].class))).thenReturn(contextRegistration);
        when(roleRegistration.addPathPatterns(any(String[].class))).thenReturn(roleRegistration);
        when(roleRegistration.excludePathPatterns(any(String[].class))).thenReturn(roleRegistration);
    }

    private List<String> capturePaths(InterceptorRegistration registration) {
        ArgumentCaptor<String[]> paths = ArgumentCaptor.forClass(String[].class);
        verify(registration).addPathPatterns(paths.capture());
        return Arrays.asList(paths.getValue());
    }
}
