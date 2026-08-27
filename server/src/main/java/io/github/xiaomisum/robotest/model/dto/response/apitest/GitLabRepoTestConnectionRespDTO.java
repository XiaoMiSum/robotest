package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

@Data
public class GitLabRepoTestConnectionRespDTO {

    private Boolean success;
    private String message;
    private String repoName;
    private String defaultBranch;
    private Long commitCount;
}
