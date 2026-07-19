package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InvitationRespDTO {

    private String id;
    private String token;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private String status;
    private LocalDateTime createdAt;
}
