package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class InvitationRespDTO {

    private UUID id;
    private String token;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private String status;
    private LocalDateTime createdAt;
}
