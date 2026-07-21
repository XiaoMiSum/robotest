package io.github.xiaomisum.robotest.model.dto.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RoleUsersAddReqDTO {

    private List<UUID> userIds;
}
