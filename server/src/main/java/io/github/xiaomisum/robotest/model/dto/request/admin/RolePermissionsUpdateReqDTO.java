package io.github.xiaomisum.robotest.model.dto.request.admin;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionsUpdateReqDTO {

    private List<String> permissions;
}
