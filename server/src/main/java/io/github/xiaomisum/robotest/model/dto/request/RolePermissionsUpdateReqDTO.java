package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionsUpdateReqDTO {

    private List<String> permissions;
}
