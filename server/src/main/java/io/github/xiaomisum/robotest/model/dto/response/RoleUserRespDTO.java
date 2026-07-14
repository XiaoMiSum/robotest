package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleUserRespDTO {

    private String id;
    private String username;
    private String email;
    private String status;
    private LocalDateTime createdAt;
}
