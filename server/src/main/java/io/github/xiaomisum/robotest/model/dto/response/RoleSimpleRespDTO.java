package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class RoleSimpleRespDTO {

    private UUID id;
    private String name;
    private String type;
    private Boolean isSystem;
    private Integer userCount;
}
