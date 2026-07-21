package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class WorkspaceMemberAddResultRespDTO {

    private Integer successCount;
    private List<UUID> skippedUserIds;
}
