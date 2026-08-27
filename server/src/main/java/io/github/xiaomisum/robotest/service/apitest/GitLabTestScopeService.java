package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabTestScopeSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabTestScopeRespDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GitLabTestScopeService {

    List<GitLabTestScopeRespDTO> fetchScopeList(UUID projectId, UUID workspaceId, UUID userId, UUID repositoryId);

    boolean saveScopeList(UUID projectId, UUID workspaceId, UUID userId, GitLabTestScopeSaveReqDTO reqDTO);

    Map<String, String> buildScopeVariables(UUID repositoryId);
}
