package io.github.xiaomisum.robotest.framework.security;

import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.migoo.framework.security.core.AuthUserDetails;

@Data
@EqualsAndHashCode(callSuper = true)
public class RobotestUserDetails extends AuthUserDetails<RobotestUserDetails> {

    private String id;

    @Override
    public Object getId() {
        return id;
    }
}
