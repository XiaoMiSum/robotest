package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

@Data
public class GitLabRepoListItemRespDTO {

    private String id;
    private String name;
    private String repoUrl;
    private String branch;
    private String tokenSuffix;
    private String testSourcePath;
    private String lastImportStatus;
    private String lastImportAt;
    private String lastMetadataSyncAt;
    private String updatedAt;
}
