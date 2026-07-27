package io.github.xiaomisum.robotest.framework.security;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
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
     */
    public void appendWorkspaceAuthorities(Collection<? extends GrantedAuthority> authorities) {
        if (authorities != null) {
            this.workspaceAuthorities.addAll(authorities);
        }
    }
}
