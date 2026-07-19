package io.github.xiaomisum.robotest.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        private String id;
        private String username;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveWorkspaceInfo {
        private String id;
        private String name;
        private String workspaceRole;
    }
}
