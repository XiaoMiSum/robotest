package io.github.xiaomisum.robotest.model.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class WorkspaceMemberAddResultRespDTO {

    private Integer successCount;
    private List<String> skippedUserIds;
}
