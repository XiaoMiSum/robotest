package io.github.xiaomisum.robotest.framework.interceptor;

import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间角色权限拦截器。
 *
 * <p>在请求到达 Controller 之前，读取 {@link LoginUser#activeWorkspaceId}
 * （由 ContextHeaderInterceptor 从 {@code X-Active-Workspace} 解析注入，07 §3.1.2 避免双读），
 * 查询当前用户在该工作空间中的角色及权限，追加到 {@link LoginUser#workspaceAuthorities} 中，
 * 使后续 {@code @PreAuthorize} 等注解可以基于工作空间角色进行授权判断。</p>
 *
 * <p>fail-closed（安全规范 §3.2）：scoped 路径上找不到成员、角色或项目归属即抛业务异常拒绝，
 * 不再仅告警放行；{@code /api/auth/permissions} 只返回调用方自身权限，非成员拿不到任何 ws-*
 * 权限码，维持容忍不拒绝（避免陈旧空间头阻断登录后首屏）。</p>
 */
@Component
public class WorkspaceRoleInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceRoleInterceptor.class);

    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            log.debug("[WS-Auth] 无 LoginUser，跳过工作空间权限加载");
            return true;
        }

        UUID workspaceId = loginUser.getActiveWorkspaceId();
        if (workspaceId == null) {
            // scoped 路径的上下文头由先注册的 ContextHeaderInterceptor 强制；此处仅剩 /api/auth/permissions 可选头
            log.debug("[WS-Auth] 无活跃工作空间，跳过工作空间权限加载");
            return true;
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, loginUser.getId())
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || workspaceUser.getWorkspaceRole() == null) {
            if (isScopedPath(request.getRequestURI())) {
                log.warn("[WS-Auth] 用户 {} 在工作空间 {} 无成员或角色分配，拒绝访问", loginUser.getId(), workspaceId);
                throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
            }
            log.warn("[WS-Auth] 用户 {} 在工作空间 {} 无角色分配，仅不追加工作空间权限", loginUser.getId(), workspaceId);
            return true;
        }

        SysRole role = roleMapper.selectById(workspaceUser.getWorkspaceRole());
        if (role == null) {
            if (isScopedPath(request.getRequestURI())) {
                log.warn("[WS-Auth] 角色 {} 不存在，拒绝访问", workspaceUser.getWorkspaceRole());
                throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
            }
            log.warn("[WS-Auth] 角色 {} 不存在，仅不追加工作空间权限", workspaceUser.getWorkspaceRole());
            return true;
        }

        // 项目上下文归属：项目头必须属于活动空间头，防止跨空间项目越权；
        // 归属不符按项目不存在处理，不泄露跨空间项目存在性（与 ProjectAccessGuard 口径一致）
        UUID projectId = loginUser.getActiveProjectId();
        if (isScopedPath(request.getRequestURI()) && projectId != null) {
            Project project = projectMapper.selectById(projectId);
            if (project == null || !workspaceId.equals(project.getWorkspaceId())) {
                log.warn("[WS-Auth] 项目 {} 不属于工作空间 {}，拒绝访问", projectId, workspaceId);
                throw ServiceExceptionUtil.get(ErrorCodeConstants.PROJECT_NOT_FOUND);
            }
        }

        // 追加角色名（如 ROLE_管理员）
        List<org.springframework.security.core.GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Constants.Auth.ROLE_PREFIX + role.getName()));

        // 追加角色的 permissions JSONB 中的权限码
        if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
            role.getPermissions().forEach(code ->
                    authorities.add(new SimpleGrantedAuthority(code)));
        }

        loginUser.appendWorkspaceAuthorities(authorities);
        log.debug("[WS-Auth] 已加载工作空间 {} 权限: {}", workspaceId, loginUser.getPermissions());
        return true;
    }

    /** scoped = 承载租户/资源数据的路径；/api/auth/permissions 只回显调用方自身权限，不属资源访问 */
    private static boolean isScopedPath(String path) {
        return path.startsWith("/api/workspace/") || path.startsWith("/api/project/");
    }
}
