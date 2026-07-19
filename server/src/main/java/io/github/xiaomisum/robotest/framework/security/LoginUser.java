package io.github.xiaomisum.robotest.framework.security;

import lombok.Getter;
import lombok.Setter;
import xyz.migoo.framework.security.core.AuthUserDetails;

@Getter
@Setter
public class LoginUser extends AuthUserDetails<LoginUser, String> {

    private String email;

    @Override
    public LoginUser setId(String id) {
        return super.setId(id);
    }

    @Override
    public LoginUser setUsername(String username) {
        return super.setUsername(username);
    }

    @Override
    public LoginUser setPassword(String password) {
        return super.setPassword(password);
    }

    @Override
    public LoginUser setName(String name) {
        return super.setName(name);
    }

    @Override
    public LoginUser setEnabled(boolean enabled) {
        return super.setEnabled(enabled);
    }
}
