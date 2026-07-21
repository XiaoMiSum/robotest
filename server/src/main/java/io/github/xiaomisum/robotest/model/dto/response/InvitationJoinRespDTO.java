package io.github.xiaomisum.robotest.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationJoinRespDTO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private UserInfo user;
    private ActiveWorkspaceInfo activeWorkspace;
    private Boolean isNewUser;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private UUID id;
        private String username;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveWorkspaceInfo {
        private UUID id;
        private String name;
        private String workspaceRole;
    }
}
