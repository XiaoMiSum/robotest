package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RoleTreeRespDTO {

    private UUID id;
    private String name;
    private String type;
    private Boolean isGroup;
    private Boolean isSystem;
    private Integer userCount;
    private List<RoleTreeRespDTO> children;
}
