package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

@Data
public class GitLabSyncConfigRespDTO {
    private Boolean autoSyncEnabled;
    private String testSourcePath;
    private String annotationFilter;
    private Boolean onlyWithResourcePath;
}
