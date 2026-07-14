package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class RoleRespDTO {

    private String id;
    private String name;
    private String description;
    private String type;
    private Boolean isSystem;
    private List<String> permissions;
    private Integer userCount;
}
