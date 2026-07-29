package io.github.xiaomisum.robotest.model.dto.response.workspace;

import lombok.Data;

@Data
public class InvitationVerifyRespDTO {

    private Boolean valid;
    private String workspaceName;
    private String expiresAt;
}
