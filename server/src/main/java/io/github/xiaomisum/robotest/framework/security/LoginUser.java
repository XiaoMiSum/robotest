package io.github.xiaomisum.robotest.framework.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import xyz.migoo.framework.security.core.AuthUserDetails;

import java.util.*;
import java.util.stream.Stream;

@Getter
@Setter
public class LoginUser extends AuthUserDetails<LoginUser, UUID> {

    private String email;

    /**
     * 当前用户的权限码列表（系统权限 + 工作空间权限），序列化返回前端。
     */
    @Setter
    private List<String> permissions = new ArrayList<>();

    /**
     * 工作空间角色追加的权限（由 WorkspaceRoleInterceptor 注入），与系统权限合并后返回。
     */
    @JsonIgnore
    private List<GrantedAuthority> workspaceAuthorities = new ArrayList<>();

    @Override
    @JsonIgnore
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Stream<? extends GrantedAuthority> baseStream =  super.getAuthorities().stream();
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
