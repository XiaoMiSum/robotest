package io.github.xiaomisum.robotest.framework.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.migoo.framework.security.core.AuthUserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Getter
@Setter
public class LoginUser extends AuthUserDetails<LoginUser, UUID> {

    private String email;

    /**
     * 是否拥有至少一个工作空间
     */
    private boolean hasWorkspace;

    /**
     * 工作空间角色追加的权限（由 WorkspaceRoleInterceptor 注入），与系统权限合并后返回。
     */
    private List<GrantedAuthority> workspaceAuthorities = new ArrayList<>();

    @Override
    @JsonIgnore
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Stream<? extends GrantedAuthority> baseStream = super.getAuthorities().stream();
        Stream<? extends GrantedAuthority> wsStream = workspaceAuthorities != null
                ? workspaceAuthorities.stream()
                : Stream.empty();
        return Stream.concat(baseStream, wsStream).distinct().toList();
    }

    /**
     * 追加工作空间角色权限（由 WorkspaceRoleInterceptor 调用）。
     *
     * <p>由于 {@code UsernamePasswordAuthenticationToken} 的 authorities 是创建时的快照，
     * 追加后必须刷新 SecurityContext 中的 Authentication，否则 {@code @PreAuthorize} 等
     * 方法级安全注解无法感知工作空间权限。</p>
     */
    public void appendWorkspaceAuthorities(Collection<? extends GrantedAuthority> authorities) {
        if (authorities != null) {
            this.workspaceAuthorities.addAll(authorities);
            refreshAuthentication();
        }
    }

    /**
     * 刷新 SecurityContext 中的 Authentication，使其 authorities 与合并后的权限一致。
     */
    private void refreshAuthentication() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            var refreshed = new UsernamePasswordAuthenticationToken(
                    auth.getPrincipal(), auth.getCredentials(), getAuthorities());
            refreshed.setDetails(auth.getDetails());
            SecurityContextHolder.getContext().setAuthentication(refreshed);
        }
    }

    public List<String> getPermissions() {
        return getAuthorities().stream().map(GrantedAuthority::getAuthority).distinct().toList();
    }

}
