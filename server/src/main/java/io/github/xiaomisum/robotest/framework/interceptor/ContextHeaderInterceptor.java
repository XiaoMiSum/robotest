package io.github.xiaomisum.robotest.framework.interceptor;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.UUID;

/**
 * C4 上下文头唯一解析点（02 §3.1.1 / 07 §3.1.2）。
 *
 * <p>将 {@code X-Active-Workspace} / {@code X-Active-Project} 解析到
 * {@link LoginUser#activeWorkspaceId} / {@link LoginUser#activeProjectId}，
 * 使 Controller 不再直接 {@code @RequestHeader} 读取上下文头。</p>
 *
 * <p>严格拒绝（用户确认）：业务路径缺少必要上下文头或头格式非法即抛 4xx 业务异常。
 * 豁免路径（不强制头）：{@code /api/auth/permissions}（可选头，无空间时返回系统权限）、
 * 邀请公开接口（verify/check-email/join）、{@code /api/workspace/ai/status}（全局开关，
 * AI 基础设施详细设计 3.2.1 不依赖工作空间上下文）。匿名请求（无 LoginUser）不拦截。</p>
 */
@Component
public class ContextHeaderInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ContextHeaderInterceptor.class);

    private static final String HEADER_WORKSPACE = "X-Active-Workspace";
    private static final String HEADER_PROJECT = "X-Active-Project";

    @Override
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                             @Nonnull Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return true;
        }

        UUID workspaceId = parseHeader(request.getHeader(HEADER_WORKSPACE), HEADER_WORKSPACE, request);
        UUID projectId = parseHeader(request.getHeader(HEADER_PROJECT), HEADER_PROJECT, request);
        loginUser.setActiveWorkspaceId(workspaceId);
        loginUser.setActiveProjectId(projectId);

        String path = request.getRequestURI();
        if (isExempt(path)) {
            return true;
        }
        if (path.startsWith("/api/project/")) {
            require(workspaceId, HEADER_WORKSPACE);
            require(projectId, HEADER_PROJECT);
        } else if (path.startsWith("/api/workspace/")) {
            require(workspaceId, HEADER_WORKSPACE);
        }
        return true;
    }

    private UUID parseHeader(String value, String header, HttpServletRequest request) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("[C4] {} 头非 UUID 格式: {}，路径 {}", header, value, request.getRequestURI());
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_INVALID);
        }
    }

    private void require(UUID id, String header) {
        if (id == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING, header);
        }
    }

    private boolean isExempt(String path) {
        return path.equals("/api/auth/permissions")
                || path.equals("/api/workspace/ai/status")
                || path.equals("/api/workspace/invitations/verify")
                || path.equals("/api/workspace/invitations/check-email")
                || path.equals("/api/workspace/invitations/join");
    }
}