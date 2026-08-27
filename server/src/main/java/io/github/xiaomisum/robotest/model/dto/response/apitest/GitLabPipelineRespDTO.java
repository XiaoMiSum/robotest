package io.github.xiaomisum.robotest.model.dto.response.apitest;

import lombok.Data;

@Data
public class GitLabPipelineRespDTO {
    private String executionRecordId;
    private String pipelineId;
    private String pipelineUrl;
    private String status;
    private boolean metadataExpired;
    private Integer metadataSyncClassCount;
}
