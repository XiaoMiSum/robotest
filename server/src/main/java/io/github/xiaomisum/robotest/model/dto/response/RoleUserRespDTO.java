package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class RoleUserRespDTO {

    private UUID id;
    private String username;
    private String email;
    private String status;
    private LocalDateTime createdAt;
}
