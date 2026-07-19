package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InvitationCreateReqDTO {

    private LocalDateTime expiresAt;
    private Integer maxUses;
}
