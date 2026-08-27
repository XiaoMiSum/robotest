package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabPipelineTriggerReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineReportRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabPipelineStatusRespDTO;

import java.util.UUID;

public interface GitLabPipelineService {

    GitLabPipelineRespDTO triggerPipeline(UUID projectId, UUID workspaceId, UUID userId,
                                           UUID repositoryId, GitLabPipelineTriggerReqDTO reqDTO);

    GitLabPipelineStatusRespDTO queryPipelineStatus(UUID projectId, UUID workspaceId, UUID userId,
                                                     UUID executionId);

    GitLabPipelineReportRespDTO pullReport(UUID projectId, UUID workspaceId, UUID userId, UUID executionId);
}
