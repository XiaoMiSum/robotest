package io.github.xiaomisum.robotest.model.dto.response;

import io.github.xiaomisum.robotest.framework.security.LoginUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespDTO {

    private String accessToken;
    private String refreshToken;
    private String accessExpiry;
    private String refreshExpiry;
    private LoginUser user;
    private boolean hasWorkspace;
}
