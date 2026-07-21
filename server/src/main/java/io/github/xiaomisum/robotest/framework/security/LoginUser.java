package io.github.xiaomisum.robotest.framework.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import xyz.migoo.framework.security.core.AuthUserDetails;

import java.util.*;
import java.util.stream.Stream;

@Getter
@Setter
public class LoginUser extends AuthUserDetails<LoginUser, UUID> {

    private String email;

    private Set<String> roleNames;

    private Set<String> permissionCodes;

    /**
     * 工作空间角色追加的权限（由 WorkspaceRoleInterceptor 注入），与系统权限合并后返回。
     */
    @JsonIgnore
    private List<GrantedAuthority> workspaceAuthorities = new ArrayList<>();

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Stream<String> roleStream = roleNames != null
                ? roleNames.stream().map(r -> "ROLE_" + r)
                : Stream.empty();
        Stream<String> permStream = permissionCodes != null
                ? permissionCodes.stream()
                : Stream.empty();
        Stream<GrantedAuthority> wsStream = workspaceAuthorities != null
                ? workspaceAuthorities.stream()
                : Stream.empty();
        return Stream.concat(Stream.concat(roleStream, permStream).map(SimpleGrantedAuthority::new), wsStream)
                .distinct()
                .toList();
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
