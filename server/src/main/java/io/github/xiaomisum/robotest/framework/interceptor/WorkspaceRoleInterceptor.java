package io.github.xiaomisum.robotest.framework.interceptor;

import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;
import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 工作空间角色权限拦截器。
 *
 * <p>在请求到达 Controller 之前，读取 {@code X-Active-Workspace} 头，
 * 查询当前用户在该工作空间中的角色及权限，追加到 {@link LoginUser#workspaceAuthorities} 中，
 * 使后续 {@code @PreAuthorize} 等注解可以基于工作空间角色进行授权判断。</p>
 */
@Component
public class WorkspaceRoleInterceptor implements HandlerInterceptor {

    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private SysRoleMapper roleMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return true;
        }

        String workspaceIdStr = request.getHeader("X-Active-Workspace");
        if (!StringUtils.hasText(workspaceIdStr)) {
            return true;
        }
        UUID workspaceId = UUID.fromString(workspaceIdStr);

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapperX<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, loginUser.getId())
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || workspaceUser.getWorkspaceRole() == null) {
            return true;
        }

        SysRole role = roleMapper.selectById(workspaceUser.getWorkspaceRole());
        if (role == null) {
            return true;
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
        return true;
    }
}
