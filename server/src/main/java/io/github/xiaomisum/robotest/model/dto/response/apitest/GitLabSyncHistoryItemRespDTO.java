package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

@Data
public class GitLabSyncHistoryItemRespDTO {
    private String id;
    private String syncAt;
    private Integer classCount;
    private Integer methodCount;
    private String commitSha;
    private String status;
}
