package io.github.xiaomisum.robotest.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import io.github.xiaomisum.robotest.model.entity.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.SysRoleMapper;
import io.github.xiaomisum.robotest.repository.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.core.type.TypeReference;
import xyz.migoo.framework.common.util.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;

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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return true;
        }

        String workspaceId = request.getHeader("X-Active-Workspace");
        if (!StringUtils.hasText(workspaceId)) {
            return true;
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceUser>()
                        .eq(WorkspaceUser::getUserId, loginUser.getId())
                        .eq(WorkspaceUser::getWorkspaceId, workspaceId));
        if (workspaceUser == null || !StringUtils.hasText(workspaceUser.getWorkspaceRole())) {
            return true;
        }

        SysRole role = roleMapper.selectById(workspaceUser.getWorkspaceRole());
        if (role == null) {
            return true;
        }

        // 追加角色名权限（如 ROLE_workspace_admin）
        List<String> wsRoleNames = new ArrayList<>();
        wsRoleNames.add(role.getName());

        // 追加角色的 permissions JSONB 中的权限码
        List<String> wsPermCodes = new ArrayList<>();
        if (role.getPermissions() != null && !role.getPermissions().isEmpty()) {
            wsPermCodes.addAll(role.getPermissions());
        }

        // 转为 GrantedAuthority 并追加到 LoginUser
        List<org.springframework.security.core.GrantedAuthority> authorities = new ArrayList<>();
        wsRoleNames.forEach(name ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + name)));
        wsPermCodes.forEach(code ->
                authorities.add(new SimpleGrantedAuthority(code)));
        loginUser.appendWorkspaceAuthorities(authorities);

        return true;
    }
}
